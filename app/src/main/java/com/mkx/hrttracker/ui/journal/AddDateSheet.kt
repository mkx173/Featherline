package com.mkx.hrttracker.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.components.DatePickerModal
import com.mkx.hrttracker.ui.components.HazeAlertDialog
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.components.hazeSheetBlurActive
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.medication.LocalSheetDismissFocusRequester
import com.mkx.hrttracker.ui.medication.MedicationEditorSheetScaffold
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.LocalDate
import kotlin.math.ceil

const val AddDateNameFieldTestTag = "add-date-name-field"
const val AddDateDeleteConfirmButtonTestTag = "add-date-delete-confirm-button"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDateSheet(
    today: LocalDate,
    anchor: AnchorRowUiState?,
    initiallyPinned: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (name: String, icon: String, date: LocalDate, paletteKey: String?, pinned: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val closeSheet = { hideBottomSheet(scope, sheetState, onDismissRequest) }

    var name by remember(anchor) { mutableStateOf(anchor?.name.orEmpty()) }
    var selectedIcon by remember(anchor) { mutableStateOf(anchor?.icon ?: AnchorIcon.EVENT) }
    var selectedDate by remember(anchor) { mutableStateOf(anchor?.date ?: today) }
    var selectedPalette by remember(anchor) { mutableStateOf(anchor?.palette) }
    var pinned by remember(anchor) { mutableStateOf(initiallyPinned) }
    var isDeleteConfirmationVisible by remember { mutableStateOf(false) }

    val trimmedName = name.trim()
    // Date always has a value (defaults to today), so name is the only gate.
    val canConfirm = trimmedName.isNotEmpty()

    if (isDeleteConfirmationVisible && onDelete != null) {
        HazeAlertDialog(
            onDismissRequest = { isDeleteConfirmationVisible = false },
            title = { Text(text = stringResource(R.string.journal_delete_date_title)) },
            text = { Text(text = stringResource(R.string.journal_delete_date_confirmation)) },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag(AddDateDeleteConfirmButtonTestTag),
                    onClick = {
                        isDeleteConfirmationVisible = false
                        onDelete()
                        closeSheet()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.delete_entries_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { isDeleteConfirmationVisible = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }

    MedicationEditorSheetScaffold(
        modifier = modifier,
        title = stringResource(
            if (anchor == null) {
                R.string.journal_date_sheet_add_title
            } else {
                R.string.journal_date_sheet_edit_title
            },
        ),
        sheetState = sheetState,
        confirmButtonText = stringResource(R.string.save),
        onDismissRequest = onDismissRequest,
        onCloseClick = closeSheet,
        fillAvailableHeight = false,
        isSaving = false,
        confirmEnabled = canConfirm,
        destructiveButtonText = onDelete?.let { stringResource(R.string.journal_delete_date) },
        onDestructiveAction = onDelete?.let { { isDeleteConfirmationVisible = true } },
        onConfirm = {
            if (canConfirm) {
                onConfirm(trimmedName, selectedIcon.storageKey, selectedDate, selectedPalette?.name, pinned)
                closeSheet()
            }
        },
    ) {
        AddDateSheetContent(
            name = name,
            onNameChange = { name = it },
            selectedIcon = selectedIcon,
            onIconSelected = { selectedIcon = it },
            selectedDate = selectedDate,
            onDateSelected = { selectedDate = it },
            selectedPalette = selectedPalette,
            onPaletteSelected = { selectedPalette = it },
            pinned = pinned,
            onPinnedChange = { pinned = it },
            today = today,
        )
    }
}

@Composable
private fun AddDateSheetContent(
    name: String,
    onNameChange: (String) -> Unit,
    selectedIcon: AnchorIcon,
    onIconSelected: (AnchorIcon) -> Unit,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    selectedPalette: MedicationGroupColorKey?,
    onPaletteSelected: (MedicationGroupColorKey?) -> Unit,
    pinned: Boolean,
    onPinnedChange: (Boolean) -> Unit,
    today: LocalDate,
) {
    // The date picker lives in the sheet content (not the host) so its focusManager shares the
    // sheet's focus owner; dismissing focus then actually drops the date field's focus.
    val focusManager = LocalFocusManager.current
    // Park on the sheet's non-text anchor instead of clearing focus: on API 26
    // clearFocus jumps focus back to the first field rather than dismissing.
    val dismissFocusAnchor = LocalSheetDismissFocusRequester.current
    val dismissKeyboard: () -> Unit = {
        dismissFocusAnchor?.requestFocus() ?: focusManager.clearFocus()
    }
    var isDatePickerVisible by remember { mutableStateOf(false) }

    if (isDatePickerVisible) {
        DatePickerModal(
            onDateSelected = onDateSelected,
            onDismiss = {
                isDatePickerVisible = false
                dismissKeyboard()
            },
            initialSelectedDate = selectedDate,
        )
    }

    Column {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AddDateNameFieldTestTag),
            label = { Text(text = stringResource(R.string.journal_date_name_label)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { dismissKeyboard() }),
            // The leading icon previews the selected anchor icon, tinted with the
            // chosen palette's primary so the field surfaces the colour choice.
            leadingIcon = {
                val scheme = rememberMedicationGroupColorScheme(colorKey = selectedPalette)
                Icon(
                    painter = painterResource(anchorIconRes(selectedIcon)),
                    contentDescription = null,
                    tint = scheme.primary,
                )
            },
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        DateSelectorField(
            date = selectedDate,
            today = today,
            onClick = { isDatePickerVisible = true },
        )

        Spacer(modifier = Modifier.height(16.dp))

        AnchorIconGrid(
            selectedIcon = selectedIcon,
            selectedPalette = selectedPalette,
            onIconSelected = onIconSelected,
        )

        Spacer(modifier = Modifier.height(16.dp))

        AnchorPaletteRow(
            selectedPalette = selectedPalette,
            onPaletteSelected = onPaletteSelected,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Whether to pin the date (it joins the pinned tray / hero). A checkbox, not a switch,
        // because the choice only takes effect on Save. Defaulted by the caller: on for the
        // first date, off once something is already pinned; editable here either way.
        PreferenceSegmentedListItem(
            title = stringResource(R.string.journal_pin_date),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_keep),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = { onPinnedChange(!pinned) },
            trailingContent = {
                Checkbox(checked = pinned, onCheckedChange = onPinnedChange)
            },
        )
    }
}

@Composable
private fun DateSelectorField(
    date: LocalDate,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) { dateLabelFormatter(appLocale, today) }
    OutlinedTextField(
        value = dateFormatter(date),
        onValueChange = {},
        readOnly = true,
        label = { Text(text = stringResource(R.string.journal_date_date_label)) },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(date) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    val upEvent = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    if (upEvent != null) {
                        onClick()
                    }
                }
            },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_calendar_month),
                contentDescription = stringResource(R.string.select_date),
            )
        },
        singleLine = true,
    )
}

