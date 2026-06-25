@file:OptIn(ExperimentalMaterial3Api::class)

package com.mkx.hrttracker.ui.components

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.BasicAlertDialog as MaterialBasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CenterAlignedTopAppBar as MaterialCenterAlignedTopAppBar
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog as MaterialDatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet as MaterialModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.TimePickerDialog as MaterialTimePickerDialog
import androidx.compose.material3.TopAppBar as MaterialTopAppBar
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.hideBottomSheet
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazePositionStrategy
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

val LocalHazeBlurEnabled = staticCompositionLocalOf { isHazeBlurSupported() }
val LocalChromeHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * True while composing inside a [HazeBottomSheetSurface]. Components whose default
 * container color blends into the translucent sheet material (e.g. connected button
 * groups) use this to lift their container to a higher-contrast tone.
 */
val LocalHazeBottomSheet = staticCompositionLocalOf { false }

internal const val TopAppBarScrolledOverlapThreshold = 0.01f

/**
 * Maps the bar's content overlap to chrome opacity. The raw fraction is the
 * scrolled distance over the bar height; doubling it reaches full chrome once half
 * the bar is covered, so a slow scroll reads as a deliberate fade instead of a
 * barely-there ramp, while still tracking the content exactly (never lagging a
 * fast fling the way a time-based fade does).
 */
internal fun topAppBarOverlapAlpha(overlappedFraction: Float): Float {
    return (overlappedFraction * 2f).coerceIn(0f, 1f)
}

@Composable
fun rememberChromeHazeState(): HazeState = rememberHazeState(
    // Chrome haze states are consumed by effects in different windows: the in-window
    // top/bottom bars plus bottom sheets and dialogs, which live in their own windows
    // and sample the app window's content. Haze 2.0's Auto strategy resolves per
    // effect node but stores the result in the shared HazeState.resolvedStrategy, so
    // a same-window and a cross-window effect on one state overwrite each other in an
    // endless snapshot loop, freezing the UI (seen on the medication log entry sheet).
    // Pinning Screen coordinates keeps every consumer in one coordinate system and
    // matches Haze 1.x behavior on Android.
    positionStrategy = HazePositionStrategy.Screen,
)

/**
 * Top app bar window insets with a synchronously measured status-bar fallback.
 *
 * Compose's first frame is laid out before the window-insets dispatch reaches the
 * composition: the initial composition runs pre-attach with all insets at zero, and
 * the corrected values only land on the next frame (a long-standing toolkit gap —
 * see https://github.com/google/accompanist/issues/155, which still applies to
 * foundation's WindowInsets on cold start). Frame #0 therefore lays the bar out a
 * status-bar-height too short. Pre-haze the opaque bar camouflaged that frame; with
 * content drawn behind a transparent bar it shows as content overlapping the bar,
 * then jumping down — and a fast cold start puts exactly that frame on screen.
 *
 * Unioning with the window-metrics height (API 30+; reports the bar's geometry
 * synchronously, even while it is hidden) makes frame #0 match the final layout with
 * no startup delay; once the live inset arrives the union resolves to the same
 * value, so nothing moves. Below API 30 this is a no-op, which is fine: blur — and
 * with it the transparent bar that makes the artifact visible — requires API 31+.
 *
 * This is the default insets for every [HazeTopAppBar]: cold start puts the home
 * screen on screen, but process-death restore recomposes whichever tab was active
 * with the same first-frame gap.
 */
@Composable
private fun topAppBarWindowInsetsWithStartupFallback(): WindowInsets {
    val context = LocalContext.current
    // remember(context) is invalidated on rotation despite android:configChanges:
    // MainActivity provides a configuration-keyed ContextWrapper as LocalContext,
    // so a stale pre-rotation status-bar height is never served from this cache.
    val fallbackStatusBarTop = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            statusBarHeightFromWindowMetrics(context)
        } else {
            0
        }
    }
    return TopAppBarDefaults.windowInsets.union(WindowInsets(top = fallbackStatusBarTop))
}

