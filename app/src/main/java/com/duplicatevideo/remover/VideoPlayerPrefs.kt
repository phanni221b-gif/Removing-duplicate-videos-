package com.duplicatevideo.remover

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri


data class VideoPlayerApp(
    val packageName: String,
    val label: String
)

object VideoPlayerPrefs {
    private const val PREFS = "video_player_preferences"
    private const val KEY_PACKAGE = "preferred_video_player"

    fun getPreferredPackage(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PACKAGE, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun setPreferredPackage(context: Context, packageName: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PACKAGE, packageName.orEmpty())
            .apply()
    }

    fun installedPlayers(context: Context): List<VideoPlayerApp> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://media/external/video/media/1"), "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val manager = context.packageManager
        val flags = PackageManager.MATCH_DEFAULT_ONLY
        return manager.queryIntentActivities(intent, flags)
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName) return@mapNotNull null
                VideoPlayerApp(
                    packageName = packageName,
                    label = info.loadLabel(manager)?.toString()?.takeIf { it.isNotBlank() } ?: packageName
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun openVideo(context: Context, uri: Uri) {
        val preferred = getPreferredPackage(context)
        val baseIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (preferred != null) {
            val preferredIntent = Intent(baseIntent).setPackage(preferred)
            try {
                context.startActivity(preferredIntent)
                return
            } catch (_: ActivityNotFoundException) {
                setPreferredPackage(context, null)
            }
        }

        try {
            context.startActivity(Intent.createChooser(baseIntent, "동영상 재생 앱 선택"))
        } catch (_: ActivityNotFoundException) {
            // 재생 가능한 앱이 없는 경우 호출 측 UI가 그대로 유지됩니다.
        }
    }
}
