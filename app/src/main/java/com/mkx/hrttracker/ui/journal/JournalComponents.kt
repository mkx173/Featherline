package com.mkx.hrttracker.ui.journal

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.util.lerp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.journal.MilestoneUnit
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.model.journal.PrideFlag
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.segmentedListItemShape
import com.mkx.hrttracker.ui.components.FlipSlot
import com.mkx.hrttracker.ui.components.HazeAlertDialog
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.HrtPill
import com.mkx.hrttracker.ui.components.HrtPillSize
import com.mkx.hrttracker.ui.components.LocalSegmentPosition
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.components.SegmentPosition
import com.mkx.hrttracker.ui.components.StockStatusIndicator
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.isHazeBlurSupported
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.isAppInDarkTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.medicationGroupScheduleDateFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import sh.calvin.reorderable.ReorderableColumn
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TodayComposerTextFieldTestTag = "today-composer-text-field"
private const val NoteTimelineTextFieldTestTagPrefix = "note-timeline-text-field-"
private const val NoteTimelineRowTestTagPrefix = "note-timeline-row-"
private const val NoteTimelineDotTestTagPrefix = "note-timeline-dot-"
private const val NoteTimelineLineBottomTestTagPrefix = "note-timeline-line-bottom-"
internal const val SimpleHomeCardTestTag = "simple-home-card"

// Shared by the timeline rail. The inter-card gap lives inside each row's
// content cell (not as Column spacing) so the rail column is gapless and the
// connector line is continuous from the first node to the last.
private val TimelineNodeSize = 12.dp      // milestone dot diameter
private val TimelineNodeGap = 6.dp        // gap between a node and the line ends abutting it
private val TimelineRailStroke = 2.dp     // connector line thickness
// The dot's left edge starts the row (no leading inset), so this inset is both the
// gap from the card edge to the dot (the Column's horizontal padding) and the gap
// from the dot to the subcard — a symmetric 16dp gutter on either side of the dot.
private val TimelineDotInset = 16.dp
private val TimelineRailWidth = TimelineNodeSize + TimelineDotInset
private val TimelineCardGap = 6.dp        // vertical gap between adjacent subcards
// The today dot wears a static, soft "now" halo — a translucent disc behind it. Its
// dot carves a wider rail gap so the connector line ends the same TimelineNodeGap
// distance from the halo's outer rim as it does from a plain dot's edge, keeping the
// line-to-rim spacing consistent across the rail (the cell adds nodeGap to centreX,
// i.e. to the dot radius, so back out that radius from the rim distance). The halo
// marks the present in place of the Today divider, which is suppressed when a node
// already falls on today.
private val TodayHaloRadius = 12.dp
private val TimelineTodayNodeGap = TodayHaloRadius + TimelineNodeGap - TimelineNodeSize / 2
private const val TodayHaloAlpha = 0.10f

// Set to false to drop the Today marker entirely (variant A: one uninterrupted
// line, no "now" divider). The timeline's first/last rail math reads this, so the
// continuous line stays correct either way.
private const val ShowTodayMarker = true

// The pinned-dates card on the journal page: a rounded surface with a "Pinned" header
// (enter-screen chevron) over a segmented stack of the subsequent pinned anchors. The hero
// is rendered separately above this; the empty states use their own card.
@Composable
fun PinnedDatesCard(
    today: LocalDate,
    anchors: List<AnchorRowUiState>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }

    // Inherit the segment position from the enclosing HrtSection so this card and the
    // hero above it round into a single grouped stack (the hero is index 0, this card
    // the last segment); a standalone card outside a section stays fully rounded.
    val position = LocalSegmentPosition.current ?: SegmentPosition(0, 1)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = segmentedListItemShape(
            index = position.index,
            count = position.count,
            cornerShape = MaterialTheme.shapes.large,
        ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            PinnedDatesCardHeader(
                modifier = Modifier.padding(vertical = 6.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap)),
            ) {
                anchors.forEachIndexed { index, anchor ->
                    PinnedDateRow(
                        anchor = anchor,
                        dateLabel = dateFormatter(anchor.date),
                        index = index,
                        itemCount = anchors.size,
                    )
                }
            }
        }
    }
}

@Composable
private fun PinnedDatesCardHeader(
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.journal_pinned_section)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_keep),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .cjkTextOffset(title),
        )

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnchorIconChip(anchor = anchor)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anchor.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.cjkTextOffset(anchor.name),
                )
                val supportingTextLabel = stringResource(
                    R.string.journal_since_date,
                    dateFormatter(anchor.date),
                )
                Text(
                    text = supportingTextLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.cjkTextOffset(supportingTextLabel),
                    )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val dayCountLabel = anchor.dayCountLabel()
                Text(
                    text = dayCountLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (anchor.isOnToday()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.cjkTextOffset(dayCountLabel),
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PinnedDateRow(
    anchor: AnchorRowUiState,
    dateLabel: String,
    index: Int,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    val colorScheme = rememberMedicationGroupColorScheme(colorKey = anchor.palette)
    val dayCountLabel = anchor.dayCountLabel()

    // A non-clickable Surface: it draws the segmented card but installs no clickable, so taps
    // pass through to the outer card's onClick. The whole PinnedDatesCard stays one touch
    // target (an interactive SegmentedListItem here would swallow the row's tap instead).
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = segmentedListItemShape(
            index = index,
            count = itemCount,
            cornerShape = MaterialTheme.shapes.large,
        ),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ListItemDefaults.ContentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anchor.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.cjkTextOffset(anchor.name),
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.cjkTextOffset(dateLabel),
                )
            }

            Text(
                text = dayCountLabel,
                style = MaterialTheme.typography.labelLarge,
                color = if (anchor.isOnToday()) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.cjkTextOffset(dayCountLabel),
            )
        }
    }
}

// The brand-new-user empty state for the Timeline section (no tracked dates at all): a centered
// welcome with a tinted calendar glyph, an encouraging headline + subtitle, and a primary
// "Add a date" button. Unlike the compact MilestonesEmptyCard row, the action lives on the button
// rather than the whole surface, giving first-time users a single, obvious tap target.
@Composable
fun EmptyMilestonesCard(
    onAddDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.journal_no_dates_welcome_title)
    val subtitle = stringResource(R.string.journal_no_dates_subtitle)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_event),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.cjkTextOffset(title),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.cjkTextOffset(subtitle),
            )
            Spacer(modifier = Modifier.height(14.dp))
            HrtFilledTonalButton(
                text = stringResource(R.string.journal_add_date),
                icon = Icons.Rounded.Add,
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
    MilestonesEmptyCard(
        icon = painterResource(R.drawable.ic_keep_alt),
        title = stringResource(R.string.journal_nothing_pinned_title),
        subtitle = stringResource(R.string.journal_nothing_pinned_subtitle),
        onClick = onClick,
        modifier = modifier,
    )
}

// A dedicated empty card for the timeline section (not the pinned shell): a leading icon, a
// title + subtitle, and a trailing outlined action. The whole card is also tappable, opening
// the milestones screen / add-date flow.
@Composable
private fun MilestonesEmptyCard(
    icon: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.cjkTextOffset(title),
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.cjkTextOffset(subtitle),
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null
            )
        }
    }
}

