package com.mkx.hrttracker.wear

import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import com.mkx.hrttracker.util.calibrationUnitLabel
import com.mkx.hrttracker.util.formatMainE2ConcentrationValue
import com.mkx.hrttracker.wear.protocol.WearEstradiolSnapshot
import com.mkx.hrttracker.wear.protocol.WearDoseRow
import com.mkx.hrttracker.wear.protocol.WearDoseSnapshot
import com.mkx.hrttracker.wear.protocol.WearDoseStatus
import com.mkx.hrttracker.wear.protocol.WearRecentDose
import com.mkx.hrttracker.widget.WidgetPkProjectionRecord
import com.mkx.hrttracker.widget.WidgetDoseStatus
import com.mkx.hrttracker.widget.WidgetSnapshotRecord
import com.mkx.hrttracker.widget.convertWidgetE2Value
import dagger.Module
import dagger.multibindings.Multibinds
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.Instant

interface WearSnapshotSink {
    suspend fun publish(snapshot: WearDoseSnapshot)
}

fun WidgetSnapshotRecord.toWearDoseSnapshot(
    generatedAtEpochMillis: Long = System.currentTimeMillis(),
): WearDoseSnapshot = WearDoseSnapshot(
    generatedAtEpochMillis = generatedAtEpochMillis,
    zoneId = zoneId,
    anchorDateEpochDay = anchorDateEpochDay,
    doneCount = doneCount,
    totalCount = totalCount,
    hideMedicationDetails = hideMedicationDetails,
    appLanguageTag = appLanguageTag,
    rows = wearDoseRows.ifEmpty { doseRows }
        .filter { row -> row.groupUuid != null && !row.isManualRecord }
        .take(MAX_WEAR_ROWS)
        .map { row ->
            WearDoseRow(
                medicationName = if (hideMedicationDetails) "" else row.medicationName,
                groupName = row.groupName,
                routeLabel = if (hideMedicationDetails) "" else row.routeLabel,
                doseText = if (hideMedicationDetails) "" else row.doseText,
                status = when (row.status) {
                    WidgetDoseStatus.DONE -> WearDoseStatus.DONE
                    WidgetDoseStatus.DUE_SOON -> WearDoseStatus.DUE_SOON
                    WidgetDoseStatus.OVERDUE -> WearDoseStatus.OVERDUE
                    WidgetDoseStatus.UPCOMING -> WearDoseStatus.UPCOMING
                    WidgetDoseStatus.LOGGED_OUT_OF_WINDOW ->
                        WearDoseStatus.LOGGED_OUT_OF_WINDOW
                },
                scheduledAt = row.scheduledAt.toString(),
                trailingText = row.trailingText,
                groupUuid = row.groupUuid,
                scheduleTimeUuid = row.scheduleTimeUuid,
            )
        },
    estradiol = pkProjection?.toWearEstradiolSnapshot(
        generatedAtEpochMillis = generatedAtEpochMillis,
        displayUnit = BloodUnitKey.fromStorageValue(e2DisplayUnit) ?: BloodUnitKey.PG_ML,
    ),
    recentDose = wearRecentDose?.let { row ->
        WearRecentDose(
            groupName = row.groupName,
            medicationSummary = if (hideMedicationDetails) {
                ""
            } else {
                listOf(row.medicationName, row.doseText, row.routeLabel)
                    .filter(String::isNotBlank)
                    .joinToString(" · ")
            },
            recordedAt = row.scheduledAt.toString(),
            entryUuids = wearRecentDoseEntryUuids,
        )
    },
)

private fun WidgetPkProjectionRecord.toWearEstradiolSnapshot(
    generatedAtEpochMillis: Long,
    displayUnit: BloodUnitKey,
): WearEstradiolSnapshot? {
    if (timeH.isEmpty() || timeH.size != concentrations.size) return null
    val sourceUnit = runCatching {
        PkConcentrationUnit.valueOf(concentrationUnit)
    }.getOrNull() ?: return null
    val windowStart = Instant.ofEpochMilli(windowStartEpochMillis)
    val now = Instant.ofEpochMilli(generatedAtEpochMillis)
    if (now.isBefore(windowStart) || now.isAfter(Instant.ofEpochMilli(windowEndEpochMillis))) {
        return null
    }

    val samples = (WEAR_E2_SAMPLE_COUNT - 1 downTo 0).map { index ->
        val target = now.minus(Duration.ofMinutes(index * WEAR_E2_SAMPLE_INTERVAL_MINUTES.toLong()))
        val targetHour = Duration.between(windowStart, target).toMillis() / MILLIS_PER_HOUR
        val concentration = concentrationAt(targetHour) ?: return null
        convertWidgetE2Value(
            concentration = concentration,
            concentrationUnit = sourceUnit,
            displayUnit = displayUnit,
        ).coerceAtLeast(0.0)
    }
    val currentValue = samples.last()
    return WearEstradiolSnapshot(
        currentValueText = formatMainE2ConcentrationValue(currentValue, displayUnit),
        unitLabel = calibrationUnitLabel(displayUnit),
        samples = samples,
        sampleIntervalMinutes = WEAR_E2_SAMPLE_INTERVAL_MINUTES,
    )
}

private fun WidgetPkProjectionRecord.concentrationAt(hour: Double): Double? {
    if (!hour.isFinite() || timeH.isEmpty() || timeH.size != concentrations.size) return null
    if (hour <= timeH.first()) return concentrations.first()
    if (hour >= timeH.last()) return concentrations.last()

    var low = 0
    var high = timeH.lastIndex
    while (high - low > 1) {
        val middle = (low + high) / 2
        if (timeH[middle] <= hour) low = middle else high = middle
    }
    val span = timeH[high] - timeH[low]
    if (span <= 0.0) return concentrations[low]
    val ratio = (hour - timeH[low]) / span
    return concentrations[low] + (concentrations[high] - concentrations[low]) * ratio
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WearSnapshotSinkModule {
    @Multibinds
    abstract fun sinks(): Set<WearSnapshotSink>
}

private const val MAX_WEAR_ROWS = 64
private const val WEAR_E2_SAMPLE_INTERVAL_MINUTES = 120
private const val WEAR_E2_SAMPLE_COUNT = 25
private const val MILLIS_PER_HOUR = 3_600_000.0
