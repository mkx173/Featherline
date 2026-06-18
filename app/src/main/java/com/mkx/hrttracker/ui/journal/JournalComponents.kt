package com.mkx.hrttracker.ui.journal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.MilestoneUnit
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HazeAlertDialog
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtOutlinedButton
import com.mkx.hrttracker.ui.components.HrtPill
import com.mkx.hrttracker.ui.components.HrtPillSize
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.components.SegmentPosition
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
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

// Every pinned row renders through this one structure so that becoming (or ceasing to be)
// the hero never swaps the row out for a different composable. Only the hero in *view* mode
// gets the big celebratory block; every other state — the hero in edit mode and all normal
// rows in either mode — is the compact row. That single distinction is the AnimatedContent
// target, so:
//   • Toggling edit mode on the hero morphs big-view ⇄ compact. The leading icon and title
//     are shared elements (matched by key across the two states), so they physically slide
//     and resize instead of cross-fading; the rest fades and the height interpolates once
//     via SizeTransform, collapsing monotonically with no bulge.
//   • A reorder (always in edit mode) leaves the target on "compact" for every row, so the
//     compact row is never torn down — only isHero flips, and the tag inside it animates.
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PinnedRowContent(
    anchor: AnchorRowUiState,
    isHero: Boolean,
    isEditMode: Boolean,
    heroNextMilestone: NextMilestoneUiState?,
    today: LocalDate,
    dateFormatter: (LocalDate) -> String,
    onUnpin: () -> Unit,
    homeVisible: MutableTransitionState<Boolean>,
    dragHandle: Modifier = Modifier,
) {
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val sizeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<IntSize>()
    // The only state with a distinct layout is the hero while viewing; everything else is the
    // compact row. Keeping this off isEditMode (not isHero) means a reorder can't change it.
    val showHeroView = isHero && !isEditMode
    SharedTransitionLayout(modifier = Modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = showHeroView,
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = fadeIn(fadeSpec),
                    initialContentExit = fadeOut(fadeSpec),
                    sizeTransform = SizeTransform { _, _ -> sizeSpec },
                )
            },
            contentAlignment = Alignment.TopStart,
            label = "hero-morph",
        ) { heroView ->
            if (heroView) {
                HeroViewLayout(
                    anchor = anchor,
                    heroNextMilestone = heroNextMilestone,
                    today = today,
                    sharedScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent,
                )
            } else {
                PinnedCompactLayout(
                    anchor = anchor,
                    isHero = isHero,
                    isEditMode = isEditMode,
                    dateFormatter = dateFormatter,
                    onUnpin = onUnpin,
                    homeVisible = homeVisible,
                    dragHandle = dragHandle,
                    sharedScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent,
                )
            }
        }
    }
}

private const val HeroIconSharedKey = "hero-icon"
private const val HeroTitleSharedKey = "hero-title"

// Gap between a pinned row's leading chip and its text. Matches MedicationCard's 12dp so
// the hero edit row and the other pinned rows read as the same component.
private val PinnedRowLeadingGap = 12.dp

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
                HeroTitle(name = anchor.name, animatedVisibilityScope = animatedVisibilityScope)
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

