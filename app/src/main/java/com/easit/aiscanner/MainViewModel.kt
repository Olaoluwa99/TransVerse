package com.easit.aiscanner

import androidx.lifecycle.ViewModel
import com.easit.aiscanner.data.AppearanceSettings
import com.easit.aiscanner.data.AppearanceStore
import com.easit.aiscanner.ui.app.IncomingContent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Activity-level state: how the app looks and content waiting for the Translate screen. */
@HiltViewModel
class MainViewModel @Inject constructor(
    appearanceStore: AppearanceStore,
) : ViewModel() {

    val appearance: StateFlow<AppearanceSettings> = appearanceStore.settings

    private val _incoming = MutableStateFlow<IncomingContent?>(null)
    val incoming: StateFlow<IncomingContent?> = _incoming.asStateFlow()

    /** Queues [content] for the Translate screen, replacing anything not yet taken. */
    fun offerIncoming(content: IncomingContent) {
        _incoming.value = content
    }

    fun consumeIncoming() {
        _incoming.value = null
    }
}
