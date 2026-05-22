package com.mkx.hrttracker.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import java.time.LocalDateTime
import java.time.ZoneId

internal const val ACTION_WIDGET_DATE_REFRESH = "com.mkx.hrttracker.widget.ACTION_DATE_REFRESH"

internal fun nextWidgetDateRefreshAt(now: LocalDateTime): LocalDateTime =
    now.toLocalDate().plusDays(1).atStartOfDay()

internal fun scheduleNextWidgetDateRefresh(
    context: Context,
    now: LocalDateTime = LocalDateTime.now(),
    diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
    val triggerAt = nextWidgetDateRefreshAt(now)
    val triggerAtMillis = triggerAt
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    val pendingIntent = widgetDateRefreshPendingIntent(context)
    val exactAlarm = runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)

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
