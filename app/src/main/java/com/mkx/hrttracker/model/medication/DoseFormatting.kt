package com.mkx.hrttracker.model.medication

import java.util.Locale

fun Double.formatDose(locale: Locale): String {
    return if (this % 1.0 == 0.0) {
        String.format(locale, "%.0f", this)
    } else {
        String.format(locale, "%.2f", this)
    }
}
