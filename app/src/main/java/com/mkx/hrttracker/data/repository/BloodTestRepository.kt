package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.BloodTestDao
import com.mkx.hrttracker.data.local.BloodTestPanelEntity
import com.mkx.hrttracker.data.local.BloodTestPanelWithResultsEntity
import com.mkx.hrttracker.data.local.BloodTestResultEntity
import com.mkx.hrttracker.data.local.BloodTestTrendPointEntity
import com.mkx.hrttracker.data.local.CustomBloodAnalyteEntity
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodTestResultInput
import com.mkx.hrttracker.model.bloodtest.BloodTestTrendPoint
import com.mkx.hrttracker.model.bloodtest.CustomBloodAnalyte
import com.mkx.hrttracker.model.medication.timeSinceEntryMillis
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BloodTestRepository @Inject constructor(
    private val databaseHolder: DatabaseHolder,
    private val medicationLogRepository: MedicationLogRepository,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    @Volatile
    private var cachedPanels: List<BloodTestPanel> = emptyList()

    @Volatile
    private var hasCachedPanelList = false

    @Volatile
    private var cachedActiveCustomAnalytes: List<CustomBloodAnalyte>? = null

    suspend fun getPanels(): List<BloodTestPanel> {
        val dao = databaseHolder.get().bloodTestDao()
        val panels = dao.getPanels()
        return cachePanels(mapPanels(panels = panels, dao = dao))
    }

    fun observePanels(): Flow<List<BloodTestPanel>> {
        val dao = databaseHolder.get().bloodTestDao()
        return dao.observePanels()
            .mapNotNull { panels ->
                // A restore committing between the panel query and the
                // custom-analyte resolve pairs a stale panel list with the
                // already-swapped custom_analytes table and mapPanels() throws.
                // Suppress that single inconsistent emission (null -> mapNotNull)
                // instead of replaying the warm cache: a replayed pre-restore
                // list re-renders stale panels for one frame before the
                // consistent emission lands.
                try {
                    cachePanels(mapPanels(panels = panels, dao = dao))
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    diagnosticsLogger.warning(
                        TAG,
                        "panels_flow_suppressed count=${panels.size}",
                        error,
                    )
                    null
                }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error !is Exception) throw error
                diagnosticsLogger.warning(TAG, "panels_flow_room_error", error)
                emit(cachedPanelFallback())
            }
    }

    suspend fun getPanel(uuid: UUID): BloodTestPanel? {
        val dao = databaseHolder.get().bloodTestDao()
        return dao.getPanel(uuid.toString())?.let { panel ->
            mapPanels(panels = listOf(panel), dao = dao).firstOrNull()?.also(::cachePanel)
        }
    }

    fun getCachedPanel(uuid: UUID): BloodTestPanel? {
        return cachedPanels.firstOrNull { panel -> panel.uuid == uuid }
    }

    fun getCachedPanels(): List<BloodTestPanel>? {
        return cachedPanels.takeIf { hasCachedPanelList }
    }

    suspend fun savePanel(
        uuid: UUID?,
        collectedAt: Instant,
        collectedAtTimeZoneId: String,
        notes: String?,
        results: List<BloodTestResultInput>,
        now: Instant = Instant.now(),
    ): UUID {
        validateTimeZoneId(collectedAtTimeZoneId)
        require(results.isNotEmpty()) { "Blood test panel must contain at least one result." }

        val dao = databaseHolder.get().bloodTestDao()
        val panelUuid = uuid ?: UUID.randomUUID()
        val existingPanel = uuid?.let { dao.getPanel(it.toString()) }
        val existingResultsByUuid = existingPanel?.orEmptyResultsByUuid().orEmpty()
        val customAnalytesByUuid = resolveCustomAnalytesForResults(results, dao)
        validateUniqueAnalytes(results)
        val timeSinceLastEstradiolDoseMillis = resolveTimeSinceLastEstradiolDoseMillis(collectedAt)

        val normalizedResults = results.mapIndexed { index, result ->
            result.toEntity(
                panelUuid = panelUuid,
                displayOrder = index,
                createdAtEpochMillis = existingResultsByUuid[result.uuid]?.createdAtEpochMillis
                    ?: now.toEpochMilli(),
                customAnalytesByUuid = customAnalytesByUuid,
            )
        }

        val createdAtEpochMillis = existingPanel?.panel?.createdAtEpochMillis ?: now.toEpochMilli()
        dao.upsertPanelWithResults(
            panel = BloodTestPanelEntity(
                uuid = panelUuid.toString(),
                collectedAtInstantEpochMillis = collectedAt.toEpochMilli(),
                collectedAtTimeZoneId = collectedAtTimeZoneId,
                notes = notes?.trim()?.takeUnless(String::isEmpty),
                timeSinceLastEstradiolDoseMillis = timeSinceLastEstradiolDoseMillis,
                timeSinceLastTestosteroneDoseMillis = null,
                createdAtEpochMillis = createdAtEpochMillis,
                updatedAtEpochMillis = now.toEpochMilli(),
            ),
            results = normalizedResults,
        )
        dao.getPanel(panelUuid.toString())?.let { panel ->
            mapPanels(panels = listOf(panel), dao = dao).firstOrNull()?.also(::cachePanel)
        }
        return panelUuid
    }

    suspend fun deletePanel(uuid: UUID) {
        databaseHolder.get().bloodTestDao().deletePanel(uuid.toString())
        cachedPanels = cachedPanels.filterNot { panel -> panel.uuid == uuid }
    }

    suspend fun deleteAllPanels() {
        databaseHolder.withTransaction { database ->
            val dao = database.bloodTestDao()
            dao.deleteAllResults()
            dao.deleteAllPanels()
        }
        cachedPanels = emptyList()
        hasCachedPanelList = true
    }

    suspend fun getActiveCustomAnalytes(): List<CustomBloodAnalyte> {
        return cacheActiveCustomAnalytes(
            databaseHolder.get().bloodTestDao()
            .getActiveCustomAnalytes()
            .map { analyte -> analyte.toModel() }
            .sortedWith(
                compareBy<CustomBloodAnalyte>(
                    { it.createdAt },
                    { it.name.lowercase(Locale.ROOT) })
                    .thenBy { it.unitLabel.lowercase(Locale.ROOT) }
            )
        )
    }

    suspend fun preloadActiveCustomAnalytes(): List<CustomBloodAnalyte> {
        return getActiveCustomAnalytes()
    }

    fun getCachedActiveCustomAnalytes(): List<CustomBloodAnalyte>? {
        return cachedActiveCustomAnalytes
    }

    suspend fun getCustomAnalytes(): List<CustomBloodAnalyte> {
        return databaseHolder.get().bloodTestDao()
            .getCustomAnalytes()
            .map { analyte -> analyte.toModel() }
    }

    suspend fun getCustomAnalyte(uuid: UUID): CustomBloodAnalyte? {
        return databaseHolder.get().bloodTestDao()
            .getCustomAnalyte(uuid.toString())
            ?.toModel()
    }

    suspend fun saveCustomAnalyte(
        uuid: UUID?,
        abbreviation: String,
        name: String,
        unitLabel: String,
        now: Instant = Instant.now(),
    ): UUID {
        val trimmedAbbreviation = abbreviation.trim()
        normalizeCustomField(trimmedAbbreviation, "Custom analyte abbreviation")
        val normalizedName = normalizeCustomField(name, "Custom analyte name")
        val normalizedUnitLabel = normalizeCustomField(unitLabel, "Custom analyte unit")
        val dao = databaseHolder.get().bloodTestDao()
        val existingDuplicate = dao.getCustomAnalyteByNormalizedPair(
            normalizedName = normalizedName,
            normalizedUnitLabel = normalizedUnitLabel,
        )
        val reusableArchivedDuplicate = existingDuplicate?.takeIf { duplicate ->
            duplicate.archivedAtEpochMillis != null && uuid == null
        }
        require(
            existingDuplicate == null ||
                    existingDuplicate.uuid == uuid?.toString() ||
                    reusableArchivedDuplicate != null
        ) {
            "A custom analyte with the same name and unit already exists."
        }

        val analyteUuid = uuid
            ?: reusableArchivedDuplicate?.uuid?.let(UUID::fromString)
            ?: UUID.randomUUID()
        val existingAnalyte =
            uuid?.let { dao.getCustomAnalyte(it.toString()) } ?: reusableArchivedDuplicate
        val nowEpochMillis = now.toEpochMilli()
        val isRevivingArchivedDuplicate = reusableArchivedDuplicate != null && uuid == null
        val entity = CustomBloodAnalyteEntity(
            uuid = analyteUuid.toString(),
            abbreviation = trimmedAbbreviation,
            name = name.trim(),
            normalizedName = normalizedName,
            unitLabel = unitLabel.trim(),
            normalizedUnitLabel = normalizedUnitLabel,
            createdAtEpochMillis = if (isRevivingArchivedDuplicate) {
                nowEpochMillis
            } else {
                existingAnalyte?.createdAtEpochMillis ?: nowEpochMillis
            },
            updatedAtEpochMillis = nowEpochMillis,
            archivedAtEpochMillis = null,
        )
        if (existingAnalyte == null) {
            dao.insertCustomAnalyte(entity)
        } else {
            dao.updateCustomAnalyte(entity)
        }
        cachedActiveCustomAnalytes = null
        return analyteUuid
    }

    suspend fun archiveCustomAnalyte(
        uuid: UUID,
        now: Instant = Instant.now(),
    ) {
        val dao = databaseHolder.get().bloodTestDao()
        val existing = checkNotNull(dao.getCustomAnalyte(uuid.toString())) {
            "Custom analyte $uuid does not exist."
        }

        dao.updateCustomAnalyte(
            existing.copy(
                updatedAtEpochMillis = now.toEpochMilli(),
                archivedAtEpochMillis = now.toEpochMilli(),
            )
        )
        cachedActiveCustomAnalytes = null
    }

    suspend fun deleteCustomAnalyte(uuid: UUID) {
        // Wrap in a transaction so a concurrent panel save can't insert a referencing
        // result between the count check and the delete. With ON DELETE RESTRICT the
        // DB would block the delete with SQLiteConstraintException, which surfaces as
        // an opaque constraint exception rather than the friendly require() message.
        databaseHolder.withTransaction { database ->
            val dao = database.bloodTestDao()
            require(dao.countResultsForCustomAnalyte(uuid.toString()) == 0) {
                "Cannot delete a custom analyte that is referenced by blood test results."
            }
            dao.deleteCustomAnalyte(uuid.toString())
        }
        cachedActiveCustomAnalytes = null
    }

    suspend fun hasResultsForCustomAnalyte(uuid: UUID): Boolean {
        return databaseHolder.get().bloodTestDao()
            .countResultsForCustomAnalyte(uuid.toString()) > 0
    }

    suspend fun getBuiltinTrendPoints(analyteKey: BloodAnalyteKey): List<BloodTestTrendPoint> {
        return databaseHolder.get().bloodTestDao()
            .getBuiltinTrendPoints(analyteKey.storageValue)
            .map { point -> point.toModel() }
    }

    suspend fun getCustomTrendPoints(customAnalyteUuid: UUID): List<BloodTestTrendPoint> {
        return databaseHolder.get().bloodTestDao()
            .getCustomTrendPoints(customAnalyteUuid.toString())
            .map { point -> point.toModel() }
    }

    private suspend fun mapPanels(
        panels: List<BloodTestPanelWithResultsEntity>,
        dao: BloodTestDao,
    ): List<BloodTestPanel> {
        val customAnalyteIds = panels.asSequence()
            .flatMap { panel -> panel.results.asSequence() }
            .mapNotNull(BloodTestResultEntity::customAnalyteUuid)
            .distinct()
            .toList()
        val customAnalytesByUuid = if (customAnalyteIds.isEmpty()) {
            emptyMap()
        } else {
            dao.getCustomAnalytesByIds(customAnalyteIds).associateBy(CustomBloodAnalyteEntity::uuid)
        }

        return panels.map { panel ->
            panel.toModel(customAnalytesByUuid = customAnalytesByUuid)
        }
    }

    private fun cachePanels(panels: List<BloodTestPanel>): List<BloodTestPanel> {
        cachedPanels = panels
        hasCachedPanelList = true
        return panels
    }

    private fun cachedPanelFallback(): List<BloodTestPanel> {
        return cachedPanels.takeIf { hasCachedPanelList } ?: emptyList()
    }

    private fun cachePanel(panel: BloodTestPanel) {
        val updatedPanels =
            cachedPanels.filterNot { cachedPanel -> cachedPanel.uuid == panel.uuid } + panel
        cachedPanels = updatedPanels.sortedByDescending(BloodTestPanel::collectedAt)
    }

    private fun cacheActiveCustomAnalytes(
        customAnalytes: List<CustomBloodAnalyte>,
    ): List<CustomBloodAnalyte> {
        cachedActiveCustomAnalytes = customAnalytes
        return customAnalytes
    }

    private suspend fun resolveCustomAnalytesForResults(
        results: List<BloodTestResultInput>,
        dao: BloodTestDao,
    ): Map<String, CustomBloodAnalyteEntity> {
        val customIds = results.mapNotNull { result ->
            when (result) {
                is BloodTestResultInput.Builtin -> null
                is BloodTestResultInput.Custom -> result.customAnalyteUuid.toString()
            }
        }.distinct()

        if (customIds.isEmpty()) {
            return emptyMap()
        }

        val customAnalytes = dao.getCustomAnalytesByIds(customIds)
        require(customAnalytes.size == customIds.size) {
            "Every custom blood test result must reference an existing custom analyte."
        }
        return customAnalytes.associateBy(CustomBloodAnalyteEntity::uuid)
    }

    private fun validateUniqueAnalytes(results: List<BloodTestResultInput>) {
        val builtinKeys = results.mapNotNull { result ->
            (result as? BloodTestResultInput.Builtin)?.analyteKey
        }
        require(builtinKeys.distinct().size == builtinKeys.size) {
            "A panel cannot contain the same built-in analyte more than once."
        }

        val customIds = results.mapNotNull { result ->
            (result as? BloodTestResultInput.Custom)?.customAnalyteUuid
        }
        require(customIds.distinct().size == customIds.size) {
            "A panel cannot contain the same custom analyte more than once."
        }
    }

    private fun validateTimeZoneId(timeZoneId: String) {
        ZoneId.of(timeZoneId)
    }

    private suspend fun resolveTimeSinceLastEstradiolDoseMillis(
        collectedAt: Instant,
    ): Long? {
        return timeSinceEntryMillis(
            target = collectedAt,
            entry = medicationLogRepository.getLatestEstradiolEntryOnOrBefore(collectedAt),
        )
    }

    private fun normalizeCustomField(
        value: String,
        fieldName: String,
    ): String {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "$fieldName must not be blank." }
        return trimmed.lowercase(Locale.ROOT)
    }

    private fun BloodTestPanelWithResultsEntity.orEmptyResultsByUuid(): Map<UUID?, BloodTestResultEntity> {
        return results.associateBy { result -> result.uuid.run(UUID::fromString) }
    }

    private fun BloodTestResultInput.toEntity(
        panelUuid: UUID,
        displayOrder: Int,
        createdAtEpochMillis: Long,
        customAnalytesByUuid: Map<String, CustomBloodAnalyteEntity>,
    ): BloodTestResultEntity {
        require(value.isFinite()) { "Blood test value must be finite." }
        val resultUuid = uuid ?: UUID.randomUUID()

        return when (this) {
            is BloodTestResultInput.Builtin -> BloodTestResultEntity(
                uuid = resultUuid.toString(),
                panelUuid = panelUuid.toString(),
                createdAtEpochMillis = createdAtEpochMillis,
                displayOrder = displayOrder,
                builtinAnalyteKey = analyteKey.storageValue,
                customAnalyteUuid = null,
                value = value,
                unitSnapshot = unit.storageValue,
                canonicalValue = BloodTestCatalog.toCanonical(
                    analyteKey = analyteKey,
                    value = value,
                    unit = unit,
                ),
            )

            is BloodTestResultInput.Custom -> {
                val analyte = checkNotNull(customAnalytesByUuid[customAnalyteUuid.toString()]) {
                    "Custom analyte $customAnalyteUuid does not exist."
                }
                BloodTestResultEntity(
                    uuid = resultUuid.toString(),
                    panelUuid = panelUuid.toString(),
                    createdAtEpochMillis = createdAtEpochMillis,
                    displayOrder = displayOrder,
                    builtinAnalyteKey = null,
                    customAnalyteUuid = customAnalyteUuid.toString(),
                    value = value,
                    unitSnapshot = analyte.unitLabel,
                    canonicalValue = value,
                )
            }
        }
    }

    private fun BloodTestPanelWithResultsEntity.toModel(
        customAnalytesByUuid: Map<String, CustomBloodAnalyteEntity>,
    ): BloodTestPanel {
        return BloodTestPanel(
            uuid = UUID.fromString(panel.uuid),
            collectedAt = Instant.ofEpochMilli(panel.collectedAtInstantEpochMillis),
            collectedAtTimeZoneId = panel.collectedAtTimeZoneId,
            notes = panel.notes,
            timeSinceLastEstradiolDoseMillis = panel.timeSinceLastEstradiolDoseMillis,
            timeSinceLastTestosteroneDoseMillis = panel.timeSinceLastTestosteroneDoseMillis,
            results = results.sortedBy(BloodTestResultEntity::displayOrder).map { result ->
                result.toModel(customAnalytesByUuid = customAnalytesByUuid)
            },
            createdAt = Instant.ofEpochMilli(panel.createdAtEpochMillis),
            updatedAt = Instant.ofEpochMilli(panel.updatedAtEpochMillis),
        )
    }

    private fun BloodTestResultEntity.toModel(
        customAnalytesByUuid: Map<String, CustomBloodAnalyteEntity>,
    ): BloodTestResult {
        val analyte = builtinAnalyteKey?.let { builtinKey ->
            BloodTestResultAnalyte.Builtin(
                key = checkNotNull(BloodAnalyteKey.fromStorageValue(builtinKey)) {
                    "Unknown built-in blood analyte key $builtinKey."
                }
            )
        } ?: BloodTestResultAnalyte.Custom(
            uuid = UUID.fromString(checkNotNull(customAnalyteUuid)),
            abbreviation = checkNotNull(customAnalytesByUuid[customAnalyteUuid]?.abbreviation),
            name = checkNotNull(customAnalytesByUuid[customAnalyteUuid]?.name),
        )

        return BloodTestResult(
            uuid = UUID.fromString(uuid),
            createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
            displayOrder = displayOrder,
            analyte = analyte,
            value = value,
            unitSnapshot = unitSnapshot,
            canonicalValue = canonicalValue,
        )
    }

    private fun CustomBloodAnalyteEntity.toModel(): CustomBloodAnalyte {
        return CustomBloodAnalyte(
            uuid = UUID.fromString(uuid),
            abbreviation = abbreviation,
            name = name,
            unitLabel = unitLabel,
            createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
            updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
            archivedAt = archivedAtEpochMillis?.let(Instant::ofEpochMilli),
        )
    }

    private fun BloodTestTrendPointEntity.toModel(): BloodTestTrendPoint {
        return BloodTestTrendPoint(
            panelUuid = UUID.fromString(panelUuid),
            resultUuid = UUID.fromString(resultUuid),
            collectedAt = Instant.ofEpochMilli(collectedAtInstantEpochMillis),
            collectedAtTimeZoneId = collectedAtTimeZoneId,
            value = value,
            unitSnapshot = unitSnapshot,
            canonicalValue = canonicalValue,
        )
    }
}

private const val TAG = "BloodTestRepository"
