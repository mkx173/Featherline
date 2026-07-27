package com.mkx.hrttracker.reminder

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

private val Context.skippedDoseDataStore by preferencesDataStore(
    name = "skipped_medication_slots",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Singleton
class SkippedDoseStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun getSkippedSlots(now: LocalDateTime = LocalDateTime.now()): Set<MedicationReminderSlot> {
        var retained = emptySet<MedicationReminderSlot>()
        context.skippedDoseDataStore.edit { preferences ->
            val decoded = preferences[skippedSlotsKey]
                .orEmpty()
                .mapNotNull(::medicationReminderSlotFromStorageValue)
                .toSet()
            retained = decoded.filterTo(mutableSetOf()) { slot ->
                !slot.scheduledAt.isBefore(now.minusDays(SKIPPED_SLOT_RETENTION_DAYS))
            }
            if (retained.size != decoded.size) {
                preferences[skippedSlotsKey] =
                    retained.mapTo(mutableSetOf(), MedicationReminderSlot::toStorageValue)
            }
        }
        return retained
    }

    suspend fun addSkippedSlot(
        slot: MedicationReminderSlot,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        context.skippedDoseDataStore.edit { preferences ->
            val retained = preferences[skippedSlotsKey]
                .orEmpty()
                .mapNotNull(::medicationReminderSlotFromStorageValue)
                .filterTo(mutableSetOf()) { existing ->
                    !existing.scheduledAt.isBefore(now.minusDays(SKIPPED_SLOT_RETENTION_DAYS))
                }
                .plus(slot)
            preferences[skippedSlotsKey] =
                retained.mapTo(mutableSetOf(), MedicationReminderSlot::toStorageValue)
        }
    }

    private companion object {
        val skippedSlotsKey = stringSetPreferencesKey("skipped_slots")
        const val SKIPPED_SLOT_RETENTION_DAYS = 14L
    }
}

@Singleton
class MedicationSkipActionHandler @Inject constructor(
    private val skippedDoseStore: SkippedDoseStore,
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler,
    private val medicationReminderScheduler: MedicationReminderScheduler,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    suspend fun skip(
        slot: MedicationReminderSlot,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        diagnosticsLogger.info(
            TAG,
            "medication_skip_start groupUuid=${slot.groupUuid} scheduledAt=${slot.scheduledAt}",
        )
        skippedDoseStore.addSkippedSlot(slot, now)
        medicationReminderSnoozeScheduler.clearSnoozesForSlots(listOf(slot))
        medicationReminderScheduler.rescheduleGroup(slot.groupUuid, after = now)
        diagnosticsLogger.info(
            TAG,
            "medication_skip_complete groupUuid=${slot.groupUuid} scheduledAt=${slot.scheduledAt}",
        )
    }

    private companion object {
        const val TAG = "MedicationSkipAction"
    }
}
