package com.easit.aiscanner

import com.easit.aiscanner.scannerAI.AnalysisPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPolicyTest {
    @Test
    fun normalizeInput_removesOuterWhitespace_only() {
        assertEquals("hello   world", AnalysisPolicy.normalizeInput("  hello   world\n"))
    }

    @Test
    fun canAnalyze_requiresTextAndTargetLanguage() {
        assertTrue(AnalysisPolicy.canAnalyze("Bonjour", hasTargetLanguage = true))
        assertFalse(AnalysisPolicy.canAnalyze("   ", hasTargetLanguage = true))
        assertFalse(AnalysisPolicy.canAnalyze("Bonjour", hasTargetLanguage = false))
    }

    @Test
    fun EnglishOnlyEnhancements_areNotOfferedForOtherTargets() {
        assertTrue(AnalysisPolicy.supportsEnglishOnlyEnhancements("en"))
        assertFalse(AnalysisPolicy.supportsEnglishOnlyEnhancements("fr"))
        assertFalse(AnalysisPolicy.supportsEnglishOnlyEnhancements("yo"))
    }
}
