package com.mkx.hrttracker.data.backup

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

object BackupSnapshotJsonCodec {
    private val adapter: JsonAdapter<BackupSnapshot> = Moshi.Builder()
        .build()
        .adapter(BackupSnapshot::class.java)
        .serializeNulls()

    fun encode(snapshot: BackupSnapshot): String {
        return adapter.toJson(snapshot)
    }

    fun decode(json: String): BackupSnapshot? {
        return adapter.fromJson(json)
    }
}
