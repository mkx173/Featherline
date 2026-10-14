package com.mkx.hrttracker.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import com.mkx.hrttracker.R

/**
 * Roboto with its vertical metrics raised to 1.50/0.76 em so the Latin line box already covers
 * every CJK fallback font (Noto Sans CJK, SEC CJK, MiSans). Compose always lays text out with
 * fallback line spacing and offers no switch, so with plain Roboto a single CJK glyph grows the
 * line box and Material 3 re-centres it, dropping the baseline about 1.5 dp and making text
 * fields jump. The ascent/descent difference (0.74 em) is what places the baseline, and it is
 * tuned so Roboto capitals and CJK ideographs both sit within a pixel of the line centre; the
 * absolute size only has to beat every line-height/font-size ratio in use (see [TightLineHeight]).
 * Instanced from the official OFL Roboto; see docs/third-party-notices.md.
 */
private val RobotoFeatherline = FontFamily(
    Font(R.font.roboto_featherline_regular, FontWeight.Normal),
    Font(R.font.roboto_featherline_medium, FontWeight.Medium),
    Font(R.font.roboto_featherline_semibold, FontWeight.SemiBold),
    Font(R.font.roboto_featherline_bold, FontWeight.Bold),
)

// Tight keeps each line at exactly the style's line height as long as the font box (2.26 em) is
// taller than that line height; the default Fixed mode would pad the first and last line back out
// to the box. Below that ratio Tight+Both trims single lines to the box instead, so a style whose
// lineHeight exceeds 2.26 x fontSize renders shorter: M3 tops out at 1.5, and every
// typography.X.copy(fontSize = ...) in the app stays under 2.0.
private val TightLineHeight = LineHeightStyle(
    LineHeightStyle.Alignment.Center,
    LineHeightStyle.Trim.Both,
    LineHeightStyle.Mode.Tight,
)

/**
 * Stock Material 3 typography, or, when [fixCjkFallback] is true, the same scale on
 * [RobotoFeatherline] with [TightLineHeight] so CJK text can no longer move the baseline.
 */
fun appTypography(fixCjkFallback: Boolean): Typography =
    if (fixCjkFallback) {
        Typography().mapStyles { it.copy(fontFamily = RobotoFeatherline, lineHeightStyle = TightLineHeight) }
    } else {
        Typography()
    }

/** Decided once per process from the system font; see [hasTallerCjkFallback]. */
val AppTypography: Typography by lazy { appTypography(hasTallerCjkFallback()) }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun Typography.mapStyles(f: (TextStyle) -> TextStyle): Typography = copy(
    displayLarge = f(displayLarge), displayMedium = f(displayMedium), displaySmall = f(displaySmall),
    headlineLarge = f(headlineLarge), headlineMedium = f(headlineMedium), headlineSmall = f(headlineSmall),
    titleLarge = f(titleLarge), titleMedium = f(titleMedium), titleSmall = f(titleSmall),
    bodyLarge = f(bodyLarge), bodyMedium = f(bodyMedium), bodySmall = f(bodySmall),
    labelLarge = f(labelLarge), labelMedium = f(labelMedium), labelSmall = f(labelSmall),
    displayLargeEmphasized = f(displayLargeEmphasized),
    displayMediumEmphasized = f(displayMediumEmphasized),
    displaySmallEmphasized = f(displaySmallEmphasized),
    headlineLargeEmphasized = f(headlineLargeEmphasized),
    headlineMediumEmphasized = f(headlineMediumEmphasized),
    headlineSmallEmphasized = f(headlineSmallEmphasized),
    titleLargeEmphasized = f(titleLargeEmphasized),
    titleMediumEmphasized = f(titleMediumEmphasized),
    titleSmallEmphasized = f(titleSmallEmphasized),
    bodyLargeEmphasized = f(bodyLargeEmphasized),
    bodyMediumEmphasized = f(bodyMediumEmphasized),
    bodySmallEmphasized = f(bodySmallEmphasized),
    labelLargeEmphasized = f(labelLargeEmphasized),
    labelMediumEmphasized = f(labelMediumEmphasized),
    labelSmallEmphasized = f(labelSmallEmphasized),
)
