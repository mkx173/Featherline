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
    fun resolveArchiveDateWindow_usesAppTimeZoneAndLoadedFlag() {
        val window = resolveArchiveDateWindow(
            groupCreatedAt = Instant.parse("2026-04-01T16:00:00Z"),
            latestRecordedDoseDate = null,
            areEntriesLoaded = true,
            today = LocalDate.of(2026, 4, 5),
            zoneId = ZoneId.of("Asia/Tokyo"),
        )

        assertTrue(window.isLoaded)
        assertEquals(LocalDate.of(2026, 4, 2), window.minDate)
        assertEquals(LocalDate.of(2026, 4, 5), window.maxDate)
        assertTrue(window.isSelectable)
    }

    @Test
    fun resolveArchiveDateWindow_blocksWhenEntriesAreNotLoaded() {
        val window = resolveArchiveDateWindow(
            groupCreatedAt = Instant.parse("2026-04-01T16:00:00Z"),
            latestRecordedDoseDate = null,
            areEntriesLoaded = false,
            today = LocalDate.of(2026, 4, 5),
            zoneId = ZoneId.of("Asia/Tokyo"),
        )

        assertFalse(window.isLoaded)
        assertNull(window.minDate)
        assertEquals(LocalDate.of(2026, 4, 5), window.maxDate)
        assertFalse(window.isSelectable)
    }

    @Test
    fun resolveArchiveDateWindow_blocksWhenLatestRecordedDoseIsAfterToday() {
        val window = resolveArchiveDateWindow(
            groupCreatedAt = Instant.parse("2026-04-01T00:00:00Z"),
            latestRecordedDoseDate = LocalDate.of(2026, 4, 8),
            areEntriesLoaded = true,
            today = LocalDate.of(2026, 4, 5),
            zoneId = ZoneId.of("UTC"),
        )

        assertTrue(window.isLoaded)
        assertEquals(LocalDate.of(2026, 4, 8), window.minDate)
        assertEquals(LocalDate.of(2026, 4, 5), window.maxDate)
        assertFalse(window.isSelectable)
    }
}
