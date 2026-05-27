package com.mkx.hrttracker.ui.components

import androidx.compose.ui.graphics.Color
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.medication.testPatchOffMedicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class MedicationStockSubcardTest {

    @Test
    fun iconContainerColorStepsAboveSubcardContainerColor() {
        val surfaceContainer = Color(0xFF111111)
        val surfaceContainerHigh = Color(0xFF222222)
        val surfaceContainerHighest = Color(0xFF333333)

        assertEquals(
            surfaceContainerHighest,
            stockSubcardIconContainerColor(
                containerColor = surfaceContainerHigh,
                surfaceContainer = surfaceContainer,
                surfaceContainerHigh = surfaceContainerHigh,
                surfaceContainerHighest = surfaceContainerHighest,
            ),
        )
        assertEquals(
            surfaceContainerHigh,
            stockSubcardIconContainerColor(
                containerColor = surfaceContainer,
                surfaceContainer = surfaceContainer,
                surfaceContainerHigh = surfaceContainerHigh,
                surfaceContainerHighest = surfaceContainerHighest,
            ),
        )
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
            StockSubcardProgressIndicatorColors(
                color = tertiary,
                trackColor = tertiaryContainer,
            ),
            stockSubcardProgressIndicatorColors(
                tone = MedicationStockSubcardTone.WARNING,
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
            StockSubcardProgressIndicatorColors(
                color = error,
                trackColor = errorContainer,
            ),
            stockSubcardProgressIndicatorColors(
                tone = MedicationStockSubcardTone.ERROR,
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
            StockSubcardProgressIndicatorColors(
                color = primary,
                trackColor = primaryContainer,
            ),
            stockSubcardProgressIndicatorColors(
                tone = MedicationStockSubcardTone.HEALTHY,
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
            StockSubcardProgressIndicatorColors(
                color = secondary,
                trackColor = secondaryContainer,
            ),
            stockSubcardProgressIndicatorColors(
                tone = MedicationStockSubcardTone.NEUTRAL,
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
        val runwayText = requireNotNull(model.runwayText)
        assertEquals(R.string.stock_subcard_runway_days, runwayText.resId)
        assertEquals(10, runwayText.intArg)
        assertEquals(1, model.rows.size)

        val row = model.rows.single()
        assertEquals(MedicationStockSubcardRowKind.STOCK_POOL, row.kind)
        assertEquals(R.string.stock_row_label_stock, row.labelRes)
        assertEquals(R.drawable.ic_inventory_2, row.iconRes)
        assertEquals(R.string.stock_subcard_cd_stock_pool, row.contentDescriptionRes)
        assertEquals("4 / 10", row.valueText)
        assertEquals(R.string.stock_unit_tablets, row.valueUnitRes)
        assertEquals(0.4f, row.progress, 1e-6f)
    }

    @Test
    fun poolRowsCarryLocalizedPreparationUnitForEveryPoolPreparationType() {
        val cases = listOf(
            MedicinePreparation.Pill(strengthMgPerTablet = 2.0) to R.string.stock_unit_tablets,
            MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0) to R.string.stock_unit_capsules,
            MedicinePreparation.InjectionSingleUseVial(strengthMgPerVial = 10.0) to
                R.string.stock_unit_vials,
            MedicinePreparation.GelSachet(
                concentrationPercent = 0.06,
                sachetWeightGrams = 1.0,
            ) to R.string.stock_unit_sachets,
            MedicinePreparation.Patch(
                specification = MedicinePreparation.PatchSpecification.TotalMg(1.0),
            ) to R.string.stock_unit_patches,
        )

        cases.forEach { (preparation, expectedUnitRes) ->
            val model = medicationStockSubcardModel(
                projection(
                    preparation = preparation,
                    stock = MedicineStock(
                        trackingEnabled = true,
                        unitsRemaining = 4.0,
                        unitsLastTotal = 10.0,
                    ),
                ),
            )

            requireNotNull(model)
            assertEquals("4 / 10", model.rows.single().valueText)
            assertEquals(expectedUnitRes, model.rows.single().valueUnitRes)
        }
    }

    @Test
    fun beyondHorizonShowsMoreThanOneYear() {
        val model = medicationStockSubcardModel(
            projection(
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 84.0,
                    unitsLastTotal = 84.0,
                ),
                runway = RunwayProjection.BeyondHorizon,
                state = MedicineStockState.HEALTHY,
            ),
        )

        requireNotNull(model)
        assertEquals(R.string.stock_subcard_chip_in_stock, model.chipLabelRes)
        val runwayText = requireNotNull(model.runwayText)
        assertEquals(R.string.stock_subcard_runway_more_than_one_year, runwayText.resId)
        assertNull(runwayText.intArg)
    }

    @Test
    fun unknownStatusOmitsHeaderRow() {
        val model = medicationStockSubcardModel(
            projection(
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 84.0,
                    unitsLastTotal = 84.0,
                ),
                runway = RunwayProjection.NoSchedule,
                state = MedicineStockState.NO_RUNWAY,
            ),
        )

        requireNotNull(model)
        assertNull(model.chipLabelRes)
        assertNull(model.runwayText)
        assertFalse(model.showsHeader)
    }

    @Test
    fun multiUseVialBuildsOpenVialRowWithSealedSupplement() {
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
        assertEquals(1, model.rows.size)

        val row = model.rows.single()
        assertEquals(MedicationStockSubcardRowKind.OPEN_VIAL, row.kind)
        assertEquals(R.string.stock_row_label_current_vial, row.labelRes)
        assertEquals("1.25 / 5", row.valueText)
        assertEquals(R.string.stock_unit_ml, row.valueUnitRes)
        assertEquals(0.25f, row.progress, 1e-6f)
        assertEquals("2", row.sealedSupplement?.countText)
        assertEquals("+2", row.sealedSupplement?.chipText)
        assertEquals(R.drawable.ic_inventory_2, row.sealedSupplement?.iconRes)
        assertEquals(2, row.sealedSupplement?.pluralQuantity)
        assertEquals(R.plurals.stock_subcard_unit_vials, row.sealedSupplement?.unitPluralRes)
    }

    @Test
    fun multiUseVialShowsZeroSealedSupplement() {
        val preparation = MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = 20.0,
            vialVolumeMl = 5.0,
        )
        val model = medicationStockSubcardModel(
            projection(
                preparation = preparation,
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 0.0,
                    unitsLastTotal = 4.0,
                    openContainerAmount = 1.25,
                ),
            ),
        )

        requireNotNull(model)
        val row = model.rows.single()
        assertEquals("0", row.sealedSupplement?.countText)
        assertEquals("+0", row.sealedSupplement?.chipText)
        assertEquals(R.drawable.ic_inventory_2, row.sealedSupplement?.iconRes)
        assertEquals(2, row.sealedSupplement?.pluralQuantity)
        assertEquals(R.plurals.stock_subcard_unit_vials, row.sealedSupplement?.unitPluralRes)
    }

    @Test
    fun gelContainerBuildsOpenContainerRowWithSealedSupplement() {
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
        assertEquals(1, model.rows.size)

        val row = model.rows.single()
        assertEquals(MedicationStockSubcardRowKind.OPEN_CONTAINER, row.kind)
        assertEquals(R.string.stock_row_label_current_container, row.labelRes)
        assertEquals("20 / 80", row.valueText)
        assertEquals(R.string.stock_unit_g, row.valueUnitRes)
        assertEquals(0.25f, row.progress, 1e-6f)
        assertEquals("1", row.sealedSupplement?.countText)
        assertEquals("+1", row.sealedSupplement?.chipText)
        assertEquals(R.drawable.ic_inventory_2, row.sealedSupplement?.iconRes)
        assertEquals(1, row.sealedSupplement?.pluralQuantity)
        assertEquals(R.plurals.stock_subcard_unit_containers, row.sealedSupplement?.unitPluralRes)
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
        assertEquals(R.string.stock_unit_tablets, row.valueUnitRes)
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
        assertEquals(R.string.stock_unit_tablets, row.valueUnitRes)
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
        assertEquals(R.string.stock_unit_tablets, row.valueUnitRes)
        assertEquals(0f, row.progress, 1e-6f)
    }

    @Test
    fun compactCountsUseLocalizedDecimalSeparatorWithoutTrailingZeros() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            val preparation = MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = 20.0,
                vialVolumeMl = 5.0,
            )
            val model = medicationStockSubcardModel(
                projection(
                    preparation = preparation,
                    stock = MedicineStock(
                        trackingEnabled = true,
                        unitsRemaining = 4.0,
                        unitsLastTotal = 10.0,
                        openContainerAmount = 1.25,
                    ),
                ),
            )

            requireNotNull(model)
            val row = model.rows.single()
            assertEquals("1,25 / 5", row.valueText)
            assertEquals(R.string.stock_unit_ml, row.valueUnitRes)
            assertEquals("4", row.sealedSupplement?.countText)
            assertEquals(2, row.sealedSupplement?.pluralQuantity)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun sealedOnlyContainerFallsBackToTotalStockRow() {
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
        assertEquals(MedicationStockSubcardRowKind.STOCK_POOL, model.rows.single().kind)
        assertEquals(R.string.stock_row_label_stock, model.rows.single().labelRes)
        assertEquals("3 / 4", model.rows.single().valueText)
        assertEquals(R.string.stock_unit_vials, model.rows.single().valueUnitRes)
        assertEquals(0.75f, model.rows.single().progress, 1e-6f)
        assertNull(model.rows.single().sealedSupplement)
    }

    @Test
    fun sealedOnlyGelContainerFallbackUsesContainerUnit() {
        val preparation = MedicinePreparation.GelContainer(
            concentrationPercent = 0.06,
            containerWeightGrams = 80.0,
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
        assertEquals(MedicationStockSubcardRowKind.STOCK_POOL, model.rows.single().kind)
        assertEquals("3 / 4", model.rows.single().valueText)
        assertEquals(R.string.stock_unit_containers, model.rows.single().valueUnitRes)
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
