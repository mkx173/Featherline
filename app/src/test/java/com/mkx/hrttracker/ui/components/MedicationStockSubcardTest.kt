package com.mkx.hrttracker.ui.components

import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.medication.testPatchOffMedicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MedicationStockSubcardTest {

    @Test
    fun poolProjectionBuildsOneStockPoolRow() {
        val model = medicationStockSubcardModel(
            projection(
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 4.0,
                    unitsLastTotal = 10.0,
                ),
                runway = RunwayProjection.Days(
                    days = 10,
                    lastFulfillable = LocalDate.of(2026, 6, 6),
                ),
                state = MedicineStockState.HEALTHY,
            ),
        )

        requireNotNull(model)
        assertEquals(R.string.stock_subcard_chip_in_stock, model.chipLabelRes)
        assertEquals(MedicationStockSubcardTone.HEALTHY, model.tone)
        assertEquals(R.string.stock_subcard_runway_days, model.runwayText.resId)
        assertEquals(10, model.runwayText.intArg)
        assertEquals(1, model.rows.size)

        val row = model.rows.single()
        assertEquals(MedicationStockSubcardRowKind.STOCK_POOL, row.kind)
        assertEquals(R.drawable.ic_inventory_2, row.iconRes)
        assertEquals(R.string.stock_subcard_cd_stock_pool, row.contentDescriptionRes)
        assertEquals("4 / 10", row.valueText)
        assertEquals(0.4f, row.progress, 1e-6f)
    }

    @Test
    fun multiUseVialBuildsOpenVialAndSealedRows() {
        val preparation = MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = 20.0,
            vialVolumeMl = 5.0,
        )
        val model = medicationStockSubcardModel(
            projection(
                preparation = preparation,
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 2.0,
                    unitsLastTotal = 4.0,
                    openContainerAmount = 1.25,
                ),
                state = MedicineStockState.USER_LOW,
            ),
        )

        requireNotNull(model)
        assertEquals(R.string.stock_subcard_chip_low, model.chipLabelRes)
        assertEquals(MedicationStockSubcardTone.WARNING, model.tone)
        assertEquals(
            listOf(
                MedicationStockSubcardRowKind.OPEN_VIAL,
                MedicationStockSubcardRowKind.SEALED_VIALS,
            ),
            model.rows.map { it.kind },
        )
        assertEquals("1.25 / 5 mL", model.rows[0].valueText)
        assertEquals(0.25f, model.rows[0].progress, 1e-6f)
        assertEquals("2 / 4", model.rows[1].valueText)
        assertEquals(0.5f, model.rows[1].progress, 1e-6f)
    }

    @Test
    fun gelContainerBuildsOpenContainerAndSealedRows() {
        val preparation = MedicinePreparation.GelContainer(
            concentrationPercent = 0.06,
            containerWeightGrams = 80.0,
        )
        val model = medicationStockSubcardModel(
            projection(
                preparation = preparation,
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 1.0,
                    unitsLastTotal = 2.0,
                    openContainerAmount = 20.0,
                ),
                state = MedicineStockState.IMMINENT,
            ),
        )

        requireNotNull(model)
        assertEquals(R.string.stock_subcard_chip_almost_out, model.chipLabelRes)
        assertEquals(MedicationStockSubcardTone.ERROR, model.tone)
        assertEquals(
            listOf(
                MedicationStockSubcardRowKind.OPEN_CONTAINER,
                MedicationStockSubcardRowKind.SEALED_CONTAINERS,
            ),
            model.rows.map { it.kind },
        )
        assertEquals("20 / 80 g", model.rows[0].valueText)
        assertEquals(0.25f, model.rows[0].progress, 1e-6f)
        assertEquals("1 / 2", model.rows[1].valueText)
        assertEquals(0.5f, model.rows[1].progress, 1e-6f)
    }

    @Test
    fun zeroDenominatorKeepsVisibleEmptyProgressBar() {
        val model = medicationStockSubcardModel(
            projection(
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 4.0,
                    unitsLastTotal = 0.0,
                ),
            ),
        )

        requireNotNull(model)
        val row = model.rows.single()
        assertEquals("4", row.valueText)
        assertEquals(0f, row.progress, 1e-6f)
    }

    @Test
    fun invalidDenominatorShowsCurrentOnlyWithEmptyProgress() {
        val model = medicationStockSubcardModel(
            projection(
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 4.0,
                    unitsLastTotal = Double.NaN,
                ),
            ),
        )

        requireNotNull(model)
        val row = model.rows.single()
        assertEquals("4", row.valueText)
        assertEquals(0f, row.progress, 1e-6f)
    }

    @Test
    fun invalidNumeratorUsesDashWithEmptyProgress() {
        val model = medicationStockSubcardModel(
            projection(
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = Double.POSITIVE_INFINITY,
                    unitsLastTotal = 10.0,
                ),
            ),
        )

        requireNotNull(model)
        val row = model.rows.single()
        assertEquals("- / 10", row.valueText)
        assertEquals(0f, row.progress, 1e-6f)
    }

    @Test
    fun sealedOnlyContainerOmitsOpenRowButKeepsSealedRow() {
        val preparation = MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = 20.0,
            vialVolumeMl = 5.0,
        )
        val model = medicationStockSubcardModel(
            projection(
                preparation = preparation,
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 3.0,
                    unitsLastTotal = 4.0,
                    openContainerAmount = null,
                ),
            ),
        )

        requireNotNull(model)
        assertEquals(1, model.rows.size)
        assertEquals(MedicationStockSubcardRowKind.SEALED_VIALS, model.rows.single().kind)
        assertEquals("3 / 4", model.rows.single().valueText)
        assertEquals(0.75f, model.rows.single().progress, 1e-6f)
    }

    @Test
    fun untrackedAndPatchOffDoNotRender() {
        assertNull(
            medicationStockSubcardModel(
                projection(
                    stock = MedicineStock(
                        trackingEnabled = false,
                        unitsRemaining = 4.0,
                        unitsLastTotal = 10.0,
                    ),
                    state = MedicineStockState.UNTRACKED,
                ),
            ),
        )

        assertNull(
            medicationStockSubcardModel(
                projection(
                    medicine = testPatchOffMedicine(
                        stock = MedicineStock(
                            trackingEnabled = true,
                            unitsRemaining = 1.0,
                            unitsLastTotal = 1.0,
                        ),
                    ),
                    state = MedicineStockState.HEALTHY,
                ),
            ),
        )
    }

    private fun projection(
        medicine: com.mkx.hrttracker.model.medication.Medicine? = null,
        preparation: MedicinePreparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        stock: MedicineStock = medicine?.stock ?: MedicineStock(),
        runway: RunwayProjection = RunwayProjection.BeyondHorizon,
        state: MedicineStockState = MedicineStockState.HEALTHY,
    ): MedicineStockProjection {
        val resolvedMedicine = medicine ?: testMedicine(
            preparation = preparation,
            stock = stock,
        )
        return MedicineStockProjection(
            medicine = resolvedMedicine,
            dosesPerDayMagnitude = 1.0,
            totalStockUnits = stock.unitsRemaining ?: 0.0,
            runway = runway,
            intervalDays = null,
            maxPerAdministration = 1.0,
            state = state,
        )
    }
}
