package com.easit.aiscanner.ui.barcode

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.easit.aiscanner.R
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.Calendar
import java.util.TimeZone

/** A decoded code, independent of ML Kit so the UI can be previewed and tested. */
@Immutable
data class ScannedCode(
    val content: CodeContent,
    val format: CodeFormat,
    /** Human-readable summary shown in the result sheet. */
    val displayText: String,
    /** The exact encoded value. */
    val rawValue: String,
) {
    /** What Copy and Share hand out. */
    val copyText: String
        get() = when (content) {
            is CodeContent.Url -> content.url
            is CodeContent.Text -> content.text
            is CodeContent.Phone -> content.number
            is CodeContent.Email -> content.address
            else -> displayText.ifBlank { rawValue }
        }

    /** Text worth translating, or null when translation makes no sense (links, numbers, Wi-Fi). */
    val translatableText: String?
        get() = when (content) {
            is CodeContent.Text -> content.text
            is CodeContent.Sms -> content.message.takeIf { it.isNotBlank() }
            is CodeContent.CalendarEvent -> displayText
            is CodeContent.Contact -> displayText
            is CodeContent.Email -> listOf(content.subject, content.body)
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .takeIf { it.isNotBlank() }
            else -> null
        }

    /** Raw codes (product numbers, licences) read better in a monospace face. */
    val isRawCode: Boolean
        get() = content is CodeContent.Product || content is CodeContent.Isbn ||
            content is CodeContent.DriverLicense
}

/** Typed payload of a [ScannedCode]. */
@Immutable
sealed interface CodeContent {
    data class Url(val url: String, val title: String) : CodeContent
    data class Wifi(val ssid: String, val password: String, val security: WifiSecurity) : CodeContent
    data class Email(val address: String, val subject: String, val body: String) : CodeContent
    data class Phone(val number: String) : CodeContent
    data class Sms(val number: String, val message: String) : CodeContent
    data class Geo(val latitude: Double, val longitude: Double) : CodeContent
    data class Contact(
        val name: String,
        val organization: String,
        val jobTitle: String,
        val phones: List<String>,
        val emails: List<String>,
    ) : CodeContent
    data class CalendarEvent(
        val summary: String,
        val description: String,
        val location: String,
        val startMillis: Long?,
        val endMillis: Long?,
    ) : CodeContent
    data class Product(val code: String) : CodeContent
    data class Isbn(val code: String) : CodeContent
    data object DriverLicense : CodeContent
    data class Text(val text: String) : CodeContent
}

enum class WifiSecurity(@StringRes val label: Int) {
    Open(R.string.barcode_wifi_security_open),
    Wpa(R.string.barcode_wifi_security_wpa),
    Wep(R.string.barcode_wifi_security_wep),
}

enum class CodeFormat(@StringRes val label: Int) {
    QrCode(R.string.barcode_format_qr),
    Aztec(R.string.barcode_format_aztec),
    DataMatrix(R.string.barcode_format_data_matrix),
    Pdf417(R.string.barcode_format_pdf417),
    Ean13(R.string.barcode_format_ean13),
    Ean8(R.string.barcode_format_ean8),
    UpcA(R.string.barcode_format_upca),
    UpcE(R.string.barcode_format_upce),
    Code128(R.string.barcode_format_code128),
    Code39(R.string.barcode_format_code39),
    Code93(R.string.barcode_format_code93),
    Codabar(R.string.barcode_format_codabar),
    Itf(R.string.barcode_format_itf),
    Unknown(R.string.barcode_format_unknown),
}

@get:StringRes
val CodeContent.typeLabel: Int
    get() = when (this) {
        is CodeContent.Url -> R.string.barcode_type_url
        is CodeContent.Wifi -> R.string.barcode_type_wifi
        is CodeContent.Email -> R.string.barcode_type_email
        is CodeContent.Phone -> R.string.barcode_type_phone
        is CodeContent.Sms -> R.string.barcode_type_sms
        is CodeContent.Geo -> R.string.barcode_type_geo
        is CodeContent.Contact -> R.string.barcode_type_contact
        is CodeContent.CalendarEvent -> R.string.barcode_type_calendar
        is CodeContent.Product -> R.string.barcode_type_product
        is CodeContent.Isbn -> R.string.barcode_type_isbn
        CodeContent.DriverLicense -> R.string.barcode_type_driver_license
        is CodeContent.Text -> R.string.barcode_type_text
    }

/** Maps an ML Kit result to a [ScannedCode]; null when the code carries no readable value. */
fun Barcode.toScannedCode(): ScannedCode? {
    val raw = rawValue?.takeIf { it.isNotBlank() } ?: return null
    val display = displayValue?.takeIf { it.isNotBlank() } ?: raw
    val content = typedContent(raw) ?: CodeContent.Text(raw)
    return ScannedCode(
        content = content,
        format = format.toCodeFormat(),
        displayText = content.describe() ?: display,
        rawValue = raw,
    )
}

