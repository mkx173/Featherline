package com.mkx.hrttracker.widget

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.glance.unit.ColorProvider
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.settings.DarkModeOption
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

// Snapshot-time DarkModeOption baking is gone; both render paths resolve at render time.
internal fun DarkModeOption.toForcedDark(): Boolean? = when (this) {
    DarkModeOption.LIGHT -> false
    DarkModeOption.DARK -> true
    DarkModeOption.FOLLOW_SYSTEM -> null
}

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
    appearance: WidgetAppearance = WidgetAppearance.Default,
    forcedDark: Boolean? = null,
): WidgetColorScheme {
    val alpha = appearance.backgroundAlpha
    val lightSurfaces = deriveWidgetSurfaces(
        light.secondaryContainer, light.onSurface, light.onSurfaceVariant,
        appearance.backgroundHue, appearance.vibrancy, dark = false,
    )
    val darkSurfaces = deriveWidgetSurfaces(
        dark.secondaryContainer, dark.onSurface, dark.onSurfaceVariant,
        appearance.backgroundHue, appearance.vibrancy, dark = true,
    )
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
        // Both modes now cut the control pill from the background palette (the dark
        // neutral surfaceVariant pill is the design's one approved visible change).
        widgetControl = provider(lightSurfaces.control, darkSurfaces.control),
        onSurfaceVariant = provider(lightSurfaces.onSurfaceVariant, darkSurfaces.onSurfaceVariant),
        widgetContainer = provider(
            lightSurfaces.card.copy(alpha = alpha * CONTAINER_ALPHA_FACTOR),
            darkSurfaces.card.copy(alpha = alpha * CONTAINER_ALPHA_FACTOR),
        ),
        widgetBackground = provider(
            lightSurfaces.shell.copy(alpha = alpha),
            darkSurfaces.shell.copy(alpha = alpha),
        ),
        onSurface = provider(lightSurfaces.onSurface, darkSurfaces.onSurface),
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

// ── Prototype-locked derivation (notes/2026-06-12-widget-customization-design.md) ──

// Vibrancy anchor: 0 = neutral black/white, DEFAULT_VIBRANCY = today's tint
// (the regression anchor), 1 = max tint. Below the anchor only chroma fades —
// tones hold and no text lift — which is what keeps the anchor bit-exact.
private const val CHROMA_BOOST = 18.0
private const val LIGHT_SHELL_BASE = 94.0
private const val LIGHT_SHELL_MAX = 82.0
// SPEC_2025 dark secondaryContainer sits at tone ~25 (the 2021 spec the prototype
// ran on had 30), so the legacy anchor output is 25 − 10 = 15. The v=1 ceiling
// stays 35 — the dark ramp is simply longer than the prototype's.
private const val DARK_SHELL_BASE = 15.0
private const val DARK_SHELL_MAX = 35.0 // ceiling; text lift keeps onSurface ΔTone >= 50 here
private const val LIGHT_CARD_DELTA = -4.0
private const val DARK_CARD_DELTA = 10.0
private const val LIGHT_CONTROL_DELTA = -6.0
// Dark control mirrors light (card +6), cut from the BACKGROUND palette: with
// arbitrary hues the old neutral dark.surfaceVariant pill read broken on tinted
// cards. Tone 36 tinted at defaults vs today's tone-30 neutral — approved change.
private const val DARK_CONTROL_DELTA = 6.0
private const val ON_SURFACE_LIFT_LIGHT = -4.0
private const val ON_SURFACE_LIFT_DARK = 6.0
private const val ON_SURFACE_VARIANT_LIFT_LIGHT = -8.0
// Sized so the SPEC_2025 dark onSurfaceVariant (tone ~70; the prototype's 2021
// spec had ~80) lands at the prototype's locked v=1 endpoint of ~88, keeping the
// secondary-text gap vs cards at the accepted ~44 floor instead of collapsing.
private const val ON_SURFACE_VARIANT_LIFT_DARK = 18.0

private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

// Ramp above the anchor; 0 at/below it. All tone/lift movement runs on this.
private fun vibrancyRamp(vibrancy: Float): Double =
    (((vibrancy - WidgetAppearance.DEFAULT_VIBRANCY) / (1f - WidgetAppearance.DEFAULT_VIBRANCY))
        .coerceIn(0f, 1f)).toDouble()

private fun vibrancyChroma(baseChroma: Double, vibrancy: Float): Double =
    if (vibrancy <= WidgetAppearance.DEFAULT_VIBRANCY) {
        baseChroma * (vibrancy / WidgetAppearance.DEFAULT_VIBRANCY)
    } else {
        baseChroma + CHROMA_BOOST * vibrancyRamp(vibrancy)
    }

private fun colorAt(hue: Double, chroma: Double, tone: Double): Color =
    Color(Hct.from(hue, chroma, tone.coerceIn(0.0, 100.0)).toInt())

private fun Color.shiftTone(delta: Double): Color {
    // Not an optimization: at the vibrancy anchor the lift is exactly 0 and text
    // colors must pass through UNTOUCHED — an Hct round-trip can move a channel
    // by 1, which would break the bit-exact anchor contract.
    if (delta == 0.0) return this
    val hct = Hct.fromInt(toArgb())
    return Color(Hct.from(hct.hue, hct.chroma, (hct.tone + delta).coerceIn(0.0, 100.0)).toInt())
}

internal data class WidgetSurfaces(
    val shell: Color,
    val card: Color,
    val control: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
)

// One mode's background surfaces + vibrancy-lifted text tones, built absolutely in
// HCT. Invariants (encoded in WidgetThemeDerivationTest, not re-derived here):
// onSurface keeps ΔTone >= 50 against every surface at all vibrancy; secondary
// text degrades gracefully above the anchor (dark floors 44 cards / 36 pills at v=1).
internal fun deriveWidgetSurfaces(
    secondaryContainer: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    backgroundHue: Float?,
    vibrancy: Float,
    dark: Boolean,
): WidgetSurfaces {
    val sc = Hct.fromInt(secondaryContainer.toArgb())
    val hue = backgroundHue?.toDouble() ?: sc.hue
    val chroma = vibrancyChroma(sc.chroma, vibrancy)
    val u = vibrancyRamp(vibrancy)
    val shellTone =
        if (dark) lerp(DARK_SHELL_BASE, DARK_SHELL_MAX, u)
        else lerp(LIGHT_SHELL_BASE, LIGHT_SHELL_MAX, u)
    val cardTone = shellTone + if (dark) DARK_CARD_DELTA else LIGHT_CARD_DELTA
    val controlTone = cardTone + if (dark) DARK_CONTROL_DELTA else LIGHT_CONTROL_DELTA
    return WidgetSurfaces(
        shell = colorAt(hue, chroma, shellTone),
        card = colorAt(hue, chroma, cardTone),
        control = colorAt(hue, chroma, controlTone),
        onSurface = onSurface.shiftTone((if (dark) ON_SURFACE_LIFT_DARK else ON_SURFACE_LIFT_LIGHT) * u),
        onSurfaceVariant = onSurfaceVariant.shiftTone(
            (if (dark) ON_SURFACE_VARIANT_LIFT_DARK else ON_SURFACE_VARIANT_LIFT_LIGHT) * u
        ),
    )
}

// An explicit hue pick materialized as a seed. Fixed chroma/tone: TonalSpot only
// consumes the seed's hue, and constructing in HCT keeps the foreground slider
// from drifting the perceptual hue (the prototype's HSV-saturation leak).
internal fun seedColorFromHue(seedHue: Float): Color =
    Color(Hct.from(seedHue.toDouble(), 36.0, 50.0).toInt())

// Where the config sliders rest while their value is null. Uses the seeded
// fallback scheme; on dynamic-palette devices the true resting hue is the
// wallpaper's, but the preview shows ground truth and the handle position is
// cosmetic.
internal fun defaultSeedHue(): Float =
    Hct.fromInt(DefaultSeedColor.toArgb()).hue.toFloat()

internal fun derivedBackgroundHue(seedHue: Float?): Float {
    val seed = seedHue?.let(::seedColorFromHue) ?: DefaultSeedColor
    val (light, _) = seededWidgetColorSchemes(seed)
    return Hct.fromInt(light.secondaryContainer.toArgb()).hue.toFloat()
}