// The compact pinned row, shared by every row (the hero in edit mode and all normal rows).
// The leading icon and title carry the hero's shared-element keys unconditionally: for the
// hero they morph against HeroViewLayout, for a normal row there's no counterpart so they
// just render in place. The trailing controls reveal with [isEditMode]; the "· Home" tag
// reveals with [homeVisible] (driven by "is this the hero"), whose state is hoisted to
// PinnedTray so a reorder rebuilding this row doesn't reset the tag's transition.
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PinnedCompactLayout(
    anchor: AnchorRowUiState,
    isHero: Boolean,
    isEditMode: Boolean,
    dateFormatter: (LocalDate) -> String,
    onUnpin: () -> Unit,
    homeVisible: MutableTransitionState<Boolean>,
    sharedScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    dragHandle: Modifier = Modifier,
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
            Spacer(modifier = Modifier.width(PinnedRowLeadingGap))
            Column(modifier = Modifier.weight(1f)) {
                HeroTitle(name = anchor.name, animatedVisibilityScope = animatedVisibilityScope)
                val baseSummary = stringResource(
                    R.string.journal_hero_edit_summary,
                    dateFormatter(anchor.date),
                    anchor.dayCountLabel(),
                )
                Row {
                    Text(
                        text = baseSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline().cjkTextOffset(baseSummary),
                    )
                    HomeSuffix(visibleState = homeVisible, modifier = Modifier.alignByBaseline())
                }
            }
            // The trailing cluster slides in/out with the edit toggle. Two paths, one spec:
            //   • Hero: this compact layout IS the AnimatedContent edit target, recreated on
            //     every toggle, so an inner AnimatedVisibility would be seeded already-visible
            //     and skip its enter slide (it would only fade in on the morph). Instead tie
            //     the cluster to the morph's scope via animateEnterExit, so it slides as the
            //     morph brings the layout in/out. The hero reaches compact only in edit mode.
            //   • Every other row: this layout persists across the toggle, so AnimatedVisibility
            //     on isEditMode runs the slide. (Their AnimatedContent never transitions, so
            //     animateEnterExit would never fire.)
            // Both paths use editTrailingEnter/Exit, keeping the hero in sync with the rest.
            if (isHero) {
                EditTrailingCluster(
                    name = anchor.name,
                    onUnpin = onUnpin,
                    dragHandle = dragHandle,
                    modifier = with(animatedVisibilityScope) {
                        Modifier.animateEnterExit(
                            enter = editTrailingEnter(),
                            exit = editTrailingExit(),
                        )
                    },
                )
            } else {
                AnimatedVisibility(
                    visible = isEditMode,
                    enter = editTrailingEnter(),
                    exit = editTrailingExit(),
                ) {
                    EditTrailingCluster(
                        name = anchor.name,
                        onUnpin = onUnpin,
                        dragHandle = dragHandle,
                    )
                }
            }
        }
    }
}

// The hero title is its own shared element, so it slides between the view header
// and the compact edit row. The Home indicator is no longer adjacent to it: in view
// mode it's a chip in the row below, in edit mode it's appended to the summary line.
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.HeroTitle(
    name: String,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    Text(
        text = name,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .sharedElement(
                rememberSharedContentState(key = HeroTitleSharedKey),
                animatedVisibilityScope = animatedVisibilityScope,
            )
            .cjkTextOffset(name),
    )
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
            // Filled (edit) matches AnchorIconChip/MedicationCard at 36dp; the bare view
            // glyph stays 18dp and the shared element grows the container across the morph.
            .size(if (filled) 36.dp else 18.dp)
            .background(
                color = if (filled) colorScheme.primaryContainer else Color.Transparent,
                shape = MaterialTheme.shapes.small,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(anchorIconRes(anchor.icon)),
            contentDescription = null,
            modifier = Modifier.size(if (filled) 20.dp else 18.dp),
            tint = if (filled) colorScheme.onPrimaryContainer else colorScheme.primary,
        )
    }
}

// Enter/exit for the edit-mode trailing controls: the cluster slides in from the end
// (right) and fades. Shared by the hero (via animateEnterExit) and every other pinned row
// (via AnimatedVisibility) so all rows' controls animate with the same spec.
private fun editTrailingEnter() = slideInHorizontally { width -> width } + fadeIn()

private fun editTrailingExit() = slideOutHorizontally { width -> width } + fadeOut()

// Enter/exit for the "· Home" tag. Anchored at the start (the end of the date string), it
// expands rightward out of the date line on enter and collapses back leftward into it on
// exit, so the tag reads as sliding into/out of the date string rather than just fading.
private fun homeSuffixEnter() = expandHorizontally(expandFrom = Alignment.Start) + fadeIn()

private fun homeSuffixExit() = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()

// The "· Home" tag appended to a pinned row's date line, marking the row shown on the Home
// screen. [visibleState] is hoisted to PinnedTray (keyed by anchor id) rather than remembered
// here, because ReorderableColumn rebuilds this row's subtree on every reorder; resuming the
// transition from the hoisted state is what lets the tag slide in/out as the hero changes,
// the same way the trailing controls slide when edit mode toggles.
@Composable
private fun HomeSuffix(
    visibleState: MutableTransitionState<Boolean>,
    modifier: Modifier = Modifier,
) {
    val homeSuffix = stringResource(
        R.string.journal_hero_edit_home_suffix,
        stringResource(R.string.journal_home_tag),
    )
    // [modifier] carries the row's alignByBaseline so the tag sits on the date line's
    // baseline; expandHorizontally only animates width, so the baseline stays put. The text
    // keeps cjkTextOffset to match the base summary's CJK nudge.
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = homeSuffixEnter(),
        exit = homeSuffixExit(),
    ) {
        Text(
            text = homeSuffix,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.cjkTextOffset(homeSuffix),
        )
    }
}

