package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.util.AppDiagnosticsLogger
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

class MedicationSkipActionHandlerTest {
    private val skippedDoseStore: SkippedDoseStore = mockk()
    private val snoozeScheduler: MedicationReminderSnoozeScheduler = mockk()
    private val reminderScheduler: MedicationReminderScheduler = mockk()
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)

    @Test
    fun skip_persists_slot_clears_snooze_and_reschedules_group() = runTest {
        val now = LocalDateTime.of(2026, 7, 27, 0, 30)
        val slot = MedicationReminderSlot(
            groupUuid = UUID.fromString("c287b78a-1986-491e-a22a-8f29127dc64e"),
            scheduledAt = LocalDateTime.of(2026, 7, 27, 1, 0),
            scheduleTimeUuid = UUID.fromString("98438752-ee85-4ac7-842a-11f7ef8cc827"),
        )
        coEvery { skippedDoseStore.addSkippedSlot(slot, now) } just Runs
        coEvery { snoozeScheduler.clearSnoozesForSlots(listOf(slot)) } just Runs
        coEvery { reminderScheduler.rescheduleGroup(slot.groupUuid, now) } just Runs

        MedicationSkipActionHandler(
            skippedDoseStore = skippedDoseStore,
            medicationReminderSnoozeScheduler = snoozeScheduler,
            medicationReminderScheduler = reminderScheduler,
            diagnosticsLogger = diagnosticsLogger,
        ).skip(slot, now)

        coVerifyOrder {
            skippedDoseStore.addSkippedSlot(slot, now)
            snoozeScheduler.clearSnoozesForSlots(listOf(slot))
            reminderScheduler.rescheduleGroup(slot.groupUuid, now)
        }
    }
}
