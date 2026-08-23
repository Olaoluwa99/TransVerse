package com.easit.aiscanner.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.easit.aiscanner.R
import com.easit.aiscanner.BuildConfig
import com.easit.aiscanner.MainActivity
import com.easit.aiscanner.data.Constants
import com.easit.aiscanner.databinding.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt
import java.time.Year

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private val appURL = "https://play.google.com/store/apps/details?id="

    //GENERAL
    private val generalText get() = binding.generalText
    private val launchModeText get() = binding.launchModeText
    private val launchModeLayout get() = binding.launchModeLayout
    private val launchModeValueText get() = binding.launchModeValueText
    private val appThemeLayout get() = binding.appThemeLayout
    private val appTheme get() = binding.appTheme
    private val appThemeValueText get() = binding.appThemeValueText
    private val fontSize get() = binding.fontSize
    private val fontSizeSeekbar get() = binding.fontSizeSeekbar
    private val fontSizeSeekbarValue get() = binding.fontSizeSeekbarValue
    private val bubbleSizeLayout get() = binding.bubbleSizeLayout
    private val bubbleSize get() = binding.bubbleSize
    private val bubbleSizeValueText get() = binding.bubbleSizeValueText

    //AUDIO
    private val audioHeader get() = binding.audioHeader
    private val readingSpeed get() = binding.readingSpeed
    private val readingSpeedSeekbarValue get() = binding.readingSpeedSeekbarValue
    private val readingSpeedSeekbar get() = binding.readingSpeedSeekbar
    private val speechPitch get() = binding.speechPitch
    private val speechPitchSeekbarValue get() = binding.speechPitchSeekbarValue
    private val speechPitchSeekbar get() = binding.speechPitchSeekbar

    //STORAGE
    private val goIncognitoCheckbox get() = binding.goIncognitoCheckbox
    private val deleteHistoryLayout get() = binding.deleteHistoryLayout
    private val storageHeader get() = binding.storageHeader
    private val incognitoText get() = binding.incognitoText
    private val incognitoDetailText get() = binding.incognitoDetailText
    private val deleteHistory get() = binding.deleteHistory
    private val deleteHistoryDetail get() = binding.deleteHistoryDetail

    //OTHERS
    private val othersHeader get() = binding.othersHeader
    private val helpSupportLayout get() = binding.helpSupportLayout
    private val privacyPermissionLayout get() = binding.privacyPermissionLayout
    private val thirdPartyNoticesLayout get() = binding.thirdPartyNoticesLayout
    private val shareAppLayout get() = binding.shareAppLayout
    private val rateUsLayout get() = binding.rateUsLayout
    private val helpSupport get() = binding.helpSupport
    private val privacyPermission get() = binding.privacyPermission
    private val thirdPartyNotices get() = binding.thirdPartyNotices
    private val shareApp get() = binding.shareApp
    private val rateUs get() = binding.rateUs

    //LAST
    private val versionInfo get() = binding.versionInfo
    private val companyName get() = binding.companyName
    private val allRightsReserved get() = binding.allRightsReserved


    //MAIN
    private var _binding : FragmentSettingsBinding? = null
    private val binding get() = requireNotNull(_binding) {
        "Binding accessed outside the view lifecycle"
    }
    val viewModel by activityViewModels<SettingsViewModel>()
    var appFontSize = 0
    var progressReadingSpeed = 0
    var progressSpeechPitch = 0


    //DIALOGS
    private var dialogThemeBinding: DialogThemeBinding? = null
    private var dialogBubbleSizeBinding: DialogBubbleSizeBinding? = null
    private var dialogDeleteHistoryBinding: DialogDeleteHistoryBinding? = null
    private var themeAlertDialog: AlertDialog? = null
    private var bubbleSizeAlertDialog: AlertDialog? = null
    private var deleteHistoryAlertDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appFontSize = viewModel.selectedFontSize
        progressSpeechPitch = viewModel.selectedSpeechPitch
        progressReadingSpeed = viewModel.selectedReadingSpeed

        setFontSize()
        //Seekbar
        fontSizeSeekbar.progress = appFontSize
        speechPitchSeekbar.progress = viewModel.selectedSpeechPitch
        readingSpeedSeekbar.progress = viewModel.selectedReadingSpeed
        //Text
        fontSizeSeekbarValue.text = "$appFontSize"
        bubbleSizeValueText.text = viewModel.selectedBubbleSize
        goIncognitoCheckbox.isChecked = viewModel.selectedIncognitoMode
        //Seekbar text
        readingSpeedSeekbarValue.text = viewModel.selectedReadingSpeed.toString()
        speechPitchSeekbarValue.text = viewModel.selectedSpeechPitch.toString()
        versionInfo.text = getString(R.string.version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        companyName.text = getString(R.string.copyright_format, Year.now().value)



        when (viewModel.selectedTheme){
            Constants.DEVICE_THEME -> appThemeValueText.text = Constants.SYSTEM_DEFAULT
            Constants.LIGHT_THEME -> appThemeValueText.text = Constants.LIGHT
            Constants.DARK_THEME -> appThemeValueText.text = Constants.DARK
        }

        generalListeners()
        audioListeners()
        storageListeners()
        othersListeners()
    }
    private fun generalListeners(){
        launchModeLayout.setOnClickListener{
            (activity as? MainActivity)?.launchFloater()
        }
        appThemeLayout.setOnClickListener{
            showThemeDialog()
        }
        //var seekValue = viewModel.selectedFontSize
        fontSizeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                appFontSize = progress
                fontSizeSeekbarValue.text = "$progress"
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                viewModel.selectedFontSize = appFontSize
                setFontSize()
            }

        })
        bubbleSizeLayout.setOnClickListener {
            showBubbleSizeDialog()
        }
    }
    private fun audioListeners(){
        readingSpeedSeekbar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                val step = 10
                val rangeProgress = ((progress.toDouble() / step.toDouble()).roundToInt() * step)
                viewModel.selectedReadingSpeed = rangeProgress
                progressReadingSpeed = rangeProgress
                readingSpeedSeekbarValue.text = "$rangeProgress"
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }

        })
        speechPitchSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(p0: SeekBar?, progress: Int, p2: Boolean) {
                val step = 10
                val rangeProgress = ((progress.toDouble() / step.toDouble()).roundToInt() * step)
                viewModel.selectedSpeechPitch = rangeProgress
                progressSpeechPitch = rangeProgress
                speechPitchSeekbarValue.text = "$rangeProgress"
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }

        })
    }
    private fun storageListeners(){
        goIncognitoCheckbox.setOnCheckedChangeListener { compoundButton, isChecked ->
            viewModel.selectedIncognitoMode = isChecked
        }
        deleteHistoryLayout.setOnClickListener {
            showDeleteHistoryDialog()
        }
    }
    private fun othersListeners(){
        helpSupportLayout.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_helpFragment)
        }
        privacyPermissionLayout.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_privacyPolicyFragment)
        }
        thirdPartyNoticesLayout.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_thirdPartyNoticeFragment)
        }
        shareAppLayout.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_app_subject))
            intent.putExtra(
                Intent.EXTRA_TEXT,
                getString(R.string.share_app_message, appURL + requireActivity().packageName.removeSuffix(".debug"))
            )
            startActivity(Intent.createChooser(intent, getString(R.string.share_app_chooser)))
        }
        rateUsLayout.setOnClickListener {
            try {
                val rateIntent = Intent(Intent.ACTION_VIEW)
                rateIntent.data = Uri.parse(appURL + requireActivity().packageName.removeSuffix(".debug"))
                rateIntent.setPackage("com.android.vending")
                startActivity(rateIntent)
            } catch (e: ActivityNotFoundException) {
                val rateIntent = Intent(Intent.ACTION_VIEW)
                rateIntent.data = Uri.parse(appURL + requireActivity().packageName.removeSuffix(".debug"))
                startActivity(rateIntent)
            }
        }
    }
    private fun setFontSize(){
        //
        generalText.textSize = appFontSize.toFloat()
        launchModeText.textSize = appFontSize.toFloat()

        launchModeValueText.textSize = appFontSize.toFloat()
        appTheme.textSize = appFontSize.toFloat()
        appThemeValueText.textSize = appFontSize.toFloat()
        fontSize.textSize = appFontSize.toFloat()
        fontSizeSeekbarValue.textSize = appFontSize.toFloat()
        bubbleSize.textSize = appFontSize.toFloat()
        bubbleSizeValueText.textSize = appFontSize.toFloat()
        //
        audioHeader.textSize = appFontSize.toFloat()
        readingSpeed.textSize = appFontSize.toFloat()
        readingSpeedSeekbarValue.textSize = appFontSize.toFloat()
        speechPitch.textSize = appFontSize.toFloat()
        speechPitchSeekbarValue.textSize = appFontSize.toFloat()
        storageHeader.textSize = appFontSize.toFloat()
        incognitoText.textSize = appFontSize.toFloat()
        incognitoDetailText.textSize = appFontSize.toFloat()
        deleteHistory.textSize = appFontSize.toFloat()
        deleteHistoryDetail.textSize = appFontSize.toFloat()
        //
        othersHeader.textSize = appFontSize.toFloat()
        helpSupport.textSize = appFontSize.toFloat()
        privacyPermission.textSize = appFontSize.toFloat()
        thirdPartyNotices.textSize = appFontSize.toFloat()
        shareApp.textSize = appFontSize.toFloat()
        rateUs.textSize = appFontSize.toFloat()
        //
        versionInfo.textSize = appFontSize.toFloat()
        companyName.textSize = appFontSize.toFloat()
        allRightsReserved.textSize = appFontSize.toFloat()
    }
    private fun showThemeDialog(){
        dialogThemeBinding = DialogThemeBinding.inflate(layoutInflater)
        themeAlertDialog = MaterialAlertDialogBuilder(requireContext()).apply {
            dialogThemeBinding?.root?.let { setView(it) }
            setCancelable(true)
        }.create()

        when (viewModel.selectedTheme){
            Constants.DEVICE_THEME -> dialogThemeBinding?.systemDefault?.isChecked = true
            Constants.LIGHT_THEME -> dialogThemeBinding?.light?.isChecked = true
            Constants.DARK_THEME -> dialogThemeBinding?.dark?.isChecked = true
        }

        dialogThemeBinding?.systemDefault?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked){
                viewModel.selectedTheme = Constants.DEVICE_THEME
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                themeAlertDialog?.dismiss()
                appThemeValueText.text = Constants.SYSTEM_DEFAULT
            }
        }
        dialogThemeBinding?.light?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked){
                viewModel.selectedTheme = Constants.LIGHT_THEME
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                themeAlertDialog?.dismiss()
                appThemeValueText.text = Constants.LIGHT
            }
        }
        dialogThemeBinding?.dark?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked){
                viewModel.selectedTheme = Constants.DARK_THEME
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                themeAlertDialog?.dismiss()
                appThemeValueText.text = Constants.DARK
            }
        }
        themeAlertDialog?.show()
    }
    private fun showBubbleSizeDialog(){
        dialogBubbleSizeBinding = DialogBubbleSizeBinding.inflate(layoutInflater)
        bubbleSizeAlertDialog = MaterialAlertDialogBuilder(requireContext()).apply {
            dialogBubbleSizeBinding?.root?.let { setView(it) }
            setCancelable(true)
        }.create()
        when (viewModel.selectedBubbleSize){
            Constants.VERY_SMALL -> dialogBubbleSizeBinding?.verySmall?.isChecked = true
            Constants.SMALL -> dialogBubbleSizeBinding?.small?.isChecked = true
            Constants.MEDIUM -> dialogBubbleSizeBinding?.medium?.isChecked = true
            Constants.LARGE -> dialogBubbleSizeBinding?.large?.isChecked = true
        }
        dialogBubbleSizeBinding?.verySmall?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked){
                viewModel.selectedBubbleSize = Constants.VERY_SMALL
                bubbleSizeAlertDialog?.dismiss()
                bubbleSizeValueText.text = Constants.VERY_SMALL
            }
        }
        dialogBubbleSizeBinding?.small?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked){
                viewModel.selectedBubbleSize = Constants.SMALL
                bubbleSizeAlertDialog?.dismiss()
                bubbleSizeValueText.text = Constants.SMALL
            }
        }
        dialogBubbleSizeBinding?.medium?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked){
                viewModel.selectedBubbleSize = Constants.MEDIUM
                bubbleSizeAlertDialog?.dismiss()
                bubbleSizeValueText.text = Constants.MEDIUM
            }
        }
        dialogBubbleSizeBinding?.large?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked){
                viewModel.selectedBubbleSize = Constants.LARGE
                bubbleSizeAlertDialog?.dismiss()
                bubbleSizeValueText.text = Constants.LARGE
            }
        }
        bubbleSizeAlertDialog?.show()
    }
    private fun showDeleteHistoryDialog(){
        dialogDeleteHistoryBinding = DialogDeleteHistoryBinding.inflate(layoutInflater)
        deleteHistoryAlertDialog = MaterialAlertDialogBuilder(requireContext()).apply {
            dialogDeleteHistoryBinding?.root?.let { setView(it) }
            setCancelable(true)
        }.create()

        dialogDeleteHistoryBinding?.confirmButton?.setOnClickListener {
            viewModel.deleteAllHistory()
            deleteHistoryAlertDialog?.dismiss()
            Snackbar.make(binding.root, R.string.history_deleted, Snackbar.LENGTH_LONG).show()
        }
        deleteHistoryAlertDialog?.show()
    }
    override fun onDestroyView() {
        themeAlertDialog?.dismiss()
        bubbleSizeAlertDialog?.dismiss()
        deleteHistoryAlertDialog?.dismiss()
        themeAlertDialog = null
        bubbleSizeAlertDialog = null
        deleteHistoryAlertDialog = null
        dialogThemeBinding = null
        dialogBubbleSizeBinding = null
        dialogDeleteHistoryBinding = null
        _binding = null
        super.onDestroyView()
    }
}
