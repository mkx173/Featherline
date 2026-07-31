package com.mkx.hrttracker.cloudsync

import android.content.Intent
import com.mkx.hrttracker.data.backup.BackupExportService
import com.mkx.hrttracker.data.backup.BackupRestoreService
import com.mkx.hrttracker.data.backup.PreparedCloudBackup
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncCoordinator @Inject constructor(
    private val preferences: CloudSyncPreferences,
    private val secretStore: CloudSyncSecretStore,
    private val backupExportService: BackupExportService,
    private val backupRestoreService: BackupRestoreService,
    private val gateway: CloudDriveGateway,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    private val syncMutex = Mutex()

    val isAvailable: Boolean get() = gateway.isAvailable

    suspend fun enable(password: String): CloudSyncResult {
        secretStore.storePassword(password)
        preferences.setEnabled(true)
        return syncNow()
    }

    suspend fun disconnect() {
        try {
            gateway.disconnect()
        } finally {
            secretStore.clear()
            preferences.clearConnection()
        }
    }

    suspend fun setInterval(interval: CloudSyncInterval) {
        preferences.setInterval(interval)
    }

    fun completeAuthorization(data: Intent?): CloudDriveAuthorization =
        gateway.completeAuthorization(data)

    suspend fun syncNow(): CloudSyncResult = syncMutex.withLock {
        val state = preferences.state.first()
        if (!state.enabled) return@withLock CloudSyncResult.Disabled
        if (!gateway.isAvailable) return@withLock CloudSyncResult.Unavailable
        val password = secretStore.loadPassword()
            ?: return@withLock fail(IllegalStateException("Cloud sync password is unavailable."))

        preferences.markSyncing()
        when (val authorization = gateway.authorize()) {
            is CloudDriveAuthorization.Authorized -> {
                performAutomaticSync(
                    accessToken = authorization.accessToken,
                    password = password,
                    previousState = state,
                )
            }
            is CloudDriveAuthorization.RequiresUserAction -> {
                preferences.recordNeedsAuthorization()
                CloudSyncResult.NeedsAuthorization(authorization.pendingIntent)
            }
            CloudDriveAuthorization.Unavailable -> CloudSyncResult.Unavailable
            is CloudDriveAuthorization.Failed -> fail(authorization.error)
        }
    }

    suspend fun resolveConflict(resolution: CloudConflictResolution): CloudSyncResult =
        syncMutex.withLock {
            val state = preferences.state.first()
            if (!state.enabled) return@withLock CloudSyncResult.Disabled
            val password = secretStore.loadPassword()
                ?: return@withLock fail(IllegalStateException("Cloud sync password is unavailable."))
            preferences.markSyncing()
            when (val authorization = gateway.authorize()) {
                is CloudDriveAuthorization.Authorized -> try {
                    val remote = gateway.readRemote(authorization.accessToken)
                    when (resolution) {
                        CloudConflictResolution.KEEP_LOCAL -> uploadLocal(
                            accessToken = authorization.accessToken,
                            password = password,
                            currentRemote = remote,
                        )
                        CloudConflictResolution.USE_CLOUD -> {
                            if (remote == null) {
                                uploadLocal(
                                    accessToken = authorization.accessToken,
                                    password = password,
                                    currentRemote = null,
                                )
                            } else {
                                downloadRemote(
                                    accessToken = authorization.accessToken,
                                    password = password,
                                    remote = remote,
                                )
                            }
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    fail(error)
                }
                is CloudDriveAuthorization.RequiresUserAction -> {
                    preferences.recordNeedsAuthorization()
                    CloudSyncResult.NeedsAuthorization(authorization.pendingIntent)
                }
                CloudDriveAuthorization.Unavailable -> CloudSyncResult.Unavailable
                is CloudDriveAuthorization.Failed -> fail(authorization.error)
            }
        }

    private suspend fun performAutomaticSync(
        accessToken: String,
        password: String,
        previousState: CloudSyncStoredState,
    ): CloudSyncResult {
        var prepared: PreparedCloudBackup? = null
        return try {
            val remote = gateway.readRemote(accessToken)
            prepared = backupExportService.prepareCloudBackup(password)
            when (
                CloudSyncDecisionResolver.resolve(
                    localHash = prepared.contentSha256,
                    remoteHash = remote?.manifest?.contentSha256,
                    lastSyncedHash = previousState.lastSyncedContentHash,
                )
            ) {
                CloudSyncDecision.UPLOAD -> uploadPrepared(accessToken, remote, prepared)
                CloudSyncDecision.DOWNLOAD -> downloadRemote(accessToken, password, checkNotNull(remote))
                CloudSyncDecision.UP_TO_DATE -> {
                    val manifest = checkNotNull(remote).manifest
                    preferences.recordSuccess(
                        syncedAt = Instant.now(),
                        revision = manifest.revision,
                        contentHash = manifest.contentSha256,
                    )
                    CloudSyncResult.UpToDate
                }
                CloudSyncDecision.CONFLICT -> {
                    preferences.recordConflict()
                    CloudSyncResult.Conflict
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            fail(error)
        } finally {
            prepared?.encryptedBytes?.fill(0)
        }
    }

    private suspend fun uploadLocal(
        accessToken: String,
        password: String,
        currentRemote: CloudRemoteSnapshot?,
    ): CloudSyncResult {
        val prepared = backupExportService.prepareCloudBackup(password)
        return try {
            uploadPrepared(accessToken, currentRemote, prepared)
        } finally {
            prepared.encryptedBytes.fill(0)
        }
    }

    private suspend fun uploadPrepared(
        accessToken: String,
        currentRemote: CloudRemoteSnapshot?,
        prepared: PreparedCloudBackup,
    ): CloudSyncResult {
        val now = prepared.exportedAt
        val manifest = CloudSyncManifest(
            revision = UUID.randomUUID().toString(),
            deviceId = preferences.deviceId(),
            snapshotFileId = "",
            contentSha256 = prepared.contentSha256,
            exportedAtEpochMillis = now.toEpochMilli(),
        )
        val uploaded = gateway.uploadRevision(
            accessToken = accessToken,
            currentRemote = currentRemote,
            manifest = manifest,
            encryptedSnapshot = prepared.encryptedBytes,
        )
        preferences.recordSuccess(
            syncedAt = Instant.now(),
            revision = uploaded.manifest.revision,
            contentHash = uploaded.manifest.contentSha256,
        )
        diagnosticsLogger.info(TAG, "cloud_sync_uploaded revision=${uploaded.manifest.revision}")
        return CloudSyncResult.Uploaded
    }

    private suspend fun downloadRemote(
        accessToken: String,
        password: String,
        remote: CloudRemoteSnapshot,
    ): CloudSyncResult {
        require(remote.manifest.formatVersion == CLOUD_SYNC_MANIFEST_VERSION) {
            "Unsupported cloud sync manifest version ${remote.manifest.formatVersion}."
        }
        val encryptedBytes = gateway.downloadSnapshot(
            accessToken = accessToken,
            fileId = remote.manifest.snapshotFileId,
        )
        try {
            backupRestoreService.validateBackupBytes(encryptedBytes)
            backupRestoreService.restoreBackupBytes(encryptedBytes, password)
        } finally {
            encryptedBytes.fill(0)
        }
        preferences.recordSuccess(
            syncedAt = Instant.now(),
            revision = remote.manifest.revision,
            contentHash = remote.manifest.contentSha256,
        )
        diagnosticsLogger.info(TAG, "cloud_sync_downloaded revision=${remote.manifest.revision}")
        return CloudSyncResult.Downloaded
    }

    private suspend fun fail(error: Throwable): CloudSyncResult.Failed {
        preferences.recordFailure(error)
        diagnosticsLogger.warning(TAG, "cloud_sync_failed", error)
        return CloudSyncResult.Failed(
            error = error,
            retryable = error is IOException,
        )
    }

    private companion object {
        const val TAG = "CloudSyncCoordinator"
    }
}
