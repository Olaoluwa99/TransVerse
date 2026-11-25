package com.easit.aiscanner.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.automirrored.rounded.ScreenShare
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import com.easit.aiscanner.BuildConfig
import com.easit.aiscanner.R
import com.easit.aiscanner.data.AppearanceSettings
import com.easit.aiscanner.data.ThemeMode
import com.easit.aiscanner.ui.theme.TransVerseTheme
import kotlinx.coroutines.launch
import java.time.Year

/**
 * Settings: appearance, speech, quick capture, privacy and about. Help, privacy policy and
 * licences are pushed screens ([HelpScreen], [PrivacyPolicyScreen], [LicensesScreen]).
 */
@Composable
fun SettingsScreen(
    onOpenHelp: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenLicenses: () -> Unit,
    onStartQuickCapture: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val historyDeleted = stringResource(R.string.history_deleted)
    val historyDeleteFailed = stringResource(R.string.settings_history_delete_failed)
    val voiceUnavailable = stringResource(R.string.settings_voice_unavailable)
    val voiceSample = stringResource(R.string.settings_voice_sample)
    val linkUnavailable = stringResource(R.string.settings_link_unavailable)
    val shareSubject = stringResource(R.string.share_app_subject)
    val shareChooser = stringResource(R.string.share_app_chooser)
    val shareMessage = stringResource(R.string.share_app_message, storeListingUrl(context))

    val voicePreview = rememberVoicePreview(
        onUnavailable = { scope.launch { snackbarHostState.showSnackbar(voiceUnavailable) } },
    )

    // Preferences can change elsewhere (e.g. incognito from another screen); stop speaking when
    // the screen is no longer in front.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { voicePreview.stop() }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                SettingsEvent.HistoryDeleted -> historyDeleted
                SettingsEvent.HistoryDeleteFailed -> historyDeleteFailed
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    SettingsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        isVoicePlaying = voicePreview.isSpeaking,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColor,
        onReadingTextSizeChange = viewModel::setReadingTextSize,
        onReadingSpeedChange = viewModel::setReadingSpeed,
        onSpeechPitchChange = viewModel::setSpeechPitch,
        onTestVoice = {
            if (voicePreview.isSpeaking) {
                voicePreview.stop()
            } else {
                voicePreview.speak(
                    text = voiceSample,
                    rate = SettingsRanges.speechMultiplier(state.readingSpeed),
                    pitch = SettingsRanges.speechMultiplier(state.speechPitch),
                )
            }
        },
        onStartQuickCapture = onStartQuickCapture,
        onBubbleSizeChange = viewModel::setBubbleSize,
        onCaptureSessionChange = viewModel::setCaptureSessionEnabled,
        onIncognitoChange = viewModel::setIncognitoMode,
        onDeleteAllHistory = viewModel::deleteAllHistory,
        onOpenHelp = onOpenHelp,
        onOpenPrivacyPolicy = onOpenPrivacyPolicy,
        onOpenLicenses = onOpenLicenses,
        onShareApp = {
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                .putExtra(Intent.EXTRA_TEXT, shareMessage)
            context.startActivity(Intent.createChooser(send, shareChooser))
        },
        onRateApp = {
            if (!context.openStoreListing()) {
                scope.launch { snackbarHostState.showSnackbar(linkUnavailable) }
            }
        },
        modifier = modifier,
    )
}

