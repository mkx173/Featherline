package com.mkx.hrttracker.ui.navigation

import androidx.compose.runtime.saveable.SaverScope
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.ui.log.AddEntryEditSnapshot
import com.mkx.hrttracker.ui.log.AddEntryQuickLogRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

class HrtTrackerNavHostTest {
    @Test
    fun topLevelNavigationTapAction_returnsScrollToTop_for_active_top_level_route() {
        assertEquals(
            TopLevelNavigationTapAction.SCROLL_TO_TOP,
            topLevelNavigationTapAction(
                tappedScreen = Screen.Main,
                selectedBottomScreen = Screen.Main,
                currentRoute = Screen.Main.route,
            )
        )
    }

    @Test
    fun topLevelNavigationTapAction_returnsPopToTopLevel_for_child_of_selected_route() {
        assertEquals(
            TopLevelNavigationTapAction.POP_TO_TOP_LEVEL,
            topLevelNavigationTapAction(
                tappedScreen = Screen.Settings,
                selectedBottomScreen = Screen.Settings,
                currentRoute = Screen.SettingsCalibration.baseRoute,
            )
        )
    }

    @Test
    fun topLevelNavigationTapAction_returnsNavigate_for_different_top_level_route() {
        assertEquals(
            TopLevelNavigationTapAction.NAVIGATE,
            topLevelNavigationTapAction(
                tappedScreen = Screen.Settings,
                selectedBottomScreen = Screen.Plan,
                currentRoute = Screen.Plan.route,
            )
        )
    }

    @Test
    fun topLevelNavigationTapAction_returnsPopToTopLevel_for_plan_history_route() {
        assertEquals(
            TopLevelNavigationTapAction.POP_TO_TOP_LEVEL,
            topLevelNavigationTapAction(
                tappedScreen = Screen.Plan,
                selectedBottomScreen = Screen.Plan,
                currentRoute = Screen.History.route,
            )
        )
    }

    @Test
    fun topLevelNavigationTapAction_returnsPopToTopLevel_for_planBatchAdd_route() {
        assertEquals(
            TopLevelNavigationTapAction.POP_TO_TOP_LEVEL,
            topLevelNavigationTapAction(
                tappedScreen = Screen.Plan,
                selectedBottomScreen = Screen.Plan,
                currentRoute = Screen.PlanBatchAdd.route,
            )
        )
    }

    @Test
    fun topLevelNavigationTapAction_returnsPopToTopLevel_for_planArchivedGroups_route() {
        assertEquals(
            TopLevelNavigationTapAction.POP_TO_TOP_LEVEL,
            topLevelNavigationTapAction(
                tappedScreen = Screen.Plan,
                selectedBottomScreen = Screen.Plan,
                currentRoute = Screen.PlanArchivedGroups.route,
            )
        )
    }

    @Test
    fun medicinesCreateRouteIncludesOptionalPickerResultKey() {
        assertEquals("medicines/plan", Screen.Medicines.createRoute(Screen.Plan.route))
        assertEquals(
            "medicines/plan?pickerResultKey=medicineResult",
            Screen.Medicines.createRoute(Screen.Plan.route, pickerResultKey = "medicineResult"),
        )
    }

    @Test
    fun topLevelNavigationReplacementPopUpToRoute_usesSelectedTopLevelRoute() {
        assertEquals(
            Screen.Plan.route,
            topLevelNavigationReplacementPopUpToRoute(
                targetScreen = Screen.Main,
                selectedBottomScreen = Screen.Plan,
            )
        )
    }

    @Test
    fun topLevelRootBackAction_navigatesHomeFromNonHomeTopLevelRoot() {
        assertEquals(
            TopLevelRootBackAction.NAVIGATE_HOME,
            topLevelRootBackAction(
                selectedBottomScreen = Screen.Settings,
                currentRoute = Screen.Settings.route,
            )
        )
    }

    @Test
    fun topLevelRootBackAction_doesNotHandleBackFromChildRoute() {
        assertEquals(
            TopLevelRootBackAction.NONE,
            topLevelRootBackAction(
                selectedBottomScreen = Screen.Settings,
                currentRoute = Screen.SettingsCalibration.baseRoute,
            )
        )
    }

    @Test
    fun homeDeepLinkNavigationAction_ignoresAlreadyHandledSignal() {
        assertEquals(
            HomeDeepLinkNavigationAction.NONE,
            homeDeepLinkNavigationAction(
                homeDeepLinkSignal = 2,
                lastHandledHomeDeepLinkSignal = 2,
                currentRoute = Screen.Settings.route,
            )
        )
    }

