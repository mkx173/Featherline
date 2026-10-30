package com.mkx.hrttracker.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.ui.components.LocalAppContentBottomInset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The scaffold measures the navigation bar *before* subcomposing content, so
 * [LocalTopLevelBottomBarHeight] — the inset the top-level destinations pad for, under the bar
 * they draw themselves — is exact in every frame content ever composes with. The previous
 * onSizeChanged-into-state wiring published 0.dp on the first frame and a stale height for a
 * frame after the navigation-suite type changed in place (fold, rotation), which matters now that
 * the activity handles those config changes without recreating.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EdgeToEdgeNavigationSuiteScaffoldTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var suiteType by mutableStateOf(NavigationSuiteType.ShortNavigationBarCompact)
    private val recordedBarHeights = mutableListOf<Dp>()
    private val recordedInsets = mutableListOf<Dp>()

    private fun setScaffold() {
        composeRule.setContent {
            EdgeToEdgeNavigationSuiteScaffold(
                navigationSuiteType = suiteType,
                navigationChromeHazeState = null,
                navigationSuiteItems = {
                    item(
                        selected = true,
                        onClick = { },
                        icon = { Box(Modifier.size(24.dp)) },
                    )
                },
                bottomBar = {
                    PillNavigationBar(
                        items = topLevelNavigationItems,
                        selectedScreen = Screen.Main,
                        onItemClick = { },
                        hazeState = null,
                    )
                },
            ) {
                recordedBarHeights += LocalTopLevelBottomBarHeight.current
                recordedInsets += LocalAppContentBottomInset.current
                Box(Modifier.size(48.dp))
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun contentNeverComposesWithAPlaceholderBarHeight() {
        setScaffold()

        assertTrue("content never composed", recordedBarHeights.isNotEmpty())
        assertTrue(
            "first composition must already see the measured bar height, got " +
                    "${recordedBarHeights.first()}",
            recordedBarHeights.first() > 0.dp,
        )
        assertEquals(
            "bar height must not change after the first composition: $recordedBarHeights",
            1,
            recordedBarHeights.toSet().size,
        )
    }

    @Test
    fun suiteTypeFlipNeverPublishesAStaleBarHeight() {
        setScaffold()
        val barHeight = recordedBarHeights.first()

        suiteType = NavigationSuiteType.WideNavigationRailCollapsed
        composeRule.waitForIdle()
        suiteType = NavigationSuiteType.ShortNavigationBarCompact
        composeRule.waitForIdle()

        // Rail mode has no bottom bar; bottom-bar mode publishes the measured bar height.
        // Nothing in between, and never the rail's measured height left over.
        assertEquals(setOf(barHeight, 0.dp), recordedBarHeights.toSet())
        assertEquals(barHeight, recordedBarHeights.last())
        // Content itself only ever clears the raw gesture inset (0 under Robolectric).
        assertEquals(setOf(0.dp), recordedInsets.toSet())
    }

    @Test
    fun bottomBarLayoutsLeaveTheVisibleBarToTheDestinations() {
        setScaffold()

        // The measured-only copy must not reach accessibility as a second, tappable bar.
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)

        suiteType = NavigationSuiteType.WideNavigationRailCollapsed
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
    }
}
