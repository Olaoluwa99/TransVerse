package com.easit.aiscanner.ui.audioGround

import android.Manifest.permission.RECORD_AUDIO
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.easit.aiscanner.BuildConfig
import com.easit.aiscanner.MainActivity
import com.easit.aiscanner.R
import com.easit.aiscanner.data.Constants
import com.easit.aiscanner.databinding.FragmentAudioGroundBinding
import com.easit.aiscanner.scannerAI.phase2.Language
import com.easit.aiscanner.utils.EntityActions
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.nl.smartreply.SmartReplySuggestion
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@AndroidEntryPoint
class AudioGroundFragment : Fragment() {

    private val seekHandler = Handler(Looper.getMainLooper())
    private var audioHasBeenPrepared = false
    private var mediaPlayer: MediaPlayer? = null
    private var pausedAt = 0

    //BATCH 1
    private val recordButton get() = binding.micState

    //BATCH 2
    private val getDetails get() = binding.getDetails
    private val downloadAudio get() = binding.downloadAudio
    private val shareAudio get() = binding.shareAudio


    //BATCH 3
    private val srcLang get() = binding.srcLang
    private val transcriptTitle get() = binding.transcriptTitle
    private val translationTitle get() = binding.translationTitle
    private val transcriptEditText get() = binding.transcribedEdittext
    private val translationEditText get() = binding.translationEdittext

    //BATCH 4
    private val playAudioImage get() = binding.playAudioImage
    private val clickTextView get() = binding.clickTextView
    private val seekBar get() = binding.seekBar
    private val passText get() = binding.passText
    private val dueText get() = binding.dueText

    //BATCH 5
    private val targetLangSelector get() = binding.targetLangSelector
    private val targetLangSelectorCover get() = binding.targetLangSelectorCover
    private val progressText get() = binding.progressText
    private val progressBar get() = binding.progressBar

    //BATCH 6
    private val smartReplyHeader get() = binding.smartReplyHeader
    private val smartReplyGroup get() = binding.smartReplyGroup
    private val entitiesTitle get() = binding.entitiesTitle
    private val entitiesGroup get() = binding.entitiesGroup

    //  NON VIEWS
    private var textToSpeech: TextToSpeech? = null
    private lateinit var designatedFileName: String

    //
    private val copyTranscript get() = binding.copyTranscript
    private val copyTranslate get() = binding.copyTranslate

    //SYSTEM
    private var _binding: FragmentAudioGroundBinding? = null
    private val binding get() = requireNotNull(_binding) {
        "Binding accessed outside the view lifecycle"
    }
    private val viewModel by viewModels<AudioGroundViewModel>()
    var appFontSize = 0

