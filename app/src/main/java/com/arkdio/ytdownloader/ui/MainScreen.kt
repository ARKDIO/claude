package com.arkdio.ytdownloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkdio.ytdownloader.DownloadStatus
import com.arkdio.ytdownloader.EngineState
import com.arkdio.ytdownloader.MainViewModel
import com.arkdio.ytdownloader.Mode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onDownload: (Mode) -> Unit,
    onOpen: (DownloadStatus.Done) -> Unit,
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val engine by viewModel.engine.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val busy = status is DownloadStatus.Running

    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("YT 다운로더", fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = viewModel.url,
                onValueChange = { viewModel.url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("유튜브 주소") },
                placeholder = { Text("https://youtu.be/…") },
                singleLine = true,
                enabled = !busy,
                trailingIcon = {
                    if (viewModel.url.isNotEmpty() && !busy) {
                        IconButton(onClick = { viewModel.url = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "지우기")
                        }
                    }
                },
            )

            OutlinedButton(
                onClick = { viewModel.url = clipboard.getText()?.text.orEmpty() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("📋 클립보드에서 붙여넣기") }

            Button(
                onClick = { onDownload(Mode.VIDEO) },
                enabled = !busy && engine is EngineState.Ready,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) { Text("🎬 영상 다운로드 (최고화질)", style = MaterialTheme.typography.titleMedium) }

            FilledTonalButton(
                onClick = { onDownload(Mode.AUDIO) },
                enabled = !busy && engine is EngineState.Ready,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("🎵 소리만 다운로드") }

            StatusCard(
                status = status,
                onCancel = viewModel::cancel,
                onOpen = onOpen,
                onClear = viewModel::clearResult,
            )

            Spacer(Modifier.height(8.dp))
            EngineRow(engine, viewModel.updating, enabled = !busy, onUpdate = viewModel::updateEngine)

            Text(
                "• 영상은 갤러리(Movies/YTDownloader)에 저장돼요\n" +
                    "• 소리는 내 파일(Download/YTDownloader)에 저장돼요\n" +
                    "• 유튜브 앱에서 공유 → YT 다운로더 를 눌러도 주소가 자동으로 들어와요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusCard(
    status: DownloadStatus,
    onCancel: () -> Unit,
    onOpen: (DownloadStatus.Done) -> Unit,
    onClear: () -> Unit,
) {
    when (status) {
        DownloadStatus.Idle -> Unit

        is DownloadStatus.Running -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                status.title?.let {
                    Text(it, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(status.stage, style = MaterialTheme.typography.bodyMedium)
                if (status.percent != null) {
                    LinearProgressIndicator(
                        progress = { status.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        buildString {
                            append("${"%.1f".format(status.percent)}%")
                            if (status.etaSeconds > 0) append(" · 남은 시간 ${formatEta(status.etaSeconds)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("취소") }
            }
        }

        is DownloadStatus.Done -> Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("다운로드 완료! 🎉", fontWeight = FontWeight.Bold)
                Text(status.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("저장 위치: ${status.location}", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.align(Alignment.End)) {
                    TextButton(onClick = onClear) { Text("닫기") }
                    Button(onClick = { onOpen(status) }) { Text("열기") }
                }
            }
        }

        is DownloadStatus.Failed -> Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("앗, 문제가 생겼어요 😢", fontWeight = FontWeight.Bold)
                Text(status.message, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onClear, modifier = Modifier.align(Alignment.End)) { Text("닫기") }
            }
        }
    }
}

@Composable
private fun EngineRow(engine: EngineState, updating: Boolean, enabled: Boolean, onUpdate: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = when (engine) {
                EngineState.Initializing -> "엔진 준비 중… (처음 실행은 조금 걸려요)"
                is EngineState.Ready -> "엔진 버전: ${engine.version}"
                is EngineState.Failed -> "엔진 오류: ${engine.message}"
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        if (updating || engine is EngineState.Initializing) {
            CircularProgressIndicator(Modifier.padding(8.dp).size(20.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onUpdate, enabled = enabled && engine is EngineState.Ready) {
                Text("엔진 업데이트")
            }
        }
    }
}

private fun formatEta(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}분 ${s}초" else "${s}초"
}