// The top-level Journal page hero card: a tappable, frosted summary of the first pinned
// anchor. It keeps the palette aurora wash + haze frost (the same blur as the Milestones
// hero) for identity but drops the watermark glyph. A small inline glyph precedes the title,
// the since-date and next-milestone pills sit under it, and a compact day count + chevron
// trail. The Milestones screen still shows the full HeroViewLayout.
@Composable
fun JournalHeroCard(
    hero: AnchorRowUiState,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = rememberMedicationGroupColorScheme(colorKey = hero.palette)
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) { dateLabelFormatter(appLocale, today) }
    val heroHazeState = rememberHazeState()
    val hazeBlurSupported = isHazeBlurSupported()
    // HazeMaterials.* are @Composable, so build the frost style here and capture it in the
    // (non-composable) blurEffect lambda below. A thin material keeps the wash visible.
    val frostStyle = HazeMaterials.thin(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

    // Inherit the segment position from the enclosing HrtSection so the hero rounds into
    // a grouped stack with the PinnedDatesCard below it (hero index 0); a standalone hero
    // (no pinned card) stays fully rounded.
    val position = LocalSegmentPosition.current ?: SegmentPosition(0, 1)

    EditorSegmentedListItem(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        cornerShape = MaterialTheme.shapes.large,
        // Pin the pressed shape to the resting segmented shape so SegmentedListItem's
        // default press morph doesn't reshape the edge-to-edge wash on tap.
        pressedShape = segmentedListItemShape(
            index = position.index,
            count = position.count,
            cornerShape = MaterialTheme.shapes.large,
        ),
        // Drawn edge-to-edge so the wash bleeds into the corners; the row re-applies its inset.
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val heroBackground = hero.heroBackground
            val drawsHeroBackground = heroBackground != HeroBackground.None
            // The wash is the haze source for the frosted foreground card on API 31+.
            when (heroBackground) {
                HeroBackground.DateColor -> HeroDateColorBackground(
                    colorScheme = colorScheme,
                    modifier = Modifier
                        .matchParentSize()
                        .hazeSource(heroHazeState),
                )

                is HeroBackground.Flag -> HeroColorBackground(
                    flag = heroBackground.flag,
                    modifier = Modifier
                        .matchParentSize()
                        .hazeSource(heroHazeState),
                )

                HeroBackground.None -> Unit
            }
            // Haze blur over the wash (API 31+ only), matching the Milestones hero's frost.
            if (drawsHeroBackground && hazeBlurSupported) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .hazeEffect(heroHazeState) {
                            blurEffect { this.style = frostStyle }
                        },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PinnedRowContentInset),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(bottom = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        MorphingLeadingIcon(anchor = hero, filled = false)
                        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_small)))
                        Text(
                            text = hero.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .cjkTextOffset(hero.name),
                        )
                    }
                    JournalHeroPills(
                        dateLabel = dateFormatter(hero.date),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompactHeroDayCount(hero = hero, valueColor = colorScheme.primary)
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// The hero's pills: a since-date pill and, for past anchors with an upcoming milestone, the
// next-milestone pill. Mirrors HeroChips (minus the Home tag) so the journal hero reads the
// same as the Milestones hero.
@Composable
private fun JournalHeroPills(
    dateLabel: String,
) {
    HrtPill(
        label = stringResource(R.string.journal_since_date, dateLabel),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        size = HrtPillSize.Small,
        icon = { Icon(painterResource(R.drawable.ic_event), null, iconModifier) },
    )
}

// The today/future/past pieces a hero day count is built from. Shared by CompactHeroDayCount
// and HeroCount so the magnitude conversion, today test, and string lookups live in one place;
// each composable styles the pieces its own way.
private data class HeroDayCountParts(
    val isToday: Boolean,
    val isFuture: Boolean,
    val value: Int,
    val todayText: String,
    val inPrefix: String,
    val dayUnit: String,
)

@Composable
private fun heroDayCountParts(hero: AnchorRowUiState): HeroDayCountParts {
    val value = hero.dayMagnitude.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return HeroDayCountParts(
        isToday = hero.isOnToday(),
        isFuture = hero.isFuture,
        value = value,
        todayText = stringResource(R.string.journal_today),
        inPrefix = stringResource(R.string.journal_in_prefix),
        dayUnit = pluralStringResource(R.plurals.journal_day_unit, value),
    )
}

// The hero's trailing day count: the magnitude as an emphasized number (palette primary) with
// the unit beside it, on a single baseline-aligned line. Mirrors HeroCount's past/future/today
// handling at a compact size.
@Composable
private fun CompactHeroDayCount(
    hero: AnchorRowUiState,
    valueColor: Color,
) {
    val parts = heroDayCountParts(hero)
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val tertiary = MaterialTheme.colorScheme.tertiary
    val text = buildAnnotatedString {
        if (parts.isToday) {
            withStyle(SpanStyle(color = tertiary, fontWeight = FontWeight.Medium)) {
                append(parts.todayText)
            }
        } else {
            // A normal space joins the parts so they share one baseline and shape together.
            if (parts.isFuture) {
                withStyle(SpanStyle(color = onSurfaceVariant, fontWeight = FontWeight.Medium)) { append(parts.inPrefix) }
                append(" ")
            }
            withStyle(SpanStyle(color = valueColor, fontWeight = FontWeight.Medium)) {
                append(parts.value.toString())
            }
            append(" ")
            withStyle(SpanStyle(color = onSurfaceVariant, fontWeight = FontWeight.Medium)) { append(parts.dayUnit) }
        }
    }

    // The unit ("days") is the only part that can be CJK; offsetting the whole line by it shifts
    // the number and prefix in lockstep. Today is a single span, so offset by it instead.
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = 20.sp
        ),
        modifier = Modifier.cjkTextOffset(if (parts.isToday) parts.todayText else parts.dayUnit),
    )
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
                    dragHandle = dragHandle,
                    sharedScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@AnimatedContent,
                )
            }
        }
    }
}

// The leading icon is two nested shared targets: the surface (chip background) morphs and
// crossfades via sharedBounds, while the glyph itself is a single continuous sharedElement so
// it slides/resizes without being crossfaded (doubled) against itself.
private const val HeroIconSurfaceSharedKey = "hero-icon-surface"
private const val HeroIconGlyphSharedKey = "hero-icon-glyph"
private const val HeroTitleSharedKey = "hero-title"

// Gap between a pinned row's leading chip and its text. Matches MedicationCard's 12dp so
// the hero edit row and the other pinned rows read as the same component.
private val PinnedRowLeadingGap = 12.dp

// The row zeroes SegmentedListItem's built-in content padding so the hero's large glyph
// overlay can bleed into the card's corner; each morph branch re-applies this inset to its
// own content so positions stay identical. Mirrors SegmentedListItem's default 16dp/10dp
// (also the inset MainE2HeroCard re-applies).
private val PinnedRowContentInset = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HeroViewLayout(
    anchor: AnchorRowUiState,
    heroNextMilestone: NextMilestoneUiState?,
    today: LocalDate,
    sharedScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val colorScheme = rememberMedicationGroupColorScheme(colorKey = anchor.palette)
    val heroHazeState = rememberHazeState()
    val hazeBlurSupported = isHazeBlurSupported()
    // HazeMaterials.* are @Composable, so build the frost style here and capture it in the
    // (non-composable) blurEffect lambda below. A thin material keeps the wash visible.
    val frostStyle = HazeMaterials.thin(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
    with(sharedScope) {
        Box(modifier = Modifier.fillMaxWidth()) {
            val heroBackground = anchor.heroBackground
            val drawsHeroBackground = heroBackground != HeroBackground.None
            // The wash is always drawn when a hero background is active. On API 31+ it also acts as
            // the haze source for the frosted foreground card and corner watermark.
            when (heroBackground) {
                HeroBackground.DateColor -> HeroDateColorBackground(
                    colorScheme = colorScheme,
                    modifier = Modifier
                        .matchParentSize()
                        .hazeSource(heroHazeState),
                )
                is HeroBackground.Flag -> HeroColorBackground(
                    flag = heroBackground.flag,
                    modifier = Modifier
                        .matchParentSize()
                        .hazeSource(heroHazeState),
                )
                HeroBackground.None -> Unit
            }
            // Haze blur on the foreground card (API 31+ only; deliberately independent of the
            // blur preference, unlike effectiveHazeBlurEnabled).
            if (drawsHeroBackground && hazeBlurSupported) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .hazeEffect(heroHazeState) {
                            blurEffect { this.style = frostStyle }
                        },
                )
            }
            // The large corner glyph. With a background on API 31+ it's a frosted, icon-shaped
            // haze over the wash (thick glass, surfaceContainerHigh); otherwise it stays a faint
            // flat tint.
            // Anchored top-end, bleeding past the card edge (the row zeroes its content padding for
            // this) and clipped by the rounded corner. Mirrors MainE2HeroCard's watermark.
            if (drawsHeroBackground && hazeBlurSupported) {
                HeroBackgroundWatermark(anchor = anchor, hazeState = heroHazeState)
            } else {
                Icon(
                    painter = painterResource(anchorIconRes(anchor.icon)),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(160.dp)
                        .alpha(0.1f)
                        .offset(x = 20.dp, y = (-20).dp),
                    // Tint to the date colour only when there's no background. This branch is
                    // reached with a background only below API 31 (no frosted watermark there);
                    // keep the glyph neutral then — onSurfaceVariant, the same tint the API 31+
                    // watermark glass uses — instead of washing it in the date hue on the wash.
                    tint = if (drawsHeroBackground) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        colorScheme.primary
                    },
                )
            }
            // Mirror MainE2HeroCard's content column, which carries a 6dp bottom padding
            // below its last row so the chips don't sit flush against the card's edge.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PinnedRowContentInset)
                    .padding(bottom = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MorphingLeadingIcon(
                        anchor = anchor,
                        filled = false,
                        sharedScope = sharedScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                    Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_small)))
                    HeroTitle(name = anchor.name, animatedVisibilityScope = animatedVisibilityScope, heroView = true, modifier = Modifier.padding(vertical = 6.dp))
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
                ) {
                    HeroCount(hero = anchor)
                    HeroChips(hero = anchor, nextMilestone = heroNextMilestone, today = today)
                }
            }
        }
    }
}

// The hero colour background: an "aurora band" — flag hues normalised by [HeroBackgroundColors],
// hue-sorted, and laid out as a soft linear spectrum sweep across the top-end, masked so the wash
// fades down before it reaches the text. A slow "breathing" glow (a gentle opacity + scale pulse
// anchored at the top-end) animates it, dropped to a static rest under the system "remove
// animations" setting. See the hero-background-placements design (Aurora band).
// Default linear-sweep angle: rightward, tilted slightly down. 90° = straight rightward; above 90
// tilts the sweep downward, below 90 tilts it up. Per-call overridable (the date wash uses its own).
private const val AuroraAngleDegrees = 110.0
private const val AuroraMaskOpaqueStop = 0.32f // fully visible from the top down to here…
private const val AuroraMaskFadeStop = 0.78f   // …then faded out by here, clearing the text below
private const val AuroraPulseDurationMillis = 6000
// "Breathing" amplitude: opacity dips to this floor (peaking at 1f) and the band zooms to this scale
// (resting at 1f). Wide enough to read as a live glow rather than a static wash.
private const val AuroraPulseMinAlpha = 0.55f
private const val AuroraPulseScale = 1.12f
// The mono-hue date wash uses a flatter, near-horizontal sweep so both seeds read across the whole
// top edge instead of one hue dominating a corner the way the steeper flag angle would.
private const val DateAuroraAngleDegrees = 95.0