// Edit-mode trailing cluster: unpin first, drag grip last. Shared by the hero row and
// the other pinned rows. [dragHandle] turns the grip into an immediate (no long-press)
// drag handle; it's empty when the row isn't draggable.
@Composable
private fun EditTrailingCluster(
    name: String,
    onUnpin: () -> Unit,
    dragHandle: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        UnpinButton(name = name, onUnpin = onUnpin)
        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_xsmall)))
        DragGrip(modifier = dragHandle)
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
        HomeTag()
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
    // The big count mirrors MainE2HeroCard's hero value: displayLarge/Medium in the
    // primary color with a titleMedium supporting unit, aligned to the value's
    // baseline (alignByBaseline) rather than nudged with bottom padding.
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (isToday) {
            Text(
                text = stringResource(R.string.journal_today),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFeatureSettings = "tnum",
                ),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alignByBaseline(),
            )
        } else {
            if (hero.isFuture) {
                Text(
                    text = stringResource(R.string.journal_in_prefix),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Text(
                text = days.toString(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFeatureSettings = "tnum",
                ),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = pluralStringResource(R.plurals.journal_day_unit, days),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
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
    // Matches MedicationCard's leading icon (36dp container, ~20dp glyph) so journal
    // rows read as the same component. SimpleHomeCard overrides this with a larger size.
    size: Dp = 36.dp,
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
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.cjkTextOffset(anchor.name),
            )
            nameGlyph?.invoke()
        }
        Text(
            text = supportingLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.cjkTextOffset(supportingLabel),
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun PinnedTray(
    anchors: List<AnchorRowUiState>,
    isEditMode: Boolean,
    onReorder: (List<String>) -> Unit,
    onSetPinned: (String, Boolean) -> Unit,
    heroNextMilestone: NextMilestoneUiState? = null,
    today: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier,
) {
    var lastNonEmptyAnchors by remember { mutableStateOf(emptyList<AnchorRowUiState>()) }
    val rowAnchors = if (anchors.isEmpty()) lastNonEmptyAnchors else anchors
    SideEffect {
        if (anchors.isNotEmpty()) {
            lastNonEmptyAnchors = anchors
        }
    }
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val sizeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<IntSize>()

    AnimatedContent(
        targetState = anchors.isEmpty(),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
        transitionSpec = {
            ContentTransform(
                targetContentEnter = EnterTransition.None,
                initialContentExit = ExitTransition.None,
                sizeTransform = SizeTransform { _, _ -> sizeSpec },
            )
        },
        contentAlignment = Alignment.TopStart,
        label = "pinned-tray-empty-morph",
    ) { isEmpty ->
        val contentFadeModifier = Modifier.animateEnterExit(
            enter = fadeIn(fadeSpec),
            exit = fadeOut(fadeSpec),
        )
        if (isEmpty) {
            PinnedHomeSlotEmpty(
                modifier = Modifier.fillMaxWidth(),
                contentModifier = contentFadeModifier,
            )
        } else {
            PinnedTrayRows(
                anchors = rowAnchors,
                isEditMode = isEditMode,
                onReorder = onReorder,
                onSetPinned = onSetPinned,
                heroNextMilestone = heroNextMilestone,
                today = today,
                contentModifier = contentFadeModifier,
            )
        }
    }
}

@Composable
private fun PinnedTrayRows(
    anchors: List<AnchorRowUiState>,
    isEditMode: Boolean,
    onReorder: (List<String>) -> Unit,
    onSetPinned: (String, Boolean) -> Unit,
    heroNextMilestone: NextMilestoneUiState?,
    today: LocalDate,
    contentModifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val gap = dimensionResource(R.dimen.list_segment_gap)
    val rowVisibilityStates = remember { mutableMapOf<String, MutableTransitionState<Boolean>>() }
    val rowAnchors = remember { mutableMapOf<String, AnchorRowUiState>() }
    val rowOrder = remember { mutableListOf<String>() }
    val lastPositions = remember { mutableMapOf<String, SegmentPosition>() }
    val activeIds = anchors.map { it.id }
    val activeIdSet = activeIds.toSet()
    val initialRows = rowVisibilityStates.isEmpty()

    anchors.forEachIndexed { index, anchor ->
        rowAnchors[anchor.id] = anchor
        rowVisibilityStates
            .getOrPut(anchor.id) { MutableTransitionState(initialRows) }
            .targetState = true
        lastPositions[anchor.id] = SegmentPosition(index, anchors.size)
    }
    rowVisibilityStates.forEach { (id, state) ->
        if (id !in activeIdSet) {
            state.targetState = false
        }
    }
    val exitingIds = rowOrder.filter { id ->
        id !in activeIdSet && rowVisibilityStates[id]?.isPinnedTrayRowPresent() == true
    }
    val nextOrder = activeIds.toMutableList()
    exitingIds.forEach { id ->
        nextOrder.add(rowOrder.indexOf(id).coerceIn(0, nextOrder.size), id)
    }
    rowOrder.clear()
    rowOrder.addAll(nextOrder)
    val displayAnchors = rowOrder.mapNotNull { id ->
        val state = rowVisibilityStates[id]
        rowAnchors[id]?.takeIf { state?.isPinnedTrayRowPresent() == true }
    }
    val displayIds = displayAnchors.map { it.id }.toSet()
    rowVisibilityStates.keys.retainAll(displayIds)
    rowAnchors.keys.retainAll(displayIds)
    rowOrder.retainAll(displayIds)
    val hasTransitioningRows = displayIds.any { id ->
        rowVisibilityStates[id]?.isIdle == false
    }
    val reorderOwnsGaps = isEditMode && !hasTransitioningRows

    // ReorderableColumn lays its rows out positionally (no per-item key), so a reorder
    // rebuilds a row's whole subtree from scratch — any AnimatedVisibility inside the row
    // would lose its transition and snap. So the "· Home" tag's visibility lives here, above
    // the column, keyed by anchor id: the state survives the rebuild, and the new row's
    // AnimatedVisibility resumes it (current=true, target=false ⇒ animates out, and vice
    // versa). targetState tracks "is this the hero (top) row"; a fresh id seeds to its
    // current position so the initial hero shows without animating in.
    val homeVisibleStates = remember { mutableMapOf<String, MutableTransitionState<Boolean>>() }
    homeVisibleStates.keys.retainAll(displayIds)
    displayAnchors.forEach { anchor ->
        val isHome = activeIds.indexOf(anchor.id) == 0
        val wasHome = lastPositions[anchor.id]?.index == 0
        homeVisibleStates.getOrPut(anchor.id) { MutableTransitionState(isHome || wasHome) }
            .targetState = isHome
    }
    // ReorderableColumn is used in both modes so the hero stays the first row and
    // morphs in place; drag is only enabled in edit mode.
    ReorderableColumn(
        modifier = Modifier.fillMaxWidth(),
        list = displayAnchors,
        onSettle = { fromIndex, toIndex ->
            val displayOrder = displayAnchors.map { it.id }
            onReorder(
                reorderedIds(displayOrder, fromIndex, toIndex)
                    .filter { it in activeIdSet }
            )
        },
        onMove = {
            ViewCompat.performHapticFeedback(
                view,
                HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK,
            )
        },
        verticalArrangement = if (reorderOwnsGaps) {
            Arrangement.spacedBy(gap)
        } else {
            Arrangement.Top
        },
    ) { index, anchor, isDragging ->
        key(anchor.id) {
            val activePosition = activeIds.indexOf(anchor.id)
                .takeIf { it >= 0 }
                ?.let { SegmentPosition(it, anchors.size) }
            val position = activePosition ?: lastPositions.getValue(anchor.id)
            lastPositions[anchor.id] = position
            val hasLeadingGap = !reorderOwnsGaps &&
                rowOrder.take(index).any { it in activeIdSet }
            val isActive = anchor.id in activeIdSet
            val interactionSource = remember { MutableInteractionSource() }
            val gripInteractionSource = remember { MutableInteractionSource() }
            val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "drag")
            val moveActions = pinnedRowAccessibilityActions(
                anchor = anchor,
                index = position.index,
                anchors = anchors,
                onReorder = onReorder,
            )
            // Two ways to start a drag in edit mode. Long-press anywhere on the row
            // (so the page can still scroll, and the unpin tap never starts a drag),
            // or touch the trailing grip, which begins dragging immediately with no
            // long-press. Both handles drive the same item's drag.
            val rowDragModifier = if (isEditMode && isActive) {
                Modifier.longPressDraggableHandle(interactionSource = interactionSource)
            } else {
                Modifier
            }
            val gripDragHandle = if (isEditMode && isActive) {
                Modifier.draggableHandle(interactionSource = gripInteractionSource)
            } else {
                Modifier
            }
            AnimatedVisibility(
                visibleState = rowVisibilityStates.getValue(anchor.id),
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column {
                    if (hasLeadingGap) {
                        Spacer(modifier = Modifier.height(gap))
                    }
                    PinnedRow(
                        anchor = anchor,
                        index = position.index,
                        count = position.count,
                        isHero = position.index == 0,
                        isEditMode = isEditMode,
                        isDragging = isDragging,
                        heroNextMilestone = heroNextMilestone,
                        today = today,
                        onUnpin = {
                            if (isActive) {
                                onSetPinned(anchor.id, false)
                            }
                        },
                        homeVisible = homeVisibleStates.getValue(anchor.id),
                        dragHandle = gripDragHandle,
                        contentModifier = contentModifier,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(rowDragModifier)
                            .shadow(elevation, shape = MaterialTheme.shapes.large)
                            .semantics {
                                if (isEditMode && isActive) {
                                    customActions = moveActions
                                }
                            },
                    )
                }
            }
        }
    }
}

private fun MutableTransitionState<Boolean>.isPinnedTrayRowPresent(): Boolean =
    currentState || targetState || !isIdle

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
    homeVisible: MutableTransitionState<Boolean>,
    dragHandle: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
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
        // In edit mode each pinned row detaches into its own card with all four
        // corners rounded; the per-corner springs animate the morph as the user
        // enters/exits edit mode. At rest the rows read as one grouped segment.
        fullyRounded = isEditMode,
        modifier = modifier,
    ) {
        Box(modifier = contentModifier) {
            PinnedRowContent(
                anchor = anchor,
                isHero = isHero,
                isEditMode = isEditMode,
                heroNextMilestone = heroNextMilestone,
                today = today,
                dateFormatter = dateFormatter,
                onUnpin = onUnpin,
                homeVisible = homeVisible,
                dragHandle = dragHandle,
            )
        }
    }
}

