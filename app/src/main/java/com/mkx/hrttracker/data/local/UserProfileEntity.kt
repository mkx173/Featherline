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
) {
    companion object {
        const val SINGLETON_ID = "default"
    }
}
