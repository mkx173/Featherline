package com.mkx.hrttracker.ui.plan

import android.Manifest
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.model.medication.formatDose
import com.mkx.hrttracker.reminder.canPostNotifications
import com.mkx.hrttracker.reminder.canScheduleExactAlarms
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.ConnectedButtonGroupLayout
import com.mkx.hrttracker.ui.components.DatePickerModal
import com.mkx.hrttracker.ui.components.TimePickerModal
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.medication.MedicationDraftUiState
import com.mkx.hrttracker.ui.medication.StructuredMedicationEditorSheet
import com.mkx.hrttracker.ui.medication.applicationTypeBadgeLabel
import com.mkx.hrttracker.ui.medication.changeApplicationType
import com.mkx.hrttracker.ui.medication.changeCategory
import com.mkx.hrttracker.ui.medication.changeDoseKind
import com.mkx.hrttracker.ui.medication.changeMedicationKey
import com.mkx.hrttracker.ui.medication.changeSelectionKind
import com.mkx.hrttracker.ui.medication.medicationDisplayName
import com.mkx.hrttracker.ui.medication.medicationDoseText
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.UUID

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
        onDecreaseMedicationCount = viewModel::removeMedication,
        onIncreaseMedicationCount = viewModel::increaseMedicationCount,
        onDismissMedicationEditor = viewModel::dismissMedicationEditor,
        onConsumeMedicationEditorSaved = viewModel::consumeMedicationEditorSaved,
        onMedicationDraftChange = viewModel::updateEditingMedicationDraft,
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
    onDecreaseMedicationCount: (String) -> Unit,
    onIncreaseMedicationCount: (String) -> Unit,
    onDismissMedicationEditor: () -> Unit,
    onConsumeMedicationEditorSaved: () -> Unit,
    onMedicationDraftChange: ((MedicationDraftUiState) -> MedicationDraftUiState) -> Unit,
    onSaveMedicationClick: () -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appLocale = rememberAppLocale()
    val context = LocalContext.current
    val groupColorScheme = rememberMedicationGroupColorScheme(uiState.groupColorKey)
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
    val is24Hour = DateFormat.is24HourFormat(context)
    val scheduleOptions = remember {
        listOf(
            MedicationGroupScheduleType.DAILY,
            MedicationGroupScheduleType.WEEKLY
        )
    }
    var pendingSinceDate by remember { mutableStateOf<LocalDate?>(null) }
    var pendingWeeklyTime by remember { mutableStateOf<LocalTime?>(null) }
    var pendingDailyTimeEdit by remember { mutableStateOf<DailyTimeEditRequest?>(null) }
    val canSave = resolveMedicationGroupName(
        groupName = uiState.groupName,
        defaultGroupName = uiState.defaultGroupName,
        isEditing = uiState.isEditing
    ).isNotBlank() &&
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

    pendingSinceDate?.let { initialSinceDate ->
        DatePickerModal(
            onDateSelected = onSinceDateChange,
            onDismiss = { pendingSinceDate = null },
            initialSelectedDate = initialSinceDate
        )
    }

    pendingWeeklyTime?.let { initialWeeklyTime ->
        TimePickerModal(
            onTimeSelected = onWeeklyTimeChange,
            onDismiss = { pendingWeeklyTime = null },
            initialTime = initialWeeklyTime,
            is24Hour = is24Hour
        )
    }

    pendingDailyTimeEdit?.let { dailyTimeEdit ->
        TimePickerModal(
            onTimeSelected = { selectedTime ->
                onDailyTimeChange(dailyTimeEdit.localId, selectedTime)
            },
            onDismiss = { pendingDailyTimeEdit = null },
            initialTime = dailyTimeEdit.initialTime,
            is24Hour = is24Hour
        )
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
                        modifier = Modifier.padding(end = 8.dp)
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.groupName,
                        onValueChange = onGroupNameChange,
                        label = { Text(text = stringResource(R.string.field_medication_group_name)) },
                        placeholder = if (!uiState.isEditing && uiState.defaultGroupName.isNotBlank()) {
                            {
                                Text(text = uiState.defaultGroupName)
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    GroupColorPreviewStrip(
                        color = groupColorScheme.primary
                    )
                }
            }

            item {
                EditorSectionHeader(title = stringResource(R.string.group_schedule_title))
                Column(
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
                ) {
                    ConnectedButtonGroup(
                        modifier = Modifier.fillMaxWidth(),
                        options = scheduleOptions,
                        selectedOption = uiState.scheduleType,
                        optionLabel = { scheduleType ->
                            stringResource(
                                if (scheduleType == MedicationGroupScheduleType.DAILY) {
                                    R.string.group_schedule_daily
                                } else {
                                    R.string.group_schedule_weekly
                                }
                            )
                        },
                        onOptionSelected = onScheduleTypeChange,
                        layout = ConnectedButtonGroupLayout.ROW,
                        expandOptions = true,
                    )

                    if (uiState.scheduleType == MedicationGroupScheduleType.WEEKLY) {
                        WeeklyScheduleEditor(
                            sinceDate = uiState.sinceDate,
                            intervalWeeks = uiState.weeklyIntervalWeeks,
                            selectedDaysOfWeek = uiState.weeklyDaysOfWeek,
                            time = uiState.weeklyTime,
                            appLocale = appLocale,
                            dateFormatter = dateFormatter,
                            timeFormatter = timeFormatter,
                            onSinceDateChange = { currentDate -> pendingSinceDate = currentDate },
                            onIntervalChange = onWeeklyIntervalChange,
                            onDayChange = onWeeklyDayChange,
                            onTimeChange = { currentTime -> pendingWeeklyTime = currentTime }
                        )
                    } else {
                        DailyScheduleEditor(
                            sinceDate = uiState.sinceDate,
                            intervalDays = uiState.dailyIntervalDays,
                            dailyTimes = uiState.dailyTimes,
                            dateFormatter = dateFormatter,
                            timeFormatter = timeFormatter,
                            onSinceDateChange = { currentDate -> pendingSinceDate = currentDate },
                            onIntervalChange = onDailyIntervalChange,
                            onAddTime = onAddDailyTime,
                            onTimeClick = { localId, currentTime ->
                                pendingDailyTimeEdit = DailyTimeEditRequest(
                                    localId = localId,
                                    initialTime = currentTime
                                )
                            },
                            onRemoveTime = onRemoveDailyTime
                        )
                    }
                }
            }

            item {
                EditorSectionHeader(title = stringResource(R.string.group_notifications_title))
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NotificationsCard(
                        enabled = uiState.notificationsEnabled,
                        toggleEnabled = notificationsToggleEnabled,
                        onToggle = onNotificationsEnabledChange
                    )
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
                    Column(
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

            items(
                items = uiState.medications,
                key = { it.localId }
            ) { medication ->
                MedicationGroupMedicationCard(
                    medication = medication,
                    groupColorKey = uiState.groupColorKey,
                    appLocale = appLocale,
                    onClick = { onMedicationClick(medication.localId) },
                    onDecreaseClick = { onDecreaseMedicationCount(medication.localId) },
                    onIncreaseClick = { onIncreaseMedicationCount(medication.localId) }
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
        StructuredMedicationEditorSheet(
            modifier = Modifier.fillMaxSize(),
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
            draft = medication.draft,
            onCategoryChange = { category ->
                onMedicationDraftChange { draft -> draft.changeCategory(category) }
            },
            onApplicationTypeChange = { applicationType ->
                onMedicationDraftChange { draft -> draft.changeApplicationType(applicationType) }
            },
            onSelectionKindChange = { selectionKind ->
                onMedicationDraftChange { draft -> draft.changeSelectionKind(selectionKind) }
            },
            onMedicationKeyChange = { medicationKey ->
                onMedicationDraftChange { draft -> draft.changeMedicationKey(medicationKey) }
            },
            onCustomMedicationNameChange = { medicationName ->
                onMedicationDraftChange { draft -> draft.copy(customMedicationName = medicationName) }
            },
            onDoseKindChange = { doseKind ->
                onMedicationDraftChange { draft -> draft.changeDoseKind(doseKind) }
            },
            onDoseMgChange = { doseMg ->
                onMedicationDraftChange { draft -> draft.copy(doseMg = doseMg) }
            },
            onGelPercentChange = { gelPercent ->
                onMedicationDraftChange { draft -> draft.copy(gelPercent = gelPercent) }
            },
            onGelWeightChange = { gelWeight ->
                onMedicationDraftChange { draft -> draft.copy(gelWeightGrams = gelWeight) }
            },
            onPatchReleaseRateChange = { releaseRate ->
                onMedicationDraftChange { draft ->
                    draft.copy(patchReleaseRateMcgPerDay = releaseRate)
                }
            },
            errorMessageRes = uiState.medicationEditorErrorMessageRes,
            onConfirm = onSaveMedicationClick
        )
    }
}

private data class DailyTimeEditRequest(
    val localId: String,
    val initialTime: LocalTime,
)

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

        IntervalStepperCard(
            label = stringResource(R.string.group_schedule_every_weeks),
            value = parseScheduleInterval(intervalWeeks),
            unit = pluralStringResource(
                R.plurals.group_schedule_weeks_unit,
                parseScheduleInterval(intervalWeeks),
                parseScheduleInterval(intervalWeeks)
            ),
            onDecreaseClick = { onIntervalChange(decrementScheduleInterval(intervalWeeks)) },
            onIncreaseClick = { onIntervalChange(incrementScheduleInterval(intervalWeeks)) },
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.group_schedule_days_of_week),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ConnectedButtonGroup(
                modifier = Modifier.fillMaxWidth(),
                options = DayOfWeek.entries.toList(),
                selectedOptions = selectedDaysOfWeek,
                optionLabel = { weekday ->
                    weekday.getDisplayName(TextStyle.NARROW, appLocale)
                },
                onOptionToggled = onDayChange,
                layout = ConnectedButtonGroupLayout.ROW,
                expandOptions = true,
            )
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EditorFieldRow(
            label = stringResource(R.string.group_schedule_since),
            value = sinceDate.format(dateFormatter),
            icon = Icons.Default.Event,
            onClick = { onSinceDateChange(sinceDate) }
        )

        IntervalStepperCard(
            label = stringResource(R.string.group_schedule_every_days),
            value = parseScheduleInterval(intervalDays),
            unit = pluralStringResource(
                R.plurals.group_schedule_days_unit,
                parseScheduleInterval(intervalDays),
                parseScheduleInterval(intervalDays)
            ),
            onDecreaseClick = { onIntervalChange(decrementScheduleInterval(intervalDays)) },
            onIncreaseClick = { onIntervalChange(incrementScheduleInterval(intervalDays)) },
        )

        DailyTimesCard(
            times = dailyTimes,
            timeFormatter = timeFormatter,
            onAddTime = onAddTime,
            onTimeClick = onTimeClick,
            onRemoveTime = onRemoveTime
        )
    }
}

@Composable
private fun IntervalStepperCard(
    label: String,
    value: Int,
    unit: String,
    onDecreaseClick: () -> Unit,
    onIncreaseClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            StepperCircleButton(
                icon = Icons.Default.Remove,
                contentDescription = stringResource(R.string.decrease_schedule_interval),
                enabled = value > 1,
                onClick = onDecreaseClick
            )
            StepperCircleButton(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.increase_schedule_interval),
                enabled = true,
                onClick = onIncreaseClick
            )
        }
    }
}