    @Test
    fun homeDeepLinkNavigationAction_waitsForCurrentRouteBeforeHandlingNewSignal() {
        assertEquals(
            HomeDeepLinkNavigationAction.AWAIT_ROUTE,
            homeDeepLinkNavigationAction(
                homeDeepLinkSignal = 1,
                lastHandledHomeDeepLinkSignal = 0,
                currentRoute = null,
            )
        )
    }

    @Test
    fun homeDeepLinkNavigationAction_marksHandledWithoutNavigationWhenAlreadyOnHome() {
        assertEquals(
            HomeDeepLinkNavigationAction.NONE,
            homeDeepLinkNavigationAction(
                homeDeepLinkSignal = 1,
                lastHandledHomeDeepLinkSignal = 0,
                currentRoute = Screen.Main.route,
            )
        )
    }

    @Test
    fun homeDeepLinkNavigationAction_navigatesHomeFromOtherRoutes() {
        assertEquals(
            HomeDeepLinkNavigationAction.NAVIGATE_HOME,
            homeDeepLinkNavigationAction(
                homeDeepLinkSignal = 1,
                lastHandledHomeDeepLinkSignal = 0,
                currentRoute = Screen.SettingsCalibration.route,
            )
        )
    }

    @Test
    fun homeDeepLinkHighlightEffectsEnabled_waitsForReadySignal() {
        assertFalse(
            homeDeepLinkHighlightEffectsEnabled(
                shellHighlightEffectsEnabled = true,
                homeDeepLinkSignal = 2,
                readyHomeDeepLinkHighlightSignal = 1,
            )
        )
    }

    @Test
    fun homeDeepLinkHighlightEffectsEnabled_enablesAfterReadySignal() {
        assertTrue(
            homeDeepLinkHighlightEffectsEnabled(
                shellHighlightEffectsEnabled = true,
                homeDeepLinkSignal = 2,
                readyHomeDeepLinkHighlightSignal = 2,
            )
        )
    }

    @Test
    fun homeDeepLinkHighlightEffectsEnabled_staysDisabledWhenShellDisabled() {
        assertFalse(
            homeDeepLinkHighlightEffectsEnabled(
                shellHighlightEffectsEnabled = false,
                homeDeepLinkSignal = 2,
                readyHomeDeepLinkHighlightSignal = 2,
            )
        )
    }

    @Test
    fun addEntrySheetRequestSaver_roundTripsNull() {
        val saver = AddEntrySheetRequestSaver
        val saved = with(saver) { scope.save(null) }
        assertNotNull(saved)
        assertNull(saver.restore(saved!!))
    }

    @Test
    fun addEntrySheetRequestSaver_roundTripsEditEntries() {
        val original = AddEntrySheetRequest(
            entryIds = listOf("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222"),
        )

        assertEquals(original, roundTrip(original))
    }

    @Test
    fun addEntrySheetRequestSaver_roundTripsQuickLogRequestForEveryDoseShape() {
        // Cover every DoseInstruction branch so future additions break the test.
        val doseInstructions = listOf(
            DoseInstruction.TabletFraction(1, 2),
            DoseInstruction.WholeUnit,
            DoseInstruction.VolumeMl(0.5),
            DoseInstruction.WeightGrams(1.25),
            DoseInstruction.Noop,
        )
        doseInstructions.forEach { doseInstruction ->
            val request = AddEntrySheetRequest(
                quickLogRequest = AddEntryQuickLogRequest(
                    groupId = UUID.fromString("33333333-3333-3333-3333-333333333333"),
                    scheduleTimeUuid = UUID.fromString("44444444-4444-4444-4444-444444444444"),
                    scheduledFor = LocalDateTime.of(2026, 5, 11, 9, 30),
                    medicine = testMedicine(
                        uuid = UUID.fromString("dddddddd-0000-0000-0000-000000000001"),
                        key = MedicationKey.ESTRADIOL,
                    ),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = doseInstruction,
                    medicationCount = 1,
                ),
            )
            assertEquals("doseInstruction=$doseInstruction", request, roundTrip(request))
        }
    }