@Composable
private fun DragGrip(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_drag_indicator),
        contentDescription = null,
        modifier = modifier.size(20.dp),
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

@Composable
private fun PinnedHomeSlotEmpty(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
) {
    SupportMessageListItem(
        text = stringResource(R.string.journal_home_slot_empty),
        painter = painterResource(R.drawable.ic_info),
        modifier = modifier,
        contentModifier = contentModifier,
    )
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

// ---- Previews ----

private val previewToday = LocalDate.of(2026, 6, 17)

private fun previewAnchors() = listOf(
    AnchorRowUiState(
        id = "estradiol", name = "On estradiol", icon = AnchorIcon.MEDICATION,
        palette = MedicationGroupColorKey.ROSE, date = LocalDate.of(2024, 4, 1),
        dayMagnitude = 807, isFuture = false,
    ),
    AnchorRowUiState(
        id = "injection", name = "First injection", icon = AnchorIcon.VACCINES,
        palette = MedicationGroupColorKey.INDIGO, date = LocalDate.of(2026, 3, 1),
        dayMagnitude = 108, isFuture = false,
    ),
    AnchorRowUiState(
        id = "surgery", name = "Surgery", icon = AnchorIcon.FLAG,
        palette = MedicationGroupColorKey.SAGE, date = LocalDate.of(2026, 9, 15),
        dayMagnitude = 90, isFuture = true,
    ),
)

private fun previewNotes() = listOf(
    Note(id = "n1", date = LocalDate.of(2026, 6, 17), text = "Felt steadier today. Sleep is finally settling."),
    Note(id = "n2", date = LocalDate.of(2026, 6, 14), text = "Bloodwork came back in range."),
    Note(id = "n3", date = LocalDate.of(2026, 6, 10), text = "Started the new dose this morning."),
)

@Composable
private fun JournalComponentPreview(content: @Composable () -> Unit) {
    HrtTrackerTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap)),
            ) {
                content()
            }
        }
    }
}

