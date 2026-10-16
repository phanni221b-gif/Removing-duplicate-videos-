package com.duplicatevideo.remover

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HomeScreen(
    onCriterion: (MatchCriterion) -> Unit,
    onSettings: () -> Unit
) {
    val cards = listOf(
        Triple(MatchCriterion.DURATION, Icons.Outlined.Schedule, Color(0xFFEAF2FF)),
        Triple(MatchCriterion.SIZE, Icons.Outlined.Description, Color(0xFFEAF9EF)),
        Triple(MatchCriterion.SIMILAR_NAME, Icons.Outlined.TextFields, Color(0xFFFFF3DC)),
        Triple(MatchCriterion.THUMBNAIL, Icons.Outlined.Image, Color(0xFFF2ECFF)),
        Triple(MatchCriterion.HASH, Icons.Outlined.Tag, Color(0xFFE9FBF8)),
        Triple(MatchCriterion.RECOMMENDED, Icons.Outlined.AutoAwesome, Color(0xFFFFEAF0))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("중복동영상제거", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = AppNavy)
                    Text("같은 영상은 하나만 남기세요", fontSize = 13.sp, color = SubtleText)
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "설정", tint = AppNavy)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("어떤 기준으로 중복 영상을 찾을까요?", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(cards) { (criterion, icon, background) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = background),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth().height(164.dp).clickable { onCriterion(criterion) }
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null, tint = AppNavy, modifier = Modifier.size(31.dp))
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SubtleText)
                        }
                        Column {
                            Text(criterion.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppNavy)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                criterion.description,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = SubtleText,
                                maxLines = 3
                            )
                        }
                    }
                }
            }
        }

        Text(
            "동영상 형식은 따로 고르지 않습니다. Android 미디어 저장소에 등록된 모든 영상 형식을 자동으로 확인합니다.",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = SubtleText,
            modifier = Modifier.padding(vertical = 14.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScanScreen(
    criterion: MatchCriterion,
    progress: ScanProgress,
    onCancel: () -> Unit
) {
    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("중복 동영상 검색 중") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Outlined.ArrowBack, contentDescription = "뒤로") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(44.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.size(150.dp),
                    strokeWidth = 10.dp,
                    color = AppBlue,
                    trackColor = Color(0xFFE3E8F2)
                )
                Text("${(progress.fraction * 100).toInt()}%", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(30.dp))
            Text("${criterion.title} 기준으로 분석하고 있습니다.", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(progress.stage, color = SubtleText)
            Spacer(Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    LinearProgressIndicator(
                        progress = { progress.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = AppBlue,
                        trackColor = Color(0xFFE6EAF1)
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("분석 진행", color = SubtleText)
                        Text(if (progress.total > 0) "${progress.processed} / ${progress.total}" else "준비 중", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("영상 수가 많거나 해시·썸네일 비교를 선택하면 시간이 더 걸릴 수 있습니다.", fontSize = 12.sp, color = SubtleText)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE9ECF2), contentColor = AppNavy),
                shape = RoundedCornerShape(18.dp)
            ) { Text("검색 취소") }
        }
    }
}
