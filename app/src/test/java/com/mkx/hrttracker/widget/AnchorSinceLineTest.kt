package com.mkx.hrttracker.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class AnchorSinceLineTest {
    private val date = LocalDate.of(2026, 5, 1)

    // Intent: the ladder exists so the widget can always show SOME date rather than
    // clipping - each rung trades wording for width.
    @Test
    fun `english candidates degrade from full sentence to two digit year`() {
        val candidates = sinceLineCandidates("Since %1\$s", date, Locale.US)
        assertEquals("Since May 1, 2026", candidates[0])
        assertEquals("May 1, 2026", candidates[1])
        // en-US SHORT is already 2-digit ("5/1/26"), so rungs 3 and 4 dedupe.
        assertEquals(listOf("Since May 1, 2026", "May 1, 2026", "5/1/26"), candidates)
    }

    @Test
    fun `chinese candidates keep the app's 开始于 prefix on the first rung`() {
        val candidates = sinceLineCandidates("开始于 %1\$s", date, Locale.SIMPLIFIED_CHINESE)
        assertEquals("开始于 2026年5月1日", candidates[0])
        assertEquals("2026年5月1日", candidates[1])
        // zh SHORT is "2026/5/1"; the 2-digit rung shortens the year.
        assertEquals("2026/5/1", candidates[2])
        assertEquals("26/5/1", candidates[3])
    }

    @Test
    fun `fit picks the longest candidate that fits`() {
        val picked = fitSinceLine(
            candidates = listOf("aaaaaaaaaa", "aaaaa", "aa"),
            maxWidthPx = 6f,
            measure = { it.length.toFloat() },
        )
        assertEquals("aaaaa", picked)
    }

    @Test
    fun `fit falls back to the shortest candidate when nothing fits`() {
        // Intent: never return an empty line - worst case we render the tightest form
        // and let Glance ellipsize.
        val picked = fitSinceLine(
            candidates = listOf("aaaaaaaaaa", "aaaaa", "aa"),
            maxWidthPx = 1f,
            measure = { it.length.toFloat() },
        )
        assertEquals("aa", picked)
    }
}
