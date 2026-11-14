package com.arkdio.ytdownloader

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var cancelled = false
    private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must enter the foreground quickly after startForegroundService().
        enterForeground(progressNotification("준비 중이에요…", null))

        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelled = true
                YoutubeDL.getInstance().destroyProcessById(PROCESS_ID)
                job?.cancel()
                if (job == null) finish()
            }

            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL)
                val mode = Mode.entries[intent.getIntExtra(EXTRA_MODE, 0)]
                val quality = Quality.entries[intent.getIntExtra(EXTRA_QUALITY, 0)]
                if (url != null && job?.isActive != true) {
                    cancelled = false
                    job = scope.launch {
                        try {
                            runDownload(url, mode, quality)
                        } finally {
                            job = null
                            finish()
                        }
                    }
                }
            }

            else -> if (job == null) finish()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun runDownload(url: String, mode: Mode, quality: Quality) {
        var title: String? = null
        var part = 0
        fun running(stage: String, percent: Float?, eta: Long = -1) {
            val s = DownloadStatus.Running(mode, stage, percent, eta, title)
            DownloadCenter.set(s)
            notifyProgress(s)
        }

        running("엔진을 준비하는 중이에요…", null)
        if (!Engine.awaitReady()) {
            val err = (Engine.state.value as? EngineState.Failed)?.message ?: "알 수 없는 오류"
            fail("다운로드 엔진을 시작하지 못했어요: $err")
            return
        }

        val workDir = File(cacheDir, "downloads").apply {
            deleteRecursively()
            mkdirs()
        }

        try {
            running("영상 정보를 가져오는 중이에요…", null)
            val request = buildRequest(url, mode, quality, workDir)

            YoutubeDL.getInstance().execute(request, PROCESS_ID) { progress, eta, line ->
                when {
                    line.contains("Destination:") && line.startsWith("[download]") -> {
                        part++
                        val name = File(line.substringAfter("Destination:").trim()).name
                        title = name.substringBeforeLast(ID_SEPARATOR, name).ifBlank { title }
                        running(stageLabel(mode, part), 0f, -1)
                    }

                    line.startsWith("[Merger]") -> running("영상과 소리를 합치는 중이에요…", null)
                    line.startsWith("[ExtractAudio]") -> running("오디오를 정리하는 중이에요…", null)
                    line.startsWith("[download]") && progress >= 0f ->
                        running(stageLabel(mode, part), progress.coerceIn(0f, 100f), eta)
                }
            }

            if (cancelled) throw CancellationException()

            val output = workDir.listFiles()
                ?.filter { it.isFile && it.length() > 0 && !isTempFile(it.name) }
                ?.maxByOrNull { it.length() }
                ?: throw IllegalStateException("다운로드된 파일을 찾지 못했어요")

            val cleanTitle = output.name.substringBeforeLast(ID_SEPARATOR, output.nameWithoutExtension)
            title = cleanTitle
            running("저장하는 중이에요…", null)
            val saved = MediaSaver.save(
                context = this,
                source = output,
                displayName = "$cleanTitle.${output.extension}",
                isVideo = mode == Mode.VIDEO,
            )
            val done = DownloadStatus.Done(cleanTitle, saved.uri, saved.mime, saved.location)
            DownloadCenter.set(done)
            notifyDone(done)
        } catch (e: Throwable) {
            if (cancelled || e is CancellationException || e is YoutubeDL.CanceledException) {
                DownloadCenter.set(DownloadStatus.Failed("다운로드를 취소했어요"))
            } else {
                fail(friendlyError(e))
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun buildRequest(url: String, mode: Mode, quality: Quality, workDir: File): YoutubeDLRequest {
        val template = "${workDir.absolutePath}/%(title).150B$ID_SEPARATOR%(id)s.%(ext)s"
        return YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--newline")
            addOption("-o", template)
            when (mode) {
                Mode.VIDEO -> {
                    // Best video stream (within the chosen limit) + best audio, merged into
                    // MP4 by stream copy — no re-encoding, so no quality loss.
                    // AAC audio is preferred so the file plays everywhere, including the Gallery.
                    if (quality.compat) {
                        // H.264 video plays on practically every device.
                        addOption("-f", "bv*[vcodec^=avc]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b")
                    } else {
                        addOption("-f", "bv*+ba[ext=m4a]/bv*+ba/b")
                    }
                    // "res:N" = prefer the largest resolution not above N (works for
                    // vertical Shorts too, since it compares the shorter side).
                    quality.maxRes?.let { addOption("-S", "res:$it") }
                    addOption("--merge-output-format", "mp4")
                }

                Mode.AUDIO -> {
                    // Best audio stream, kept without re-encoding when possible.
                    addOption("-f", "ba[ext=m4a]/ba/b")
                    addOption("-x")
                    addOption("--audio-format", "m4a")
                    addOption("--audio-quality", "0")
                }
            }
        }
    }

    private fun stageLabel(mode: Mode, part: Int): String = when (mode) {
        Mode.AUDIO -> "오디오를 받는 중이에요"
        Mode.VIDEO -> when (part) {
            0, 1 -> "영상을 받는 중이에요 (1/2)"
            else -> "소리를 받는 중이에요 (2/2)"
        }
    }

    private fun isTempFile(name: String) =
        name.endsWith(".part") || name.endsWith(".ytdl") || name.contains(".temp.") ||
            Regex("""\.f\d[\w-]*\.\w+$""").containsMatchIn(name)

    private fun fail(message: String) {
        DownloadCenter.set(DownloadStatus.Failed(message))
        val n = NotificationCompat.Builder(this, App.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("다운로드 실패")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        notificationManager().notify(NOTIFICATION_RESULT, n)
    }

    private fun friendlyError(e: Throwable): String {
        val raw = e.message ?: e.toString()
        val errorLine = raw.lines().lastOrNull { it.contains("ERROR") }?.substringAfter("ERROR:")?.trim()
        val msg = errorLine ?: raw.lines().lastOrNull { it.isNotBlank() } ?: raw
        return when {
            msg.contains("Unsupported URL", true) -> "지원하지 않는 주소예요. 유튜브 링크를 확인해 주세요"
            msg.contains("Sign in to confirm", true) ->
                "유튜브가 로그인 확인을 요구하고 있어요. 잠시 후 다시 시도하거나 '엔진 업데이트'를 눌러 주세요"
            msg.contains("Private video", true) -> "비공개 영상이라 받을 수 없어요"
            msg.contains("Unable to resolve host", true) || msg.contains("Network", true) ->
                "인터넷 연결을 확인해 주세요"
            else -> msg.take(300)
        }
    }

    // ---------- notifications ----------

    private fun enterForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_PROGRESS, notification, type)
    }

    private fun notifyProgress(s: DownloadStatus.Running) {
        val now = System.currentTimeMillis()
        if (s.percent != null && now - lastNotify < 700) return
        lastNotify = now
        val text = buildString {
            append(s.stage)
            s.percent?.let { append(" · ${it.toInt()}%") }
        }
        notificationManager().notify(NOTIFICATION_PROGRESS, progressNotification(text, s))
    }

    private fun progressNotification(text: String, s: DownloadStatus.Running?): Notification {
        val cancel = PendingIntent.getService(
            this, 1,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, App.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(s?.title ?: "유튜브 다운로드")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, s?.percent?.toInt() ?: 0, s?.percent == null)
            .setContentIntent(openAppIntent())
            .addAction(0, "취소", cancel)
            .build()
    }

    private fun notifyDone(d: DownloadStatus.Done) {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(d.uri, d.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            this, 2, view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, App.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("다운로드 완료 🎉")
            .setContentText("${d.title} → ${d.location}")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        notificationManager().notify(NOTIFICATION_RESULT, n)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    companion object {
        private const val ACTION_START = "com.arkdio.ytdownloader.START"
        private const val ACTION_CANCEL = "com.arkdio.ytdownloader.CANCEL"
        private const val EXTRA_URL = "url"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_QUALITY = "quality"
        private const val PROCESS_ID = "yt-download"
        private const val ID_SEPARATOR = " ~"
        private const val NOTIFICATION_PROGRESS = 1
        private const val NOTIFICATION_RESULT = 2

        fun start(context: Context, url: String, mode: Mode, quality: Quality) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_MODE, mode.ordinal)
                .putExtra(EXTRA_QUALITY, quality.ordinal)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
