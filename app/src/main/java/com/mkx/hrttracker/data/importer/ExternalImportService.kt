package com.mkx.hrttracker.data.importer

import com.mkx.hrttracker.data.local.BloodTestDao
import com.mkx.hrttracker.data.local.BloodTestPanelEntity
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
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ExternalImportService"

@Singleton
class ExternalImportService @Inject constructor(
    private val parser: ExternalImportParser,
    private val databaseHolder: DatabaseHolder,
    private val homeSnapshotRepository: HomeSnapshotRepository,
    private val diagnosticsLogger: AppDiagnosticsLogger,
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

        // Bulk-load the existing imported rows once rather than querying per
        // dose and per medicine identity: a preview of N doses across K
        // medicines must not fan out into N+K round-trips on the preview path.
        val existingEntriesByExternalId = medicationDao.getImportedEntries(sourceApp)
            .mapNotNull { entry -> entry.importExternalId?.let { externalId -> externalId to entry } }
            .toMap()

        val distinctIdentities = parseResult.distinctMedicineIdentities()
        val existingMedicinesByIdentityKey = medicineDao
            .getImportedByIdentityKeys(distinctIdentities.map { identity -> identity.identityKey })
            .associateBy { medicine -> medicine.identityKey }
        val (importedMedicinesToReuse, importedMedicinesToCreate) =
            distinctIdentities.partition { identity ->
                identity.identityKey in existingMedicinesByIdentityKey
            }

        // Classify medication rows with the same pass commit uses, so the review
        // summary's created/updated/unchanged tally can never disagree with what
        // commit reports. Preview resolves only already-existing medicines, so a
        // dose that introduces a new medicine has no uuid here and is never counted
        // unchanged — which matches commit's conclusion once it assigns that
        // medicine a fresh uuid that cannot equal the stored row.
        val medicationPlan = planMedicationImport(
            doses = parseResult.medicationDoses,
            existingEntriesByExternalId = existingEntriesByExternalId,
            medicineUuidByIdentityKey = existingMedicinesByIdentityKey
                .mapValues { (_, medicine) -> medicine.uuid },
            sourceApp = sourceApp,
        )

        // Run the same reconciliation commit uses, as a read-only dry run that
        // writes nothing, so the review summary's lab tally and skip warnings can
        // never disagree with what the commit actually does (one algorithm, not
        // two hand-synced count implementations).
        val labPlan = planLabReconciliation(
            bloodTestDao = bloodTestDao,
            sourceApp = sourceApp,
            labResults = parseResult.labResults,
            nowEpochMillis = now.toEpochMilli(),
        )

        val warnings = parseResult.warnings + labPlan.warnings
        logPreviewWarnings(
            sourceApp = parseResult.sourceApp,
            warnings = warnings,
        )

        return ExternalImportPreview(
            parseResult = parseResult,
            sourceAppLabel = parseResult.sourceApp.label,
            medicationRowsToCreate = medicationPlan.created,
            medicationRowsToUpdate = medicationPlan.updated,
            medicationRowsUnchanged = medicationPlan.unchanged,
            labRowsToCreate = labPlan.created,
            labRowsToUpdate = labPlan.updated,
            labRowsUnchanged = labPlan.unchanged,
            importedMedicinesToCreate = importedMedicinesToCreate,
            importedMedicinesToReuse = importedMedicinesToReuse,
            warnings = warnings,
        )
    }

    suspend fun commit(
        preview: ExternalImportPreview,
        now: Instant = Instant.now(),
    ): ExternalImportCommitResult {
        val result = homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                commitInTransaction(
                    preview = preview,
                    database = database,
                    now = now,
                )
            }
        }
        logImportedMedicines(preview, result)
        return result
    }

    private fun logImportedMedicines(
        preview: ExternalImportPreview,
        result: ExternalImportCommitResult,
    ) {
        diagnosticsLogger.info(
            TAG,
            "external_import_committed_medicines " +
                "created=${result.importedMedicinesCreated} " +
                "reused=${result.importedMedicinesReused} " +
                "createdKeys=${preview.importedMedicinesToCreate.joinToString { it.identityKey }} " +
                "reusedKeys=${preview.importedMedicinesToReuse.joinToString { it.identityKey }}",
        )
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

        // Resolve existing imported medicines and dose rows with one bulk query
        // each instead of a query per identity / per dose, mirroring the preview
        // path so the commit does not re-incur the same N+K round-trips.
        val distinctIdentities = parseResult.distinctMedicineIdentities()
        val existingMedicinesByIdentityKey = medicineDao
            .getImportedByIdentityKeys(distinctIdentities.map { identity -> identity.identityKey })
            .associateBy { medicine -> medicine.identityKey }

        var importedMedicinesCreated = 0
        var importedMedicinesReused = 0
        val importedMedicineByIdentityKey = mutableMapOf<String, MedicineEntity>()
        distinctIdentities.forEach { identity ->
            val existing = existingMedicinesByIdentityKey[identity.identityKey]
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

        val existingEntriesByExternalId = medicationDao.getImportedEntries(sourceApp)
            .mapNotNull { entry -> entry.importExternalId?.let { externalId -> externalId to entry } }
            .toMap()

        // Every distinct medicine identity was resolved above (created or reused),
        // so this map covers every dose's medicine; the shared pass therefore sees
        // the real uuid each row will store and tallies exactly as the preview did.
        val medicationPlan = planMedicationImport(
            doses = parseResult.medicationDoses,
            existingEntriesByExternalId = existingEntriesByExternalId,
            medicineUuidByIdentityKey = importedMedicineByIdentityKey
                .mapValues { (_, medicine) -> medicine.uuid },
            sourceApp = sourceApp,
        )
        medicationDao.insertEntries(medicationPlan.entriesToInsert)

        val labCommitResult = reconcileLabResults(
            database = database,
            sourceApp = sourceApp,
            labResults = parseResult.labResults,
            nowEpochMillis = nowEpochMillis,
        )

        return ExternalImportCommitResult(
            sourceAppLabel = preview.sourceAppLabel,
            medicationRowsCreated = medicationPlan.created,
            medicationRowsUpdated = medicationPlan.updated,
            medicationRowsUnchanged = medicationPlan.unchanged,
            labRowsCreated = labCommitResult.created,
            labRowsUpdated = labCommitResult.updated,
            labRowsUnchanged = labCommitResult.unchanged,
            importedMedicinesCreated = importedMedicinesCreated,
            importedMedicinesReused = importedMedicinesReused,
            warnings = parseResult.warnings + labCommitResult.warnings,
        )
    }

    /**
     * Pure classification pass shared by [buildPreview] and [commitInTransaction]:
     * builds the dose row each candidate would store and tallies it as created (no
     * prior row for this external id), unchanged (byte-identical to the stored
     * row), or updated (differs). Preview consumes only the counts; commit also
     * inserts [MedicationImportPlan.entriesToInsert]. One algorithm, so the review
     * summary can never disagree with the commit.
     *
     * [medicineUuidByIdentityKey] holds only medicines that already exist. A dose
     * whose medicine is absent here will mint a fresh medicine on commit, so its
     * candidate cannot match the stored row — it is classified updated/created,
     * never unchanged, identically in both passes.
     */
    private fun planMedicationImport(
        doses: List<ExternalImportCandidate.MedicationDose>,
        existingEntriesByExternalId: Map<String, MedicationLogEntryEntity>,
        medicineUuidByIdentityKey: Map<String, String>,
        sourceApp: String,
    ): MedicationImportPlan {
        var created = 0
        var updated = 0
        var unchanged = 0
        val entriesToInsert = doses.map { dose ->
            val existing = existingEntriesByExternalId[dose.provenance.externalId]
            val identityKey = dose.medicineIdentity?.identityKey
            val resolvedMedicineUuid = identityKey?.let { medicineUuidByIdentityKey[it] }
            val referencesUnresolvedMedicine = identityKey != null && resolvedMedicineUuid == null
            val candidate = dose.toEntity(
                uuid = existing?.uuid ?: UUID.randomUUID().toString(),
                medicineUuid = resolvedMedicineUuid,
                sourceApp = sourceApp,
            )
            when {
                existing == null -> created += 1
                !referencesUnresolvedMedicine && candidate == existing -> unchanged += 1
                else -> updated += 1
            }
            candidate
        }
        return MedicationImportPlan(
            created = created,
            updated = updated,
            unchanged = unchanged,
            entriesToInsert = entriesToInsert,
        )
    }

    private suspend fun reconcileLabResults(
        database: HrtTrackerDatabase,
        sourceApp: String,
        labResults: List<ExternalImportCandidate.LabResult>,
        nowEpochMillis: Long,
    ): LabCommitResult {
        val bloodTestDao = database.bloodTestDao()
        val plan = planLabReconciliation(
            bloodTestDao = bloodTestDao,
            sourceApp = sourceApp,
            labResults = labResults,
            nowEpochMillis = nowEpochMillis,
        )
        // Upsert (not REPLACE) existing panels: an INSERT OR REPLACE on a panel
        // cascade-deletes its results (blood_test_results ON DELETE CASCADE),
        // which would silently drop any user-owned result the user added to an
        // imported panel for a different analyte. upsertPanel updates in place
        // and leaves child results untouched.
        plan.panelsToUpsert.forEach { panel -> bloodTestDao.upsertPanel(panel) }
        if (plan.resultsToUpsert.isNotEmpty()) {
            // A row moved across panels keeps its UUID so the same DB row relocates
            // instead of duplicating. The DAO parks conflicting unique keys and
            // updates that row in place rather than delete/reinsert via REPLACE.
            bloodTestDao.upsertResultsInPlace(plan.resultsToUpsert)
        }
        bloodTestDao.deleteEmptyImportedPanels()

        return LabCommitResult(
            created = plan.created,
            updated = plan.updated,
            unchanged = plan.unchanged,
            warnings = plan.warnings,
        )
    }

    /**
     * Pure planning pass shared by [buildPreview] and [reconcileLabResults]:
     * replays the imported-panel reconciliation against an in-memory copy of the
     * current imported state and returns the create/update tally, skip warnings,
     * and the panel/result rows to upsert — writing nothing. Preview consumes only
     * the counts and warnings; commit additionally applies the upserts. One
     * algorithm, so the review summary can never disagree with the commit.
     */
    private suspend fun planLabReconciliation(
        bloodTestDao: BloodTestDao,
        sourceApp: String,
        labResults: List<ExternalImportCandidate.LabResult>,
        nowEpochMillis: Long,
    ): LabReconciliationPlan {
        if (labResults.isEmpty()) {
            return LabReconciliationPlan()
        }

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
        // Index every existing imported result for this source by external id in
        // one query. Building it from the loaded panels alone would miss a result
        // whose panel is not imported-for-this-source, forcing a per-row DB
        // fallback (an N+1 on the very path the bulk panel load avoids).
        val importedResultsByExternalId = bloodTestDao.getImportedResults(sourceApp)
            .mapNotNull { result ->
                result.importExternalId?.let { externalId -> externalId to result }
            }
            .toMap()

        // Snapshot the stored panels and results before the loop below mutates
        // panel entities (panelStateFor) and result lists, so the upsert sets can
        // drop rows identical to what is already persisted. Without this, a
        // re-import of an unchanged export bumps every visited panel's updatedAt
        // and rewrites byte-identical results in place, dirtying the DB and firing
        // spurious home/widget recomputes despite "nothing changed".
        val originalPanelsByUuid = panelStatesByUuid.mapValues { (_, state) -> state.panel }
        val originalResultsByUuid = buildMap {
            panelStatesByUuid.values.forEach { state ->
                state.results.forEach { result -> put(result.uuid, result) }
            }
            importedResultsByExternalId.values.forEach { result -> put(result.uuid, result) }
        }

        var created = 0
        var updated = 0
        var unchanged = 0
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

            // The schema holds one result per analyte per panel. If the target
            // panel already holds a *different* imported reading (different
            // external id) for this analyte, reject the incoming row rather than
            // silently clobbering the existing reading — the user sees the skip
            // in the review summary and can resolve it. A row updating its own
            // prior reading (same external id) is not a conflict and proceeds.
            if (targetState.conflictsWithDifferentImportedResult(labResult)) {
                warnings += labResult.analyteConflictWarning()
                return@forEach
            }

            targetPanelUuids += targetState.panel.uuid
            affectedPanelUuids += targetState.panel.uuid

            val existingResult = importedResultsByExternalId[labResult.provenance.externalId]
            val resultEntity = labResult.toEntity(
                uuid = existingResult?.uuid ?: UUID.randomUUID().toString(),
                panelUuid = targetState.panel.uuid,
                createdAtEpochMillis = existingResult?.createdAtEpochMillis ?: nowEpochMillis,
                sourceApp = sourceApp,
            )
            if (existingResult != null) {
                // displayOrder is assigned by the reconciliation pass below, not
                // carried in the export, so a reading whose value/unit/analyte and
                // target panel all match the stored row counts as unchanged even if
                // its position within the panel shifts.
                if (existingResult.copy(displayOrder = resultEntity.displayOrder) == resultEntity) {
                    unchanged += 1
                } else {
                    updated += 1
                }
                affectedPanelUuids += existingResult.panelUuid
                panelStatesByUuid[existingResult.panelUuid]
                    ?.results
                    ?.removeAll { result -> result.uuid == existingResult.uuid }
            } else {
                created += 1
            }
            targetState.results.removeAll { result -> result.uuid == resultEntity.uuid }
            targetState.results += resultEntity
        }

        // Recompute the desired imported-result rows for every affected panel,
        // then keep only those that differ from the stored row (new, value-changed,
        // or displayOrder-shifted). Identical rows are dropped so an unchanged
        // re-import writes nothing.
        val resultsToUpsert = affectedPanelUuids.flatMap { panelUuid ->
            val state = panelStatesByUuid[panelUuid] ?: return@flatMap emptyList<BloodTestResultEntity>()
            val userOwnedMaxDisplayOrder = state.results
                .filter { result -> result.isUserOwned() }
                .maxOfOrNull(BloodTestResultEntity::displayOrder)
            val firstImportedDisplayOrder = (userOwnedMaxDisplayOrder ?: -1) + 1
            state.results
                .filterNot { result -> result.isUserOwned() }
                .distinctBy(BloodTestResultEntity::uuid)
                .mapIndexed { index, result ->
                    result.copy(
                        panelUuid = panelUuid,
                        displayOrder = firstImportedDisplayOrder + index,
                    )
                }
        }.filter { result -> originalResultsByUuid[result.uuid] != result }

        // Upsert a panel only when it is new, one of its stored fields changed, or
        // one of its results is being written. Comparing on every field except
        // updatedAtEpochMillis lets an otherwise-identical panel keep its stored
        // timestamp instead of being bumped on a no-op re-import.
        val panelUuidsWithResultWrites =
            resultsToUpsert.mapTo(mutableSetOf(), BloodTestResultEntity::panelUuid)
        val panelsToUpsert = targetPanelUuids.mapNotNull { panelUuid ->
            val candidate = panelStatesByUuid.getValue(panelUuid).panel
            val original = originalPanelsByUuid[panelUuid]
            val changed = original == null ||
                original.copy(updatedAtEpochMillis = candidate.updatedAtEpochMillis) != candidate ||
                panelUuid in panelUuidsWithResultWrites
            candidate.takeIf { changed }
        }

        return LabReconciliationPlan(
            created = created,
            updated = updated,
            unchanged = unchanged,
            warnings = warnings,
            panelsToUpsert = panelsToUpsert,
            resultsToUpsert = resultsToUpsert,
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

    // True when this panel already holds a *different* imported reading (a
    // non-user-owned result with a different external id) for the same analyte.
    // The schema allows one result per analyte per panel, so the incoming row
    // would overwrite that reading — instead it is rejected to preserve it. A
    // result with the same external id is the row's own prior reading, not a
    // conflict.
    private fun MutableImportedPanel.conflictsWithDifferentImportedResult(
        labResult: ExternalImportCandidate.LabResult,
    ): Boolean {
        return results.any { result ->
            !result.isUserOwned() &&
                    result.builtinAnalyteKey == labResult.analyteKey.storageValue &&
                    result.importExternalId != labResult.provenance.externalId
        }
    }

    private fun ExternalImportCandidate.LabResult.userConflictWarning(): ExternalImportWarning {
        return ExternalImportWarning(
            reason = ExternalImportWarningReason.LAB_USER_CONFLICT,
            externalId = provenance.externalId,
            rowIndex = null,
            message = "Skipped imported lab result because the target imported panel contains a user-created result for the same analyte.",
            messageKey = ExternalImportWarningMessageKey.LAB_USER_CONFLICT,
        )
    }

    private fun ExternalImportCandidate.LabResult.analyteConflictWarning(): ExternalImportWarning {
        return ExternalImportWarning(
            reason = ExternalImportWarningReason.LAB_ANALYTE_CONFLICT,
            externalId = provenance.externalId,
            rowIndex = null,
            message = "Skipped imported lab result because the target imported panel already holds a different imported result for the same analyte.",
            messageKey = ExternalImportWarningMessageKey.LAB_ANALYTE_CONFLICT,
        )
    }

    private fun logPreviewWarnings(
        sourceApp: ExternalTrackerSourceApp,
        warnings: List<ExternalImportWarning>,
    ) {
        warnings.forEach { warning ->
            diagnosticsLogger.warning(
                TAG,
                warning.toDiagnosticMessage(sourceApp),
                null,
            )
        }
    }

    private fun ExternalImportWarning.toDiagnosticMessage(
        sourceApp: ExternalTrackerSourceApp,
    ): String {
        val cleanMessage = message.lineSequence().joinToString(separator = " ") { line -> line.trim() }
        return "external_import_skipped_row " +
                "sourceApp=${sourceApp.storageValue} " +
                "reason=$reason " +
                "messageKey=${messageKey?.name ?: "none"} " +
                "externalId=${externalId ?: "none"} " +
                "rowIndex=${rowIndex?.toString() ?: "none"} " +
                "message=$cleanMessage"
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

    private data class MedicationImportPlan(
        val created: Int,
        val updated: Int,
        val unchanged: Int,
        val entriesToInsert: List<MedicationLogEntryEntity>,
    )

    private data class LabCommitResult(
        val created: Int,
        val updated: Int,
        val unchanged: Int,
        val warnings: List<ExternalImportWarning>,
    )

    private data class LabReconciliationPlan(
        val created: Int = 0,
        val updated: Int = 0,
        val unchanged: Int = 0,
        val warnings: List<ExternalImportWarning> = emptyList(),
        val panelsToUpsert: List<BloodTestPanelEntity> = emptyList(),
        val resultsToUpsert: List<BloodTestResultEntity> = emptyList(),
    )
}

private val ExternalTrackerSourceApp.label: String
    get() = when (this) {
        ExternalTrackerSourceApp.TRANSMTF -> "hrt.transmtf.com"
        ExternalTrackerSourceApp.OYAMA -> "hrt.mahiro.uk"
        ExternalTrackerSourceApp.NOMTF -> "HRT Recorder"
        ExternalTrackerSourceApp.TRANSMTF_COMPATIBLE -> "hrt.transmtf.com"
    }
