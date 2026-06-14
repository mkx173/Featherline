package com.mkx.hrttracker.data.importer

import com.mkx.hrttracker.data.local.BloodTestPanelEntity
import com.mkx.hrttracker.data.local.BloodTestPanelWithResultsEntity
import com.mkx.hrttracker.data.local.BloodTestResultEntity
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.toEntity
import com.mkx.hrttracker.data.repository.toStorageFields
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.medication.MedicationGelApplicationArea
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicineStock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalImportService @Inject constructor(
    private val parser: ExternalImportParser,
    private val databaseHolder: DatabaseHolder,
    private val homeSnapshotRepository: HomeSnapshotRepository,
) {
    suspend fun buildPreview(
        json: String,
        now: Instant = Instant.now(),
    ): ExternalImportPreview {
        val parseResult = parser.parse(json)
        val database = databaseHolder.get()
        val sourceApp = parseResult.sourceApp.storageValue
        val medicationDao = database.medicationLogDao()
        val medicineDao = database.medicineDao()
        val bloodTestDao = database.bloodTestDao()

        var medicationRowsToCreate = 0
        var medicationRowsToUpdate = 0
        parseResult.medicationDoses.forEach { dose ->
            if (
                medicationDao.getImportedEntry(
                    sourceApp = sourceApp,
                    externalId = dose.provenance.externalId,
                ) == null
            ) {
                medicationRowsToCreate += 1
            } else {
                medicationRowsToUpdate += 1
            }
        }

        var labRowsToCreate = 0
        var labRowsToUpdate = 0
        val labWarnings = mutableListOf<ExternalImportWarning>()
        parseResult.labResults.forEach { result ->
            val targetPanel = bloodTestDao.getImportedPanel(
                sourceApp = sourceApp,
                panelKey = result.panelProvenance.panelKey,
            )
            if (targetPanel.hasUserOwnedConflict(result)) {
                labWarnings += result.userConflictWarning()
                return@forEach
            }
            if (
                bloodTestDao.getImportedResult(
                    sourceApp = sourceApp,
                    externalId = result.provenance.externalId,
                ) == null
            ) {
                labRowsToCreate += 1
            } else {
                labRowsToUpdate += 1
            }
        }

        val (importedMedicinesToReuse, importedMedicinesToCreate) =
            parseResult.distinctMedicineIdentities().partition { identity ->
                medicineDao.getImportedByIdentityKey(identity.identityKey) != null
            }

        return ExternalImportPreview(
            parseResult = parseResult,
            sourceAppLabel = parseResult.sourceApp.label,
            medicationRowsToCreate = medicationRowsToCreate,
            medicationRowsToUpdate = medicationRowsToUpdate,
            labRowsToCreate = labRowsToCreate,
            labRowsToUpdate = labRowsToUpdate,
            importedMedicinesToCreate = importedMedicinesToCreate,
            importedMedicinesToReuse = importedMedicinesToReuse,
            warnings = parseResult.warnings + labWarnings,
        )
    }

    suspend fun commit(
        preview: ExternalImportPreview,
        now: Instant = Instant.now(),
    ): ExternalImportCommitResult {
        return homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                commitInTransaction(
                    preview = preview,
                    database = database,
                    now = now,
                )
            }
        }
    }

    private suspend fun commitInTransaction(
        preview: ExternalImportPreview,
        database: HrtTrackerDatabase,
        now: Instant,
    ): ExternalImportCommitResult {
        val parseResult = preview.parseResult
        val sourceApp = parseResult.sourceApp.storageValue
        val medicineDao = database.medicineDao()
        val medicationDao = database.medicationLogDao()
        val bloodTestDao = database.bloodTestDao()
        val nowEpochMillis = now.toEpochMilli()

        var importedMedicinesCreated = 0
        var importedMedicinesReused = 0
        val importedMedicineByIdentityKey = mutableMapOf<String, MedicineEntity>()
        parseResult.distinctMedicineIdentities().forEach { identity ->
            val existing = medicineDao.getImportedByIdentityKey(identity.identityKey)
            if (existing != null) {
                importedMedicinesReused += 1
                importedMedicineByIdentityKey[identity.identityKey] = existing
            } else {
                val created = Medicine(
                    uuid = UUID.randomUUID(),
                    selection = identity.selection,
                    category = identity.category,
                    preparation = identity.preparation,
                    displayName = identity.displayName,
                    identityKey = identity.identityKey,
                    createdAt = now,
                    updatedAt = now,
                    archivedAt = null,
                    displayDoseUnit = MedicineDisplayDoseUnit.MG,
                    stock = MedicineStock(),
                    importedFromExternalTracker = true,
                ).toEntity()
                medicineDao.insert(created)
                importedMedicinesCreated += 1
                importedMedicineByIdentityKey[identity.identityKey] = created
            }
        }

        var medicationRowsCreated = 0
        var medicationRowsUpdated = 0
        parseResult.medicationDoses.forEach { dose ->
            val existing = medicationDao.getImportedEntry(
                sourceApp = sourceApp,
                externalId = dose.provenance.externalId,
            )
            if (existing == null) {
                medicationRowsCreated += 1
            } else {
                medicationRowsUpdated += 1
            }
            medicationDao.insertEntry(
                dose.toEntity(
                    uuid = existing?.uuid ?: UUID.randomUUID().toString(),
                    medicineUuid = dose.medicineIdentity?.let { identity ->
                        checkNotNull(importedMedicineByIdentityKey[identity.identityKey]) {
                            "Imported medicine ${identity.identityKey} was not resolved."
                        }.uuid
                    },
                    sourceApp = sourceApp,
                )
            )
        }

        val labCommitResult = reconcileLabResults(
            database = database,
            sourceApp = sourceApp,
            labResults = parseResult.labResults,
            nowEpochMillis = nowEpochMillis,
        )

        return ExternalImportCommitResult(
            sourceAppLabel = preview.sourceAppLabel,
            medicationRowsCreated = medicationRowsCreated,
            medicationRowsUpdated = medicationRowsUpdated,
            labRowsCreated = labCommitResult.created,
            labRowsUpdated = labCommitResult.updated,
            importedMedicinesCreated = importedMedicinesCreated,
            importedMedicinesReused = importedMedicinesReused,
            warnings = parseResult.warnings + labCommitResult.warnings,
        )
    }

    private suspend fun reconcileLabResults(
        database: HrtTrackerDatabase,
        sourceApp: String,
        labResults: List<ExternalImportCandidate.LabResult>,
        nowEpochMillis: Long,
    ): LabCommitResult {
        if (labResults.isEmpty()) {
            return LabCommitResult(created = 0, updated = 0, warnings = emptyList())
        }

        val bloodTestDao = database.bloodTestDao()
        val panelStatesByUuid = linkedMapOf<String, MutableImportedPanel>()
        val panelUuidByKey = mutableMapOf<Long, String>()
        bloodTestDao.getImportedPanels(sourceApp).forEach { panelWithResults ->
            panelStatesByUuid[panelWithResults.panel.uuid] = MutableImportedPanel(
                panel = panelWithResults.panel,
                results = panelWithResults.results
                    .sortedBy(BloodTestResultEntity::displayOrder)
                    .toMutableList(),
            )
            panelWithResults.panel.importPanelKey?.let { panelKey ->
                panelUuidByKey[panelKey] = panelWithResults.panel.uuid
            }
        }
        val importedResultsByExternalId = panelStatesByUuid.values
            .flatMap { panel -> panel.results }
            .mapNotNull { result ->
                result.importExternalId?.let { externalId -> externalId to result }
            }
            .toMap()

        var created = 0
        var updated = 0
        val warnings = mutableListOf<ExternalImportWarning>()
        val affectedPanelUuids = linkedSetOf<String>()
        val targetPanelUuids = linkedSetOf<String>()
        labResults.forEach { labResult ->
            val targetState = panelStateFor(
                labResult = labResult,
                sourceApp = sourceApp,
                panelStatesByUuid = panelStatesByUuid,
                panelUuidByKey = panelUuidByKey,
                nowEpochMillis = nowEpochMillis,
            )

            if (targetState.hasUserOwnedConflict(labResult)) {
                warnings += labResult.userConflictWarning()
                return@forEach
            }

            targetPanelUuids += targetState.panel.uuid
            affectedPanelUuids += targetState.panel.uuid

            val existingResult = importedResultsByExternalId[labResult.provenance.externalId]
                ?: bloodTestDao.getImportedResult(
                    sourceApp = sourceApp,
                    externalId = labResult.provenance.externalId,
                )
            if (existingResult == null) {
                created += 1
            } else {
                updated += 1
                affectedPanelUuids += existingResult.panelUuid
                panelStatesByUuid[existingResult.panelUuid]
                    ?.results
                    ?.removeAll { result -> result.uuid == existingResult.uuid }
            }

            val resultEntity = labResult.toEntity(
                uuid = existingResult?.uuid ?: UUID.randomUUID().toString(),
                panelUuid = targetState.panel.uuid,
                createdAtEpochMillis = existingResult?.createdAtEpochMillis ?: nowEpochMillis,
                sourceApp = sourceApp,
            )
            targetState.results.removeAll { result -> result.uuid == resultEntity.uuid }
            targetState.results += resultEntity
        }

        targetPanelUuids.forEach { panelUuid ->
            bloodTestDao.insertPanel(panelStatesByUuid.getValue(panelUuid).panel)
        }
        affectedPanelUuids.forEach { panelUuid ->
            val state = panelStatesByUuid[panelUuid] ?: return@forEach
            val userOwnedMaxDisplayOrder = state.results
                .filter { result -> result.isUserOwned() }
                .maxOfOrNull(BloodTestResultEntity::displayOrder)
            val firstImportedDisplayOrder = (userOwnedMaxDisplayOrder ?: -1) + 1
            val normalizedImportedResults = state.results
                .filterNot { result -> result.isUserOwned() }
                .distinctBy(BloodTestResultEntity::uuid)
                .mapIndexed { index, result ->
                    result.copy(
                        panelUuid = panelUuid,
                        displayOrder = firstImportedDisplayOrder + index,
                    )
                }
            if (normalizedImportedResults.isNotEmpty()) {
                bloodTestDao.insertResults(normalizedImportedResults)
            }
        }
        bloodTestDao.deleteEmptyImportedPanels()

        return LabCommitResult(
            created = created,
            updated = updated,
            warnings = warnings,
        )
    }

    private fun panelStateFor(
        labResult: ExternalImportCandidate.LabResult,
        sourceApp: String,
        panelStatesByUuid: MutableMap<String, MutableImportedPanel>,
        panelUuidByKey: MutableMap<Long, String>,
        nowEpochMillis: Long,
    ): MutableImportedPanel {
        val panelKey = labResult.panelProvenance.panelKey
        val existingUuid = panelUuidByKey[panelKey]
        if (existingUuid != null) {
            val state = panelStatesByUuid.getValue(existingUuid)
            state.panel = state.panel.copy(
                collectedAtInstantEpochMillis = labResult.collectedAtEpochMillis,
                collectedAtTimeZoneId = labResult.collectedAtTimeZoneId,
                updatedAtEpochMillis = nowEpochMillis,
                importSourceApp = sourceApp,
                importPanelKey = panelKey,
            )
            return state
        }

        val panel = BloodTestPanelEntity(
            uuid = UUID.randomUUID().toString(),
            collectedAtInstantEpochMillis = labResult.collectedAtEpochMillis,
            collectedAtTimeZoneId = labResult.collectedAtTimeZoneId,
            notes = null,
            timeSinceLastEstradiolDoseMillis = null,
            timeSinceLastTestosteroneDoseMillis = null,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
            importSourceApp = sourceApp,
            importPanelKey = panelKey,
        )
        val state = MutableImportedPanel(
            panel = panel,
            results = mutableListOf(),
        )
        panelStatesByUuid[panel.uuid] = state
        panelUuidByKey[panelKey] = panel.uuid
        return state
    }

    private fun BloodTestResultEntity.isUserOwned(): Boolean {
        return importSourceApp == null && importExternalId == null
    }

    private fun BloodTestPanelWithResultsEntity?.hasUserOwnedConflict(
        labResult: ExternalImportCandidate.LabResult,
    ): Boolean {
        return this?.results?.hasUserOwnedConflict(labResult) == true
    }

    private fun List<BloodTestResultEntity>.hasUserOwnedConflict(
        labResult: ExternalImportCandidate.LabResult,
    ): Boolean {
        return any { result ->
            result.isUserOwned() && result.builtinAnalyteKey == labResult.analyteKey.storageValue
        }
    }

    private fun MutableImportedPanel.hasUserOwnedConflict(
        labResult: ExternalImportCandidate.LabResult,
    ): Boolean {
        return results.hasUserOwnedConflict(labResult)
    }

    private fun ExternalImportCandidate.LabResult.userConflictWarning(): ExternalImportWarning {
        return ExternalImportWarning(
            reason = ExternalImportWarningReason.LAB_USER_CONFLICT,
            externalId = provenance.externalId,
            rowIndex = null,
            message = "Skipped imported lab result because the target imported panel contains a user-created result for the same analyte.",
        )
    }

    private fun ExternalImportCandidate.MedicationDose.toEntity(
        uuid: String,
        medicineUuid: String?,
        sourceApp: String,
    ): MedicationLogEntryEntity {
        val doseInstructionFields = doseInstruction.toStorageFields()
        return MedicationLogEntryEntity(
            uuid = uuid,
            category = category.name,
            medicineUuid = medicineUuid,
            applicationType = applicationType.name,
            doseInstructionKind = doseInstructionFields.doseInstructionKind,
            tabletFractionNumerator = doseInstructionFields.tabletFractionNumerator,
            tabletFractionDenominator = doseInstructionFields.tabletFractionDenominator,
            doseVolumeMl = doseInstructionFields.doseVolumeMl,
            doseWeightGrams = doseInstructionFields.doseWeightGrams,
            equivalentE2Mg = equivalentE2Mg,
            sourceGroupUuid = null,
            scheduleTimeUuid = null,
            appliedAtEpochMillis = appliedAtEpochMillis,
            appliedAtTimeZoneId = appliedAtTimeZoneId,
            scheduledForIso = null,
            count = 1,
            gelApplicationArea = MedicationGelApplicationArea.DEFAULT.name,
            doseAmountDelta = null,
            importSourceApp = sourceApp,
            importExternalId = provenance.externalId,
        )
    }

    private fun ExternalImportCandidate.LabResult.toEntity(
        uuid: String,
        panelUuid: String,
        createdAtEpochMillis: Long,
        sourceApp: String,
    ): BloodTestResultEntity {
        return BloodTestResultEntity(
            uuid = uuid,
            panelUuid = panelUuid,
            createdAtEpochMillis = createdAtEpochMillis,
            displayOrder = 0,
            builtinAnalyteKey = analyteKey.storageValue,
            customAnalyteUuid = null,
            value = value,
            unitSnapshot = unitKey.storageValue,
            canonicalValue = BloodTestCatalog.toCanonical(
                analyteKey = analyteKey,
                value = value,
                unit = unitKey,
            ),
            importSourceApp = sourceApp,
            importExternalId = provenance.externalId,
        )
    }

    private fun ExternalImportParseResult.distinctMedicineIdentities(): List<ImportedMedicineIdentity> {
        return medicationDoses
            .mapNotNull(ExternalImportCandidate.MedicationDose::medicineIdentity)
            .distinctBy(ImportedMedicineIdentity::identityKey)
    }

    private data class MutableImportedPanel(
        var panel: BloodTestPanelEntity,
        val results: MutableList<BloodTestResultEntity>,
    )

    private data class LabCommitResult(
        val created: Int,
        val updated: Int,
        val warnings: List<ExternalImportWarning>,
    )
}

private val ExternalTrackerSourceApp.label: String
    get() = when (this) {
        ExternalTrackerSourceApp.TRANSMTF -> "Transfem Science"
        ExternalTrackerSourceApp.OYAMA -> "Oyama"
        ExternalTrackerSourceApp.NOMTF -> "NoMTF"
        ExternalTrackerSourceApp.TRANSMTF_COMPATIBLE -> "Transfem-compatible"
    }
