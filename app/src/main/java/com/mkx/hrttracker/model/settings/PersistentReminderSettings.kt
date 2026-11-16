package com.mkx.hrttracker.model.settings

import java.util.UUID

/**
 * How a dose reminder keeps after the user until the dose is logged: repeats
 * while it is pending, the snooze length, and whether it rings as a full-screen
 * alarm over the lock screen. Everything here is opt-in except the snooze
 * length, whose default matches the fixed 15 minutes it replaced.
 */
data class PersistentReminderSettings(
    /** Re-post a pending dose reminder every [repeatInterval] until it is logged. */
    val repeatEnabled: Boolean = false,
    val repeatInterval: ReminderRepeatIntervalOption = ReminderRepeatIntervalOption.TEN_MINUTES,
    val repeatLimit: ReminderRepeatLimitOption = ReminderRepeatLimitOption.SIX,
    val snoozeDuration: ReminderSnoozeDurationOption = ReminderSnoozeDurationOption.FIFTEEN_MINUTES,
    /** Wake the screen and show the reminder over the lock screen. */
    val fullScreenAlarmEnabled: Boolean = false,
    /** Ring the alarm tone on the full-screen alarm (alarm volume, so silent mode does not mute it). */
    val alarmSoundEnabled: Boolean = true,
    val alarmVibrationEnabled: Boolean = true,
    val alarmVibrationPattern: AlarmVibrationPatternOption = AlarmVibrationPatternOption.CONTINUOUS,
    /**
     * The sound picked for persistent reminders: repeats post with it, and the
     * full-screen alarm rings it. Null keeps the defaults: repeats sound like
     * any dose reminder, and the alarm rings the device's alarm tone.
     */
    val customSoundUri: String? = null,
    /**
     * Plans left out of persistent reminders. Stored as exclusions, so every
     * plan, a new one included, takes part until the user turns it off.
     */
    val excludedGroupUuids: Set<UUID> = emptySet(),
) {
    /** Whether persistent reminders (repeats and the full-screen alarm) apply to the plan [groupUuid]. */
    fun appliesTo(groupUuid: UUID): Boolean = groupUuid !in excludedGroupUuids
}

enum class AlarmVibrationPatternOption {
    /** Buzz on a loop until the alarm is answered or times out. */
    CONTINUOUS,

    /** Three buzzes, then still. */
    THREE_PULSES;

    companion object {
        fun fromStorageValue(value: String?): AlarmVibrationPatternOption {
            return entries.firstOrNull { it.name == value } ?: CONTINUOUS
        }
    }
}

enum class ReminderRepeatIntervalOption(val minutes: Long) {
    ONE_MINUTE(minutes = 1),
    TWO_MINUTES(minutes = 2),
    THREE_MINUTES(minutes = 3),
    FIVE_MINUTES(minutes = 5),
    TEN_MINUTES(minutes = 10),
    FIFTEEN_MINUTES(minutes = 15),
    TWENTY_MINUTES(minutes = 20),
    THIRTY_MINUTES(minutes = 30),
    FORTY_FIVE_MINUTES(minutes = 45),
    SIXTY_MINUTES(minutes = 60);

    companion object {
        fun fromStorageValue(value: String?): ReminderRepeatIntervalOption {
            return entries.firstOrNull { it.name == value } ?: TEN_MINUTES
        }
    }
}

/** How many repeats follow the first reminder; [maxRepeats] null keeps going until the dose is logged. */
enum class ReminderRepeatLimitOption(val maxRepeats: Int?) {
    THREE(maxRepeats = 3),
    SIX(maxRepeats = 6),
    TWELVE(maxRepeats = 12),
    TWENTY_FOUR(maxRepeats = 24),
    UNTIL_LOGGED(maxRepeats = null);

    companion object {
        fun fromStorageValue(value: String?): ReminderRepeatLimitOption {
            return entries.firstOrNull { it.name == value } ?: SIX
        }
    }
}

enum class ReminderSnoozeDurationOption(val minutes: Long) {
    FIVE_MINUTES(minutes = 5),
    TEN_MINUTES(minutes = 10),
    FIFTEEN_MINUTES(minutes = 15),
    TWENTY_MINUTES(minutes = 20),
    THIRTY_MINUTES(minutes = 30),
    FORTY_FIVE_MINUTES(minutes = 45),
    SIXTY_MINUTES(minutes = 60);

    companion object {
        fun fromStorageValue(value: String?): ReminderSnoozeDurationOption {
            return entries.firstOrNull { it.name == value } ?: FIFTEEN_MINUTES
        }
    }
}
