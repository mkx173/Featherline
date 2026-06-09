package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.MedicationGroupItemEntity
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.DoseInstructionKind
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class MedicationEntityMappersTest {
    @Test
    fun capsulePreparationRoundTripsThroughMedicineEntityFields() {
        val medicine = testCustomMedicine(
            medicationName = "Progesterone",
            category = MedicationCategory.CUSTOM,
            preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
        )

        val entity = medicine.toEntity()
        val restored = entity.toMedicineModel()

        assertEquals(MedicinePreparationType.CAPSULE.name, entity.preparationType)
        assertEquals(100.0, entity.strengthMgPerTablet!!, 1e-9)
        assertEquals(
            MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
            restored.preparation
        )
    }

    @Test
    fun medicineStock_roundTripsThroughMedicineEntityFields() {
        val stock = MedicineStock(
            trackingEnabled = true,
            unitsRemaining = 12.5,
            unitsLastTotal = 30.0,
            openContainerAmount = 0.75,
            warnAtDaysRemaining = 10,
            generation = 4L,
        )
        val medicine = testMedicine().copy(stock = stock)

        val entity = medicine.toEntity()
        val restored = entity.toMedicineModel()

        assertEquals(true, entity.trackingEnabled)
        assertEquals(12.5, entity.stockUnitsRemaining!!, 1e-9)
        assertEquals(30.0, entity.stockUnitsLastTotal!!, 1e-9)
        assertEquals(0.75, entity.openContainerAmount!!, 1e-9)
        assertEquals(10, entity.warnAtDaysRemaining)
        assertEquals(4L, entity.stockGeneration)
        assertEquals(stock, restored.stock)
    }

    @Test
    fun medicineStock_healsLegacyEmptyOpenContainerWhenSealedStockRemains() {
        val stock = MedicineStock(
            trackingEnabled = true,
            unitsRemaining = 2.0,
            openContainerAmount = 0.0,
        )
        val preparation = MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = 20.0,
            vialVolumeMl = 1.0,
        )
        val medicine = testCustomMedicine(
            medicationName = "Legacy vial",
            category = MedicationCategory.CUSTOM,
            preparation = preparation,
            stock = stock,
        )

        val restored = medicine.toEntity().toMedicineModel()

        assertEquals(1.0, restored.stock.unitsRemaining!!, 1e-9)
        assertEquals(1.0, restored.stock.openContainerAmount!!, 1e-9)
    }

    @Test
    fun medicineStock_healLegacyEmptyOpenContainerDecrementsGaugeDenominator() {
        // Cracking one sealed container for display must also drop unitsLastTotal
        // (the gauge denominator), matching the write-side promote path; otherwise
        // a legacy 30-vial row renders 29/30 instead of 29/29.
        val stock = MedicineStock(
            trackingEnabled = true,
            unitsRemaining = 30.0,
            unitsLastTotal = 30.0,
            openContainerAmount = 0.0,
        )
        val preparation = MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = 20.0,
            vialVolumeMl = 1.0,
        )
        val medicine = testCustomMedicine(
            medicationName = "Legacy vial denominator",
            category = MedicationCategory.CUSTOM,
            preparation = preparation,
            stock = stock,
        )

        val restored = medicine.toEntity().toMedicineModel()

        assertEquals(29.0, restored.stock.unitsRemaining!!, 1e-9)
        assertEquals(1.0, restored.stock.openContainerAmount!!, 1e-9)
        assertEquals(29.0, restored.stock.unitsLastTotal!!, 1e-9)
    }

    @Test
    fun medicineStock_preservesOutNullOpenRepresentationWhenNoSealedStockRemains() {
        val stock = MedicineStock(
            trackingEnabled = true,
            unitsRemaining = 0.0,
            openContainerAmount = null,
        )
        val preparation = MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = 20.0,
            vialVolumeMl = 1.0,
        )
        val medicine = testCustomMedicine(
            medicationName = "Out vial",
            category = MedicationCategory.CUSTOM,
            preparation = preparation,
            stock = stock,
        )

        val restored = medicine.toEntity().toMedicineModel()

        assertEquals(0.0, restored.stock.unitsRemaining!!, 1e-9)
        assertNull(restored.stock.openContainerAmount)
    }

    @Test
    fun groupItemDoseInstructionStorage_roundTripsEveryShape() {
        val cases = listOf(
            DoseInstruction.TabletFraction(1, 4),
            DoseInstruction.WholeUnit,
            DoseInstruction.VolumeMl(0.2),
            DoseInstruction.WeightGrams(2.5),
            DoseInstruction.Noop,
        )

        cases.forEachIndexed { index, instruction ->
            val entity = groupItemEntity(index, instruction)
            assertEquals(instruction, entity.toDoseInstruction())
        }
    }

    @Test
    fun groupItemModel_coercesLegacyPillWholeUnitToWholeTabletFraction() {
        val medicine = testMedicine(
            uuid = UUID.fromString("cccccccc-0000-0000-0000-000000000000"),
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        val entity = groupItemEntity(0, DoseInstruction.WholeUnit)

        val model = entity.toMedicationGroupMedicationModel(
            medicinesByUuid = mapOf(medicine.uuid.toString() to medicine),
        )

        assertEquals(DoseInstruction.TabletFraction(1, 1), model.doseInstruction)
    }

    @Test
    fun logEntryModel_coercesLegacyPillWholeUnitToWholeTabletFraction() {
        val medicine = testMedicine(
            uuid = UUID.fromString("cccccccc-0000-0000-0000-000000000000"),
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        val entity = MedicationLogEntryEntity(
            uuid = "aaaaaaaa-0000-0000-0000-000000000010",
            category = MedicationCategory.ESTRADIOL.name,
            medicineUuid = medicine.uuid.toString(),
            applicationType = MedicationApplicationType.ORAL.name,
            doseInstructionKind = DoseInstructionKind.WHOLE_UNIT.name,
            tabletFractionNumerator = null,
            tabletFractionDenominator = null,
            doseVolumeMl = null,
            doseWeightGrams = null,
            equivalentE2Mg = null,
            sourceGroupUuid = null,
            appliedAtEpochMillis = 0L,
        )

        val model = entity.toMedicationLogEntryModel(
            medicinesByUuid = mapOf(medicine.uuid.toString() to medicine),
        )

        assertEquals(DoseInstruction.TabletFraction(1, 1), model.doseInstruction)
    }

    @Test
    fun toModel_carriesDoseAmountDelta() {
        val entity = MedicationLogEntryEntity(
            uuid = "aaaaaaaa-0000-0000-0000-000000000011",
            category = MedicationCategory.ESTRADIOL.name,
            medicineUuid = null,
            applicationType = MedicationApplicationType.PATCH_OFF.name,
            doseInstructionKind = DoseInstructionKind.NOOP.name,
            tabletFractionNumerator = null,
            tabletFractionDenominator = null,
            doseVolumeMl = null,
            doseWeightGrams = null,
            equivalentE2Mg = null,
            sourceGroupUuid = null,
            appliedAtEpochMillis = 0L,
            doseAmountDelta = 0.1,
        )

        val model = entity.toMedicationLogEntryModel(emptyMap())

        assertEquals(0.1, model.doseAmountDelta)
    }

    private fun groupItemEntity(
        index: Int,
        doseInstruction: DoseInstruction
    ): MedicationGroupItemEntity {
        return MedicationGroupItemEntity(
            uuid = "aaaaaaaa-0000-0000-0000-00000000000$index",
            groupUuid = "bbbbbbbb-0000-0000-0000-000000000000",
            sortOrder = index,
            count = 1,
            medicineUuid = "cccccccc-0000-0000-0000-000000000000",
            applicationType = "ORAL",
            doseInstructionKind = doseInstruction.kind.name,
            tabletFractionNumerator = (doseInstruction as? DoseInstruction.TabletFraction)?.numerator,
            tabletFractionDenominator = (doseInstruction as? DoseInstruction.TabletFraction)?.denominator,
            doseVolumeMl = (doseInstruction as? DoseInstruction.VolumeMl)?.valueMl,
            doseWeightGrams = (doseInstruction as? DoseInstruction.WeightGrams)?.valueGrams,
            gelApplicationArea = "DEFAULT",
        )
    }
}
