package com.arkdio.ytdownloader

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Mode { VIDEO, AUDIO }

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
