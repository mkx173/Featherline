package com.mkx.hrttracker.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class AppColorSchemeTest {

    @Test
    fun adaptiveOff_returnsDefaultSeed_withoutReadingSystem() {
        val context = mockk<Context>()
        val seed = resolveSeedColor(context, adaptiveEnabled = false, sdkInt = 34)
        assertEquals(DefaultSeedColor, seed)
        verify(exactly = 0) { context.getColor(any()) }
    }

    @Test
    fun adaptiveOn_belowS_returnsDefaultSeed_withoutReadingSystem() {
        val context = mockk<Context>()
        val seed = resolveSeedColor(context, adaptiveEnabled = true, sdkInt = 30)
        assertEquals(DefaultSeedColor, seed)
        verify(exactly = 0) { context.getColor(any()) }
    }

    @Test
    fun adaptiveOn_sOrAbove_readsSystemAccent1_500() {
        val context = mockk<Context>()
        every { context.getColor(any()) } returns 0xFF112233.toInt()
        val seed = resolveSeedColor(context, adaptiveEnabled = true, sdkInt = 31)
        assertEquals(0xFF112233.toInt(), seed.toArgb())
        verify { context.getColor(android.R.color.system_accent1_500) }
    }
}
