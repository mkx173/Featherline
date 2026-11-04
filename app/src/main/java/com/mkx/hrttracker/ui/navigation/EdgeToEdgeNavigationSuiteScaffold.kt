package com.mkx.hrttracker.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.ui.components.LocalAppContentBottomInset
import com.mkx.hrttracker.ui.components.LocalAppContentStartInset
import com.mkx.hrttracker.ui.components.LocalTopAppBarBesideSideNavigation
import com.mkx.hrttracker.ui.components.hazeSourceArea
import dev.chrisbanes.haze.HazeState

// Every layout but the wide rail puts the navigation bar along the bottom edge.
internal val NavigationSuiteType.isBottomBar: Boolean
    get() = this != NavigationSuiteType.WideNavigationRailCollapsed

/**
 * The surface behind the app's navigation, and the side navigation of wide windows, which stays
 * beside every page as they push and pop: the classic [rail] beside [content], or the upright
 * [pill] floating over its start edge. Pages get the room the pill takes through
 * [LocalAppContentStartInset], and keep their top bar opaque beside either through
 * [LocalTopAppBarBesideSideNavigation]. The bottom bar is not drawn here: [HrtTrackerNavHost] draws
 * it inside the top-level pager's destination, so it rides its transitions and sub pages show none.
 * [content] gets the system navigation-bar inset through [LocalAppContentBottomInset].
 *
 * @param navigationChromeHazeState stable Haze state for chrome hosted above the routed content
 *   (sheets and dialogs). The source is attached above the routed content so their blur sees
 *   the composed NavHost frame. The [pill] blurs it from outside, the rail and [content] inside.
 * @param pillStartInset the room [pill] takes at [content]'s start edge.
 */
@Composable
fun EdgeToEdgeNavigationSuiteScaffold(
    navigationChromeHazeState: HazeState?,
    modifier: Modifier = Modifier,
    rail: (@Composable () -> Unit)? = null,
    pill: (@Composable () -> Unit)? = null,
    pillStartInset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val contentBottomInset = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    Surface(
        modifier = modifier,
        color = NavigationSuiteScaffoldDefaults.containerColor,
        contentColor = NavigationSuiteScaffoldDefaults.contentColor,
    ) {
        // The source area is attached in every layout: sheets and dialogs blur through this
        // same state, and Haze draws nothing at all (leaving their transparent containers
        // invisible) when the state has no source areas.
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().hazeSourceArea(navigationChromeHazeState)) {
                rail?.invoke()
                // One call however the navigation sits, so the pages keep their state as it moves.
                Box(Modifier.weight(1f)) {
                    CompositionLocalProvider(
                        LocalAppContentBottomInset provides contentBottomInset,
                        LocalAppContentStartInset provides if (pill == null) 0.dp else pillStartInset,
                        LocalTopAppBarBesideSideNavigation provides (rail != null || pill != null),
                    ) {
                        content()
                    }
                }
            }
            pill?.let { pill ->
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterStart) { pill() }
            }
        }
    }
}

private enum class BottomBarOverPageSlot { Bar, Page }

/**
 * [bar] along the bottom edge over [page], which pads for it through [LocalAppContentBottomInset]:
 * the bar is measured *before* the page is composed, so the page's inset is exact in every frame
 * it composes with, the first one and the one after the bar changes in place (fold, rotation,
 * window resize) included. With no bar, the page keeps the inset it already had. The height also
 * goes to [onBarHeightChange], for what sits outside.
 */
@Composable
internal fun BottomBarOverPage(
    bar: @Composable () -> Unit,
    onBarHeightChange: (Dp) -> Unit,
    page: @Composable () -> Unit,
) {
    val pageInset = LocalAppContentBottomInset.current
    SubcomposeLayout { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val barPlaceables = subcompose(BottomBarOverPageSlot.Bar) {
            Box(Modifier.onSizeChanged { size -> onBarHeightChange(size.height.toDp()) }) { bar() }
        }.map { measurable -> measurable.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val barHeight = barPlaceables.maxOf { placeable -> placeable.height }.toDp()
        val pagePlaceables = subcompose(BottomBarOverPageSlot.Page) {
            CompositionLocalProvider(LocalAppContentBottomInset provides maxOf(barHeight, pageInset)) {
                page()
            }
        }.map { measurable -> measurable.measure(Constraints.fixed(width, height)) }
        layout(width, height) {
            pagePlaceables.forEach { placeable -> placeable.placeRelative(0, 0) }
            barPlaceables.forEach { placeable ->
                placeable.placeRelative((width - placeable.width) / 2, height - placeable.height)
            }
        }
    }
}
