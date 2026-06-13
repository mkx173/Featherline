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

// Cards stack on top of the shell, so a card pixel covers the wallpaper through TWO
// translucent layers and reads more "solid" than the surrounding shell — worst at low
// opacity (a flat factor barely helps; the fully-present shell underneath is the real
// occluder). Round 6: ramp the factor down with opacity (HIGH at full opacity → FLOOR at the
// 0.5 slider floor) so the excess solidity shrinks as opacity drops, with FLOOR a floor that
// keeps the row visibly distinct. At full opacity the factor is still 0.85, so the visible
// card color is 0.85·card + 0.15·shell — unchanged.
private const val CONTAINER_ALPHA_FACTOR_HIGH = 0.85f   // @ backgroundAlpha 1.0 (unchanged look)
private const val CONTAINER_ALPHA_FACTOR_FLOOR = 0.40f  // @ backgroundAlpha 0.5 (the slider floor)

// Card paint alpha for a given user backgroundAlpha: the opacity-aware factor times the alpha
// itself. backgroundAlpha is sanitized to [0.5, 1]; the coerce defends the endpoints.
internal fun containerAlpha(backgroundAlpha: Float): Float {
    val t = ((backgroundAlpha - 0.5f) / 0.5f).coerceIn(0f, 1f)
    return backgroundAlpha *
        (CONTAINER_ALPHA_FACTOR_FLOOR + (CONTAINER_ALPHA_FACTOR_HIGH - CONTAINER_ALPHA_FACTOR_FLOOR) * t)
}

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
    val cardAlpha = containerAlpha(alpha)
    val lightSurfaces = deriveWidgetSurfaces(
        light.secondaryContainer, light.onSurface, light.onSurfaceVariant,
        appearance.saturation, appearance.balance, dark = false, backgroundAlpha = alpha,
    )
    val darkSurfaces = deriveWidgetSurfaces(
        dark.secondaryContainer, dark.onSurface, dark.onSurfaceVariant,
        appearance.saturation, appearance.balance, dark = true, backgroundAlpha = alpha,
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
            lightSurfaces.card.copy(alpha = cardAlpha),
            darkSurfaces.card.copy(alpha = cardAlpha),
        ),
        widgetBackground = provider(
            lightSurfaces.shell.copy(alpha = alpha),
            darkSurfaces.shell.copy(alpha = alpha),
        ),
        onSurface = provider(lightSurfaces.onSurface, darkSurfaces.onSurface),
        // No widget composable consumes the plain `outline` role (only outlineVariant);
        // kept as scheme pass-through for scheme completeness, not tuned.
        outline = provider(light.outline, dark.outline),
        outlineVariant = provider(lightSurfaces.outlineVariant, darkSurfaces.outlineVariant),
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

