package com.mkx.hrttracker.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.PkCalibrationStorageRepository
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
    fun panelSave_exclusionSurvivesValueAndCollectionTimeEdits() = runTest {
        // Explicit exclusions are the user's decision about a result, not a
        // property of its value: value/unit/collection-time edits keep them.
        val dao = database.bloodTestDao()
        val edited = panel("panel-excl-edit")
        val editedResult = result("result-excl-edit", edited.uuid, 0, "e2", value = 70.0)
        val timeShift = panel("panel-excl-time")
        val timeShiftResult = result("result-excl-time", timeShift.uuid, 0, "e2", value = 80.0)
        dao.insertPanels(listOf(edited, timeShift))
        dao.insertResults(listOf(editedResult, timeShiftResult))
        database.pkCalibrationDao().insertMetadata(
            listOf(
                excludedMetadata(editedResult.uuid),
                excludedMetadata(timeShiftResult.uuid),
            )
        )

        dao.upsertPanelWithResults(edited, listOf(editedResult.copy(value = 75.0)))
        dao.upsertPanelWithResults(
            timeShift.copy(collectedAtInstantEpochMillis = 5_000L),
            listOf(timeShiftResult),
        )

        assertEquals(
            "EXCLUDED",
            database.pkCalibrationDao().getMetadata(editedResult.uuid)?.disposition,
        )
        assertEquals(
            "EXCLUDED",
            database.pkCalibrationDao().getMetadata(timeShiftResult.uuid)?.disposition,
        )
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
    fun metadataRepository_savesAndRoundTripsAutoAndExcluded() = runTest {
        val autoId = UUID.fromString("00000000-0000-0000-0000-000000000c01")
        val excludedId = UUID.fromString("00000000-0000-0000-0000-000000000c02")
        val autoPanel = panel("panel-metadata-auto")
        val excludedPanel = panel("panel-metadata-excluded")
        database.bloodTestDao().insertPanels(listOf(autoPanel, excludedPanel))
        database.bloodTestDao().insertResults(
            listOf(
                result(autoId.toString(), autoPanel.uuid, 0, "e2"),
                result(excludedId.toString(), excludedPanel.uuid, 0, "e2"),
            )
        )
        val auto = E2CalibrationMetadata(
            resultId = autoId,
            disposition = E2CalibrationDisposition.AUTO,
            updatedAt = Instant.ofEpochMilli(2_000L),
        )
        val excluded = E2CalibrationMetadata(
            resultId = excludedId,
            disposition = E2CalibrationDisposition.EXCLUDED,
            updatedAt = Instant.ofEpochMilli(3_000L),
        )
        val homeSnapshotRepository = homeSnapshotRepository()
        val repository = storageRepository(homeSnapshotRepository)

        repository.saveMetadata(auto)
        repository.saveMetadata(excluded)
        // Re-excluding overwrites in place (upsert), no duplicate rows.
        val reExcluded = excluded.copy(updatedAt = Instant.ofEpochMilli(4_000L))
        repository.saveMetadata(reExcluded)

        assertEquals(
            mapOf(autoId to auto, excludedId to reExcluded),
            repository.getAllMetadata().associateBy(E2CalibrationMetadata::resultId),
        )
        coVerify(exactly = 3) { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) }
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
        val homeSnapshotRepository: HomeSnapshotRepository = mockk()
        // The target flips to a non-E2 analyte while waiting on Home's mutation lock.
        coEvery { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) } coAnswers {
            database.bloodTestDao().updateResults(
                listOf(e2Result.copy(builtinAnalyteKey = "testosterone"))
            )
            firstArg<suspend () -> Unit>().invoke()
        }
        val repository = storageRepository(homeSnapshotRepository)

        assertMetadataSaveRejected(repository, excludedModel(resultId))

        assertEquals(emptyList<E2CalibrationMetadata>(), repository.getAllMetadata())
        coVerify(exactly = 1) { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) }
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
        updatedAtEpochMillis = 1_000L,
    )

    private fun storageRepository(
        homeSnapshotRepository: HomeSnapshotRepository = homeSnapshotRepository(),
    ): PkCalibrationStorageRepository {
        val databaseHolder: DatabaseHolder = mockk()
        every { databaseHolder.get() } returns database
        coEvery { databaseHolder.withTransaction<Unit>(any()) } coAnswers {
            database.withTransaction {
                firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
            }
        }
        return PkCalibrationStorageRepository(databaseHolder, homeSnapshotRepository)
    }

    private fun homeSnapshotRepository(): HomeSnapshotRepository {
        val repository: HomeSnapshotRepository = mockk()
        coEvery { repository.runHomeDataMutation<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        return repository
    }

    private fun excludedModel(resultId: UUID) = E2CalibrationMetadata(
        resultId = resultId,
        disposition = E2CalibrationDisposition.EXCLUDED,
        updatedAt = Instant.ofEpochMilli(4_000L),
    )

    private suspend fun assertMetadataSaveRejected(
        repository: PkCalibrationStorageRepository,
        metadata: E2CalibrationMetadata,
    ) {
        try {
            repository.saveMetadata(metadata)
            fail("Expected metadata save to reject a missing or non-E2 result.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