@RequiresApi(Build.VERSION_CODES.R)
private fun statusBarHeightFromWindowMetrics(context: Context): Int {
    val windowManager = context.getSystemService(WindowManager::class.java) ?: return 0
    return windowManager.currentWindowMetrics.windowInsets
        .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars())
        .top
}

fun isHazeBlurSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
    return sdkInt >= Build.VERSION_CODES.S
}

fun effectiveHazeBlurEnabled(
    preferenceEnabled: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean {
    return preferenceEnabled && isHazeBlurSupported(sdkInt)
}

@Composable
fun Modifier.hazeSourceArea(state: HazeState?): Modifier {
    return if (!LocalHazeBlurEnabled.current || state == null) this else hazeSource(state)
}

@Composable
fun Modifier.hazeChrome(
    state: HazeState? = LocalChromeHazeState.current,
    enabled: Boolean = true,
    alpha: (() -> Float)? = null,
): Modifier {
    if (!LocalHazeBlurEnabled.current || !enabled || state == null) return this

    val style = HazeMaterials.thin(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
    return hazeEffect(state = state) {
        // Blur a downscaled copy of the source — Auto picks the factor from the blur
        // radius, where the lost detail is imperceptible. Cuts the per-frame GPU cost.
        // inputScale = HazeInputScale.Auto
        blurEffect {
            this.style = style
            if (alpha != null) {
                this.alpha = alpha().coerceIn(0f, 1f)
            }
        }
    }
}

/**
 * Top app bar chrome that tracks the content actually covering the bar.
 *
 * [TopAppBarState.overlappedFraction] is the scrolled distance over the bar height
 * — exactly the portion of the bar the content overlaps — so driving the chrome
 * from it (through [topAppBarOverlapAlpha]) fades it in lockstep with the content
 * sliding underneath: a slow scroll ramps it gradually, and a fast fling reaches
 * full chrome in the same frames the content arrives, with no unstyled flash (a
 * time-based fade lags the content there).
 *
 * With blur enabled this is the blur's opacity, read inside the effect block which
 * haze re-evaluates on snapshot changes; the effect node attaches only once the bar
 * is overlapped at all. With blur disabled the same overlap drives a draw-time lerp
 * between the vanilla container and scrolled-container colors, replacing M3's
 * threshold-triggered time-based crossfade (the Material containers stay
 * transparent via [hazeTopAppBarColors] in both modes). Either way, per-scroll-frame
 * updates skip recomposition.
 */
@Composable
private fun Modifier.hazeTopAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    state: HazeState? = LocalChromeHazeState.current,
): Modifier {
    if (LocalHazeBlurEnabled.current && state != null) {
        return hazeChrome(
            state = state,
            enabled = topAppBarHazeEnabled(scrollBehavior),
            alpha = { topAppBarOverlapAlpha(scrollBehavior.state.overlappedFraction) },
        )
    }

    val defaultColors = TopAppBarDefaults.topAppBarColors()
    val containerColor = defaultColors.containerColor
    val scrolledContainerColor = defaultColors.scrolledContainerColor
    return drawBehind {
        drawRect(
            lerp(
                containerColor,
                scrolledContainerColor,
                topAppBarOverlapAlpha(scrollBehavior.state.overlappedFraction),
            )
        )
    }
}

@Composable
fun Modifier.hazeBottomSheet(
    state: HazeState? = LocalChromeHazeState.current,
): Modifier {
    if (!LocalHazeBlurEnabled.current || state == null) return this

    val style = HazeMaterials.regular(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
    return hazeEffect(state = state) {
        // inputScale = HazeInputScale.Auto
        blurEffect {
            this.style = style
        }
    }
}

@Composable
fun Modifier.hazeDialog(
    state: HazeState? = LocalChromeHazeState.current,
    shape: Shape = AlertDialogDefaults.shape,
): Modifier {
    if (!LocalHazeBlurEnabled.current || state == null) return this

    val style = HazeMaterials.regular(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
    return this.clip(shape)
        .hazeEffect(state = state) {
            // inputScale = HazeInputScale.Auto
            blurEffect {
                this.style = style
                blurredEdgeTreatment = BlurredEdgeTreatment(shape)
            }
        }
}

/**
 * Whether components inside a [HazeBottomSheetSurface] should swap their opaque
 * containers for a blur treatment: we're composing on a haze sheet and chrome
 * blur is actually active.
 */
@Composable
fun hazeSheetBlurActive(): Boolean {
    return LocalHazeBottomSheet.current &&
        LocalHazeBlurEnabled.current &&
        LocalChromeHazeState.current != null
}

/**
 * Thick blur for a surface sitting on a haze bottom sheet, tinted with the
 * surface's own [containerColor]. The caller draws its actual container
 * transparent so the blur shows through; gate both on [hazeSheetBlurActive].
 */
@Composable
fun Modifier.hazeSheetSurface(
    containerColor: Color,
    shape: Shape,
    state: HazeState? = LocalChromeHazeState.current,
): Modifier {
    if (!hazeSheetBlurActive() || state == null) return this

    val style = HazeMaterials.thick(containerColor = containerColor)
    return this.clip(shape)
        .hazeEffect(state = state) {
            // inputScale = HazeInputScale.Auto
            blurEffect {
                this.style = style
                blurredEdgeTreatment = BlurredEdgeTreatment(shape)
            }
        }
}

@Composable
private fun hazeBottomSheetContainerColor(
    // Like the dialog gate below, require an actual haze state before going
    // transparent: hazeBottomSheet() no-ops on a null state, so a transparent
    // container without it renders the sheet invisible.
    enabled: Boolean = LocalHazeBlurEnabled.current && LocalChromeHazeState.current != null,
): Color {
    val defaultColor = BottomSheetDefaults.ContainerColor
    if (!enabled) return defaultColor

    return defaultColor.copy(alpha = 0f)
}

private fun hazeBottomSheetContentWindowInsets(): WindowInsets = WindowInsets(0, 0, 0, 0)

@Composable
fun hazeDialogContainerColor(
    enabled: Boolean = LocalHazeBlurEnabled.current && LocalChromeHazeState.current != null,
    containerColor: Color = AlertDialogDefaults.containerColor,
): Color {
    if (!enabled) return containerColor

    return containerColor.copy(alpha = 0.2f)
}

@Composable
fun hazeDatePickerColors(
    colors: DatePickerColors = DatePickerDefaults.colors(),
): DatePickerColors {
    return colors.copy(
        containerColor = hazeDialogContainerColor(
            containerColor = colors.containerColor,
        ),
    )
}

// Whether the route hosting a modal is still the current back-stack
// destination, provided per-route by the NavHost. Must be backed by a live
// navController.currentBackStackEntry read — currentBackStackEntryAsState()
// lags navigate()'s synchronous lifecycle drop by a frame. The custom local
// does propagate into nested dialog/popup windows, but the gate below never
// engages there in practice: those windows re-provide LocalLifecycleOwner as
// the Activity's, which sits at RESUMED whenever the app is foregrounded, so
// a nested modal (a picker opened from inside a sheet or dialog) composes
// with allowed = true and is neither held nor dropped. Only route-level
// modals get the gate; the nested gap is reachable only through the
// deep-link lock bypass and is accepted (see pr60-review.md, finding 9).
// Null outside the NavHost (previews, onboarding, NavHost-level hosts):
// treated as still-current.
internal val LocalModalHostIsCurrentDestination =
    staticCompositionLocalOf<(() -> Boolean)?> { null }

// Route content stays composed and interactive while its exit transition
// runs, so a tap can open a dialog/sheet after navigation has already begun.
// The modal window renders above everything — it would blink over the
// destination page and vanish when the outgoing route is disposed. Hold a
// modal back when it is first composed while its host lifecycle is below
// RESUMED. Whether the host route is still the current back-stack destination
// then decides drop vs show:
//  - no longer current: the open raced a navigation the user already won —
//    clear the open state (onDismissRequest) so the modal doesn't re-present
//    "out of nowhere" on the next return to this page. Checked again when a
//    still-held gate is disposed, because a navigation that wins mid-hold
//    disposes the route at the end of its exit transition without any
//    lifecycle event the hold could observe.
//  - still current: settling in (tab return, config change, paused
//    activity) — show when the host resumes, as before.
// Once shown, a modal stays shown through later lifecycle dips (e.g. the
// activity pausing behind a system dialog).
@Composable
internal fun rememberModalWindowAllowed(onDismissRequest: () -> Unit): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val isHostCurrentDestination = LocalModalHostIsCurrentDestination.current
    var allowed by remember {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    if (!allowed) {
        val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
        // One drop per held window, whichever of the two checks fires first.
        val dropped = remember { mutableStateOf(false) }
        val leaving = isHostCurrentDestination?.invoke() == false
        LaunchedEffect(leaving) {
            if (leaving) {
                if (!dropped.value) {
                    dropped.value = true
                    currentOnDismissRequest()
                }
            } else {
                lifecycle.withResumed { }
                allowed = true
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                if (!dropped.value && isHostCurrentDestination?.invoke() == false) {
                    dropped.value = true
                    currentOnDismissRequest()
                }
            }
        }
    }
    return allowed
}

/**
 * Modal bottom sheet with the haze chrome wiring applied: the Material sheet's
 * own container is transparent and inset-free, and the [HazeBottomSheetSurface]
 * inside draws the blur (or the opaque fallback container), the modal content
 * insets, and an accessible drag handle. Screens must use this wrapper instead
 * of Material3's ModalBottomSheet; hand-assembling the pieces at every call
 * site is how transparent-sheet bugs slip in.
 */
@Composable
fun HazeModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!rememberModalWindowAllowed(onDismissRequest = onDismissRequest)) {
        return
    }
    // Every modal window locks top-level navigation for its whole composition,
    // covering the appear/disappear frames where the window doesn't yet (or no
    // longer) blocks chrome taps itself.
    NavigationLockEffect(active = true)
    MaterialModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = hazeBottomSheetContainerColor(),
        dragHandle = null,
        contentWindowInsets = { hazeBottomSheetContentWindowInsets() },
    ) {
        HazeBottomSheetSurface(
            sheetState = sheetState,
            onDismissRequest = onDismissRequest,
            dragHandle = dragHandle,
            content = content,
        )
    }
}

