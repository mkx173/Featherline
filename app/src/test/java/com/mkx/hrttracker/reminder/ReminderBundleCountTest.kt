package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationGroupSlotKey
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.missingSlotCounts
import com.mkx.hrttracker.model.medication.testInstant
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
 * A reminder shows what its dose still misses, one row per medication with the count left, as
 * its Log now writes (second review 2026-11-18, finding 2). On the real bundle builder and the
 * real Log now entry builder.
 */
class ReminderBundleCountTest {
    private val groupUuid = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    private val estradiol = testMedicine(
        uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
        key = MedicationKey.ESTRADIOL,
    )
    private val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)

    /**
     * The review's case, from a hand-edited backup: two one-unit rows of one medication, route
     * and dose; one unit logged.
     */
    @Test
    fun twoRowsOfOneSignature_oneLogged_showOneRowOfOne_asLogNowWrites() {
        val group = planWith(tablet(count = 1), tablet(count = 1))
        val entries = listOf(tabletLog(count = 1))

        val bundle = buildMedicationReminderBundle(scheduledAt, group, entries)!!
        val shownUnits = bundle.items.single().medications.sumOf(MedicationGroupMedication::count)

        assertEquals("one unit is missing", listOf(1), missingUnits(group, bundle, entries))
        assertEquals("one row is shown", 1, bundle.items.single().medications.size)
        assertEquals("naming one unit", 1, shownUnits)
        assertEquals("Log now writes the one that is missing", listOf(1), logNowCounts(group, bundle, entries))
    }

    /**
     * A plan the editor makes: one row of two tablets, and one tablet recorded for the slot (the
     * log editor has a count field). The reminder says one, as Log now writes.
     */
    @Test
    fun oneRowOfTwoUnits_oneLogged_showsOne_asLogNowWrites() {
        val group = planWith(tablet(count = 2))
        val entries = listOf(tabletLog(count = 1))

        val bundle = buildMedicationReminderBundle(scheduledAt, group, entries)!!

        assertEquals("one unit is missing", listOf(1), missingUnits(group, bundle, entries))
        assertEquals("the row shown says one", listOf(1), bundle.items.single().medications.map { it.count })
        assertEquals("Log now writes the one that is missing", listOf(1), logNowCounts(group, bundle, entries))
    }

    private fun missingUnits(group: MedicationGroup, bundle: MedicationReminderBundle, entries: List<MedicationLogEntry>) =
        missingSlotCounts(
            group = group,
            slot = bundle.slots.single().let { slot -> MedicationGroupSlotKey(slot.scheduleTimeUuid, slot.scheduledAt) },
            entries = entries,
        ).values.toList()

    /** What the reminder's Log now records, with the signatures its PendingIntent carries. */
    private fun logNowCounts(group: MedicationGroup, bundle: MedicationReminderBundle, entries: List<MedicationLogEntry>) =
        buildMissingScheduledLogEntries(
            group = group,
            slot = bundle.slots.single(),
            entries = entries,
            appliedAt = scheduledAt.plusMinutes(20).atZone(ZoneId.systemDefault()).toInstant(),
            restrictToSignatures = bundle.logTargets.mapTo(mutableSetOf(), MedicationReminderLogTarget::signature),
        ).map { input -> input.count }

    private fun tablet(count: Int) = testMedicationGroupMedication(
        medicine = estradiol,
        applicationType = MedicationApplicationType.ORAL,
        doseInstruction = DoseInstruction.TabletFraction(1, 1),
        count = count,
    )

    private fun tabletLog(count: Int) = testMedicationLogEntry(
        medicine = estradiol,
        applicationType = MedicationApplicationType.ORAL,
        doseInstruction = DoseInstruction.TabletFraction(1, 1),
        equivalentE2Mg = 2.0,
        sourceGroupUuid = groupUuid,
        appliedAt = testInstant(scheduledAt.plusMinutes(5)),
        scheduledFor = scheduledAt,
        count = count,
    )

    private fun planWith(vararg medications: MedicationGroupMedication) = MedicationGroup(
        uuid = groupUuid,
        name = "Morning",
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 4, 1),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(9, 0)),
        ),
        medications = medications.toList(),
        notificationsEnabled = true,
        createdAt = Instant.parse("2026-04-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
    )
}
