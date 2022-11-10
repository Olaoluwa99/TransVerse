package com.easit.aiscanner

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.easit.aiscanner.bottomSheets.ItemClickListener
import com.easit.aiscanner.data.Constants
import com.easit.aiscanner.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() , ItemClickListener {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var toolbarText: TextView
    private lateinit var toolbarSettingsIcon: ImageView
    private lateinit var toolbarBackIcon: ImageView
    val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        //WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setupTheme()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        toolbarText = binding.textView2
        toolbarSettingsIcon = binding.imageView2
        toolbarBackIcon = binding.backArrow
        //setSupportActionBar(binding.toolbar)
        //  Navigation controller
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHostFragment.navController
        //appBarConfiguration = AppBarConfiguration(navController.graph)
        //setupActionBarWithNavController(navController, appBarConfiguration)

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
}