/**
 * Stateless Settings UI.
 *
 * @param supportsDynamicColor whether wallpaper colours are available (Android 12+)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    isVoicePlaying: Boolean,
    versionName: String,
    versionCode: Int,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onReadingTextSizeChange: (Int) -> Unit,
    onReadingSpeedChange: (Int) -> Unit,
    onSpeechPitchChange: (Int) -> Unit,
    onTestVoice: () -> Unit,
    onStartQuickCapture: () -> Unit,
    onBubbleSizeChange: (BubbleSize) -> Unit,
    onIncognitoChange: (Boolean) -> Unit,
    onDeleteAllHistory: () -> Unit,
    onOpenHelp: () -> Unit,
    onCaptureSessionChange: (Boolean) -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit,
    onOpenLicenses: () -> Unit,
    onShareApp: () -> Unit,
    onRateApp: () -> Unit,
    modifier: Modifier = Modifier,
    supportsDynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "appearance") {
                AppearanceGroup(
                    state = state,
                    supportsDynamicColor = supportsDynamicColor,
                    onThemeModeChange = onThemeModeChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onReadingTextSizeChange = onReadingTextSizeChange,
                )
            }
            item(key = "speech") {
                SpeechGroup(
                    state = state,
                    isVoicePlaying = isVoicePlaying,
                    onReadingSpeedChange = onReadingSpeedChange,
                    onSpeechPitchChange = onSpeechPitchChange,
                    onTestVoice = onTestVoice,
                )
            }
            item(key = "quick_capture") {
                QuickCaptureGroup(
                    bubbleSize = state.bubbleSize,
                    captureSessionEnabled = state.captureSessionEnabled,
                    onStartQuickCapture = onStartQuickCapture,
                    onBubbleSizeChange = onBubbleSizeChange,
                    onCaptureSessionChange = onCaptureSessionChange,
                )
            }
            item(key = "privacy") {
                PrivacyGroup(
                    incognitoMode = state.incognitoMode,
                    isDeletingHistory = state.isDeletingHistory,
                    onIncognitoChange = onIncognitoChange,
                    onDeleteAllHistory = { showDeleteDialog = true },
                )
            }
            item(key = "about") {
                AboutGroup(
                    versionName = versionName,
                    versionCode = versionCode,
                    onOpenHelp = onOpenHelp,
                    onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                    onOpenLicenses = onOpenLicenses,
                    onShareApp = onShareApp,
                    onRateApp = onRateApp,
                )
            }
            item(key = "footer") { SettingsFooter() }
        }
    }

    if (showDeleteDialog) {
        DeleteHistoryDialog(
            onConfirm = {
                showDeleteDialog = false
                onDeleteAllHistory()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun AppearanceGroup(
    state: SettingsUiState,
    supportsDynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onReadingTextSizeChange: (Int) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_section_appearance)) {
        item { shapes ->
            SettingsCustomItem(shapes = shapes) {
                SettingsItemHeader(
                    headline = stringResource(R.string.settings_theme_label),
                    icon = Icons.Rounded.Palette,
                )
                ConnectedChoiceRow(
                    options = ThemeMode.entries,
                    selected = state.themeMode,
                    onSelect = onThemeModeChange,
                    label = { themeLabel(it) },
                )
            }
        }
        if (supportsDynamicColor) {
            item { shapes ->
                SettingsSwitchItem(
                    shapes = shapes,
                    headline = stringResource(R.string.settings_dynamic_color),
                    supporting = stringResource(R.string.settings_dynamic_color_summary),
                    icon = Icons.Rounded.Wallpaper,
                    checked = state.dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                )
            }
        }
        item { shapes ->
            SettingsSliderItem(
                shapes = shapes,
                headline = stringResource(R.string.settings_reading_text_size),
                supporting = stringResource(R.string.settings_reading_text_size_summary),
                icon = Icons.Rounded.FormatSize,
                value = state.readingTextSizeSp,
                range = SettingsRanges.TEXT_SIZE_MIN..SettingsRanges.TEXT_SIZE_MAX,
                step = 1,
                onValueCommitted = onReadingTextSizeChange,
                valueLabel = { stringResource(R.string.settings_value_sp, it) },
            ) { size -> ReadingTextSample(sizeSp = size) }
        }
    }
}

/** The sample sentence at the size being chosen, styled like transcripts and translations. */
@Composable
private fun ReadingTextSample(sizeSp: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.settings_reading_text_sample),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = sizeSp.sp,
                lineHeight = (sizeSp * 1.5f).sp,
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun SpeechGroup(
    state: SettingsUiState,
    isVoicePlaying: Boolean,
    onReadingSpeedChange: (Int) -> Unit,
    onSpeechPitchChange: (Int) -> Unit,
    onTestVoice: () -> Unit,
) {
    val speechRange = SettingsRanges.SPEECH_MIN..SettingsRanges.SPEECH_MAX
    SettingsGroup(title = stringResource(R.string.settings_section_speech)) {
        item { shapes ->
            SettingsSliderItem(
                shapes = shapes,
                headline = stringResource(R.string.settings_reading_speed),
                icon = Icons.Rounded.Speed,
                value = state.readingSpeed,
                range = speechRange,
                step = SettingsRanges.SPEECH_STEP,
                onValueCommitted = onReadingSpeedChange,
                valueLabel = { speechValueLabel(it) },
            )
        }
        item { shapes ->
            SettingsSliderItem(
                shapes = shapes,
                headline = stringResource(R.string.settings_voice_pitch),
                icon = Icons.Rounded.GraphicEq,
                value = state.speechPitch,
                range = speechRange,
                step = SettingsRanges.SPEECH_STEP,
                onValueCommitted = onSpeechPitchChange,
                valueLabel = { speechValueLabel(it) },
            )
        }
        item { shapes ->
            SettingsItem(
                shapes = shapes,
                headline = stringResource(
                    if (isVoicePlaying) R.string.settings_stop_voice else R.string.settings_test_voice,
                ),
                supporting = stringResource(R.string.settings_voice_sample),
                icon = if (isVoicePlaying) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                onClick = onTestVoice,
            )
        }
    }
}

