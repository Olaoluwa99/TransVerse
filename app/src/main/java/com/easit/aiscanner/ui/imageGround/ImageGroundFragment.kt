package com.easit.aiscanner.ui.imageGround

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Color.WHITE
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.easit.aiscanner.MainActivity
import com.easit.aiscanner.R
import com.easit.aiscanner.databinding.FragmentImageGroundBinding
import com.easit.aiscanner.scannerAI.phase2.Language
import com.easit.aiscanner.utils.EntityActions
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.nl.smartreply.SmartReplySuggestion
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@AndroidEntryPoint
class ImageGroundFragment : Fragment() {

    companion object {
        fun newInstance() = ImageGroundFragment()
        const val DATE_FORMAT = "yyyyMMdd_HHmmss"
        const val FILE_NAMING_PREFIX = "JPEG_"
        const val FILE_NAMING_SUFFIX = "_"
        const val FILE_FORMAT = ".jpg"
        const val AUTHORITY_SUFFIX = ".cropper.fileprovider"
    }

    private var _binding: FragmentImageGroundBinding? = null
    private val binding get() = requireNotNull(_binding) {
        "Binding accessed outside the view lifecycle"
    }
    private val viewModel by viewModels<ImageGroundViewModel>()
    private lateinit var textRecognizer: TextRecognizer
    private lateinit var designatedFileName: String
    private var imageFileName = ""

    var appFontSize = 0


    //BATCH 1
    private val clickTextView get() = binding.clickTextView
    private val selectedImage get() = binding.selectedImage

    //BATCH 2
    private val getDetails get() = binding.getDetails

    //BATCH 3
    private val srcLang get() = binding.srcLang
    private val transcriptTitle get() = binding.transcriptTitle
    private val translationTitle get() = binding.translationTitle
    private val transcriptEditText get() = binding.transcribedEdittext
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
        _binding = FragmentImageGroundBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        designatedFileName = getCurrentDateTime()
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        appFontSize = viewModel.selectedFontSize
        setFontSize()
        selectedImage.setOnClickListener {
            startCameraWithoutUri()
        }
        showValuesForScanner() //Only when app is launched from scanner
        setupTargetLanguageSpinner()
        retrieveAndSetDetailsForHistory()
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


