package com.mkx.hrttracker.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class RecoveryKeyTest {
    @Test
    fun generated_keys_are_16_canonical_symbols_that_parse_to_themselves() {
        val secureRandom = SecureRandom()
        repeat(500) {
            val key = RecoveryKey.generate(secureRandom)
            assertEquals(16, key.length)
            assertTrue(key, key.all { it in "0123456789ABCDEFGHJKMNPQRSTVWXYZ" })
            assertEquals(key, RecoveryKey.parse(key))
            assertEquals(key, RecoveryKey.parse(RecoveryKey.format(key)))
        }
    }

    @Test
    fun generate_encodes_bytes_as_crockford_base32() {
        val allOnes = object : SecureRandom() {
            override fun nextBytes(bytes: ByteArray) = bytes.fill(0xFF.toByte())
        }
        val allZeros = object : SecureRandom() {
            override fun nextBytes(bytes: ByteArray) = bytes.fill(0)
        }

        assertEquals("ZZZZZZZZZZZZZZZZ", RecoveryKey.generate(allOnes))
        assertEquals("0000000000000000", RecoveryKey.generate(allZeros))
    }

    @Test
    fun format_groups_symbols_in_fours() {
        assertEquals("K7QM-3XRT-9F2B-HW4D", RecoveryKey.format("K7QM3XRT9F2BHW4D"))
    }

    @Test
    fun parse_ignores_case_spaces_and_dashes() {
        assertEquals("K7QM3XRT9F2BHW4D", RecoveryKey.parse("k7qm-3xrt-9f2b-hw4d"))
        assertEquals("K7QM3XRT9F2BHW4D", RecoveryKey.parse("  K7QM 3XRT\t9F2B\nHW4D "))
        assertEquals("K7QM3XRT9F2BHW4D", RecoveryKey.parse("K7QM--3XRT-9F2B-HW4D-"))
    }

    @Test
    fun parse_reads_o_as_zero_and_i_or_l_as_one() {
        assertEquals("0011000000000000", RecoveryKey.parse("oOiL-0000-0000-0000"))
        assertEquals("1111000000000000", RecoveryKey.parse("IiLl-OOOO-oooo-0000"))
    }

    @Test
    fun parse_rejects_u_wrong_lengths_and_other_characters() {
        assertNull(RecoveryKey.parse("U7QM-3XRT-9F2B-HW4D"))
        assertNull(RecoveryKey.parse("u7qm-3xrt-9f2b-hw4d"))
        assertNull(RecoveryKey.parse("K7QM-3XRT-9F2B-HW4"))
        assertNull(RecoveryKey.parse("K7QM-3XRT-9F2B-HW4DX"))
        assertNull(RecoveryKey.parse("K7QM_3XRT-9F2B-HW4D"))
        assertNull(RecoveryKey.parse("K7QM-3XRT-9F2B-HW4!"))
        assertNull(RecoveryKey.parse("ı7QM-3XRT-9F2B-HW4D"))
        assertNull(RecoveryKey.parse(""))
    }

    @Test
    fun parse_folds_full_width_symbols_and_skips_unicode_dashes_and_format_characters() {
        // Full-width letters and digits from a CJK keyboard.
        assertEquals("K7QM3XRT9F2BHW4D", RecoveryKey.parse("\uFF2B\uFF17\uFF31\uFF2D-3XRT-9F2B-HW4D"))
        assertEquals("K7QM3XRT9F2BHW4D", RecoveryKey.parse("ｋ７ｑｍ－３ｘｒｔ－９ｆ２ｂ－ｈｗ４ｄ"))
        // En dash, em dash, hyphen, non-breaking hyphen and the minus sign.
        assertEquals("K7QM3XRT9F2BHW4D", RecoveryKey.parse("K7QM\u20133XRT\u20149F2B\u2010HW4D\u2011\u2212"))
        // Zero-width space, zero-width joiner, BOM and a no-break space.
        assertEquals("K7QM3XRT9F2BHW4D", RecoveryKey.parse("\uFEFFK7QM\u200B3XRT\u200D9F2B\u00A0HW4D"))
    }

    @Test
    fun parse_still_rejects_u_and_wrong_lengths_after_folding() {
        assertNull(RecoveryKey.parse("\uFF35" + "7QM-3XRT-9F2B-HW4D"))
        assertNull(RecoveryKey.parse("K7QM\u20133XRT\u20149F2B\u2010HW4"))
        assertNull(RecoveryKey.parse("K7QM\u200B3XRT\u200B9F2B\u200BHW4DX"))
    }

    @Test
    fun symbols_keeps_invalid_characters_for_parse_to_reject() {
        assertEquals("K7QMU", RecoveryKey.symbols("k7qm-u"))
        assertEquals("K7QM!", RecoveryKey.symbols("k7qm !"))
    }
}
