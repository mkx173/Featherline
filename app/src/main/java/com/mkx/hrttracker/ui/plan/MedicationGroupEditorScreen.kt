package com.mkx.hrttracker.ui.plan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
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
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.model.medication.nextOccurrencesFrom
import com.mkx.hrttracker.reminder.canPostNotifications
import com.mkx.hrttracker.reminder.canScheduleExactAlarms
import com.mkx.hrttracker.reminder.shouldShowNotificationPermissionRecoveryToast
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.ConnectedButtonGroupLayout
import com.mkx.hrttracker.ui.components.DatePickerModal
import com.mkx.hrttracker.ui.components.ExactAlarmAccessDialog
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.MedicationCard
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.TimePickerModal
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.medication.MedicationDefinitionEditorSheet
import com.mkx.hrttracker.ui.medication.MedicationDraftUiState
import com.mkx.hrttracker.ui.medication.changeApplicationType
import com.mkx.hrttracker.ui.medication.changeCategory
import com.mkx.hrttracker.ui.medication.changeCustomDoseUnit
import com.mkx.hrttracker.ui.medication.changeDoseKind
import com.mkx.hrttracker.ui.medication.changeMedicationKey
import com.mkx.hrttracker.ui.medication.medicationDisplayName
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import com.mkx.hrttracker.util.medicationGroupScheduleDateFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.uses24HourTimeFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@Composable
fun MedicationGroupEditorScreen(
    onNavigateBack: () -> Unit,
    onGroupSaved: () -> Unit,
    onGroupRecreated: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedicationGroupEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentMinute by viewModel.currentMinute.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasNotificationAccess by remember { mutableStateOf(canPostNotifications(context)) }
    val reminderNotificationsUnavailableMessage =
        stringResource(R.string.settings_reminders_notifications_unavailable)
    var isExactAlarmDialogVisible by rememberSaveable { mutableStateOf(false) }
    var showInexactReminderWarning by rememberSaveable { mutableStateOf(false) }
    var pendingNotificationEnableRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var hasRequestedNotificationPermission by rememberSaveable { mutableStateOf(false) }

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
                    reminderNotificationsUnavailableMessage,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                viewModel.updateNotificationsEnabled(false)
                hasNotificationAccess = false
                Toast.makeText(
                    context,
                    reminderNotificationsUnavailableMessage,
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
            if (
                shouldShowNotificationPermissionRecoveryToast(
                    sdkInt = Build.VERSION.SDK_INT,
                    hasRequestedPermissionBefore = hasRequestedNotificationPermission,
                    shouldShowPermissionRationale = activity?.let {
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            it,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    } ?: false
                )
            ) {
                Toast.makeText(
                    context,
                    reminderNotificationsUnavailableMessage,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                hasRequestedNotificationPermission = true
                pendingNotificationEnableRequest = MASTER_AND_GROUP_NOTIFICATION_ENABLE_REQUEST
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
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

    LaunchedEffect(uiState.recreatedGroupId) {
        val recreatedGroupUuid = uiState.recreatedGroupId
            ?.let { groupId -> runCatching { UUID.fromString(groupId) }.getOrNull() }
        if (recreatedGroupUuid != null) {
            viewModel.consumeRecreatedGroupId()
            onGroupRecreated(recreatedGroupUuid)
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
        ExactAlarmAccessDialog(
            onConfirm = {
                isExactAlarmDialogVisible = false
                maybeRequestExactAlarmAccess(context, exactAlarmAccessLauncher::launch)
            },
            onDismiss = {
                isExactAlarmDialogVisible = false
                showInexactReminderWarning = true
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
                if (
                    shouldShowNotificationPermissionRecoveryToast(
                        sdkInt = Build.VERSION.SDK_INT,
                        hasRequestedPermissionBefore = hasRequestedNotificationPermission,
                        shouldShowPermissionRationale = activity?.let {
                            ActivityCompat.shouldShowRequestPermissionRationale(
                                it,
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        } ?: false
                    )
                ) {
                    Toast.makeText(
                        context,
                        reminderNotificationsUnavailableMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    hasRequestedNotificationPermission = true
                    pendingNotificationEnableRequest = GROUP_ONLY_NOTIFICATION_ENABLE_REQUEST
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
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
            isExactAlarmDialogVisible = true
        },
        onRecoverMasterReminders = enableMasterReminders,
        hasNotificationAccess = hasNotificationAccess,
        notificationsToggleEnabled = uiState.remindersEnabled && hasNotificationAccess && !uiState.isArchived,
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
        onMedicationDraftChange = viewModel::updateEditingMedicationDraft,
        onEditingMedicationCountTextChange = viewModel::updateEditingMedicationCountText,
        onDecreaseEditingMedicationCount = viewModel::decreaseEditingMedicationCount,
        onIncreaseEditingMedicationCount = viewModel::increaseEditingMedicationCount,
        onSaveMedicationClick = viewModel::saveEditingMedication,
        onSaveClick = viewModel::saveGroup,
        onSaveMedicationGroupResultConsumed = viewModel::consumeSaveMedicationGroupResult,
        onDeleteRelatedEntriesClick = viewModel::showDeleteRelatedEntriesConfirmation,
        onDeleteRelatedEntriesDismiss = viewModel::dismissDeleteRelatedEntriesConfirmation,
        onDeleteRelatedEntriesConfirm = viewModel::deleteRelatedEntries,
        onDeleteRelatedEntriesResultConsumed = viewModel::consumeDeleteRelatedEntriesResult,
        onDeleteMedicationGroupResultConsumed = viewModel::consumeDeleteMedicationGroupResult,
        onArchiveClick = viewModel::showArchiveConfirmation,
        onArchiveDismiss = viewModel::dismissArchiveConfirmation,
        onArchiveConfirm = viewModel::archiveGroup,
        onArchiveMedicationGroupResultConsumed = viewModel::consumeArchiveMedicationGroupResult,
        onArchiveAndRecreateClick = viewModel::showArchiveAndRecreateConfirmation,
        onArchiveAndRecreateDismiss = viewModel::dismissArchiveAndRecreateConfirmation,
        onArchiveAndRecreateConfirm = viewModel::archiveAndRecreateGroup,
        onArchiveAndRecreateMedicationGroupResultConsumed =
            viewModel::consumeArchiveAndRecreateMedicationGroupResult,
        onDeleteClick = viewModel::showDeleteConfirmation,
        onDeleteDismiss = viewModel::dismissDeleteConfirmation,
        onDeleteConfirm = viewModel::deleteGroup,
        onDeleteWithRecordsConfirm = viewModel::deleteGroupAndRelatedEntries,
        occurrenceReferenceTime = currentMinute,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    onRemoveMedication: (String) -> Unit,
    onDismissMedicationEditor: () -> Unit,
    onConsumeMedicationEditorSaved: () -> Unit,
    onMedicationDraftChange: ((MedicationDraftUiState) -> MedicationDraftUiState) -> Unit,
    onEditingMedicationCountTextChange: (String) -> Unit,
    onDecreaseEditingMedicationCount: () -> Unit,
    onIncreaseEditingMedicationCount: () -> Unit,
    onSaveMedicationClick: () -> Unit,
    onSaveClick: () -> Unit,
    onSaveMedicationGroupResultConsumed: () -> Unit,
    onDeleteRelatedEntriesClick: () -> Unit,
    onDeleteRelatedEntriesDismiss: () -> Unit,
    onDeleteRelatedEntriesConfirm: () -> Unit,
    onDeleteRelatedEntriesResultConsumed: () -> Unit,
    onDeleteMedicationGroupResultConsumed: () -> Unit,
    onArchiveClick: () -> Unit,
    onArchiveDismiss: () -> Unit,
    onArchiveConfirm: () -> Unit,
    onArchiveMedicationGroupResultConsumed: () -> Unit,
    onArchiveAndRecreateClick: () -> Unit,
    onArchiveAndRecreateDismiss: () -> Unit,
    onArchiveAndRecreateConfirm: () -> Unit,
    onArchiveAndRecreateMedicationGroupResultConsumed: () -> Unit,
    onDeleteClick: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteWithRecordsConfirm: () -> Unit,
    occurrenceReferenceTime: LocalDateTime? = null,
    modifier: Modifier = Modifier
) {
    val appLocale = rememberAppLocale()
    val context = LocalContext.current
    val duplicateDailyTimeMessage =
        stringResource(R.string.group_schedule_duplicate_time)
    val medicationEditorInfoMessage = uiState.medicationEditorInfoMessageRes?.let { messageRes ->
        stringResource(messageRes)
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val scope = rememberCoroutineScope()
    val resolvedOccurrenceReferenceTime = occurrenceReferenceTime
        ?: LocalDateTime.now().withSecond(0).withNano(0)
    val currentDate = resolvedOccurrenceReferenceTime.toLocalDate()
    val timeFormatter = remember(appLocale) {
        localizedShortTimeFormatter(appLocale)
    }
    val dateFormatter = remember(appLocale, currentDate) {
        medicationGroupScheduleDateFormatter(appLocale, currentDate)
    }
    val notificationSupportState = resolveNotificationSupportState(
        hasNotificationAccess = hasNotificationAccess,
        remindersEnabled = uiState.remindersEnabled,
        showInexactReminderWarning = showInexactReminderWarning
    )
    val is24Hour = context.uses24HourTimeFormat()
    val deleteRelatedEntriesSuccessMessage =
        stringResource(R.string.delete_group_related_records_success)
    val deleteRelatedEntriesFailureMessage =
        stringResource(R.string.delete_group_related_records_failure)
    val saveMedicationGroupFailureMessage =
        stringResource(R.string.save_medication_group_failure)
    val archiveMedicationGroupFailureMessage =
        stringResource(R.string.archive_medication_group_failure)
    val archiveAndRecreateMedicationGroupFailureMessage =
        stringResource(R.string.archive_and_recreate_medication_group_failure)
    val deleteMedicationGroupFailureMessage =
        stringResource(R.string.delete_medication_group_failure)
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
        !uiState.isLoadingGroupForEditing &&
        !uiState.isSaving &&
        !uiState.isDeleting &&
        !uiState.isArchiving &&
        !uiState.isRecreatingAfterArchive &&
        !uiState.isDeletingRelatedEntries &&
        !uiState.isArchived &&
        !uiState.scheduleTimeOrderError
    val dangerZoneActionEnabled = !uiState.isLoadingGroupForEditing &&
        !uiState.isSaving &&
        !uiState.isDeleting &&
        !uiState.isArchiving &&
        !uiState.isRecreatingAfterArchive &&
        !uiState.isDeletingRelatedEntries &&
        !uiState.isArchived
    val upcomingOccurrences = remember(
        uiState.scheduleType,
        uiState.sinceDate,
        uiState.weeklyIntervalWeeks,
        uiState.weeklyDaysOfWeek,
        uiState.weeklyTime,
        uiState.dailyIntervalDays,
        uiState.dailyTimes,
        resolvedOccurrenceReferenceTime
    ) {
        buildMedicationGroupEditorUpcomingOccurrences(
            uiState = uiState,
            start = resolvedOccurrenceReferenceTime
        )
    }

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

    LaunchedEffect(uiState.deleteRelatedEntriesResult) {
        when (uiState.deleteRelatedEntriesResult) {
            DeleteRelatedEntriesResult.SUCCESS -> {
                Toast.makeText(
                    context,
                    deleteRelatedEntriesSuccessMessage,
                    Toast.LENGTH_SHORT,
                ).show()
                onDeleteRelatedEntriesResultConsumed()
            }

            DeleteRelatedEntriesResult.FAILURE -> {
                Toast.makeText(
                    context,
                    deleteRelatedEntriesFailureMessage,
                    Toast.LENGTH_SHORT,
                ).show()
                onDeleteRelatedEntriesResultConsumed()
            }

            null -> Unit
        }
    }

    LaunchedEffect(uiState.saveMedicationGroupResult) {
        when (uiState.saveMedicationGroupResult) {
            SaveMedicationGroupResult.FAILURE -> {
                Toast.makeText(
                    context,
                    saveMedicationGroupFailureMessage,
                    Toast.LENGTH_SHORT,
                ).show()
                onSaveMedicationGroupResultConsumed()
            }

            null -> Unit
        }
    }

    LaunchedEffect(uiState.deleteMedicationGroupResult) {
        when (uiState.deleteMedicationGroupResult) {
            DeleteMedicationGroupResult.FAILURE -> {
                Toast.makeText(
                    context,
                    deleteMedicationGroupFailureMessage,
                    Toast.LENGTH_SHORT,
                ).show()
                onDeleteMedicationGroupResultConsumed()
            }

            null -> Unit
        }
    }

    LaunchedEffect(uiState.archiveMedicationGroupResult) {
        when (uiState.archiveMedicationGroupResult) {
            ArchiveMedicationGroupResult.FAILURE -> {
                Toast.makeText(
                    context,
                    archiveMedicationGroupFailureMessage,
                    Toast.LENGTH_SHORT,
                ).show()
                onArchiveMedicationGroupResultConsumed()
            }

            null -> Unit
        }
    }

    LaunchedEffect(uiState.archiveAndRecreateMedicationGroupResult) {
        when (uiState.archiveAndRecreateMedicationGroupResult) {
            ArchiveAndRecreateMedicationGroupResult.FAILURE -> {
                Toast.makeText(
                    context,
                    archiveAndRecreateMedicationGroupFailureMessage,
                    Toast.LENGTH_SHORT,
                ).show()
                onArchiveAndRecreateMedicationGroupResultConsumed()
            }

            null -> Unit
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
        val hasRelatedEntries = uiState.relatedEntryCount > 0
        val deleteGroupConfirmationText = if (hasRelatedEntries) {
            pluralStringResource(
                R.plurals.delete_medication_group_confirmation_with_related_records,
                uiState.relatedEntryCount,
                uiState.relatedEntryCount,
            )
        } else {
            stringResource(R.string.delete_medication_group_confirmation)
        }
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isDeleting) {
                    onDeleteDismiss()
                }
            },
            title = { Text(text = stringResource(R.string.delete_medication_group_title)) },
            text = { Text(text = deleteGroupConfirmationText) },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (hasRelatedEntries) {
                        TextButton(
                            enabled = !uiState.isDeleting,
                            onClick = onDeleteWithRecordsConfirm,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                        ) {
                            Text(text = stringResource(R.string.delete_group_related_records))
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = !uiState.isDeleting,
                        onClick = onDeleteDismiss,
                    ) {
                        Text(text = stringResource(R.string.cancel))
                    }
                    TextButton(
                        enabled = !uiState.isDeleting,
                        onClick = onDeleteConfirm,
                    ) {
                        Text(
                            text = stringResource(
                                if (hasRelatedEntries) {
                                    R.string.delete_medication_group_keep_records
                                } else {
                                    R.string.delete_entries_confirm
                                }
                            )
                        )
                    }
                }
            }
        )
    }

    if (uiState.isArchiveConfirmationVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isArchiving) {
                    onArchiveDismiss()
                }
            },
            title = { Text(text = stringResource(R.string.archive_medication_group_title)) },
            text = { Text(text = stringResource(R.string.archive_medication_group_confirmation)) },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isArchiving,
                    onClick = onArchiveConfirm,
                ) {
                    Text(text = stringResource(R.string.archive))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isArchiving,
                    onClick = onArchiveDismiss,
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (uiState.isArchiveAndRecreateConfirmationVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isRecreatingAfterArchive) {
                    onArchiveAndRecreateDismiss()
                }
            },
            title = { Text(text = stringResource(R.string.archive_and_recreate_medication_group_title)) },
            text = {
                Text(text = stringResource(R.string.archive_and_recreate_medication_group_confirmation))
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isRecreatingAfterArchive,
                    onClick = onArchiveAndRecreateConfirm,
                ) {
                    Text(text = stringResource(R.string.archive_and_recreate))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isRecreatingAfterArchive,
                    onClick = onArchiveAndRecreateDismiss,
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (uiState.isDeleteRelatedEntriesConfirmationVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isDeletingRelatedEntries) {
                    onDeleteRelatedEntriesDismiss()
                }
            },
            title = {
                Text(text = stringResource(R.string.delete_group_related_records_title))
            },
            text = {
                Text(
                    text = pluralStringResource(
                        R.plurals.delete_group_related_records_confirmation,
                        uiState.relatedEntryCount,
                        uiState.relatedEntryCount,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isDeletingRelatedEntries,
                    onClick = onDeleteRelatedEntriesConfirm,
                ) {
                    Text(text = stringResource(R.string.delete_entries_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isDeletingRelatedEntries,
                    onClick = onDeleteRelatedEntriesDismiss,
                ) {
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
                        onRemoveMedication(removalRequest.localId)
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

    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        lazyListState = listState,
        state = topAppBarState
    )
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior) {
                    listState.animateScrollToItem(0)
                },
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
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                },
                actions = {
                    HrtButton(
                        text = stringResource(R.string.save),
                        onClick = onSaveClick,
                        enabled = canSave,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        if (uiState.isLoadingGroupForEditing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(dimensionResource(R.dimen.padding_medium)),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isLocked) {
                item {
                    SupportMessageListItem(
                        text = stringResource(R.string.group_locked_banner),
                        icon = Icons.Rounded.ErrorOutline,
                        leadingIconTint = MaterialTheme.colorScheme.tertiary,
                        leadingIconSize = 24.dp,
                    )
                }
            }

            item {
                val focusManager = LocalFocusManager.current
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.groupName,
                        onValueChange = onGroupNameChange,
                        label = { Text(text = stringResource(R.string.field_medication_group_name)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Label,
                                contentDescription = null
                            )
                        },
                        trailingIcon = if (!uiState.isArchived && shouldShowGroupNameClearAction(uiState.groupName)) {
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
                        enabled = !uiState.isArchived,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        )
                    )
                }
            }

            item {
                Column {
                    EditorSectionHeader(
                        title = stringResource(R.string.group_medications_title)
                    )
                    if (uiState.medications.isEmpty()) {
                        SupportMessageListItem(
                            text = stringResource(R.string.group_medications_empty),
                            painter = painterResource(R.drawable.ic_info),
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))
                        ) {
                            uiState.medications.forEachIndexed { index, medication ->
                                val medicationName = medicationDisplayName(medication.details)
                                val medicationEditable = !uiState.areMedicationsLocked
                                MedicationCard(
                                    details = medication.details,
                                    medicationCount = medication.count,
                                    groupColorKey = uiState.groupColorKey,
                                    onClick = {
                                        if (medicationEditable) {
                                            onMedicationClick(medication.localId)
                                        }
                                    },
                                    onDeleteClick = if (medicationEditable) {
                                        {
                                            pendingMedicationRemoval = MedicationRemovalRequest(
                                                localId = medication.localId,
                                                medicationName = medicationName
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    enabled = medicationEditable,
                                    index = index,
                                    itemCount = uiState.medications.size,
                                )
                            }
                        }
                    }
                    if (!uiState.areMedicationsLocked) {
                        HrtFilledTonalButton(
                            text = stringResource(R.string.add),
                            onClick = onAddMedication,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            icon = Icons.Rounded.Add,
                            iconModifier = Modifier.size(
                                ButtonDefaults.iconSizeFor(ButtonDefaults.MinHeight)
                            ),
                            iconSpacing = ButtonDefaults.iconSpacingFor(ButtonDefaults.MinHeight),
                            compact = true,
                            contentPadding = ButtonDefaults.contentPaddingFor(
                                ButtonDefaults.MinHeight,
                                hasStartIcon = true
                            ),
                        )
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
                            enabled = !uiState.areScheduleShapeFieldsLocked,
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
                            previewOccurrences = upcomingOccurrences,
                            appLocale = appLocale,
                            dateFormatter = dateFormatter,
                            timeFormatter = timeFormatter,
                            onSinceDateChange = { currentDate -> pendingSinceDate = currentDate },
                            onIntervalChange = onWeeklyIntervalChange,
                            onDayChange = onWeeklyDayChange,
                            onTimeChange = { currentTime -> pendingWeeklyTime = currentTime },
                            sinceEnabled = !uiState.areScheduleShapeFieldsLocked,
                            intervalEnabled = !uiState.areScheduleShapeFieldsLocked,
                            daySelectionEnabled = !uiState.areScheduleShapeFieldsLocked,
                            timeEditEnabled = !uiState.isArchived,
                        )
                    } else {
                        DailyScheduleEditor(
                            sinceDate = uiState.sinceDate,
                            intervalDays = uiState.dailyIntervalDays,
                            dailyTimes = uiState.dailyTimes,
                            previewOccurrences = upcomingOccurrences,
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
                            onRemoveTime = onRemoveDailyTime,
                            sinceEnabled = !uiState.areScheduleShapeFieldsLocked,
                            intervalEnabled = !uiState.areScheduleShapeFieldsLocked,
                            addRemoveTimeEnabled = !uiState.areScheduleShapeFieldsLocked,
                            timeEditEnabled = !uiState.isArchived,
                        )
                    }
                    if (uiState.isLocked) {
                        SupportMessageListItem(
                            text = stringResource(
                                if (uiState.scheduleTimeOrderError) {
                                    R.string.group_locked_slot_order_error
                                } else {
                                    R.string.group_locked_time_note
                                }
                            ),
                            icon = Icons.Rounded.ErrorOutline,
                            leadingIconTint = if (uiState.scheduleTimeOrderError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                            leadingIconSize = 24.dp,
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
                        count = if (notificationSupportState == NotificationSupportState.NONE) 1 else 2,
                    )
                    when (notificationSupportState) {
                        NotificationSupportState.ACCESS_OFF -> {
                            SupportMessageListItem(
                                text = stringResource(R.string.settings_reminders_permission_off_summary),
                                icon = Icons.Rounded.ErrorOutline,
                                leadingIconTint = MaterialTheme.colorScheme.tertiary,
                                leadingIconSize = 24.dp,
                                onClick = onRecoverMasterReminders,
                                showChevron = true,
                                index = 1,
                                count = 2,
                            )
                        }

                        NotificationSupportState.MASTER_OFF -> {
                            SupportMessageListItem(
                                text = stringResource(R.string.group_notifications_master_disabled),
                                icon = Icons.Rounded.ErrorOutline,
                                leadingIconTint = MaterialTheme.colorScheme.tertiary,
                                leadingIconSize = 24.dp,
                                onClick = { isMasterReminderRecoveryDialogVisible = true },
                                showChevron = true,
                                index = 1,
                                count = 2,
                            )
                        }

                        NotificationSupportState.INEXACT -> {
                            SupportMessageListItem(
                                text = stringResource(R.string.group_notifications_inexact_warning),
                                icon = Icons.Rounded.ErrorOutline,
                                leadingIconTint = MaterialTheme.colorScheme.tertiary,
                                leadingIconSize = 24.dp,
                                onClick = onRequestExactAlarmAccess,
                                showChevron = true,
                                index = 1,
                                count = 2,
                            )
                        }

                        NotificationSupportState.NONE -> Unit
                    }
                }
            }

            if (uiState.isEditing && !uiState.isArchived) {
                item {
                    EditorSectionHeader(title = stringResource(R.string.group_danger_zone_title))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(
                            dimensionResource(R.dimen.list_segment_gap)
                        )
                    ) {
                        ArchiveMedicationGroupCard(
                            enabled = dangerZoneActionEnabled,
                            onClick = onArchiveClick,
                            index = 0,
                            count = 4,
                        )
                        ArchiveAndRecreateMedicationGroupCard(
                            enabled = dangerZoneActionEnabled,
                            onClick = onArchiveAndRecreateClick,
                            index = 1,
                            count = 4,
                        )
                        DeleteMedicationGroupRecordsCard(
                            enabled = dangerZoneActionEnabled && uiState.relatedEntryCount > 0,
                            onClick = onDeleteRelatedEntriesClick,
                            index = 2,
                            count = 4,
                        )
                        DeleteMedicationGroupCard(
                            enabled = dangerZoneActionEnabled,
                            onClick = onDeleteClick,
                            index = 3,
                            count = 4,
                        )
                    }
                }
            }
        }
    }

    uiState.editingMedication?.let { medication ->
        MedicationDefinitionEditorSheet(
            modifier = Modifier,
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
            onMedicationKeyChange = { medicationKey ->
                onMedicationDraftChange { draft -> draft.changeMedicationKey(medicationKey) }
            },
            onCustomMedicationNameChange = { medicationName ->
                onMedicationDraftChange { draft -> draft.copy(customMedicationName = medicationName) }
            },
            onDoseKindChange = { doseKind ->
                onMedicationDraftChange { draft -> draft.changeDoseKind(doseKind) }
            },
            onCustomDoseUnitChange = { customDoseUnit ->
                onMedicationDraftChange { draft -> draft.changeCustomDoseUnit(customDoseUnit) }
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
            countText = medication.countText,
            onCountTextChange = onEditingMedicationCountTextChange,
            onDecreaseCountClick = onDecreaseEditingMedicationCount,
            onIncreaseCountClick = onIncreaseEditingMedicationCount,
            errorMessageRes = uiState.medicationEditorErrorMessageRes,
            onConfirm = onSaveMedicationClick
        )
    }
}

internal fun buildMedicationGroupEditorUpcomingOccurrences(
    uiState: MedicationGroupEditorUiState,
    start: LocalDateTime
): List<LocalDateTime> {
    val schedule = MedicationGroupSchedule(
        type = uiState.scheduleType,
        interval = when (uiState.scheduleType) {
            MedicationGroupScheduleType.DAILY -> parseScheduleInterval(uiState.dailyIntervalDays)
            MedicationGroupScheduleType.WEEKLY -> parseScheduleInterval(uiState.weeklyIntervalWeeks)
        },
        since = uiState.sinceDate,
        weeklyDaysOfWeek = if (uiState.scheduleType == MedicationGroupScheduleType.WEEKLY) {
            uiState.weeklyDaysOfWeek
        } else {
            emptySet()
        },
        times = if (uiState.scheduleType == MedicationGroupScheduleType.WEEKLY) {
            listOf(uiState.weeklyTime)
        } else {
            uiState.dailyTimes.map(MedicationGroupScheduleTimeUiState::time)
        }
    )
    return schedule.nextOccurrencesFrom(
        start = start,
        limit = medicationGroupEditorUpcomingOccurrenceLimit(uiState)
    )
}

internal fun medicationGroupEditorUpcomingOccurrenceLimit(
    uiState: MedicationGroupEditorUiState
): Int {
    return when (uiState.scheduleType) {
        MedicationGroupScheduleType.DAILY -> uiState.dailyTimes.size + 1
        MedicationGroupScheduleType.WEEKLY -> uiState.weeklyDaysOfWeek.size + 1
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(4.dp)
        )
        trailing?.invoke()
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
            onRemoveMedication = { },
            onDismissMedicationEditor = { },
            onConsumeMedicationEditorSaved = { },
            onMedicationDraftChange = { },
            onEditingMedicationCountTextChange = { },
            onDecreaseEditingMedicationCount = { },
            onIncreaseEditingMedicationCount = { },
            onSaveMedicationClick = { },
            onSaveClick = { },
            onSaveMedicationGroupResultConsumed = { },
            onDeleteRelatedEntriesClick = { },
            onDeleteRelatedEntriesDismiss = { },
            onDeleteRelatedEntriesConfirm = { },
            onDeleteRelatedEntriesResultConsumed = { },
            onDeleteMedicationGroupResultConsumed = { },
            onArchiveClick = { },
            onArchiveDismiss = { },
            onArchiveConfirm = { },
            onArchiveMedicationGroupResultConsumed = { },
            onArchiveAndRecreateClick = { },
            onArchiveAndRecreateDismiss = { },
            onArchiveAndRecreateConfirm = { },
            onArchiveAndRecreateMedicationGroupResultConsumed = { },
            onDeleteClick = { },
            onDeleteDismiss = { },
            onDeleteConfirm = { },
            onDeleteWithRecordsConfirm = { },
            occurrenceReferenceTime = LocalDateTime.of(2026, 4, 25, 10, 0)
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
            onRemoveMedication = { },
            onDismissMedicationEditor = { },
            onConsumeMedicationEditorSaved = { },
            onMedicationDraftChange = { },
            onEditingMedicationCountTextChange = { },
            onDecreaseEditingMedicationCount = { },
            onIncreaseEditingMedicationCount = { },
            onSaveMedicationClick = { },
            onSaveClick = { },
            onSaveMedicationGroupResultConsumed = { },
            onDeleteRelatedEntriesClick = { },
            onDeleteRelatedEntriesDismiss = { },
            onDeleteRelatedEntriesConfirm = { },
            onDeleteRelatedEntriesResultConsumed = { },
            onDeleteMedicationGroupResultConsumed = { },
            onArchiveClick = { },
            onArchiveDismiss = { },
            onArchiveConfirm = { },
            onArchiveMedicationGroupResultConsumed = { },
            onArchiveAndRecreateClick = { },
            onArchiveAndRecreateDismiss = { },
            onArchiveAndRecreateConfirm = { },
            onArchiveAndRecreateMedicationGroupResultConsumed = { },
            onDeleteClick = { },
            onDeleteDismiss = { },
            onDeleteConfirm = { },
            onDeleteWithRecordsConfirm = { },
            occurrenceReferenceTime = LocalDateTime.of(2026, 4, 25, 10, 0)
        )
    }
}

private fun buildMedicationGroupEditorPreviewUiState(
    scheduleType: MedicationGroupScheduleType,
    editingGroupId: String?,
    remindersEnabled: Boolean,
    notificationsEnabled: Boolean
): MedicationGroupEditorUiState {
    val today = LocalDate.of(2026, 4, 25)
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
