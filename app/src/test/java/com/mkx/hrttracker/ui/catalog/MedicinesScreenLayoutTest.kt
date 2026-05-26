package com.mkx.hrttracker.ui.catalog

import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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
    fun medicineManagerFuelGaugeShownOnlyForTrackedRunwayStates() {
        assertFalse(medicineManagerShowsFuelGauge(testProjection(MedicineStockState.UNTRACKED)))
        assertFalse(medicineManagerShowsFuelGauge(testProjection(MedicineStockState.NO_RUNWAY)))
        assertTrue(medicineManagerShowsFuelGauge(testProjection(MedicineStockState.OUT)))
        assertTrue(medicineManagerShowsFuelGauge(testProjection(MedicineStockState.USER_LOW)))
        assertTrue(medicineManagerShowsFuelGauge(testProjection(MedicineStockState.HEALTHY)))
    }

    @Test
    fun medicineManagerFuelGaugeProgressUsesWarnBuffer() {
        assertEquals(
            0.5f,
            medicineManagerFuelGaugeProgress(
                testProjection(
                    state = MedicineStockState.HEALTHY,
                    runwayDays = 21.0,
                    warnAtDaysRemaining = 14,
                )
            ),
        )
        assertEquals(
            1.0f,
            medicineManagerFuelGaugeProgress(
                testProjection(
                    state = MedicineStockState.HEALTHY,
                    runwayDays = 99.0,
                    warnAtDaysRemaining = 14,
                )
            ),
        )
        assertEquals(
            0.0f,
            medicineManagerFuelGaugeProgress(testProjection(MedicineStockState.NO_RUNWAY)),
        )
    }
}

private fun testProjection(
    state: MedicineStockState,
    runwayDays: Double? = 7.0,
    warnAtDaysRemaining: Int = 14,
): MedicineStockProjection {
    val medicine = testMedicine(
        stock = MedicineStock(
            trackingEnabled = state != MedicineStockState.UNTRACKED,
            warnAtDaysRemaining = warnAtDaysRemaining,
        )
    )
    return MedicineStockProjection(
        medicine = medicine,
        dosesPerDayMagnitude = 1.0,
        totalStockUnits = 7.0,
        runway = runwayDays?.let { days ->
            RunwayProjection.Days(
                days = days.toInt(),
                lastFulfillable = LocalDate.of(2026, 1, 1).plusDays(days.toLong()),
            )
        } ?: RunwayProjection.NoSchedule,
        intervalDays = null,
        maxPerAdministration = 1.0,
        state = state,
    )
}
