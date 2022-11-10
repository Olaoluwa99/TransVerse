package com.easit.aiscanner.ui.text

import android.graphics.Color
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.easit.aiscanner.R
import com.easit.aiscanner.databinding.FragmentTextBinding
import com.easit.aiscanner.scannerAI.phase2.Language
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.transition.MaterialContainerTransform
import com.google.mlkit.nl.smartreply.SmartReplySuggestion
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class TextFragment : Fragment() {

    private lateinit var viewModel: TextViewModel2

    private var _binding: FragmentTextBinding? = null
    private val binding get() = _binding!!
    private var pageEntitiesList = mutableListOf<String>()
    private var pageRepliesList = mutableListOf<String>()
    private lateinit var designatedFileName: String
    var appFontSize = 0

    //BATCH 1
    private lateinit var transcriptTitle: TextView
    private lateinit var transcriptEditText: TextInputEditText
    private lateinit var getDetails: Button

    //BATCH 3
    private lateinit var translationTitle: TextView
    private lateinit var srcLang: TextView
    private lateinit var translationEditText: TextInputEditText

    //BATCH 4
    private lateinit var targetLangSelector: Spinner
    private lateinit var targetLangSelectorCover: TextView
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar

    //BATCH 5
    private lateinit var smartReplyHeader: TextView
    private lateinit var smartReplyGroup: ChipGroup
    private lateinit var entitiesTitle: TextView
    private lateinit var entitiesGroup: ChipGroup

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel = ViewModelProvider(this).get(TextViewModel2::class.java)
        _binding = FragmentTextBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        designatedFileName = getCurrentDateTime()
        initializations()
        appFontSize = viewModel.selectedFontSize!!
        setFontSize()
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
        targetLangSelectorCover = binding.targetLangSelectorCover
        transcriptTitle = binding.transcriptTitle
        translationTitle = binding.translationTitle
        srcLang = binding.srcLang
        transcriptEditText = binding.transcriptEditText
        getDetails = binding.getDetails
        smartReplyGroup = binding.smartReplyGroup
        entitiesGroup = binding.entitiesGroup

        smartReplyHeader = binding.smartReplyHeader
        entitiesTitle = binding.entitiesTitle
    }
    private fun setFontSize() {
        //
        getDetails.textSize = appFontSize.toFloat()
        transcriptTitle.textSize = (appFontSize + 10).toFloat()
        srcLang.textSize = appFontSize.toFloat()
        transcriptEditText.textSize = appFontSize.toFloat()
        progressText.textSize = appFontSize.toFloat()
        translationTitle.textSize = (appFontSize + 10).toFloat()
        translationEditText.textSize = appFontSize.toFloat()
        entitiesTitle.textSize = (appFontSize + 10).toFloat()
        smartReplyHeader.textSize = (appFontSize + 10).toFloat()

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
        viewModel.newTranslatedText.observe(viewLifecycleOwner, androidx.lifecycle.Observer { result ->
            translationEditText.setText(result)
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
                scanType = "text",
                imageUrl = "")
        }
    }
    private fun retrieveAndSetDetailsForHistory(){
        val currentScanId = requireArguments().getString("selectedScanId").toString()
        if (currentScanId != ""){
            viewModel.getSelectedScanObject(currentScanId)

            viewModel.currentHistoryItem.observe(viewLifecycleOwner, androidx.lifecycle.Observer { scanObject ->
                transcriptEditText.setText(scanObject.transcriptText)
                transcriptEditText.isFocusable = false
                translationEditText.setText(scanObject.translatedText)
                translationEditText.isFocusable = false

                getDetails.isClickable = false

                designatedFileName = scanObject.id
                //Show entity chips
                showEntities(scanObject.entities)
                //Show replies chips
                showSmartReplyString(scanObject.smartReplies)
                //Show source language
                srcLang.text = scanObject.sourceLanguage
                //Show translated language spinner
                targetLangSelector.visibility = View.INVISIBLE
                targetLangSelectorCover.visibility = View.VISIBLE
                targetLangSelectorCover.text = scanObject.translatedLanguage
            })
        }
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
            }
            entitiesGroup.addView(chip as View)
        }
    }
    private fun getCurrentDateTime(): String {
        val currentTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
        return currentTime.format(formatter)
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}