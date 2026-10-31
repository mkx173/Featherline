package com.mkx.hrttracker.ui.navigation

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.LocalHazeBlurEnabled
import com.mkx.hrttracker.ui.components.hazeChrome
import com.mkx.hrttracker.ui.components.redrawWhilePagesMove
import dev.chrisbanes.haze.HazeState
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
 * With [hazeState] and blur on, the capsule blurs the page scrolling beneath it; otherwise it is
 * an opaque container, so it never turns transparent with no blur behind it.
 */
@Composable
internal fun PillNavigationBar(
    items: List<NavigationItemContent>,
    selectedScreen: Screen,
    onItemClick: (Screen) -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    pagerState: PagerState? = null,
    pagerDragInteractions: InteractionSource? = null,
) {
    val selectedIndex = items.indexOfFirst { item -> item.screen == selectedScreen }.coerceAtLeast(0)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnItemClick by rememberUpdatedState(onItemClick)
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
    val layoutDirection = LocalLayoutDirection.current
    val indicatorColor = MaterialTheme.colorScheme.secondaryContainer
    val containerModifier = if (LocalHazeBlurEnabled.current && hazeState != null) {
        Modifier.hazeChrome(hazeState)
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
    }
    // Rebuilt at draw time from the animated position, so the slide only redraws.
    val pillPath = remember { Path() }
    fun DrawScope.pill(): Path {
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                    .union(WindowInsets(bottom = 8.dp))
            )
            .padding(horizontal = pagePadding),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                // Items as tall as their content (at least 56dp, growing with the font scale)
                // and PillAspectRatio times as wide, capped at the width left between the
                // side paddings.
                .layout { measurable, constraints ->
                    val inset = BarInset.roundToPx() * 2
                    val itemHeight = measurable.minIntrinsicHeight(constraints.maxWidth) - inset
                    val width = (items.size * itemHeight * PillAspectRatio + inset).roundToInt()
                        .coerceIn(constraints.minWidth, constraints.maxWidth)
                    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .height(IntrinsicSize.Min)
                .clip(CircleShape)
                .then(containerModifier)
                .padding(BarInset)
                .redrawWhilePagesMove(pagerState)
                .drawBehind { drawPath(pill(), indicatorColor) }
                // Touch drives the pill directly, taken in the Initial pass so the items'
                // selectable (kept for accessibility and keys) never sees it.
                .pointerInput(items.size, layoutDirection) {
                    fun positionAt(x: Float): Float {
                        val ltrX = if (layoutDirection == LayoutDirection.Rtl) size.width - x else x
                        return (ltrX / (size.width / items.size) - 0.5f).coerceIn(0f, items.lastIndex.toFloat())
                    }
                    fun follow(x: Float) {
                        scope.launch { indicator.animateTo(positionAt(x), FollowSpec) }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        down.consume()
                        following = false
                        // A touch picks the tab under it, so the pill goes to that tab, not
                        // to the finger; only a drag past the slop has it follow the finger.
                        val downSlot = positionAt(down.position.x).roundToInt().toFloat()
                        scope.launch { indicator.animateTo(downSlot, settleSpec) }
                        var lastX = down.position.x
                        var dragging = false
                        var released = false
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                .firstOrNull { it.id == down.id }
                            if (change == null || change.isConsumed) break
                            change.consume()
                            // The lift too: a quick drag can cross into the next tab with it.
                            lastX = change.position.x
                            if (!change.pressed) {
                                released = true
                                break
                            }
                            if (!dragging) dragging = abs(lastX - down.position.x) > viewConfiguration.touchSlop
                            if (dragging) follow(lastX)
                        }
                        // From the touch itself: the pill's animation may not have caught up.
                        val target = if (dragging) positionAt(lastX).roundToInt() else downSlot.toInt()
                        // Dragged from another tab back to the selected one: a switch called
                        // off, not a tap on the selected tab, which scrolls it to the top.
                        val cancelled = target == currentSelectedIndex && target != downSlot.toInt()
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        clipPath(pill(), ClipOp.Difference) { this@drawWithContent.drawContent() }
                    }
                    .selectableGroup(),
            ) {
                items.forEach { item ->
                    PillNavigationBarItem(
                        item = item,
                        filled = false,
                        selected = item.screen == selectedScreen,
                        onClick = { onItemClick(item.screen) },
                    )
                }
            }
            // The same items filled, shown only under the pill.
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .clearAndSetSemantics { }
                    .drawWithContent {
                        clipPath(pill()) { this@drawWithContent.drawContent() }
                    },
            ) {
                items.forEach { item ->
                    PillNavigationBarItem(item = item, filled = true)
                }
            }
        }
    }
}

// One item of either layer; only the outlined layer's items take [onClick].
@Composable
private fun RowScope.PillNavigationBarItem(
    item: NavigationItemContent,
    filled: Boolean,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val contentColor = if (filled) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
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
        Icon(
            painter = painterResource(if (filled) item.icon else item.iconAlt),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = stringResource(item.screen.label),
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Width to height of each item and so of the pill, kept unless the side paddings squeeze it.
private const val PillAspectRatio = 1.67f

// Between the capsule's edge and the items, all around.
private val BarInset = 4.dp

// Stiff enough that the pill reads as stuck to the finger, with a frame or two of give.
private val FollowSpec = spring<Float>(stiffness = Spring.StiffnessMedium)
