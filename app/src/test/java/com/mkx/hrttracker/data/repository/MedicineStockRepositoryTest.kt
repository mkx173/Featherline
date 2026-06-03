package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MedicineStockRepositoryTest {

    private lateinit var medicineRepository: MedicineRepository
    private lateinit var medicationGroupRepository: MedicationGroupRepository
    private lateinit var medicationLogRepository: MedicationLogRepository
    private lateinit var homeSnapshotRepository: HomeSnapshotRepository
    private lateinit var repository: MedicineStockRepository
    private lateinit var appScope: CoroutineScope
    private lateinit var originalTimeZone: TimeZone

    private val medicineUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val groupUuid = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val clock = Clock.fixed(Instant.parse("2026-01-01T07:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        medicineRepository = mockk(relaxed = true)
        medicationGroupRepository = mockk(relaxed = true)
        medicationLogRepository = mockk(relaxed = true)
        homeSnapshotRepository = mockk(relaxed = true)
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        every { medicineRepository.observeAllActive() } returns MutableStateFlow(emptyList())
        every { medicineRepository.observeAllActiveOrNull() } returns MutableStateFlow(emptyList())
        every { medicationGroupRepository.observeGroups() } returns MutableStateFlow(emptyList())
        every { medicationLogRepository.observeEntries() } returns MutableStateFlow(emptyList())
        every { homeSnapshotRepository.observeHomeSnapshot() } returns MutableStateFlow(null)
        repository = MedicineStockRepository(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            appScope = appScope,
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        appScope.cancel()
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun untrackedMedicineWithStock_returnsUntrackedState() {
        val medicine = pill(
            MedicineStock(
                trackingEnabled = false,
                unitsRemaining = 30.0,
                unitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                generation = 0L,
            )
        )

        val projection = repository.projectAll(
            medicines = listOf(medicine),
            activeGroups = listOf(dailyGroup(medicine)),
        ).single()

        assertEquals(MedicineStockState.UNTRACKED, projection.state)
        assertEquals(0.0, projection.totalStockUnits, 0.0)
        assertEquals(RunwayProjection.NoSchedule, projection.runway)
    }

    @Test
    fun trackedNoGroups_returnsNoRunway() {
        val medicine = pill(
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 30.0,
                unitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                generation = 1L,
            )
        )

        val projection = repository.projectAll(listOf(medicine), emptyList()).single()

        assertEquals(MedicineStockState.NO_RUNWAY, projection.state)
        assertEquals(RunwayProjection.NoSchedule, projection.runway)
    }

    @Test
    fun trackedHealthy_1PerDay_30Tabs_yields30Days() {
        val medicine = pill(
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 30.0,
                unitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                generation = 1L,
            )
        )

        val projection = repository.projectAll(
            medicines = listOf(medicine),
            activeGroups = listOf(dailyGroup(medicine)),
        ).single()

        assertEquals(1.0, projection.dosesPerDayMagnitude, 1e-9)
        assertEquals(RunwayProjection.Days(29, LocalDate.of(2026, 1, 30)), projection.runway)
        assertEquals(MedicineStockState.HEALTHY, projection.state)
    }

    @Test
    fun trackedLow_runwayAtThreshold() {
        val medicine = pill(
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 14.0,
                unitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                generation = 1L,
            )
        )

        val projection = repository.projectAll(
            medicines = listOf(medicine),
            activeGroups = listOf(dailyGroup(medicine)),
        ).single()

        assertEquals(MedicineStockState.USER_LOW, projection.state)
    }

    @Test
    fun trackedOut_zeroStock() {
        val medicine = pill(
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 0.0,
                unitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                generation = 1L,
            )
        )

        val projection = repository.projectAll(
            medicines = listOf(medicine),
            activeGroups = listOf(dailyGroup(medicine)),
        ).single()

        assertEquals(MedicineStockState.OUT, projection.state)
    }

    @Test
    fun containerTotalIncludesOpenAmountAndSealedContainerCapacity() {
        val medicine = vial(
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 2.0,
                unitsLastTotal = null,
                openContainerAmount = 0.5,
                warnAtDaysRemaining = 14,
                generation = 1L,
            )
        )

        val projection = repository.projectAll(
            medicines = listOf(medicine),
            activeGroups = listOf(
                dailyGroup(
                    medicine = medicine,
                    applicationType = MedicationApplicationType.INJECTION,
                    doseInstruction = DoseInstruction.VolumeMl(0.5),
                )
            ),
        ).single()

        assertEquals(2.5, projection.totalStockUnits, 1e-9)
        assertEquals(0.5, projection.dosesPerDayMagnitude, 1e-9)
        assertEquals(RunwayProjection.Days(4, LocalDate.of(2026, 1, 5)), projection.runway)
    }

    @Test
    fun projectAllSkipsDoseAlreadyLoggedToday() {
        val medicine = pill(
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 2.0,
                unitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 0,
                generation = 1L,
            )
        )
        val group = dailyGroup(medicine)
        val log = logForToday(group = group, medicine = medicine)

        val projection = repository.projectAll(
            medicines = listOf(medicine),
            activeGroups = listOf(group),
            logEntries = listOf(log),
            now = clock.instant(),
        ).single()

        assertEquals(RunwayProjection.Days(2, LocalDate.of(2026, 1, 3)), projection.runway)
        assertEquals(MedicineStockState.HEALTHY, projection.state)
    }

    @Test
    fun projectAllReturnsImminentForContainerThatOnlyFitsOneDoseByTopology() {
        val medicine = vial(
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 1.0,
                unitsLastTotal = null,
                openContainerAmount = 0.5,
                warnAtDaysRemaining = 14,
                generation = 1L,
            )
        )

        val projection = repository.projectAll(
            medicines = listOf(medicine),
            activeGroups = listOf(
                dailyGroup(
                    medicine = medicine,
                    applicationType = MedicationApplicationType.INJECTION,
                    doseInstruction = DoseInstruction.VolumeMl(0.7),
                )
            ),
            now = clock.instant(),
        ).single()

        assertEquals(RunwayProjection.Days(0, LocalDate.of(2026, 1, 1)), projection.runway)
        assertEquals(0.7, projection.maxPerAdministration, 1e-9)
        assertEquals(MedicineStockState.IMMINENT, projection.state)
    }

    @Test
    fun projectAllReturnsLargestCalendarGapAsIntervalDays() {
        val medicine = pill(
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 30.0,
                unitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                generation = 1L,
            )
        )

        val projection = repository.projectAll(
            medicines = listOf(medicine),
            activeGroups = listOf(weeklyGroup(medicine, weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))),
            now = clock.instant(),
        ).single()

        assertEquals(4, projection.intervalDays)
    }

    @Test
    fun projectAllUsesProvidedZoneForScheduledOccurrences() {
        val providedZone = ZoneId.of("Asia/Tokyo")
        val medicine = pill(
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 1.0,
                unitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 0,
                generation = 1L,
            )
        )

        val projection = repository.projectAll(
            medicines = listOf(medicine),
            activeGroups = listOf(dailyGroup(medicine)),
            now = Instant.parse("2025-12-31T23:30:00Z"),
            zoneId = providedZone,
        ).single()

        assertEquals(RunwayProjection.Days(1, LocalDate.of(2026, 1, 2)), projection.runway)
    }

    @Test
    fun previewRunwayUsesCachedScheduleAwareInputsForHypotheticalStock() = runTest {
        val medicine = patch(
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 1.0,
                unitsLastTotal = 1.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                generation = 1L,
            )
        )
        val group = weeklyGroup(
            medicine = medicine,
            weeklyDaysOfWeek = setOf(DayOfWeek.THURSDAY),
            applicationType = MedicationApplicationType.PATCH_ON,
            doseInstruction = DoseInstruction.WholeUnit,
        )
        val medicines = MutableStateFlow(listOf(medicine))
        every { medicineRepository.observeAllActive() } returns medicines
        every { medicineRepository.observeAllActiveOrNull() } returns medicines
        every { medicationGroupRepository.observeGroups() } returns
            MutableStateFlow<List<MedicationGroup>?>(listOf(group))
        every { medicationLogRepository.observeEntries() } returns MutableStateFlow(emptyList())
        every { homeSnapshotRepository.observeHomeSnapshot() } returns MutableStateFlow(null)
        val projectionScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = MedicineStockRepository(
                medicineRepository = medicineRepository,
                medicationGroupRepository = medicationGroupRepository,
                medicationLogRepository = medicationLogRepository,
                homeSnapshotRepository = homeSnapshotRepository,
                appScope = projectionScope,
                clock = clock,
            )
            advanceUntilIdle()

            val preview = repository.previewRunway(
                medicineUuid = medicine.uuid,
                hypotheticalStock = medicine.stock.copy(unitsRemaining = 4.0),
            )

            assertEquals(
                RunwayProjection.Days(days = 21, lastFulfillable = LocalDate.of(2026, 1, 22)),
                preview,
            )
        } finally {
            projectionScope.cancel()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeProjectionsCombinesMedicinesGroupsAndLogEntries() = runTest {
        val medicine = pill(
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 2.0,
                unitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 0,
                generation = 1L,
            )
        )
        val group = dailyGroup(medicine)
        val medicines = MutableStateFlow(listOf(medicine))
        val groups = MutableStateFlow<List<MedicationGroup>?>(listOf(group))
        val logs = MutableStateFlow<List<com.mkx.hrttracker.model.medication.MedicationLogEntry>?>(emptyList())
        every { medicineRepository.observeAllActive() } returns medicines
        every { medicineRepository.observeAllActiveOrNull() } returns medicines
        every { medicationGroupRepository.observeGroups() } returns groups
        every { medicationLogRepository.observeEntries() } returns logs
        every { homeSnapshotRepository.observeHomeSnapshot() } returns MutableStateFlow(null)
        val projectionScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = MedicineStockRepository(
                medicineRepository = medicineRepository,
                medicationGroupRepository = medicationGroupRepository,
                medicationLogRepository = medicationLogRepository,
                homeSnapshotRepository = homeSnapshotRepository,
                appScope = projectionScope,
                clock = clock,
            )
            advanceUntilIdle()

            val initial = repository.observeProjections().first()
            assertEquals(RunwayProjection.Days(1, LocalDate.of(2026, 1, 2)), initial.single().runway)

            logs.value = listOf(logForToday(group = group, medicine = medicine))
            advanceUntilIdle()

            val updated = repository.observeProjections().first()
            assertEquals(RunwayProjection.Days(2, LocalDate.of(2026, 1, 3)), updated.single().runway)
        } finally {
            projectionScope.cancel()
        }
    }

    @Test
    fun observeProjections_sharesSingleUpstreamSubscriptionAcrossCollectors() = runTest {
        val medicine = pill(
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 2.0,
                unitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 0,
                generation = 1L,
            )
        )
        every { medicineRepository.observeAllActive() } returns MutableStateFlow(listOf(medicine))
        every { medicineRepository.observeAllActiveOrNull() } returns MutableStateFlow(listOf(medicine))
        every { medicationGroupRepository.observeGroups() } returns MutableStateFlow<List<MedicationGroup>?>(emptyList())
        every { medicationLogRepository.observeEntries() } returns MutableStateFlow(emptyList())
        every { homeSnapshotRepository.observeHomeSnapshot() } returns MutableStateFlow(null)
        clearMocks(
            medicineRepository,
            medicationGroupRepository,
            medicationLogRepository,
            homeSnapshotRepository,
            answers = false,
            recordedCalls = true,
        )
        val repository = MedicineStockRepository(
            medicineRepository = medicineRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            appScope = backgroundScope,
            clock = clock,
        )

        val first = async { repository.observeProjections().first() }
        val second = async { repository.observeProjections().first() }
        advanceUntilIdle()

        assertEquals(medicine.uuid, first.await().single().medicine.uuid)
        assertEquals(medicine.uuid, second.await().single().medicine.uuid)
        verify(exactly = 1) { medicineRepository.observeAllActiveOrNull() }
        verify(exactly = 1) { medicationGroupRepository.observeGroups() }
        verify(exactly = 1) { medicationLogRepository.observeEntries() }
        verify(exactly = 1) { homeSnapshotRepository.observeHomeSnapshot() }
    }

    @Test
    fun projectAllOnceUsesOneShotActiveInputsAndLogEntries() = runTest {
        val medicine = pill(
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 2.0,
                unitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 0,
                generation = 1L,
            )
        )
        val group = dailyGroup(medicine)
        val log = logForToday(group = group, medicine = medicine)
        coEvery { medicineRepository.getAllActive() } returns listOf(medicine)
        coEvery { medicationGroupRepository.getActiveGroups() } returns listOf(group)
        coEvery { medicationLogRepository.getEntries() } returns listOf(log)

        val oneShotProjection = repository.projectAllOnce(now = clock.instant())
        val directProjection = repository.projectAll(
            medicines = listOf(medicine),
            activeGroups = listOf(group),
            logEntries = listOf(log),
            now = clock.instant(),
        )

        assertEquals(directProjection, oneShotProjection)
        coVerify { medicineRepository.getAllActive() }
        coVerify { medicationGroupRepository.getActiveGroups() }
        coVerify { medicationLogRepository.getEntries() }
    }

    private fun pill(stock: MedicineStock): Medicine {
        val preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0)
        return medicine(preparation = preparation, stock = stock)
    }

    private fun vial(stock: MedicineStock): Medicine {
        val preparation = MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = 20.0,
            vialVolumeMl = 1.0,
        )
        return medicine(preparation = preparation, stock = stock)
    }

    private fun patch(stock: MedicineStock): Medicine {
        val preparation = MedicinePreparation.Patch(
            MedicinePreparation.PatchSpecification.TotalMg(valueMg = 1.0),
        )
        return medicine(preparation = preparation, stock = stock)
    }

    private fun medicine(
        preparation: MedicinePreparation,
        stock: MedicineStock,
    ): Medicine {
        return Medicine(
            uuid = medicineUuid,
            selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL_VALERATE),
            category = MedicationCategory.ESTRADIOL,
            preparation = preparation,
            displayName = null,
            identityKey = MedicineIdentityKey.catalog(MedicationKey.ESTRADIOL_VALERATE, preparation),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            archivedAt = null,
            displayDoseUnit = MedicineDisplayDoseUnit.MG,
            stock = stock,
        )
    }

    private fun dailyGroup(
        medicine: Medicine,
        applicationType: MedicationApplicationType = MedicationApplicationType.ORAL,
        doseInstruction: DoseInstruction = DoseInstruction.TabletFraction(1, 1),
        count: Int = 1,
    ): MedicationGroup {
        return MedicationGroup(
            uuid = groupUuid,
            name = "Morning",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 1, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0)),
            ),
            medications = listOf(
                MedicationGroupMedication(
                    uuid = UUID.fromString("33333333-3333-3333-3333-333333333333"),
                    medicine = medicine,
                    applicationType = applicationType,
                    doseInstruction = doseInstruction,
                    count = count,
                )
            ),
            notificationsEnabled = true,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            archivedAt = null,
            includePastScheduledSlots = false,
        )
    }

    private fun weeklyGroup(
        medicine: Medicine,
        weeklyDaysOfWeek: Set<DayOfWeek>,
        applicationType: MedicationApplicationType = MedicationApplicationType.ORAL,
        doseInstruction: DoseInstruction = DoseInstruction.TabletFraction(1, 1),
    ): MedicationGroup {
        return MedicationGroup(
            uuid = groupUuid,
            name = "Weekly",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 1,
                since = LocalDate.of(2026, 1, 1),
                weeklyDaysOfWeek = weeklyDaysOfWeek,
                times = listOf(LocalTime.of(8, 0)),
            ),
            medications = listOf(
                MedicationGroupMedication(
                    uuid = UUID.fromString("33333333-3333-3333-3333-333333333333"),
                    medicine = medicine,
                    applicationType = applicationType,
                    doseInstruction = doseInstruction,
                    count = 1,
                )
            ),
            notificationsEnabled = true,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            archivedAt = null,
            includePastScheduledSlots = false,
        )
    }

    private fun logForToday(
        group: MedicationGroup,
        medicine: Medicine,
    ): com.mkx.hrttracker.model.medication.MedicationLogEntry {
        val scheduledFor = LocalDateTime.of(LocalDate.of(2026, 1, 1), LocalTime.of(8, 0))
        val slot = group.medications.single()
        return testMedicationLogEntry(
            medicine = medicine,
            applicationType = slot.applicationType,
            doseInstruction = slot.doseInstruction,
            sourceGroupUuid = group.uuid,
            appliedAt = scheduledFor.atZone(clock.zone).toInstant(),
            appliedAtTimeZoneId = clock.zone.id,
            scheduledFor = scheduledFor,
            count = 1,
            scheduleTimeUuid = group.schedule.timeSlots.single().uuid,
        )
    }
}
