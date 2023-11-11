package com.easit.aiscanner.ui.audioGround

import android.Manifest.permission.RECORD_AUDIO
import android.app.Activity
import android.app.Activity.MODE_PRIVATE
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.easit.aiscanner.BuildConfig
import com.easit.aiscanner.MainActivity
import com.easit.aiscanner.R
import com.easit.aiscanner.data.Constants
import com.easit.aiscanner.databinding.FragmentAudioGroundBinding
import com.easit.aiscanner.scannerAI.phase2.Language
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.nl.smartreply.SmartReplySuggestion
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Runnable
import java.io.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@AndroidEntryPoint
class AudioGroundFragment : Fragment() {

    private val REQUEST_SPEECH_RECORDING = Constants.REQUEST_SPEECH_RECORDING
    private val REQUEST_RECORD_AUDIO = Constants.REQUEST_RECORD_AUDIO
    private val WRITE_EXTERNAL_STORAGE_CODE = Constants.WRITE_EXTERNAL_STORAGE
    private val TEMP_AUDIO_FILE = "poem.wav"
    private val MY_SPEECH = "MYSPEECH"
    private val AI_SPEECH = "AISPEECH"
    private var isMediaRecorderOn = false
    private var audioHasBeenPrepared = false
    //Describes whether audio is playing from database history
    //  or as a new created file.
    private var audioPlayModule = Constants.NEW_AUDIO
    private var pageEntitiesList = mutableListOf<String>()
    private var pageRepliesList = mutableListOf<String>()
    var mMediaPlayer: MediaPlayer? = null
    private var restartAudio = true
    private var pauseLength: Int? = null
    var floatingAudioTranscribedText = ""

    //BATCH 1
    private lateinit var recordButton: ImageView
    private lateinit var recordButtonLayout: CardView

    //BATCH 2
    private lateinit var getDetails: Button
    private lateinit var downloadAudio: Button
    private lateinit var shareAudio: Button


    //BATCH 3
    private lateinit var srcLang: TextView
    private lateinit var transcriptTitle: TextView
    private lateinit var translationTitle: TextView
    private lateinit var transcriptEditText: TextInputEditText
    private lateinit var translationEditText: TextInputEditText

    //BATCH 4
    private lateinit var playAudioCard: CardView
    private lateinit var playAudioImage: ImageView
    private lateinit var clickTextView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var passText: TextView
    private lateinit var dueText: TextView

    //BATCH 5
    private lateinit var targetLangSelector: Spinner
    private lateinit var targetLangSelectorCover: TextView
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar

    //BATCH 6
    private lateinit var smartReplyHeader: TextView
    private lateinit var smartReplyGroup: ChipGroup
    private lateinit var entitiesTitle: TextView
    private lateinit var entitiesGroup: ChipGroup

    //  NON VIEWS
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var designatedFileName: String
    private lateinit var mediaRecorder: MediaRecorder

    //
    private lateinit var copyTranscript: ImageView
    private lateinit var copyTranslate: ImageView

