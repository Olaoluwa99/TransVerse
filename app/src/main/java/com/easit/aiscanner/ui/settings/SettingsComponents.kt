package com.easit.aiscanner.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// Small settings-list toolkit: a titled group of Material 3 Expressive "segmented" items.
// The group hands each item its position-aware shape (large outer corners on the first and
// last item, small inner corners) and items are separated by the segmented 2dp gap.

/** Collects the items of a [SettingsGroup]; each item receives its position-aware shapes. */
class SettingsGroupScope internal constructor() {
    internal val items = mutableListOf<@Composable (ListItemShapes) -> Unit>()

    /** Adds one item. Use [shapes] for the item's container (see the Settings* items). */
    fun item(content: @Composable (shapes: ListItemShapes) -> Unit) {
        items += content
    }
}

/**
 * A titled group of segmented settings items.
 *
 * ```
 * SettingsGroup(title = "Privacy") {
 *     item { shapes -> SettingsSwitchItem(shapes, ...) }
 *     item { shapes -> SettingsItem(shapes, ...) }
 * }
 * ```
 */
@Composable
fun SettingsGroup(
    title: String?,
    modifier: Modifier = Modifier,
    content: SettingsGroupScope.() -> Unit,
) {
    val items = SettingsGroupScope().apply(content).items
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
                    .semantics { heading() },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            items.forEachIndexed { index, item ->
                item(ListItemDefaults.segmentedShapes(index = index, count = items.size))
            }
        }
    }
}

/** Segmented item colours on the surfaceContainer tone; selection does not recolour the row. */
@Composable
fun settingsItemColors(): ListItemColors {
    val colors = MaterialTheme.colorScheme
    return ListItemDefaults.segmentedColors(
        containerColor = colors.surfaceContainer,
        disabledContainerColor = colors.surfaceContainer,
        selectedContainerColor = colors.surfaceContainer,
        selectedContentColor = colors.onSurface,
        selectedLeadingContentColor = colors.onSurfaceVariant,
        selectedTrailingContentColor = colors.onSurfaceVariant,
        selectedSupportingContentColor = colors.onSurfaceVariant,
    )
}

/**
 * A settings row. Clickable when [onClick] is set; [trailing] is usually a chevron or value.
 * Pass [contentColor] to tint the headline and icon (e.g. error colour for destructive rows).
 */
@Composable
fun SettingsItem(
    shapes: ListItemShapes,
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    contentColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val leading: (@Composable () -> Unit)? = if (icon != null) {
        {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor.takeOrElse { LocalContentColor.current },
            )
        }
    } else {
        null
    }
    val supportingContent: (@Composable () -> Unit)? =
        if (supporting != null) { { Text(supporting) } } else null
    val headlineContent: @Composable () -> Unit = { Text(headline, color = contentColor) }
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supportingContent,
            colors = settingsItemColors(),
            content = headlineContent,
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supportingContent,
            colors = settingsItemColors(),
            content = headlineContent,
        )
    }
}

/** A settings row with a [Switch]; the whole row toggles. */
@Composable
fun SettingsSwitchItem(
    shapes: ListItemShapes,
    headline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        // Keep the shape steady when switched on; the Switch already shows the state.
        shapes = shapes.copy(selectedShape = shapes.shape),
        modifier = modifier,
        enabled = enabled,
        leadingContent = if (icon != null) {
            { Icon(imageVector = icon, contentDescription = null) }
        } else {
            null
        },
        supportingContent = if (supporting != null) { { Text(supporting) } } else null,
        trailingContent = {
            // The row handles the toggle (and its semantics), so the Switch is display-only.
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        },
        colors = settingsItemColors(),
    ) {
        Text(headline)
    }
}

/**
 * A segmented container for richer content (sliders, button groups), matching the list items'
 * shape and colour.
 */
@Composable
fun SettingsCustomItem(
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = shapes.shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Headline row (icon, title, supporting text and an optional value) for a [SettingsCustomItem]. */
@Composable
fun SettingsItemHeader(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * A stepped integer slider. The thumb moves freely while dragging; [onValueCommitted] runs when
 * the user lets go, so preferences (and the app theme) are written once per change.
 *
 * @param valueLabel formats the value shown in the header (and announced while dragging)
 */
@Composable
fun SettingsSliderItem(
    shapes: ListItemShapes,
    headline: String,
    value: Int,
    range: IntRange,
    step: Int,
    onValueCommitted: (Int) -> Unit,
    valueLabel: @Composable (Int) -> String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    onValueChanging: ((Int) -> Unit)? = null,
    below: (@Composable ColumnScope.(current: Int) -> Unit)? = null,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val current = sliderValue.roundToInt()
    val stepCount = ((range.last - range.first) / step - 1).coerceAtLeast(0)

    SettingsCustomItem(shapes = shapes, modifier = modifier) {
        SettingsItemHeader(
            headline = headline,
            supporting = supporting,
            icon = icon,
            value = valueLabel(current),
        )
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                onValueChanging?.invoke(it.roundToInt())
            },
            onValueChangeFinished = {
                val committed = sliderValue.roundToInt()
                if (committed != value) onValueCommitted(committed)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = stepCount,
            modifier = Modifier.fillMaxWidth(),
        )
        below?.invoke(this, current)
    }
}

/**
 * A single-choice, connected button group (Material 3 Expressive): equally weighted
 * [ToggleButton]s with leading, middle and trailing connected shapes.
 *
 * @param label each option's name; shown as text when [showLabel], otherwise used as the
 *   button's accessibility label
 * @param icon optional icon content for each option (shown before the label, or alone)
 */
@Composable
fun <T> ConnectedChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    icon: (@Composable (T) -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            val optionLabel = label(option)
            val leadingIcon: (@Composable () -> Unit)? =
                if (icon != null && showLabel) { { icon(option) } } else null
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                shapes = shapes,
                icon = leadingIcon,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        role = Role.RadioButton
                        if (!showLabel) contentDescription = optionLabel
                    },
            ) {
                if (showLabel) {
                    Text(
                        text = optionLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (icon != null) {
                    icon(option)
                }
            }
        }
    }
}