@Composable
private fun HazeBottomSheetSurface(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalHazeBottomSheet provides true) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .hazeBottomSheet()
                .background(hazeBottomSheetContainerColor())
                .windowInsetsPadding(contentWindowInsets()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (dragHandle != null) {
                // The ModalBottomSheet dragHandle slot stays null (the handle must
                // render inside this surface, on top of the blur), but Material3 only
                // attaches tap-to-dismiss and the TalkBack dismiss action when that
                // slot is non-null — so re-add them here. All haze sheets skip the
                // partially-expanded state, so dismiss is the only M3 handle
                // affordance that applies.
                val scope = rememberCoroutineScope()
                val dismissLabel = stringResource(R.string.bottom_sheet_dismiss)
                Box(
                    modifier = Modifier
                        .clickable { hideBottomSheet(scope, sheetState, onDismissRequest) }
                        .semantics(mergeDescendants = true) {
                            dismiss(dismissLabel) {
                                hideBottomSheet(scope, sheetState, onDismissRequest)
                                true
                            }
                        },
                ) {
                    dragHandle()
                }
            }
            content()
        }
    }
}

@Composable
fun HazeAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = hazeDialogContainerColor(),
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    if (!rememberModalWindowAllowed(onDismissRequest = onDismissRequest)) {
        return
    }
    NavigationLockEffect(active = true)
    MaterialAlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.hazeDialog(shape = shape),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

