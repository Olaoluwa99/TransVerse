package com.easit.aiscanner.ui.text

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.easit.aiscanner.MainActivity
import com.easit.aiscanner.R
import com.easit.aiscanner.databinding.FragmentTextBinding
import com.easit.aiscanner.scannerAI.phase2.Language
import com.easit.aiscanner.utils.EntityActions
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.nl.smartreply.SmartReplySuggestion
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class TextFragment : Fragment() {

    private val viewModel by viewModels<TextViewModel>()

    private var _binding: FragmentTextBinding? = null
    private val binding get() = requireNotNull(_binding) {
        "Binding accessed outside the view lifecycle"
    }
    private lateinit var designatedFileName: String
    var appFontSize = 0

    //BATCH 1
    private val transcriptTitle get() = binding.transcriptTitle
    private val transcriptEditText get() = binding.transcriptEditText
    private val getDetails get() = binding.getDetails

    //BATCH 3
    private val translationTitle get() = binding.translationTitle
    private val srcLang get() = binding.srcLang
    private val translationEditText get() = binding.translationEdittext

    //BATCH 4
    private val targetLangSelector get() = binding.targetLangSelector
    private val targetLangSelectorCover get() = binding.targetLangSelectorCover
    private val progressText get() = binding.progressText
    private val progressBar get() = binding.progressBar

    //BATCH 5
    private val smartReplyHeader get() = binding.smartReplyHeader
    private val smartReplyGroup get() = binding.smartReplyGroup
    private val entitiesTitle get() = binding.entitiesTitle
    private val entitiesGroup get() = binding.entitiesGroup

    //
    private val copyTranscript get() = binding.copyTranscript
    private val copyTranslate get() = binding.copyTranslate

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentTextBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        designatedFileName = getCurrentDateTime()
        appFontSize = viewModel.selectedFontSize
        setFontSize()
        (activity as? MainActivity)?.let { mainActivity ->
            if (mainActivity.incomingText.isNotBlank()) {
                transcriptEditText.setText(mainActivity.incomingText)
                mainActivity.incomingText = ""
            }
        }
        setupTargetLanguageSpinner()
        retrieveAndSetDetailsForHistory()
        getFullScan()

        observeTranslatedTextResponse()
        observeModelDownloading()
        observeForReturnedEntities()
        observeForReturnedReplies()
        observeSourceLanguage()
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
                scanType = "text",
                imageUrl = "")
        }
    }
    private fun retrieveAndSetDetailsForHistory(){
        val currentScanId = arguments?.getString("selectedScanId").orEmpty()
        if (currentScanId.isNotBlank()){
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

    private fun saveToClipboard(label: String, saveText: String) {
        val clipboard = activity?.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, saveText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
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
    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
