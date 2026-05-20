package com.mkx.hrttracker.widget

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.unit.ColorProvider
import com.materialkolor.dynamicColorScheme
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey

internal data class WidgetColorScheme(
    val primary: ColorProvider,
    val primaryContainer: ColorProvider,
    val onPrimaryContainer: ColorProvider,
    val secondaryContainer: ColorProvider,
    val onSecondaryContainer: ColorProvider,
    val tertiaryContainer: ColorProvider,
    val onTertiaryContainer: ColorProvider,
    val surfaceVariant: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val surfaceContainerLow: ColorProvider,
    val surface: ColorProvider,
    val onSurface: ColorProvider,
    val outline: ColorProvider,
    val outlineVariant: ColorProvider,
)

internal fun hardcodedWidgetColorScheme(alpha: Float = 1.0f): WidgetColorScheme {
    fun provider(day: Color, night: Color) = DayNightColorProvider(day, night)
    return WidgetColorScheme(
        primary = provider(Color(0xFF8D4959), Color(0xFFFFB1C0)),
        primaryContainer = provider(Color(0xFFFFD9DF), Color(0xFF723341)),
        onPrimaryContainer = provider(Color(0xFF3B0717), Color(0xFFFFD9DF)),
        secondaryContainer = provider(Color(0xFFFFD9DF), Color(0xFF5B3F44)),
        onSecondaryContainer = provider(Color(0xFF5B3F44), Color(0xFFFFD9DF)),
        tertiaryContainer = provider(Color(0xFFFFDCBC), Color(0xFF5F401D)),
        onTertiaryContainer = provider(Color(0xFF5F401D), Color(0xFFFFDCBC)),
        surfaceVariant = provider(Color(0xFFF5E4E6), Color(0xFF312829)),
        onSurfaceVariant = provider(Color(0xFF524345), Color(0xFFD6C2C4)),
        surfaceContainerLow = provider(
            Color(0xFFFFF0F1).copy(alpha = alpha),
            Color(0xFF22191B).copy(alpha = alpha),
        ),
        surface = provider(
            Color(0xFFFFF8F7).copy(alpha = alpha),
            Color(0xFF191113).copy(alpha = alpha),
        ),
        onSurface = provider(Color(0xFF22191B), Color(0xFFEFDEE0)),
        outline = provider(Color(0xFF847375), Color(0xFF9F8C8F)),
        outlineVariant = provider(Color(0xFFD6C2C4), Color(0xFF524345)),
    )
}

@RequiresApi(Build.VERSION_CODES.S)
internal fun dynamicWidgetColorScheme(context: Context, alpha: Float = 1.0f): WidgetColorScheme {
    val seed = Color(context.getColor(android.R.color.system_accent1_500))
    val light = dynamicColorScheme(seed, isDark = false)
    val dark = dynamicColorScheme(seed, isDark = true)
    fun provider(lightColor: Color, darkColor: Color) = DayNightColorProvider(lightColor, darkColor)
    return WidgetColorScheme(
        primary = provider(light.primary, dark.primary),
        primaryContainer = provider(light.primaryContainer, dark.primaryContainer),
        onPrimaryContainer = provider(light.onPrimaryContainer, dark.onPrimaryContainer),
        secondaryContainer = provider(light.secondaryContainer, dark.secondaryContainer),
        onSecondaryContainer = provider(light.onSecondaryContainer, dark.onSecondaryContainer),
        tertiaryContainer = provider(light.tertiaryContainer, dark.tertiaryContainer),
        onTertiaryContainer = provider(light.onTertiaryContainer, dark.onTertiaryContainer),
        surfaceVariant = provider(light.surfaceVariant, dark.surfaceVariant),
        onSurfaceVariant = provider(light.onSurfaceVariant, dark.onSurfaceVariant),
        surfaceContainerLow = provider(
            light.surfaceContainerLow.copy(alpha = alpha * 0.85f),
            dark.surfaceContainerLow.copy(alpha = alpha * 0.85f),
        ),
        surface = provider(light.surface.copy(alpha = alpha), dark.surface.copy(alpha = alpha)),
        onSurface = provider(light.onSurface, dark.onSurface),
        outline = provider(light.outline, dark.outline),
        outlineVariant = provider(light.outlineVariant, dark.outlineVariant),
    )
}

internal val LocalWidgetColors = compositionLocalOf { hardcodedWidgetColorScheme() }
internal val LocalWidgetScale = compositionLocalOf { 1.0f }
internal val LocalWidgetAlpha = compositionLocalOf { 1.0f }

private val colorGroupSlate = DayNightColorProvider(day = Color(0xFF60646C), night = Color(0xFFB0B4BA))
private val colorGroupRose = DayNightColorProvider(day = Color(0xFFCE2C31), night = Color(0xFFFF8A88))
private val colorGroupCoral = DayNightColorProvider(day = Color(0xFFD14E00), night = Color(0xFFFF9B52))
private val colorGroupAmber = DayNightColorProvider(day = Color(0xFFA06E00), night = Color(0xFFD9C600))
private val colorGroupCitron = DayNightColorProvider(day = Color(0xFF5C7C2F), night = Color(0xFFBDEE63))
private val colorGroupSage = DayNightColorProvider(day = Color(0xFF00824D), night = Color(0xFF3DD68C))
private val colorGroupTeal = DayNightColorProvider(day = Color(0xFF00826D), night = Color(0xFF0AD8B6))
private val colorGroupSky = DayNightColorProvider(day = Color(0xFF00749E), night = Color(0xFF7CE2FE))
private val colorGroupIndigo = DayNightColorProvider(day = Color(0xFF3A5BC7), night = Color(0xFF9DB1FF))
private val colorGroupViolet = DayNightColorProvider(day = Color(0xFF8145B5), night = Color(0xFFD59CFF))
private val colorGroupPlum = DayNightColorProvider(day = Color(0xFFC1298A), night = Color(0xFFFF80CA))

internal fun groupAccentColor(colorKey: MedicationGroupColorKey?): ColorProvider = when (colorKey) {
    null -> colorGroupSlate
    MedicationGroupColorKey.ROSE -> colorGroupRose
    MedicationGroupColorKey.CORAL -> colorGroupCoral
    MedicationGroupColorKey.AMBER -> colorGroupAmber
    MedicationGroupColorKey.CITRON -> colorGroupCitron
    MedicationGroupColorKey.SAGE -> colorGroupSage
    MedicationGroupColorKey.TEAL -> colorGroupTeal
    MedicationGroupColorKey.SKY -> colorGroupSky
    MedicationGroupColorKey.INDIGO -> colorGroupIndigo
    MedicationGroupColorKey.VIOLET -> colorGroupViolet
    MedicationGroupColorKey.PLUM -> colorGroupPlum
}

