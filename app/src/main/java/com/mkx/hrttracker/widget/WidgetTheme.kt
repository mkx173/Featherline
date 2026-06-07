package com.mkx.hrttracker.widget

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
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

// Glance's androidx.glance.unit.ColorProvider(Color) factory is public, but it shares its
// file-level class (ColorProviderKt) with the @RestrictTo(LIBRARY_GROUP) ColorProvider(@ColorRes
// Int) overload. Lint's RestrictedApi check resolves by class + method name and conflates the two,
// so it flags the (legitimate) Color call as restricted. The warning is compile-time only with no
// runtime effect; route the Color factory through here so the suppression lives in one place.
@Suppress("RestrictedApi")
internal fun fixedColorProvider(color: Color): ColorProvider = ColorProvider(color)

// Builds a ColorProvider that either follows the launcher's day/night state (when
// forcedDark is null) or pins to one mode (when the user picked LIGHT or DARK in
// the app's settings). The launcher's day/night doesn't track the app's
// preference, so without this routing the in-app dark mode option had no effect
// on the widget.
private fun colorProvider(light: Color, dark: Color, forcedDark: Boolean?): ColorProvider =
    when (forcedDark) {
        true -> fixedColorProvider(dark)
        false -> fixedColorProvider(light)
        null -> DayNightColorProvider(light, dark)
    }

// Builds the widget's day/night ColorProvider scheme from explicit light & dark Material 3
// schemes. The source is chosen upstream: the live system palette (mirrors AndroidX) on API
// 31+ with adaptive color on, or a DefaultSeedColor MaterialKolor scheme otherwise. Widgets
// never apply AMOLED, so there is no amoled handling here.
internal fun widgetColorScheme(
    light: ColorScheme,
    dark: ColorScheme,
    alpha: Float = 1.0f,
    forcedDark: Boolean? = null,
): WidgetColorScheme {
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

// MaterialKolor fallback: regenerate matching light & dark schemes from a single seed. Used
// when adaptive color is off or below API 31, and by the preview/default surfaces below.
internal fun seededWidgetColorSchemes(seed: Color): Pair<ColorScheme, ColorScheme> =
    dynamicColorScheme(seedColor = seed, isDark = false, specVersion = ColorSpec.SpecVersion.SPEC_2025) to
        dynamicColorScheme(seedColor = seed, isDark = true, specVersion = ColorSpec.SpecVersion.SPEC_2025)

internal fun widgetColorScheme(seed: Color): WidgetColorScheme {
    val (light, dark) = seededWidgetColorSchemes(seed)
    return widgetColorScheme(light, dark)
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
    MedicationGroupPalettes.mapValues { (_, palette) -> fixedColorProvider(palette.lightAccent) }
private val darkGroupProviders: Map<MedicationGroupColorKey?, ColorProvider> =
    MedicationGroupPalettes.mapValues { (_, palette) -> fixedColorProvider(palette.darkAccent) }

internal fun groupAccentColor(colorKey: MedicationGroupColorKey?, forcedDark: Boolean? = null): ColorProvider {
    val providers = when (forcedDark) {
        true -> darkGroupProviders
        false -> lightGroupProviders
        null -> dayNightGroupProviders
    }
    return providers.getValue(colorKey)
}
