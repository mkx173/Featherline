package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.ScheduledDoseRowHighlightTarget
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.scheduledDoseRowHighlightTargetFromStorageValue
import com.mkx.hrttracker.toStorageValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

class DoseRowHighlightKeyTest {

    private val groupUuid = UUID.fromString("11111111-0000-0000-0000-000000000000")
    private val slotUuid = UUID.fromString("22222222-0000-0000-0000-000000000000")
    private val medUuid = UUID.fromString("33333333-0000-0000-0000-000000000000")
    private val entryUuid = UUID.fromString("44444444-0000-0000-0000-000000000000")
    private val scheduledAt = LocalDateTime.of(2026, 5, 20, 8, 0)

    private val medicine = testCustomMedicine(medicationName = "Estradiol")

    private fun scheduledTodayRow(
        groupUuid: UUID = this.groupUuid,
        scheduleTimeUuid: UUID? = slotUuid,
        scheduledAt: LocalDateTime = this.scheduledAt,
        medicationUuid: UUID = medUuid,
    ): MainTodayDoseRowUiState = MainTodayDoseRowUiState(
        groupUuid = groupUuid,
        groupName = "Test",
        groupColorKey = null,
        scheduleTimeUuid = scheduleTimeUuid,
        scheduledAt = scheduledAt,
        medication = testMedicationGroupMedication(uuid = medicationUuid, medicine = medicine),
        status = MainTodayDoseStatus.DUE_SOON,
    )

    private fun manualTodayRow(entryUuid: UUID = this.entryUuid): MainTodayDoseRowUiState =
        MainTodayDoseRowUiState(
            groupUuid = null,
            groupName = "",
            groupColorKey = null,
            scheduleTimeUuid = null,
            scheduledAt = scheduledAt,
            medication = testMedicationGroupMedication(
                uuid = UUID.randomUUID(),
                medicine = medicine
            ),
            status = MainTodayDoseStatus.DONE,
            isManualRecord = true,
            fulfillingEntryUuids = listOf(entryUuid),
        )

    private fun upcomingRow(
        groupUuid: UUID = this.groupUuid,
        scheduleTimeUuid: UUID? = slotUuid,
        scheduledAt: LocalDateTime = this.scheduledAt,
        medicationUuid: UUID = medUuid,
    ): MainUpcomingDoseRowUiState = MainUpcomingDoseRowUiState(
        groupUuid = groupUuid,
        groupName = "Test",
        groupColorKey = MedicationGroupColorKey.ROSE,
        scheduleTimeUuid = scheduleTimeUuid,
        scheduledAt = scheduledAt,
        medication = testMedicationGroupMedication(uuid = medicationUuid, medicine = medicine),
    )

    // -- Scheduled matches today row --

    @Test
    fun `Scheduled matches today row with same group, slot, time, and medication`() {
        val key = DoseRowHighlightKey.Scheduled(groupUuid, slotUuid, scheduledAt, medUuid)
        assertTrue(key.matches(scheduledTodayRow()))
    }

    @Test
    fun `Scheduled with null medicationUuid matches any medication in that slot`() {
        val key =
            DoseRowHighlightKey.Scheduled(groupUuid, slotUuid, scheduledAt, medicationUuid = null)
        assertTrue(key.matches(scheduledTodayRow(medicationUuid = UUID.randomUUID())))
    }

    @Test
    fun `Scheduled does not match today row with wrong groupUuid`() {
        val key = DoseRowHighlightKey.Scheduled(groupUuid, slotUuid, scheduledAt, medUuid)
        assertFalse(key.matches(scheduledTodayRow(groupUuid = UUID.randomUUID())))
    }

    @Test
    fun `Scheduled does not match today row with wrong scheduledAt`() {
        val key = DoseRowHighlightKey.Scheduled(groupUuid, slotUuid, scheduledAt, medUuid)
        assertFalse(key.matches(scheduledTodayRow(scheduledAt = scheduledAt.plusHours(1))))
    }

