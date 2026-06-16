package com.mkx.hrttracker.model.journal

import java.time.LocalDate

/** A freeform plain-text entry for one day; one note per date. */
data class Note(
    val id: String,
    val date: LocalDate,
    val text: String,
)
