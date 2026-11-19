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
                preparation == MedicinePreparationType.GEL_SACHET ||
                preparation == MedicinePreparationType.PATCH ||
                preparation == MedicinePreparationType.IMPORTED_INJECTION ||
                preparation == MedicinePreparationType.IMPORTED_GEL

        is DoseInstruction.VolumeMl -> preparation == MedicinePreparationType.INJECTION_MULTI_USE_VIAL
        is DoseInstruction.WeightGrams -> preparation == MedicinePreparationType.GEL_CONTAINER
        DoseInstruction.Noop -> preparation == MedicinePreparationType.PATCH_OFF || preparation == null
    }
}

fun MedicationApplicationType.supportsMedicationCountEditor(
    // Three preparation types override their parent application route's default:
    //   INJECTION_SINGLE_USE_VIAL — one ampule per administration; users adjust
    //     the actual drawn amount via the dose delta, not a count, so the editor
    //     is hidden and the saved count is forced to 1.
    //   INJECTION_MULTI_USE_VIAL — dose is the drawn volume; "N injections" is
    //     not how users describe it, so the count editor is hidden and the
    //     saved count is forced to 1.
    //   GEL_SACHET — single-sachet packaging means N sachets at once is a
    //     real per-occurrence choice; the DoseInstructionCalculator already
    //     multiplies the WholeUnit dose by count.
    preparationType: MedicinePreparationType? = null,
): Boolean {
    when (preparationType) {
        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL -> return false
        MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> return false
        MedicinePreparationType.GEL_SACHET -> return true
        else -> Unit
    }
    return when (this) {
        MedicationApplicationType.ORAL,
        MedicationApplicationType.SUBLINGUAL,
        MedicationApplicationType.PATCH_ON,
            // Injection route with an as-yet-unresolved preparation; both concrete
            // injection preparations above hide the count editor, so this only
            // affects transient drafts before a preparation is inferred.
        MedicationApplicationType.INJECTION -> true

        MedicationApplicationType.GEL,
        MedicationApplicationType.PATCH_OFF -> false
    }
}

fun normalizeMedicationCount(
    applicationType: MedicationApplicationType,
    count: Int,
    preparationType: MedicinePreparationType? = null,
): Int {
    return if (applicationType.supportsMedicationCountEditor(preparationType)) {
        count.coerceAtLeast(1)
    } else {
        1
    }
}
