package com.duplicatevideo.remover

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val AppBackground = Color(0xFFF8F9FC)
internal val AppSurface = Color(0xFFFFFFFF)
internal val AppBlue = Color(0xFF2F6FED)
internal val AppNavy = Color(0xFF182339)
internal val AppPink = Color(0xFFFF2D62)
internal val SubtleText = Color(0xFF6D7584)
internal val Border = Color(0xFFE8EBF1)

internal enum class Screen {
    HOME,
    SCANNING,
    RESULTS,
    GROUP,
    SETTINGS,
    PLAYER_PICKER,
    DONE
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = AppBlue,
                    secondary = Color(0xFF7658F6),
                    tertiary = AppPink,
                    background = AppBackground,
                    surface = AppSurface,
                    onSurface = AppNavy
                )
            ) {
                DuplicateVideoApp()
            }
        }
    }
}

@Composable
internal fun DuplicateVideoApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resultsListState = rememberLazyListState()

    var screen by remember { mutableStateOf(Screen.HOME) }
    var criterion by remember { mutableStateOf(MatchCriterion.RECOMMENDED) }
    var progress by remember { mutableStateOf(ScanProgress(0f, "준비 중", 0, 0)) }
    var groups by remember { mutableStateOf<List<VideoGroup>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<VideoGroup?>(null) }
    var selectedUris by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var pendingCriterion by remember { mutableStateOf<MatchCriterion?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteItems by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var deletedCount by remember { mutableStateOf(0) }
    var deletedBytes by remember { mutableStateOf(0L) }

    fun beginScan(selected: MatchCriterion) {
        scanJob?.cancel()
        criterion = selected
        progress = ScanProgress(0f, "검색 준비 중", 0, 0)
        groups = emptyList()
        selectedGroup = null
        selectedUris = emptySet()
        screen = Screen.SCANNING
        scanJob = scope.launch {
            runCatching {
                VideoScanner.scan(context, selected) { update ->
                    withContext(Dispatchers.Main) { progress = update }
                }
            }.onSuccess { result ->
                groups = result
                screen = Screen.RESULTS
            }.onFailure {
                groups = emptyList()
                screen = Screen.RESULTS
            }
        }
    }

    fun leaveGroup() {
        selectedUris = emptySet()
        selectedGroup = null
        screen = Screen.RESULTS
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val requested = pendingCriterion
        pendingCriterion = null
        if (granted && requested != null) {
            beginScan(requested)
        } else {
            showPermissionDialog = true
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val deleted = pendingDeleteItems
            val deletedSet = deleted.map { it.uri }.toSet()
            deletedCount = deleted.size
            deletedBytes = deleted.sumOf { it.sizeBytes }
            selectedUris = emptySet()
            groups = groups.mapNotNull { group ->
                val remaining = group.items.filterNot { it.uri in deletedSet }
                if (remaining.size > 1) group.copy(items = remaining) else null
            }
            selectedGroup = null
            screen = Screen.DONE
        }
        pendingDeleteItems = emptyList()
    }

    fun launchDeleteRequest(items: List<VideoItem>) {
        if (items.isEmpty()) return
        runCatching {
            pendingDeleteItems = items
            val request = MediaStore.createDeleteRequest(
                context.contentResolver,
                items.map { it.uri }
            )
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }.onFailure {
            pendingDeleteItems = emptyList()
        }
    }

    val manageMediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val items = pendingDeleteItems
        if (items.isNotEmpty()) {
            // Android 12+에서 미디어 관리 권한을 허용했다면 이후 삭제 요청은
            // 시스템의 파일별 승인창 없이 처리됩니다. 허용하지 않았더라도
            // 기존 방식의 삭제 확인창으로 계속 진행할 수 있습니다.
            launchDeleteRequest(items)
        }
    }

    fun askToScan(selected: MatchCriterion) {
        if (hasVideoPermission(context)) {
            beginScan(selected)
        } else {
            pendingCriterion = selected
            permissionLauncher.launch(requiredVideoPermission())
        }
    }

    fun requestPermanentDeletion(items: List<VideoItem>) {
        if (items.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !MediaStore.canManageMedia(context)) {
            pendingDeleteItems = items
            val intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            runCatching {
                manageMediaLauncher.launch(intent)
            }.onFailure {
                launchDeleteRequest(items)
            }
        } else {
            launchDeleteRequest(items)
        }
    }

    val selectedItems = remember(groups, selectedUris) {
        groups.flatMap { it.items }
            .filter { it.uri in selectedUris }
            .distinctBy { it.uri }
    }

    BackHandler(enabled = screen != Screen.HOME) {
        when (screen) {
            Screen.GROUP -> leaveGroup()
            Screen.RESULTS -> screen = Screen.HOME
            Screen.SCANNING -> {
                scanJob?.cancel()
                screen = Screen.HOME
            }
            Screen.SETTINGS -> screen = Screen.HOME
            Screen.PLAYER_PICKER -> screen = Screen.SETTINGS
            Screen.DONE -> screen = Screen.RESULTS
            Screen.HOME -> Unit
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = AppBackground) {
        when (screen) {
            Screen.HOME -> HomeScreen(
                onCriterion = ::askToScan,
                onSettings = { screen = Screen.SETTINGS }
            )

            Screen.SCANNING -> ScanScreen(
                criterion = criterion,
                progress = progress,
                onCancel = {
                    scanJob?.cancel()
                    screen = Screen.HOME
                }
            )

            Screen.RESULTS -> ResultsScreen(
                listState = resultsListState,
                criterion = criterion,
                groups = groups,
                selectedCount = selectedItems.size,
                selectedBytes = selectedItems.sumOf { it.sizeBytes },
                onBack = { screen = Screen.HOME },
                onOpenGroup = {
                    // 선택 상태는 그룹 내부에서만 유지합니다.
                    selectedUris = emptySet()
                    selectedGroup = it
                    screen = Screen.GROUP
                },
                onDeleteSelected = { showDeleteConfirm = true },
                onSettings = { screen = Screen.SETTINGS }
            )

            Screen.GROUP -> {
                val group = selectedGroup
                if (group == null) {
                    selectedUris = emptySet()
                    screen = Screen.RESULTS
                } else {
                    GroupDetailScreen(
                        group = group,
                        selectedUris = selectedUris,
                        onBack = ::leaveGroup,
                        onToggle = { uri ->
                            selectedUris = if (uri in selectedUris) {
                                selectedUris - uri
                            } else {
                                selectedUris + uri
                            }
                        },
                        onSelectAll = { shouldSelect ->
                            val groupUris = group.items.map { it.uri }.toSet()
                            selectedUris = if (shouldSelect) {
                                selectedUris + groupUris
                            } else {
                                selectedUris - groupUris
                            }
                        },
                        onPlay = { VideoPlayerPrefs.openVideo(context, it.uri) },
                        onDeleteSelected = { showDeleteConfirm = true }
                    )
                }
            }

            Screen.SETTINGS -> SettingsScreen(
                onBack = { screen = Screen.HOME },
                onPlayerSettings = { screen = Screen.PLAYER_PICKER }
            )

            Screen.PLAYER_PICKER -> PlayerPickerScreen(
                onBack = { screen = Screen.SETTINGS }
            )

            Screen.DONE -> DeleteDoneScreen(
                count = deletedCount,
                bytes = deletedBytes,
                onDone = { screen = Screen.HOME },
                onSeeResults = { screen = Screen.RESULTS }
            )
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            items = selectedItems,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                requestPermanentDeletion(selectedItems)
            }
        )
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("동영상 접근 권한이 필요합니다") },
            text = {
                Text("기기 전체의 동영상 중 중복을 찾으려면 동영상 읽기 권한이 필요합니다. 권한이 제한되면 선택된 미디어만 검색될 수 있습니다.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }) { Text("앱 권한 설정") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("취소") }
            }
        )
    }
}

internal fun hasVideoPermission(context: Context): Boolean {
    val permission = requiredVideoPermission()
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

internal fun requiredVideoPermission(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
