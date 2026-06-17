package com.mkx.hrttracker.ui.journal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.components.DatePickerModal
import com.mkx.hrttracker.ui.components.HazeModalBottomSheet
import com.mkx.hrttracker.ui.components.HazeAlertDialog
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
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
    val closeSheet = {
        hideBottomSheet(scope, sheetState, onDismissRequest)
    }

    HazeModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        AddDateSheetContent(
            title = stringResource(
                if (anchor == null) {
                    R.string.journal_date_sheet_add_title
                } else {
                    R.string.journal_date_sheet_edit_title
                }
            ),
            confirmButtonText = stringResource(R.string.save),
            initialName = anchor?.name.orEmpty(),
            initialIcon = anchor?.icon ?: AnchorIcon.EVENT,
            initialDate = anchor?.date ?: today,
            initialPalette = anchor?.palette,
            today = today,
            onDismissRequest = closeSheet,
            onConfirm = { name, icon, date, paletteKey ->
                onConfirm(name, icon, date, paletteKey)
                closeSheet()
            },
            onDelete = onDelete?.let { delete ->
                {
                    delete()
                    closeSheet()
                }
            },
        )
    }
}

@Composable
fun AddDateSheetContent(
    title: String,
    confirmButtonText: String,
    initialName: String,
    initialIcon: AnchorIcon,
    initialDate: LocalDate?,
    initialPalette: MedicationGroupColorKey?,
    today: LocalDate,
    onDismissRequest: () -> Unit,
    onConfirm: (name: String, icon: String, date: LocalDate, paletteKey: String?) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var selectedIcon by remember(initialIcon) { mutableStateOf(initialIcon) }
    var selectedDate by remember(initialDate) { mutableStateOf(initialDate) }
    var selectedPalette by remember(initialPalette) { mutableStateOf(initialPalette) }
    var isDatePickerVisible by remember { mutableStateOf(false) }
    var isDeleteConfirmationVisible by remember { mutableStateOf(false) }
    val trimmedName = name.trim()
    val canConfirm = trimmedName.isNotEmpty() && selectedDate != null

    if (isDatePickerVisible) {
        DatePickerModal(
            onDateSelected = { selectedDate = it },
            onDismiss = { isDatePickerVisible = false },
            initialSelectedDate = selectedDate ?: today,
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = dimensionResource(R.dimen.padding_large),
                end = dimensionResource(R.dimen.padding_large),
                bottom = dimensionResource(R.dimen.padding_large),
            ),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            HrtFilledTonalButton(
                text = stringResource(R.string.cancel),
                onClick = onDismissRequest,
                compact = true,
            )
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AddDateNameFieldTestTag),
            label = { Text(text = stringResource(R.string.journal_date_name_label)) },
            singleLine = true,
        )

        DateSelectorRow(
            date = selectedDate,
            today = today,
            onClick = { isDatePickerVisible = true },
        )

        AnchorIconSelector(
            selectedIcon = selectedIcon,
            onIconSelected = { selectedIcon = it },
        )

        PaletteSelector(
            selectedPalette = selectedPalette,
            onPaletteSelected = { selectedPalette = it },
        )

        if (onDelete != null) {
            HrtFilledTonalButton(
                text = stringResource(R.string.journal_delete_date),
                onClick = { isDeleteConfirmationVisible = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HrtButton(
            text = confirmButtonText,
            onClick = {
                selectedDate?.let { date ->
                    if (canConfirm) {
                        onConfirm(
                            trimmedName,
                            selectedIcon.storageKey,
                            date,
                            selectedPalette?.name,
                        )
                    }
                }
            },
            enabled = canConfirm,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DateSelectorRow(
    date: LocalDate?,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val appLocale = rememberAppLocale()
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }

    PreferenceSegmentedListItem(
        title = stringResource(R.string.journal_date_date_label),
        supportingText = date?.let(dateFormatter)
            ?: stringResource(R.string.journal_select_date),
        onClick = onClick,
    )
}

@Composable
private fun AnchorIconSelector(
    selectedIcon: AnchorIcon,
    onIconSelected: (AnchorIcon) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))) {
        Text(
            text = stringResource(R.string.journal_date_icon_section),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        ) {
            AnchorIcon.entries.forEach { icon ->
                FilterChip(
                    selected = icon == selectedIcon,
                    onClick = { onIconSelected(icon) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(anchorIconRes(icon)),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    label = {
                        Text(text = icon.storageKey.replace('_', ' '))
                    },
                )
            }
        }
    }
}

@Composable
private fun PaletteSelector(
    selectedPalette: MedicationGroupColorKey?,
    onPaletteSelected: (MedicationGroupColorKey?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))) {
        Text(
            text = stringResource(R.string.journal_date_palette_section),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
        ) {
            PaletteChip(
                palette = null,
                selected = selectedPalette == null,
                onClick = { onPaletteSelected(null) },
            )
            MedicationGroupColorKey.entries.forEach { palette ->
                PaletteChip(
                    palette = palette,
                    selected = selectedPalette == palette,
                    onClick = { onPaletteSelected(palette) },
                )
            }
        }
    }
}

@Composable
private fun PaletteChip(
    palette: MedicationGroupColorKey?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = rememberMedicationGroupColorScheme(colorKey = palette)
    val label = palette?.name ?: stringResource(R.string.journal_date_palette_none)

    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            Surface(
                modifier = Modifier.size(18.dp),
                shape = CircleShape,
                color = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
            ) {
                Box(modifier = Modifier.size(18.dp))
            }
        },
        label = {
            Text(text = label)
        },
    )
}
