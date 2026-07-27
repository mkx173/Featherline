package com.mkx.hrttracker.wear

import android.content.Context
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.StateBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.expression.dynamicDataMapOf
import androidx.wear.protolayout.expression.mapTo
import androidx.wear.protolayout.expression.stringAppDataKey
import androidx.wear.protolayout.material3.Typography.BODY_MEDIUM
import androidx.wear.protolayout.material3.Typography.TITLE_SMALL
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.loadAction
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures

private val SELECTED_SLOT_KEY = stringAppDataKey("selected_slot")
private val SELECTED_ACTION_KEY = stringAppDataKey("selected_action")

class FeatherlineTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) =
        Futures.immediateFuture(buildTile(requestParams))

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest) =
        Futures.immediateFuture(
            Resources.Builder()
                .setVersion(TILE_RESOURCE_VERSION)
                .build()
        )

    private fun buildTile(requestParams: RequestBuilders.TileRequest): Tile {
        val snapshot = WearSnapshotStore.read(this)
        WearSyncManager.requestSnapshotIfStale(
            context = this,
            snapshotGeneratedAtEpochMillis = snapshot?.generatedAtEpochMillis,
        )

        val selectedToken = requestParams.currentState.stateMap[SELECTED_SLOT_KEY]
        val selectedAction = requestParams.currentState.stateMap[SELECTED_ACTION_KEY]
            ?.let(TileDoseAction::fromStorageValue)
        val selectedSlot = selectedToken
            ?.let { token -> snapshot?.findSlot(token) }
            ?.takeIf(WearDoseSlot::isQuickLoggable)
        if (selectedSlot != null && selectedAction != null) {
            when (selectedAction) {
                TileDoseAction.LOG -> WearSyncManager.logDose(this, selectedSlot)
                TileDoseAction.SKIP -> WearSyncManager.skipDose(this, selectedSlot)
            }
            markTileAction(this, selectedSlot, selectedAction)
        }

        val now = System.currentTimeMillis()
        val feedback = recentTileAction(
            context = this,
            nowEpochMillis = now,
            snapshotGeneratedAtEpochMillis = snapshot?.generatedAtEpochMillis,
        )
        val excludedTokens = feedback
            ?.takeIf { now - it.sentAtEpochMillis <= LOCAL_ACTION_RETENTION_MILLIS }
            ?.let { setOf(it.actionToken) }
            .orEmpty()
        val feedbackEndsAt = feedback?.sentAtEpochMillis?.plus(ACTION_FEEDBACK_MILLIS)
        val feedbackSlot = feedback
            ?.takeIf { feedbackEndsAt != null && now < feedbackEndsAt }
            ?.let { action -> snapshot?.findSlot(action.actionToken) ?: action.slot }
        val nextSlot = snapshot?.nextActionableSlot(excludedTokens)

        val timeline = if (feedbackSlot != null && feedbackEndsAt != null) {
            Timeline.Builder()
                .addTimelineEntry(
                    timelineEntry(
                        layout = buildLayout(
                            requestParams = requestParams,
                            snapshot = snapshot,
                            slot = feedbackSlot,
                            feedbackToken = feedbackSlot.actionToken,
                        ),
                        startMillis = 0L,
                        endMillis = feedbackEndsAt,
                    )
                )
                .addTimelineEntry(
                    timelineEntry(
                        layout = buildLayout(
                            requestParams = requestParams,
                            snapshot = snapshot,
                            slot = nextSlot,
                            feedbackToken = null,
                        ),
                        startMillis = feedbackEndsAt,
                        endMillis = Long.MAX_VALUE,
                    )
                )
                .build()
        } else {
            Timeline.fromLayoutElement(
                buildLayout(
                    requestParams = requestParams,
                    snapshot = snapshot,
                    slot = nextSlot,
                    feedbackToken = null,
                )
            )
        }

        return Tile.Builder()
            .setResourcesVersion(TILE_RESOURCE_VERSION)
            .setFreshnessIntervalMillis(TILE_FRESHNESS_MILLIS)
            .setTileTimeline(timeline)
            .setState(StateBuilders.State.Builder().build())
            .build()
    }

    private fun buildLayout(
        requestParams: RequestBuilders.TileRequest,
        snapshot: com.mkx.hrttracker.wear.protocol.WearDoseSnapshot?,
        slot: WearDoseSlot?,
        feedbackToken: String?,
    ): LayoutElementBuilders.LayoutElement =
        materialScope(this, requestParams.deviceConfiguration) {
            primaryLayout(
                titleSlot = {
                    text(
                        (
                                snapshot?.estradiol?.let { estradiol ->
                                    "E2 ~${estradiol.currentValueText} ${estradiol.unitLabel}"
                                } ?: snapshot?.let {
                                    getString(
                                        R.string.wear_done_count,
                                        it.doneCount,
                                        it.totalCount,
                                    )
                                } ?: getString(R.string.app_name)
                                ).layoutString,
                        typography = TITLE_SMALL,
                    )
                },
                mainSlot = {
                    val column = LayoutElementBuilders.Column.Builder()
                        .setWidth(DimensionBuilders.expand())
                        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

                    if (slot == null) {
                        column.addContent(
                            text(
                                getString(
                                    if (snapshot == null) {
                                        R.string.wear_connect_phone
                                    } else {
                                        R.string.wear_all_done
                                    }
                                ).layoutString,
                                typography = BODY_MEDIUM,
                                maxLines = 3,
                            )
                        )
                    } else {
                        column.addContent(
                            text(
                                slot.groupName.layoutString,
                                typography = TITLE_SMALL,
                                maxLines = 1,
                            )
                        )
                        column.addContent(
                            text(
                                (
                                        "${slot.scheduledDateTimeText(snapshot?.anchorDateEpochDay ?: 0L)} · " +
                                                slot.medicationSummary
                                        ).layoutString,
                                typography = BODY_MEDIUM,
                                maxLines = 2,
                            )
                        )
                        column.addContent(
                            LayoutElementBuilders.Spacer.Builder()
                                .setHeight(DimensionBuilders.dp(8f))
                                .build()
                        )
                        if (feedbackToken == slot.actionToken) {
                            column.addContent(
                                text(
                                    getString(R.string.wear_sent).layoutString,
                                    typography = BODY_MEDIUM,
                                    maxLines = 1,
                                )
                            )
                        } else if (slot.isQuickLoggable()) {
                            column.addContent(tileActionRow(slot))
                        }
                    }
                    column.build()
                },
            )
        }

    private fun MaterialScope.tileActionRow(slot: WearDoseSlot): LayoutElementBuilders.Row =
        LayoutElementBuilders.Row.Builder()
            .setWidth(DimensionBuilders.dp(162f))
            .addContent(tileActionButton(slot, TileDoseAction.LOG, R.string.wear_log))
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setWidth(DimensionBuilders.dp(6f))
                    .build()
            )
            .addContent(tileActionButton(slot, TileDoseAction.SKIP, R.string.wear_skip))
            .build()

    private fun MaterialScope.tileActionButton(
        slot: WearDoseSlot,
        action: TileDoseAction,
        labelRes: Int,
    ): LayoutElementBuilders.LayoutElement =
        textButton(
            onClick = clickable(
                action = loadAction(
                    dynamicDataMapOf(
                        SELECTED_SLOT_KEY mapTo slot.actionToken,
                        SELECTED_ACTION_KEY mapTo action.storageValue,
                    )
                )
            ),
            width = DimensionBuilders.dp(78f),
            height = DimensionBuilders.dp(44f),
            labelContent = {
                text(
                    getString(labelRes).layoutString,
                    typography = BODY_MEDIUM,
                    maxLines = 1,
                )
            },
        )
}