    //SYSTEM
    private var _binding: FragmentAudioGroundBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AudioGroundViewModel
    var appFontSize = 0


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel = ViewModelProvider(this).get(AudioGroundViewModel::class.java)
        _binding = FragmentAudioGroundBinding.inflate(inflater, container, false)
        mediaRecorder = MediaRecorder()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        designatedFileName = getCurrentDateTime()
        appFontSize = viewModel.selectedFontSize!!
        initializations()
        setFontSize()
        setupTargetLanguageSpinner()
        //showValuesForScanner() //Only when app is launched from scanner
        retrieveAndSetDetailsForHistory()
        performSpeechToText()
        playAudioImage.setOnClickListener {
            //Plays the recorded audio in new language
            if (audioHasBeenPrepared){
                playAudioAsHistory()
            }else{
                Toast.makeText(requireContext(), "No translated text available", Toast.LENGTH_SHORT).show()
            }
        }
        downloadAudio.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                downloadAudio()
            }else{
                val storageDir = requireActivity().getExternalFilesDir("${Environment.DIRECTORY_MUSIC}/AIScanner")
                val path = "${requireContext().filesDir.path}/${AI_SPEECH}$designatedFileName.mp4"
                val file = File(path)
                File.createTempFile(designatedFileName, ".mp4", storageDir)
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

        copyTranscript.setOnClickListener {
            saveToClipboard("TRANSCRIPTED_TEXT", transcriptEditText.text.toString())
        }
        copyTranslate.setOnClickListener {
            saveToClipboard("TRANSLATED_TEXT", translationEditText.text.toString())
        }
    }

    private fun initializations() {
        recordButton = binding.micState
        clickTextView = binding.clickTextView
        recordButtonLayout = binding.micLayout
        transcriptTitle = binding.transcriptTitle
        transcriptEditText = binding.transcribedEdittext
        translationTitle = binding.translationTitle
        translationEditText = binding.translationEdittext

        playAudioCard = binding.playAudioCard
        playAudioImage = binding.playAudioImage
        targetLangSelector = binding.targetLangSelector
        targetLangSelectorCover = binding.targetLangSelectorCover

        downloadAudio = binding.downloadAudio
        shareAudio = binding.shareAudio

        smartReplyHeader = binding.smartReplyHeader
        smartReplyGroup = binding.smartReplyGroup
        entitiesTitle = binding.entitiesTitle
        entitiesGroup = binding.entitiesGroup

        progressText = binding.progressText
        progressBar = binding.progressBar
        srcLang = binding.srcLang

        getDetails = binding.getDetails
        seekBar = binding.seekBar
        passText = binding.passText
        dueText = binding.dueText

        copyTranscript = binding.copyTranscript
        copyTranslate = binding.copyTranslate
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
        //TODO Here
        floatingAudioTranscribedText = (activity as MainActivity).floatingAudioTranscribedText
        if (floatingAudioTranscribedText.isNotBlank()){
            transcriptEditText.setText(floatingAudioTranscribedText)
            (activity as MainActivity).floatingAudioTranscribedText = ""
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
                for (entity in entities){
                    pageEntitiesList.add(entity)
                }
                showEntities(entities)
                //stop showing progressbar
            }
        }
    }
    private fun observeForReturnedReplies(){
        /**Smart reply*/
        viewModel.suggestionResultList.observe(viewLifecycleOwner) { suggestions ->
            if (suggestions != null){
                for (suggestion in suggestions){
                    pageRepliesList.add(suggestion.text)
                }
                showSmartReply(suggestions)
            }
        }
    }
    private fun observeSourceLanguage(){
        viewModel.sourceLanguageText.observe(viewLifecycleOwner, androidx.lifecycle.Observer {
            srcLang.text = it
        })
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
        val currentScanId = requireArguments().getString("selectedScanId").toString()
        if (currentScanId != "") {
            viewModel.getSelectedScanObject(currentScanId)
            audioPlayModule = Constants.HISTORY_AUDIO

            viewModel.currentHistoryItem.observe(
                viewLifecycleOwner,
                androidx.lifecycle.Observer { scanObject ->
                    designatedFileName = scanObject.id

                    recordButton.isClickable = false
                    audioHasBeenPrepared = true

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
        Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    private fun showSmartReply(suggestionsList: List<SmartReplySuggestion>) {
        smartReplyGroup.removeAllViews()
        suggestionsList.forEachIndexed { index, suggestion ->
            val chip = Chip(requireContext())
            chip.apply {
                text = suggestion.text
                isCheckable = false
                isClickable = true
                id = index
                chipIcon = resources.getDrawable(R.drawable.ic_baseline_content_copy_24, requireActivity().theme)
                setOnClickListener {view ->
                    val copyText = (view as Chip).text.toString()
                    saveToClipboard("SmartReplyChip", copyText)
                }
            }
            smartReplyGroup.addView(chip as View)
        }
    }
    private fun showSmartReplyString(suggestionsList: List<String>) {
        smartReplyGroup.removeAllViews()
        suggestionsList.forEachIndexed { index, suggestion ->
            val chip = Chip(requireContext())
            chip.apply {
                text = suggestion
                isCheckable = false
                isClickable = true
                id = index
                chipIcon = resources.getDrawable(R.drawable.ic_baseline_content_copy_24, requireActivity().theme)
                setOnClickListener {view ->
                    val copyText = (view as Chip).text.toString()
                    saveToClipboard("SmartReplyChip", copyText)
                }
            }
            smartReplyGroup.addView(chip as View)
        }
    }
    private fun showEntities(entitiesList: List<String>) {
        entitiesGroup.removeAllViews()
        entitiesList.forEachIndexed { index, entity ->
            val chip = Chip(requireContext())
            chip.apply {
                text = entity
                isCheckable = false
                isClickable = true
                id = index
                chipIcon = resources.getDrawable(R.drawable.ic_baseline_content_copy_24, requireActivity().theme)
                setOnClickListener {view ->
                    val copyText = (view as Chip).text.toString()
                    saveToClipboard("EntityExtractChip", copyText)
                }
            }
            entitiesGroup.addView(chip as View)
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
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something!")

            //Record audio to file
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(RECORD_AUDIO),
                    REQUEST_RECORD_AUDIO
                )
            } else {
                Toast.makeText(requireContext(), "Record Permission is granted", Toast.LENGTH_SHORT)
                    .show()
                recordAudioOnClick()
            }

            try {
                activityResultLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(
                    requireContext(),
                    "Speech recognition is not available",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        activityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult? ->
                if (result!!.resultCode == RESULT_OK && result.data != null) {
                    val speechText =
                        result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) as ArrayList<Editable>
                    transcriptEditText.text = speechText[0]
                }
            }
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SPEECH_RECORDING && requestCode == Activity.RESULT_OK) {
            val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (result?.get(0).toString() != "null"){
                transcriptEditText.setText(result?.get(0).toString())
            }
        }
    }
    override fun onResume() {
        super.onResume()
        if (isMediaRecorderOn) {
            mediaRecorder.stop()
            isMediaRecorderOn = false
            recordButton.setImageResource(R.drawable.baseline_mic_24)
        }
    }
    private fun recordAudioOnClick() {
        val path = "${requireContext().filesDir.path}/$MY_SPEECH$designatedFileName.mp4"

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setOutputFile(path)
        mediaRecorder.prepare()
        mediaRecorder.start()
        isMediaRecorderOn = true
        recordButton.setImageResource(R.drawable.ic_baseline_mic_off_24)
        Toast.makeText(requireContext(), "Record started", Toast.LENGTH_SHORT).show()
    }
    private fun prepareAndSynthesizeAudioToFile() {
        textToSpeech =
            TextToSpeech(requireActivity().applicationContext, TextToSpeech.OnInitListener {
                if (textToSpeech.isSpeaking) {
                    textToSpeech.stop()
                    playAudioImage.setImageResource(R.drawable.ic_baseline_play_arrow_24)
                } else {
                    if (it == TextToSpeech.SUCCESS) {
                        textToSpeech.language = Locale.getDefault()
                        var pitch = (viewModel.selectedSpeechPitch!!/50).toFloat()
                        var speed = (viewModel.selectedReadingSpeed!!/50).toFloat()
                        if (pitch < 0.1f){
                            pitch = 0.1f
                        }
                        if (speed < 0.1f){
                            speed = 0.1f
                        }
                        textToSpeech.setSpeechRate(speed)
                        textToSpeech.setPitch(pitch)
                        if (translationEditText.text.toString() == "") {
                            Toast.makeText(
                                requireContext(),
                                "No text available",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            val path =
                                "${requireContext().filesDir.path}/${AI_SPEECH}$designatedFileName.mp4"
                            val param = Bundle()
                            val file = File(path)

                            textToSpeech.synthesizeToFile(
                                translationEditText.text.toString(),
                                param,
                                file,
                                TEMP_AUDIO_FILE
                            )
                            audioHasBeenPrepared = true
                        }
                    }
                }
            })
    }
    private fun playAudioAsNew(){
        if (audioHasBeenPrepared){
            val speechListener = object : UtteranceProgressListener(){
                override fun onStart(p0: String?) {
                    //Code to convert drawable to stop
                    playAudioImage.setImageResource(R.drawable.ic_baseline_stop_24)
                }

                override fun onDone(p0: String?) {
                    //Code to convert drawable to play
                    playAudioImage.setImageResource(R.drawable.ic_baseline_play_arrow_24)
                }

                override fun onError(p0: String?) {
                    //
                    Toast.makeText(requireContext(), p0.toString(), Toast.LENGTH_SHORT).show()
                }
            }
            textToSpeech.setOnUtteranceProgressListener(speechListener)
            textToSpeech.speak(
                translationEditText.text.toString(),
                TextToSpeech.QUEUE_ADD, null
            )
        }else{
            Toast.makeText(
                requireContext(),
                "No translated text available",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    private fun playAudioAsHistory() {

        val uri = Uri.parse("${requireContext().filesDir.path}/${AI_SPEECH}$designatedFileName.mp4")
        if (mMediaPlayer?.isPlaying == true){
            mMediaPlayer?.pause()
            pauseLength = mMediaPlayer!!.currentPosition
            playAudioImage.setImageResource(R.drawable.ic_baseline_play_arrow_24)
            restartAudio = false
        }else{
            try {
                mMediaPlayer = MediaPlayer.create(requireContext(), uri)

                mMediaPlayer = MediaPlayer().apply {
                    setDataSource(requireActivity().application, uri)
                    setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                    )
                    prepare()
                    initializeSeekbar()
                    if (!restartAudio){
                        seekTo(pauseLength!!)
                    }
                    start()
                    playAudioImage.setImageResource(R.drawable.ic_baseline_pause_24)

                    setOnCompletionListener {
                        playAudioImage.setImageResource(R.drawable.ic_baseline_play_arrow_24)
                        restartAudio = true
                    }
                }
            } catch (exception: IOException) {
                mMediaPlayer?.release()
                mMediaPlayer = null
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekbar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) mMediaPlayer?.seekTo(progress)
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }

        })
    }
    private fun formatTimer(inputTime : Int): String{
        val hours = inputTime/3600
        val minutes = (inputTime % 3600) / 60
        val seconds = (inputTime % 3600) % 60
        var state = 0

        if (minutes > 9 && seconds < 9){ state = 1 }
        if (minutes < 9 && seconds > 9){ state = 2 }
        if (minutes > 9 && seconds > 9){ state = 3 }
        if (minutes < 9 && seconds < 9){ state = 4 }

        if (inputTime > 3600){
            return when (state){
                1 -> { "$hours:$minutes:0$seconds" }
                2 -> { "$hours:0$minutes:$seconds" }
                3 -> { "$hours:$minutes:$seconds" }
                else -> { "$hours:0$minutes:0$seconds" }
            }
        }else{
            return when (state){
                1 -> { "$minutes:0$seconds" }
                2 -> { "0$minutes:$seconds" }
                3 -> { "$minutes:$seconds" }
                else -> { "0$minutes:0$seconds" }
            }
        }
    }
    private fun initializeSeekbar(){
        seekBar.max = mMediaPlayer!!.duration

        val handler = Handler()
        handler.postDelayed(object : Runnable{
            override fun run() {
                try {

                    //passText.text = "${(mMediaPlayer!!.currentPosition)/1000}"
                    //val diff = (mMediaPlayer!!.duration - mMediaPlayer!!.currentPosition)/1000
                    //dueText.text = "-$diff"

                    passText.text = formatTimer((mMediaPlayer!!.currentPosition)/1000)
                    val diff = (mMediaPlayer!!.duration - mMediaPlayer!!.currentPosition)/1000
                    dueText.text = "-${formatTimer(diff)}"

                    /*
                    val passTextTemp = "${(mMediaPlayer!!.currentPosition)/1000}"
                    passText.text = formatSeekbarTimer(passTextTemp)
                    val dueTextTemp = (mMediaPlayer!!.duration - mMediaPlayer!!.currentPosition)/1000
                    dueText.text = formatSeekbarTimer(dueTextTemp.toString())*/

                    seekBar.progress = mMediaPlayer!!.currentPosition
                    handler.postDelayed(this, 100)
                }catch (e: Exception){
                    seekBar.progress = 0
                }
            }
        }, 0)
    }
    private fun shareAudio2(){
        val audioUri = Uri.parse("${requireContext().filesDir.path}/${AI_SPEECH}$designatedFileName.3gp")

        val audioFile = Intent(Intent.ACTION_SEND)
        audioFile.type = "audio/3gp"
        audioFile.setDataAndType(audioUri, context?.contentResolver?.getType(audioUri))
        audioFile.putExtra(Intent.EXTRA_STREAM, audioUri)
        //audioFile.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context?.startActivity(Intent.createChooser(audioFile, "Select an application: "))
    }

    private fun shareAudio() {
        val requestFile = File("${requireContext().filesDir.path}/${AI_SPEECH}$designatedFileName.3gp")

        val audioUri: Uri = FileProvider.getUriForFile(
            requireNotNull(context), "${BuildConfig.APPLICATION_ID}.provider", requestFile
        )
        val audioFile = Intent(Intent.ACTION_SEND)
        audioFile.type = "audio/*"
        audioFile.setDataAndType(audioUri, context?.contentResolver?.getType(audioUri))
        audioFile.putExtra(Intent.EXTRA_STREAM, audioUri)
        audioFile.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context?.startActivity(Intent.createChooser(audioFile, "Select an application: "))
    }

    private fun downloadAudioNN(){
        //
        //val savedAudio = requireActivity().contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        //val outputStream = savedAudio!!.let { this.requireActivity().contentResolver.openOutputStream(it) }
        var fos: FileOutputStream? = null


        try {
            fos = activity?.openFileOutput("$AI_SPEECH$designatedFileName.mpeg", MODE_PRIVATE)
            val path = "${requireContext().filesDir.path}/${AI_SPEECH}$designatedFileName.mpeg"
            val file = File(path)
            val fis = FileInputStream(file)
            var length : Int
            val buffer = ByteArray(8192)
            while (fis.read(buffer).also { length = it } > 0) {
                fos?.write(buffer, 0, length)
            }
            Toast.makeText(requireContext(), "Download successful: /Internal Storage/Music/AIScanner", Toast.LENGTH_LONG).show()
        }catch (e: FileNotFoundException){
            e.printStackTrace()
        }catch (e: IOException){
            e.printStackTrace()
        }finally {
            if (fos != null){
                try {
                    fos.close()
                }catch (e: IOException){
                    e.printStackTrace()
                }
            }
        }
    }

    //@RequiresApi(Build.VERSION_CODES.Q)
    private fun downloadAudio(){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q){
            try {
                val values = ContentValues()
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, "$AI_SPEECH$designatedFileName")
                values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/AIScanner")

                val savedAudio = requireActivity().contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                val outputStream = savedAudio!!.let { this.requireActivity().contentResolver.openOutputStream(it) }


                val path = "${requireContext().filesDir.path}/${AI_SPEECH}$designatedFileName.mp4"
                val file = File(path)
                val fis = FileInputStream(file)


                var length : Int
                val buffer = ByteArray(8192)
                while (fis.read(buffer).also { length = it } > 0) {
                    outputStream?.write(buffer, 0, length)
                }
                outputStream?.flush()
                outputStream?.close()
                fis.close()
                Toast.makeText(requireContext(), "Download successful: /Internal Storage/Music?AIScanner", Toast.LENGTH_LONG).show()

            }catch (e: IOException){
                e.printStackTrace()
            }
        }else{
            Toast.makeText(requireContext(), "Audio download is currently unavailable for android version 9 and below", Toast.LENGTH_LONG).show()

        }
    }

}
