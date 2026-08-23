package com.easit.aiscanner

import androidx.lifecycle.ViewModel
import com.easit.aiscanner.data.Preference
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preference: Preference
) : ViewModel() {

    val selectedTheme
        get() = preference.appTheme
}
