package com.mkx.hrttracker.healthconnect

import java.time.Instant

enum class HealthConnectAvailability {
    AVAILABLE,
    UPDATE_REQUIRED,
    UNAVAILABLE,
}

data class HealthConnectIntegrationState(
    val availability: HealthConnectAvailability = HealthConnectAvailability.UNAVAILABLE,
    val personalHealthRecordAvailable: Boolean = false,
    val weightReadPermissionGranted: Boolean = false,
    val medicalWritePermissionGranted: Boolean = false,
    val weightSyncEnabled: Boolean = false,
    val medicationSyncEnabled: Boolean = false,
    val lastWeightSyncAt: Instant? = null,
    val lastMedicationSyncAt: Instant? = null,
    val lastImportedWeightKg: Double? = null,
    val lastError: HealthConnectError? = null,
) {
    val isAvailable: Boolean
        get() = availability == HealthConnectAvailability.AVAILABLE

    val canReadWeight: Boolean
        get() = isAvailable && weightReadPermissionGranted

    val canWriteMedication: Boolean
        get() = isAvailable &&
            personalHealthRecordAvailable &&
            medicalWritePermissionGranted
}

enum class HealthConnectError {
    PERMISSION_REQUIRED,
    FEATURE_UNAVAILABLE,
    READ_WEIGHT_FAILED,
    WRITE_MEDICATION_FAILED,
}

internal data class HealthConnectStoredState(
    val weightSyncEnabled: Boolean = false,
    val medicationSyncEnabled: Boolean = false,
    val importedWeightFingerprint: String? = null,
    val exportedMedicationFingerprints: Map<String, String> = emptyMap(),
    val medicalDataSourceId: String? = null,
    val lastWeightSyncAt: Instant? = null,
    val lastMedicationSyncAt: Instant? = null,
    val lastImportedWeightKg: Double? = null,
)
