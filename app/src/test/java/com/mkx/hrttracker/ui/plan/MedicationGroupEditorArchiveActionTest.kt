package com.mkx.hrttracker.ui.plan

import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class MedicationGroupEditorArchiveActionTest {
    @Test
    fun runArchiveConfirmationAction_whenRecreating_dismissesInputBeforeAction() {
        val calls = mutableListOf<String>()
        val focusManager = mockk<FocusManager>(relaxed = true)
        val keyboardController = mockk<SoftwareKeyboardController>(relaxed = true)
        val archivedThroughDate = LocalDate.of(2026, 4, 25)

        every { focusManager.clearFocus(force = true) } answers {
            calls += "focus"
        }
        every { keyboardController.hide() } answers {
            calls += "hide"
        }

        runArchiveConfirmationAction(
            shouldCreateActiveCopyAfterArchive = true,
            archivedThroughDate = archivedThroughDate,
            focusManager = focusManager,
            keyboardController = keyboardController,
            onArchiveConfirm = {
                calls += "archive:$it"
            },
            onArchiveAndRecreateConfirm = {
                calls += "recreate:$it"
            },
        )

        assertEquals(listOf("focus", "hide", "recreate:2026-04-25"), calls)
    }

    @Test
    fun entryCountsForGroup_tracksLoadedFlagAndLatestRecordedDoseDate() {
        val groupUuid = UUID.fromString("f02d18aa-d514-4c87-98e7-710c5f4f7635")
        val counts = entryCountsForGroup(
            entries = listOf(
                testMedicationLogEntry(
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-05-08T15:30:00Z"),
                    appliedAtTimeZoneId = "Asia/Tokyo",
                    scheduledFor = null,
                ),
                testMedicationLogEntry(
                    sourceGroupUuid = groupUuid,
                    appliedAt = Instant.parse("2026-05-08T00:30:00Z"),
                    appliedAtTimeZoneId = "UTC",
                    scheduledFor = LocalDateTime.of(2026, 5, 10, 9, 0),
                ),
            ),
            groupId = groupUuid.toString(),
            currentMinute = LocalDateTime.of(2026, 5, 8, 10, 0),
            zoneId = ZoneId.of("America/Los_Angeles"),
            areEntriesLoaded = true,
        )

        assertTrue(counts.areEntriesLoaded)
        assertEquals(LocalDate.of(2026, 5, 10), counts.latestRecordedDoseDate)
    }

    @Test
    fun entryCountsForGroup_preservesNotLoadedState() {
        val counts = entryCountsForGroup(
            entries = emptyList(),
            groupId = UUID.fromString("f02d18aa-d514-4c87-98e7-710c5f4f7635").toString(),
            currentMinute = LocalDateTime.of(2026, 5, 8, 10, 0),
            zoneId = ZoneId.of("UTC"),
            areEntriesLoaded = false,
        )

        assertFalse(counts.areEntriesLoaded)
        assertNull(counts.latestRecordedDoseDate)
    }

    @Test
    fun resolveArchiveDateWindow_floorsMinOnScheduleStartNotToday() {
        // Backdated/backfilled plan: schedule started Jun 1, today is Jun 3, and
        // the only dose is on the start day. Earlier days must stay selectable —
        // the floor is the plan's start, not the wall-clock creation date. This
        // is the "cannot select days other than today" regression.
        val window = resolveArchiveDateWindow(
            scheduleSince = LocalDate.of(2026, 6, 1),
            latestRecordedDoseDate = LocalDate.of(2026, 6, 1),
            areEntriesLoaded = true,
            today = LocalDate.of(2026, 6, 3),
        )

        assertTrue(window.isLoaded)
        assertEquals(LocalDate.of(2026, 6, 1), window.minDate)
        assertEquals(LocalDate.of(2026, 6, 3), window.maxDate)
        assertTrue(window.isSelectable)
    }

    @Test
    fun resolveArchiveDateWindow_usesLatestDoseWhenAfterScheduleStart() {
        val window = resolveArchiveDateWindow(
            scheduleSince = LocalDate.of(2026, 4, 1),
            latestRecordedDoseDate = LocalDate.of(2026, 4, 3),
            areEntriesLoaded = true,
            today = LocalDate.of(2026, 4, 5),
        )

        assertTrue(window.isLoaded)
        assertEquals(LocalDate.of(2026, 4, 3), window.minDate)
        assertEquals(LocalDate.of(2026, 4, 5), window.maxDate)
        assertTrue(window.isSelectable)
    }

    @Test
    fun resolveArchiveDateWindow_blocksWhenEntriesAreNotLoaded() {
        val window = resolveArchiveDateWindow(
            scheduleSince = LocalDate.of(2026, 4, 1),
            latestRecordedDoseDate = null,
            areEntriesLoaded = false,
            today = LocalDate.of(2026, 4, 5),
        )

        assertFalse(window.isLoaded)
        assertNull(window.minDate)
        assertEquals(LocalDate.of(2026, 4, 5), window.maxDate)
        assertFalse(window.isSelectable)
    }

    @Test
    fun resolveArchiveDateWindow_blocksWhenScheduleStartMissing() {
        // A not-yet-persisted group has no schedule start, so archiving stays
        // unavailable until the group is loaded.
        val window = resolveArchiveDateWindow(
            scheduleSince = null,
            latestRecordedDoseDate = null,
            areEntriesLoaded = true,
            today = LocalDate.of(2026, 4, 5),
        )

        assertFalse(window.isLoaded)
        assertNull(window.minDate)
        assertFalse(window.isSelectable)
    }

    @Test
    fun resolveArchiveDateWindow_blocksWhenLatestRecordedDoseIsAfterToday() {
        val window = resolveArchiveDateWindow(
            scheduleSince = LocalDate.of(2026, 4, 1),
            latestRecordedDoseDate = LocalDate.of(2026, 4, 8),
            areEntriesLoaded = true,
            today = LocalDate.of(2026, 4, 5),
        )

        assertTrue(window.isLoaded)
        assertEquals(LocalDate.of(2026, 4, 8), window.minDate)
        assertEquals(LocalDate.of(2026, 4, 5), window.maxDate)
        assertFalse(window.isSelectable)
    }

    @Test
    fun isArchiveDateSelectionInvalid_allowsNullNowDateForNotYetStartedPlan() {
        // A plan that starts in the future has no selectable backdate (its window
        // is loaded but not selectable, min > today). The "Now" default (null)
        // must still be accepted so the not-yet-started plan stays cancelable —
        // the picker window only constrains an explicitly picked date. Regression
        // for archive being silently blocked for future-start groups.
        val window = resolveArchiveDateWindow(
            scheduleSince = LocalDate.of(2026, 6, 10),
            latestRecordedDoseDate = null,
            areEntriesLoaded = true,
            today = LocalDate.of(2026, 6, 3),
        )
        assertTrue(window.isLoaded)
        assertFalse(window.isSelectable)

        assertFalse(isArchiveDateSelectionInvalid(archivedThroughDate = null, window = window))
    }

    @Test
    fun isArchiveDateSelectionInvalid_rejectsNullNowDateBeforeWindowLoaded() {
        // Until entries load we don't know the current/future-slot picture, so
        // even the "Now" path stays gated.
        val window = resolveArchiveDateWindow(
            scheduleSince = LocalDate.of(2026, 4, 1),
            latestRecordedDoseDate = null,
            areEntriesLoaded = false,
            today = LocalDate.of(2026, 4, 5),
        )

        assertTrue(isArchiveDateSelectionInvalid(archivedThroughDate = null, window = window))
    }

    @Test
    fun isArchiveDateSelectionInvalid_acceptsPickedDateInsideWindow() {
        val window = resolveArchiveDateWindow(
            scheduleSince = LocalDate.of(2026, 4, 1),
            latestRecordedDoseDate = LocalDate.of(2026, 4, 3),
            areEntriesLoaded = true,
            today = LocalDate.of(2026, 4, 5),
        )

        assertFalse(
            isArchiveDateSelectionInvalid(
                archivedThroughDate = LocalDate.of(2026, 4, 4),
                window = window,
            )
        )
    }

    @Test
    fun isArchiveDateSelectionInvalid_rejectsPickedDateWhenNoBackdateSelectable() {
        // A future-start plan accepts "Now" but cannot accept an explicit picked
        // date, since there is no valid day in [min, max].
        val window = resolveArchiveDateWindow(
            scheduleSince = LocalDate.of(2026, 6, 10),
            latestRecordedDoseDate = null,
            areEntriesLoaded = true,
            today = LocalDate.of(2026, 6, 3),
        )

        assertTrue(
            isArchiveDateSelectionInvalid(
                archivedThroughDate = LocalDate.of(2026, 6, 3),
                window = window,
            )
        )
    }

    @Test
    fun isArchiveDateSelectionInvalid_rejectsPickedDateOutsideWindow() {
        val window = resolveArchiveDateWindow(
            scheduleSince = LocalDate.of(2026, 4, 1),
            latestRecordedDoseDate = LocalDate.of(2026, 4, 3),
            areEntriesLoaded = true,
            today = LocalDate.of(2026, 4, 5),
        )

        assertTrue(
            isArchiveDateSelectionInvalid(
                archivedThroughDate = LocalDate.of(2026, 4, 6),
                window = window,
            )
        )
    }
}
