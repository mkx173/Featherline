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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Remove
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
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
import com.mkx.hrttracker.ui.components.AddChip
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.ConnectedButtonGroupLayout
import com.mkx.hrttracker.ui.components.DatePickerModal
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
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
    val masterReminderPermissionDeniedMessage =
        stringResource(R.string.settings_reminders_permission_denied)
    val reminderNotificationsUnavailableMessage =
        stringResource(R.string.settings_reminders_notifications_unavailable)
    var isExactAlarmDialogVisible by rememberSaveable { mutableStateOf(false) }
    var showInexactReminderWarning by rememberSaveable { mutableStateOf(false) }
    var pendingNotificationEnableRequest by rememberSaveable { mutableStateOf<String?>(null) }

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
            hasNotificationAccess = canPostNotifications(context)
            if (shouldEnableMasterReminders(pendingNotificationEnableRequest)) {
                viewModel.setMasterRemindersEnabled(true)
            }
            if (shouldEnableGroupNotifications(pendingNotificationEnableRequest)) {
                viewModel.updateNotificationsEnabled(true)
                if (canScheduleExactAlarms(context)) {
                    showInexactReminderWarning = false
                } else {
                    isExactAlarmDialogVisible = true
                }
            }
        } else {
            if (shouldEnableMasterReminders(pendingNotificationEnableRequest)) {
                Toast.makeText(
                    context,
                    masterReminderPermissionDeniedMessage,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                viewModel.updateNotificationsEnabled(false)
                hasNotificationAccess = false
                Toast.makeText(
                    context,
                    notificationPermissionDeniedMessage,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        pendingNotificationEnableRequest = null
    }
    val enableMasterReminders = {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingNotificationEnableRequest = MASTER_AND_GROUP_NOTIFICATION_ENABLE_REQUEST
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Toast.makeText(
                context,
                reminderNotificationsUnavailableMessage,
                Toast.LENGTH_SHORT
            ).show()
        } else {
            viewModel.setMasterRemindersEnabled(true)
            viewModel.updateNotificationsEnabled(true)
            if (canScheduleExactAlarms(context)) {
                showInexactReminderWarning = false
            } else {
                isExactAlarmDialogVisible = true
            }
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
        if (!hasNotificationAccess) {
            pendingNotificationEnableRequest = null
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
                pendingNotificationEnableRequest = null
                isExactAlarmDialogVisible = false
                showInexactReminderWarning = false
            } else if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingNotificationEnableRequest = GROUP_ONLY_NOTIFICATION_ENABLE_REQUEST
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
        onRequestExactAlarmAccess = {
            maybeRequestExactAlarmAccess(context, exactAlarmAccessLauncher::launch)
        },
        onRecoverMasterReminders = enableMasterReminders,
        hasNotificationAccess = hasNotificationAccess,
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
    onRequestExactAlarmAccess: () -> Unit,
    onRecoverMasterReminders: () -> Unit,
    hasNotificationAccess: Boolean,
    notificationsToggleEnabled: Boolean,
    showInexactReminderWarning: Boolean,
    onWeeklyIntervalChange: (String) -> Unit,
    onWeeklyDayChange: (DayOfWeek) -> Unit,
    onWeeklyTimeChange: (LocalTime) -> Unit,
    onDailyIntervalChange: (String) -> Unit,
    onAddDailyTime: (LocalTime) -> Unit,
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
    val duplicateDailyTimeMessage =
        stringResource(R.string.group_schedule_duplicate_time)
    val medicationEditorInfoMessage = uiState.medicationEditorInfoMessageRes?.let { messageRes ->
        stringResource(messageRes)
    }
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
    val notificationSupportState = resolveNotificationSupportState(
        hasNotificationAccess = hasNotificationAccess,
        remindersEnabled = uiState.remindersEnabled,
        showInexactReminderWarning = showInexactReminderWarning
    )
    val is24Hour = DateFormat.is24HourFormat(context)
    val scheduleOptions = remember {
        listOf(
            MedicationGroupScheduleType.DAILY,
            MedicationGroupScheduleType.WEEKLY
        )
    }
    var pendingSinceDate by remember { mutableStateOf<LocalDate?>(null) }
    var pendingWeeklyTime by remember { mutableStateOf<LocalTime?>(null) }
    var pendingNewDailyTime by remember { mutableStateOf<LocalTime?>(null) }
    var pendingDailyTimeEdit by remember { mutableStateOf<DailyTimeEditRequest?>(null) }
    var pendingMedicationRemoval by remember { mutableStateOf<MedicationRemovalRequest?>(null) }
    var isMasterReminderRecoveryDialogVisible by remember { mutableStateOf(false) }
    val groupNameFocusRequester = remember { FocusRequester() }
    val canSave = hasSaveableMedicationGroupContent(uiState) &&
        !uiState.isSaving &&
        !uiState.isDeleting

    LaunchedEffect(uiState.isMedicationEditorSaved, medicationEditorInfoMessage) {
        if (uiState.isMedicationEditorSaved) {
            medicationEditorInfoMessage?.let { message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
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
            onTimeSelected = { selectedTime ->
                onWeeklyTimeChange(selectedTime)
                true
            },
            onDismiss = { pendingWeeklyTime = null },
            initialTime = initialWeeklyTime,
            is24Hour = is24Hour
        )
    }

    pendingNewDailyTime?.let { initialDailyTime ->
        TimePickerModal(
            onTimeSelected = { selectedTime ->
                if (hasDuplicateDailyTime(uiState.dailyTimes, selectedTime)) {
                    Toast.makeText(context, duplicateDailyTimeMessage, Toast.LENGTH_SHORT).show()
                    false
                } else {
                    onAddDailyTime(selectedTime)
                    true
                }
            },
            onDismiss = { pendingNewDailyTime = null },
            initialTime = initialDailyTime,
            is24Hour = is24Hour
        )
    }

    pendingDailyTimeEdit?.let { dailyTimeEdit ->
        TimePickerModal(
            onTimeSelected = { selectedTime ->
                if (hasDuplicateDailyTime(
                        dailyTimes = uiState.dailyTimes,
                        time = selectedTime,
                        excludingLocalId = dailyTimeEdit.localId
                    )
                ) {
                    Toast.makeText(context, duplicateDailyTimeMessage, Toast.LENGTH_SHORT).show()
                    false
                } else {
                    onDailyTimeChange(dailyTimeEdit.localId, selectedTime)
                    true
                }
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

    if (isMasterReminderRecoveryDialogVisible) {
        AlertDialog(
            onDismissRequest = { isMasterReminderRecoveryDialogVisible = false },
            title = {
                Text(text = stringResource(R.string.group_notifications_reenable_title))
            },
            text = {
                Text(text = stringResource(R.string.group_notifications_reenable_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isMasterReminderRecoveryDialogVisible = false
                        onRecoverMasterReminders()
                    }
                ) {
                    Text(text = stringResource(R.string.group_notifications_reenable_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { isMasterReminderRecoveryDialogVisible = false }
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingMedicationRemoval?.let { removalRequest ->
        AlertDialog(
            onDismissRequest = { pendingMedicationRemoval = null },
            title = {
                Text(text = stringResource(R.string.delete_group_medication_title))
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.delete_group_medication_confirmation,
                        removalRequest.medicationName
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDecreaseMedicationCount(removalRequest.localId)
                        pendingMedicationRemoval = null
                    }
                ) {
                    Text(text = stringResource(R.string.delete_entries_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMedicationRemoval = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(dimensionResource(R.dimen.padding_medium)),
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
                        trailingIcon = if (shouldShowGroupNameClearAction(uiState.groupName)) {
                            {
                                IconButton(
                                    onClick = {
                                        onGroupNameChange("")
                                        groupNameFocusRequester.requestFocus()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(
                                            R.string.clear_group_name
                                        )
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(groupNameFocusRequester),
                        singleLine = true
                    )
                }
            }

            item {
                EditorSectionHeader(
                    title = stringResource(R.string.group_medications_title),
                    trailing = {
                        AddChip(onClick = onAddMedication)
                    }
                )
                if (uiState.medications.isEmpty()) {
                    EditorSupportMessage(stringResource(R.string.group_medications_empty))
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))
                    ) {
                        uiState.medications.forEachIndexed { index, medication ->
                            val medicationName = medicationDisplayName(medication.details)
                            MedicationGroupMedicationCard(
                                medication = medication,
                                groupColorKey = uiState.groupColorKey,
                                appLocale = appLocale,
                                onClick = { onMedicationClick(medication.localId) },
                                onDecreaseClick = {
                                    if (shouldConfirmMedicationRemoval(medication.count)) {
                                        pendingMedicationRemoval = MedicationRemovalRequest(
                                            localId = medication.localId,
                                            medicationName = medicationName
                                        )
                                    } else {
                                        onDecreaseMedicationCount(medication.localId)
                                    }
                                },
                                onIncreaseClick = { onIncreaseMedicationCount(medication.localId) },
                                index = index,
                                count = uiState.medications.size
                            )
                        }
                    }
                }
            }

            item {
                EditorSectionHeader(title = stringResource(R.string.group_schedule_title))
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompositionLocalProvider(
                        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
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
                            onAddTime = {
                                pendingNewDailyTime = LocalTime.now()
                                    .withSecond(0)
                                    .withNano(0)
                            },
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
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))
                ) {
                    NotificationsCard(
                        enabled = uiState.notificationsEnabled,
                        toggleEnabled = notificationsToggleEnabled,
                        onToggle = onNotificationsEnabledChange,
                        index = 0,
                        count = if (notificationSupportState == NotificationSupportState.NONE) 1 else 2
                    )
                    when (notificationSupportState) {
                        NotificationSupportState.ACCESS_OFF -> {
                            EditorSupportMessage(
                                text = stringResource(R.string.settings_reminders_permission_off_summary),
                                icon = Icons.Rounded.Info,
                                onClick = onRecoverMasterReminders,
                                showChevron = true,
                                index = 1,
                                count = 2
                            )
                        }

                        NotificationSupportState.MASTER_OFF -> {
                        EditorSupportMessage(
                            text = stringResource(R.string.group_notifications_master_disabled),
                            icon = Icons.Rounded.Info,
                            onClick = { isMasterReminderRecoveryDialogVisible = true },
                            showChevron = true,
                            index = 1,
                            count = 2
                        )
                        }

                        NotificationSupportState.INEXACT -> {
                            EditorSupportMessage(
                                text = stringResource(R.string.group_notifications_inexact_warning),
                                icon = Icons.Rounded.Info,
                                onClick = onRequestExactAlarmAccess,
                                showChevron = true,
                                index = 1,
                                count = 2
                            )
                        }

                        NotificationSupportState.NONE -> Unit
                    }
                }
            }

            if (uiState.isEditing) {
                item {
                    EditorSectionHeader(title = stringResource(R.string.group_danger_zone_title))
                    DeleteMedicationGroupCard(
                        enabled = !uiState.isSaving && !uiState.isDeleting,
                        onClick = onDeleteClick
                    )
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
            confirmButtonText = stringResource(R.string.save),
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

private data class MedicationRemovalRequest(
    val localId: String,
    val medicationName: String,
)

internal fun shouldConfirmMedicationRemoval(count: Int): Boolean = count <= 1

internal enum class NotificationSupportState {
    NONE,
    ACCESS_OFF,
    MASTER_OFF,
    INEXACT
}

internal fun resolveNotificationSupportState(
    hasNotificationAccess: Boolean,
    remindersEnabled: Boolean,
    showInexactReminderWarning: Boolean
): NotificationSupportState = when {
    !hasNotificationAccess -> NotificationSupportState.ACCESS_OFF
    !remindersEnabled -> NotificationSupportState.MASTER_OFF
    showInexactReminderWarning -> NotificationSupportState.INEXACT
    else -> NotificationSupportState.NONE
}

internal const val GROUP_ONLY_NOTIFICATION_ENABLE_REQUEST = "group_only"
internal const val MASTER_AND_GROUP_NOTIFICATION_ENABLE_REQUEST = "master_and_group"

internal fun shouldEnableMasterReminders(
    pendingNotificationEnableRequest: String?
): Boolean = pendingNotificationEnableRequest == MASTER_AND_GROUP_NOTIFICATION_ENABLE_REQUEST

internal fun shouldEnableGroupNotifications(
    pendingNotificationEnableRequest: String?
): Boolean = pendingNotificationEnableRequest == GROUP_ONLY_NOTIFICATION_ENABLE_REQUEST ||
    pendingNotificationEnableRequest == MASTER_AND_GROUP_NOTIFICATION_ENABLE_REQUEST

internal fun shouldShowGroupNameClearAction(groupName: String): Boolean = groupName.isNotBlank()

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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun MedicationGroupMedicationCard(
    medication: MedicationGroupMedicationItemUiState,
    groupColorKey: MedicationGroupColorKey,
    appLocale: java.util.Locale,
    onClick: () -> Unit,
    onDecreaseClick: () -> Unit,
    onIncreaseClick: () -> Unit,
    index: Int = 0,
    count: Int = 1
) {
    val groupColorScheme = rememberMedicationGroupColorScheme(groupColorKey)
    EditorSegmentedListItem(
        index = index,
        count = count,
        onClick = onClick,
        leadingContent = {
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
        },
        supportingContent = {
            Text(
                text = medicationDoseText(medication.details)
                    ?: stringResource(medication.details.applicationType.labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            MedicationCountEditor(
                count = medication.count,
                onDecreaseClick = onDecreaseClick,
                onIncreaseClick = onIncreaseClick
            )
        }
    ) {
        Text(
            text = medicationDisplayName(medication.details),
            style = MaterialTheme.typography.titleMedium
        )
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDecreaseClick,
                modifier = Modifier.size(32.dp),
                colors = if (isRemoveStep) {
                    IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    IconButtonDefaults.iconButtonColors()
                }
            ) {
                Icon(
                    imageVector = if (isRemoveStep) {
                        Icons.Rounded.Delete
                    } else {
                        Icons.Rounded.Remove
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
                    imageVector = Icons.Rounded.Add,
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
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = trailing?.let { 0.dp } ?: 4.dp)
        )
        trailing?.invoke()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorSupportMessage(
    text: String,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    index: Int = 0,
    count: Int = 1
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {
        EditorSegmentedListItem(
            index = index,
            count = count,
            onClick = onClick ?: {},
            modifier = Modifier.wrapContentHeight(),
            leadingContent = icon?.let { iconVector ->
                {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            trailingContent = if (showChevron) {
                {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                null
            }
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
            onRequestExactAlarmAccess = { },
            onRecoverMasterReminders = { },
            hasNotificationAccess = false,
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
                notificationsEnabled = true,
            ).copy(
                medications = emptyList()
            ),
            onNavigateBack = { },
            onGroupNameChange = { },
            onScheduleTypeChange = { },
            onSinceDateChange = { },
            onNotificationsEnabledChange = { },
            onRequestExactAlarmAccess = { },
            onRecoverMasterReminders = { },
            hasNotificationAccess = true,
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

