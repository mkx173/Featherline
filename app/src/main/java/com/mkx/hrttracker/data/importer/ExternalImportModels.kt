package com.mkx.hrttracker.data.importer

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection

enum class ExternalTrackerSourceApp(val storageValue: String) {
    TRANSMTF("transmtf"),
    OYAMA("oyama"),
    NOMTF("nomtf"),
    TRANSMTF_COMPATIBLE("transmtf-compatible");

    companion object {
        val storageValues: Set<String> = entries.map { source -> source.storageValue }.toSet()
    }
}

data class ExternalImportProvenance(
    val sourceApp: ExternalTrackerSourceApp,
    val externalId: String,
)

data class ExternalImportPanelProvenance(
    val sourceApp: ExternalTrackerSourceApp,
    val panelKey: Long,
)

sealed interface ExternalImportCandidate {
    data class MedicationDose(
        val provenance: ExternalImportProvenance,
        val appliedAtEpochMillis: Long,
        val appliedAtTimeZoneId: String,
        val category: MedicationCategory,
        val applicationType: MedicationApplicationType,
        val doseInstruction: DoseInstruction,
        val medicineIdentity: ImportedMedicineIdentity?,
        val equivalentE2Mg: Double?,
    ) : ExternalImportCandidate

    data class LabResult(
        val provenance: ExternalImportProvenance,
        val panelProvenance: ExternalImportPanelProvenance,
        val collectedAtEpochMillis: Long,
        val collectedAtTimeZoneId: String,
        val analyteKey: BloodAnalyteKey,
        val unitKey: BloodUnitKey,
        val value: Double,
    ) : ExternalImportCandidate
}

data class ImportedMedicineIdentity(
    val identityKey: String,
    val displayName: String,
    val category: MedicationCategory,
    val selection: MedicineSelection,
    val preparation: MedicinePreparation,
    val applicationType: MedicationApplicationType,
    val doseInstruction: DoseInstruction,
)

data class ExternalImportParseResult(
    val sourceApp: ExternalTrackerSourceApp,
    val exportVersion: String?,
    val exportedAt: String?,
    val medicationDoses: List<ExternalImportCandidate.MedicationDose>,
    val labResults: List<ExternalImportCandidate.LabResult>,
    val warnings: List<ExternalImportWarning>,
)

data class ExternalImportPreview(
    val parseResult: ExternalImportParseResult,
    val sourceAppLabel: String,
    val medicationRowsToCreate: Int,
    val medicationRowsToUpdate: Int,
    val labRowsToCreate: Int,
    val labRowsToUpdate: Int,
    val importedMedicinesToCreate: List<ImportedMedicineIdentity>,
    val importedMedicinesToReuse: List<ImportedMedicineIdentity>,
    val warnings: List<ExternalImportWarning>,
)

data class ExternalImportCommitResult(
    val sourceAppLabel: String,
    val medicationRowsCreated: Int,
    val medicationRowsUpdated: Int,
    val labRowsCreated: Int,
    val labRowsUpdated: Int,
    val importedMedicinesCreated: Int,
    val importedMedicinesReused: Int,
    val warnings: List<ExternalImportWarning>,
)

data class ExternalImportWarning(
    val reason: ExternalImportWarningReason,
    val externalId: String?,
    val rowIndex: Int?,
    val message: String,
    val messageKey: ExternalImportWarningMessageKey? = null,
)

enum class ExternalImportWarningReason {
    MISSING_EXTERNAL_ID,
    DUPLICATE_EXTERNAL_ID,
    MALFORMED_ROW,
    UNSUPPORTED_COMPOUND,
    UNSUPPORTED_CATEGORY,
    UNSUPPORTED_ROUTE,
    AMBIGUOUS_LAB_UNIT,
    DUPLICATE_LAB_RESULT,
    LAB_USER_CONFLICT,
    SOURCE_FALLBACK,
}

enum class ExternalImportWarningMessageKey {
    SOURCE_FALLBACK,
    MEDICATION_NON_OBJECT_ROW,
    MEDICATION_MISSING_ID,
    MEDICATION_DUPLICATE_ID,
    MEDICATION_INVALID_TIME,
    MEDICATION_UNSUPPORTED_ROUTE,
    UNSUPPORTED_ANTIANDROGEN,
    ESTROGEN_UNSUPPORTED_COMPOUND,
    TESTOSTERONE_MEDICATION_ROW,
    RECORD_ONLY_ANTIANDROGEN_UNSUPPORTED,
    NOMTF_RECORD_ONLY_UNSUPPORTED,
    NOMTF_CATEGORY_UNSUPPORTED,
    ANTIANDROGEN_UNSUPPORTED_ROUTE,
    ANTIANDROGEN_INVALID_DOSE,
    ESTROGEN_UNSUPPORTED_ROUTE_COMPOUND,
    ESTROGEN_INVALID_DOSE,
    LAB_NON_OBJECT_ROW,
    LAB_MISSING_ID,
    LAB_DUPLICATE_ID,
    LAB_MALFORMED,
    LAB_AMBIGUOUS_ANALYTE_UNIT,
    LAB_DUPLICATE_ANALYTE_PANEL,
    LAB_USER_CONFLICT,
}

class ExternalImportFatalException(message: String) : IllegalArgumentException(message)