// Round 4 split the old single "vibrancy" axis into two independent ones:
//  - saturation OWNS chroma (0 = b/w, 0.5 = scheme standard, 1 = scChroma + boost);
//  - balance is pure tone depth + text lift + the Round-2 ramps. Bidirectional (Round 6):
//    0.5 = today's tones (regression anchor); 0.5→1 DEEPENS, 0.5→0 LIGHTENS. All u-driven
//    ramps below are UNCHANGED as functions of u; u is now the DEEPEN half of balance
//    (deepenRamp), which is 0 across the lighten half — so they hold at the anchor there.
private const val CHROMA_BOOST = 18.0
private const val LIGHT_SHELL_BASE = 94.0
// Round 5: the old endpoints (light 82 / dark 35) were the original prototype's
// contrast LIMITS, not good-looking depths — the upper half of the slider drifted
// toward mid-tone murk and carried all the accepted contrast-floor degradation. The
// range is compressed (≈ today's 50% becomes the new maximum) to keep the whole
// slider useful (notes/prototype-widget-theme-NOTES.md, Round 5).
private const val LIGHT_SHELL_MAX = 88.0
// SPEC_2025 dark secondaryContainer sits at tone ~25 (the 2021 spec the prototype
// ran on had 30), so the legacy anchor output is 25 − 10 = 15.
private const val DARK_SHELL_BASE = 15.0
// ceiling; with the compressed range the text lift keeps onSurface ΔTone >= 50 and
// secondary-text contrast back to ≈Δ45+ across the whole slider (min at the anchor).
private const val DARK_SHELL_MAX = 25.0
// Round 6 — lighten half (balance 0.5→0): the shell runs the OTHER way, light toward
// near-white / dark toward near-black. Tones-only (every ramp below keys off deepenRamp,
// which is 0 here), and lightening only ever IMPROVES contrast, so no claw-back is needed.
private const val LIGHT_SHELL_LIGHTEN = 98.0
private const val DARK_SHELL_LIGHTEN = 8.0
// Light card delta ramps from the anchor (−4) to a deeper u=1 cut (−9): flat −4
// composited (~3.4 tones) read too subtle at high balance (Round-2 tuner verdict,
// notes/prototype-widget-theme-NOTES.md). Dark card stays flat +10.
private const val LIGHT_CARD_DELTA_ANCHOR = -4.0
private const val LIGHT_CARD_DELTA_V1 = -9.0
private const val DARK_CARD_DELTA = 10.0
private const val LIGHT_CONTROL_DELTA = -6.0
// Dark control mirrors light (card +6), cut from the BACKGROUND palette: with
// arbitrary hues the old neutral dark.surfaceVariant pill read broken on tinted
// cards. Tone 36 tinted at defaults vs today's tone-30 neutral — approved change.
private const val DARK_CONTROL_DELTA = 6.0
// Light text lifts ramp anchor→v1 then scale by u (amount = lerp(anchor, v1, u)·u),
// so they're 0 at the anchor and reach −9 / −13 at u=1 — text now tracks balance
// more in light mode (Round-2 tuner verdict). Dark lifts are flat per-u (anchor==v1).
private const val ON_SURFACE_LIFT_LIGHT_ANCHOR = -4.0
private const val ON_SURFACE_LIFT_LIGHT_V1 = -9.0
private const val ON_SURFACE_LIFT_DARK = 6.0
private const val ON_SURFACE_VARIANT_LIFT_LIGHT_ANCHOR = -8.0
private const val ON_SURFACE_VARIANT_LIFT_LIGHT_V1 = -13.0
// Sized so the SPEC_2025 dark onSurfaceVariant (tone ~70; the prototype's 2021
// spec had ~80) lands at the locked v=1 endpoint of ~88. With the Round-5
// compressed shell (ceiling 25), the secondary-text gap vs cards no longer
// degrades with balance — its minimum is now at the anchor (~Δ46.5).
private const val ON_SURFACE_VARIANT_LIFT_DARK = 18.0
// outlineVariant (divider + unfulfilled progress dots/track). Round 7: tinted with the surface across
// the WHOLE balance range (surface hue + capped chroma; tone sits |offset| from the shell — darker in
// light, lighter in dark). Offsets are flat (anchor == deepen) so the separation is constant across
// balance. Round 8: the outline is painted SOLID over the translucent shell, so as backgroundAlpha
// drops the rendered background drifts toward the wallpaper and the solid outline's contrast falls;
// the offset is boosted on a linear opacity ramp (up to ×1.333 at the 0.5 floor) so the tone
// separation grows as opacity drops.
private const val OUTLINE_VARIANT_OFFSET_LIGHT_ANCHOR = -18.0
private const val OUTLINE_VARIANT_OFFSET_LIGHT_DEEPEN = -18.0
private const val OUTLINE_VARIANT_OFFSET_DARK_ANCHOR = 18.0
private const val OUTLINE_VARIANT_OFFSET_DARK_DEEPEN = 18.0
// Chroma ceiling for the tinted outline (keeps it from over-saturating).
private const val OUTLINE_VARIANT_CHROMA_MAX = 22.0
// Round 8: the solid outline's offset grows as backgroundAlpha drops (the shell fades toward the
// wallpaper under it). LINEAR ramp — ×1 at full opacity → ×this at the 0.5 floor. 1.333 gives offset
// 18 at full opacity, ~21 at 0.75, ~24 at the 0.5 floor; steeper boosts (the old 1/alpha curve hit ×2
// / offset 36) read too bright at low opacity. Tuned on device.
private const val OUTLINE_VARIANT_OFFSET_BOOST_AT_MIN_ALPHA = 1.333

