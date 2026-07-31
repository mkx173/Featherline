package com.mkx.hrttracker.cloudsync

import android.content.Intent

internal class UnavailableCloudDriveGateway : CloudDriveGateway {
    override val isAvailable: Boolean = false

    override suspend fun authorize(): CloudDriveAuthorization = CloudDriveAuthorization.Unavailable
    override fun completeAuthorization(data: Intent?): CloudDriveAuthorization =
        CloudDriveAuthorization.Unavailable

    override suspend fun readRemote(accessToken: String): CloudRemoteSnapshot? = null
    override suspend fun downloadSnapshot(accessToken: String, fileId: String): ByteArray =
        throw UnsupportedOperationException("Google Drive sync is unavailable in this build.")

    override suspend fun uploadRevision(
        accessToken: String,
        currentRemote: CloudRemoteSnapshot?,
        manifest: CloudSyncManifest,
        encryptedSnapshot: ByteArray,
    ): CloudRemoteSnapshot =
        throw UnsupportedOperationException("Google Drive sync is unavailable in this build.")

    override suspend fun disconnect() = Unit
}
