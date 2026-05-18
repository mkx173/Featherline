package com.mkx.hrttracker.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

class ReminderNotificationManagerTest {
    private val context: Context = mockk(relaxed = true)
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)
    private lateinit var notificationManager: ReminderNotificationManager

    @Before
    fun setUp() {
        mockkStatic(NotificationManagerCompat::class)
        mockkStatic(PendingIntent::class)
        mockkStatic(Uri::class)
        mockkConstructor(NotificationCompat.Builder::class)
        mockkConstructor(Intent::class)

        every { Uri.parse(any()) } returns mockk(relaxed = true)
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk()
        every { PendingIntent.getBroadcast(any(), any(), any(), any<Int>()) } returns mockk()

        // Builder fluent chain — all mutators return self so chaining is preserved
        every { anyConstructed<NotificationCompat.Builder>().setSmallIcon(any<Int>()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setContentTitle(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setContentText(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setPriority(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setContentIntent(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setAutoCancel(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setStyle(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().addAction(any<Int>(), any(), any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().build() } returns mockk()

        // Intent setters used in apply blocks; return values are discarded by the caller
        every { anyConstructed<Intent>().setAction(any()) } returns mockk()
        every { anyConstructed<Intent>().setData(any()) } returns mockk()
        every { anyConstructed<Intent>().putStringArrayListExtra(any(), any()) } returns mockk()
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } returns mockk()

        notificationManager = spyk(
            ReminderNotificationManager(
                context = context,
                diagnosticsLogger = diagnosticsLogger,
            )
        )
        every { notificationManager.createNotificationChannel(any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun showDoseReminderNotification_logsWarningWhenNotificationPermissionIsRevokedDuringPost() {
        val slot = MedicationReminderSlot(
            groupUuid = UUID.fromString("1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d"),
            scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
            scheduleTimeUuid = UUID.fromString("9f8e7d6c-5b4a-3928-1706-050403020100"),
        )
        val bundle = MedicationReminderBundle(
            scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
            items = listOf(
                MedicationReminderBundleItem(
                    slot = slot,
                    groupName = "Estradiol",
                    medications = emptyList(),
                )
            )
        )
        val notifManagerCompat: NotificationManagerCompat = mockk()
        every { NotificationManagerCompat.from(any()) } returns notifManagerCompat
        every { notifManagerCompat.areNotificationsEnabled() } returns true
        every { notifManagerCompat.notify(any<String>(), any(), any()) } throws SecurityException("revoked")

        notificationManager.showDoseReminderNotification(bundle, canSnooze = false)

        verify {
            diagnosticsLogger.warning(
                "ReminderNotificationManager",
                "reminder_notification_show_failed reason=security_exception tag=${bundle.notificationTag}",
            )
        }
    }
}
