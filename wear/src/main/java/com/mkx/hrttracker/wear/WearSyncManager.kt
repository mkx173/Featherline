package com.mkx.hrttracker.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.mkx.hrttracker.wear.protocol.WEAR_LOG_DOSE_PATH
import com.mkx.hrttracker.wear.protocol.WEAR_REQUEST_SNAPSHOT_PATH
import com.mkx.hrttracker.wear.protocol.WEAR_SKIP_DOSE_PATH
import com.mkx.hrttracker.wear.protocol.WEAR_UNDO_DOSE_PATH
import com.mkx.hrttracker.wear.protocol.WearLogDoseCommand
import com.mkx.hrttracker.wear.protocol.WearProtocolCodec
import com.mkx.hrttracker.wear.protocol.WearRecentDose
import com.mkx.hrttracker.wear.protocol.WearUndoDoseCommand
import java.util.UUID

object WearSyncManager {
    fun requestSnapshotIfStale(
        context: Context,
        snapshotGeneratedAtEpochMillis: Long?,
    ) {
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(
            SYNC_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        val now = System.currentTimeMillis()
        if (!shouldRequestWearSnapshot(
                snapshotGeneratedAtEpochMillis = snapshotGeneratedAtEpochMillis,
                lastRequestedAtEpochMillis = preferences.getLong(KEY_LAST_REQUESTED_AT, 0L),
                nowEpochMillis = now,
            )
        ) {
            return
        }
        preferences.edit().putLong(KEY_LAST_REQUESTED_AT, now).apply()
        requestSnapshot(appContext)
    }

    fun requestSnapshot(context: Context) {
        sendToConnectedNodes(
            context = context,
            path = WEAR_REQUEST_SNAPSHOT_PATH,
            payload = byteArrayOf(),
        )
    }

    fun logDose(
        context: Context,
        slot: WearDoseSlot,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val payload = WearProtocolCodec.encodeLogDoseCommand(
            WearLogDoseCommand(
                requestId = UUID.randomUUID().toString(),
                groupUuid = slot.groupUuid,
                scheduleTimeUuid = slot.scheduleTimeUuid,
                scheduledAt = slot.scheduledAt,
            )
        )
        sendDoseAction(context, WEAR_LOG_DOSE_PATH, payload, onComplete)
    }

    fun skipDose(
        context: Context,
        slot: WearDoseSlot,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val payload = WearProtocolCodec.encodeLogDoseCommand(
            WearLogDoseCommand(
                requestId = UUID.randomUUID().toString(),
                groupUuid = slot.groupUuid,
                scheduleTimeUuid = slot.scheduleTimeUuid,
                scheduledAt = slot.scheduledAt,
            )
        )
        sendDoseAction(context, WEAR_SKIP_DOSE_PATH, payload, onComplete)
    }

    fun undoDose(
        context: Context,
        recentDose: WearRecentDose,
        onComplete: (Boolean) -> Unit = {},
    ) {
        if (recentDose.entryUuids.isEmpty()) {
            onComplete(false)
            return
        }
        val payload = WearProtocolCodec.encodeUndoDoseCommand(
            WearUndoDoseCommand(
                requestId = UUID.randomUUID().toString(),
                entryUuids = recentDose.entryUuids,
            )
        )
        sendDoseAction(context, WEAR_UNDO_DOSE_PATH, payload, onComplete)
    }

    private fun sendDoseAction(
        context: Context,
        path: String,
        payload: ByteArray,
        onComplete: (Boolean) -> Unit,
    ) {
        sendToConnectedNodes(context, path, payload, onComplete)
    }

    private fun sendToConnectedNodes(
        context: Context,
        path: String,
        payload: ByteArray,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val appContext = context.applicationContext
        Wearable.getNodeClient(appContext).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    onComplete(false)
                    return@addOnSuccessListener
                }
                var remaining = nodes.size
                var sent = false
                nodes.forEach { node ->
                    Wearable.getMessageClient(appContext)
                        .sendMessage(node.id, path, payload)
                        .addOnSuccessListener {
                            sent = true
                            remaining -= 1
                            if (remaining == 0) onComplete(sent)
                        }
                        .addOnFailureListener {
                            remaining -= 1
                            if (remaining == 0) onComplete(sent)
                        }
                }
            }
            .addOnFailureListener { onComplete(false) }
    }
}

internal fun shouldRequestWearSnapshot(
    snapshotGeneratedAtEpochMillis: Long?,
    lastRequestedAtEpochMillis: Long,
    nowEpochMillis: Long,
): Boolean {
    val snapshotIsFresh = snapshotGeneratedAtEpochMillis != null &&
            nowEpochMillis - snapshotGeneratedAtEpochMillis < SNAPSHOT_MAX_AGE_MILLIS
    if (snapshotIsFresh) return false
    val retryInterval = if (snapshotGeneratedAtEpochMillis == null) {
        EMPTY_SNAPSHOT_RETRY_MILLIS
    } else {
        STALE_SNAPSHOT_RETRY_MILLIS
    }
    return nowEpochMillis - lastRequestedAtEpochMillis >= retryInterval
}

private const val SYNC_PREFERENCES = "featherline_wear_sync"
private const val KEY_LAST_REQUESTED_AT = "last_requested_at"
private const val SNAPSHOT_MAX_AGE_MILLIS = 30 * 60 * 1_000L
private const val STALE_SNAPSHOT_RETRY_MILLIS = 5 * 60 * 1_000L
private const val EMPTY_SNAPSHOT_RETRY_MILLIS = 30 * 1_000L
