package com.mkx.hrttracker.widget

import android.appwidget.AppWidgetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.layout.Spacer
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.PrideFlag
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.ui.journal.AnchorSelectorSheet
import com.mkx.hrttracker.ui.journal.FlagSwatch
import com.mkx.hrttracker.ui.journal.HeroBackgroundColors
import com.mkx.hrttracker.ui.journal.NoneSwatch
import com.mkx.hrttracker.ui.journal.anchorIconRes
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtDropdownMenu
import com.mkx.hrttracker.ui.components.HrtDropdownMenuItem
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.HazeTopAppBar
import com.mkx.hrttracker.ui.components.HrtPill
import com.mkx.hrttracker.ui.components.HrtPillSize
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WidgetConfigScreen(
    initialAppearance: WidgetAppearance,
    configType: WidgetConfigType,
    appWidgetId: Int,
    snapshot: WidgetSnapshotRecord?,
    anchors: List<com.mkx.hrttracker.model.journal.TrackedDate> = emptyList(),
    initialAnchorId: String? = null,
    initialBackgroundFlag: PrideFlag? = null,
    today: java.time.LocalDate = java.time.LocalDate.now(),
    onSaveAnchor: (
        appearance: WidgetAppearance,
        anchorId: String,
        backgroundFlag: PrideFlag?,
    ) -> Unit = { _, _, _ -> },
    onSave: (WidgetAppearance) -> Unit,
    onCancel: () -> Unit,
) {
    val isMediumWidget = configType == WidgetConfigType.MEDIUM
    val sanitizedInitial = remember(initialAppearance) { initialAppearance.sanitized() }
    var seedHue by rememberSaveable { mutableStateOf(sanitizedInitial.seedHue) }
    var saturation by rememberSaveable {
        mutableStateOf(snapToWholePercent(sanitizedInitial.saturation))
    }
    var balance by rememberSaveable {
        mutableStateOf(snapToWholePercent(sanitizedInitial.balance))
    }
    var contentScale by rememberSaveable {
        mutableStateOf(snapToWholePercent(sanitizedInitial.contentScale))
    }
    var backgroundAlpha by rememberSaveable {
        mutableStateOf(snapToWholePercent(sanitizedInitial.backgroundAlpha))
    }
    var darkModeOption by rememberSaveable { mutableStateOf(sanitizedInitial.darkMode) }
    var isDarkModeMenuExpanded by rememberSaveable { mutableStateOf(false) }

    // Preview-only light/dark flip, shown only while darkMode == FOLLOW_SYSTEM. Seeded
    // once from the system appearance at first composition; the saveable preserves the
    // user's flip across config changes. This NEVER touches darkModeOption / the saved
    // appearance — it only overrides the PREVIEW's resolved darkMode below.
    // Deliberately NOT isSystemInDarkTheme(): this AppCompat activity's configuration is
    // overridden by AppCompatDelegate.setDefaultNightMode() (the APP's dark-mode setting),
    // while the home-screen widget under FOLLOW_SYSTEM tracks the SYSTEM appearance —
    // which Resources.getSystem() reports unaffected by the per-activity override.
    val initialSystemDark = remember {
        Resources.getSystem().configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
    var previewDark by rememberSaveable { mutableStateOf(initialSystemDark) }

    // ANCHOR mode only: the per-instance anchor selection (null until chosen — Save stays
    // disabled meanwhile) plus the picker-sheet plumbing. Inert/unused in MEDIUM/LARGE.
    var selectedAnchorId by rememberSaveable { mutableStateOf(initialAnchorId) }
    var isAnchorSheetOpen by rememberSaveable { mutableStateOf(false) }
    val selectedAnchor = anchors.firstOrNull { it.id == selectedAnchorId }
    // Gradient-flag selection (ANCHOR mode). Non-null = a pride-flag wash layered over the
    // seeded card; null = plain card. Stored by name so rememberSaveable can persist it.
    var selectedFlagName by rememberSaveable { mutableStateOf(initialBackgroundFlag?.name) }
    val selectedFlag = selectedFlagName?.let { n -> PrideFlag.entries.firstOrNull { it.name == n } }
    // Fold state for the gradient swatch grid; collapsed by default so the common case
    // (no gradient) stays one row tall.
    var isGradientExpanded by rememberSaveable { mutableStateOf(false) }

    val liveAppearance = WidgetAppearance(
        seedHue = seedHue,
        saturation = saturation,
        balance = balance,
        contentScale = contentScale,
        backgroundAlpha = backgroundAlpha,
        darkMode = darkModeOption,
    )

    val context = LocalContext.current
    // Resolved through the same path the render itself uses, so the wallpaper window
    // can reserve the preview's final footprint on the first frame — before the first
    // async render lands — without the two sizes ever diverging.
    val previewPlaceholderSizeDp = remember(configType, appWidgetId) {
        if (configType == WidgetConfigType.ANCHOR) {
            anchorWidgetPreviewSizeDp(context, appWidgetId)
        } else {
            widgetPreviewSizeDp(context, isMediumWidget, appWidgetId)
        }
    }
    // A very tall widget (e.g. a large instance on a tall layout) would otherwise scale the
    // preview up until it crowds the controls out. Cap the window so it never exceeds a
    // fraction of the screen; WidgetPreview fit-scales the widget down into the cap.
    val maxPreviewHeightDp =
        LocalConfiguration.current.screenHeightDp.dp * PREVIEW_MAX_HEIGHT_FRACTION
    val previewRender by produceState<WidgetConfigPreviewRender?>(
        initialValue = null,
        configType, appWidgetId, snapshot, selectedAnchorId, selectedFlagName,
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
                balance = balance,
                contentScale = contentScale,
                backgroundAlpha = backgroundAlpha,
                // Preview-only override: an explicit LIGHT/DARK selection renders as
                // chosen, but a FOLLOW_SYSTEM selection follows the previewDark flip so
                // the user can inspect both appearances without changing the saved value.
                darkMode = if (darkModeOption == DarkModeOption.FOLLOW_SYSTEM) {
                    if (previewDark) DarkModeOption.DARK else DarkModeOption.LIGHT
                } else {
                    darkModeOption
                },
            )
        }
            .conflate()
            .collect { appearance ->
                value = try {
                    if (configType == WidgetConfigType.ANCHOR) {
                        composeAnchorPreviewRemoteViews(
                            context = context.applicationContext,
                            appearance = appearance,
                            anchor = selectedAnchor,
                            backgroundFlag = selectedFlag,
                            appWidgetId = appWidgetId,
                        )
                    } else {
                        composeWidgetPreviewRemoteViews(
                            context = context.applicationContext,
                            isMedium = isMediumWidget,
                            appearance = appearance,
                            snapshot = snapshot,
                            appWidgetId = appWidgetId,
                        )
                    }
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
                    // animation: WidgetPreview reserves its final footprint from the
                    // placeholder size, so the hole lands at its final size on the FIRST
                    // frame and the widget fades in inside it once the first render arrives.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxPreviewHeightDp)
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
                            placeholderSizeDp = previewPlaceholderSizeDp,
                            modifier = Modifier.padding(WIDGET_PREVIEW_WINDOW_INSET),
                        )
                        // Preview-only light/dark flip, only meaningful while the saved
                        // selection follows the system. The icon shows the appearance the
                        // tap switches TO: a light preview offers the moon (→ dark), a dark
                        // preview offers the sun (→ light).
                        if (darkModeOption == DarkModeOption.FOLLOW_SYSTEM) {
                            FilledTonalIconButton(
                                onClick = { previewDark = !previewDark },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                                    .size(36.dp),
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (previewDark) {
                                            R.drawable.ic_sunny
                                        } else {
                                            R.drawable.ic_moon_stars
                                        },
                                    ),
                                    contentDescription = stringResource(
                                        R.string.widget_config_preview_toggle,
                                    ),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
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
                        if (configType == WidgetConfigType.ANCHOR) {
                            HrtSection(title = null) {
                                item {
                                    PreferenceSegmentedListItem(
                                        title = stringResource(
                                            R.string.widget_config_bg_gradient,
                                        ),
                                        onClick = {
                                            isGradientExpanded = !isGradientExpanded
                                        },
                                        leadingContent = {
                                            RowLeadingIcon(
                                                painterResource(R.drawable.ic_format_paint),
                                                size = 24.dp,
                                            )
                                        },
                                        trailingContent = {
                                            // Same rotating chevron as MainLowStockSection's
                                            // expandable header.
                                            val chevronRotation = animateFloatAsState(
                                                targetValue = if (isGradientExpanded) {
                                                    180f
                                                } else {
                                                    0f
                                                },
                                                label = "GradientExpandChevronRotation",
                                            ).value
                                            Row(
                                                verticalAlignment =
                                                    Alignment.CenterVertically,
                                                horizontalArrangement =
                                                    Arrangement.spacedBy(8.dp),
                                            ) {
                                                GradientSelectionSwatch(selectedFlag)
                                                Icon(
                                                    imageVector = Icons.Rounded.ExpandMore,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme
                                                        .onSurfaceVariant,
                                                    modifier = Modifier.graphicsLayer {
                                                        rotationZ = chevronRotation
                                                    },
                                                )
                                            }
                                        },
                                    )
                                }
                                animatedItem(visible = isGradientExpanded) {
                                    // None + 9 flags = 10 swatches → a centred 2×5 grid.
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.extraSmall,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow
                                    ) {
                                        FlowRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp)
                                                .selectableGroup(),
                                            maxItemsInEachRow = 5,
                                            horizontalArrangement = Arrangement.spacedBy(
                                                12.dp,
                                                Alignment.CenterHorizontally,
                                            ),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            NoneSwatch(
                                                selected = selectedFlag == null,
                                                onClick = { selectedFlagName = null },
                                            )
                                            PrideFlag.entries.forEach { flag ->
                                                FlagSwatch(
                                                    flag = flag,
                                                    selected = flag == selectedFlag,
                                                    onClick = { selectedFlagName = flag.name },
                                                )
                                            }
                                        }
                                    }
                                }
                                item {
                                    PreferenceSegmentedListItem(
                                        title = stringResource(
                                            R.string.anchor_config_row_title,
                                        ),
                                        supportingText = selectedAnchor?.name
                                            ?: stringResource(R.string.anchor_config_choose),
                                        onClick = { isAnchorSheetOpen = true },
                                        leadingContent = {
                                            RowLeadingIcon(
                                                painterResource(R.drawable.ic_event),
                                                size = 24.dp,
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))

                        HrtSection(title = null) {
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
                                    icon = painterResource(R.drawable.ic_deblur),
                                    iconSize = 18.dp,
                                    value = backgroundAlpha,
                                    valueRange = 0.5f..1f,
                                    onValueChange = { backgroundAlpha = snapToWholePercent(it) },
                                )
                            }
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
                                    icon = painterResource(R.drawable.ic_colors),
                                    iconSize = 18.dp,
                                    value = saturation,
                                    valueRange = 0f..1f,
                                    onValueChange = { saturation = snapToWholePercent(it) },
                                )
                            }
                            item {
                                SliderRow(
                                    label = stringResource(R.string.widget_config_balance),
                                    icon = painterResource(R.drawable.ic_contrast_circle),
                                    iconSize = 18.dp,
                                    value = balance,
                                    valueRange = 0f..1f,
                                    onValueChange = { balance = snapToWholePercent(it) },
                                    centered = true,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(
                            dimensionResource(R.dimen.padding_small),
                        ),
                    ) {
                        HrtFilledTonalButton(
                            text = stringResource(R.string.cancel),
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            compact = true
                        )
                        HrtButton(
                            text = stringResource(R.string.save),
                            onClick = {
                                if (configType == WidgetConfigType.ANCHOR) {
                                    selectedAnchorId?.let { onSaveAnchor(liveAppearance, it, selectedFlag) }
                                } else {
                                    onSave(liveAppearance)
                                }
                            },
                            enabled = configType != WidgetConfigType.ANCHOR ||
                                selectedAnchorId != null,
                            modifier = Modifier.weight(1f),
                            compact = true
                        )
                    }
                }
            }
        }
    }

    if (isAnchorSheetOpen) {
        AnchorSelectorSheet(
            title = stringResource(R.string.anchor_config_choose),
            anchors = anchors,
            today = today,
            onDismissRequest = { isAnchorSheetOpen = false },
            onSelect = { id -> selectedAnchorId = id },
        )
    }
}

