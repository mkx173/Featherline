package com.mkx.hrttracker.widget

import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import com.mkx.hrttracker.model.pk.PkDoseMarker
import com.mkx.hrttracker.model.pk.PkProjectionResult
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

enum class WidgetDoseStatus { DONE, DUE_SOON, OVERDUE, UPCOMING, LOGGED_OUT_OF_WINDOW }

enum class WidgetDoseChip { LAST_NIGHT, COMING_UP }

data class WidgetDoseRow(
    val medicationName: String,
    val groupName: String,
    val colorKey: MedicationGroupColorKey?,
    val routeLabel: String,
    val doseText: String,
    val status: WidgetDoseStatus,
    val scheduledAt: LocalDateTime,
    val trailingText: String?,
    val isManualRecord: Boolean,
    val isImportedRecord: Boolean = false,
    val isFromArchivedGroup: Boolean = false,
    val contextChip: WidgetDoseChip?,
    val groupUuid: String?,
    val scheduleTimeUuid: String?,
    val medicationUuid: String? = null,
    val entryUuid: String? = null,
)

data class WidgetPkDoseMarkerRecord(
    val timeH: Double,
    val concentration: Double,
    val isPlanned: Boolean,
)

data class WidgetPkProjectionRecord(
    val generatedAtEpochMillis: Long,
    val windowStartEpochMillis: Long,
    val windowEndEpochMillis: Long,
    val pkProjectionExpiresAtEpochMillis: Long,
    val concentrationUnit: String,
    val timeH: List<Double>,
    val concentrations: List<Double>,
    val doseMarkers: List<WidgetPkDoseMarkerRecord>,
) {
    fun toPkProjectionResult(
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PkProjectionResult? {
        val nowEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli()
        if (nowEpochMillis >= pkProjectionExpiresAtEpochMillis) return null
        return runCatching {
            PkProjectionResult(
                generatedAt = Instant.ofEpochMilli(generatedAtEpochMillis),
                windowStart = Instant.ofEpochMilli(windowStartEpochMillis),
                windowEnd = Instant.ofEpochMilli(windowEndEpochMillis),
                concentrationUnit = PkConcentrationUnit.valueOf(concentrationUnit),
                timeH = timeH,
                concentrations = concentrations,
                doseMarkers = doseMarkers.map { marker ->
                    PkDoseMarker(
                        timeH = marker.timeH,
                        concentration = marker.concentration,
                        isPlanned = marker.isPlanned,
                    )
                },
            )
        }.getOrNull()
    }
}

data class WidgetSnapshotRecord(
    val schemaVersion: Int,
    val zoneId: String,
    val anchorDateEpochDay: Long,
    val doneCount: Int,
    val totalCount: Int,
    val manualCount: Int,
    val hasActiveGroups: Boolean,
    val hideMedicationDetails: Boolean,
    val adaptiveColorEnabled: Boolean,
    val e2DisplayUnit: String,
    // The app UI language (BCP-47 tag) captured when the snapshot was built. The
    // live-rendered widget chrome (section headers, "done" label, E2 label) resolves
    // its strings against this so it matches the medication/dose strings already baked
    // into the snapshot. Persisted because below API 33 a freshly-spawned widget
    // process can't read the per-app locale back from AppCompatDelegate/the app context.
    val appLanguageTag: String,
    val doseRows: List<WidgetDoseRow>,
    val pkProjection: WidgetPkProjectionRecord?,
    val wearDoseRows: List<WidgetDoseRow> = emptyList(),
    val wearRecentDose: WidgetDoseRow? = null,
    val wearRecentDoseEntryUuids: List<String> = emptyList(),
)
