package com.mkx.hrttracker.ui.components

import androidx.compose.ui.graphics.Color
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.medication.testPatchOffMedicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val tertiary = Color(0xFF333333)
        val error = Color(0xFF555555)
        val secondary = Color(0xFF777777)
        val secondaryContainer = Color(0xFF888888)

        assertEquals(
            StockSubcardProgressIndicatorColors(
                color = tertiary,
                trackColor = secondaryContainer,
            ),
            stockSubcardProgressIndicatorColors(
                tone = MedicationStockSubcardTone.WARNING,
                primary = primary,
                tertiary = tertiary,
                error = error,
                secondary = secondary,
                secondaryContainer = secondaryContainer,
            ),
        )
        assertEquals(
            StockSubcardProgressIndicatorColors(
                color = error,
                trackColor = secondaryContainer,
            ),
            stockSubcardProgressIndicatorColors(
                tone = MedicationStockSubcardTone.ERROR,
                primary = primary,
                tertiary = tertiary,
                error = error,
                secondary = secondary,
                secondaryContainer = secondaryContainer,
            ),
        )
        assertEquals(
            StockSubcardProgressIndicatorColors(
                color = primary,
                trackColor = secondaryContainer,
            ),
            stockSubcardProgressIndicatorColors(
                tone = MedicationStockSubcardTone.HEALTHY,
                primary = primary,
                tertiary = tertiary,
                error = error,
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
                tertiary = tertiary,
                error = error,
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
        assertEquals(R.plurals.stock_subcard_runway_days, runwayText.pluralResId)
        assertEquals(10, runwayText.intArg)
        assertEquals(1, model.rows.size)

        val row = model.rows.single()
        assertEquals(MedicationStockSubcardRowKind.STOCK_POOL, row.kind)
        assertEquals(R.string.stock_row_label_stock, row.labelRes)
        assertEquals(R.drawable.ic_inventory_2, row.iconRes)
        assertEquals(R.string.stock_subcard_cd_stock_pool, row.contentDescriptionRes)
        assertEquals("4 / 10", row.valueText)
        assertEquals(R.string.stock_unit_tablets, row.valueUnitRes)
        assertEquals(10.0, row.valuePluralCount)
        assertEquals(10.0, row.previewPluralCount)
        assertEquals(0.4f, row.progress, 1e-6f)
    }

    @Test
    fun poolProjectionShowsPostMutationAmountWhenPreviewDoseProvided() {
        val model = medicationStockSubcardModel(
            projection = projection(
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 4.0,
                    unitsLastTotal = 10.0,
                ),
            ),
            mutationPreviewDoseMagnitude = 1.5,
        )

        requireNotNull(model)
        val row = model.rows.single()
        assertEquals("4", row.valueText)
        assertEquals("2.5 / 10", row.previewValueText)
        assertEquals(R.string.stock_unit_tablets, row.valueUnitRes)
        assertEquals(10.0, row.valuePluralCount)
        assertEquals(10.0, row.previewPluralCount)
        assertEquals(0.4f, row.progress, 1e-6f)
    }

    @Test
    fun poolProjectionKeepsPreviewLayoutWhenPreviewDoseIsZero() {
        val model = medicationStockSubcardModel(
            projection = projection(
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 10.0,
                    unitsLastTotal = 10.0,
                ),
            ),
            mutationPreviewDoseMagnitude = 0.0,
        )

        requireNotNull(model)
        val row = model.rows.single()
        assertEquals("10", row.valueText)
        assertEquals("10 / 10", row.previewValueText)
        assertEquals(R.string.stock_unit_tablets, row.valueUnitRes)
        assertEquals(10.0, row.valuePluralCount)
        assertEquals(10.0, row.previewPluralCount)
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
    }

    @Test
    fun openContainerProjectionShowsPostMutationAmountWhenPreviewDoseProvided() {
        val preparation = MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = 20.0,
            vialVolumeMl = 5.0,
        )
        val model = medicationStockSubcardModel(
            projection = projection(
                preparation = preparation,
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 2.0,
                    unitsLastTotal = 4.0,
                    openContainerAmount = 1.25,
                ),
            ),
            mutationPreviewDoseMagnitude = 0.5,
        )

        requireNotNull(model)
        val row = model.rows.single()
        assertEquals("1.25", row.valueText)
        assertEquals("0.75 / 5", row.previewValueText)
        assertEquals(R.string.stock_unit_ml, row.valueUnitRes)
        assertEquals(0.25f, row.progress, 1e-6f)
    }

    @Test
    fun openContainerProjectionKeepsPreviewLayoutWhenPreviewDoseIsZero() {
        val preparation = MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = 20.0,
            vialVolumeMl = 5.0,
        )
        val model = medicationStockSubcardModel(
            projection = projection(
                preparation = preparation,
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 2.0,
                    unitsLastTotal = 4.0,
                    openContainerAmount = 1.25,
                ),
            ),
            mutationPreviewDoseMagnitude = 0.0,
        )

        requireNotNull(model)
        val row = model.rows.single()
        assertEquals("1.25", row.valueText)
        assertEquals("1.25 / 5", row.previewValueText)
        assertEquals(R.string.stock_unit_ml, row.valueUnitRes)
    }

    @Test
    fun openContainerPreviewExactDrainPromotesFreshSealedUnit() {
        val preparation = MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = 20.0,
            vialVolumeMl = 5.0,
        )
        val model = medicationStockSubcardModel(
            projection = projection(
                preparation = preparation,
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 2.0,
                    unitsLastTotal = 4.0,
                    openContainerAmount = 1.25,
                ),
            ),
            mutationPreviewDoseMagnitude = 1.25,
        )

        requireNotNull(model)
        val row = model.rows.single()
        // The dose exactly empties the open vial; the deduction then promotes a
        // sealed unit to a fresh full open container, and the preview mirrors
        // that committed state (5 / 5, one fewer sealed) so preview == commit.
        assertEquals("5 / 5", row.previewValueText)
        assertTrue(row.opensNewContainer)
        assertEquals("2", row.sealedSupplement?.countText)
        assertEquals("1", row.sealedSupplement?.previewCountText)
        // Icon no longer swaps: the two-bar treatment carries the "opens a new
        // container" meaning, so the droplet stays mid.
        assertEquals(R.drawable.ic_humidity_mid, row.iconRes)
    }

    @Test
    fun openContainerPreviewStraddleCarriesDregAndOpensSealedUnit() {
        val preparation = MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = 20.0,
            vialVolumeMl = 10.0,
        )
        val model = medicationStockSubcardModel(
            projection = projection(
                preparation = preparation,
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 1.0,
                    openContainerAmount = 0.1,
                ),
            ),
            mutationPreviewDoseMagnitude = 0.25,
        )

        requireNotNull(model)
        val row = model.rows.single()
        // 0.1 dreg carried, 0.15 residual drawn from the cracked unit:
        // 10 - (0.25 - 0.1) = 9.85, and the last sealed unit is consumed.
        assertEquals("9.85 / 10", row.previewValueText)
        assertTrue(row.opensNewContainer)
        assertEquals("1", row.sealedSupplement?.countText)
        assertEquals("0", row.sealedSupplement?.previewCountText)
        assertEquals(R.drawable.ic_humidity_mid, row.iconRes)
    }

    @Test
    fun openContainerWithinVialDoseDoesNotOpenNewContainer() {
        val preparation = MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = 20.0,
            vialVolumeMl = 5.0,
        )
        val model = medicationStockSubcardModel(
            projection = projection(
                preparation = preparation,
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 2.0,
                    unitsLastTotal = 4.0,
                    openContainerAmount = 1.25,
                ),
            ),
            mutationPreviewDoseMagnitude = 0.5,
        )

        requireNotNull(model)
        val row = model.rows.single()
        // Dose fits within the open vial: no crack, no icon swap, no sealed
        // preview, and the before -> after value keeps both ends.
        assertEquals("1.25", row.valueText)
        assertEquals("0.75 / 5", row.previewValueText)
        assertFalse(row.opensNewContainer)
        assertNull(row.sealedSupplement?.previewCountText)
        assertEquals(R.drawable.ic_humidity_mid, row.iconRes)
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
