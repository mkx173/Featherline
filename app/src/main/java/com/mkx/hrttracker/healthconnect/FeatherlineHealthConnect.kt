package com.mkx.hrttracker.healthconnect

import android.content.Context
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.FhirResource
import androidx.health.connect.client.records.FhirVersion
import androidx.health.connect.client.records.MedicalDataSource
import androidx.health.connect.client.records.MedicalResourceId
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.CreateMedicalDataSourceRequest
import androidx.health.connect.client.request.GetMedicalDataSourcesRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.request.UpsertMedicalResourceRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalPersonalHealthRecordApi::class)
@Singleton
class FeatherlineHealthConnect @Inject internal constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: HealthConnectPreferences,
    private val userProfileRepository: UserProfileRepository,
    @AppScope appScope: CoroutineScope,
    private val diagnosticsLogger: AppDiagnosticsLogger,
) {
    private val capabilities = MutableStateFlow(HealthConnectCapabilities())
    private val weightSyncMutex = Mutex()
    private val medicationSyncMutex = Mutex()
    private val fhirMapper = MedicationStatementFhirMapper(context)

    val state: StateFlow<HealthConnectIntegrationState> = combine(
        preferences.state,
        capabilities,
    ) { stored, capability ->
        HealthConnectIntegrationState(
            availability = capability.availability,
            personalHealthRecordAvailable = capability.personalHealthRecordAvailable,
            weightReadPermissionGranted = capability.grantedPermissions.contains(
                WEIGHT_READ_PERMISSION
            ),
            medicalWritePermissionGranted = capability.grantedPermissions.contains(
                MEDICAL_WRITE_PERMISSION
            ),
            weightSyncEnabled = stored.weightSyncEnabled,
            medicationSyncEnabled = stored.medicationSyncEnabled,
            lastWeightSyncAt = stored.lastWeightSyncAt,
            lastMedicationSyncAt = stored.lastMedicationSyncAt,
            lastImportedWeightKg = stored.lastImportedWeightKg,
            lastError = capability.lastError,
        )
    }.stateIn(
        scope = appScope,
        started = SharingStarted.Eagerly,
        initialValue = HealthConnectIntegrationState(),
    )

    suspend fun refreshCapabilities() {
        val availability = resolveAvailability()
        if (availability != HealthConnectAvailability.AVAILABLE) {
            capabilities.value = HealthConnectCapabilities(availability = availability)
            return
        }

        val client = clientOrNull() ?: run {
            capabilities.value = HealthConnectCapabilities(
                availability = HealthConnectAvailability.UNAVAILABLE
            )
            return
        }
        val granted = runCatching {
            client.permissionController.getGrantedPermissions()
        }.getOrElse { error ->
            rethrowCancellation(error)
            emptySet()
        }
        val phrAvailable = client.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_PERSONAL_HEALTH_RECORD
        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        capabilities.value = HealthConnectCapabilities(
            availability = HealthConnectAvailability.AVAILABLE,
            personalHealthRecordAvailable = phrAvailable,
            grantedPermissions = granted,
        )
    }

    suspend fun setWeightSyncEnabled(enabled: Boolean) {
        preferences.setWeightSyncEnabled(enabled)
        if (enabled) syncLatestWeight()
    }

    suspend fun setMedicationSyncEnabled(
        enabled: Boolean,
        entries: List<MedicationLogEntry> = emptyList(),
    ) {
        preferences.setMedicationSyncEnabled(enabled)
        if (enabled) syncMedicationEntries(entries)
    }

    suspend fun syncEnabled(entries: List<MedicationLogEntry>): Boolean {
        val stored = preferences.state.first()
        if (!stored.weightSyncEnabled && !stored.medicationSyncEnabled) return false
        refreshCapabilities()
        var attempted = false
        var succeeded = true
        if (stored.weightSyncEnabled) {
            attempted = true
            succeeded = syncLatestWeight() && succeeded
        }
        if (stored.medicationSyncEnabled) {
            attempted = true
            succeeded = syncMedicationEntries(entries) && succeeded
        }
        return attempted && succeeded
    }

    suspend fun syncLatestWeight(now: Instant = Instant.now()): Boolean =
        weightSyncMutex.withLock {
            refreshCapabilities()
            val storedState = preferences.state.first()
            val currentState = capabilities.value
            if (!storedState.weightSyncEnabled) return@withLock false
            if (!currentState.grantedPermissions.contains(WEIGHT_READ_PERMISSION)) {
                setError(HealthConnectError.PERMISSION_REQUIRED)
                return@withLock false
            }

            val client = clientOrNull() ?: return@withLock false
            try {
                val latest = client.readRecords(
                    ReadRecordsRequest(
                        recordType = WeightRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(
                            now.minus(WEIGHT_LOOKBACK),
                            now.plus(Duration.ofDays(1)),
                        ),
                        ascendingOrder = false,
                        pageSize = 1,
                    )
                ).records.firstOrNull()
                if (latest == null) {
                    preferences.recordWeightCheck(now)
                    clearError()
                    return@withLock true
                }

                val fingerprint = buildString {
                    append(latest.metadata.id)
                    append('|')
                    append(latest.metadata.lastModifiedTime.toEpochMilli())
                }
                val stored = preferences.state.first()
                if (fingerprint != stored.importedWeightFingerprint) {
                    val kilograms = latest.weight.inKilograms
                    userProfileRepository.setWeight(
                        originalValue = kilograms,
                        originalUnit = WeightUnit.KILOGRAMS,
                        now = latest.time,
                    )
                    preferences.recordWeightImport(
                        fingerprint = fingerprint,
                        weightKg = kilograms,
                        syncedAt = now,
                    )
                } else {
                    preferences.recordWeightCheck(now)
                }
                clearError()
                true
            } catch (error: Exception) {
                rethrowCancellation(error)
                diagnosticsLogger.warning(TAG, "health_connect_weight_read_failed", error)
                setError(HealthConnectError.READ_WEIGHT_FAILED)
                false
            }
        }

    suspend fun syncMedicationEntries(
        entries: List<MedicationLogEntry>,
        now: Instant = Instant.now(),
    ): Boolean = medicationSyncMutex.withLock {
        refreshCapabilities()
        val storedState = preferences.state.first()
        val currentState = capabilities.value
        if (!storedState.medicationSyncEnabled) return@withLock false
        if (!currentState.personalHealthRecordAvailable) {
            setError(HealthConnectError.FEATURE_UNAVAILABLE)
            return@withLock false
        }
        if (!currentState.grantedPermissions.contains(MEDICAL_WRITE_PERMISSION)) {
            setError(HealthConnectError.PERMISSION_REQUIRED)
            return@withLock false
        }

        val client = clientOrNull() ?: return@withLock false
        try {
            val stored = preferences.state.first()
            val dataSource = getOrCreateDataSource(client, stored.medicalDataSourceId)
            val previousFingerprints =
                stored.exportedMedicationFingerprints.takeIf {
                    stored.medicalDataSourceId == dataSource.id
                }.orEmpty()
            if (stored.medicalDataSourceId != dataSource.id) {
                preferences.resetMedicationExportState(dataSource.id)
            }

            val mapped = entries.mapNotNull(fhirMapper::map)
            val currentFingerprints = mapped.associate {
                it.localId to it.fingerprint
            }
            val changed = mapped.filter { statement ->
                previousFingerprints[statement.localId] != statement.fingerprint
            }
            changed.chunked(MEDICAL_WRITE_BATCH_SIZE).forEach { batch ->
                client.upsertMedicalResources(
                    batch.map { statement ->
                        UpsertMedicalResourceRequest(
                            dataSourceId = dataSource.id,
                            fhirVersion = dataSource.fhirVersion,
                            data = statement.json,
                        )
                    }
                )
            }

            val deletedLocalIds = previousFingerprints.keys - currentFingerprints.keys
            deletedLocalIds.chunked(MEDICAL_WRITE_BATCH_SIZE).forEach { batch ->
                client.deleteMedicalResources(
                    batch.map { localId ->
                        MedicalResourceId(
                            dataSourceId = dataSource.id,
                            fhirResourceType =
                                FhirResource.FHIR_RESOURCE_TYPE_MEDICATION_STATEMENT,
                            fhirResourceId = "featherline-${localId.lowercase()}",
                        )
                    }
                )
            }
            preferences.recordMedicationSync(
                dataSourceId = dataSource.id,
                fingerprints = currentFingerprints,
                syncedAt = now,
            )
            clearError()
            true
        } catch (error: Exception) {
            rethrowCancellation(error)
            diagnosticsLogger.warning(TAG, "health_connect_medication_write_failed", error)
            setError(HealthConnectError.WRITE_MEDICATION_FAILED)
            false
        }
    }

    private suspend fun getOrCreateDataSource(
        client: HealthConnectClient,
        storedId: String?,
    ): MedicalDataSource {
        storedId?.let { id ->
            client.getMedicalDataSources(listOf(id)).firstOrNull()?.let { return it }
        }
        client.getMedicalDataSources(
            GetMedicalDataSourcesRequest(listOf(context.packageName))
        ).firstOrNull { source ->
            source.fhirBaseUri.toString() == MedicationStatementFhirMapper.FHIR_BASE_URI
        }?.let { return it }

        return client.createMedicalDataSource(
            CreateMedicalDataSourceRequest(
                fhirBaseUri = Uri.parse(MedicationStatementFhirMapper.FHIR_BASE_URI),
                displayName = DATA_SOURCE_DISPLAY_NAME,
                // FhirVersion itself is feature-gated by Health Connect. This method is
                // reached only after refreshCapabilities confirmed PHR support, so create
                // it lazily here instead of during class initialization.
                fhirVersion = FhirVersion(4, 0, 1),
            )
        )
    }

    private fun resolveAvailability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.UPDATE_REQUIRED

            else -> HealthConnectAvailability.UNAVAILABLE
        }

    private fun clientOrNull(): HealthConnectClient? =
        if (resolveAvailability() == HealthConnectAvailability.AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }

    private fun setError(error: HealthConnectError) {
        capabilities.value = capabilities.value.copy(lastError = error)
    }

    private fun clearError() {
        capabilities.value = capabilities.value.copy(lastError = null)
    }

    companion object {
        val WEIGHT_READ_PERMISSION: String =
            HealthPermission.getReadPermission(WeightRecord::class)
        val MEDICAL_WRITE_PERMISSION: String = HealthPermission.PERMISSION_WRITE_MEDICAL_DATA
        val ALL_PERMISSIONS: Set<String> =
            setOf(WEIGHT_READ_PERMISSION, MEDICAL_WRITE_PERMISSION)

        private val WEIGHT_LOOKBACK: Duration = Duration.ofDays(30)
        private const val DATA_SOURCE_DISPLAY_NAME = "Featherline medication history"
        private const val MEDICAL_WRITE_BATCH_SIZE = 100
        private const val TAG = "FeatherlineHealthConnect"
    }
}

private data class HealthConnectCapabilities(
    val availability: HealthConnectAvailability = HealthConnectAvailability.UNAVAILABLE,
    val personalHealthRecordAvailable: Boolean = false,
    val grantedPermissions: Set<String> = emptySet(),
    val lastError: HealthConnectError? = null,
)

private fun rethrowCancellation(error: Throwable) {
    if (error is CancellationException) throw error
}
