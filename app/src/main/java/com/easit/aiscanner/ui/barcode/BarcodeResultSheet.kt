package com.easit.aiscanner.ui.barcode

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.easit.aiscanner.R
import com.easit.aiscanner.ui.theme.TransVerseTheme

/**
 * Body of the result bottom sheet: what was scanned, its value and what to do with it.
 * Stateless apart from the Wi-Fi password visibility toggle.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BarcodeResultContent(
    code: ScannedCode,
    onPrimaryAction: () -> Unit,
    onCopy: (text: String, sensitive: Boolean) -> Unit,
    onShare: () -> Unit,
    onTranslate: (() -> Unit)?,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ResultHeader(code)

        val content = code.content
        if (content is CodeContent.Wifi) {
            WifiDetails(content, onCopy = onCopy)
        } else {
            ValueCard(code)
        }

        code.primaryActionLabel()?.let { label ->
            val height = ButtonDefaults.MediumContainerHeight
            Button(
                onClick = onPrimaryAction,
                shapes = ButtonDefaults.shapesFor(height),
                modifier = Modifier.fillMaxWidth().heightIn(min = height),
                contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
            ) {
                Icon(
                    imageVector = content.primaryActionIcon,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                )
                Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
                Text(stringResource(label), style = ButtonDefaults.textStyleFor(height))
            }
        }

        SecondaryActions(
            showCopy = content !is CodeContent.Wifi,
            onCopy = { onCopy(code.copyText, false) },
            onShare = onShare,
            onTranslate = onTranslate,
        )

        OutlinedButton(
            onClick = onScanAgain,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Icon(
                Icons.Rounded.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.barcode_action_scan_again))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ResultHeader(code: ScannedCode) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = code.content.typeIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(code.content.typeLabel),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(code.format.label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ValueCard(code: ScannedCode) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SelectionContainer(Modifier.padding(20.dp)) {
            Text(
                text = code.displayText,
                style = if (code.isRawCode) {
                    MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Network, password and security as a segmented group with per-row copy buttons. */
@Composable
private fun WifiDetails(
    wifi: CodeContent.Wifi,
    onCopy: (text: String, sensitive: Boolean) -> Unit,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val large = MaterialTheme.shapes.large
    val small = MaterialTheme.shapes.extraSmall
    val hasPassword = wifi.password.isNotEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        WifiRow(
            label = stringResource(R.string.barcode_wifi_network),
            value = wifi.ssid,
            shape = large.copy(bottomStart = small.bottomStart, bottomEnd = small.bottomEnd),
        ) {
            IconButton(onClick = { onCopy(wifi.ssid, false) }) {
                Icon(Icons.Rounded.ContentCopy, stringResource(R.string.barcode_wifi_copy_network))
            }
        }
        if (hasPassword) {
            WifiRow(
                label = stringResource(R.string.barcode_wifi_password),
                value = wifi.password,
                monospace = true,
                obscured = !showPassword,
                shape = small,
            ) {
                IconButton(onClick = { showPassword = !showPassword }) {
                    AnimatedContent(targetState = showPassword, label = "passwordVisibility") { shown ->
                        Icon(
                            imageVector = if (shown) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(
                                if (shown) R.string.barcode_wifi_hide_password else R.string.barcode_wifi_show_password,
                            ),
                        )
                    }
                }
                IconButton(onClick = { onCopy(wifi.password, true) }) {
                    Icon(Icons.Rounded.ContentCopy, stringResource(R.string.barcode_wifi_copy_password))
                }
            }
        }
        WifiRow(
            label = stringResource(R.string.barcode_wifi_security),
            value = stringResource(wifi.security.label),
            shape = large.copy(topStart = small.topStart, topEnd = small.topEnd),
        )
    }
}

@Composable
private fun WifiRow(
    label: String,
    value: String,
    shape: Shape,
    monospace: Boolean = false,
    obscured: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val style = MaterialTheme.typography.bodyLarge.let {
                    if (monospace) it.copy(fontFamily = FontFamily.Monospace) else it
                }
                if (obscured) {
                    Text("•".repeat(value.length.coerceIn(8, 16)), style = style)
                } else {
                    SelectionContainer { Text(value, style = style) }
                }
            }
            if (trailing != null) Row { trailing() }
        }
    }
}

