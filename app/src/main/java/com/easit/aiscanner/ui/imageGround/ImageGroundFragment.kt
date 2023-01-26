package com.easit.aiscanner.ui.imageGround

import android.content.Context.MODE_PRIVATE
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Color.WHITE
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options
import com.easit.aiscanner.MainActivity
import com.easit.aiscanner.R
import com.easit.aiscanner.databinding.FragmentImageGroundBinding
import com.easit.aiscanner.model.InternalStoragePhoto
import com.easit.aiscanner.scannerAI.phase2.Language
import com.google.android.gms.tasks.Task
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.nl.smartreply.SmartReplySuggestion
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
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

    private var outputUri: Uri? = null
    private var _binding: FragmentImageGroundBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ImageGroundViewModel
    private lateinit var textRecognizer: TextRecognizer
    private lateinit var designatedFileName: String

    private var pageEntitiesList = mutableListOf<String>()
    private var pageRepliesList = mutableListOf<String>()
    var appFontSize = 0

    var isAppLaunchedFromScanner = false
    var scannerImageURI = ""


    //BATCH 1
    private lateinit var clickTextView: TextView
    private lateinit var selectedImage: ImageView

    //BATCH 2
    private lateinit var getDetails: Button

    //BATCH 3
    private lateinit var srcLang: TextView
    private lateinit var transcriptTitle: TextView
    private lateinit var translationTitle: TextView
    private lateinit var transcriptEditText: TextInputEditText
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
        viewModel = ViewModelProvider(this).get(ImageGroundViewModel::class.java)
        _binding = FragmentImageGroundBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        designatedFileName = getCurrentDateTime()
        initializations()
        appFontSize = viewModel.selectedFontSize!!
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
    }

    private fun initializations(){
        clickTextView = binding.clickTextView
        selectedImage = binding.selectedImage
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        translationEditText = binding.translationEdittext
        transcriptEditText = binding.transcribedEdittext
        transcriptTitle = binding.transcriptTitle
        translationTitle = binding.translationTitle

        targetLangSelectorCover = binding.targetLangSelectorCover
        targetLangSelector = binding.targetLangSelector

        smartReplyGroup = binding.smartReplyGroup
        entitiesGroup = binding.entitiesGroup

        progressText = binding.progressText
        progressBar = binding.progressBar
        srcLang = binding.srcLang

        getDetails = binding.getDetails

        smartReplyHeader = binding.smartReplyHeader
        entitiesTitle = binding.entitiesTitle
    }

    private fun showValuesForScanner(){
        //TODO Here
        scannerImageURI = (activity as MainActivity).scannedImageURI
        if (scannerImageURI.isNotBlank() && scannerImageURI != "" && scannerImageURI != "null"){
            clickTextView.visibility = View.GONE
            val path =
                "${requireContext().filesDir.path}/SCAN.$scannerImageURI.jpg"
            val fileURI = Uri.fromFile(File(path))
            val bitmap: Bitmap = MediaStore.Images.Media.getBitmap(context?.contentResolver, Uri.parse(fileURI.toString()))
            showImageWithGlide(bitmap)
            convertImageToText(fileURI)
            (activity as MainActivity).scannedImageURI = ""
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
                scanType = "image",
                imageUrl = "$designatedFileName.jpg")
        }
    }
    private fun retrieveAndSetDetailsForHistory(){
        var currentScanId = ""
        // val currentScanId = requireArguments().getString("selectedScanId").toString()
        try {
            currentScanId = requireArguments().getString("selectedScanId").toString()
        }catch (e: Exception){

        }
        if (currentScanId != "" && currentScanId != "null"){
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
                Log.v("Directory", "ImageUrl = ${scanObject.imageUrl}")
                lifecycleScope.launch {
                    val imageList = loadImageFromInternalStorage(scanObject.imageUrl)
                    if (imageList.isNotEmpty()){
                        val image = imageList[0]
                        showImageWithGlide(image.bmp)
                    }
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

    //IMAGE SPECIFIC CODES
    private val cropImageOther = registerForActivityResult(CropImageContract()) { result ->
        when {
            result.isSuccessful -> {
                Log.v("Bitmap", result.bitmap.toString())
                Log.v("File Path", context?.let { result.getUriFilePath(it) }.toString())
                findNavController().popBackStack(R.id.imageGroundFragment, false)

                if (result.uriContent != null){
                    Toast.makeText(context, "Success here", Toast.LENGTH_SHORT).show()
                    if (result.uriContent != null){
                        loadImageThroughGlide(result.uriContent)
                        clickTextView.visibility = View.GONE
                    }
                    convertUriToBitmap(result.uriContent!!)
                    convertImageToText(result.uriContent)
                    Log.v("Uri", result.uriContent.toString())
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
            options {
                setImageSource(
                    includeGallery = true,
                    includeCamera = true,
                )
                // Normal Settings
                setScaleType(CropImageView.ScaleType.FIT_CENTER)
                setCropShape(CropImageView.CropShape.RECTANGLE)
                setGuidelines(CropImageView.Guidelines.ON_TOUCH)
                setAspectRatio(1, 1)
                setMaxZoom(4)
                setAutoZoomEnabled(true)
                setMultiTouchEnabled(true)
                setCenterMoveEnabled(true)
                setShowCropOverlay(true)
                setAllowFlipping(true)
                setSnapRadius(3f)
                setTouchRadius(48f)
                setInitialCropWindowPaddingRatio(0.1f)
                setBorderLineThickness(3f)
                setBorderLineColor(Color.argb(170, 255, 255, 255))
                setBorderCornerThickness(2f)
                setBorderCornerOffset(5f)
                setBorderCornerLength(14f)
                setBorderCornerColor(WHITE)
                setGuidelinesThickness(1f)
                setGuidelinesColor(R.color.white)
                setBackgroundColor(Color.argb(119, 0, 0, 0))
                setMinCropWindowSize(24, 24)
                setMinCropResultSize(20, 20)
                setMaxCropResultSize(99999, 99999)
                setActivityTitle("")
                setActivityMenuIconColor(0)
                setOutputCompressFormat(Bitmap.CompressFormat.JPEG)
                setOutputCompressQuality(90)
                setRequestedSize(0, 0)
                setRequestedSize(0, 0, CropImageView.RequestSizeOptions.RESIZE_INSIDE)
                setInitialCropWindowRectangle(null)
                setInitialRotation(0)
                setAllowCounterRotation(false)
                setFlipHorizontally(false)
                setFlipVertically(false)
                setCropMenuCropButtonTitle(null)
                setCropMenuCropButtonIcon(0)
                setAllowRotation(true)
                setNoOutputImage(false)
                setFixAspectRatio(false)
            }
        )
    }
    private fun showErrorMessage(message: String) {
        Log.e("Camera Error:", message)
        Toast.makeText(activity, "Crop failed: $message", Toast.LENGTH_SHORT).show()
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
    private fun convertImageToText(imageUri: Uri?){
        try {
            val inputImage = InputImage.fromFilePath(requireActivity().applicationContext, imageUri!!)
            val result : Task<Text>  = textRecognizer.process(inputImage)
                .addOnSuccessListener {
                    transcriptEditText.setText(it.text)
                    //viewModel.createScanTest(it.text, "Translation of:${it.text}")
                    //Toast.makeText(context, "Sxan created successfully", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    transcriptEditText.setText("Error: ${it.message.toString()}")
                }
            textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }catch (e: Exception){

        }
    }

    //Save method 1
    private fun convertUriToBitmap(uri: Uri){
        val bitmap: Bitmap = MediaStore.Images.Media.getBitmap(context?.contentResolver, Uri.parse(uri.toString()))
        saveImageToInternalStorage(bitmap)
    }
    private fun saveImageToInternalStorage(bmp: Bitmap): Boolean{
        return try {
            //
            requireActivity().openFileOutput("$designatedFileName.jpg", MODE_PRIVATE).use {stream ->
                if (!bmp.compress(Bitmap.CompressFormat.JPEG, 90, stream)){
                    Toast.makeText(context,"Couldn't save bitmap.", Toast.LENGTH_SHORT).show()
                    throw IOException("Couldn't save bitmap.")
                }
            }
            true
        }catch (e: IOException){
            e.printStackTrace()
            false
        }
    }
    private suspend fun loadImageFromInternalStorage(filename: String): List<InternalStoragePhoto>{
        return withContext(Dispatchers.IO){
            val files = requireActivity().filesDir.listFiles()
            files.filter { it.canRead() && it.isFile && it.name == filename }.map {
                val bytes = it.readBytes()
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                InternalStoragePhoto(it.name, bmp)
            } ?: listOf()
        }
    }
    private fun deleteFromInternalStorage(filename: String): Boolean{
        return try {
            requireActivity().deleteFile(filename)
        }catch (e: Exception){
            e.printStackTrace()
            false
        }
    }

    private fun retrieveImageByID(imageID: String){
        lifecycleScope.launch {
            val imageList = loadImageFromInternalStorage(imageID)
            if (imageList.isNotEmpty()){
                val image = imageList[0]
                showImageWithGlide(image.bmp)
            }
        }
    }

}