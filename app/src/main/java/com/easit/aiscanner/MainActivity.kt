package com.easit.aiscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.easit.aiscanner.data.Constants
import com.easit.aiscanner.databinding.ActivityMainBinding
import com.easit.aiscanner.floatingWindow.Floater
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val viewModel by viewModels<MainViewModel>()
    private var quickCaptureRequested = false

    var scannedImageURI: String = ""
    var floatingAudioTranscribedText: String = ""
    var incomingText: String = ""

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
        super.onCreate(savedInstanceState)
        setupTheme()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHost.navController
        appBarConfiguration = AppBarConfiguration(navController.graph)

        configureToolbar()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun configureToolbar() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.textView2.text = destination.label ?: getString(R.string.app_name)
            val isHome = destination.id == R.id.typeChooserFragment
            binding.backArrow.apply {
                visibility = if (isHome) View.INVISIBLE else View.VISIBLE
                setImageResource(R.drawable.ic_baseline_arrow_back_24)
                setOnClickListener { navController.navigateUp() }
            }
            binding.imageView2.apply {
                visibility = if (destination.id == R.id.settingsFragment) View.INVISIBLE else View.VISIBLE
                setOnClickListener {
                    if (navController.currentDestination?.id != R.id.settingsFragment) {
                        navController.navigate(R.id.settingsFragment)
                    }
                }
            }
        }
    }

    private fun handleIntent(source: Intent) {
        scannedImageURI = source.getStringExtra(EXTRA_SCANNED_IMAGE).orEmpty()
        floatingAudioTranscribedText = source.getStringExtra(EXTRA_TRANSCRIBED_TEXT).orEmpty()
        source.removeExtra(EXTRA_SCANNED_IMAGE)
        source.removeExtra(EXTRA_TRANSCRIBED_TEXT)

        when (source.action) {
            Intent.ACTION_PROCESS_TEXT -> {
                incomingText = source.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
                source.removeExtra(Intent.EXTRA_PROCESS_TEXT)
            }
            Intent.ACTION_SEND -> {
                handleSharedContent(source)
                source.removeExtra(Intent.EXTRA_TEXT)
                source.removeExtra(Intent.EXTRA_STREAM)
            }
        }
        source.action = null

        when {
            scannedImageURI.isNotBlank() -> navigateFromHome(R.id.imageGroundFragment)
            floatingAudioTranscribedText.isNotBlank() -> navigateFromHome(R.id.audioGroundFragment)
            incomingText.isNotBlank() -> navigateFromHome(R.id.textGroundFragment)
        }

        if (source.getBooleanExtra(EXTRA_MINIMIZE_APP, false)) {
            source.removeExtra(EXTRA_MINIMIZE_APP)
            moveTaskToBack(true)
        }
    }

    private fun handleSharedContent(source: Intent) {
        val type = source.type.orEmpty()
        if (type.startsWith("image/")) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                source.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                source.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            scannedImageURI = uri?.toString().orEmpty()
        } else {
            incomingText = source.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        }
    }

    private fun navigateFromHome(destinationId: Int) {
        if (!::navController.isInitialized) return
        if (navController.currentDestination?.id != R.id.typeChooserFragment) {
            navController.popBackStack(R.id.typeChooserFragment, false)
        }
        if (navController.currentDestination?.id == R.id.typeChooserFragment) {
            navController.navigate(destinationId)
        }
    }

    fun launchScanner() {
        requestQuickCapture()
    }

    fun launchFloater() {
        requestQuickCapture()
    }

    private fun requestQuickCapture() {
        quickCaptureRequested = true
        if (!Settings.canDrawOverlays(this)) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(settingsIntent)
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

    private fun setupTheme() {
        val mode = when (viewModel.selectedTheme) {
            Constants.LIGHT_THEME -> AppCompatDelegate.MODE_NIGHT_NO
            Constants.DARK_THEME -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()

    companion object {
        const val EXTRA_SCANNED_IMAGE = "ScannedImageURI"
        const val EXTRA_TRANSCRIBED_TEXT = "transcribedText"
        const val EXTRA_MINIMIZE_APP = "minimizeApp"
    }
}