/** Copy / Share / Translate as an expressive button group (overflows into a menu when narrow). */
@Composable
private fun SecondaryActions(
    showCopy: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onTranslate: (() -> Unit)?,
) {
    val copyLabel = stringResource(R.string.barcode_action_copy)
    val shareLabel = stringResource(R.string.barcode_action_share)
    val translateLabel = stringResource(R.string.barcode_action_translate)

    ButtonGroup(
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        fun item(label: String, icon: ImageVector, onClick: () -> Unit) {
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    FilledTonalButton(
                        onClick = onClick,
                        shapes = ButtonDefaults.shapes(),
                        interactionSource = interactionSource,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .animateWidth(interactionSource),
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(label, maxLines = 1)
                    }
                },
                menuContent = { menuState ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = { Icon(icon, contentDescription = null) },
                        onClick = {
                            onClick()
                            menuState.dismiss()
                        },
                    )
                },
            )
        }

        if (showCopy) item(copyLabel, Icons.Rounded.ContentCopy, onCopy)
        item(shareLabel, Icons.Rounded.Share, onShare)
        if (onTranslate != null) item(translateLabel, Icons.Rounded.Translate, onTranslate)
    }
}

private val CodeContent.typeIcon: ImageVector
    get() = when (this) {
        is CodeContent.Url -> Icons.Rounded.Link
        is CodeContent.Wifi -> Icons.Rounded.Wifi
        is CodeContent.Email -> Icons.Rounded.Email
        is CodeContent.Phone -> Icons.Rounded.Phone
        is CodeContent.Sms -> Icons.Rounded.Sms
        is CodeContent.Geo -> Icons.Rounded.Place
        is CodeContent.Contact -> Icons.Rounded.Person
        is CodeContent.CalendarEvent -> Icons.Rounded.Event
        is CodeContent.Product -> Icons.Rounded.ShoppingCart
        is CodeContent.Isbn -> Icons.AutoMirrored.Rounded.MenuBook
        CodeContent.DriverLicense -> Icons.Rounded.Badge
        is CodeContent.Text -> Icons.Rounded.Description
    }

private val CodeContent.primaryActionIcon: ImageVector
    get() = when (this) {
        is CodeContent.Email -> Icons.Rounded.Email
        is CodeContent.Phone -> Icons.Rounded.Phone
        is CodeContent.Sms -> Icons.Rounded.Sms
        is CodeContent.Geo -> Icons.Rounded.Map
        is CodeContent.Contact -> Icons.Rounded.PersonAdd
        is CodeContent.CalendarEvent -> Icons.Rounded.Event
        is CodeContent.Wifi -> Icons.Rounded.Wifi
        is CodeContent.Product, is CodeContent.Isbn -> Icons.Rounded.Search
        else -> Icons.AutoMirrored.Rounded.OpenInNew
    }

// region Previews

private val PreviewUrl = ScannedCode(
    content = CodeContent.Url("https://example.com/menu", ""),
    format = CodeFormat.QrCode,
    displayText = "https://example.com/menu",
    rawValue = "https://example.com/menu",
)

private val PreviewWifi = ScannedCode(
    content = CodeContent.Wifi("Cafe Guest", "espresso-2024", WifiSecurity.Wpa),
    format = CodeFormat.QrCode,
    displayText = "Cafe Guest",
    rawValue = "WIFI:S:Cafe Guest;T:WPA;P:espresso-2024;;",
)

private val PreviewProduct = ScannedCode(
    content = CodeContent.Product("5901234123457"),
    format = CodeFormat.Ean13,
    displayText = "5901234123457",
    rawValue = "5901234123457",
)

@Preview(showBackground = true, name = "Result – link")
@Composable
private fun ResultUrlPreview() {
    TransVerseTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            BarcodeResultContent(
                code = PreviewUrl,
                onPrimaryAction = {},
                onCopy = { _, _ -> },
                onShare = {},
                onTranslate = null,
                onScanAgain = {},
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "Result – Wi-Fi")
@Composable
private fun ResultWifiPreview() {
    TransVerseTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            BarcodeResultContent(
                code = PreviewWifi,
                onPrimaryAction = {},
                onCopy = { _, _ -> },
                onShare = {},
                onTranslate = null,
                onScanAgain = {},
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "Result – product")
@Composable
private fun ResultProductPreview() {
    TransVerseTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            BarcodeResultContent(
                code = PreviewProduct,
                onPrimaryAction = {},
                onCopy = { _, _ -> },
                onShare = {},
                onTranslate = {},
                onScanAgain = {},
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

// endregion
