package com.mkx.hrttracker.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.mkx.hrttracker.model.medication.MedicationDayTimeRange
import com.mkx.hrttracker.reminder.canScheduleExactAlarms
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import java.time.LocalDateTime
import java.time.ZoneId

internal const val ACTION_WIDGET_DATE_REFRESH = "com.mkx.hrttracker.widget.ACTION_DATE_REFRESH"

/**
 * The next start of a Home/widget section ([MedicationDayTimeRange]). Civil midnight
 * refreshes journal anchors (civil dates), 04:00 the day split for night owl plans, noon
 * drops Last night and 18:00 opens Coming up for midnight plans, matching Home.
 */
// ponytail: every start is armed whatever the plans are, so the alarm never reads them;
// a start no plan uses (04:00 or 18:00) is one spare widget refresh a day.
internal fun nextWidgetDateRefreshAt(now: LocalDateTime): LocalDateTime {
    val date = now.toLocalDate()
    return (MedicationDayTimeRange.entries.map { range -> date.atTime(range.startHour, 0) } +
        date.plusDays(1).atStartOfDay())
        .first { refreshAt -> refreshAt.isAfter(now) }
}

internal fun scheduleNextWidgetDateRefresh(
    context: Context,
    now: LocalDateTime = LocalDateTime.now(),
    diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
    sdkInt: Int = Build.VERSION.SDK_INT,
) {
    val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
    val triggerAt = nextWidgetDateRefreshAt(now)
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
    // Best effort: an alarm failure (e.g. IllegalStateException at the 500-alarm cap)
    // must not stop the date refresh or app startup that called this.
    try {
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
    } catch (error: RuntimeException) {
        diagnosticsLogger.warning(
            WIDGET_DATE_REFRESH_TAG,
            "widget_date_refresh_alarm_schedule_failed triggerAt=$triggerAt",
            error,
        )
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