@Composable
private fun StepperCircleButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = if (enabled) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
        }
    }
}

internal fun parseScheduleInterval(value: String): Int {
    return value.toIntOrNull()?.takeIf { it > 0 } ?: 1
}

internal fun incrementScheduleInterval(value: String): String {
    return (parseScheduleInterval(value) + 1).toString()
}

internal fun decrementScheduleInterval(value: String): String {
    return maxOf(1, parseScheduleInterval(value) - 1).toString()
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
    groupColorKey: MedicationGroupColorKey,
    appLocale: java.util.Locale,
    onClick: () -> Unit,
    onDecreaseClick: () -> Unit,
    onIncreaseClick: () -> Unit,
) {
    val groupColorScheme = rememberMedicationGroupColorScheme(groupColorKey)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = groupColorScheme.primaryContainer,
                contentColor = groupColorScheme.onPrimaryContainer
            ) {
                Text(
                    text = applicationTypeBadgeLabel(medication.details.applicationType),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = medicationDisplayName(medication.details),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = medicationDoseText(medication.details)
                        ?: stringResource(medication.details.applicationType.labelRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MedicationCountEditor(
                count = medication.count,
                onDecreaseClick = onDecreaseClick,
                onIncreaseClick = onIncreaseClick
            )
        }
    }
}