// A static (non-clickable) segmented row hosting a labelled slider, matching the
// label/percentage layout of the in-app dialog but as an HrtSection row.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SliderRow(
    label: String,
    icon: Painter,
    iconSize: Dp = 22.dp,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    // When true this is a bidirectional axis anchored at the midpoint of valueRange
    // (light balance: 0.5 in 0..1). Renders the Material 3 Expressive centered track —
    // the active indicator grows from the midpoint out to the thumb — and shows the
    // readout as a signed offset from neutral (−50..+50) instead of 0..100%. The thumb
    // still moves freely.
    centered: Boolean = false,
) {
    EditorSegmentedListItem {
        Column {
            // Bidirectional axes read as a signed offset from neutral (e.g. +30 / 0 / -20);
            // ordinary axes read as a plain percentage of the value.
            val readout = if (centered) {
                centeredOffsetReadout(value, valueRange)
            } else {
                "${(value * 100).roundToInt()}%"
            }
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
                    text = readout,
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
                track = { sliderState ->
                    if (centered) {
                        WidgetCenteredSliderTrack(sliderState)
                    } else {
                        SliderDefaults.Track(sliderState = sliderState)
                    }
                },
            )
        }
    }
}

// A hue slider row with a live swatch and a reset affordance. While [hue] is null
// the slider rests at [restingHue] (the value the system would derive) and the
// reset button is disabled; grabbing the slider promotes the resting value to an
// explicit pick.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
                RowLeadingIcon(icon, size = 16.dp)
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
                Spacer(modifier = Modifier.width(12.dp))
                // SELECTED = Dynamic active (hue == null): the pill fills with the
                // secondaryContainer accent. Tapping it while already selected is a
                // no-op; tapping while an explicit hue is set resets back to Dynamic.
                val dynamicSelected = hue == null
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                ) {
                    HrtPill(
                        label = resetLabel,
                        containerColor = if (dynamicSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        size = HrtPillSize.Small,
                        onClick = if (dynamicSelected) null else onReset,
                    )
                }
            }
            Slider(
                value = hue ?: restingHue,
                onValueChange = onHueChange,
                // Ends at 359 so the canonical mod-360 stored value round-trips to the
                // same thumb position (360 would alias to 0 and snap the handle far-left).
                valueRange = 0f..359f,
                // The track IS the hue spectrum, so the thumb reads directly as the hue.
                track = { sliderState -> WidgetHueSpectrumTrack(sliderState) },
            )
        }
    }
}

