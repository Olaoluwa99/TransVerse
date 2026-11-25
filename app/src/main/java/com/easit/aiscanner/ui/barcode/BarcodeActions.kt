package com.easit.aiscanner.ui.barcode

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.PersistableBundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Patterns
import android.widget.Toast
import androidx.annotation.StringRes
import com.easit.aiscanner.R
import com.easit.aiscanner.utils.EntityActions

/** The main "do something with it" action for a code, if it has one. */
@StringRes
internal fun ScannedCode.primaryActionLabel(): Int? = when (val c = content) {
    is CodeContent.Url -> R.string.barcode_action_open_link
    is CodeContent.Email -> R.string.barcode_action_send_email
    is CodeContent.Phone -> R.string.barcode_action_call
    is CodeContent.Sms -> R.string.barcode_action_send_sms
    is CodeContent.Geo -> R.string.barcode_action_open_map
    is CodeContent.Contact -> R.string.barcode_action_add_contact
    is CodeContent.CalendarEvent -> R.string.barcode_action_add_event
    is CodeContent.Wifi -> R.string.barcode_action_join_wifi
    is CodeContent.Product, is CodeContent.Isbn -> R.string.barcode_action_search_web
    is CodeContent.Text -> if (c.text.looksActionable()) R.string.barcode_action_open else null
    CodeContent.DriverLicense -> null
}

/** Plain text that is really a link, email address or phone number. */
private fun String.looksActionable(): Boolean {
    val value = trim()
    return Patterns.WEB_URL.matcher(value).matches() ||
        Patterns.EMAIL_ADDRESS.matcher(value).matches() ||
        Patterns.PHONE.matcher(value).matches()
}

/** Runs the primary action; falls back to copying when no app can handle it. */
internal fun runPrimaryAction(context: Context, code: ScannedCode) {
    val content = code.content
    if (content is CodeContent.Text) {
        // Shared with the Translate screen's entity chips: opens links, mail and phone numbers.
        EntityActions.openOrCopy(context, content.text)
        return
    }
    val intent = when (content) {
        is CodeContent.Url -> Intent(Intent.ACTION_VIEW, Uri.parse(content.url.withScheme()))
        is CodeContent.Email -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(content.address))
            if (content.subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, content.subject)
            if (content.body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, content.body)
        }
        is CodeContent.Phone ->
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(content.number)}"))
        is CodeContent.Sms ->
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(content.number)}")).apply {
                if (content.message.isNotBlank()) putExtra("sms_body", content.message)
            }
        is CodeContent.Geo -> {
            val point = "${content.latitude},${content.longitude}"
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:$point?q=${Uri.encode(point)}"))
        }
        is CodeContent.Contact -> Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            if (content.name.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NAME, content.name)
            if (content.organization.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.COMPANY, content.organization)
            }
            if (content.jobTitle.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.JOB_TITLE, content.jobTitle)
            }
            content.phones.firstOrNull()?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
            content.emails.firstOrNull()?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
        }
        is CodeContent.CalendarEvent ->
            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
                if (content.summary.isNotBlank()) putExtra(CalendarContract.Events.TITLE, content.summary)
                if (content.description.isNotBlank()) {
                    putExtra(CalendarContract.Events.DESCRIPTION, content.description)
                }
                if (content.location.isNotBlank()) {
                    putExtra(CalendarContract.Events.EVENT_LOCATION, content.location)
                }
                content.startMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                content.endMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
            }
        is CodeContent.Wifi -> wifiIntent(content)
        is CodeContent.Product -> webSearchIntent(content.code)
        is CodeContent.Isbn -> webSearchIntent("ISBN ${content.code}")
        else -> null
    }

    if (intent == null || !context.tryStart(intent)) {
        copyToClipboard(context, code.copyText, sensitive = false, silent = true)
        Toast.makeText(context, R.string.barcode_no_app, Toast.LENGTH_SHORT).show()
    }
}

private fun String.withScheme(): String =
    if (Uri.parse(this).scheme.isNullOrBlank()) "https://$this" else this

private fun webSearchIntent(query: String): Intent =
    Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)

/**
 * On Android 11+ offers the network to the system's "Add network" sheet; otherwise (or for WEP,
 * which suggestions don't support) opens Wi-Fi settings so the user can join manually.
 */
private fun wifiIntent(wifi: CodeContent.Wifi): Intent {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifi.ssid.isNotBlank() &&
        wifi.security != WifiSecurity.Wep
    ) {
        val suggestion = runCatching {
            WifiNetworkSuggestion.Builder()
                .setSsid(wifi.ssid)
                .apply {
                    if (wifi.security == WifiSecurity.Wpa && wifi.password.isNotEmpty()) {
                        setWpa2Passphrase(wifi.password)
                    }
                }
                .build()
        }.getOrNull()
        if (suggestion != null) {
            return Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
        }
    }
    return Intent(Settings.ACTION_WIFI_SETTINGS)
}

private fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}

internal fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.tryStart(Intent.createChooser(send, context.getString(R.string.barcode_share_chooser)))
}

/**
 * Copies [text]. Android 13+ shows its own clipboard confirmation, so the toast is only shown
 * on older versions. [sensitive] hides the preview (e.g. Wi-Fi passwords).
 */
internal fun copyToClipboard(
    context: Context,
    text: String,
    sensitive: Boolean = false,
    silent: Boolean = false,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("TransVerse code", text)
    if (sensitive) {
        clip.description.extras = PersistableBundle().apply {
            val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ClipDescription.EXTRA_IS_SENSITIVE
            } else {
                "android.content.extra.IS_SENSITIVE"
            }
            putBoolean(key, true)
        }
    }
    clipboard.setPrimaryClip(clip)
    if (!silent && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
}

internal fun openAppSettings(context: Context) {
    context.tryStart(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
