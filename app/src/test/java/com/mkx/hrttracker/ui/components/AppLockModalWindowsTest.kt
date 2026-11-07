package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.mkx.hrttracker.healthconnect.HealthConnectCapabilityState
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.ui.settings.WeightSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Sheets, dialogs and menus are windows of their own, above the in-window lock screen. While the
 * app is locked they must not show, and must not close either: a half-filled sheet comes back
 * after unlock, as does one restored after process death under the lock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AppLockModalWindowsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var locked by mutableStateOf(false)
    private var dismissals = 0

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
        composeRule.onNodeWithText("Delete entry").assertIsNotDisplayed()
        assertEquals(0, dismissals)

        locked = false
        composeRule.onNodeWithText("Delete entry").assertIsDisplayed()
        assertEquals(0, dismissals)
    }

    @Test
    fun discardDialog_waitsUnderTheLock_andAsksOnUnlock() {
        val unsavedChanges = UnsavedChangesState()
        Any().also { editor ->
            unsavedChanges.hostInBackStack(editor)
            unsavedChanges.update(editor, dirty = true)
        }
        var left = 0
        // A Home deep link leaving a dirty editor while the app is locked.
        unsavedChanges.leaveBackStack { left++; true }
        locked = true
        composeRule.setContent {
            CompositionLocalProvider(LocalAppLocked provides locked) {
                UnsavedChangesDialog(hazeState = null, state = unsavedChanges)
            }
        }

        composeRule.onNodeWithText("Discard your unsaved changes?").assertIsNotDisplayed()
        assertNotNull(unsavedChanges.pendingLeave)

        locked = false
        composeRule.onNodeWithText("Discard").performClick()
        assertEquals(1, left)
    }
}
