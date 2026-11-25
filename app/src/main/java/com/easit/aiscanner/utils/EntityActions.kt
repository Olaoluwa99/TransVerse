package com.easit.aiscanner.utils

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Patterns

/** Turns extracted entities into useful, privacy-preserving Android actions. */
object EntityActions {

    enum class Kind { EMAIL, PHONE, LINK, OTHER }

    enum class Outcome { OPENED, COPIED }

    /** What [rawValue] looks like, checked in the same order [openOrCopy] acts on it. */
    fun kindOf(rawValue: String): Kind {
        val value = rawValue.trim()
        return when {
            Patterns.EMAIL_ADDRESS.matcher(value).matches() -> Kind.EMAIL
            Patterns.PHONE.matcher(value).matches() -> Kind.PHONE
            Patterns.WEB_URL.matcher(value).matches() -> Kind.LINK
            else -> Kind.OTHER
        }
    }

    /**
     * Opens an email, phone or link entity in the matching app, or copies it to the clipboard
     * when there is no such app or the entity is something else. The caller confirms a copy.
     */
    fun openOrCopy(context: Context, rawValue: String): Outcome {
        val value = rawValue.trim()
        val intent = when (kindOf(value)) {
            Kind.EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(value)}"))
            Kind.PHONE -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(value)}"))
            Kind.LINK -> {
                val url = if (value.startsWith("http://") || value.startsWith("https://")) {
                    value
                } else {
                    "https://$value"
                }
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            }
            Kind.OTHER -> null
        }

        if (intent != null) {
            try {
                context.startActivity(intent)
                return Outcome.OPENED
            } catch (_: ActivityNotFoundException) {
                // Fall through to copying.
            }
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("TransVerse entity", value))
        return Outcome.COPIED
    }
}
