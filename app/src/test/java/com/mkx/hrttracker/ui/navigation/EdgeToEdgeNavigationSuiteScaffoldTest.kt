package com.mkx.hrttracker.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
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
 * [LocalAppContentBottomInset] is exact in every frame content ever composes with —
 * the previous onSizeChanged-into-state wiring published 0.dp on the first frame and a
 * stale height for a frame after the navigation-suite type changed in place (fold,
 * rotation), which matters now that the activity handles those config changes without
 * recreating.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EdgeToEdgeNavigationSuiteScaffoldTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var suiteType by mutableStateOf(NavigationSuiteType.ShortNavigationBarCompact)
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
            ) {
                recordedInsets += LocalAppContentBottomInset.current
                Box(Modifier.size(48.dp))
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun contentNeverComposesWithAPlaceholderInset() {
        setScaffold()

        assertTrue("content never composed", recordedInsets.isNotEmpty())
        assertTrue(
            "first composition must already see the measured bar height, got " +
                    "${recordedInsets.first()}",
            recordedInsets.first() > 0.dp,
        )
        assertEquals(
            "inset must not change after the first composition: $recordedInsets",
            1,
            recordedInsets.toSet().size,
        )
    }

    @Test
    fun suiteTypeFlipNeverPublishesAStaleInset() {
        setScaffold()
        val barInset = recordedInsets.first()

        suiteType = NavigationSuiteType.WideNavigationRailCollapsed
        composeRule.waitForIdle()
        suiteType = NavigationSuiteType.ShortNavigationBarCompact
        composeRule.waitForIdle()

        // Rail mode publishes the raw gesture inset (0 under Robolectric); bottom-bar
        // mode publishes the measured bar height. Nothing in between, and never the
        // rail's full-screen measured height left over from the previous mode.
        assertEquals(setOf(barInset, 0.dp), recordedInsets.toSet())
        assertEquals(barInset, recordedInsets.last())
    }
}
