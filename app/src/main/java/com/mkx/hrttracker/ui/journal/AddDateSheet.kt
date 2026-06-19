package com.mkx.hrttracker.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.components.ColorPaletteSwatchGrid
import com.mkx.hrttracker.ui.components.DatePickerModal
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HazeAlertDialog
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.medication.MedicationEditorSheetScaffold
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
        AnchorPreviewHero(
            name = name,
            icon = selectedIcon,
            date = selectedDate,
            palette = selectedPalette,
            today = today,
        )

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AddDateNameFieldTestTag),
            label = { Text(text = stringResource(R.string.journal_date_name_label)) },
            singleLine = true,
        )

        DateSelectorRow(date = selectedDate, today = today, onClick = onDateClick)

        AnchorIconGrid(
            selectedIcon = selectedIcon,
            selectedPalette = selectedPalette,
            onIconSelected = onIconSelected,
        )

        AnchorPaletteRow(
            selectedPalette = selectedPalette,
            onPaletteSelected = onPaletteSelected,
        )
    }
}

@Composable
private fun AnchorPreviewHero(
    name: String,
    icon: AnchorIcon,
    date: LocalDate,
    palette: MedicationGroupColorKey?,
    today: LocalDate,
) {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) { dateLabelFormatter(appLocale, today) }
    val trimmed = name.trim()

    EditorSegmentedListItem(
        fullyRounded = true,
        leadingContent = { AnchorPrimaryChip(icon = icon, palette = palette) },
        supportingContent = {
            Text(
                text = dateFormatter(date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Text(
            text = trimmed.ifEmpty { stringResource(R.string.journal_date_preview_placeholder) },
            style = MaterialTheme.typography.titleMedium,
            color = if (trimmed.isEmpty()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                Color.Unspecified
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AnchorPrimaryChip(
    icon: AnchorIcon,
    palette: MedicationGroupColorKey?,
    size: Dp = 48.dp,
) {
    val scheme = rememberMedicationGroupColorScheme(colorKey = palette)
    Surface(
        modifier = Modifier.size(size),
        shape = MaterialTheme.shapes.small,
        color = scheme.primary,
        contentColor = scheme.onPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(anchorIconRes(icon)),
                contentDescription = null,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

@Composable
private fun DateSelectorRow(
    date: LocalDate,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) { dateLabelFormatter(appLocale, today) }
    PreferenceSegmentedListItem(
        title = stringResource(R.string.journal_date_date_label),
        supportingText = dateFormatter(date),
        onClick = onClick,
    )
}

@Composable
private fun AnchorIconGrid(
    selectedIcon: AnchorIcon,
    selectedPalette: MedicationGroupColorKey?,
    onIconSelected: (AnchorIcon) -> Unit,
) {
    val columns = 6
    Column(
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
    ) {
        Text(
            text = stringResource(R.string.journal_date_icon_section),
            style = MaterialTheme.typography.titleSmall,
        )
        AnchorIcon.entries.chunked(columns).forEach { rowIcons ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
            ) {
                rowIcons.forEach { icon ->
                    AnchorIconTile(
                        icon = icon,
                        selected = icon == selectedIcon,
                        selectedPalette = selectedPalette,
                        onClick = { onIconSelected(icon) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowIcons.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AnchorIconTile(
    icon: AnchorIcon,
    selected: Boolean,
    selectedPalette: MedicationGroupColorKey?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = rememberMedicationGroupColorScheme(colorKey = selectedPalette)
    val containerColor = if (selected) {
        scheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (selected) {
        scheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = stringResource(anchorIconLabelRes(icon))
    Surface(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(1f)
            .semantics { this.selected = selected },
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(anchorIconRes(icon)),
                contentDescription = label,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun AnchorPaletteRow(
    selectedPalette: MedicationGroupColorKey?,
    onPaletteSelected: (MedicationGroupColorKey?) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
    ) {
        Text(
            text = stringResource(R.string.journal_date_palette_section),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium)),
        ) {
            DefaultPaletteSwatch(
                selected = selectedPalette == null,
                onClick = { onPaletteSelected(null) },
            )
            ColorPaletteSwatchGrid(
                selectedColorKey = selectedPalette,
                onColorSelected = { onPaletteSelected(it) },
                modifier = Modifier.weight(1f),
            )
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
