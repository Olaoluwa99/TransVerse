package com.easit.aiscanner.ui.typeChooser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.easit.aiscanner.data.Preference
import javax.inject.Inject

class HistoryViewModel @Inject constructor(
    application: Application,
    private val preference: Preference
) : AndroidViewModel(application) {

    var selectedFontSize
        get() = preference.appFontSize
        set(value) {
            preference.appFontSize = value
        }

    var selectedIncognitoMode
        get() = preference.appIncognitoMode
        set(value) {
            preference.appIncognitoMode = value
        }
}