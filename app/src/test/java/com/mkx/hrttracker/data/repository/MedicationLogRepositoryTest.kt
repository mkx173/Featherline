package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationLogDao
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.data.local.MedicineDao
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.DoseInstructionKind
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationLogRepositoryTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val database: HrtTrackerDatabase = mockk()
    private val dao: MedicationLogDao = mockk(relaxed = true)
    private val medicineDao: MedicineDao = mockk(relaxed = true)
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk(relaxed = true)
    private val stockMutator: MedicineStockMutator = mockk(relaxed = true)

    private lateinit var repository: MedicationLogRepository

    @Before
    fun setUp() {
        every { databaseHolder.databaseFlow } returns MutableStateFlow(null)
        every { databaseHolder.get() } returns database
        every { database.medicationLogDao() } returns dao
        every { database.medicineDao() } returns medicineDao
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
    fun deleteAllEntries_clears_logs_in_single_transaction() = runTest {
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { dao.deleteAllEntries() } returns Unit

        repository.deleteAllEntries()

        coVerify(exactly = 1) { databaseHolder.withTransaction<Unit>(any()) }
        coVerify(exactly = 1) { dao.deleteAllEntries() }
    }

    @Test
    fun deleteEntriesForGroup_clears_group_logs_in_single_transaction() = runTest {
        val groupUuid = UUID.fromString("83b0354d-b6f1-4c04-9e35-0d2583e5a07b")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { dao.deleteEntriesForGroup(groupUuid.toString()) } returns Unit

        repository.deleteEntriesForGroup(groupUuid)

        coVerify(exactly = 1) { databaseHolder.withTransaction<Unit>(any()) }
        coVerify(exactly = 1) { dao.deleteEntriesForGroup(groupUuid.toString()) }
        coVerify(exactly = 1) { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) }
    }

    @Test
    fun deleteEntries_runsInsideHomeDataMutation() = runTest {
        val entryUuid = UUID.fromString("bf5d6d17-097a-45a7-ae8e-8202aa10cf01")
        coEvery { dao.deleteEntries(listOf(entryUuid.toString())) } returns Unit

        repository.deleteEntries(listOf(entryUuid))

        coVerify(exactly = 1) { dao.deleteEntries(listOf(entryUuid.toString())) }
        coVerify(exactly = 1) { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) }
    }

    @Test
    fun saveEntry_runsWriteInsideHomeDataMutation() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        val events = mutableListOf<String>()
        coEvery {
            medicineDao.getByUuid(medicineUuid.toString())
        } returns testMedicineEntity(
            uuid = medicineUuid.toString(),
            medicationKey = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        coEvery { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) } coAnswers {
            events += "mutation-start"
            firstArg<suspend () -> Unit>().invoke()
            events += "mutation-end"
        }
        coEvery { dao.insertEntry(any()) } coAnswers {
            events += "write"
        }

        repository.saveEntry(
            uuid = null,
            medicineUuid = medicineUuid,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            sourceGroupUuid = null,
            appliedAt = Instant.parse("2026-04-30T08:00:00Z"),
        )

        assertEquals(listOf("mutation-start", "write", "mutation-end"), events)
    }

    @Test
    fun saveEntry_snapshotsCategoryAndPerUnitEquivalentE2AtCreation() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        val capturedEntry = slot<MedicationLogEntryEntity>()
        coEvery {
            medicineDao.getByUuid(medicineUuid.toString())
        } returns testMedicineEntity(
            uuid = medicineUuid.toString(),
            medicationKey = MedicationKey.ESTRADIOL_VALERATE,
            preparation = MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = 20.0,
                vialVolumeMl = 10.0,
            ),
        )
        coEvery { dao.insertEntry(capture(capturedEntry)) } returns Unit

        repository.saveEntry(
            uuid = null,
            medicineUuid = medicineUuid,
            applicationType = MedicationApplicationType.INJECTION,
            doseInstruction = DoseInstruction.VolumeMl(0.2),
            sourceGroupUuid = null,
            appliedAt = Instant.parse("2026-05-22T08:00:00Z"),
            count = 3,
        )

        assertEquals(MedicationCategory.ESTRADIOL.name, capturedEntry.captured.category)
        assertEquals(medicineUuid.toString(), capturedEntry.captured.medicineUuid)
        assertEquals(DoseInstructionKind.VOLUME_ML.name, capturedEntry.captured.doseInstructionKind)
        assertEquals(0.2, capturedEntry.captured.doseVolumeMl!!, 0.000001)
        assertEquals(
            4.0 * (272.4 / 356.5),
            capturedEntry.captured.equivalentE2Mg!!,
            0.000001,
        )
    }

    @Test
    fun saveEntry_multiUseVial_deductsDeltaAdjustedVolume() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        val capturedEntry = slot<MedicationLogEntryEntity>()
        coEvery {
            medicineDao.getByUuid(medicineUuid.toString())
        } returns testMedicineEntity(
            uuid = medicineUuid.toString(),
            medicationKey = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = 20.0,
                vialVolumeMl = 5.0,
            ),
        )
        coEvery { dao.insertEntry(capture(capturedEntry)) } returns Unit

        repository.saveEntry(
            uuid = null,
            medicineUuid = medicineUuid,
            applicationType = MedicationApplicationType.INJECTION,
            doseInstruction = DoseInstruction.VolumeMl(0.5),
            sourceGroupUuid = null,
            appliedAt = Instant.EPOCH,
            count = 1,
            doseAmountDelta = 0.1,
        )

        coVerify(exactly = 1) {
            stockMutator.resolveDeductionForInsert(
                database = database,
                medicineUuid = medicineUuid,
                requestedDose = 0.6,
                now = any(),
            )
        }
        assertEquals(12.0, capturedEntry.captured.equivalentE2Mg!!, 1e-9)
        assertEquals(0.1, capturedEntry.captured.doseAmountDelta!!, 1e-9)
    }

    @Test
    fun saveEntry_singleUseVial_deductsWholeUnitAndStoresDeltaAdjustedE2() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        val capturedEntry = slot<MedicationLogEntryEntity>()
        coEvery {
            medicineDao.getByUuid(medicineUuid.toString())
        } returns testMedicineEntity(
            uuid = medicineUuid.toString(),
            medicationKey = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.InjectionSingleUseVial(
                strengthMgPerVial = 10.0,
            ),
        )
        coEvery { dao.insertEntry(capture(capturedEntry)) } returns Unit

        repository.saveEntry(
            uuid = null,
            medicineUuid = medicineUuid,
            applicationType = MedicationApplicationType.INJECTION,
            doseInstruction = DoseInstruction.WholeUnit,
            sourceGroupUuid = null,
            appliedAt = Instant.EPOCH,
            count = 1,
            doseAmountDelta = 2.0,
        )

        coVerify(exactly = 1) {
            stockMutator.resolveDeductionForInsert(
                database = database,
                medicineUuid = medicineUuid,
                requestedDose = 1.0,
                now = any(),
            )
        }
        assertEquals(12.0, capturedEntry.captured.equivalentE2Mg!!, 1e-9)
        assertEquals(2.0, capturedEntry.captured.doseAmountDelta!!, 1e-9)
    }

    @Test
    fun saveEntry_editOnlyUpdatesDateTimeAndCount() = runTest {
        val entryUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000000")
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        val appliedAt = Instant.parse("2026-05-22T09:30:00Z")
        coEvery {
            dao.updateEditableLogFields(
                uuid = entryUuid.toString(),
                appliedAtEpochMillis = appliedAt.toEpochMilli(),
                appliedAtTimeZoneId = "Asia/Tokyo",
                scheduledForIso = null,
                count = 2,
            )
        } returns Unit

        repository.saveEntry(
            uuid = entryUuid,
            medicineUuid = medicineUuid,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            sourceGroupUuid = null,
            appliedAt = appliedAt,
            count = 2,
            appliedAtTimeZoneId = "Asia/Tokyo",
        )

        coVerify(exactly = 1) {
            dao.updateEditableLogFields(
                uuid = entryUuid.toString(),
                appliedAtEpochMillis = appliedAt.toEpochMilli(),
                appliedAtTimeZoneId = "Asia/Tokyo",
                scheduledForIso = null,
                count = 2,
            )
        }
        coVerify(exactly = 0) { dao.insertEntry(any()) }
        coVerify(exactly = 0) { medicineDao.getByUuid(any()) }
    }

    @Test
    fun saveEntry_rejectsIncompatibleApplicationTypeBeforeInsert() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        coEvery {
            medicineDao.getByUuid(medicineUuid.toString())
        } returns testMedicineEntity(
            uuid = medicineUuid.toString(),
            medicationKey = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
        )

        val thrown = runCatching {
            repository.saveEntry(
                uuid = null,
                medicineUuid = medicineUuid,
                applicationType = MedicationApplicationType.SUBLINGUAL,
                doseInstruction = DoseInstruction.WholeUnit,
                sourceGroupUuid = null,
                appliedAt = Instant.parse("2026-05-22T08:00:00Z"),
            )
        }.exceptionOrNull()

        assertEquals(
            "medication log applicationType=SUBLINGUAL is not compatible with preparation=CAPSULE.",
            thrown?.message,
        )
        coVerify(exactly = 0) {
            stockMutator.resolveDeductionForInsert(any(), any(), any(), any())
        }
        coVerify(exactly = 0) { dao.insertEntry(any()) }
    }

    @Test
    fun saveEntry_rejectsPillWholeUnitBeforeInsert() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        coEvery {
            medicineDao.getByUuid(medicineUuid.toString())
        } returns testMedicineEntity(
            uuid = medicineUuid.toString(),
            medicationKey = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )

        val thrown = runCatching {
            repository.saveEntry(
                uuid = null,
                medicineUuid = medicineUuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.WholeUnit,
                sourceGroupUuid = null,
                appliedAt = Instant.parse("2026-05-22T08:00:00Z"),
            )
        }.exceptionOrNull()

        assertEquals(
            "medication log doseInstruction=WHOLE_UNIT is not compatible with preparation=PILL.",
            thrown?.message,
        )
        coVerify(exactly = 0) { dao.insertEntry(any()) }
    }

    @Test
    fun saveNewEntries_rejectsMissingMedicineBeforeInsert() = runTest {
        val medicineUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        coEvery { medicineDao.getByUuid(medicineUuid.toString()) } returns null

        val thrown = runCatching {
            repository.saveNewEntries(
                listOf(
                    MedicationLogEntryInput(
                        medicineUuid = medicineUuid,
                        applicationType = MedicationApplicationType.ORAL,
                        doseInstruction = DoseInstruction.TabletFraction(1, 1),
                        sourceGroupUuid = null,
                        appliedAt = Instant.parse("2026-05-22T08:00:00Z"),
                    )
                )
            )
        }.exceptionOrNull()

        assertEquals("Medicine $medicineUuid was not found.", thrown?.message)
        coVerify(exactly = 0) { dao.insertEntries(any()) }
    }

    @Test
    fun getObservedLatestEstradiolEntryOnOrBefore_returnsLatestObservedEntryAtOrBeforeTarget() = runTest {
        val target = Instant.parse("2026-04-30T00:00:00Z")
        val medicineUuid = "aaaaaaaa-0000-0000-0000-000000000000"
        val latestEntry = testMedicationLogEntryEntity(
            uuid = "8bbef05b-368d-4ae4-9e9d-4a83e35f8d9c",
            appliedAt = target.minus(Duration.ofDays(60)),
            medicineUuid = medicineUuid,
        )
        val olderEntry = testMedicationLogEntryEntity(
            uuid = "e93047ed-1825-4f9f-a017-3b93d4f0698e",
            appliedAt = target.minus(Duration.ofDays(90)),
            medicineUuid = medicineUuid,
        )
        val futureEntry = testMedicationLogEntryEntity(
            uuid = "151986f6-7981-4734-ad5b-5366c5dcd931",
            appliedAt = target.plus(Duration.ofHours(1)),
            medicineUuid = medicineUuid,
        )
        val antiandrogenEntry = testMedicationLogEntryEntity(
            uuid = "e22f2f1e-1cb5-4137-83ca-f5d35417cbcd",
            appliedAt = target.minus(Duration.ofDays(2)),
            category = MedicationCategory.ANTIANDROGEN,
            medicineUuid = medicineUuid,
        )

        val repository = repositoryWithObservedEntries(
            entries = listOf(
                futureEntry,
                latestEntry,
                antiandrogenEntry,
                olderEntry,
            ),
            medicineEntities = listOf(
                testMedicineEntity(
                    uuid = medicineUuid,
                    medicationKey = MedicationKey.ESTRADIOL,
                    preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
                ),
            ),
            appScope = CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            ),
        )
        advanceUntilIdle()

        val lookup = repository.getObservedLatestEstradiolEntryOnOrBefore(target)

        assertTrue(lookup is ObservedEstradiolEntryLookup.Loaded)
        assertEquals(
            latestEntry.uuid,
            (lookup as ObservedEstradiolEntryLookup.Loaded).entry?.uuid.toString(),
        )
    }

    @Test
    fun getObservedLatestEstradiolEntryOnOrBefore_returnsNotLoadedBeforeEntriesFlowLoads() {
        assertEquals(
            ObservedEstradiolEntryLookup.NotLoaded,
            repository.getObservedLatestEstradiolEntryOnOrBefore(
                Instant.parse("2026-04-30T00:00:00Z")
            ),
        )
    }

    private fun repositoryWithObservedEntries(
        entries: List<MedicationLogEntryEntity>,
        medicineEntities: List<MedicineEntity>,
        appScope: CoroutineScope,
    ): MedicationLogRepository {
        every { databaseHolder.databaseFlow } returns MutableStateFlow<HrtTrackerDatabase?>(database)
        every { databaseHolder.get() } returns database
        every { database.medicationLogDao() } returns dao
        every { database.medicineDao() } returns medicineDao
        every { dao.observeEntries() } returns MutableStateFlow(entries)
        every { medicineDao.observeMedicineChangeVersion() } returns MutableStateFlow(0)
        coEvery { medicineDao.getByUuids(any()) } returns medicineEntities

        return MedicationLogRepository(
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            stockMutator = stockMutator,
            appScope = appScope,
        )
    }

    private fun testMedicationLogEntryEntity(
        uuid: String,
        appliedAt: Instant,
        category: MedicationCategory = MedicationCategory.ESTRADIOL,
        medicineUuid: String? = "aaaaaaaa-0000-0000-0000-000000000000",
    ): MedicationLogEntryEntity {
        return MedicationLogEntryEntity(
            uuid = uuid,
            category = category.name,
            medicineUuid = medicineUuid,
            applicationType = MedicationApplicationType.ORAL.name,
            doseInstructionKind = DoseInstructionKind.TABLET_FRACTION.name,
            tabletFractionNumerator = 1,
            tabletFractionDenominator = 1,
            doseVolumeMl = null,
            doseWeightGrams = null,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = null,
            appliedAtEpochMillis = appliedAt.toEpochMilli(),
        )
    }

    private fun testMedicineEntity(
        uuid: String,
        medicationKey: MedicationKey,
        preparation: MedicinePreparation,
    ): MedicineEntity {
        val fields = preparation.toStorageFields()
        return MedicineEntity(
            uuid = uuid,
            selectionKind = MedicationSelectionKind.CATALOG.name,
            medicationKey = medicationKey.name,
            customMedicationName = null,
            customMedicationNameNormalized = null,
            category = medicationKey.category.name,
            preparationType = fields.preparationType,
            strengthMgPerTablet = fields.strengthMgPerTablet,
            strengthMgPerVial = fields.strengthMgPerVial,
            concentrationMgPerMl = fields.concentrationMgPerMl,
            vialVolumeMl = fields.vialVolumeMl,
            concentrationPercent = fields.concentrationPercent,
            sachetWeightGrams = fields.sachetWeightGrams,
            containerWeightGrams = fields.containerWeightGrams,
            patchTotalMg = fields.patchTotalMg,
            patchReleaseRateMcgPerDay = fields.patchReleaseRateMcgPerDay,
            displayName = null,
            identityKey = MedicineIdentityKey.catalog(medicationKey, preparation),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
            archivedAtEpochMillis = null,
        )
    }
}
