package com.mkx.hrttracker.ui.catalog

import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class MedicinesScreenLayoutTest {
    @Test
    fun medicineManagerAddsSectionTopSpacingOnlyBetweenSections() {
        assertFalse(medicineManagerNeedsSectionTopSpacing(sectionIndex = 0))
        assertTrue(medicineManagerNeedsSectionTopSpacing(sectionIndex = 1))
        assertTrue(medicineManagerNeedsSectionTopSpacing(sectionIndex = 2))
    }

    @Test
    fun medicineManagerAddsListSegmentGapOnlyBetweenRows() {
        assertFalse(medicineManagerNeedsRowBottomGap(index = 0, itemCount = 1))
        assertTrue(medicineManagerNeedsRowBottomGap(index = 0, itemCount = 2))
        assertFalse(medicineManagerNeedsRowBottomGap(index = 1, itemCount = 2))
    }

    @Test
    fun medicineManagerSectionHeaderPaddingMatchesSettingsHeaderPadding() {
        assertEquals(4, MedicineManagerSectionHeaderTopPaddingDp)
        assertEquals(10, MedicineManagerSectionHeaderBottomPaddingDp)
    }

    @Test
    fun medicineManagerLaunchMode_resolvesManagerWithoutResultKey() {
        assertEquals(
            MedicineManagerLaunchMode.Manager,
            medicineManagerLaunchMode(slotResultKey = null, manualLogResultKey = "manual"),
        )
    }

    @Test
    fun medicineManagerLaunchMode_resolvesManualLogForManualResultKey() {
        assertEquals(
            MedicineManagerLaunchMode.ManualLog,
            medicineManagerLaunchMode(slotResultKey = "manual", manualLogResultKey = "manual"),
        )
    }

    @Test
    fun medicineManagerLaunchMode_resolvesGroupSlotForOtherResultKey() {
        assertEquals(
            MedicineManagerLaunchMode.GroupSlot("group-slot-1"),
            medicineManagerLaunchMode(slotResultKey = "group-slot-1", manualLogResultKey = "manual"),
        )
    }

    @Test
    fun medicineManagerAddNewTarget_usesCreateSheetOnlyForManagerMode() {
        assertEquals(
            MedicineManagerAddNewTarget.CreateMedicine,
            medicineManagerAddNewTarget(MedicineManagerLaunchMode.Manager),
        )
    }

    @Test
    fun medicineManagerAddNewTarget_usesCombinedSheetForGroupSlotMode() {
        assertEquals(
            MedicineManagerAddNewTarget.NewMedicineSlot(NewMedicineSlotSheetMode.GROUP_SLOT),
            medicineManagerAddNewTarget(MedicineManagerLaunchMode.GroupSlot("group-slot-1")),
        )
    }

    @Test
    fun medicineManagerAddNewTarget_usesCombinedSheetForManualLogMode() {
        assertEquals(
            MedicineManagerAddNewTarget.NewMedicineSlot(NewMedicineSlotSheetMode.MANUAL_LOG),
            medicineManagerAddNewTarget(MedicineManagerLaunchMode.ManualLog),
        )
    }

    @Test
    fun medicineManagerTitle_usesManagerTitleForManagerMode() {
        assertEquals(
            R.string.medicines_title,
            medicineManagerTitle(MedicineManagerLaunchMode.Manager),
        )
    }

    @Test
    fun medicineManagerTitle_usesPickerTitleForManualLogMode() {
        assertEquals(
            R.string.medicine_picker_select_medicine,
            medicineManagerTitle(MedicineManagerLaunchMode.ManualLog),
        )
    }

    @Test
    fun medicineManagerTitle_usesPickerTitleForGroupSlotMode() {
        assertEquals(
            R.string.medicine_picker_select_medicine,
            medicineManagerTitle(MedicineManagerLaunchMode.GroupSlot("group-slot-1")),
        )
    }

    @Test
    fun medicineManagerKeepsReferenceCountTrailingContent() {
        assertEquals(
            MedicineManagerTrailingContentKind.NONE,
            medicineManagerTrailingContentKind(referenceCount = 0),
        )
        assertEquals(
            MedicineManagerTrailingContentKind.REFERENCE_COUNT,
            medicineManagerTrailingContentKind(referenceCount = 1),
        )
    }

    @Test
    fun onboardingMedicineManagerRowsUseChevronInsteadOfReferenceCount() {
        assertEquals(
            MedicineManagerTrailingContentKind.CHEVRON,
            medicineManagerTrailingContentKind(
                referenceCount = 0,
                showOnboardingChevron = true,
            ),
        )
        assertEquals(
            MedicineManagerTrailingContentKind.CHEVRON,
            medicineManagerTrailingContentKind(
                referenceCount = 2,
                showOnboardingChevron = true,
            ),
        )
    }

    @Test
    fun pendingSlotSelectionKeepsStockProjectionForDoseSheet() {
        val medicine = testMedicine(
            uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000031"),
        )
        val projection = MedicineStockProjection(
            medicine = medicine,
            dosesPerDayMagnitude = 1.0,
            totalStockUnits = 12.0,
            runway = RunwayProjection.Days(
                days = 11,
                lastFulfillable = LocalDate.of(2026, 1, 12),
            ),
            intervalDays = null,
            maxPerAdministration = 1.0,
            state = MedicineStockState.HEALTHY,
        )
        val state = MedicinesUiState(
            activeSections = listOf(
                MedicineCategorySection(
                    category = medicine.category,
                    medicines = listOf(
                        MedicineListItem(
                            medicine = medicine,
                            activeGroupReferenceCount = 0,
                            stockProjection = projection,
                        )
                    ),
                )
            ),
            isLoading = false,
        )

        val selected = state.findMedicineItemByUuid(medicine.uuid)

        assertEquals(projection, selected?.stockProjection)
    }
}