private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

// Deepen ramp: 0 at the anchor (balance 0.5) → 1 at the deepest (balance 1); 0 across the
// whole lighten half. Every tone/lift/outline ramp runs on this — UNCHANGED as a function of
// u from the pre-Round-6 code (for b >= 0.5, deepenRamp(b) equals the old balanceRamp run on
// 2·(b − 0.5)), so the deepen half reproduces the old 0→1 bit-exact.
private fun deepenRamp(balance: Float): Double =
    (((balance - 0.5f) * 2f).coerceIn(0f, 1f)).toDouble()

// Lighten ramp: 0 at the anchor → 1 at the lightest (balance 0); 0 across the deepen half.
private fun lightenRamp(balance: Float): Double =
    (((0.5f - balance) * 2f).coerceIn(0f, 1f)).toDouble()

// Saturation owns chroma outright (Round 4): 0 = black/white, 0.5 = the scheme's
// standard chroma (regression anchor), 1 = the old maximum (scChroma + CHROMA_BOOST).
private fun saturationChroma(baseChroma: Double, saturation: Float): Double =
    if (saturation <= WidgetAppearance.DEFAULT_SATURATION) {
        baseChroma * (saturation / WidgetAppearance.DEFAULT_SATURATION)
    } else {
        baseChroma + CHROMA_BOOST *
            ((saturation - WidgetAppearance.DEFAULT_SATURATION) /
                (1f - WidgetAppearance.DEFAULT_SATURATION))
    }

private fun colorAt(hue: Double, chroma: Double, tone: Double): Color =
    Color(Hct.from(hue, chroma, tone.coerceIn(0.0, 100.0)).toInt())

private fun Color.shiftTone(delta: Double): Color {
    // Not an optimization: at the balance anchor the lift is exactly 0 and text
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
    val outlineVariant: Color,
)

// One mode's background surfaces + balance-lifted text tones, built absolutely in
// HCT. Invariants (encoded in WidgetThemeDerivationTest, not re-derived here):
// onSurface keeps ΔTone >= 50 against every surface at all balance; with the
// Round-5 compressed shell (light 88 / dark 25 ceiling) secondary text holds
// ≈Δ45+ across the whole slider, its minimum now at the anchor.
internal fun deriveWidgetSurfaces(
    secondaryContainer: Color,
    onSurface: Color,
    onSurfaceVariant: Color,
    saturation: Float,
    balance: Float,
    dark: Boolean,
    backgroundAlpha: Float = 1f,
): WidgetSurfaces {
    val sc = Hct.fromInt(secondaryContainer.toArgb())
    // One theme color: the background always follows the seed scheme's
    // secondaryContainer hue (Round 3 dropped the separate background-hue pick).
    val hue = sc.hue
    // Saturation owns chroma outright (Round 4); at DEFAULT_SATURATION this is
    // EXACTLY the scheme chroma (bit-identical anchor: x/0.5*0.5 short-circuits
    // through the <= branch).
    val chroma = saturationChroma(sc.chroma, saturation)
    val u = deepenRamp(balance)
    val lighten = lightenRamp(balance)
    // Anchor at balance 0.5; deepen toward _MAX (b→1) or lighten toward _LIGHTEN (b→0). u and
    // lighten are mutually exclusive (each is 0 off its own half), so one expression covers both.
    val shellBase = if (dark) DARK_SHELL_BASE else LIGHT_SHELL_BASE
    val shellTone = shellBase +
        ((if (dark) DARK_SHELL_MAX else LIGHT_SHELL_MAX) - shellBase) * u +
        ((if (dark) DARK_SHELL_LIGHTEN else LIGHT_SHELL_LIGHTEN) - shellBase) * lighten
    val cardDelta =
        if (dark) DARK_CARD_DELTA
        else lerp(LIGHT_CARD_DELTA_ANCHOR, LIGHT_CARD_DELTA_V1, u)
    val cardTone = shellTone + cardDelta
    val controlTone = cardTone + if (dark) DARK_CONTROL_DELTA else LIGHT_CONTROL_DELTA
    // Light lifts: amount = lerp(anchor, v1, u)·u so they vanish at the anchor and
    // deepen toward v=1. Dark lifts are flat per-u (anchor==v1), unchanged.
    val onSurfaceLift =
        if (dark) ON_SURFACE_LIFT_DARK * u
        else lerp(ON_SURFACE_LIFT_LIGHT_ANCHOR, ON_SURFACE_LIFT_LIGHT_V1, u) * u
    val onSurfaceVariantLift =
        if (dark) ON_SURFACE_VARIANT_LIFT_DARK * u
        else lerp(ON_SURFACE_VARIANT_LIFT_LIGHT_ANCHOR, ON_SURFACE_VARIANT_LIFT_LIGHT_V1, u) * u
    return WidgetSurfaces(
        shell = colorAt(hue, chroma, shellTone),
        card = colorAt(hue, chroma, cardTone),
        control = colorAt(hue, chroma, controlTone),
        onSurface = onSurface.shiftTone(onSurfaceLift),
        onSurfaceVariant = onSurfaceVariant.shiftTone(onSurfaceVariantLift),
        outlineVariant = deriveOutlineVariant(hue, chroma, shellTone, u, dark, backgroundAlpha),
    )
}

