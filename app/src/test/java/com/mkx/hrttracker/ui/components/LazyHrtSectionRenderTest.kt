package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
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
class LazyHrtSectionRenderTest {
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
            LazyColumn {
                hrtSection(key = "s") {
                    item("a") { PositionProbe("a") }
                    item("b") { PositionProbe("b") }
                    item("c") { PositionProbe("c") }
                }
            }
        }
        composeRule.onNodeWithTag("a").assertTextEquals("0/3")
        composeRule.onNodeWithTag("b").assertTextEquals("1/3")
        composeRule.onNodeWithTag("c").assertTextEquals("2/3")
    }

    @Test
    fun headerRendersTitle() {
        composeRule.setContent {
            LazyColumn {
                hrtSection(
                    key = "s",
                    header = { HrtSectionHeader(text = "Regimen") },
                ) {
                    item("a") { PositionProbe("a") }
                }
            }
        }
        composeRule.onNodeWithText("REGIMEN").assertIsDisplayed()
    }

    @Test
    fun rowOutsideSection_hasNoPosition() {
        composeRule.setContent {
            LazyColumn { item { PositionProbe("solo") } }
        }
        composeRule.onNodeWithTag("solo").assertTextEquals("none")
    }
}
