package com.mkx.hrttracker.ui.catalog.stock

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.RunwayProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AdjustStockSheetTest {

    @Test
    fun parseAdjustStockCountAcceptsDotAndCommaDecimalsForTablets() {
        assertEquals(1.5, requireNotNull(parseAdjustStockCount("1.5", allowDecimal = true)), 0.0)
        assertEquals(1.5, requireNotNull(parseAdjustStockCount("1,5", allowDecimal = true)), 0.0)
    }

    @Test
    fun parseAdjustStockCountRejectsDecimalSeparatorsWhenDecimalsAreDisabled() {
        assertNull(parseAdjustStockCount("1.5", allowDecimal = false))
        assertNull(parseAdjustStockCount("1,5", allowDecimal = false))
        assertEquals(15.0, requireNotNull(parseAdjustStockCount("15", allowDecimal = false)), 0.0)
    }

    @Test
    fun parseAdjustStockCount_acceptsZero() {
        assertEquals(0.0, requireNotNull(parseAdjustStockCount("0", allowDecimal = true)), 0.0)
        assertEquals(0.0, requireNotNull(parseAdjustStockCount("0", allowDecimal = false)), 0.0)
    }

    @Test
    fun sanitizeAdjustStockCountTextAllowsOneDecimalSeparatorForTablets() {
        assertEquals("12.5", sanitizeAdjustStockCountText("12.5", allowDecimal = true))
        assertEquals("12,5", sanitizeAdjustStockCountText("12,5", allowDecimal = true))
        assertEquals("12.56", sanitizeAdjustStockCountText("12.5.6", allowDecimal = true))
        assertEquals("125", sanitizeAdjustStockCountText("12.5", allowDecimal = false))
    }

    @Test
    fun stepAdjustStockCountTextPreservesDecimalRemainderAndClampsAtZero() {
        assertEquals("2.5", stepAdjustStockCountText("1.5", delta = 1, allowDecimal = true))
        assertEquals("0.5", stepAdjustStockCountText("1.5", delta = -1, allowDecimal = true))
        assertEquals("0", stepAdjustStockCountText("0.25", delta = -1, allowDecimal = true))
    }

    @Test
    fun displayCountUsesProvidedLocaleWithoutFixedTrailingZeros() {
        assertEquals("1,5", formatAdjustStockCount(1.5, Locale.GERMANY))
        assertEquals("1", formatAdjustStockCount(1.0, Locale.GERMANY))
    }

    @Test
    fun adjustPreviewRunwayTextReturnsNullWhenRunwayCannotBeCalculated() {
        assertNull(adjustPreviewRunwayText(RunwayProjection.NoSchedule))
    }

    @Test
    fun adjustStockAfterUnitUsesInventoryPluralResourceForContainerPreparations() {
        assertEquals(
            R.plurals.stock_count_vials,
            adjustStockAfterUnitPluralRes(
                MedicinePreparation.InjectionMultiUseVial(
                    concentrationMgPerMl = 20.0,
                    vialVolumeMl = 5.0,
                ),
            ),
        )
        assertEquals(
            R.plurals.stock_count_containers,
            adjustStockAfterUnitPluralRes(
                MedicinePreparation.GelContainer(
                    concentrationPercent = 0.06,
                    containerWeightGrams = 80.0,
                ),
            ),
        )
        assertNull(adjustStockAfterUnitPluralRes(MedicinePreparation.PatchOff))
    }
}