// The Gradient row's trailing "current selection" indicator: the selected flag's chromatic
// strips in a circle (same paletteSeeds as the grid's FlagSwatch so the two never diverge),
// or the None swatch's block glyph when no flag is set. Purely indicative — the row itself
// toggles the fold.
private val GradientSelectionSwatchSize = 22.dp

@Composable
private fun GradientSelectionSwatch(flag: PrideFlag?) {
    if (flag == null) {
        Box(
            modifier = Modifier
                .size(GradientSelectionSwatchSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_block),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    } else {
        val strips = remember(flag) {
            HeroBackgroundColors.paletteSeeds(flag.seeds).map { Color(it) }
        }
        Row(
            modifier = Modifier
                .size(GradientSelectionSwatchSize)
                .clip(CircleShape),
        ) {
            strips.forEach { color ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(color),
                )
            }
        }
    }
}

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
// visually fit-scaled about its center so content renders at its real baseline. Until
// the first render lands it reserves the placeholder footprint but draws nothing (the
// window just shows wallpaper meanwhile), so the wallpaper window is already at its
// final size on the first frame instead of blinking in with the first render.
@Composable
private fun WidgetPreview(
    render: WidgetConfigPreviewRender?,
    placeholderSizeDp: DpSize,
    modifier: Modifier = Modifier,
) {
    val sizeDp = render?.sizeDp ?: placeholderSizeDp
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

// Upper bound on the pinned preview window as a fraction of the screen height, so a tall
// widget can't crowd out the scrolling controls beneath it. The preview still hugs its
// fitted height when shorter than this.
private const val PREVIEW_MAX_HEIGHT_FRACTION = 0.33f

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
            configType = WidgetConfigType.MEDIUM,
            appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID,
            snapshot = null,
            onSave = {},
            onCancel = {},
        )
    }
}