    @Test
    fun `Scheduled does not match today row with wrong medicationUuid when key has one`() {
        val key = DoseRowHighlightKey.Scheduled(groupUuid, slotUuid, scheduledAt, medUuid)
        assertFalse(key.matches(scheduledTodayRow(medicationUuid = UUID.randomUUID())))
    }

    // -- Manual matches today row --

    @Test
    fun `Manual matches today row whose fulfillingEntryUuids contains the entryUuid`() {
        val key = DoseRowHighlightKey.Manual(entryUuid)
        assertTrue(key.matches(manualTodayRow(entryUuid)))
    }

    @Test
    fun `Manual does not match today row whose fulfillingEntryUuids lacks the entryUuid`() {
        val key = DoseRowHighlightKey.Manual(entryUuid)
        assertFalse(key.matches(manualTodayRow(UUID.randomUUID())))
    }

    @Test
    fun `Manual does not match a non-manual today row`() {
        val key = DoseRowHighlightKey.Manual(entryUuid)
        assertFalse(key.matches(scheduledTodayRow()))
    }

    // -- Scheduled matches upcoming row --

    @Test
    fun `Scheduled matches upcoming row with same identity`() {
        val key = DoseRowHighlightKey.Scheduled(groupUuid, slotUuid, scheduledAt, medUuid)
        assertTrue(key.matches(upcomingRow()))
    }

    @Test
    fun `Scheduled with null medicationUuid matches upcoming row`() {
        val key =
            DoseRowHighlightKey.Scheduled(groupUuid, slotUuid, scheduledAt, medicationUuid = null)
        assertTrue(key.matches(upcomingRow(medicationUuid = UUID.randomUUID())))
    }

    @Test
    fun `Scheduled does not match upcoming row with wrong groupUuid`() {
        val key = DoseRowHighlightKey.Scheduled(groupUuid, slotUuid, scheduledAt, medUuid)
        assertFalse(key.matches(upcomingRow(groupUuid = UUID.randomUUID())))
    }

    @Test
    fun `Manual does not match an upcoming row`() {
        val key = DoseRowHighlightKey.Manual(entryUuid)
        assertFalse(key.matches(upcomingRow()))
    }

    // -- Multi-key request matching --

    @Test
    fun `Request matches today row when any key matches`() {
        val request = DoseRowHighlightRequest(
            listOf(
                DoseRowHighlightKey.Scheduled(UUID.randomUUID(), slotUuid, scheduledAt, medUuid),
                DoseRowHighlightKey.Scheduled(
                    groupUuid,
                    slotUuid,
                    scheduledAt,
                    medicationUuid = null
                ),
            )
        )

        assertTrue(request.matches(scheduledTodayRow(medicationUuid = UUID.randomUUID())))
    }

    @Test
    fun `Request matches upcoming row when any key matches`() {
        val otherGroupUuid = UUID.fromString("55555555-0000-0000-0000-000000000000")
        val request = DoseRowHighlightRequest(
            listOf(
                DoseRowHighlightKey.Scheduled(otherGroupUuid, slotUuid, scheduledAt, medUuid),
                DoseRowHighlightKey.Scheduled(
                    groupUuid,
                    slotUuid,
                    scheduledAt,
                    medicationUuid = null
                ),
            )
        )

        assertTrue(request.matches(upcomingRow(medicationUuid = UUID.randomUUID())))
    }

    @Test
    fun `Request does not match rows when no key matches`() {
        val request = DoseRowHighlightRequest(
            listOf(
                DoseRowHighlightKey.Scheduled(UUID.randomUUID(), slotUuid, scheduledAt, medUuid),
                DoseRowHighlightKey.Manual(UUID.randomUUID()),
            )
        )

        assertFalse(request.matches(scheduledTodayRow()))
    }

    // -- Scheduled highlight target storage --

    @Test
    fun `Scheduled highlight target storage round trips null schedule time`() {
        val target = ScheduledDoseRowHighlightTarget(
            groupUuid = groupUuid,
            scheduleTimeUuid = null,
            scheduledAt = scheduledAt,
        )

        assertEquals(
            target,
            scheduledDoseRowHighlightTargetFromStorageValue(target.toStorageValue())
        )
    }
}
