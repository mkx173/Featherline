package com.mkx.hrttracker.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mkx.hrttracker.EXTRA_HIGHLIGHT_KIND
import com.mkx.hrttracker.EXTRA_HIGHLIGHT_SCHEDULED_TARGETS
import com.mkx.hrttracker.HIGHLIGHT_KIND_SCHEDULED
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.withAppLanguage
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

class ReminderNotificationManagerTest {
    private val context: Context = mockk(relaxed = true)
    private val resources: Resources = mockk(relaxed = true)
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)
    private val toast: Toast = mockk(relaxed = true)
    private lateinit var notificationManager: ReminderNotificationManager

    @Before
    fun setUp() {
        mockkStatic(NotificationManagerCompat::class)
        mockkStatic(PendingIntent::class)
        mockkStatic(Uri::class)
        mockkStatic(Looper::class)
        mockkStatic(Toast::class)
        mockkConstructor(NotificationCompat.Builder::class)
        mockkConstructor(Intent::class)
        mockkConstructor(Handler::class)

        every { Uri.parse(any()) } returns mockk(relaxed = true)
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk()
        every { PendingIntent.getBroadcast(any(), any(), any(), any<Int>()) } returns mockk()
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        every { anyConstructed<Handler>().post(any()) } answers {
            firstArg<Runnable>().run()
            true
        }
        every { context.resources } returns resources
        // showDoseReminderNotification pins content to the app language via
        // context.withAppLanguage(); that locale machinery is verified separately,
        // so here it resolves to the same mock context.
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        every { context.withAppLanguage() } returns context
        every { Toast.makeText(any(), any<String>(), Toast.LENGTH_SHORT) } returns toast

        // Builder fluent chain — all mutators return self so chaining is preserved
        every { anyConstructed<NotificationCompat.Builder>().setSmallIcon(any<Int>()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setContentTitle(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setContentText(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setPriority(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setContentIntent(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setAutoCancel(any()) } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().setStyle(any()) } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().addAction(
                any<Int>(),
                any(),
                any()
            )
        } answers { self as NotificationCompat.Builder }
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
        every {
            notifManagerCompat.notify(
                any<String>(),
                any(),
                any()
            )
        } throws SecurityException("revoked")

        notificationManager.showDoseReminderNotification(bundle, canSnooze = false)

        verify {
            diagnosticsLogger.warning(
                "ReminderNotificationManager",
                "reminder_notification_show_failed reason=security_exception tag=${bundle.notificationTag}",
            )
        }
    }

    @Test
    fun stockWarningToasts_useMedicineDisplayNameAndCountTemplates() {
        val medicine = testMedicine(
            key = MedicationKey.ESTRADIOL,
            displayName = null,
        )
        every { context.getString(R.string.medication_name_estradiol) } returns "Estradiol"
        every {
            context.getString(R.string.stock_toast_out_single, "Estradiol")
        } returns "Out of stock: Estradiol"
        every {
            context.getString(R.string.stock_toast_imminent_single, "Estradiol")
        } returns "Almost out: Estradiol"
        every {
            context.getString(R.string.stock_toast_user_low_single, "Estradiol")
        } returns "Low stock: Estradiol"
        every { resources.getQuantityString(R.plurals.stock_toast_out_multiple, 2, 2) } returns
                "2 medicines out of stock"
        every { resources.getQuantityString(R.plurals.stock_toast_imminent_multiple, 2, 2) } returns
                "2 medicines almost out"
        every { resources.getQuantityString(R.plurals.stock_toast_user_low_multiple, 2, 2) } returns
                "2 medicines low on stock"

        notificationManager.showStockOutToast(medicine)
        notificationManager.showStockImminentToast(medicine)
        notificationManager.showStockUserLowToast(medicine)
        notificationManager.showStockOutCountToast(2)
        notificationManager.showStockImminentCountToast(2)
        notificationManager.showStockUserLowCountToast(2)

        verify { Toast.makeText(context, "Out of stock: Estradiol", Toast.LENGTH_SHORT) }
        verify { Toast.makeText(context, "Almost out: Estradiol", Toast.LENGTH_SHORT) }
        verify { Toast.makeText(context, "Low stock: Estradiol", Toast.LENGTH_SHORT) }
        verify { Toast.makeText(context, "2 medicines out of stock", Toast.LENGTH_SHORT) }
        verify { Toast.makeText(context, "2 medicines almost out", Toast.LENGTH_SHORT) }
        verify { Toast.makeText(context, "2 medicines low on stock", Toast.LENGTH_SHORT) }
        verify(exactly = 6) { toast.show() }
    }

    @Test
    fun stockWarningCountToasts_supportSingleCount() {
        every { resources.getQuantityString(R.plurals.stock_toast_out_multiple, 1, 1) } returns
                "1 medicine out of stock"
        every { resources.getQuantityString(R.plurals.stock_toast_imminent_multiple, 1, 1) } returns
                "1 medicine almost out"
        every { resources.getQuantityString(R.plurals.stock_toast_user_low_multiple, 1, 1) } returns
                "1 medicine low on stock"

        notificationManager.showStockOutCountToast(1)
        notificationManager.showStockImminentCountToast(1)
        notificationManager.showStockUserLowCountToast(1)

        verify { Toast.makeText(context, "1 medicine out of stock", Toast.LENGTH_SHORT) }
        verify { Toast.makeText(context, "1 medicine almost out", Toast.LENGTH_SHORT) }
        verify { Toast.makeText(context, "1 medicine low on stock", Toast.LENGTH_SHORT) }
        verify(exactly = 3) { toast.show() }
    }

    @Test
    fun stockWarningToasts_resolveStringsThroughAppLanguageContext() {
        // Regression: below API 33 the injected ApplicationContext stays on the system
        // locale, so a post-log toast must resolve its text through context.withAppLanguage()
        // rather than the plain context, or it shows in the system language even when the
        // user picked a different app language.
        val localizedContext: Context = mockk(relaxed = true)
        val localizedResources: Resources = mockk(relaxed = true)
        every { context.withAppLanguage() } returns localizedContext
        every { localizedContext.resources } returns localizedResources
        every {
            localizedResources.getQuantityString(R.plurals.stock_toast_out_multiple, 2, 2)
        } returns "Localized out"
        // The plain (system-locale) context would resolve a different string.
        every {
            resources.getQuantityString(R.plurals.stock_toast_out_multiple, 2, 2)
        } returns "System out"

        notificationManager.showStockOutCountToast(2)

        verify { Toast.makeText(localizedContext, "Localized out", Toast.LENGTH_SHORT) }
    }

    @Test
    fun reminderNotificationScheduledHighlightTargetStorageValues_returnsEveryBundleSlotWithoutMedicationUuid() {
        val firstSlot = MedicationReminderSlot(
            groupUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000"),
            scheduledAt = LocalDateTime.of(2026, 5, 20, 9, 0),
            scheduleTimeUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000000"),
        )
        val secondSlot = MedicationReminderSlot(
            groupUuid = UUID.fromString("cccccccc-0000-0000-0000-000000000000"),
            scheduledAt = LocalDateTime.of(2026, 5, 20, 9, 0),
            scheduleTimeUuid = null,
        )
        val bundle = MedicationReminderBundle(
            scheduledAt = LocalDateTime.of(2026, 5, 20, 9, 0),
            items = listOf(
                MedicationReminderBundleItem(firstSlot, "Morning", emptyList()),
                MedicationReminderBundleItem(secondSlot, "Evening", emptyList()),
            ),
        )

        assertEquals(
            listOf(
                "aaaaaaaa-0000-0000-0000-000000000000|bbbbbbbb-0000-0000-0000-000000000000|2026-05-20T09:00",
                "cccccccc-0000-0000-0000-000000000000||2026-05-20T09:00",
            ),
            reminderNotificationScheduledHighlightTargetStorageValues(bundle)
        )
    }

    @Test
    fun showDoseReminderNotification_addsScheduledHighlightTargetsToContentIntent() {
        val slot = MedicationReminderSlot(
            groupUuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000"),
            scheduledAt = LocalDateTime.of(2026, 5, 20, 9, 0),
            scheduleTimeUuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000000"),
        )
        val bundle = MedicationReminderBundle(
            scheduledAt = slot.scheduledAt,
            items = listOf(MedicationReminderBundleItem(slot, "Morning", emptyList())),
        )
        val notifManagerCompat: NotificationManagerCompat = mockk()
        every { NotificationManagerCompat.from(any()) } returns notifManagerCompat
        every { notifManagerCompat.areNotificationsEnabled() } returns true
        every { notifManagerCompat.notify(any<String>(), any(), any()) } just Runs

        notificationManager.showDoseReminderNotification(bundle, canSnooze = false)

        verify {
            anyConstructed<Intent>().putExtra(EXTRA_HIGHLIGHT_KIND, HIGHLIGHT_KIND_SCHEDULED)
            anyConstructed<Intent>().putStringArrayListExtra(
                EXTRA_HIGHLIGHT_SCHEDULED_TARGETS,
                arrayListOf(
                    "aaaaaaaa-0000-0000-0000-000000000000|" +
                            "bbbbbbbb-0000-0000-0000-000000000000|" +
                            "2026-05-20T09:00"
                ),
            )
        }
    }
}