@Composable
fun HazeBasicAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = AlertDialogDefaults.shape,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    if (!rememberModalWindowAllowed(onDismissRequest = onDismissRequest)) {
        return
    }
    NavigationLockEffect(active = true)
    MaterialBasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.hazeDialog(shape = shape),
        properties = properties,
        content = content,
    )
}

@Composable
fun HazeDialogSurface(
    modifier: Modifier = Modifier,
    shape: Shape = AlertDialogDefaults.shape,
    color: Color = hazeDialogContainerColor(),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        content = content,
    )
}

@Composable
fun HazeDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    shape: Shape = DatePickerDefaults.shape,
    tonalElevation: Dp = DatePickerDefaults.TonalElevation,
    colors: DatePickerColors = DatePickerDefaults.colors(),
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit,
) {
    val hazeColors = hazeDatePickerColors(colors)

    if (!rememberModalWindowAllowed(onDismissRequest = onDismissRequest)) {
        return
    }
    NavigationLockEffect(active = true)
    MaterialDatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.hazeDialog(shape = shape),
        dismissButton = dismissButton,
        shape = shape,
        tonalElevation = tonalElevation,
        colors = hazeColors,
        properties = properties,
        content = content,
    )
}

@Composable
fun HazeTimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    modeToggleButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    shape: Shape = TimePickerDialogDefaults.shape,
    containerColor: Color = TimePickerDialogDefaults.containerColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!rememberModalWindowAllowed(onDismissRequest = onDismissRequest)) {
        return
    }
    NavigationLockEffect(active = true)
    MaterialTimePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        title = title,
        modifier = modifier.hazeDialog(shape = shape),
        properties = properties,
        modeToggleButton = modeToggleButton,
        dismissButton = dismissButton,
        shape = shape,
        containerColor = hazeDialogContainerColor(containerColor = containerColor),
        content = content,
    )
}

