package com.mkx.hrttracker.ui.alarm

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.reminder.MedicationReminderBundle
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.HrtOutlinedButton
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.appLanguageLocale
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.doseInstructionText
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import com.mkx.hrttracker.util.medicationEntryTitle
import com.mkx.hrttracker.util.medicationRouteLabel
import com.mkx.hrttracker.util.uses24HourTimeFormat
import java.time.LocalDate
import java.time.LocalDateTime

/** One plan's rows on the alarm: its name and each pending medication, title and detail. */
internal data class DoseAlarmGroup(
    val name: String,
    val rows: List<DoseAlarmRow>,
)

internal data class DoseAlarmRow(
    val title: String,
    val detail: String?,
)

@Composable
internal fun DoseAlarmScreen(
    content: DoseAlarmContent,
    hideMedicationDetails: Boolean,
    canSnooze: Boolean,
    snoozeMinutes: Long,
    canStopRepeats: Boolean,
    onLog: (MedicationReminderBundle?) -> Unit,
    onSnooze: (MedicationReminderBundle?) -> Unit,
    onStop: (MedicationReminderBundle?) -> Unit,
    onSilence: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val context = LocalContext.current
    val bundle = (content as? DoseAlarmContent.Pending)?.bundle
    val scheduledAt = bundle?.scheduledAt ?: remember { LocalDateTime.now().withSecond(0).withNano(0) }
    val groups = when (content) {
        is DoseAlarmContent.Pending -> content.bundle.items.map { item ->
            DoseAlarmGroup(
                name = item.groupName,
                rows = item.medications.map { medication ->
                    DoseAlarmRow(
                        title = medicationEntryTitle(medication.medicine, medication.applicationType, context),
                        detail = listOfNotNull(
                            medicationRouteLabel(medication.medicine, medication.applicationType, context),
                            doseInstructionText(
                                context = context,
                                medicine = medication.medicine,
                                doseInstruction = medication.doseInstruction,
                                count = medication.count,
                            ),
                        ).joinToString(separator = " · ").ifEmpty { null },
                    )
                },
            )
        }

        DoseAlarmContent.Preview -> listOf(
            DoseAlarmGroup(
                name = stringResource(R.string.dose_alarm_preview_group),
                rows = listOf(
                    DoseAlarmRow(
                        title = stringResource(R.string.dose_alarm_preview_medication),
                        detail = stringResource(R.string.dose_alarm_preview_detail),
                    ),
                ),
            ),
        )
    }
    DoseAlarmScreenContent(
        scheduledAt = scheduledAt,
        groups = groups,
        hideMedicationDetails = hideMedicationDetails,
        isPreview = content is DoseAlarmContent.Preview,
        canSnooze = canSnooze,
        snoozeMinutes = snoozeMinutes,
        canStopRepeats = canStopRepeats,
        onLog = { onLog(bundle) },
        onSnooze = { onSnooze(bundle) },
        onStop = { onStop(bundle) },
        onSilence = onSilence,
        onOpenApp = onOpenApp,
    )
}

@Composable
private fun DoseAlarmScreenContent(
    scheduledAt: LocalDateTime,
    groups: List<DoseAlarmGroup>,
    hideMedicationDetails: Boolean,
    isPreview: Boolean,
    canSnooze: Boolean,
    snoozeMinutes: Long,
    canStopRepeats: Boolean,
    onLog: () -> Unit,
    onSnooze: () -> Unit,
    onStop: () -> Unit,
    onSilence: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val context = LocalContext.current
    val locale = appLanguageLocale()
    val timeText = remember(scheduledAt, locale) {
        scheduledAt.format(localizedShortTimeFormatter(locale, context.uses24HourTimeFormat()))
    }
    val dateText = remember(scheduledAt, locale) {
        dateLabelFormatter(locale, LocalDate.now())(scheduledAt.toLocalDate())
    }
    val medicationCount = groups.sumOf { group -> group.rows.size }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                PulsingMedicationBadge()
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = pluralStringResource(
                        R.plurals.dose_alarm_title,
                        medicationCount.coerceAtLeast(1),
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                if (isPreview) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.dose_alarm_preview_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                DoseAlarmDoses(groups = groups, hideMedicationDetails = hideMedicationDetails)
                Spacer(modifier = Modifier.height(24.dp))
            }

            DoseAlarmActions(
                medicationCount = medicationCount,
                canSnooze = canSnooze,
                snoozeMinutes = snoozeMinutes,
                canStopRepeats = canStopRepeats,
                onLog = onLog,
                onSnooze = onSnooze,
                onStop = onStop,
                onSilence = onSilence,
                onOpenApp = onOpenApp,
            )
        }
    }
}

