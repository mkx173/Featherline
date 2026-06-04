package com.mkx.hrttracker.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CurrentSegmentPositionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun ResolvedProbe(tag: String, explicit: SegmentPosition? = null) {
        val pos = currentSegmentPosition(explicit)
        Text("${pos.index}/${pos.count}", modifier = Modifier.testTag(tag))
    }

    @Test
    fun outsideSection_defaultsToStandaloneCard() {
        composeRule.setContent { ResolvedProbe("a") }
        composeRule.onNodeWithTag("a").assertTextEquals("0/1")
    }

    @Test
    fun insideSection_inheritsProvidedPosition() {
        composeRule.setContent {
            CompositionLocalProvider(LocalSegmentPosition provides SegmentPosition(1, 3)) {
                ResolvedProbe("a")
            }
        }
        composeRule.onNodeWithTag("a").assertTextEquals("1/3")
    }

    @Test
    fun explicitPositionOverridesTheLocal() {
        composeRule.setContent {
            CompositionLocalProvider(LocalSegmentPosition provides SegmentPosition(1, 3)) {
                ResolvedProbe("a", explicit = SegmentPosition(2, 5))
            }
        }
        composeRule.onNodeWithTag("a").assertTextEquals("2/5")
    }

    @Test
    fun editorRowsInsideSection_publishInheritedPositions() {
        composeRule.setContent {
            HrtSection(title = null) {
                item { EditorSegmentedListItem { Text("row-0") } }
                item { EditorSegmentedListItem(onClick = {}) { Text("row-1") } }
                item { EditorSegmentedListItem { Text("row-2") } }
            }
        }
        listOf(
            SegmentPosition(0, 3),
            SegmentPosition(1, 3),
            SegmentPosition(2, 3),
        ).forEach { expected ->
            composeRule
                .onNode(SemanticsMatcher.expectValue(SegmentPositionSemanticsKey, expected))
                .assertExists()
        }
    }
}
