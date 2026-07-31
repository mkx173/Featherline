package com.mkx.hrttracker.cloudsync

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class GoogleDriveGateway(
    context: Context,
) : CloudDriveGateway {
    private val authorizationClient = Identity.getAuthorizationClient(context.applicationContext)
    private val requestedScopes = listOf(Scope(GOOGLE_DRIVE_APPDATA_SCOPE))
    private val authorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(requestedScopes)
        .build()
    private val manifestAdapter = Moshi.Builder().build().adapter(CloudSyncManifest::class.java)

    override val isAvailable: Boolean = true

    override suspend fun authorize(): CloudDriveAuthorization = try {
        authorizationClient.authorize(authorizationRequest).await().toCloudAuthorization()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        CloudDriveAuthorization.Failed(error)
    }

    override fun completeAuthorization(data: Intent?): CloudDriveAuthorization {
        if (data == null) return CloudDriveAuthorization.Failed(
            IllegalStateException("Google Drive authorization was cancelled.")
        )
        return try {
            authorizationClient.getAuthorizationResultFromIntent(data).toCloudAuthorization()
        } catch (error: Throwable) {
            CloudDriveAuthorization.Failed(error)
        }
    }

    override suspend fun readRemote(accessToken: String): CloudRemoteSnapshot? =
        withTokenRecovery(accessToken) {
            withContext(Dispatchers.IO) {
                val files = listAppDataFiles(accessToken)
                val manifestFile = files
                    .filter { it.name == CLOUD_SYNC_MANIFEST_FILE_NAME }
                    .maxByOrNull { it.modifiedTime }
                    ?: return@withContext null
                val manifestBytes = downloadFile(
                    accessToken = accessToken,
                    fileId = manifestFile.id,
                    maximumBytes = MAX_MANIFEST_BYTES,
                )
                val manifestJson = try {
                    String(manifestBytes, Charsets.UTF_8)
                } finally {
                    manifestBytes.fill(0)
                }
                val manifest = manifestAdapter.fromJson(manifestJson)
                    ?: throw IOException("Google Drive returned an empty cloud sync manifest.")
                if (manifest.formatVersion != CLOUD_SYNC_MANIFEST_VERSION) {
                    throw IOException(
                        "Unsupported cloud sync manifest version ${manifest.formatVersion}."
                    )
                }
                CloudRemoteSnapshot(
                    manifestFileId = manifestFile.id,
                    manifest = manifest,
                )
            }
        }

    override suspend fun downloadSnapshot(
        accessToken: String,
        fileId: String,
    ): ByteArray = withTokenRecovery(accessToken) {
        withContext(Dispatchers.IO) {
            downloadFile(
                accessToken = accessToken,
                fileId = fileId,
                maximumBytes = MAX_SNAPSHOT_BYTES,
            )
        }
    }

    override suspend fun uploadRevision(
        accessToken: String,
        currentRemote: CloudRemoteSnapshot?,
        manifest: CloudSyncManifest,
        encryptedSnapshot: ByteArray,
    ): CloudRemoteSnapshot = withTokenRecovery(accessToken) {
        withContext(Dispatchers.IO) {
            val snapshotName = CLOUD_SYNC_SNAPSHOT_FILE_PREFIX + manifest.revision +
                    CLOUD_SYNC_SNAPSHOT_FILE_SUFFIX
            val snapshotFileId = createAppDataFile(accessToken, snapshotName)
            try {
                uploadMedia(
                    accessToken = accessToken,
                    fileId = snapshotFileId,
                    mimeType = SNAPSHOT_MIME_TYPE,
                    bytes = encryptedSnapshot,
                )
                val completedManifest = manifest.copy(snapshotFileId = snapshotFileId)
                val manifestBytes = checkNotNull(manifestAdapter.toJson(completedManifest))
                    .toByteArray(Charsets.UTF_8)
                val manifestFileId = currentRemote?.manifestFileId
                    ?: createAppDataFile(accessToken, CLOUD_SYNC_MANIFEST_FILE_NAME)
                try {
                    uploadMedia(
                        accessToken = accessToken,
                        fileId = manifestFileId,
                        mimeType = "application/json",
                        bytes = manifestBytes,
                    )
                } finally {
                    manifestBytes.fill(0)
                }
                currentRemote?.manifest?.snapshotFileId
                    ?.takeIf { it != snapshotFileId }
                    ?.let { oldFileId -> runCatching { deleteFile(accessToken, oldFileId) } }
                CloudRemoteSnapshot(
                    manifestFileId = manifestFileId,
                    manifest = completedManifest,
                )
            } catch (error: Throwable) {
                runCatching { deleteFile(accessToken, snapshotFileId) }
                throw error
            }
        }
    }

    override suspend fun disconnect() {
        when (val authorization = authorize()) {
            is CloudDriveAuthorization.Authorized -> {
                val result = authorizationClient.authorize(authorizationRequest).await()
                val account = result.toGoogleSignInAccount()?.account ?: return
                val request = RevokeAccessRequest.builder()
                    .setAccount(account)
                    .setScopes(requestedScopes)
                    .build()
                authorizationClient.revokeAccess(request).await()
            }
            else -> Unit
        }
    }

    private suspend fun <T> withTokenRecovery(
        accessToken: String,
        block: suspend () -> T,
    ): T = try {
        block()
    } catch (error: CloudDriveUnauthorizedException) {
        runCatching {
            authorizationClient.clearToken(
                ClearTokenRequest.builder().setToken(accessToken).build()
            ).await()
        }
        throw error
    }

    private fun listAppDataFiles(accessToken: String): List<DriveFile> {
        val connection = openConnection(
            url = "$DRIVE_API_BASE/files?spaces=appDataFolder&pageSize=100&fields=files(id,name,modifiedTime,size)",
            method = "GET",
            accessToken = accessToken,
        )
        val response = connection.readJsonResponse()
        val files = response.optJSONArray("files") ?: JSONArray()
        return buildList {
            for (index in 0 until files.length()) {
                val file = files.getJSONObject(index)
                add(
                    DriveFile(
                        id = file.getString("id"),
                        name = file.getString("name"),
                        modifiedTime = file.optString("modifiedTime"),
                    )
                )
            }
        }
    }

    private fun createAppDataFile(accessToken: String, name: String): String {
        val connection = openConnection(
            url = "$DRIVE_API_BASE/files?fields=id",
            method = "POST",
            accessToken = accessToken,
            contentType = "application/json; charset=utf-8",
        )
        val body = JSONObject()
            .put("name", name)
            .put("parents", JSONArray().put("appDataFolder"))
            .toString()
            .toByteArray(Charsets.UTF_8)
        connection.outputStream.use { it.write(body) }
        body.fill(0)
        return connection.readJsonResponse().getString("id")
    }

    private fun uploadMedia(
        accessToken: String,
        fileId: String,
        mimeType: String,
        bytes: ByteArray,
    ) {
        val connection = openConnection(
            url = "$DRIVE_UPLOAD_BASE/files/${Uri.encode(fileId)}?uploadType=media",
            method = "PATCH",
            accessToken = accessToken,
            contentType = mimeType,
        )
        connection.setFixedLengthStreamingMode(bytes.size)
        connection.outputStream.use { it.write(bytes) }
        connection.ensureSuccess()
    }

    private fun downloadFile(
        accessToken: String,
        fileId: String,
        maximumBytes: Int,
    ): ByteArray {
        val connection = openConnection(
            url = "$DRIVE_API_BASE/files/${Uri.encode(fileId)}?alt=media",
            method = "GET",
            accessToken = accessToken,
        )
        connection.ensureSuccess()
        val output = ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > maximumBytes) throw IOException("Cloud backup exceeds the size limit.")
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun deleteFile(accessToken: String, fileId: String) {
        openConnection(
            url = "$DRIVE_API_BASE/files/${Uri.encode(fileId)}",
            method = "DELETE",
            accessToken = accessToken,
        ).ensureSuccess()
    }

    private fun openConnection(
        url: String,
        method: String,
        accessToken: String,
        contentType: String? = null,
    ): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = CONNECT_TIMEOUT_MILLIS
        readTimeout = READ_TIMEOUT_MILLIS
        setRequestProperty("Authorization", "Bearer $accessToken")
        setRequestProperty("Accept", "application/json")
        if (contentType != null) {
            doOutput = true
            setRequestProperty("Content-Type", contentType)
        }
    }

    private fun HttpURLConnection.readJsonResponse(): JSONObject {
        ensureSuccess()
        return JSONObject(inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    private fun HttpURLConnection.ensureSuccess() {
        val status = responseCode
        if (status in 200..299) return
        val detail = errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
            throw CloudDriveUnauthorizedException("Google Drive authorization expired.")
        }
        throw IOException("Google Drive request failed ($status): ${detail.take(500)}")
    }

    private fun com.google.android.gms.auth.api.identity.AuthorizationResult.toCloudAuthorization():
            CloudDriveAuthorization {
        if (hasResolution()) {
            return CloudDriveAuthorization.RequiresUserAction(checkNotNull(pendingIntent))
        }
        val token = accessToken
            ?: return CloudDriveAuthorization.Failed(
                IllegalStateException("Google Drive did not return an access token.")
            )
        return CloudDriveAuthorization.Authorized(token)
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value -> continuation.resume(value) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }

    private data class DriveFile(
        val id: String,
        val name: String,
        val modifiedTime: String,
    )

    private companion object {
        const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        const val SNAPSHOT_MIME_TYPE = "application/octet-stream"
        const val CONNECT_TIMEOUT_MILLIS = 20_000
        const val READ_TIMEOUT_MILLIS = 60_000
        const val MAX_MANIFEST_BYTES = 256 * 1024
        const val MAX_SNAPSHOT_BYTES = 128 * 1024 * 1024
    }
}

private class CloudDriveUnauthorizedException(message: String) : IOException(message)