// outlineVariant (divider + unfulfilled progress dots/track), Round 7: tinted with the surface at
// EVERY balance. Tone = shellTone + offset, where the offset ramps on u from the anchor offset
// (lighten half + the default) to the deepen offset (balance 1); hue is the surface hue and chroma
// the capped surface chroma. Because u is 0 across the lighten half, the offset holds at the anchor
// and the outline tracks the shell as it brightens/darkens. There is deliberately NO anchor
// short-circuit: unlike shiftTone, the outline is no longer the scheme color at the default (it gains
// the surface tint), so there is no bit-exact scheme value to preserve (notes Round 7).
//
// Round 8: the outline is painted SOLID over the translucent shell, so as backgroundAlpha drops the
// rendered background drifts toward the wallpaper and the solid outline's tone separation collapses.
// Boost the offset on a LINEAR opacity ramp (×1 at full opacity → ×BOOST_AT_MIN_ALPHA at the 0.5
// floor) so the separation grows as opacity falls. No effect at full opacity (boost = 1).
private fun deriveOutlineVariant(
    hue: Double,
    chroma: Double,
    shellTone: Double,
    u: Double,
    dark: Boolean,
    backgroundAlpha: Float,
): Color {
    val anchorOffset =
        if (dark) OUTLINE_VARIANT_OFFSET_DARK_ANCHOR else OUTLINE_VARIANT_OFFSET_LIGHT_ANCHOR
    val deepenOffset =
        if (dark) OUTLINE_VARIANT_OFFSET_DARK_DEEPEN else OUTLINE_VARIANT_OFFSET_LIGHT_DEEPEN
    // alpha is sanitized to [0.5, 1] upstream; coerce defends against a stray value.
    val alpha = backgroundAlpha.coerceIn(0.5f, 1f).toDouble()
    // Linear boost: ×1 at full opacity → ×BOOST_AT_MIN_ALPHA at the 0.5 floor.
    val alphaBoost = lerp(1.0, OUTLINE_VARIANT_OFFSET_BOOST_AT_MIN_ALPHA, (1.0 - alpha) / 0.5)
    val offset = lerp(anchorOffset, deepenOffset, u) * alphaBoost
    return colorAt(
        hue,
        chroma.coerceAtMost(OUTLINE_VARIANT_CHROMA_MAX),
        shellTone + offset,
    )
}

// An explicit hue pick materialized as a seed. Fixed chroma/tone: TonalSpot only
// consumes the seed's hue, and constructing in HCT keeps the foreground slider
// from drifting the perceptual hue (the prototype's HSV-saturation leak).
internal fun seedColorFromHue(seedHue: Float): Color =
    Color(Hct.from(seedHue.toDouble(), 36.0, 50.0).toInt())

// Where the accent (seed) slider rests while its value is null. Uses the seeded
// fallback scheme; on dynamic-palette devices the true resting hue is the
// wallpaper's, but the preview shows ground truth and the handle position is
// cosmetic.
internal fun defaultSeedHue(): Float =
    Hct.fromInt(DefaultSeedColor.toArgb()).hue.toFloat()
