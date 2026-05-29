package com.mkx.hrttracker.ui.catalog.stock

import androidx.compose.ui.graphics.Color
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.ui.components.stockInventoryUnitRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockSectionTest {

    @Test
    fun statusIndicatorTrailingContentOnlyShowsWhenChipCanRender() {
        assertFalse(stockSectionShowsStatusIndicator(MedicineStockState.HEALTHY))
        assertFalse(stockSectionShowsStatusIndicator(MedicineStockState.UNTRACKED))
        assertTrue(stockSectionShowsStatusIndicator(MedicineStockState.USER_LOW))
        assertTrue(stockSectionShowsStatusIndicator(MedicineStockState.IMMINENT))
        assertTrue(stockSectionShowsStatusIndicator(MedicineStockState.OUT))
        assertTrue(stockSectionShowsStatusIndicator(MedicineStockState.NO_RUNWAY))
    }

    @Test
    fun progressIndicatorColorsUseToneColorAndContainerTrackColor() {
        val primary = Color(0xFF111111)
        val tertiary = Color(0xFF333333)
        val error = Color(0xFF555555)
        val secondary = Color(0xFF777777)
        val secondaryContainer = Color(0xFF888888)

        assertEquals(
            StockSectionProgressIndicatorColors(
                color = tertiary,
                trackColor = secondaryContainer,
            ),
            stockSectionProgressIndicatorColors(
                state = MedicineStockState.USER_LOW,
                primary = primary,
                tertiary = tertiary,
                error = error,
                secondary = secondary,
                secondaryContainer = secondaryContainer,
            ),
        )
        assertEquals(
            StockSectionProgressIndicatorColors(
                color = error,
                trackColor = secondaryContainer,
            ),
            stockSectionProgressIndicatorColors(
                state = MedicineStockState.IMMINENT,
                primary = primary,
                tertiary = tertiary,
                error = error,
                secondary = secondary,
                secondaryContainer = secondaryContainer,
            ),
        )
        assertEquals(
            StockSectionProgressIndicatorColors(
                color = error,
                trackColor = secondaryContainer,
            ),
            stockSectionProgressIndicatorColors(
                state = MedicineStockState.OUT,
                primary = primary,
                tertiary = tertiary,
                error = error,
                secondary = secondary,
                secondaryContainer = secondaryContainer,
            ),
        )
        assertEquals(
            StockSectionProgressIndicatorColors(
                color = secondary,
                trackColor = secondaryContainer,
            ),
            stockSectionProgressIndicatorColors(
                state = MedicineStockState.NO_RUNWAY,
                primary = primary,
                tertiary = tertiary,
                error = error,
                secondary = secondary,
                secondaryContainer = secondaryContainer,
            ),
        )
        assertEquals(
            StockSectionProgressIndicatorColors(
                color = primary,
                trackColor = secondaryContainer,
            ),
            stockSectionProgressIndicatorColors(
                state = MedicineStockState.HEALTHY,
                primary = primary,
                tertiary = tertiary,
                error = error,
                secondary = secondary,
                secondaryContainer = secondaryContainer,
            ),
        )
    }

    @Test
    fun stockCountTextCarriesLocalizedUnitResource() {
        val countText = stockSectionCountText(
            numerator = 4.0,
            denominator = 10.0,
            unitRes = R.string.stock_unit_tablets,
        )

        assertEquals("4 / 10", countText.valueText)
        assertEquals(R.string.stock_unit_tablets, countText.unitRes)
    }

    @Test
    fun inventoryUnitResourcesUseSealedUnitsForContainerPreparations() {
        assertEquals(
            R.string.stock_unit_vials,
            stockInventoryUnitRes(
                MedicinePreparation.InjectionMultiUseVial(
                    concentrationMgPerMl = 20.0,
                    vialVolumeMl = 5.0,
                ),
            ),
        )
        assertEquals(
            R.string.stock_unit_containers,
            stockInventoryUnitRes(
                MedicinePreparation.GelContainer(
                    concentrationPercent = 0.06,
                    containerWeightGrams = 80.0,
                ),
            ),
        )
    }
}
