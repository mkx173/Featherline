package com.mkx.hrttracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String = SINGLETON_ID,
    val weightKg: Double?,
    val weightOriginalValue: Double?,
    val weightOriginalUnit: String?,
    val updatedAtEpochMillis: Long,
    // The weight's date: when a typed weight was saved, or the adopted Health
    // Connect reading's time. Null with no weight, or for one saved by an older
    // build or restored from an older backup. Shown as "recorded ..." and lets a
    // sync skip a write when nothing changed.
    val weightMeasuredAtEpochMillis: Long? = null,
) {
    companion object {
        const val SINGLETON_ID = "default"
    }
}
