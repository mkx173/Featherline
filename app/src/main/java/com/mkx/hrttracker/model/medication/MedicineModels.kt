package com.mkx.hrttracker.model.medication

import java.time.Instant
import java.util.UUID

enum class MedicinePreparationType {
    PILL,
    CAPSULE,
    INJECTION_SINGLE_USE_VIAL,
    INJECTION_MULTI_USE_VIAL,
    DEPOT_INJECTION,
    GEL_SACHET,
    GEL_CONTAINER,
    IMPORTED_INJECTION,
    IMPORTED_GEL,
    PATCH,

    // Sentinel "preparation" carried by the global PATCH_OFF singleton medicine.
    // The PK simulator routes solely on applicationType for patch removals; this
    // preparation exists so the singleton can show up as a tappable row in the
    // medicine manager. It has no numeric fields.
    PATCH_OFF;

    companion object {
        fun fromStorageValue(value: String?): MedicinePreparationType {
            return entries.firstOrNull { it.name == value } ?: PILL
        }
    }
}

val IMPORTED_INJECTION_ESTER_KEYS = setOf(
    MedicationKey.ESTRADIOL,
    MedicationKey.ESTRADIOL_VALERATE,
    MedicationKey.ESTRADIOL_BENZOATE,
    MedicationKey.ESTRADIOL_CYPIONATE,
    MedicationKey.ESTRADIOL_ENANTHATE,
    MedicationKey.ESTRADIOL_UNDECYLATE,
)

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

    // Identity of the global PATCH_OFF singleton. Reuses CATALOG selectionKind
    // for storage purposes (the entity layer needs *some* enum value) but
    // serializes via the PATCH_OFF preparation type — there is no
    // MedicationSelectionKind variant for it, since adding one would force a
    // when-branch update on every selection-kind switch in the persistence
    // layer for what is effectively a single special-cased row.
    data object PatchOff : MedicineSelection {
        override val kind: MedicationSelectionKind = MedicationSelectionKind.CATALOG
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

    data class Capsule(val strengthMgPerCapsule: Double) : MedicinePreparation {
        init {
            require(strengthMgPerCapsule.isFinitePositive())
        }

        override val type: MedicinePreparationType = MedicinePreparationType.CAPSULE
    }

    data class InjectionSingleUseVial(val strengthMgPerVial: Double) : MedicinePreparation {
        init {
            require(strengthMgPerVial.isFinitePositive())
        }

        override val type: MedicinePreparationType =
            MedicinePreparationType.INJECTION_SINGLE_USE_VIAL
    }

    data class InjectionMultiUseVial(
        val concentrationMgPerMl: Double,
        val vialVolumeMl: Double,
    ) : MedicinePreparation {
        init {
            require(concentrationMgPerMl.isFinitePositive())
            require(vialVolumeMl.isFinitePositive())
        }

        override val type: MedicinePreparationType =
            MedicinePreparationType.INJECTION_MULTI_USE_VIAL
    }

    // Long-acting depot injection (GnRH agonists): one prefilled unit = fixed
    // mg, administered whole. Mechanically an ampule, but kept as its own
    // variant so user-facing wording never says "vial" for a depot syringe.
    data class DepotInjection(val strengthMg: Double) : MedicinePreparation {
        init {
            require(strengthMg.isFinitePositive())
        }

        override val type: MedicinePreparationType = MedicinePreparationType.DEPOT_INJECTION
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

    data class ImportedInjection(
        val administeredMg: Double,
        val ester: MedicationKey,
    ) : MedicinePreparation {
        init {
            require(administeredMg.isFinitePositive())
            require(ester in IMPORTED_INJECTION_ESTER_KEYS)
        }

        override val type: MedicinePreparationType = MedicinePreparationType.IMPORTED_INJECTION
    }

    data class ImportedGel(
        val appliedEstradiolMg: Double,
    ) : MedicinePreparation {
        init {
            require(appliedEstradiolMg.isFinitePositive())
        }

        override val type: MedicinePreparationType = MedicinePreparationType.IMPORTED_GEL
    }

    data class Patch(val specification: PatchSpecification) : MedicinePreparation {
        override val type: MedicinePreparationType = MedicinePreparationType.PATCH
    }

    // Sentinel preparation carried by the global PATCH_OFF singleton medicine.
    // Has no numeric fields, no per-unit mg, and is compatible only with the
    // PATCH_OFF application route. The PK simulator routes patch removals on
    // applicationType alone, so this carries no PK-relevant data.
    data object PatchOff : MedicinePreparation {
        override val type: MedicinePreparationType = MedicinePreparationType.PATCH_OFF
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
    // The unit a custom medicine's raw-mass fields (pill strength, single-use
    // vial strength, patch total mg) are entered and displayed in. Catalog
    // medicines always store MG; the picker only renders for custom medicines.
    val displayDoseUnit: MedicineDisplayDoseUnit = MedicineDisplayDoseUnit.MG,
    val stock: MedicineStock,
    val importedFromExternalTracker: Boolean = false,
) {
    init {
        if (selection is MedicineSelection.Catalog) {
            require(category == selection.medicationKey.category)
        }
        // PatchOff selection and PatchOff preparation are biconditional — a
        // hand-constructed Medicine.{selection = Catalog(...), preparation =
        // PatchOff} would pass earlier checks but collide on identityKey at
        // insert time. Guard both directions so fixtures can't bypass it.
        if (selection is MedicineSelection.PatchOff) {
            require(preparation is MedicinePreparation.PatchOff) {
                "PATCH_OFF selection requires PatchOff preparation."
            }
            require(category == MedicationCategory.ESTRADIOL) {
                "PATCH_OFF selection requires ESTRADIOL category."
            }
        }
        if (preparation is MedicinePreparation.PatchOff) {
            require(selection is MedicineSelection.PatchOff) {
                "PatchOff preparation requires PATCH_OFF selection."
            }
        }
        if (
            preparation is MedicinePreparation.ImportedInjection ||
            preparation is MedicinePreparation.ImportedGel
        ) {
            require(importedFromExternalTracker) {
                "Import-only preparations require importedFromExternalTracker."
            }
            require(selection == MedicineSelection.Custom("External tracker")) {
                "Import-only preparations require External tracker custom selection."
            }
            require(stock.trackingEnabled.not()) {
                "Imported external medicines cannot track stock."
            }
        }
        if (importedFromExternalTracker) {
            require(stock.trackingEnabled.not()) {
                "Imported external medicines cannot track stock."
            }
        }
    }

    val isArchived: Boolean
        get() = archivedAt != null
}

private fun Double.isFinitePositive(): Boolean {
    return this > 0.0 && !isNaN() && !isInfinite()
}
