package com.mkx.hrttracker.model.journal

/**
 * Background selection for the home hero date. Persisted in the existing nullable
 * heroBackgroundKey column:
 *
 * - null means the default Date color option
 * - "NONE" means explicitly no background
 * - PrideFlag.name means that pride flag wash
 */
sealed interface HeroBackground {
    val storageKey: String?

    data object DateColor : HeroBackground {
        override val storageKey: String? = null
    }

    data object None : HeroBackground {
        const val StorageKey = "NONE"
        override val storageKey: String = StorageKey
    }

    data class Flag(val flag: PrideFlag) : HeroBackground {
        override val storageKey: String = flag.name
    }

    companion object {
        fun fromStorageValue(value: String?): HeroBackground {
            if (value == null) return DateColor
            if (value == None.StorageKey) return None
            return PrideFlag.fromStorageValueOrNull(value)?.let(::Flag) ?: None
        }
    }
}
