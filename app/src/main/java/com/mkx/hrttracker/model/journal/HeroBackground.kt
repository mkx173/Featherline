package com.mkx.hrttracker.model.journal

/**
 * Background selection for the home hero date. Persisted in the existing nullable
 * heroBackgroundKey column:
 *
 * - null means the default no-background option
 * - "DATE_COLOR" means the date's palette color wash
 * - PrideFlag.name means that pride flag wash
 *
 * Legacy "NONE" rows also decode to [None]; they predate None becoming the null-backed default.
 */
sealed interface HeroBackground {
    val storageKey: String?

    data object None : HeroBackground {
        override val storageKey: String? = null
    }

    data object DateColor : HeroBackground {
        const val StorageKey = "DATE_COLOR"
        override val storageKey: String = StorageKey
    }

    data class Flag(val flag: PrideFlag) : HeroBackground {
        override val storageKey: String = flag.name
    }

    companion object {
        const val LegacyNoneStorageKey = "NONE"

        fun fromStorageValue(value: String?): HeroBackground {
            if (value == null || value == LegacyNoneStorageKey) return None
            if (value == DateColor.StorageKey) return DateColor
            return PrideFlag.fromStorageValueOrNull(value)?.let(::Flag) ?: None
        }
    }
}
