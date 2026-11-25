package com.easit.aiscanner.ui.translate

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers the chosen translation target and the most recently used targets. */
@Singleton
class RecentLanguages @Inject constructor(@ApplicationContext context: Context) {

    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** The target chosen last time, or English. */
    val currentTarget: String
        get() = preferences.getString(KEY_TARGET, null) ?: DEFAULT_TARGET

    /** Most recent first, at most [MAX_RECENT]. */
    val recentTargets: List<String>
        get() = preferences.getString(KEY_RECENT, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    /** Makes [code] the current target and moves it to the front of the recent list. */
    fun useTarget(code: String) {
        val recent = (listOf(code) + recentTargets.filterNot { it == code }).take(MAX_RECENT)
        preferences.edit {
            putString(KEY_TARGET, code)
            putString(KEY_RECENT, recent.joinToString(SEPARATOR))
        }
    }

    private companion object {
        const val FILE_NAME = "translate_languages"
        const val KEY_TARGET = "target"
        const val KEY_RECENT = "recent_targets"
        const val SEPARATOR = ","
        const val DEFAULT_TARGET = "en"
        const val MAX_RECENT = 5
    }
}
