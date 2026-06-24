package com.mkx.hrttracker.ui.journal

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

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
}
