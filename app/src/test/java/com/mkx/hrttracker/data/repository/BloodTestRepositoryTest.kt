package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.BloodTestDao
import com.mkx.hrttracker.data.local.BloodTestPanelEntity
import com.mkx.hrttracker.data.local.BloodTestPanelWithResultsEntity
import com.mkx.hrttracker.data.local.BloodTestResultEntity
import com.mkx.hrttracker.data.local.CustomBloodAnalyteEntity
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodTestResultInput
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
        coEvery { dao.getPanel(any()) } returns null

        repository = BloodTestRepository(databaseHolder, medicationLogRepository)
    }

    @Test
    fun savePanel_persists_builtin_and_custom_results_with_contiguous_display_order() = runTest {
        val panelSlot = slot<BloodTestPanelEntity>()
        val resultsSlot = slot<List<BloodTestResultEntity>>()
        val customAnalyteUuid = UUID.randomUUID()
        coEvery { medicationLogRepository.getLatestEstradiolEntryOnOrBefore(any()) } returns
                testMedicationLogEntry(
                    medicine = testMedicine(key = MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                    sourceGroupUuid = null,
                    appliedAt = Instant.ofEpochMilli(1_699_999_000_000L)
                )
        coEvery { dao.getCustomAnalytesByIds(listOf(customAnalyteUuid.toString())) } returns listOf(
            customAnalyte(
                uuid = customAnalyteUuid,
                name = "DHT",
                unitLabel = "ng/dL"
            )
        )
        coEvery {
            dao.upsertPanelWithResults(
                capture(panelSlot),
                capture(resultsSlot)
            )
        } returns Unit

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

    @Test
    fun savePanel_preservesImportedPanelAndResultProvenanceForSurvivingResults() = runTest {
        val panelUuid = UUID.fromString("aaaaaaaa-1000-0000-0000-000000000001")
        val resultUuid = UUID.fromString("aaaaaaaa-1000-0000-0000-000000000002")
        val panelSlot = slot<BloodTestPanelEntity>()
        val resultsSlot = slot<List<BloodTestResultEntity>>()
        coEvery { medicationLogRepository.getLatestEstradiolEntryOnOrBefore(any()) } returns null
        coEvery { dao.getPanel(panelUuid.toString()) } returns BloodTestPanelWithResultsEntity(
            panel = BloodTestPanelEntity(
                uuid = panelUuid.toString(),
                collectedAtInstantEpochMillis = 1_700_000_000_000L,
                collectedAtTimeZoneId = "Asia/Tokyo",
                notes = null,
                timeSinceLastEstradiolDoseMillis = null,
                timeSinceLastTestosteroneDoseMillis = null,
                createdAtEpochMillis = 1_700_000_000_000L,
                updatedAtEpochMillis = 1_700_000_000_000L,
                importSourceApp = "transmtf",
                importPanelKey = 1_700_000_000_000L,
            ),
            results = listOf(
                BloodTestResultEntity(
                    uuid = resultUuid.toString(),
                    panelUuid = panelUuid.toString(),
                    createdAtEpochMillis = 1_700_000_000_000L,
                    displayOrder = 0,
                    builtinAnalyteKey = "e2",
                    customAnalyteUuid = null,
                    value = 100.0,
                    unitSnapshot = "pg_ml",
                    canonicalValue = 100.0,
                    importSourceApp = "transmtf",
                    importExternalId = "lab-e2",
                )
            ),
        )
        coEvery { dao.upsertPanelWithResults(capture(panelSlot), capture(resultsSlot)) } returns Unit

        repository.savePanel(
            uuid = panelUuid,
            collectedAt = Instant.ofEpochMilli(1_700_000_060_000L),
            collectedAtTimeZoneId = "Asia/Tokyo",
            notes = "edited",
            results = listOf(
                BloodTestResultInput.Builtin(
                    uuid = resultUuid,
                    analyteKey = BloodAnalyteKey.E2,
                    unit = com.mkx.hrttracker.model.bloodtest.BloodUnitKey.PG_ML,
                    value = 125.0,
                )
            ),
            now = Instant.ofEpochMilli(1_700_000_120_000L),
        )

        assertEquals("transmtf", panelSlot.captured.importSourceApp)
        assertEquals(1_700_000_000_000L, panelSlot.captured.importPanelKey)
        val savedResult = resultsSlot.captured.single()
        assertEquals(resultUuid.toString(), savedResult.uuid)
        assertEquals("transmtf", savedResult.importSourceApp)
        assertEquals("lab-e2", savedResult.importExternalId)
    }

    @Test
    fun deleteAllPanels_clears_results_and_panels_in_single_transaction() = runTest {
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { dao.deleteAllResults() } returns Unit
        coEvery { dao.deleteAllPanels() } returns Unit

        repository.deleteAllPanels()

        coVerify(exactly = 1) { databaseHolder.withTransaction<Unit>(any()) }
        coVerifyOrder {
            dao.deleteAllResults()
            dao.deleteAllPanels()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun savePanel_rejects_empty_results() = runTest {
        coEvery { medicationLogRepository.getLatestEstradiolEntryOnOrBefore(any()) } returns null
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
        coEvery { medicationLogRepository.getLatestEstradiolEntryOnOrBefore(any()) } returns null
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
        assertEquals(panelUuid, repository.getCachedPanel(panelUuid)?.uuid)
        assertNull(repository.getCachedPanels())
        assertEquals(
            BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
            panel.results.first().analyte
        )
        assertEquals(
            BloodTestResultAnalyte.Custom(customAnalyteUuid, "DHT", "DHT"),
            panel.results.last().analyte
        )
    }

    @Test
    fun observePanels_cachesLatestPanelsForSynchronousLookup() = runTest {
        val panelUuid = UUID.randomUUID()
        val resultUuid = UUID.randomUUID()
        every { dao.observePanels() } returns flowOf(
            listOf(
                BloodTestPanelWithResultsEntity(
                    panel = BloodTestPanelEntity(
                        uuid = panelUuid.toString(),
                        collectedAtInstantEpochMillis = 1_700_000_000_000L,
                        collectedAtTimeZoneId = "Asia/Tokyo",
                        notes = null,
                        timeSinceLastEstradiolDoseMillis = null,
                        timeSinceLastTestosteroneDoseMillis = null,
                        createdAtEpochMillis = 1_700_000_000_000L,
                        updatedAtEpochMillis = 1_700_000_100_000L,
                    ),
                    results = listOf(
                        BloodTestResultEntity(
                            uuid = resultUuid.toString(),
                            panelUuid = panelUuid.toString(),
                            createdAtEpochMillis = 1_700_000_000_000L,
                            displayOrder = 0,
                            builtinAnalyteKey = "e2",
                            customAnalyteUuid = null,
                            value = 95.0,
                            unitSnapshot = "pg_ml",
                            canonicalValue = 95.0,
                        )
                    )
                )
            )
        )

        val panels = repository.observePanels().first()

        assertEquals(panelUuid, panels.single().uuid)
        assertEquals(panelUuid, repository.getCachedPanel(panelUuid)?.uuid)
        assertEquals(panels, repository.getCachedPanels())
    }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun observePanels_recoversAfterTransientMissingCustomAnalyteDuringRestore() = runTest {
        val staleAnalyteUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val restoredAnalyteUuid = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val stalePanelUuid = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val restoredPanelUuid = UUID.fromString("44444444-4444-4444-4444-444444444444")
        val panelsSource = MutableStateFlow(
            listOf(
                bloodTestPanelWithCustomResult(
                    panelUuid = stalePanelUuid,
                    customAnalyteUuid = staleAnalyteUuid,
                    collectedAtEpochMillis = 1_000L,
                )
            )
        )
        var resolvableAnalyteUuid = staleAnalyteUuid
        every { dao.observePanels() } returns panelsSource
        coEvery { dao.getCustomAnalytesByIds(any()) } coAnswers {
            firstArg<List<String>>()
                .filter { uuid -> uuid == resolvableAnalyteUuid.toString() }
                .map { uuid ->
                    val parsedUuid = UUID.fromString(uuid)
                    customAnalyte(
                        uuid = parsedUuid,
                        abbreviation = if (parsedUuid == staleAnalyteUuid) "Old" else "Restored",
                        name = if (parsedUuid == staleAnalyteUuid) "Old" else "Restored",
                        unitLabel = "ng/dL",
                    )
                }
        }

        val emissions = mutableListOf<List<BloodTestPanel>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observePanels().collect { panels -> emissions += panels }
        }
        advanceUntilIdle()

        assertEquals(
            BloodTestResultAnalyte.Custom(staleAnalyteUuid, "Old", "Old"),
            emissions.last().single().results.single().analyte,
        )

        val emissionCountBeforeRestoreRace = emissions.size
        resolvableAnalyteUuid = restoredAnalyteUuid
        panelsSource.value = listOf(
            bloodTestPanelWithCustomResult(
                panelUuid = stalePanelUuid,
                customAnalyteUuid = staleAnalyteUuid,
                collectedAtEpochMillis = 2_000L,
            )
        )
        advanceUntilIdle()

        // The inconsistent pairing must be SUPPRESSED, not replayed from the
        // warm cache: a replayed pre-restore list re-renders stale panels for
        // one frame before the consistent emission lands.
        assertEquals(
            "the inconsistent emission must be suppressed, not replayed from the cache",
            emissionCountBeforeRestoreRace,
            emissions.size,
        )

        panelsSource.value = listOf(
            bloodTestPanelWithCustomResult(
                panelUuid = restoredPanelUuid,
                customAnalyteUuid = restoredAnalyteUuid,
                collectedAtEpochMillis = 3_000L,
            )
        )
        advanceUntilIdle()

        assertEquals(
            "observePanels() must recover after a restore emission references a missing custom analyte",
            restoredPanelUuid,
            emissions.last().singleOrNull()?.uuid,
        )
        assertEquals(
            BloodTestResultAnalyte.Custom(restoredAnalyteUuid, "Restored", "Restored"),
            emissions.last().single().results.single().analyte,
        )
    }

    @Test
    fun savePanel_updatesWarmPanelListCache() = runTest {
        val editedPanelUuid = UUID.fromString("4add0f27-0729-45cc-840d-8970c1ff8bfd")
        val otherPanelUuid = UUID.fromString("198fcb60-4a1e-4a3f-af61-3df66b3cc939")
        every { dao.observePanels() } returns flowOf(
            listOf(
                bloodTestPanelWithResult(
                    panelUuid = otherPanelUuid,
                    collectedAtEpochMillis = 2_000L,
                    notes = "other",
                ),
                bloodTestPanelWithResult(
                    panelUuid = editedPanelUuid,
                    collectedAtEpochMillis = 1_000L,
                    notes = "stale",
                )
            )
        )
        coEvery { dao.getPanel(editedPanelUuid.toString()) } returnsMany listOf(
            bloodTestPanelWithResult(
                panelUuid = editedPanelUuid,
                collectedAtEpochMillis = 1_000L,
                notes = "stale",
            ),
            bloodTestPanelWithResult(
                panelUuid = editedPanelUuid,
                collectedAtEpochMillis = 3_000L,
                notes = "updated",
            )
        )
        coEvery { medicationLogRepository.getLatestEstradiolEntryOnOrBefore(any()) } returns null
        coEvery { dao.upsertPanelWithResults(any(), any()) } returns Unit

        repository.observePanels().first()

        repository.savePanel(
            uuid = editedPanelUuid,
            collectedAt = Instant.ofEpochMilli(3_000L),
            collectedAtTimeZoneId = "Asia/Tokyo",
            notes = "updated",
            results = listOf(
                BloodTestResultInput.Builtin(
                    analyteKey = BloodAnalyteKey.E2,
                    unit = com.mkx.hrttracker.model.bloodtest.BloodUnitKey.PG_ML,
                    value = 100.0
                )
            ),
            now = Instant.ofEpochMilli(4_000L),
        )

        assertEquals(
            listOf(editedPanelUuid, otherPanelUuid),
            repository.getCachedPanels()?.map { panel -> panel.uuid },
        )
        assertEquals("updated", repository.getCachedPanel(editedPanelUuid)?.notes)
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
            abbreviation = "DHT",
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
            abbreviation = "DHT",
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
            abbreviation = "DHT",
            name = "DHT",
            unitLabel = "ng/dL",
            now = Instant.ofEpochMilli(200L)
        )

        assertEquals(archivedUuid, restoredUuid)
        assertEquals(archivedUuid.toString(), captured.captured.uuid)
        assertEquals(200L, captured.captured.createdAtEpochMillis)
        assertEquals(200L, captured.captured.updatedAtEpochMillis)
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
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { dao.countResultsForCustomAnalyte(analyteUuid.toString()) } returns 0
        coEvery { dao.deleteCustomAnalyte(analyteUuid.toString()) } returns Unit

        repository.deleteCustomAnalyte(analyteUuid)

        coVerify(exactly = 1) { dao.deleteCustomAnalyte(analyteUuid.toString()) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun deleteCustomAnalyte_rejects_referenced_analyte() = runTest {
        val analyteUuid = UUID.randomUUID()
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { dao.countResultsForCustomAnalyte(analyteUuid.toString()) } returns 2

        repository.deleteCustomAnalyte(analyteUuid)
    }

    @Test
    fun getActiveCustomAnalytes_orders_oldest_first() = runTest {
        val newerAnalyteUuid = UUID.randomUUID()
        val olderAnalyteUuid = UUID.randomUUID()
        coEvery { dao.getActiveCustomAnalytes() } returns listOf(
            customAnalyte(
                uuid = newerAnalyteUuid,
                name = "DHT",
                unitLabel = "ng/dL",
                createdAtEpochMillis = 200L,
                archivedAtEpochMillis = null
            ),
            customAnalyte(
                uuid = olderAnalyteUuid,
                name = "Prog",
                unitLabel = "ng/mL",
                createdAtEpochMillis = 100L,
                archivedAtEpochMillis = null
            )
        )

        val analytes = repository.getActiveCustomAnalytes()

        assertEquals(
            listOf(olderAnalyteUuid, newerAnalyteUuid),
            analytes.map { it.uuid }
        )
        assertNull(analytes.first().archivedAt)
        assertNull(analytes.last().archivedAt)
    }

    @Test
    fun savePanel_persists_null_when_no_prior_estradiol_dose_exists() = runTest {
        val panelSlot = slot<BloodTestPanelEntity>()
        coEvery { medicationLogRepository.getLatestEstradiolEntryOnOrBefore(any()) } returns null
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
        abbreviation: String = name,
        createdAtEpochMillis: Long = 100L,
        updatedAtEpochMillis: Long = 200L,
        archivedAtEpochMillis: Long? = null,
    ): CustomBloodAnalyteEntity {
        return CustomBloodAnalyteEntity(
            uuid = uuid.toString(),
            abbreviation = abbreviation,
            name = name,
            normalizedName = name.trim().lowercase(),
            unitLabel = unitLabel,
            normalizedUnitLabel = unitLabel.trim().lowercase(),
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
            archivedAtEpochMillis = archivedAtEpochMillis
        )
    }

    private fun bloodTestPanelWithResult(
        panelUuid: UUID,
        collectedAtEpochMillis: Long,
        notes: String?,
    ): BloodTestPanelWithResultsEntity {
        val resultUuid = UUID.nameUUIDFromBytes(panelUuid.toString().encodeToByteArray())
        return BloodTestPanelWithResultsEntity(
            panel = BloodTestPanelEntity(
                uuid = panelUuid.toString(),
                collectedAtInstantEpochMillis = collectedAtEpochMillis,
                collectedAtTimeZoneId = "Asia/Tokyo",
                notes = notes,
                timeSinceLastEstradiolDoseMillis = null,
                timeSinceLastTestosteroneDoseMillis = null,
                createdAtEpochMillis = collectedAtEpochMillis,
                updatedAtEpochMillis = collectedAtEpochMillis,
            ),
            results = listOf(
                BloodTestResultEntity(
                    uuid = resultUuid.toString(),
                    panelUuid = panelUuid.toString(),
                    createdAtEpochMillis = collectedAtEpochMillis,
                    displayOrder = 0,
                    builtinAnalyteKey = "e2",
                    customAnalyteUuid = null,
                    value = 100.0,
                    unitSnapshot = "pg_ml",
                    canonicalValue = 100.0,
                )
            )
        )
    }

    private fun bloodTestPanelWithCustomResult(
        panelUuid: UUID,
        customAnalyteUuid: UUID,
        collectedAtEpochMillis: Long,
    ): BloodTestPanelWithResultsEntity {
        val resultUuid = UUID.nameUUIDFromBytes(panelUuid.toString().encodeToByteArray())
        return BloodTestPanelWithResultsEntity(
            panel = BloodTestPanelEntity(
                uuid = panelUuid.toString(),
                collectedAtInstantEpochMillis = collectedAtEpochMillis,
                collectedAtTimeZoneId = "Asia/Tokyo",
                notes = null,
                timeSinceLastEstradiolDoseMillis = null,
                timeSinceLastTestosteroneDoseMillis = null,
                createdAtEpochMillis = collectedAtEpochMillis,
                updatedAtEpochMillis = collectedAtEpochMillis,
            ),
            results = listOf(
                BloodTestResultEntity(
                    uuid = resultUuid.toString(),
                    panelUuid = panelUuid.toString(),
                    createdAtEpochMillis = collectedAtEpochMillis,
                    displayOrder = 0,
                    builtinAnalyteKey = null,
                    customAnalyteUuid = customAnalyteUuid.toString(),
                    value = 18.0,
                    unitSnapshot = "ng/dL",
                    canonicalValue = 18.0,
                )
            )
        )
    }
}
