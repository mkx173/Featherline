package com.mkx.hrttracker.data.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.reminder.MedicationReminderSlot
import com.mkx.hrttracker.reminder.buildMissingScheduledLogEntries
import com.mkx.hrttracker.util.MedicationDayBoundary
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * A slot's writers (widget quick-log, notification "Log", the in-app sheet) each decide what
 * is missing from a read made before the write gate. These pin that what reaches the
 * database never goes past what the live plan still misses at that slot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MedicationLogSlotWriteTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val gate: HomeSnapshotRepository = mockk()
    private val writeLock = Mutex()
    private val arrivedAtGate = MutableStateFlow(0)

    // Writers held at the gate until this many have arrived, so all of them read the slot
    // before any of them writes.
    private var writersToHold = 0

    // Commits once, between the writers' reads and their writes, like a backup restore.
    private var replacingWrite: (suspend () -> Unit)? = null

    private lateinit var database: HrtTrackerDatabase
    private lateinit var groupRepository: MedicationGroupRepository
    private lateinit var logRepository: MedicationLogRepository

    private val estradiol = testMedicine(
        uuid = UUID.fromString("5a1e0000-0000-0000-0000-000000000001"),
        key = MedicationKey.ESTRADIOL,
        stock = MedicineStock(trackingEnabled = true, unitsRemaining = 10.0, unitsLastTotal = 10.0),
    )
    private val spironolactone = testMedicine(
        uuid = UUID.fromString("5a1e0000-0000-0000-0000-000000000002"),
        key = MedicationKey.SPIRONOLACTONE,
        preparation = MedicinePreparation.Pill(strengthMgPerTablet = 50.0),
        stock = MedicineStock(trackingEnabled = true, unitsRemaining = 10.0, unitsLastTotal = 10.0),
    )
    // Set by the first seed, which creates the plan; later seeds edit it.
    private var planUuid: UUID? = null
    private val wholeTablet = DoseInstruction.TabletFraction(1, 1)
    private val scheduledAt = LocalDateTime.of(2026, 11, 8, 8, 0)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HrtTrackerDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        every { databaseHolder.get() } returns database
        every { databaseHolder.databaseFlow } returns MutableStateFlow(null)
        coEvery { databaseHolder.withTransaction<Any?>(any()) } coAnswers {
            database.withTransaction {
                firstArg<suspend (HrtTrackerDatabase) -> Any?>().invoke(database)
            }
        }
        coEvery { gate.runHomeDataMutation<Any?>(any()) } coAnswers {
            val block = firstArg<suspend () -> Any?>()
            arrivedAtGate.update { it + 1 }
            arrivedAtGate.first { arrived -> arrived >= writersToHold }
            replacingWrite?.let { write ->
                replacingWrite = null
                write()
            }
            writeLock.withLock { block() }
        }
        val appScope = CoroutineScope(StandardTestDispatcher())
        groupRepository = MedicationGroupRepository(
            databaseHolder = databaseHolder,
            homeSnapshotRepository = gate,
            appScope = appScope,
        )
        logRepository = MedicationLogRepository(
            databaseHolder = databaseHolder,
            homeSnapshotRepository = gate,
            stockMutator = MedicineStockMutator(),
            appScope = appScope,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun twoQuickLogsRacingForAPartlyLoggedSlotLogOnlyWhatIsMissing() = runTest {
        val plan = seedPlan()
        logEarlier(plan, estradiol, count = 1, appliedAt = scheduledAt.plusMinutes(5))
        // Widget and notification both read 1 estradiol and 1 spironolactone missing.
        val missing = missingAsReadBeforeTheGate(plan)

        holdWritersAtTheGate(2)
        listOf(
            launch { logRepository.saveNewEntries(missing) },
            launch { logRepository.saveNewEntries(missing) },
        ).joinAll()

        assertEquals("estradiol on the slot", 2, loggedCount(estradiol))
        // One record of the slot doesn't stop the rest of it: count per medication.
        assertEquals("spironolactone on the slot", 1, loggedCount(spironolactone))
        assertEquals("estradiol stock", 9.0, stockOf(estradiol), 0.0)
        assertEquals("spironolactone stock", 9.0, stockOf(spironolactone), 0.0)
    }

    @Test
    fun anOpenSheetSavingAfterAQuickLogAddsNothingPastTheSlot() = runTest {
        val plan = seedPlan()
        logEarlier(plan, estradiol, count = 1, appliedAt = scheduledAt.plusMinutes(5))
        val missing = missingAsReadBeforeTheGate(plan)

        holdWritersAtTheGate(2)
        listOf(
            launch { logRepository.saveNewEntries(missing) },
            // The sheet opened on 1 estradiol still to log.
            launch { runCatching { saveFromSheet(plan, estradiol, count = 1) } },
        ).joinAll()

        assertEquals("estradiol on the slot", 2, loggedCount(estradiol))
        assertEquals("spironolactone on the slot", 1, loggedCount(spironolactone))
        assertEquals("estradiol stock", 9.0, stockOf(estradiol), 0.0)
        assertEquals("spironolactone stock", 9.0, stockOf(spironolactone), 0.0)
    }

    @Test
    fun aLateRecordStillCountsAsTheSlotsDose() = runTest {
        val plan = seedPlan()
        // Logged outside the slot's window: it doesn't count as on time, but it is the dose.
        logEarlier(plan, estradiol, count = 2, appliedAt = scheduledAt.plusHours(12).plusMinutes(30))

        runCatching { saveFromSheet(plan, estradiol, count = 2) }

        assertEquals("estradiol on the slot", 2, loggedCount(estradiol))
        assertEquals("estradiol stock", 10.0, stockOf(estradiol), 0.0)
    }

    @Test
    fun aRestoreThatDropsThePlanBeforeTheWriteLeavesNothingToLog() = runTest {
        val plan = seedPlan()
        val missing = missingAsReadBeforeTheGate(plan)
        replacingWrite = { groupRepository.deleteGroup(plan.uuid) }

        runCatching { logRepository.saveNewEntries(missing) }

        assertEquals("records on the slot", 0, database.medicationLogDao().getEntries().size)
        assertEquals("estradiol stock", 10.0, stockOf(estradiol), 0.0)
        assertEquals("spironolactone stock", 10.0, stockOf(spironolactone), 0.0)
    }

    @Test
    fun aRestoreThatChangesTheDoseBeforeTheWriteLogsOnlyTheDoseStillPlanned() = runTest {
        val plan = seedPlan()
        val missing = missingAsReadBeforeTheGate(plan)
        // The restored plan keeps the slot, but its estradiol is now half a tablet.
        replacingWrite = {
            seedPlan(
                estradiolDose = DoseInstruction.TabletFraction(1, 2),
                scheduleTimeUuid = plan.schedule.timeSlots.single().uuid,
            )
        }

        runCatching { logRepository.saveNewEntries(missing) }

        assertEquals("estradiol on the slot", 0, loggedCount(estradiol))
        assertEquals("spironolactone on the slot", 1, loggedCount(spironolactone))
        assertEquals("estradiol stock", 10.0, stockOf(estradiol), 0.0)
        assertEquals("spironolactone stock", 9.0, stockOf(spironolactone), 0.0)
    }

    @Test
    fun aRestoreThatMovesTheSlotBeforeTheWriteLeavesNothingToLog() = runTest {
        val plan = seedPlan()
        val missing = missingAsReadBeforeTheGate(plan)
        // The restored plan has the same doses, but at 09:00.
        replacingWrite = { seedPlan(time = LocalTime.of(9, 0)) }

        runCatching { logRepository.saveNewEntries(missing) }

        assertEquals("records on the slot", 0, database.medicationLogDao().getEntries().size)
        assertEquals("estradiol stock", 10.0, stockOf(estradiol), 0.0)
        assertEquals("spironolactone stock", 10.0, stockOf(spironolactone), 0.0)
    }

    @Test
    fun aNightOwlsPastMidnightDoseLoggedTwiceIsWrittenOnce() = runTest {
        val plan = seedPlan(time = LocalTime.of(1, 0), dayBoundary = MedicationDayBoundary.FourAm)
        // Nov 7's 01:00 dose: its medication day is Nov 7, its records are dated Nov 8.
        val at = LocalDateTime.of(2026, 11, 8, 1, 0)
        val missing = buildMissingScheduledLogEntries(
            group = plan,
            slot = MedicationReminderSlot(plan.uuid, at, plan.schedule.timeSlots.single().uuid),
            entries = emptyList(),
            appliedAt = testInstant(at.plusMinutes(5)),
        )
        suspend fun onTheSlot() = database.medicationLogDao().getEntries()
            .filter { entry -> entry.scheduledForIso == at.toString() }
            .sumOf { entry -> entry.count }

        logRepository.saveNewEntries(missing)
        assertEquals("the first write logs the dose", 3, onTheSlot())
        logRepository.saveNewEntries(missing)

        assertEquals("the second finds it logged", 3, onTheSlot())
        assertEquals("estradiol stock", 8.0, stockOf(estradiol), 0.0)
        assertEquals("spironolactone stock", 9.0, stockOf(spironolactone), 0.0)
    }

    // A morning plan: 2 estradiol tablets and 1 spironolactone tablet at 08:00.
    private suspend fun seedPlan(
        estradiolDose: DoseInstruction = wholeTablet,
        scheduleTimeUuid: UUID? = null,
        time: LocalTime = LocalTime.of(8, 0),
        dayBoundary: MedicationDayBoundary = MedicationDayBoundary.Midnight,
    ): MedicationGroup {
        if (database.medicineDao().getByUuid(estradiol.uuid.toString()) == null) {
            database.medicineDao().insert(estradiol.toEntity())
            database.medicineDao().insert(spironolactone.toEntity())
        }
        val uuid = groupRepository.saveGroup(
            uuid = planUuid,
            name = "Morning",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 11, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(time),
                timeSlots = listOf(MedicationGroupScheduleTimeInput(uuid = scheduleTimeUuid, time = time)),
                dayBoundary = dayBoundary,
            ),
            medications = listOf(
                MedicationGroupMedicationInput(
                    medicineUuid = estradiol.uuid,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = estradiolDose,
                    count = 2,
                ),
                MedicationGroupMedicationInput(
                    medicineUuid = spironolactone.uuid,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = wholeTablet,
                    count = 1,
                ),
            ),
            now = testInstant(LocalDateTime.of(2026, 11, 1, 7, 0)),
        )
        planUuid = uuid
        return checkNotNull(groupRepository.getGroup(uuid))
    }

    private fun holdWritersAtTheGate(count: Int) {
        arrivedAtGate.value = 0
        writersToHold = count
    }

    private fun slotOf(plan: MedicationGroup) = MedicationReminderSlot(
        groupUuid = plan.uuid,
        scheduledAt = scheduledAt,
        scheduleTimeUuid = plan.schedule.timeSlots.single().uuid,
    )

    private suspend fun logEarlier(
        plan: MedicationGroup,
        medicine: Medicine,
        count: Int,
        appliedAt: LocalDateTime,
    ) {
        logRepository.saveBackfillEntries(
            listOf(
                MedicationLogEntryInput(
                    medicineUuid = medicine.uuid,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = wholeTablet,
                    sourceGroupUuid = plan.uuid,
                    scheduleTimeUuid = plan.schedule.timeSlots.single().uuid,
                    appliedAt = testInstant(appliedAt),
                    scheduledFor = scheduledAt,
                    count = count,
                )
            )
        )
    }

    // What a widget or notification writer computes from its read before the gate.
    private suspend fun missingAsReadBeforeTheGate(plan: MedicationGroup): List<MedicationLogEntryInput> {
        return buildMissingScheduledLogEntries(
            group = plan,
            slot = slotOf(plan),
            entries = logRepository.getScheduledGroupEntriesSince(scheduledAt),
            appliedAt = testInstant(scheduledAt.plusMinutes(10)),
            zoneId = ZoneId.systemDefault(),
        )
    }

    // The quick-log sheet's Save: a new record for the sheet's slot with the count it opened on.
    private suspend fun saveFromSheet(
        plan: MedicationGroup,
        medicine: Medicine,
        count: Int,
    ) {
        logRepository.saveEntry(
            uuid = null,
            medicineUuid = medicine.uuid,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = wholeTablet,
            sourceGroupUuid = plan.uuid,
            scheduleTimeUuid = plan.schedule.timeSlots.single().uuid,
            appliedAt = testInstant(scheduledAt.plusMinutes(15)),
            scheduledFor = scheduledAt,
            count = count,
        )
    }

    private suspend fun loggedCount(medicine: Medicine): Int {
        return database.medicationLogDao().getEntries()
            .filter { entry ->
                entry.medicineUuid == medicine.uuid.toString() &&
                        entry.sourceGroupUuid == planUuid.toString() &&
                        entry.scheduledForIso == scheduledAt.toString()
            }
            .sumOf { entry -> entry.count }
    }

    private suspend fun stockOf(medicine: Medicine): Double {
        return checkNotNull(
            database.medicineDao().getByUuid(medicine.uuid.toString())?.stockUnitsRemaining
        )
    }
}
