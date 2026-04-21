package com.mkx.hrttracker.model.personalization

import androidx.annotation.StringRes
import com.mkx.hrttracker.R
import java.time.Instant

enum class WeightUnit(
    @get:StringRes val labelRes: Int,
    @get:StringRes val shortLabelRes: Int,
    val kgPerUnit: Double,
) {
    KILOGRAMS(
        labelRes = R.string.personalization_weight_unit_kg,
        shortLabelRes = R.string.personalization_weight_unit_kg_short,
        kgPerUnit = 1.0
    ),
    POUNDS(
        labelRes = R.string.personalization_weight_unit_lb,
        shortLabelRes = R.string.personalization_weight_unit_lb_short,
        kgPerUnit = 0.45359237
    );

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