private fun timelineEntry(
    layout: LayoutElementBuilders.LayoutElement,
    startMillis: Long,
    endMillis: Long,
): TimelineBuilders.TimelineEntry =
    TimelineBuilders.TimelineEntry.Builder()
        .setValidity(
            TimelineBuilders.TimeInterval.Builder()
                .setStartMillis(startMillis)
                .setEndMillis(endMillis)
                .build()
        )
        .setLayout(LayoutElementBuilders.Layout.fromLayoutElement(layout))
        .build()

private enum class TileDoseAction(val storageValue: String) {
    LOG("log"),
    SKIP("skip");

    companion object {
        fun fromStorageValue(value: String): TileDoseAction? =
            entries.firstOrNull { it.storageValue == value }
    }
}

private data class TileActionFeedback(
    val slot: WearDoseSlot,
    val action: TileDoseAction,
    val sentAtEpochMillis: Long,
) {
    val actionToken: String
        get() = slot.actionToken
}

private fun markTileAction(
    context: Context,
    slot: WearDoseSlot,
    action: TileDoseAction,
) {
    context.getSharedPreferences(TILE_PREFERENCES, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_SENT_TOKEN, slot.actionToken)
        .putString(KEY_SENT_ACTION, action.storageValue)
        .putString(KEY_SENT_GROUP_UUID, slot.groupUuid)
        .putString(KEY_SENT_SCHEDULE_TIME_UUID, slot.scheduleTimeUuid)
        .putString(KEY_SENT_SCHEDULED_AT, slot.scheduledAt)
        .putString(KEY_SENT_GROUP_NAME, slot.groupName)
        .putString(KEY_SENT_MEDICATION_SUMMARY, slot.medicationSummary)
        .putInt(KEY_SENT_STATUS, slot.status.ordinal)
        .putLong(KEY_SENT_AT, System.currentTimeMillis())
        .apply()
}

