package com.mkx.hrttracker.ui.catalog.stock

import androidx.compose.ui.graphics.Color
import com.mkx.hrttracker.model.medication.MedicineStockState
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
        val primaryContainer = Color(0xFF222222)
        val tertiary = Color(0xFF333333)
        val tertiaryContainer = Color(0xFF444444)
        val error = Color(0xFF555555)
        val errorContainer = Color(0xFF666666)
        val secondary = Color(0xFF777777)
        val secondaryContainer = Color(0xFF888888)

        assertEquals(
            StockSectionProgressIndicatorColors(
                color = tertiary,
                trackColor = tertiaryContainer,
            ),
            stockSectionProgressIndicatorColors(
                state = MedicineStockState.USER_LOW,
                primary = primary,
                primaryContainer = primaryContainer,
                tertiary = tertiary,
                tertiaryContainer = tertiaryContainer,
                error = error,
                errorContainer = errorContainer,
                secondary = secondary,
                secondaryContainer = secondaryContainer,
            ),
        )
        assertEquals(
            StockSectionProgressIndicatorColors(
                color = tertiary,
                trackColor = tertiaryContainer,
            ),
            stockSectionProgressIndicatorColors(
                state = MedicineStockState.IMMINENT,
                primary = primary,
                primaryContainer = primaryContainer,
                tertiary = tertiary,
                tertiaryContainer = tertiaryContainer,
                error = error,
                errorContainer = errorContainer,
                secondary = secondary,
                secondaryContainer = secondaryContainer,
            ),
        )
        assertEquals(
            StockSectionProgressIndicatorColors(
                color = error,
                trackColor = errorContainer,
            ),
            stockSectionProgressIndicatorColors(
                state = MedicineStockState.OUT,
                primary = primary,
                primaryContainer = primaryContainer,
                tertiary = tertiary,
                tertiaryContainer = tertiaryContainer,
                error = error,
                errorContainer = errorContainer,
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
                primaryContainer = primaryContainer,
                tertiary = tertiary,
                tertiaryContainer = tertiaryContainer,
                error = error,
                errorContainer = errorContainer,
                secondary = secondary,
                secondaryContainer = secondaryContainer,
            ),
        )
        assertEquals(
            StockSectionProgressIndicatorColors(
                color = primary,
                trackColor = primaryContainer,
            ),
            stockSectionProgressIndicatorColors(
                state = MedicineStockState.HEALTHY,
                primary = primary,
                primaryContainer = primaryContainer,
                tertiary = tertiary,
                tertiaryContainer = tertiaryContainer,
                error = error,
                errorContainer = errorContainer,
                secondary = secondary,
                secondaryContainer = secondaryContainer,
            ),
        )
    }
}
