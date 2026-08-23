package com.easit.aiscanner.ui.imageGround

import com.easit.aiscanner.data.Preference
import com.easit.aiscanner.database.ScanRepository
import com.easit.aiscanner.scannerAI.AnalysisViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ImageGroundViewModel @Inject constructor(
    preference: Preference,
    repository: ScanRepository
) : AnalysisViewModel(preference, repository)
