package com.duplicatevideo.remover

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale


enum class MatchCriterion(
    val title: String,
    val description: String
) {
    DURATION("같은 길이", "재생 시간이 같은 영상을 찾습니다."),
    SIZE("같은 크기", "파일 용량이 정확히 같은 영상을 찾습니다."),
    SIMILAR_NAME("비슷한 이름", "파일명에 연속된 6글자가 같은 영상을 찾습니다."),
    THUMBNAIL("같은 썸네일", "대표 장면의 시각적 특징이 같은 영상을 찾습니다."),
    HASH("같은 해시값", "파일 내용이 완전히 동일한 영상을 찾습니다."),
    RECOMMENDED("추천 조합", "길이와 크기·이름·썸네일을 조합해 오탐을 줄입니다.")
}

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val relativePath: String,
    val dateModified: Long
)

data class VideoGroup(
    val id: String,
    val items: List<VideoItem>
) {
    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
}

data class ScanProgress(
    val fraction: Float,
    val stage: String,
    val processed: Int,
    val total: Int
)

object VideoScanner {
    private const val SIMILAR_NAME_LENGTH = 6

    suspend fun scan(
        context: Context,
        criterion: MatchCriterion,
        onProgress: suspend (ScanProgress) -> Unit
    ): List<VideoGroup> = withContext(Dispatchers.IO) {
        onProgress(ScanProgress(0.03f, "동영상 목록을 불러오는 중", 0, 0))
        val videos = queryVideos(context)
        if (videos.isEmpty()) {
            onProgress(ScanProgress(1f, "완료", 0, 0))
            return@withContext emptyList()
        }

        onProgress(ScanProgress(0.12f, "${videos.size}개 영상을 분석하는 중", 0, videos.size))

        val groups = when (criterion) {
            MatchCriterion.DURATION -> groupSameDuration(videos)
            MatchCriterion.SIZE -> groupByExact(videos) { it.sizeBytes.takeIf { size -> size > 0 } }
            MatchCriterion.SIMILAR_NAME -> groupSimilarNames(videos)
            MatchCriterion.THUMBNAIL -> groupSameThumbnail(context, videos, onProgress)
            MatchCriterion.HASH -> groupSameHash(context, videos, onProgress)
            MatchCriterion.RECOMMENDED -> groupRecommended(context, videos, onProgress)
        }

        onProgress(ScanProgress(1f, "중복 그룹 분류 완료", videos.size, videos.size))
        groups
            .mapIndexed { index, list ->
                VideoGroup(
                    id = "${criterion.name}-${list.firstOrNull()?.id ?: index}",
                    items = list.sortedWith(compareByDescending<VideoItem> { it.sizeBytes }.thenBy { it.name })
                )
            }
            .sortedByDescending { it.totalBytes }
    }

    private fun queryVideos(context: Context): List<VideoItem> {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.DATE_MODIFIED
        )

        val result = mutableListOf<VideoItem>()
        resolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val size = cursor.getLong(sizeCol)
                if (size <= 0L) continue

