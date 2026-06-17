package com.mkx.hrttracker.ui.journal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.unit.IntSize
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

// Shared between TimelineRail and TodayDivider so the today node's dot lands on the
// same anchor rail as every other row (same width, same dot top offset).
private val TimelineRailWidth = 34.dp
private val TimelineRailDotTopOffset = 14.dp

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
            AnchorIconChip(anchor = anchor, size = 40.dp)
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

// The hero morphs between two layouts inside one AnimatedContent. The leading icon and the
// title are shared elements (matched by key across the two states), so they physically
// slide — and resize — between their positions instead of cross-fading: the icon travels
// from the view header to the centre of the compact edit row, growing from a bare glyph
// into a filled chip as sharedBounds cross-fades that difference. The non-shared parts (the
// big numeral + chips, the date line, the edit controls) fade, and the card height
// interpolates once via SizeTransform, so it collapses monotonically with no bulge.
//   • view: header (bare glyph + title) with the celebratory block at the card's edge.
//   • edit: a standard compact pinned row — chip + title/date + unpin/grip — with the icon
//     and controls centred against the whole two-line card, like the other pinned rows.
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroPinnedContent(
    anchor: AnchorRowUiState,
    isEditMode: Boolean,
    heroNextMilestone: NextMilestoneUiState?,
    today: LocalDate,
    dateFormatter: (LocalDate) -> String,
    onUnpin: () -> Unit,
) {
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val sizeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<IntSize>()
    SharedTransitionLayout(modifier = Modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = isEditMode,
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = fadeIn(fadeSpec),
                    initialContentExit = fadeOut(fadeSpec),
                    sizeTransform = SizeTransform { _, _ -> sizeSpec },
                )
            },
            contentAlignment = Alignment.TopStart,
            label = "hero-morph",
        ) { editing ->
            if (editing) {
                HeroEditLayout(
                    anchor = anchor,
                    dateFormatter = dateFormatter,
                    onUnpin = onUnpin,
                    sharedScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent,
                )
            } else {
                HeroViewLayout(
                    anchor = anchor,
                    heroNextMilestone = heroNextMilestone,
                    today = today,
                    sharedScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent,
                )
            }
        }
    }
}

private const val HeroIconSharedKey = "hero-icon"
private const val HeroTitleSharedKey = "hero-title"
private const val HomePillSharedKey = "home-pill"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HeroViewLayout(
    anchor: AnchorRowUiState,
    heroNextMilestone: NextMilestoneUiState?,
    today: LocalDate,
    sharedScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    with(sharedScope) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MorphingLeadingIcon(
                    anchor = anchor,
                    filled = false,
                    modifier = Modifier.sharedElement(
                        rememberSharedContentState(key = HeroIconSharedKey),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
                )
                Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_small)))
                HeroTitleAndHome(name = anchor.name, animatedVisibilityScope = animatedVisibilityScope)
            }
            Column(
                modifier = Modifier.padding(top = dimensionResource(R.dimen.padding_small)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
            ) {
                HeroCount(hero = anchor)
                HeroChips(hero = anchor, nextMilestone = heroNextMilestone, today = today)
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HeroEditLayout(
    anchor: AnchorRowUiState,
    dateFormatter: (LocalDate) -> String,
    onUnpin: () -> Unit,
    sharedScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    with(sharedScope) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MorphingLeadingIcon(
                anchor = anchor,
                filled = true,
                modifier = Modifier.sharedElement(
                    rememberSharedContentState(key = HeroIconSharedKey),
                    animatedVisibilityScope = animatedVisibilityScope,
                ),
            )
            Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_small)))
            Column(modifier = Modifier.weight(1f)) {
                HeroTitleAndHome(name = anchor.name, animatedVisibilityScope = animatedVisibilityScope)
                Text(
                    text = "${dateFormatter(anchor.date)} · ${anchor.dayCountLabel()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // The trailing controls have no view counterpart, so they aren't a shared
            // element — animateEnterExit slides them in from the end (right) of the row.
            EditTrailingCluster(
                name = anchor.name,
                onUnpin = onUnpin,
                modifier = with(animatedVisibilityScope) {
                    Modifier.animateEnterExit(
                        enter = slideInHorizontally { width -> width } + fadeIn(),
                        exit = slideOutHorizontally { width -> width } + fadeOut(),
                    )
                },
            )
        }
    }
}

