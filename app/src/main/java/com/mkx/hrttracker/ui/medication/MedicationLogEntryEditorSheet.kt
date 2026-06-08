package com.mkx.hrttracker.ui.medication

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.ui.components.DatePickerModal
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.MedicationCardMissingGroupColorTreatment
import com.mkx.hrttracker.ui.components.MedicationCardWithStockSubcard
import com.mkx.hrttracker.ui.components.TimePickerModal
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.ui.theme.rememberMedicationGroupColorScheme
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.formatEditorZoneLabel
import com.mkx.hrttracker.util.labelRes
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import com.mkx.hrttracker.util.rememberUses24HourTimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs

// ---------------------------------------------------------------------------
// Log entry sheet entry point.
// ---------------------------------------------------------------------------

/**
 * Bottom sheet that edits or creates a history `MedicationLog` entry.
 *
 * Opened from: medication-log-entry quick-log and history-log edit flows
 *   routed through MedicationLogEntryScreen.
 * Hosted by: MedicationLogEntryScreen.
 * Produces: a saved history `MedicationLog`; never creates a catalog [Medicine]
 *   and never returns a regimen [MedicineSlotResult].
 * Identity: locked to [lockedMedicine] when editing/logging against an
 *   existing medicine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationLogEntryEditorSheet(
    modifier: Modifier = Modifier,
    title: String,
    sheetState: SheetState,
    confirmButtonText: String,
    onDismissRequest: () -> Unit,
    onCloseClick: () -> Unit,
    medicineDraft: MedicinePickerUiState,
    doseInstructionDraft: DoseInstructionDraftUiState?,
    lockedMedicine: Medicine?,
    selectedStockProjection: MedicineStockProjection? = null,
    stockMutationPreviewDoseMagnitude: Double? = null,
    previewPostMutationState: ((MedicineStock) -> MedicineStockState?)? = null,
    allowsActualDoseDelta: Boolean = false,
    showActualDoseDeltaReadOnly: Boolean = false,
    doseAmountDelta: Double? = null,
    scheduledDoseAmount: Double? = null,
    plannedDoseAmount: Double? = null,
    effectiveActualAmount: Double? = null,
    sourceGroupName: String? = null,
    sourceGroupColorKey: MedicationGroupColorKey? = null,
    sourceGroupIsArchived: Boolean = false,
    sourceGroupScheduledFor: LocalDateTime? = null,
    sourceGroupScheduleOffsetOutsideFulfillmentWindow: Boolean = false,
    countText: String,
    appliedDate: LocalDate,
    appliedTime: LocalTime,
    appliedZoneId: ZoneId = ZoneId.systemDefault(),
    onAppliedDateChange: (LocalDate) -> Unit,
    onAppliedTimeChange: (LocalTime) -> Unit,
    onDoseAmountDeltaChange: (Double?) -> Unit = { },
    onLiveActualAmountChange: (Double) -> Unit = { },
    onScrollingChange: (Boolean) -> Unit = { },
    isSaving: Boolean = false,
    destructiveButtonText: String? = null,
    onDestructiveAction: (() -> Unit)? = null,
    onConfirm: () -> Unit,
) {
    val appLocale = rememberAppLocale()
    val today = remember { LocalDate.now() }
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }
    val timeFormatter = rememberLocalizedShortTimeFormatter(appLocale)
    val sourceGroupScheduledForText = sourceGroupScheduledFor?.let { scheduledFor ->
        stringResource(
            R.string.medication_editor_original_schedule,
            listOf(
                dateFormatter(scheduledFor.toLocalDate()),
                scheduledFor.toLocalTime().format(timeFormatter),
            ).joinToString(separator = " "),
        )
    }
    val sourceGroupScheduleOffset = sourceGroupScheduledFor?.let { scheduledFor ->
        medicationLogScheduleOffset(
            scheduledFor = scheduledFor,
            appliedAt = LocalDateTime.of(appliedDate, appliedTime),
        )
    }
    val sourceGroupScheduleOffsetText = sourceGroupScheduleOffset?.let { offset ->
        stringResource(offset.labelRes, offset.value)
    }

    MedicationEditorSheetScaffold(
        modifier = modifier,
        title = title,
        sheetState = sheetState,
        confirmButtonText = confirmButtonText,
        onDismissRequest = onDismissRequest,
        onCloseClick = onCloseClick,
        fillAvailableHeight = false,
        isSaving = isSaving,
        destructiveButtonText = destructiveButtonText,
        onDestructiveAction = onDestructiveAction,
        disclaimerKinds = emptyList(),
        onConfirm = onConfirm,
    ) {
        val linkedApplicationType = doseInstructionDraft?.let { draft ->
            lockedMedicine?.let { medicine ->
                resolvedApplicationTypeForDose(medicine.preparation.type, draft)
            }
        } ?: medicineDraft.catalogFilterApplicationType
        MedicationLogEntryLinkedMedicationSummary(
            lockedMedicine = lockedMedicine,
            applicationType = linkedApplicationType,
            doseInstruction = doseInstructionDraft?.toDoseInstructionOrNull(),
            doseAmountDelta = medicationLogEntrySummaryDoseAmountDelta(
                allowsActualDoseDelta = allowsActualDoseDelta,
                showActualDoseDeltaReadOnly = showActualDoseDeltaReadOnly,
                doseAmountDelta = doseAmountDelta,
            ),
            countText = countText,
            sourceGroupName = sourceGroupName,
            sourceGroupColorKey = sourceGroupColorKey,
            sourceGroupIsArchived = sourceGroupIsArchived,
            sourceGroupScheduledForText = sourceGroupScheduledForText,
            sourceGroupScheduleOffsetText = sourceGroupScheduleOffsetText,
            sourceGroupScheduleOffsetOutsideFulfillmentWindow =
                sourceGroupScheduleOffsetOutsideFulfillmentWindow,
            selectedStockProjection = selectedStockProjection,
            stockMutationPreviewDoseMagnitude = stockMutationPreviewDoseMagnitude,
            previewPostMutationState = previewPostMutationState,
        )

        Spacer(modifier = Modifier.height(8.dp))

        ActualAmountRulerCard(
            modifier = Modifier.padding(top = 8.dp),
            preparationType = lockedMedicine?.preparation?.type,
            allowsActualDoseDelta = allowsActualDoseDelta,
            plannedAmount = plannedDoseAmount,
            doseAmountDelta = doseAmountDelta,
            isSaving = isSaving,
            onDoseAmountDeltaChange = onDoseAmountDeltaChange,
            onLiveActualAmountChange = onLiveActualAmountChange,
            onScrollingChange = onScrollingChange,
        )

        ActualAmountReadOnlyCard(
            modifier = Modifier.padding(top = 8.dp),
            preparationType = lockedMedicine?.preparation?.type,
            showActualDoseDeltaReadOnly = showActualDoseDeltaReadOnly,
            scheduledDoseAmount = scheduledDoseAmount,
            doseAmountDelta = doseAmountDelta,
            effectiveActualAmount = effectiveActualAmount,
        )

        val showsActualAmountSection = (allowsActualDoseDelta || showActualDoseDeltaReadOnly) &&
            effectiveActualAmount != null
        if (showsActualAmountSection) {
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))
        }

        MedicationLogAppliedAtFields(
            appliedDate = appliedDate,
            appliedTime = appliedTime,
            appliedDateText = dateFormatter(appliedDate),
            appliedTimeText = appliedTime.format(timeFormatter),
            appliedZoneId = appliedZoneId,
            onAppliedDateChange = { if (!isSaving) onAppliedDateChange(it) },
            onAppliedTimeChange = { if (!isSaving) onAppliedTimeChange(it) },
        )
    }
}

// ---------------------------------------------------------------------------
// Linked (locked) medication summary for group-linked log edits.
// ---------------------------------------------------------------------------

@Composable
internal fun MedicationLogEntryLinkedMedicationSummary(
    lockedMedicine: Medicine?,
    applicationType: MedicationApplicationType,
    doseInstruction: DoseInstruction?,
    doseAmountDelta: Double? = null,
    countText: String,
    sourceGroupName: String?,
    sourceGroupColorKey: MedicationGroupColorKey?,
    sourceGroupIsArchived: Boolean,
    sourceGroupScheduledForText: String?,
    sourceGroupScheduleOffsetText: String?,
    sourceGroupScheduleOffsetOutsideFulfillmentWindow: Boolean,
    selectedStockProjection: MedicineStockProjection? = null,
    stockMutationPreviewDoseMagnitude: Double? = null,
    previewPostMutationState: ((MedicineStock) -> MedicineStockState?)? = null,
) {
    val groupName = sourceGroupName?.takeIf(String::isNotBlank)
    val hasGroupInfo = groupName != null && sourceGroupScheduledForText != null
    val resolvedCount = remember(countText) { parseMedicationCountText(countText) }

    if (hasGroupInfo) {
        MedicationEditorGroupInfoCard(
            groupName = checkNotNull(groupName),
            groupColorKey = sourceGroupColorKey,
            isArchived = sourceGroupIsArchived,
            scheduledForText = checkNotNull(sourceGroupScheduledForText),
            scheduleOffsetText = sourceGroupScheduleOffsetText,
            showScheduleOffsetWarning = sourceGroupScheduleOffsetOutsideFulfillmentWindow,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
    }

    MedicationCardWithStockSubcard(
        medicine = lockedMedicine,
        doseInstruction = doseInstruction
            ?: DoseInstruction.Noop,
        applicationType = applicationType,
        medicationCount = resolvedCount.coerceAtLeast(1),
        groupColorKey = sourceGroupColorKey,
        doseAmountDelta = doseAmountDelta,
        stockProjection = selectedStockProjection.takeIf {
            medicationSummaryShouldShowStockSubcard(
                hasMedicine = lockedMedicine != null,
                hasStockProjection = it != null,
            )
        },
        stockMutationPreviewDoseMagnitude = stockMutationPreviewDoseMagnitude,
        previewPostMutationState = previewPostMutationState,
        missingGroupColorTreatment = linkedMedicationSummaryMissingGroupColorTreatment(
            sourceGroupColorKey = sourceGroupColorKey,
        ),
        // Medicine identity is locked on existing log entries; render the
        // card as a non-clickable summary so it neither ripples nor grays.
        onClick = null,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        index = if (hasGroupInfo) 1 else 0,
        itemCount = if (hasGroupInfo) 2 else 1,
    )
}

internal fun linkedMedicationSummaryMissingGroupColorTreatment(
    sourceGroupColorKey: MedicationGroupColorKey?,
): MedicationCardMissingGroupColorTreatment {
    return if (sourceGroupColorKey == null) {
        MedicationCardMissingGroupColorTreatment.NEUTRAL_GROUP_PALETTE
    } else {
        MedicationCardMissingGroupColorTreatment.PRIMARY_CONTAINER
    }
}

@Composable
internal fun MedicationLogAppliedAtFields(
    appliedDate: LocalDate,
    appliedTime: LocalTime,
    appliedDateText: String,
    appliedTimeText: String,
    appliedZoneId: ZoneId = ZoneId.systemDefault(),
    onAppliedDateChange: (LocalDate) -> Unit,
    onAppliedTimeChange: (LocalTime) -> Unit,
) {
    val uses24HourFormat = rememberUses24HourTimeFormat()
    val focusManager = LocalFocusManager.current
    var showDatePickerModal by remember { mutableStateOf(false) }
    var showTimePickerModal by remember { mutableStateOf(false) }

    if (showDatePickerModal) {
        DatePickerModal(
            onDateSelected = onAppliedDateChange,
            onDismiss = {
                showDatePickerModal = false
                focusManager.clearFocus()
            },
            initialSelectedDate = appliedDate,
        )
    }

    OutlinedTextField(
        value = appliedDateText,
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.field_date_of_application)) },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(appliedDate) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    val upEvent = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    if (upEvent != null) {
                        showDatePickerModal = true
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

    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

    if (showTimePickerModal) {
        TimePickerModal(
            onTimeSelected = { selectedTime ->
                onAppliedTimeChange(selectedTime)
                true
            },
            onDismiss = {
                showTimePickerModal = false
                focusManager.clearFocus()
            },
            initialTime = appliedTime,
            is24Hour = uses24HourFormat,
        )
    }

    OutlinedTextField(
        value = appliedTimeText,
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.field_time_of_application)) },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(appliedTime) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial)
                    val upEvent = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    if (upEvent != null) {
                        showTimePickerModal = true
                    }
                }
            },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_schedule),
                contentDescription = stringResource(R.string.select_time),
            )
        },
        singleLine = true,
    )

    val deviceZone = remember { ZoneId.systemDefault() }
    val zoneLabelLocale = rememberAppLocale()
    val pickerInstant = remember(appliedDate, appliedTime, appliedZoneId) {
        LocalDateTime.of(appliedDate, appliedTime).atZone(appliedZoneId).toInstant()
    }
    val zoneLabel = remember(pickerInstant, appliedZoneId, deviceZone, zoneLabelLocale) {
        formatEditorZoneLabel(appliedZoneId, pickerInstant, deviceZone, zoneLabelLocale)
    }
    if (zoneLabel != null) {
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_xsmall)))
        Text(
            text = zoneLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MedicationEditorGroupInfoCard(
    modifier: Modifier = Modifier,
    groupName: String,
    groupColorKey: MedicationGroupColorKey?,
    isArchived: Boolean = false,
    scheduledForText: String,
    scheduleOffsetText: String? = null,
    showScheduleOffsetWarning: Boolean = false,
) {
    val groupColorScheme = rememberMedicationGroupColorScheme(colorKey = groupColorKey)

    Column {
        EditorSegmentedListItem(
            index = 0,
            count = 2,
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            trailingContent = if (isArchived || scheduleOffsetText != null) {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isArchived) {
                            Icon(
                                painter = painterResource(R.drawable.ic_archive),
                                contentDescription = stringResource(
                                    R.string.archived_group_record_indicator,
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        if (scheduleOffsetText != null) {
                            Text(
                                text = scheduleOffsetText,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.cjkTextOffset(scheduleOffsetText),
                            )
                            if (showScheduleOffsetWarning) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = stringResource(
                                        R.string.medication_editor_schedule_offset_warning,
                                    ),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                null
            },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(IntrinsicSize.Min),
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .padding(vertical = 4.dp)
                        .fillMaxHeight()
                        .background(
                            color = groupColorScheme.primary,
                            shape = RoundedCornerShape(3.dp),
                        ),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = groupName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.cjkTextOffset(groupName),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_calendar_clock),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = scheduledForText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.cjkTextOffset(scheduledForText),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Schedule offset (used by Plan + tests).
// ---------------------------------------------------------------------------

internal fun medicationLogScheduleOffset(
    scheduledFor: LocalDateTime,
    appliedAt: LocalDateTime,
): MedicationLogScheduleOffset? {
    val deltaMinutes = ChronoUnit.MINUTES.between(scheduledFor, appliedAt)
    if (deltaMinutes == 0L) {
        return null
    }

    val isEarly = deltaMinutes < 0
    val absoluteMinutes = kotlin.math.abs(deltaMinutes)
    val value: Long
    @StringRes val labelRes: Int

    when {
        absoluteMinutes >= MINUTES_PER_DAY -> {
            value = absoluteMinutes / MINUTES_PER_DAY
            labelRes = if (isEarly) {
                R.string.medication_editor_schedule_offset_days_earlier
            } else {
                R.string.medication_editor_schedule_offset_days_later
            }
        }

        absoluteMinutes >= MINUTES_PER_HOUR -> {
            value = absoluteMinutes / MINUTES_PER_HOUR
            labelRes = if (isEarly) {
                R.string.medication_editor_schedule_offset_hours_earlier
            } else {
                R.string.medication_editor_schedule_offset_hours_later
            }
        }

        else -> {
            value = absoluteMinutes
            labelRes = if (isEarly) {
                R.string.medication_editor_schedule_offset_minutes_earlier
            } else {
                R.string.medication_editor_schedule_offset_minutes_later
            }
        }
    }

    return MedicationLogScheduleOffset(labelRes = labelRes, value = value)
}

internal data class MedicationLogScheduleOffset(
    @param:StringRes val labelRes: Int,
    val value: Long,
)

private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24L * MINUTES_PER_HOUR

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(
    name = "Medication Log Entry Editor",
    showBackground = true,
    widthDp = 420,
    heightDp = 720,
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationLogEntryEditorSheetPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
        )
        val medicine = previewMedicationEditorMedicine(
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        MedicationLogEntryEditorSheet(
            title = "Add entry",
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            confirmButtonText = "Save",
            onDismissRequest = { },
            onCloseClick = { },
            medicineDraft = draft,
            doseInstructionDraft = draft.toDoseInstructionDraft(),
            lockedMedicine = medicine,
            sourceGroupName = "Nightly estradiol",
            sourceGroupColorKey = MedicationGroupColorKey.INDIGO,
            sourceGroupScheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0),
            countText = "1",
            appliedDate = LocalDate.of(2026, 4, 22),
            appliedTime = LocalTime.of(20, 30),
            onAppliedDateChange = { },
            onAppliedTimeChange = { },
            onConfirm = { },
        )
    }
}

private fun previewMedicationEditorMedicine(
    key: MedicationKey,
    preparation: MedicinePreparation,
): Medicine {
    return Medicine(
        uuid = UUID.nameUUIDFromBytes("preview-medicine-${key.name}".toByteArray()),
        selection = MedicineSelection.Catalog(key),
        category = key.category,
        preparation = preparation,
        displayName = null,
        identityKey = MedicineIdentityKey.catalog(key, preparation),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        archivedAt = null,
        stock = MedicineStock(),
    )
}
