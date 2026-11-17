package com.mkx.hrttracker.model.personalization

import java.time.Instant

enum class WeightUnit(val kgPerUnit: Double) {
    KILOGRAMS(kgPerUnit = 1.0),
    POUNDS(kgPerUnit = 0.45359237);

    fun toKg(value: Double): Double = value * kgPerUnit

    companion object {
        fun fromStorageValue(value: String?): WeightUnit {
            return entries.firstOrNull { it.name == value } ?: KILOGRAMS
        }
    }
}

data class UserProfile(
    val weightKg: Double? = null,
    val weightOriginalValue: Double? = null,
    val weightOriginalUnit: WeightUnit = WeightUnit.KILOGRAMS,
    val updatedAt: Instant? = null,
    val weightMeasuredAt: Instant? = null,
) {
    /**
     * The weight is a Health Connect reading, from the stored row alone, so it
     * holds after a restart before the first sync: a typed weight is dated when
     * it's saved (setWeight), an adopted one with its reading's time, and the
     * unit switch keeps that date. A weight typed before weights were dated has
     * none. An adopted reading can't share its sync's time: the read ends at that
     * time, exclusive. The rule is on UserProfileEntity's fields: a write that
     * bumps updatedAt on a typed weight would credit Health Connect.
     */
    val isHealthConnectReading: Boolean
        get() = weightKg != null && weightMeasuredAt != null && weightMeasuredAt != updatedAt
}
