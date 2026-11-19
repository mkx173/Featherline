package com.mkx.hrttracker.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mkx.hrttracker.healthconnect.HealthConnectCapabilityState
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.components.AccessDenial
import com.mkx.hrttracker.ui.components.AccessDenials
import com.mkx.hrttracker.ui.components.FollowAccessDenial
import com.mkx.hrttracker.widget.WidgetAppearance
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two switches' denials, as WeightSheetTest checks the Health Connect switch's message: the
 * real Settings content and weight sheet, with the app's denial holder ([AccessDenials]) and
 * Android's answer faked (a tap on the reminders switch with no access is a denial, as the
 * permission dialog's "Don't allow"; a denial set on the holder, as the Health Connect dialog's).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SettingsReminderSwitchTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class TestOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
        fun moveTo(state: Lifecycle.State) {
            registry.currentState = state
        }
    }

    private val screen = TestOwner()
    private var access by mutableStateOf(true)
    private var remindersEnabled by mutableStateOf(false)
    private var shown by mutableStateOf(true)
    private val enables = mutableListOf<Boolean>()
    private val message = "Enable app notifications in system settings."
    private val healthConnectMessage = "Access denied. Tap to allow it in Health Connect."

    /** Settings with its reminders switch, as SettingsScreen wires it to [denial]. */
    private fun setSettings(denial: AccessDenial) {
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides screen) {
                if (shown) {
                    val context = LocalContext.current
                    FollowAccessDenial(
                        denial = denial,
                        hasAccess = access,
                        onAccessAllowed = {
                            enables += true
                            remindersEnabled = true
                        },
                    )
                    SettingsScreenContent(
                        uiState = SettingsUiState(settingsState = SettingsState(remindersEnabled = remindersEnabled)),
                        widgetAppearance = WidgetAppearance.Default,
                        hasNotificationAccess = access,
                        onWeightSave = { _, _ -> },
                        onWeightClear = {},
                        reminderSupportState = resolveSettingsReminderSupportState(
                            hasNotificationAccess = access,
                            hasExactAlarmAccess = true,
                            remindersEnabled = remindersEnabled,
                            isNotificationAccessDenied = denial.isDenied,
                        ),
                        onRemindersEnabledChange = { on ->
                            when {
                                !on -> remindersEnabled = false
                                access -> {
                                    enables += true
                                    remindersEnabled = true
                                }
                                else -> denial.isDenied = true
                            }
                        },
                        onOpenNotificationSettings = {
                            denial.openSettings(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS))
                        },
                        onRequestExactAlarmAccess = {},
                        onScreenLockProtectionToggle = {},
                        onAppLockGracePeriodOptionChange = {},
                        onHideScreenContentEnabledChange = {},
                        onAppLanguageOptionChange = {},
                        onFirstDayOfWeekOptionChange = {},
                        onDarkModeOptionChange = {},
                        onAdaptiveColorEnabledChange = {},
                        onPureBlackEnabledChange = {},
                        onHazeBlurEnabledChange = {},
                        onClassicNavigationBarEnabledChange = {},
                        onShowArchivedGroupRecordsChange = {},
                        onHideReferenceRangesChange = {},
                        onHideMedicationDetailsChange = {},
                        onWidgetAppearanceChange = {},
                        onBackupToFileClick = {},
                        onRestoreFromFileClick = {},
                        onImportExternalTrackerClick = {},
                        showDiagnosticsExport = false,
                        onExportDiagnosticLogsClick = {},
                        onCalibrationClick = {},
                    )
                }
            }
        }
    }

    private fun remindersRow() =
        composeRule.onNode(hasText("Dose reminders") and hasClickAction()).performScrollTo()

    private fun messageShows(text: String = message) =
        composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** Leaves the screen (another page or screen, the app left) and comes back to it. */
    private fun leaveAndReturn() {
        screen.moveTo(Lifecycle.State.STARTED)
        shown = false
        composeRule.waitForIdle()
        shown = true
        screen.moveTo(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
    }

    /**
     * Takes the content out of composition and back with the screen resumed throughout, as a sheet
     * closes and opens again: nothing pauses.
     */
    private fun closeAndReopen(whileClosed: () -> Unit = {}) {
        shown = false
        composeRule.waitForIdle()
        whileClosed()
        composeRule.waitForIdle()
        shown = true
        composeRule.waitForIdle()
    }

    /** Later in the session: the app is left, access is granted there, and the app comes back. */
    private fun grantElsewhere() {
        screen.moveTo(Lifecycle.State.CREATED)
        access = true
        screen.moveTo(Lifecycle.State.RESUMED)
    }

    /** The message's trip to the settings screen and back, with nothing granted there. */
    private fun ungrantedTrip(messageText: String) {
        composeRule.onNodeWithText(messageText).performScrollTo().performClick()
        screen.moveTo(Lifecycle.State.STARTED)
        screen.moveTo(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        assertEquals(emptyList<Boolean>(), enables)
    }

    @Test
    fun reminders_noMessageUpFront_aDenialShowsItAndDisablesTheSwitch_andItIsBackAfterLeavingSettings() {
        access = false
        setSettings(AccessDenials().notifications)
        assertEquals(false, messageShows())
        remindersRow().assertIsEnabled()

        remindersRow().performClick()
        composeRule.waitForIdle()
        assertEquals(true, messageShows())
        remindersRow().assertIsNotEnabled()

        leaveAndReturn()

        assertEquals(true, messageShows())
        remindersRow().assertIsNotEnabled()
        assertEquals(emptyList<Boolean>(), enables)
    }

    @Test
    fun reminders_accessBackFromTheMessagesSettingsTrip_turnsThemOn() {
        access = false
        setSettings(AccessDenials().notifications)
        remindersRow().performClick()
        composeRule.onNodeWithText(message).performScrollTo().performClick()
        // The trip to Android's settings pauses Settings; the return resumes it, then the
        // reconciler sees notifications allowed.
        screen.moveTo(Lifecycle.State.STARTED)
        screen.moveTo(Lifecycle.State.RESUMED)
        access = true
        composeRule.waitForIdle()

        assertEquals(listOf(true), enables)
        assertEquals(false, messageShows())
    }

    @Test
    fun reminders_accessBackAnotherWay_endsTheMessage_andTurnsNothingOn() {
        access = false
        setSettings(AccessDenials().notifications)
        remindersRow().performClick()
        composeRule.waitForIdle()
        // Allowed some other day, by another route, with Settings left meanwhile.
        leaveAndReturn()
        access = true
        composeRule.waitForIdle()

        assertEquals(emptyList<Boolean>(), enables)
        assertEquals(false, messageShows())
        remindersRow().assertIsEnabled()
    }

    @Test
    fun reminders_contentGoneAfterAnUngrantedTrip_aLaterGrantElsewhere_turnsNothingOn() {
        access = false
        setSettings(AccessDenials().notifications)
        remindersRow().performClick()
        ungrantedTrip(message)

        closeAndReopen(whileClosed = ::grantElsewhere)

        assertEquals(emptyList<Boolean>(), enables)
        assertEquals(false, messageShows())
    }

    @Test
    fun reminders_aMessageWhoseSettingsDontOpen_takesNoTrip() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        access = false
        setSettings(AccessDenials().notifications)
        remindersRow().performClick()
        composeRule.onNodeWithText(message).performScrollTo().performClick()
        composeRule.waitForIdle()

        grantElsewhere()
        composeRule.waitForIdle()

        assertEquals(emptyList<Boolean>(), enables)
    }

    @Test
    fun reminders_aFreshHolder_asAfterARestart_showsNoMessage_andAccessLostElsewhereSetsNone() {
        // A denial in the last process; the app restarts with a new holder.
        AccessDenials().notifications.isDenied = true
        access = false
        remindersEnabled = true
        setSettings(AccessDenials().notifications)
        assertEquals(false, messageShows())

        // Revoked in Android's settings: the reconciler turns reminders off (R4), and its
        // return turns nothing on.
        remindersEnabled = false
        composeRule.waitForIdle()
        access = true
        composeRule.waitForIdle()
        assertEquals(false, messageShows())
        assertEquals(emptyList<Boolean>(), enables)
    }

    /** The weight sheet, as Settings and onboarding open it, with the app's Health Connect denial. */
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    private fun setWeightSheet(denial: AccessDenial) {
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides screen) {
                if (shown) {
                    WeightSheet(
                        profile = UserProfile(),
                        syncEnabled = false,
                        healthConnect = HealthConnectCapabilityState(sdkAvailable = true, hasAccess = access),
                        isSyncing = false,
                        readingProblem = null,
                        isInProgress = false,
                        onSave = { _, _ -> },
                        onClear = {},
                        onSyncEnable = { enables += true },
                        onSyncDisable = {},
                        onSyncNow = {},
                        onDismissRequest = {},
                        healthConnectDenial = denial,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun syncSwitch() = composeRule.onNodeWithContentDescription("Sync with Health Connect")

    @Test
    fun healthConnect_theMessageIsBackWhenTheSheetOpensAgain_andAFreshHolderHasNone() {
        access = false
        val denial = AccessDenials().healthConnect
        denial.isDenied = true // A "Don't allow" in Health Connect's dialog.
        setWeightSheet(denial)
        assertEquals(true, messageShows(healthConnectMessage))

        closeAndReopen()

        assertEquals(true, messageShows(healthConnectMessage))
        syncSwitch().assertIsNotEnabled()
        assertEquals(emptyList<Boolean>(), enables)
    }

    @Test
    fun healthConnect_aFreshHolder_asAfterARestart_showsNoMessage() {
        access = false
        setWeightSheet(AccessDenials().healthConnect)

        assertEquals(false, messageShows(healthConnectMessage))
        syncSwitch().assertIsEnabled()
    }

    @Test
    fun healthConnect_accessBackFromTheMessagesTrip_turnsSyncOn() {
        access = false
        val denial = AccessDenials().healthConnect
        denial.isDenied = true
        setWeightSheet(denial)
        composeRule.onNodeWithText(healthConnectMessage).performClick()
        screen.moveTo(Lifecycle.State.STARTED)
        screen.moveTo(Lifecycle.State.RESUMED)
        access = true
        composeRule.waitForIdle()

        assertEquals(listOf(true), enables)
        assertEquals(false, messageShows(healthConnectMessage))
    }

    @Test
    fun healthConnect_accessBackAnotherWay_endsTheMessage_andTurnsNothingOn() {
        access = false
        val denial = AccessDenials().healthConnect
        denial.isDenied = true
        setWeightSheet(denial)
        leaveAndReturn()
        access = true
        composeRule.waitForIdle()

        assertEquals(emptyList<Boolean>(), enables)
        assertEquals(false, messageShows(healthConnectMessage))
        syncSwitch().assertIsEnabled()
    }

    @Test
    fun healthConnect_sheetClosedAfterAnUngrantedTrip_aLaterGrantElsewhere_turnsNothingOn() {
        access = false
        val denial = AccessDenials().healthConnect
        denial.isDenied = true
        setWeightSheet(denial)
        ungrantedTrip(healthConnectMessage)

        // The sheet closes with Settings under it resumed; it opens again after a grant elsewhere.
        closeAndReopen(whileClosed = ::grantElsewhere)

        assertEquals("sync turned on by a grant made another way", emptyList<Boolean>(), enables)
    }

    @Test
    fun healthConnect_sheetOpenWhenTheAppIsLeftAfterAnUngrantedTrip_aLaterGrantElsewhere_turnsNothingOn() {
        access = false
        val denial = AccessDenials().healthConnect
        denial.isDenied = true
        setWeightSheet(denial)
        ungrantedTrip(healthConnectMessage)

        screen.moveTo(Lifecycle.State.CREATED)
        closeAndReopen {
            access = true
            screen.moveTo(Lifecycle.State.RESUMED)
        }

        assertEquals(emptyList<Boolean>(), enables)
    }

    @Test
    fun healthConnect_accessAllowedOnTheTrip_turnsSyncOn_evenIfTheActivityIsRecreatedMeanwhile() {
        access = false
        val denial = AccessDenials().healthConnect
        denial.isDenied = true
        setWeightSheet(denial)
        composeRule.onNodeWithText(healthConnectMessage).performClick()
        // Away in Health Connect: the activity is recreated (a font-size change, "Don't keep
        // activities"), so the sheet leaves composition, and access is allowed there.
        screen.moveTo(Lifecycle.State.CREATED)
        closeAndReopen { access = true }
        screen.moveTo(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()

        assertEquals(listOf(true), enables)
    }

    @Test
    fun healthConnect_aMessageWhoseSettingsDontOpen_takesNoTrip() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        access = false
        val denial = AccessDenials().healthConnect
        denial.isDenied = true
        setWeightSheet(denial)
        composeRule.onNodeWithText(healthConnectMessage).performClick()
        composeRule.waitForIdle()

        // Health Connect is back and granted; the sheet, open all along, sees the return.
        grantElsewhere()
        composeRule.waitForIdle()

        assertEquals(emptyList<Boolean>(), enables)
    }
}