@Composable
private fun topAppBarHazeEnabled(scrollBehavior: TopAppBarScrollBehavior): Boolean {
    return remember(scrollBehavior) {
        derivedStateOf {
            scrollBehavior.state.overlappedFraction > TopAppBarScrolledOverlapThreshold
        }
    }.value
}

/**
 * The app's top app bar: Material3's bar with the haze chrome wiring applied —
 * [hazeTopAppBarColors] keeps the Material containers transparent and
 * [hazeTopAppBar] draws the bar background (blur, or the overlap-tracking color
 * lerp) from the scroll overlap. Screens must use these wrappers instead of the
 * Material bars; hand-assembling the pieces at every call site is how
 * transparent-bar bugs slip in.
 */
@Composable
fun HazeTopAppBar(
    title: @Composable () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = topAppBarWindowInsetsWithStartupFallback(),
) {
    MaterialTopAppBar(
        title = title,
        modifier = modifier.hazeTopAppBar(scrollBehavior),
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        colors = hazeTopAppBarColors(),
        scrollBehavior = scrollBehavior,
    )
}

/** [HazeTopAppBar] variant of Material3's CenterAlignedTopAppBar. */
@Composable
fun HazeCenterAlignedTopAppBar(
    title: @Composable () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = topAppBarWindowInsetsWithStartupFallback(),
) {
    MaterialCenterAlignedTopAppBar(
        title = title,
        modifier = modifier.hazeTopAppBar(scrollBehavior),
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        colors = hazeTopAppBarColors(),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun hazeTopAppBarColors(): TopAppBarColors {
    // The Material containers stay transparent in both blur modes: hazeTopAppBar
    // draws the bar background itself (blur, or the overlap-tracking color lerp),
    // so M3's internal threshold-triggered crossfade must never paint over it.
    val defaultColors = TopAppBarDefaults.topAppBarColors()
    return defaultColors.copy(
        containerColor = defaultColors.containerColor.copy(alpha = 0f),
        scrolledContainerColor = defaultColors.scrolledContainerColor.copy(alpha = 0f),
    )
}

@Composable
fun hazeNavigationSuiteColors(
    // Gated on the state like the sheet/dialog containers: hazeChrome(null)
    // no-ops, and a transparent bar without chrome behind it is invisible.
    state: HazeState?,
    enabled: Boolean = LocalHazeBlurEnabled.current && state != null,
): NavigationSuiteColors {
    if (!enabled) return NavigationSuiteDefaults.colors()

    return NavigationSuiteDefaults.colors(
        shortNavigationBarContainerColor = Color.Transparent,
        navigationBarContainerColor = Color.Transparent,
    )
}
