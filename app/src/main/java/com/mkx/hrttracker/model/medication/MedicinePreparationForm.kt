package com.mkx.hrttracker.model.medication

enum class MedicinePreparationForm {
    TABLET,
    CAPSULE,
    INJECTION,
    GEL,
    PATCH,
}

fun MedicinePreparationType.form(): MedicinePreparationForm {
    return when (this) {
        MedicinePreparationType.PILL -> MedicinePreparationForm.TABLET
        MedicinePreparationType.CAPSULE -> MedicinePreparationForm.CAPSULE
        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
        MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
        MedicinePreparationType.DEPOT_INJECTION,
        MedicinePreparationType.IMPORTED_INJECTION,
            -> MedicinePreparationForm.INJECTION

        MedicinePreparationType.GEL_SACHET,
        MedicinePreparationType.GEL_CONTAINER,
        MedicinePreparationType.IMPORTED_GEL,
            -> MedicinePreparationForm.GEL

        MedicinePreparationType.PATCH,
        MedicinePreparationType.PATCH_OFF,
            -> MedicinePreparationForm.PATCH
    }
}

fun MedicinePreparationType.requiredApplicationType(): MedicationApplicationType? {
    return when (this) {
        MedicinePreparationType.PILL -> null
        MedicinePreparationType.CAPSULE -> MedicationApplicationType.ORAL
        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
        MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
        MedicinePreparationType.DEPOT_INJECTION,
        MedicinePreparationType.IMPORTED_INJECTION,
            -> MedicationApplicationType.INJECTION

        MedicinePreparationType.GEL_SACHET,
        MedicinePreparationType.GEL_CONTAINER,
        MedicinePreparationType.IMPORTED_GEL,
            -> MedicationApplicationType.GEL

        MedicinePreparationType.PATCH -> MedicationApplicationType.PATCH_ON
        MedicinePreparationType.PATCH_OFF -> MedicationApplicationType.PATCH_OFF
    }
}

fun MedicinePreparation.requiredApplicationType(): MedicationApplicationType? {
    return type.requiredApplicationType()
}

fun MedicationApplicationType.isCompatibleWith(preparation: MedicinePreparationType?): Boolean {
    return when (this) {
        MedicationApplicationType.ORAL -> preparation == MedicinePreparationType.PILL ||
                preparation == MedicinePreparationType.CAPSULE

        MedicationApplicationType.SUBLINGUAL -> preparation == MedicinePreparationType.PILL
        MedicationApplicationType.INJECTION -> preparation == MedicinePreparationType.INJECTION_SINGLE_USE_VIAL ||
                preparation == MedicinePreparationType.INJECTION_MULTI_USE_VIAL ||
                preparation == MedicinePreparationType.DEPOT_INJECTION ||
                preparation == MedicinePreparationType.IMPORTED_INJECTION

        MedicationApplicationType.GEL -> preparation == MedicinePreparationType.GEL_SACHET ||
                preparation == MedicinePreparationType.GEL_CONTAINER ||
                preparation == MedicinePreparationType.IMPORTED_GEL

        MedicationApplicationType.PATCH_ON -> preparation == MedicinePreparationType.PATCH
        MedicationApplicationType.PATCH_OFF -> preparation == MedicinePreparationType.PATCH_OFF ||
                preparation == null
    }
}

fun DoseInstruction.isCompatibleWith(preparation: MedicinePreparationType?): Boolean {
    return when (this) {
        is DoseInstruction.TabletFraction -> preparation == MedicinePreparationType.PILL
        DoseInstruction.WholeUnit -> preparation == MedicinePreparationType.CAPSULE ||
                preparation == MedicinePreparationType.INJECTION_SINGLE_USE_VIAL ||
                preparation == MedicinePreparationType.DEPOT_INJECTION ||
                preparation == MedicinePreparationType.GEL_SACHET ||
                preparation == MedicinePreparationType.PATCH ||
                preparation == MedicinePreparationType.IMPORTED_INJECTION ||
                preparation == MedicinePreparationType.IMPORTED_GEL

        is DoseInstruction.VolumeMl -> preparation == MedicinePreparationType.INJECTION_MULTI_USE_VIAL
        is DoseInstruction.WeightGrams -> preparation == MedicinePreparationType.GEL_CONTAINER
        DoseInstruction.Noop -> preparation == MedicinePreparationType.PATCH_OFF || preparation == null
    }
}