    private fun showValuesForScanner(){
        val scannerImageURI = (activity as? MainActivity)?.scannedImageURI.orEmpty()
        if (scannerImageURI.isNotBlank()){
            clickTextView.visibility = View.GONE
            val imageUri = if (scannerImageURI.contains("://")) {
                imageFileName = "$designatedFileName.jpg"
                copyImageToInternalStorage(Uri.parse(scannerImageURI), imageFileName)
                Uri.parse(scannerImageURI)
            } else {
                imageFileName = "SCAN.$scannerImageURI.jpg"
                Uri.fromFile(File(requireContext().filesDir, "SCAN.$scannerImageURI.jpg"))
            }
            Glide.with(this)
                .load(imageUri)
                .placeholder(R.drawable.ic_baseline_insert_photo_24)
                .into(selectedImage)
            convertImageToText(imageUri)
            (activity as? MainActivity)?.scannedImageURI = ""
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
                scanType = "image",
                imageUrl = imageFileName)
        }
    }
    private fun retrieveAndSetDetailsForHistory(){
        val currentScanId = arguments?.getString("selectedScanId").orEmpty()
        if (currentScanId.isNotBlank()){
            viewModel.getSelectedScanObject(currentScanId)

            viewModel.currentHistoryItem.observe(viewLifecycleOwner, androidx.lifecycle.Observer { scanObject ->

                selectedImage.isClickable = false

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
                lifecycleScope.launch {
                    loadImageFromInternalStorage(scanObject.imageUrl)?.let(::showImageWithGlide)
                }
            })
        }
    }
    private fun showImageWithGlide(bmp: Bitmap){
        try {
            Glide
                .with(this)
                .load(bmp)
                .placeholder(R.drawable.ic_baseline_insert_photo_24)
                .into(selectedImage)
        }catch (e: IOException){
            e.printStackTrace()
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

    //IMAGE SPECIFIC CODES
    private val cropImageOther = registerForActivityResult(CropImageContract()) { result ->
        when {
            result.isSuccessful -> {
                Log.v("Bitmap", result.bitmap.toString())
                Log.v("File Path", context?.let { result.getUriFilePath(it) }.toString())
                result.uriContent?.let { uri ->
                    loadImageThroughGlide(uri)
                    clickTextView.visibility = View.GONE
                    imageFileName = "$designatedFileName.jpg"
                    copyImageToInternalStorage(uri, imageFileName)
                    convertImageToText(uri)
                }
            }
            result is CropImage.CancelledResult -> {
                showErrorMessage("cropping image was cancelled by the user")
            }
            else -> {
                showErrorMessage("cropping image failed")
            }
        }
    }
    private fun startCameraWithoutUri() {
        cropImageOther.launch(
            CropImageContractOptions(
                uri = null,
                cropImageOptions = CropImageOptions(
                    imageSourceIncludeGallery = true,
                    imageSourceIncludeCamera = true,
                    scaleType = CropImageView.ScaleType.FIT_CENTER,
                    cropShape = CropImageView.CropShape.RECTANGLE,
                    guidelines = CropImageView.Guidelines.ON_TOUCH,
                    aspectRatioX = 1,
                    aspectRatioY = 1,
                    maxZoom = 4,
                    autoZoomEnabled = true,
                    multiTouchEnabled = true,
                    centerMoveEnabled = true,
                    showCropOverlay = true,
                    allowFlipping = true,
                    snapRadius = 3f,
                    touchRadius = 48f,
                    initialCropWindowPaddingRatio = 0.1f,
                    borderLineThickness = 3f,
                    borderLineColor = Color.argb(170, 255, 255, 255),
                    borderCornerThickness = 2f,
                    borderCornerOffset = 5f,
                    borderCornerLength = 14f,
                    borderCornerColor = WHITE,
                    guidelinesThickness = 1f,
                    guidelinesColor = ContextCompat.getColor(requireContext(), R.color.white),
                    backgroundColor = Color.argb(119, 0, 0, 0),
                    minCropWindowWidth = 24,
                    minCropWindowHeight = 24,
                    minCropResultWidth = 20,
                    minCropResultHeight = 20,
                    maxCropResultWidth = 99999,
                    maxCropResultHeight = 99999,
                    outputCompressFormat = Bitmap.CompressFormat.JPEG,
                    outputCompressQuality = 90,
                    outputRequestWidth = 0,
                    outputRequestHeight = 0,
                    outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_INSIDE,
                    initialRotation = 0,
                    allowCounterRotation = false,
                    flipHorizontally = false,
                    flipVertically = false,
                    allowRotation = true,
                    noOutputImage = false,
                    fixAspectRatio = false,
                ),
            )
        )
    }
    private fun showErrorMessage(message: String) {
        Log.e("Camera Error:", message)
        Snackbar.make(binding.root, R.string.image_crop_failed, Snackbar.LENGTH_LONG).show()
    }
    private fun loadImageThroughGlide(imageUri: Uri?){
        try {
            Glide
                .with(this)
                .load(imageUri)
                .placeholder(R.drawable.ic_baseline_insert_photo_24)
                .into(selectedImage)
        }catch (e: IOException){
            e.printStackTrace()
        }
    }
    private fun convertImageToText(imageUri: Uri){
        try {
            val inputImage = InputImage.fromFilePath(requireContext(), imageUri)
            textRecognizer.process(inputImage)
                .addOnSuccessListener {
                    _binding?.transcribedEdittext?.setText(it.text)
                }.addOnFailureListener {
                    _binding?.root?.let { root ->
                        Snackbar.make(
                            root,
                            R.string.image_text_recognition_failed,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
        } catch (_: IOException) {
            Snackbar.make(binding.root, R.string.image_open_failed, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun copyImageToInternalStorage(uri: Uri, filename: String) {
        val resolver = requireContext().contentResolver
        val destination = File(requireContext().filesDir, filename)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().use(input::copyTo)
                } ?: throw IOException("Unable to open selected image")
            }.onFailure {
                withContext(Dispatchers.Main) {
                    if (imageFileName == filename) imageFileName = ""
                    _binding?.root?.let { root ->
                        Snackbar.make(root, R.string.image_save_failed, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private suspend fun loadImageFromInternalStorage(filename: String): Bitmap? =
        withContext(Dispatchers.IO) {
            filename.takeIf(String::isNotBlank)
                ?.let { File(requireContext().filesDir, it) }
                ?.takeIf { it.isFile && it.canRead() }
                ?.let { BitmapFactory.decodeFile(it.absolutePath) }
        }

    override fun onDestroyView() {
        textRecognizer.close()
        _binding = null
        super.onDestroyView()
    }

}
