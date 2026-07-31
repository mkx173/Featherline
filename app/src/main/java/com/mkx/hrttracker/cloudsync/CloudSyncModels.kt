package com.mkx.hrttracker.cloudsync

import android.app.PendingIntent
import android.content.Intent
import com.squareup.moshi.JsonClass
import java.time.Instant

enum class CloudSyncInterval(val days: Long) {
    DAILY(1),
    EVERY_THREE_DAYS(3),
    WEEKLY(7),
    MONTHLY(30),
}

enum class CloudSyncStatus {
    DISCONNECTED,
    READY,
    SYNCING,
    NEEDS_AUTHORIZATION,
    CONFLICT,
    ERROR,
}

data class CloudSyncStoredState(
    val enabled: Boolean = false,
    val interval: CloudSyncInterval = CloudSyncInterval.DAILY,
    val status: CloudSyncStatus = CloudSyncStatus.DISCONNECTED,
    val lastSyncAt: Instant? = null,
    val lastSyncedRevision: String? = null,
    val lastSyncedContentHash: String? = null,
    val deviceId: String? = null,
    val lastError: String? = null,
)

@JsonClass(generateAdapter = true)
data class CloudSyncManifest(
    val formatVersion: Int = CLOUD_SYNC_MANIFEST_VERSION,
    val revision: String,
    val deviceId: String,
    val snapshotFileId: String,
    val contentSha256: String,
    val exportedAtEpochMillis: Long,
)

data class CloudRemoteSnapshot(
    val manifestFileId: String,
    val manifest: CloudSyncManifest,
)

sealed interface CloudDriveAuthorization {
    data class Authorized(val accessToken: String) : CloudDriveAuthorization
    data class RequiresUserAction(val pendingIntent: PendingIntent) : CloudDriveAuthorization
    data object Unavailable : CloudDriveAuthorization
    data class Failed(val error: Throwable) : CloudDriveAuthorization
}

interface CloudDriveGateway {
    val isAvailable: Boolean

    suspend fun authorize(): CloudDriveAuthorization
    fun completeAuthorization(data: Intent?): CloudDriveAuthorization
    suspend fun readRemote(accessToken: String): CloudRemoteSnapshot?
    suspend fun downloadSnapshot(accessToken: String, fileId: String): ByteArray
    suspend fun uploadRevision(
        accessToken: String,
        currentRemote: CloudRemoteSnapshot?,
        manifest: CloudSyncManifest,
        encryptedSnapshot: ByteArray,
    ): CloudRemoteSnapshot

    suspend fun disconnect()
}

sealed interface CloudSyncResult {
    data object Disabled : CloudSyncResult
    data object Unavailable : CloudSyncResult
    data object UpToDate : CloudSyncResult
    data object Uploaded : CloudSyncResult
    data object Downloaded : CloudSyncResult
    data object Conflict : CloudSyncResult
    data class NeedsAuthorization(val pendingIntent: PendingIntent) : CloudSyncResult
    data class Failed(val error: Throwable, val retryable: Boolean) : CloudSyncResult
}

enum class CloudConflictResolution {
    KEEP_LOCAL,
    USE_CLOUD,
}

internal enum class CloudSyncDecision {
    UPLOAD,
    DOWNLOAD,
    UP_TO_DATE,
    CONFLICT,
}

internal object CloudSyncDecisionResolver {
    fun resolve(
        localHash: String,
        remoteHash: String?,
        lastSyncedHash: String?,
    ): CloudSyncDecision {
        if (remoteHash == null) return CloudSyncDecision.UPLOAD
        if (remoteHash == localHash) return CloudSyncDecision.UP_TO_DATE
        if (lastSyncedHash == null) return CloudSyncDecision.CONFLICT
        if (remoteHash == lastSyncedHash) return CloudSyncDecision.UPLOAD
        if (localHash == lastSyncedHash) return CloudSyncDecision.DOWNLOAD
        return CloudSyncDecision.CONFLICT
    }
}

internal const val CLOUD_SYNC_MANIFEST_VERSION = 1
internal const val CLOUD_SYNC_MANIFEST_FILE_NAME = "featherline-cloud-manifest.json"
internal const val CLOUD_SYNC_SNAPSHOT_FILE_PREFIX = "featherline-cloud-snapshot-"
internal const val CLOUD_SYNC_SNAPSHOT_FILE_SUFFIX = ".hrtbackup"
internal const val GOOGLE_DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