@Composable
private fun HeroColorBackground(flag: PrideFlag, modifier: Modifier = Modifier) {
    // Read the actual scheme (covers system dark, the in-app theme override, and AMOLED).
    val isDark = isAppInDarkTheme()
    // No haze blur below API 31, so the unblurred wash is dimmed and desaturated a touch.
    val blurred = isHazeBlurSupported()
    val alpha = HeroBackgroundColors.bloomParams(isDark, blurred).alpha
    val colors = remember(flag, isDark, blurred) {
        HeroBackgroundColors.bloomColors(flag.seeds, isDark, blurred).map { Color(it).copy(alpha = alpha) }
    }
    HeroAuroraBackground(colors = colors, modifier = modifier)
}

@Composable
private fun HeroDateColorBackground(colorScheme: ColorScheme, modifier: Modifier = Modifier) {
    val isDark = isAppInDarkTheme()
    // No haze blur below API 31, so the unblurred wash is dimmed and desaturated a touch.
    val blurred = isHazeBlurSupported()
    val alpha = HeroBackgroundColors.bloomParams(isDark, blurred).alpha
    val colors = remember(colorScheme.primary, colorScheme.primaryContainer, isDark, blurred, alpha) {
        // Reversed so the brighter `primary` lands on the right (sparse content side) and the deeper
        // `primaryContainer` on the left, rather than the other way round.
        HeroBackgroundColors.dateColorBloomColors(
            primary = colorScheme.primary.toArgb(),
            primaryContainer = colorScheme.primaryContainer.toArgb(),
            isDark = isDark,
            blurred = blurred,
        ).asReversed().map { Color(it).copy(alpha = alpha) }
    }
    HeroAuroraBackground(colors = colors, angleDegrees = DateAuroraAngleDegrees, modifier = modifier)
}

@Composable
private fun HeroAuroraBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    angleDegrees: Double = AuroraAngleDegrees,
) {
    // "Breathing": opacity dips to AuroraPulseMinAlpha and the band zooms to AuroraPulseScale,
    // anchored top-end. Honour the system animator setting ("remove animations" /
    // animator-duration-scale 0) by resting static.
    val animatorsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    // Drive the pulse off the monotonic animation clock rather than a rememberInfiniteTransition, so
    // the breath stays phase-continuous: navigating away disposes this composable, but the clock
    // keeps running, so on return the wash resumes mid-breath instead of restarting from its dim
    // floor. A 0..1 eased triangle over a full in+out period reproduces the old Reverse-mode curve.
    val pulse = produceState(initialValue = 1f, animatorsEnabled) {
        if (!animatorsEnabled) return@produceState
        val periodMillis = AuroraPulseDurationMillis * 2
        while (true) {
            withInfiniteAnimationFrameMillis { frameMillis ->
                val phase = (frameMillis % periodMillis).toFloat() / periodMillis
                val triangle = if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
                value = EaseInOut.transform(triangle)
            }
        }
    }
    // Vertical fade mask: opaque across the top, gone before the text. Applied via DstIn below.
    val fadeMask = remember {
        Brush.verticalGradient(
            0f to Color.Black,
            AuroraMaskOpaqueStop to Color.Black,
            AuroraMaskFadeStop to Color.Transparent,
            1f to Color.Transparent,
        )
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                // Read the pulse inside the layer block (a deferred snapshot read) so each
                // animation frame only re-runs this lambda and invalidates the layer, instead
                // of recomposing the whole composable ~60fps.
                val p = if (animatorsEnabled) pulse.value else 1f
                this.alpha = if (animatorsEnabled) lerp(AuroraPulseMinAlpha, 1f, p) else 1f
                val scale = if (animatorsEnabled) lerp(1f, AuroraPulseScale, p) else 1f
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.8f, 0f)
                // Offscreen so the DstIn mask clips the band instead of punching through behind.
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawBehind {
                if (colors.isEmpty()) return@drawBehind
                // CSS linear-gradient angle -> a start/end line through the centre; the gradient
                // length |w·sinθ| + |h·cosθ| lands the end stops at the box's projection.
                val rad = Math.toRadians(angleDegrees)
                val dx = sin(rad)
                val dy = -cos(rad)
                val half = (abs(size.width * dx) + abs(size.height * dy)).toFloat() / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val start = Offset(center.x - (dx * half).toFloat(), center.y - (dy * half).toFloat())
                val end = Offset(center.x + (dx * half).toFloat(), center.y + (dy * half).toFloat())
                val band = if (colors.size == 1) {
                    Brush.linearGradient(listOf(colors.first(), colors.first()), start = start, end = end)
                } else {
                    Brush.linearGradient(colors = colors, start = start, end = end)
                }
                drawRect(brush = band)
                drawRect(brush = fadeMask, blendMode = BlendMode.DstIn)
            },
    )
}

// The large corner glyph as a frosted, icon-shaped haze: a haze region the size of the watermark,
// masked to the icon silhouette via DstIn so only the glyph shape shows the frosted aurora behind
// it (thick glass, tinted with surfaceContainerHigh). A Painter can't draw with a BlendMode, so
// the DstIn mask needs the glyph rasterised to an ImageBitmap first; the offscreen layer keeps
// DstIn from punching through to the layers behind. Placement mirrors the faint-icon overlay.
@Composable
private fun BoxScope.HeroBackgroundWatermark(
    anchor: AnchorRowUiState,
    hazeState: HazeState,
) {
    val iconPainter = painterResource(anchorIconRes(anchor.icon))
    val density = LocalDensity.current
    val sizePx = with(density) { 160.dp.roundToPx() }
    // HazeMaterials.* are @Composable; build the tinted style here and capture it below.
    val watermarkStyle = HazeMaterials.ultraThin(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    val iconMask = remember(anchor.icon, sizePx, iconPainter) {
        val bitmap = ImageBitmap(sizePx, sizePx)
        val maskSize = Size(sizePx.toFloat(), sizePx.toFloat())
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, GraphicsCanvas(bitmap), maskSize) {
            with(iconPainter) { draw(size = maskSize) }
        }
        bitmap
    }
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .size(160.dp)
            .offset(x = 20.dp, y = (-20).dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawImage(image = iconMask, blendMode = BlendMode.DstIn)
            }
            .hazeEffect(hazeState) {
                blurEffect { this.style = watermarkStyle }
            },
    )
}