    @Test
    fun addEntrySheetRequestSaver_roundTripsPatchOffQuickLogWithNullMedicine() {
        // A PATCH_OFF quick-log carries no medicine; the Saver must survive null.
        val request = AddEntrySheetRequest(
            quickLogRequest = AddEntryQuickLogRequest(
                groupId = UUID.fromString("33333333-3333-3333-3333-333333333333"),
                scheduleTimeUuid = UUID.fromString("44444444-4444-4444-4444-444444444444"),
                scheduledFor = LocalDateTime.of(2026, 5, 11, 9, 30),
                medicine = null,
                applicationType = MedicationApplicationType.PATCH_OFF,
                doseInstruction = DoseInstruction.Noop,
                medicationCount = 1,
            ),
        )

        assertEquals(request, roundTrip(request))
    }

    @Test
    fun addEntrySheetRequestSaver_roundTripsCustomSelectionAndOptionalScheduleTime() {
        val request = AddEntrySheetRequest(
            quickLogRequest = AddEntryQuickLogRequest(
                groupId = UUID.fromString("55555555-5555-5555-5555-555555555555"),
                scheduleTimeUuid = null,
                scheduledFor = LocalDateTime.of(2026, 5, 11, 21, 0),
                medicine = testCustomMedicine(
                    uuid = UUID.fromString("dddddddd-0000-0000-0000-000000000002"),
                    medicationName = "My med",
                    preparation = MedicinePreparation.Pill(strengthMgPerTablet = 0.25),
                ),
                applicationType = MedicationApplicationType.SUBLINGUAL,
                doseInstruction = DoseInstruction.WholeUnit,
                medicationCount = 2,
            ),
        )

        assertEquals(request, roundTrip(request))
    }

    @Test
    fun addEntrySheetRequestSaver_roundTripsSnapshotGroupContext() {
        val request = AddEntrySheetRequest(
            quickLogRequest = AddEntryQuickLogRequest(
                groupId = UUID.fromString("66666666-6666-6666-6666-666666666666"),
                scheduleTimeUuid = UUID.fromString("77777777-7777-7777-7777-777777777777"),
                scheduledFor = LocalDateTime.of(2026, 5, 11, 21, 0),
                medicine = testMedicine(
                    uuid = UUID.fromString("dddddddd-0000-0000-0000-000000000003"),
                    key = MedicationKey.ESTRADIOL,
                ),
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                medicationCount = 2,
                sourceGroupName = "Snapshot estradiol",
                sourceGroupColorKey = MedicationGroupColorKey.PLUM,
                sourceGroupPreviousScheduledFor = LocalDateTime.of(2026, 5, 10, 21, 0),
                sourceGroupNextScheduledFor = LocalDateTime.of(2026, 5, 12, 21, 0),
            ),
        )

        assertEquals(request, roundTrip(request))
    }

    @Test
    fun addEntrySheetRequestSaver_roundTripsEditSnapshot() {
        val groupId = UUID.fromString("88888888-8888-8888-8888-888888888888")
        val scheduleTimeId = UUID.fromString("99999999-9999-9999-9999-999999999999")
        val entryId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val scheduledFor = LocalDateTime.of(2026, 5, 11, 21, 0)
        val entry = MedicationLogEntry(
            uuid = entryId,
            medicine = testMedicine(
                uuid = UUID.fromString("dddddddd-0000-0000-0000-000000000004"),
                key = MedicationKey.ESTRADIOL,
            ),
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            equivalentE2Mg = 2.0,
            sourceGroupUuid = groupId,
            scheduleTimeUuid = scheduleTimeId,
            appliedAt = Instant.parse("2026-05-11T12:05:00Z"),
            appliedAtTimeZoneId = "Asia/Tokyo",
            scheduledFor = scheduledFor,
            count = 2,
        )
        val request = AddEntrySheetRequest(
            entryIds = listOf(entryId.toString()),
            editSnapshot = AddEntryEditSnapshot(
                entries = listOf(entry),
                sourceGroupName = "Snapshot estradiol",
                sourceGroupColorKey = MedicationGroupColorKey.PLUM,
                sourceGroupPreviousScheduledFor = scheduledFor.minusDays(1),
                sourceGroupNextScheduledFor = scheduledFor.plusDays(1),
            ),
        )

        assertEquals(request, roundTrip(request))
    }

    private val scope = SaverScope { true }

    private fun roundTrip(request: AddEntrySheetRequest?): AddEntrySheetRequest? {
        val saver = AddEntrySheetRequestSaver
        val saved = with(saver) { scope.save(request) } ?: error("saver returned null")
        return saver.restore(saved)
    }
}
