package com.mkx.hrttracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
internal data class ManualMedicationColors(
    val primary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
)

@Composable
fun rememberManualMedicationColorScheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    baseColorScheme: ColorScheme = MaterialTheme.colorScheme,
): ColorScheme {
    val colors = manualMedicationColors(darkTheme)
    return baseColorScheme.copy(
        primary = colors.primary,
        secondaryContainer = colors.secondaryContainer,
        onSecondaryContainer = colors.onSecondaryContainer,
    )
}

internal fun manualMedicationColors(darkTheme: Boolean): ManualMedicationColors {
    return if (darkTheme) {
        ManualMedicationColors(
            primary = Color(0xFFC1C6D6),
            secondaryContainer = Color(0xFF47494F),
            onSecondaryContainer = Color(0xFFB7B8BF),
        )
    } else {
        ManualMedicationColors(
            primary = Color(0xFF555B69),
            secondaryContainer = Color(0xFFDFDFE6),
            onSecondaryContainer = Color(0xFF616268),
        )
    }
}
