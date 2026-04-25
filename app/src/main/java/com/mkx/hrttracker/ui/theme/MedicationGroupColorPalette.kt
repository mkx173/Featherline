package com.mkx.hrttracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.ktx.harmonize
import com.materialkolor.rememberDynamicColorScheme
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey

@Composable
fun rememberMedicationGroupColorScheme(colorKey: MedicationGroupColorKey?): ColorScheme {
    val appPrimaryColor = MaterialTheme.colorScheme.primary
    return rememberDynamicColorScheme(
        seedColor = medicationGroupSeedColor(colorKey).harmonize(appPrimaryColor),
        isDark = isSystemInDarkTheme(),
    )
}

internal fun medicationGroupSeedColor(colorKey: MedicationGroupColorKey?): Color {
    return when (colorKey) {
        null -> Color(0xFF6E7482)
        MedicationGroupColorKey.ROSE -> Color(0xFFB14C62)
        MedicationGroupColorKey.ORCHID -> Color(0xFF8F56B8)
        MedicationGroupColorKey.INDIGO -> Color(0xFF4B63D2)
        MedicationGroupColorKey.TEAL -> Color(0xFF006D6B)
        MedicationGroupColorKey.EMERALD -> Color(0xFF3A6A2D)
        MedicationGroupColorKey.AMBER -> Color(0xFF8B5E10)
        MedicationGroupColorKey.CORAL -> Color(0xFFBA4F38)
        MedicationGroupColorKey.SLATE -> Color(0xFF586273)
    }
}