// The title and the Home pill are each their own shared element, so they slide
// independently between the view header and the compact edit row.
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.HeroTitleAndHome(
    name: String,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_xsmall)),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.sharedElement(
                rememberSharedContentState(key = HeroTitleSharedKey),
                animatedVisibilityScope = animatedVisibilityScope,
            ),
        )
        HomeTag(
            modifier = Modifier.sharedElement(
                rememberSharedContentState(key = HomePillSharedKey),
                animatedVisibilityScope = animatedVisibilityScope,
            ),
        )
    }
}

// The hero's leading icon: a bare tinted glyph in view (no surface), a filled chip in edit
// (like the other pinned rows). The caller marks it as a shared element, so it slides and
// resizes between the two states as a single continuous element rather than cross-fading.
@Composable
private fun MorphingLeadingIcon(
    anchor: AnchorRowUiState,
    filled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = rememberMedicationGroupColorScheme(colorKey = anchor.palette)
    Box(
        modifier = modifier
            .size(if (filled) 32.dp else 24.dp)
            .background(
                color = if (filled) colorScheme.primaryContainer else Color.Transparent,
                shape = MaterialTheme.shapes.small,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(anchorIconRes(anchor.icon)),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (filled) colorScheme.onPrimaryContainer else colorScheme.primary,
        )
    }
}

// Edit-mode trailing cluster: unpin first, drag grip last. Shared by the hero row and
// the other pinned rows.
@Composable
private fun EditTrailingCluster(name: String, onUnpin: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        UnpinButton(name = name, onUnpin = onUnpin)
        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_xsmall)))
        DragGrip()
    }
}

// Status tag on the first pinned row only: "this is the one shown on your Home
// screen." Not a button — the hero is retargeted by dragging a row to the top.
@Composable
private fun HomeTag(modifier: Modifier = Modifier) {
    HrtPill(
        label = stringResource(R.string.journal_home_tag),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        size = HrtPillSize.XSmall,
        modifier = modifier,
        icon = { Icon(painterResource(R.drawable.ic_home), null, iconModifier) },
    )
}

