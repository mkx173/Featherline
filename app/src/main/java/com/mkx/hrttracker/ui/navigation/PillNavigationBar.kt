package com.mkx.hrttracker.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.LocalHazeBlurEnabled
import com.mkx.hrttracker.ui.components.hazeChrome
import com.mkx.hrttracker.ui.components.redrawWhilePagesMove
import com.mkx.hrttracker.ui.theme.isAppInDarkTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import kotlin.math.sign
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The bottom navigation bar: a capsule floating above the gesture area, its items sharing the
 * width evenly, with a selection pill covering the selected item. Each item, and so the pill, is
 * [PillAspectRatio] times as wide as it is tall, unless the bar would then crowd the screen
 * edges closer than padding_medium; then the items narrow to fit. On touch down the pill
 * slides to the item under the finger, follows the finger once it drags, and on release
 * settles on the nearest item and selects it. While the user swipes [pagerState], as reported to
 * [pagerDragInteractions] (the pager's own interactions see no drags with pagerSwipe), the pill
 * tracks the pages.
 *
 * The items are drawn twice: outlined everywhere but under the pill, and filled only under it.
 * The pill is the mask between the two, so while it slides, every item it passes reads as
 * selected exactly where it covers it.
 *
 * [vertical] stands it upright for the side of a wide window: the items stacked, icons only,
 * [VerticalItemSize] square, with the tab names left to accessibility.
 *
 * With [hazeState] and blur on, the capsule blurs the page scrolling beneath it; otherwise it is
 * an opaque container, so it never turns transparent with no blur behind it.
 *
 * While [askBeforeSwitching] (an editor's unsaved changes, asked about before leaving), a touch
 * picks its tab at once, for the question to show on it, and the pill stays put: it moves only
 * once the selection does, after the user discards.
 */
@Composable
internal fun PillNavigationBar(
    items: List<NavigationItemContent>,
    selectedScreen: Screen,
    onItemClick: (Screen) -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    pagerState: PagerState? = null,
    pagerDragInteractions: InteractionSource? = null,
    askBeforeSwitching: Boolean = false,
) {
    val selectedIndex = items.indexOfFirst { item -> item.screen == selectedScreen }.coerceAtLeast(0)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnItemClick by rememberUpdatedState(onItemClick)
    val currentAskBeforeSwitching by rememberUpdatedState(askBeforeSwitching)
    val settleSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val indicator = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) { indicator.animateTo(selectedIndex.toFloat(), settleSpec) }
    // From a swipe's start until the pages settle or the pill is touched; a scroll the pill
    // started keeps the pill's own settle instead.
    var following by remember { mutableStateOf(false) }
    if (pagerState != null && pagerDragInteractions != null) {
        val pagerDragged by pagerDragInteractions.collectIsDraggedAsState()
        LaunchedEffect(pagerState) {
            snapshotFlow {
                Triple(
                    pagerState.currentPage + pagerState.currentPageOffsetFraction,
                    pagerDragged,
                    pagerState.isScrollInProgress,
                )
            }.collect { (position, dragged, scrolling) ->
                if (dragged) following = true
                if (!scrolling) following = false
                if (following) indicator.snapTo(position)
            }
        }
    }
    val scope = rememberCoroutineScope()
    // The finger's travel along the items since a drag on the pill began; nudges the pill
    // toward it.
    val nudge = remember { Animatable(0f) }
    val layoutDirection = LocalLayoutDirection.current
    // After Kyant0/AndroidLiquidGlass's bottom tabs: the theme background tinted at 40% over
    // the blur and a faint neutral indicator. In dark the background all but vanished into the
    // page, and the shadow can't show there, so it takes surfaceContainer instead.
    val dark = isAppInDarkTheme()
    val containerColor = if (dark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background
    val indicatorColor = if (dark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
    val blurStyle = remember(containerColor) {
        HazeBlurStyle {
            backgroundColor(containerColor)
            colorEffects(listOf(HazeColorEffect.tint(containerColor.copy(alpha = 0.4f))))
        }
    }
    // The opaque container stays under the blur: Haze draws nothing on an effect's first frame
    // (its source starts capturing only once the effect asks), so turning blur on left the
    // capsule empty for a frame. The blur samples the opaque page, so it covers this fully.
    val containerModifier = Modifier
        .background(containerColor)
        .then(if (LocalHazeBlurEnabled.current && hazeState != null) Modifier.hazeChrome(hazeState, style = blurStyle) else Modifier)
    // Rebuilt at draw time from the animated position, so the slide only redraws.
    val pillPath = remember { Path() }
    fun DrawScope.pill(): Path {
        if (vertical) {
            val itemHeight = size.height / items.size
            val top = indicator.value * itemHeight
            pillPath.reset()
            pillPath.addRoundRect(RoundRect(0f, top, size.width, top + itemHeight, CornerRadius(size.width / 2)))
            return pillPath
        }
        val itemWidth = size.width / items.size
        val center = (indicator.value + 0.5f) * itemWidth
        val left = (if (layoutDirection == LayoutDirection.Rtl) size.width - center else center) - itemWidth / 2
        pillPath.reset()
        pillPath.addRoundRect(
            RoundRect(left, 0f, left + itemWidth, size.height, CornerRadius(size.height / 2))
        )
        return pillPath
    }
    val pagePadding = dimensionResource(R.dimen.padding_medium)

    // Sits right on the gesture area or the 3-button bar; without either it keeps 8dp off the
    // screen edge (union takes the larger of the two, not their sum).
    // Upright, it keeps pagePadding off the window's start edge instead, past a cutout there.
    val edgeModifier = if (vertical) {
        Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
            .padding(start = pagePadding)
    } else {
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    .union(WindowInsets(bottom = 8.dp))
            )
            .padding(horizontal = pagePadding)
    }
    // Items as tall as their content (at least 56dp, growing with the font scale) and
    // PillAspectRatio times as wide, capped at the width left between the side paddings;
    // upright, the items size themselves.
    val sizeModifier = if (vertical) {
        Modifier
    } else {
        Modifier
            .layout { measurable, constraints ->
                val inset = BarInset.roundToPx() * 2
                val itemHeight = measurable.minIntrinsicHeight(constraints.maxWidth) - inset
                val width = (items.size * itemHeight * PillAspectRatio + inset).roundToInt()
                    .coerceIn(constraints.minWidth, constraints.maxWidth)
                val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .height(IntrinsicSize.Min)
    }
    Box(
        modifier = modifier.then(edgeModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .then(sizeModifier)
                // As in Kyant0/AndroidLiquidGlass's bottom tabs: a drag nudges the whole pill up
                // to MaxNudge its way, easing out over the pill's length, and it springs back on
                // release.
                .graphicsLayer {
                    if (vertical) {
                        translationY = nudgeOffset(nudge.value, size.height)
                    } else {
                        translationX = nudgeOffset(nudge.value, size.width)
                    }
                }
                // Lifts the capsule off the content behind it; outside the clip so it shows.
                .dropShadow(CircleShape, PillShadow)
                .clip(CircleShape)
                .then(containerModifier)
                .padding(BarInset)
                .redrawWhilePagesMove(pagerState)
                .drawBehind { drawPath(pill(), indicatorColor) }
                // Touch drives the pill directly, taken in the Initial pass so the items'
                // selectable (kept for accessibility and keys) never sees it.
                .pointerInput(items.size, layoutDirection, vertical) {
                    // The touch's coordinate along the items.
                    fun Offset.along(): Float = if (vertical) y else x
                    fun positionAt(x: Float): Float {
                        if (vertical) {
                            return (x / (size.height / items.size) - 0.5f).coerceIn(0f, items.lastIndex.toFloat())
                        }
                        val ltrX = if (layoutDirection == LayoutDirection.Rtl) size.width - x else x
                        return (ltrX / (size.width / items.size) - 0.5f).coerceIn(0f, items.lastIndex.toFloat())
                    }
                    fun follow(x: Float) {
                        scope.launch { indicator.animateTo(positionAt(x), FollowSpec) }
                    }
                    // The nudge's graphicsLayer sits above this, so positions arrive shifted
                    // by it; adding it back keeps the drag from feeding on its own nudge.
                    val layerLength = (if (vertical) size.height else size.width) + BarInset.toPx() * 2
                    fun unNudged(x: Float) = x + nudgeOffset(nudge.value, layerLength)
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        down.consume()
                        following = false
                        val downX = unNudged(down.position.along())
                        // A touch picks the tab under it, so the pill goes to that tab, not
                        // to the finger; only a drag past the slop has it follow the finger.
                        val downSlot = positionAt(downX).roundToInt().toFloat()
                        if (currentAskBeforeSwitching) {
                            // Asked on the touch, the pill held where it is; the rest of the
                            // gesture, now under the question, does nothing.
                            currentOnItemClick(items[downSlot.toInt()].screen)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { change -> change.consume() }
                            } while (event.changes.any { change -> change.pressed })
                            return@awaitEachGesture
                        }
                        scope.launch { indicator.animateTo(downSlot, settleSpec) }
                        var lastX = downX
                        var dragging = false
                        var dragStartX = 0f
                        var released = false
                        try {
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                    .firstOrNull { it.id == down.id }
                                if (change == null || change.isConsumed) break
                                change.consume()
                                // The lift too: a quick drag can cross into the next tab with it.
                                lastX = unNudged(change.position.along())
                                if (!change.pressed) {
                                    released = true
                                    break
                                }
                                if (!dragging && abs(lastX - downX) > viewConfiguration.touchSlop) {
                                    dragging = true
                                    dragStartX = lastX
                                }
                                if (dragging) {
                                    follow(lastX)
                                    val travel = lastX - dragStartX
                                    scope.launch { nudge.snapTo(travel) }
                                }
                            }
                        } finally {
                            // Also when the gesture is cancelled mid-drag (pointerInput restart).
                            scope.launch { nudge.animateTo(0f, NudgeReleaseSpec) }
                        }
                        // From the touch itself: the pill's animation may not have caught up.
                        val target = if (dragging) positionAt(lastX).roundToInt() else downSlot.toInt()
                        // Dragged back to the selected tab, wherever the drag began: a switch
                        // called off, not a tap on the selected tab, which scrolls it to the top.
                        val cancelled = dragging && target == currentSelectedIndex
                        if (released && !cancelled) {
                            scope.launch {
                                indicator.animateTo(target.toFloat(), settleSpec)
                                // Back to the real selection if the click did not change it.
                                indicator.animateTo(currentSelectedIndex.toFloat(), settleSpec)
                            }
                            currentOnItemClick(items[target].screen)
                        } else {
                            scope.launch { indicator.animateTo(currentSelectedIndex.toFloat(), settleSpec) }
                        }
                    }
                },
        ) {
            // The tabs themselves, with their selection and clicks; outlined, outside the pill.
            PillNavigationBarItems(
                vertical = vertical,
                modifier = Modifier
                    .then(if (vertical) Modifier else Modifier.fillMaxWidth())
                    .drawWithContent {
                        clipPath(pill(), ClipOp.Difference) { this@drawWithContent.drawContent() }
                    }
                    .selectableGroup(),
            ) { itemModifier ->
                items.forEach { item ->
                    PillNavigationBarItem(
                        item = item,
                        filled = false,
                        showLabel = !vertical,
                        modifier = itemModifier,
                        selected = item.screen == selectedScreen,
                        onClick = { onItemClick(item.screen) },
                    )
                }
            }
            // The same items filled, shown only under the pill.
            PillNavigationBarItems(
                vertical = vertical,
                modifier = Modifier
                    .matchParentSize()
                    .clearAndSetSemantics { }
                    .drawWithContent {
                        clipPath(pill()) { this@drawWithContent.drawContent() }
                    },
            ) { itemModifier ->
                items.forEach { item ->
                    PillNavigationBarItem(item = item, filled = true, showLabel = !vertical, modifier = itemModifier)
                }
            }
        }
    }
}

