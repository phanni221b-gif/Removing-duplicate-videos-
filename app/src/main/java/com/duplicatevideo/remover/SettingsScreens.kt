package com.duplicatevideo.remover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onPlayerSettings: () -> Unit
) {
    val context = LocalContext.current
    val preferred = VideoPlayerPrefs.getPreferredPackage(context)
    val installed = remember { VideoPlayerPrefs.installedPlayers(context) }
    val currentLabel = installed.firstOrNull { it.packageName == preferred }?.label ?: "매번 선택"

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("재생 설정", color = SubtleText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            SettingsRow(title = "동영상 재생 앱", subtitle = currentLabel, onClick = onPlayerSettings)
            Spacer(Modifier.height(24.dp))
            Text("탐색 방식", color = SubtleText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("모든 영상 형식 자동 확인", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "별도 확장자 선택 없이 Android 미디어 저장소가 영상으로 인식한 파일을 전체 검색합니다.",
                        color = SubtleText,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("비슷한 이름 기준", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "확장자를 제외한 파일명에서 연속된 6글자가 하나라도 같으면 같은 그룹 후보로 봅니다.",
                        color = SubtleText,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PlayCircleOutline, null, tint = AppBlue)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = SubtleText, fontSize = 12.sp)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = SubtleText)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerPickerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedPackage by remember { mutableStateOf(VideoPlayerPrefs.getPreferredPackage(context)) }
    val players = remember { VideoPlayerPrefs.installedPlayers(context) }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("연결할 비디오 플레이어") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            item {
                Text(
                    "그룹에서 영상을 탭했을 때 사용할 앱을 선택합니다. 언제든 여기서 바꿀 수 있습니다.",
                    color = SubtleText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                PlayerRow(
                    title = "매번 선택",
                    subtitle = "재생할 때 Android 연결 앱 선택창을 표시",
                    selected = selectedPackage == null,
                    onClick = {
                        selectedPackage = null
                        VideoPlayerPrefs.setPreferredPackage(context, null)
                    }
                )
            }
            items(players, key = { it.packageName }) { player ->
                PlayerRow(
                    title = player.label,
                    subtitle = player.packageName,
                    selected = selectedPackage == player.packageName,
                    onClick = {
                        selectedPackage = player.packageName
                        VideoPlayerPrefs.setPreferredPackage(context, player.packageName)
                    }
                )
            }
            if (players.isEmpty()) {
                item {
                    Text("설치된 비디오 재생 앱을 찾지 못했습니다.", color = SubtleText, modifier = Modifier.padding(top = 24.dp))
                }
            }
        }
    }
}

@Composable
internal fun PlayerRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAF2FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.PlayCircleOutline, null, tint = AppBlue)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = SubtleText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}
