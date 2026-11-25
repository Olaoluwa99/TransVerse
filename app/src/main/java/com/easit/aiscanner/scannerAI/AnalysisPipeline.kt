package com.easit.aiscanner.scannerAI

import android.util.Log
import android.util.LruCache
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.nl.smartreply.TextMessage
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Where an analysis currently is; drives the progress text on the Translate screen. */
enum class AnalysisStatus {
    IDLE,
    /** Play services is still downloading the language-identification module (first use). */
    PREPARING,
    IDENTIFYING,
    DOWNLOADING_MODEL,
    TRANSLATING,
    ANALYZING,
    COMPLETE,
    ERROR,
}

/** Why an analysis stopped. */
enum class AnalysisFailure {
    EMPTY_INPUT,
    /** The language-identification module couldn't be installed (usually no connection). */
    LANGUAGE_SETUP_FAILED,
    LANGUAGE_UNDETERMINED,
    LANGUAGE_DETECTION_FAILED,
    UNSUPPORTED_PAIR,
    MODEL_DOWNLOAD_TIMED_OUT,
    TRANSLATION_FAILED,
}

class AnalysisException(val failure: AnalysisFailure, cause: Throwable? = null) :
    Exception(failure.name, cause)

/** The outcome of a successful analysis. Saving it is the caller's decision. */
data class AnalysisOutput(
    val sourceText: String,
    val sourceLanguageCode: String,
    val sourceLanguageName: String,
    val targetLanguageCode: String,
    val translatedText: String,
    val smartReplies: List<String>,
    val entities: List<String>,
)

/** Per-block translations from [AnalysisPipeline.translateBlocks], in the same order as the input. */
data class BlockTranslation(
    val sourceLanguageCode: String,
    val sourceLanguageName: String,
    val targetLanguageCode: String,
    val translations: List<String>,
)

/**
 * The text analysis pipeline shared by every capture mode:
 * language identification → translation model download (with a watchdog) → translation →
 * English-only smart replies and entity extraction.
 *
 * Not thread-safe; call [analyze] from the main thread. Cancelling the calling coroutine abandons
 * the run, which replaces the old request-generation bookkeeping. Call [close] when done.
 */
class AnalysisPipeline(private val modules: MlKitModules) : Closeable {

    private val languageIdentifier = LanguageIdentification.getClient()
    private val smartReply = SmartReply.getClient()
    private val remoteUserId = UUID.randomUUID().toString()

    private val translators = object : LruCache<TranslatorOptions, Translator>(2) {
        override fun create(options: TranslatorOptions): Translator = Translation.getClient(options)

        override fun entryRemoved(
            evicted: Boolean,
            key: TranslatorOptions,
            oldValue: Translator,
            newValue: Translator?
        ) = oldValue.close()
    }

    /**
     * Analyses [text] into [targetLanguageCode].
     *
     * @param onStatus progress updates, on the caller's thread
     * @param onLanguageDetected the identified source language (code, display name)
     * @param onSetupProgress while [AnalysisStatus.PREPARING]: module download progress from 0 to
     *   1, or null while its size isn't known yet
     * @throws AnalysisException when the analysis cannot complete
     */
    suspend fun analyze(
        text: String,
        targetLanguageCode: String,
        onStatus: (AnalysisStatus) -> Unit,
        onLanguageDetected: (code: String, name: String) -> Unit,
        onSetupProgress: (Float?) -> Unit = {},
    ): AnalysisOutput {
        val normalizedText = AnalysisPolicy.normalizeInput(text)
        if (normalizedText.isEmpty()) throw AnalysisException(AnalysisFailure.EMPTY_INPUT)

        val sourceCode = identifyLanguage(normalizedText, onStatus, onSetupProgress)
        val sourceName = displayNameOf(sourceCode)
        onLanguageDetected(sourceCode, sourceName)

        val translatedText = if (sourceCode == targetLanguageCode) {
            normalizedText
        } else {
            translate(normalizedText, sourceCode, targetLanguageCode, onStatus)
        }

        onStatus(AnalysisStatus.ANALYZING)
        val (replies, entities) = coroutineScope {
            val replies = async { smartReplies(translatedText, targetLanguageCode) }
            val entities = async { englishEntities(translatedText, targetLanguageCode) }
            replies.await() to entities.await()
        }

        return AnalysisOutput(
            sourceText = normalizedText,
            sourceLanguageCode = sourceCode,
            sourceLanguageName = sourceName,
            targetLanguageCode = targetLanguageCode,
            translatedText = translatedText,
            smartReplies = replies,
            entities = entities,
        )
    }

