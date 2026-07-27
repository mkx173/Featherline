package com.mkx.hrttracker.wear

import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.mkx.hrttracker.wear.protocol.WEAR_PAYLOAD_KEY
import com.mkx.hrttracker.wear.protocol.WEAR_SNAPSHOT_PATH
import com.mkx.hrttracker.wear.protocol.WearProtocolCodec

class WearDoseListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents
            .filter { event ->
                event.type == DataEvent.TYPE_CHANGED &&
                        event.dataItem.uri.path == WEAR_SNAPSHOT_PATH
            }
            .forEach { event ->
                val payload = DataMapItem.fromDataItem(event.dataItem)
                    .dataMap
                    .getByteArray(WEAR_PAYLOAD_KEY)
                    ?: return@forEach
                val snapshot = runCatching {
                    WearProtocolCodec.decodeSnapshot(payload)
                }.getOrNull() ?: return@forEach
                WearSnapshotStore.write(applicationContext, snapshot)
            }

        TileService.getUpdater(applicationContext)
            .requestUpdate(FeatherlineTileService::class.java)
    }
}
