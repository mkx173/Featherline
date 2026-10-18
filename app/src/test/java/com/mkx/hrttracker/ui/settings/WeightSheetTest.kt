package com.mkx.hrttracker.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mkx.hrttracker.healthconnect.HealthConnectCapabilityState
import com.mkx.hrttracker.healthconnect.ReadingProblem
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WeightSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val access = HealthConnectCapabilityState(sdkAvailable = true, hasAccess = true)

    private fun weight(value: Double?, measuredAt: Instant? = null) = UserProfile(
        weightKg = value,
        weightOriginalValue = value,
        weightOriginalUnit = WeightUnit.KILOGRAMS,
        weightMeasuredAt = measuredAt,
    )

    private fun setSheet(
        profile: () -> UserProfile,
        syncEnabled: Boolean,
        onSave: (Double, WeightUnit) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            WeightSheetContent(
                profile = profile(),
                syncEnabled = syncEnabled,
                healthConnect = access,
                isSyncing = false,
                readingProblem = null,
                isInProgress = false,
                onSave = onSave,
                onClearRequest = {},
                onSyncEnabledChange = {},
                onSyncNow = {},
                onClose = {},
            )
        }
    }

    @Test
    fun syncOn_isReadOnlyWithOkOnly_andUpdatesWhenASyncLands() {
        var profile by mutableStateOf(weight(62.4, Instant.parse("2026-10-14T07:12:00Z")))
        setSheet(profile = { profile }, syncEnabled = true)

        composeRule.onNodeWithText("62.4").assertIsNotEnabled()
        composeRule.onNodeWithText("kg").assertExists()
        composeRule.onNodeWithContentDescription("Unit: kg. Switch to lb").assertIsNotEnabled()
        composeRule.onNodeWithText("OK").assertExists()
        composeRule.onNodeWithText("Clear").assertDoesNotExist()
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
        composeRule.onNodeWithText("Save").assertDoesNotExist()

        profile = weight(61.8, Instant.parse("2026-10-15T07:00:00Z"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("61.8").assertExists()
    }

    @Test
    fun syncOn_withNoReadingYet_saysSyncing_untilASyncFindsNone() {
        var profile by mutableStateOf(weight(62.4))
        var readingProblem by mutableStateOf<ReadingProblem?>(null)
        var healthConnect by mutableStateOf(access)
        composeRule.setContent {
            WeightSheetContent(
                profile = profile,
                syncEnabled = true,
                healthConnect = healthConnect,
                isSyncing = false,
                readingProblem = readingProblem,
                isInProgress = false,
                onSave = { _, _ -> },
                onClearRequest = {},
                onSyncEnabledChange = {},
                onSyncNow = {},
                onClose = {},
            )
        }

        // Sync just turned on: no reading's date yet, and no sync found none.
        composeRule.onNodeWithText("Syncing…").assertExists()

        // That sync threw: nothing is running any more.
        healthConnect = access.copy(syncFailed = true)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Couldn’t sync").assertExists()
        healthConnect = access

        readingProblem = ReadingProblem.NONE_FOUND
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No weight records yet").assertExists()

        readingProblem = ReadingProblem.INVALID
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Latest record isn’t a valid weight").assertExists()

        readingProblem = null
        profile = weight(61.8, Instant.parse("2026-10-15T07:00:00Z"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Synced").assertExists()
    }

    @Test
    fun aFastSync_showsSyncingForOneWholeTurn() {
        var isSyncing by mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            WeightSheetContent(
                profile = weight(62.4, Instant.parse("2026-10-14T07:12:00Z")),
                syncEnabled = true,
                healthConnect = access,
                isSyncing = isSyncing,
                readingProblem = null,
                isInProgress = false,
                onSave = { _, _ -> },
                onClearRequest = {},
                onSyncEnabledChange = {},
                onSyncNow = {},
                onClose = {},
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("Synced").assertExists()

        // The coordinator flips back before the next frame after recomposition; the sheet still holds.
        composeRule.runOnUiThread { isSyncing = true }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnUiThread { isSyncing = false }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(50)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Syncing…").assertExists()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Syncing…").assertExists()

        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Synced").assertExists()
    }

    @Test
    fun aDenial_disablesTheSwitch_andShowsAMessageThatOpensHealthConnect() {
        var opened = false
        composeRule.setContent {
            WeightSheetContent(
                profile = weight(62.4),
                syncEnabled = false,
                healthConnect = HealthConnectCapabilityState(sdkAvailable = true, hasAccess = false),
                isSyncing = false,
                readingProblem = null,
                isInProgress = false,
                onSave = { _, _ -> },
                onClearRequest = {},
                onSyncEnabledChange = {},
                onSyncNow = {},
                onClose = {},
                showPermissionDenied = true,
                onOpenHealthConnect = { opened = true },
            )
        }

        composeRule.onNodeWithContentDescription("Sync with Health Connect").assertIsNotEnabled()
        composeRule.onNodeWithText(
            "Access denied. Tap to allow it in Health Connect."
        ).performClick()
        assertEquals(true, opened)
    }

    @Test
    fun providerUpdateRequired_showsAMessageThatOpensPlay() {
        var opened = false
        composeRule.setContent {
            WeightSheetContent(
                profile = weight(62.4),
                syncEnabled = false,
                healthConnect = HealthConnectCapabilityState(providerUpdateRequired = true),
                isSyncing = false,
                readingProblem = null,
                isInProgress = false,
                onSave = { _, _ -> },
                onClearRequest = {},
                onSyncEnabledChange = {},
                onSyncNow = {},
                onClose = {},
                onOpenHealthConnect = { opened = true },
            )
        }

        composeRule.onNodeWithText("Install or update needed").assertExists()
        composeRule.onNodeWithContentDescription("Sync with Health Connect").assertIsNotEnabled()
        composeRule.onNodeWithText(
            "Health Connect needs to be installed or updated. Tap to get it on Google Play."
        ).performClick()
        assertEquals(true, opened)
    }

    @Test
    fun syncOnWhileAnUpdateIsNeeded_keepsTheWeightReadOnly_andTheSwitchUsable() {
        var disabled = false
        composeRule.setContent {
            WeightSheetContent(
                profile = weight(62.4),
                syncEnabled = true,
                healthConnect = HealthConnectCapabilityState(providerUpdateRequired = true),
                isSyncing = false,
                readingProblem = null,
                isInProgress = false,
                onSave = { _, _ -> },
                onClearRequest = {},
                onSyncEnabledChange = { disabled = !it },
                onSyncNow = {},
                onClose = {},
            )
        }

        composeRule.onNodeWithText("62.4").assertIsNotEnabled()
        composeRule.onNodeWithText("Install or update needed").assertExists()
        composeRule.onNodeWithContentDescription("Sync now").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Sync with Health Connect").assertIsEnabled().performClick()
        assertEquals(true, disabled)
    }

    @Test
    fun syncOff_reseedsToRealProfile_whenItArrivesAfterPlaceholder() {
        // uiState starts at UserProfile() (weight unset, KILOGRAMS); a sheet restored
        // before the profile loads must pick up the real weight and unit.
        var profile by mutableStateOf(UserProfile())
        var saved: Pair<Double, WeightUnit>? = null
        setSheet(profile = { profile }, syncEnabled = false, onSave = { v, u -> saved = v to u })

        profile = UserProfile(weightOriginalValue = 115.0, weightOriginalUnit = WeightUnit.POUNDS)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("115").assertExists()
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(115.0 to WeightUnit.POUNDS, saved)
    }

    @Test
    fun syncOffWithWeight_isEditableWithClearAndSave() {
        setSheet(profile = { weight(62.4) }, syncEnabled = false)

        composeRule.onNodeWithText(
            "Access denied. Tap to allow it in Health Connect."
        ).assertDoesNotExist()
        composeRule.onNodeWithText("62.4").assertIsEnabled()
        composeRule.onNodeWithText("Clear").assertExists()
        composeRule.onNodeWithText("Save").assertExists()
        composeRule.onNodeWithText("Sync off").assertExists()
        composeRule.onNodeWithContentDescription("Sync now").assertDoesNotExist()
    }

    @Test
    fun syncOffWithoutWeight_showsCancelAndSave() {
        setSheet(profile = { weight(null) }, syncEnabled = false)

        composeRule.onNodeWithText("Cancel").assertExists()
        composeRule.onNodeWithText("Save").assertExists()
        composeRule.onNodeWithText("Clear").assertDoesNotExist()
    }

    @Test
    fun unitSwitchInsideFieldConvertsAndRounds_andSaveUsesNewUnit() {
        var saved: Pair<Double, WeightUnit>? = null
        setSheet(profile = { weight(62.4) }, syncEnabled = false, onSave = { v, u -> saved = v to u })

        composeRule.onNodeWithContentDescription("Unit: kg. Switch to lb").performClick()
        composeRule.onNodeWithText("137.6").assertExists()
        composeRule.onNodeWithText("lb").assertExists()
        composeRule.onNodeWithContentDescription("Unit: lb. Switch to kg").assertExists()
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(137.6 to WeightUnit.POUNDS, saved)
    }

    @Test
    fun parseWeightText_rejectsNonPositiveAndNonFinite() {
        assertEquals(62.4, parseWeightText(" 62,4 "))
        assertEquals(null, parseWeightText("0"))
        assertEquals(null, parseWeightText("-1"))
        assertEquals(null, parseWeightText("Infinity"))
        assertEquals(null, parseWeightText("1e400"))
        assertEquals(null, parseWeightText("NaN"))
    }

    @Test
    fun convertWeightText_convertsRoundsAndLeavesInvalidTextAlone() {
        assertEquals("137.6", convertWeightText("62.4", WeightUnit.KILOGRAMS, WeightUnit.POUNDS))
        assertEquals("62.4", convertWeightText("137.6", WeightUnit.POUNDS, WeightUnit.KILOGRAMS))
        assertEquals("62.4", convertWeightText("137,6", WeightUnit.POUNDS, WeightUnit.KILOGRAMS))
        assertEquals("", convertWeightText("", WeightUnit.KILOGRAMS, WeightUnit.POUNDS))
        assertEquals("abc", convertWeightText("abc", WeightUnit.KILOGRAMS, WeightUnit.POUNDS))
    }
}
