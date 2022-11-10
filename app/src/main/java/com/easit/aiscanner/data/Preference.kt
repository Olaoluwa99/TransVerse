package com.easit.aiscanner.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.easit.aiscanner.di.*
import javax.inject.Inject

class Preference @Inject constructor(
    @AppTheme private val appThemeSharedPreference: SharedPreferences,
    @AppFontSize private val appFontSizeSharedPreference: SharedPreferences,
    @AppSelectedLanguage private val appLanguageSharedPreference: SharedPreferences,
    @AppBubbleSize private val appBubbleSizeSharedPreferences: SharedPreferences,
    @AppReadingSpeed private val appReadingSpeedSharedPreferences: SharedPreferences,
    @AppSpeechPitch private val appSpeechPitchSharedPreferences: SharedPreferences,
    @AppIncognitoMode private val appIncognitoModeSharedPreferences: SharedPreferences,
) {
    companion object {
        const val KEY_THEME_PREF = "KEY_THEME_PREF"
        const val KEY_FONT_SIZE_PREF = "KEY_FONT_SIZE_PREF"
        const val KEY_LANGUAGE_PREF = "KEY_LANGUAGE_PREF"
        const val KEY_BUBBLE_SIZE_PREF = "KEY_BUBBLE_SIZE_PREF"
        const val KEY_READING_SPEED_PREF = "KEY_READING_SPEED_PREF"
        const val KEY_SPEECH_PITCH_PREF = "KEY_SPEECH_PITCH_PREF"
        const val KEY_INCOGNITO_MODE_PREF = "KEY_INCOGNITO_MODE_PREF"
    }
    var appTheme : String?
        get() = appThemeSharedPreference.getString(KEY_THEME_PREF, Constants.DEVICE_THEME)
        set(value) = appThemeSharedPreference.edit { putString(KEY_THEME_PREF, value)}

    var appFontSize : Int?
        get() = appFontSizeSharedPreference.getInt(KEY_FONT_SIZE_PREF, Constants.S14)
        set(value) = appFontSizeSharedPreference.edit { putInt(KEY_FONT_SIZE_PREF, value!!)}

    var appLanguage : String?
        get() = appLanguageSharedPreference.getString(KEY_LANGUAGE_PREF, "English")
        set(value) = appLanguageSharedPreference.edit { putString(KEY_LANGUAGE_PREF, value!!)}

    var appBubbleSize : String?
        get() = appBubbleSizeSharedPreferences.getString(KEY_BUBBLE_SIZE_PREF, "small")
        set(value) = appBubbleSizeSharedPreferences.edit { putString(KEY_BUBBLE_SIZE_PREF, value!!)}

    var appReadingSpeed : Int?
        get() = appReadingSpeedSharedPreferences.getInt(KEY_READING_SPEED_PREF, 1)
        set(value) = appReadingSpeedSharedPreferences.edit { putInt(KEY_READING_SPEED_PREF, value!!)}

    var appSpeechPitch : Int?
        get() = appSpeechPitchSharedPreferences.getInt(KEY_SPEECH_PITCH_PREF, 1)
        set(value) = appSpeechPitchSharedPreferences.edit { putInt(KEY_SPEECH_PITCH_PREF, value!!)}

    var appIncognitoMode : Boolean?
        get() = appIncognitoModeSharedPreferences.getBoolean(KEY_INCOGNITO_MODE_PREF, false)
        set(value) = appIncognitoModeSharedPreferences.edit { putBoolean(KEY_INCOGNITO_MODE_PREF, value!!)}

}