@Composable
private fun MedicationCountEditor(
    count: Int,
    onDecreaseClick: () -> Unit,
    onIncreaseClick: () -> Unit
) {
    val isRemoveStep = count == 1
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDecreaseClick,
                modifier = Modifier.size(32.dp),
                colors = if (isRemoveStep) {
                    IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else {
                    IconButtonDefaults.iconButtonColors()
                }
            ) {
                Icon(
                    imageVector = if (isRemoveStep) {
                        Icons.Default.Delete
                    } else {
                        Icons.Default.Remove
                    },
                    contentDescription = stringResource(
                        if (isRemoveStep) {
                            R.string.remove_medication_from_group
                        } else {
                            R.string.decrease_medication_count
                        }
                    )
                )
            }
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(
                onClick = onIncreaseClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.increase_medication_count)
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
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing?.invoke()
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(),
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
private fun DailyTimesCard(
    times: List<MedicationGroupScheduleTimeUiState>,
    timeFormatter: DateTimeFormatter,
    onAddTime: () -> Unit,
    onTimeClick: (String, LocalTime) -> Unit,
    onRemoveTime: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.group_schedule_times_with_count,
                        times.size
                    ).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AddTimeChip(onClick = onAddTime)
            }
            if (times.isEmpty()) {
                Text(
                    text = stringResource(R.string.add_time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            } else {
                times.forEach { dailyTime ->
                    DailyTimeRow(
                        formattedTime = dailyTime.time.format(timeFormatter),
                        onClick = { onTimeClick(dailyTime.localId, dailyTime.time) },
                        onRemoveClick = { onRemoveTime(dailyTime.localId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddTimeChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.add_time),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DailyTimeRow(
    formattedTime: String,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.remove_time),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun NotificationsCard(
    enabled: Boolean,
    toggleEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (enabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Icon(
                        imageVector = if (enabled) {
                            Icons.Default.Notifications
                        } else {
                            Icons.Default.NotificationsOff
                        },
                        contentDescription = null,
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.group_notifications_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.group_notifications_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                enabled = toggleEnabled
            )
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

@Composable
private fun GroupColorPreviewStrip(
    color: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(
                color = color.copy(alpha = 0.32f),
                shape = RoundedCornerShape(999.dp)
            )
    )
}

@Preview(
    name = "Group Editor Daily",
    showBackground = true,
    widthDp = 420,
    heightDp = 920
)
@Composable
private fun MedicationGroupEditorDailyPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        MedicationGroupEditorScreenContent(
            uiState = buildMedicationGroupEditorPreviewUiState(
                scheduleType = MedicationGroupScheduleType.DAILY,
                editingGroupId = null,
                remindersEnabled = false,
                notificationsEnabled = true
            ),
            onNavigateBack = { },
            onGroupNameChange = { },
            onScheduleTypeChange = { },
            onSinceDateChange = { },
            onNotificationsEnabledChange = { },
            notificationsToggleEnabled = false,
            showInexactReminderWarning = false,
            onWeeklyIntervalChange = { },
            onWeeklyDayChange = { },
            onWeeklyTimeChange = { },
            onDailyIntervalChange = { },
            onAddDailyTime = { },
            onDailyTimeChange = { _, _ -> },
            onRemoveDailyTime = { },
            onAddMedication = { },
            onMedicationClick = { },
            onDecreaseMedicationCount = { },
            onIncreaseMedicationCount = { },
            onDismissMedicationEditor = { },
            onConsumeMedicationEditorSaved = { },
            onMedicationDraftChange = { },
            onSaveMedicationClick = { },
            onSaveClick = { },
            onDeleteClick = { },
            onDeleteDismiss = { },
            onDeleteConfirm = { }
        )
    }
}

@Preview(
    name = "Group Editor Weekly",
    showBackground = true,
    widthDp = 420,
    heightDp = 920
)
@Composable
private fun MedicationGroupEditorWeeklyPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        MedicationGroupEditorScreenContent(
            uiState = buildMedicationGroupEditorPreviewUiState(
                scheduleType = MedicationGroupScheduleType.WEEKLY,
                editingGroupId = "preview-weekly-group",
                remindersEnabled = true,
                notificationsEnabled = true
            ),
            onNavigateBack = { },
            onGroupNameChange = { },
            onScheduleTypeChange = { },
            onSinceDateChange = { },
            onNotificationsEnabledChange = { },
            notificationsToggleEnabled = true,
            showInexactReminderWarning = true,
            onWeeklyIntervalChange = { },
            onWeeklyDayChange = { },
            onWeeklyTimeChange = { },
            onDailyIntervalChange = { },
            onAddDailyTime = { },
            onDailyTimeChange = { _, _ -> },
            onRemoveDailyTime = { },
            onAddMedication = { },
            onMedicationClick = { },
            onDecreaseMedicationCount = { },
            onIncreaseMedicationCount = { },
            onDismissMedicationEditor = { },
            onConsumeMedicationEditorSaved = { },
            onMedicationDraftChange = { },
            onSaveMedicationClick = { },
            onSaveClick = { },
            onDeleteClick = { },
            onDeleteDismiss = { },
            onDeleteConfirm = { }
        )
    }
}

private fun buildMedicationGroupEditorPreviewUiState(
    scheduleType: MedicationGroupScheduleType,
    editingGroupId: String?,
    remindersEnabled: Boolean,
    notificationsEnabled: Boolean
): MedicationGroupEditorUiState {
    val today = LocalDate.now()
    return MedicationGroupEditorUiState(
        editingGroupId = editingGroupId,
        groupName = if (scheduleType == MedicationGroupScheduleType.DAILY) {
            "Daily estradiol"
        } else {
            "Injection cycle"
        },
        groupColorKey = if (scheduleType == MedicationGroupScheduleType.DAILY) {
            MedicationGroupColorKey.TEAL
        } else {
            MedicationGroupColorKey.INDIGO
        },
        scheduleType = scheduleType,
        sinceDate = today.minusWeeks(6),
        weeklyIntervalWeeks = "1",
        weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        weeklyTime = LocalTime.of(9, 0),
        dailyIntervalDays = "1",
        dailyTimes = listOf(
            MedicationGroupScheduleTimeUiState(
                localId = "morning-dose",
                time = LocalTime.of(8, 0)
            ),
            MedicationGroupScheduleTimeUiState(
                localId = "evening-dose",
                time = LocalTime.of(20, 0)
            )
        ),
        remindersEnabled = remindersEnabled,
        notificationsEnabled = notificationsEnabled,
        medications = listOf(
            MedicationGroupMedicationItemUiState(
                localId = "med-1",
                persistedMedicationId = UUID.fromString("a5a8da0b-2510-4f7c-8bf3-fbc74b409321").toString(),
                details = MedicationDetails(
                    category = MedicationCategory.ESTRADIOL,
                    applicationType = if (scheduleType == MedicationGroupScheduleType.WEEKLY) {
                        MedicationApplicationType.INJECTION
                    } else {
                        MedicationApplicationType.ORAL
                    },
                    selection = MedicationSelection.Catalog(
                        if (scheduleType == MedicationGroupScheduleType.WEEKLY) {
                            MedicationKey.ESTRADIOL_VALERATE
                        } else {
                            MedicationKey.ESTRADIOL
                        }
                    ),
                    dose = MedicationDose.MgAsMedicine(
                        if (scheduleType == MedicationGroupScheduleType.WEEKLY) 5.0 else 2.0
                    )
                )
            ),
            MedicationGroupMedicationItemUiState(
                localId = "med-2",
                persistedMedicationId = UUID.fromString("73ceca25-8547-43cf-8517-0d1e46a95d56").toString(),
                details = MedicationDetails(
                    category = MedicationCategory.ANTIANDROGEN,
                    applicationType = MedicationApplicationType.ORAL,
                    selection = MedicationSelection.Catalog(MedicationKey.SPIRONOLACTONE),
                    dose = MedicationDose.MgAsMedicine(50.0)
                )
            )
        )
    )
}
