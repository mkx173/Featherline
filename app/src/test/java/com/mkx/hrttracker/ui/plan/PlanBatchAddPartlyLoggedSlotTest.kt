package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationGroupSlotKey
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.missingSlotCounts
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Batch Add on a partly logged dose time adds only what it misses, as [missingSlotCounts] (and so
 * the write clamp, reminders and runway) counts it; a fully logged time is still skipped.
 */
class PlanBatchAddPartlyLoggedSlotTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private val day = LocalDate.of(2026, 11, 8)
    private val slotAt = LocalDateTime.of(day, LocalTime.of(8, 0))
    private val now = LocalDateTime.of(day, LocalTime.of(12, 0))
    private val estradiol = testMedicine(uuid = UUID.randomUUID(), key = MedicationKey.ESTRADIOL)
    private val spiro = testMedicine(
        uuid = UUID.randomUUID(),
        key = MedicationKey.SPIRONOLACTONE,
        preparation = MedicinePreparation.Pill(strengthMgPerTablet = 50.0),
    )

    @Test
    fun oneOfTwoMedicinesLogged_theOtherIsOffered() {
        val plan = plan(
            testMedicationGroupMedication(medicine = estradiol),
            testMedicationGroupMedication(medicine = spiro),
        )
        val logged = listOf(record(plan, estradiol, count = 1))

        val batch = buildPlanBatchAddEntryPlan(plan, logged, day, day, now, zone)

        assertEquals(1, missing(plan, logged))
        assertEquals(spiro.uuid, batch.entries.single().medicineUuid)
        assertEquals("the logged estradiol is skipped", 1, batch.skippedEntryCount)
    }

    @Test
    fun oneOfTwoTabletsLogged_theOtherIsOffered() {
        val plan = plan(testMedicationGroupMedication(medicine = estradiol, count = 2))
        val logged = listOf(record(plan, estradiol, count = 1))

        val batch = buildPlanBatchAddEntryPlan(plan, logged, day, day, now, zone)

        assertEquals(1, missing(plan, logged))
        assertEquals(1, batch.entries.single().count)
        assertEquals(0, batch.skippedEntryCount)
    }

    @Test
    fun aFullyLoggedTime_isSkipped() {
        val plan = plan(
            testMedicationGroupMedication(medicine = estradiol),
            testMedicationGroupMedication(medicine = spiro),
        )
        val logged = listOf(record(plan, estradiol, count = 1), record(plan, spiro, count = 1))

        val batch = buildPlanBatchAddEntryPlan(plan, logged, day, day, now, zone)

        assertEquals(emptyList<Any>(), batch.entries)
        assertEquals(2, batch.skippedEntryCount)
    }

    @Test
    fun twoItemsOfOneSignature_nothingLogged_skipsNothing() {
        // The "Remove patch" corner: two items that log as one signature.
        val plan = plan(
            testMedicationGroupMedication(medicine = estradiol),
            testMedicationGroupMedication(medicine = estradiol),
        )

        val batch = buildPlanBatchAddEntryPlan(plan, emptyList(), day, day, now, zone)

        assertEquals(listOf(2), batch.entries.map { it.count })
        assertEquals(0, batch.skippedEntryCount)
    }

    @Test
    fun eachTimeReadsOnlyItsOwnDatesRecords() {
        // A month of twice-daily doses: the records are read once, not once per dose time.
        val plan = MedicationGroup(
            uuid = UUID.randomUUID(),
            name = "Twice",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = day.minusDays(30),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            ),
            medications = listOf(testMedicationGroupMedication(medicine = estradiol)),
        )
        val logged = CountingList(
            listOf(
                testMedicationLogEntry(
                    medicine = estradiol,
                    sourceGroupUuid = plan.uuid,
                    scheduleTimeUuid = plan.schedule.timeSlots.first().uuid,
                    appliedAt = slotAt.atZone(zone).toInstant(),
                    appliedAtTimeZoneId = zone.id,
                    scheduledFor = slotAt,
                ),
            ),
        )

        val batch = buildPlanBatchAddEntryPlan(plan, logged, day.minusDays(30), day, now, zone)

        assertEquals(60, batch.entries.size)
        assertEquals(1, logged.iterations)
    }

    private class CountingList<T>(private val list: List<T>) : List<T> by list {
        var iterations = 0
        override fun iterator(): Iterator<T> = list.iterator().also { iterations++ }
    }

    private fun missing(plan: MedicationGroup, logged: List<com.mkx.hrttracker.model.medication.MedicationLogEntry>) =
        missingSlotCounts(
            group = plan,
            slot = MedicationGroupSlotKey(plan.schedule.timeSlots.single().uuid, slotAt),
            entries = logged,
        ).values.sum()

    private fun record(plan: MedicationGroup, medicine: com.mkx.hrttracker.model.medication.Medicine, count: Int) =
        testMedicationLogEntry(
            medicine = medicine,
            sourceGroupUuid = plan.uuid,
            scheduleTimeUuid = plan.schedule.timeSlots.single().uuid,
            appliedAt = slotAt.atZone(zone).toInstant(),
            appliedAtTimeZoneId = zone.id,
            scheduledFor = slotAt,
            count = count,
        )

    private fun plan(vararg medications: com.mkx.hrttracker.model.medication.MedicationGroupMedication) = MedicationGroup(
        uuid = UUID.randomUUID(),
        name = "Morning",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = day.minusDays(7),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(8, 0)),
        ),
        medications = medications.toList(),
    )
}
