package com.mkx.hrttracker.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import com.mkx.hrttracker.ui.components.LocalAppContentBottomInset
import com.mkx.hrttracker.ui.components.hazeChrome
import com.mkx.hrttracker.ui.components.hazeNavigationSuiteColors
import com.mkx.hrttracker.ui.components.hazeSourceArea
import dev.chrisbanes.haze.HazeState

private enum class EdgeToEdgeScaffoldSlot { Navigation, Content }

/**
 * A [androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold] variant that
 * paints [content] full-height *behind* the bottom navigation bar instead of constraining it into
 * the space above the bar.
 *
 * The stock scaffold measures content at `height - navigationBarHeight` and stacks the bar below
 * it, so the content's bottom edge sits exactly at the bar's top. Painting content full-height lets
 * bottom-bar chrome float over the routed content instead.
 *
 * Body content must pad its scrollable region above the bar itself; the scaffold provides the
 * required padding via [LocalAppContentBottomInset]. The bar is measured *before* content is
 * subcomposed, so the inset is exact in the same frame — no first-frame underlap and no stale
 * value when the navigation-suite type changes in place (fold, rotation, window resize).
 *
 * Only the bottom-bar layouts (compact / medium) draw content behind the bar; the wide rail keeps
 * the stock side-by-side placement, where content already fills the full height beside the rail
 * and only needs to clear the system gesture inset.
 *
 * @param navigationChromeHazeState stable Haze state used by bottom navigation chrome. The source
 *   is attached above the routed content so navigation transitions are captured after NavHost has
 *   composited outgoing and incoming destinations.
 */
@Composable
fun EdgeToEdgeNavigationSuiteScaffold(
    navigationSuiteType: NavigationSuiteType,
    navigationChromeHazeState: HazeState?,
    modifier: Modifier = Modifier,
    navigationSuiteItems: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val isBottomBar = navigationSuiteType != NavigationSuiteType.WideNavigationRailCollapsed
    val railContentBottomInset = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    Surface(
        modifier = modifier,
        color = NavigationSuiteScaffoldDefaults.containerColor,
        contentColor = NavigationSuiteScaffoldDefaults.contentColor,
    ) {
        SubcomposeLayout { constraints ->
            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val navigationPlaceable = subcompose(EdgeToEdgeScaffoldSlot.Navigation) {
                val navigationModifier = if (isBottomBar) {
                    Modifier.hazeChrome(navigationChromeHazeState)
                } else {
                    Modifier
                }
                Box(navigationModifier) {
                    NavigationSuite(
                        navigationSuiteType = navigationSuiteType,
                        colors = hazeNavigationSuiteColors(navigationChromeHazeState),
                        verticalArrangement = navigationSuiteVerticalArrangement(isBottomBar),
                        content = navigationSuiteItems,
                    )
                }
            }.first().measure(looseConstraints)
            val layoutWidth = constraints.maxWidth
            val layoutHeight = constraints.maxHeight
            val appContentBottomInset =
                if (isBottomBar) navigationPlaceable.height.toDp() else railContentBottomInset
            val contentPlaceable = subcompose(EdgeToEdgeScaffoldSlot.Content) {
                // The source area is attached in every layout, not just behind the
                // bottom bar: sheets and dialogs blur through this same state, and
                // Haze draws nothing at all (leaving their transparent containers
                // invisible) when the state has no source areas — as the wide rail
                // layout otherwise would.
                Box(Modifier.hazeSourceArea(navigationChromeHazeState)) {
                    CompositionLocalProvider(
                        LocalAppContentBottomInset provides appContentBottomInset
                    ) {
                        content()
                    }
                }
            }.first().measure(
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

/**
 * Keep the bottom navigation's stock ordering, but center the items when the
 * adaptive suite becomes a side rail on wide/foldable windows.
 */
internal fun navigationSuiteVerticalArrangement(isBottomBar: Boolean): Arrangement.Vertical =
    if (isBottomBar) Arrangement.Top else Arrangement.Center
