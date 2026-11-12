package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

private const val LEGACY_SNOOZE_STORE = "medication_reminder_snoozes"

private val Context.legacySnoozeDataStore by preferencesDataStore(
    name = LEGACY_SNOOZE_STORE,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * The upgrade from v1.4.2, which had Remind later: cancels each snooze alarm its store names, then
 * deletes the store. Once the store is gone this only checks that its file is missing. A snooze
 * alarm it misses posts nothing: the action receiver no longer knows its action.
 */
internal suspend fun cancelLegacySnoozes(context: Context): Int {
    val file = context.dataStoreFile("$LEGACY_SNOOZE_STORE.preferences_pb")
    if (!file.exists()) return 0
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    return cancelLegacySnoozes(file, context.legacySnoozeDataStore) { alarmData ->
        val intent = Intent(context, MedicationReminderActionReceiver::class.java).apply {
            action = LEGACY_ACTION_SNOOZE_ALARM
            data = alarmData.toUri()
        }
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { pendingIntent ->
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}

/** [cancelLegacySnoozes] on the store in [file]: [cancel] gets each alarm's data. */
internal suspend fun cancelLegacySnoozes(
    file: File,
    store: DataStore<Preferences>,
    cancel: (String) -> Unit,
): Int {
    if (!file.exists()) return 0
    val alarms = legacySnoozeAlarmData(store.data.first()[stringSetPreferencesKey("snooze_records")].orEmpty())
    alarms.forEach(cancel)
    store.edit { preferences -> preferences.clear() }
    file.delete()
    return alarms.size
}

/**
 * The data of each alarm v1.4.2 armed for its snooze [records]: it stored
 * "<slot>\u001F<local snooze time>\u001F<count>", and armed one alarm per snooze time for every
 * record that shared it. A PendingIntent matches on its action and data, not its extras.
 */
internal fun legacySnoozeAlarmData(records: Set<String>): List<String> =
    records.map { value -> value.split("\u001F") }
        .filter { parts -> parts.size == 3 }
        .groupBy(keySelector = { parts -> parts[1] }, valueTransform = { parts -> parts[0] })
        .map { (snoozeAt, slots) ->
            val bundleUuid = UUID.nameUUIDFromBytes(
                slots.sorted().joinToString(separator = ";").toByteArray(StandardCharsets.UTF_8)
            )
            "hrttracker://medication-reminder-snooze/$snoozeAt/$bundleUuid"
        }

internal const val LEGACY_ACTION_SNOOZE_ALARM = "com.mkx.hrttracker.action.MEDICATION_REMINDER_SNOOZE_ALARM"