                result += VideoItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = cursor.getString(nameCol) ?: "video_$id",
                    sizeBytes = size,
                    durationMs = cursor.getLong(durationCol).coerceAtLeast(0L),
                    width = cursor.getInt(widthCol).coerceAtLeast(0),
                    height = cursor.getInt(heightCol).coerceAtLeast(0),
                    relativePath = cursor.getString(pathCol) ?: "",
                    dateModified = cursor.getLong(dateCol)
                )
            }
        }
        return result
    }

    private fun groupSameDuration(videos: List<VideoItem>): List<List<VideoItem>> {
        return videos
            .filter { it.durationMs > 0 }
            .groupBy { (it.durationMs + 500L) / 1000L }
            .values
            .filter { it.size > 1 }
    }

    private fun <K> groupByExact(
        videos: List<VideoItem>,
        keySelector: (VideoItem) -> K?
    ): List<List<VideoItem>> {
        return videos
            .mapNotNull { video -> keySelector(video)?.let { it to video } }
            .groupBy({ it.first }, { it.second })
            .values
            .filter { it.size > 1 }
    }

    private fun groupSimilarNames(videos: List<VideoItem>): List<List<VideoItem>> {
        val dsu = DisjointSet(videos.size)
        val tokenOwner = HashMap<String, Int>()

        videos.forEachIndexed { index, video ->
            val tokens = sixCharacterTokens(normalizeName(video.name))
            for (token in tokens) {
                val previous = tokenOwner.putIfAbsent(token, index)
                if (previous != null) dsu.union(previous, index)
            }
        }
        return dsu.toGroups(videos)
    }

    private suspend fun groupSameThumbnail(
        context: Context,
        videos: List<VideoItem>,
        onProgress: suspend (ScanProgress) -> Unit
    ): List<List<VideoItem>> {
        val map = HashMap<Long, MutableList<VideoItem>>()
        videos.forEachIndexed { index, video ->
            thumbnailHash(context, video.uri)?.let { hash ->
                map.getOrPut(hash) { mutableListOf() }.add(video)
            }
            if (index % 8 == 0 || index == videos.lastIndex) {
                val fraction = 0.12f + 0.82f * ((index + 1f) / videos.size)
                onProgress(ScanProgress(fraction, "썸네일 비교 중", index + 1, videos.size))
            }
        }
        return map.values.filter { it.size > 1 }
    }

    private suspend fun groupSameHash(
        context: Context,
        videos: List<VideoItem>,
        onProgress: suspend (ScanProgress) -> Unit
    ): List<List<VideoItem>> {
        val candidateGroups = videos.groupBy { it.sizeBytes }.values.filter { it.size > 1 }
        val candidates = candidateGroups.flatten()
        if (candidates.isEmpty()) return emptyList()

        val map = HashMap<String, MutableList<VideoItem>>()
        candidates.forEachIndexed { index, video ->
            sha256(context, video.uri)?.let { hash ->
                map.getOrPut(hash) { mutableListOf() }.add(video)
            }
            if (index % 4 == 0 || index == candidates.lastIndex) {
                val fraction = 0.12f + 0.82f * ((index + 1f) / candidates.size)
                onProgress(ScanProgress(fraction, "파일 내용 확인 중", index + 1, candidates.size))
            }
        }
        return map.values.filter { it.size > 1 }
    }

    private suspend fun groupRecommended(
        context: Context,
        videos: List<VideoItem>,
        onProgress: suspend (ScanProgress) -> Unit
    ): List<List<VideoItem>> {
        val thumbnailHashes = HashMap<Long, Long?>()
        videos.forEachIndexed { index, video ->
            thumbnailHashes[video.id] = thumbnailHash(context, video.uri)
            if (index % 8 == 0 || index == videos.lastIndex) {
                val fraction = 0.12f + 0.50f * ((index + 1f) / videos.size)
                onProgress(ScanProgress(fraction, "대표 장면 분석 중", index + 1, videos.size))
            }
        }

        val dsu = DisjointSet(videos.size)
        val durationBuckets = videos.withIndex()
            .filter { it.value.durationMs > 0 }
            .groupBy { (it.value.durationMs + 500L) / 1000L }

        var processedBuckets = 0
        val totalBuckets = durationBuckets.size.coerceAtLeast(1)
        for ((_, entries) in durationBuckets) {
            if (entries.size > 1) {
                for (a in 0 until entries.lastIndex) {
                    for (b in a + 1 until entries.size) {
                        val left = entries[a]
                        val right = entries[b]
                        val sizeMatches = left.value.sizeBytes == right.value.sizeBytes
                        val nameMatches = namesShareSixCharacters(left.value.name, right.value.name)
                        val leftThumb = thumbnailHashes[left.value.id]
                        val rightThumb = thumbnailHashes[right.value.id]
                        val thumbnailMatches = leftThumb != null && leftThumb == rightThumb

                        if (sizeMatches || nameMatches || thumbnailMatches) {
                            dsu.union(left.index, right.index)
                        }
                    }
                }
            }
            processedBuckets++
            if (processedBuckets % 12 == 0 || processedBuckets == totalBuckets) {
                val fraction = 0.62f + 0.32f * (processedBuckets.toFloat() / totalBuckets)
                onProgress(ScanProgress(fraction, "추천 조합 판정 중", processedBuckets, totalBuckets))
            }
        }
        return dsu.toGroups(videos)
    }

    private fun normalizeName(name: String): String {
        return name.substringBeforeLast('.', name)
            .lowercase(Locale.ROOT)
            .trim()
    }

    private fun sixCharacterTokens(name: String): Set<String> {
        if (name.length < SIMILAR_NAME_LENGTH) return emptySet()
        val result = HashSet<String>()
        for (i in 0..name.length - SIMILAR_NAME_LENGTH) {
            result += name.substring(i, i + SIMILAR_NAME_LENGTH)
        }
        return result
    }

    private fun namesShareSixCharacters(a: String, b: String): Boolean {
        val left = normalizeName(a)
        val right = normalizeName(b)
        if (left.length < SIMILAR_NAME_LENGTH || right.length < SIMILAR_NAME_LENGTH) return false
        val shorter = if (left.length <= right.length) left else right
        val longer = if (left.length <= right.length) right else left
        for (i in 0..shorter.length - SIMILAR_NAME_LENGTH) {
            if (longer.contains(shorter.substring(i, i + SIMILAR_NAME_LENGTH))) return true
        }
        return false
    }

    private fun thumbnailHash(context: Context, uri: Uri): Long? {
        return runCatching {
            val source = context.contentResolver.loadThumbnail(uri, Size(160, 90), null)
            dHash(source).also { source.recycle() }
        }.getOrNull()
    }

    private fun dHash(source: Bitmap): Long {
        val bitmap = Bitmap.createScaledBitmap(source, 9, 8, true)
        var hash = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = bitmap.getPixel(x, y)
                val right = bitmap.getPixel(x + 1, y)
                val leftGray = gray(left)
                val rightGray = gray(right)
                hash = (hash shl 1) or if (leftGray > rightGray) 1L else 0L
            }
        }
        if (bitmap !== source) bitmap.recycle()
        return hash
    }

    private fun gray(color: Int): Int {
        val r = (color shr 16) and 0xff
        val g = (color shr 8) and 0xff
        val b = color and 0xff
        return (r * 30 + g * 59 + b * 11) / 100
    }

    private fun sha256(context: Context, uri: Uri): String? {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            } ?: return null
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    private class DisjointSet(size: Int) {
        private val parent = IntArray(size) { it }
        private val rank = IntArray(size)

        private fun find(value: Int): Int {
            if (parent[value] != value) parent[value] = find(parent[value])
            return parent[value]
        }

        fun union(a: Int, b: Int) {
            var rootA = find(a)
            var rootB = find(b)
            if (rootA == rootB) return
            if (rank[rootA] < rank[rootB]) {
                val tmp = rootA
                rootA = rootB
                rootB = tmp
            }
            parent[rootB] = rootA
            if (rank[rootA] == rank[rootB]) rank[rootA]++
        }

        fun toGroups(videos: List<VideoItem>): List<List<VideoItem>> {
            val map = LinkedHashMap<Int, MutableList<VideoItem>>()
            videos.indices.forEach { index ->
                map.getOrPut(find(index)) { mutableListOf() }.add(videos[index])
            }
            return map.values.filter { it.size > 1 }
        }
    }
}
