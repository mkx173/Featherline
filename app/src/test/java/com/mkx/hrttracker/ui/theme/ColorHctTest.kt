package com.mkx.hrttracker.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorHctTest {
    @Test
    fun atTone_movesColorToRequestedHctTone() {
        val color = Color(0xFF6476A5).atTone(12.0)

        assertEquals(12.0, color.hctTone(), 0.5)
    }

    @Test
    fun desaturateHct_scalesChromaAndKeepsTone() {
        val source = Color(0xFF6476A5)
        val desaturated = source.desaturateHct(factor = 0.5)
        val sourceHct = Hct.fromInt(source.toArgb())
        val desaturatedHct = Hct.fromInt(desaturated.toArgb())

        assertEquals(sourceHct.tone, desaturatedHct.tone, 0.5)
        assertEquals(sourceHct.chroma * 0.5, desaturatedHct.chroma, 1.0)
    }

    @Test
    fun amoledContainers_compressesElevatedContainerRampTowardBlack() {
        val source = darkColorScheme(
            background = Color(0xFF101010),
            surface = Color(0xFF111111),
            onSurface = Color(0xFFEFEFEF),
            surfaceDim = Color(0xFF151515),
            surfaceContainerLowest = Color(0xFF181818),
            surfaceContainerLow = Color(0xFF22191B),
            surfaceContainer = Color(0xFF261D1F),
            surfaceContainerHigh = Color(0xFF312829),
            surfaceContainerHighest = Color(0xFF3C3234),
            surfaceBright = Color(0xFF463B3C),
        )

        val amoled = source.amoledContainers()

        assertEquals(source.background, amoled.background)
        assertEquals(source.surface, amoled.surface)
        assertEquals(source.onSurface, amoled.onSurface)
        assertEquals(Color.Black, amoled.surfaceDim)
        assertEquals(Color.Black, amoled.surfaceContainerLowest)
        assertEquals(4.0, amoled.surfaceContainerLow.hctTone(), 0.5)
        assertEquals(6.0, amoled.surfaceContainer.hctTone(), 0.5)
        assertEquals(9.0, amoled.surfaceContainerHigh.hctTone(), 0.5)
        assertEquals(12.0, amoled.surfaceContainerHighest.hctTone(), 0.5)
        assertEquals(14.0, amoled.surfaceBright.hctTone(), 0.5)
    }

    private fun Color.hctTone(): Double = Hct.fromInt(toArgb()).tone
}