// The compact pinned row, shared by every row (the hero in edit mode and all normal rows).
// The leading icon and title carry the hero's shared-element keys unconditionally: for the
// hero they morph against HeroViewLayout, for a normal row there's no counterpart so they
// just render in place. The trailing controls reveal with [isEditMode].
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PinnedCompactLayout(
    anchor: AnchorRowUiState,
    isHero: Boolean,
    isEditMode: Boolean,
    dateFormatter: (LocalDate) -> String,
    onUnpin: () -> Unit,
    sharedScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    dragHandle: Modifier = Modifier,
) {
    with(sharedScope) {
        Row(
            // Re-apply the inset the row zeroes for the hero overlay, so the compact row keeps
            // SegmentedListItem's standard content padding.
            modifier = Modifier.fillMaxWidth().padding(PinnedRowContentInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MorphingLeadingIcon(
                anchor = anchor,
                filled = true,
                sharedScope = sharedScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
            Spacer(modifier = Modifier.width(PinnedRowLeadingGap))
            Column(modifier = Modifier.weight(1f)) {
                HeroTitle(name = anchor.name, animatedVisibilityScope = animatedVisibilityScope)
                val baseSummary = stringResource(
                    R.string.journal_hero_edit_summary,
                    dateFormatter(anchor.date),
                    anchor.dayCountLabel(),
                )
                Text(
                    text = baseSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.cjkTextOffset(baseSummary),
                )
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
// and the compact edit row. The Home indicator (a chip in the view-mode row below)
// is not adjacent to it.
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.HeroTitle(
    modifier: Modifier = Modifier,
    name: String,
    animatedVisibilityScope: AnimatedVisibilityScope,
    heroView: Boolean = false,
) {
    Text(
        text = name,
        style = if (heroView) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
        fontWeight = if (heroView) FontWeight.SemiBold else FontWeight.Normal,
        color = if (heroView) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .sharedBounds(
                rememberSharedContentState(key = HeroTitleSharedKey),
                animatedVisibilityScope = animatedVisibilityScope,
            )
            .cjkTextOffset(name),
    )
}

// The hero's leading icon: a bare tinted glyph in view (no surface), a filled chip in edit
// (like the other pinned rows). The two states differ in two independent ways, so they get two
// nested shared targets (see NestedSharedBoundsSample):
//   • The surface (chip background) uses sharedBounds — its presence/size differs between
//     states, so we want both ends rendered and crossfaded while the bounds morph. The surface
//     is a background, so RemeasureToBounds keeps the corner radius crisp as it resizes (rather
//     than scaleToBounds, which would graphically stretch the rounded rect).
//   • The glyph is identical in both states, so it's a single continuous sharedElement: it
//     slides/resizes as one element instead of being crossfaded against itself (which is what
//     made it balloon then fade). sharedElement must be the innermost target on the glyph.
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MorphingLeadingIcon(
    anchor: AnchorRowUiState,
    filled: Boolean,
    modifier: Modifier = Modifier,
    sharedScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val colorScheme = rememberMedicationGroupColorScheme(colorKey = anchor.palette)
    // The morphing hero ⇄ compact rows pass both scopes so the surface and glyph become shared
    // targets; the standalone journal hero card passes neither and just renders in place. The
    // null-ness is fixed per call site, so the conditional remember calls are stable.
    val shared = sharedScope != null && animatedVisibilityScope != null
    val surfaceShared = if (shared) {
        with(sharedScope!!) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = HeroIconSurfaceSharedKey),
                animatedVisibilityScope = animatedVisibilityScope!!,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            )
        }
    } else {
        Modifier
    }
    val glyphShared = if (shared) {
        with(sharedScope!!) {
            Modifier.sharedElement(
                rememberSharedContentState(key = HeroIconGlyphSharedKey),
                animatedVisibilityScope = animatedVisibilityScope!!,
            )
        }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(surfaceShared)
            // Filled (edit) matches AnchorIconChip/MedicationCard at 36dp; the bare view
            // glyph stays 18dp and sharedBounds morphs the surface across the two sizes.
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
            modifier = Modifier
                .size(if (filled) 20.dp else 18.dp)
                .then(glyphShared),
            tint = if (filled) colorScheme.onPrimaryContainer else colorScheme.primary,
        )
    }
}

// Enter/exit for the edit-mode trailing controls: the cluster slides in from the end
// (right) and fades. Shared by the hero (via animateEnterExit) and every other pinned row
// (via AnimatedVisibility) so all rows' controls animate with the same spec.
private fun editTrailingEnter() = slideInHorizontally { width -> width } + fadeIn()

private fun editTrailingExit() = slideOutHorizontally { width -> width } + fadeOut()

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
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        size = HrtPillSize.Small,
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
    val colorScheme = rememberMedicationGroupColorScheme(colorKey = hero.palette)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        HrtPill(
            label = stringResource(R.string.journal_since_date, dateFormatter(hero.date)),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            size = HrtPillSize.Small,
            icon = { Icon(painterResource(R.drawable.ic_event), null, iconModifier) },
        )
        HomeTag()
        if (!hero.isFuture && nextMilestone != null) {
            // The milestone pill carries the date color (RegimenMedicationChip's scheme).
            HrtPill(
                label = nextMilestone.label(),
                containerColor = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
                labelColor = colorScheme.onPrimaryFixed,
                size = HrtPillSize.Small,
                icon = { Icon(painterResource(R.drawable.ic_flag), null, iconModifier) },
            )
        }
    }
}

