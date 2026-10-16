package com.duplicatevideo.remover

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

@Composable
internal fun DeleteDoneScreen(
    count: Int,
    bytes: Long,
    onDone: () -> Unit,
    onSeeResults: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier.size(108.dp).clip(CircleShape).background(Color(0xFFE7F7EA)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF168D43), modifier = Modifier.size(62.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("파일이 삭제되었습니다", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("$count개의 파일 · ${formatBytes(bytes)} 공간 확보", color = SubtleText)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onSeeResults,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp)
        ) { Text("남은 결과 보기") }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("처음 화면으로") }
    }
}

internal fun hasVideoPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, requiredVideoPermission()) == PackageManager.PERMISSION_GRANTED
}

internal fun requiredVideoPermission(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

internal fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (value >= 100 || value % 1.0 == 0.0) {
        String.format(Locale.getDefault(), "%.0f %s", value, units[index])
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[index])
    }
}

internal fun resolution(item: VideoItem): String {
    return if (item.width > 0 && item.height > 0) "${item.width}×${item.height}" else "해상도 정보 없음"
}
