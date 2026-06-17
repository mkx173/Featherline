package com.mkx.hrttracker.ui.journal

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.MilestoneUnit
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HazeAlertDialog
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtOutlinedButton
import com.mkx.hrttracker.ui.components.HrtPill
import com.mkx.hrttracker.ui.components.HrtPillSize
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import sh.calvin.reorderable.ReorderableColumn
import java.time.LocalDate
import java.time.format.TextStyle

private const val TodayComposerTextFieldTestTag = "today-composer-text-field"
private const val NoteTimelineTextFieldTestTagPrefix = "note-timeline-text-field-"
internal const val SimpleHomeCardTestTag = "simple-home-card"

@Composable
fun MilestonesStackCard(
    today: LocalDate,
    anchors: List<AnchorRowUiState>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }

    EditorSegmentedListItem(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_schedule),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_small)))
                Text(
                    text = stringResource(R.string.journal_since_you_started),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            anchors.forEach { anchor ->
                MilestonesStackAnchorRow(
                    anchor = anchor,
                    dateLabel = dateFormatter(anchor.date),
                )
            }
        }
    }
}

@Composable
fun SimpleHomeCard(
    anchor: AnchorRowUiState,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SimpleHomeCardTestTag)
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(R.dimen.padding_medium)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnchorIconChip(anchor = anchor)
            Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_small)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anchor.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        R.string.journal_since_date,
                        dateFormatter(anchor.date),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_small)))
            Text(
                text = anchor.dayCountLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_xsmall)))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MilestonesStackAnchorRow(
    anchor: AnchorRowUiState,
    dateLabel: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = rememberMedicationGroupColorScheme(colorKey = anchor.palette)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = MaterialTheme.shapes.small,
            color = colorScheme.primaryContainer,
            contentColor = colorScheme.onPrimaryContainer,
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(anchorIconRes(anchor.icon)),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_small)))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = anchor.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_small)))
        Text(
            text = anchor.dayCountLabel(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun EmptyMilestonesCard(
    onAddDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EditorSegmentedListItem(modifier = modifier.fillMaxWidth()) {
        Column {
            Text(
                text = stringResource(R.string.journal_no_dates),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
            HrtButton(
                text = stringResource(R.string.journal_add_date),
                onClick = onAddDate,
            )
        }
    }
}

@Composable
fun EmptyPinnedMilestonesCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EditorSegmentedListItem(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Text(
            text = stringResource(R.string.journal_nothing_pinned),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
fun MilestonesHero(
    hero: AnchorRowUiState?,
    nextMilestone: NextMilestoneUiState?,
    today: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier,
) {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }

    EditorSegmentedListItem(modifier = modifier.fillMaxWidth()) {
        if (hero == null) {
            Text(
                text = stringResource(R.string.journal_nothing_pinned),
                style = MaterialTheme.typography.titleMedium,
            )
            return@EditorSegmentedListItem
        }
        Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))) {
                AnchorIconChip(anchor = hero, size = 30.dp)
                Text(text = hero.name, style = MaterialTheme.typography.titleMedium)
            }
            HeroCount(hero = hero)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall))) {
                HrtPill(
                    label = stringResource(R.string.journal_since_date, dateFormatter(hero.date)),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    size = HrtPillSize.Small,
                    icon = { Icon(painterResource(R.drawable.ic_event), null, iconModifier) },
                )
                if (!hero.isFuture && nextMilestone != null) {
                    HrtPill(
                        label = nextMilestone.label(),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        size = HrtPillSize.Small,
                        icon = { Icon(painterResource(R.drawable.ic_flag), null, iconModifier) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCount(hero: AnchorRowUiState) {
    val days = hero.dayMagnitude.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val isToday = hero.dayMagnitude == 0L && !hero.isFuture
    Row(verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (isToday) {
            Text(
                text = stringResource(R.string.journal_today),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            if (hero.isFuture) {
                Text(
                    text = stringResource(R.string.journal_in_prefix),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = days.toString(),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (-0.5).sp,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = pluralStringResource(R.plurals.journal_day_unit, days),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun NextMilestoneUiState.label(): String {
    val value = value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val label = when (unit) {
        MilestoneUnit.DAYS -> pluralStringResource(
            R.plurals.journal_milestone_label_days,
            value,
            value,
        )

        MilestoneUnit.MONTHS -> pluralStringResource(
            R.plurals.journal_milestone_label_months,
            value,
            value,
        )
    }
    if (remainingDays == 0L) {
        return stringResource(R.string.journal_milestone_today, label)
    }
    val days = remainingDays.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return pluralStringResource(
        R.plurals.journal_next_milestone_days_to_label,
        days,
        days,
        label,
    )
}

@Composable
private fun AnchorIconChip(
    anchor: AnchorRowUiState,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val colorScheme = rememberMedicationGroupColorScheme(colorKey = anchor.palette)
    Surface(
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.small,
        color = colorScheme.primaryContainer,
        contentColor = colorScheme.onPrimaryContainer,
    ) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(anchorIconRes(anchor.icon)),
                contentDescription = null,
                modifier = Modifier.size(size * 0.56f),
            )
        }
    }
}

@Composable
private fun AnchorSummaryText(
    anchor: AnchorRowUiState,
    today: LocalDate,
    showDayCountInline: Boolean,
    modifier: Modifier = Modifier,
    nameGlyph: (@Composable () -> Unit)? = null,
) {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }
    val dateLabel = dateFormatter(anchor.date)
    val supportingLabel = if (showDayCountInline) {
        "$dateLabel · ${anchor.dayCountLabel()}"
    } else {
        dateLabel
    }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall)),
        ) {
            Text(
                text = anchor.name,
                style = MaterialTheme.typography.titleMedium,
            )
            nameGlyph?.invoke()
        }
        Text(
            text = supportingLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Single source of reorder math for both the drag `onSettle` and the a11y
// "move" actions: removes the item at [fromIndex] and reinserts it at [toIndex].
// Out-of-range indices leave the list unchanged so callers can't corrupt order.
internal fun reorderedIds(ids: List<String>, fromIndex: Int, toIndex: Int): List<String> {
    if (fromIndex !in ids.indices || toIndex !in ids.indices) return ids
    return ids.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}

@Composable
private fun PinnedTrayRowView(
    anchor: AnchorRowUiState,
    index: Int,
    count: Int,
    today: LocalDate,
) {
    EditorSegmentedListItem(
        index = index,
        count = count,
        modifier = Modifier.fillMaxWidth(),
        leadingContent = { AnchorIconChip(anchor = anchor) },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
    ) {
        AnchorSummaryText(
            anchor = anchor,
            today = today,
            showDayCountInline = true,
        )
    }
}

@Composable
fun PinnedTray(
    anchors: List<AnchorRowUiState>,
    isEditMode: Boolean,
    onReorder: (List<String>) -> Unit,
    onSetPinned: (String, Boolean) -> Unit,
    today: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier,
) {
    if (anchors.isEmpty()) {
        if (isEditMode) {
            EditorSegmentedListItem(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.journal_nothing_pinned),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    if (!isEditMode) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap)),
        ) {
            anchors.forEachIndexed { index, anchor ->
                PinnedTrayRowView(anchor, index, anchors.size, today)
            }
        }
        return
    }
    PinnedTrayEdit(anchors, onReorder, onSetPinned, today, modifier)
}

@Composable
private fun PinnedTrayEdit(
    anchors: List<AnchorRowUiState>,
    onReorder: (List<String>) -> Unit,
    onSetPinned: (String, Boolean) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    // ReorderableColumn (non-lazy, sh.calvin.reorderable 2.5.1) positions each
    // item itself: the content receiver is a ReorderableScope exposing
    // draggableHandle directly — there is no ReorderableItem wrapper (that exists
    // only for the lazy variants). onSettle/move both route through reorderedIds.
    ReorderableColumn(
        modifier = modifier.fillMaxWidth(),
        list = anchors,
        onSettle = { fromIndex, toIndex ->
            onReorder(reorderedIds(anchors.map { it.id }, fromIndex, toIndex))
        },
        onMove = {
            ViewCompat.performHapticFeedback(
                view,
                HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK,
            )
        },
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap)),
    ) { index, anchor, isDragging ->
        key(anchor.id) {
            if (index == 0) {
                HrtPill(
                    label = stringResource(R.string.journal_hero_badge),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    size = HrtPillSize.XSmall,
                )
            } else if (index == 1) {
                Text(
                    text = stringResource(R.string.journal_pinned_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val interactionSource = remember { MutableInteractionSource() }
            val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "drag")
            val moveActions = pinnedRowAccessibilityActions(
                anchor = anchor,
                index = index,
                anchors = anchors,
                onReorder = onReorder,
            )
            EditorSegmentedListItem(
                index = index,
                count = anchors.size,
                containerColor = if (isDragging) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation)
                    .semantics { customActions = moveActions },
                leadingContent = {
                    IconButton(
                        onClick = {},
                        modifier = Modifier
                            .size(36.dp)
                            .draggableHandle(interactionSource = interactionSource),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_drag_indicator),
                            contentDescription = stringResource(
                                R.string.journal_reorder_anchor,
                                anchor.name,
                            ),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailingContent = {
                    IconButton(
                        onClick = { onSetPinned(anchor.id, false) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(
                                R.string.journal_unpin_anchor,
                                anchor.name,
                            ),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnchorIconChip(anchor = anchor, size = 32.dp)
                    Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_small)))
                    AnchorSummaryText(
                        anchor = anchor,
                        today = today,
                        showDayCountInline = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun pinnedRowAccessibilityActions(
    anchor: AnchorRowUiState,
    index: Int,
    anchors: List<AnchorRowUiState>,
    onReorder: (List<String>) -> Unit,
): List<CustomAccessibilityAction> {
    val ids = anchors.map { it.id }
    val moveUp = stringResource(R.string.journal_move_anchor_up, anchor.name)
    val moveDown = stringResource(R.string.journal_move_anchor_down, anchor.name)
    val moveTop = stringResource(R.string.journal_move_anchor_to_top, anchor.name)
    return buildList {
        if (index > 0) {
            add(CustomAccessibilityAction(moveTop) { onReorder(reorderedIds(ids, index, 0)); true })
            add(CustomAccessibilityAction(moveUp) { onReorder(reorderedIds(ids, index, index - 1)); true })
        }
        if (index < anchors.lastIndex) {
            add(CustomAccessibilityAction(moveDown) { onReorder(reorderedIds(ids, index, index + 1)); true })
        }
    }
}

@Composable
fun MilestonesTimeline(
    nodes: List<TimelineNodeUiState>,
    todayDividerIndex: Int,
    isEditMode: Boolean,
    onSetPinned: (String, Boolean) -> Unit,
    onUpdateDate: (AnchorRowUiState) -> Unit,
    today: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier,
) {
    if (nodes.isEmpty()) {
        EditorSegmentedListItem(modifier = modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.journal_no_dates),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val dividerIndex = todayDividerIndex.coerceIn(0, nodes.size)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap)),
    ) {
        nodes.forEachIndexed { index, node ->
            if (index == dividerIndex) {
                TodayDivider(today = today)
            }
            TimelineAnchorRow(
                node = node,
                index = index,
                count = nodes.size,
                isLast = index == nodes.lastIndex,
                isToday = node.anchor.date == today,
                isEditMode = isEditMode,
                today = today,
                onSetPinned = onSetPinned,
                onUpdateDate = onUpdateDate,
            )
        }
        if (dividerIndex == nodes.size) {
            TodayDivider(today = today, isLast = true)
        }
    }
}

@Composable
private fun TimelineAnchorRow(
    node: TimelineNodeUiState,
    index: Int,
    count: Int,
    isLast: Boolean,
    isToday: Boolean,
    isEditMode: Boolean,
    today: LocalDate,
    onSetPinned: (String, Boolean) -> Unit,
    onUpdateDate: (AnchorRowUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val anchor = node.anchor

    // IntrinsicSize.Min lets the rail's fillMaxHeight match the entry's height,
    // so the connector spans exactly from this dot toward the next row's dot.
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        TimelineRail(anchor = anchor, isLast = isLast, isToday = isToday)
        EditorSegmentedListItem(
            index = index,
            count = count,
            modifier = Modifier.fillMaxWidth(),
            onClick = if (isEditMode) {
                { onUpdateDate(anchor) }
            } else {
                null
            },
            leadingContent = { AnchorIconChip(anchor = anchor) },
            trailingContent = {
                if (isEditMode) {
                    PinToggle(
                        checked = node.isPinned,
                        onCheckedChange = { onSetPinned(anchor.id, it) },
                    )
                } else {
                    Text(
                        text = anchor.dayCountLabel(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        color = if (anchor.isFuture) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            },
        ) {
            AnchorSummaryText(
                anchor = anchor,
                today = today,
                showDayCountInline = isEditMode,
                nameGlyph = if (!isEditMode && node.isPinned) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.ic_push_pin),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun TimelineRail(
    anchor: AnchorRowUiState,
    isLast: Boolean,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = if (isToday) {
        MaterialTheme.colorScheme.tertiary
    } else {
        rememberMedicationGroupColorScheme(colorKey = anchor.palette).primary
    }
    Column(
        modifier = modifier.width(34.dp).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .size(16.dp)
                .border(2.dp, accent, CircleShape)
                .background(
                    color = if (anchor.isFuture && !isToday) {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    } else {
                        accent
                    },
                    shape = CircleShape,
                ),
        )
        if (!isLast) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun TodayDivider(
    today: LocalDate,
    isLast: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) { dateLabelFormatter(appLocale, today) }
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier.width(34.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                    .border(4.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f), CircleShape),
            )
            if (!isLast) {
                Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
            }
        }
        Surface(
            modifier = Modifier.weight(1f).padding(vertical = 5.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_schedule), null, Modifier.size(18.dp))
                    }
                }
                Column {
                    Text(
                        text = stringResource(R.string.journal_today),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = dateFormatter(today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PinToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "pinToggleBg",
    )
    Surface(
        color = container,
        shape = CircleShape,
        modifier = modifier.size(36.dp),
    ) {
        IconToggleButton(checked = checked, onCheckedChange = onCheckedChange) {
            Icon(
                painter = painterResource(R.drawable.ic_push_pin),
                contentDescription = stringResource(R.string.journal_pin_to_home_content_description),
                modifier = Modifier.size(20.dp),
                tint = if (checked) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
fun TodayComposer(
    today: LocalDate,
    note: Note?,
    onSave: (String) -> Unit,
    onDelete: () -> Unit = { },
    modifier: Modifier = Modifier,
) {
    var isEditing by rememberSaveable(today.toString(), note?.id, note?.text) {
        mutableStateOf(false)
    }
    var draftText by rememberSaveable(today.toString(), note?.id, note?.text) {
        mutableStateOf(note?.text.orEmpty())
    }
    var isDeleteConfirmationVisible by rememberSaveable(today.toString(), note?.id) {
        mutableStateOf(false)
    }
    val currentText = note?.text.orEmpty()

    if (isEditing) {
        TodayComposerEditor(
            today = today,
            text = draftText,
            onTextChange = { draftText = it },
            onCancel = {
                draftText = currentText
                isEditing = false
            },
            onSave = {
                val text = draftText.trim()
                if (text.isNotEmpty()) {
                    onSave(text)
                    isEditing = false
                }
            },
            onDelete = note?.let {
                {
                    isDeleteConfirmationVisible = true
                }
            },
            modifier = modifier,
        )
        if (isDeleteConfirmationVisible) {
            NoteDeleteConfirmationDialog(
                onDismissRequest = { isDeleteConfirmationVisible = false },
                onConfirm = {
                    isDeleteConfirmationVisible = false
                    isEditing = false
                    onDelete()
                },
            )
        }
        return
    }

    PreferenceSegmentedListItem(
        modifier = modifier,
        title = stringResource(R.string.journal_today),
        supportingText = note?.text ?: stringResource(R.string.journal_write_about_today),
        onClick = {
            draftText = currentText
            isEditing = true
        },
    )
}

@Composable
private fun TodayComposerEditor(
    today: LocalDate,
    text: String,
    onTextChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val canSave = text.isNotBlank()
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }

    EditorSegmentedListItem(modifier = modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.journal_today),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = dateFormatter(today),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TodayComposerTextFieldTestTag),
                placeholder = {
                    Text(text = stringResource(R.string.journal_write_about_today))
                },
                minLines = 3,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onDelete != null) {
                    HrtOutlinedButton(
                        text = stringResource(R.string.journal_delete_note),
                        onClick = onDelete,
                        compact = true,
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                Row {
                    HrtOutlinedButton(
                        text = stringResource(R.string.cancel),
                        onClick = onCancel,
                        compact = true,
                    )
                    Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_small)))
                    HrtButton(
                        text = stringResource(R.string.save),
                        onClick = onSave,
                        enabled = canSave,
                        compact = true,
                    )
                }
            }
        }
    }
}

@Composable
fun NotesTimeline(
    notes: List<Note>,
    today: LocalDate,
    onSave: (LocalDate, String) -> Unit,
    onDelete: (LocalDate) -> Unit = { },
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (notes.isEmpty()) {
            EmptyRecentNotesCard()
        } else {
            notes.forEach { note ->
                NoteTimelineRow(
                    note = note,
                    onSave = onSave,
                    onDelete = onDelete,
                    today = today,
                )
            }
        }
    }
}

@Composable
fun NoteTimelineRow(
    note: Note,
    onSave: (LocalDate, String) -> Unit,
    onDelete: (LocalDate) -> Unit = { },
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    var isEditing by rememberSaveable(note.id, note.date.toString(), note.text) {
        mutableStateOf(false)
    }
    var draftText by rememberSaveable(note.id, note.date.toString(), note.text) {
        mutableStateOf(note.text)
    }
    var isDeleteConfirmationVisible by rememberSaveable(note.id, note.date.toString()) {
        mutableStateOf(false)
    }
    val dateLabel = noteTimelineDateLabel(note.date, today)

    if (isEditing) {
        NoteTimelineRowEditor(
            note = note,
            dateLabel = dateLabel,
            text = draftText,
            onTextChange = { draftText = it },
            onCancel = {
                draftText = note.text
                isEditing = false
            },
            onSave = {
                val text = draftText.trim()
                if (text.isNotEmpty()) {
                    onSave(note.date, text)
                    isEditing = false
                }
            },
            onDelete = {
                isDeleteConfirmationVisible = true
            },
            modifier = modifier,
        )
        if (isDeleteConfirmationVisible) {
            NoteDeleteConfirmationDialog(
                onDismissRequest = { isDeleteConfirmationVisible = false },
                onConfirm = {
                    isDeleteConfirmationVisible = false
                    isEditing = false
                    onDelete(note.date)
                },
            )
        }
        return
    }

    PreferenceSegmentedListItem(
        modifier = modifier,
        title = dateLabel,
        supportingText = note.text,
        onClick = {
            draftText = note.text
            isEditing = true
        },
        leadingContent = { NoteTimelineMarker() },
        titleTextStyle = MaterialTheme.typography.labelMedium,
        supportingTextStyle = MaterialTheme.typography.bodyMedium,
        titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NoteTimelineRowEditor(
    note: Note,
    dateLabel: String,
    text: String,
    onTextChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trimmedText = text.trim()
    val canSave = trimmedText.isNotEmpty() && trimmedText != note.text.trim()

    EditorSegmentedListItem(modifier = modifier.fillMaxWidth()) {
        Row {
            NoteTimelineMarker()
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
            ) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("$NoteTimelineTextFieldTestTagPrefix${note.id}"),
                    minLines = 3,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HrtOutlinedButton(
                        text = stringResource(R.string.journal_delete_note),
                        onClick = onDelete,
                        compact = true,
                    )
                    Row {
                        HrtOutlinedButton(
                            text = stringResource(R.string.cancel),
                            onClick = onCancel,
                            compact = true,
                        )
                        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_small)))
                        HrtButton(
                            text = stringResource(R.string.save),
                            onClick = onSave,
                            enabled = canSave,
                            compact = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteDeleteConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    HazeAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.journal_delete_note_title)) },
        text = { Text(text = stringResource(R.string.journal_delete_note_confirmation)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.delete_entries_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun NoteTimelineMarker(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(16.dp)
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
    }
}

@Composable
private fun noteTimelineDateLabel(
    date: LocalDate,
    today: LocalDate,
): String {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }
    val todayLabel = stringResource(R.string.journal_today)
    val yesterdayLabel = stringResource(R.string.journal_yesterday)
    val label = when (date) {
        today -> todayLabel
        today.minusDays(1) -> yesterdayLabel
        else -> stringResource(
            R.string.journal_note_date_weekday_pattern,
            date.dayOfWeek.getDisplayName(TextStyle.FULL, appLocale),
            dateFormatter(date),
        )
    }

    return label.uppercase(appLocale)
}

@Composable
fun EmptyRecentNotesCard(
    modifier: Modifier = Modifier,
) {
    PreferenceSegmentedListItem(
        modifier = modifier,
        title = stringResource(R.string.journal_notes_window_meta),
        supportingText = stringResource(R.string.journal_write_about_today),
    )
}

@Composable
fun EmptyAllNotesCard(
    modifier: Modifier = Modifier,
) {
    PreferenceSegmentedListItem(
        modifier = modifier,
        title = stringResource(R.string.journal_no_notes),
        supportingText = stringResource(R.string.journal_all_notes_empty),
    )
}

@Composable
private fun AnchorRowUiState.dayCountLabel(): String {
    if (dayMagnitude == 0L && !isFuture) return stringResource(R.string.journal_today)
    val days = dayMagnitude.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return if (isFuture) {
        pluralStringResource(R.plurals.journal_milestone_days_future, days, days)
    } else {
        pluralStringResource(R.plurals.journal_milestone_days_past, days, days)
    }
}