@Composable
private fun HeroChips(
    hero: AnchorRowUiState,
    nextMilestone: NextMilestoneUiState?,
    today: LocalDate,
) {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) { dateLabelFormatter(appLocale, today) }
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
fun PinnedTray(
    anchors: List<AnchorRowUiState>,
    isEditMode: Boolean,
    onReorder: (List<String>) -> Unit,
    onSetPinned: (String, Boolean) -> Unit,
    heroNextMilestone: NextMilestoneUiState? = null,
    today: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier,
) {
    if (anchors.isEmpty()) {
        PinnedHomeSlotEmpty(modifier = modifier.fillMaxWidth())
        return
    }
    val view = LocalView.current
    var isDraggingAny by remember { mutableStateOf(false) }
    // The "Home slot" floor is painted behind the rows. The opaque first row covers
    // it at rest; while a drag is in progress the lifted row's reserved slot turns
    // transparent and the floor shows through at the top, making "top = Home" legible
    // during the very gesture that changes it. ReorderableColumn is used in both modes
    // so the hero stays the first row and morphs in place; drag is only enabled in edit.
    Box(modifier = modifier.fillMaxWidth()) {
        PinnedHomeSlotFloor(
            visible = isDraggingAny,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        )
        ReorderableColumn(
            modifier = Modifier.fillMaxWidth(),
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
                val interactionSource = remember { MutableInteractionSource() }
                val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "drag")
                val moveActions = pinnedRowAccessibilityActions(
                    anchor = anchor,
                    index = index,
                    anchors = anchors,
                    onReorder = onReorder,
                )
                // Whole row is the drag handle (long-press) in edit mode, so the page
                // can still scroll; the unpin button's tap never starts a drag.
                val dragModifier = if (isEditMode) {
                    Modifier.longPressDraggableHandle(
                        interactionSource = interactionSource,
                        onDragStarted = { isDraggingAny = true },
                        onDragStopped = { isDraggingAny = false },
                    )
                } else {
                    Modifier
                }
                PinnedRow(
                    anchor = anchor,
                    index = index,
                    count = anchors.size,
                    isHero = index == 0,
                    isEditMode = isEditMode,
                    isDragging = isDragging,
                    heroNextMilestone = heroNextMilestone,
                    today = today,
                    onUnpin = { onSetPinned(anchor.id, false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(dragModifier)
                        .shadow(elevation, shape = MaterialTheme.shapes.large)
                        .semantics {
                            if (isEditMode) {
                                customActions = moveActions
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun PinnedRow(
    anchor: AnchorRowUiState,
    index: Int,
    count: Int,
    isHero: Boolean,
    isEditMode: Boolean,
    isDragging: Boolean,
    heroNextMilestone: NextMilestoneUiState?,
    today: LocalDate,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isDragging) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) { dateLabelFormatter(appLocale, today) }
    // Everything lives in the single content slot (no leading/trailing ListItem slots)
    // so nothing re-centers against a mid-animation height. The hero keeps Top
    // alignment across the morph so its icon never jumps from center to top.
    EditorSegmentedListItem(
        index = index,
        count = count,
        containerColor = containerColor,
        modifier = modifier,
    ) {
        if (isHero) {
            HeroPinnedContent(
                anchor = anchor,
                isEditMode = isEditMode,
                heroNextMilestone = heroNextMilestone,
                today = today,
                dateFormatter = dateFormatter,
                onUnpin = onUnpin,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnchorIconChip(anchor = anchor)
                Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_small)))
                AnchorSummaryText(
                    anchor = anchor,
                    today = today,
                    showDayCountInline = true,
                    modifier = Modifier.weight(1f),
                )
                // Edit-only trailing cluster: expands in from the end so the summary
                // reflows continuously rather than snapping. Non-hero rows keep a fixed
                // height, so the cluster has no row resize to track and never drifts.
                AnimatedVisibility(
                    visible = isEditMode,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    enter = fadeIn() + expandHorizontally(),
                    exit = shrinkHorizontally() + fadeOut(),
                ) {
                    EditTrailingCluster(name = anchor.name, onUnpin = onUnpin)
                }
            }
        }
    }
}

@Composable
private fun DragGrip() {
    Icon(
        painter = painterResource(R.drawable.ic_drag_indicator),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun UnpinButton(name: String, onUnpin: () -> Unit) {
    IconButton(
        onClick = onUnpin,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.journal_unpin_anchor, name),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Empty state: the Home slot is fully shown with teaching copy — this is where a
// first-time user learns that pinning surfaces a milestone on Home.
@Composable
private fun PinnedHomeSlotEmpty(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.large,
            )
            .padding(dimensionResource(R.dimen.padding_medium)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_home),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.journal_home_slot_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PinnedHomeSlotFloor(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "homeSlotFloor")
    Box(
        modifier = modifier
            .height(72.dp)
            .alpha(alpha)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.large,
            )
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                shape = MaterialTheme.shapes.large,
            )
            .padding(horizontal = dimensionResource(R.dimen.padding_medium)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_home),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.journal_home_slot_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
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
                            painter = painterResource(R.drawable.ic_keep),
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
        modifier = modifier.width(TimelineRailWidth).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(TimelineRailDotTopOffset))
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
            modifier = Modifier.width(TimelineRailWidth).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(TimelineRailDotTopOffset))
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
        animationSpec = tween(durationMillis = 200),
        label = "pinToggleBg",
    )
    Surface(
        color = container,
        shape = CircleShape,
        modifier = modifier.size(36.dp),
    ) {
        IconToggleButton(checked = checked, onCheckedChange = onCheckedChange) {
            Icon(
                painter = painterResource(
                    if (checked) R.drawable.ic_keep else R.drawable.ic_keep_alt,
                ),
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
