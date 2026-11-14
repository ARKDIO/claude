package com.arkdio.ytdownloader.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkdio.ytdownloader.DownloadStatus
import com.arkdio.ytdownloader.EngineState
import com.arkdio.ytdownloader.MainViewModel
import com.arkdio.ytdownloader.Mode
import com.arkdio.ytdownloader.Quality

private val Radius = RoundedCornerShape(16.dp)

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onDownload: (Mode) -> Unit,
    onOpen: (DownloadStatus.Done) -> Unit,
) {
    val p = LocalPalette.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val engine by viewModel.engine.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val busy = status is DownloadStatus.Running
    val ready = engine is EngineState.Ready

    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        containerColor = p.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(p.bg)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TossButton(
                    text = "소리만",
                    container = p.fill,
                    content = p.body,
                    enabled = !busy && ready,
                    modifier = Modifier.weight(1f),
                    onClick = { onDownload(Mode.AUDIO) },
                )
                TossButton(
                    text = "영상 저장",
                    container = p.blue,
                    content = p.onBlue,
                    enabled = !busy && ready,
                    modifier = Modifier.weight(2f),
                    onClick = { onDownload(Mode.VIDEO) },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .animateContentSize(),
        ) {
            Spacer(Modifier.height(36.dp))
            Text(
                "유튜브 영상을\n저장할게요",
                color = p.title,
                fontSize = 26.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(28.dp))

            UrlField(
                value = viewModel.url,
                onValueChange = { viewModel.url = it },
                enabled = !busy,
            )

            Spacer(Modifier.height(28.dp))
            Text("화질", color = p.sub, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            QualityPicker(viewModel.quality, enabled = !busy, onSelect = viewModel::selectQuality)

            Spacer(Modifier.height(28.dp))
            StatusCard(status, onCancel = viewModel::cancel, onOpen = onOpen, onClear = viewModel::clearResult)

            Spacer(Modifier.height(24.dp))
            EngineLine(engine, viewModel.updating, enabled = !busy, onUpdate = viewModel::updateEngine)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun UrlField(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    val p = LocalPalette.current
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius)
            .background(p.fill)
            .padding(start = 18.dp, end = 8.dp)
            .height(58.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("유튜브 링크", color = p.sub, fontSize = 17.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(color = p.title, fontSize = 17.sp),
                cursorBrush = SolidColor(p.blue),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (enabled) {
            if (value.isEmpty()) {
                SmallAction("붙여넣기", p.blue) { onValueChange(clipboard.getText()?.text.orEmpty()) }
            } else {
                SmallAction("지우기", p.sub) { onValueChange("") }
            }
        }
    }
}

@Composable
private fun SmallAction(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        color = color,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun QualityPicker(selected: Quality, enabled: Boolean, onSelect: (Quality) -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Quality.entries.forEach { q ->
            val on = q == selected
            Text(
                q.label,
                color = if (on) p.blue else p.body,
                fontSize = 15.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) p.blueSoft else p.fill)
                    .clickable(enabled = enabled) { onSelect(q) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
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
    val p = LocalPalette.current
    val card = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(p.fill)
        .padding(20.dp)

    when (status) {
        DownloadStatus.Idle -> Unit

        is DownloadStatus.Running -> Column(card) {
            Text(
                status.title ?: "준비 중",
                color = p.title, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(status.stage, color = p.sub, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            if (status.percent != null) {
                LinearProgressIndicator(
                    progress = { status.percent / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = p.blue, trackColor = p.bg, strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = p.blue, trackColor = p.bg, strokeCap = StrokeCap.Round,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    status.percent?.let { "${it.toInt()}%" } ?: "",
                    color = p.blue, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                )
                if ((status.percent ?: 0f) > 0f && status.etaSeconds > 0) {
                    Text("  ${formatEta(status.etaSeconds)} 남음", color = p.sub, fontSize = 14.sp)
                }
                Spacer(Modifier.weight(1f))
                SmallAction("취소", p.sub, onCancel)
            }
        }

        is DownloadStatus.Done -> Column(card) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(p.blue),
                contentAlignment = Alignment.Center,
            ) { Text("✓", color = p.onBlue, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(14.dp))
            Text("저장했어요", color = p.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(status.title, color = p.body, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TossButton("닫기", p.bg, p.body, true, Modifier.weight(1f), height = 48.dp, onClick = onClear)
                TossButton("열기", p.blue, p.onBlue, true, Modifier.weight(1f), height = 48.dp) { onOpen(status) }
            }
        }

        is DownloadStatus.Failed -> Column(card) {
            Text("저장하지 못했어요", color = p.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(status.message, color = p.body, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                SmallAction("닫기", p.sub, onClear)
            }
        }
    }
}

@Composable
private fun EngineLine(engine: EngineState, updating: Boolean, enabled: Boolean, onUpdate: () -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            when (engine) {
                EngineState.Initializing -> "준비 중…"
                is EngineState.Ready -> "엔진 ${engine.version}"
                is EngineState.Failed -> "엔진 오류"
            },
            color = p.sub, fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        if (updating || engine is EngineState.Initializing) {
            CircularProgressIndicator(Modifier.size(16.dp), color = p.sub, strokeWidth = 2.dp)
        } else if (enabled && engine is EngineState.Ready) {
            Text(
                "업데이트",
                color = p.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onUpdate).padding(4.dp),
            )
        }
    }
}

@Composable
private fun TossButton(
    text: String,
    container: Color,
    content: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = Radius,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.4f),
            disabledContentColor = content.copy(alpha = 0.6f),
        ),
        elevation = null,
        modifier = modifier.height(height),
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatEta(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}분 ${s}초" else "${s}초"
}
