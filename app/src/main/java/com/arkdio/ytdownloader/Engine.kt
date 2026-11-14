package com.arkdio.ytdownloader

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface EngineState {
    data object Initializing : EngineState
    data class Ready(val version: String) : EngineState
    data class Failed(val message: String) : EngineState
}

/** Wraps yt-dlp (python) + ffmpeg bundled by youtubedl-android. */
object Engine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<EngineState>(EngineState.Initializing)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    fun init(context: Context) {
        val app = context.applicationContext
        scope.launch {
            _state.value = try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                EngineState.Ready(YoutubeDL.getInstance().version(app) ?: "알 수 없음")
            } catch (e: Exception) {
                EngineState.Failed(e.message ?: e.toString())
            }
        }
    }

    /** Suspends until init finished. Returns true when the engine is usable. */
    suspend fun awaitReady(): Boolean =
        state.first { it !is EngineState.Initializing } is EngineState.Ready

    /** Updates yt-dlp to the latest stable release. Returns a message for the user. */
    suspend fun update(context: Context): String = withContext(Dispatchers.IO) {
        if (!awaitReady()) return@withContext "엔진이 준비되지 않았어요"
        val app = context.applicationContext
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
            val version = YoutubeDL.getInstance().version(app) ?: "알 수 없음"
            _state.value = EngineState.Ready(version)
            when (status) {
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "이미 최신 버전이에요 ($version)"
                else -> "엔진을 업데이트했어요 ($version)"
            }
        } catch (e: Exception) {
            "업데이트 실패: ${e.message ?: e}"
        }
    }
}