@Composable
private fun HeroCount(hero: AnchorRowUiState) {
    val parts = heroDayCountParts(hero)
    // The big count mirrors MainE2HeroCard's hero value: displayLarge/Medium in the
    // date's primary color with a titleMedium supporting unit, aligned to the value's
    // baseline (alignByBaseline) rather than nudged with bottom padding.
    val datePrimary = rememberMedicationGroupColorScheme(colorKey = hero.palette).primary
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (parts.isToday) {
            Text(
                text = parts.todayText,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        } else {
            if (parts.isFuture) {
                Text(
                    text = parts.inPrefix,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Text(
                text = parts.value.toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Medium,
                color = datePrimary,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = parts.dayUnit,
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

        MilestoneUnit.YEARS -> pluralStringResource(
            R.plurals.journal_milestone_label_years,
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
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(anchorIconRes(anchor.icon)),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun AnchorSummaryText(
    anchor: AnchorRowUiState,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }
    // The supporting line is always just the anchor date. The day count lives in the
    // trailing slot in both modes, so it is never duplicated inline here. The pinned
    // glyph is gone too: a row's pin state is already shown by the Pinned section above
    // and by the trailing pin toggle in edit mode.
    val dateLabel = dateFormatter(anchor.date)

    Column(modifier = modifier) {
        Text(
            text = anchor.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.cjkTextOffset(anchor.name),
        )
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.cjkTextOffset(dateLabel),
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
    // A row being dragged is the only time the container clip below is dropped.
    var draggingRowCount by remember { mutableStateOf(0) }

    AnimatedContent(
        targetState = anchors.isEmpty(),
        // Clip the empty<->rows size morph to the rounded card shape so the size animation
        // can't briefly reveal square bottom corners. Dropped only while a row is being
        // dragged, so the lifted row's elevation shadow isn't cropped at the tray's left/right
        // edges — a drag never coincides with the empty<->rows morph, so the two never fight.
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (draggingRowCount > 0) Modifier else Modifier.clip(MaterialTheme.shapes.large)
            ),
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
                onDraggingRowCountChange = { delta -> draggingRowCount += delta },
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
    // Reports +1 when a row starts dragging and -1 when it stops, so PinnedTray can
    // drop its shadow-cropping clip for the duration of the drag.
    onDraggingRowCountChange: (Int) -> Unit = {},
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
    // The reorderable library calls onSettle (and so onReorder, which applies the new
    // order) only AFTER its drop spring finishes. If edit mode is exited inside that
    // window, the rows would react to the not-yet-reordered list: the gap arrangement
    // would flip (changing ReorderableColumn's remember(list, spacing) key, rebuilding
    // its state and snapping the order back), and the hero would morph in using the old
    // top row before re-morphing once the new order lands. So keep treating the rows as
    // in edit mode until the settle completes — gaps, drag handles and the hero transform
    // all wait for onSettle.
    var settlingAfterDrag by remember { mutableStateOf(false) }
    // onSettle records the just-settled order here instead of clearing the hold directly.
    // A reorder rebuilds the moved row's subtree at its new position, so releasing the hold
    // in that same composition would seed the fresh top row straight into the hero view with
    // no morph (a snap). Instead release the hold one composition LATER — once the reordered
    // list has rendered with the hold still on, so the new top is compact for a frame and
    // then morphs into the hero view.
    var settledOrder by remember { mutableStateOf<List<String>?>(null) }
    val displayOrderIds = displayAnchors.map { it.id }
    val settledOrderLanded = settledOrder == displayOrderIds
    LaunchedEffect(settledOrderLanded) {
        if (settledOrderLanded) {
            settlingAfterDrag = false
            settledOrder = null
        }
    }
    LaunchedEffect(settlingAfterDrag) {
        if (settlingAfterDrag) {
            // Safety net for a drop with no reorder (onSettle never fires, so settledOrder
            // never lands): the spring is StiffnessMediumLow, so this outlasts it.
            delay(1000)
            settlingAfterDrag = false
            settledOrder = null
        }
    }
    val editingOrSettling = isEditMode || settlingAfterDrag
    val reorderOwnsGaps = editingOrSettling && !hasTransitioningRows

    // ReorderableColumn is used in both modes so the hero stays the first row and
    // morphs in place; drag is only enabled in edit mode.
    ReorderableColumn(
        modifier = Modifier.fillMaxWidth(),
        list = displayAnchors,
        onSettle = { fromIndex, toIndex ->
            val displayOrder = displayAnchors.map { it.id }
            val newOrder = reorderedIds(displayOrder, fromIndex, toIndex)
                .filter { it in activeIdSet }
            onReorder(newOrder)
            // Release the hold only after this newOrder has rendered (see settledOrder above),
            // so the reordered top row morphs into the hero view instead of snapping into it.
            settledOrder = newOrder
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
            if (isDragging) {
                DisposableEffect(Unit) {
                    onDraggingRowCountChange(1)
                    onDispose { onDraggingRowCountChange(-1) }
                }
            }
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
            // onDragStopped fires at release, before the drop spring (and so onSettle).
            // Mark the settle as in-flight here so the gap arrangement holds until the
            // reorder is applied, instead of snapping if edit mode is exited meanwhile.
            val onDragStopped: (Float) -> Unit = { settlingAfterDrag = true }
            val rowDragModifier = if (editingOrSettling && isActive) {
                Modifier.longPressDraggableHandle(
                    interactionSource = interactionSource,
                    onDragStopped = onDragStopped,
                )
            } else {
                Modifier
            }
            val gripDragHandle = if (editingOrSettling && isActive) {
                Modifier.draggableHandle(
                    interactionSource = gripInteractionSource,
                    onDragStopped = onDragStopped,
                )
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
                        isEditMode = editingOrSettling,
                        isDragging = isDragging,
                        heroNextMilestone = heroNextMilestone,
                        today = today,
                        onUnpin = {
                            if (isActive) {
                                onSetPinned(anchor.id, false)
                            }
                        },
                        dragHandle = gripDragHandle,
                        contentModifier = contentModifier,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(rowDragModifier)
                            .shadow(elevation, shape = MaterialTheme.shapes.large)
                            .semantics {
                                if (editingOrSettling && isActive) {
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
        // Drawn edge-to-edge: the hero's overlay glyph bleeds into the card corner and
        // each morph branch re-applies PinnedRowContentInset to its own content.
        contentPadding = PaddingValues(0.dp),
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
            modifier = Modifier.size(22.dp),
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
        SupportMessageListItem(
            text = stringResource(R.string.journal_no_dates),
            painter = painterResource(R.drawable.ic_info),
            modifier = modifier,
        )
        return
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // Split the date-sorted nodes into three runs around today: past, today, and
        // future. Each run gets its own segment index/count so its cards carry their own
        // grouped corners — a lone today node becomes a fully-rounded standalone card —
        // while one continuous rail still threads through all three. isFirst/isLast below
        // drive that rail's end-caps and are computed over the whole sequence.
        val dividerIndex = todayDividerIndex.coerceIn(0, nodes.size)
        val past = nodes.subList(0, dividerIndex)
        val rest = nodes.subList(dividerIndex, nodes.size)
        // Today-dated nodes are never "before" today, so they lead the rest run; peel the
        // contiguous leading run of them off into their own section.
        val todayCount = rest.takeWhile { it.anchor.isOnToday() }.size
        val todayNodes = rest.subList(0, todayCount)
        val future = rest.subList(todayCount, rest.size)
        // The Today divider only marks "now" when it falls in the gap between a past and a
        // future milestone. When a node already lands on today, that node is the marker
        // (haloed dot + "Today" badge), so the rule is dropped; and with only past or only
        // future dates it would dangle at an end, so it is dropped there too.
        val showToday = ShowTodayMarker && past.isNotEmpty() && future.isNotEmpty() &&
            todayNodes.isEmpty()
        // Content padding matches MainLowStockSection's card so the subcards sit with
        // the same inset as the home low-stock list.
        Column(modifier = Modifier.padding(horizontal = TimelineDotInset, vertical = 16.dp)) {
            val rowCount = nodes.size + if (showToday) 1 else 0
            var rendered = 0
            past.forEachIndexed { i, node ->
                TimelineMilestoneRow(
                    node = node,
                    segIndex = i,
                    segCount = past.size,
                    isFirst = rendered == 0,
                    isLast = rendered == rowCount - 1,
                    isEditMode = isEditMode,
                    today = today,
                    onSetPinned = onSetPinned,
                    onUpdateDate = onUpdateDate,
                )
                rendered++
            }
            todayNodes.forEachIndexed { i, node ->
                TimelineMilestoneRow(
                    node = node,
                    segIndex = i,
                    segCount = todayNodes.size,
                    isFirst = rendered == 0,
                    isLast = rendered == rowCount - 1,
                    isEditMode = isEditMode,
                    today = today,
                    onSetPinned = onSetPinned,
                    onUpdateDate = onUpdateDate,
                )
                rendered++
            }
            if (showToday) {
                TodayMarkerRow(
                    today = today,
                    isFirst = rendered == 0,
                    isLast = rendered == rowCount - 1,
                )
                rendered++
            }
            future.forEachIndexed { j, node ->
                TimelineMilestoneRow(
                    node = node,
                    segIndex = j,
                    segCount = future.size,
                    isFirst = rendered == 0,
                    isLast = rendered == rowCount - 1,
                    isEditMode = isEditMode,
                    today = today,
                    onSetPinned = onSetPinned,
                    onUpdateDate = onUpdateDate,
                )
                rendered++
            }
        }
    }
}

@Composable
private fun TimelineMilestoneRow(
    node: TimelineNodeUiState,
    segIndex: Int,
    segCount: Int,
    isFirst: Boolean,
    isLast: Boolean,
    isEditMode: Boolean,
    today: LocalDate,
    onSetPinned: (String, Boolean) -> Unit,
    onUpdateDate: (AnchorRowUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val anchor = node.anchor
    // IntrinsicSize.Min lets the rail cell's fillMaxHeight match the row height,
    // so the rail line spans the full row and abuts its neighbours into one
    // continuous line. The inter-card gap is split across each card's top and
    // bottom padding so two neighbours sum to list_segment_gap; the first card
    // drops its top inset and the last drops its bottom so the run sits flush
    // against the surrounding Column padding.
    val halfGap = dimensionResource(R.dimen.list_segment_gap) / 2
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        TimelineRailCell(
            isFirst = isFirst,
            isLast = isLast,
            hasNode = true,
            // The today dot widens its rail gap to make room for the breathing halo.
            nodeGap = if (anchor.isOnToday()) TimelineTodayNodeGap else TimelineNodeGap,
        ) { MilestoneNode(anchor) }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(
                    top = if (isFirst) 0.dp else halfGap,
                    bottom = if (isLast) 0.dp else halfGap,
                ),
        ) {
            EditorSegmentedListItem(
                index = segIndex,
                count = segCount,
                modifier = Modifier.fillMaxWidth(),
                cornerShape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                // View mode: the whole row taps through to the date editor (the trailing
                // chevron hints it). Edit mode: the whole row toggles this anchor's pin
                // (the trailing pin icon reflects the resulting state).
                onClick = if (isEditMode) {
                    { onSetPinned(anchor.id, !node.isPinned) }
                } else {
                    { onUpdateDate(anchor) }
                },
                leadingContent = { AnchorIconChip(anchor = anchor) },
                trailingContent = {
                    // The day count stays put; the trailing slot flips between a chevron
                    // (view mode — the row taps through to the editor) and a pin icon
                    // (edit mode — the whole row toggles the pin), a coin-flip matching the
                    // History app bar's FlipSlot. Both faces share a 24dp footprint so the
                    // trailing width holds across the flip.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dayCountLabelText = anchor.dayCountLabel()
                        Text(
                            text = dayCountLabelText,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (anchor.isOnToday()) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.cjkTextOffset(dayCountLabelText)
                        )
                        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_small)))
                        FlipSlot(
                            flipped = isEditMode,
                            front = {
                                Box(
                                    modifier = Modifier.size(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                            back = {
                                Box(
                                    modifier = Modifier.size(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (node.isPinned) {
                                                R.drawable.ic_keep
                                            } else {
                                                R.drawable.ic_keep_alt
                                            },
                                        ),
                                        contentDescription = stringResource(
                                            R.string.journal_pin_to_home_content_description,
                                        ),
                                        // The pin glyph reads heavier than the chevron, so it
                                        // sits a touch smaller to balance the two flip faces.
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                        )
                    }
                },
            ) {
                AnchorSummaryText(anchor = anchor, today = today)
            }
        }
    }
}

@Composable
private fun TimelineRailCell(
    isFirst: Boolean,
    isLast: Boolean,
    hasNode: Boolean,
    modifier: Modifier = Modifier,
    nodeGap: Dp = TimelineNodeGap,
    node: @Composable BoxScope.() -> Unit,
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    // The dot's left edge sits flush at the row's start; the connector line runs
    // straight down the dot's centre with rounded caps. isFirst/isLast drop the
    // segment that would dangle past the first/last node, and hasNode carves the
    // gap around a dot (the Today marker passes false for an uninterrupted rule).
    // nodeGap widens that carve — the today dot uses a larger one for its halo.
    Box(
        modifier = modifier.width(TimelineRailWidth).fillMaxHeight(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val centerX = TimelineNodeSize.toPx() / 2f
            val gap = centerX + nodeGap.toPx()
            val stroke = TimelineRailStroke.toPx()
            val centerY = size.height / 2f
            if (!isFirst) {
                drawLine(
                    color = line,
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, if (hasNode) centerY - gap else centerY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            if (!isLast) {
                drawLine(
                    color = line,
                    start = Offset(centerX, if (hasNode) centerY + gap else centerY),
                    end = Offset(centerX, size.height),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
        node()
    }
}

// A node dated exactly today: magnitude 0 and not in the future. Drives the "now"
// halo and the today timeline section.
private fun AnchorRowUiState.isOnToday(): Boolean = dayMagnitude == 0L && !isFuture

@Composable
private fun MilestoneNode(anchor: AnchorRowUiState) {
    val accent = rememberMedicationGroupColorScheme(colorKey = anchor.palette).primary
    Box(
        modifier = Modifier
            .size(TimelineNodeSize)
            // A today dot wears a soft "now" halo: a translucent disc drawn behind it.
            // drawBehind keeps the measured size at TimelineNodeSize, so the dot's centre
            // stays on the connector line while the halo overflows behind it.
            .then(
                if (anchor.isOnToday()) {
                    Modifier.drawBehind {
                        drawCircle(
                            color = accent.copy(alpha = TodayHaloAlpha),
                            radius = TodayHaloRadius.toPx(),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .background(
                color = if (anchor.isFuture) {
                    MaterialTheme.colorScheme.surfaceContainer
                } else {
                    accent
                },
                shape = CircleShape,
            )
            .border(2.dp, accent, CircleShape),
    )
}

// Quiet "dotless rule" Today marker: the rail line passes straight through (no
// node), and the content is a faint full-width rule around a small neutral
// "Today · date" label. To drop the Today marker entirely (variant A), set
// ShowTodayMarker = false — the rail's first/last line math adjusts on its own.
@Composable
private fun TodayMarkerRow(
    today: LocalDate,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) { dateLabelFormatter(appLocale, today) }
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        TimelineRailCell(isFirst = isFirst, isLast = isLast, hasNode = false) {}
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = TimelineCardGap / 2 + 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Separate Text nodes so tests match "Today" and the date exactly.
                Text(
                    text = stringResource(R.string.journal_today),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = dateFormatter(today),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(modifier = Modifier.weight(1f))
        }
    }
}

// Coordinates which note editor is open across the Today composer and the timeline rows below
// it. Holding the open note's identity in one place keeps a single editor open at a time —
// opening another closes the previous. The open identity is persisted via [Saver] (it is always
// a note id or "today-<date>" String), so an editor left open survives process death and
// navigating away and back: the row reopens with its saved draft (draftText is itself
// rememberSaveable). On restore the field reopens but is not focused, so the keyboard stays down
// until the user taps it (see the focus gate in NoteEditorCard).
@Stable
class NoteEditorController(initialActiveEditorId: Any? = null) {
    var activeEditorId by mutableStateOf(initialActiveEditorId)
        private set

    fun isEditing(identity: Any?): Boolean = activeEditorId != null && activeEditorId == identity

    fun begin(identity: Any?) {
        activeEditorId = identity
    }

    fun finish(identity: Any?) {
        if (activeEditorId == identity) activeEditorId = null
    }

    companion object {
        val Saver: Saver<NoteEditorController, String> = Saver(
            save = { it.activeEditorId as? String },
            restore = { NoteEditorController(initialActiveEditorId = it) },
        )
    }
}

@Composable
fun rememberNoteEditorController(): NoteEditorController =
    rememberSaveable(saver = NoteEditorController.Saver) { NoteEditorController() }

@Composable
fun TodayComposer(
    today: LocalDate,
    note: Note?,
    onSave: (String) -> Unit,
    onDelete: () -> Unit = { },
    modifier: Modifier = Modifier,
    editorController: NoteEditorController = rememberNoteEditorController(),
    saveFailureToken: Int = 0,
) {
    EditorSegmentedListItem(
        modifier = modifier.fillMaxWidth(),
    ) {
        NoteEditorCard(
            modifier = Modifier.padding(bottom = 6.dp),
            text = note?.text.orEmpty(),
            identity = note?.id ?: "today-$today",
            controller = editorController,
            onSave = onSave,
            onDelete = onDelete,
            fieldModifier = Modifier.testTag(TodayComposerTextFieldTestTag),
            reserveWritingHeight = true,
            header = { TodayComposerHeader(today = today) },
            prompt = { onClick -> TodayComposerPrompt(onClick = onClick) },
            saveFailureToken = saveFailureToken,
        )
    }
}

// The composer's editable note surface, shared by the Today composer and the notes-timeline
// rows. Owns the view<->edit state and the open/close animation: it hugs the saved [text] in
// view mode and, when tapped, expands into a filled box with a minimum writing height and
// Cancel/Save (+Delete) icon buttons that fade in on the spring. [header] renders above the
// box (the Today title+date, or a row's date label). [prompt] is the view-mode affordance
// shown when there is no text yet (Today's "write about today"); pass null where text always
// exists (timeline rows). [identity] keys the edit state so it resets when the note changes.
// [reserveWritingHeight] opens the field to a minimum writing height (the Today composer, where a
// new note starts empty); leave it false where the field already has text to hug (timeline rows).
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NoteEditorCard(
    text: String,
    identity: Any,
    controller: NoteEditorController,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    reserveWritingHeight: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    prompt: (@Composable (onClick: () -> Unit) -> Unit)? = null,
    saveFailureToken: Int = 0,
) {
    // The open/closed state is owned by the shared controller so only one card edits at a time
    // and the open state is dropped on navigation away. The draft stays local and is re-seeded
    // from [text] each time editing begins.
    val isEditing = controller.isEditing(identity)
    var draftText by rememberSaveable(identity, text) { mutableStateOf(text) }
    var isDeleteConfirmationVisible by rememberSaveable(identity) { mutableStateOf(false) }
    // Whether this edit targets text that already existed. Captured when editing begins and
    // held for the whole session, including the closing animation. Saving a brand-new note
    // flips the saved text from empty to non-empty right as the editor collapses, so deriving
    // the delete button from it live would pop delete in mid-close; this keeps it hidden for a
    // note first created in this session.
    var editingExistingNote by remember { mutableStateOf(text.isNotEmpty()) }
    // A brand-new note's saved [text] only flips non-empty after the save round-trips through the
    // repository — a frame or more after the editor closes [isEditing] synchronously. Without this
    // latch there's a window where neither isEditing nor text is truthy, so the field would collapse
    // back to the prompt for a beat ("write about today…" blinking in) before the saved text lands.
    // Set when a save is committed; released once the text catches up (see the effect below).
    var savePending by remember(identity) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    // A stable scope for the reveal scroll, deliberately separate from the keyed effect below.
    // The reveal has to re-fire as the box grows and the keyboard rises, but a LaunchedEffect
    // cancels its body whenever its keys change — so firing the scroll there cancels the in-flight
    // animation every frame the keyboard ticks, and it only settles once the keyboard stops (a
    // visible "catch-up" step after the expand). Launching into this scope instead leaves prior
    // requests running; the bring-into-view responder folds them into one continuous scroll.
    val revealScope = rememberCoroutineScope()
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val sizeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<IntSize>()

    // Reveal the whole editor — field plus the button row that grows in below it — above the IME.
    // The reveal must re-fire as the box expands and as the keyboard rises (a single request on
    // focus only catches the collapsed box, leaving the buttons under the IME), so it is keyed on
    // both the measured box height and the IME inset. Only the open editor reads the IME inset, so
    // idle rows don't recompose through the keyboard animation.
    val density = LocalDensity.current
    var editorBoxHeightPx by remember { mutableStateOf(0) }
    val imeBottomPx = if (isEditing) WindowInsets.ime.getBottom(density) else 0

    // The box stays mounted whenever text exists, so tapping to edit never shifts it; with no
    // text yet the prompt stands in until the first edit.
    val showField = isEditing || text.isNotEmpty() || savePending

    val finishEditing = {
        focusManager.clearFocus()
        // Dismiss the keyboard on close, mirroring the calibration notes field's onDone
        // (clearFocus + hide). Shared by Save/Cancel/IME action; a no-op when already hidden.
        // NOTE: this dismiss can stutter (the focus change cancels the keyboard's slide); the
        // calibration field has the same issue, to be fixed for both later.
        keyboardController?.hide()
        controller.finish(identity)
    }

    val beginEditing = {
        draftText = text
        editingExistingNote = text.isNotEmpty()
        controller.begin(identity)
    }

    // Commit the draft and close the editor. Shared by the Save button and the IME "Done" action
    // so both follow the same save-then-clear-focus path. A blank or unchanged draft writes
    // nothing and just reverts to the saved text.
    val saveEditing = {
        val trimmed = draftText.trim()
        if (trimmed.isNotEmpty() && trimmed != text.trim()) {
            // Hold the field through the async save so the prompt doesn't blink back in while the
            // committed text round-trips. Released by the effect below once [text] reflects the save.
            savePending = true
            onSave(trimmed)
        } else if (text.isNotEmpty()) {
            draftText = text
        }
        finishEditing()
    }

    // Discard the in-progress edit and close. Existing text stays mounted, so revert the draft to
    // it; a brand-new note animates back to the prompt, so leave the draft alone (clearing it
    // would blank the field mid-exit) and let the next open re-seed it. Shared by the Cancel button
    // and the back gesture.
    val cancelEditing = {
        if (text.isNotEmpty()) draftText = text
        finishEditing()
    }

    // While an editor is open, back cancels the edit and dismisses the keyboard instead of leaving
    // the journal; once nothing is open the handler is disabled and back falls through to
    // navigation (home). Only the open card has isEditing == true, so only its handler is enabled.
    BackHandler(enabled = isEditing) { cancelEditing() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        header?.invoke()
        AnimatedContent(
            targetState = showField,
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = fadeIn(fadeSpec),
                    initialContentExit = fadeOut(fadeSpec),
                    sizeTransform = SizeTransform { _, _ -> sizeSpec },
                )
            },
            contentAlignment = Alignment.TopStart,
            label = "note-editor",
        ) { fieldShown ->
            if (fieldShown) {
                // One filled, rounded box holds the text field and—while editing—the buttons,
                // so the editing surface reads as a single card that grows and shrinks. The
                // field reserves a minimum height when editing and hugs the text otherwise; the
                // buttons reveal below it on the same spring, so the box resizes as one motion.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        // Track the box's grown height and let the reveal scroll it (field + button
                        // row) above the IME — see the bringIntoView effect below.
                        .onSizeChanged { editorBoxHeightPx = it.height }
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    ComposerTextField(
                        value = draftText,
                        onValueChange = { draftText = it },
                        focusRequester = focusRequester,
                        onFocused = {
                            editingExistingNote = text.isNotEmpty()
                            controller.begin(identity)
                        },
                        onImeAction = saveEditing,
                        expanded = isEditing,
                        reserveWritingHeight = reserveWritingHeight,
                        modifier = fieldModifier.fillMaxWidth(),
                    )
                    AnimatedVisibility(
                        visible = isEditing,
                        // The box makes room on [sizeSpec]; the buttons fade on a slower spring
                        // so the fade lags the height reveal (otherwise they snap to full
                        // opacity while the box is still clipping them in, which reads as a
                        // wipe, not a fade). On exit the fade leads the shrink so they fade out
                        // before the box closes over them.
                        enter = expandVertically(
                            animationSpec = sizeSpec,
                            expandFrom = Alignment.Top,
                        ) + fadeIn(MaterialTheme.motionScheme.slowEffectsSpec()),
                        exit = shrinkVertically(
                            animationSpec = sizeSpec,
                            shrinkTowards = Alignment.Top,
                        ) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        TodayComposerControls(
                            // Gap above the buttons, inside the reveal so it animates too.
                            modifier = Modifier.padding(
                                top = dimensionResource(R.dimen.padding_small),
                            ),
                            onDelete = if (editingExistingNote) {
                                { isDeleteConfirmationVisible = true }
                            } else {
                                null
                            },
                            onCancel = cancelEditing,
                            onSave = saveEditing,
                        )
                    }
                }
            } else {
                prompt?.invoke(beginEditing)
            }
        }
    }

    // Focus the field (raising the IME) when edit mode begins from a tap this session. Tapping
    // existing text's field already focuses it (that focus is what flips isEditing); this also
    // covers expanding from the prompt, where the tap landed on the prompt rather than the field.
    // An editor restored already-open (process death, or navigating away and back) is open on the
    // first composition, so it reopens silently — draft shown, keyboard down — until the user taps.
    // On close with no pending save, discard the in-progress draft so it can't linger: the collapsed
    // row renders draftText, and re-opening goes through controller.begin (not beginEditing) without
    // re-seeding, so an abandoned edit would otherwise resurface — and be re-savable — after
    // switching to another card, cancelling, backing out, or confirming a delete (whose write may
    // fail, leaving the row showing an unsaved draft as if persisted). A pending save keeps its
    // draft: it is held through the round-trip and the failure-recovery reopen.
    var firstCompositionSettled by remember { mutableStateOf(false) }
    LaunchedEffect(isEditing) {
        if (isEditing) {
            if (firstCompositionSettled) runCatching { focusRequester.requestFocus() }
        } else if (!savePending) {
            draftText = text
        }
        firstCompositionSettled = true
    }

    // Release the save latch once the committed text lands — from here the field is held by
    // text.isNotEmpty() and normal text-driven logic governs again (so a later delete can revert
    // to the prompt). Until then savePending keeps the field mounted through the save round-trip.
    LaunchedEffect(text) {
        if (text.isNotEmpty()) savePending = false
    }

    // A failed save never round-trips text, so the savePending latch (and the held field showing the
    // unsaved draft as if it closed) would otherwise stick forever. When the VM signals a save
    // failure via a bumped token, release the latch and re-open this editor so the unsaved draft
    // stays editable for retry. The token is shared by every note composer, but only the one that
    // actually saved has savePending set, so only it reacts; the initial token (0) is a no-op
    // because savePending is false then. controller.begin(identity) is used directly (not
    // beginEditing) so draftText is NOT re-seeded — the typed text is preserved.
    LaunchedEffect(saveFailureToken) {
        if (savePending) {
            savePending = false
            controller.begin(identity)
        }
    }

    // Keep the full editor in view as it grows and as the keyboard rises: re-fire on every box
    // height and IME-inset change so the final, fully-expanded box (field + button row) clears the
    // keyboard. Each request is launched into [revealScope] rather than run in this effect's own
    // (keyed, cancel-on-rekey) coroutine, so a new request never cancels the in-flight scroll —
    // the responder coalesces them into one motion that rides up with the keyboard instead of
    // snapping into place after it settles.
    LaunchedEffect(isEditing, editorBoxHeightPx, imeBottomPx) {
        if (isEditing) revealScope.launch { runCatching { bringIntoViewRequester.bringIntoView() } }
    }

    if (isDeleteConfirmationVisible) {
        NoteDeleteConfirmationDialog(
            onDismissRequest = { isDeleteConfirmationVisible = false },
            onConfirm = {
                isDeleteConfirmationVisible = false
                finishEditing()
                onDelete()
            },
        )
    }
}

@Composable
private fun TodayComposerHeader(
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) {
        medicationGroupScheduleDateFormatter(appLocale, today)
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_edit_note),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_small)))

        val journalTodayLabel = stringResource(R.string.journal_today)
        Text(
            text = journalTodayLabel,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).alignByBaseline().cjkTextOffset(journalTodayLabel),
        )
        // Trailing date with a short weekday, matching the note rows' titles (e.g. "Jun 23 Tue").
        val todayLabel = dateFormatter(today)
        Text(
            text = todayLabel,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline().cjkTextOffset(todayLabel)
        )
    }
}

@Composable
private fun TodayComposerControls(
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.journal_delete_note),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(
                onClick = onSave,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.save),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

// Collapsed "write about today" affordance — a filled inset that reads as a tappable field.
@Composable
private fun TodayComposerPrompt(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(dimensionResource(R.dimen.padding_medium)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val journalWriteAboutTodayLabel = stringResource(R.string.journal_write_about_today)
        Text(
            text = journalWriteAboutTodayLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.cjkTextOffset(journalWriteAboutTodayLabel)
        )
    }
}

// Borderless multiline input for the composer. The parent draws the filled, rounded box and
// its padding; this just lays out the text and reserves a minimum writing height while
// editing. Gaining focus (a tap) signals edit mode via [onFocused]; the parent drives
// [focusRequester] when edit mode is entered some other way (the prompt).
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ComposerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    reserveWritingHeight: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    // When [reserveWritingHeight] is set (the Today composer), edit mode opens ~3 lines of writing
    // room so a fresh, empty note starts comfortable; otherwise (timeline rows, which already have
    // text) the field just hugs its text. Animating the min height (rather than toggling minLines)
    // grows/shrinks smoothly instead of snapping, and doesn't re-animate on every keystroke. Uses
    // the motionScheme effects spring — the same one the buttons reveal on — so the field and
    // buttons move together as one resize.
    val minHeight by animateDpAsState(
        targetValue = if (expanded && reserveWritingHeight) 72.dp else 0.dp,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Dp>(),
        label = "composer-field-min-height",
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onSurface),
        cursorBrush = SolidColor(colorScheme.primary),
        // The keyboard's action key saves and dismisses (commit + clearFocus + hide) — the same
        // path as the Save button, matching the calibration notes field's onDone. The field stays
        // multiline for display.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onImeAction() }),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = minHeight)) {
                innerTextField()
            }
        },
    )
}

@Composable
fun NotesTimeline(
    notes: List<Note>,
    today: LocalDate,
    onSave: (LocalDate, String) -> Unit,
    onDelete: (LocalDate) -> Unit = { },
    modifier: Modifier = Modifier,
    editorController: NoteEditorController = rememberNoteEditorController(),
    saveFailureToken: Int = 0,
) {
    if (notes.isEmpty()) {
        return
    }
    // A continuous rail of accent dots (mirroring MilestonesTimeline) with each day's note as
    // an editable card to its right. Every note here is past-dated — today lives in the
    // composer above — so there is no past/today/future split and no Today divider.
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = TimelineDotInset, vertical = 16.dp),
        ) {
            notes.forEachIndexed { index, note ->
                NoteTimelineRow(
                    note = note,
                    isFirst = index == 0,
                    isLast = index == notes.lastIndex,
                    today = today,
                    controller = editorController,
                    onSave = onSave,
                    onDelete = onDelete,
                    saveFailureToken = saveFailureToken,
                )
            }
        }
    }
}

@Composable
private fun NoteTimelineRow(
    note: Note,
    isFirst: Boolean,
    isLast: Boolean,
    today: LocalDate,
    controller: NoteEditorController,
    onSave: (LocalDate, String) -> Unit,
    onDelete: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    saveFailureToken: Int = 0,
) {
    val dateLabel = noteTimelineDateLabel(note.date, today)
    val dateLabelStyle = MaterialTheme.typography.labelLarge
    var dateLabelHeightPx by remember(dateLabel) { mutableStateOf(0) }
    val density = LocalDensity.current
    val outgoingLineBottomInset = with(density) {
        val dateLabelHeight = if (dateLabelHeightPx > 0) {
            dateLabelHeightPx.toFloat()
        } else {
            dateLabelStyle.lineHeight.takeOrElse { dateLabelStyle.fontSize }.toPx()
        }
        val dotTopInDateLabel = (dateLabelHeight - TimelineNodeSize.toPx()) / 2f
        val insetPx = (TimelineNodeGap.toPx() - dotTopInDateLabel)
            .coerceAtLeast(0f)
            .toInt()
        insetPx.toDp()
    }
    val labelGap = dimensionResource(R.dimen.padding_small)
    val railColor = MaterialTheme.colorScheme.outlineVariant
    // ConstraintLayout so the dot is anchored to the date label (not the row), keeping it put
    // when the card below expands into the editor. The connector segments run from the row
    // edges to the dot, abutting the neighbouring rows into one continuous rail.
    ConstraintLayout(
        modifier = modifier
            .fillMaxWidth()
            .testTag("$NoteTimelineRowTestTagPrefix${note.id}"),
    ) {
        val (lineTop, lineBottom, dot, header, card) = createRefs()
        val contentStart = createGuidelineFromStart(TimelineRailWidth)

        Text(
            text = dateLabel,
            style = dateLabelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .onSizeChanged { dateLabelHeightPx = it.height }
                .constrainAs(header) {
                    top.linkTo(parent.top)
                    start.linkTo(contentStart)
                }
                .cjkTextOffset(dateLabel),
        )
        Box(
            modifier = Modifier
                .constrainAs(card) {
                    top.linkTo(header.bottom, margin = labelGap)
                    start.linkTo(contentStart)
                    end.linkTo(parent.end)
                    width = Dimension.fillToConstraints
                }
                // 8dp between this note's text field and the next row's date string; dropped on
                // the last row since nothing follows it.
                .padding(bottom = if (isLast) 0.dp else dimensionResource(R.dimen.padding_small)),
        ) {
            NoteEditorCard(
                text = note.text,
                identity = note.id,
                controller = controller,
                onSave = { onSave(note.date, it) },
                onDelete = { onDelete(note.date) },
                fieldModifier = Modifier.testTag("$NoteTimelineTextFieldTestTagPrefix${note.id}"),
                saveFailureToken = saveFailureToken,
            )
        }
        // Accent dot, vertically centred on the date label.
        Box(
            modifier = Modifier
                .constrainAs(dot) {
                    start.linkTo(parent.start)
                    top.linkTo(header.top)
                    bottom.linkTo(header.bottom)
                }
                .size(TimelineNodeSize)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .testTag("$NoteTimelineDotTestTagPrefix${note.id}"),
        )
        if (!isFirst) {
            Box(
                modifier = Modifier
                    .constrainAs(lineTop) {
                        top.linkTo(parent.top)
                        bottom.linkTo(dot.top, margin = TimelineNodeGap)
                        start.linkTo(dot.start)
                        end.linkTo(dot.end)
                        height = Dimension.fillToConstraints
                    }
                    .width(TimelineRailStroke)
                    .background(railColor, RoundedCornerShape(TimelineRailStroke / 2)),
            )
        }
        if (!isLast) {
            Box(
                modifier = Modifier
                    .constrainAs(lineBottom) {
                        top.linkTo(dot.bottom, margin = TimelineNodeGap)
                        bottom.linkTo(parent.bottom, margin = outgoingLineBottomInset)
                        start.linkTo(dot.start)
                        end.linkTo(dot.end)
                        height = Dimension.fillToConstraints
                    }
                    .width(TimelineRailStroke)
                    .background(railColor, RoundedCornerShape(TimelineRailStroke / 2))
                    .testTag("$NoteTimelineLineBottomTestTagPrefix${note.id}"),
            )
        }
    }
}

// A single past note on the All Notes screen: its own segmented list card, the date label as
// the card's title above the editable text field. Unlike [NotesTimeline] (the journal top page),
// the all-notes list drops the timeline rail and groups notes as plain segmented cards, mirroring
// the calibration screen's month sections. [index]/[count] drive the card's segmented corners.
@Composable
fun AllNotesNoteRow(
    note: Note,
    index: Int,
    count: Int,
    controller: NoteEditorController,
    onSave: (LocalDate, String) -> Unit,
    onDelete: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    saveFailureToken: Int = 0,
) {
    val dateLabel = noteDateLabelWithoutYear(note.date)
    EditorSegmentedListItem(
        index = index,
        count = count,
        modifier = modifier
            .fillMaxWidth()
            .testTag("$NoteTimelineRowTestTagPrefix${note.id}"),
    ) {
        NoteEditorCard(
            modifier = Modifier.padding(bottom = 6.dp),
            text = note.text,
            identity = note.id,
            controller = controller,
            onSave = { onSave(note.date, it) },
            onDelete = { onDelete(note.date) },
            fieldModifier = Modifier.testTag("$NoteTimelineTextFieldTestTagPrefix${note.id}"),
            saveFailureToken = saveFailureToken,
            header = {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // 6dp here plus the editor Column's 2dp gap matches the old rail's 8dp label gap.
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .cjkTextOffset(dateLabel),
                )
            },
        )
    }
}

// A filled accent dot for a note's day. Notes carry no medication palette, so every dot is
// the app's primary accent (unlike MilestoneNode, which colours by palette / future state).
@Composable
private fun NoteNode() {
    Box(
        modifier = Modifier
            .size(TimelineNodeSize)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
    )
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
private fun noteTimelineDateLabel(
    date: LocalDate,
    today: LocalDate,
): String {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) {
        medicationGroupScheduleDateFormatter(appLocale, today)
    }
    // Exact date with a short weekday, year shown only for past years (e.g. "Jun 15 Sun",
    // "Jun 15, 2025 Sun", "6月15日 周三"). No relative "Today"/"Yesterday" labels.
    return dateFormatter(date)
}

// Date + short weekday without the year (e.g. "Jun 15 Sun", "6月15日 周三"). Used by the All Notes
// list, where each row already sits under a month-and-year section header, so repeating the year
// per row is redundant. Reuse the schedule formatter with the note's own date as the reference so
// it never crosses into the "other year" branch.
@Composable
private fun noteDateLabelWithoutYear(date: LocalDate): String {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, date) {
        medicationGroupScheduleDateFormatter(appLocale, today = date)
    }
    return dateFormatter(date)
}

