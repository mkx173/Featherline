package com.mkx.hrttracker.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.settings.AlarmVibrationPatternOption
import com.mkx.hrttracker.model.settings.PersistentReminderSettings
import com.mkx.hrttracker.model.settings.ReminderRepeatIntervalOption
import com.mkx.hrttracker.model.settings.ReminderRepeatLimitOption
import com.mkx.hrttracker.model.settings.ReminderSnoozeDurationOption
import com.mkx.hrttracker.reminder.canScheduleExactAlarms
import com.mkx.hrttracker.reminder.isFullScreenIntentAllowed
import com.mkx.hrttracker.reminder.rememberReminderCapabilityReconciler
import com.mkx.hrttracker.ui.alarm.DoseAlarmActivity
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.ExactAlarmAccessDialog
import com.mkx.hrttracker.ui.components.HazeTopAppBar
import com.mkx.hrttracker.ui.components.HrtDropdownMenu
import com.mkx.hrttracker.ui.components.HrtDropdownMenuItem
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.appContentPaddingValuesBehindTopAppBar
import com.mkx.hrttracker.ui.components.paddingBehindTopAppBar
import com.mkx.hrttracker.ui.components.pinnedTopAppBarScrollBehavior
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.medicationEntryTitle
import java.util.UUID

@Composable
fun PersistentRemindersScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersistentRemindersViewModel = hiltViewModel(),
) {
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val activeGroups by viewModel.activeGroups.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val latestContext by rememberUpdatedState(context)
    val reminderCapabilityReconciler = rememberReminderCapabilityReconciler()
    val capabilityState by reminderCapabilityReconciler.state.collectAsStateWithLifecycle()
    // No change broadcast exists for this grant, so re-read it whenever the
    // user comes back, as from the system page the warning row opens.
    var canUseFullScreenIntent by remember { mutableStateOf(isFullScreenIntentAllowed(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canUseFullScreenIntent = isFullScreenIntentAllowed(context)
    }
    val exactAlarmAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        reminderCapabilityReconciler.requestReconcile("persistent_reminders_exact_alarm_result")
    }

    val soundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked = result.data?.let { data ->
            IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        // The picker's "Default" entry is the system default URI: store
        // nothing, so the defaults (reminder sound, alarm tone) apply.
        val stored = picked?.takeUnless(RingtoneManager::isDefault)?.toString()
        viewModel.update { current -> current.copy(customSoundUri = stored) }
    }
    val soundPickerTitle = stringResource(R.string.persistent_reminders_sound)

    LaunchedEffect(viewModel) {
        viewModel.updateFailures.collect {
            Toast.makeText(
                latestContext,
                latestContext.getString(R.string.settings_update_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    PersistentRemindersScreenContent(
        settings = settingsState.persistentReminders,
        remindersEnabled = settingsState.remindersEnabled,
        hasExactAlarmAccess = capabilityState.hasExactAlarmAccess,
        canUseFullScreenIntent = canUseFullScreenIntent,
        groups = activeGroups.map { group -> group.toPlanRow(context) },
        soundLabel = customSoundLabel(settingsState.persistentReminders.customSoundUri),
        onNavigateBack = onNavigateBack,
        onUpdate = viewModel::update,
        onGroupIncludedChange = viewModel::setGroupIncluded,
        onPickSound = {
            val current = settingsState.persistentReminders.customSoundUri?.toUri()
            soundPickerLauncher.launch(
                Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(
                        RingtoneManager.EXTRA_RINGTONE_TYPE,
                        RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM,
                    )
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, soundPickerTitle)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(
                        RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                        Settings.System.DEFAULT_NOTIFICATION_URI,
                    )
                    .putExtra(
                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                        current ?: Settings.System.DEFAULT_NOTIFICATION_URI,
                    )
            )
        },
        onRequestExactAlarmAccess = {
            if (!canScheduleExactAlarms(context)) {
                exactAlarmAccessLauncher.launch(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        "package:${context.packageName}".toUri(),
                    )
                )
            }
        },
        onRequestFullScreenIntentAccess = { openFullScreenIntentSettings(context) },
        onPreviewAlarm = { context.startActivity(DoseAlarmActivity.previewIntent(context)) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersistentRemindersScreenContent(
    settings: PersistentReminderSettings,
    remindersEnabled: Boolean,
    hasExactAlarmAccess: Boolean,
    canUseFullScreenIntent: Boolean,
    groups: List<PlanRow>,
    soundLabel: String,
    onNavigateBack: () -> Unit,
    onUpdate: ((PersistentReminderSettings) -> PersistentReminderSettings) -> Unit,
    onGroupIncludedChange: (UUID, Boolean) -> Unit,
    onPickSound: () -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onRequestFullScreenIntentAccess: () -> Unit,
    onPreviewAlarm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = pinnedTopAppBarScrollBehavior(
        scrollState = scrollState,
        state = topAppBarState,
    )
    val sectionSpacing = dimensionResource(R.dimen.padding_medium)
    var showExactAlarmDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HazeTopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior, scrollState),
                title = { Text(text = stringResource(R.string.settings_persistent_reminders)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->
        AppContentContainer(modifier = Modifier.paddingBehindTopAppBar(innerPadding)) {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(appContentPaddingValuesBehindTopAppBar(innerPadding)),
            ) {
                if (!remindersEnabled) {
                    SupportMessageListItem(
                        text = stringResource(R.string.persistent_reminders_reminders_off),
                        painter = painterResource(R.drawable.ic_error_outline),
                        leadingIconTint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(modifier = Modifier.height(sectionSpacing))
                }

                RepeatSection(
                    settings = settings,
                    showInexactWarning = settings.repeatEnabled && !hasExactAlarmAccess,
                    onUpdate = onUpdate,
                    onInexactWarningClick = { showExactAlarmDialog = true },
                )


                Spacer(modifier = Modifier.height(sectionSpacing))

                FullScreenAlarmSection(
                    settings = settings,
                    canUseFullScreenIntent = canUseFullScreenIntent,
                    onUpdate = onUpdate,
                    onRequestFullScreenIntentAccess = onRequestFullScreenIntentAccess,
                )

                if (settings.fullScreenAlarmEnabled) {
                    HrtFilledTonalButton(
                        text = stringResource(R.string.persistent_reminders_preview),
                        onClick = onPreviewAlarm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        iconPainter = painterResource(R.drawable.ic_alarm_filled),
                    )
                }

                Spacer(modifier = Modifier.height(sectionSpacing))

                HrtSection(title = stringResource(R.string.persistent_reminders_section_sound)) {
                    item {
                        PreferenceSegmentedListItem(
                            title = stringResource(R.string.persistent_reminders_sound),
                            supportingText = soundLabel,
                            onClick = onPickSound,
                            leadingContent = {
                                PreferenceLeadingIcon(painter = null, icon = Icons.Rounded.MusicNote)
                            },
                        )
                    }
                    item {
                        SupportMessageListItem(
                            text = stringResource(R.string.persistent_reminders_sound_summary),
                            painter = painterResource(R.drawable.ic_info),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(sectionSpacing))

                PlansSection(groups = groups, settings = settings, onGroupIncludedChange = onGroupIncludedChange)
                Spacer(modifier = Modifier.height(sectionSpacing))

                HrtSection(title = stringResource(R.string.persistent_reminders_section_snooze)) {
                    item {
                        OptionPreferenceItem(
                            title = stringResource(R.string.persistent_reminders_snooze_duration),
                            selectedLabel = minutesLabel(settings.snoozeDuration.minutes),
                            painter = painterResource(R.drawable.ic_snooze),
                            options = ReminderSnoozeDurationOption.entries.map { option ->
                                OptionMenuEntry(label = minutesLabel(option.minutes)) {
                                    onUpdate { current -> current.copy(snoozeDuration = option) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showExactAlarmDialog) {
        ExactAlarmAccessDialog(
            onConfirm = {
                showExactAlarmDialog = false
                onRequestExactAlarmAccess()
            },
            onDismiss = { showExactAlarmDialog = false },
        )
    }
}

@Composable
private fun RepeatSection(
    settings: PersistentReminderSettings,
    showInexactWarning: Boolean,
    onUpdate: ((PersistentReminderSettings) -> PersistentReminderSettings) -> Unit,
    onInexactWarningClick: () -> Unit,
) {
    val untilLoggedLabel = stringResource(R.string.persistent_reminders_limit_until_logged)
    val untilLoggedSummary = stringResource(R.string.persistent_reminders_limit_until_logged_summary)
    val limitLabels = ReminderRepeatLimitOption.entries.associateWith { option ->
        option.maxRepeats?.let { count ->
            pluralStringResource(R.plurals.persistent_reminders_limit_count, count, count)
        } ?: untilLoggedLabel
    }

    HrtSection(
        title = stringResource(R.string.persistent_reminders_section_repeat),
        topPadding = false,
    ) {
        item {
            SwitchPreferenceItem(
                title = stringResource(R.string.persistent_reminders_repeat),
                supportingText = stringResource(R.string.persistent_reminders_repeat_summary),
                painter = painterResource(R.drawable.ic_repeat),
                checked = settings.repeatEnabled,
                onCheckedChange = { enabled ->
                    onUpdate { current -> current.copy(repeatEnabled = enabled) }
                },
            )
        }

        animatedItem(visible = settings.repeatEnabled) {
            OptionPreferenceItem(
                title = stringResource(R.string.persistent_reminders_interval),
                selectedLabel = minutesLabel(settings.repeatInterval.minutes),
                painter = painterResource(R.drawable.ic_schedule),
                options = ReminderRepeatIntervalOption.entries.map { option ->
                    OptionMenuEntry(label = minutesLabel(option.minutes)) {
                        onUpdate { current -> current.copy(repeatInterval = option) }
                    }
                },
            )
        }

        animatedItem(visible = settings.repeatEnabled) {
            OptionPreferenceItem(
                title = stringResource(R.string.persistent_reminders_limit),
                selectedLabel = limitLabels.getValue(settings.repeatLimit),
                icon = Icons.Rounded.HourglassBottom,
                options = ReminderRepeatLimitOption.entries.map { option ->
                    OptionMenuEntry(
                        label = limitLabels.getValue(option),
                        supportingText = untilLoggedSummary.takeIf { option.maxRepeats == null },
                    ) {
                        onUpdate { current -> current.copy(repeatLimit = option) }
                    }
                },
            )
        }

        animatedItem(visible = showInexactWarning) {
            SupportMessageListItem(
                text = stringResource(R.string.group_notifications_inexact_warning),
                painter = painterResource(R.drawable.ic_error_outline),
                leadingIconTint = MaterialTheme.colorScheme.tertiary,
                onClick = onInexactWarningClick,
                showChevron = true,
            )
        }

        animatedItem(visible = settings.repeatEnabled) {
            SupportMessageListItem(
                text = stringResource(R.string.persistent_reminders_repeat_note),
                painter = painterResource(R.drawable.ic_info),
            )
        }
    }
}

@Composable
private fun FullScreenAlarmSection(
    settings: PersistentReminderSettings,
    canUseFullScreenIntent: Boolean,
    onUpdate: ((PersistentReminderSettings) -> PersistentReminderSettings) -> Unit,
    onRequestFullScreenIntentAccess: () -> Unit,
) {
    HrtSection(title = stringResource(R.string.persistent_reminders_section_alarm)) {
        item {
            SwitchPreferenceItem(
                title = stringResource(R.string.persistent_reminders_full_screen),
                supportingText = stringResource(R.string.persistent_reminders_full_screen_summary),
                icon = Icons.Rounded.ScreenLockPortrait,
                checked = settings.fullScreenAlarmEnabled,
                onCheckedChange = { enabled ->
                    onUpdate { current -> current.copy(fullScreenAlarmEnabled = enabled) }
                },
            )
        }

        animatedItem(visible = settings.fullScreenAlarmEnabled && !canUseFullScreenIntent) {
            SupportMessageListItem(
                text = stringResource(R.string.persistent_reminders_full_screen_permission),
                painter = painterResource(R.drawable.ic_error_outline),
                leadingIconTint = MaterialTheme.colorScheme.tertiary,
                onClick = onRequestFullScreenIntentAccess,
                showChevron = true,
            )
        }

        animatedItem(visible = settings.fullScreenAlarmEnabled) {
            SwitchPreferenceItem(
                title = stringResource(R.string.persistent_reminders_alarm_sound),
                supportingText = stringResource(R.string.persistent_reminders_alarm_sound_summary),
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                checked = settings.alarmSoundEnabled,
                onCheckedChange = { enabled ->
                    onUpdate { current -> current.copy(alarmSoundEnabled = enabled) }
                },
            )
        }

        animatedItem(visible = settings.fullScreenAlarmEnabled) {
            SwitchPreferenceItem(
                title = stringResource(R.string.persistent_reminders_vibration),
                icon = Icons.Rounded.Vibration,
                checked = settings.alarmVibrationEnabled,
                onCheckedChange = { enabled ->
                    onUpdate { current -> current.copy(alarmVibrationEnabled = enabled) }
                },
            )
        }

        animatedItem(visible = settings.fullScreenAlarmEnabled && settings.alarmVibrationEnabled) {
            OptionPreferenceItem(
                title = stringResource(R.string.persistent_reminders_vibration_pattern),
                selectedLabel = stringResource(settings.alarmVibrationPattern.labelRes),
                icon = Icons.Rounded.Waves,
                options = AlarmVibrationPatternOption.entries.map { option ->
                    OptionMenuEntry(label = stringResource(option.labelRes)) {
                        onUpdate { current -> current.copy(alarmVibrationPattern = option) }
                    }
                },
            )
        }
    }
}

/** A plan in the Medications section: its name and what it takes. */
internal data class PlanRow(
    val uuid: UUID,
    val name: String,
    val medications: String,
)

private fun MedicationGroup.toPlanRow(context: Context): PlanRow = PlanRow(
    uuid = uuid,
    name = name,
    medications = medications.joinToString(separator = " · ") { medication ->
        medicationEntryTitle(medication.medicine, medication.applicationType, context)
    },
)

@Composable
private fun PlansSection(
    groups: List<PlanRow>,
    settings: PersistentReminderSettings,
    onGroupIncludedChange: (UUID, Boolean) -> Unit,
) {
    HrtSection(title = stringResource(R.string.persistent_reminders_section_plans)) {
        item {
            SupportMessageListItem(
                text = stringResource(
                    if (groups.isEmpty()) {
                        R.string.persistent_reminders_plans_empty
                    } else {
                        R.string.persistent_reminders_plans_summary
                    }
                ),
                painter = painterResource(R.drawable.ic_info),
            )
        }
        groups.forEach { group ->
            item {
                SwitchPreferenceItem(
                    title = group.name,
                    supportingText = group.medications.ifEmpty { null },
                    painter = painterResource(R.drawable.ic_medication),
                    checked = settings.appliesTo(group.uuid),
                    onCheckedChange = { included -> onGroupIncludedChange(group.uuid, included) },
                )
            }
        }
    }
}

private val AlarmVibrationPatternOption.labelRes: Int
    get() = when (this) {
        AlarmVibrationPatternOption.CONTINUOUS -> R.string.persistent_reminders_vibration_continuous
        AlarmVibrationPatternOption.THREE_PULSES -> R.string.persistent_reminders_vibration_three_pulses
    }

/** The picked sound's title, "Default" when none, or a generic name if it can't be read. */
@Composable
private fun customSoundLabel(soundUri: String?): String {
    val context = LocalContext.current
    val defaultLabel = stringResource(R.string.persistent_reminders_sound_default)
    val unknownLabel = stringResource(R.string.persistent_reminders_sound_unknown)
    return remember(soundUri, defaultLabel, unknownLabel) {
        if (soundUri == null) {
            defaultLabel
        } else {
            runCatching {
                RingtoneManager.getRingtone(context, soundUri.toUri())?.getTitle(context)
            }.getOrNull() ?: unknownLabel
        }
    }
}

private data class OptionMenuEntry(
    val label: String,
    val supportingText: String? = null,
    val onSelect: () -> Unit,
)

/** A row whose value is picked from a dropdown, as the app-lock grace period row is. */
@Composable
private fun OptionPreferenceItem(
    title: String,
    selectedLabel: String,
    options: List<OptionMenuEntry>,
    painter: Painter? = null,
    icon: ImageVector? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PreferenceSegmentedListItem(
            title = title,
            supportingText = selectedLabel,
            onClick = { expanded = true },
            leadingContent = { PreferenceLeadingIcon(painter = painter, icon = icon) },
        )
        HrtDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(IntrinsicSize.Min),
            items = options.map { option ->
                HrtDropdownMenuItem(
                    text = option.label,
                    supportingText = option.supportingText,
                    onClick = option.onSelect,
                )
            },
        )
    }
}

@Composable
private fun SwitchPreferenceItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supportingText: String? = null,
    painter: Painter? = null,
    icon: ImageVector? = null,
) {
    PreferenceSegmentedListItem(
        title = title,
        supportingText = supportingText,
        onClick = { onCheckedChange(!checked) },
        leadingContent = { PreferenceLeadingIcon(painter = painter, icon = icon) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

/** The settings rows' 24dp leading icon, tinted as SettingsScreen tints its own. */
@Composable
private fun PreferenceLeadingIcon(painter: Painter?, icon: ImageVector?) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        icon != null -> Icon(imageVector = icon, contentDescription = null, tint = tint)
        painter != null -> Icon(painter = painter, contentDescription = null, tint = tint)
    }
}

@Composable
private fun minutesLabel(minutes: Long): String =
    pluralStringResource(R.plurals.persistent_reminders_minutes, minutes.toInt(), minutes.toInt())

/** Summary for the Settings row that opens this page. */
@Composable
internal fun persistentRemindersSummary(settings: PersistentReminderSettings): String {
    val parts = buildList {
        if (settings.repeatEnabled) {
            add(
                stringResource(
                    R.string.settings_persistent_reminders_summary_repeat,
                    minutesLabel(settings.repeatInterval.minutes),
                )
            )
        }
        if (settings.fullScreenAlarmEnabled) {
            add(stringResource(R.string.settings_persistent_reminders_summary_alarm))
        }
    }
    return parts.joinToString(separator = " · ")
        .ifEmpty { stringResource(R.string.settings_persistent_reminders_summary_off) }
}

private fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        return
    }
    val intent = Intent(
        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
        "package:${context.packageName}".toUri(),
    )
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Some builds lack the per-app page; the app's details page links to it.
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri(),
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PersistentRemindersScreenPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        PersistentRemindersScreenContent(
            settings = PersistentReminderSettings(
                repeatEnabled = true,
                fullScreenAlarmEnabled = true,
            ),
            remindersEnabled = true,
            hasExactAlarmAccess = true,
            canUseFullScreenIntent = false,
            groups = listOf(
                PlanRow(UUID.randomUUID(), "Morning", "Estradiol valerate · Spironolactone"),
                PlanRow(UUID.randomUUID(), "Injection", "Estradiol enanthate"),
            ),
            soundLabel = "Default",
            onNavigateBack = {},
            onUpdate = {},
            onGroupIncludedChange = { _, _ -> },
            onPickSound = {},
            onRequestExactAlarmAccess = {},
            onRequestFullScreenIntentAccess = {},
            onPreviewAlarm = {},
        )
    }
}
