package com.mkx.hrttracker.data.backup

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

object BackupSnapshotJsonCodec {
    private val moshi: Moshi = Moshi.Builder().build()
    private val adapter: JsonAdapter<BackupSnapshot> = moshi
        .adapter(BackupSnapshot::class.java)
        .serializeNulls()
    private val versionEnvelopeAdapter: JsonAdapter<BackupVersionEnvelope> =
        moshi.adapter(BackupVersionEnvelope::class.java)

    fun encode(snapshot: BackupSnapshot): String {
        return adapter.toJson(snapshot)
    }

    fun decode(json: String): BackupSnapshot? {
        return adapter.fromJson(json)
    }

    // Read just `snapshotVersion` without touching the rest of the payload, so
    // older backups whose shape no longer matches the current `BackupSnapshot`
    // schema still surface a clean unsupported-version error instead of a
    // missing-field Moshi failure.
    fun peekSnapshotVersion(json: String): Int? {
        return versionEnvelopeAdapter.fromJson(json)?.snapshotVersion
    }
}

@JsonClass(generateAdapter = true)
internal data class BackupVersionEnvelope(val snapshotVersion: Int = 0)
