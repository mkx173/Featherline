package com.mkx.hrttracker.data.repository

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
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class ScheduledRunwayCalculatorTest {
    private val zoneId = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 5, 27)
    private val now = LocalDateTime.of(today, LocalTime.of(7, 0)).atZone(zoneId).toInstant()
    private val medicineUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val groupUuid = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Test
    fun noActiveGroupYieldsNoSchedule() {
        val medicine = pill(unitsRemaining = 10.0)

        val runway = compute(medicine = medicine, activeGroups = emptyList())

        assertEquals(RunwayProjection.NoSchedule, runway)
    }

    @Test
    fun dailyPoolWithStockBeyondHorizonYieldsBeyondHorizon() {
        val medicine = pill(unitsRemaining = 400.0)

        val runway = compute(medicine = medicine, activeGroups = listOf(dailyGroup(medicine)))

        assertEquals(RunwayProjection.BeyondHorizon, runway)
    }

    @Test
    fun dailyPoolReturnsLastFulfillableDayBeforeStockout() {
        val medicine = pill(unitsRemaining = 10.0)

        val runway = compute(medicine = medicine, activeGroups = listOf(dailyGroup(medicine)))

        assertEquals(RunwayProjection.Days(days = 9, lastFulfillable = today.plusDays(9)), runway)
    }

    @Test
    fun gnrhSingleUseInjection_wholeUnit_consumesOneUnit() {
        assertEquals(
            1.0,
            resolvePerAdministrationMagnitude(
                preparation = MedicinePreparation.InjectionSingleUseVial(strengthMgPerVial = 11.25),
                doseInstruction = DoseInstruction.WholeUnit,
            ),
        )
    }

    @Test
    fun weeklyVialUsesSparseScheduleInsteadOfAmortizedRunway() {
        val medicine = vial(
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 0.0,
                unitsLastTotal = null,
                openContainerAmount = 1.2,
                warnAtDaysRemaining = 14,
            ),
            vialVolumeMl = 1.2,
        )
        // Midday today: the 08:00 slot has passed but is unlogged, so under the
        // start-of-today cutoff it still counts as demand. 1.2ml / 0.25ml = 4
        // doses land on today, +7, +14, +21 (sparse weekly steps, not amortized).
        val middayToday = LocalDateTime.of(today, LocalTime.NOON).atZone(zoneId).toInstant()

        val runway = compute(
            medicine = medicine,
            activeGroups = listOf(
                weeklyGroup(
                    medicine = medicine,
                    doseInstruction = DoseInstruction.VolumeMl(0.25),
                    applicationType = MedicationApplicationType.INJECTION,
                    weeklyDaysOfWeek = setOf(today.dayOfWeek),
                )
            ),
            now = middayToday,
        )

        assertEquals(RunwayProjection.Days(days = 21, lastFulfillable = today.plusDays(21)), runway)
    }

    @Test
    fun unloggedDoseEarlierTodayStillCountsAfterItsScheduledTime() {
        // Start-of-today cutoff: a scheduled dose is owed for the whole day it
        // falls on, so today's 08:00 slot still counts at noon even though it is
        // unlogged and its time has already passed. Prevents the intraday jump
        // where runway lengthened the instant a scheduled time went by unlogged.
        val medicine = pill(unitsRemaining = 1.0)
        val group = dailyGroup(medicine)
        val afternoon = LocalDateTime.of(today, LocalTime.NOON).atZone(zoneId).toInstant()

        val runway = compute(medicine = medicine, activeGroups = listOf(group), now = afternoon)

        assertEquals(RunwayProjection.Days(days = 0, lastFulfillable = today), runway)
    }

    @Test
    fun scheduledLogAlreadyCoveringTodayMakesTomorrowTheFirstDeduction() {
        val medicine = pill(unitsRemaining = 1.0)
        val group = dailyGroup(medicine)
        val scheduledFor = LocalDateTime.of(today, LocalTime.of(8, 0))
        val log = logFor(group = group, medicine = medicine, scheduledFor = scheduledFor)

        val runway = compute(
            medicine = medicine,
            activeGroups = listOf(group),
            logEntries = listOf(log),
        )

        assertEquals(RunwayProjection.Days(days = 1, lastFulfillable = today.plusDays(1)), runway)
    }

    @Test
    fun scheduledLogWithSameScheduleTimeUuidAndDateCoversSlotAfterTimeChange() {
        val medicine = pill(unitsRemaining = 1.0)
        val group = dailyGroup(medicine)
        val originalScheduledFor = LocalDateTime.of(today, LocalTime.of(7, 30))
        val log = logFor(group = group, medicine = medicine, scheduledFor = originalScheduledFor)

        val runway = compute(
            medicine = medicine,
            activeGroups = listOf(group),
            logEntries = listOf(log),
        )

        assertEquals(RunwayProjection.Days(days = 1, lastFulfillable = today.plusDays(1)), runway)
    }

    @Test
    fun scheduledLogWithoutScheduleTimeUuidFallsBackToExactScheduledFor() {
        val medicine = pill(unitsRemaining = 1.0)
        val group = dailyGroup(medicine)
        val scheduledFor = LocalDateTime.of(today, LocalTime.of(8, 0))
        val log = logFor(group = group, medicine = medicine, scheduledFor = scheduledFor)
            .copy(scheduleTimeUuid = null)

        val runway = compute(
            medicine = medicine,
            activeGroups = listOf(group),
            logEntries = listOf(log),
        )

        assertEquals(RunwayProjection.Days(days = 1, lastFulfillable = today.plusDays(1)), runway)
    }

    @Test
    fun fulfilledSlotExcludesThatOccurrenceFromEnumeration() {
        val medicine = pill(unitsRemaining = 30.0)
        val group = dailyGroup(medicine)
        val scheduledFor = LocalDateTime.of(today, LocalTime.of(8, 0))
        val slotUuid = group.schedule.timeSlots.single().uuid

        val withoutSuppression = enumerate(medicine = medicine, activeGroups = listOf(group))
        assertEquals(scheduledFor, withoutSuppression.first().scheduledFor)

        val withSuppression = ScheduledRunwayCalculator.enumerateUnfulfilledOccurrencesForMedicine(
            medicine = medicine,
            activeGroups = listOf(group),
            logEntries = emptyList(),
            nowLocal = LocalDateTime.of(today, LocalTime.of(7, 0)),
            horizonEnd = today.plusDays(ScheduledRunwayCalculator.HORIZON_DAYS),
            zoneId = zoneId,
            fulfilledSlot = FulfilledScheduledSlot(
                groupUuid = group.uuid,
                scheduleTimeUuid = slotUuid,
                scheduledFor = scheduledFor,
            ),
        )
        // The pre-logged slot is gone, so the first remaining demand is tomorrow.
        assertEquals(scheduledFor.plusDays(1), withSuppression.first().scheduledFor)
    }

    @Test
    fun fulfilledSlotPreventsDoubleCountingPreLoggedFutureDoseInRunway() {
        // Preview scenario: the dose being logged is already deducted from stock
        // (2 doses left). Without suppression the to-be-saved future slot is still
        // counted, costing a day of runway; suppressing it matches post-save reality.
        val medicine = pill(unitsRemaining = 2.0)
        val group = dailyGroup(medicine)
        val scheduledFor = LocalDateTime.of(today, LocalTime.of(8, 0))
        val slotUuid = group.schedule.timeSlots.single().uuid

        val doubleCounted = compute(medicine = medicine, activeGroups = listOf(group))
        assertEquals(
            RunwayProjection.Days(days = 1, lastFulfillable = today.plusDays(1)),
            doubleCounted
        )

        val suppressed = ScheduledRunwayCalculator.computeScheduledRunway(
            medicine = medicine,
            activeGroups = listOf(group),
            logEntries = emptyList(),
            now = now,
            zoneId = zoneId,
            fulfilledSlot = FulfilledScheduledSlot(
                groupUuid = group.uuid,
                scheduleTimeUuid = slotUuid,
                scheduledFor = scheduledFor,
            ),
        )
        assertEquals(
            RunwayProjection.Days(days = 2, lastFulfillable = today.plusDays(2)),
            suppressed
        )
    }

    @Test
    fun partialCountLogLeavesOnlyTheRemainingCountForThatSignature() {
        val medicine = pill(unitsRemaining = 2.0)
        val group =
            dailyGroup(medicine = medicine, medications = listOf(medication(medicine, count = 3)))
        val scheduledFor = LocalDateTime.of(today, LocalTime.of(8, 0))
        val log = logFor(group = group, medicine = medicine, scheduledFor = scheduledFor, count = 1)

        val occurrences =
            enumerate(medicine = medicine, activeGroups = listOf(group), logEntries = listOf(log))

        assertEquals(2.0, occurrences.first().perDose, 1e-9)
    }

    @Test
    fun sameSignatureDoubleSlotSubtractsLoggedCountsOnceAcrossTheAggregate() {
        val medicine = pill(unitsRemaining = 2.0)
        val group = dailyGroup(
            medicine = medicine,
            medications = listOf(
                medication(
                    medicine = medicine,
                    uuid = UUID.fromString("33333333-3333-3333-3333-333333333331"),
                    count = 2,
                ),
                medication(
                    medicine = medicine,
                    uuid = UUID.fromString("33333333-3333-3333-3333-333333333332"),
                    count = 1,
                ),
            ),
        )
        val scheduledFor = LocalDateTime.of(today, LocalTime.of(8, 0))
        val log = logFor(group = group, medicine = medicine, scheduledFor = scheduledFor, count = 1)

        val occurrences =
            enumerate(medicine = medicine, activeGroups = listOf(group), logEntries = listOf(log))

        assertEquals(2.0, occurrences.first().perDose, 1e-9)
    }

    @Test
    fun everyDayPoolWithOneDoseLeftYieldsToday() {
        val medicine = pill(unitsRemaining = 1.0)

        val runway = compute(medicine = medicine, activeGroups = listOf(dailyGroup(medicine)))

        assertEquals(RunwayProjection.Days(days = 0, lastFulfillable = today), runway)
    }

    @Test
    fun containerTopologyWorkedExampleFulfillsTwoDosesViaDregCarryOver() {
        val medicine = vial(
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 1.0,
                unitsLastTotal = null,
                openContainerAmount = 0.5,
                warnAtDaysRemaining = 14,
            )
        )

        val runway = compute(
            medicine = medicine,
            activeGroups = listOf(
                dailyGroup(
                    medicine = medicine,
                    applicationType = MedicationApplicationType.INJECTION,
                    doseInstruction = DoseInstruction.VolumeMl(0.7),
                )
            ),
        )

        // Exact-split conserves the 1.5 mL on hand (0.5 open + one 1.0 mL vial).
        // Dose 1 drains the 0.5 dreg and 0.2 from a cracked vial (-> 0.8 left);
        // dose 2 is fulfilled from that carried 0.8. Crack-and-discard would have
        // wasted the 0.5 dreg and stopped after a single dose today.
        assertEquals(RunwayProjection.Days(days = 1, lastFulfillable = today.plusDays(1)), runway)
    }

    @Test
    fun sameTimestampOccurrencesSortByGroupCreatedAtThenUuidRegardlessOfInputOrder() {
        val medicine = vial(
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 1.0,
                unitsLastTotal = null,
                openContainerAmount = 0.5,
                warnAtDaysRemaining = 14,
            )
        )
        val earlyGroup = dailyGroup(
            medicine = medicine,
            uuid = UUID.fromString("22222222-2222-2222-2222-222222222221"),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            applicationType = MedicationApplicationType.INJECTION,
            doseInstruction = DoseInstruction.VolumeMl(0.4),
        )
        val lateGroup = dailyGroup(
            medicine = medicine,
            uuid = UUID.fromString("22222222-2222-2222-2222-222222222220"),
            createdAt = Instant.parse("2026-01-02T00:00:00Z"),
            applicationType = MedicationApplicationType.INJECTION,
            doseInstruction = DoseInstruction.VolumeMl(0.7),
        )

        val normalOrder =
            enumerate(medicine = medicine, activeGroups = listOf(earlyGroup, lateGroup))
                .take(2)
                .map { occurrence -> occurrence.perDose }
        val reversedInputOrder =
            enumerate(medicine = medicine, activeGroups = listOf(lateGroup, earlyGroup))
                .take(2)
                .map { occurrence -> occurrence.perDose }

        assertEquals(listOf(0.4, 0.7), normalOrder)
        assertEquals(normalOrder, reversedInputOrder)
    }

    private fun compute(
        medicine: Medicine,
        activeGroups: List<MedicationGroup>,
        logEntries: List<com.mkx.hrttracker.model.medication.MedicationLogEntry> = emptyList(),
        now: Instant = this.now,
    ): RunwayProjection {
        return ScheduledRunwayCalculator.computeScheduledRunway(
            medicine = medicine,
            activeGroups = activeGroups,
            logEntries = logEntries,
            now = now,
            zoneId = zoneId,
        )
    }

    private fun enumerate(
        medicine: Medicine,
        activeGroups: List<MedicationGroup>,
        logEntries: List<com.mkx.hrttracker.model.medication.MedicationLogEntry> = emptyList(),
    ): List<MedicineDoseOccurrence> {
        return ScheduledRunwayCalculator.enumerateUnfulfilledOccurrencesForMedicine(
            medicine = medicine,
            activeGroups = activeGroups,
            logEntries = logEntries,
            nowLocal = LocalDateTime.of(today, LocalTime.of(7, 0)),
            horizonEnd = today.plusDays(ScheduledRunwayCalculator.HORIZON_DAYS),
            zoneId = zoneId,
        )
    }

    private fun logFor(
        group: MedicationGroup,
        medicine: Medicine,
        scheduledFor: LocalDateTime,
        count: Int = 1,
    ): com.mkx.hrttracker.model.medication.MedicationLogEntry {
        return testMedicationLogEntry(
            medicine = medicine,
            applicationType = group.medications.first().applicationType,
            doseInstruction = group.medications.first().doseInstruction,
            sourceGroupUuid = group.uuid,
            appliedAt = scheduledFor.atZone(zoneId).toInstant(),
            appliedAtTimeZoneId = zoneId.id,
            scheduledFor = scheduledFor,
            count = count,
            scheduleTimeUuid = group.schedule.timeSlots.single().uuid,
        )
    }

    private fun pill(unitsRemaining: Double): Medicine {
        val preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0)
        return medicine(
            preparation = preparation,
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = unitsRemaining,
                unitsLastTotal = unitsRemaining,
                warnAtDaysRemaining = 14,
            ),
        )
    }

    private fun vial(
        stock: MedicineStock,
        vialVolumeMl: Double = 1.0,
    ): Medicine {
        val preparation = MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = 20.0,
            vialVolumeMl = vialVolumeMl,
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
            identityKey = MedicineIdentityKey.catalog(
                MedicationKey.ESTRADIOL_VALERATE,
                preparation
            ),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            archivedAt = null,
            displayDoseUnit = MedicineDisplayDoseUnit.MG,
            stock = stock,
        )
    }

    private fun dailyGroup(
        medicine: Medicine,
        uuid: UUID = groupUuid,
        createdAt: Instant = Instant.EPOCH,
        applicationType: MedicationApplicationType = MedicationApplicationType.ORAL,
        doseInstruction: DoseInstruction = DoseInstruction.TabletFraction(1, 1),
        count: Int = 1,
        medications: List<MedicationGroupMedication> = listOf(
            medication(
                medicine = medicine,
                applicationType = applicationType,
                doseInstruction = doseInstruction,
                count = count,
            )
        ),
    ): MedicationGroup {
        return group(
            medicine = medicine,
            uuid = uuid,
            createdAt = createdAt,
            scheduleType = MedicationGroupScheduleType.DAILY,
            weeklyDaysOfWeek = emptySet(),
            medications = medications,
        )
    }

    private fun weeklyGroup(
        medicine: Medicine,
        applicationType: MedicationApplicationType,
        doseInstruction: DoseInstruction,
        weeklyDaysOfWeek: Set<DayOfWeek>,
    ): MedicationGroup {
        return group(
            medicine = medicine,
            scheduleType = MedicationGroupScheduleType.WEEKLY,
            weeklyDaysOfWeek = weeklyDaysOfWeek,
            medications = listOf(
                medication(
                    medicine = medicine,
                    applicationType = applicationType,
                    doseInstruction = doseInstruction,
                )
            ),
        )
    }

    private fun group(
        medicine: Medicine,
        uuid: UUID = groupUuid,
        createdAt: Instant = Instant.EPOCH,
        scheduleType: MedicationGroupScheduleType,
        weeklyDaysOfWeek: Set<DayOfWeek>,
        medications: List<MedicationGroupMedication>,
    ): MedicationGroup {
        return MedicationGroup(
            uuid = uuid,
            name = "Morning",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupSchedule(
                type = scheduleType,
                interval = 1,
                since = today.minusDays(7),
                weeklyDaysOfWeek = weeklyDaysOfWeek,
                times = listOf(LocalTime.of(8, 0)),
            ),
            medications = medications,
            notificationsEnabled = true,
            createdAt = createdAt,
            updatedAt = createdAt,
            archivedAt = null,
            includePastScheduledSlots = false,
        )
    }

    private fun medication(
        medicine: Medicine,
        uuid: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333"),
        applicationType: MedicationApplicationType = MedicationApplicationType.ORAL,
        doseInstruction: DoseInstruction = DoseInstruction.TabletFraction(1, 1),
        count: Int = 1,
    ): MedicationGroupMedication {
        return MedicationGroupMedication(
            uuid = uuid,
            medicine = medicine,
            applicationType = applicationType,
            doseInstruction = doseInstruction,
            count = count,
        )
    }
}
