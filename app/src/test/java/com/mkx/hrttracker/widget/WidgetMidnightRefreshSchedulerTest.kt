package com.mkx.hrttracker.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Build
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class WidgetMidnightRefreshSchedulerTest {
    private val context: Context = mockk()
    private val alarmManager: AlarmManager = mockk(relaxed = true)
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)
    private val pendingIntent: PendingIntent = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(PendingIntent::class)
        mockkStatic(Uri::class)
        every {
            PendingIntent.getBroadcast(any(), any(), any(), any<Int>())
        } returns pendingIntent
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.canScheduleExactAlarms() } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun nextRefreshIsTheNextSectionStart() {
        // 04:00, noon, 18:00 (Coming up opens for midnight plans) and midnight.
        val midnight = LocalDateTime.of(2026, 10, 2, 0, 0)
        assertEquals(midnight.withHour(4), nextWidgetDateRefreshAt(midnight))
        assertEquals(midnight.withHour(4), nextWidgetDateRefreshAt(midnight.withHour(1)))
        assertEquals(midnight.withHour(12), nextWidgetDateRefreshAt(midnight.withHour(4)))
        assertEquals(midnight.withHour(12), nextWidgetDateRefreshAt(midnight.withHour(11).withMinute(55)))
        assertEquals(midnight.withHour(18), nextWidgetDateRefreshAt(midnight.withHour(12)))
        assertEquals(midnight.withHour(18), nextWidgetDateRefreshAt(midnight.withHour(16).withMinute(45)))
        assertEquals(midnight.plusDays(1), nextWidgetDateRefreshAt(midnight.withHour(18)))
        assertEquals(midnight.plusDays(1), nextWidgetDateRefreshAt(midnight.withHour(23).withMinute(59)))
    }

    @Test
    fun scheduleNextWidgetDateRefresh_schedulesExactAlarmAtTheNextSectionStart() {
        val triggerSlot = slot<Long>()
        val now = LocalDateTime.of(2026, 5, 22, 16, 45)

        scheduleNextWidgetDateRefresh(
            context = context,
            now = now,
            diagnosticsLogger = diagnosticsLogger,
        )
        verify(exactly = 1) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                capture(triggerSlot),
                pendingIntent,
            )
        }
        assertEquals(
            LocalDateTime.of(2026, 5, 22, 18, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
            triggerSlot.captured,
        )
    }

    @Test
    fun scheduleNextWidgetDateRefresh_logsInsteadOfThrowingAtTheAlarmLimit() {
        every {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any())
        } throws IllegalStateException("Maximum limit of concurrent alarms 500 reached")

        scheduleNextWidgetDateRefresh(
            context = context,
            now = LocalDateTime.of(2026, 10, 28, 1, 0),
            diagnosticsLogger = diagnosticsLogger,
        )

        verify { diagnosticsLogger.warning(any(), match { it.startsWith("widget_date_refresh_alarm_schedule_failed") }, any()) }
    }

    @Test
    fun scheduleNextWidgetDateRefresh_fallsBackToInexactAlarmWhenExactAccessIsMissing() {
        every { alarmManager.canScheduleExactAlarms() } returns false

        scheduleNextWidgetDateRefresh(
            context = context,
            now = LocalDateTime.of(2026, 5, 22, 16, 45),
            diagnosticsLogger = diagnosticsLogger,
            sdkInt = Build.VERSION_CODES.S,
        )
        verify(exactly = 0) {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
        verify(exactly = 1) {
            alarmManager.setAndAllowWhileIdle(any(), any(), pendingIntent)
        }
    }
}