// Smallest column count whose tile width is <= maxTile (so a wide sheet gains columns
// rather than inflating tiles around the fixed glyph), floored at 8 so phones keep their
// two-row layout. All values in dp. Visible for unit testing.
internal fun anchorIconColumnCount(availableWidthDp: Float, gapDp: Float, maxTileDp: Float): Int =
    maxOf(8, ceil((availableWidthDp + gapDp) / (maxTileDp + gapDp)).toInt())

@Composable
private fun AnchorIconGrid(
    selectedIcon: AnchorIcon,
    selectedPalette: MedicationGroupColorKey?,
    onIconSelected: (AnchorIcon) -> Unit,
) {
    val gap = dimensionResource(R.dimen.padding_small)
    // Cap tile size so a wide sheet (tablet) gains columns instead of inflating each tile
    // around the fixed-size glyph. ~44dp keeps tablet tiles close to a phone's natural
    // 8-column tile (~36-40dp at 24dp side padding), so the grid reads the same on both;
    // normal phone widths stay at 8 columns (their tiles are already below this).
    val maxTile = 44.dp
    // The chosen colour's scheme fills the selected tile so the grid echoes the colour pick.
    val scheme = rememberMedicationGroupColorScheme(colorKey = selectedPalette)
    // Defer the tiles off the sheet's first frame: each tile inflates a vector drawable,
    // and composing all of AnchorIcon.entries at once is the bulk of the sheet's
    // first-frame cost (the jank when it opens). The grid still reserves its full
    // footprint with equally-sized empty cells below, so filling the glyphs one frame
    // later doesn't resize the sheet mid-open.
    var tilesReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { tilesReady = true }
    Column(
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        Text(
            text = stringResource(R.string.journal_date_icon_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Tiles share the row width evenly (weight) with a small uniform gap, so the grid
        // reads as a dense band instead of small tiles scattered by SpaceBetween. The column
        // count grows with the available width so tile size stays bounded by maxTile.
        BoxWithConstraints {
            val columns = anchorIconColumnCount(maxWidth.value, gap.value, maxTile.value)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                AnchorIcon.entries.chunked(columns).forEach { rowIcons ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        rowIcons.forEach { icon ->
                            if (tilesReady) {
                                AnchorIconTile(
                                    icon = icon,
                                    selected = icon == selectedIcon,
                                    scheme = scheme,
                                    onClick = { onIconSelected(icon) },
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                // Same footprint as a tile (aspectRatio 1) so the grid's
                                // height matches before and after the glyphs land.
                                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                        // Pad a short final row with empty cells so its tiles keep the same
                        // size as the full rows instead of stretching to fill the width.
                        repeat(columns - rowIcons.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnchorIconTile(
    icon: AnchorIcon,
    selected: Boolean,
    scheme: ColorScheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tiles stay neutral (haze-aware); only the selected tile is filled in the chosen colour, with
    // the scheme's onPrimaryContainer as its surface and the theme's surface as its glyph.
    val containerColor = if (selected) {
        scheme.onPrimaryContainer
    } else if (hazeSheetBlurActive()) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = stringResource(anchorIconLabelRes(icon))
    Surface(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(1f)
            .semantics { this.selected = selected },
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(anchorIconRes(icon)),
                contentDescription = label,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// Color bar geometry. Segments are individually shaped and separated by
// list_segment_gap (matching the app's segmented lists); the selection ring is
// inset inside each segment with concentric corners.
private val ColorBarHeight = 40.dp
private val SegmentOuterCorner = 12.dp
// Inner corner must clear ColorRingInset + ColorRingWidth (4dp) so the ring's inner
// stroke edge stays rounded instead of collapsing to a square.
private val SegmentInnerCorner = 6.dp
private val ColorRingInset = 2.dp
private val ColorRingWidth = 2.dp
private val DefaultColorChipWidth = 40.dp

private enum class ColorSegmentPosition { FIRST, MIDDLE, LAST, SINGLE }

// (start, end) corner radii: outer on the strip's rounded ends, inner (small, so the
// segment reads as a square) on the corners that meet a neighbour.
private fun segmentCornerSizes(position: ColorSegmentPosition): Pair<Dp, Dp> = when (position) {
    ColorSegmentPosition.SINGLE -> SegmentOuterCorner to SegmentOuterCorner
    ColorSegmentPosition.FIRST -> SegmentOuterCorner to SegmentInnerCorner
    ColorSegmentPosition.LAST -> SegmentInnerCorner to SegmentOuterCorner
    ColorSegmentPosition.MIDDLE -> SegmentInnerCorner to SegmentInnerCorner
}

private fun segmentShape(position: ColorSegmentPosition): Shape {
    val (start, end) = segmentCornerSizes(position)
    return RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
}

// The ring is inset inside each segment with concentric corners (segment radius − inset).
// Because inset + track ≤ the segment's corner, even the inner corners stay rounded.
private fun selectionRingShape(position: ColorSegmentPosition): Shape {
    val (start, end) = segmentCornerSizes(position)
    val s = (start - ColorRingInset).coerceAtLeast(0.dp)
    val e = (end - ColorRingInset).coerceAtLeast(0.dp)
    return RoundedCornerShape(topStart = s, bottomStart = s, topEnd = e, bottomEnd = e)
}

@Composable
private fun AnchorPaletteRow(
    selectedPalette: MedicationGroupColorKey?,
    onPaletteSelected: (MedicationGroupColorKey?) -> Unit,
) {
    // Display order is red → purple (enum declaration order); assignmentOrder only
    // drives a11y indices and colour auto-assignment elsewhere.
    val colors = MedicationGroupColorKey.entries
    val onSelect by rememberUpdatedState(onPaletteSelected)
    Column(
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
    ) {
        Text(
            text = stringResource(R.string.journal_date_palette_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ColorBarHeight),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        ) {
            DefaultColorChip(
                selected = selectedPalette == null,
                onClick = { onSelect(null) },
            )
            // The drag gesture lives on the whole strip (not per segment) so a press or
            // drag continuously selects the colour under the finger. Segments stay
            // individually shaped/gapped and keep their own a11y semantics.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            var lastIndex = -1
                            fun selectAt(x: Float) {
                                val width = size.width
                                if (width <= 0) return
                                val index = ((x / width) * colors.size)
                                    .toInt().coerceIn(0, colors.lastIndex)
                                if (index != lastIndex) {
                                    lastIndex = index
                                    onSelect(colors[index])
                                }
                            }
                            val down = awaitFirstDown()
                            selectAt(down.position.x)
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change != null) {
                                    selectAt(change.position.x)
                                    if (change.positionChanged()) change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap)),
            ) {
                colors.forEachIndexed { index, key ->
                    val position = when {
                        colors.size == 1 -> ColorSegmentPosition.SINGLE
                        index == 0 -> ColorSegmentPosition.FIRST
                        index == colors.lastIndex -> ColorSegmentPosition.LAST
                        else -> ColorSegmentPosition.MIDDLE
                    }
                    ColorBarSegment(
                        colorKey = key,
                        selected = key == selectedPalette,
                        position = position,
                        onSelect = { onSelect(key) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorBarSegment(
    colorKey: MedicationGroupColorKey,
    selected: Boolean,
    position: ColorSegmentPosition,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = rememberMedicationGroupColorScheme(colorKey = colorKey)
    val absoluteIndex = MedicationGroupColorKey.assignmentOrder.indexOf(colorKey) + 1
    val description = stringResource(
        if (selected) {
            R.string.group_color_picker_swatch_selected_content_description
        } else {
            R.string.group_color_picker_swatch_content_description
        },
        absoluteIndex,
    )
    // No clickable here: touch is handled by the strip's drag gesture (so no ripple and
    // drags track across segments). An onClick semantics action keeps it activatable
    // for TalkBack and exposes the same per-colour target the tests select by.
    Box(
        modifier = modifier
            .clip(segmentShape(position))
            .background(scheme.primary)
            .semantics {
                contentDescription = description
                this.selected = selected
                onClick { onSelect(); true }
            },
    ) {
        if (selected) {
            SelectionRing(shape = selectionRingShape(position))
        }
    }
}

@Composable
private fun DefaultColorChip(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = rememberMedicationGroupColorScheme(colorKey = null)
    val description = stringResource(R.string.journal_date_palette_none)
    Box(
        modifier = Modifier
            .width(DefaultColorChipWidth)
            .fillMaxHeight()
            .clip(RoundedCornerShape(SegmentOuterCorner))
            .background(scheme.primary)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics {
                this.selected = selected
                contentDescription = description
            },
    ) {
        if (selected) {
            SelectionRing(shape = selectionRingShape(ColorSegmentPosition.SINGLE))
        }
    }
}

@Composable
private fun BoxScope.SelectionRing(shape: Shape) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .padding(ColorRingInset)
            .border(
                width = ColorRingWidth,
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = shape,
            ),
    )
}

@Preview(name = "AddDateSheet content — new", showBackground = true, widthDp = 420)
@Composable
private fun AddDateSheetContentNewPreview() {
    val today = LocalDate.of(2026, 6, 19)
    AddDateSheetPreviewContainer {
        var name by remember { mutableStateOf("") }
        var icon by remember { mutableStateOf(AnchorIcon.EVENT) }
        var palette by remember { mutableStateOf<MedicationGroupColorKey?>(null) }
        var pinned by remember { mutableStateOf(true) }
        AddDateSheetContent(
            name = name,
            onNameChange = { name = it },
            selectedIcon = icon,
            onIconSelected = { icon = it },
            selectedDate = today,
            onDateSelected = {},
            selectedPalette = palette,
            onPaletteSelected = { palette = it },
            pinned = pinned,
            onPinnedChange = { pinned = it },
            today = today,
        )
    }
}

@Preview(name = "AddDateSheet content — filled", showBackground = true, widthDp = 420)
@Composable
private fun AddDateSheetContentFilledPreview() {
    val today = LocalDate.of(2026, 6, 19)
    AddDateSheetPreviewContainer {
        var name by remember { mutableStateOf("First injection") }
        var icon by remember { mutableStateOf(AnchorIcon.VACCINES) }
        var palette by remember { mutableStateOf<MedicationGroupColorKey?>(MedicationGroupColorKey.ROSE) }
        var pinned by remember { mutableStateOf(false) }
        AddDateSheetContent(
            name = name,
            onNameChange = { name = it },
            selectedIcon = icon,
            onIconSelected = { icon = it },
            selectedDate = LocalDate.of(2025, 9, 1),
            onDateSelected = {},
            selectedPalette = palette,
            onPaletteSelected = { palette = it },
            pinned = pinned,
            onPinnedChange = { pinned = it },
            today = today,
        )
    }
}

@Composable
private fun AddDateSheetPreviewContainer(content: @Composable () -> Unit) {
    HrtTrackerTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(R.dimen.padding_large)),
            ) {
                content()
            }
        }
    }
}
