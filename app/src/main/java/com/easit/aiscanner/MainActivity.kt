package com.easit.aiscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.easit.aiscanner.floatingWindow.Floater
import com.easit.aiscanner.ui.app.IncomingContent
import com.easit.aiscanner.ui.app.TransVerseApp
import com.easit.aiscanner.ui.theme.TransVerseTheme
import com.easit.aiscanner.ui.theme.applyAsDefaultNightMode
import com.easit.aiscanner.ui.theme.isDarkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel by viewModels<MainViewModel>()
    private var quickCaptureRequested = false

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                ensureNotificationPermission()
            } else {
                quickCaptureRequested = false
                Toast.makeText(this, R.string.overlay_permission_denied, Toast.LENGTH_LONG).show()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, R.string.notification_permission_explanation, Toast.LENGTH_LONG)
                    .show()
            }
            continueOverlayAction()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Night mode is applied in BaseApp; ViewModels can't be created before super.onCreate.
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            val incoming by viewModel.incoming.collectAsStateWithLifecycle()
            val darkTheme = appearance.isDarkTheme()

            // Keeps AppCompat screens in step when the theme is changed in Settings.
            LaunchedEffect(appearance.themeMode) { appearance.themeMode.applyAsDefaultNightMode() }
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
                onDispose {}
            }

            TransVerseTheme(settings = appearance) {
                TransVerseApp(
                    incoming = incoming,
                    onIncomingConsumed = viewModel::consumeIncoming,
                    onOfferIncoming = viewModel::offerIncoming,
                    onStartQuickCapture = ::requestQuickCapture,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Turns share-sheet, Process Text and overlay-capture intents into [IncomingContent]. */
    private fun handleIntent(source: Intent) {
        val content: IncomingContent? = when {
            source.hasExtra(EXTRA_SCANNED_IMAGE) ->
                source.getStringExtra(EXTRA_SCANNED_IMAGE)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { IncomingContent.ScreenCapture("SCAN.$it.jpg") }
            source.hasExtra(EXTRA_TRANSCRIBED_TEXT) ->
                source.getStringExtra(EXTRA_TRANSCRIBED_TEXT)
                    ?.takeIf { it.isNotBlank() }
                    ?.let(IncomingContent::Speech)
            source.action == Intent.ACTION_PROCESS_TEXT ->
                source.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                    ?.toString()?.takeIf { it.isNotBlank() }
                    ?.let(IncomingContent::Text)
            source.action == Intent.ACTION_SEND && source.type.orEmpty().startsWith("image/") ->
                IntentCompat.getParcelableExtra(source, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let(IncomingContent::SharedImage)
            source.action == Intent.ACTION_SEND ->
                source.getCharSequenceExtra(Intent.EXTRA_TEXT)
                    ?.toString()?.takeIf { it.isNotBlank() }
                    ?.let(IncomingContent::Text)
            else -> null
        }
        content?.let(viewModel::offerIncoming)

        // From the Quick Settings tile or launcher shortcut when a permission is still missing.
        if (source.action == ACTION_START_FLOATING_CAPTURE) requestQuickCapture()

        // Don't replay the same content when the activity is recreated.
        source.removeExtra(EXTRA_SCANNED_IMAGE)
        source.removeExtra(EXTRA_TRANSCRIBED_TEXT)
        source.removeExtra(Intent.EXTRA_PROCESS_TEXT)
        source.removeExtra(Intent.EXTRA_TEXT)
        source.removeExtra(Intent.EXTRA_STREAM)
        source.action = null
    }

    private fun requestQuickCapture() {
        quickCaptureRequested = true
        if (!Settings.canDrawOverlays(this)) {
            overlayPermissionLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        ensureNotificationPermission()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            continueOverlayAction()
        }
    }

    private fun continueOverlayAction() {
        if (quickCaptureRequested) {
            ContextCompat.startForegroundService(this, Intent(this, Floater::class.java))
        }
        quickCaptureRequested = false
    }

    companion object {
        /** Id of a screen capture saved as `SCAN.<id>.jpg` in `filesDir`. */
        const val EXTRA_SCANNED_IMAGE = "ScannedImageURI"
        const val EXTRA_TRANSCRIBED_TEXT = "transcribedText"

        /** Asks for the floating-capture permissions if needed, then starts the bubble. */
        const val ACTION_START_FLOATING_CAPTURE = "com.easit.aiscanner.action.START_FLOATING_CAPTURE"
    }
}
