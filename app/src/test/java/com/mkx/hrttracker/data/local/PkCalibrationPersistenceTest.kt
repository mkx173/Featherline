package com.mkx.hrttracker.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.HomeDataConditionalMutationResult
import com.mkx.hrttracker.data.repository.PkCalibrationStorageRepository
import com.mkx.hrttracker.data.repository.toHomeCalibrationDisplay
import com.mkx.hrttracker.data.repository.toModel
import com.mkx.hrttracker.model.pk.CanonicalDigest
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
import com.mkx.hrttracker.model.pk.PK_CALIBRATION_DISPLAY_SCHEMA
import com.mkx.hrttracker.model.pk.PkCalibrationAcceptanceRecord
import com.mkx.hrttracker.model.pk.PersistedPkCalibrationDisplay
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.toValidatedPersonalParams
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PkCalibrationPersistenceTest {
    private lateinit var database: HrtTrackerDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HrtTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun displayArtifact_mapsPromotedRoutesAndNonZeroBetasToValidatedPersonalParams() {
        val artifact = checkNotNull(
            PersistedPkCalibrationDisplay.create(
                calibrationModelVersion = "route-v9-final",
                resultInputDigest = digest("hrttracker.fit-input/test", 'a'),
                promotedRoutes = listOf(PkCalibrationRoute.PATCH, PkCalibrationRoute.GEL),
                displayRouteLogScaleByRoute = mapOf(PkCalibrationRoute.GEL to 0.2),
            )
        )

        val params = checkNotNull(artifact.toValidatedPersonalParams())

        assertEquals(0.0, params.logScaleFor(PkCalibrationRoute.PATCH), 0.0)
        assertEquals(0.2, params.logScaleFor(PkCalibrationRoute.GEL), 0.0)
        assertEquals(mapOf(PkCalibrationRoute.GEL to 0.2), params.routeLogScale)
    }

    @Test
    fun publishDisplayArtifactIfCurrent_doesNotReturnBeforeGenerationBoundPropagationCompletes() =
        runTest {
        val databaseHolder: DatabaseHolder = mockk()
        every { databaseHolder.get() } returns database
        val homeSnapshotRepository: HomeSnapshotRepository = mockk()
        val propagationStarted = CompletableDeferred<Unit>()
        val allowPropagation = CompletableDeferred<Unit>()
        coEvery {
            homeSnapshotRepository.runHomeDataMutationIfGenerationCurrent<Unit>(11L, any())
        } coAnswers {
            propagationStarted.complete(Unit)
            allowPropagation.await()
            secondArg<suspend (Long) -> Unit>().invoke(12L)
            HomeDataConditionalMutationResult.Published(12L, Unit)
        }
        val repository = PkCalibrationStorageRepository(
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
        )
        val artifact = checkNotNull(
            PersistedPkCalibrationDisplay.create(
                calibrationModelVersion = "route-v9-final",
                resultInputDigest = digest("hrttracker.fit-input/test", 'b'),
                promotedRoutes = listOf(PkCalibrationRoute.ORAL),
                displayRouteLogScaleByRoute = mapOf(PkCalibrationRoute.ORAL to 0.2),
            )
        )

        val saveJob = launch {
            assertTrue(
                repository.publishDisplayArtifactIfCurrent(
                    artifact = artifact,
                    expectedInputGeneration = 11L,
                )
            )
        }
        propagationStarted.await()

        assertFalse(saveJob.isCompleted)
        allowPropagation.complete(Unit)
        saveJob.join()

        assertEquals(12L, database.pkCalibrationDao().getDisplayArtifact()?.homeSnapshotGeneration)
    }

    @Test
    fun publishDisplayArtifactIfCurrent_staleGenerationDoesNotTouchDatabase() = runTest {
        val databaseHolder: DatabaseHolder = mockk()
        val homeSnapshotRepository: HomeSnapshotRepository = mockk()
        coEvery { homeSnapshotRepository.captureCurrentHomeDataGeneration() } returns 4L
        coEvery {
            homeSnapshotRepository.runHomeDataMutationIfGenerationCurrent<Unit>(4L, any())
        } returns HomeDataConditionalMutationResult.Stale(
            expectedGeneration = 4L,
            currentGeneration = 5L,
        )
        val repository = PkCalibrationStorageRepository(
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
        )

        val capturedGeneration = repository.captureHomeDataGeneration()
        val published = repository.publishDisplayArtifactIfCurrent(
            artifact = artifact(),
            expectedInputGeneration = capturedGeneration,
        )

        assertEquals(4L, capturedGeneration)
        assertFalse(published)
        verify(exactly = 0) { databaseHolder.get() }
        coVerify(exactly = 1) {
            homeSnapshotRepository.runHomeDataMutationIfGenerationCurrent<Unit>(4L, any())
        }
    }

    @Test
    fun publishDisplayArtifactIfCurrent_artifactWriteFailurePropagates() = runTest {
        val databaseHolder: DatabaseHolder = mockk()
        val failingDatabase: HrtTrackerDatabase = mockk()
        val calibrationDao: PkCalibrationDao = mockk()
        every { databaseHolder.get() } returns failingDatabase
        every { failingDatabase.pkCalibrationDao() } returns calibrationDao
        coEvery { calibrationDao.upsertDisplayArtifact(any()) } throws
            IOException("artifact write failed")
        val homeSnapshotRepository: HomeSnapshotRepository = mockk()
        coEvery {
            homeSnapshotRepository.runHomeDataMutationIfGenerationCurrent<Unit>(8L, any())
        } coAnswers {
            secondArg<suspend (Long) -> Unit>().invoke(9L)
            HomeDataConditionalMutationResult.Published(9L, Unit)
        }
        val repository = PkCalibrationStorageRepository(
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
        )

        var failure: Throwable? = null
        try {
            repository.publishDisplayArtifactIfCurrent(
                artifact = artifact(),
                expectedInputGeneration = 8L,
            )
        } catch (throwable: IOException) {
            failure = throwable
        }

        assertEquals("artifact write failed", failure?.message)
        coVerify(exactly = 1) {
            calibrationDao.upsertDisplayArtifact(
                match { entity -> entity.homeSnapshotGeneration == 9L }
            )
        }
    }

    @Test
    fun panelSave_reordersInPlace_preservesMetadata_thenAnalyteChangeResetsIt() = runTest {
        val dao = database.bloodTestDao()
        val panel = panel("panel-a")
        val e2 = result("result-e2", panel.uuid, 0, "e2", value = 100.0)
        val testosterone = result("result-t", panel.uuid, 1, "testosterone", value = 20.0)
        dao.insertPanel(panel)
        dao.insertResults(listOf(e2, testosterone))
        database.pkCalibrationDao().insertMetadata(
            listOf(excludedMetadata(e2.uuid))
        )

        dao.upsertPanelWithResults(
            panel = panel.copy(collectedAtInstantEpochMillis = 2_000L),
            results = listOf(
                testosterone.copy(displayOrder = 0),
                e2.copy(
                    displayOrder = 1,
                    value = 367.1,
                    unitSnapshot = "pmol_l",
                    canonicalValue = 100.0,
                ),
            ),
        )

        val reordered = dao.getPanel(panel.uuid)!!
        assertEquals(2_000L, reordered.panel.collectedAtInstantEpochMillis)
        assertEquals(
            listOf(testosterone.uuid, e2.uuid),
            reordered.results.sortedBy(BloodTestResultEntity::displayOrder)
                .map(BloodTestResultEntity::uuid),
        )
        assertNotNull(database.pkCalibrationDao().getMetadata(e2.uuid))

        dao.upsertPanelWithResults(
            panel = reordered.panel,
            results = listOf(
                testosterone.copy(displayOrder = 0),
                e2.copy(
                    displayOrder = 1,
                    builtinAnalyteKey = "fsh",
                    value = 5.0,
                    unitSnapshot = "miu_ml",
                    canonicalValue = 5.0,
                ),
            ),
        )

        assertNull(database.pkCalibrationDao().getMetadata(e2.uuid))
    }

    @Test
    fun panelSave_deletesOnlyRemovedResult_andCascadeDeletesItsMetadata() = runTest {
        val dao = database.bloodTestDao()
        val panel = panel("panel-b")
        val e2 = result("result-removed", panel.uuid, 0, "e2")
        val retained = result("result-retained", panel.uuid, 1, "testosterone")
        dao.insertPanel(panel)
        dao.insertResults(listOf(e2, retained))
        database.pkCalibrationDao().insertMetadata(listOf(excludedMetadata(e2.uuid)))

        dao.upsertPanelWithResults(panel, listOf(retained.copy(displayOrder = 0)))

        assertEquals(listOf(retained.uuid), dao.getPanel(panel.uuid)!!.results.map { it.uuid })
        assertNull(database.pkCalibrationDao().getMetadata(e2.uuid))
    }

    @Test
    fun failedPanelSave_rollsBackParkedRowsAndMetadata() = runTest {
        val dao = database.bloodTestDao()
        val panel = panel("panel-c")
        val e2 = result("result-rollback-e2", panel.uuid, 0, "e2")
        val testosterone = result("result-rollback-t", panel.uuid, 1, "testosterone")
        dao.insertPanel(panel)
        dao.insertResults(listOf(e2, testosterone))
        database.pkCalibrationDao().insertMetadata(listOf(excludedMetadata(e2.uuid)))

        try {
            dao.upsertPanelWithResults(
                panel,
                listOf(
                    e2.copy(
                        displayOrder = 0,
                        builtinAnalyteKey = "fsh",
                        unitSnapshot = "miu_ml",
                        value = 5.0,
                        canonicalValue = 5.0,
                    ),
                    testosterone.copy(displayOrder = 0),
                ),
            )
            fail("Expected duplicate displayOrder to fail.")
        } catch (_: SQLiteConstraintException) {
            // Room rolls the parking updates back with the failed final update.
        }

        val restored = dao.getPanel(panel.uuid)!!.results.sortedBy { it.displayOrder }
        assertEquals(listOf(e2, testosterone), restored)
        assertNotNull(database.pkCalibrationDao().getMetadata(e2.uuid))
    }

    @Test
    fun metadataRepository_savesAndRoundTripsAcceptedAndExcluded() = runTest {
        val acceptedId = UUID.fromString("00000000-0000-0000-0000-000000000c01")
        val excludedId = UUID.fromString("00000000-0000-0000-0000-000000000c02")
        val acceptedPanel = panel("panel-metadata-accepted")
        val excludedPanel = panel("panel-metadata-excluded")
        database.bloodTestDao().insertPanels(listOf(acceptedPanel, excludedPanel))
        database.bloodTestDao().insertResults(
            listOf(
                result(acceptedId.toString(), acceptedPanel.uuid, 0, "e2"),
                result(excludedId.toString(), excludedPanel.uuid, 0, "e2"),
            )
        )
        val accepted = checkNotNull(
            E2CalibrationMetadata.create(
                resultId = acceptedId,
                disposition = E2CalibrationDisposition.ACCEPTED,
                acceptedRecord = checkNotNull(
                    PkCalibrationAcceptanceRecord.create(
                        calibrationModelVersion = "pk-calibration:test/v9",
                        sourceValueBits = "4059000000000000",
                        collectedAtEpochMillis = 600L,
                        unitId = "hrttracker:unit/pg-ml/v1",
                    )
                ),
                updatedAt = Instant.ofEpochMilli(2_000L),
            )
        )
        val excluded = checkNotNull(
            E2CalibrationMetadata.create(
                resultId = excludedId,
                disposition = E2CalibrationDisposition.EXCLUDED,
                acceptedRecord = null,
                updatedAt = Instant.ofEpochMilli(3_000L),
            )
        )
        val repository = storageRepository()

        assertTrue(repository.saveMetadataIfCurrent(accepted, expectedGeneration = 6L))
        assertTrue(repository.saveMetadataIfCurrent(excluded, expectedGeneration = 7L))

        assertEquals(
            mapOf(acceptedId to accepted, excludedId to excluded),
            repository.getAllMetadata().associateBy(E2CalibrationMetadata::resultId),
        )
    }

    @Test
    fun metadataRepository_rejectsMissingAndNonE2Results() = runTest {
        val nonE2Id = UUID.fromString("00000000-0000-0000-0000-000000000c11")
        val missingId = UUID.fromString("00000000-0000-0000-0000-000000000c12")
        val panel = panel("panel-metadata-non-e2")
        database.bloodTestDao().insertPanel(panel)
        database.bloodTestDao().insertResults(
            listOf(result(nonE2Id.toString(), panel.uuid, 0, "testosterone"))
        )
        val repository = storageRepository()

        assertMetadataSaveRejected(repository, excludedModel(missingId))
        assertMetadataSaveRejected(repository, excludedModel(nonE2Id))

        assertEquals(emptyList<E2CalibrationMetadata>(), repository.getAllMetadata())
    }

    @Test
    fun metadataRepository_rechecksE2TargetInsideWriteTransaction() = runTest {
        val resultId = UUID.fromString("00000000-0000-0000-0000-000000000c13")
        val panel = panel("panel-metadata-target-race")
        val e2Result = result(resultId.toString(), panel.uuid, 0, "e2")
        database.bloodTestDao().insertPanel(panel)
        database.bloodTestDao().insertResults(listOf(e2Result))
        val databaseHolder: DatabaseHolder = mockk()
        every { databaseHolder.get() } returns database
        coEvery { databaseHolder.withTransaction<Unit>(any()) } coAnswers {
            database.withTransaction {
                firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
            }
        }
        val homeSnapshotRepository: HomeSnapshotRepository = mockk()
        coEvery {
            homeSnapshotRepository.runHomeDataMutationIfGenerationCurrent<Unit>(6L, any())
        } coAnswers {
            database.bloodTestDao().updateResults(
                listOf(e2Result.copy(builtinAnalyteKey = "testosterone"))
            )
            secondArg<suspend (Long) -> Unit>().invoke(7L)
            HomeDataConditionalMutationResult.Published(7L, Unit)
        }
        val repository = PkCalibrationStorageRepository(
            databaseHolder,
            homeSnapshotRepository,
        )

        assertMetadataSaveRejected(repository, excludedModel(resultId))

        assertEquals(emptyList<E2CalibrationMetadata>(), repository.getAllMetadata())
        coVerify(exactly = 1) {
            homeSnapshotRepository.runHomeDataMutationIfGenerationCurrent<Unit>(6L, any())
        }
    }

    @Test
    fun displayArtifact_roundTripsExactZeroPromotion_andDiscardsStaleOrMalformedRows() = runTest {
        val databaseHolder: DatabaseHolder = mockk()
        every { databaseHolder.get() } returns database
        coEvery {
            databaseHolder.withTransaction<PersistedPkCalibrationDisplay?>(any())
        } coAnswers {
            database.withTransaction {
                firstArg<suspend (HrtTrackerDatabase) -> PersistedPkCalibrationDisplay?>()
                    .invoke(database)
            }
        }
        val repository = PkCalibrationStorageRepository(
            databaseHolder,
            homeSnapshotRepository(),
        )
        val digest = digest("hrttracker.fit-input/test", 'a')
        val artifact = checkNotNull(
            PersistedPkCalibrationDisplay.create(
                calibrationModelVersion = "route-v9-final",
                resultInputDigest = digest,
                promotedRoutes = listOf(PkCalibrationRoute.PATCH, PkCalibrationRoute.GEL),
                displayRouteLogScaleByRoute = mapOf(PkCalibrationRoute.GEL to 0.2),
            )
        )

        val capturedGeneration = repository.captureHomeDataGeneration()
        assertTrue(
            repository.publishDisplayArtifactIfCurrent(
                artifact = artifact,
                expectedInputGeneration = capturedGeneration,
            )
        )

        assertEquals(
            artifact,
            repository.readDisplayArtifact("route-v9-final", digest),
        )
        val staleDigest = digest("hrttracker.fit-input/test", 'b')
        assertNull(repository.readDisplayArtifact("route-v9-final", staleDigest))
        assertNull(database.pkCalibrationDao().getDisplayArtifact())

        database.pkCalibrationDao().upsertDisplayArtifact(
            artifactEntity().copy(promotedRouteStableIds = "gel,patch")
        )
        assertNull(repository.readDisplayArtifact("route-v9-final", digest))
        assertNull(database.pkCalibrationDao().getDisplayArtifact())
    }

    @Test
    fun homeDisplayProjection_includesOnlyMatchingGenerationAndModel_andOmitsInvalidRows() {
        val expected = artifactEntity().toModel()
        assertEquals(
            expected,
            artifactEntity().toHomeCalibrationDisplay(
                expectedHomeSnapshotGeneration = 7L,
                expectedCalibrationModelVersion = "route-v9-final",
            ),
        )
        assertNull(
            artifactEntity(homeSnapshotGeneration = 6L).toHomeCalibrationDisplay(
                expectedHomeSnapshotGeneration = 7L,
                expectedCalibrationModelVersion = "route-v9-final",
            )
        )
        assertNull(
            artifactEntity(homeSnapshotGeneration = 8L).toHomeCalibrationDisplay(
                expectedHomeSnapshotGeneration = 7L,
                expectedCalibrationModelVersion = "route-v9-final",
            )
        )
        assertNull(
            artifactEntity(calibrationModelVersion = "route-v9-draft")
                .toHomeCalibrationDisplay(
                    expectedHomeSnapshotGeneration = 7L,
                    expectedCalibrationModelVersion = "route-v9-final",
                )
        )
        assertNull(
            artifactEntity(promotedRouteStableIds = "gel,patch")
                .toHomeCalibrationDisplay(
                    expectedHomeSnapshotGeneration = 7L,
                    expectedCalibrationModelVersion = "route-v9-final",
                )
        )
    }

    private fun panel(uuid: String) = BloodTestPanelEntity(
        uuid = uuid,
        collectedAtInstantEpochMillis = 1_000L,
        collectedAtTimeZoneId = "UTC",
        notes = null,
        timeSinceLastEstradiolDoseMillis = null,
        timeSinceLastTestosteroneDoseMillis = null,
        createdAtEpochMillis = 1_000L,
        updatedAtEpochMillis = 1_000L,
    )

    private fun result(
        uuid: String,
        panelUuid: String,
        displayOrder: Int,
        analyte: String,
        value: Double = 100.0,
    ) = BloodTestResultEntity(
        uuid = uuid,
        panelUuid = panelUuid,
        createdAtEpochMillis = 1_000L,
        displayOrder = displayOrder,
        builtinAnalyteKey = analyte,
        customAnalyteUuid = null,
        value = value,
        unitSnapshot = "pg_ml",
        canonicalValue = value,
    )

    private fun excludedMetadata(resultUuid: String) = E2CalibrationMetadataEntity(
        resultUuid = resultUuid,
        disposition = "EXCLUDED",
        acceptedModelVersion = null,        acceptedSourceValueBits = null,        acceptedCollectedAtEpochMillis = null,
        acceptedUnitId = null,
        updatedAtEpochMillis = 1_000L,
    )

    private fun digest(schema: String, character: Char) = checkNotNull(
        CanonicalDigest.create(schema, "SHA-256", character.toString().repeat(64))
    )

    private fun storageRepository(): PkCalibrationStorageRepository {
        val databaseHolder: DatabaseHolder = mockk()
        every { databaseHolder.get() } returns database
        coEvery { databaseHolder.withTransaction<Unit>(any()) } coAnswers {
            database.withTransaction {
                firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
            }
        }
        return PkCalibrationStorageRepository(
            databaseHolder,
            homeSnapshotRepository(),
        )
    }

    private fun homeSnapshotRepository(
        generation: Long = 7L,
    ): HomeSnapshotRepository {
        val repository: HomeSnapshotRepository = mockk()
        var currentGeneration = generation - 1L
        coEvery { repository.runHomeDataMutation<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery { repository.captureCurrentHomeDataGeneration() } coAnswers {
            currentGeneration
        }
        coEvery {
            repository.runHomeDataMutationIfGenerationCurrent<Unit>(any(), any())
        } coAnswers {
            val expectedGeneration = firstArg<Long>()
            if (expectedGeneration != currentGeneration) {
                HomeDataConditionalMutationResult.Stale(
                    expectedGeneration = expectedGeneration,
                    currentGeneration = currentGeneration,
                )
            } else {
                currentGeneration += 1L
                secondArg<suspend (Long) -> Unit>().invoke(currentGeneration)
                HomeDataConditionalMutationResult.Published(currentGeneration, Unit)
            }
        }
        return repository
    }

    private fun artifact(): PersistedPkCalibrationDisplay = checkNotNull(
        PersistedPkCalibrationDisplay.create(
            calibrationModelVersion = "route-v9-final",
            resultInputDigest = digest("hrttracker.fit-input/test", 'c'),
            promotedRoutes = listOf(PkCalibrationRoute.ORAL),
            displayRouteLogScaleByRoute = mapOf(PkCalibrationRoute.ORAL to 0.1),
        )
    )

    private fun excludedModel(resultId: UUID): E2CalibrationMetadata = checkNotNull(
        E2CalibrationMetadata.create(
            resultId = resultId,
            disposition = E2CalibrationDisposition.EXCLUDED,
            acceptedRecord = null,
            updatedAt = Instant.ofEpochMilli(4_000L),
        )
    )

    private suspend fun assertMetadataSaveRejected(
        repository: PkCalibrationStorageRepository,
        metadata: E2CalibrationMetadata,
    ) {
        try {
            repository.saveMetadataIfCurrent(metadata, expectedGeneration = 6L)
            fail("Expected metadata save to reject a missing or non-E2 result.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun artifactEntity(
        homeSnapshotGeneration: Long = 7L,
        calibrationModelVersion: String = "route-v9-final",
        promotedRouteStableIds: String = "patch,gel",
    ) = PkCalibrationDisplayArtifactEntity(
        singletonId = PK_CALIBRATION_DISPLAY_SINGLETON_ID,
        homeSnapshotGeneration = homeSnapshotGeneration,
        schema = PK_CALIBRATION_DISPLAY_SCHEMA,
        calibrationModelVersion = calibrationModelVersion,
        resultInputDigestSchema = "hrttracker.fit-input/test",
        resultInputDigestAlgorithm = "SHA-256",
        resultInputDigestHexLower = "a".repeat(64),
        promotedRouteStableIds = promotedRouteStableIds,
        injectionLogScale = null,
        patchLogScale = null,
        gelLogScale = 0.2,
        oralLogScale = null,
        sublingualLogScale = null,
    )
}
