package com.mkx.hrttracker.ui.plan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
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
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.nextOccurrencesFrom
import com.mkx.hrttracker.reminder.canScheduleExactAlarms
import com.mkx.hrttracker.reminder.rememberReminderCapabilityReconciler
import com.mkx.hrttracker.reminder.shouldShowNotificationPermissionRecoveryToast
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.ConnectedButtonGroup
import com.mkx.hrttracker.ui.components.ConnectedButtonGroupLayout
import com.mkx.hrttracker.ui.components.DatePickerModal
import com.mkx.hrttracker.ui.components.ExactAlarmAccessDialog
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.MedicationCard
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.TimePickerModal
import com.mkx.hrttracker.ui.components.appContentPaddingValues
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.dismissInputAndRun
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.medication.DoseInstructionDraftUiState
import com.mkx.hrttracker.ui.medication.MedicationGroupSlotEditorSheet
import com.mkx.hrttracker.ui.medication.MedicinePickerUiState
import com.mkx.hrttracker.ui.medication.medicationEntryTitle
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.medicationGroupScheduleDateFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import com.mkx.hrttracker.util.rememberUses24HourTimeFormat
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@Composable
fun MedicationGroupEditorScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    onGroupSaved: () -> Unit,
    onGroupSavedToPlan: () -> Unit = onGroupSaved,
    openedFromArchivedGroupsPage: Boolean = false,
    drawBehindNavigationBar: Boolean = false,
    viewModel: MedicationGroupEditorViewModel = hiltViewModel(),
    onOpenMedicinePicker: (String) -> Unit = { _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentMinute by viewModel.currentMinute.collectAsStateWithLifecycle()
    val latestUiState by rememberUpdatedState(uiState)
    val context = LocalContext.current
    val activity = LocalActivity.current
    val reminderCapabilityReconciler = rememberReminderCapabilityReconciler()
    val reminderCapabilityState by reminderCapabilityReconciler.state.collectAsStateWithLifecycle()
    val hasNotificationAccess = reminderCapabilityState.hasNotificationAccess
    val hasExactAlarmAccess = reminderCapabilityState.hasExactAlarmAccess
    val reminderNotificationsUnavailableMessage =
        stringResource(R.string.settings_reminders_notifications_unavailable)
    var isExactAlarmDialogVisible by rememberSaveable { mutableStateOf(false) }
    var showInexactReminderWarning by rememberSaveable { mutableStateOf(false) }
    var pendingNotificationEnableRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var hasRequestedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    val startedAsNewGroupCreationFlow = remember { !uiState.isEditing }
    val isNewGroupCreationFlow = resolveMedicationGroupEditorIsNewGroupCreationFlow(
        uiState = uiState,
        startedAsNewGroupCreationFlow = startedAsNewGroupCreationFlow,
        openedFromArchivedGroupsPage = openedFromArchivedGroupsPage,
    )
    val applicationContext = context.applicationContext
    val onCreatePastScheduledSlotRecordsCompleted =
        remember(applicationContext) {
            { saveState: CreatePastScheduledSlotRecordsSaveState ->
                showCreatePastScheduledSlotRecordsToast(
                    context = applicationContext,
                    saveState = saveState,
                )
            }
        }
    val exactAlarmAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        reminderCapabilityReconciler.requestReconcile("medication_group_editor_exact_alarm_result")
        isExactAlarmDialogVisible = false
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        reminderCapabilityReconciler.requestReconcile("medication_group_editor_notification_permission_result")
        if (isGranted) {
            if (shouldEnableMasterReminders(pendingNotificationEnableRequest)) {
                viewModel.setMasterRemindersEnabled(true)
            }
            if (shouldEnableGroupNotifications(pendingNotificationEnableRequest)) {
                viewModel.updateNotificationsEnabled(true)
                val exactAlarmUiState = resolveExactAlarmUiStateAfterEnablingReminders(
                    canScheduleExactAlarms = hasExactAlarmAccess
                )
                showInexactReminderWarning = exactAlarmUiState.showInexactReminderWarning
                isExactAlarmDialogVisible = exactAlarmUiState.showExactAlarmDialog
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
            val exactAlarmUiState = resolveExactAlarmUiStateAfterEnablingReminders(
                canScheduleExactAlarms = hasExactAlarmAccess
            )
            showInexactReminderWarning = exactAlarmUiState.showInexactReminderWarning
            isExactAlarmDialogVisible = exactAlarmUiState.showExactAlarmDialog
        }
    }
    fun navigateAfterSave(target: MedicationGroupEditorSaveNavigationTarget) {
        when (target) {
            MedicationGroupEditorSaveNavigationTarget.BACK -> onGroupSaved()
            MedicationGroupEditorSaveNavigationTarget.PLAN -> onGroupSavedToPlan()
        }
    }

    LaunchedEffect(viewModel, openedFromArchivedGroupsPage) {
        viewModel.events.collect { event ->
            when (event) {
                MedicationGroupEditorEvent.SaveCompleted -> {
                    val target = resolveMedicationGroupEditorSaveNavigationTarget(
                        uiState = latestUiState,
                        openedFromArchivedGroupsPage = openedFromArchivedGroupsPage,
                    )
                    navigateAfterSave(target)
                }

                MedicationGroupEditorEvent.DeleteOrArchiveCompleted -> onGroupSaved()
            }
        }
    }

    LaunchedEffect(hasNotificationAccess) {
        if (!hasNotificationAccess) {
            pendingNotificationEnableRequest = null
            isExactAlarmDialogVisible = false
            showInexactReminderWarning = false
        }
    }

    LaunchedEffect(hasNotificationAccess, hasExactAlarmAccess, isExactAlarmDialogVisible) {
        if (!isExactAlarmDialogVisible) {
            showInexactReminderWarning = resolveInexactReminderWarningVisibility(
                hasNotificationAccess = hasNotificationAccess,
                canScheduleExactAlarms = hasExactAlarmAccess
            )
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
        onGroupColorChange = viewModel::updateGroupColor,
        onScheduleTypeChange = viewModel::updateScheduleType,
        onSinceDateChange = viewModel::updateSinceDate,
        onIncludePastScheduledSlotsChange = viewModel::updateIncludePastScheduledSlots,
        onCreatePastScheduledSlotRecordsChange = viewModel::updateCreatePastScheduledSlotRecords,
        onNotificationsEnabledChange = { enabled ->
            if (!enabled) {
                viewModel.updateNotificationsEnabled(false)
                pendingNotificationEnableRequest = null
                isExactAlarmDialogVisible = false
                showInexactReminderWarning = resolveInexactReminderWarningVisibility(
                    hasNotificationAccess = hasNotificationAccess,
                    canScheduleExactAlarms = hasExactAlarmAccess
                )
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
                val exactAlarmUiState = resolveExactAlarmUiStateAfterEnablingReminders(
                    canScheduleExactAlarms = hasExactAlarmAccess
                )
                showInexactReminderWarning = exactAlarmUiState.showInexactReminderWarning
                isExactAlarmDialogVisible = exactAlarmUiState.showExactAlarmDialog
            }
        },
        onRequestExactAlarmAccess = {
            isExactAlarmDialogVisible = true
        },
        onRecoverMasterReminders = enableMasterReminders,
        hasNotificationAccess = hasNotificationAccess,
        notificationsToggleEnabled = uiState.remindersEnabled && hasNotificationAccess,
        showInexactReminderWarning = showInexactReminderWarning,
        onWeeklyIntervalChange = viewModel::updateWeeklyIntervalWeeks,
        onWeeklyDayChange = viewModel::toggleWeeklyDayOfWeek,
        onWeeklyResetDaysOfWeek = viewModel::resetWeeklyDaysOfWeekToDefault,
        onWeeklyTimeChange = viewModel::updateWeeklyTime,
        onDailyIntervalChange = viewModel::updateDailyIntervalDays,
        onAddDailyTime = viewModel::addDailyTime,
        onDailyTimeChange = viewModel::updateDailyTime,
        onRemoveDailyTime = viewModel::removeDailyTime,
        // "+ Add medication" navigates straight to the medicine picker; the host
        // generates a fresh slot localId per tap so the result wiring is unique.
        // The editor sheet is opened pre-filled when the picker returns, via
        // beginAddMedicationWithMedicine(localId, uuid) — see HrtTrackerNavHost.
        onAddMedication = { onOpenMedicinePicker(UUID.randomUUID().toString()) },
        onMedicationClick = viewModel::showMedicationEditor,
        onRemoveMedication = viewModel::removeMedication,
        onDismissMedicationEditor = viewModel::dismissMedicationEditor,
        onConsumeMedicationEditorSaved = viewModel::consumeMedicationEditorSaved,
        onConsumeMedicationEditorInfoMessage = viewModel::consumeMedicationEditorInfoMessage,
        onMedicineDraftChange = viewModel::updateEditingMedicineDraft,
        onDoseInstructionDraftChange = viewModel::updateEditingDoseInstructionDraft,
        onOpenMedicinePicker = onOpenMedicinePicker,
        onEditingMedicationCountTextChange = viewModel::updateEditingMedicationCountText,
        onDecreaseEditingMedicationCount = viewModel::decreaseEditingMedicationCount,
        onIncreaseEditingMedicationCount = viewModel::increaseEditingMedicationCount,
        onSaveMedicationClick = viewModel::saveEditingMedication,
        onSaveClick = {
            viewModel.saveGroup(
                onCreatePastScheduledSlotRecordsCompleted =
                    onCreatePastScheduledSlotRecordsCompleted,
            )
        },
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
        onDuplicateArchivedGroupClick = viewModel::duplicateArchivedGroup,
        onDuplicateArchivedGroupResultConsumed =
            viewModel::consumeDuplicateArchivedGroupResult,
        onArchiveAndRecreateConfirm = viewModel::archiveAndRecreateGroup,
        onArchiveAndRecreateMedicationGroupResultConsumed =
            viewModel::consumeArchiveAndRecreateMedicationGroupResult,
        onDeleteClick = viewModel::showDeleteConfirmation,
        onDeleteDismiss = viewModel::dismissDeleteConfirmation,
        onDeleteConfirm = viewModel::deleteGroup,
        onDeleteWithRecordsConfirm = viewModel::deleteGroupAndRelatedEntries,
        occurrenceReferenceTime = currentMinute,
        isNewGroupCreationFlow = isNewGroupCreationFlow,
        openedFromArchivedGroupsPage = openedFromArchivedGroupsPage,
        drawBehindNavigationBar = drawBehindNavigationBar,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MedicationGroupEditorScreenContent(
    modifier: Modifier = Modifier,
    uiState: MedicationGroupEditorUiState,
    onNavigateBack: () -> Unit,
    onGroupNameChange: (String) -> Unit,
    onGroupColorChange: (MedicationGroupColorKey) -> Unit,
    onScheduleTypeChange: (MedicationGroupScheduleType) -> Unit,
    onSinceDateChange: (LocalDate) -> Unit,
    onIncludePastScheduledSlotsChange: (Boolean) -> Unit,
    onCreatePastScheduledSlotRecordsChange: (Boolean) -> Unit,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onRecoverMasterReminders: () -> Unit,
    hasNotificationAccess: Boolean,
    notificationsToggleEnabled: Boolean,
    showInexactReminderWarning: Boolean,
    onWeeklyIntervalChange: (String) -> Unit,
    onWeeklyDayChange: (DayOfWeek) -> Unit,
    onWeeklyResetDaysOfWeek: () -> Unit,
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
    onConsumeMedicationEditorInfoMessage: () -> Unit,
    onMedicineDraftChange: ((MedicinePickerUiState) -> MedicinePickerUiState) -> Unit,
    onDoseInstructionDraftChange: ((DoseInstructionDraftUiState) -> DoseInstructionDraftUiState) -> Unit,
    onOpenMedicinePicker: (String) -> Unit,
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
    onArchiveConfirm: (LocalDate?) -> Unit,
    onArchiveMedicationGroupResultConsumed: () -> Unit,
    onDuplicateArchivedGroupClick: () -> Unit,
    onDuplicateArchivedGroupResultConsumed: () -> Unit,
    onArchiveAndRecreateConfirm: (LocalDate?) -> Unit,
    onArchiveAndRecreateMedicationGroupResultConsumed: () -> Unit,
    onDeleteClick: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteWithRecordsConfirm: () -> Unit,
    occurrenceReferenceTime: LocalDateTime? = null,
    isNewGroupCreationFlow: Boolean = !uiState.isEditing,
    openedFromArchivedGroupsPage: Boolean = false,
    drawBehindNavigationBar: Boolean = false,
) {
    val appLocale = rememberAppLocale()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
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
    val timeFormatter = rememberLocalizedShortTimeFormatter(appLocale)
    val dateFormatter = remember(appLocale, currentDate) {
        medicationGroupScheduleDateFormatter(appLocale, currentDate)
    }
    // Archive end-date follows the plain date convention (year only when it
    // differs from this year, no weekday), unlike schedule rows which append
    // the weekday via medicationGroupScheduleDateFormatter.
    val archiveDateFormatter = remember(appLocale, currentDate) {
        dateLabelFormatter(appLocale, currentDate)
    }
    val notificationSupportState = resolveNotificationSupportState(
        hasNotificationAccess = hasNotificationAccess,
        remindersEnabled = uiState.remindersEnabled,
        showInexactReminderWarning = showInexactReminderWarning
    )
    val is24Hour = rememberUses24HourTimeFormat()
    val deleteRelatedEntriesSuccessMessage =
        stringResource(R.string.delete_group_related_records_success)
    val deleteRelatedEntriesFailureMessage =
        stringResource(R.string.delete_group_related_records_failure)
    val deleteRelatedEntriesUnavailableMessage =
        stringResource(R.string.delete_group_related_records_disabled)
    val saveMedicationGroupFailureMessage =
        stringResource(R.string.save_medication_group_failure)
    val archiveMedicationGroupFailureMessage =
        stringResource(R.string.archive_medication_group_failure)
    val archiveMedicationGroupBlockedByPlannedSlotsMessage =
        stringResource(R.string.archive_medication_group_blocked_by_planned_slots)
    val archiveAndRecreateMedicationGroupFailureMessage =
        stringResource(R.string.archive_and_recreate_medication_group_failure)
    val archiveAndRecreateMedicationGroupSuccessMessage =
        stringResource(R.string.archive_and_recreate_medication_group_success)
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
    // Form-validity gates only. Transient post-save / post-delete states
    // keep the save button visually normal; the click handler no-ops via
    // shouldDisableMedicationGroupEditorSaveAction below.
    val canSave = hasSaveableMedicationGroupContent(uiState) &&
        !uiState.scheduleTimeOrderError &&
        !uiState.isArchived
    val shouldUseArchivedPresentation = shouldUseArchivedMedicationGroupEditorPresentation(
        uiState = uiState,
        openedFromArchivedGroupsPage = openedFromArchivedGroupsPage,
    )
    val presentedNotificationsToggleEnabled =
        notificationsToggleEnabled && !uiState.isArchived
    val shouldRenderAsEditing = shouldRenderMedicationGroupEditorAsEditing(
        uiState = uiState,
        isNewGroupCreationFlow = isNewGroupCreationFlow,
    )
    val shouldSuppressLockedStateDuringGeneratedHistorySave =
        shouldSuppressMedicationGroupEditorLockedStateDuringGeneratedHistorySave(
            uiState = uiState,
            isNewGroupCreationFlow = isNewGroupCreationFlow,
        )
    val shouldRenderLockedState =
        uiState.isLocked && !shouldSuppressLockedStateDuringGeneratedHistorySave
    val shouldShowLockedTimeChangeNote = shouldShowLockedScheduleTimeNote(uiState)
    val areFieldsRenderedLocked = shouldRenderLockedState || uiState.isArchived
    val openMedicationEditor = remember(
        focusManager,
        keyboardController,
        onMedicationClick,
    ) {
        { medicationLocalId: String ->
            dismissInputAndRun(
                focusManager = focusManager,
                keyboardController = keyboardController,
            ) {
                onMedicationClick(medicationLocalId)
            }
        }
    }
    val openAddMedicationEditor = remember(
        focusManager,
        keyboardController,
        onAddMedication,
    ) {
        {
            dismissInputAndRun(
                focusManager = focusManager,
                keyboardController = keyboardController,
            ) {
                onAddMedication()
            }
        }
    }
    val upcomingOccurrences = remember(
        uiState.isArchived,
        uiState.scheduleType,
        uiState.sinceDate,
        uiState.weeklyIntervalWeeks,
        uiState.weeklyDaysOfWeek,
        uiState.weeklyTime,
        uiState.dailyIntervalDays,
        uiState.dailyTimes,
        resolvedOccurrenceReferenceTime
    ) {
        if (uiState.isArchived) {
            emptyList()
        } else {
            buildMedicationGroupEditorUpcomingOccurrences(
                uiState = uiState,
                start = resolvedOccurrenceReferenceTime
            )
        }
    }

    LaunchedEffect(medicationEditorInfoMessage) {
        medicationEditorInfoMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onConsumeMedicationEditorInfoMessage()
        }
    }

    LaunchedEffect(uiState.isMedicationEditorSaved) {
        if (uiState.isMedicationEditorSaved) {
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

            ArchiveMedicationGroupResult.BLOCKED_BY_FUTURE_PLANNED_SLOTS -> {
                Toast.makeText(
                    context,
                    archiveMedicationGroupBlockedByPlannedSlotsMessage,
                    Toast.LENGTH_LONG,
                ).show()
                onArchiveMedicationGroupResultConsumed()
            }

            null -> Unit
        }
    }

    LaunchedEffect(uiState.archiveAndRecreateMedicationGroupResult) {
        when (uiState.archiveAndRecreateMedicationGroupResult) {
            ArchiveAndRecreateMedicationGroupResult.SUCCESS -> {
                Toast.makeText(
                    context,
                    archiveAndRecreateMedicationGroupSuccessMessage,
                    Toast.LENGTH_SHORT,
                ).show()
                onArchiveAndRecreateMedicationGroupResultConsumed()
            }

            ArchiveAndRecreateMedicationGroupResult.FAILURE -> {
                Toast.makeText(
                    context,
                    archiveAndRecreateMedicationGroupFailureMessage,
                    Toast.LENGTH_SHORT,
                ).show()
                onArchiveAndRecreateMedicationGroupResultConsumed()
            }

            ArchiveAndRecreateMedicationGroupResult.BLOCKED_BY_FUTURE_PLANNED_SLOTS -> {
                Toast.makeText(
                    context,
                    archiveMedicationGroupBlockedByPlannedSlotsMessage,
                    Toast.LENGTH_LONG,
                ).show()
                onArchiveAndRecreateMedicationGroupResultConsumed()
            }

            null -> Unit
        }
    }

    LaunchedEffect(uiState.duplicateArchivedGroupSkippedCount) {
        val skipped = uiState.duplicateArchivedGroupSkippedCount ?: return@LaunchedEffect
        Toast.makeText(
            context,
            context.resources.getQuantityString(
                R.plurals.duplicate_group_archived_medicines_skipped,
                skipped,
                skipped,
            ),
            Toast.LENGTH_SHORT,
        ).show()
        onDuplicateArchivedGroupResultConsumed()
    }

    pendingSinceDate?.let { initialSinceDate ->
        DatePickerModal(
            onDateSelected = onSinceDateChange,
            onDismiss = { pendingSinceDate = null },
            initialSelectedDate = initialSinceDate,
            minimumDate = if (uiState.recreatedFromGroupId != null) {
                maxOf(currentDate, uiState.originalSinceDate ?: currentDate)
            } else {
                null
            },
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
                val normalizedSelectedTime = selectedTime.withSecond(0).withNano(0)
                if (hasDuplicateDailyTime(
                        dailyTimes = uiState.dailyTimes,
                        time = normalizedSelectedTime,
                        excludingLocalId = dailyTimeEdit.localId
                    )
                ) {
                    Toast.makeText(context, duplicateDailyTimeMessage, Toast.LENGTH_SHORT).show()
                    false
                } else {
                    onDailyTimeChange(dailyTimeEdit.localId, normalizedSelectedTime)
                    true
                }
            },
            onDismiss = { pendingDailyTimeEdit = null },
            initialTime = dailyTimeEdit.initialTime,
            is24Hour = is24Hour,
            onRemove = if (
                !areFieldsRenderedLocked &&
                canRemoveDailyTime(uiState.dailyTimes.size)
            ) {
                { onRemoveDailyTime(dailyTimeEdit.localId) }
            } else {
                null
            },
        )
    }

    if (uiState.isDeleteConfirmationVisible) {
        var shouldDeleteRelatedEntriesWithGroup by remember { mutableStateOf(false) }
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
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))) {
                    Text(text = deleteGroupConfirmationText)
                    if (hasRelatedEntries) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HrtSection(title = null) {
                            item {
                                PreferenceSegmentedListItem(
                                    title = stringResource(R.string.delete_group_also_related_records),
                                    titleTextStyle = MaterialTheme.typography.labelLarge,
                                    onClick = {
                                        if (uiState.isDeleting) return@PreferenceSegmentedListItem
                                        shouldDeleteRelatedEntriesWithGroup =
                                            !shouldDeleteRelatedEntriesWithGroup
                                    },
                                    trailingContent = {
                                        Checkbox(
                                            checked = shouldDeleteRelatedEntriesWithGroup,
                                            onCheckedChange = {
                                                if (uiState.isDeleting) return@Checkbox
                                                shouldDeleteRelatedEntriesWithGroup = it
                                            },
                                        )
                                    },
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (uiState.isDeleting) return@TextButton
                        if (hasRelatedEntries && shouldDeleteRelatedEntriesWithGroup) {
                            onDeleteWithRecordsConfirm()
                        } else {
                            onDeleteConfirm()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = stringResource(R.string.delete_entries_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!uiState.isDeleting) onDeleteDismiss() },
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (uiState.isArchiveConfirmationVisible) {
        var shouldCreateActiveCopyAfterArchive by rememberSaveable { mutableStateOf(false) }
        var hasAcknowledgedArchiveIsPermanent by rememberSaveable { mutableStateOf(false) }
        val isArchiveActionInProgress = uiState.isSaving ||
            uiState.isFinishingAfterSave ||
            uiState.isArchiving ||
            uiState.isRecreatingAfterArchive
        val isArchiveBlockedByCurrentOrFuturePlannedSlots =
            uiState.currentOrFuturePlannedSlotCount > 0
        // null = the default "now" cutoff (bound at confirm time); a non-null
        // value is an explicitly picked day, archived through its end of day.
        var selectedArchiveDate by rememberSaveable {
            mutableStateOf<LocalDate?>(null)
        }
        var isArchiveDatePickerVisible by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(
            uiState.archiveDateWindow.minDate,
            uiState.archiveDateWindow.maxDate,
            uiState.archiveDateWindow.isSelectable,
        ) {
            val minDate = uiState.archiveDateWindow.minDate
            val current = selectedArchiveDate
            selectedArchiveDate = when {
                current == null -> null
                !uiState.archiveDateWindow.isSelectable -> null
                minDate != null && current.isBefore(minDate) -> minDate
                current.isAfter(uiState.archiveDateWindow.maxDate) ->
                    uiState.archiveDateWindow.maxDate

                else -> current
            }
        }
        if (isArchiveDatePickerVisible && uiState.archiveDateWindow.isSelectable) {
            DatePickerModal(
                onDateSelected = { selectedArchiveDate = it },
                onDismiss = { isArchiveDatePickerVisible = false },
                initialSelectedDate = selectedArchiveDate ?: uiState.archiveDateWindow.maxDate,
                minimumDate = uiState.archiveDateWindow.minDate,
                maximumDate = uiState.archiveDateWindow.maxDate,
                // Only offer a reset once a date is chosen; null already means "now".
                onReset = if (selectedArchiveDate != null) {
                    { selectedArchiveDate = null }
                } else {
                    null
                },
                resetButtonText = stringResource(R.string.archive_medication_group_reset_to_now),
            )
        }
        AlertDialog(
            onDismissRequest = {
                if (!isArchiveActionInProgress) {
                    onArchiveDismiss()
                }
            },
            title = { Text(text = stringResource(R.string.archive_medication_group_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap))) {
                    Text(text = stringResource(R.string.archive_medication_group_confirmation))
                    if (isArchiveBlockedByCurrentOrFuturePlannedSlots) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.archive_medication_group_current_or_future_planned_slots_block,
                                uiState.currentOrFuturePlannedSlotCount,
                                uiState.currentOrFuturePlannedSlotCount,
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    val selectedArchiveDateValue = selectedArchiveDate
                    Spacer(modifier = Modifier.height(12.dp))
                    HrtSection(title = null) {
                        item {
                            PreferenceSegmentedListItem(
                                title = stringResource(R.string.archive_medication_group_end_date),
                                titleTextStyle = MaterialTheme.typography.labelLarge,
                                supportingText = when {
                                    !uiState.archiveDateWindow.isLoaded -> null
                                    // No explicit pick archives as of now; an explicit date
                                    // (incl. today) archives through the end of that day. A
                                    // not-yet-started plan has no selectable backdate and
                                    // stays on "Now".
                                    selectedArchiveDateValue == null ->
                                        stringResource(R.string.archive_medication_group_end_date_now)
                                    else -> archiveDateFormatter(selectedArchiveDateValue)
                                },
                                onClick = if (uiState.archiveDateWindow.isSelectable) {
                                    {
                                        if (!isArchiveActionInProgress) {
                                            isArchiveDatePickerVisible = true
                                        }
                                    }
                                } else {
                                    null
                                },
                                trailingContent = if (uiState.archiveDateWindow.isSelectable) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    null
                                },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        }
                        item {
                            PreferenceSegmentedListItem(
                                title = stringResource(R.string.archive_create_active_copy),
                                titleTextStyle = MaterialTheme.typography.labelLarge,
                                onClick = {
                                    if (isArchiveActionInProgress) {
                                        return@PreferenceSegmentedListItem
                                    }
                                    shouldCreateActiveCopyAfterArchive =
                                        !shouldCreateActiveCopyAfterArchive
                                },
                                trailingContent = {
                                    Checkbox(
                                        checked = shouldCreateActiveCopyAfterArchive,
                                        onCheckedChange = {
                                            if (isArchiveActionInProgress) return@Checkbox
                                            shouldCreateActiveCopyAfterArchive = it
                                        },
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        }
                        item {
                            PreferenceSegmentedListItem(
                                title = stringResource(
                                    R.string.archive_medication_group_irreversible_acknowledgement
                                ),
                                titleTextStyle = MaterialTheme.typography.labelLarge,
                                onClick = {
                                    if (isArchiveActionInProgress) {
                                        return@PreferenceSegmentedListItem
                                    }
                                    hasAcknowledgedArchiveIsPermanent =
                                        !hasAcknowledgedArchiveIsPermanent
                                },
                                trailingContent = {
                                    Checkbox(
                                        checked = hasAcknowledgedArchiveIsPermanent,
                                        onCheckedChange = {
                                            if (isArchiveActionInProgress) return@Checkbox
                                            hasAcknowledgedArchiveIsPermanent = it
                                        },
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                // Semantic enable kept (acknowledgement + no-blocking-slots) so
                // the user can't fire archive without meeting the gates. The
                // in-progress lock is enforced as a click no-op so the button
                // stays visually normal while ROOM is writing.
                TextButton(
                    enabled = hasAcknowledgedArchiveIsPermanent &&
                        !isArchiveBlockedByCurrentOrFuturePlannedSlots &&
                        !isArchiveDateSelectionInvalid(
                            selectedArchiveDate,
                            uiState.archiveDateWindow,
                        ),
                    onClick = {
                        if (isArchiveActionInProgress) return@TextButton
                        runArchiveConfirmationAction(
                            shouldCreateActiveCopyAfterArchive =
                                shouldCreateActiveCopyAfterArchive,
                            archivedThroughDate = selectedArchiveDate,
                            focusManager = focusManager,
                            keyboardController = keyboardController,
                            onArchiveConfirm = onArchiveConfirm,
                            onArchiveAndRecreateConfirm = onArchiveAndRecreateConfirm,
                        )
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = stringResource(R.string.archive)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!isArchiveActionInProgress) onArchiveDismiss() },
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
                    onClick = {
                        if (!uiState.isDeletingRelatedEntries) onDeleteRelatedEntriesConfirm()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = stringResource(R.string.delete_entries_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!uiState.isDeletingRelatedEntries) onDeleteRelatedEntriesDismiss()
                    },
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
                        val localId = removalRequest.localId
                        pendingMedicationRemoval = null
                        // Removal is triggered from inside the slot editor
                        // sheet, so animate the sheet closed and dismiss the
                        // editor alongside the actual removal.
                        hideBottomSheet(scope, sheetState) {
                            onRemoveMedication(localId)
                            onDismissMedicationEditor()
                        }
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
    LaunchedEffect(uiState.scrollToTopRequestVersion) {
        if (uiState.scrollToTopRequestVersion > 0) {
            listState.animateScrollToItem(0)
            topAppBarState.heightOffset = 0f
            topAppBarState.contentOffset = 0f
        }
    }
    val contentPadding = dimensionResource(R.dimen.padding_medium)
    val navigationBarBottomPadding = if (drawBehindNavigationBar) {
        val density = LocalDensity.current
        with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    } else {
        0.dp
    }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = if (drawBehindNavigationBar) {
            WindowInsets(0, 0, 0, 0)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        topBar = {
            TopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior) {
                    listState.animateScrollToItem(0)
                },
                title = {
                    val title = stringResource(
                        if (shouldUseArchivedPresentation) {
                            R.string.archived_medication_group
                        } else if (shouldRenderAsEditing) {
                            R.string.edit_medication_group
                        } else {
                            R.string.add_medication_group
                        }
                    )
                    Text(
                        text = title,
                        modifier = Modifier.cjkTextOffset(title, amount = (-1.5).dp),
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
                    if (!shouldUseArchivedPresentation) {
                        HrtButton(
                            text = stringResource(R.string.save),
                            onClick = {
                                if (shouldDisableMedicationGroupEditorSaveAction(
                                        uiState = uiState,
                                    )
                                ) return@HrtButton
                                onSaveClick()
                            },
                            enabled = canSave,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        AppContentContainer(modifier = Modifier.padding(innerPadding)) {
            if (uiState.isLoadingGroupForEditing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
                return@AppContentContainer
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = appContentPaddingValues(
                    bottom = contentPadding + navigationBarBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            if (shouldRenderLockedState) {
                item {
                    SupportMessageListItem(
                        text = stringResource(R.string.group_locked_banner),
                        painter = painterResource(R.drawable.ic_calendar_lock),
                        leadingIconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        titleColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            item {
                MedicationGroupNameField(
                    groupName = uiState.groupName,
                    defaultGroupName = uiState.defaultGroupName,
                    isEditing = uiState.isEditing,
                    isFinishingAfterSave = uiState.isFinishingAfterSave,
                    selectedColorKey = uiState.groupColorKey,
                    enabled = !uiState.isArchived,
                    focusRequester = groupNameFocusRequester,
                    onGroupNameChange = onGroupNameChange,
                    onColorSelected = onGroupColorChange,
                )
            }

            item {
                Column {
                    HrtSection(title = stringResource(R.string.group_medications_title)) {
                        if (uiState.medications.isEmpty()) {
                            item {
                                SupportMessageListItem(
                                    text = stringResource(R.string.group_medications_empty),
                                    painter = painterResource(R.drawable.ic_info),
                                    leadingIconSize = 22.dp
                                )
                            }
                        } else {
                            uiState.medications.forEach { medication ->
                                item {
                                    val medicationEditable = !areFieldsRenderedLocked
                                    val isMedicineArchived =
                                        medication.resolvedMedicine?.isArchived == true
                                    MedicationCard(
                                        medicine = medication.resolvedMedicine,
                                        doseInstruction = medication.doseInstruction,
                                        applicationType = medication.applicationType,
                                        medicationCount = medication.count,
                                        groupColorKey = uiState.groupColorKey,
                                        // Null onClick drops the ripple on
                                        // medication cards while the group is
                                        // archived / locked, since tapping does
                                        // nothing in that state.
                                        onClick = if (medicationEditable) {
                                            { openMedicationEditor(medication.localId) }
                                        } else {
                                            null
                                        },
                                        // Editable cards open the slot editor sheet,
                                        // where the removal action now lives — so the
                                        // trailing affordance is a tap-to-edit
                                        // chevron rather than a delete icon. When the
                                        // group is archived, flag any medicine that's
                                        // also archived instead (the duplicate flow
                                        // skips those slots since saving them would
                                        // silently revive the archived medicine).
                                        trailingContent = when {
                                            uiState.isArchived && isMedicineArchived -> {
                                                {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_archive),
                                                        contentDescription = stringResource(
                                                            R.string.medication_archived_indicator_cd,
                                                        ),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                }
                                            }

                                            medicationEditable -> {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Rounded.ChevronRight,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(24.dp),
                                                    )
                                                }
                                            }

                                            else -> null
                                        },
                                        enabled = true,
                                    )
                                }
                            }
                        }
                    }
                    if (!areFieldsRenderedLocked) {
                        HrtFilledTonalButton(
                            text = stringResource(R.string.add),
                            onClick = openAddMedicationEditor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
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
                val pastScheduleSelectorState =
                    if (
                        shouldShowPastScheduleSection(
                            uiState = uiState,
                            referenceTime = resolvedOccurrenceReferenceTime,
                        )
                    ) {
                        val recreatedLockedMessage =
                            if (shouldShowRecreatedPastScheduleMessage(uiState)) {
                                stringResource(R.string.group_past_schedule_recreated_locked_note)
                            } else {
                                null
                            }
                        resolvePastScheduleSelectorState(
                            uiState = uiState,
                            isNewGroupCreationFlow = isNewGroupCreationFlow,
                            lockedMessage = recreatedLockedMessage,
                        )
                    } else {
                        null
                    }
                val onPastScheduleOptionSelected: (PastScheduleOption) -> Unit = { option ->
                    if (pastScheduleSelectorState?.interactive == true) {
                        applyPastScheduleOption(
                            option = option,
                            onIncludePastScheduledSlotsChange = onIncludePastScheduledSlotsChange,
                            onCreatePastScheduledSlotRecordsChange =
                                onCreatePastScheduledSlotRecordsChange,
                        )
                    }
                }
                val showRecreatedStartDatePastLimitMessage =
                    shouldShowRecreatedStartDatePastLimitMessage(uiState)
                Column {
                    HrtSection(title = stringResource(R.string.group_schedule_title)) {
                        if (showRecreatedStartDatePastLimitMessage) {
                            item {
                                SupportMessageListItem(
                                    text = stringResource(R.string.group_schedule_recreated_start_date_past_note),
                                    painter = painterResource(R.drawable.ic_calendar_lock),
                                    leadingIconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    titleColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }
                    if (showRecreatedStartDatePastLimitMessage) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
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
                            enabled = !areFieldsRenderedLocked,
                            layout = ConnectedButtonGroupLayout.ROW,
                            expandOptions = true,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (uiState.scheduleType == MedicationGroupScheduleType.WEEKLY) {
                        WeeklyScheduleEditor(
                            sinceDate = uiState.sinceDate,
                            intervalWeeks = uiState.weeklyIntervalWeeks,
                            selectedDaysOfWeek = uiState.weeklyDaysOfWeek,
                            firstDayOfWeek = uiState.firstDayOfWeek,
                            time = uiState.weeklyTime,
                            originalTime = uiState.originalWeeklyTime.takeIf {
                                uiState.originalScheduleType ==
                                    MedicationGroupScheduleType.WEEKLY
                            },
                            previewOccurrences = upcomingOccurrences,
                            currentDate = currentDate,
                            appLocale = appLocale,
                            dateFormatter = dateFormatter,
                            timeFormatter = timeFormatter,
                            onSinceDateChange = { currentDate -> pendingSinceDate = currentDate },
                            onIntervalChange = onWeeklyIntervalChange,
                            onDayChange = onWeeklyDayChange,
                            onResetDaysOfWeek = onWeeklyResetDaysOfWeek,
                            onTimeChange = { currentTime -> pendingWeeklyTime = currentTime },
                            pastScheduleSelectorState = pastScheduleSelectorState,
                            onPastScheduleOptionSelected = onPastScheduleOptionSelected,
                            sinceEnabled = !areFieldsRenderedLocked &&
                                !uiState.isScheduleStartDateLocked,
                            intervalEnabled = !areFieldsRenderedLocked,
                            daySelectionEnabled = !areFieldsRenderedLocked,
                            timeEditEnabled = !uiState.isArchived,
                            showLockedTimeNote = shouldShowLockedTimeChangeNote,
                            shapeLocked = areFieldsRenderedLocked,
                        )
                    } else {
                        DailyScheduleEditor(
                            sinceDate = uiState.sinceDate,
                            intervalDays = uiState.dailyIntervalDays,
                            dailyTimes = uiState.dailyTimes,
                            previewOccurrences = upcomingOccurrences,
                            currentDate = currentDate,
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
                            pastScheduleSelectorState = pastScheduleSelectorState,
                            onPastScheduleOptionSelected = onPastScheduleOptionSelected,
                            sinceEnabled = !areFieldsRenderedLocked &&
                                !uiState.isScheduleStartDateLocked,
                            intervalEnabled = !areFieldsRenderedLocked,
                            addRemoveTimeEnabled = !areFieldsRenderedLocked,
                            timeEditEnabled = !uiState.isArchived,
                            showLockedTimeNote = shouldShowLockedTimeChangeNote,
                            shapeLocked = areFieldsRenderedLocked,
                        )
                    }
                }
            }

            item {
                HrtSection(title = stringResource(R.string.group_notifications_title)) {
                    item {
                        NotificationsCard(
                            enabled = uiState.notificationsEnabled,
                            toggleEnabled = presentedNotificationsToggleEnabled,
                            onToggle = onNotificationsEnabledChange,
                        )
                    }
                    if (uiState.isArchived) {
                        item {
                            SupportMessageListItem(
                                text = stringResource(R.string.group_notifications_archived_disabled),
                                painter = painterResource(R.drawable.ic_error_outline),
                                leadingIconTint = MaterialTheme.colorScheme.tertiary,
                                leadingIconSize = 24.dp,
                            )
                        }
                    } else {
                        when (notificationSupportState) {
                            NotificationSupportState.ACCESS_OFF -> {
                                item {
                                    SupportMessageListItem(
                                        text = stringResource(R.string.settings_reminders_permission_off_summary),
                                        painter = painterResource(R.drawable.ic_error_outline),
                                        leadingIconTint = MaterialTheme.colorScheme.tertiary,
                                        leadingIconSize = 24.dp,
                                        onClick = onRecoverMasterReminders,
                                        showChevron = true,
                                    )
                                }
                            }

                            NotificationSupportState.MASTER_OFF -> {
                                item {
                                    SupportMessageListItem(
                                        text = stringResource(R.string.group_notifications_master_disabled),
                                        painter = painterResource(R.drawable.ic_error_outline),
                                        leadingIconTint = MaterialTheme.colorScheme.tertiary,
                                        leadingIconSize = 24.dp,
                                        onClick = { isMasterReminderRecoveryDialogVisible = true },
                                        showChevron = true,
                                    )
                                }
                            }

                            NotificationSupportState.INEXACT -> {
                                item {
                                    SupportMessageListItem(
                                        text = stringResource(R.string.group_notifications_inexact_warning),
                                        painter = painterResource(R.drawable.ic_error_outline),
                                        leadingIconTint = MaterialTheme.colorScheme.tertiary,
                                        leadingIconSize = 24.dp,
                                        onClick = onRequestExactAlarmAccess,
                                        showChevron = true,
                                    )
                                }
                            }

                            NotificationSupportState.NONE -> Unit
                        }
                    }
                }
            }

            if (shouldRenderAsEditing) {
                item {
                    val hasRelatedRecords = shouldShowMedicationGroupDeleteRelatedRecordsAsAvailable(
                        uiState = uiState,
                        isNewGroupCreationFlow = isNewGroupCreationFlow,
                    )
                    HrtSection(title = stringResource(R.string.group_danger_zone_title)) {
                        if (!uiState.isArchived) {
                            item {
                                ArchiveMedicationGroupCard(
                                    onClick = onArchiveClick,
                                )
                            }
                        } else {
                            item {
                                DuplicateMedicationGroupCard(
                                    onClick = onDuplicateArchivedGroupClick,
                                )
                            }
                        }
                        item {
                            DeleteMedicationGroupRecordsCard(
                                onClick = {
                                    if (!isMedicationGroupEditorBusy(uiState)) {
                                        if (hasRelatedRecords) {
                                            onDeleteRelatedEntriesClick()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                deleteRelatedEntriesUnavailableMessage,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                },
                            )
                        }
                        item {
                            DeleteMedicationGroupCard(
                                onClick = onDeleteClick,
                            )
                        }
                    }
                }
            }
        }
        }
    }

    uiState.editingMedication?.let { medication ->
        val isEditingExistingMedication = uiState.medications.any { it.localId == medication.localId }
        val canEditMedicationIdentity = !areFieldsRenderedLocked && !isEditingExistingMedication
        val resolvedMedicine = checkNotNull(medication.resolvedMedicine) {
            "Medication group slot editor requires a resolved medicine."
        }
        // Removal lives in the sheet now (the card shows a chevron, not a
        // delete icon). Only an already-added slot can be removed, and only
        // while the group's fields are editable.
        val canRemoveMedication = isEditingExistingMedication && !areFieldsRenderedLocked
        val editingMedicationName = medicationEntryTitle(
            resolvedMedicine,
            medication.resolvedApplicationType(),
        )
        MedicationGroupSlotEditorSheet(
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
            medicineDraft = medication.medicineDraft,
            doseInstructionDraft = medication.doseInstructionDraft,
            resolvedMedicine = resolvedMedicine,
            canEditMedicationIdentity = canEditMedicationIdentity,
            onMedicineDraftChange = onMedicineDraftChange,
            onDoseInstructionDraftChange = onDoseInstructionDraftChange,
            onOpenMedicinePicker = {
                if (canEditMedicationIdentity) {
                    onOpenMedicinePicker(medication.localId)
                }
            },
            countText = medication.countText,
            onCountTextChange = onEditingMedicationCountTextChange,
            onDecreaseCountClick = onDecreaseEditingMedicationCount,
            onIncreaseCountClick = onIncreaseEditingMedicationCount,
            errorMessageRes = uiState.medicationEditorErrorMessageRes,
            destructiveButtonText = if (canRemoveMedication) {
                stringResource(R.string.remove)
            } else {
                null
            },
            onDestructiveAction = if (canRemoveMedication) {
                {
                    pendingMedicationRemoval = MedicationRemovalRequest(
                        localId = medication.localId,
                        medicationName = editingMedicationName,
                    )
                }
            } else {
                null
            },
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

internal fun resolveMedicationGroupEditorIsNewGroupCreationFlow(
    uiState: MedicationGroupEditorUiState,
    startedAsNewGroupCreationFlow: Boolean,
    openedFromArchivedGroupsPage: Boolean,
): Boolean {
    return startedAsNewGroupCreationFlow ||
        (openedFromArchivedGroupsPage && !uiState.isArchived)
}

internal fun shouldRenderMedicationGroupEditorAsEditing(
    uiState: MedicationGroupEditorUiState,
    isNewGroupCreationFlow: Boolean,
): Boolean {
    return uiState.isEditing &&
        !(isNewGroupCreationFlow && (uiState.isSaved || uiState.isFinishingAfterSave))
}

internal fun shouldUseArchivedMedicationGroupEditorPresentation(
    uiState: MedicationGroupEditorUiState,
    openedFromArchivedGroupsPage: Boolean,
): Boolean {
    return openedFromArchivedGroupsPage && uiState.isArchived
}

internal enum class MedicationGroupEditorSaveNavigationTarget {
    BACK,
    PLAN,
}

internal fun resolveMedicationGroupEditorSaveNavigationTarget(
    uiState: MedicationGroupEditorUiState,
    openedFromArchivedGroupsPage: Boolean,
): MedicationGroupEditorSaveNavigationTarget {
    return if (openedFromArchivedGroupsPage && !uiState.isArchived) {
        MedicationGroupEditorSaveNavigationTarget.PLAN
    } else {
        MedicationGroupEditorSaveNavigationTarget.BACK
    }
}

private fun showCreatePastScheduledSlotRecordsToast(
    context: Context,
    saveState: CreatePastScheduledSlotRecordsSaveState,
) {
    val message = when (saveState.result) {
        CreatePastScheduledSlotRecordsResult.FAILURE ->
            context.getString(R.string.group_schedule_backfill_records_failure)

        null -> saveState.savedRecordCount?.let { savedRecordCount ->
            if (savedRecordCount == 0) {
                context.getString(R.string.group_schedule_backfill_records_empty)
            } else {
                context.resources.getQuantityString(
                    R.plurals.plan_batch_add_success,
                    savedRecordCount,
                    savedRecordCount,
                )
            }
        }
    }
    if (message != null) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

internal fun shouldDisableMedicationGroupEditorSaveAction(
    uiState: MedicationGroupEditorUiState,
): Boolean {
    return !hasSaveableMedicationGroupContent(uiState) ||
        uiState.isArchived ||
        uiState.scheduleTimeOrderError ||
        uiState.isSaved ||
        uiState.isFinishingAfterSave ||
        uiState.isDeleted ||
        uiState.isFinishingAfterDeleteOrArchive
}

internal fun runArchiveConfirmationAction(
    shouldCreateActiveCopyAfterArchive: Boolean,
    archivedThroughDate: LocalDate?,
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    onArchiveConfirm: (LocalDate?) -> Unit,
    onArchiveAndRecreateConfirm: (LocalDate?) -> Unit,
) {
    dismissInputAndRun(
        focusManager = focusManager,
        keyboardController = keyboardController,
    ) {
        if (shouldCreateActiveCopyAfterArchive) {
            onArchiveAndRecreateConfirm(archivedThroughDate)
        } else {
            onArchiveConfirm(archivedThroughDate)
        }
    }
}

internal fun isMedicationGroupEditorBusy(
    uiState: MedicationGroupEditorUiState,
): Boolean {
    return uiState.isLoadingGroupForEditing ||
        uiState.isSaving ||
        uiState.isDeleting ||
        uiState.isArchiving ||
        uiState.isRecreatingAfterArchive ||
        uiState.isDeletingRelatedEntries ||
        uiState.isSaved ||
        uiState.isFinishingAfterSave ||
        uiState.isDeleted ||
        uiState.isFinishingAfterDeleteOrArchive
}

internal fun shouldSuppressMedicationGroupEditorLockedStateDuringGeneratedHistorySave(
    uiState: MedicationGroupEditorUiState,
    isNewGroupCreationFlow: Boolean,
): Boolean {
    return (uiState.isSaving || uiState.isSaved || uiState.isFinishingAfterSave) &&
        (isNewGroupCreationFlow || uiState.createPastScheduledSlotRecords)
}

internal fun shouldShowMedicationGroupDeleteRelatedRecordsAsAvailable(
    uiState: MedicationGroupEditorUiState,
    isNewGroupCreationFlow: Boolean,
): Boolean {
    return uiState.relatedEntryCount > 0 &&
        !shouldSuppressMedicationGroupEditorLockedStateDuringGeneratedHistorySave(
            uiState = uiState,
            isNewGroupCreationFlow = isNewGroupCreationFlow,
        )
}

internal fun shouldShowRecreatedStartDatePastLimitMessage(
    uiState: MedicationGroupEditorUiState,
): Boolean {
    return !uiState.isArchived &&
        uiState.recreatedFromGroupId != null &&
        uiState.relatedEntryCount == 0
}

internal enum class PastScheduleOption {
    DO_NOT_SHOW,
    SHOW,
    SHOW_AND_GENERATE_RECORDS,
}

internal fun resolvePastScheduleOption(
    uiState: MedicationGroupEditorUiState,
): PastScheduleOption {
    return when {
        uiState.includePastScheduledSlots && uiState.createPastScheduledSlotRecords ->
            PastScheduleOption.SHOW_AND_GENERATE_RECORDS

        uiState.includePastScheduledSlots -> PastScheduleOption.SHOW
        else -> PastScheduleOption.DO_NOT_SHOW
    }
}

internal fun resolvePastScheduleSelectorState(
    uiState: MedicationGroupEditorUiState,
    isNewGroupCreationFlow: Boolean,
    lockedMessage: String?,
): PastScheduleSelectorUiState {
    val selectedPastScheduleOption = resolvePastScheduleOption(uiState)
    val pastScheduleOptionsEnabled = lockedMessage == null &&
        uiState.canEditBackfillOption
    val showGeneratePastRecordsOption =
        shouldShowGeneratePastScheduledSlotRecordsOption(
            uiState = uiState,
            isNewGroupCreationFlow = isNewGroupCreationFlow,
            isFinishingAfterSave = uiState.isFinishingAfterSave,
        ) ||
            selectedPastScheduleOption == PastScheduleOption.SHOW_AND_GENERATE_RECORDS

    return PastScheduleSelectorUiState(
        selectedOption = selectedPastScheduleOption,
        showGeneratePastRecordsOption = showGeneratePastRecordsOption,
        enabled = pastScheduleOptionsEnabled,
        interactive = pastScheduleOptionsEnabled,
        lockedMessage = lockedMessage,
    )
}

internal fun shouldShowPastScheduleSection(
    uiState: MedicationGroupEditorUiState,
    referenceTime: LocalDateTime,
): Boolean {
    return !uiState.isArchived &&
        (
            shouldShowRecreatedPastScheduleMessage(uiState) ||
                hasPastScheduleOptionWindow(
                    uiState = uiState,
                    referenceTime = referenceTime,
                )
            )
}

internal fun shouldShowGeneratePastScheduledSlotRecordsOption(
    uiState: MedicationGroupEditorUiState,
    isNewGroupCreationFlow: Boolean,
    isFinishingAfterSave: Boolean,
): Boolean {
    return !uiState.isArchived &&
        !shouldShowRecreatedPastScheduleMessage(uiState)
}

internal fun shouldShowRecreatedPastScheduleMessage(
    uiState: MedicationGroupEditorUiState,
): Boolean {
    return !uiState.isArchived &&
        (uiState.recreatedFromGroupId != null || uiState.pendingReplacementGroupId != null)
}

internal fun hasPastScheduleOptionWindow(
    uiState: MedicationGroupEditorUiState,
    referenceTime: LocalDateTime,
): Boolean {
    val currentDate = referenceTime.toLocalDate()
    return when {
        uiState.sinceDate.isBefore(currentDate) -> true
        uiState.sinceDate.isAfter(currentDate) -> false
        else -> firstPlannedDoseOnScheduleStartDate(uiState)?.let { firstDose ->
            firstDose.isBefore(referenceTime)
        } ?: false
    }
}

internal fun firstPlannedDoseOnScheduleStartDate(
    uiState: MedicationGroupEditorUiState,
): LocalDateTime? {
    val firstTime = when (uiState.scheduleType) {
        MedicationGroupScheduleType.DAILY -> uiState.dailyTimes
            .ifEmpty { listOf(MedicationGroupScheduleTimeUiState(time = LocalTime.of(9, 0))) }
            .map(MedicationGroupScheduleTimeUiState::time)
            .minOrNull()

        MedicationGroupScheduleType.WEEKLY -> uiState.weeklyTime.takeIf {
            uiState.sinceDate.dayOfWeek in uiState.weeklyDaysOfWeek
        }
    }
    return firstTime?.let { time -> LocalDateTime.of(uiState.sinceDate, time) }
}

private fun applyPastScheduleOption(
    option: PastScheduleOption,
    onIncludePastScheduledSlotsChange: (Boolean) -> Unit,
    onCreatePastScheduledSlotRecordsChange: (Boolean) -> Unit,
) {
    when (option) {
        PastScheduleOption.DO_NOT_SHOW -> {
            onCreatePastScheduledSlotRecordsChange(false)
            onIncludePastScheduledSlotsChange(false)
        }

        PastScheduleOption.SHOW -> {
            onCreatePastScheduledSlotRecordsChange(false)
            onIncludePastScheduledSlotsChange(true)
        }

        PastScheduleOption.SHOW_AND_GENERATE_RECORDS -> {
            onIncludePastScheduledSlotsChange(true)
            onCreatePastScheduledSlotRecordsChange(true)
        }
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

internal fun resolveInexactReminderWarningVisibility(
    hasNotificationAccess: Boolean,
    canScheduleExactAlarms: Boolean,
): Boolean = hasNotificationAccess && !canScheduleExactAlarms

internal data class ExactAlarmReminderEnableUiState(
    val showInexactReminderWarning: Boolean,
    val showExactAlarmDialog: Boolean,
)

internal fun resolveExactAlarmUiStateAfterEnablingReminders(
    canScheduleExactAlarms: Boolean,
): ExactAlarmReminderEnableUiState {
    return ExactAlarmReminderEnableUiState(
        showInexactReminderWarning = !canScheduleExactAlarms,
        showExactAlarmDialog = false,
    )
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

@Composable
internal fun MedicationGroupNameField(
    groupName: String,
    defaultGroupName: String,
    isEditing: Boolean,
    isFinishingAfterSave: Boolean,
    selectedColorKey: MedicationGroupColorKey,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onGroupNameChange: (String) -> Unit,
    onColorSelected: (MedicationGroupColorKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val groupNameState = rememberTextFieldState(initialText = groupName)
    val currentGroupName by rememberUpdatedState(groupName)
    val currentOnGroupNameChange by rememberUpdatedState(onGroupNameChange)

    LaunchedEffect(groupNameState, groupName) {
        if (groupNameState.text.toString() != groupName) {
            groupNameState.setTextAndPlaceCursorAtEnd(groupName)
        }
    }

    LaunchedEffect(groupNameState) {
        snapshotFlow { groupNameState.text.toString() }.collect { value ->
            if (value != currentGroupName) {
                currentOnGroupNameChange(value)
            }
        }
    }

    val groupNamePlaceholder = medicationGroupNamePlaceholder(
        defaultGroupName = defaultGroupName,
        isEditing = isEditing,
        isFinishingAfterSave = isFinishingAfterSave,
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            state = groupNameState,
            labelPosition = medicationGroupNameFieldLabelPosition(),
            label = { Text(text = stringResource(R.string.field_medication_group_name)) },
            placeholder = {
                if (groupNamePlaceholder != null) {
                    Text(text = groupNamePlaceholder)
                }
            },
            leadingIcon = {
                MedicationGroupColorPickerLeadingIcon(
                    selectedColorKey = selectedColorKey,
                    enabled = enabled,
                    onColorSelected = onColorSelected,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            enabled = enabled,
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onKeyboardAction = { focusManager.clearFocus() },
        )
    }
}

internal fun medicationGroupNamePlaceholder(
    defaultGroupName: String,
    isEditing: Boolean,
    isFinishingAfterSave: Boolean,
): String? {
    // isFinishingAfterSave bridges the one-frame gap where the snapshot has
    // already flipped isEditing=true but the TextFieldState hasn't synced the
    // resolved groupName yet, which would otherwise flash an empty field.
    val suppress = isEditing && !isFinishingAfterSave
    return defaultGroupName.takeIf { !suppress && it.isNotBlank() }
}

internal fun medicationGroupNameFieldLabelPosition(): TextFieldLabelPosition {
    return TextFieldLabelPosition.Attached(alwaysMinimize = true)
}

private fun maybeRequestExactAlarmAccess(
    context: Context,
    launch: (Intent) -> Unit
) {
    if (canScheduleExactAlarms(context)) {
        return
    }

    val intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        "package:${context.packageName}".toUri()
    )
    launch(intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationGroupColorPickerLeadingIcon(
    selectedColorKey: MedicationGroupColorKey,
    enabled: Boolean,
    onColorSelected: (MedicationGroupColorKey) -> Unit,
) {
    val currentScheme = rememberMedicationGroupColorScheme(colorKey = selectedColorKey)
    if (!enabled) {
        Icon(
            painter = painterResource(R.drawable.ic_circle),
            contentDescription = null,
            tint = currentScheme.primary,
        )
        return
    }
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val pickerActionDescription = stringResource(R.string.group_color_picker_action)
    val pickerTitle = stringResource(R.string.group_color_picker_title)
    BackHandler(enabled = tooltipState.isVisible) {
        tooltipState.dismiss()
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Below,
        ),
        tooltip = {
            RichTooltip(
                title = { Text(pickerTitle) },
            ) {
                MedicationGroupColorSwatchGrid(
                    selectedColorKey = selectedColorKey,
                    onColorSelected = { colorKey ->
                        onColorSelected(colorKey)
                        tooltipState.dismiss()
                    },
                )
            }
        },
        state = tooltipState,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(
                    onClickLabel = pickerActionDescription,
                ) {
                    if (tooltipState.isVisible) {
                        tooltipState.dismiss()
                    } else {
                        scope.launch { tooltipState.show() }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_circle),
                contentDescription = pickerActionDescription,
                tint = currentScheme.primary,
            )
        }
    }
}

@Composable
private fun MedicationGroupColorSwatchGrid(
    selectedColorKey: MedicationGroupColorKey,
    onColorSelected: (MedicationGroupColorKey) -> Unit,
) {
    val ordered = MedicationGroupColorKey.assignmentOrder
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ordered.chunked(5).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEachIndexed { indexInRow, key ->
                    val absoluteIndex = ordered.indexOf(key) + 1
                    val scheme = rememberMedicationGroupColorScheme(colorKey = key)
                    val isSelected = key == selectedColorKey
                    val swatchDescription = stringResource(
                        if (isSelected) {
                            R.string.group_color_picker_swatch_selected_content_description
                        } else {
                            R.string.group_color_picker_swatch_content_description
                        },
                        absoluteIndex,
                    )
                    // Selection carves a small inner ring in the surface
                    // colour instead of showing a check, so the swatch reads
                    // as a framed chip.
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(scheme.primary)
                            .clickable(onClickLabel = swatchDescription) {
                                onColorSelected(key)
                            },
                    ) {
                        if (isSelected) {
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

                    if (indexInRow < row.lastIndex) {
                        // No spacer needed: Row's horizontalArrangement
                        // already handles gaps. Keeping the index check so
                        // future tweaks (e.g. a separator) have a hook.
                    }
                }
            }
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
            onGroupColorChange = { },
            onScheduleTypeChange = { },
            onSinceDateChange = { },
            onIncludePastScheduledSlotsChange = { },
            onCreatePastScheduledSlotRecordsChange = { },
            onNotificationsEnabledChange = { },
            onRequestExactAlarmAccess = { },
            onRecoverMasterReminders = { },
            hasNotificationAccess = false,
            notificationsToggleEnabled = false,
            showInexactReminderWarning = false,
            onWeeklyIntervalChange = { },
            onWeeklyDayChange = { },
            onWeeklyResetDaysOfWeek = { },
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
            onConsumeMedicationEditorInfoMessage = { },
            onMedicineDraftChange = { },
            onDoseInstructionDraftChange = { },
            onOpenMedicinePicker = { _ -> },
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
            onArchiveConfirm = { _ -> },
            onArchiveMedicationGroupResultConsumed = { },
            onDuplicateArchivedGroupClick = { },
            onDuplicateArchivedGroupResultConsumed = { },
            onArchiveAndRecreateConfirm = { _ -> },
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
            onGroupColorChange = { },
            onScheduleTypeChange = { },
            onSinceDateChange = { },
            onIncludePastScheduledSlotsChange = { },
            onCreatePastScheduledSlotRecordsChange = { },
            onNotificationsEnabledChange = { },
            onRequestExactAlarmAccess = { },
            onRecoverMasterReminders = { },
            hasNotificationAccess = true,
            notificationsToggleEnabled = true,
            showInexactReminderWarning = true,
            onWeeklyIntervalChange = { },
            onWeeklyDayChange = { },
            onWeeklyResetDaysOfWeek = { },
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
            onConsumeMedicationEditorInfoMessage = { },
            onMedicineDraftChange = { },
            onDoseInstructionDraftChange = { },
            onOpenMedicinePicker = { _ -> },
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
            onArchiveConfirm = { _ -> },
            onArchiveMedicationGroupResultConsumed = { },
            onDuplicateArchivedGroupClick = { },
            onDuplicateArchivedGroupResultConsumed = { },
            onArchiveAndRecreateConfirm = { _ -> },
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
    name = "Group Editor Daily Locked",
    showBackground = true,
    widthDp = 420,
    heightDp = 920
)
@Composable
private fun MedicationGroupEditorDailyLockedPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        MedicationGroupEditorPreviewContent(
            uiState = buildMedicationGroupEditorPreviewUiState(
                scheduleType = MedicationGroupScheduleType.DAILY,
                editingGroupId = "preview-locked-daily-group",
                remindersEnabled = true,
                notificationsEnabled = true,
            ).copy(
                plannedEntryCount = 12,
                relatedEntryCount = 16,
            ),
            hasNotificationAccess = true,
            notificationsToggleEnabled = true,
        )
    }
}

@Preview(
    name = "Group Editor Weekly Locked",
    showBackground = true,
    widthDp = 420,
    heightDp = 920
)
@Composable
private fun MedicationGroupEditorWeeklyLockedPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        MedicationGroupEditorPreviewContent(
            uiState = buildMedicationGroupEditorPreviewUiState(
                scheduleType = MedicationGroupScheduleType.WEEKLY,
                editingGroupId = "preview-locked-weekly-group",
                remindersEnabled = true,
                notificationsEnabled = true,
            ).copy(
                plannedEntryCount = 6,
                relatedEntryCount = 8,
            ),
            hasNotificationAccess = true,
            notificationsToggleEnabled = true,
        )
    }
}

@Composable
private fun MedicationGroupEditorPreviewContent(
    uiState: MedicationGroupEditorUiState,
    hasNotificationAccess: Boolean,
    notificationsToggleEnabled: Boolean,
    showInexactReminderWarning: Boolean = false,
) {
    MedicationGroupEditorScreenContent(
        uiState = uiState,
        onNavigateBack = { },
        onGroupNameChange = { },
        onGroupColorChange = { },
        onScheduleTypeChange = { },
        onSinceDateChange = { },
        onIncludePastScheduledSlotsChange = { },
        onCreatePastScheduledSlotRecordsChange = { },
        onNotificationsEnabledChange = { },
        onRequestExactAlarmAccess = { },
        onRecoverMasterReminders = { },
        hasNotificationAccess = hasNotificationAccess,
        notificationsToggleEnabled = notificationsToggleEnabled,
        showInexactReminderWarning = showInexactReminderWarning,
        onWeeklyIntervalChange = { },
        onWeeklyDayChange = { },
        onWeeklyResetDaysOfWeek = { },
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
        onConsumeMedicationEditorInfoMessage = { },
        onMedicineDraftChange = { },
            onDoseInstructionDraftChange = { },
            onOpenMedicinePicker = { _ -> },
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
        onArchiveConfirm = { _ -> },
        onArchiveMedicationGroupResultConsumed = { },
        onDuplicateArchivedGroupClick = { },
        onDuplicateArchivedGroupResultConsumed = { },
        onArchiveAndRecreateConfirm = { _ -> },
        onArchiveAndRecreateMedicationGroupResultConsumed = { },
        onDeleteClick = { },
        onDeleteDismiss = { },
        onDeleteConfirm = { },
        onDeleteWithRecordsConfirm = { },
        occurrenceReferenceTime = LocalDateTime.of(2026, 4, 25, 10, 0)
    )
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
                resolvedMedicine = previewEditorMedicine(MedicationKey.ESTRADIOL),
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                count = 1,
            ),
            MedicationGroupMedicationItemUiState(
                localId = "med-2",
                persistedMedicationId = UUID.fromString("73ceca25-8547-43cf-8517-0d1e46a95d56").toString(),
                resolvedMedicine = previewEditorMedicine(MedicationKey.SPIRONOLACTONE),
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                count = 1,
            )
        )
    )
}

private fun previewEditorMedicine(
    medicationKey: MedicationKey,
): com.mkx.hrttracker.model.medication.Medicine {
    val selection = com.mkx.hrttracker.model.medication.MedicineSelection.Catalog(medicationKey)
    val preparation = com.mkx.hrttracker.model.medication.MedicinePreparation.Pill(
        strengthMgPerTablet = 2.0,
    )
    return com.mkx.hrttracker.model.medication.Medicine(
        uuid = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        selection = selection,
        category = medicationKey.category,
        preparation = preparation,
        displayName = null,
        identityKey = com.mkx.hrttracker.model.medication.MedicineIdentityKey.catalog(
            medicationKey, preparation,
        ),
        createdAt = java.time.Instant.EPOCH,
        updatedAt = java.time.Instant.EPOCH,
        archivedAt = null,
        stock = com.mkx.hrttracker.model.medication.MedicineStock(),
    )
}
