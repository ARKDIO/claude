package com.arkdio.ytdownloader

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Mode { VIDEO, AUDIO }

/**
 * Video quality choices. Every option downloads the original YouTube stream
 * (no re-encoding) — lower choices simply pick a smaller stream from the start.
 *
 * @param maxRes  upper bound on resolution (shorter side), null = no limit
 * @param compat  prefer H.264 (avc1) so it plays on old phones / PCs
 */
enum class Quality(val label: String, val maxRes: Int?, val compat: Boolean = false) {
    BEST("최고화질", null),
    UHD_4K("4K", 2160),
    QHD("1440p", 1440),
    FHD("1080p", 1080),
    HD("720p", 720),
    COMPAT("호환 모드", 1080, compat = true),
}

sealed interface DownloadStatus {
    data object Idle : DownloadStatus

    data class Running(
        val mode: Mode,
        val stage: String,
        /** 0..100, or null when the stage has no measurable progress. */
        val percent: Float?,
        val etaSeconds: Long,
        val title: String?,
    ) : DownloadStatus

    data class Done(
        val title: String,
        val uri: Uri,
        val mime: String,
        val location: String,
    ) : DownloadStatus

    data class Failed(val message: String) : DownloadStatus
}

/** Shared state between the download service and the UI. */
object DownloadCenter {
    private val _status = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val status: StateFlow<DownloadStatus> = _status.asStateFlow()

    val isBusy: Boolean get() = _status.value is DownloadStatus.Running

    internal fun set(status: DownloadStatus) {
        _status.value = status
    }

    fun clear() {
        if (!isBusy) _status.value = DownloadStatus.Idle
    }
}
