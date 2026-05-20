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
import com.mkx.hrttracker.ui.theme.MedicationGroupPalettes
import com.mkx.hrttracker.ui.theme.onPrimaryDark
import com.mkx.hrttracker.ui.theme.onPrimaryContainerDark
import com.mkx.hrttracker.ui.theme.onPrimaryContainerLight
import com.mkx.hrttracker.ui.theme.onPrimaryLight
import com.mkx.hrttracker.ui.theme.onSecondaryContainerDark
import com.mkx.hrttracker.ui.theme.onSecondaryContainerLight
import com.mkx.hrttracker.ui.theme.onSurfaceDark
import com.mkx.hrttracker.ui.theme.onSurfaceLight
import com.mkx.hrttracker.ui.theme.onSurfaceVariantDark
import com.mkx.hrttracker.ui.theme.onSurfaceVariantLight
import com.mkx.hrttracker.ui.theme.onTertiaryContainerDark
import com.mkx.hrttracker.ui.theme.onTertiaryContainerLight
import com.mkx.hrttracker.ui.theme.outlineDark
import com.mkx.hrttracker.ui.theme.outlineLight
import com.mkx.hrttracker.ui.theme.outlineVariantDark
import com.mkx.hrttracker.ui.theme.outlineVariantLight
import com.mkx.hrttracker.ui.theme.primaryContainerDark
import com.mkx.hrttracker.ui.theme.primaryContainerLight
import com.mkx.hrttracker.ui.theme.primaryDark
import com.mkx.hrttracker.ui.theme.primaryLight
import com.mkx.hrttracker.ui.theme.secondaryContainerDark
import com.mkx.hrttracker.ui.theme.secondaryContainerLight
import com.mkx.hrttracker.ui.theme.surfaceContainerLowDark
import com.mkx.hrttracker.ui.theme.surfaceContainerLowLight
import com.mkx.hrttracker.ui.theme.surfaceDark
import com.mkx.hrttracker.ui.theme.surfaceLight
import com.mkx.hrttracker.ui.theme.surfaceVariantDark
import com.mkx.hrttracker.ui.theme.surfaceVariantLight
import com.mkx.hrttracker.ui.theme.tertiaryContainerDark
import com.mkx.hrttracker.ui.theme.tertiaryContainerLight

internal data class WidgetColorScheme(
    val primary: ColorProvider,
    val onPrimary: ColorProvider,
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

internal fun hardcodedWidgetColorScheme(
    alpha: Float = 1.0f,
    forcedDark: Boolean? = null,
): WidgetColorScheme {
    fun provider(day: Color, night: Color) = colorProvider(day, night, forcedDark)
    return WidgetColorScheme(
        primary = provider(primaryLight, primaryDark),
        onPrimary = provider(onPrimaryLight, onPrimaryDark),
        primaryContainer = provider(primaryContainerLight, primaryContainerDark),
        onPrimaryContainer = provider(onPrimaryContainerLight, onPrimaryContainerDark),
        secondaryContainer = provider(secondaryContainerLight, secondaryContainerDark),
        onSecondaryContainer = provider(onSecondaryContainerLight, onSecondaryContainerDark),
        tertiaryContainer = provider(tertiaryContainerLight, tertiaryContainerDark),
        onTertiaryContainer = provider(onTertiaryContainerLight, onTertiaryContainerDark),
        surfaceVariant = provider(surfaceVariantLight, surfaceVariantDark),
        onSurfaceVariant = provider(onSurfaceVariantLight, onSurfaceVariantDark),
        surfaceContainerLow = provider(
            surfaceContainerLowLight.copy(alpha = alpha * 0.85f),
            surfaceContainerLowDark.copy(alpha = alpha * 0.85f),
        ),
        surface = provider(
            surfaceLight.copy(alpha = alpha),
            surfaceDark.copy(alpha = alpha),
        ),
        onSurface = provider(onSurfaceLight, onSurfaceDark),
        outline = provider(outlineLight, outlineDark),
        outlineVariant = provider(outlineVariantLight, outlineVariantDark),
    )
}

@RequiresApi(Build.VERSION_CODES.S)
internal fun dynamicWidgetColorScheme(
    context: Context,
    alpha: Float = 1.0f,
    forcedDark: Boolean? = null,
): WidgetColorScheme {
    val seed = Color(context.getColor(android.R.color.system_accent1_500))
    val light = dynamicColorScheme(seed, isDark = false)
    val dark = dynamicColorScheme(seed, isDark = true)
    fun provider(lightColor: Color, darkColor: Color) = colorProvider(lightColor, darkColor, forcedDark)
    return WidgetColorScheme(
        primary = provider(light.primary, dark.primary),
        onPrimary = provider(light.onPrimary, dark.onPrimary),
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
