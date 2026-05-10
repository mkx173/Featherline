package com.mkx.hrttracker.ui.navigation

import androidx.compose.runtime.saveable.SaverScope
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGelApplicationArea
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.ui.log.AddEntryQuickLogRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
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
        // Cover every MedicationDose branch so future additions break the test.
        val doses = listOf(
            MedicationDose.MgAsMedicine(2.5),
            MedicationDose.GelEquivalentEstradiolMg(1.5),
            MedicationDose.GelPercentAndWeight(0.06, 1.25),
            MedicationDose.PatchTotalMg(3.8),
            MedicationDose.PatchReleaseRateMcgPerDay(75.0),
            MedicationDose.None,
        )
        doses.forEach { dose ->
            val request = AddEntrySheetRequest(
                quickLogRequest = AddEntryQuickLogRequest(
                    groupId = UUID.fromString("33333333-3333-3333-3333-333333333333"),
                    scheduleTimeUuid = UUID.fromString("44444444-4444-4444-4444-444444444444"),
                    scheduledFor = LocalDateTime.of(2026, 5, 11, 9, 30),
                    medicationDetails = MedicationDetails(
                        category = MedicationCategory.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        selection = MedicationSelection.Catalog(MedicationKey.ESTRADIOL),
                        dose = dose,
                        gelApplicationArea = MedicationGelApplicationArea.DEFAULT,
                    ),
                    medicationCount = 1,
                ),
            )
            assertEquals("dose=$dose", request, roundTrip(request))
        }
    }

    @Test
    fun addEntrySheetRequestSaver_roundTripsCustomSelectionAndOptionalScheduleTime() {
        val request = AddEntrySheetRequest(
            quickLogRequest = AddEntryQuickLogRequest(
                groupId = UUID.fromString("55555555-5555-5555-5555-555555555555"),
                scheduleTimeUuid = null,
                scheduledFor = LocalDateTime.of(2026, 5, 11, 21, 0),
                medicationDetails = MedicationDetails(
                    category = MedicationCategory.CUSTOM,
                    applicationType = MedicationApplicationType.SUBLINGUAL,
                    selection = MedicationSelection.Custom("My med"),
                    dose = MedicationDose.MgAsMedicine(0.25),
                ),
                medicationCount = 2,
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
