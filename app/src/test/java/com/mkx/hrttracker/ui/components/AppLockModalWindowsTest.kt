package com.mkx.hrttracker.ui.components

import android.view.WindowManager.LayoutParams
import android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModel
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.mkx.hrttracker.healthconnect.HealthConnectCapabilityState
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.ui.security.AppLockBackHandler
import com.mkx.hrttracker.ui.security.HiddenUnderAppLock
import com.mkx.hrttracker.ui.settings.WeightSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * Sheets, dialogs and menus are windows of their own, above the in-window lock screen. While the
 * app is locked they must not show, and must not close either: a half-filled sheet comes back
 * after unlock, as does one restored after process death under the lock. Back doesn't reach them,
 * nor anything else under the lock screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AppLockModalWindowsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var locked by mutableStateOf(false)
    private var dismissals = 0

    // MainActivity's layers: the home shell, and the lock screen's back handler over it.
    @Composable
    private fun Shell(content: @Composable () -> Unit) {
        HiddenUnderAppLock(locked = locked, content = content)
        AppLockBackHandler(enabled = locked)
    }

    // A top-level page, as TopLevelPager hosts Settings, with a dispatcher of its own that its
    // sheets' windows inherit: their back handlers sit on the activity's, not their own window's.
    @Composable
    private fun TopLevelPage(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides rememberNavigationEventDispatcherOwner(),
            content = content,
        )
    }

    // Back, as the lock screen's window gets it, and as each modal window would if it had focus.
    private fun pressBack() {
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
            // Not isShowing, false for a window hidden under the lock: one not dismissed.
            ShadowDialog.getShownDialogs().filter { it.window!!.decorView.isAttachedToWindow }.forEach { dialog ->
                (dialog as ComponentDialog).onBackPressedDispatcher.onBackPressed()
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun weightSheet_hidesUnderTheLock_andComesBackWithItsDraftOnUnlock() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppLocked provides locked) {
                WeightSheet(
                    profile = UserProfile(
                        weightKg = 62.4,
                        weightOriginalValue = 62.4,
                        weightOriginalUnit = WeightUnit.KILOGRAMS,
                    ),
                    syncEnabled = false,
                    healthConnect = HealthConnectCapabilityState(sdkAvailable = true, hasAccess = true),
                    isSyncing = false,
                    readingProblem = null,
                    isInProgress = false,
                    onSave = { _, _ -> },
                    onClear = {},
                    onSyncEnable = {},
                    onSyncDisable = {},
                    onSyncNow = {},
                    onDismissRequest = { dismissals++ },
                )
            }
        }
        composeRule.onNodeWithText("62.4").performTextReplacement("70.1")

        locked = true
        composeRule.onNodeWithText("70.1").assertIsNotDisplayed()
        assertEquals(0, dismissals)

        locked = false
        composeRule.onNodeWithText("70.1").assertIsDisplayed()
        assertEquals(0, dismissals)
    }

    @Test
    fun backUnderTheLock_leavesAnOpenSheetWithItsDraft() {
        var open by mutableStateOf(false)
        composeRule.setContent {
            Shell {
                TopLevelPage {
                    if (open) {
                        WeightSheet(
                            profile = UserProfile(
                                weightKg = 62.4,
                                weightOriginalValue = 62.4,
                                weightOriginalUnit = WeightUnit.KILOGRAMS,
                            ),
                            syncEnabled = false,
                            healthConnect = HealthConnectCapabilityState(sdkAvailable = true, hasAccess = true),
                            isSyncing = false,
                            readingProblem = null,
                            isInProgress = false,
                            onSave = { _, _ -> },
                            onClear = {},
                            onSyncEnable = {},
                            onSyncDisable = {},
                            onSyncNow = {},
                            onDismissRequest = { dismissals++ },
                        )
                    }
                }
            }
        }
        // Opened after launch, so its back handler registers after the lock screen's.
        open = true
        composeRule.onNodeWithText("62.4").performTextReplacement("65")

        locked = true
        pressBack()
        assertEquals("Back under the lock screen dismissed the sheet hidden there", 0, dismissals)

        locked = false
        composeRule.onNodeWithText("65").assertIsDisplayed()
        assertEquals(0, dismissals)

        pressBack()
        assertEquals("Back after the unlock didn't dismiss the sheet", 1, dismissals)
    }

    @Test
    fun backUnderTheLock_leavesTheDiscardPromptOverADirtyEditor() {
        val unsavedChanges = UnsavedChangesState()
        val editor = object : ViewModel() {}
        var pops = 0
        var editorOpen by mutableStateOf(false)
        composeRule.setContent {
            Shell {
                CompositionLocalProvider(LocalUnsavedChanges provides unsavedChanges) {
                    if (editorOpen) unsavedChangesBack(editor = editor, onBack = { pops++; true })
                    UnsavedChangesDialog(hazeState = null)
                }
            }
        }
        // Pushed after launch, so its back handler registers after the lock screen's.
        editorOpen = true
        unsavedChanges.update(editor, dirty = true)
        pressBack()
        composeRule.onNodeWithText("Discard your unsaved changes?").assertIsDisplayed()
        val leave = unsavedChanges.pendingLeave

        locked = true
        pressBack()
        assertSame(
            "Back under the lock screen reached the editor or the prompt hidden there",
            leave,
            unsavedChanges.pendingLeave,
        )
        assertEquals("The hidden prompt's window was re-created", 1, ShadowDialog.getShownDialogs().size)

        locked = false
        composeRule.onNodeWithText("Discard").performClick()
        assertEquals(1, pops)
    }

    @Test
    fun backupPasswordDialog_hidesUnderTheLock_andKeepsItsPasswordForUnlock() {
        var confirmed: String? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalAppLocked provides locked) {
                BackupPasswordDialog(
                    title = "Back up data",
                    message = "Choose a password",
                    confirmLabel = "Export",
                    passwordLabel = "Password",
                    onConfirm = { confirmed = it },
                    onDismiss = { dismissals++ },
                )
            }
        }
        composeRule.onNodeWithText("Password").performTextInput("secret12")

        locked = true
        composeRule.onNodeWithText("Back up data").assertIsNotDisplayed()
        assertEquals(0, dismissals)

        locked = false
        composeRule.onNodeWithText("Export").performClick()
        assertEquals("secret12", confirmed)
        assertEquals(0, dismissals)

        composeRule.runOnIdle {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        assertEquals("Back on the dialog after the unlock didn't dismiss it", 1, dismissals)
    }

    @Test
    fun dialogRestoredUnderTheLock_staysHiddenUntilUnlock() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            var open by rememberSaveable { mutableStateOf(true) }
            CompositionLocalProvider(LocalAppLocked provides locked) {
                if (open) {
                    HazeAlertDialog(
                        onDismissRequest = {
                            dismissals++
                            open = false
                        },
                        confirmButton = {},
                        title = { Text("Delete all records?") },
                    )
                }
            }
        }
        composeRule.onNodeWithText("Delete all records?").assertIsDisplayed()

        // Process death while away; the activity comes back locked.
        locked = true
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("Delete all records?").assertIsNotDisplayed()
        assertEquals(0, dismissals)

        locked = false
        composeRule.onNodeWithText("Delete all records?").assertIsDisplayed()
        assertEquals(0, dismissals)
    }

    // The window manager may keep a hidden window's surface and show it again, with its last frame
    // and touch region, over the lock screen, as once a system panel over the locked app closes:
    // hidden at the window too, and as it was after the unlock.
    @Test
    fun sheetWindow_isClearAndUntouchableUnderTheLock_andAsItWasOnUnlock() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppLocked provides locked) {
                HazeModalBottomSheet(
                    onDismissRequest = { dismissals++ },
                    sheetState = rememberModalBottomSheetState(),
                ) { Text("Body weight") }
            }
        }
        assertWindowHiddenOnlyWhileLocked()
    }

    @Test
    fun dialogWindow_isClearUntouchableAndDimsNothingUnderTheLock_andAsItWasOnUnlock() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppLocked provides locked) {
                HazeAlertDialog(
                    onDismissRequest = { dismissals++ },
                    confirmButton = {},
                    title = { Text("Back up data") },
                )
            }
        }
        val shown = assertWindowHiddenOnlyWhileLocked()
        assertNotEquals("The dialog doesn't dim behind it", 0, shown.flags and FLAG_DIM_BEHIND)
    }

    // Returns the window's attributes from before the lock.
    private fun assertWindowHiddenOnlyWhileLocked(): LayoutParams {
        val window = composeRule.runOnIdle { ShadowDialog.getLatestDialog().window!! }
        val shown = composeRule.runOnIdle { LayoutParams().apply { copyFrom(window.attributes) } }

        locked = true
        composeRule.runOnIdle {
            val attributes = window.attributes
            assertEquals("The hidden window isn't transparent", 0f, attributes.alpha)
            assertEquals(
                "The hidden window still takes touches or focus",
                FLAG_NOT_TOUCHABLE or FLAG_NOT_FOCUSABLE,
                attributes.flags and (FLAG_NOT_TOUCHABLE or FLAG_NOT_FOCUSABLE),
            )
            assertEquals("The hidden window dims the lock screen", 0, attributes.flags and FLAG_DIM_BEHIND)
        }
        assertEquals(0, dismissals)

        locked = false
        composeRule.runOnIdle {
            val attributes = window.attributes
            assertEquals(shown.alpha, attributes.alpha)
            assertEquals(Integer.toHexString(shown.flags), Integer.toHexString(attributes.flags))
            assertEquals(shown.dimAmount, attributes.dimAmount)
            assertEquals(shown.softInputMode, attributes.softInputMode)
        }
        assertEquals(0, dismissals)
        return shown
    }

    @Test
    fun dropdownMenu_hidesUnderTheLock_andIsOpenAgainOnUnlock() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppLocked provides locked) {
                Box {
                    HrtDropdownMenu(
                        expanded = true,
                        onDismissRequest = { dismissals++ },
                        items = listOf(HrtDropdownMenuItem(text = "Delete entry", onClick = {})),
                    )
                }
            }
        }
        composeRule.onNodeWithText("Delete entry").assertIsDisplayed()

        locked = true
        // Gone with its window, not just hidden.
        composeRule.onNodeWithText("Delete entry").assertDoesNotExist()
        assertEquals(0, dismissals)

        locked = false
        composeRule.onNodeWithText("Delete entry").assertIsDisplayed()
        assertEquals(0, dismissals)
    }

    // A persistent tooltip's popup, such as the group colour picker's, is a window of its own.
    @Test
    fun persistentTooltip_isGoneUnderTheLock_andOpenAgainOnUnlock() {
        var picked = 0
        val tooltipState = TooltipState(initialIsVisible = true, isPersistent = true)
        composeRule.setContent {
            CompositionLocalProvider(LocalAppLocked provides locked) {
                HrtTooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Below,
                    ),
                    tooltip = {
                        RichTooltip { TextButton(onClick = { picked++ }) { Text("Rose") } }
                    },
                    state = tooltipState,
                ) {
                    Text("Group colour")
                }
            }
        }
        composeRule.onNodeWithText("Rose").assertIsDisplayed()

        locked = true
        composeRule.onNodeWithText("Rose").assertDoesNotExist()

        locked = false
        composeRule.onNodeWithText("Rose").performClick()
        assertEquals(1, picked)
    }

    @Test
    fun discardDialog_waitsUnderTheLock_andAsksOnUnlock() {
        val unsavedChanges = UnsavedChangesState()
        Any().also { editor ->
            unsavedChanges.hostInBackStack(editor)
            unsavedChanges.update(editor, dirty = true)
        }
        var left = 0
        // Left asking when the activity is recreated, as a font size changed while away does,
        // and comes back locked: the retained leave's prompt is composed again under the lock.
        // (A Home deep link waits for the unlock before it asks; nor does a leave outlive the
        // process.)
        unsavedChanges.leaveBackStack { left++; true }
        locked = true
        composeRule.setContent {
            CompositionLocalProvider(LocalAppLocked provides locked) {
                UnsavedChangesDialog(hazeState = null, state = unsavedChanges)
            }
        }

        composeRule.onNodeWithText("Discard your unsaved changes?").assertIsNotDisplayed()
        // Not even shown and then hidden: it would draw a frame or more over the lock screen.
        assertEquals(
            "A window was shown under the lock screen",
            0,
            ShadowDialog.getShownDialogs().size,
        )
        assertNotNull(unsavedChanges.pendingLeave)

        locked = false
        composeRule.onNodeWithText("Discard").performClick()
        assertEquals(1, left)
    }
}
