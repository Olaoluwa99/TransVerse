package com.easit.aiscanner.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Everything that changes how the app looks.
 *
 * @property readingTextSizeSp size of the text being read and translated (transcript,
 *   translation). The rest of the UI follows the system font scale instead.
 */
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val readingTextSizeSp: Int = Constants.S14,
)

/**
 * Observable source of truth for [AppearanceSettings], persisted through [Preference].
 * The activity collects it to theme the app; the Settings screen updates it.
 */
@Singleton
class AppearanceStore @Inject constructor(private val preference: Preference) {

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppearanceSettings> = _settings.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        preference.appTheme = when (mode) {
            ThemeMode.SYSTEM -> Constants.DEVICE_THEME
            ThemeMode.LIGHT -> Constants.LIGHT_THEME
            ThemeMode.DARK -> Constants.DARK_THEME
        }
        _settings.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        preference.appDynamicColor = enabled
        _settings.update { it.copy(dynamicColor = enabled) }
    }

    fun setReadingTextSize(sizeSp: Int) {
        preference.appFontSize = sizeSp
        // Preference clamps the stored value; mirror what was actually saved.
        _settings.update { it.copy(readingTextSizeSp = preference.appFontSize) }
    }

    private fun read() = AppearanceSettings(
        themeMode = when (preference.appTheme) {
            Constants.LIGHT_THEME -> ThemeMode.LIGHT
            Constants.DARK_THEME -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        },
        dynamicColor = preference.appDynamicColor,
        readingTextSizeSp = preference.appFontSize,
    )
}
