package com.mkx.hrttracker.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.components.ColorPaletteSwatch
import com.mkx.hrttracker.ui.components.DatePickerModal
import com.mkx.hrttracker.ui.components.HazeAlertDialog
import com.mkx.hrttracker.ui.components.hazeSheetBlurActive
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.medication.MedicationEditorSheetScaffold
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.LocalDate

const val AddDateNameFieldTestTag = "add-date-name-field"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDateSheet(
    today: LocalDate,
    anchor: AnchorRowUiState?,
    onDismissRequest: () -> Unit,
    onConfirm: (name: String, icon: String, date: LocalDate, paletteKey: String?) -> Unit,
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
    var isDatePickerVisible by remember { mutableStateOf(false) }
    var isDeleteConfirmationVisible by remember { mutableStateOf(false) }

    val trimmedName = name.trim()
    // Date always has a value (defaults to today), so name is the only gate.
    val canConfirm = trimmedName.isNotEmpty()

    if (isDatePickerVisible) {
        DatePickerModal(
            onDateSelected = { selectedDate = it },
            onDismiss = { isDatePickerVisible = false },
            initialSelectedDate = selectedDate,
        )
    }

    if (isDeleteConfirmationVisible && onDelete != null) {
        HazeAlertDialog(
            onDismissRequest = { isDeleteConfirmationVisible = false },
            title = { Text(text = stringResource(R.string.journal_delete_date_title)) },
            text = { Text(text = stringResource(R.string.journal_delete_date_confirmation)) },
            confirmButton = {
                TextButton(
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
                onConfirm(trimmedName, selectedIcon.storageKey, selectedDate, selectedPalette?.name)
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
            onDateClick = { isDatePickerVisible = true },
            selectedPalette = selectedPalette,
            onPaletteSelected = { selectedPalette = it },
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
    onDateClick: () -> Unit,
    selectedPalette: MedicationGroupColorKey?,
    onPaletteSelected: (MedicationGroupColorKey?) -> Unit,
    today: LocalDate,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium)),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AddDateNameFieldTestTag),
            label = { Text(text = stringResource(R.string.journal_date_name_label)) },
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

        DateSelectorField(date = selectedDate, today = today, onClick = onDateClick)

        AnchorIconGrid(
            selectedIcon = selectedIcon,
            onIconSelected = onIconSelected,
        )

        AnchorPaletteRow(
            selectedPalette = selectedPalette,
            onPaletteSelected = onPaletteSelected,
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

@Composable
private fun AnchorIconGrid(
    selectedIcon: AnchorIcon,
    onIconSelected: (AnchorIcon) -> Unit,
) {
    val columns = 8
    val gap = dimensionResource(R.dimen.padding_small)
    Column(
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        Text(
            text = stringResource(R.string.journal_date_icon_section),
            style = MaterialTheme.typography.titleSmall,
        )
        // Tiles share the row width evenly (weight) with a small uniform gap, so the grid
        // reads as a dense band instead of small tiles scattered by SpaceBetween.
        AnchorIcon.entries.chunked(columns).forEach { rowIcons ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                rowIcons.forEach { icon ->
                    AnchorIconTile(
                        icon = icon,
                        selected = icon == selectedIcon,
                        onClick = { onIconSelected(icon) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnchorIconTile(
    icon: AnchorIcon,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tiles stay neutral and selection uses the app theme primary; the chosen anchor
    // colour is surfaced through the name field's leading icon, not the grid.
    val hazeSheet = hazeSheetBlurActive()
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        if (hazeSheet) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
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

@Composable
private fun AnchorPaletteRow(
    selectedPalette: MedicationGroupColorKey?,
    onPaletteSelected: (MedicationGroupColorKey?) -> Unit,
) {
    val ordered = MedicationGroupColorKey.assignmentOrder
    // Reveal the pre-selected swatch (edit mode) by initialising the scroll position at
    // creation; index 0 is the "none" default and colours follow at indexOf(key) + 1.
    val initialIndex = remember {
        selectedPalette?.let { ordered.indexOf(it) + 1 } ?: 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    Column(
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
    ) {
        Text(
            text = stringResource(R.string.journal_date_palette_section),
            style = MaterialTheme.typography.titleSmall,
        )
        LazyRow(
            state = listState,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        ) {
            item {
                DefaultPaletteSwatch(
                    selected = selectedPalette == null,
                    onClick = { onPaletteSelected(null) },
                )
            }
            items(ordered, key = { it }) { key ->
                ColorPaletteSwatch(
                    colorKey = key,
                    selected = key == selectedPalette,
                    onClick = { onPaletteSelected(key) },
                )
            }
        }
    }
}

@Composable
private fun DefaultPaletteSwatch(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = rememberMedicationGroupColorScheme(colorKey = null)
    val description = stringResource(R.string.journal_date_palette_none)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(scheme.primary)
            .clickable(onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = description
            },
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(2.5.dp)
                    .border(
                        width = 2.5.dp,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Preview(name = "AddDateSheet content — new", showBackground = true, widthDp = 420)
@Composable
private fun AddDateSheetContentNewPreview() {
    val today = LocalDate.of(2026, 6, 19)
    AddDateSheetPreviewContainer {
        var name by remember { mutableStateOf("") }
        var icon by remember { mutableStateOf(AnchorIcon.EVENT) }
        var palette by remember { mutableStateOf<MedicationGroupColorKey?>(null) }
        AddDateSheetContent(
            name = name,
            onNameChange = { name = it },
            selectedIcon = icon,
            onIconSelected = { icon = it },
            selectedDate = today,
            onDateClick = {},
            selectedPalette = palette,
            onPaletteSelected = { palette = it },
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
        AddDateSheetContent(
            name = name,
            onNameChange = { name = it },
            selectedIcon = icon,
            onIconSelected = { icon = it },
            selectedDate = LocalDate.of(2025, 9, 1),
            onDateClick = {},
            selectedPalette = palette,
            onPaletteSelected = { palette = it },
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
