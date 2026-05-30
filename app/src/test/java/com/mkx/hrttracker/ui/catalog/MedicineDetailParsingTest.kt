package com.mkx.hrttracker.ui.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MedicineDetailParsingTest {

    @Test
    fun toPositiveDoubleOrThrow_acceptsDotAndCommaDecimalSeparators() {
        assertEquals(1.5, "1.5".toPositiveDoubleOrThrow(), 0.0)
        assertEquals(1.5, "1,5".toPositiveDoubleOrThrow(), 0.0)
    }

    @Test
    fun toPositiveDoubleOrThrow_rejectsNonPositiveValues() {
        assertThrows(IllegalArgumentException::class.java) {
            "0".toPositiveDoubleOrThrow()
        }
        assertThrows(IllegalArgumentException::class.java) {
            "-1,5".toPositiveDoubleOrThrow()
        }
    }
}
