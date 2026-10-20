package com.mkx.hrttracker.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.mkx.hrttracker.reminder.canScheduleExactAlarms
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.medicationDay
import com.mkx.hrttracker.util.medicationDayStart
import kotlinx.coroutines.CancellationException
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

internal const val ACTION_WIDGET_DATE_REFRESH = "com.mkx.hrttracker.widget.ACTION_DATE_REFRESH"

internal fun nextWidgetDateRefreshAt(now: LocalDateTime, boundary: MedicationDayBoundary): LocalDateTime {
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
    val nextBoundary = medicationDayStart(medicationDay(now, boundary).plusDays(1), boundary)
    // Noon drops Last night from the widget, matching Home.
    val noon = if (now.toLocalTime() < LocalTime.NOON) {
        now.toLocalDate().atTime(12, 0)
    } else {
        now.toLocalDate().plusDays(1).atTime(12, 0)
    }
    // Midnight also refreshes journal anchors, which retain their civil dates; the
    // medication-day boundary refreshes the day split.
    return minOf(midnight, nextBoundary, noon)
}

internal fun scheduleNextWidgetDateRefresh(
    context: Context,
    now: LocalDateTime = LocalDateTime.now(),
    diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
    sdkInt: Int = Build.VERSION.SDK_INT,
    boundary: MedicationDayBoundary,
) {
    val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
    val triggerAt = nextWidgetDateRefreshAt(now, boundary)
    val triggerAtMillis = triggerAt
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    val pendingIntent = widgetDateRefreshPendingIntent(context)
    val exactAlarm = canScheduleExactAlarms(alarmManager, sdkInt)

    diagnosticsLogger.info(
        WIDGET_DATE_REFRESH_TAG,
        "widget_date_refresh_alarm_schedule triggerAt=$triggerAt triggerAtMillis=$triggerAtMillis exact=$exactAlarm"
    )
    if (exactAlarm) {
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } catch (error: SecurityException) {
            diagnosticsLogger.warning(
                WIDGET_DATE_REFRESH_TAG,
                "widget_date_refresh_alarm_exact_revoked triggerAt=$triggerAt",
                error,
            )
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    } else {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }
}

/**
 * The single read-then-schedule path for the widget date-refresh alarm: reads the day start
 * through [readBoundary], falling back to [MedicationDayBoundary.Midnight] on failure, then
 * arms the next alarm. Callers supply the boundary source because startup must use the eager
 * settings state (no suspension) while the receiver reads the persisted settings.
 */
internal suspend fun armWidgetDateRefreshFromSettings(
    context: Context,
    diagnosticsLogger: AppDiagnosticsLogger,
    logTag: String,
    scheduleFailedMessage: String,
    readBoundary: suspend () -> MedicationDayBoundary,
) {
    val boundary = runCatching { readBoundary() }
        .onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
            diagnosticsLogger.warning(logTag, "widget_day_boundary_read_failed", throwable)
        }
        .getOrDefault(MedicationDayBoundary.Midnight)
    runCatching {
        scheduleNextWidgetDateRefresh(
            context = context,
            boundary = boundary,
            diagnosticsLogger = diagnosticsLogger,
        )
    }.onFailure { throwable ->
        if (throwable is CancellationException) throw throwable
        diagnosticsLogger.warning(logTag, scheduleFailedMessage, throwable)
    }
}

private fun widgetDateRefreshPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, WidgetDateReceiver::class.java).apply {
        action = ACTION_WIDGET_DATE_REFRESH
        data = WIDGET_DATE_REFRESH_URI.toUri()
    }
    return PendingIntent.getBroadcast(
        context,
        WIDGET_DATE_REFRESH_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private const val WIDGET_DATE_REFRESH_TAG = "WidgetDateRefresh"
private const val WIDGET_DATE_REFRESH_REQUEST_CODE = 0x485254
private const val WIDGET_DATE_REFRESH_URI = "hrttracker://widget/date-refresh"
