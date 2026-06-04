package com.mkx.hrttracker.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HrtSectionRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Reads the position the scaffold provided and renders it as "index/count". */
    @Composable
    private fun PositionProbe(tag: String) {
        val position = LocalSegmentPosition.current
        Text(
            text = position?.let { "${it.index}/${it.count}" } ?: "none",
            modifier = Modifier.testTag(tag),
        )
    }

    @Test
    fun assignsIndexAndCountFromDeclarationOrder() {
        composeRule.setContent {
            HrtSection(title = null) {
                item { PositionProbe("a") }
                item { PositionProbe("b") }
            }
        }
        composeRule.onNodeWithTag("a").assertTextEquals("0/2")
        composeRule.onNodeWithTag("b").assertTextEquals("1/2")
    }

    @Test
    fun conditionalRowNotRecorded_dropsFromCount() {
        val show = false
        composeRule.setContent {
            HrtSection(title = null) {
                item { PositionProbe("a") }
                if (show) item { PositionProbe("skip") }
                item { PositionProbe("b") }
            }
        }
        composeRule.onNodeWithTag("a").assertTextEquals("0/2")
        composeRule.onNodeWithTag("b").assertTextEquals("1/2")
        composeRule.onAllNodesWithTag("skip").assertCountEquals(0)
    }

    @Test
    fun animatedItem_countsOnlyWhenVisible() {
        composeRule.setContent {
            HrtSection(title = null) {
                item { PositionProbe("a") }
                animatedItem(visible = false) { PositionProbe("mid") }
                item { PositionProbe("b") }
            }
        }
        composeRule.onNodeWithTag("a").assertTextEquals("0/2")
        composeRule.onNodeWithTag("b").assertTextEquals("1/2")
    }

    @Test
    fun headerRendersTitle() {
        composeRule.setContent {
            HrtSection(title = "Display") {
                item { PositionProbe("a") }
            }
        }
        composeRule.onNodeWithText("DISPLAY").assertIsDisplayed()
    }

    @Test
    fun headerWithBaselineAlignedTrailing_rendersTitleAndTrailing() {
        // Smoke-covers the baseline-aligned trailing path (RowScope trailing +
        // Modifier.alignByBaseline()). Actual baseline alignment is verified
        // visually; this guards the code path against compile/runtime breakage.
        composeRule.setContent {
            HrtSection(
                title = "Regimen",
                headerTrailingAlignByBaseline = true,
                headerTrailing = {
                    Text(text = "SUMMARY", modifier = Modifier.alignByBaseline())
                },
            ) {
                item { PositionProbe("a") }
            }
        }
        composeRule.onNodeWithText("REGIMEN").assertIsDisplayed()
        composeRule.onNodeWithText("SUMMARY").assertIsDisplayed()
    }

    @Test
    fun rowOutsideSection_hasNoPosition() {
        composeRule.setContent { PositionProbe("solo") }
        composeRule.onNodeWithTag("solo").assertTextEquals("none")
    }

    @Test
    fun supportMessageAndDangerRows_inSection_inheritPositions() {
        composeRule.setContent {
            HrtSection(title = null) {
                item { SupportMessageListItem(text = "msg") }
                item { DangerZoneListItem(label = "danger", enabled = true, onClick = {}) }
            }
        }
        composeRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SegmentPositionSemanticsKey,
                    SegmentPosition(0, 2),
                )
            )
            .assertExists()
        composeRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SegmentPositionSemanticsKey,
                    SegmentPosition(1, 2),
                )
            )
            .assertExists()
    }
}
