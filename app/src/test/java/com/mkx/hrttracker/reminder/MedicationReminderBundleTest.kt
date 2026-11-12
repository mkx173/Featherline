package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/** One plan's reminder for its doses at a time: what it shows of each, and when it has none. */
class MedicationReminderBundleTest {
    // Shared so the fulfillment signature matches between the plan's medication and its log.
    private val estradiolMedicineUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val spiroUuid = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)

    @Test
    fun aPartlyLoggedDose_showsOnlyWhatIsLeft() {
        val bundle = buildMedicationReminderBundle(
            scheduledAt = scheduledAt,
            group = estradiolAndSpiro(),
            entries = listOf(estradiolLog(appliedAt = scheduledAt.minusMinutes(10))),
        )

        assertEquals(listOf(spiroUuid), bundle?.items?.single()?.medications?.map { it.medicineUuid })
    }

    @Test
    fun aRecordLoggedLateOutsideItsWindow_stillTakesItsMedication() {
        // Only the estradiol, logged at 21:30, 12.5 h late: the spiro is still due.
        val bundle = buildMedicationReminderBundle(
            scheduledAt = scheduledAt,
            group = estradiolAndSpiro(),
            entries = listOf(estradiolLog(appliedAt = scheduledAt.plusHours(12).plusMinutes(30))),
        )

        assertEquals(listOf(spiroUuid), bundle?.items?.single()?.medications?.map { it.medicineUuid })
    }

    @Test
    fun aFullyLoggedDose_hasNoReminder() {
        val estradiolOnly = estradiolAndSpiro().let { group -> group.copy(medications = group.medications.take(1)) }

        assertNull(
            buildMedicationReminderBundle(
                scheduledAt = scheduledAt,
                group = estradiolOnly,
                entries = listOf(estradiolLog(appliedAt = scheduledAt.plusHours(12).plusMinutes(30))),
            )
        )
    }

    private fun estradiolAndSpiro() = MedicationGroup(
        uuid = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
        name = "Morning",
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 4, 1),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(9, 0)),
        ),
        medications = listOf(
            testMedicationGroupMedication(
                medicine = testMedicine(uuid = estradiolMedicineUuid, key = MedicationKey.ESTRADIOL),
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
            ),
            testMedicationGroupMedication(
                medicine = testMedicine(uuid = spiroUuid, key = MedicationKey.SPIRONOLACTONE),
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
            ),
        ),
        notificationsEnabled = true,
        createdAt = Instant.parse("2026-04-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
    )

    private fun estradiolLog(appliedAt: LocalDateTime): MedicationLogEntry = testMedicationLogEntry(
        medicine = testMedicine(uuid = estradiolMedicineUuid, key = MedicationKey.ESTRADIOL),
        applicationType = MedicationApplicationType.ORAL,
        doseInstruction = DoseInstruction.TabletFraction(1, 1),
        equivalentE2Mg = 2.0,
        sourceGroupUuid = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
        appliedAt = testInstant(appliedAt),
        scheduledFor = scheduledAt,
    )
}
