package com.mkx.hrttracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import com.materialkolor.ktx.darken
import com.materialkolor.ktx.harmonize
import com.materialkolor.ktx.lighten
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.AMBER
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.CITRON
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.CORAL
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.INDIGO
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.PLUM
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.ROSE
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.SAGE
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.SKY
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.TEAL
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.VIOLET

@Composable
fun rememberMedicationGroupColorScheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorKey: MedicationGroupColorKey?
): ColorScheme {
    val groupPrimaryColor = medicationGroupSeedColor(colorKey)
    val delta = medicationGroupLightenRatioDelta(colorKey)
    return if (darkTheme) {
        MaterialTheme.colorScheme.copy(
            primary = groupPrimaryColor.darken(1.2f).desaturateHct(0.8),
            primaryContainer = groupPrimaryColor.darken(1.2f).desaturateHct(0.8),
            onPrimaryContainer = groupPrimaryColor.lighten(4.15f + delta)
        )
    } else {
        MaterialTheme.colorScheme.copy(
            primary = groupPrimaryColor.darken(1.3f),
            primaryContainer = groupPrimaryColor.lighten(4.0f + delta).desaturateHct(),
            onPrimaryContainer = groupPrimaryColor.darken(1.3f)
        )
    }
}

internal fun medicationGroupLightenRatioDelta(colorKey: MedicationGroupColorKey?): Float {
    return when (colorKey) {
        ROSE -> 1.2f
        CORAL -> -0.6f
        AMBER -> -0.9f
        CITRON -> -0.3f
        TEAL -> 0.2f
        SKY -> -0.6f
        INDIGO -> 0.5f
        PLUM -> 0.3f
        else -> 0f
    }
}

internal fun medicationGroupSeedColor(colorKey: MedicationGroupColorKey?): Color {
    return when (colorKey) {
        null -> Color(0xFF5A6470)
        ROSE -> Color(0xFF95414A)
        CORAL -> Color(0xFFBD5C28)
        AMBER -> Color(0xFF9A8518)
        CITRON -> Color(0xFF6E8320)
        SAGE -> Color(0xFF4F7A55)
        TEAL -> Color(0xFF2E6E72)
        SKY -> Color(0xFF2D80B0)
        INDIGO -> Color(0xFF4D55AC)
        VIOLET -> Color(0xFF8C4AA8)
        PLUM -> Color(0xFF9A3D78)
    }
}

fun Color.desaturateHct(factor: Double = 0.62): Color {
    val source = Hct.fromInt(toArgb())
    val safeFactor = factor.coerceIn(0.0, 1.0)

    return Color(
        Hct.from(
            source.hue,
            source.chroma * safeFactor,
            source.tone,
        ).toInt()
    )
}