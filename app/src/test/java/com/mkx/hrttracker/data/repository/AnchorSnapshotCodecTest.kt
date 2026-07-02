package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.journal.PrideFlag
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class AnchorSnapshotCodecTest {

    // Exercises every field shape the codec serializes: null/non-null palette, all three
    // HeroBackground variants, pinned + unpinned, non-ASCII names.
    private val fullRecord = AnchorSnapshotRecord(
        schemaVersion = ANCHOR_SNAPSHOT_SCHEMA_VERSION,
        trackedDates = listOf(
            TrackedDate(
                id = "a1",
                name = "Started HRT 💊",
                icon = AnchorIcon.MEDICATION,
                date = LocalDate.of(2024, 4, 1),
                palette = MedicationGroupColorKey.ROSE,
                heroBackground = HeroBackground.DateColor,
                pinnedOrder = 0,
                createdAtEpochMillis = 1_700_000_000_000L,
            ),
            TrackedDate(
                id = "a2",
                name = "Name change",
                icon = AnchorIcon.EVENT,
                date = LocalDate.of(2026, 12, 24),
                palette = null,
                heroBackground = HeroBackground.None,
                pinnedOrder = null,
                createdAtEpochMillis = 0L,
            ),
            TrackedDate(
                id = "a3",
                name = "Flag day",
                icon = AnchorIcon.FAVORITE,
                date = LocalDate.of(2025, 6, 1),
                palette = MedicationGroupColorKey.VIOLET,
                heroBackground = HeroBackground.Flag(PrideFlag.entries.first()),
                pinnedOrder = 3,
                createdAtEpochMillis = 42L,
            ),
        ),
    )

    // Intent: the persisted snapshot must reproduce the anchor list exactly, or the
    // cold-start seed would render wrong data as if it were real.
    @Test
    fun `encode then decode round-trips every field`() {
        val decoded = AnchorSnapshotCodec.decode(AnchorSnapshotCodec.encode(fullRecord))
        assertEquals(fullRecord, decoded)
    }

    // Intent: an empty journal is a legitimate snapshot (all anchors deleted) and must
    // round-trip as genuinely empty, not fail or decode as "no snapshot".
    @Test
    fun `empty list round-trips`() {
        val record = AnchorSnapshotRecord(ANCHOR_SNAPSHOT_SCHEMA_VERSION, emptyList())
        assertEquals(record, AnchorSnapshotCodec.decode(AnchorSnapshotCodec.encode(record)))
    }

    // Intent: a codec-version bump must invalidate old payloads loudly (callers treat the
    // throw as "no snapshot") instead of misreading bytes into garbage anchors.
    @Test
    fun `decode rejects unknown codec version`() {
        val bytes = AnchorSnapshotCodec.encode(fullRecord)
        bytes[3] = 99 // codec version is the first writeInt (big-endian); clobber its low byte
        assertThrows(IllegalArgumentException::class.java) { AnchorSnapshotCodec.decode(bytes) }
    }
}
