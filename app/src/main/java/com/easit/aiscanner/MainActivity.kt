package com.easit.aiscanner

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import com.easit.aiscanner.bottomSheets.ItemClickListener
import com.easit.aiscanner.data.Constants
import com.easit.aiscanner.databinding.ActivityMainBinding
import com.easit.aiscanner.floatingWindow.Floater
import com.easit.aiscanner.floatingWindow.FloatingWindow
import com.easit.aiscanner.floatingWindow.TransparentActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() , ItemClickListener {

    private val CODE_DRAW_OVER_OTHER_APP_PERMISSION = 200

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var toolbarText: TextView
    private lateinit var toolbarSettingsIcon: ImageView
    private lateinit var toolbarBackIcon: ImageView
    val viewModel by viewModels<MainViewModel>()
    var scannedImageURI = ""
    var floatingAudioTranscribedText = ""
    var minimizeAppValue = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        //WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setupTheme()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Minimize app for floating window
        try {
            minimizeAppValue = intent.getStringExtra("minimizeApp").toString()
            if (minimizeAppValue.isNotBlank() && minimizeAppValue != "" && minimizeAppValue != "null"){
                //val y = minimizeAppValue
                //finish()
                MainActivity().moveTaskToBack(true)
                //exitProcess(0)
            }
        }catch (e: Exception){
            Toast.makeText(this, e.message.toString(), Toast.LENGTH_LONG).show()
        }


        toolbarText = binding.textView2
        toolbarSettingsIcon = binding.imageView2
        toolbarBackIcon = binding.backArrow

        //  Navigation controller
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHostFragment.navController
        //appBarConfiguration = AppBarConfiguration(navController.graph)
        //setupActionBarWithNavController(navController, appBarConfiguration)

        //Check for image scanner value
        try {
            scannedImageURI = intent.getStringExtra("ScannedImageURI").toString()
            if (scannedImageURI.isNotBlank() && scannedImageURI != "" && scannedImageURI != "null"){
                val y = scannedImageURI
                findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.action_typeChooserFragment_to_imageGroundFragment)
            }// 20230123050325128
        }catch (e: Exception){
            Toast.makeText(this, e.message.toString(), Toast.LENGTH_LONG).show()
        }

        //Check for audio scanner value
        try {
            floatingAudioTranscribedText = intent.getStringExtra("transcribedText").toString()
            if (floatingAudioTranscribedText.isNotBlank() && floatingAudioTranscribedText != "" && floatingAudioTranscribedText != "null"){
                val y = floatingAudioTranscribedText
                findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.action_typeChooserFragment_to_audioGroundFragment)
            }
        }catch (e: Exception){
            Toast.makeText(this, e.message.toString(), Toast.LENGTH_LONG).show()
        }

        lifecycleScope.launchWhenResumed {
            navController.addOnDestinationChangedListener { _, destination, _ ->
                when (destination.id) {
                    R.id.audioGroundFragment -> {
                        toolbarText.text = getString(R.string.title_audio)
                        toolbarSettingsIcon.visibility = View.VISIBLE
                        toolbarSettingsIcon.setOnClickListener {
                            findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.settingsFragment)
                        }
                        toolbarBackIcon.visibility = View.VISIBLE
                        toolbarBackIcon.setOnClickListener {
                            findNavController(R.id.nav_host_fragment_content_main).popBackStack()
                        }
                    }
                    R.id.imageGroundFragment -> {
                        toolbarText.text = getString(R.string.title_image)
                        toolbarSettingsIcon.visibility = View.VISIBLE
                        toolbarSettingsIcon.setOnClickListener {
                            findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.settingsFragment)
                        }
                        toolbarBackIcon.visibility = View.VISIBLE
                        toolbarBackIcon.setOnClickListener {
                            findNavController(R.id.nav_host_fragment_content_main).popBackStack()
                        }
                    }
                    R.id.barcodeLiveFragment -> {
                        toolbarText.text = getString(R.string.title_audio)
                        toolbarSettingsIcon.visibility = View.VISIBLE
                        toolbarSettingsIcon.setOnClickListener {
                            findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.settingsFragment)
                        }
                        toolbarBackIcon.visibility = View.VISIBLE
                        toolbarBackIcon.setOnClickListener {
                            findNavController(R.id.nav_host_fragment_content_main).popBackStack()
                        }
                    }
                    R.id.textGroundFragment -> {
                        toolbarText.text = getString(R.string.title_text)
                        toolbarSettingsIcon.visibility = View.VISIBLE
                        toolbarSettingsIcon.setOnClickListener {
                            findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.settingsFragment)
                        }
                        toolbarBackIcon.visibility = View.VISIBLE
                        toolbarBackIcon.setOnClickListener {
                            findNavController(R.id.nav_host_fragment_content_main).popBackStack()
                        }
                    }
                    R.id.settingsFragment -> {
                        toolbarText.text = getString(R.string.title_settings)
                        toolbarSettingsIcon.visibility = View.INVISIBLE
                        toolbarBackIcon.visibility = View.VISIBLE
                        toolbarBackIcon.setOnClickListener {
                            findNavController(R.id.nav_host_fragment_content_main).popBackStack()
                        }
                    }
                    R.id.typeChooserFragment -> {
                        toolbarText.text = getString(R.string.title_home)
                        toolbarSettingsIcon.visibility = View.VISIBLE
                        toolbarSettingsIcon.setOnClickListener {
                            findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.settingsFragment)
                        }
                        toolbarBackIcon.visibility = View.GONE
                        toolbarBackIcon.setOnClickListener {
                            findNavController(R.id.nav_host_fragment_content_main).popBackStack()
                        }
                    }
                    R.id.helpFragment -> {
                        toolbarText.text = getString(R.string.title_help)
                        toolbarSettingsIcon.visibility = View.INVISIBLE
                        toolbarSettingsIcon.setOnClickListener {
                            findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.settingsFragment)
                        }
                        toolbarBackIcon.visibility = View.VISIBLE
                        toolbarBackIcon.setOnClickListener {
                            findNavController(R.id.nav_host_fragment_content_main).popBackStack()
                        }
                    }
                    R.id.privacyPolicyFragment -> {
                        toolbarText.text = getString(R.string.title_privacy_policy)
                        toolbarSettingsIcon.visibility = View.INVISIBLE
                        toolbarSettingsIcon.setOnClickListener {
                            findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.settingsFragment)
                        }
                        toolbarBackIcon.visibility = View.VISIBLE
                        toolbarBackIcon.setOnClickListener {
                            findNavController(R.id.nav_host_fragment_content_main).popBackStack()
                        }
                    }
                    R.id.thirdPartyNoticeFragment -> {
                        toolbarText.text = getString(R.string.title_third_party)
                        toolbarSettingsIcon.visibility = View.INVISIBLE
                        toolbarSettingsIcon.setOnClickListener {
                            findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.settingsFragment)
                        }
                        toolbarBackIcon.visibility = View.VISIBLE
                        toolbarBackIcon.setOnClickListener {
                            findNavController(R.id.nav_host_fragment_content_main).popBackStack()
                        }
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.settingsFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
    override fun onItemClick(item: String?) {
        TODO("Not yet implemented")
    }

    private fun setupTheme(){
        when (viewModel.selectedTheme){
            Constants.DEVICE_THEME -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            Constants.LIGHT_THEME -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            Constants.DARK_THEME -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
    }

    fun test(){

    }

    //FLOATER
    fun launchScanner(){
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)

            Toast.makeText(this, "Please Grant STORAGE Permission", Toast.LENGTH_SHORT).show()
            requestStoragePermission()
        }

        checkOverlayPermission()
        // start projection
        val i = Intent(this, TransparentActivity::class.java)
        startActivity(i)

        /*
        // stop projection
        val stopButton: Button = findViewById<Button>(R.id.stopButton)
        stopButton.setOnClickListener {
            stopServiceIfRunning()
        }*/
    }

    //Permission STORAGE
    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ),
            PackageManager.PERMISSION_GRANTED
        )
    }

    //Permission OVERLAY
    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please Grant Overlay Permission 3", Toast.LENGTH_SHORT).show()

            //If the draw over permission is not available open the settings screen
            //to grant the permission.
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(
                intent,
                CODE_DRAW_OVER_OTHER_APP_PERMISSION
            )
        } else {
            Toast.makeText(this, "Service should start 1", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (FloatingWindow::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun stopServiceIfRunning() {
        if (isServiceRunning()) {
            stopService(Intent(this, FloatingWindow::class.java))
        }
    }

    fun launchFloater(){
        checkOverlayPermission()
        startService(Intent(this, Floater::class.java))
    }


}