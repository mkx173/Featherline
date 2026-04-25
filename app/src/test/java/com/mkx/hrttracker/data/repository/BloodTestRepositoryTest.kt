package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.BloodTestDao
import com.mkx.hrttracker.data.local.BloodTestPanelEntity
import com.mkx.hrttracker.data.local.BloodTestPanelWithResultsEntity
import com.mkx.hrttracker.data.local.BloodTestResultEntity
import com.mkx.hrttracker.data.local.CustomBloodAnalyteEntity
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodTestResultInput
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

class BloodTestRepositoryTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val database: HrtTrackerDatabase = mockk()
    private val dao: BloodTestDao = mockk(relaxed = true)
    private val medicationLogRepository: MedicationLogRepository = mockk()

    private lateinit var repository: BloodTestRepository

    @Before
    fun setUp() {
        every { databaseHolder.get() } returns database
        every { database.bloodTestDao() } returns dao

        repository = BloodTestRepository(databaseHolder, medicationLogRepository)
    }

    @Test
    fun savePanel_persists_builtin_and_custom_results_with_contiguous_display_order() = runTest {
        val panelSlot = slot<BloodTestPanelEntity>()
        val resultsSlot = slot<List<BloodTestResultEntity>>()
        val customAnalyteUuid = UUID.randomUUID()
        coEvery { medicationLogRepository.getEntries() } returns listOf(
            testMedicationLogEntry(
                details = testCatalogMedicationDetails(
                    key = MedicationKey.ESTRADIOL,
                    applicationType = MedicationApplicationType.ORAL,
                    dose = MedicationDose.MgAsMedicine(2.0)
                ),
                sourceType = MedicationLogEntrySourceType.MANUAL,
                sourceGroupUuid = null,
                appliedAt = Instant.ofEpochMilli(1_699_999_000_000L)
            )
        )
        coEvery { dao.getCustomAnalytesByIds(listOf(customAnalyteUuid.toString())) } returns listOf(
            customAnalyte(
                uuid = customAnalyteUuid,
                name = "DHT",
                unitLabel = "ng/dL"
            )
        )
        coEvery { dao.upsertPanelWithResults(capture(panelSlot), capture(resultsSlot)) } returns Unit

        val panelUuid = repository.savePanel(
            uuid = null,
            collectedAt = Instant.ofEpochMilli(1_700_000_000_000L),
            collectedAtTimeZoneId = "Asia/Tokyo",
            notes = "  fasting  ",
            results = listOf(
                BloodTestResultInput.Custom(
                    customAnalyteUuid = customAnalyteUuid,
                    value = 18.0
                ),
                BloodTestResultInput.Builtin(
                    analyteKey = BloodAnalyteKey.E2,
                    unit = com.mkx.hrttracker.model.bloodtest.BloodUnitKey.PMOL_L,
                    value = 367.1
                )
            ),
            now = Instant.ofEpochMilli(1_700_000_100_000L)
        )

        assertEquals(panelUuid.toString(), panelSlot.captured.uuid)
        assertEquals("Asia/Tokyo", panelSlot.captured.collectedAtTimeZoneId)
        assertEquals("fasting", panelSlot.captured.notes)
        assertEquals(1_000_000L, panelSlot.captured.timeSinceLastEstradiolDoseMillis)
        assertNull(panelSlot.captured.timeSinceLastTestosteroneDoseMillis)
        assertEquals(2, resultsSlot.captured.size)

        val first = resultsSlot.captured[0]
        assertEquals(0, first.displayOrder)
        assertEquals(customAnalyteUuid.toString(), first.customAnalyteUuid)
        assertEquals("ng/dL", first.unitSnapshot)
        assertEquals(18.0, first.canonicalValue, 1e-9)

        val second = resultsSlot.captured[1]
        assertEquals(1, second.displayOrder)
        assertEquals("e2", second.builtinAnalyteKey)
        assertEquals("pmol_l", second.unitSnapshot)
        assertEquals(100.0, second.canonicalValue, 1e-6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun savePanel_rejects_empty_results() = runTest {
        coEvery { medicationLogRepository.getEntries() } returns emptyList()
        repository.savePanel(
            uuid = null,
            collectedAt = Instant.ofEpochMilli(1L),
            collectedAtTimeZoneId = "Asia/Tokyo",
            notes = null,
            results = emptyList(),
            now = Instant.ofEpochMilli(2L)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun savePanel_rejects_duplicate_builtin_analytes() = runTest {
        coEvery { medicationLogRepository.getEntries() } returns emptyList()
        repository.savePanel(
            uuid = null,
            collectedAt = Instant.ofEpochMilli(1L),
            collectedAtTimeZoneId = "Asia/Tokyo",
            notes = null,
            results = listOf(
                BloodTestResultInput.Builtin(
                    analyteKey = BloodAnalyteKey.E2,
                    unit = com.mkx.hrttracker.model.bloodtest.BloodUnitKey.PG_ML,
                    value = 90.0
                ),
                BloodTestResultInput.Builtin(
                    analyteKey = BloodAnalyteKey.E2,
                    unit = com.mkx.hrttracker.model.bloodtest.BloodUnitKey.PMOL_L,
                    value = 367.1
                )
            ),
            now = Instant.ofEpochMilli(2L)
        )
    }

    @Test
    fun getPanel_sorts_results_by_display_order_and_attaches_custom_name() = runTest {
        val panelUuid = UUID.randomUUID()
        val customAnalyteUuid = UUID.randomUUID()
        val firstResultUuid = UUID.randomUUID()
        val secondResultUuid = UUID.randomUUID()
        coEvery { dao.getPanel(panelUuid.toString()) } returns BloodTestPanelWithResultsEntity(
            panel = BloodTestPanelEntity(
                uuid = panelUuid.toString(),
                collectedAtInstantEpochMillis = 1_700_000_000_000L,
                collectedAtTimeZoneId = "Asia/Tokyo",
                notes = null,
                timeSinceLastEstradiolDoseMillis = 3_600_000L,
                timeSinceLastTestosteroneDoseMillis = null,
                createdAtEpochMillis = 1_700_000_000_000L,
                updatedAtEpochMillis = 1_700_000_100_000L
            ),
            results = listOf(
                BloodTestResultEntity(
                    uuid = secondResultUuid.toString(),
                    panelUuid = panelUuid.toString(),
                    createdAtEpochMillis = 20L,
                    displayOrder = 1,
                    builtinAnalyteKey = null,
                    customAnalyteUuid = customAnalyteUuid.toString(),
                    value = 18.0,
                    unitSnapshot = "ng/dL",
                    canonicalValue = 18.0
                ),
                BloodTestResultEntity(
                    uuid = firstResultUuid.toString(),
                    panelUuid = panelUuid.toString(),
                    createdAtEpochMillis = 10L,
                    displayOrder = 0,
                    builtinAnalyteKey = "e2",
                    customAnalyteUuid = null,
                    value = 95.0,
                    unitSnapshot = "pg_ml",
                    canonicalValue = 95.0
                )
            )
        )
        coEvery { dao.getCustomAnalytesByIds(listOf(customAnalyteUuid.toString())) } returns listOf(
            customAnalyte(
                uuid = customAnalyteUuid,
                name = "DHT",
                unitLabel = "ng/dL"
            )
        )

        val panel = repository.getPanel(panelUuid)

        checkNotNull(panel)
        assertEquals(listOf(firstResultUuid, secondResultUuid), panel.results.map { it.uuid })
        assertEquals(3_600_000L, panel.timeSinceLastEstradiolDoseMillis)
        assertNull(panel.timeSinceLastTestosteroneDoseMillis)
        assertEquals(
            BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
            panel.results.first().analyte
        )
        assertEquals(
            BloodTestResultAnalyte.Custom(customAnalyteUuid, "DHT"),
            panel.results.last().analyte
        )
    }

    @Test
    fun saveCustomAnalyte_normalizes_fields_and_preserves_existing_timestamps() = runTest {
        val analyteUuid = UUID.randomUUID()
        val captured = slot<CustomBloodAnalyteEntity>()
        coEvery {
            dao.getCustomAnalyteByNormalizedPair("dht", "ng/dl")
        } returns null
        coEvery { dao.getCustomAnalyte(analyteUuid.toString()) } returns customAnalyte(
            uuid = analyteUuid,
            name = "Old DHT",
            unitLabel = "ng/dL",
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 200L,
            archivedAtEpochMillis = 300L
        )
        coEvery { dao.updateCustomAnalyte(capture(captured)) } returns Unit

        repository.saveCustomAnalyte(
            uuid = analyteUuid,
            name = "  DHT ",
            unitLabel = " ng/dL ",
            now = Instant.ofEpochMilli(400L)
        )

        assertEquals("DHT", captured.captured.name)
        assertEquals("ng/dL", captured.captured.unitLabel)
        assertEquals("dht", captured.captured.normalizedName)
        assertEquals("ng/dl", captured.captured.normalizedUnitLabel)
        assertEquals(100L, captured.captured.createdAtEpochMillis)
        assertEquals(400L, captured.captured.updatedAtEpochMillis)
        assertNull(captured.captured.archivedAtEpochMillis)
    }

    @Test(expected = IllegalArgumentException::class)
    fun saveCustomAnalyte_rejects_duplicate_name_and_unit_pair() = runTest {
        val existingUuid = UUID.randomUUID()
        coEvery {
            dao.getCustomAnalyteByNormalizedPair("dht", "ng/dl")
        } returns customAnalyte(
            uuid = existingUuid,
            name = "DHT",
            unitLabel = "ng/dL"
        )

        repository.saveCustomAnalyte(
            uuid = UUID.randomUUID(),
            name = "DHT",
            unitLabel = "ng/dL",
            now = Instant.ofEpochMilli(1L)
        )
    }

    @Test
    fun saveCustomAnalyte_allows_reusing_name_and_unit_after_archive() = runTest {
        val archivedUuid = UUID.randomUUID()
        val captured = slot<CustomBloodAnalyteEntity>()
        coEvery {
            dao.getCustomAnalyteByNormalizedPair("dht", "ng/dl")
        } returns customAnalyte(
            uuid = archivedUuid,
            name = "DHT",
            unitLabel = "ng/dL",
            archivedAtEpochMillis = 100L,
        )
        coEvery { dao.updateCustomAnalyte(capture(captured)) } returns Unit

        val restoredUuid = repository.saveCustomAnalyte(
            uuid = null,
            name = "DHT",
            unitLabel = "ng/dL",
            now = Instant.ofEpochMilli(200L)
        )

        assertEquals(archivedUuid, restoredUuid)
        assertEquals(archivedUuid.toString(), captured.captured.uuid)
        assertNull(captured.captured.archivedAtEpochMillis)
        coVerify(exactly = 1) { dao.updateCustomAnalyte(any()) }
    }

    @Test
    fun archiveCustomAnalyte_sets_archived_at_timestamp() = runTest {
        val analyteUuid = UUID.randomUUID()
        val captured = slot<CustomBloodAnalyteEntity>()
        coEvery { dao.getCustomAnalyte(analyteUuid.toString()) } returns customAnalyte(
            uuid = analyteUuid,
            name = "DHT",
            unitLabel = "ng/dL",
            archivedAtEpochMillis = null
        )
        coEvery { dao.updateCustomAnalyte(capture(captured)) } returns Unit

        repository.archiveCustomAnalyte(
            uuid = analyteUuid,
            now = Instant.ofEpochMilli(500L)
        )

        assertEquals(500L, captured.captured.updatedAtEpochMillis)
        assertEquals(500L, captured.captured.archivedAtEpochMillis)
    }

    @Test
    fun deleteCustomAnalyte_deletes_only_when_unreferenced() = runTest {
        val analyteUuid = UUID.randomUUID()
        coEvery { dao.countResultsForCustomAnalyte(analyteUuid.toString()) } returns 0
        coEvery { dao.deleteCustomAnalyte(analyteUuid.toString()) } returns Unit

        repository.deleteCustomAnalyte(analyteUuid)

        coVerify(exactly = 1) { dao.deleteCustomAnalyte(analyteUuid.toString()) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun deleteCustomAnalyte_rejects_referenced_analyte() = runTest {
        val analyteUuid = UUID.randomUUID()
        coEvery { dao.countResultsForCustomAnalyte(analyteUuid.toString()) } returns 2

        repository.deleteCustomAnalyte(analyteUuid)
    }

    @Test
    fun getActiveCustomAnalytes_maps_archive_state() = runTest {
        val analyteUuid = UUID.randomUUID()
        coEvery { dao.getActiveCustomAnalytes() } returns listOf(
            customAnalyte(
                uuid = analyteUuid,
                name = "DHT",
                unitLabel = "ng/dL",
                archivedAtEpochMillis = null
            )
        )

        val analytes = repository.getActiveCustomAnalytes()

        assertEquals(1, analytes.size)
        assertEquals(analyteUuid, analytes.single().uuid)
        assertNull(analytes.single().archivedAt)
    }

    @Test
    fun savePanel_persists_null_when_no_prior_estradiol_dose_exists() = runTest {
        val panelSlot = slot<BloodTestPanelEntity>()
        coEvery { medicationLogRepository.getEntries() } returns emptyList()
        coEvery { dao.upsertPanelWithResults(capture(panelSlot), any()) } returns Unit

        repository.savePanel(
            uuid = null,
            collectedAt = Instant.ofEpochMilli(1_700_000_000_000L),
            collectedAtTimeZoneId = "Asia/Tokyo",
            notes = null,
            results = listOf(
                BloodTestResultInput.Builtin(
                    analyteKey = BloodAnalyteKey.E2,
                    unit = com.mkx.hrttracker.model.bloodtest.BloodUnitKey.PG_ML,
                    value = 100.0
                )
            ),
            now = Instant.ofEpochMilli(1_700_000_100_000L)
        )

        assertNull(panelSlot.captured.timeSinceLastEstradiolDoseMillis)
        assertNull(panelSlot.captured.timeSinceLastTestosteroneDoseMillis)
    }

    private fun customAnalyte(
        uuid: UUID,
        name: String,
        unitLabel: String,
        createdAtEpochMillis: Long = 100L,
        updatedAtEpochMillis: Long = 200L,
        archivedAtEpochMillis: Long? = null,
    ): CustomBloodAnalyteEntity {
        return CustomBloodAnalyteEntity(
            uuid = uuid.toString(),
            name = name,
            normalizedName = name.trim().lowercase(),
            unitLabel = unitLabel,
            normalizedUnitLabel = unitLabel.trim().lowercase(),
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
            archivedAtEpochMillis = archivedAtEpochMillis
        )
    }
}
