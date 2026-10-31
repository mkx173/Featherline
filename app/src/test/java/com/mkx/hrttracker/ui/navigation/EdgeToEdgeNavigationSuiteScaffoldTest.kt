package com.mkx.hrttracker.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
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
 * [BottomBarOverPage] measures the bar *before* subcomposing the page, so the page's
 * [LocalAppContentBottomInset] is exact in every frame it ever composes with. An
 * onSizeChanged-into-state wiring would publish the inset without the bar on the first frame and a
 * stale one for a frame after the bar changed in place (fold, rotation), which matters now that
 * the activity handles those config changes without recreating.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EdgeToEdgeNavigationSuiteScaffoldTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var showBar by mutableStateOf(true)
    private val recordedInsets = mutableListOf<Dp>()

    private fun setPage() {
        composeRule.setContent {
            EdgeToEdgeNavigationSuiteScaffold(navigationChromeHazeState = null) {
                BottomBarOverPage(
                    bar = {
                        if (showBar) {
                            PillNavigationBar(
                                items = topLevelNavigationItems,
                                selectedScreen = Screen.Main,
                                onItemClick = { },
                                hazeState = null,
                            )
                        }
                    },
                    onBarHeightChange = { },
                ) {
                    recordedInsets += LocalAppContentBottomInset.current
                    Box(Modifier.size(48.dp))
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun pageNeverComposesWithAPlaceholderBarHeight() {
        setPage()

        assertTrue("page never composed", recordedInsets.isNotEmpty())
        assertTrue(
            "first composition must already see the measured bar height, got ${recordedInsets.first()}",
            recordedInsets.first() > 0.dp,
        )
        assertEquals(
            "inset must not change after the first composition: $recordedInsets",
            1,
            recordedInsets.toSet().size,
        )
    }

    @Test
    fun barRemovalNeverPublishesAStaleHeight() {
        setPage()
        val barHeight = recordedInsets.first()

        showBar = false
        composeRule.waitForIdle()
        showBar = true
        composeRule.waitForIdle()

        // Without the bar the page keeps the scaffold's gesture inset (0 under Robolectric);
        // with it, the measured bar height. Nothing in between.
        assertEquals(setOf(barHeight, 0.dp), recordedInsets.toSet())
        assertEquals(barHeight, recordedInsets.last())
    }

    @Test
    fun theBarIsComposedOnce() {
        setPage()

        // No measured-only copy beside the real bar: one tappable item per tab.
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(topLevelNavigationItems.size)
    }
}
