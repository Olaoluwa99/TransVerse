package com.easit.aiscanner.ui.history

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FilterListOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.easit.aiscanner.R
import com.easit.aiscanner.model.Scan
import com.easit.aiscanner.ui.theme.TransVerseTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Saved translations: search, filter, favourite, delete with undo, open. */
@Composable
fun HistoryScreen(
    onOpenScan: (scanId: String) -> Unit,
    onStartTranslating: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.history_item_deleted)
    val undoLabel = stringResource(R.string.history_undo)
    val currentDeletedMessage by rememberUpdatedState(deletedMessage)
    val currentUndoLabel by rememberUpdatedState(undoLabel)

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is HistoryEvent.Deleted -> launch {
                    // A newer deletion replaces the snackbar, which ends the older undo window.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    var undone = false
                    try {
                        undone = snackbarHostState.showSnackbar(
                            message = currentDeletedMessage,
                            actionLabel = currentUndoLabel,
                            duration = SnackbarDuration.Long,
                        ) == SnackbarResult.ActionPerformed
                    } finally {
                        // Leaving the screen also ends the undo window, so files go then too.
                        if (undone) viewModel.restore(event.scan)
                        else viewModel.finalizeDeletion(event.scan)
                    }
                }
            }
        }
    }

    HistoryContent(
        state = state,
        query = viewModel.query,
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onClearFilters = viewModel::clearFilters,
        onOpenScan = { scan -> onOpenScan(scan.id) },
        onToggleFavorite = viewModel::toggleFavorite,
        onDelete = viewModel::delete,
        onStartTranslating = onStartTranslating,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** Stateless History screen. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryContent(
    state: HistoryUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onFilterChange: (HistoryFilter) -> Unit,
    onClearFilters: () -> Unit,
    onOpenScan: (Scan) -> Unit,
    onToggleFavorite: (Scan) -> Unit,
    onDelete: (Scan) -> Unit,
    onStartTranslating: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // Hoisted so the scroll position survives the brief loading state on return to this tab.
    val listState = rememberLazyListState()
    val showCount = !state.isLoading && state.totalCount > 0

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.nav_history)) },
                subtitle = if (showCount) {
                    {
                        Text(
                            pluralStringResource(
                                R.plurals.history_saved_count,
                                state.totalCount,
                                state.totalCount,
                            )
                        )
                    }
                } else {
                    null
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
        when {
            state.isLoading -> Box(contentModifier, contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }

            state.hasNoHistory -> HistoryEmptyState(
                icon = Icons.Rounded.History,
                containerShape = MaterialShapes.SoftBurst.toShape(),
                title = stringResource(R.string.history_empty_title),
                body = stringResource(R.string.history_empty_body),
                modifier = contentModifier,
            ) {
                val height = ButtonDefaults.MediumContainerHeight
                Button(
                    onClick = onStartTranslating,
                    shapes = ButtonDefaults.shapesFor(height),
                    modifier = Modifier.heightIn(min = height),
                    contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
                ) {
                    Icon(
                        Icons.Rounded.Translate,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                    )
                    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
                    Text(
                        stringResource(R.string.history_empty_action),
                        style = ButtonDefaults.textStyleFor(height),
                    )
                }
            }

            else -> Column(contentModifier) {
                HistorySearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                HistoryFilterRow(
                    selected = state.filter,
                    onSelect = onFilterChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.hasNoResults) {
                    HistoryEmptyState(
                        icon = Icons.Rounded.SearchOff,
                        containerShape = MaterialShapes.Cookie9Sided.toShape(),
                        title = stringResource(R.string.history_no_results_title),
                        body = stringResource(R.string.history_no_results_body),
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding(),
                    ) {
                        if (state.isFiltering) {
                            FilledTonalButton(onClick = onClearFilters) {
                                Icon(
                                    Icons.Rounded.FilterListOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.history_clear_filters))
                            }
                        }
                    }
                } else {
                    HistoryList(
                        state = state,
                        onOpenScan = onOpenScan,
                        onToggleFavorite = onToggleFavorite,
                        onDelete = onDelete,
                        listState = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding(),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.history_search_placeholder)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.history_search_clear),
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerHigh,
            unfocusedContainerColor = colors.surfaceContainerHigh,
            disabledContainerColor = colors.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun HistoryFilterRow(
    selected: HistoryFilter,
    onSelect: (HistoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.labelRes)) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

private val HistoryFilter.labelRes: Int
    get() = when (this) {
        HistoryFilter.ALL -> R.string.history_filter_all
        HistoryFilter.FAVOURITES -> R.string.history_filter_favourites
        HistoryFilter.TEXT -> R.string.history_filter_text
        HistoryFilter.IMAGE -> R.string.history_filter_image
        HistoryFilter.VOICE -> R.string.history_filter_voice
    }

@Composable
private fun HistoryList(
    state: HistoryUiState,
    onOpenScan: (Scan) -> Unit,
    onToggleFavorite: (Scan) -> Unit,
    onDelete: (Scan) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    // A new filter shows a new list: start it from the top (but keep the position on return).
    var lastFilter by rememberSaveable { mutableStateOf(state.filter) }
    LaunchedEffect(state.filter) {
        if (state.filter != lastFilter) {
            lastFilter = state.filter
            listState.scrollToItem(0)
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
    ) {
        state.sections.forEach { section ->
            item(key = section.key, contentType = "header") {
                HistorySectionTitle(section.header, Modifier.animateItem())
            }
            itemsIndexed(
                items = section.items,
                key = { _, item -> item.scan.id },
                contentType = { _, _ -> "scan" },
            ) { index, item ->
                HistoryRow(
                    item = item,
                    index = index,
                    count = section.items.size,
                    showDate = section.header == HistorySectionHeader.Favourites,
                    onOpen = { onOpenScan(item.scan) },
                    onToggleFavorite = { onToggleFavorite(item.scan) },
                    onDelete = { onDelete(item.scan) },
                    modifier = Modifier
                        .animateItem()
                        .padding(top = if (index > 0) ListItemDefaults.SegmentedGap else 0.dp),
                )
            }
        }
    }
}

@Composable
private fun HistorySectionTitle(header: HistorySectionHeader, modifier: Modifier = Modifier) {
    val label = when (header) {
        HistorySectionHeader.Favourites -> stringResource(R.string.history_section_favourites)
        HistorySectionHeader.Today -> stringResource(R.string.history_section_today)
        HistorySectionHeader.Yesterday -> stringResource(R.string.history_section_yesterday)
        is HistorySectionHeader.Day -> rememberDayLabel(header.date)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun rememberDayLabel(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        val skeleton = if (date.year == LocalDate.now().year) "EEEEMMMMd" else "yMMMMd"
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        DateTimeFormatter.ofPattern(pattern, locale).format(date)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryRow(
    item: HistoryItem,
    index: Int,
    count: Int,
    showDate: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scan = item.scan
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    // Plain remember (not rememberSaveable): a row restored by Undo must come back settled.
    val dismissState = remember(scan.id) {
        SwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            positionalThreshold = { totalDistance -> totalDistance * DISMISS_THRESHOLD },
        )
    }
    val currentOnDelete by rememberUpdatedState(onDelete)
    // SwipeToDismissBox may report the same dismissal more than once; delete only once.
    val onDismiss: (SwipeToDismissBoxValue) -> Unit = remember(dismissState) {
        var requested = false
        val callback: (SwipeToDismissBoxValue) -> Unit = {
            if (!requested) {
                requested = true
                currentOnDelete()
            }
        }
        callback
    }
    val deleteLabel = stringResource(R.string.history_delete)

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = onDismiss,
        backgroundContent = {
            DeleteBackground(
                visible = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart,
                armed = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart,
                shape = shapes.shape,
            )
        },
    ) {
        SegmentedListItem(
            onClick = onOpen,
            shapes = shapes,
            // Swipe has an accessible equivalent: a "Delete" custom action.
            modifier = Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction(deleteLabel) {
                        onDismiss(SwipeToDismissBoxValue.EndToStart)
                        true
                    }
                )
            },
            leadingContent = { HistoryLeading(item) },
            trailingContent = {
                IconToggleButton(checked = scan.isFavorite, onCheckedChange = { onToggleFavorite() }) {
                    Icon(
                        imageVector = if (scan.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = stringResource(
                            if (scan.isFavorite) R.string.history_favourite_remove
                            else R.string.history_favourite_add
                        ),
                    )
                }
            },
            supportingContent = { HistorySupporting(scan, showDate) },
        ) {
            Text(
                text = scan.sourceSnippet(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeleteBackground(visible: Boolean, armed: Boolean, shape: Shape) {
    if (!visible) return
    val scale by animateFloatAsState(
        targetValue = if (armed) 1.2f else 0.9f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "deleteIconScale",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            Icons.Rounded.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.scale(scale),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryLeading(item: HistoryItem) {
    val kind = item.scan.kind
    val description = stringResource(kind.descriptionRes)
    val thumbnail = item.thumbnail
    if (thumbnail != null) {
        AsyncImage(
            model = thumbnail,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        return
    }
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (kind) {
        ScanKind.TEXT -> colors.primaryContainer to colors.onPrimaryContainer
        ScanKind.IMAGE -> colors.tertiaryContainer to colors.onTertiaryContainer
        ScanKind.VOICE -> colors.secondaryContainer to colors.onSecondaryContainer
        ScanKind.BARCODE, ScanKind.OTHER -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
    // Each kind gets its own shape as well as colour, so it doesn't rely on colour alone.
    val shape = when (kind) {
        ScanKind.TEXT -> MaterialShapes.Cookie9Sided
        ScanKind.IMAGE -> MaterialShapes.Sunny
        ScanKind.VOICE -> MaterialShapes.Clover4Leaf
        ScanKind.BARCODE, ScanKind.OTHER -> MaterialShapes.Cookie4Sided
    }.toShape()
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(kind.icon, contentDescription = description, tint = content, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun HistorySupporting(scan: Scan, showDate: Boolean) {
    val context = LocalContext.current
    val unknown = stringResource(R.string.history_unknown_language)
    val languages = stringResource(
        R.string.history_language_pair,
        scan.sourceLanguage.ifBlank { unknown },
        scan.translatedLanguage.ifBlank { unknown },
    )
    val time = remember(scan.timeMilliseconds, showDate) {
        val flags = if (showDate) {
            DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
        } else {
            DateUtils.FORMAT_SHOW_TIME
        }
        DateUtils.formatDateTime(context, scan.timeMilliseconds, flags)
    }
    val translation = scan.translatedText.trim()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (translation.isNotEmpty() && translation != scan.sourceSnippet()) {
            Text(
                text = translation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "$languages · $time",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Scan.sourceSnippet(): String = transcriptText.trim().ifEmpty { barcodeScan.trim() }

private val ScanKind.icon: ImageVector
    get() = when (this) {
        ScanKind.TEXT -> Icons.Rounded.TextFields
        ScanKind.IMAGE -> Icons.Rounded.Image
        ScanKind.VOICE -> Icons.Rounded.Mic
        ScanKind.BARCODE -> Icons.Rounded.QrCode2
        ScanKind.OTHER -> Icons.Rounded.Translate
    }

private val ScanKind.descriptionRes: Int
    get() = when (this) {
        ScanKind.TEXT -> R.string.history_type_text
        ScanKind.IMAGE -> R.string.history_type_image
        ScanKind.VOICE -> R.string.history_type_voice
        ScanKind.BARCODE -> R.string.history_type_barcode
        ScanKind.OTHER -> R.string.history_type_other
    }

@Composable
private fun HistoryEmptyState(
    icon: ImageVector,
    containerShape: Shape,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(containerShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        action()
    }
}

private const val DISMISS_THRESHOLD = 0.4f

// region Previews

private fun previewScan(
    id: String,
    type: String,
    source: String,
    translated: String,
    from: String,
    to: String,
    daysAgo: Long,
    favourite: Boolean = false,
): Scan = Scan(
    id = id,
    dateTime = "",
    timeMilliseconds = System.currentTimeMillis() - daysAgo * DateUtils.DAY_IN_MILLIS,
    scanType = type,
    transcriptText = source,
    translatedText = translated,
    sourceLanguage = from,
    translatedLanguage = to,
    entities = emptyList(),
    smartReplies = emptyList(),
    imageUrl = "",
    audioUrl = "",
    barcodeScan = "",
    isFavorite = favourite,
)

private fun previewState(filter: HistoryFilter = HistoryFilter.ALL): HistoryUiState {
    val scans = listOf(
        previewScan("1", SCAN_TYPE_TEXT, "¿Dónde está la estación de tren?", "Where is the train station?", "Spanish", "English", 0, favourite = true),
        previewScan("2", SCAN_TYPE_IMAGE, "Sortie de secours", "Emergency exit", "French", "English", 0),
        previewScan("3", SCAN_TYPE_AUDIO, "Guten Morgen, wie geht es Ihnen heute?", "Good morning, how are you today?", "German", "English", 0),
        previewScan("4", SCAN_TYPE_TEXT, "Obrigado pela ajuda", "Thank you for the help", "Portuguese", "English", 1),
        previewScan("5", SCAN_TYPE_BARCODE, "https://example.com/menu", "https://example.com/menu", "English", "English", 9),
    )
    val items = scans.filter { filter.matches(it) }.map { HistoryItem(it) }
    return HistoryUiState(
        isLoading = false,
        totalCount = scans.size,
        filter = filter,
        isFiltering = filter != HistoryFilter.ALL,
        sections = groupIntoSections(items, filter, LocalDate.now(), ZoneId.systemDefault()),
    )
}

@Composable
private fun PreviewHistory(state: HistoryUiState, query: String = "") {
    TransVerseTheme {
        HistoryContent(
            state = state,
            query = query,
            onQueryChange = {},
            onFilterChange = {},
            onClearFilters = {},
            onOpenScan = {},
            onToggleFavorite = {},
            onDelete = {},
            onStartTranslating = {},
        )
    }
}

@Preview(name = "History list", showBackground = true)
@Composable
private fun HistoryListPreview() = PreviewHistory(previewState())

@Preview(name = "History list, dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryListDarkPreview() = PreviewHistory(previewState())

@Preview(name = "Voice filter", showBackground = true)
@Composable
private fun HistoryVoiceFilterPreview() = PreviewHistory(previewState(HistoryFilter.VOICE))

@Preview(name = "No history", showBackground = true)
@Composable
private fun HistoryEmptyPreview() = PreviewHistory(HistoryUiState(isLoading = false))

@Preview(name = "No results", showBackground = true)
@Composable
private fun HistoryNoResultsPreview() = PreviewHistory(
    HistoryUiState(isLoading = false, totalCount = 5, isFiltering = true),
    query = "zebra",
)

// endregion
