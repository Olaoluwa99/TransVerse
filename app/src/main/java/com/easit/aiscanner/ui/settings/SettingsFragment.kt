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
import com.easit.aiscanner.data.Constants
import com.easit.aiscanner.databinding.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private val shareURL = ""
    private val appURL = "https://play.google.com/store/apps/details?id="

    //GENERAL
    private lateinit var generalText: TextView
    private lateinit var launchModeText: TextView
    private lateinit var launchModeLayout: LinearLayout
    private lateinit var launchModeValueText: TextView
    private lateinit var appThemeLayout: LinearLayout
    private lateinit var appTheme: TextView
    private lateinit var appThemeValueText: TextView
    private lateinit var appLanguageLayout: LinearLayout
    private lateinit var appLanguage: TextView
    private lateinit var appLanguageValueText: TextView
    private lateinit var fontSize: TextView
    private lateinit var fontSizeSeekbar: SeekBar
    private lateinit var fontSizeSeekbarValue: TextView
    private lateinit var bubbleSizeLayout: LinearLayout
    private lateinit var bubbleSize: TextView
    private lateinit var bubbleSizeValueText: TextView

    //AUDIO
    private lateinit var audioHeader: TextView
    private lateinit var readingSpeed: TextView
    private lateinit var readingSpeedSeekbarValue: TextView
    private lateinit var readingSpeedSeekbar: SeekBar
    private lateinit var speechPitch: TextView
    private lateinit var speechPitchSeekbarValue: TextView
    private lateinit var speechPitchSeekbar: SeekBar

    //STORAGE
    private lateinit var goIncognitoCheckbox: CheckBox
    private lateinit var deleteHistoryLayout: LinearLayout
    private lateinit var storageHeader: TextView
    private lateinit var incognitoText: TextView
    private lateinit var incognitoDetailText: TextView
    private lateinit var deleteHistory: TextView
    private lateinit var deleteHistoryDetail: TextView

    //OTHERS
    private lateinit var othersHeader: TextView
    private lateinit var helpSupportLayout: LinearLayout
    private lateinit var privacyPermissionLayout: LinearLayout
    private lateinit var thirdPartyNoticesLayout: LinearLayout
    private lateinit var shareAppLayout: LinearLayout
    private lateinit var rateUsLayout: LinearLayout
    private lateinit var helpSupport: TextView
    private lateinit var privacyPermission: TextView
    private lateinit var thirdPartyNotices: TextView
    private lateinit var shareApp: TextView
    private lateinit var rateUs: TextView

    //LAST
    private lateinit var versionInfo: TextView
    private lateinit var companyName: TextView
    private lateinit var allRightsReserved: TextView


    //MAIN
    private var _binding : FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    val viewModel by activityViewModels<SettingsViewModel>()
    var appFontSize = 0
    var progressReadingSpeed = 0
    var progressSpeechPitch = 0


    //DIALOGS
    private var dialogThemeBinding: DialogThemeBinding? = null
    private var dialogBubbleSizeBinding: DialogBubbleSizeBinding? = null
    private var dialogLanguageBinding: DialogLanguageBinding? = null
    private var dialogDeleteHistoryBinding: DialogDeleteHistoryBinding? = null
    private var themeAlertDialog: AlertDialog? = null
    private var bubbleSizeAlertDialog: AlertDialog? = null
    private var languageAlertDialog: AlertDialog? = null
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
        appFontSize = viewModel.selectedFontSize!!
        progressSpeechPitch = viewModel.selectedSpeechPitch!!
        progressReadingSpeed = viewModel.selectedReadingSpeed!!

        initializations()
        setFontSize()
        //Seekbar
        fontSizeSeekbar.progress = appFontSize
        speechPitchSeekbar.progress = viewModel.selectedSpeechPitch!!
        readingSpeedSeekbar.progress = viewModel.selectedReadingSpeed!!
        //Text
        fontSizeSeekbarValue.text = "$appFontSize"
        bubbleSizeValueText.text = viewModel.selectedBubbleSize
        appLanguageValueText.text = viewModel.selectedLanguage
        goIncognitoCheckbox.isChecked = viewModel.selectedIncognitoMode!!
        //Seekbar text
        readingSpeedSeekbarValue.text = "${viewModel.selectedReadingSpeed!!}"
        speechPitchSeekbarValue.text = "${viewModel.selectedSpeechPitch!!}"



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
    private fun initializations(){
        //GENERAL
        generalText = binding.generalText
        launchModeText = binding.launchModeText
        launchModeLayout = binding.launchModeLayout
        launchModeValueText = binding.launchModeValueText
        appThemeLayout = binding.appThemeLayout
        appTheme = binding.appTheme
        appThemeValueText = binding.appThemeValueText
        appLanguageLayout = binding.appLanguageLayout
        appLanguage = binding.appLanguage
        appLanguageValueText = binding.appLanguageValueText
        fontSize = binding.fontSize
        fontSizeSeekbar = binding.fontSizeSeekbar
        fontSizeSeekbarValue = binding.fontSizeSeekbarValue
        bubbleSizeLayout = binding.bubbleSizeLayout
        bubbleSize = binding.bubbleSize
        bubbleSizeValueText = binding.bubbleSizeValueText

        //AUDIO
        audioHeader = binding.audioHeader
        readingSpeedSeekbar = binding.readingSpeedSeekbar
        speechPitchSeekbar = binding.speechPitchSeekbar
        readingSpeed = binding.readingSpeed
        readingSpeedSeekbarValue = binding.readingSpeedSeekbarValue
        speechPitch = binding.speechPitch
        speechPitchSeekbarValue = binding.speechPitchSeekbarValue
        storageHeader = binding.storageHeader
        incognitoText = binding.incognitoText
        incognitoDetailText = binding.incognitoDetailText
        deleteHistory = binding.deleteHistory
        deleteHistoryDetail = binding.deleteHistoryDetail

        //STORAGE
        goIncognitoCheckbox = binding.goIncognitoCheckbox
        deleteHistoryLayout = binding.deleteHistoryLayout

        //OTHERS
        othersHeader = binding.othersHeader
        helpSupport = binding.helpSupport
        privacyPermission = binding.privacyPermission
        thirdPartyNotices = binding.thirdPartyNotices
        shareApp = binding.shareApp
        rateUs = binding.rateUs
        helpSupportLayout = binding.helpSupportLayout
        privacyPermissionLayout = binding.privacyPermissionLayout
        thirdPartyNoticesLayout = binding.thirdPartyNoticesLayout
        shareAppLayout = binding.shareAppLayout
        rateUsLayout = binding.rateUsLayout
        //
        versionInfo = binding.versionInfo
        companyName = binding.companyName
        allRightsReserved = binding.allRightsReserved

    }
    private fun generalListeners(){
        launchModeLayout.setOnClickListener{

        }
        appThemeLayout.setOnClickListener{
            showThemeDialog()
        }
        appLanguageLayout.setOnClickListener {
            showLanguageChooserDialog()
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
            //findNavController().navigate(R.id.action_settingsFragment_to_helpFragment)
            Toast.makeText(requireContext(), "Coming soon...", Toast.LENGTH_SHORT).show()
        }
        privacyPermissionLayout.setOnClickListener {
            val url = "https://olaoluwa99.github.io/privacy-policy-scanner/"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            startActivity(intent)
        }
        thirdPartyNoticesLayout.setOnClickListener {
            //findNavController().navigate(R.id.action_settingsFragment_to_thirdPartyNoticeFragment)
        }
        shareAppLayout.setOnClickListener {
            //Toast.makeText(requireContext(), "Share stuff", Toast.LENGTH_SHORT).show()
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_SUBJECT, "Share App")
            intent.putExtra(Intent.EXTRA_TEXT, "Lost in translation? Not anymore! Dive into global chats with TransVerse." +
                    " Download now for text, speech, and even joke translations. Let the linguistic fun commence!" + shareURL + requireActivity().packageName)
            startActivity(Intent.createChooser(intent, "Share App Via..."))
        }
        rateUsLayout.setOnClickListener {
            //Toast.makeText(requireContext(), "Open play-store Intent", Toast.LENGTH_SHORT).show()
            try {
                val rateIntent = Intent(Intent.ACTION_VIEW)
                rateIntent.data = Uri.parse(appURL + requireActivity().packageName)
                rateIntent.setPackage("com.android.vending")
                startActivity(rateIntent)
            } catch (e: ActivityNotFoundException) {
                val rateIntent = Intent(Intent.ACTION_VIEW)
                rateIntent.data = Uri.parse(appURL + requireActivity().packageName)
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
        appLanguage.textSize = appFontSize.toFloat()
        appLanguageValueText.textSize = appFontSize.toFloat()
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
            Constants.VERY_SMALL -> dialogThemeBinding?.systemDefault?.isChecked = true
            Constants.SMALL -> dialogThemeBinding?.light?.isChecked = true
            Constants.MEDIUM -> dialogThemeBinding?.dark?.isChecked = true
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
    private fun showLanguageChooserDialog(){
        dialogLanguageBinding = DialogLanguageBinding.inflate(layoutInflater)
        languageAlertDialog = MaterialAlertDialogBuilder(requireContext()).apply {
            dialogLanguageBinding?.root?.let { setView(it) }
            setCancelable(true)
        }.create()

        when (viewModel.selectedLanguage){
            Constants.SYSTEM_DEFAULT_LANGUAGE -> dialogLanguageBinding?.systemDefault?.isChecked = true
            Constants.ENGLISH -> dialogLanguageBinding?.english?.isChecked = true
            Constants.FRENCH -> dialogLanguageBinding?.french?.isChecked = true
            Constants.DEUTSCH -> dialogLanguageBinding?.deutsch?.isChecked = true
            Constants.JAPANESE -> dialogLanguageBinding?.japanese?.isChecked = true
        }

        dialogLanguageBinding?.systemDefault?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked){
                viewModel.selectedLanguage = Constants.SYSTEM_DEFAULT
                languageAlertDialog?.dismiss()
                appLanguageValueText.text = Constants.SYSTEM_DEFAULT
            }
        }
        dialogLanguageBinding?.english?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked){
                viewModel.selectedLanguage = Constants.ENGLISH
                languageAlertDialog?.dismiss()
                appLanguageValueText.text = Constants.ENGLISH
            }
        }
        dialogLanguageBinding?.french?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked){
                viewModel.selectedLanguage = Constants.FRENCH
                languageAlertDialog?.dismiss()
                appLanguageValueText.text = Constants.FRENCH
            }
        }
        dialogLanguageBinding?.deutsch?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked){
                viewModel.selectedLanguage = Constants.DEUTSCH
                languageAlertDialog?.dismiss()
                appLanguageValueText.text = Constants.DEUTSCH
            }
        }
        dialogLanguageBinding?.japanese?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked){
                viewModel.selectedLanguage = Constants.JAPANESE
                languageAlertDialog?.dismiss()
                appLanguageValueText.text = Constants.JAPANESE
            }
        }
        languageAlertDialog?.show()
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
            Toast.makeText(requireContext(), "All history deleted successfully", Toast.LENGTH_SHORT).show()
        }
        deleteHistoryAlertDialog?.show()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}