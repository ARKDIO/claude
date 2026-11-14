package com.arkdio.ytdownloader

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val status = DownloadCenter.status
    val engine = Engine.state

    private val prefs = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    var url by mutableStateOf("")

    /** Chosen video quality, remembered between app launches. */
    var quality by mutableStateOf(
        Quality.entries.getOrNull(prefs.getInt(KEY_QUALITY, 0)) ?: Quality.BEST
    )
        private set

    fun selectQuality(q: Quality) {
        quality = q
        prefs.edit().putInt(KEY_QUALITY, q.ordinal).apply()
    }
    var message by mutableStateOf<String?>(null)
        private set
    var updating by mutableStateOf(false)
        private set

    fun onSharedText(text: String?) {
        extractUrl(text)?.let { url = it }
    }

    fun start(mode: Mode) {
        val link = extractUrl(url)
        when {
            link == null -> message = "유튜브 주소를 먼저 넣어 주세요"
            DownloadCenter.isBusy -> message = "이미 다운로드 중이에요. 끝나면 다시 눌러 주세요"
            else -> {
                url = link
                DownloadService.start(getApplication(), link, mode, quality)
            }
        }
    }

    fun cancel() = DownloadService.cancel(getApplication())

    fun clearResult() = DownloadCenter.clear()

    fun updateEngine() {
        if (updating) return
        updating = true
        viewModelScope.launch {
            message = Engine.update(getApplication())
            updating = false
        }
    }

    fun messageShown() {
        message = null
    }

    companion object {
        private const val KEY_QUALITY = "quality"
        private val URL_REGEX = Regex("""https?://\S+""")

        fun extractUrl(text: String?): String? =
            text?.let { URL_REGEX.find(it)?.value?.trimEnd('.', ',', ')', '"', '\'') }
    }
}