// One layer's items in a row, or stacked when [vertical], each laid out by the modifier
// [content] is handed.
@Composable
private fun PillNavigationBarItems(
    vertical: Boolean,
    modifier: Modifier,
    content: @Composable (itemModifier: Modifier) -> Unit,
) {
    if (vertical) {
        Column(modifier) { content(Modifier.size(VerticalItemSize)) }
    } else {
        Row(modifier) { content(Modifier.weight(1f).fillMaxHeight()) }
    }
}

// One item of either layer; only the outlined layer's items take [onClick]. Without its
// label, the item names its tab through its icon.
@Composable
private fun PillNavigationBarItem(
    item: NavigationItemContent,
    filled: Boolean,
    showLabel: Boolean,
    modifier: Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val contentColor = if (filled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.selectable(
                        selected = selected,
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                }
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val label = stringResource(item.screen.label)
        Icon(
            painter = painterResource(if (filled) item.icon else item.iconAlt),
            contentDescription = if (showLabel) null else label,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
        if (showLabel) {
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Width to height of each item and so of the pill, kept unless the side paddings squeeze it.
private const val PillAspectRatio = 1.67f

// Each item of the upright pill, square.
private val VerticalItemSize = 56.dp

// Between the capsule's edge and the items, all around.
private val BarInset = 4.dp

// The upright pill's width, edge to edge.
internal val VerticalPillNavigationBarWidth = VerticalItemSize + BarInset * 2

// Stiff enough that the pill reads as stuck to the finger, with a frame or two of give.
private val FollowSpec = spring<Float>(stiffness = Spring.StiffnessMedium)

// How far a drag nudges the pill, and how it settles back: the reference's values.
private val MaxNudge = 4.dp
private val NudgeReleaseSpec = spring(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = 0.5f)

// The soft shadow under the reference's bars: 24dp blur, 4dp down, black at 10%.
private val PillShadow = Shadow(radius = 24.dp, color = Color.Black.copy(alpha = 0.1f), offset = DpOffset(0.dp, 4.dp))

// The nudge's translation for a drag [travel] across a pill [width] wide, easing out to MaxNudge.
private fun Density.nudgeOffset(travel: Float, width: Float): Float {
    val fraction = (travel / width).coerceIn(-1f, 1f)
    return MaxNudge.toPx() * sign(fraction) * EaseOut.transform(abs(fraction))
}
