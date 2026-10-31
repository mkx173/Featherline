package com.mkx.hrttracker.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.mkx.hrttracker.ui.components.LocalAppContentBottomInset
import com.mkx.hrttracker.ui.components.hazeSourceArea
import dev.chrisbanes.haze.HazeState

// Every layout but the wide rail puts the navigation bar along the bottom edge.
internal val NavigationSuiteType.isBottomBar: Boolean
    get() = this != NavigationSuiteType.WideNavigationRailCollapsed

/**
 * The surface behind the app's navigation, which draws none of its own: [HrtTrackerNavHost] draws
 * the bar, or the rail, inside the top-level pager's destination, so it rides its transitions and
 * sub pages show none. [content] gets the system navigation-bar inset through
 * [LocalAppContentBottomInset].
 *
 * @param navigationChromeHazeState stable Haze state for chrome hosted above the routed content
 *   (sheets and dialogs). The source is attached above the routed content so their blur sees
 *   the composed NavHost frame.
 */
@Composable
fun EdgeToEdgeNavigationSuiteScaffold(
    navigationChromeHazeState: HazeState?,
    modifier: Modifier = Modifier,
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
        Box(Modifier.fillMaxSize().hazeSourceArea(navigationChromeHazeState)) {
            CompositionLocalProvider(LocalAppContentBottomInset provides contentBottomInset) {
                content()
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
