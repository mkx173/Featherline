package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A touch outside a menu over the pages only closes it: a tap mustn't click what's beneath,
 * while a drag content takes, such as a swipe, still ends as its own, fling included.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ConsumeTapsClosingMenusTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val exemption = PagerSwipeExemption()
    private var clicks = 0
    private var stopVelocity = 0f

    private fun setTarget() {
        composeRule.setContent {
            Box(Modifier.size(300.dp).consumeTapsClosingMenus(exemption)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag("target")
                        .draggable(
                            state = rememberDraggableState { },
                            orientation = Orientation.Horizontal,
                            onDragStopped = { velocity -> stopVelocity = velocity },
                        )
                        .clickable { clicks++ },
                )
            }
        }
    }

    @Test
    fun tapClicks_whenNoMenuIsOpen() {
        setTarget()
        composeRule.onNodeWithTag("target").performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun tapOnlyClosesTheMenu_whenOneIsOpen() {
        exemption.openMenus = 1
        setTarget()
        composeRule.onNodeWithTag("target").performClick()
        composeRule.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun dragKeepsItsRelease_whenAMenuIsOpen() {
        exemption.openMenus = 1
        setTarget()
        composeRule.onNodeWithTag("target").performTouchInput { swipeLeft() }
        // A release taken from the drag would cancel it, with no fling.
        composeRule.runOnIdle { assertNotEquals(0f, stopVelocity) }
    }
}
