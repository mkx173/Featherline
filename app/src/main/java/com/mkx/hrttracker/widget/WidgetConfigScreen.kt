package com.mkx.hrttracker.widget

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.viewinterop.AndroidView
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtDropdownMenu
import com.mkx.hrttracker.ui.components.HrtDropdownMenuItem
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.settings.labelRes
import kotlinx.coroutines.CancellationException
import kotlin.math.min
import kotlin.math.roundToInt

// Full-screen launcher-reconfigure layout: an opaque scaffold whose "wallpaper window"
// is a transparent hole (BlendMode.Clear punch-out) showing the system wallpaper —
// supplied by windowShowWallpaper in Theme.HrtTracker.WidgetConfig — with the live
// widget preview centered in it, and the appearance controls as HrtSection rows below.
// Deliberately does NOT reuse the in-app WidgetAppearanceDialog.
@Composable
internal fun WidgetConfigScreen(
    initialContentScale: Float,
    initialBackgroundAlpha: Float,
    initialDarkModeOption: DarkModeOption,
    isMediumWidget: Boolean,
    appWidgetId: Int,
    snapshot: WidgetSnapshotRecord?,
    onSave: (Float, Float, DarkModeOption) -> Unit,
    onCancel: () -> Unit,
) {
    var contentScale by rememberSaveable {
        mutableStateOf(snapToWholePercent(initialContentScale.coerceIn(0.5f, 1.5f)))
    }
    var backgroundAlpha by rememberSaveable {
        mutableStateOf(snapToWholePercent(initialBackgroundAlpha.coerceIn(0.5f, 1f)))
    }
    var darkModeOption by rememberSaveable { mutableStateOf(initialDarkModeOption) }
    var isDarkModeMenuExpanded by rememberSaveable { mutableStateOf(false) }

    // Same option→forcedDark mapping the snapshot builder bakes, so the preview shows
    // exactly what the home-screen widget will render after Save.
    val forcedDark = when (darkModeOption) {
        DarkModeOption.LIGHT -> false
        DarkModeOption.DARK -> true
        DarkModeOption.FOLLOW_SYSTEM -> null
    }
    val context = LocalContext.current
    val previewRender by produceState<WidgetConfigPreviewRender?>(
        initialValue = null,
        contentScale, backgroundAlpha, forcedDark, isMediumWidget, appWidgetId, snapshot,
    ) {
        value = try {
            composeWidgetPreviewRemoteViews(
                context = context.applicationContext,
                isMedium = isMediumWidget,
                contentScale = contentScale,
                backgroundAlpha = backgroundAlpha,
                forcedDark = forcedDark,
                snapshot = snapshot,
                appWidgetId = appWidgetId,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The compose helper deliberately propagates failures; for a live preview the
            // right policy is graceful degradation — keep the last good render rather than
            // crashing the config UI on a transient composition failure.
            value
        }
    }

    // Offscreen compositing so the wallpaper window's BlendMode.Clear punches a real
    // transparent hole in the opaque background instead of painting black over it.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(dimensionResource(R.dimen.padding_medium)),
        ) {
            // The wallpaper window hugs the fitted preview height (preview + inset on every
            // side) rather than claiming all leftover space; animateContentSize smooths the
            // first-frame arrival when the preview composes (render goes null → sized).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .drawBehind {
                        drawRoundRect(
                            color = Color.Black,
                            cornerRadius = CornerRadius(WALLPAPER_WINDOW_CORNER_DP.dp.toPx()),
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
            HrtSection(title = stringResource(R.string.settings_widget_appearance)) {
                item {
                    SliderRow(
                        label = stringResource(R.string.settings_widget_content_scale),
                        value = contentScale,
                        valueRange = 0.5f..1.5f,
                        onValueChange = { contentScale = snapToWholePercent(it) },
                    )
                }
                item {
                    SliderRow(
                        label = stringResource(R.string.settings_widget_background_opacity),
                        value = backgroundAlpha,
                        valueRange = 0.5f..1f,
                        onValueChange = { backgroundAlpha = snapToWholePercent(it) },
                    )
                }
                item {
                    Box {
                        PreferenceSegmentedListItem(
                            title = stringResource(R.string.settings_widget_dark_mode),
                            supportingText = stringResource(darkModeOption.labelRes),
                            onClick = { isDarkModeMenuExpanded = true },
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
            Spacer(modifier = Modifier.weight(1f))
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
                    onClick = { onSave(contentScale, backgroundAlpha, darkModeOption) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// A static (non-clickable) segmented row hosting a labelled slider, matching the
// label/percentage layout of the in-app dialog but as an HrtSection row.
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    EditorSegmentedListItem {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "${(value * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
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
    val sizeDp = render?.sizeDp ?: return
    val remoteViews = render.remoteViews
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
            AndroidView(
                modifier = Modifier
                    .requiredSize(sizeDp.width, sizeDp.height)
                    .graphicsLayer {
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
                    // Skip no-op reapplies on unrelated recompositions: only rebuild when a new
                    // RemoteViews instance arrives (every real preview change is a new instance).
                    if (container.tag !== remoteViews) {
                        container.tag = remoteViews
                        container.removeAllViews()
                        // Apply with the application context, NOT the activity context: the AppCompat
                        // activity's LayoutInflater factory would inflate ImageView as AppCompatImageView,
                        // whose setters lack @RemotableViewMethod, crashing RemoteViews actions. The
                        // launcher applies these RemoteViews with a non-AppCompat context too.
                        container.addView(
                            remoteViews.apply(container.context.applicationContext, container)
                        )
                    }
                },
            )
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
