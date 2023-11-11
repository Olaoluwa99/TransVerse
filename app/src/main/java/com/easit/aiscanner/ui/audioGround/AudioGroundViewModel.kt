package com.easit.aiscanner.ui.audioGround

import android.app.Application
import android.os.Handler
import android.util.Log
import android.util.LruCache
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.easit.aiscanner.data.Preference
import com.easit.aiscanner.database.ScanDatabase
import com.easit.aiscanner.database.ScanRepository
import com.easit.aiscanner.model.Scan
import com.easit.aiscanner.scannerAI.phase2.Language
import com.easit.aiscanner.scannerAI.phase2.SmoothedMutableLiveData
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.nl.smartreply.SmartReplySuggestion
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult
import com.google.mlkit.nl.smartreply.TextMessage
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

@HiltViewModel
class AudioGroundViewModel @Inject constructor(
    application: Application,
    private val preference: Preference
) : AndroidViewModel(application) {
    private val repository : ScanRepository
    lateinit var currentHistoryItem: LiveData<Scan>

    var selectedFontSize
        get() = preference.appFontSize
        set(value) {
            preference.appFontSize = value
        }

    private var selectedIncognitoMode
        get() = preference.appIncognitoMode
        set(value) {
            preference.appIncognitoMode = value
        }

    var selectedReadingSpeed
        get() = preference.appReadingSpeed
        set(value) {
            preference.appReadingSpeed = value
        }

    var selectedSpeechPitch
        get() = preference.appSpeechPitch
        set(value) {
            preference.appSpeechPitch = value
        }

    companion object {
        //FOR IDENTIFICATION & TRANSLATION
        // Amount of time (in milliseconds) to wait for detected text to settle
        private const val SMOOTHING_DURATION = 50L
        private const val NUM_TRANSLATORS = 1

        //FOR SMART REPLY
        private const val TAG = "ImageGroundViewModel"

        private fun getEntityExtractionParams(input: String): EntityExtractionParams {
            return EntityExtractionParams.Builder(input).build()
        }
    }

    /** Identification and translation*/
    private val languageIdentifier = LanguageIdentification.getClient()
    val targetLang = MutableLiveData<Language>()


    private var _newTranslatedText = MutableLiveData<String>()
    val newTranslatedText: LiveData<String> = _newTranslatedText
    private var _sourceLanguageText = MutableLiveData<String>()
    val sourceLanguageText: LiveData<String> = _sourceLanguageText

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

    // Gets a list of all available translation languages.
    val availableLanguages: List<Language> = TranslateLanguage.getAllLanguages()
        .map { Language(it) }

    /**Smart reply*/
    private val remoteUserId = UUID.randomUUID().toString()
    private val smartReply = SmartReply.getClient()
    var suggestionResultList = MutableLiveData<List<SmartReplySuggestion>>()
    var smartRepliesList = MutableLiveData<List<String>>()

    /**Entity extraction*/
    var entitiesLiveList = MutableLiveData<List<String>>()


    init {
        val dao = ScanDatabase.getDatabase(application).getScanDao()
        repository = ScanRepository(dao)

        modelDownloading.setValue(false)
        translating.value = false
    }


    /**BEGIN ALL ACTIONS*/
    fun performLanguageActions(id: String, transcriptText: String, scanType: String,
                               imageUrl: String) {
        languageIdentifier.identifyLanguage(transcriptText)
            .addOnSuccessListener { languageCode ->
                if (languageCode != "und"){
                    _sourceLanguageText.value = Language(languageCode).displayName
                    translate(id = id, transcriptText = transcriptText,
                        sourceLanguage = Language(languageCode).displayName,
                        sourceLanguageCode = languageCode,
                        scanType = scanType,
                        imageUrl = imageUrl)
                }
            }
    }

    /**TRANSLATE TEXT*/
    private fun translate(id : String, transcriptText: String, sourceLanguageCode: String,
                          sourceLanguage: String, scanType: String, imageUrl: String){
        val target = targetLang.value
        if (modelDownloading.value != false || translating.value != false) {
            Log.e("Translate Error", "Translate Error 1 here" )
        }
        if (target == null || transcriptText.isEmpty()) {
            Log.e("Translate Error",
                "Translate Error 2 here, source: $sourceLanguage, target: $target, text: $transcriptText" )
        }else{
            val sourceLangCode = TranslateLanguage.fromLanguageTag(sourceLanguageCode)
            val targetLangCode = TranslateLanguage.fromLanguageTag(target.code)
            if (sourceLangCode == null || targetLangCode == null) {
                Log.e("Translate Error", "Translate Error 3 here" )
            }
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLangCode!!)
                .setTargetLanguage(targetLangCode!!)
                .build()
            val translator = translators[options]
            modelDownloading.setValue(true)

            // Register watchdog to unblock long running downloads
            Handler().postDelayed({ modelDownloading.setValue(false) }, 15000)
            modelDownloadTask = translator.downloadModelIfNeeded().addOnCompleteListener {
                modelDownloading.setValue(false)
            }
            translating.value = true

            modelDownloadTask.onSuccessTask {
                translator.translate(transcriptText)
            }.addOnSuccessListener {
                translating.value = false
                _newTranslatedText.value = it
                if (!it.isNullOrBlank()){
                    //TODO SHOW SMART REPLY
                    Log.d("Translate Error", "Before thr generate reply block")
                    generateReplies(id = id, transcriptText = transcriptText, translatedText = it,
                        sourceLanguage = sourceLanguage, translatedLanguage = target.displayName,
                        scanType = scanType, imageUrl = imageUrl,
                        System.currentTimeMillis())
                }
            }.addOnFailureListener {
                translating.value = false
                Log.e("Translate Error", "Final translate Error here" )
            }
        }
    }

    /**SMART REPLY*/
    private fun generateReplies(id : String, transcriptText: String, translatedText: String,
                                sourceLanguage: String, translatedLanguage: String, scanType: String,
                                imageUrl: String, timestamp: Long) {
        val chatHistory = ArrayList<TextMessage>()
        chatHistory.add(
            TextMessage.createForRemoteUser(translatedText, timestamp, remoteUserId)
        )
        smartReply
            .suggestReplies(chatHistory)
            .continueWith { task ->
                val result = task.result
                when (result.status) {
                    SmartReplySuggestionResult.STATUS_NOT_SUPPORTED_LANGUAGE -> {
                        //Toast.makeText(getApplication(), "R.string.error_not_supported_language", Toast.LENGTH_SHORT).show()
                        }
                    SmartReplySuggestionResult.STATUS_NO_REPLY -> {
                        //Toast.makeText(getApplication(), "R.string.error_no_reply", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        // Do nothing.
                    }
                }
                result!!.suggestions
            }.addOnSuccessListener {
                suggestionResultList.value = it
                val repliesList = mutableListOf<String>()
                for (i in it){
                    repliesList.add(i.text)
                }
                smartRepliesList.value = repliesList
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Smart reply error", e)
                //Toast.makeText(getApplication(), "Smart reply error" + "\nError: " + e.localizedMessage + "\nCause: " + e.cause, Toast.LENGTH_LONG).show()
            }
        extractEntities(id = id, transcriptText = transcriptText, translatedText = translatedText,
            sourceLanguage = sourceLanguage, translatedLanguage = translatedLanguage, scanType = scanType,
            imageUrl = imageUrl, smartReplies = smartRepliesList.value ?: emptyList()
        )
    }

    /**ENTITY EXTRACTION*/
    private fun extractEntities(id : String, translatedText: String, transcriptText: String,
                                sourceLanguage: String, translatedLanguage: String, scanType: String,
                                smartReplies: List<String>, imageUrl: String) {

        val entityExtractor = EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
        )

        entityExtractor.downloadModelIfNeeded().onSuccessTask {
            entityExtractor.annotate(
                getEntityExtractionParams(
                    transcriptText
                )
            )
        }.addOnFailureListener { e: Exception? ->
                Log.e(TAG, "Annotation failed", e)
            }.addOnSuccessListener { result: List<EntityAnnotation> ->
                if (result.isEmpty()) {
                    return@addOnSuccessListener
                }
                val entitiesList = mutableListOf<String>()
                for (entityAnnotation in result) {
                    entitiesList.add(entityAnnotation.annotatedText)
                }
                entitiesLiveList.value = entitiesList
            }
        //Final
        if (!selectedIncognitoMode!!){
            createScan(id, transcriptText = transcriptText, translatedText = translatedText,
                sourceLanguage = sourceLanguage, translatedLanguage = translatedLanguage, scanType = scanType,
                imageUrl = imageUrl,
                smartReplies = smartReplies, entitiesList = entitiesLiveList.value ?: emptyList()
            )
        }
    }

    override fun onCleared() {
        //languageIdentifier.close()
        translators.evictAll()
        smartReply.close()
    }

    fun getSelectedScanObject(id: String) = viewModelScope.launch{
        currentHistoryItem = repository.getSelectedScanById(id)
    }
    private fun createScan(id: String, translatedText: String, transcriptText: String,
                           sourceLanguage: String, translatedLanguage: String, scanType: String,
                           smartReplies: List<String>, entitiesList: List<String>, imageUrl: String){

        val scan = Scan(id, getCurrentDateTime(), System.currentTimeMillis(), scanType, transcriptText,
            translatedText, sourceLanguage, translatedLanguage, entitiesList, smartReplies, imageUrl,
            "", "barcodeScan")

        addScan(scan)
    }
    private fun addScan(scan: Scan) = viewModelScope.launch (Dispatchers.IO) {
        repository.insert(scan)
    }
    private fun getCurrentDateTime(): String {
        val currentTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
        return currentTime.format(formatter)
    }
}