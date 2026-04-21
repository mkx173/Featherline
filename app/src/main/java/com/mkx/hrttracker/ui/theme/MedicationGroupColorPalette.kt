package com.mkx.hrttracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey

@Immutable
data class MedicationGroupPaletteColors(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
)

@Composable
fun medicationGroupPaletteColors(colorKey: MedicationGroupColorKey?): MedicationGroupPaletteColors {
    if (colorKey == null) {
        return MedicationGroupPaletteColors(
            accent = outlineLight,
            container = surfaceContainerHighestLight,
            onContainer = onSurfaceVariantLight,
        )
    }

    return if (isSystemInDarkTheme()) {
        darkMedicationGroupPalette(colorKey)
    } else {
        lightMedicationGroupPalette(colorKey)
    }
}

private fun lightMedicationGroupPalette(colorKey: MedicationGroupColorKey): MedicationGroupPaletteColors {
    return when (colorKey) {
        MedicationGroupColorKey.ROSE -> MedicationGroupPaletteColors(
            accent = Color(0xFFB14C62),
            container = Color(0xFFFFD9DF),
            onContainer = Color(0xFF3F0017),
        )

        MedicationGroupColorKey.ORCHID -> MedicationGroupPaletteColors(
            accent = Color(0xFF8F56B8),
            container = Color(0xFFF0DBFF),
            onContainer = Color(0xFF2F1146),
        )

        MedicationGroupColorKey.INDIGO -> MedicationGroupPaletteColors(
            accent = Color(0xFF4B63D2),
            container = Color(0xFFDCE2FF),
            onContainer = Color(0xFF001A4A),
        )

        MedicationGroupColorKey.TEAL -> MedicationGroupPaletteColors(
            accent = Color(0xFF006D6B),
            container = Color(0xFFB8F1EC),
            onContainer = Color(0xFF00201F),
        )

        MedicationGroupColorKey.EMERALD -> MedicationGroupPaletteColors(
            accent = Color(0xFF3A6A2D),
            container = Color(0xFFCDEFC7),
            onContainer = Color(0xFF0F2009),
        )

        MedicationGroupColorKey.AMBER -> MedicationGroupPaletteColors(
            accent = Color(0xFF8B5E10),
            container = Color(0xFFFFDDAF),
            onContainer = Color(0xFF2D1700),
        )

        MedicationGroupColorKey.CORAL -> MedicationGroupPaletteColors(
            accent = Color(0xFFBA4F38),
            container = Color(0xFFFFDAD2),
            onContainer = Color(0xFF3A0C00),
        )

        MedicationGroupColorKey.SLATE -> MedicationGroupPaletteColors(
            accent = Color(0xFF586273),
            container = Color(0xFFDDE5F3),
            onContainer = Color(0xFF121C28),
        )
    }
}

private fun darkMedicationGroupPalette(colorKey: MedicationGroupColorKey): MedicationGroupPaletteColors {
    return when (colorKey) {
        MedicationGroupColorKey.ROSE -> MedicationGroupPaletteColors(
            accent = Color(0xFFFFB2C0),
            container = Color(0xFF5F2333),
            onContainer = Color(0xFFFFD9DF),
        )

        MedicationGroupColorKey.ORCHID -> MedicationGroupPaletteColors(
            accent = Color(0xFFE4BCFF),
            container = Color(0xFF573673),
            onContainer = Color(0xFFF0DBFF),
        )

        MedicationGroupColorKey.INDIGO -> MedicationGroupPaletteColors(
            accent = Color(0xFFB8C4FF),
            container = Color(0xFF21387B),
            onContainer = Color(0xFFDCE2FF),
        )

        MedicationGroupColorKey.TEAL -> MedicationGroupPaletteColors(
            accent = Color(0xFF94D6D0),
            container = Color(0xFF004F4D),
            onContainer = Color(0xFFB8F1EC),
        )

        MedicationGroupColorKey.EMERALD -> MedicationGroupPaletteColors(
            accent = Color(0xFFB2DFA9),
            container = Color(0xFF234A1B),
            onContainer = Color(0xFFCDEFC7),
        )

        MedicationGroupColorKey.AMBER -> MedicationGroupPaletteColors(
            accent = Color(0xFFFFC973),
            container = Color(0xFF5A3C04),
            onContainer = Color(0xFFFFDDAF),
        )

        MedicationGroupColorKey.CORAL -> MedicationGroupPaletteColors(
            accent = Color(0xFFFFB4A5),
            container = Color(0xFF6B2B1C),
            onContainer = Color(0xFFFFDAD2),
        )

        MedicationGroupColorKey.SLATE -> MedicationGroupPaletteColors(
            accent = Color(0xFFC0CCDE),
            container = Color(0xFF3B4655),
            onContainer = Color(0xFFDDE5F3),
        )
    }
}