private fun recentTileAction(
    context: Context,
    nowEpochMillis: Long,
    snapshotGeneratedAtEpochMillis: Long?,
): TileActionFeedback? {
    val preferences = context.getSharedPreferences(TILE_PREFERENCES, Context.MODE_PRIVATE)
    val sentAt = preferences.getLong(KEY_SENT_AT, 0L)
    if (nowEpochMillis - sentAt > LOCAL_ACTION_RETENTION_MILLIS) return null
    if (snapshotGeneratedAtEpochMillis != null &&
        snapshotGeneratedAtEpochMillis >= sentAt &&
        nowEpochMillis - sentAt >= ACTION_FEEDBACK_MILLIS
    ) {
        preferences.edit().clear().apply()
        return null
    }
    val token = preferences.getString(KEY_SENT_TOKEN, null) ?: return null
    val action = preferences.getString(KEY_SENT_ACTION, null)
        ?.let(TileDoseAction::fromStorageValue)
        ?: return null
    val status = preferences.getInt(KEY_SENT_STATUS, -1)
        .takeIf { it in com.mkx.hrttracker.wear.protocol.WearDoseStatus.entries.indices }
        ?.let(com.mkx.hrttracker.wear.protocol.WearDoseStatus.entries::get)
        ?: return null
    val slot = WearDoseSlot(
        groupUuid = preferences.getString(KEY_SENT_GROUP_UUID, null) ?: return null,
        scheduleTimeUuid = preferences.getString(KEY_SENT_SCHEDULE_TIME_UUID, null),
        scheduledAt = preferences.getString(KEY_SENT_SCHEDULED_AT, null) ?: return null,
        groupName = preferences.getString(KEY_SENT_GROUP_NAME, null) ?: return null,
        medicationSummary =
            preferences.getString(KEY_SENT_MEDICATION_SUMMARY, null) ?: return null,
        status = status,
    )
    if (slot.actionToken != token) return null
    return TileActionFeedback(slot, action, sentAt)
}

private const val TILE_RESOURCE_VERSION = "3"
private const val TILE_PREFERENCES = "featherline_tile"
private const val KEY_SENT_TOKEN = "sent_token"
private const val KEY_SENT_ACTION = "sent_action"
private const val KEY_SENT_GROUP_UUID = "sent_group_uuid"
private const val KEY_SENT_SCHEDULE_TIME_UUID = "sent_schedule_time_uuid"
private const val KEY_SENT_SCHEDULED_AT = "sent_scheduled_at"
private const val KEY_SENT_GROUP_NAME = "sent_group_name"
private const val KEY_SENT_MEDICATION_SUMMARY = "sent_medication_summary"
private const val KEY_SENT_STATUS = "sent_status"
private const val KEY_SENT_AT = "sent_at"
private const val ACTION_FEEDBACK_MILLIS = 1_500L
private const val LOCAL_ACTION_RETENTION_MILLIS = 15 * 60 * 1_000L
private const val TILE_FRESHNESS_MILLIS = 30 * 60 * 1_000L
