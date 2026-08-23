package com.easit.aiscanner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.easit.aiscanner.di.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** A single, typed gateway for user preferences. */
class Preference @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppPreferences private val preferences: SharedPreferences
) {
    init {
        migrateLegacyPreferencesOnce()
    }

    var appTheme: String
        get() = preferences.getString(KEY_THEME, Constants.DEVICE_THEME) ?: Constants.DEVICE_THEME
        set(value) = preferences.edit { putString(KEY_THEME, value) }

    var appFontSize: Int
        get() = preferences.getInt(KEY_FONT_SIZE, Constants.S14)
        set(value) = preferences.edit { putInt(KEY_FONT_SIZE, value.coerceIn(12, 24)) }

    var appBubbleSize: String
        get() = preferences.getString(KEY_BUBBLE_SIZE, Constants.SMALL) ?: Constants.SMALL
        set(value) = preferences.edit { putString(KEY_BUBBLE_SIZE, value) }

    var appReadingSpeed: Int
        get() = preferences.getInt(KEY_READING_SPEED, DEFAULT_AUDIO_SETTING)
        set(value) = preferences.edit { putInt(KEY_READING_SPEED, value.coerceIn(20, 100)) }

    var appSpeechPitch: Int
        get() = preferences.getInt(KEY_SPEECH_PITCH, DEFAULT_AUDIO_SETTING)
        set(value) = preferences.edit { putInt(KEY_SPEECH_PITCH, value.coerceIn(20, 100)) }

    var appIncognitoMode: Boolean
        get() = preferences.getBoolean(KEY_INCOGNITO, false)
        set(value) = preferences.edit { putBoolean(KEY_INCOGNITO, value) }

    companion object {
        const val FILE_NAME = "transverse_preferences"
        private const val KEY_THEME = "theme"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_BUBBLE_SIZE = "bubble_size"
        private const val KEY_READING_SPEED = "reading_speed"
        private const val KEY_SPEECH_PITCH = "speech_pitch"
        private const val KEY_INCOGNITO = "incognito"
        private const val DEFAULT_AUDIO_SETTING = 50
        private const val KEY_MIGRATED = "legacy_preferences_migrated"
    }

    private fun migrateLegacyPreferencesOnce() {
        if (preferences.getBoolean(KEY_MIGRATED, false)) return

        val legacyTheme = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)
        val legacyFont = context.getSharedPreferences("font_size", Context.MODE_PRIVATE)
        val legacyBubble = context.getSharedPreferences("bubble_size", Context.MODE_PRIVATE)
        val legacySpeed = context.getSharedPreferences("reading_speed", Context.MODE_PRIVATE)
        val legacyPitch = context.getSharedPreferences("speech_pitch", Context.MODE_PRIVATE)
        val legacyIncognito = context.getSharedPreferences("incognito_mode", Context.MODE_PRIVATE)

        preferences.edit {
            legacyTheme.getString("KEY_THEME_PREF", null)?.let { putString(KEY_THEME, it) }
            if (legacyFont.contains("KEY_FONT_SIZE_PREF")) {
                putInt(KEY_FONT_SIZE, legacyFont.getInt("KEY_FONT_SIZE_PREF", Constants.S14))
            }
            legacyBubble.getString("KEY_BUBBLE_SIZE_PREF", null)?.let { putString(KEY_BUBBLE_SIZE, it) }
            if (legacySpeed.contains("KEY_READING_SPEED_PREF")) {
                putInt(KEY_READING_SPEED, legacySpeed.getInt("KEY_READING_SPEED_PREF", DEFAULT_AUDIO_SETTING))
            }
            if (legacyPitch.contains("KEY_SPEECH_PITCH_PREF")) {
                putInt(KEY_SPEECH_PITCH, legacyPitch.getInt("KEY_SPEECH_PITCH_PREF", DEFAULT_AUDIO_SETTING))
            }
            if (legacyIncognito.contains("KEY_INCOGNITO_MODE_PREF")) {
                putBoolean(KEY_INCOGNITO, legacyIncognito.getBoolean("KEY_INCOGNITO_MODE_PREF", false))
            }
            putBoolean(KEY_MIGRATED, true)
        }
    }
}
