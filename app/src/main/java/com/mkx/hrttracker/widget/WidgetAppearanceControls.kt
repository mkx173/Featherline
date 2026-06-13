package com.mkx.hrttracker.widget

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.materialkolor.hct.Hct
import kotlin.math.roundToInt

// Shared appearance-slider building blocks used by both the launcher-reconfigure editor
// (WidgetConfigScreen) and the in-app appearance dialog (SettingsScreen), so the two
// surfaces present the same accent / saturation / light-balance controls.

// Tone-60/chroma-48 cut: a recognizable, mode-independent preview of a hue.
internal fun hueSwatchColor(hue: Float): Color =
    Color(Hct.from(hue.toDouble(), 48.0, 60.0).toInt())

// A bidirectional axis reads as a signed offset from the midpoint of [valueRange], scaled
// to -50..+50 (e.g. "+30" / "0" / "-20").
internal fun centeredOffsetReadout(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
): String {
    val mid = (valueRange.start + valueRange.endInclusive) / 2f
    val offset =
        ((value - mid) / (valueRange.endInclusive - valueRange.start) * 100).roundToInt()
    return if (offset > 0) "+$offset" else offset.toString()
}

// Material 3 Expressive centered track: the active indicator grows from the track midpoint,
// marking the neutral anchor of a bidirectional axis (light balance defaults at 0.5).
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WidgetCenteredSliderTrack(sliderState: SliderState) {
    SliderDefaults.CenteredTrack(sliderState = sliderState)
}

// Hue picker: the default expressive track tinted with the hue spectrum via SrcAtop, so the
// thumb/track gap and rounded ends match the other rows and the gradient stays continuous
// across the gap. Chroma/tone match hueSwatchColor so the bar matches the swatch dot.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WidgetHueSpectrumTrack(sliderState: SliderState) {
    val hueColors = remember { (0..360 step 15).map { hueSwatchColor(it.toFloat()) } }
    SliderDefaults.Track(
        sliderState = sliderState,
        // Opaque mask: SrcAtop keeps the track's alpha (so the gap stays a real gap) but
        // replaces its colour, so both segments read equally vivid.
        colors = SliderDefaults.colors(
            activeTrackColor = Color.Black,
            inactiveTrackColor = Color.Black,
        ),
        modifier = Modifier
            // Isolate so SrcAtop tints only the track's painted pixels, leaving the thumb
            // gap and rounded ends transparent.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(hueColors, endX = size.width),
                    blendMode = BlendMode.SrcAtop,
                )
            },
    )
}
