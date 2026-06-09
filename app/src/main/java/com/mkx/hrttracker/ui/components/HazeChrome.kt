@file:OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalMaterial3Api::class)

package com.mkx.hrttracker.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.BasicAlertDialog as MaterialBasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog as MaterialDatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TimePickerDialog as MaterialTimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState

val LocalHazeBlurEnabled = staticCompositionLocalOf { isHazeBlurSupported() }
val LocalChromeHazeState = staticCompositionLocalOf<HazeState?> { null }

internal const val TopAppBarScrolledOverlapThreshold = 0.01f

@Composable
fun rememberChromeHazeState(): HazeState = rememberHazeState()

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
): Modifier {
    if (!LocalHazeBlurEnabled.current || !enabled || state == null) return this

    return hazeEffect(
        state = state,
        style = HazeMaterials.thin(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
    )
}

@Composable
fun Modifier.hazeBottomSheet(
    state: HazeState? = LocalChromeHazeState.current,
): Modifier {
    if (!LocalHazeBlurEnabled.current || state == null) return this

    return hazeEffect(
        state = state,
        style = HazeMaterials.regular(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        forceInvalidateOnPreDraw = true
    }
}

@Composable
fun Modifier.hazeDialog(
    state: HazeState? = LocalChromeHazeState.current,
    shape: Shape = AlertDialogDefaults.shape,
): Modifier {
    if (!LocalHazeBlurEnabled.current || state == null) return this

    return this.clip(shape)
        .hazeEffect(
            state = state,
            style = HazeMaterials.regular(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
        ) {
            blurredEdgeTreatment = BlurredEdgeTreatment(shape)
            forceInvalidateOnPreDraw = true
        }
}

@Composable
fun hazeBottomSheetContainerColor(
    enabled: Boolean = LocalHazeBlurEnabled.current,
): Color {
    val defaultColor = BottomSheetDefaults.ContainerColor
    if (!enabled) return defaultColor

    return defaultColor.copy(alpha = 0f)
}

fun hazeBottomSheetContentWindowInsets(): WindowInsets = WindowInsets(0, 0, 0, 0)

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

@Composable
fun HazeBottomSheetSurface(
    modifier: Modifier = Modifier,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .hazeBottomSheet()
            .background(hazeBottomSheetContainerColor())
            .windowInsetsPadding(contentWindowInsets()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        dragHandle?.invoke()
        content()
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
fun topAppBarHazeEnabled(scrollBehavior: TopAppBarScrollBehavior): Boolean {
    return remember(scrollBehavior) {
        derivedStateOf {
            scrollBehavior.state.overlappedFraction > TopAppBarScrolledOverlapThreshold
        }
    }.value
}

@Composable
fun HazeTopAppBarColorReset(content: @Composable () -> Unit) {
    // Material3 animates top app bar container colors internally. When Haze is toggled off,
    // recreate only the app bar so the new opaque target color is used immediately instead of
    // animating up from the previous transparent Haze color.
    key(LocalHazeBlurEnabled.current) {
        content()
    }
}

@Composable
fun hazeTopAppBarColors(enabled: Boolean = LocalHazeBlurEnabled.current): TopAppBarColors {
    val defaultColors = TopAppBarDefaults.topAppBarColors()
    if (!enabled) return defaultColors

    return defaultColors.copy(
        containerColor = defaultColors.containerColor.copy(alpha = 0f),
        scrolledContainerColor = defaultColors.scrolledContainerColor.copy(alpha = 0f),
    )
}

@Composable
fun hazeNavigationSuiteColors(
    enabled: Boolean = LocalHazeBlurEnabled.current,
): NavigationSuiteColors {
    if (!enabled) return NavigationSuiteDefaults.colors()

    return NavigationSuiteDefaults.colors(
        shortNavigationBarContainerColor = Color.Transparent,
        navigationBarContainerColor = Color.Transparent,
    )
}
