package com.mkx.hrttracker.widget

import android.appwidget.AppWidgetManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.viewinterop.AndroidView
import com.materialkolor.hct.Hct
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtDropdownMenu
import com.mkx.hrttracker.ui.components.HrtDropdownMenuItem
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.HazeTopAppBar
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.settings.labelRes
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.conflate
import kotlin.math.min
import kotlin.math.roundToInt

// Full-screen launcher-reconfigure layout: an opaque scaffold whose "wallpaper window"
// is a transparent hole (BlendMode.Clear punch-out) showing the system wallpaper —
// supplied by windowShowWallpaper in Theme.HrtTracker.WidgetConfig — with the live
// widget preview centered in it, and the appearance controls as HrtSection rows below.
// Deliberately does NOT reuse the in-app WidgetAppearanceDialog.
@Composable
internal fun WidgetConfigScreen(
    initialAppearance: WidgetAppearance,
    isMediumWidget: Boolean,
    appWidgetId: Int,
    snapshot: WidgetSnapshotRecord?,
    onSave: (WidgetAppearance) -> Unit,
    onCancel: () -> Unit,
) {
    val sanitizedInitial = remember(initialAppearance) { initialAppearance.sanitized() }
    var seedHue by rememberSaveable { mutableStateOf(sanitizedInitial.seedHue) }
    var saturation by rememberSaveable {
        mutableStateOf(snapToWholePercent(sanitizedInitial.saturation))
    }
    var vibrancy by rememberSaveable { mutableStateOf(sanitizedInitial.vibrancy) }
    var contentScale by rememberSaveable {
        mutableStateOf(snapToWholePercent(sanitizedInitial.contentScale))
    }
    var backgroundAlpha by rememberSaveable {
        mutableStateOf(snapToWholePercent(sanitizedInitial.backgroundAlpha))
    }
    var darkModeOption by rememberSaveable { mutableStateOf(sanitizedInitial.darkMode) }
    var isDarkModeMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val liveAppearance = WidgetAppearance(
        seedHue = seedHue,
        saturation = saturation,
        vibrancy = vibrancy,
        contentScale = contentScale,
        backgroundAlpha = backgroundAlpha,
        darkMode = darkModeOption,
    )

    val context = LocalContext.current
    val previewRender by produceState<WidgetConfigPreviewRender?>(
        initialValue = null,
        isMediumWidget, appWidgetId, snapshot,
    ) {
        // Conflated live rendering: always render the LATEST control values, but never
        // queue more than one render. While a render is in flight, slider ticks only
        // overwrite the pending value, so a fast drag costs a few renders instead of one
        // full widget composition per tick — and no in-flight render is ever cancelled,
        // so the preview keeps updating continuously THROUGH the drag.
        // Read the state delegates INSIDE the lambda: snapshotFlow only re-emits for
        // snapshot state read in its block — a captured composition-local value would
        // freeze the preview at the first composition's appearance.
        snapshotFlow {
            WidgetAppearance(
                seedHue = seedHue,
                saturation = saturation,
                vibrancy = vibrancy,
                contentScale = contentScale,
                backgroundAlpha = backgroundAlpha,
                darkMode = darkModeOption,
            )
        }
            .conflate()
            .collect { appearance ->
                value = try {
                    composeWidgetPreviewRemoteViews(
                        context = context.applicationContext,
                        isMedium = isMediumWidget,
                        appearance = appearance,
                        snapshot = snapshot,
                        appWidgetId = appWidgetId,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // The compose helper deliberately propagates failures; for a live
                    // preview the right policy is graceful degradation — keep the last
                    // good render rather than crashing the config UI on a transient
                    // composition failure.
                    value
                }
            }
    }

    // Offscreen compositing so the wallpaper window's BlendMode.Clear punches a real
    // transparent hole in the opaque background instead of painting black over it. The
    // opaque background stays full-bleed; AppContentContainer caps/centers only the body
    // on wide screens (its chrome-haze source is a no-op here — this activity provides
    // no LocalChromeHazeState).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Pinned bar: the preview and the Cancel/Save actions stay pinned while only the
            // control rows between them scroll, so the bar exists only for the title (not a
            // scroll source). It consumes the status-bar inset; the body below pads the
            // remaining sides. Full-width on purpose — AppContentContainer caps only the body.
            HazeTopAppBar(
                title = {
                    val title = stringResource(R.string.widget_config_title)
                    Text(
                        text = title,
                        modifier = Modifier.cjkTextOffset(title, amount = (-1.5).dp),
                    )
                },
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            )
            AppContentContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.systemBars
                                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                        )
                        .padding(dimensionResource(R.dimen.padding_medium)),
                ) {
                    // The wallpaper window hugs the fitted preview height (preview + inset on
                    // every side) rather than claiming all leftover space. No content-size
                    // animation: the hole lands at its final size in one frame, and the widget
                    // fades in inside it once the first render arrives (see WidgetPreview).
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawRoundRect(
                                    color = Color.Black,
                                    cornerRadius = CornerRadius(
                                        WALLPAPER_WINDOW_CORNER_DP.dp.toPx(),
                                    ),
                                    blendMode = BlendMode.Clear,
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        WidgetPreview(
                            render = previewRender,
                            modifier = Modifier.padding(WIDGET_PREVIEW_WINDOW_INSET),
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    // The control rows take the remaining height and scroll: with six rows
                    // plus the dark-mode row the content overflows a portrait screen, so it
                    // must scroll between the pinned preview above and the pinned actions
                    // below. On tall screens where it all fits the rows top-align here and
                    // the buttons stay at the bottom, visually identical to a fixed layout.
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        HrtSection(title = null) {
                            item {
                                HueSliderRow(
                                    label = stringResource(R.string.widget_config_seed_hue),
                                    icon = painterResource(R.drawable.ic_palette),
                                    hue = seedHue,
                                    restingHue = remember { defaultSeedHue() },
                                    onHueChange = { seedHue = it },
                                    resetLabel = stringResource(
                                        R.string.widget_config_seed_dynamic,
                                    ),
                                    onReset = { seedHue = null },
                                )
                            }
                            item {
                                SliderRow(
                                    label = stringResource(R.string.widget_config_saturation),
                                    icon = painterResource(R.drawable.ic_invert_colors),
                                    iconSize = 18.dp,
                                    value = saturation,
                                    valueRange = 0f..1f,
                                    onValueChange = { saturation = snapToWholePercent(it) },
                                )
                            }
                            item {
                                SliderRow(
                                    label = stringResource(R.string.widget_config_vibrancy),
                                    icon = painterResource(R.drawable.ic_contrast),
                                    iconSize = 18.dp,
                                    value = vibrancy,
                                    valueRange = 0f..1f,
                                    onValueChange = { vibrancy = snapToWholePercent(it) },
                                )
                            }
                            item {
                                SliderRow(
                                    label = stringResource(
                                        R.string.settings_widget_content_scale,
                                    ),
                                    icon = painterResource(R.drawable.ic_loupe),
                                    iconSize = 18.dp,
                                    value = contentScale,
                                    valueRange = 0.5f..1.5f,
                                    onValueChange = { contentScale = snapToWholePercent(it) },
                                )
                            }
                            item {
                                SliderRow(
                                    label = stringResource(
                                        R.string.settings_widget_background_opacity,
                                    ),
                                    icon = painterResource(R.drawable.ic_blur_linear),
                                    iconSize = 18.dp,
                                    value = backgroundAlpha,
                                    valueRange = 0.5f..1f,
                                    onValueChange = { backgroundAlpha = snapToWholePercent(it) },
                                )
                            }
                            item {
                                Box {
                                    PreferenceSegmentedListItem(
                                        title = stringResource(
                                            R.string.settings_widget_dark_mode,
                                        ),
                                        supportingText = stringResource(darkModeOption.labelRes),
                                        onClick = { isDarkModeMenuExpanded = true },
                                        leadingContent = {
                                            RowLeadingIcon(
                                                painterResource(R.drawable.ic_dark_mode),
                                                size = 24.dp,
                                            )
                                        },
                                    )
                                    HrtDropdownMenu(
                                        expanded = isDarkModeMenuExpanded,
                                        onDismissRequest = { isDarkModeMenuExpanded = false },
                                        modifier = Modifier.width(IntrinsicSize.Min),
                                        items = DarkModeOption.entries.map { option ->
                                            HrtDropdownMenuItem(
                                                text = stringResource(option.labelRes),
                                                onClick = { darkModeOption = option },
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = dimensionResource(R.dimen.padding_medium)),
                        horizontalArrangement = Arrangement.spacedBy(
                            dimensionResource(R.dimen.padding_small),
                        ),
                    ) {
                        HrtFilledTonalButton(
                            text = stringResource(R.string.cancel),
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                        )
                        HrtButton(
                            text = stringResource(R.string.save),
                            onClick = { onSave(liveAppearance) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

// A static (non-clickable) segmented row hosting a labelled slider, matching the
// label/percentage layout of the in-app dialog but as an HrtSection row.
@Composable
private fun SliderRow(
    label: String,
    icon: Painter,
    iconSize: Dp = 22.dp,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    EditorSegmentedListItem {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RowLeadingIcon(icon, size = iconSize)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).cjkTextOffset(label),
                )
                Text(
                    text = "${(value * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    // Keyed on the label on purpose: the "NN%" text is always non-CJK, so
                    // keying on its own text would never offset it — it must follow the
                    // label's CJK offset to stay baseline-aligned with it.
                    modifier = Modifier.cjkTextOffset(label)
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
            )
        }
    }
}

// A hue slider row with a live swatch and a reset affordance. While [hue] is null
// the slider rests at [restingHue] (the value the system would derive) and the
// reset button is disabled; grabbing the slider promotes the resting value to an
// explicit pick.
@Composable
private fun HueSliderRow(
    label: String,
    icon: Painter,
    hue: Float?,
    restingHue: Float,
    onHueChange: (Float) -> Unit,
    resetLabel: String,
    onReset: () -> Unit,
) {
    EditorSegmentedListItem {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RowLeadingIcon(icon, size = 18.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).cjkTextOffset(label),
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(hueSwatchColor(hue ?: restingHue), CircleShape),
                )
                TextButton(onClick = onReset, enabled = hue != null) {
                    Text(resetLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
            Slider(
                value = hue ?: restingHue,
                onValueChange = onHueChange,
                valueRange = 0f..360f,
            )
        }
    }
}

// Tone-60/chroma-48 cut: a recognizable, mode-independent preview of the hue itself.
private fun hueSwatchColor(hue: Float): Color =
    Color(Hct.from(hue.toDouble(), 48.0, 60.0).toInt())

// Mirrors SettingsScreen's SettingsLeadingIconSlot (private there) for this screen's rows.
@Composable
private fun RowLeadingIcon(painter: Painter, size: Dp) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size)
        )
    }
}

// Hosts the composed RemoteViews, fully inert: intercepted touches, blocked descendant
// focus, and hidden a11y descendants, so the widget's quick-log / navigation
// PendingIntents can never fire from the preview (a touch overlay alone would still
// leak d-pad and accessibility activations). Laid out at the composed widget size and
// visually fit-scaled about its center so content renders at its real baseline. Renders
// nothing until the first render lands (the window just shows wallpaper meanwhile).
@Composable
private fun WidgetPreview(
    render: WidgetConfigPreviewRender?,
    modifier: Modifier = Modifier,
) {
    // In @Preview (inspection mode) no RemoteViews render exists; reserve the medium
    // reference footprint (306x276dp, mirroring the private MEDIUM_WIDGET_PREVIEW_SIZE)
    // so the wallpaper window still lays out at a realistic size.
    val sizeDp = render?.sizeDp
        ?: if (LocalInspectionMode.current) DpSize(306.dp, 276.dp) else return
    BoxWithConstraints(modifier = modifier) {
        // Fit inside the constraints (the breathing-room inset is now this composable's outer
        // padding, so maxWidth/maxHeight already exclude it); never scale UP past the true
        // size. With the window hugging its content the height is loosely constrained — fall
        // back to a width-only fit if maxHeight is unbounded so a requiredSize child can't
        // overflow the screen.
        val widthFit = maxWidth / sizeDp.width
        val fit = if (maxHeight.isFinite) {
            min(1f, min(widthFit, maxHeight / sizeDp.height))
        } else {
            min(1f, widthFit)
        }
        Box(
            modifier = Modifier.size(sizeDp.width * fit, sizeDp.height * fit),
            contentAlignment = Alignment.Center,
        ) {
            if (render != null) {
                val remoteViews = render.remoteViews
                // One-shot fade-in: this branch first composes when the FIRST render lands
                // (until then the early return above keeps the whole tree out), so the alpha
                // animates 0→1 exactly once; later re-renders swap views in place at full
                // opacity.
                val appearAlpha = remember { Animatable(0f) }
                LaunchedEffect(Unit) { appearAlpha.animateTo(1f, tween()) }
                AndroidView(
                    modifier = Modifier
                        .requiredSize(sizeDp.width, sizeDp.height)
                        .graphicsLayer {
                            alpha = appearAlpha.value
                            scaleX = fit
                            scaleY = fit
                        },
                    factory = { viewContext ->
                        object : FrameLayout(viewContext) {
                            override fun onInterceptTouchEvent(ev: MotionEvent?) = true
                        }.apply {
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            isFocusable = false
                            importantForAccessibility =
                                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        }
                    },
                    update = { container ->
                        // Skip no-op reapplies on unrelated recompositions: only rebuild when a
                        // new RemoteViews instance arrives (every real preview change is a new
                        // instance).
                        if (container.tag !== remoteViews) {
                            container.tag = remoteViews
                            container.removeAllViews()
                            // Apply with the application context, NOT the activity context: the
                            // AppCompat activity's LayoutInflater factory would inflate ImageView
                            // as AppCompatImageView, whose setters lack @RemotableViewMethod,
                            // crashing RemoteViews actions. The launcher applies these
                            // RemoteViews with a non-AppCompat context too.
                            container.addView(
                                remoteViews.apply(container.context.applicationContext, container)
                            )
                        }
                    },
                )
            }
        }
    }
}

// Mirrors SettingsScreen.snapToWholePercent (private there): keeps the saved value
// identical to the displayed "NN%" label.
private fun snapToWholePercent(value: Float): Float = (value * 100).roundToInt() / 100f

private const val WALLPAPER_WINDOW_CORNER_DP = 28

// Breathing-room inset applied as the preview's outer padding, so the wallpaper window
// (which hugs this composable) stands off the fit-scaled preview on every side.
private val WIDGET_PREVIEW_WINDOW_INSET = 24.dp

// The window region renders as an empty reference-size hole here: no RemoteViews can be
// composed in inspection mode (and on-device the hole shows the wallpaper anyway).
@Preview(
    name = "Widget Config Screen",
    showBackground = true,
    widthDp = 420,
    heightDp = 920,
)
@Composable
private fun WidgetConfigScreenPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        WidgetConfigScreen(
            initialAppearance = WidgetAppearance.Default.copy(backgroundAlpha = 0.8f),
            isMediumWidget = true,
            appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID,
            snapshot = null,
            onSave = {},
            onCancel = {},
        )
    }
}
