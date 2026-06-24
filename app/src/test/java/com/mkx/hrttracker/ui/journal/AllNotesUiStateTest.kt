package com.mkx.hrttracker.ui.journal

import com.mkx.hrttracker.model.journal.Note
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AllNotesUiStateTest {
    @Test
    fun toggleNoteSelection_addsThenRemovesDate() {
        val date = LocalDate.of(2026, 6, 1)

        val selected = toggleNoteSelection(current = emptySet(), date = date)
        val deselected = toggleNoteSelection(current = selected, date = date)

        assertEquals(setOf(date), selected)
        assertEquals(emptySet<LocalDate>(), deselected)
    }

    @Test
    fun selectAllNoteDates_unionsWithoutDroppingExistingSelection() {
        val alreadySelected = LocalDate.of(2026, 5, 20)
        val displayed = LocalDate.of(2026, 6, 1)

        val selection = selectAllNoteDates(
            current = setOf(alreadySelected),
            dates = setOf(displayed),
        )

        assertEquals(setOf(alreadySelected, displayed), selection)
    }

    @Test
    fun reconcileNoteSelection_returnsEmptyWhenSelectionIsEmpty() {
        val existingDate = LocalDate.of(2026, 6, 1)

        val selection = reconcileNoteSelection(
            selectedDates = emptySet(),
            monthGroups = listOf(
                MonthGroupUiState(
                    month = YearMonth.of(2026, 6),
                    notes = listOf(note(id = "june-1", date = existingDate)),
                )
            ),
        )

        assertEquals(emptySet<LocalDate>(), selection)
    }

    @Test
    fun reconcileNoteSelection_dropsDatesWithoutExistingNotes() {
        val existingDate = LocalDate.of(2026, 6, 1)
        val vanishedDate = LocalDate.of(2026, 5, 20)

        val selection = reconcileNoteSelection(
            selectedDates = setOf(existingDate, vanishedDate),
            monthGroups = listOf(
                MonthGroupUiState(
                    month = YearMonth.of(2026, 6),
                    notes = listOf(note(id = "june-1", date = existingDate)),
                )
            ),
        )

        assertEquals(setOf(existingDate), selection)
    }

    private fun note(id: String, date: LocalDate): Note = Note(
        id = id,
        date = date,
        text = id,
    )
}
