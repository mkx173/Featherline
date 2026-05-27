package com.mkx.hrttracker.ui.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
