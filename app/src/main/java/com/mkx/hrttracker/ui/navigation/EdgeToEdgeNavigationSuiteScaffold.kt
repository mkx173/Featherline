package com.mkx.hrttracker.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.util.fastFirst
import com.mkx.hrttracker.ui.components.hazeChrome
import com.mkx.hrttracker.ui.components.hazeNavigationSuiteColors
import com.mkx.hrttracker.ui.components.hazeSourceArea
import dev.chrisbanes.haze.HazeState

private const val NavigationLayoutId = "navigation"
private const val ContentLayoutId = "content"

/**
 * A [androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold] variant that
 * paints [content] full-height *behind* the bottom navigation bar instead of constraining it into
 * the space above the bar.
 *
 * The stock scaffold measures content at `height - navigationBarHeight` and stacks the bar below
 * it, so the content's bottom edge sits exactly at the bar's top. Painting content full-height lets
 * bottom-bar chrome float over the routed content instead.
 *
 * Body content must pad its scrollable region above the bar itself: the measured bar height is
 * reported through [onNavigationBarSizeChanged] so the caller can publish it via
 * [com.mkx.hrttracker.ui.components.LocalAppContentBottomInset].
 *
 * Only the bottom-bar layouts (compact / medium) draw content behind the bar; the wide rail keeps
 * the stock side-by-side placement, where content already fills the full height beside the rail.
 *
 * @param onNavigationBarSizeChanged invoked with the navigation component's measured height in
 *   pixels. In the wide-rail layout this is the rail's (full-screen) height; callers gate on
 *   [navigationSuiteType] and ignore it there.
 * @param navigationChromeHazeState stable Haze state used by bottom navigation chrome. The source
 *   is attached above the routed content so navigation transitions are captured after NavHost has
 *   composited outgoing and incoming destinations.
 */
@Composable
fun EdgeToEdgeNavigationSuiteScaffold(
    navigationSuiteType: NavigationSuiteType,
    navigationChromeHazeState: HazeState?,
    onNavigationBarSizeChanged: (heightPx: Int) -> Unit,
    modifier: Modifier = Modifier,
    navigationSuiteItems: NavigationSuiteScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val isBottomBar = navigationSuiteType != NavigationSuiteType.WideNavigationRailCollapsed
    Surface(
        modifier = modifier,
        color = NavigationSuiteScaffoldDefaults.containerColor,
        contentColor = NavigationSuiteScaffoldDefaults.contentColor,
    ) {
        Layout(
            content = {
                val navigationModifier = Modifier
                    .layoutId(NavigationLayoutId)
                    .onSizeChanged { onNavigationBarSizeChanged(it.height) }
                    .let {
                        if (isBottomBar) {
                            it.hazeChrome(navigationChromeHazeState)
                        } else {
                            it
                        }
                    }
                Box(
                    navigationModifier
                ) {
                    NavigationSuite(
                        layoutType = navigationSuiteType,
                        colors = hazeNavigationSuiteColors(),
                        content = navigationSuiteItems,
                    )
                }
                val contentModifier = Modifier
                    .layoutId(ContentLayoutId)
                    .let {
                        if (isBottomBar) {
                            it.hazeSourceArea(navigationChromeHazeState)
                        } else {
                            it
                        }
                    }
                Box(contentModifier) { content() }
            },
        ) { measurables, constraints ->
            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val navigationPlaceable =
                measurables.fastFirst { it.layoutId == NavigationLayoutId }.measure(looseConstraints)
            val layoutWidth = constraints.maxWidth
            val layoutHeight = constraints.maxHeight
            val contentPlaceable =
                measurables.fastFirst { it.layoutId == ContentLayoutId }.measure(
                    if (isBottomBar) {
                        // Full height: content paints behind the bar, which is overlaid below.
                        constraints.copy(minHeight = layoutHeight, maxHeight = layoutHeight)
                    } else {
                        // Wide rail: content fills the width beside the rail (stock behavior).
                        val contentWidth = layoutWidth - navigationPlaceable.width
                        constraints.copy(minWidth = contentWidth, maxWidth = contentWidth)
                    }
                )
            layout(layoutWidth, layoutHeight) {
                if (isBottomBar) {
                    contentPlaceable.placeRelative(0, 0)
                    navigationPlaceable.placeRelative(0, layoutHeight - navigationPlaceable.height)
                } else {
                    navigationPlaceable.placeRelative(0, 0)
                    contentPlaceable.placeRelative(navigationPlaceable.width, 0)
                }
            }
        }
    }
}
