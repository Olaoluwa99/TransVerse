package com.easit.aiscanner.ui.app

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.ui.graphics.vector.ImageVector
import com.easit.aiscanner.R

/** Navigation routes. Top-level destinations show the navigation bar or rail. */
object Destinations {
    const val TRANSLATE = "translate"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    const val SCAN_ID_ARG = "scanId"
    const val SAVED_SCAN = "scan/{$SCAN_ID_ARG}"
    fun savedScan(scanId: String) = "scan/$scanId"

    const val BARCODE = "barcode"
    const val HELP = "settings/help"
    const val PRIVACY_POLICY = "settings/privacy"
    const val LICENSES = "settings/licenses"
}

enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    TRANSLATE(Destinations.TRANSLATE, R.string.nav_translate, Icons.Filled.Translate, Icons.Outlined.Translate),
    HISTORY(Destinations.HISTORY, R.string.nav_history, Icons.Filled.History, Icons.Outlined.History),
    SETTINGS(Destinations.SETTINGS, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}
