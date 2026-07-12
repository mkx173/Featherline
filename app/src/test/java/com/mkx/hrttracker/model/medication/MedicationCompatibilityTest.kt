package com.mkx.hrttracker.model.medication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class MedicationCompatibilityTest {
    @Test
    fun preparationType_form_mapsEveryPreparationType() {
        assertForm(MedicinePreparationType.PILL, MedicinePreparationForm.TABLET)
        assertForm(MedicinePreparationType.CAPSULE, MedicinePreparationForm.CAPSULE)
        assertForm(
            MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
            MedicinePreparationForm.INJECTION
        )
        assertForm(
            MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
            MedicinePreparationForm.INJECTION
        )
        assertForm(MedicinePreparationType.GEL_SACHET, MedicinePreparationForm.GEL)
        assertForm(MedicinePreparationType.GEL_CONTAINER, MedicinePreparationForm.GEL)
        assertForm(MedicinePreparationType.PATCH, MedicinePreparationForm.PATCH)
        assertForm(MedicinePreparationType.PATCH_OFF, MedicinePreparationForm.PATCH)
    }

    @Test
    fun applicationTypeCompatibility_matchesPreparationTable() {
        assertTrue(MedicationApplicationType.ORAL.isCompatibleWith(MedicinePreparationType.PILL))
        assertTrue(MedicationApplicationType.SUBLINGUAL.isCompatibleWith(MedicinePreparationType.PILL))
        assertFalse(MedicationApplicationType.INJECTION.isCompatibleWith(MedicinePreparationType.PILL))

        assertTrue(MedicationApplicationType.ORAL.isCompatibleWith(MedicinePreparationType.CAPSULE))
        assertFalse(MedicationApplicationType.SUBLINGUAL.isCompatibleWith(MedicinePreparationType.CAPSULE))

        assertTrue(MedicationApplicationType.INJECTION.isCompatibleWith(MedicinePreparationType.INJECTION_SINGLE_USE_VIAL))
        assertTrue(MedicationApplicationType.INJECTION.isCompatibleWith(MedicinePreparationType.INJECTION_MULTI_USE_VIAL))
        assertFalse(MedicationApplicationType.ORAL.isCompatibleWith(MedicinePreparationType.INJECTION_SINGLE_USE_VIAL))

        assertTrue(MedicationApplicationType.GEL.isCompatibleWith(MedicinePreparationType.GEL_SACHET))
        assertTrue(MedicationApplicationType.GEL.isCompatibleWith(MedicinePreparationType.GEL_CONTAINER))
        assertFalse(MedicationApplicationType.ORAL.isCompatibleWith(MedicinePreparationType.GEL_SACHET))

        assertTrue(MedicationApplicationType.PATCH_ON.isCompatibleWith(MedicinePreparationType.PATCH))
        assertFalse(MedicationApplicationType.ORAL.isCompatibleWith(MedicinePreparationType.PATCH))

        assertTrue(MedicationApplicationType.PATCH_OFF.isCompatibleWith(MedicinePreparationType.PATCH_OFF))
        assertTrue(MedicationApplicationType.PATCH_OFF.isCompatibleWith(null))
        assertFalse(MedicationApplicationType.ORAL.isCompatibleWith(null))
    }

    @Test
    fun doseInstructionCompatibility_matchesDoseShapeTable() {
        assertTrue(
            DoseInstruction.TabletFraction(1, 1).isCompatibleWith(MedicinePreparationType.PILL)
        )
        assertFalse(DoseInstruction.WholeUnit.isCompatibleWith(MedicinePreparationType.PILL))

        assertTrue(DoseInstruction.WholeUnit.isCompatibleWith(MedicinePreparationType.CAPSULE))
        assertFalse(
            DoseInstruction.TabletFraction(1, 1).isCompatibleWith(MedicinePreparationType.CAPSULE)
        )

        assertTrue(DoseInstruction.WholeUnit.isCompatibleWith(MedicinePreparationType.INJECTION_SINGLE_USE_VIAL))
        assertFalse(
            DoseInstruction.VolumeMl(0.5)
                .isCompatibleWith(MedicinePreparationType.INJECTION_SINGLE_USE_VIAL)
        )

        assertTrue(
            DoseInstruction.VolumeMl(0.5)
                .isCompatibleWith(MedicinePreparationType.INJECTION_MULTI_USE_VIAL)
        )
        assertFalse(DoseInstruction.WholeUnit.isCompatibleWith(MedicinePreparationType.INJECTION_MULTI_USE_VIAL))

        assertTrue(DoseInstruction.WholeUnit.isCompatibleWith(MedicinePreparationType.GEL_SACHET))
        assertFalse(
            DoseInstruction.WeightGrams(1.0).isCompatibleWith(MedicinePreparationType.GEL_SACHET)
        )

        assertTrue(
            DoseInstruction.WeightGrams(1.0).isCompatibleWith(MedicinePreparationType.GEL_CONTAINER)
        )
        assertFalse(DoseInstruction.WholeUnit.isCompatibleWith(MedicinePreparationType.GEL_CONTAINER))

        assertTrue(DoseInstruction.WholeUnit.isCompatibleWith(MedicinePreparationType.PATCH))
        assertFalse(
            DoseInstruction.TabletFraction(1, 1).isCompatibleWith(MedicinePreparationType.PATCH)
        )

        assertTrue(DoseInstruction.Noop.isCompatibleWith(MedicinePreparationType.PATCH_OFF))
        assertTrue(DoseInstruction.Noop.isCompatibleWith(null))
        assertFalse(DoseInstruction.WholeUnit.isCompatibleWith(null))
    }

    @Test
    fun gnrhSingleUseInjection_isInjectionRoute_wholeUnitOnly() {
        assertTrue(
            MedicationApplicationType.INJECTION
                .isCompatibleWith(MedicinePreparationType.INJECTION_SINGLE_USE_VIAL),
        )
        assertTrue(
            DoseInstruction.WholeUnit
                .isCompatibleWith(MedicinePreparationType.INJECTION_SINGLE_USE_VIAL),
        )
        assertFalse(
            DoseInstruction.VolumeMl(1.0)
                .isCompatibleWith(MedicinePreparationType.INJECTION_SINGLE_USE_VIAL),
        )
        assertEquals(
            MedicationApplicationType.INJECTION,
            MedicinePreparationType.INJECTION_SINGLE_USE_VIAL.requiredApplicationType(),
        )
        assertEquals(
            MedicinePreparationForm.INJECTION,
            MedicinePreparationType.INJECTION_SINGLE_USE_VIAL.form(),
        )
    }

    @Test
    fun importedInjectionIsCompatibleOnlyWithInjectionWholeUnit() {
        val preparation = MedicinePreparation.ImportedInjection(
            administeredMg = 5.0,
            ester = MedicationKey.ESTRADIOL_VALERATE,
        )
        val type = preparation.type

        assertTrue(type.form() == MedicinePreparationForm.INJECTION)
        assertTrue(preparation.requiredApplicationType() == MedicationApplicationType.INJECTION)
        assertTrue(MedicationApplicationType.INJECTION.isCompatibleWith(type))
        assertFalse(MedicationApplicationType.ORAL.isCompatibleWith(type))
        assertFalse(MedicationApplicationType.SUBLINGUAL.isCompatibleWith(type))
        assertFalse(MedicationApplicationType.GEL.isCompatibleWith(type))
        assertFalse(MedicationApplicationType.PATCH_ON.isCompatibleWith(type))
        assertFalse(MedicationApplicationType.PATCH_OFF.isCompatibleWith(type))

        assertTrue(DoseInstruction.WholeUnit.isCompatibleWith(type))
        assertFalse(DoseInstruction.TabletFraction(1, 1).isCompatibleWith(type))
        assertFalse(DoseInstruction.VolumeMl(0.5).isCompatibleWith(type))
        assertFalse(DoseInstruction.WeightGrams(1.0).isCompatibleWith(type))
        assertFalse(DoseInstruction.Noop.isCompatibleWith(type))
    }

    @Test
    fun importedGelIsCompatibleOnlyWithGelWholeUnit() {
        val preparation = MedicinePreparation.ImportedGel(appliedEstradiolMg = 1.5)
        val type = preparation.type

        assertTrue(type.form() == MedicinePreparationForm.GEL)
        assertTrue(preparation.requiredApplicationType() == MedicationApplicationType.GEL)
        assertTrue(MedicationApplicationType.GEL.isCompatibleWith(type))
        assertFalse(MedicationApplicationType.ORAL.isCompatibleWith(type))
        assertFalse(MedicationApplicationType.SUBLINGUAL.isCompatibleWith(type))
        assertFalse(MedicationApplicationType.INJECTION.isCompatibleWith(type))
        assertFalse(MedicationApplicationType.PATCH_ON.isCompatibleWith(type))
        assertFalse(MedicationApplicationType.PATCH_OFF.isCompatibleWith(type))

        assertTrue(DoseInstruction.WholeUnit.isCompatibleWith(type))
        assertFalse(DoseInstruction.TabletFraction(1, 1).isCompatibleWith(type))
        assertFalse(DoseInstruction.VolumeMl(0.5).isCompatibleWith(type))
        assertFalse(DoseInstruction.WeightGrams(1.0).isCompatibleWith(type))
        assertFalse(DoseInstruction.Noop.isCompatibleWith(type))
    }

    @Test
    fun importedInjectionRejectsInvalidAmountAndInvalidEster() {
        val invalidAmounts = listOf(
            0.0,
            -1.0,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
        )

        invalidAmounts.forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                MedicinePreparation.ImportedInjection(
                    administeredMg = value,
                    ester = MedicationKey.ESTRADIOL_VALERATE,
                )
            }
        }
        listOf(
            MedicationKey.ESTRADIOL_GEL,
            MedicationKey.ESTRADIOL_PATCH,
            MedicationKey.CYPROTERONE_ACETATE,
        ).forEach { key ->
            assertThrows(IllegalArgumentException::class.java) {
                MedicinePreparation.ImportedInjection(administeredMg = 5.0, ester = key)
            }
        }
    }

    @Test
    fun importedGelRejectsInvalidAppliedEstradiolAmount() {
        val invalidAmounts = listOf(
            0.0,
            -1.0,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
        )

        invalidAmounts.forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                MedicinePreparation.ImportedGel(appliedEstradiolMg = value)
            }
        }
    }

    @Test
    fun requiredApplicationType_returnsNullOnlyForPill() {
        assertTrue(MedicinePreparation.Pill(2.0).requiredApplicationType() == null)
        assertTrue(
            MedicinePreparation.Capsule(100.0)
                .requiredApplicationType() == MedicationApplicationType.ORAL
        )
        assertTrue(
            MedicinePreparation.InjectionSingleUseVial(10.0).requiredApplicationType() ==
                    MedicationApplicationType.INJECTION,
        )
        assertTrue(
            MedicinePreparation.InjectionMultiUseVial(10.0, 5.0).requiredApplicationType() ==
                    MedicationApplicationType.INJECTION,
        )
        assertTrue(
            MedicinePreparation.GelSachet(0.06, 1.0)
                .requiredApplicationType() == MedicationApplicationType.GEL
        )
        assertTrue(
            MedicinePreparation.GelContainer(0.06, 80.0)
                .requiredApplicationType() == MedicationApplicationType.GEL,
        )
        assertTrue(
            MedicinePreparation.Patch(MedicinePreparation.PatchSpecification.TotalMg(1.56))
                .requiredApplicationType() == MedicationApplicationType.PATCH_ON,
        )
        assertTrue(MedicinePreparation.PatchOff.requiredApplicationType() == MedicationApplicationType.PATCH_OFF)
    }

    @Test
    fun medicineRejectsImportedOnlyPreparationWithoutImportedFlag() {
        assertThrows(IllegalArgumentException::class.java) {
            importedMedicine(
                preparation = MedicinePreparation.ImportedInjection(
                    administeredMg = 5.0,
                    ester = MedicationKey.ESTRADIOL_VALERATE,
                ),
                importedFromExternalTracker = false,
            )
        }
    }

    @Test
    fun medicineRejectsImportedOnlyPreparationWithoutExternalTrackerCustomSelection() {
        assertThrows(IllegalArgumentException::class.java) {
            importedMedicine(
                preparation = MedicinePreparation.ImportedInjection(
                    administeredMg = 5.0,
                    ester = MedicationKey.ESTRADIOL_VALERATE,
                ),
                selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL_VALERATE),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            importedMedicine(
                preparation = MedicinePreparation.ImportedGel(appliedEstradiolMg = 1.5),
                selection = MedicineSelection.Custom("NoMTF"),
                applicationType = MedicationApplicationType.GEL,
                compound = "ESTRADIOL_GEL",
                doseKey = "1.5",
            )
        }
    }

    @Test
    fun medicineAllowsImportedCatalogPillAndPatch() {
        val pill = importedMedicine(
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
            selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL_VALERATE),
            applicationType = MedicationApplicationType.ORAL,
            compound = "ESTRADIOL_VALERATE",
            doseKey = "2",
        )
        val patch = importedMedicine(
            preparation = MedicinePreparation.Patch(
                specification = MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(
                    valueMcgPerDay = 100.0,
                ),
            ),
            selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL_PATCH),
            applicationType = MedicationApplicationType.PATCH_ON,
            compound = "ESTRADIOL_PATCH",
            doseKey = "100mcgPerDay",
        )

        assertTrue(pill.importedFromExternalTracker)
        assertTrue(patch.importedFromExternalTracker)
    }

    @Test
    fun medicineRejectsStockTrackingForImportedMedicine() {
        assertThrows(IllegalArgumentException::class.java) {
            importedMedicine(
                preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL),
                importedFromExternalTracker = true,
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 10.0,
                    unitsLastTotal = 10.0,
                ),
            )
        }
    }

    @Test
    fun medicationGroupMedication_rejectsRouteIncompatibleWithPreparation() {
        val medicine = testMedicine(
            preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
        )

        assertThrows(IllegalArgumentException::class.java) {
            MedicationGroupMedication(
                uuid = UUID.randomUUID(),
                medicine = medicine,
                applicationType = MedicationApplicationType.SUBLINGUAL,
                doseInstruction = DoseInstruction.WholeUnit,
            )
        }
    }

    @Test
    fun medicationGroupMedication_rejectsDoseShapeIncompatibleWithPreparation() {
        val medicine = testMedicine(
            preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
        )

        assertThrows(IllegalArgumentException::class.java) {
            MedicationGroupMedication(
                uuid = UUID.randomUUID(),
                medicine = medicine,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
            )
        }
    }

    @Test
    fun medicationLogEntry_rejectsRouteIncompatibleWithPreparation() {
        val medicine = testMedicine(
            preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
        )

        assertThrows(IllegalArgumentException::class.java) {
            MedicationLogEntry(
                uuid = UUID.randomUUID(),
                medicine = medicine,
                category = medicine.category,
                applicationType = MedicationApplicationType.SUBLINGUAL,
                doseInstruction = DoseInstruction.WholeUnit,
                equivalentE2Mg = null,
                sourceGroupUuid = null,
                appliedAt = Instant.EPOCH,
            )
        }
    }

    @Test
    fun medicationLogEntry_rejectsDoseShapeIncompatibleWithPreparation() {
        val medicine = testMedicine(
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )

        assertThrows(IllegalArgumentException::class.java) {
            MedicationLogEntry(
                uuid = UUID.randomUUID(),
                medicine = medicine,
                category = medicine.category,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.WholeUnit,
                equivalentE2Mg = null,
                sourceGroupUuid = null,
                appliedAt = Instant.EPOCH,
            )
        }
    }

    @Test
    fun patchOffNullMedicineFallbackStillWorks() {
        MedicationGroupMedication(
            uuid = UUID.randomUUID(),
            medicine = null,
            applicationType = MedicationApplicationType.PATCH_OFF,
            doseInstruction = DoseInstruction.Noop,
        )

        MedicationLogEntry(
            uuid = UUID.randomUUID(),
            medicine = null,
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_OFF,
            doseInstruction = DoseInstruction.Noop,
            equivalentE2Mg = null,
            sourceGroupUuid = null,
            appliedAt = Instant.EPOCH,
        )
    }

    private fun assertForm(
        preparationType: MedicinePreparationType,
        expectedForm: MedicinePreparationForm,
    ) {
        assertTrue(preparationType.form() == expectedForm)
    }

    private fun importedMedicine(
        preparation: MedicinePreparation,
        selection: MedicineSelection = MedicineSelection.Custom("External tracker"),
        importedFromExternalTracker: Boolean = true,
        stock: MedicineStock = MedicineStock(),
        applicationType: MedicationApplicationType = MedicationApplicationType.INJECTION,
        compound: String = "ESTRADIOL_VALERATE",
        doseKey: String = "5",
    ): Medicine {
        val timestamp = Instant.parse("2026-05-22T00:00:00Z")
        return Medicine(
            uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000"),
            selection = selection,
            category = when (selection) {
                is MedicineSelection.Catalog -> selection.medicationKey.category
                is MedicineSelection.Custom -> MedicationCategory.ESTRADIOL
                is MedicineSelection.PatchOff -> MedicationCategory.ESTRADIOL
            },
            preparation = preparation,
            displayName = null,
            identityKey = MedicineIdentityKey.external(
                sourceApp = "NoMTF",
                applicationType = applicationType,
                compound = compound,
                doseKey = doseKey,
            ),
            createdAt = timestamp,
            updatedAt = timestamp,
            archivedAt = null,
            stock = stock,
            importedFromExternalTracker = importedFromExternalTracker,
        )
    }
}
