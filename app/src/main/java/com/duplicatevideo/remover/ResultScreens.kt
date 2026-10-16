package com.duplicatevideo.remover

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResultsScreen(
    criterion: MatchCriterion,
    groups: List<VideoGroup>,
    selectedCount: Int,
    selectedBytes: Long,
    onBack: () -> Unit,
    onOpenGroup: (VideoGroup) -> Unit,
    onDeleteSelected: () -> Unit,
    onSettings: () -> Unit
) {
    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("중복 동영상 (${groups.size}개 그룹)") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null) } },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "설정") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        bottomBar = {
            if (selectedCount > 0) {
                Surface(shadowElevation = 8.dp, color = AppSurface) {
                    Button(
                        onClick = onDeleteSelected,
                        modifier = Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppPink),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Outlined.DeleteForever, null)
                        Spacer(Modifier.width(8.dp))
                        Text("선택한 파일 삭제 ($selectedCount개, ${formatBytes(selectedBytes)})")
                    }
                }
            }
        }
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyResults(modifier = Modifier.padding(padding), criterion = criterion)
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("검색 기준", color = SubtleText)
                        Text(criterion.title, color = AppBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
                items(groups, key = { it.id }) { group ->
                    GroupCard(group = group, onClick = { onOpenGroup(group) })
                }
            }
        }
    }
}

@Composable
internal fun EmptyResults(modifier: Modifier = Modifier, criterion: MatchCriterion) {
    Column(
        modifier = modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(92.dp).clip(CircleShape).background(Color(0xFFEAF9EF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF159447), modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("중복 후보가 없습니다", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("${criterion.title} 기준에서 2개 이상 묶이는 영상이 없습니다.", color = SubtleText)
    }
}

@Composable
internal fun GroupCard(group: VideoGroup, onClick: () -> Unit) {
    val first = group.items.first()
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                VideoThumbnail(first.uri, Modifier.size(width = 104.dp, height = 72.dp))
                Surface(
                    color = Color(0xCC111111),
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
                ) {
                    Text("${group.items.size}", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("중복 그룹 (${group.items.size}개)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))
                Text("${formatDuration(first.durationMs)} · ${resolution(first)}", color = SubtleText, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text("총 ${formatBytes(group.totalBytes)}", color = SubtleText, fontSize = 13.sp)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = SubtleText)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupDetailScreen(
    group: VideoGroup,
    selectedUris: Set<Uri>,
    onBack: () -> Unit,
    onToggle: (Uri) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onPlay: (VideoItem) -> Unit,
    onDeleteSelected: () -> Unit
) {
    val groupSelected = group.items.filter { it.uri in selectedUris }
    val allSelected = groupSelected.size == group.items.size

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("그룹 (${group.items.size}개)") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = AppSurface) {
                Button(
                    enabled = groupSelected.isNotEmpty(),
                    onClick = onDeleteSelected,
                    modifier = Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppPink),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Outlined.DeleteForever, null)
                    Spacer(Modifier.width(8.dp))
                    Text("선택한 파일 삭제 (${groupSelected.size}개, ${formatBytes(groupSelected.sumOf { it.sizeBytes })})")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("${formatDuration(group.items.first().durationMs)} · ${resolution(group.items.first())}", color = SubtleText)
                        Text("총 ${formatBytes(group.totalBytes)}", color = SubtleText, fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("전체 선택", fontSize = 13.sp)
                        Checkbox(checked = allSelected, onCheckedChange = onSelectAll)
                    }
                }
            }
            items(group.items, key = { it.uri.toString() }) { item ->
                VideoRow(
                    item = item,
                    checked = item.uri in selectedUris,
                    onToggle = { onToggle(item.uri) },
                    onPlay = { onPlay(item) }
                )
                HorizontalDivider(color = Border)
            }
        }
    }
}

@Composable
internal fun VideoRow(
    item: VideoItem,
    checked: Boolean,
    onToggle: () -> Unit,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            VideoThumbnail(item.uri, Modifier.size(width = 112.dp, height = 76.dp))
            Surface(
                color = Color(0xCC111111),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp)
            ) {
                Text(formatDuration(item.durationMs), color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text("${formatBytes(item.sizeBytes)} · ${resolution(item)}", color = SubtleText, fontSize = 12.sp)
            Text("/${item.relativePath.trim('/')}", color = SubtleText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PlayCircleOutline, null, tint = AppBlue, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("탭하여 재생", color = AppBlue, fontSize = 11.sp)
            }
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
internal fun VideoThumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(uri, Size(320, 180), null) }.getOrNull()
        }
    }
    Box(
        modifier = modifier.clip(RoundedCornerShape(13.dp)).background(Color(0xFFE7EBF2)),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(bitmap = image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Outlined.Movie, null, tint = Color(0xFF8C95A7), modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
internal fun DeleteConfirmDialog(
    items: List<VideoItem>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.DeleteForever, null, tint = AppPink, modifier = Modifier.size(38.dp)) },
        title = { Text("${items.size}개의 파일을 영구 삭제할까요?") },
        text = {
            Column {
                Text("삭제한 파일은 이 앱에서 복구할 수 없습니다.", color = AppPink, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text("선택한 용량: ${formatBytes(items.sumOf { it.sizeBytes })}")
                Spacer(Modifier.height(6.dp))
                Text("Android 시스템이 마지막 삭제 확인창을 한 번 더 표시합니다.", color = SubtleText, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("영구 삭제", color = AppPink, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
