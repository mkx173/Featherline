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
    // The adopted Health Connect reading's time; null for a typed weight or none.
    // Shown as "Measured ..." and lets a sync skip a write when nothing changed.
    val weightMeasuredAtEpochMillis: Long? = null,
) {
    companion object {
        const val SINGLETON_ID = "default"
    }
}