    private val speechResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val speechText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (result.resultCode == RESULT_OK && speechText.isNotBlank()) {
                _binding?.transcribedEdittext?.setText(speechText)
            }
        }

    private val recordPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && _binding != null) launchSpeechRecognition()
        }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentAudioGroundBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        designatedFileName = getCurrentDateTime()
        appFontSize = viewModel.selectedFontSize
        setFontSize()
        setupTargetLanguageSpinner()
        showValuesForScanner()
        retrieveAndSetDetailsForHistory()
        performSpeechToText()
        playAudioImage.setOnClickListener {
            if (audioHasBeenPrepared){
                playAudioAsHistory()
            }else{
                Snackbar.make(binding.root, R.string.no_audio_available, Snackbar.LENGTH_SHORT).show()
            }
        }
        downloadAudio.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                downloadAudio()
            }else{
                Snackbar.make(binding.root, R.string.audio_download_requires_android_10, Snackbar.LENGTH_LONG).show()
            }
        }
        shareAudio.setOnClickListener {
            shareAudio()
        }
        getFullScan()
        observeSourceLanguage()
        observeTranslatedTextResponse()
        observeModelDownloading()
        observeForReturnedEntities()
        observeForReturnedReplies()
        observeErrors()

        copyTranscript.setOnClickListener {
            saveToClipboard("TRANSCRIPTED_TEXT", transcriptEditText.text.toString())
        }
        copyTranslate.setOnClickListener {
            saveToClipboard("TRANSLATED_TEXT", translationEditText.text.toString())
        }
    }

    private fun setFontSize() {
        //
        clickTextView.textSize = appFontSize.toFloat()
        getDetails.textSize = appFontSize.toFloat()
        transcriptTitle.textSize = (appFontSize + 10).toFloat()
        srcLang.textSize = appFontSize.toFloat()
        transcriptEditText.textSize = appFontSize.toFloat()
        progressText.textSize = appFontSize.toFloat()
        translationTitle.textSize = (appFontSize + 10).toFloat()
        translationEditText.textSize = appFontSize.toFloat()
        passText.textSize = appFontSize.toFloat()
        dueText.textSize = appFontSize.toFloat()
        downloadAudio.textSize = appFontSize.toFloat()
        shareAudio.textSize = appFontSize.toFloat()
        entitiesTitle.textSize = (appFontSize + 10).toFloat()
        smartReplyHeader.textSize = (appFontSize + 10).toFloat()

    }

    private fun showValuesForScanner(){
        val floatingAudioTranscribedText =
            (activity as? MainActivity)?.floatingAudioTranscribedText.orEmpty()
        if (floatingAudioTranscribedText.isNotBlank()){
            transcriptEditText.setText(floatingAudioTranscribedText)
            (activity as? MainActivity)?.floatingAudioTranscribedText = ""
        }
    }

    private fun setupTargetLanguageSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item, viewModel.availableLanguages
        )
        targetLangSelector.adapter = adapter
        targetLangSelector.setSelection(adapter.getPosition(Language("en")))
        targetLangSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                viewModel.targetLang.value = adapter.getItem(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }
    private fun observeModelDownloading(){
        viewModel.modelDownloading.observe(viewLifecycleOwner, androidx.lifecycle.Observer { isDownloading ->
            progressBar.visibility = if (isDownloading) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
            progressText.visibility = progressBar.visibility
        })
    }

    private fun observeTranslatedTextResponse(){
        viewModel.newTranslatedText.observe(viewLifecycleOwner, androidx.lifecycle.Observer { result ->
            translationEditText.setText(result)
            prepareAndSynthesizeAudioToFile()
        })
    }
    private fun observeForReturnedEntities(){
        /**Entities extraction*/
        viewModel.entitiesLiveList.observe(viewLifecycleOwner) { entities ->
            if (entities != null){
                showEntities(entities)
                //stop showing progressbar
            }
        }
    }
    private fun observeForReturnedReplies(){
        /**Smart reply*/
        viewModel.suggestionResultList.observe(viewLifecycleOwner) { suggestions ->
            if (suggestions != null){
                showSmartReply(suggestions)
            }
        }
    }
    private fun observeSourceLanguage(){
        viewModel.sourceLanguageText.observe(viewLifecycleOwner, androidx.lifecycle.Observer {
            srcLang.text = it
        })
    }
    private fun observeErrors() {
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            message?.let { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() }
        }
    }
    private fun getFullScan(){
        getDetails.setOnClickListener {
            viewModel.performLanguageActions(
                id = designatedFileName,
                transcriptText = transcriptEditText.text.toString(),
                scanType = "audio",
                imageUrl = "$designatedFileName.jpg")
        }
    }
    private fun retrieveAndSetDetailsForHistory() {
        val currentScanId = arguments?.getString("selectedScanId").orEmpty()
        if (currentScanId.isNotBlank()) {
            viewModel.getSelectedScanObject(currentScanId)
            viewModel.currentHistoryItem.observe(
                viewLifecycleOwner,
                androidx.lifecycle.Observer { scanObject ->
                    designatedFileName = scanObject.id

                    recordButton.isClickable = false
                    audioHasBeenPrepared = audioFile().exists()

                    transcriptEditText.setText(scanObject.transcriptText)
                    transcriptEditText.isFocusable = false
                    translationEditText.setText(scanObject.translatedText)
                    translationEditText.isFocusable = false

                    getDetails.isClickable = false

                    showEntities(scanObject.entities)
                    //Show replies chips
                    showSmartReplyString(scanObject.smartReplies)
                    //Show source language
                    srcLang.text = scanObject.sourceLanguage
                    //Show translated language spinner
                    targetLangSelector.visibility = View.INVISIBLE
                    targetLangSelectorCover.visibility = View.VISIBLE
                    targetLangSelectorCover.text = scanObject.translatedLanguage
                    //Setup recorded audio

                })
        }
    }

    private fun saveToClipboard(label: String, saveText: String) {
        val clipboard = activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, saveText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
    private fun showSmartReply(suggestionsList: List<SmartReplySuggestion>) {
        smartReplyGroup.removeAllViews()
        suggestionsList.forEach { suggestion ->
            val chip = Chip(requireContext())
            chip.apply {
                text = suggestion.text
                isCheckable = false
                isClickable = true
                chipIcon = resources.getDrawable(R.drawable.ic_baseline_content_copy_24, requireActivity().theme)
                setOnClickListener {view ->
                    val copyText = (view as Chip).text.toString()
                    saveToClipboard("SmartReplyChip", copyText)
                }
            }
            smartReplyGroup.addView(chip)
        }
    }
    private fun showSmartReplyString(suggestionsList: List<String>) {
        smartReplyGroup.removeAllViews()
        suggestionsList.forEach { suggestion ->
            val chip = Chip(requireContext())
            chip.apply {
                text = suggestion
                isCheckable = false
                isClickable = true
                chipIcon = resources.getDrawable(R.drawable.ic_baseline_content_copy_24, requireActivity().theme)
                setOnClickListener {view ->
                    val copyText = (view as Chip).text.toString()
                    saveToClipboard("SmartReplyChip", copyText)
                }
            }
            smartReplyGroup.addView(chip)
        }
    }
    private fun showEntities(entitiesList: List<String>) {
        entitiesGroup.removeAllViews()
        entitiesList.forEach { entity ->
            val chip = Chip(requireContext())
            chip.apply {
                text = entity
                isCheckable = false
                isClickable = true
                chipIcon = resources.getDrawable(R.drawable.ic_baseline_content_copy_24, requireActivity().theme)
                setOnClickListener {view ->
                    val copyText = (view as Chip).text.toString()
                    EntityActions.openOrCopy(requireContext(), copyText)
                }
            }
            entitiesGroup.addView(chip)
        }
    }
    private fun getCurrentDateTime(): String {
        val currentTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
        return currentTime.format(formatter)
    }

    //AUDIO SPECIFIC CODES
    private fun performSpeechToText() {
        recordButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), RECORD_AUDIO) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                recordPermissionLauncher.launch(RECORD_AUDIO)
            } else {
                launchSpeechRecognition()
            }
        }
    }

    private fun launchSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_prompt))
        }
        try {
            speechResultLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.speech_unavailable, Snackbar.LENGTH_LONG).show()
        }
    }
    private fun prepareAndSynthesizeAudioToFile() {
        val text = translationEditText.text?.toString().orEmpty()
        if (text.isBlank()) {
            Snackbar.make(binding.root, R.string.no_audio_available, Snackbar.LENGTH_SHORT).show()
            return
        }

        textToSpeech?.shutdown()
        audioHasBeenPrepared = false
        audioFile().delete()
        textToSpeech = TextToSpeech(requireContext().applicationContext, onInit@ { status ->
            val engine = textToSpeech ?: return@onInit
            if (status != TextToSpeech.SUCCESS) {
                showAudioError()
                return@onInit
            }

            val languageResult = engine.setLanguage(Locale.forLanguageTag(
                viewModel.targetLang.value?.code ?: Locale.getDefault().toLanguageTag()
            ))
            if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                showAudioError()
                return@onInit
            }
            engine.setSpeechRate((viewModel.selectedReadingSpeed / 50f).coerceAtLeast(0.1f))
            engine.setPitch((viewModel.selectedSpeechPitch / 50f).coerceAtLeast(0.1f))
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    activity?.runOnUiThread {
                        val generatedFile = context?.filesDir?.let {
                            File(it, "$AUDIO_FILE_PREFIX$designatedFileName.wav")
                        }
                        audioHasBeenPrepared = generatedFile?.exists() == true
                        _binding?.playAudioImage?.setImageResource(
                            R.drawable.ic_baseline_play_arrow_24
                        )
                    }
                }

                @Deprecated("Deprecated by the platform")
                override fun onError(utteranceId: String?) = showAudioError()

                override fun onError(utteranceId: String?, errorCode: Int) = showAudioError()
            })

            val result = engine.synthesizeToFile(text, Bundle(), audioFile(), TTS_UTTERANCE_ID)
            if (result == TextToSpeech.ERROR) showAudioError()
        })
    }

    private fun showAudioError() {
        activity?.runOnUiThread {
            _binding?.root?.let {
                Snackbar.make(it, R.string.audio_generation_failed, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun audioFile(): File =
        File(requireContext().filesDir, "$AUDIO_FILE_PREFIX$designatedFileName.wav")

    private fun playAudioAsHistory() {
        val currentPlayer = mediaPlayer
        if (currentPlayer?.isPlaying == true) {
            currentPlayer.pause()
            pausedAt = currentPlayer.currentPosition
            playAudioImage.setImageResource(R.drawable.ic_baseline_play_arrow_24)
            return
        }
        if (currentPlayer != null && pausedAt > 0) {
            currentPlayer.start()
            playAudioImage.setImageResource(R.drawable.ic_baseline_pause_24)
            startSeekUpdates()
            return
        }

        val file = audioFile()
        if (!file.exists()) {
            audioHasBeenPrepared = false
            Snackbar.make(binding.root, R.string.no_audio_available, Snackbar.LENGTH_SHORT).show()
            return
        }

        releaseMediaPlayer()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                prepare()
                setOnCompletionListener {
                    pausedAt = 0
                    seekBar.progress = 0
                    playAudioImage.setImageResource(R.drawable.ic_baseline_play_arrow_24)
                }
                start()
            }
            initializeSeekbar()
            playAudioImage.setImageResource(R.drawable.ic_baseline_pause_24)
        } catch (_: IOException) {
            releaseMediaPlayer()
            showAudioError()
        }
    }

    private fun formatTimer(inputTime: Int): String {
        val hours = inputTime / 3600
        val minutes = (inputTime % 3600) / 60
        val seconds = inputTime % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    private fun initializeSeekbar() {
        val player = mediaPlayer ?: return
        seekBar.max = player.duration
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) mediaPlayer?.seekTo(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        startSeekUpdates()
    }

    private fun startSeekUpdates() {
        seekHandler.removeCallbacksAndMessages(null)
        seekHandler.post(object : Runnable {
            override fun run() {
                val player = mediaPlayer ?: return
                if (_binding == null) return
                val position = player.currentPosition
                passText.text = formatTimer(position / 1000)
                dueText.text = "-${formatTimer((player.duration - position) / 1000)}"
                seekBar.progress = position
                if (player.isPlaying) seekHandler.postDelayed(this, SEEK_UPDATE_INTERVAL_MS)
            }
        })
    }

    private fun shareAudio() {
        val file = audioFile()
        if (!file.exists()) {
            Snackbar.make(binding.root, R.string.no_audio_available, Snackbar.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            requireContext(), "${BuildConfig.APPLICATION_ID}.provider", file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = AUDIO_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_audio_chooser)))
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun downloadAudio() {
        val source = audioFile()
        if (!source.exists()) {
            Snackbar.make(binding.root, R.string.no_audio_available, Snackbar.LENGTH_SHORT).show()
            return
        }
        val resolver = requireContext().contentResolver
        viewLifecycleOwner.lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, AUDIO_MIME_TYPE)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MUSIC}/${Constants.APP_NAME}"
                    )
                }
                val destination = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext false
                try {
                    resolver.openOutputStream(destination)?.use { output ->
                        FileInputStream(source).use { input -> input.copyTo(output) }
                    } != null
                } catch (_: IOException) {
                    resolver.delete(destination, null, null)
                    false
                }
            }
            val message = if (saved) R.string.audio_downloaded else R.string.audio_download_failed
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun releaseMediaPlayer() {
        seekHandler.removeCallbacksAndMessages(null)
        mediaPlayer?.release()
        mediaPlayer = null
        pausedAt = 0
    }

    override fun onDestroyView() {
        releaseMediaPlayer()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val AUDIO_FILE_PREFIX = "TRANSVERSE_SPEECH_"
        private const val AUDIO_MIME_TYPE = "audio/wav"
        private const val TTS_UTTERANCE_ID = "translated_speech"
        private const val SEEK_UPDATE_INTERVAL_MS = 250L
    }
}
