package com.mkx.hrttracker.ui.components

import androidx.annotation.StringRes
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.personalization.WeightUnit

@get:StringRes
val WeightUnit.shortLabelRes: Int
    get() = when (this) {
        WeightUnit.KILOGRAMS -> R.string.personalization_weight_unit_kg_short
        WeightUnit.POUNDS -> R.string.personalization_weight_unit_lb_short
    }
