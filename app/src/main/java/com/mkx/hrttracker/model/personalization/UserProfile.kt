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
)
