package com.easit.aiscanner.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import android.widget.Toast
import com.easit.aiscanner.R

/** Turns extracted entities into useful, privacy-preserving Android actions. */
object EntityActions {
    fun openOrCopy(context: Context, rawValue: String) {
        val value = rawValue.trim()
        val intent = when {
            Patterns.EMAIL_ADDRESS.matcher(value).matches() ->
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(value)}"))
            Patterns.PHONE.matcher(value).matches() ->
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(value)}"))
            Patterns.WEB_URL.matcher(value).matches() -> {
                val url = if (value.startsWith("http://") || value.startsWith("https://")) {
                    value
                } else {
                    "https://$value"
                }
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            }
            else -> null
        }

        if (intent != null && intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("TransVerse entity", value))
            Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        }
    }
}
