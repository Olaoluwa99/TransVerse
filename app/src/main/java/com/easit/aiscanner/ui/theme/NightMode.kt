package com.easit.aiscanner.ui.theme

import androidx.appcompat.app.AppCompatDelegate
import com.easit.aiscanner.data.ThemeMode

/**
 * Applies [this] as AppCompat's app-wide night mode, so View-based screens (image cropper,
 * transparent capture activities, system dialogs) match the Compose theme. AppCompat recreates
 * running activities only when the mode actually changes.
 */
fun ThemeMode.applyAsDefaultNightMode() {
    AppCompatDelegate.setDefaultNightMode(
        when (this) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    )
}