@Composable
private fun DoseAlarmDoses(
    groups: List<DoseAlarmGroup>,
    hideMedicationDetails: Boolean,
) {
    if (hideMedicationDetails) {
        // As the notification does with details hidden: plan names only.
        HrtSection(title = null) {
            groups.forEach { group ->
                item {
                    PreferenceSegmentedListItem(
                        title = group.name,
                        leadingContent = { DoseAlarmRowIcon() },
                    )
                }
            }
        }
        return
    }
    groups.forEachIndexed { index, group ->
        HrtSection(title = group.name, topPadding = index > 0) {
            group.rows.forEach { row ->
                item {
                    PreferenceSegmentedListItem(
                        title = row.title,
                        supportingText = row.detail,
                        leadingContent = { DoseAlarmRowIcon() },
                    )
                }
            }
        }
    }
}

@Composable
private fun DoseAlarmRowIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_medication),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DoseAlarmActions(
    medicationCount: Int,
    canSnooze: Boolean,
    snoozeMinutes: Long,
    canStopRepeats: Boolean,
    onLog: () -> Unit,
    onSnooze: () -> Unit,
    onStop: () -> Unit,
    onSilence: () -> Unit,
    onOpenApp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HoldToConfirmButton(
            text = stringResource(
                if (medicationCount > 1) {
                    R.string.dose_alarm_action_hold_to_log_all
                } else {
                    R.string.dose_alarm_action_hold_to_log
                }
            ),
            onConfirm = onLog,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (canSnooze) {
                HrtFilledTonalButton(
                    text = stringResource(R.string.dose_alarm_action_snooze, snoozeMinutes),
                    onClick = onSnooze,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                )
            }
            HrtOutlinedButton(
                text = stringResource(
                    if (canStopRepeats) {
                        R.string.reminder_notification_action_stop
                    } else {
                        R.string.dose_alarm_action_dismiss
                    }
                ),
                onClick = onStop,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onSilence) {
                Text(text = stringResource(R.string.dose_alarm_action_silence))
            }
            TextButton(onClick = onOpenApp) {
                Text(text = stringResource(R.string.dose_alarm_action_open_app))
            }
        }
    }
}

/**
 * A primary button that confirms only once held for [HOLD_TO_CONFIRM_MS],
 * filling from the start edge as it is held; letting go early does nothing.
 * Logging from the alarm then takes intent, never a stray touch from a pocket
 * or a swipe to unlock. A screen reader's double tap, already deliberate,
 * confirms at once.
 */
@Composable
private fun HoldToConfirmButton(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = 0.55f))
            .drawBehind {
                drawRect(
                    color = colors.primary,
                    size = size.copy(width = size.width * progress.value),
                )
            }
            .semantics {
                role = Role.Button
                onClick(label = text) {
                    currentOnConfirm()
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val fill = scope.launch {
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(HOLD_TO_CONFIRM_MS, easing = LinearEasing),
                            )
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnConfirm()
                        }
                        val completed = tryAwaitRelease() && fill.isCompleted
                        if (!completed) {
                            fill.cancel()
                            scope.launch { progress.animateTo(0f, tween(200)) }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onPrimary,
                maxLines = 1,
            )
        }
    }
}

private const val HOLD_TO_CONFIRM_MS = 700

/** The medication badge, breathing gently so the screen reads as a live alarm. */
@Composable
private fun PulsingMedicationBadge() {
    val transition = rememberInfiniteTransition(label = "doseAlarmPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "doseAlarmPulseScale",
    )
    Box(
        modifier = Modifier
            .size(112.dp)
            .scale(pulse)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_medication),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(56.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DoseAlarmScreenPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        DoseAlarmScreenContent(
            scheduledAt = LocalDateTime.of(2026, 11, 16, 9, 0),
            groups = listOf(
                DoseAlarmGroup(
                    name = "Morning",
                    rows = listOf(
                        DoseAlarmRow(title = "Estradiol valerate", detail = "Oral · 2 mg"),
                        DoseAlarmRow(title = "Spironolactone", detail = "Oral · 50 mg"),
                    ),
                ),
            ),
            hideMedicationDetails = false,
            isPreview = false,
            canSnooze = true,
            snoozeMinutes = 15,
            canStopRepeats = true,
            onLog = {},
            onSnooze = {},
            onStop = {},
            onSilence = {},
            onOpenApp = {},
        )
    }
}
