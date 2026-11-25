package com.easit.aiscanner

import android.app.Application
import android.util.Log
import com.easit.aiscanner.data.AppearanceStore
import com.easit.aiscanner.database.CaptureFileStore
import com.easit.aiscanner.scannerAI.MlKitModules
import com.easit.aiscanner.ui.theme.applyAsDefaultNightMode
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BaseApp : Application() {

    @Inject
    lateinit var captureFileStore: CaptureFileStore

    @Inject
    lateinit var appearanceStore: AppearanceStore

    @Inject
    lateinit var mlKitModules: MlKitModules

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Before any activity starts, so AppCompat screens open in the saved theme.
        appearanceStore.settings.value.themeMode.applyAsDefaultNightMode()
        // Covers installs where the install-time download didn't happen (e.g. sideloaded builds).
        // Best effort only: an ML Kit problem must never stop the app from launching.
        try {
            mlKitModules.prefetch()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not prefetch ML Kit modules", e)
        }

        // No capture screen is open yet, so any capture file not referenced by saved history
        // (incognito captures, unsaved captures, interrupted deletions) can be removed safely.
        applicationScope.launch {
            try {
                captureFileStore.deleteOrphanedCaptureFiles()
            } catch (e: Exception) {
                Log.w(TAG, "Capture file cleanup failed", e)
            }
        }
    }

    private companion object {
        const val TAG = "BaseApp"
    }
}
