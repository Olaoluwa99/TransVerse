package com.easit.aiscanner.ui.audioGround

import com.easit.aiscanner.data.Preference
import com.easit.aiscanner.database.ScanRepository
import com.easit.aiscanner.scannerAI.AnalysisViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AudioGroundViewModel @Inject constructor(
    private val preference: Preference,
    repository: ScanRepository
) : AnalysisViewModel(preference, repository) {
    val selectedSpeechPitch: Int
        get() = preference.appSpeechPitch

    val selectedReadingSpeed: Int
        get() = preference.appReadingSpeed
}
