package com.easit.aiscanner.scannerAI

/** Pure product rules kept separate from ML SDK orchestration so they are easy to verify. */
object AnalysisPolicy {
    fun normalizeInput(text: String): String = text.trim()

    fun canAnalyze(text: String, hasTargetLanguage: Boolean): Boolean =
        normalizeInput(text).isNotEmpty() && hasTargetLanguage

    fun supportsEnglishOnlyEnhancements(targetLanguageCode: String): Boolean =
        targetLanguageCode == ENGLISH_LANGUAGE_TAG

    private const val ENGLISH_LANGUAGE_TAG = "en"
}
