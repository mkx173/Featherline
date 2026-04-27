package com.mkx.hrttracker.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CjkTextOffsetTest {
    @Test
    fun containsCjkCharacters_returnsFalse_forLatinText() {
        assertFalse("Dose".containsCjkCharacters())
    }

    @Test
    fun containsCjkCharacters_returnsTrue_forHanText() {
        assertTrue("剂量".containsCjkCharacters())
    }

    @Test
    fun containsCjkCharacters_returnsTrue_forMixedText() {
        assertTrue("Estradiol 雌二醇".containsCjkCharacters())
    }

    @Test
    fun containsCjkCharacters_returnsTrue_forJapaneseKana() {
        assertTrue("カレンダー".containsCjkCharacters())
    }

    @Test
    fun containsCjkCharacters_returnsTrue_forHangul() {
        assertTrue("복용 기록".containsCjkCharacters())
    }
}
