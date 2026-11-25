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

    /**
     * Last position of the floating bubble in screen pixels (top-left of its window), or -1 when
     * it has never been moved.
     */
    var bubbleX: Int
        get() = preferences.getInt(KEY_BUBBLE_X, -1)
        set(value) = preferences.edit { putInt(KEY_BUBBLE_X, value) }

    var bubbleY: Int
        get() = preferences.getInt(KEY_BUBBLE_Y, -1)
        set(value) = preferences.edit { putInt(KEY_BUBBLE_Y, value) }

    /**
     * Keep the screen-capture session open while the floating bubble is showing, so repeat
     * captures skip Android's consent prompt. Android shows a screen-sharing indicator meanwhile.
     */
    var captureSessionEnabled: Boolean
        get() = preferences.getBoolean(KEY_CAPTURE_SESSION, false)
        set(value) = preferences.edit { putBoolean(KEY_CAPTURE_SESSION, value) }

    /** Use wallpaper-based colours on Android 12+ instead of the TransVerse palette. */
    var appDynamicColor: Boolean
        get() = preferences.getBoolean(KEY_DYNAMIC_COLOR, false)
        set(value) = preferences.edit { putBoolean(KEY_DYNAMIC_COLOR, value) }

    companion object {
        const val FILE_NAME = "transverse_preferences"
        private const val KEY_THEME = "theme"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_BUBBLE_SIZE = "bubble_size"
        private const val KEY_READING_SPEED = "reading_speed"
        private const val KEY_SPEECH_PITCH = "speech_pitch"
        private const val KEY_INCOGNITO = "incognito"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        private const val KEY_CAPTURE_SESSION = "capture_session"
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
