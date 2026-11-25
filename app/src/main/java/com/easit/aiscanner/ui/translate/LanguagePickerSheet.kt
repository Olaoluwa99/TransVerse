package com.easit.aiscanner.ui.translate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.easit.aiscanner.R
import com.easit.aiscanner.ui.theme.TransVerseTheme
import kotlinx.coroutines.launch

/** Searchable target-language picker: recent targets first, then every language. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LanguagePickerSheet(
    picker: LanguagePickerState,
    selectedCode: String,
    onSelect: (LanguageOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        LanguagePickerContent(
            picker = picker,
            selectedCode = selectedCode,
            onSelect = { option ->
                onSelect(option)
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) onDismiss()
                }
            },
        )
    }
}

@Composable
private fun LanguagePickerContent(
    picker: LanguagePickerState,
    selectedCode: String,
    onSelect: (LanguageOption) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val trimmed = query.trim()
    val matches = remember(picker.all, trimmed) {
        if (trimmed.isEmpty()) {
            picker.all
        } else {
            picker.all.filter {
                it.name.contains(trimmed, ignoreCase = true) || it.code.equals(trimmed, ignoreCase = true)
            }
        }
    }

    Column(Modifier.fillMaxHeight(0.9f)) {
        Text(
            text = stringResource(R.string.translate_language_picker_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.translate_language_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Rounded.Clear,
                            contentDescription = stringResource(R.string.translate_language_clear_search),
                        )
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (trimmed.isEmpty() && picker.recent.isNotEmpty()) {
                header(R.string.translate_language_recent)
                languageItems("recent", picker.recent, selectedCode, picker.downloaded, onSelect)
                header(R.string.translate_language_all)
            }
            if (matches.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.translate_language_no_match, trimmed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp),
                    )
                }
            } else {
                languageItems("all", matches, selectedCode, picker.downloaded, onSelect)
            }
        }
    }
}

private fun LazyListScope.header(title: Int) {
    item(key = "header_$title") {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp),
        )
    }
}

private fun LazyListScope.languageItems(
    section: String,
    languages: List<LanguageOption>,
    selectedCode: String,
    downloaded: Set<String>,
    onSelect: (LanguageOption) -> Unit,
) {
    items(languages, key = { "${section}_${it.code}" }) { option ->
        val selected = option.code == selectedCode
        LanguageRow(
            option = option,
            selected = selected,
            downloaded = option.code in downloaded,
            onClick = { onSelect(option) },
        )
    }
}

@Composable
private fun LanguageRow(
    option: LanguageOption,
    selected: Boolean,
    downloaded: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(option.name) },
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .semantics { this.selected = selected }
            .clickable(onClick = onClick),
        supportingContent = if (downloaded) {
            { Text(stringResource(R.string.translate_language_downloaded)) }
        } else {
            null
        },
        leadingContent = if (downloaded) {
            {
                Icon(
                    Icons.Rounded.DownloadDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
        trailingContent = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.translate_language_selected)) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    )
}

@Preview
@Composable
private fun LanguagePickerPreview() {
    val english = LanguageOption("en", "English")
    val french = LanguageOption("fr", "French")
    val yoruba = LanguageOption("yo", "Yoruba")
    TransVerseTheme {
        LanguagePickerContent(
            picker = LanguagePickerState(
                all = listOf(english, french, LanguageOption("de", "German"), yoruba),
                recent = listOf(french, english),
                downloaded = setOf("en", "fr"),
            ),
            selectedCode = "fr",
            onSelect = {},
        )
    }
}
