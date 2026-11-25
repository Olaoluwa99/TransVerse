package com.easit.aiscanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easit.aiscanner.data.AppearanceSettings
import com.easit.aiscanner.data.AppearanceStore
import com.easit.aiscanner.data.Constants
import com.easit.aiscanner.data.Preference
import com.easit.aiscanner.data.ThemeMode
import com.easit.aiscanner.database.CaptureFileStore
import com.easit.aiscanner.database.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Size of the floating quick-capture bubble, stored in [Preference.appBubbleSize]. */
enum class BubbleSize(val preferenceValue: String) {
    VERY_SMALL(Constants.VERY_SMALL),
    SMALL(Constants.SMALL),
    MEDIUM(Constants.MEDIUM),
    LARGE(Constants.LARGE);

    companion object {
        fun fromPreference(value: String): BubbleSize =
            entries.firstOrNull { it.preferenceValue == value } ?: SMALL
    }
}

/** Everything the Settings screen shows. Speech values are 20–100, where 50 is normal (1.0×). */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val readingTextSizeSp: Int = Constants.S14,
    val readingSpeed: Int = SettingsRanges.SPEECH_DEFAULT,
    val speechPitch: Int = SettingsRanges.SPEECH_DEFAULT,
    val bubbleSize: BubbleSize = BubbleSize.SMALL,
    /** Keep screen capture ready while the bubble is open (no consent prompt per capture). */
    val captureSessionEnabled: Boolean = false,
    val incognitoMode: Boolean = false,
    val isDeletingHistory: Boolean = false,
)

/** One-off messages for the Settings screen. */
sealed interface SettingsEvent {
    data object HistoryDeleted : SettingsEvent
    data object HistoryDeleteFailed : SettingsEvent
}

/** Allowed ranges, matching the clamping in [Preference]. */
object SettingsRanges {
    const val TEXT_SIZE_MIN = 12
    const val TEXT_SIZE_MAX = 24
    const val SPEECH_MIN = 20
    const val SPEECH_MAX = 100
    const val SPEECH_STEP = 10
    const val SPEECH_DEFAULT = 50

    /** TextToSpeech rate/pitch multiplier for a stored speech value (50 → 1.0×). */
    fun speechMultiplier(value: Int): Float = (value / 50f).coerceAtLeast(0.1f)
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appearanceStore: AppearanceStore,
    private val preference: Preference,
    private val repository: ScanRepository,
    private val captureFileStore: CaptureFileStore,
) : ViewModel() {

    /** Settings kept in [Preference] that have no observable store of their own. */
    private data class LocalSettings(
        val readingSpeed: Int,
        val speechPitch: Int,
        val bubbleSize: BubbleSize,
        val captureSessionEnabled: Boolean,
        val incognitoMode: Boolean,
        val isDeletingHistory: Boolean = false,
    )

    private val local = MutableStateFlow(readLocalSettings())

    val uiState: StateFlow<SettingsUiState> =
        combine(appearanceStore.settings, local) { appearance, stored -> toUiState(appearance, stored) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = toUiState(appearanceStore.settings.value, local.value),
            )

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    /** Re-reads preferences another screen may have changed (for example incognito mode). */
    fun refresh() {
        val fresh = readLocalSettings()
        local.update { fresh.copy(isDeletingHistory = it.isDeletingHistory) }
    }

    fun setThemeMode(mode: ThemeMode) = appearanceStore.setThemeMode(mode)

    fun setDynamicColor(enabled: Boolean) = appearanceStore.setDynamicColor(enabled)

    fun setReadingTextSize(sizeSp: Int) = appearanceStore.setReadingTextSize(sizeSp)

    fun setReadingSpeed(value: Int) {
        preference.appReadingSpeed = value
        local.update { it.copy(readingSpeed = preference.appReadingSpeed) }
    }

    fun setSpeechPitch(value: Int) {
        preference.appSpeechPitch = value
        local.update { it.copy(speechPitch = preference.appSpeechPitch) }
    }

    fun setBubbleSize(size: BubbleSize) {
        preference.appBubbleSize = size.preferenceValue
        local.update { it.copy(bubbleSize = size) }
    }

    /** Takes effect the next time the floating bubble starts a capture. */
    fun setCaptureSessionEnabled(enabled: Boolean) {
        preference.captureSessionEnabled = enabled
        local.update { it.copy(captureSessionEnabled = enabled) }
    }

    fun setIncognitoMode(enabled: Boolean) {
        preference.appIncognitoMode = enabled
        local.update { it.copy(incognitoMode = enabled) }
    }

    /** Removes every saved scan, then the captured images they pointed to. */
    fun deleteAllHistory() {
        if (local.value.isDeletingHistory) return
        local.update { it.copy(isDeletingHistory = true) }
        viewModelScope.launch {
            val event = try {
                withContext(Dispatchers.IO) {
                    repository.deleteAll()
                    captureFileStore.deleteAllCaptureFiles()
                }
                SettingsEvent.HistoryDeleted
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SettingsEvent.HistoryDeleteFailed
            } finally {
                local.update { it.copy(isDeletingHistory = false) }
            }
            _events.send(event)
        }
    }

    private fun readLocalSettings() = LocalSettings(
        readingSpeed = preference.appReadingSpeed,
        speechPitch = preference.appSpeechPitch,
        bubbleSize = BubbleSize.fromPreference(preference.appBubbleSize),
        captureSessionEnabled = preference.captureSessionEnabled,
        incognitoMode = preference.appIncognitoMode,
    )

    private fun toUiState(appearance: AppearanceSettings, stored: LocalSettings) = SettingsUiState(
        themeMode = appearance.themeMode,
        dynamicColor = appearance.dynamicColor,
        readingTextSizeSp = appearance.readingTextSizeSp,
        readingSpeed = stored.readingSpeed,
        speechPitch = stored.speechPitch,
        bubbleSize = stored.bubbleSize,
        captureSessionEnabled = stored.captureSessionEnabled,
        incognitoMode = stored.incognitoMode,
        isDeletingHistory = stored.isDeletingHistory,
    )
}
