package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.MedicationLogDao
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationLogRepository @Inject constructor(
    private val medicationLogDao: MedicationLogDao
) {
    fun observeEntries(): Flow<List<MedicationLogEntry>> {
        return medicationLogDao.observeEntries().map { entries ->
            entries.map { entry ->
                MedicationLogEntry(
                    uuid = UUID.fromString(entry.uuid),
                    routeOfAdministration = RouteOfAdministration.fromStorageValue(entry.routeOfAdministration),
                    medicineName = entry.medicineName,
                    dosageMgAsMedicine = entry.dosageMgAsMedicine,
                    dosageMgAsEstradiol = entry.dosageMgAsEstradiol,
                    appliedAt = Instant.ofEpochMilli(entry.appliedAtEpochMillis)
                )
            }
        }
    }

    suspend fun addEntry(
        routeOfAdministration: RouteOfAdministration,
        medicineName: String,
        dosageMgAsMedicine: Double,
        appliedAt: Instant
    ) {
        medicationLogDao.insertEntry(
            MedicationLogEntryEntity(
                uuid = UUID.randomUUID().toString(),
                routeOfAdministration = routeOfAdministration.name,
                medicineName = medicineName,
                dosageMgAsMedicine = dosageMgAsMedicine,
                dosageMgAsEstradiol = EstradiolEquivalentCalculator.calculate(
                    medicineName = medicineName,
                    dosageMgAsMedicine = dosageMgAsMedicine
                ),
                appliedAtEpochMillis = appliedAt.toEpochMilli()
            )
        )
    }
}

private object EstradiolEquivalentCalculator {
    private const val ESTRADIOL_MOLECULAR_WEIGHT = 272.4
    private const val ESTRADIOL_VALERATE_MOLECULAR_WEIGHT = 356.5
    private const val ESTRADIOL_CYPIONATE_MOLECULAR_WEIGHT = 396.6
    private const val ESTRADIOL_ACETATE_MOLECULAR_WEIGHT = 314.4

    private val equivalenceRatios = mapOf(
        "estradiol" to 1.0,
        "17betaestradiol" to 1.0,
        "betaestradiol" to 1.0,
        "estradiolbase" to 1.0,
        "estradiolvalerate" to ESTRADIOL_MOLECULAR_WEIGHT / ESTRADIOL_VALERATE_MOLECULAR_WEIGHT,
        "ev" to ESTRADIOL_MOLECULAR_WEIGHT / ESTRADIOL_VALERATE_MOLECULAR_WEIGHT,
        "e2v" to ESTRADIOL_MOLECULAR_WEIGHT / ESTRADIOL_VALERATE_MOLECULAR_WEIGHT,
        "estradiolcypionate" to ESTRADIOL_MOLECULAR_WEIGHT / ESTRADIOL_CYPIONATE_MOLECULAR_WEIGHT,
        "ec" to ESTRADIOL_MOLECULAR_WEIGHT / ESTRADIOL_CYPIONATE_MOLECULAR_WEIGHT,
        "e2c" to ESTRADIOL_MOLECULAR_WEIGHT / ESTRADIOL_CYPIONATE_MOLECULAR_WEIGHT,
        "estradiolacetate" to ESTRADIOL_MOLECULAR_WEIGHT / ESTRADIOL_ACETATE_MOLECULAR_WEIGHT,
    )

    fun calculate(medicineName: String, dosageMgAsMedicine: Double): Double? {
        val normalizedName = medicineName.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]"), "")

        val ratio = equivalenceRatios[normalizedName] ?: return null
        return dosageMgAsMedicine * ratio
    }
}
