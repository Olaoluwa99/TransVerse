package com.easit.aiscanner.ui.imageGround

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Color.WHITE
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options
import com.easit.aiscanner.R
import com.easit.aiscanner.databinding.FragmentImageGroundBinding
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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

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
    private lateinit var savedFilePath: String


    //BATCH 1
    private lateinit var selectedImage: ImageView

    //BATCH 2
    private lateinit var getTextFromImage: Button

    //BATCH 3
    private lateinit var srcLang: TextView
    private lateinit var transcriptEditText: TextInputEditText
    private lateinit var translationEditText: TextInputEditText

    //BATCH 4
    private lateinit var playAudioCard: CardView
    private lateinit var playAudioImage: ImageView

    //BATCH 5
    private lateinit var targetLangSelector: Spinner
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar

    //BATCH 6
    private lateinit var smartReplyGroup: ChipGroup
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
        initializations()
        selectedImage.setOnClickListener {
            startCameraWithoutUri()
        }
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
        selectedImage = binding.selectedImage
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        translationEditText = binding.translationEdittext
        transcriptEditText = binding.transcribedEdittext
        targetLangSelector = binding.targetLangSelector

        smartReplyGroup = binding.smartReplyGroup
        entitiesGroup = binding.entitiesGroup

        progressText = binding.progressText
        progressBar = binding.progressBar
        srcLang = binding.srcLang

        getTextFromImage = binding.getTextFromImage
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
        getTextFromImage.setOnClickListener {
            viewModel.sourceText.value = transcriptEditText.text.toString()
            //Perform entity extraction
            viewModel.extractEntities(transcriptEditText.text.toString())

            viewModel.createScanTest(transcriptEditText.text.toString(), translationEditText.text.toString()
                ,savedFilePath )
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
                Log.v("Directory", "ImageUrl = ${scanObject.imageUrl}")
                try {
                    Glide
                        .with(this)
                        .load(scanObject.imageUrl)
                        .placeholder(R.drawable.ic_baseline_insert_photo_24)
                        .into(selectedImage)
                }catch (e: IOException){
                    e.printStackTrace()
                }
            })
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

    //IMAGE CROPPER
    private val cropImageOther = registerForActivityResult(CropImageContract()) { result ->
        when {
            result.isSuccessful -> {
                Log.v("Bitmap", result.bitmap.toString())
                Log.v("File Path", context?.let { result.getUriFilePath(it) }.toString())
                handleCropImageResult(result.uriContent.toString())

                if (result.uriContent != null){
                    showErrorMessage("Success here")
                    loadImageThroughGlide(result.uriContent)
                    savedFilePath = saveUriToFileManager(result.uriContent!!)
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
    private fun handleCropImageResult(uri: String) {
        //SampleResultScreen.start(this, null, Uri.parse(uri.replace("file:", "")), null)
        setupOutputUri()
        findNavController().popBackStack(R.id.imageGroundFragment, false)
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



    private fun saveUriToFileManager(uri: Uri): String{
        val bitmap: Bitmap = MediaStore.Images.Media.getBitmap(context?.contentResolver, Uri.parse(uri.toString()))
        return saveMediaToStorage(bitmap)
    }

    private fun saveMediaToStorage(bitmap: Bitmap): String {
        //Generating a file name
        val filename = "${getCurrentDateTime()}.jpg"

        //Output stream
        var fos: OutputStream? = null

        //For devices running android >= Q
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //getting the contentResolver
            context?.contentResolver?.also { resolver ->

                //Content resolver will process the contentvalues
                val contentValues = ContentValues().apply {

                    //putting file information in content values
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AIScanner")
                }

                //Inserting the contentValues to contentResolver and getting the Uri
                val imageUri: Uri? =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                //Opening an outputstream with the Uri that we got
                fos = imageUri?.let { resolver.openOutputStream(it) }
                Log.v("Directory", "/Internal storage/Pictures/AIScanner/$filename")
                return "/storage/emulated/0/Pictures/AIScanner/$filename"
            }
        } else {
            //These for devices running on android < Q
            //So I don't think an explanation is needed here            Environment.DIRECTORY_PICTURES
            val imagesDir =
                Environment.getExternalStoragePublicDirectory("${Environment.DIRECTORY_PICTURES}/AIScanner")
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)

            Log.v("Directory", Environment.DIRECTORY_PICTURES.toString())
            Environment.DIRECTORY_PICTURES.toString()
            //TODO GET URL FROM FILE MANAGER
            //"/Internal storage/Pictures/AIScanner/$filename"
            return "/storage/emulated/0/Pictures/AIScanner/$filename"

        }

        fos?.use {
            //Finally writing the bitmap to the output stream that we opened
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            Toast.makeText(context, "Saved to Photos", Toast.LENGTH_LONG).show()
        }
        return "/storage/emulated/0/Pictures/AIScanner/$filename"
    }
    private fun getCurrentDateTime(): String {
        val currentTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
        return currentTime.format(formatter)
    }


    private fun setupOutputUri() {
        if (outputUri == null) context?.let { ctx ->
            val authorities = "${ctx.applicationContext?.packageName}$AUTHORITY_SUFFIX"
            outputUri = FileProvider.getUriForFile(ctx, authorities, createImageFile())
        }
    }
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(Date())
        val storageDir: File? = activity?.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "$FILE_NAMING_PREFIX${timeStamp}$FILE_NAMING_SUFFIX",
            FILE_FORMAT,
            storageDir
        )
    }
}