@Preview(name = "MilestonesStackCard", showBackground = true, widthDp = 420)
@Composable
private fun MilestonesStackCardPreview() {
    JournalComponentPreview {
        MilestonesStackCard(today = previewToday, anchors = previewAnchors(), onClick = {})
    }
}

@Preview(name = "SimpleHomeCard", showBackground = true, widthDp = 420)
@Composable
private fun SimpleHomeCardPreview() {
    JournalComponentPreview {
        SimpleHomeCard(anchor = previewAnchors().first(), today = previewToday, onClick = {})
    }
}

@Preview(name = "TodayComposer", showBackground = true, widthDp = 420)
@Composable
private fun TodayComposerPreview() {
    JournalComponentPreview {
        TodayComposer(
            today = previewToday,
            note = Note(id = "today", date = previewToday, text = "Quiet day. Grateful for the small wins."),
            onSave = {},
            onDelete = {},
        )
    }
}

@Preview(name = "NotesTimeline", showBackground = true, widthDp = 420)
@Composable
private fun NotesTimelinePreview() {
    JournalComponentPreview {
        NotesTimeline(notes = previewNotes(), today = previewToday, onSave = { _, _ -> }, onDelete = {})
    }
}

@Preview(name = "Journal empty states", showBackground = true, widthDp = 420)
@Composable
private fun JournalEmptyStatesPreview() {
    JournalComponentPreview {
        EmptyMilestonesCard(onAddDate = {})
        EmptyPinnedMilestonesCard(onClick = {})
        EmptyRecentNotesCard()
        EmptyAllNotesCard()
    }
}

