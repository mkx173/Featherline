package com.mkx.hrttracker.model.journal

/**
 * A pride flag a journal anchor can wear as its hero background. Persisted by [name] through
 * [HeroBackground.Flag]. Unknown or forward-compatible stored names decode to null via
 * [fromStorageValueOrNull], then [HeroBackground.fromStorageValue] treats them as explicit None
 * instead of crashing. [seeds] are the flag's distinct stripe colours as packed ARGB ints
 * (including neutrals) and are the single source of truth shared by the dialog swatches and the
 * hero bloom. Progress is intentionally excluded (its chevron does not fit a colour-wash model).
 */
enum class PrideFlag(val seeds: List<Int>) {
    TRANSGENDER(listOf(0xFF5BCEFA.toInt(), 0xFFF5A9B8.toInt(), 0xFFFFFFFF.toInt())),
    RAINBOW(
        listOf(
            0xFFE40303.toInt(), 0xFFFF8C00.toInt(), 0xFFFFED00.toInt(),
            0xFF008026.toInt(), 0xFF004DFF.toInt(), 0xFF750787.toInt(),
        ),
    ),
    BISEXUAL(listOf(0xFFD60270.toInt(), 0xFF9B4F96.toInt(), 0xFF0038A8.toInt())),
    PANSEXUAL(listOf(0xFFFF218C.toInt(), 0xFFFFD800.toInt(), 0xFF21B1FF.toInt())),
    NONBINARY(listOf(0xFFFCF434.toInt(), 0xFFFFFFFF.toInt(), 0xFF9C59D1.toInt(), 0xFF2C2C2C.toInt())),
    LESBIAN(
        listOf(
            0xFFD52D00.toInt(), 0xFFFF9A56.toInt(), 0xFFFFFFFF.toInt(),
            0xFFD362A4.toInt(), 0xFFA30262.toInt(),
        ),
    ),
    ASEXUAL(listOf(0xFF000000.toInt(), 0xFFA3A3A3.toInt(), 0xFFFFFFFF.toInt(), 0xFF800080.toInt())),
    GENDERFLUID(
        listOf(
            0xFFFF75A2.toInt(), 0xFFFFFFFF.toInt(), 0xFFBE18D6.toInt(),
            0xFF000000.toInt(), 0xFF333EBD.toInt(),
        ),
    ),
    AGENDER(listOf(0xFF000000.toInt(), 0xFFB9B9B9.toInt(), 0xFFFFFFFF.toInt(), 0xFFB8F483.toInt()));

    companion object {
        fun fromStorageValueOrNull(value: String?): PrideFlag? =
            entries.firstOrNull { it.name == value }
    }
}
