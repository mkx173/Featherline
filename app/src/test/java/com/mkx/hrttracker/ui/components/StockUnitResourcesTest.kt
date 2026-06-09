package com.mkx.hrttracker.ui.components

import android.content.Context
import android.content.res.Configuration
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicinePreparation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StockUnitResourcesTest {

    @Test
    fun stockCountPluralQuantityUsesSingularOnlyForExactlyOne() {
        assertEquals(1, stockCountPluralQuantity(1.0))
        assertEquals(2, stockCountPluralQuantity(0.0))
        assertEquals(2, stockCountPluralQuantity(1.5))
        assertEquals(2, stockCountPluralQuantity(2.0))
    }

    @Test
    fun stockUnitNounPluralResUsesInventoryNounsForPreparations() {
        assertEquals(
            R.plurals.stock_count_tablets,
            stockUnitNounPluralRes(MedicinePreparation.Pill(strengthMgPerTablet = 2.0)),
        )
        assertEquals(
            R.plurals.stock_count_vials,
            stockUnitNounPluralRes(
                MedicinePreparation.InjectionMultiUseVial(
                    concentrationMgPerMl = 20.0,
                    vialVolumeMl = 5.0,
                ),
            ),
        )
        assertNull(stockUnitNounPluralRes(MedicinePreparation.PatchOff))
    }

    @Test
    fun stockUnitNounPluralForUnitResOnlyPluralizesCountableInventoryUnits() {
        assertEquals(
            R.plurals.stock_count_tablets,
            stockUnitNounPluralForUnitRes(R.string.stock_unit_tablets)
        )
        assertEquals(
            R.plurals.stock_count_capsules,
            stockUnitNounPluralForUnitRes(R.string.stock_unit_capsules)
        )
        assertEquals(
            R.plurals.stock_count_patches,
            stockUnitNounPluralForUnitRes(R.string.stock_unit_patches)
        )
        assertEquals(
            R.plurals.stock_count_sachets,
            stockUnitNounPluralForUnitRes(R.string.stock_unit_sachets)
        )
        assertEquals(
            R.plurals.stock_count_vials,
            stockUnitNounPluralForUnitRes(R.string.stock_unit_vials)
        )
        assertEquals(
            R.plurals.stock_count_containers,
            stockUnitNounPluralForUnitRes(R.string.stock_unit_containers)
        )

        assertNull(stockUnitNounPluralForUnitRes(R.string.stock_unit_ml))
        assertNull(stockUnitNounPluralForUnitRes(R.string.stock_unit_g))
    }

    @Test
    fun stockInventoryCountTextUsesContextAppLocaleDecimalSeparator() {
        val context = localizedContext(Locale.GERMANY)

        assertEquals(
            "1,5 tablets",
            stockInventoryCountText(
                context = context,
                preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                count = 1.5,
            ),
        )
    }

    private fun localizedContext(locale: Locale): Context {
        val appContext = RuntimeEnvironment.getApplication().applicationContext
        val configuration = Configuration(appContext.resources.configuration).apply {
            setLocale(locale)
        }
        return appContext.createConfigurationContext(configuration)
    }
}
