package com.mkx.hrttracker.ui.plan

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.formatDose
import com.mkx.hrttracker.reminder.canPostNotifications
import com.mkx.hrttracker.reminder.canScheduleExactAlarms
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.log.MedicationEditorSheet
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

@Composable
fun MedicationGroupEditorScreen(
    onNavigateBack: () -> Unit,
    onGroupSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedicationGroupEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasNotificationAccess by remember { mutableStateOf(canPostNotifications(context)) }
    val notificationPermissionDeniedMessage =
        stringResource(R.string.group_notifications_permission_denied)
    var isExactAlarmDialogVisible by rememberSaveable { mutableStateOf(false) }
    var showInexactReminderWarning by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess = canPostNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val exactAlarmAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isExactAlarmDialogVisible = false
        showInexactReminderWarning = !canScheduleExactAlarms(context)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.updateNotificationsEnabled(true)
            hasNotificationAccess = canPostNotifications(context)
            if (canScheduleExactAlarms(context)) {
                showInexactReminderWarning = false
            } else {
                isExactAlarmDialogVisible = true
            }
        } else {
            viewModel.updateNotificationsEnabled(false)
            hasNotificationAccess = false
            Toast.makeText(context, notificationPermissionDeniedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            viewModel.consumeSavedState()
            onGroupSaved()
        }
    }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            viewModel.consumeDeletedState()
            onGroupSaved()
        }
    }

    LaunchedEffect(hasNotificationAccess, uiState.notificationsEnabled) {
        if (!hasNotificationAccess && uiState.notificationsEnabled) {
            viewModel.updateNotificationsEnabled(false)
            isExactAlarmDialogVisible = false
            showInexactReminderWarning = false
        }
    }

    LaunchedEffect(hasNotificationAccess, uiState.notificationsEnabled, isExactAlarmDialogVisible) {
        if (!hasNotificationAccess || !uiState.notificationsEnabled) {
            showInexactReminderWarning = false
        } else if (!isExactAlarmDialogVisible) {
            showInexactReminderWarning = !canScheduleExactAlarms(context)
        }
    }

    if (isExactAlarmDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                isExactAlarmDialogVisible = false
                showInexactReminderWarning = true
            },
            title = {
                Text(text = stringResource(R.string.group_notifications_exact_alarm_title))
            },
            text = {
                Text(text = stringResource(R.string.group_notifications_exact_alarm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        maybeRequestExactAlarmAccess(context, exactAlarmAccessLauncher::launch)
                    }
                ) {
                    Text(text = stringResource(R.string.group_notifications_exact_alarm_settings))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isExactAlarmDialogVisible = false
                        showInexactReminderWarning = true
                    }
                ) {
                    Text(text = stringResource(R.string.group_notifications_exact_alarm_skip))
                }
            }
        )
    }

    MedicationGroupEditorScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onGroupNameChange = viewModel::updateGroupName,
        onScheduleTypeChange = viewModel::updateScheduleType,
        onSinceDateChange = viewModel::updateSinceDate,
        onNotificationsEnabledChange = { enabled ->
            if (!enabled) {
                viewModel.updateNotificationsEnabled(false)
                isExactAlarmDialogVisible = false
                showInexactReminderWarning = false
            } else if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.updateNotificationsEnabled(true)
                if (canScheduleExactAlarms(context)) {
                    showInexactReminderWarning = false
                } else {
                    isExactAlarmDialogVisible = true
                }
            }
        },
        notificationsToggleEnabled = uiState.remindersEnabled && hasNotificationAccess,
        showInexactReminderWarning = showInexactReminderWarning,
        onWeeklyIntervalChange = viewModel::updateWeeklyIntervalWeeks,
        onWeeklyDayChange = viewModel::toggleWeeklyDayOfWeek,
        onWeeklyTimeChange = viewModel::updateWeeklyTime,
        onDailyIntervalChange = viewModel::updateDailyIntervalDays,
        onAddDailyTime = viewModel::addDailyTime,
        onDailyTimeChange = viewModel::updateDailyTime,
        onRemoveDailyTime = viewModel::removeDailyTime,
        onAddMedication = viewModel::showAddMedicationEditor,
        onMedicationClick = viewModel::showMedicationEditor,
        onRemoveMedication = viewModel::removeMedication,
        onDismissMedicationEditor = viewModel::dismissMedicationEditor,
        onConsumeMedicationEditorSaved = viewModel::consumeMedicationEditorSaved,
        onMedicationRouteChange = viewModel::updateEditingMedicationRoute,
        onMedicationNameChange = viewModel::updateEditingMedicationName,
        onMedicationDosageChange = viewModel::updateEditingMedicationDosage,
        onSaveMedicationClick = viewModel::saveEditingMedication,
        onSaveClick = viewModel::saveGroup,
        onDeleteClick = viewModel::showDeleteConfirmation,
        onDeleteDismiss = viewModel::dismissDeleteConfirmation,
        onDeleteConfirm = viewModel::deleteGroup,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationGroupEditorScreenContent(
    uiState: MedicationGroupEditorUiState,
    onNavigateBack: () -> Unit,
    onGroupNameChange: (String) -> Unit,
    onScheduleTypeChange: (MedicationGroupScheduleType) -> Unit,
    onSinceDateChange: (LocalDate) -> Unit,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    notificationsToggleEnabled: Boolean,
    showInexactReminderWarning: Boolean,
    onWeeklyIntervalChange: (String) -> Unit,
    onWeeklyDayChange: (DayOfWeek) -> Unit,
    onWeeklyTimeChange: (LocalTime) -> Unit,
    onDailyIntervalChange: (String) -> Unit,
    onAddDailyTime: () -> Unit,
    onDailyTimeChange: (String, LocalTime) -> Unit,
    onRemoveDailyTime: (String) -> Unit,
    onAddMedication: () -> Unit,
    onMedicationClick: (String) -> Unit,
    onRemoveMedication: (String) -> Unit,
    onDismissMedicationEditor: () -> Unit,
    onConsumeMedicationEditorSaved: () -> Unit,
    onMedicationRouteChange: (com.mkx.hrttracker.model.medication.RouteOfAdministration) -> Unit,
    onMedicationNameChange: (String) -> Unit,
    onMedicationDosageChange: (String) -> Unit,
    onSaveMedicationClick: () -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appLocale = rememberAppLocale()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val scope = rememberCoroutineScope()
    val timeFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale)
    }
    val dateFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale)
    }
    val scheduleOptions = remember {
        listOf(
            MedicationGroupScheduleType.DAILY,
            MedicationGroupScheduleType.WEEKLY
        )
    }
    val canSave = uiState.groupName.isNotBlank() &&
        uiState.medications.isNotEmpty() &&
        !uiState.isSaving &&
        !uiState.isDeleting

    LaunchedEffect(uiState.isMedicationEditorSaved) {
        if (uiState.isMedicationEditorSaved) {
            hideBottomSheet(scope, sheetState) {
                onConsumeMedicationEditorSaved()
                onDismissMedicationEditor()
            }
        }
    }

    if (uiState.isDeleteConfirmationVisible) {
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(text = stringResource(R.string.delete_medication_group_title)) },
            text = { Text(text = stringResource(R.string.delete_medication_group_confirmation)) },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) {
                    Text(text = stringResource(R.string.delete_entries_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (uiState.isEditing) {
                                R.string.edit_medication_group
                            } else {
                                R.string.add_medication_group
                            }
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = onSaveClick,
                        enabled = canSave,
                        shape = RoundedCornerShape(percent = 50),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContentColor = MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Text(text = stringResource(R.string.save))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                EditorSectionCard {
                    OutlinedTextField(
                        value = uiState.groupName,
                        onValueChange = onGroupNameChange,
                        label = { Text(text = stringResource(R.string.field_medication_group_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            item {
                EditorSectionHeader(title = stringResource(R.string.group_schedule_title))
                EditorSectionCard {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        scheduleOptions.forEachIndexed { index, scheduleType ->
                            SegmentedButton(
                                selected = uiState.scheduleType == scheduleType,
                                onClick = { onScheduleTypeChange(scheduleType) },
                                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = scheduleOptions.size
                                )
                            ) {
                                val isSelected = uiState.scheduleType == scheduleType
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = stringResource(
                                        if (scheduleType == MedicationGroupScheduleType.DAILY) {
                                            R.string.group_schedule_daily
                                        } else {
                                            R.string.group_schedule_weekly
                                        }
                                    ),
                                    modifier = Modifier.padding(start = if (isSelected) 6.dp else 0.dp)
                                )
                            }
                        }
                    }

                    if (uiState.scheduleType == MedicationGroupScheduleType.WEEKLY) {
                        WeeklyScheduleEditor(
                            sinceDate = uiState.sinceDate,
                            intervalWeeks = uiState.weeklyIntervalWeeks,
                            selectedDaysOfWeek = uiState.weeklyDaysOfWeek,
                            time = uiState.weeklyTime,
                            appLocale = appLocale,
                            dateFormatter = dateFormatter,
                            timeFormatter = timeFormatter,
                            onSinceDateChange = { currentDate ->
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        onSinceDateChange(LocalDate.of(year, month + 1, dayOfMonth))
                                    },
                                    currentDate.year,
                                    currentDate.monthValue - 1,
                                    currentDate.dayOfMonth
                                ).show()
                            },
                            onIntervalChange = onWeeklyIntervalChange,
                            onDayChange = onWeeklyDayChange,
                            onTimeChange = { currentTime ->
                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        onWeeklyTimeChange(LocalTime.of(hourOfDay, minute))
                                    },
                                    currentTime.hour,
                                    currentTime.minute,
                                    DateFormat.is24HourFormat(context)
                                ).show()
                            }
                        )
                    } else {
                        DailyScheduleEditor(
                            sinceDate = uiState.sinceDate,
                            intervalDays = uiState.dailyIntervalDays,
                            dailyTimes = uiState.dailyTimes,
                            dateFormatter = dateFormatter,
                            timeFormatter = timeFormatter,
                            onSinceDateChange = { currentDate ->
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        onSinceDateChange(LocalDate.of(year, month + 1, dayOfMonth))
                                    },
                                    currentDate.year,
                                    currentDate.monthValue - 1,
                                    currentDate.dayOfMonth
                                ).show()
                            },
                            onIntervalChange = onDailyIntervalChange,
                            onAddTime = onAddDailyTime,
                            onTimeClick = { localId, currentTime ->
                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        onDailyTimeChange(localId, LocalTime.of(hourOfDay, minute))
                                    },
                                    currentTime.hour,
                                    currentTime.minute,
                                    DateFormat.is24HourFormat(context)
                                ).show()
                            },
                            onRemoveTime = onRemoveDailyTime
                        )
                    }
                }
            }

            item {
                EditorSectionHeader(title = stringResource(R.string.group_notifications_title))
                EditorSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.group_notifications_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.group_notifications_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.notificationsEnabled,
                            onCheckedChange = onNotificationsEnabledChange,
                            enabled = notificationsToggleEnabled
                        )
                    }
                    if (!uiState.remindersEnabled) {
                        EditorSupportMessage(
                            text = stringResource(R.string.group_notifications_master_disabled)
                        )
                    }
                    if (showInexactReminderWarning) {
                        EditorSupportMessage(
                            text = stringResource(R.string.group_notifications_inexact_warning)
                        )
                    }
                }
            }

            item {
                EditorSectionHeader(
                    title = stringResource(R.string.group_medications_title),
                    trailing = {
                        TextButton(onClick = onAddMedication) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.add_medication_to_group),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                )
            }

            if (uiState.medications.isEmpty()) {
                item {
                    EditorSectionCard {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.group_medications_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(
                items = uiState.medications,
                key = { it.localId }
            ) { medication ->
                MedicationGroupMedicationCard(
                    medication = medication,
                    appLocale = appLocale,
                    onClick = { onMedicationClick(medication.localId) },
                    onRemoveClick = { onRemoveMedication(medication.localId) }
                )
            }

            item {
                uiState.errorMessageRes?.let { errorMessageRes ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = stringResource(errorMessageRes),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                }
            }

            if (uiState.isEditing) {
                item {
                    EditorSectionHeader(title = stringResource(R.string.group_danger_zone_title))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = !uiState.isSaving && !uiState.isDeleting,
                                onClick = onDeleteClick
                            ),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.delete_medication_group),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = stringResource(R.string.delete_medication_group_confirmation),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.editingMedication?.let { medication ->
        MedicationEditorSheet(
            title = stringResource(
                if (uiState.medications.any { it.localId == medication.localId }) {
                    R.string.edit_medication
                } else {
                    R.string.add_medication_to_group
                }
            ),
            sheetState = sheetState,
            confirmButtonText = stringResource(R.string.save_medication),
            onDismissRequest = onDismissMedicationEditor,
            onCloseClick = {
                hideBottomSheet(scope, sheetState, onDismissMedicationEditor)
            },
            routeOfAdministration = medication.routeOfAdministration,
            medicineName = medication.medicineName,
            dosageMg = medication.dosageMg,
            onRouteSelected = onMedicationRouteChange,
            onMedicineNameChange = onMedicationNameChange,
            onDosageChange = onMedicationDosageChange,
            errorMessageRes = uiState.medicationEditorErrorMessageRes,
            onConfirm = onSaveMedicationClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeeklyScheduleEditor(
    sinceDate: LocalDate,
    intervalWeeks: String,
    selectedDaysOfWeek: Set<DayOfWeek>,
    time: LocalTime,
    appLocale: java.util.Locale,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    onSinceDateChange: (LocalDate) -> Unit,
    onIntervalChange: (String) -> Unit,
    onDayChange: (DayOfWeek) -> Unit,
    onTimeChange: (LocalTime) -> Unit
) {
    Column(
        modifier = Modifier.padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EditorFieldRow(
            label = stringResource(R.string.group_schedule_since),
            value = sinceDate.format(dateFormatter),
            icon = Icons.Default.Event,
            onClick = { onSinceDateChange(sinceDate) }
        )

        OutlinedTextField(
            value = intervalWeeks,
            onValueChange = onIntervalChange,
            label = { Text(text = stringResource(R.string.group_schedule_every_weeks)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.group_schedule_days_of_week),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DayOfWeek.entries.forEach { weekday ->
                    WeeklyDayChip(
                        label = weekday.getDisplayName(TextStyle.NARROW, appLocale),
                        selected = weekday in selectedDaysOfWeek,
                        onClick = { onDayChange(weekday) }
                    )
                }
            }
        }

        EditorFieldRow(
            label = stringResource(R.string.group_schedule_time),
            value = time.format(timeFormatter),
            icon = Icons.Default.Schedule,
            onClick = { onTimeChange(time) }
        )
    }
}

@Composable
private fun DailyScheduleEditor(
    sinceDate: LocalDate,
    intervalDays: String,
    dailyTimes: List<MedicationGroupScheduleTimeUiState>,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    onSinceDateChange: (LocalDate) -> Unit,
    onIntervalChange: (String) -> Unit,
    onAddTime: () -> Unit,
    onTimeClick: (String, LocalTime) -> Unit,
    onRemoveTime: (String) -> Unit
) {
    Column(
        modifier = Modifier.padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EditorFieldRow(
            label = stringResource(R.string.group_schedule_since),
            value = sinceDate.format(dateFormatter),
            icon = Icons.Default.Event,
            onClick = { onSinceDateChange(sinceDate) }
        )

        OutlinedTextField(
            value = intervalDays,
            onValueChange = onIntervalChange,
            label = { Text(text = stringResource(R.string.group_schedule_every_days)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.group_schedule_times),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onAddTime) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.add_time),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            dailyTimes.forEach { dailyTime ->
                DailyTimeRow(
                    label = stringResource(
                        R.string.group_schedule_time_item,
                        dailyTime.time.format(timeFormatter)
                    ),
                    formattedTime = dailyTime.time.format(timeFormatter),
                    onClick = { onTimeClick(dailyTime.localId, dailyTime.time) },
                    onRemoveClick = { onRemoveTime(dailyTime.localId) }
                )
            }
        }
    }
}

private fun maybeRequestExactAlarmAccess(
    context: android.content.Context,
    launch: (Intent) -> Unit
) {
    if (canScheduleExactAlarms(context)) {
        return
    }

    val intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}")
    )
    launch(intent)
}

@Composable
private fun MedicationGroupMedicationCard(
    medication: MedicationGroupMedicationItemUiState,
    appLocale: java.util.Locale,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val routeColors = routeBadgeColors(medication.routeOfAdministration)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = routeColors.first,
                contentColor = routeColors.second
            ) {
                Text(
                    text = routeBadgeLabel(medication.routeOfAdministration),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = medication.medicineName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(
                        R.string.plan_group_medication_summary,
                        medication.medicineName,
                        medication.dosageMg.toDoubleOrNull()?.formatDose(appLocale) ?: medication.dosageMg,
                        stringResource(medication.routeOfAdministration.labelRes)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit_medication)
                )
            }
            IconButton(onClick = onRemoveClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.remove_medication_from_group)
                )
            }
        }
    }
}

