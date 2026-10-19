package com.mkx.hrttracker.data.backup

import java.security.SecureRandom

// 80-bit recovery key as 16 Crockford Base32 symbols. The canonical form (what
// the keyring's recovery slot wraps under) is the bare 16 upper-case symbols;
// the dashed form is display only.
object RecoveryKey {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val KEY_LENGTH_BYTES = 10
    private const val SYMBOL_COUNT = 16
    private const val GROUP_LENGTH = 4

    fun generate(secureRandom: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(KEY_LENGTH_BYTES)
        secureRandom.nextBytes(bytes)
        return try {
            buildString(SYMBOL_COUNT) {
                var buffer = 0
                var bufferedBits = 0
                for (byte in bytes) {
                    buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
                    bufferedBits += 8
                    while (bufferedBits >= 5) {
                        bufferedBits -= 5
                        append(ALPHABET[(buffer shr bufferedBits) and 0x1F])
                    }
                }
            }
        } finally {
            bytes.fill(0)
        }
    }

    fun format(key: String): String = key.chunked(GROUP_LENGTH).joinToString("-")

    // Ignores whitespace, dashes and case, and reads O as 0 and I or L as 1.
    // Only ASCII letters are upper-cased so look-alikes such as the dotless ı
    // stay invalid instead of folding into I.
    fun parse(input: String): String? {
        val symbols = buildString(SYMBOL_COUNT) {
            for (char in input) {
                if (char.isWhitespace() || char == '-') continue
                append(
                    when (val upper = if (char in 'a'..'z') char.uppercaseChar() else char) {
                        'O' -> '0'
                        'I', 'L' -> '1'
                        else -> upper
                    }
                )
            }
        }
        return symbols.takeIf { it.length == SYMBOL_COUNT && it.all { symbol -> symbol in ALPHABET } }
    }
}
