package com.easit.aiscanner.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.easit.aiscanner.R
import com.easit.aiscanner.ui.theme.TransVerseTheme
import kotlinx.coroutines.launch

// Help & support, Privacy policy and Third-party licences: pushed, read-only pages.

/** One titled block of an info page. */
private data class InfoSection(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val body: Int,
)

private val helpSections = listOf(
    InfoSection(
        Icons.Rounded.Translate,
        R.string.settings_help_getting_started_title,
        R.string.settings_help_getting_started_body,
    ),
    InfoSection(
        Icons.Rounded.PictureInPictureAlt,
        R.string.settings_help_quick_capture_title,
        R.string.settings_help_quick_capture_body,
    ),
    InfoSection(
        Icons.Rounded.Link,
        R.string.settings_help_results_title,
        R.string.settings_help_results_body,
    ),
    InfoSection(
        Icons.Rounded.VisibilityOff,
        R.string.settings_help_privacy_title,
        R.string.settings_help_privacy_body,
    ),
)

private val privacySections = listOf(
    InfoSection(
        Icons.Rounded.Shield,
        R.string.settings_privacy_overview_title,
        R.string.settings_privacy_overview_body,
    ),
    InfoSection(
        Icons.Rounded.AutoAwesome,
        R.string.settings_privacy_models_title,
        R.string.settings_privacy_models_body,
    ),
    InfoSection(
        Icons.Rounded.Lock,
        R.string.settings_privacy_permissions_title,
        R.string.settings_privacy_permissions_body,
    ),
    InfoSection(
        Icons.Rounded.Storage,
        R.string.settings_privacy_storage_title,
        R.string.settings_privacy_storage_body,
    ),
    InfoSection(
        Icons.Rounded.Share,
        R.string.settings_privacy_sharing_title,
        R.string.settings_privacy_sharing_body,
    ),
)

/** A third-party component. Names and licence names are proper nouns, so not translated. */
private data class LicensedComponent(val name: String, val license: String, val url: String)

private const val APACHE_2 = "Apache License 2.0"

// Mirrors the dependencies in app/build.gradle (plus the bundled Figtree typeface).
private val licensedComponents = listOf(
    LicensedComponent(
        "AndroidX and Jetpack Compose (incl. Material 3, CameraX, Room, Navigation, Lifecycle)",
        APACHE_2,
        "https://developer.android.com/jetpack/androidx",
    ),
    LicensedComponent(
        "Material Components for Android",
        APACHE_2,
        "https://github.com/material-components/material-components-android",
    ),
    LicensedComponent("Dagger and Hilt", APACHE_2, "https://github.com/google/dagger"),
    LicensedComponent(
        "Kotlin and kotlinx.coroutines",
        APACHE_2,
        "https://github.com/Kotlin/kotlinx.coroutines",
    ),
    LicensedComponent("Coil", APACHE_2, "https://github.com/coil-kt/coil"),
    LicensedComponent("Gson", APACHE_2, "https://github.com/google/gson"),
    LicensedComponent(
        "Google ML Kit",
        "ML Kit Terms of Service",
        "https://developers.google.com/ml-kit/terms",
    ),
    LicensedComponent(
        "Figtree typeface",
        "SIL Open Font License 1.1",
        "https://github.com/erikdkennedy/figtree",
    ),
)

private val licenseTexts = listOf(
    LicensedComponent(APACHE_2, "apache.org", "https://www.apache.org/licenses/LICENSE-2.0"),
    LicensedComponent("SIL Open Font License 1.1", "openfontlicense.org", "https://openfontlicense.org"),
)

@Composable
fun HelpScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    InfoScaffold(
        title = stringResource(R.string.title_help),
        onBack = onBack,
        modifier = modifier,
    ) {
        item(key = "hero") {
            InfoHero(
                icon = Icons.AutoMirrored.Rounded.HelpOutline,
                text = stringResource(R.string.settings_help_intro),
            )
        }
        infoSections(helpSections)
    }
}

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    InfoScaffold(
        title = stringResource(R.string.title_privacy_policy),
        onBack = onBack,
        modifier = modifier,
    ) {
        item(key = "hero") {
            InfoHero(
                icon = Icons.Rounded.PrivacyTip,
                text = stringResource(R.string.settings_privacy_intro),
            )
        }
        infoSections(privacySections)
    }
}

@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val linkUnavailable = stringResource(R.string.settings_link_unavailable)
    val openLink: (String) -> Unit = { url ->
        // AndroidUriHandler throws when no app can handle the link.
        val opened = runCatching { uriHandler.openUri(url) }.isSuccess
        if (!opened) scope.launch { snackbarHostState.showSnackbar(linkUnavailable) }
    }

    InfoScaffold(
        title = stringResource(R.string.settings_licenses),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        item(key = "hero") {
            InfoHero(
                icon = Icons.Rounded.Gavel,
                text = stringResource(R.string.settings_licenses_intro),
            )
        }
        item(key = "terms") {
            InfoSectionCard(
                InfoSection(
                    Icons.Rounded.Gavel,
                    R.string.settings_licenses_terms_title,
                    R.string.settings_licenses_terms_body,
                ),
            )
        }
        item(key = "libraries") {
            LinkGroup(
                title = stringResource(R.string.settings_licenses_libraries_title),
                links = licensedComponents,
                onOpen = openLink,
            )
        }
        item(key = "texts") {
            LinkGroup(
                title = stringResource(R.string.settings_licenses_texts_title),
                links = licenseTexts,
                onOpen = openLink,
            )
        }
        item(key = "source") {
            InfoSectionCard(
                InfoSection(
                    Icons.Rounded.Code,
                    R.string.settings_licenses_source_title,
                    R.string.settings_licenses_source_body,
                ),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** Pushed-screen frame: collapsing medium top app bar with back, scrolling content. */
@Composable
private fun InfoScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

private fun LazyListScope.infoSections(sections: List<InfoSection>) {
    items(count = sections.size, key = { sections[it].title }) { index ->
        InfoSectionCard(sections[index])
    }
}

/** Expressive page intro: the page icon in a cookie-shaped container above a short summary. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InfoHero(icon: ImageVector, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun InfoSectionCard(section: InfoSection, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(section.title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Text(
                text = stringResource(section.body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LinkGroup(
    title: String,
    links: List<LicensedComponent>,
    onOpen: (String) -> Unit,
) {
    SettingsGroup(title = title) {
        links.forEach { link ->
            item { shapes ->
                SettingsItem(
                    shapes = shapes,
                    headline = link.name,
                    supporting = link.license,
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = stringResource(R.string.settings_open_website, link.name),
                        )
                    },
                    onClick = { onOpen(link.url) },
                )
            }
        }
    }
}

@Preview(name = "Help", showBackground = true, heightDp = 1200)
@Composable
private fun HelpScreenPreview() {
    TransVerseTheme { HelpScreen(onBack = {}) }
}

@Preview(name = "Privacy policy", showBackground = true, heightDp = 1400)
@Composable
private fun PrivacyPolicyScreenPreview() {
    TransVerseTheme { PrivacyPolicyScreen(onBack = {}) }
}

@Preview(name = "Licences", showBackground = true, heightDp = 1800)
@Composable
private fun LicensesScreenPreview() {
    TransVerseTheme { LicensesScreen(onBack = {}) }
}
