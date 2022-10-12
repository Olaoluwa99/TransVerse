package com.easit.aiscanner.ui.text

import android.app.Application
import android.os.Handler
import android.telephony.PhoneNumberUtils
import android.util.Log
import android.util.LruCache
import android.widget.Toast
import androidx.lifecycle.*
import com.easit.aiscanner.R
import com.easit.aiscanner.data.Constants.DESIRED_HEIGHT_CROP_PERCENT
import com.easit.aiscanner.data.Constants.DESIRED_WIDTH_CROP_PERCENT
import com.easit.aiscanner.database.ScanDatabase
import com.easit.aiscanner.database.ScanRepository
import com.easit.aiscanner.model.Message
import com.easit.aiscanner.model.Scan
import com.easit.aiscanner.scannerAI.phase2.Language
import com.easit.aiscanner.scannerAI.phase2.SmoothedMutableLiveData
import com.easit.aiscanner.utils.ResultOrError
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.entityextraction.*
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.nl.smartreply.SmartReplySuggestion
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult
import com.google.mlkit.nl.smartreply.TextMessage
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class TextViewModel (application: Application) : AndroidViewModel(application) {

    private val repository : ScanRepository
    lateinit var currentHistoryItem: LiveData<Scan>

    init {
        val dao = ScanDatabase.getDatabase(application).getScanDao()
        repository = ScanRepository(dao)
    }

    fun getSelectedScanObject(id: String) = viewModelScope.launch{
        currentHistoryItem = repository.getSelectedScanById(id)
    }

    fun createScanTest(transcribedText: String, translatedText: String){
        val scan = Scan(getCurrentDateTime(), getCurrentDate(), getCurrentTime(), "text", transcribedText, translatedText, "entities", "smartReplies",
            "imageUrl", "audioUrl", "barcodeScan")
        addScan(scan)
    }

    private fun addScan(scan: Scan) = viewModelScope.launch (Dispatchers.IO) {
        repository.insert(scan)
    }

    private fun getCurrentDateTime(): String {
        val currentTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
        return currentTime.format(formatter)
    }
    private fun getCurrentDate(): String {
        val currentTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        return currentTime.format(formatter)
    }
    private fun getCurrentTime(): String {
        val currentTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("HHmmssSSS")
        return currentTime.format(formatter)
    }


    companion object {
        //FOR IDENTIFICATION & TRANSLATION
        // Amount of time (in milliseconds) to wait for detected text to settle
        private const val SMOOTHING_DURATION = 50L
        private const val NUM_TRANSLATORS = 1

        //FOR SMART REPLY
        //private const val TAG = "ChatViewModel"
        private const val TAG = "HomeViewModel"

        private fun getEntityExtractionParams(input: String): EntityExtractionParams {
            return EntityExtractionParams.Builder(input).build()
        }

        //FOR ENTITY EXTRACTION
        private const val CURRENT_MODEL_KEY = "current_model_key"
    }

    /**
     * Identification and translation
     * */
    private val languageIdentifier = LanguageIdentification.getClient()
    val targetLang = MutableLiveData<Language>()
    //val sourceText = SmoothedMutableLiveData<String>(SMOOTHING_DURATION)
    val sourceText = MutableLiveData<String>()
    //TODO HERE AFFECTS (TEXT RECOGNITION)
    val imageCropPercentages = MutableLiveData<Pair<Int, Int>>()
        .apply { value = Pair(DESIRED_HEIGHT_CROP_PERCENT, DESIRED_WIDTH_CROP_PERCENT) }
    val translatedText = MediatorLiveData<ResultOrError>()
    private val translating = MutableLiveData<Boolean>()
    val modelDownloading = SmoothedMutableLiveData<Boolean>(SMOOTHING_DURATION)
    private var modelDownloadTask: Task<Void> = Tasks.forCanceled()
    private val translators =
        object : LruCache<TranslatorOptions, Translator>(NUM_TRANSLATORS) {
            override fun create(options: TranslatorOptions): Translator {
                return Translation.getClient(options)
            }

            override fun entryRemoved(
                evicted: Boolean,
                key: TranslatorOptions,
                oldValue: Translator,
                newValue: Translator?
            ) {
                oldValue.close()
            }
        }
    val sourceLang = Transformations.switchMap(sourceText) { text ->
        val result = MutableLiveData<Language>()
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                if (languageCode != "und")
                    result.value = Language(languageCode)
            }
        result
    }
    // Gets a list of all available translation languages.
    val availableLanguages: List<Language> = TranslateLanguage.getAllLanguages()
        .map { Language(it) }

    /**
     * Smart reply
     * */
    private val remoteUserId = UUID.randomUUID().toString()
    private val suggestions = MediatorLiveData<List<SmartReplySuggestion>>()
    private val messageList = MutableLiveData<MutableList<Message>>()
    private val smartReply = SmartReply.getClient()
    private var suggestionResultList = MutableLiveData<List<SmartReplySuggestion>>()

    val messages: LiveData<MutableList<Message>>
        get() = messageList

    /**
     * Entity extraction
     * */
    /*
    @EntityExtractorOptions.ModelIdentifier
    private var currentModel: String = EntityExtractorOptions.ENGLISH
    val options = EntityExtractorOptions.Builder(currentModel).build()
    private var entityExtractor = EntityExtraction.getClient(options)*/


    private var entitiesLiveList = MutableLiveData<List<String>>()
    private val entitiesListAdd = mutableListOf<String>()
    private var entity = MutableLiveData<Entity>()

    private val _outputString = MutableLiveData<String>()
    val outputString: LiveData<String> = _outputString


    /** START OF NORMAL CODE */
    init {
        modelDownloading.setValue(false)
        translating.value = false
        // Create a translation result or error object.
        val processTranslation =
            OnCompleteListener<String> { task ->
                if (task.isSuccessful) {
                    translatedText.value = ResultOrError(task.result, null)
                    //TODO SHOW SMART REPLY
                    clearSuggestions()
                    Log.d("Chips", "Before thr generate reply block")
                    generateReplies(task.result, System.currentTimeMillis()).addOnSuccessListener {repliesList ->
                        Log.d("Chips", "stuff successful")
                        suggestionResultList.value = repliesList
                    }.addOnFailureListener {e ->
                        Log.d("Chips", e.message.toString())
                    }



                    //TODO ENTITY EXTRACTION
                } else {
                    clearSuggestions()
                    if (task.isCanceled) {
                        // Tasks are cancelled for reasons such as gating; ignore.
                        return@OnCompleteListener
                    }
                    translatedText.value = ResultOrError(null, task.exception)
                }
            }
        // Start translation if any of the following change: detected text, source lang, target lang.
        translatedText.addSource(sourceText) { translate().addOnCompleteListener(processTranslation) }
        translatedText.addSource(sourceLang) { translate().addOnCompleteListener(processTranslation) }
        translatedText.addSource(targetLang) { translate().addOnCompleteListener(processTranslation) }
    }

    private fun translate(): Task<String> {
        val text = sourceText.value
        val source = sourceLang.value
        val target = targetLang.value
        if (modelDownloading.value != false || translating.value != false) {
            return Tasks.forCanceled()
        }
        if (source == null || target == null || text == null || text.isEmpty()) {
            return Tasks.forResult("")
        }
        val sourceLangCode = TranslateLanguage.fromLanguageTag(source.code)
        val targetLangCode = TranslateLanguage.fromLanguageTag(target.code)
        if (sourceLangCode == null || targetLangCode == null) {
            return Tasks.forCanceled()
        }
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLangCode)
            .setTargetLanguage(targetLangCode)
            .build()
        val translator = translators[options]
        modelDownloading.setValue(true)

        // Register watchdog to unblock long running downloads
        Handler().postDelayed({ modelDownloading.setValue(false) }, 15000)
        modelDownloadTask = translator.downloadModelIfNeeded().addOnCompleteListener {
            modelDownloading.setValue(false)
        }
        translating.value = true
        return modelDownloadTask.onSuccessTask {
            translator.translate(text)
        }.addOnCompleteListener {
            translating.value = false
        }
    }
    override fun onCleared() {
        //languageIdentifier.close()
        translators.evictAll()
        smartReply.close()
    }


    /**
     * SMART REPLY
     * */
    fun getSuggestions(): LiveData<List<SmartReplySuggestion>> {
        return suggestionResultList
    }

    private fun clearSuggestions() {
        suggestions.postValue(ArrayList())
    }

    private fun generateReplies(selectedText: String, timestamp: Long): Task<List<SmartReplySuggestion>> {
        val chatHistory = ArrayList<TextMessage>()
        chatHistory.add(
            TextMessage.createForRemoteUser(selectedText, timestamp, remoteUserId)
        )
        return smartReply
            .suggestReplies(chatHistory)
            .continueWith { task ->
                val result = task.result
                when (result.status) {
                    SmartReplySuggestionResult.STATUS_NOT_SUPPORTED_LANGUAGE ->
                        // This error happens when the detected language is not English, as that is the
                        // only supported language in Smart Reply.
                        Toast.makeText(
                            getApplication(),
                            R.string.error_not_supported_language,
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    SmartReplySuggestionResult.STATUS_NO_REPLY ->
                        // This error happens when the inference completed successfully, but no replies
                        // were returned.
                        Toast.makeText(getApplication(), R.string.error_no_reply, Toast.LENGTH_SHORT).show()
                    else -> {
                        // Do nothing.
                    }
                }
                result!!.suggestions
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Smart reply error", e)
                Toast.makeText(
                    getApplication(),
                    "Smart reply error" + "\nError: " + e.getLocalizedMessage() + "\nCause: " + e.cause,
                    Toast.LENGTH_LONG
                )
                    .show()
            }
    }



    /**
     * ENTITY EXTRACTION
     * */
    fun getEntities(): LiveData<List<String>> {
        return entitiesLiveList
    }

    fun extractEntities(input: String) {

        val entityExtractor = EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
        )

        /*currentModel = savedInstanceState?.getString(CURRENT_MODEL_KEY, EntityExtractorOptions.ENGLISH)
            ?: EntityExtractorOptions.ENGLISH
        getApplication<Application>()
        lifecycle.addObserver(entityExtractor)*/

        //output.setText(R.string.wait_message)
        _outputString.value = "Working…\\nMake sure network is on for the first time to download model"
        entityExtractor
            .downloadModelIfNeeded()
            .onSuccessTask {
                entityExtractor.annotate(
                    getEntityExtractionParams(
                        input
                    )
                )
            }
            .addOnFailureListener { e: Exception? ->
                Log.e(TAG, "Annotation failed", e)
                //output.text = getString(R.string.entity_extraction_error)
                _outputString.value = "Annotation failed"
            }
            .addOnSuccessListener { result: List<EntityAnnotation> ->
                if (result.isEmpty()) {
                    //output.text = getString(R.string.no_entity_detected)
                    _outputString.value = "There are no entities detected.\\n"
                    return@addOnSuccessListener
                }
                //output.text = getString(R.string.entities_detected).plus("\n")
                _outputString.value = "Entities detected: \\n"
                for (entityAnnotation in result) {
                    val entities = entityAnnotation.entities
                    val annotatedText = entityAnnotation.annotatedText
                    for (entity in entities) {
                        displayEntityInfo(annotatedText, entity)
                        //output.append("\n")
                        _outputString.value = "${_outputString.value}\n"
                        entitiesLiveList.value = entitiesListAdd
                    }
                }
            }
    }

    private fun displayEntityInfo(annotatedText: String, entity: Entity) {
        when (entity.type) {
            Entity.TYPE_ADDRESS -> displayAddressInfo(annotatedText)
            Entity.TYPE_DATE_TIME -> displayDateTimeInfo(entity, annotatedText)
            Entity.TYPE_EMAIL -> displayEmailInfo(annotatedText)
            Entity.TYPE_FLIGHT_NUMBER -> displayFlightNoInfo(entity, annotatedText)
            Entity.TYPE_IBAN -> displayIbanInfo(entity, annotatedText)
            Entity.TYPE_ISBN -> displayIsbnInfo(entity, annotatedText)
            Entity.TYPE_MONEY -> displayMoneyEntityInfo(entity, annotatedText)
            Entity.TYPE_PAYMENT_CARD -> displayPaymentCardInfo(entity, annotatedText)
            Entity.TYPE_PHONE -> displayPhoneInfo(annotatedText)
            Entity.TYPE_TRACKING_NUMBER -> displayTrackingNoInfo(entity, annotatedText)
            Entity.TYPE_URL -> displayUrlInfo(annotatedText)
            else -> displayDefaultInfo(annotatedText)
        }
    }

    private fun displayAddressInfo(annotatedText: String) {
        _outputString.value = "${_outputString.value} Address entity detected: $annotatedText"
        entitiesListAdd.add(annotatedText)
    }

    private fun displayEmailInfo(annotatedText: String) {
        _outputString.value = "${_outputString.value} Email entity detected: $annotatedText"
        entitiesListAdd.add(annotatedText)
    }

    private fun displayPhoneInfo(annotatedText: String) {
        _outputString.value = "${_outputString.value} Phone number entity detected: $annotatedText (formatted by libphonenumber: ${PhoneNumberUtils.formatNumber(annotatedText)}"
        entitiesListAdd.add(annotatedText)
    }

    private fun displayDefaultInfo(annotatedText: String) {
        _outputString.value = "${_outputString.value} Unknown entity detected: $annotatedText"
        entitiesListAdd.add(annotatedText)
    }

    private fun displayUrlInfo(annotatedText: String) {
        _outputString.value = "${_outputString.value} URL entity detected: $annotatedText"
        entitiesListAdd.add(annotatedText)
    }

    private fun displayDateTimeInfo(entity: Entity, annotatedText: String) {
        val dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG)
            .format(Date(entity.asDateTimeEntity()!!.timestampMillis))
        _outputString.value = "${_outputString.value} Date_time entity detected: $annotatedText.\n\t $dateTimeFormat (granularity: ${convertGranularityToString(entity)})."
        entitiesListAdd.add(annotatedText)
    }

    private fun convertGranularityToString(entity: Entity): String {
        val dateTimeEntity = entity.asDateTimeEntity()
        return when (dateTimeEntity!!.dateTimeGranularity) {
            DateTimeEntity.GRANULARITY_YEAR -> "Year"
            DateTimeEntity.GRANULARITY_MONTH -> "Month"
            DateTimeEntity.GRANULARITY_WEEK -> "Week"
            DateTimeEntity.GRANULARITY_DAY -> "Day"
            DateTimeEntity.GRANULARITY_HOUR -> "Hour"
            DateTimeEntity.GRANULARITY_MINUTE -> "Minute"
            DateTimeEntity.GRANULARITY_SECOND -> "Second"
            else -> "Unknown"
        }
    }

    private fun displayTrackingNoInfo(entity: Entity, annotatedText: String) {
        val trackingNumberEntity = entity.asTrackingNumberEntity()
        _outputString.value = "${_outputString.value} Tracking number entity detected: $annotatedText.\n" +
                "    Parcel carrier: ${trackingNumberEntity!!.parcelCarrier}, tracking number: $trackingNumberEntity.parcelTrackingNumber.\n"
        entitiesListAdd.add(annotatedText)
    }

    private fun displayPaymentCardInfo(entity: Entity, annotatedText: String) {
        val paymentCardEntity = entity.asPaymentCardEntity()
        _outputString.value = "${_outputString.value} Payment card entity detected: $annotatedText.\n" +
                "    Payment card network: ${paymentCardEntity!!.paymentCardNetwork}, payment card number: ${paymentCardEntity.paymentCardNumber}."
        entitiesListAdd.add(annotatedText)
    }

    private fun displayIsbnInfo(entity: Entity, annotatedText: String) {
        _outputString.value = "${_outputString.value} Isbn entity detected: $annotatedText.\\nIsbn:  ${entity.asIsbnEntity()!!.isbn}."
        entitiesListAdd.add(annotatedText)
    }

    private fun displayIbanInfo(entity: Entity, annotatedText: String) {
        val ibanEntity = entity.asIbanEntity()
        _outputString.value = "${_outputString.value} Iban entity detected: $annotatedText.\n" +
                "    \\nIban country code: ${ibanEntity!!.iban}, iban: ${ibanEntity.ibanCountryCode}."
        entitiesListAdd.add(annotatedText)
    }

    private fun displayFlightNoInfo(entity: Entity, annotatedText: String) {
        val flightNumberEntity = entity.asFlightNumberEntity()
        _outputString.value = "${_outputString.value} Flight number entity detected: $annotatedText.\n" +
                "    \\nAirline code: ${flightNumberEntity!!.airlineCode}, flight number: ${flightNumberEntity.flightNumber}."
    }

    private fun displayMoneyEntityInfo(entity: Entity, annotatedText: String) {
        val moneyEntity = entity.asMoneyEntity()
        _outputString.value = "${_outputString.value} Money entity detected: $annotatedText.\n" +
                "    \\nCurrency: ${moneyEntity!!.unnormalizedCurrency}, integer: ${moneyEntity.integerPart}, decimal: ${moneyEntity.fractionalPart}."
        entitiesListAdd.add(annotatedText)
    }

}
