package com.mkx.hrttracker.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.ui.components.LocalAppContentBottomInset
import com.mkx.hrttracker.ui.components.hazeNavigationSuiteColors
import com.mkx.hrttracker.ui.components.hazeSourceArea
import dev.chrisbanes.haze.HazeState

private enum class EdgeToEdgeScaffoldSlot { Navigation, Content }

// Every layout but the wide rail puts the navigation bar along the bottom edge.
internal val NavigationSuiteType.isBottomBar: Boolean
    get() = this != NavigationSuiteType.WideNavigationRailCollapsed

/**
 * Height of the bottom navigation bar a top-level destination draws over its content, for that
 * content's bottom inset. `0.dp` beside the wide rail.
 */
internal val LocalTopLevelBottomBarHeight = compositionLocalOf { 0.dp }

/**
 * A [androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold] variant that
 * draws only the wide navigation rail. In the bottom-bar layouts [HrtTrackerNavHost] draws the bar
 * inside each top-level destination, so the bar rides that page's transitions and sub pages show
 * none.
 *
 * The scaffold still measures a copy of that bar, [bottomBar] (never placed, no semantics),
 * *before* content is subcomposed and provides its height via [LocalTopLevelBottomBarHeight], so
 * the top-level pages' inset is exact in the same frame — no first-frame underlap and no stale
 * value when the navigation-suite type changes in place (fold, rotation, window resize).
 * [content] itself gets the system navigation-bar inset through [LocalAppContentBottomInset].
 *
 * @param navigationChromeHazeState stable Haze state for chrome hosted above the routed content
 *   (sheets and dialogs). The source is attached above the routed content so their blur sees
 *   the composed NavHost frame.
 */
@Composable
fun EdgeToEdgeNavigationSuiteScaffold(
    navigationSuiteType: NavigationSuiteType,
    navigationChromeHazeState: HazeState?,
    modifier: Modifier = Modifier,
    navigationSuiteItems: NavigationSuiteScope.() -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val isBottomBar = navigationSuiteType.isBottomBar
    val contentBottomInset = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    Surface(
        modifier = modifier,
        color = NavigationSuiteScaffoldDefaults.containerColor,
        contentColor = NavigationSuiteScaffoldDefaults.contentColor,
    ) {
        SubcomposeLayout { constraints ->
            val navigationPlaceable = subcompose(EdgeToEdgeScaffoldSlot.Navigation) {
                if (isBottomBar) {
                    // Only measured: the destinations draw the real bar, and its items must
                    // not show up twice to accessibility.
                    Box(Modifier.clearAndSetSemantics { }) {
                        bottomBar()
                    }
                } else {
                    NavigationSuite(
                        layoutType = navigationSuiteType,
                        colors = hazeNavigationSuiteColors(navigationChromeHazeState),
                        content = navigationSuiteItems,
                    )
                }
            }.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
            val layoutWidth = constraints.maxWidth
            val layoutHeight = constraints.maxHeight
            val railWidth = if (isBottomBar) 0 else navigationPlaceable.width
            val bottomBarHeight = if (isBottomBar) navigationPlaceable.height.toDp() else 0.dp
            val contentPlaceable = subcompose(EdgeToEdgeScaffoldSlot.Content) {
                // The source area is attached in every layout: sheets and dialogs blur
                // through this same state, and Haze draws nothing at all (leaving their
                // transparent containers invisible) when the state has no source areas.
                Box(Modifier.hazeSourceArea(navigationChromeHazeState)) {
                    CompositionLocalProvider(
                        LocalAppContentBottomInset provides contentBottomInset,
                        LocalTopLevelBottomBarHeight provides bottomBarHeight,
                    ) {
                        content()
                    }
                }
            }.first().measure(Constraints.fixed(layoutWidth - railWidth, layoutHeight))
            layout(layoutWidth, layoutHeight) {
                if (!isBottomBar) navigationPlaceable.placeRelative(0, 0)
                contentPlaceable.placeRelative(railWidth, 0)
            }
        }
    }
}
