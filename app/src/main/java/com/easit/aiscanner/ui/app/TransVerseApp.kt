package com.easit.aiscanner.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.easit.aiscanner.ui.barcode.BarcodeScannerScreen
import com.easit.aiscanner.ui.history.HistoryScreen
import com.easit.aiscanner.ui.settings.HelpScreen
import com.easit.aiscanner.ui.settings.LicensesScreen
import com.easit.aiscanner.ui.settings.PrivacyPolicyScreen
import com.easit.aiscanner.ui.settings.SettingsScreen
import com.easit.aiscanner.ui.translate.SavedScanScreen
import com.easit.aiscanner.ui.translate.TranslateScreen

/**
 * App shell: adaptive navigation (bottom bar on phones, rail from 600dp) around one NavHost.
 *
 * @param incoming content waiting for the Translate screen, if any
 * @param onIncomingConsumed called by the Translate screen once it has taken [incoming]
 * @param onOfferIncoming hands new content to the Translate screen (e.g. a scanned barcode)
 * @param onStartQuickCapture starts the floating capture bubble (asks for permissions first)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransVerseApp(
    incoming: IncomingContent?,
    onIncomingConsumed: () -> Unit,
    onOfferIncoming: (IncomingContent) -> Unit,
    onStartQuickCapture: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { it.route == currentRoute }
    val useRail = LocalConfiguration.current.screenWidthDp >= RAIL_MIN_WIDTH_DP

    // Shared or captured content always lands on the Translate screen.
    LaunchedEffect(incoming) {
        if (incoming != null && navController.currentDestination?.route != Destinations.TRANSLATE) {
            navController.navigateToTopLevel(Destinations.TRANSLATE)
        }
    }

    val navHost: @Composable (Modifier) -> Unit = { modifier ->
        TransVerseNavHost(
            navController = navController,
            incoming = incoming,
            onIncomingConsumed = onIncomingConsumed,
            onOfferIncoming = onOfferIncoming,
            onStartQuickCapture = onStartQuickCapture,
            modifier = modifier,
        )
    }

    if (useRail) {
        Row(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = currentTopLevel != null) {
                WideNavigationRail {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = destination == currentTopLevel
                        WideNavigationRailItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(destination.route) },
                            icon = { DestinationIcon(destination, selected) },
                            label = { Text(stringResource(destination.label)) },
                            railExpanded = false,
                        )
                    }
                }
            }
            navHost(Modifier.weight(1f))
        }
    } else {
        Scaffold(
            // Each screen applies its own top insets; this scaffold only reserves the bar.
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                AnimatedVisibility(
                    visible = currentTopLevel != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    ShortNavigationBar {
                        TopLevelDestination.entries.forEach { destination ->
                            val selected = destination == currentTopLevel
                            ShortNavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigateToTopLevel(destination.route) },
                                icon = { DestinationIcon(destination, selected) },
                                label = { Text(stringResource(destination.label)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            navHost(Modifier.padding(padding).consumeWindowInsets(padding))
        }
    }
}

@Composable
private fun DestinationIcon(destination: TopLevelDestination, selected: Boolean) {
    Icon(
        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
        contentDescription = null,
    )
}

@Composable
private fun TransVerseNavHost(
    navController: NavHostController,
    incoming: IncomingContent?,
    onIncomingConsumed: () -> Unit,
    onOfferIncoming: (IncomingContent) -> Unit,
    onStartQuickCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Expressive motion: spring-based spatial movement with quick fades.
    val spatial: FiniteAnimationSpec<IntOffset> = MaterialTheme.motionScheme.defaultSpatialSpec()
    val effects: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultEffectsSpec()
    val pop: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = Destinations.TRANSLATE,
        modifier = modifier,
        // Top-level switches cross-fade; pushed screens slide in from the end.
        enterTransition = {
            if (initialState.isTopLevel() && targetState.isTopLevel()) fadeIn(effects)
            else slideInHorizontally(spatial) { it / 4 } + fadeIn(effects)
        },
        exitTransition = { fadeOut(effects) },
        popEnterTransition = { fadeIn(effects) },
        popExitTransition = {
            if (initialState.isTopLevel()) fadeOut(effects)
            else slideOutHorizontally(spatial) { it / 4 } + fadeOut(effects)
        },
    ) {
        composable(Destinations.TRANSLATE) {
            TranslateScreen(
                incoming = incoming,
                onIncomingConsumed = onIncomingConsumed,
                onOpenBarcodeScanner = { navController.navigate(Destinations.BARCODE) },
                onStartQuickCapture = onStartQuickCapture,
            )
        }
        composable(Destinations.HISTORY) {
            HistoryScreen(
                onOpenScan = { scanId -> navController.navigate(Destinations.savedScan(scanId)) },
                onStartTranslating = { navController.navigateToTopLevel(Destinations.TRANSLATE) },
            )
        }
        composable(Destinations.SETTINGS) {
            SettingsScreen(
                onOpenHelp = { navController.navigate(Destinations.HELP) },
                onOpenPrivacyPolicy = { navController.navigate(Destinations.PRIVACY_POLICY) },
                onOpenLicenses = { navController.navigate(Destinations.LICENSES) },
                onStartQuickCapture = onStartQuickCapture,
            )
        }
        composable(
            route = Destinations.SAVED_SCAN,
            arguments = listOf(navArgument(Destinations.SCAN_ID_ARG) { type = NavType.StringType }),
        ) { entry ->
            SavedScanScreen(
                scanId = entry.arguments?.getString(Destinations.SCAN_ID_ARG).orEmpty(),
                onBack = pop,
            )
        }
        composable(Destinations.BARCODE) {
            BarcodeScannerScreen(
                onBack = pop,
                onTranslateText = { text ->
                    onOfferIncoming(IncomingContent.Text(text))
                    navController.navigateToTopLevel(Destinations.TRANSLATE)
                },
            )
        }
        composable(Destinations.HELP) { HelpScreen(onBack = pop) }
        composable(Destinations.PRIVACY_POLICY) { PrivacyPolicyScreen(onBack = pop) }
        composable(Destinations.LICENSES) { LicensesScreen(onBack = pop) }
    }
}

private fun NavBackStackEntry.isTopLevel(): Boolean =
    TopLevelDestination.entries.any { it.route == destination.route }

/** Switches top-level tab, keeping one copy of each tab and restoring its state. */
fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private const val RAIL_MIN_WIDTH_DP = 600