@Composable
fun EmptyAllNotesCard(
    modifier: Modifier = Modifier,
) {
    PreferenceSegmentedListItem(
        modifier = modifier,
        title = stringResource(R.string.journal_no_notes),
    )
}

@Composable
private fun AnchorRowUiState.dayCountLabel(): String {
    if (isOnToday()) return stringResource(R.string.journal_today)
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
        heroBackground = HeroBackground.Flag(PrideFlag.TRANSGENDER),
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

// Pins only the first anchor (estradiol) so the preview shows the pinned glyph in
// view mode and a mix of on/off pin toggles in edit mode.
private fun previewTimelineNodes() = previewAnchors().mapIndexed { index, anchor ->
    TimelineNodeUiState(anchor = anchor, isPinned = index == 0)
}

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

@Preview(name = "PinnedDatesCard", showBackground = true, widthDp = 420)
@Composable
private fun PinnedDatesCardPreview() {
    JournalComponentPreview {
        PinnedDatesCard(today = previewToday, anchors = previewAnchors(), onClick = {})
    }
}

@Preview(name = "JournalHeroCard", showBackground = true, widthDp = 420)
@Composable
private fun JournalHeroCardPreview() {
    JournalComponentPreview {
        JournalHeroCard(
            hero = previewAnchors().first(),
            today = previewToday,
            onClick = {},
        )
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

// todayDividerIndex = 2 places the Today marker between the past pins and the
// future "Surgery" node, exercising filled/today/hollow rail nodes in one frame.
@Preview(name = "MilestonesTimeline", showBackground = true, widthDp = 420)
@Composable
private fun MilestonesTimelinePreview() {
    JournalComponentPreview {
        MilestonesTimeline(
            nodes = previewTimelineNodes(),
            todayDividerIndex = 2,
            isEditMode = false,
            onSetPinned = { _, _ -> },
            onUpdateDate = {},
            today = previewToday,
        )
    }
}

@Preview(name = "MilestonesTimeline (edit)", showBackground = true, widthDp = 420)
@Composable
private fun MilestonesTimelineEditPreview() {
    JournalComponentPreview {
        MilestonesTimeline(
            nodes = previewTimelineNodes(),
            todayDividerIndex = 2,
            isEditMode = true,
            onSetPinned = { _, _ -> },
            onUpdateDate = {},
            today = previewToday,
        )
    }
}

@Preview(name = "Journal empty states", showBackground = true, widthDp = 420)
@Composable
private fun JournalEmptyStatesPreview() {
    JournalComponentPreview {
        EmptyMilestonesCard(onAddDate = {})
        EmptyPinnedMilestonesCard(onClick = {})
        EmptyAllNotesCard()
    }
}

@Preview(name = "EmptyMilestonesCard (welcome)", showBackground = true, widthDp = 420)
@Composable
private fun EmptyMilestonesWelcomeCardPreview() {
    JournalComponentPreview {
        EmptyMilestonesCard(onAddDate = {})
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
        )
        PinnedRow(
            anchor = anchors[1], index = 1, count = 2,
            isHero = false, isEditMode = false, isDragging = false,
            heroNextMilestone = null, today = previewToday, onUnpin = {},
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
        )
        PinnedRow(
            anchor = anchors[1], index = 1, count = 2,
            isHero = false, isEditMode = true, isDragging = false,
            heroNextMilestone = null, today = previewToday, onUnpin = {},
        )
    }
}
