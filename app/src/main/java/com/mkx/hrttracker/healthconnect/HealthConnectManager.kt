package com.mkx.hrttracker.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class HealthConnectWeight(
    val weightKg: Double,
    val time: Instant,
)

/**
 * The Health Connect calls weight sync needs (read-only: the app never writes to
 * HC); an interface so sync can be tested with a fake.
 */
interface HealthConnectManager {
    fun isAvailable(): Boolean

    /** Android 9–13 without an up-to-date Health Connect app: installing or updating it fixes that. */
    fun isProviderUpdateRequired(): Boolean

    /** READ weight is granted. Only after [isAvailable] has passed. */
    suspend fun hasWeightAccess(): Boolean

    /**
     * READ history can be asked for: without it HC hides records from over 30
     * days before the first grant. Only after [isAvailable] has passed.
     */
    fun supportsReadHistory(): Boolean

    /** The latest weight record measured at or before [notAfter], or null. */
    suspend fun readLatestWeight(notAfter: Instant): HealthConnectWeight?
}

val HEALTH_CONNECT_READ_WEIGHT_PERMISSION: String =
    HealthPermission.getReadPermission(WeightRecord::class)

@Singleton
class AndroidHealthConnectManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : HealthConnectManager {
    // getOrCreate throws when the SDK is unavailable, so it is only reached
    // after isAvailable() has passed.
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    override fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    override fun isProviderUpdateRequired(): Boolean =
        HealthConnectClient.getSdkStatus(context) ==
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    override suspend fun hasWeightAccess(): Boolean =
        HEALTH_CONNECT_READ_WEIGHT_PERMISSION in client.permissionController.getGrantedPermissions()

    override fun supportsReadHistory(): Boolean =
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    override suspend fun readLatestWeight(notAfter: Instant): HealthConnectWeight? {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.before(notAfter),
                ascendingOrder = false,
                pageSize = 1,
            )
        )
        return response.records.firstOrNull()?.let { record ->
            HealthConnectWeight(weightKg = record.weight.inKilograms, time = record.time)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthConnectModule {
    @Binds
    abstract fun bindHealthConnectManager(impl: AndroidHealthConnectManager): HealthConnectManager
}