@Composable
private fun EditorSectionHeader(
    title: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        trailing?.invoke()
    }
}

@Composable
private fun EditorSectionCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun EditorFieldRow(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RowScope.WeeklyDayChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
            )
        }
    }
}

@Composable
private fun DailyTimeRow(
    label: String,
    formattedTime: String,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit_time)
                )
            }
            IconButton(onClick = onRemoveClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.remove_time)
                )
            }
        }
    }
}

@Composable
private fun EditorSupportMessage(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

private fun routeBadgeLabel(route: com.mkx.hrttracker.model.medication.RouteOfAdministration): String {
    return when (route) {
        com.mkx.hrttracker.model.medication.RouteOfAdministration.INTRAMUSCULAR -> "IM"
        com.mkx.hrttracker.model.medication.RouteOfAdministration.SUBCUTANEOUS -> "SC"
        com.mkx.hrttracker.model.medication.RouteOfAdministration.SUBLINGUAL -> "SL"
        com.mkx.hrttracker.model.medication.RouteOfAdministration.TRANSDERMAL -> "TD"
        com.mkx.hrttracker.model.medication.RouteOfAdministration.ORAL -> "PO"
        com.mkx.hrttracker.model.medication.RouteOfAdministration.TOPICAL -> "TOP"
        com.mkx.hrttracker.model.medication.RouteOfAdministration.OTHER -> "OTR"
    }
}

@Composable
private fun routeBadgeColors(route: com.mkx.hrttracker.model.medication.RouteOfAdministration): Pair<Color, Color> {
    return when (route) {
        com.mkx.hrttracker.model.medication.RouteOfAdministration.INTRAMUSCULAR,
        com.mkx.hrttracker.model.medication.RouteOfAdministration.SUBCUTANEOUS -> {
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        }
        com.mkx.hrttracker.model.medication.RouteOfAdministration.ORAL,
        com.mkx.hrttracker.model.medication.RouteOfAdministration.SUBLINGUAL -> {
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        }
        else -> {
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        }
    }
}
