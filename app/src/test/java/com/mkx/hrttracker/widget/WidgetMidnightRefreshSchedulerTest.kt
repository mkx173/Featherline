package com.mkx.hrttracker.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.net.Uri
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
    fun nextWidgetDateRefreshAt_returnsNextLocalMidnight() {
        assertEquals(
            LocalDateTime.of(2026, 5, 23, 0, 0),
            nextWidgetDateRefreshAt(LocalDateTime.of(2026, 5, 22, 16, 45)),
        )
    }

    @Test
    fun scheduleNextWidgetDateRefresh_schedulesExactAlarmAtNextLocalMidnight() {
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
            LocalDateTime.of(2026, 5, 23, 0, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
            triggerSlot.captured,
        )
    }

    @Test
    fun scheduleNextWidgetDateRefresh_fallsBackToInexactAlarmWhenExactAccessIsMissing() {
        every { alarmManager.canScheduleExactAlarms() } returns false

        scheduleNextWidgetDateRefresh(
            context = context,
            now = LocalDateTime.of(2026, 5, 22, 16, 45),
            diagnosticsLogger = diagnosticsLogger,
        )

        verify(exactly = 0) {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
        verify(exactly = 1) {
            alarmManager.setAndAllowWhileIdle(any(), any(), pendingIntent)
        }
    }
}