    /**
     * Translates each of [texts] separately into [targetLanguageCode] (e.g. OCR text blocks, so
     * each translation can be drawn over its own block). The source language is identified once
     * from all of them together. Blank entries stay blank. No smart replies, entities or saving.
     *
     * @throws AnalysisException when the translation cannot complete
     */
    suspend fun translateBlocks(
        texts: List<String>,
        targetLanguageCode: String,
        onStatus: (AnalysisStatus) -> Unit = {},
        onSetupProgress: (Float?) -> Unit = {},
    ): BlockTranslation {
        val joined = AnalysisPolicy.normalizeInput(texts.joinToString("\n"))
        if (joined.isEmpty()) throw AnalysisException(AnalysisFailure.EMPTY_INPUT)

        val sourceCode = identifyLanguage(joined, onStatus, onSetupProgress)
        val translations = if (sourceCode == targetLanguageCode) {
            texts
        } else {
            texts.map { block ->
                val normalized = AnalysisPolicy.normalizeInput(block)
                if (normalized.isEmpty()) block
                else translate(normalized, sourceCode, targetLanguageCode, onStatus)
            }
        }
        onStatus(AnalysisStatus.COMPLETE)
        return BlockTranslation(
            sourceLanguageCode = sourceCode,
            sourceLanguageName = displayNameOf(sourceCode),
            targetLanguageCode = targetLanguageCode,
            translations = translations,
        )
    }

    /** Identifies [normalizedText]'s language, installing the ML Kit module first if needed. */
    private suspend fun identifyLanguage(
        normalizedText: String,
        onStatus: (AnalysisStatus) -> Unit,
        onSetupProgress: (Float?) -> Unit,
    ): String {
        // First use: wait for Play services to deliver language identification instead of
        // failing with "Waiting for the langid optional module to be downloaded".
        try {
            modules.ensureInstalled(listOf(languageIdentifier)) { progress ->
                onStatus(AnalysisStatus.PREPARING)
                onSetupProgress(progress)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AnalysisException(AnalysisFailure.LANGUAGE_SETUP_FAILED, e)
        }

        onStatus(AnalysisStatus.IDENTIFYING)
        val sourceCode = try {
            MlKitModules.retryWhileUnavailable {
                languageIdentifier.identifyLanguage(normalizedText).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AnalysisException(AnalysisFailure.LANGUAGE_DETECTION_FAILED, e)
        }
        if (sourceCode == UNDETERMINED_LANGUAGE) {
            throw AnalysisException(AnalysisFailure.LANGUAGE_UNDETERMINED)
        }
        return sourceCode
    }

    private suspend fun translate(
        text: String,
        sourceCode: String,
        targetCode: String,
        onStatus: (AnalysisStatus) -> Unit,
    ): String {
        val supportedSource = TranslateLanguage.fromLanguageTag(sourceCode)
        val supportedTarget = TranslateLanguage.fromLanguageTag(targetCode)
        if (supportedSource == null || supportedTarget == null) {
            throw AnalysisException(AnalysisFailure.UNSUPPORTED_PAIR)
        }
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(supportedSource)
            .setTargetLanguage(supportedTarget)
            .build()
        val translator = translators[options]

        onStatus(AnalysisStatus.DOWNLOADING_MODEL)
        try {
            // Translation models are roughly 30 MB each; ML Kit reports no progress, so this only
            // catches downloads that have genuinely stalled rather than ones on slow connections.
            withTimeout(MODEL_DOWNLOAD_WATCHDOG_MS) { translator.downloadModelIfNeeded().await() }
        } catch (e: TimeoutCancellationException) {
            throw AnalysisException(AnalysisFailure.MODEL_DOWNLOAD_TIMED_OUT, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AnalysisException(AnalysisFailure.TRANSLATION_FAILED, e)
        }

        onStatus(AnalysisStatus.TRANSLATING)
        return try {
            translator.translate(text).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AnalysisException(AnalysisFailure.TRANSLATION_FAILED, e)
        }
    }

    private suspend fun smartReplies(translatedText: String, targetLanguageCode: String): List<String> {
        if (!AnalysisPolicy.supportsEnglishOnlyEnhancements(targetLanguageCode)) return emptyList()
        val history = listOf(
            TextMessage.createForRemoteUser(translatedText, System.currentTimeMillis(), remoteUserId)
        )
        return try {
            smartReply.suggestReplies(history).await().suggestions.map { it.text }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Smart Reply failed", e)
            emptyList()
        }
    }

    private suspend fun englishEntities(translatedText: String, targetLanguageCode: String): List<String> {
        if (!AnalysisPolicy.supportsEnglishOnlyEnhancements(targetLanguageCode)) return emptyList()
        val extractor = EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
        )
        return try {
            extractor.downloadModelIfNeeded().await()
            extractor.annotate(EntityExtractionParams.Builder(translatedText).build())
                .await()
                .map { it.annotatedText }
                .distinct()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Entity extraction failed", e)
            emptyList()
        } finally {
            extractor.close()
        }
    }

    override fun close() {
        translators.evictAll()
        smartReply.close()
        languageIdentifier.close()
    }

    companion object {
        private const val TAG = "AnalysisPipeline"
        private const val UNDETERMINED_LANGUAGE = "und"
        private const val MODEL_DOWNLOAD_WATCHDOG_MS = 5 * 60_000L

        /** The language's name in the user's locale, e.g. "French" for "fr". */
        fun displayNameOf(code: String): String =
            Locale.forLanguageTag(code).getDisplayName(Locale.getDefault())
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

/** Suspends until this Play Services [Task] completes, without the play-services coroutines artifact. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        when {
            task.isCanceled -> continuation.cancel()
            task.isSuccessful -> {
                @Suppress("UNCHECKED_CAST")
                continuation.resume(task.result as T)
            }
            else -> continuation.resumeWithException(
                task.exception ?: IllegalStateException("Task failed without an exception")
            )
        }
    }
}
