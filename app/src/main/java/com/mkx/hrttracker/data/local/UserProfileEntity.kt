package com.mkx.hrttracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String = SINGLETON_ID,
    val weightKg: Double?,
    val weightOriginalValue: Double?,
    val weightOriginalUnit: String?,
    // Equal to weightMeasuredAtEpochMillis exactly when the weight was typed: that
    // equality is how UserProfile.isHealthConnectReading tells a typed weight from
    // a Health Connect reading (the Settings row's "· Health Connect"). A write that
    // bumps this on a typed weight without moving its date would credit Health
    // Connect; only the unit switch bumps it alone, and only on a reading.
    val updatedAtEpochMillis: Long,
    // The weight's date: when a typed weight was saved (the same millisecond as
    // updatedAtEpochMillis), or the adopted Health Connect reading's time (always
    // before its sync's, the read ending there, exclusive). Null with no weight, or
    // for one saved by an older build or restored from an older backup. Shown as
    // "recorded ..." and lets a sync skip a write when nothing changed.
    val weightMeasuredAtEpochMillis: Long? = null,
) {
    companion object {
        const val SINGLETON_ID = "default"
    }
}
