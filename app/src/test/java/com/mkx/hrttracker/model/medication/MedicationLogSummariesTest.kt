package com.mkx.hrttracker.model.medication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class MedicationLogSummariesTest {
    private val baseInstant = Instant.parse("2026-05-15T09:00:00Z")

    @Test
    fun findLastEstradiolEntry_returns_null_for_empty_list() {
        assertNull(findLastEstradiolEntry(entries = emptyList()))
    }

    @Test
    fun findLastEstradiolEntry_returns_latest_estradiol_entry_when_no_bound() {
        val older = estradiolEntry(appliedAt = baseInstant.minusSeconds(3_600))
        val newer = estradiolEntry(appliedAt = baseInstant)

        assertEquals(newer, findLastEstradiolEntry(entries = listOf(older, newer)))
    }

    @Test
    fun findLastEstradiolEntry_ignores_non_estradiol_categories() {
        val antiandrogen = entry(
            category = MedicationCategory.ANTIANDROGEN,
            appliedAt = baseInstant,
        )
        val olderEstradiol = estradiolEntry(appliedAt = baseInstant.minusSeconds(3_600))

        assertEquals(
            olderEstradiol,
            findLastEstradiolEntry(entries = listOf(antiandrogen, olderEstradiol)),
        )
    }

    @Test
    fun findLastEstradiolEntry_excludes_entries_after_onOrBefore() {
        val before = estradiolEntry(appliedAt = baseInstant.minusSeconds(60))
        val after = estradiolEntry(appliedAt = baseInstant.plusSeconds(60))

        assertEquals(
            before,
            findLastEstradiolEntry(entries = listOf(before, after), onOrBefore = baseInstant),
        )
    }

    @Test
    fun findLastEstradiolEntry_includes_entry_exactly_at_onOrBefore() {
        val exact = estradiolEntry(appliedAt = baseInstant)

        assertEquals(
            exact,
            findLastEstradiolEntry(entries = listOf(exact), onOrBefore = baseInstant),
        )
    }

    @Test
    fun timeSinceEntryMillis_returns_null_when_entry_is_null() {
        assertNull(timeSinceEntryMillis(target = baseInstant, entry = null))
    }

    @Test
    fun timeSinceEntryMillis_returns_elapsed_millis_for_entry_before_target() {
        val entry = estradiolEntry(appliedAt = baseInstant.minusMillis(1_500L))

        assertEquals(1_500L, timeSinceEntryMillis(target = baseInstant, entry = entry))
    }

    @Test
    fun timeSinceEntryMillis_returns_zero_when_entry_equals_target() {
        val entry = estradiolEntry(appliedAt = baseInstant)

        assertEquals(0L, timeSinceEntryMillis(target = baseInstant, entry = entry))
    }

    @Test
    fun timeSinceEntryMillis_clamps_negative_elapsed_to_zero_for_future_entry() {
        val entry = estradiolEntry(appliedAt = baseInstant.plusMillis(500L))

        assertEquals(0L, timeSinceEntryMillis(target = baseInstant, entry = entry))
    }

    private fun estradiolEntry(appliedAt: Instant): MedicationLogEntry {
        return entry(category = MedicationCategory.ESTRADIOL, appliedAt = appliedAt)
    }

    private fun entry(
        category: MedicationCategory,
        appliedAt: Instant,
    ): MedicationLogEntry {
        val key = when (category) {
            MedicationCategory.ESTRADIOL -> MedicationKey.ESTRADIOL
            MedicationCategory.ANTIANDROGEN -> MedicationKey.SPIRONOLACTONE
            MedicationCategory.TESTOSTERONE,
            MedicationCategory.CUSTOM -> error("Unsupported category for this fixture: $category")
        }
        return testMedicationLogEntry(
            medicine = testMedicine(key = key),
            category = category,
            applicationType = MedicationApplicationType.ORAL,
            sourceGroupUuid = null,
            appliedAt = appliedAt,
        )
    }
}
