package com.mkx.hrttracker.widget

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.materialkolor.dynamicColorScheme
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.theme.DefaultSeedColor
import com.mkx.hrttracker.ui.theme.MedicationGroupPalettes
import androidx.glance.color.ColorProvider as DayNightColorProvider

internal data class WidgetColorScheme(
    val primary: ColorProvider,
    val onPrimary: ColorProvider,
    val primaryContainer: ColorProvider,
    val onPrimaryContainer: ColorProvider,
    val secondary: ColorProvider,
    val onSecondary: ColorProvider,
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

// Builds a ColorProvider that either follows the launcher's day/night state (when
// forcedDark is null) or pins to one mode (when the user picked LIGHT or DARK in
// the app's settings). The launcher's day/night doesn't track the app's
// preference, so without this routing the in-app dark mode option had no effect
// on the widget.
private fun colorProvider(light: Color, dark: Color, forcedDark: Boolean?): ColorProvider =
    when (forcedDark) {
        true -> ColorProvider(dark)
        false -> ColorProvider(light)
        null -> DayNightColorProvider(light, dark)
    }

internal fun widgetColorScheme(
    seed: Color,
    alpha: Float = 1.0f,
    forcedDark: Boolean? = null,
): WidgetColorScheme {
    val light = dynamicColorScheme(seedColor = seed, isDark = false)
    val dark = dynamicColorScheme(seedColor = seed, isDark = true)
    fun provider(lightColor: Color, darkColor: Color) = colorProvider(lightColor, darkColor, forcedDark)
    return WidgetColorScheme(
        primary = provider(light.primary, dark.primary),
        onPrimary = provider(light.onPrimary, dark.onPrimary),
        primaryContainer = provider(light.primaryContainer, dark.primaryContainer),
        onPrimaryContainer = provider(light.onPrimaryContainer, dark.onPrimaryContainer),
        secondary = provider(light.secondary, dark.secondary),
        onSecondary = provider(light.onSecondary, dark.onSecondary),
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

internal val LocalWidgetColors = compositionLocalOf { widgetColorScheme(DefaultSeedColor) }
internal val LocalWidgetScale = compositionLocalOf { 1.0f }
internal val LocalWidgetAlpha = compositionLocalOf { 1.0f }
internal val LocalWidgetForcedDark = compositionLocalOf<Boolean?> { null }

private val dayNightGroupProviders: Map<MedicationGroupColorKey?, ColorProvider> =
    MedicationGroupPalettes.mapValues { (_, palette) ->
        DayNightColorProvider(day = palette.lightAccent, night = palette.darkAccent)
    }
private val lightGroupProviders: Map<MedicationGroupColorKey?, ColorProvider> =
    MedicationGroupPalettes.mapValues { (_, palette) -> ColorProvider(palette.lightAccent) }
private val darkGroupProviders: Map<MedicationGroupColorKey?, ColorProvider> =
    MedicationGroupPalettes.mapValues { (_, palette) -> ColorProvider(palette.darkAccent) }

internal fun groupAccentColor(colorKey: MedicationGroupColorKey?, forcedDark: Boolean? = null): ColorProvider {
    val providers = when (forcedDark) {
        true -> darkGroupProviders
        false -> lightGroupProviders
        null -> dayNightGroupProviders
    }
    return providers.getValue(colorKey)
}
