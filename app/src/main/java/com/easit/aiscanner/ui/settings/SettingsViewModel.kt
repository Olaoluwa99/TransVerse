package com.easit.aiscanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easit.aiscanner.data.Preference
import com.easit.aiscanner.database.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preference: Preference,
    private val repository: ScanRepository
) : ViewModel() {

    var selectedTheme
        get() = preference.appTheme
        set(value) {
            preference.appTheme = value
        }

    var selectedFontSize
        get() = preference.appFontSize
        set(value) {
            preference.appFontSize = value
        }

    var selectedBubbleSize
        get() = preference.appBubbleSize
        set(value) {
            preference.appBubbleSize = value
        }

    var selectedReadingSpeed
        get() = preference.appReadingSpeed
        set(value) {
            preference.appReadingSpeed = value
        }

    var selectedSpeechPitch
        get() = preference.appSpeechPitch
        set(value) {
            preference.appSpeechPitch = value
        }

    var selectedIncognitoMode
        get() = preference.appIncognitoMode
        set(value) {
            preference.appIncognitoMode = value
        }

    fun deleteAllHistory() = viewModelScope.launch {
        repository.deleteAll()
    }
}
