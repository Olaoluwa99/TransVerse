package com.easit.aiscanner.scannerAI.phase2

import java.util.Locale

class Language(val code: String) : Comparable<Language> {

    val displayName: String
        get() = Locale.forLanguageTag(code).getDisplayName(Locale.getDefault())

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other !is Language) {
            return false
        }

        return other.code.equals(code, ignoreCase = true)
    }

    override fun toString(): String {
        return displayName
    }

    override fun compareTo(other: Language): Int {
        return this.displayName.compareTo(other.displayName)
    }

    override fun hashCode(): Int {
        return code.lowercase(Locale.ROOT).hashCode()
    }
}
