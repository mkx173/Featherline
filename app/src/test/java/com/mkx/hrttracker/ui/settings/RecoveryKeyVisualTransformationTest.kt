package com.mkx.hrttracker.ui.settings

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryKeyVisualTransformationTest {
    @Test
    fun groups_symbols_in_fours() {
        assertEquals("K7QM-3XRT-9F2B-HW4D", transform("K7QM3XRT9F2BHW4D").text.text)
        assertEquals("K7QM-3", transform("K7QM3").text.text)
        assertEquals("K7QM", transform("K7QM").text.text)
        assertEquals("", transform("").text.text)
    }

    @Test
    fun offsets_skip_the_dashes_both_ways_at_every_length() {
        for (length in 0..17) {
            val original = "A".repeat(length)
            val transformed = transform(original)
            val shown = transformed.text.text
            val mapping = transformed.offsetMapping
            for (offset in 0..length) {
                val mapped = mapping.originalToTransformed(offset)
                assertEquals(
                    "length $length offset $offset",
                    shown.take(mapped).count { it != '-' },
                    offset,
                )
                // A cursor after a full group sits before its dash, never after it.
                if (mapped > 0) assertEquals('A', shown[mapped - 1])
                assertEquals(offset, mapping.transformedToOriginal(mapped))
            }
            for (offset in 0..shown.length) {
                assertEquals(
                    "length $length transformed $offset",
                    shown.take(offset).count { it != '-' },
                    mapping.transformedToOriginal(offset),
                )
            }
        }
    }

    private fun transform(text: String) = RecoveryKeyVisualTransformation.filter(AnnotatedString(text))
}
