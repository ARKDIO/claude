package com.arkdio.ytdownloader

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

data class SavedFile(val uri: Uri, val mime: String, val location: String)

/**
 * Copies a finished download into public storage:
 *  - video → Movies/YTDownloader  (shows up in the Gallery)
 *  - audio → Download/YTDownloader (shows up in My Files)
 */
object MediaSaver {
    private const val FOLDER = "YTDownloader"

    fun save(context: Context, source: File, displayName: String, isVideo: Boolean): SavedFile {
        val mime = mimeOf(source.extension, isVideo)
        val baseDir = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_DOWNLOADS
        val location = "$baseDir/$FOLDER"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(context, source, displayName, mime, isVideo, location)
        } else {
            saveLegacy(context, source, displayName, mime, baseDir, location)
        }
    }

    private fun saveScoped(
        context: Context, source: File, displayName: String,
        mime: String, isVideo: Boolean, location: String,
    ): SavedFile {
        val resolver = context.contentResolver
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, location)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("저장 공간에 파일을 만들 수 없어요")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("파일을 열 수 없어요")
            out.use { o -> source.inputStream().use { it.copyTo(o, 1 shl 16) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return SavedFile(uri, mime, location)
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(
        context: Context, source: File, displayName: String,
        mime: String, baseDir: String, location: String,
    ): SavedFile {
        val dir = File(Environment.getExternalStoragePublicDirectory(baseDir), FOLDER)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("폴더를 만들 수 없어요: $dir")
        var dest = File(dir, displayName)
        var n = 1
        while (dest.exists()) {
            dest = File(dir, "${displayName.substringBeforeLast('.')} ($n).${displayName.substringAfterLast('.')}")
            n++
        }
        source.copyTo(dest)
        MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime), null)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", dest)
        return SavedFile(uri, mime, location)
    }

    private fun mimeOf(ext: String, isVideo: Boolean): String = when (ext.lowercase()) {
        "mp4" -> if (isVideo) "video/mp4" else "audio/mp4"
        "m4a" -> "audio/mp4"
        "webm" -> if (isVideo) "video/webm" else "audio/webm"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "opus", "ogg" -> "audio/ogg"
        else -> if (isVideo) "video/mp4" else "audio/mpeg"
    }
}
