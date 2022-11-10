package com.easit.aiscanner.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easit.aiscanner.data.Preference
import com.easit.aiscanner.database.ScanDatabase
import com.easit.aiscanner.database.ScanRepository
import com.easit.aiscanner.model.Scan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val preference: Preference
) : AndroidViewModel(application) {

    private val repository : ScanRepository

    init {
        val dao = ScanDatabase.getDatabase(application).getScanDao()
        repository = ScanRepository(dao)
    }

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

    var selectedLanguage
        get() = preference.appLanguage
        set(value) {
            preference.appLanguage = value
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