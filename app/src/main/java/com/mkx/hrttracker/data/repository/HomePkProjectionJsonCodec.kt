package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import com.mkx.hrttracker.model.pk.PkDoseMarker
import com.mkx.hrttracker.model.pk.PkProjectionResult
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.time.Instant

object HomePkProjectionJsonCodec {
    private val adapter: JsonAdapter<HomePkProjectionPayload> = Moshi.Builder()
        .build()
        .adapter(HomePkProjectionPayload::class.java)

    fun encode(projection: PkProjectionResult): String {
        return adapter.toJson(projection.toPayload())
    }

    fun decode(json: String): HomePkProjectionPayload? {
        return adapter.fromJson(json)
    }
}

@JsonClass(generateAdapter = true)
data class HomePkProjectionPayload(
    val concentrationUnit: String,
    val timeH: List<Double>,
    val concentrations: List<Double>,
    val doseMarkers: List<HomePkProjectionDoseMarkerPayload>,
)

@JsonClass(generateAdapter = true)
data class HomePkProjectionDoseMarkerPayload(
    val timeH: Double,
    val concentration: Double,
)

fun HomePkProjectionPayload.toProjection(
    generatedAtEpochMillis: Long,
    windowStartEpochMillis: Long,
    windowEndEpochMillis: Long,
): PkProjectionResult? {
    val unit = runCatching {
        PkConcentrationUnit.valueOf(concentrationUnit)
    }.getOrNull() ?: return null

    return PkProjectionResult(
        generatedAt = Instant.ofEpochMilli(generatedAtEpochMillis),
        windowStart = Instant.ofEpochMilli(windowStartEpochMillis),
        windowEnd = Instant.ofEpochMilli(windowEndEpochMillis),
        concentrationUnit = unit,
        timeH = timeH,
        concentrations = concentrations,
        doseMarkers = doseMarkers.map { marker ->
            PkDoseMarker(
                timeH = marker.timeH,
                concentration = marker.concentration,
            )
        },
    )
}

private fun PkProjectionResult.toPayload(): HomePkProjectionPayload {
    return HomePkProjectionPayload(
        concentrationUnit = concentrationUnit.name,
        timeH = timeH,
        concentrations = concentrations,
        doseMarkers = doseMarkers.map { marker ->
            HomePkProjectionDoseMarkerPayload(
                timeH = marker.timeH,
                concentration = marker.concentration,
            )
        },
    )
}
