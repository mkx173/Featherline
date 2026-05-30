package com.mkx.hrttracker.ui.catalog.stock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenContainerEditDialogTest {

    @Test
    fun parseOpenContainerAmount_acceptsDotAndCommaDecimalSeparators() {
        assertEquals(2.4, requireNotNull(parseOpenContainerAmount("2.4")), 0.0)
        assertEquals(2.4, requireNotNull(parseOpenContainerAmount("2,4")), 0.0)
    }

    @Test
    fun parseOpenContainerAmount_returnsNullForMalformedInput() {
        assertNull(parseOpenContainerAmount(""))
        assertNull(parseOpenContainerAmount("abc"))
    }
}
