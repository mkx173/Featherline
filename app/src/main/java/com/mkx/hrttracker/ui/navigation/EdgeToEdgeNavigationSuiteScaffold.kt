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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import com.mkx.hrttracker.ui.components.LocalAppContentBottomInset

private enum class EdgeToEdgeScaffoldSlot { Navigation, Content }

/**
 * A [androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold] variant that
 * paints [content] full-height *behind* the bottom navigation bar instead of constraining it into
 * the space above the bar.
 *
 * The stock scaffold measures content at `height - navigationBarHeight` and stacks the bar below
 * it, so the content's bottom edge sits exactly at the bar's top. During the top-level fade-through
 * transition the incoming page scales in (see [topLevelEnterTransition]), lifting that bottom edge
 * upward and briefly revealing the scaffold's container color as a white strip above the bar.
 * Painting content full-height keeps the scaled page's bottom edge hidden beneath the bar, so no
 * strip appears.
 *
 * Body content must pad its scrollable region above the bar itself; the scaffold provides the
 * required padding via [LocalAppContentBottomInset]. The bar is measured *before* content is
 * subcomposed, so the inset is exact in the same frame — no first-frame underlap and no stale
 * value when the navigation-suite type changes in place (fold, rotation, window resize).
 *
 * Only the bottom-bar layouts (compact / medium) draw content behind the bar; the wide rail keeps
 * the stock side-by-side placement, where content already fills the full height beside the rail
 * and only needs to clear the system gesture inset.
 */
@Composable
fun EdgeToEdgeNavigationSuiteScaffold(
    navigationSuiteType: NavigationSuiteType,
    modifier: Modifier = Modifier,
    navigationSuiteItems: NavigationSuiteScope.() -> Unit,
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
                Box {
                    NavigationSuite(
                        layoutType = navigationSuiteType,
                        content = navigationSuiteItems,
                    )
                }
            }.first().measure(looseConstraints)
            val layoutWidth = constraints.maxWidth
            val layoutHeight = constraints.maxHeight
            val appContentBottomInset =
                if (isBottomBar) navigationPlaceable.height.toDp() else railContentBottomInset
            val contentPlaceable = subcompose(EdgeToEdgeScaffoldSlot.Content) {
                Box {
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