private fun Barcode.typedContent(raw: String): CodeContent? = when (valueType) {
    Barcode.TYPE_URL -> {
        val bookmark = url
        val link = bookmark?.url.orEmpty()
        if (link.isBlank()) null else CodeContent.Url(link, bookmark?.title.orEmpty())
    }
    Barcode.TYPE_WIFI -> wifi?.let { info ->
        CodeContent.Wifi(
            ssid = info.ssid.orEmpty(),
            password = info.password.orEmpty(),
            security = when (info.encryptionType) {
                Barcode.WiFi.TYPE_WPA -> WifiSecurity.Wpa
                Barcode.WiFi.TYPE_WEP -> WifiSecurity.Wep
                else -> WifiSecurity.Open
            },
        )
    }
    Barcode.TYPE_EMAIL -> {
        val info = email
        val address = info?.address.orEmpty()
        if (address.isBlank()) null
        else CodeContent.Email(address, info?.subject.orEmpty(), info?.body.orEmpty())
    }
    Barcode.TYPE_PHONE -> {
        val number = phone?.number.orEmpty()
        if (number.isBlank()) null else CodeContent.Phone(number)
    }
    Barcode.TYPE_SMS -> {
        val info = sms
        val number = info?.phoneNumber.orEmpty()
        if (number.isBlank()) null else CodeContent.Sms(number, info?.message.orEmpty())
    }
    Barcode.TYPE_GEO -> geoPoint?.let { point -> CodeContent.Geo(point.lat, point.lng) }
    Barcode.TYPE_CONTACT_INFO -> contactInfo?.let { info ->
        val personName = info.name
        val formatted = personName?.formattedName.orEmpty()
        CodeContent.Contact(
            name = formatted.ifBlank {
                listOf(personName?.first.orEmpty(), personName?.last.orEmpty())
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            },
            organization = info.organization.orEmpty(),
            jobTitle = info.title.orEmpty(),
            phones = info.phones.orEmpty().mapNotNull { phone ->
                phone?.number?.takeIf { it.isNotBlank() }
            },
            emails = info.emails.orEmpty().mapNotNull { mail ->
                mail?.address?.takeIf { it.isNotBlank() }
            },
        )
    }
    Barcode.TYPE_CALENDAR_EVENT -> calendarEvent?.let { event ->
        CodeContent.CalendarEvent(
            summary = event.summary.orEmpty(),
            description = event.description.orEmpty(),
            location = event.location.orEmpty(),
            startMillis = event.start?.toEpochMillis(),
            endMillis = event.end?.toEpochMillis(),
        )
    }
    Barcode.TYPE_PRODUCT -> CodeContent.Product(raw)
    Barcode.TYPE_ISBN -> CodeContent.Isbn(raw)
    Barcode.TYPE_DRIVER_LICENSE -> CodeContent.DriverLicense
    else -> null
}

/** Multi-line summary for structured payloads; null to use ML Kit's display value. */
private fun CodeContent.describe(): String? {
    val lines: List<String> = when (this) {
        is CodeContent.Url -> listOf(url)
        is CodeContent.Wifi -> listOf(ssid)
        is CodeContent.Email -> listOf(address, subject, body)
        is CodeContent.Sms -> listOf(number, message)
        is CodeContent.Geo -> listOf("$latitude, $longitude")
        is CodeContent.Contact -> listOf(name, jobTitle, organization) + phones + emails
        is CodeContent.CalendarEvent -> listOf(summary, location, description)
        is CodeContent.Text -> listOf(text)
        is CodeContent.Phone -> listOf(number)
        is CodeContent.Product, is CodeContent.Isbn, CodeContent.DriverLicense -> emptyList()
    }
    return lines.filter { it.isNotBlank() }.joinToString("\n").takeIf { it.isNotBlank() }
}

private fun Int.toCodeFormat(): CodeFormat = when (this) {
    Barcode.FORMAT_QR_CODE -> CodeFormat.QrCode
    Barcode.FORMAT_AZTEC -> CodeFormat.Aztec
    Barcode.FORMAT_DATA_MATRIX -> CodeFormat.DataMatrix
    Barcode.FORMAT_PDF417 -> CodeFormat.Pdf417
    Barcode.FORMAT_EAN_13 -> CodeFormat.Ean13
    Barcode.FORMAT_EAN_8 -> CodeFormat.Ean8
    Barcode.FORMAT_UPC_A -> CodeFormat.UpcA
    Barcode.FORMAT_UPC_E -> CodeFormat.UpcE
    Barcode.FORMAT_CODE_128 -> CodeFormat.Code128
    Barcode.FORMAT_CODE_39 -> CodeFormat.Code39
    Barcode.FORMAT_CODE_93 -> CodeFormat.Code93
    Barcode.FORMAT_CODABAR -> CodeFormat.Codabar
    Barcode.FORMAT_ITF -> CodeFormat.Itf
    else -> CodeFormat.Unknown
}

private fun Barcode.CalendarDateTime.toEpochMillis(): Long? {
    if (year <= 0 || month <= 0 || day <= 0) return null
    val zone = if (isUtc) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()
    return Calendar.getInstance(zone).run {
        clear()
        set(year, month - 1, day, hours.coerceAtLeast(0), minutes.coerceAtLeast(0), seconds.coerceAtLeast(0))
        timeInMillis
    }
}
