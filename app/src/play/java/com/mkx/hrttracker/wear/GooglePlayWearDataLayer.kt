package com.mkx.hrttracker.wear

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.mkx.hrttracker.reminder.MedicationReminderSlot
import com.mkx.hrttracker.wear.protocol.WEAR_LOG_DOSE_PATH
import com.mkx.hrttracker.wear.protocol.WEAR_PAYLOAD_KEY
import com.mkx.hrttracker.wear.protocol.WEAR_REQUEST_SNAPSHOT_PATH
import com.mkx.hrttracker.wear.protocol.WEAR_SKIP_DOSE_PATH
import com.mkx.hrttracker.wear.protocol.WEAR_SNAPSHOT_PATH
import com.mkx.hrttracker.wear.protocol.WEAR_UNDO_DOSE_PATH
import com.mkx.hrttracker.wear.protocol.WEAR_UPDATED_AT_KEY
import com.mkx.hrttracker.wear.protocol.WearDoseSnapshot
import com.mkx.hrttracker.wear.protocol.WearProtocolCodec
import com.mkx.hrttracker.widget.WidgetEntryPoint
import com.mkx.hrttracker.widget.WidgetDoseStatus
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class GooglePlayWearSnapshotSink @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : WearSnapshotSink {
    override suspend fun publish(snapshot: WearDoseSnapshot) {
        val request = PutDataMapRequest.create(WEAR_SNAPSHOT_PATH).apply {
            dataMap.putByteArray(WEAR_PAYLOAD_KEY, WearProtocolCodec.encodeSnapshot(snapshot))
            dataMap.putLong(WEAR_UPDATED_AT_KEY, snapshot.generatedAtEpochMillis)
        }.asPutDataRequest().setUrgent()

        suspendCancellableCoroutine { continuation ->
            Wearable.getDataClient(context).putDataItem(request)
                .addOnSuccessListener {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GooglePlayWearModule {
    @Binds
    @IntoSet
    abstract fun bindWearSnapshotSink(
        sink: GooglePlayWearSnapshotSink,
    ): WearSnapshotSink
}

class GooglePlayWearListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WidgetEntryPoint::class.java,
        )
        when (messageEvent.path) {
            WEAR_REQUEST_SNAPSHOT_PATH -> entryPoint.appScope().launch {
                entryPoint.widgetSnapshotRepository().refreshWidgetSnapshot()
            }

            WEAR_LOG_DOSE_PATH, WEAR_SKIP_DOSE_PATH -> {
                val command = runCatching {
                    WearProtocolCodec.decodeLogDoseCommand(messageEvent.data)
                }.getOrNull() ?: return
                val groupUuid = runCatching { UUID.fromString(command.groupUuid) }.getOrNull()
                    ?: return
                val scheduleTimeUuid = command.scheduleTimeUuid?.let {
                    runCatching { UUID.fromString(it) }.getOrNull() ?: return
                }
                val scheduledAt = runCatching {
                    LocalDateTime.parse(command.scheduledAt)
                }.getOrNull() ?: return

                entryPoint.appScope().launch {
                    val currentSnapshot = entryPoint.widgetSnapshotStore().readSnapshot()
                    val currentWearRows = currentSnapshot?.wearDoseRows
                        ?.ifEmpty { currentSnapshot.doseRows }
                        .orEmpty()
                    val stillActionable = currentWearRows.any { row ->
                        row.groupUuid == command.groupUuid &&
                                row.scheduleTimeUuid == command.scheduleTimeUuid &&
                                row.scheduledAt == scheduledAt &&
                                !row.isFromArchivedGroup &&
                                (row.status == WidgetDoseStatus.DUE_SOON ||
                                        row.status == WidgetDoseStatus.OVERDUE ||
                                        row.status == WidgetDoseStatus.UPCOMING)
                    } == true
                    if (!stillActionable) {
                        entryPoint.widgetSnapshotRepository().refreshWidgetSnapshot()
                        return@launch
                    }
                    val slot = MedicationReminderSlot(
                        groupUuid = groupUuid,
                        scheduledAt = scheduledAt,
                        scheduleTimeUuid = scheduleTimeUuid,
                    )
                    if (messageEvent.path == WEAR_SKIP_DOSE_PATH) {
                        entryPoint.medicationSkipActionHandler().skip(slot)
                    } else {
                        entryPoint.medicationReminderActionHandler().logNow(
                            slots = listOf(slot),
                            logTargets = null,
                            notificationTag = null,
                        )
                    }
                    entryPoint.widgetSnapshotRepository().refreshWidgetSnapshot()
                }
            }

            WEAR_UNDO_DOSE_PATH -> {
                val command = runCatching {
                    WearProtocolCodec.decodeUndoDoseCommand(messageEvent.data)
                }.getOrNull() ?: return
                val entryUuids = command.entryUuids
                    .mapNotNull { value -> runCatching { UUID.fromString(value) }.getOrNull() }
                if (entryUuids.size != command.entryUuids.size) return

                entryPoint.appScope().launch {
                    val currentSnapshot = entryPoint.widgetSnapshotStore().readSnapshot()
                    val currentUndoEntries = currentSnapshot
                        ?.wearRecentDoseEntryUuids
                        .orEmpty()
                        .toSet()
                    if (currentUndoEntries.isEmpty() ||
                        currentUndoEntries != command.entryUuids.toSet()
                    ) {
                        entryPoint.widgetSnapshotRepository().refreshWidgetSnapshot()
                        return@launch
                    }
                    entryPoint.medicationLogRepository().deleteEntries(entryUuids)
                    runCatching { entryPoint.medicationReminderScheduler().rescheduleAll() }
                    entryPoint.widgetSnapshotRepository().refreshWidgetSnapshot()
                }
            }
        }
    }
}
