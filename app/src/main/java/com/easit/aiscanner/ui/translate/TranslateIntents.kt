package com.easit.aiscanner.ui.translate

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.easit.aiscanner.R
import com.easit.aiscanner.data.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Clipboard, share sheet and MediaStore helpers used by the Translate screens. */
internal object TranslateIntents {
    private const val TAG = "TranslateIntents"
    private const val AUDIO_MIME_TYPE = "audio/wav"

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /** The clipboard's text, or null when it holds none. */
    fun readClipboardText(context: Context): String? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startChooser(context, intent, context.getString(R.string.translate_share_chooser))
    }

    /** Shares a speech file from `filesDir` through the app's FileProvider. */
    fun shareAudio(context: Context, file: File): Boolean {
        if (!file.isFile) return false
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Cannot share ${file.name}", e)
            return false
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = AUDIO_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return startChooser(context, intent, context.getString(R.string.share_audio_chooser))
    }

    /**
     * Copies a speech file into Music/TransVerse.
     *
     * @return the message to show the user
     */
    suspend fun saveAudioToMusic(context: Context, source: File): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return R.string.audio_download_requires_android_10
        }
        val saved = withContext(Dispatchers.IO) {
            source.isFile && copyToMediaStore(context, source)
        }
        return when {
            saved -> R.string.audio_downloaded
            !source.exists() -> R.string.no_audio_available
            else -> R.string.audio_download_failed
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun copyToMediaStore(context: Context, source: File): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
            put(MediaStore.MediaColumns.MIME_TYPE, AUDIO_MIME_TYPE)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_MUSIC}/${Constants.APP_NAME}"
            )
        }
        val destination = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        return try {
            resolver.openOutputStream(destination)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } != null
        } catch (e: IOException) {
            Log.w(TAG, "Could not save ${source.name}", e)
            resolver.delete(destination, null, null)
            false
        }
    }

    private fun startChooser(context: Context, intent: Intent, title: String): Boolean = try {
        context.startActivity(Intent.createChooser(intent, title))
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No app can handle ${intent.action}", e)
        false
    }
}