@Composable
private fun QuickCaptureGroup(
    bubbleSize: BubbleSize,
    captureSessionEnabled: Boolean,
    onStartQuickCapture: () -> Unit,
    onBubbleSizeChange: (BubbleSize) -> Unit,
    onCaptureSessionChange: (Boolean) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_quick_capture)) {
        item { shapes ->
            SettingsItem(
                shapes = shapes,
                headline = stringResource(R.string.settings_start_floating_capture),
                supporting = stringResource(R.string.settings_quick_capture_summary),
                icon = Icons.Rounded.PictureInPictureAlt,
                onClick = onStartQuickCapture,
            )
        }
        item { shapes ->
            SettingsCustomItem(shapes = shapes) {
                SettingsItemHeader(
                    headline = stringResource(R.string.settings_bubble_size),
                    supporting = stringResource(R.string.settings_bubble_size_summary),
                    icon = Icons.Rounded.BubbleChart,
                    value = bubbleSizeLabel(bubbleSize),
                )
                // Four text labels don't fit a phone-width row, so each option shows a dot of
                // the bubble's relative size; its name is the button's accessibility label.
                ConnectedChoiceRow(
                    options = BubbleSize.entries,
                    selected = bubbleSize,
                    onSelect = onBubbleSizeChange,
                    label = { bubbleSizeLabel(it) },
                    showLabel = false,
                    icon = { size -> BubbleDot(diameter = bubbleDotDiameter(size)) },
                )
            }
        }
        item { shapes ->
            SettingsSwitchItem(
                shapes = shapes,
                headline = stringResource(R.string.settings_capture_session),
                supporting = stringResource(R.string.settings_capture_session_summary),
                icon = Icons.AutoMirrored.Rounded.ScreenShare,
                checked = captureSessionEnabled,
                onCheckedChange = onCaptureSessionChange,
            )
        }
    }
}

@Composable
private fun BubbleDot(diameter: Dp) {
    Box(
        Modifier
            .size(diameter)
            .background(LocalContentColor.current, CircleShape),
    )
}

