package com.mkx.hrttracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracked_dates",
    indices = [Index("pinnedOrder")],
)
data class TrackedDateEntity(
    @PrimaryKey val uuid: String,
    val name: String,
    val iconKey: String,
    // Wall-clock local date (no time), ISO-8601 ("yyyy-MM-dd"), TZ-stable.
    val dateIso: String,
    // MedicationGroupColorKey.name(), or null for the slate default.
    val paletteKey: String?,
    // null = unpinned; ascending non-null values are the tray order.
    val pinnedOrder: Int?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "notes",
    // One note per day: a second row for the same date is rejected by SQLite.
    indices = [Index(value = ["dateIso"], unique = true)],
)
data class NoteEntity(
    @PrimaryKey val uuid: String,
    val dateIso: String,
    val text: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
