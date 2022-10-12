package com.easit.aiscanner.ui.audioGround

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.os.Environment
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.Editable
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import com.easit.aiscanner.R
import com.easit.aiscanner.data.Constants
import com.easit.aiscanner.databinding.FragmentAudioGroundBinding
import com.easit.aiscanner.scannerAI.phase2.Language
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.nl.smartreply.SmartReplySuggestion
import com.masoudss.lib.WaveformSeekBar
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class AudioGroundFragment : Fragment() {


    private val REQUEST_SPEECH_RECORDING = Constants.REQUEST_SPEECH_RECORDING

    //BATCH 1
    private lateinit var recordButton: ImageView
    private lateinit var recordButtonLayout: CardView

    //BATCH 2
    private lateinit var getTextFromSpeech: Button

    //BATCH 3
    private lateinit var srcLang: TextView
    private lateinit var transcriptEditText: TextInputEditText
    private lateinit var translationEditText: TextInputEditText

    //BATCH 4
    private lateinit var playAudioCard: CardView
    private lateinit var playAudioImage: ImageView
    private lateinit var wavesSeekBar: WaveformSeekBar

    //BATCH 5
    private lateinit var targetLangSelector: Spinner
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar

    //BATCH 6
    private lateinit var smartReplyGroup: ChipGroup
    private lateinit var entitiesGroup: ChipGroup

    //  NON VIEWS
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

    //SYSTEM
    private var _binding: FragmentAudioGroundBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AudioGroundViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel = ViewModelProvider(this).get(AudioGroundViewModel::class.java)
        _binding =  FragmentAudioGroundBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializations()
        setupTargetLanguageSpinner()
        retrieveAndSetDetailsForHistory()
        performAudioRecording()
        playAudioImage.setOnClickListener {
            //Plays the recorded audio in new language
            playAudio()
        }
        getFullScan()
        observeSourceLanguage()
        observeTranslatedTextResponse()
        observeModelDownloading()
        observeForReturnedEntities()
        observeForReturnedReplies()
    }

    private fun initializations(){
        recordButton = binding.micState
        recordButtonLayout = binding.micLayout
        transcriptEditText = binding.transcribedEdittext
        translationEditText = binding.translationEdittext

        playAudioCard = binding.playAudioCard
        playAudioImage = binding.playAudioImage
        targetLangSelector = binding.targetLangSelector

        smartReplyGroup = binding.smartReplyGroup
        entitiesGroup = binding.entitiesGroup

        progressText = binding.progressText
        progressBar = binding.progressBar
        srcLang = binding.srcLang

        getTextFromSpeech = binding.getTextFromAudio
        wavesSeekBar = binding.waveSeekbar
        /*
        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result: ActivityResult ->
            if (result.resultCode == REQUEST_SPEECH_RECORDING && result.data != null){
                val speechText = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) as ArrayList<Editable>
                transcriptEditText.setText(speechText[0])
            }
        }*/
    }
    private fun performAudioRecording(){
        recordButton.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something!")
            //startActivityForResult(intent, REQUEST_SPEECH_RECORDING)
            try {
                activityResultLauncher.launch(intent)
            }catch (e: ActivityNotFoundException){
                Toast.makeText(requireContext(), "Speech recognition is not avalable", Toast.LENGTH_SHORT).show()
            }
        }
        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
                result: ActivityResult? ->
            if (result!!.resultCode == RESULT_OK && result.data != null){
                val speechText = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) as ArrayList<Editable>
                transcriptEditText.setText(speechText[0])
            }
        }
    }
    private fun setupTargetLanguageSpinner(){
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item, viewModel.availableLanguages
        )
        targetLangSelector.adapter = adapter
        targetLangSelector.setSelection(adapter.getPosition(Language("en")))
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
        viewModel.translatedText.observe(viewLifecycleOwner, androidx.lifecycle.Observer { resultOrError ->
            resultOrError?.let {
                if (it.error != null) {
                    translationEditText.error = resultOrError.error?.localizedMessage
                } else {
                    translationEditText.setText(resultOrError.result)
                }
            }
        })
    }
    private fun observeForReturnedEntities(){
        /**Entities extraction*/
        viewModel.getEntities().observe(viewLifecycleOwner) { entities ->
            if (entities != null){
                showEntities(entities)
            }
        }
    }
    private fun observeForReturnedReplies(){
        /**Smart reply*/
        viewModel.getSuggestions().observe(viewLifecycleOwner) { suggestions ->
            if (suggestions != null){
                showSmartReply(suggestions)
            }
        }
    }
    private fun observeSourceLanguage(){
        viewModel.sourceLang.observe(viewLifecycleOwner, androidx.lifecycle.Observer { srcLang.text = it.displayName })
    }
    private fun getFullScan(){
        getTextFromSpeech.setOnClickListener {
            viewModel.sourceText.value = transcriptEditText.text.toString()
            //Perform entity extraction
            viewModel.extractEntities(transcriptEditText.text.toString())

            viewModel.createScanTest(transcriptEditText.text.toString(), translationEditText.text.toString())
            Toast.makeText(context, "Sxan created successfully", Toast.LENGTH_SHORT).show()
        }
    }
    private fun retrieveAndSetDetailsForHistory(){
        val currentScanId = requireArguments().getString("selectedScanId").toString()
        if (currentScanId != ""){
            viewModel.getSelectedScanObject2(currentScanId)

            viewModel.currentHistoryItem.observe(viewLifecycleOwner, androidx.lifecycle.Observer { scanObject ->
                transcriptEditText.setText(scanObject.transcriptText)
                translationEditText.setText(scanObject.translatedText)
                //Show entity chips
                //Show replies chips
                //Set up play recorded audio
                //Show source language
                //Show translated language spinner
            })
        }
    }
    private fun playAudio(){
        textToSpeech = TextToSpeech(requireActivity().applicationContext, TextToSpeech.OnInitListener {
            if (textToSpeech.isSpeaking){
                textToSpeech.stop()
                playAudioImage.setImageResource(R.drawable.ic_baseline_play_arrow_24)
            }else{
                if (it == TextToSpeech.SUCCESS){
                    textToSpeech.language == Locale.getDefault()
                    textToSpeech.setSpeechRate(1.0f)
                    if (translationEditText.text.toString() == ""){
                        Toast.makeText(requireContext(), "No text available", Toast.LENGTH_SHORT).show()
                    }else{
                        //Store speech as mp3 or uri
                        val hashMap = HashMap<String, String>()
                        hashMap[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = translationEditText.text.toString()

                        textToSpeech.synthesizeToFile(translationEditText.text.toString(),hashMap,
                            "${Environment.DIRECTORY_MUSIC}/AIScanner")
                        
                        textToSpeech.speak(translationEditText.text.toString(),
                            TextToSpeech.QUEUE_ADD, null)
                        //Code to convert drawable to stop
                        playAudioImage.setImageResource(R.drawable.ic_baseline_stop_24)
                    }
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SPEECH_RECORDING && requestCode == Activity.RESULT_OK){
            val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            transcriptEditText.setText(result?.get(0).toString())
        }
    }
    private fun showSmartReply(suggestionsList: List<SmartReplySuggestion>) {
        smartReplyGroup.removeAllViews()
        suggestionsList.forEachIndexed { index, suggestion ->
            val chip = Chip(requireContext())
            chip.apply {
                text = suggestion.text
                isCheckable = true
                isClickable = true
                id = index
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
                isCheckable = true
                isClickable = true
                id = index
            }
            entitiesGroup.addView(chip as View)
        }
    }
}