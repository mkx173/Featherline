package com.mkx.hrttracker.model.medication

import java.time.Instant
import java.util.UUID

enum class MedicinePreparationType {
    PILL,
    INJECTION_SINGLE_USE_VIAL,
    INJECTION_MULTI_USE_VIAL,
    GEL_SACHET,
    GEL_CONTAINER,
    PATCH;

    companion object {
        fun fromStorageValue(value: String?): MedicinePreparationType {
            return entries.firstOrNull { it.name == value } ?: PILL
        }
    }
}

sealed interface MedicineSelection {
    val kind: MedicationSelectionKind

    data class Catalog(val medicationKey: MedicationKey) : MedicineSelection {
        override val kind: MedicationSelectionKind = MedicationSelectionKind.CATALOG
    }

    data class Custom(val medicationName: String) : MedicineSelection {
        override val kind: MedicationSelectionKind = MedicationSelectionKind.CUSTOM

        val normalizedMedicationName: String
            get() = normalizeCustomMedicationName(medicationName)
    }
}

sealed interface MedicinePreparation {
    val type: MedicinePreparationType

    data class Pill(val strengthMgPerTablet: Double) : MedicinePreparation {
        init {
            require(strengthMgPerTablet.isFinitePositive())
        }

        override val type: MedicinePreparationType = MedicinePreparationType.PILL
    }

    data class InjectionSingleUseVial(val strengthMgPerVial: Double) : MedicinePreparation {
        init {
            require(strengthMgPerVial.isFinitePositive())
        }

        override val type: MedicinePreparationType = MedicinePreparationType.INJECTION_SINGLE_USE_VIAL
    }

    data class InjectionMultiUseVial(
        val concentrationMgPerMl: Double,
        val vialVolumeMl: Double,
    ) : MedicinePreparation {
        init {
            require(concentrationMgPerMl.isFinitePositive())
            require(vialVolumeMl.isFinitePositive())
        }

        override val type: MedicinePreparationType = MedicinePreparationType.INJECTION_MULTI_USE_VIAL
    }

    data class GelSachet(
        val concentrationPercent: Double,
        val sachetWeightGrams: Double,
    ) : MedicinePreparation {
        init {
            require(concentrationPercent.isFinitePositive())
            require(sachetWeightGrams.isFinitePositive())
        }

        override val type: MedicinePreparationType = MedicinePreparationType.GEL_SACHET
    }

    data class GelContainer(
        val concentrationPercent: Double,
        val containerWeightGrams: Double,
    ) : MedicinePreparation {
        init {
            require(concentrationPercent.isFinitePositive())
            require(containerWeightGrams.isFinitePositive())
        }

        override val type: MedicinePreparationType = MedicinePreparationType.GEL_CONTAINER
    }

    data class Patch(val specification: PatchSpecification) : MedicinePreparation {
        override val type: MedicinePreparationType = MedicinePreparationType.PATCH
    }

    sealed interface PatchSpecification {
        data class TotalMg(val valueMg: Double) : PatchSpecification {
            init {
                require(valueMg.isFinitePositive())
            }
        }

        data class ReleaseRateMcgPerDay(val valueMcgPerDay: Double) : PatchSpecification {
            init {
                require(valueMcgPerDay.isFinitePositive())
            }
        }
    }
}

enum class DoseInstructionKind {
    TABLET_FRACTION,
    WHOLE_UNIT,
    VOLUME_ML,
    WEIGHT_GRAMS,
    NOOP;

    companion object {
        fun fromStorageValue(value: String?): DoseInstructionKind {
            return entries.firstOrNull { it.name == value } ?: NOOP
        }
    }
}

sealed interface DoseInstruction {
    val kind: DoseInstructionKind

    data class TabletFraction(
        val numerator: Int,
        val denominator: Int,
    ) : DoseInstruction {
        init {
            require(numerator > 0)
            require(denominator > 0)
        }

        override val kind: DoseInstructionKind = DoseInstructionKind.TABLET_FRACTION
    }

    data object WholeUnit : DoseInstruction {
        override val kind: DoseInstructionKind = DoseInstructionKind.WHOLE_UNIT
    }

    data class VolumeMl(val valueMl: Double) : DoseInstruction {
        init {
            require(valueMl.isFinitePositive())
        }

        override val kind: DoseInstructionKind = DoseInstructionKind.VOLUME_ML
    }

    data class WeightGrams(val valueGrams: Double) : DoseInstruction {
        init {
            require(valueGrams.isFinitePositive())
        }

        override val kind: DoseInstructionKind = DoseInstructionKind.WEIGHT_GRAMS
    }

    data object Noop : DoseInstruction {
        override val kind: DoseInstructionKind = DoseInstructionKind.NOOP
    }
}

data class Medicine(
    val uuid: UUID,
    val selection: MedicineSelection,
    val category: MedicationCategory,
    val preparation: MedicinePreparation,
    val displayName: String?,
    val identityKey: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant?,
) {
    init {
        if (selection is MedicineSelection.Catalog) {
            require(category == selection.medicationKey.category)
        }
    }

    val isArchived: Boolean
        get() = archivedAt != null
}

private fun Double.isFinitePositive(): Boolean {
    return this > 0.0 && !isNaN() && !isInfinite()
}
