package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.NoteEntity
import com.mkx.hrttracker.data.local.TrackedDateEntity
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import java.time.LocalDate

fun TrackedDateEntity.toModel(): TrackedDate = TrackedDate(
    id = uuid,
    name = name,
    icon = AnchorIcon.fromStorageValue(iconKey),
    date = LocalDate.parse(dateIso),
    palette = MedicationGroupColorKey.fromStorageValueOrNull(paletteKey),
    heroBackground = HeroBackground.fromStorageValue(heroBackgroundKey),
    pinnedOrder = pinnedOrder,
    createdAtEpochMillis = createdAtEpochMillis,
)

fun TrackedDate.toEntity(createdAtEpochMillis: Long, updatedAtEpochMillis: Long): TrackedDateEntity =
    TrackedDateEntity(
        uuid = id,
        name = name,
        iconKey = icon.storageKey,
        dateIso = date.toString(),
        paletteKey = palette?.name,
        heroBackgroundKey = heroBackground.storageKey,
        pinnedOrder = pinnedOrder,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

fun NoteEntity.toModel(): Note = Note(id = uuid, date = LocalDate.parse(dateIso), text = text)
