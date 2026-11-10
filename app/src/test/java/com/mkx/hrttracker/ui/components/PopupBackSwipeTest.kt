package com.mkx.hrttracker.ui.components

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

/**
 * A back swipe starts with a touch at a side of the screen, outside an open menu or tooltip. Closed
 * on that touch down, the popup's window goes mid-swipe and the swipe's Back goes astray: on
 * Android 14 a menu's reaches the window beneath, which navigates, and a tooltip's is lost. The
 * popup stays open for the swipe's Back to close instead, as the Back key does, while a touch
 * outside it anywhere else still closes it. The screen's sides are the activity's full-screen
 * window's: a floating dialog's window doesn't reach them, and one fitting its content to the
 * system bars, as the date picker's, passes its content no insets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PopupBackSwipeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var expanded by mutableStateOf(true)
    private val tooltipState = TooltipState(initialIsVisible = true, isPersistent = true)

    // The menu's host view, the popup's window root, and the activity's full-screen window root.
    private lateinit var host: View
    private lateinit var popup: View
    private val screen: View get() = composeRule.activity.window.decorView

    private val pages = PagerSwipeExemption()

    private fun setMenu(overPages: Boolean, inFloatingDialog: Boolean = false) {
        val exemption = if (overPages) pages else null
        composeRule.setContent {
            CompositionLocalProvider(LocalPagerSwipeExemption provides exemption) {
                if (inFloatingDialog) {
                    // At the platform's default width, as the widget appearance dialog.
                    Dialog(onDismissRequest = {}) { Menu() }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Menu() }
                }
            }
        }
        composeRule.onNodeWithText("Immediately").assertIsDisplayed()
    }

    @Composable
    private fun Menu() {
        val view = LocalView.current
        SideEffect { host = view }
        Box {
            Text("Dark mode")
            HrtDropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
                PopupText("Immediately")
            }
        }
    }

    // In a dialog fitting its content to the system bars, as the date picker's arrows' tooltips.
    private fun setTooltip() {
        composeRule.setContent {
            Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HrtTooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Below,
                        ),
                        tooltip = { RichTooltip { PopupText("Rose") } },
                        state = tooltipState,
                    ) {
                        Text("Next month")
                    }
                }
            }
        }
        composeRule.onNodeWithText("Rose").assertIsDisplayed()
    }

    @Composable
    private fun PopupText(text: String) {
        val view = LocalView.current
        SideEffect { popup = view.rootView }
        Text(text)
    }

    // A touch below the popup, [x] across the screen, told to the popup's window as the system
    // tells it: in the popup's own coordinates, and the screen's raw ones.
    private fun touch(action: Int, x: Float, y: Float = 10f) {
        composeRule.runOnIdle {
            // Gesture navigation: a back swipe starts within 30 px of either side. Robolectric's
            // windows have no system gesture insets, so the activity's full-screen window gets
            // them as the system gives them; a window away from the sides has none.
            ReflectionHelpers.setField(
                screen.parent,
                "mLastWindowInsets",
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.systemGestures(), Insets.of(30, 0, 30, 0))
                    .build()
                    .toWindowInsets(),
            )
            val screenAt = IntArray(2).also(screen::getLocationOnScreen)
            val popupAt = IntArray(2).also(popup::getLocationOnScreen)
            val event = MotionEvent.obtain(0L, 0L, action, screenAt[0] + x, popupAt[1] + popup.height + y, 0)
            event.offsetLocation(-popupAt[0].toFloat(), -popupAt[1].toFloat())
            popup.dispatchTouchEvent(event)
            event.recycle()
        }
        composeRule.waitForIdle()
    }

    private fun pressBackOnPopup() {
        composeRule.runOnIdle {
            popup.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            popup.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
        composeRule.waitForIdle()
    }

    // Over the top-level pages, as Settings' menus: the touch goes to the pages beneath, and the
    // menu is only told of it.
    @Test
    fun backSwipeFromTheLeft_leavesAMenuOverThePagesOpen_forItsBackToClose() {
        setMenu(overPages = true)
        touch(MotionEvent.ACTION_OUTSIDE, x = 5f)
        composeRule.onNodeWithText("Immediately").assertIsDisplayed()

        pressBackOnPopup()
        composeRule.onNodeWithText("Immediately").assertDoesNotExist()
    }

    // Elsewhere the menu keeps all touch, until the system takes it for the swipe.
    @Test
    fun backSwipeFromTheRight_leavesAMenuElsewhereOpen_forItsBackToClose() {
        setMenu(overPages = false)
        touch(MotionEvent.ACTION_DOWN, x = screen.width - 5f)
        touch(MotionEvent.ACTION_CANCEL, x = screen.width - 5f)
        composeRule.onNodeWithText("Immediately").assertIsDisplayed()

        pressBackOnPopup()
        composeRule.onNodeWithText("Immediately").assertDoesNotExist()
    }

    // The widget appearance dialog's dark mode menu, opened from Settings. Robolectric lays the
    // dialog's window out at the screen's left, wrapping its content: the right side is beyond it.
    @Test
    fun backSwipeFromTheRight_leavesAMenuInAFloatingDialogOpen_forItsBackToClose() {
        setMenu(overPages = true, inFloatingDialog = true)
        composeRule.runOnIdle {
            val dialogAt = IntArray(2).also(host.rootView::getLocationOnScreen)
            assertTrue("The dialog's window reaches the right side", dialogAt[0] + host.rootView.width < screen.width - 30)
        }
        touch(MotionEvent.ACTION_OUTSIDE, x = screen.width - 5f)
        composeRule.onNodeWithText("Immediately").assertIsDisplayed()

        pressBackOnPopup()
        composeRule.onNodeWithText("Immediately").assertDoesNotExist()
    }

    // F4: a tap there isn't a swipe, so it closes the menu, as anywhere outside it.
    @Test
    fun tapOnTheRightEdge_closesAMenuElsewhere() {
        setMenu(overPages = false)
        touch(MotionEvent.ACTION_DOWN, x = screen.width - 5f)
        composeRule.onNodeWithText("Immediately").assertIsDisplayed()
        touch(MotionEvent.ACTION_UP, x = screen.width - 5f)
        composeRule.onNodeWithText("Immediately").assertDoesNotExist()
    }

    // A drag the system didn't take for a back swipe is no tap either: the menu stays.
    @Test
    fun dragFromTheLeftEdge_leavesAMenuElsewhereOpen() {
        setMenu(overPages = false)
        touch(MotionEvent.ACTION_DOWN, x = 5f)
        touch(MotionEvent.ACTION_MOVE, x = 5f, y = 200f)
        // Released back where it began: the move alone made it a drag.
        touch(MotionEvent.ACTION_UP, x = 5f)
        composeRule.onNodeWithText("Immediately").assertIsDisplayed()
    }

    // Over the pages the menu is told only of the touch down: the pages see its end, and close
    // the menu on a tap (ConsumeTapsClosingMenusTest).
    @Test
    fun touchOnTheLeftEdge_leavesAMenuOverThePagesForThePagesToClose() {
        setMenu(overPages = true)
        touch(MotionEvent.ACTION_OUTSIDE, x = 5f)
        composeRule.onNodeWithText("Immediately").assertIsDisplayed()

        composeRule.runOnIdle { checkNotNull(pages.closeKeptMenu).invoke() }
        composeRule.onNodeWithText("Immediately").assertDoesNotExist()
    }

    @Test
    fun tapOutsideAwayFromTheSides_stillClosesAMenuOverThePages() {
        setMenu(overPages = true)
        touch(MotionEvent.ACTION_OUTSIDE, x = screen.width / 2f)
        composeRule.onNodeWithText("Immediately").assertDoesNotExist()
    }

    @Test
    fun touchDownOutsideAwayFromTheSides_stillClosesAMenuElsewhere() {
        setMenu(overPages = false)
        touch(MotionEvent.ACTION_DOWN, x = screen.width / 2f)
        composeRule.onNodeWithText("Immediately").assertDoesNotExist()
    }

    // The screen's back handler closes it, as for the Back key.
    @Test
    fun backSwipeFromTheLeft_leavesATooltipInADialogOpen() {
        setTooltip()
        touch(MotionEvent.ACTION_OUTSIDE, x = 5f)
        composeRule.onNodeWithText("Rose").assertIsDisplayed()
    }

    @Test
    fun tapOutsideAwayFromTheSides_stillClosesATooltip() {
        setTooltip()
        touch(MotionEvent.ACTION_OUTSIDE, x = screen.width / 2f)
        composeRule.onNodeWithText("Rose").assertDoesNotExist()
    }
}
