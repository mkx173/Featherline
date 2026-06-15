package com.mkx.hrttracker.ui.plan

import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.PlanDayScheduleEntry
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.ui.medication.MedicationLogScheduleOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class PlanScreenComponentsTest {
    @Test
    fun selectedDayRowIndicatorSizes_useFixedSlotWithSmallerDownloadGlyph() {
        assertEquals(14.dp, SelectedDayRowIndicatorSlotSize)
        assertEquals(13.dp, selectedDayRowIndicatorGlyphSize(R.drawable.ic_download))
        assertEquals(14.dp, selectedDayRowIndicatorGlyphSize(R.drawable.ic_archive))
        assertEquals(13.dp, SelectedDayCrossZoneIndicatorGlyphSize)
    }

    @Test
    fun selectedDayRowImportedIndicatorIconRes_usesDownloadForImportedManualRows() {
        val entry = testMedicationLogEntry(
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 5, 20, 9, 0)),
        ).copy(
            importSourceApp = "transmtf",
            importExternalId = "dose-1",
        )
        val row = SelectedDayRowModel.Unplanned(
            entry = entry,
            sortTime = LocalTime.of(9, 0),
        )

        assertEquals(R.drawable.ic_download, selectedDayRowImportedIndicatorIconRes(row))
    }

    @Test
    fun selectedDayRowImportedIndicatorIconRes_usesDownloadForImportedScheduledRows() {
        val entry = PlanDayScheduleEntry(
            groupUuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            groupName = "Imported group",
            groupColorKey = MedicationGroupColorKey.ROSE,
            scheduledTime = LocalTime.of(9, 0),
            medication = testMedicationGroupMedication(),
            fulfillingEntryUuids = listOf(UUID.fromString("22222222-2222-2222-2222-222222222222")),
            isImportedRecord = true,
            isFulfilled = true,
            isDueSoon = false,
            isPastDue = false,
        )
        val row = SelectedDayRowModel.Scheduled(entry)

        assertEquals(R.drawable.ic_download, selectedDayRowImportedIndicatorIconRes(row))
    }

    @Test
    fun selectedDayRowImportedIndicatorIconRes_omitsIconForLocalManualRows() {
        val entry = testMedicationLogEntry(
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 5, 20, 9, 0)),
        )
        val row = SelectedDayRowModel.Unplanned(
            entry = entry,
            sortTime = LocalTime.of(9, 0),
        )

        assertNull(selectedDayRowImportedIndicatorIconRes(row))
    }

    @Test
    fun stockPreviewNumberUsesProvidedLocaleWithoutFixedTrailingZeros() {
        assertEquals("1,5", stockPreviewNumber(1.5, Locale.GERMANY))
        assertEquals("1", stockPreviewNumber(1.0, Locale.GERMANY))
    }

    @Test
    fun selectedDayLoggedDayOffsetDays_returnsNullForSameDayLog() {
        assertNull(
            selectedDayLoggedDayOffsetDays(
                scheduledDate = LocalDate.of(2026, 4, 18),
                loggedAt = LocalDateTime.of(2026, 4, 18, 23, 30)
            )
        )
    }

    @Test
    fun selectedDayLoggedDayOffsetDays_returnsSignedDayOffsetForDifferentDayLog() {
        assertEquals(
            1L,
            selectedDayLoggedDayOffsetDays(
                scheduledDate = LocalDate.of(2026, 4, 18),
                loggedAt = LocalDateTime.of(2026, 4, 19, 0, 15)
            )
        )
        assertEquals(
            -1L,
            selectedDayLoggedDayOffsetDays(
                scheduledDate = LocalDate.of(2026, 4, 18),
                loggedAt = LocalDateTime.of(2026, 4, 17, 23, 45)
            )
        )
    }

    @Test
    fun selectedDayLoggedTimeLabel_appendsDayOffsetAfterLoggedTime() {
        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

        assertEquals(
            "01:05 AM (+1)",
            selectedDayLoggedTimeLabel(
                loggedAt = LocalDateTime.of(2026, 4, 19, 1, 5),
                fallbackScheduledTime = LocalTime.of(23, 0),
                timeFormatter = timeFormatter,
                loggedDayOffsetText = "+1"
            )
        )
    }

    @Test
    fun selectedDayLoggedTimeLabel_omitsDayOffsetWhenSameDay() {
        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

        assertEquals(
            "09:30 PM",
            selectedDayLoggedTimeLabel(
                loggedAt = LocalDateTime.of(2026, 4, 18, 21, 30),
                fallbackScheduledTime = LocalTime.of(21, 0),
                timeFormatter = timeFormatter,
                loggedDayOffsetText = null
            )
        )
    }

    @Test
    fun selectedDayHeaderCountLabel_omits_empty_count() {
        assertNull(
            selectedDayHeaderCountLabel(
                date = LocalDate.of(2026, 4, 18),
                today = LocalDate.of(2026, 4, 18),
                completedScheduledCount = 0,
                scheduledCount = 0,
                offPlanCount = 0
            )
        )
    }

    @Test
    fun selectedDayHeaderCountLabel_formats_planned_count() {
        assertEquals(
            "1/4",
            selectedDayHeaderCountLabel(
                date = LocalDate.of(2026, 4, 18),
                today = LocalDate.of(2026, 4, 18),
                completedScheduledCount = 1,
                scheduledCount = 4,
                offPlanCount = 0
            )
        )
    }

    @Test
    fun selectedDayHeaderCountLabel_formats_manual_only_count() {
        assertEquals(
            "(2)",
            selectedDayHeaderCountLabel(
                date = LocalDate.of(2026, 4, 18),
                today = LocalDate.of(2026, 4, 18),
                completedScheduledCount = 0,
                scheduledCount = 0,
                offPlanCount = 2
            )
        )
    }

    @Test
    fun selectedDayHeaderCountLabel_formats_planned_and_manual_count() {
        assertEquals(
            "1/4 (2)",
            selectedDayHeaderCountLabel(
                date = LocalDate.of(2026, 4, 18),
                today = LocalDate.of(2026, 4, 18),
                completedScheduledCount = 1,
                scheduledCount = 4,
                offPlanCount = 2
            )
        )
    }

    @Test
    fun selectedDayScheduleOffset_omitsSubHourDelta() {
        val scheduledFor = LocalDateTime.of(2026, 4, 18, 9, 0)

        assertNull(
            selectedDayScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.plusMinutes(59),
            )
        )
        assertNull(
            selectedDayScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.minusMinutes(59),
            )
        )
        assertNull(
            selectedDayScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.plusHours(1).minusSeconds(1),
            )
        )
        assertNull(
            selectedDayScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.minusHours(1).plusSeconds(1),
            )
        )
    }

    @Test
    fun selectedDayScheduleOffset_keepsExactHourDelta() {
        val scheduledFor = LocalDateTime.of(2026, 4, 18, 9, 0)

        assertEquals(
            MedicationLogScheduleOffset(
                labelRes = R.string.medication_editor_schedule_offset_hours_later,
                value = 1,
            ),
            selectedDayScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.plusHours(1),
            )
        )
        assertEquals(
            MedicationLogScheduleOffset(
                labelRes = R.string.medication_editor_schedule_offset_hours_earlier,
                value = 1,
            ),
            selectedDayScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.minusHours(1),
            )
        )
    }
}
