package com.mkx.hrttracker.reminder

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderNotificationManagerActiveRemindersTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun activeDoseReminders_reads_back_posted_doses_and_skips_one_without_them() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = ReminderNotificationManager(context)
        val bundle = MedicationReminderBundle(
            scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
            items = listOf(
                MedicationReminderBundleItem(
                    slot = MedicationReminderSlot(
                        groupUuid = UUID.fromString("1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d"),
                        scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
                        scheduleTimeUuid = UUID.fromString("9f8e7d6c-5b4a-3928-1706-050403020100"),
                    ),
                    groupName = "Estradiol",
                    medications = listOf(testMedicationGroupMedication()),
                )
            ),
        )
        manager.showDoseReminderNotification(bundle, silent = true)
        // Posted before reminders carried their doses.
        context.getSystemService(NotificationManager::class.java).notify(
            "medication-reminder/2026-04-19T21:00/${UUID.randomUUID()}",
            0,
            NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_icon)
                .build(),
        )

        assertEquals(
            mapOf(bundle.notificationTag to ShownDoseReminder(bundle.slots, bundle.contentHash)),
            manager.activeDoseReminders(),
        )
    }
}