private fun bubbleDotDiameter(size: BubbleSize): Dp = when (size) {
    BubbleSize.VERY_SMALL -> 8.dp
    BubbleSize.SMALL -> 12.dp
    BubbleSize.MEDIUM -> 16.dp
    BubbleSize.LARGE -> 20.dp
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PrivacyGroup(
    incognitoMode: Boolean,
    isDeletingHistory: Boolean,
    onIncognitoChange: (Boolean) -> Unit,
    onDeleteAllHistory: () -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_privacy)) {
        item { shapes ->
            SettingsSwitchItem(
                shapes = shapes,
                headline = stringResource(R.string.settings_incognito),
                supporting = stringResource(R.string.settings_incognito_supporting),
                icon = Icons.Rounded.VisibilityOff,
                checked = incognitoMode,
                onCheckedChange = onIncognitoChange,
            )
        }
        item { shapes ->
            SettingsItem(
                shapes = shapes,
                headline = stringResource(R.string.settings_delete_all_history),
                supporting = stringResource(R.string.settings_delete_history_summary),
                icon = Icons.Rounded.DeleteForever,
                contentColor = MaterialTheme.colorScheme.error,
                enabled = !isDeletingHistory,
                onClick = onDeleteAllHistory,
                trailing = if (isDeletingHistory) {
                    { LoadingIndicator(Modifier.size(32.dp)) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun AboutGroup(
    versionName: String,
    versionCode: Int,
    onOpenHelp: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenLicenses: () -> Unit,
    onShareApp: () -> Unit,
    onRateApp: () -> Unit,
) {
    val chevron: @Composable () -> Unit = {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
    }
    SettingsGroup(title = stringResource(R.string.settings_section_about)) {
        item { shapes ->
            SettingsItem(
                shapes = shapes,
                headline = stringResource(R.string.title_help),
                supporting = stringResource(R.string.settings_help_summary),
                icon = Icons.AutoMirrored.Rounded.HelpOutline,
                trailing = chevron,
                onClick = onOpenHelp,
            )
        }
        item { shapes ->
            SettingsItem(
                shapes = shapes,
                headline = stringResource(R.string.title_privacy_policy),
                supporting = stringResource(R.string.settings_privacy_policy_summary),
                icon = Icons.Rounded.PrivacyTip,
                trailing = chevron,
                onClick = onOpenPrivacyPolicy,
            )
        }
        item { shapes ->
            SettingsItem(
                shapes = shapes,
                headline = stringResource(R.string.settings_licenses),
                supporting = stringResource(R.string.settings_licenses_summary),
                icon = Icons.Rounded.Gavel,
                trailing = chevron,
                onClick = onOpenLicenses,
            )
        }
        item { shapes ->
            SettingsItem(
                shapes = shapes,
                headline = stringResource(R.string.settings_share_app),
                icon = Icons.Rounded.Share,
                onClick = onShareApp,
            )
        }
        item { shapes ->
            SettingsItem(
                shapes = shapes,
                headline = stringResource(R.string.settings_rate_app),
                icon = Icons.Rounded.StarRate,
                trailing = {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                },
                onClick = onRateApp,
            )
        }
        item { shapes ->
            SettingsItem(
                shapes = shapes,
                headline = stringResource(R.string.settings_app_version),
                supporting = stringResource(R.string.version_format, versionName, versionCode),
                icon = Icons.Rounded.Info,
            )
        }
    }
}

@Composable
private fun SettingsFooter() {
    Text(
        text = stringResource(R.string.copyright_format, Year.now().value) + "\n" +
            stringResource(R.string.all_rights_reserved),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
    )
}

@Composable
private fun DeleteHistoryDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
        iconContentColor = MaterialTheme.colorScheme.error,
        title = { Text(stringResource(R.string.delete_history_title)) },
        text = { Text(stringResource(R.string.settings_delete_dialog_text)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.settings_action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_action_cancel))
            }
        },
    )
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
private fun bubbleSizeLabel(size: BubbleSize): String = stringResource(
    when (size) {
        BubbleSize.VERY_SMALL -> R.string.bubble_very_small
        BubbleSize.SMALL -> R.string.bubble_small
        BubbleSize.MEDIUM -> R.string.bubble_medium
        BubbleSize.LARGE -> R.string.bubble_large
    },
)

/** Speech values are stored as 20–100 and shown as a rate multiplier (50 → 1.0×). */
@Composable
private fun speechValueLabel(value: Int): String =
    stringResource(R.string.settings_value_multiplier, SettingsRanges.speechMultiplier(value))

/** Play Store page for the release app (debug builds share the release listing). */
private fun storeListingUrl(context: Context): String =
    "https://play.google.com/store/apps/details?id=" + context.packageName.removeSuffix(".debug")

/** Opens the Play Store listing, falling back to the browser. Returns false if nothing can. */
private fun Context.openStoreListing(): Boolean {
    val uri = storeListingUrl(this).toUri()
    val playStore = Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending")
    return try {
        startActivity(playStore)
        true
    } catch (e: ActivityNotFoundException) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (noBrowser: ActivityNotFoundException) {
            false
        }
    }
}

@Preview(name = "Settings – light", showBackground = true, heightDp = 2000)
@Composable
private fun SettingsContentPreview() {
    TransVerseTheme {
        SettingsContentPreviewBody(SettingsUiState(dynamicColor = true, incognitoMode = true))
    }
}

@Preview(name = "Settings – dark, deleting", showBackground = true, heightDp = 2000)
@Composable
private fun SettingsContentDarkPreview() {
    TransVerseTheme(AppearanceSettings(themeMode = ThemeMode.DARK)) {
        SettingsContentPreviewBody(
            SettingsUiState(
                themeMode = ThemeMode.DARK,
                readingTextSizeSp = 20,
                readingSpeed = 70,
                bubbleSize = BubbleSize.LARGE,
                isDeletingHistory = true,
            ),
            isVoicePlaying = true,
        )
    }
}

@Composable
private fun SettingsContentPreviewBody(state: SettingsUiState, isVoicePlaying: Boolean = false) {
    SettingsContent(
        state = state,
        snackbarHostState = remember { SnackbarHostState() },
        isVoicePlaying = isVoicePlaying,
        versionName = "3.0.0",
        versionCode = 7,
        onThemeModeChange = {},
        onDynamicColorChange = {},
        onReadingTextSizeChange = {},
        onReadingSpeedChange = {},
        onSpeechPitchChange = {},
        onTestVoice = {},
        onStartQuickCapture = {},
        onBubbleSizeChange = {},
        onIncognitoChange = {},
        onDeleteAllHistory = {},
        onOpenHelp = {},
        onOpenPrivacyPolicy = {},
        onOpenLicenses = {},
        onShareApp = {},
        onRateApp = {},
        supportsDynamicColor = true,
    )
}
