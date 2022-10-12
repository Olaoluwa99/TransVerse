package com.easit.aiscanner.ui.text

import android.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.lifecycle.Observer
import com.easit.aiscanner.databinding.FragmentTextBinding
import com.easit.aiscanner.scannerAI.phase2.Language
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.nl.smartreply.SmartReplySuggestion

class TextFragment : Fragment() {

    private lateinit var viewModel: TextViewModel

    private var _binding: FragmentTextBinding? = null
    private val binding get() = _binding!!

    //BATCH 1
    private lateinit var transcriptEditText: TextInputEditText
    private lateinit var setSourceText: Button

    //BATCH 3
    private lateinit var srcLang: TextView
    private lateinit var translationEditText: TextInputEditText

    //BATCH 4
    private lateinit var targetLangSelector: Spinner
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar

    //BATCH 5
    private lateinit var smartReplyGroup: ChipGroup
    private lateinit var entitiesGroup: ChipGroup

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel = ViewModelProvider(this).get(TextViewModel::class.java)
        _binding = FragmentTextBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializations()
        setupTargetLanguageSpinner()
        retrieveAndSetDetailsForHistory()
        getFullScan()
        observeSourceLanguage()
        observeTranslatedTextResponse()
        observeModelDownloading()
        observeForReturnedEntities()
        observeForReturnedReplies()

    }

    private fun initializations(){
        translationEditText = binding.translationEdittext
        progressText = binding.progressText
        progressBar = binding.progressBar
        targetLangSelector = binding.targetLangSelector
        srcLang = binding.srcLang
        transcriptEditText = binding.transcriptEditText
        setSourceText = binding.setSourceText
        smartReplyGroup = binding.smartReplyGroup
        entitiesGroup = binding.entitiesGroup
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
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    //
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
        setSourceText.setOnClickListener {
            viewModel.sourceText.value = transcriptEditText.text.toString()
            //Perform entity extraction
            viewModel.extractEntities(transcriptEditText.text.toString())

            //TODO Create Scan
            viewModel.createScanTest(transcriptEditText.text.toString(), translationEditText.text.toString())
            Toast.makeText(context, "Sxan created successfully", Toast.LENGTH_SHORT).show()
        }
    }
    private fun retrieveAndSetDetailsForHistory(){
        val currentScanId = requireArguments().getString("selectedScanId").toString()
        if (currentScanId != ""){
            viewModel.getSelectedScanObject(currentScanId)

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
}

/*

    private lateinit var dialog: AlertDialog


if (isServiceRunning()){
        requireActivity().stopService(Intent(context, FloatingWindowApp::class.java))
    }

    private fun isServiceRunning(): Boolean{
        val manager = requireActivity().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)){
            if (FloatingWindow::class.java.name == service.service.className){
                return true
            }
        }
        return false
    }

    private fun requestFloatingWindowPermission(){
        val builder = AlertDialog.Builder(context)
        builder.setCancelable(true)
        builder.setTitle("Screen overlay permission needed")
        builder.setMessage("Enable 'Display over App from settings")
        builder.setPositiveButton("Open setting", DialogInterface.OnClickListener { dialogInterface, which ->
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireActivity().packageName}"))
            startActivityForResult(intent, RESULT_OK)
        })
        dialog = builder.create()
        dialog.show()
    }

    private fun checkOverlayPermission(): Boolean{
        return if(Build.VERSION.SDK_INT > Build.VERSION_CODES.M){
            Settings.canDrawOverlays(context)
        }
        else return true
    }
 */