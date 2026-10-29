package com.mkx.hrttracker.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.AppContentMaxWidth
import com.mkx.hrttracker.ui.components.LocalHazeBlurEnabled
import com.mkx.hrttracker.ui.components.hazeChrome
import dev.chrisbanes.haze.HazeState

/**
 * The bottom navigation bar: a capsule floating above the gesture area, its items sharing the
 * width evenly, with a selection pill that slides to the selected item. The pill is as tall as
 * the items, so it grows with the font scale, and at most [PillAspectRatio] times as wide.
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
) {
    val selectedIndex = items.indexOfFirst { item -> item.screen == selectedScreen }.coerceAtLeast(0)
    val indicatorPosition by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "pillNavigationIndicator",
    )
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
        val pillWidth = minOf(itemWidth, size.height * PillAspectRatio)
        val center = (indicatorPosition + 0.5f) * itemWidth
        val left = (if (layoutDirection == LayoutDirection.Rtl) size.width - center else center) - pillWidth / 2
        pillPath.reset()
        pillPath.addRoundRect(
            RoundRect(left, 0f, left + pillWidth, size.height, CornerRadius(size.height / 2))
        )
        return pillPath
    }
    val pagePadding = dimensionResource(R.dimen.padding_medium)

    // Sized and inset like a page's body (AppContentContainer, padded by padding_medium), so the
    // bar lines up with the cards above it at any window width.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = AppContentMaxWidth)
                .fillMaxWidth()
                .padding(start = pagePadding, end = pagePadding, bottom = pagePadding)
                .height(IntrinsicSize.Min)
                .clip(CircleShape)
                .then(containerModifier)
                .padding(4.dp)
                .drawBehind { drawPath(pill(), indicatorColor) },
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
                    Modifier
                        .clip(CircleShape)
                        .selectable(selected = selected, onClick = onClick, role = Role.Tab)
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

// Width to height: the pill covers one item's icon and label, and stays this shape (if the item
// is wide enough) instead of stretching across a wide bar.
private const val PillAspectRatio = 1.5f
