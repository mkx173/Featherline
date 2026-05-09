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
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey.MAUVE
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
    val groupPrimaryColor = medicationGroupSeedColor(colorKey).harmonize(
        MaterialTheme.colorScheme.primary
    )
    val delta = medicationGroupLightenRatioDelta(colorKey)
    return if (darkTheme) {
        MaterialTheme.colorScheme.copy(
            primary = groupPrimaryColor.darken(1.2f),
            primaryContainer = groupPrimaryColor.darken(1.2f),
            onPrimaryContainer = groupPrimaryColor.lighten(4.15f + delta).desaturateHct()
        )
    } else {
        MaterialTheme.colorScheme.copy(
            primary = groupPrimaryColor.darken(1.2f),
            primaryContainer = groupPrimaryColor.lighten(4.0f + delta).desaturateHct(),
            onPrimaryContainer = groupPrimaryColor.darken(1.2f)
        )
    }
}

internal fun medicationGroupLightenRatioDelta(colorKey: MedicationGroupColorKey?): Float {
    return when (colorKey) {
        ROSE -> 0.3f
        CITRON -> 0.1f
        PLUM -> 0.25f
        CORAL -> -0.1f
        AMBER -> -0.1f
        else -> 0f
    }
}

internal fun medicationGroupSeedColor(colorKey: MedicationGroupColorKey?): Color {
    return when (colorKey) {
        null -> Color(0xFF5A6470)
        ROSE -> Color(0xFF8D4959)
        CORAL -> Color(0xFFB6584A)
        AMBER -> Color(0xFF9B7228)
        CITRON -> Color(0xFF7E7C2A)
        SAGE -> Color(0xFF4F7A55)
        TEAL -> Color(0xFF2E6E72)
        SKY -> Color(0xFF3F6FA8)
        INDIGO -> Color(0xFF5557A8)
        VIOLET -> Color(0xFF7C4FA0)
        PLUM -> Color(0xFF8E3F7A)
        MAUVE -> Color(0xFF75565C)
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