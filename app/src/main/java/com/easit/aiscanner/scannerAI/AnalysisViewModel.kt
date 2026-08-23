package com.easit.aiscanner.scannerAI

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easit.aiscanner.data.Preference
import com.easit.aiscanner.database.ScanRepository
import com.easit.aiscanner.model.Scan
import com.easit.aiscanner.scannerAI.phase2.Language
import com.easit.aiscanner.scannerAI.phase2.SmoothedMutableLiveData
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.nl.smartreply.SmartReplySuggestion
import com.google.mlkit.nl.smartreply.TextMessage
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class AnalysisStatus { IDLE, IDENTIFYING, DOWNLOADING_MODEL, TRANSLATING, ANALYZING, COMPLETE, ERROR }

/**
 * One source of truth for the text/image/audio analysis pipeline.
 * Feature ViewModels inherit this behavior and retain only feature-specific state.
 */
abstract class AnalysisViewModel(
    private val preference: Preference,
    private val repository: ScanRepository
) : ViewModel() {

    lateinit var currentHistoryItem: LiveData<Scan>
        private set

    var selectedFontSize: Int
        get() = preference.appFontSize
        set(value) { preference.appFontSize = value }

    val targetLang = MutableLiveData<Language>()
    val availableLanguages: List<Language> = TranslateLanguage.getAllLanguages()
        .map(::Language)
        .sorted()

    private val _newTranslatedText = MutableLiveData<String>()
    val newTranslatedText: LiveData<String> = _newTranslatedText

    private val _sourceLanguageText = MutableLiveData<String>()
    val sourceLanguageText: LiveData<String> = _sourceLanguageText

    private val _status = MutableLiveData(AnalysisStatus.IDLE)
    val status: LiveData<AnalysisStatus> = _status

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    val modelDownloading = SmoothedMutableLiveData<Boolean>(50L).apply { setValue(false) }
    val suggestionResultList = MutableLiveData<List<SmartReplySuggestion>>(emptyList())
    val smartRepliesList = MutableLiveData<List<String>>(emptyList())
    val entitiesLiveList = MutableLiveData<List<String>>(emptyList())

    private val languageIdentifier = LanguageIdentification.getClient()
    private val smartReply = SmartReply.getClient()
    private val remoteUserId = UUID.randomUUID().toString()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var requestGeneration = 0

    private val translators = object : LruCache<TranslatorOptions, Translator>(2) {
        override fun create(options: TranslatorOptions): Translator = Translation.getClient(options)

        override fun entryRemoved(
            evicted: Boolean,
            key: TranslatorOptions,
            oldValue: Translator,
            newValue: Translator?
        ) = oldValue.close()
    }

    fun performLanguageActions(
        id: String,
        transcriptText: String,
        scanType: String,
        imageUrl: String
    ) {
        val normalizedText = AnalysisPolicy.normalizeInput(transcriptText)
        val target = targetLang.value
        if (normalizedText.isEmpty()) {
            fail("Enter or capture some text first.")
            return
        }
        if (target == null) {
            fail("Choose a target language first.")
            return
        }

        val generation = ++requestGeneration
        _errorMessage.value = null
        _status.value = AnalysisStatus.IDENTIFYING
        languageIdentifier.identifyLanguage(normalizedText)
            .addOnSuccessListener { sourceCode ->
                if (generation != requestGeneration) return@addOnSuccessListener
                if (sourceCode == "und") {
                    fail("The source language could not be identified.")
                    return@addOnSuccessListener
                }
                val sourceLanguage = Language(sourceCode).displayName
                _sourceLanguageText.value = sourceLanguage
                translate(
                    generation = generation,
                    id = id,
                    transcriptText = normalizedText,
                    sourceCode = sourceCode,
                    sourceLanguage = sourceLanguage,
                    target = target,
                    scanType = scanType,
                    imageUrl = imageUrl
                )
            }
            .addOnFailureListener {
                if (generation == requestGeneration) {
                    fail(it.message ?: "Language detection failed.")
                }
            }
    }

    private fun translate(
        generation: Int,
        id: String,
        transcriptText: String,
        sourceCode: String,
        sourceLanguage: String,
        target: Language,
        scanType: String,
        imageUrl: String
    ) {
        if (sourceCode == target.code) {
            _newTranslatedText.value = transcriptText
            analyzeAndPersist(
                generation, id, transcriptText, transcriptText, sourceLanguage,
                target, scanType, imageUrl
            )
            return
        }

        val supportedSource = TranslateLanguage.fromLanguageTag(sourceCode)
        val supportedTarget = TranslateLanguage.fromLanguageTag(target.code)
        if (supportedSource == null || supportedTarget == null) {
            fail("This language pair is not supported for offline translation.")
            return
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(supportedSource)
            .setTargetLanguage(supportedTarget)
            .build()
        val translator = translators[options]

        _status.value = AnalysisStatus.DOWNLOADING_MODEL
        modelDownloading.setValue(true)
        val watchdog = Runnable {
            if (generation == requestGeneration) {
                fail("The language model download timed out. Check your connection and try again.")
            }
        }
        mainHandler.postDelayed(watchdog, MODEL_DOWNLOAD_WATCHDOG_MS)

        translator.downloadModelIfNeeded()
            .onSuccessTask {
                if (generation != requestGeneration) return@onSuccessTask Tasks.forCanceled<String>()
                mainHandler.removeCallbacks(watchdog)
                modelDownloading.setValue(false)
                _status.value = AnalysisStatus.TRANSLATING
                translator.translate(transcriptText)
            }
            .addOnSuccessListener { translatedText ->
                if (generation != requestGeneration) return@addOnSuccessListener
                _newTranslatedText.value = translatedText
                analyzeAndPersist(
                    generation, id, transcriptText, translatedText, sourceLanguage,
                    target, scanType, imageUrl
                )
            }
            .addOnFailureListener {
                mainHandler.removeCallbacks(watchdog)
                modelDownloading.setValue(false)
                if (generation == requestGeneration) {
                    fail(it.message ?: "Translation failed. Check your connection and language models.")
                }
            }
    }

    private fun analyzeAndPersist(
        generation: Int,
        id: String,
        transcriptText: String,
        translatedText: String,
        sourceLanguage: String,
        target: Language,
        scanType: String,
        imageUrl: String
    ) {
        _status.value = AnalysisStatus.ANALYZING
        val replyTask = getSmartReplies(translatedText, target.code)
        val entityTask = getEnglishEntities(translatedText, target.code)

        Tasks.whenAllComplete(replyTask, entityTask).addOnCompleteListener {
            if (generation != requestGeneration) return@addOnCompleteListener

            val suggestions = replyTask.successfulResultOr(emptyList())
            val entities = entityTask.successfulResultOr(emptyList())
            suggestionResultList.value = suggestions
            smartRepliesList.value = suggestions.map { it.text }
            entitiesLiveList.value = entities

            if (!preference.appIncognitoMode) {
                saveScan(
                    Scan(
                        id = id,
                        dateTime = DATE_FORMATTER.format(LocalDateTime.now()),
                        timeMilliseconds = System.currentTimeMillis(),
                        scanType = scanType,
                        transcriptText = transcriptText,
                        translatedText = translatedText,
                        sourceLanguage = sourceLanguage,
                        translatedLanguage = target.displayName,
                        entities = entities,
                        smartReplies = suggestions.map { it.text },
                        imageUrl = imageUrl,
                        audioUrl = "",
                        barcodeScan = ""
                    )
                )
            }
            _status.value = AnalysisStatus.COMPLETE
        }
    }

    private fun getSmartReplies(
        translatedText: String,
        targetLanguageCode: String
    ): Task<List<SmartReplySuggestion>> {
        if (!AnalysisPolicy.supportsEnglishOnlyEnhancements(targetLanguageCode)) {
            return Tasks.forResult(emptyList())
        }
        val history = listOf(
            TextMessage.createForRemoteUser(
                translatedText,
                System.currentTimeMillis(),
                remoteUserId
            )
        )
        return smartReply.suggestReplies(history).continueWith { task ->
            if (!task.isSuccessful) throw task.exception ?: IllegalStateException("Smart Reply failed")
            task.result.suggestions
        }
    }

    private fun getEnglishEntities(
        translatedText: String,
        targetLanguageCode: String
    ): Task<List<String>> {
        if (!AnalysisPolicy.supportsEnglishOnlyEnhancements(targetLanguageCode)) {
            return Tasks.forResult(emptyList())
        }
        val extractor = EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
        )
        return extractor.downloadModelIfNeeded()
            .onSuccessTask {
                extractor.annotate(EntityExtractionParams.Builder(translatedText).build())
            }
            .continueWith { task ->
                extractor.close()
                if (!task.isSuccessful) {
                    Log.w(TAG, "Entity extraction failed", task.exception)
                    emptyList()
                } else {
                    task.result.map { it.annotatedText }.distinct()
                }
            }
    }

    private fun <T> Task<T>.successfulResultOr(default: T): T =
        if (isSuccessful) result ?: default else default

    private fun saveScan(scan: Scan) {
        viewModelScope.launch(Dispatchers.IO) { repository.insert(scan) }
    }

    fun getSelectedScanObject(id: String) {
        currentHistoryItem = repository.getSelectedScanById(id)
    }

    private fun fail(message: String) {
        requestGeneration++
        modelDownloading.setValue(false)
        _status.value = AnalysisStatus.ERROR
        _errorMessage.value = message
    }

    override fun onCleared() {
        requestGeneration++
        mainHandler.removeCallbacksAndMessages(null)
        translators.evictAll()
        smartReply.close()
        languageIdentifier.close()
        super.onCleared()
    }

    companion object {
        private const val TAG = "AnalysisViewModel"
        private const val MODEL_DOWNLOAD_WATCHDOG_MS = 30_000L
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
    }
}
