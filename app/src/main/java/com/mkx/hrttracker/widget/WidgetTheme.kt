package com.mkx.hrttracker.widget

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils.M3HCTToColor
import androidx.core.graphics.ColorUtils.colorToM3HCT
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
    val widgetControl: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val widgetContainer: ColorProvider,
    val widgetBackground: ColorProvider,
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

// Widget shell & card colors follow the launcher convention Google's own widgets use (verified
// against Gmail): both are cuts of the secondary palette rather than the neutral surface family,
// so the widget reads as wallpaper-tinted next to other widgets. Tones are adjusted in M3 HCT
// space, matching Glance's widgetBackground derivation in androidx.glance.material3 exactly.
private fun adjustSecondaryContainerTone(
    input: Color,
    lightAdjustment: Float,
    darkAdjustment: Float,
): Color {
    val hct = floatArrayOf(0f, 0f, 0f)
    colorToM3HCT(input.toArgb(), hct)
    val adjustment = if (hct[2] > 50) lightAdjustment else darkAdjustment
    val tone = (hct[2] + adjustment).coerceIn(0f, 100f)
    return Color(M3HCTToColor(hct[0], hct[1], tone))
}

// Gmail uses tone 95/20 for the shell and 90/40 for cards, but that puts the tint contrast in
// opposite directions per mode: a near-white shell with tinted rows in light, a tinted shell
// with grey-washed rows in dark. We keep both layers visibly tinted instead, with the cards one
// subtle step away from the shell in each mode.
// Shell: secondaryContainer at tone 94 (light) / tone 20 (dark).
internal fun widgetBackgroundColor(secondaryContainer: Color): Color =
    adjustSecondaryContainerTone(secondaryContainer, lightAdjustment = 4f, darkAdjustment = -10f)

// Cards: tone 90 (light) / tone 30, i.e. secondaryContainer as-is with its full chroma.
internal fun widgetContainerColor(secondaryContainer: Color): Color =
    adjustSecondaryContainerTone(secondaryContainer, lightAdjustment = 0f, darkAdjustment = 0f)

// Trailing-button pills sit on the tone-90 cards, where the M3 light surfaceVariant (also tone
// 90, neutral) disappears; use a tone-84 cut of the secondary palette instead. The dark M3
// surfaceVariant already reads fine against the tone-30 cards and is used as-is.
internal fun widgetControlColor(secondaryContainer: Color): Color =
    adjustSecondaryContainerTone(secondaryContainer, lightAdjustment = -6f, darkAdjustment = 0f)

// Cards stack on top of the shell, so at the same alpha they occlude the wallpaper twice and
// read more "solid" than the background around them. Drawing them at 85% of the user alpha
// compensates, letting cards wash toward the wallpaper at a rate closer to the shell's. At
// full alpha the visible card color is 0.85·card + 0.15·shell — within a tone of the target.
private const val CONTAINER_ALPHA_FACTOR = 0.85f

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
    fun provider(lightColor: Color, darkColor: Color) =
        colorProvider(lightColor, darkColor, forcedDark)
    return WidgetColorScheme(
        primary = provider(light.primary, dark.primary),
        onPrimary = provider(light.onPrimary, dark.onPrimary),
        primaryContainer = provider(light.primaryContainer, dark.primaryContainer),
        onPrimaryContainer = provider(light.onPrimaryContainer, dark.onPrimaryContainer),
        secondary = provider(light.secondary, dark.secondary),
        onSecondary = provider(light.onSecondary, dark.onSecondary),
        secondaryContainer = provider(light.secondaryContainer, dark.secondaryContainer),
        onSecondaryContainer = provider(light.onSecondaryContainer, dark.onSecondaryContainer),
        widgetControl = provider(
            widgetControlColor(light.secondaryContainer),
            dark.surfaceVariant,
        ),
        onSurfaceVariant = provider(light.onSurfaceVariant, dark.onSurfaceVariant),
        widgetContainer = provider(
            widgetContainerColor(light.secondaryContainer)
                .copy(alpha = alpha * CONTAINER_ALPHA_FACTOR),
            widgetContainerColor(dark.secondaryContainer)
                .copy(alpha = alpha * CONTAINER_ALPHA_FACTOR),
        ),
        widgetBackground = provider(
            widgetBackgroundColor(light.secondaryContainer).copy(alpha = alpha),
            widgetBackgroundColor(dark.secondaryContainer).copy(alpha = alpha),
        ),
        onSurface = provider(light.onSurface, dark.onSurface),
        outline = provider(light.outline, dark.outline),
        outlineVariant = provider(light.outlineVariant, dark.outlineVariant),
    )
}

// MaterialKolor fallback: regenerate matching light & dark schemes from a single seed. Used
// when adaptive color is off or below API 31, and by the preview/default surfaces below.
internal fun seededWidgetColorSchemes(seed: Color): Pair<ColorScheme, ColorScheme> =
    dynamicColorScheme(
        seedColor = seed,
        isDark = false,
        specVersion = ColorSpec.SpecVersion.SPEC_2025
    ) to
            dynamicColorScheme(
                seedColor = seed,
                isDark = true,
                specVersion = ColorSpec.SpecVersion.SPEC_2025
            )

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

internal fun groupAccentColor(
    colorKey: MedicationGroupColorKey?,
    forcedDark: Boolean? = null
): ColorProvider {
    val providers = when (forcedDark) {
        true -> darkGroupProviders
        false -> lightGroupProviders
        null -> dayNightGroupProviders
    }
    return providers.getValue(colorKey)
}