private val previewNextMilestone =
    NextMilestoneUiState(remainingDays = 193, value = 1000, unit = MilestoneUnit.DAYS)

// The hero layouts only render inside a SharedTransitionLayout/AnimatedContent, so the
// preview supplies both scopes via a still AnimatedVisibility (visible from the start, so
// content shows without animating).
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HeroLayoutScope(
    content: @Composable (
        sharedScope: SharedTransitionScope,
        animatedVisibilityScope: AnimatedVisibilityScope,
    ) -> Unit,
) {
    SharedTransitionLayout {
        AnimatedVisibility(visible = true) {
            content(this@SharedTransitionLayout, this@AnimatedVisibility)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(name = "HeroViewLayout", showBackground = true, widthDp = 420)
@Composable
private fun HeroViewLayoutPreview() {
    JournalComponentPreview {
        HeroLayoutScope { sharedScope, animatedVisibilityScope ->
            HeroViewLayout(
                anchor = previewAnchors().first(),
                heroNextMilestone = previewNextMilestone,
                today = previewToday,
                sharedScope = sharedScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(name = "PinnedCompactLayout (hero)", showBackground = true, widthDp = 420)
@Composable
private fun PinnedCompactLayoutPreview() {
    JournalComponentPreview {
        HeroLayoutScope { sharedScope, animatedVisibilityScope ->
            PinnedCompactLayout(
                anchor = previewAnchors().first(),
                isHero = true,
                isEditMode = true,
                dateFormatter = { it.toString() },
                onUnpin = {},
                homeVisible = remember { MutableTransitionState(true) },
                sharedScope = sharedScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    }
}

@Preview(name = "PinnedRow", showBackground = true, widthDp = 420)
@Composable
private fun PinnedRowPreview() {
    val anchors = previewAnchors()
    JournalComponentPreview {
        PinnedRow(
            anchor = anchors[0], index = 0, count = 2,
            isHero = true, isEditMode = false, isDragging = false,
            heroNextMilestone = previewNextMilestone, today = previewToday, onUnpin = {},
            homeVisible = remember { MutableTransitionState(true) },
        )
        PinnedRow(
            anchor = anchors[1], index = 1, count = 2,
            isHero = false, isEditMode = false, isDragging = false,
            heroNextMilestone = null, today = previewToday, onUnpin = {},
            homeVisible = remember { MutableTransitionState(false) },
        )
    }
}

@Preview(name = "PinnedRow (edit)", showBackground = true, widthDp = 420)
@Composable
private fun PinnedRowEditPreview() {
    val anchors = previewAnchors()
    JournalComponentPreview {
        PinnedRow(
            anchor = anchors[0], index = 0, count = 2,
            isHero = true, isEditMode = true, isDragging = false,
            heroNextMilestone = previewNextMilestone, today = previewToday, onUnpin = {},
            homeVisible = remember { MutableTransitionState(true) },
        )
        PinnedRow(
            anchor = anchors[1], index = 1, count = 2,
            isHero = false, isEditMode = true, isDragging = false,
            heroNextMilestone = null, today = previewToday, onUnpin = {},
            homeVisible = remember { MutableTransitionState(false) },
        )
    }
}
