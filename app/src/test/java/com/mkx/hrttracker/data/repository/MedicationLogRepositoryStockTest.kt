package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationLogDao
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.data.local.MedicineDao
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

class MedicationLogRepositoryStockTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val database: HrtTrackerDatabase = mockk()
    private val medicineDao: MedicineDao = mockk(relaxed = true)
    private val logDao: MedicationLogDao = mockk(relaxed = true)
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk(relaxed = true)
    private val stockMutator: MedicineStockMutator = mockk(relaxed = true)

    private lateinit var repository: MedicationLogRepository

    private val medicineUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Before
    fun setUp() {
        every { databaseHolder.databaseFlow } returns MutableStateFlow(null)
        every { databaseHolder.get() } returns database
        every { database.medicineDao() } returns medicineDao
        every { database.medicationLogDao() } returns logDao
        coEvery { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery { databaseHolder.withTransaction<Unit>(any()) } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }

        repository = MedicationLogRepository(
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            stockMutator = stockMutator,
            appScope = CoroutineScope(StandardTestDispatcher()),
        )
    }

    @Test
    fun saveEntry_invokesMutatorBeforeInsert() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns pillEntity()

        val captured = slot<MedicationLogEntryEntity>()
        coEvery { logDao.insertEntry(capture(captured)) } returns Unit

        repository.saveEntry(
            uuid = null,
            medicineUuid = medicineUuid,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            sourceGroupUuid = null,
            appliedAt = Instant.ofEpochMilli(0L),
        )

        coVerifyOrder {
            stockMutator.resolveDeductionForInsert(
                database = database,
                medicineUuid = medicineUuid,
                requestedDose = 1.0,
                now = any(),
            )
            logDao.insertEntry(any())
        }
        assertEquals(medicineUuid.toString(), captured.captured.medicineUuid)
    }

    @Test
    fun saveNewEntries_invokesMutatorForEachEntryBeforeInsert() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns pillEntity()

        val captured = slot<List<MedicationLogEntryEntity>>()
        coEvery { logDao.insertEntries(capture(captured)) } returns Unit

        repository.saveNewEntries(
            listOf(
                MedicationLogEntryInput(
                    medicineUuid = medicineUuid,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                    sourceGroupUuid = null,
                    appliedAt = Instant.ofEpochMilli(0L),
                    count = 1,
                ),
                MedicationLogEntryInput(
                    medicineUuid = medicineUuid,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                    sourceGroupUuid = null,
                    appliedAt = Instant.ofEpochMilli(1L),
                    count = 2,
                ),
            )
        )

        coVerify(exactly = 1) { medicineDao.getByUuid(medicineUuid.toString()) }
        coVerifyOrder {
            stockMutator.resolveDeductionForInsert(
                database = database,
                medicineUuid = medicineUuid,
                requestedDose = 1.0,
                now = any(),
            )
            stockMutator.resolveDeductionForInsert(
                database = database,
                medicineUuid = medicineUuid,
                requestedDose = 2.0,
                now = any(),
            )
            logDao.insertEntries(any())
        }
        assertEquals(listOf(1, 2), captured.captured.map { it.count })
    }

    @Test
    fun saveBackfillEntries_defaultSkipsMutator() = runTest {
        // Backfill flows (batch-add, "show and generate past records") are
        // retrospective reconciliation — the doses left inventory long before
        // these rows were created, so stock must stay tied to the user's own
        // Adjust Stock count rather than being re-debited per backfilled entry.
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns pillEntity()

        val captured = slot<List<MedicationLogEntryEntity>>()
        coEvery { logDao.insertEntries(capture(captured)) } returns Unit

        repository.saveBackfillEntries(
            listOf(
                MedicationLogEntryInput(
                    medicineUuid = medicineUuid,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                    sourceGroupUuid = null,
                    appliedAt = Instant.ofEpochMilli(0L),
                    count = 1,
                ),
                MedicationLogEntryInput(
                    medicineUuid = medicineUuid,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                    sourceGroupUuid = null,
                    appliedAt = Instant.ofEpochMilli(1L),
                    count = 2,
                ),
            )
        )

        coVerify(exactly = 0) {
            stockMutator.resolveDeductionForInsert(any(), any(), any(), any())
        }
        assertEquals(listOf(1, 2), captured.captured.map { it.count })
    }

    @Test
    fun saveBackfillEntries_deductStockTrueInvokesMutatorForEachEntryBeforeInsert() = runTest {
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns pillEntity()

        val captured = slot<List<MedicationLogEntryEntity>>()
        coEvery { logDao.insertEntries(capture(captured)) } returns Unit

        repository.saveBackfillEntries(
            entries = listOf(
                MedicationLogEntryInput(
                    medicineUuid = medicineUuid,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                    sourceGroupUuid = null,
                    appliedAt = Instant.ofEpochMilli(0L),
                    count = 1,
                ),
                MedicationLogEntryInput(
                    medicineUuid = medicineUuid,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 2),
                    sourceGroupUuid = null,
                    appliedAt = Instant.ofEpochMilli(1L),
                    count = 4,
                ),
            ),
            deductStock = true,
        )

        coVerify(exactly = 1) { medicineDao.getByUuid(medicineUuid.toString()) }
        coVerifyOrder {
            stockMutator.resolveDeductionForInsert(
                database = database,
                medicineUuid = medicineUuid,
                requestedDose = 1.0,
                now = any(),
            )
            stockMutator.resolveDeductionForInsert(
                database = database,
                medicineUuid = medicineUuid,
                requestedDose = 2.0,
                now = any(),
            )
            logDao.insertEntries(any())
        }
        assertEquals(listOf(1, 4), captured.captured.map { it.count })
    }

    @Test
    fun saveEntries_bulkEditDoesNotDeductStock() = runTest {
        val entryUuids = listOf(
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
        )
        val appliedAt = Instant.ofEpochMilli(0L)

        repository.saveEntries(
            uuids = entryUuids,
            medicineUuid = medicineUuid,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            sourceGroupUuid = null,
            appliedAt = appliedAt,
            count = 2,
            appliedAtTimeZoneId = "Asia/Tokyo",
        )

        coVerify(exactly = 1) {
            logDao.updateEditableLogFieldsForUuids(
                uuids = entryUuids.map(UUID::toString),
                appliedAtEpochMillis = appliedAt.toEpochMilli(),
                appliedAtTimeZoneId = "Asia/Tokyo",
                scheduledForIso = null,
                count = 2,
            )
        }
        coVerify(exactly = 0) { logDao.insertEntries(any()) }
        coVerify(exactly = 0) {
            stockMutator.resolveDeductionForInsert(any(), any(), any(), any())
        }
    }

    @Test
    fun deleteEntries_deletesWithoutTouchingStock() = runTest {
        val u1 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        val u2 = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

        repository.deleteEntries(listOf(UUID.fromString(u1), UUID.fromString(u2)))

        coVerify(exactly = 1) { logDao.deleteEntries(listOf(u1, u2)) }
        confirmVerified(logDao, stockMutator)
    }

    @Test
    fun deleteEntriesForGroup_deletesWithoutTouchingStock() = runTest {
        val groupUuid = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")

        repository.deleteEntriesForGroup(groupUuid)

        coVerify(exactly = 1) { logDao.deleteEntriesForGroup(groupUuid.toString()) }
        confirmVerified(logDao, stockMutator)
    }

    @Test
    fun deleteAllEntries_deletesWithoutTouchingStock() = runTest {
        repository.deleteAllEntries()

        coVerify(exactly = 1) { logDao.deleteAllEntries() }
        confirmVerified(logDao, stockMutator)
    }

    private fun pillEntity(): MedicineEntity {
        val preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0)
        return MedicineEntity(
            uuid = medicineUuid.toString(),
            selectionKind = "CATALOG",
            medicationKey = MedicationKey.ESTRADIOL.name,
            customMedicationName = null,
            customMedicationNameNormalized = null,
            category = MedicationCategory.ESTRADIOL.name,
            preparationType = preparation.type.name,
            strengthMgPerTablet = preparation.strengthMgPerTablet,
            strengthMgPerVial = null,
            concentrationMgPerMl = null,
            vialVolumeMl = null,
            concentrationPercent = null,
            sachetWeightGrams = null,
            containerWeightGrams = null,
            patchTotalMg = null,
            patchReleaseRateMcgPerDay = null,
            displayName = null,
            identityKey = MedicineIdentityKey.catalog(MedicationKey.ESTRADIOL, preparation),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
            archivedAtEpochMillis = null,
            displayDoseUnit = MedicineDisplayDoseUnit.MG.name,
            trackingEnabled = true,
            stockUnitsRemaining = 30.0,
            stockUnitsLastTotal = 30.0,
            openContainerAmount = null,
            warnAtDaysRemaining = 14,
            stockGeneration = 1L,
        )
    }
}
