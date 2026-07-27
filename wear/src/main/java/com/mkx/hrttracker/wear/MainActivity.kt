package com.mkx.hrttracker.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mkx.hrttracker.wear.protocol.WearDoseStatus
import com.mkx.hrttracker.wear.protocol.WearEstradiolSnapshot
import com.mkx.hrttracker.wear.protocol.WearRecentDose
import kotlinx.coroutines.delay
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = featherlineWearColorScheme) {
                FeatherlineWearScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Opening the app is an explicit foreground action, so ask for the phone's
        // authoritative state even when the cached snapshot is younger than the
        // background staleness window. This keeps cross-day plans and phone-side
        // deletions current without adding a periodic battery cost.
        WearSyncManager.requestSnapshot(this)
    }
}

@Composable
private fun FeatherlineWearScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val snapshot by remember(context) {
        WearSnapshotStore.observe(context)
    }.collectAsState(initial = WearSnapshotStore.read(context))
    var feedback by remember { mutableStateOf<WearFeedback?>(null) }
    var feedbackSlot by remember { mutableStateOf<WearDoseSlot?>(null) }
    var locallyHandledTokens by remember { mutableStateOf(emptySet<String>()) }
    var undoFeedback by remember { mutableStateOf<WearUndoFeedback?>(null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        val current = snapshot
        if (current == null) {
            EmptyWearState()
            return@Surface
        }

        LaunchedEffect(current.generatedAtEpochMillis) {
            // A phone-built snapshot is authoritative. In particular, if a record was
            // deleted on the phone, its restored plan slot must not remain hidden by a
            // token that this process optimistically handled earlier.
            locallyHandledTokens = emptySet()
            if (feedbackSlot != null) {
                // Whether the phone accepted the action or returned an unchanged plan,
                // this newer snapshot is the source of truth. An unchanged slot remains
                // retryable instead of being hidden by a delivered-but-unprocessed message.
                feedbackSlot = null
                feedback = null
            }
            if (undoFeedback is WearUndoFeedback.Sent) {
                undoFeedback = null
            }
        }

        val basePlanSlots = current.nextPlanSlots(
            limit = 5,
            excludedActionTokens = locallyHandledTokens,
        )
        val planSlots = feedbackSlot
            ?.takeIf { feedback is WearFeedback.Sending || feedback is WearFeedback.Sent }
            ?.let { pending ->
                listOf(pending) +
                        basePlanSlots.filter { it.actionToken != pending.actionToken }.take(4)
            }
            ?: basePlanSlots
        val sendAction: (WearDoseSlot, WearAction) -> Unit = { slot, action ->
            feedbackSlot = slot
            feedback = WearFeedback.Sending(slot.actionToken)
            val onComplete: (Boolean) -> Unit = { sent ->
                feedback = if (sent) {
                    WearFeedback.Sent(slot.actionToken)
                } else {
                    WearFeedback.Failed(slot.actionToken)
                }
            }
            when (action) {
                WearAction.LOG -> WearSyncManager.logDose(context, slot, onComplete)
                WearAction.SKIP -> WearSyncManager.skipDose(context, slot, onComplete)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.app_name),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        R.string.wear_done_count,
                        current.doneCount,
                        current.totalCount,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 6.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }

            current.estradiol?.let { estradiol ->
                item {
                    WearEstradiolCard(estradiol)
                }
            }

            current.recentDose?.let { recentDose ->
                item {
                    WearSectionTitle(stringResource(R.string.wear_last_record))
                }
                item {
                    WearRecentDoseCard(
                        recentDose = recentDose,
                        undoFeedback = undoFeedback,
                        onUndo = {
                            undoFeedback = WearUndoFeedback.Sending
                            WearSyncManager.undoDose(context, recentDose) { sent ->
                                undoFeedback = if (sent) {
                                    WearUndoFeedback.Sent
                                } else {
                                    WearUndoFeedback.Failed
                                }
                                if (sent) WearSyncManager.requestSnapshot(context)
                            }
                        },
                    )
                }
            }

            item {
                WearSectionTitle(stringResource(R.string.wear_next_five))
            }

            if (planSlots.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.wear_all_done),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                itemsIndexed(planSlots, key = { _, slot -> slot.actionToken }) { index, slot ->
                    if (index == 0) {
                        WearDoseCard(
                            slot = slot,
                            anchorDateEpochDay = current.anchorDateEpochDay,
                            feedback = feedback,
                            onLog = { sendAction(slot, WearAction.LOG) },
                            onSkip = { sendAction(slot, WearAction.SKIP) },
                        )
                    } else {
                        WearPlanRow(
                            slot = slot,
                            anchorDateEpochDay = current.anchorDateEpochDay,
                        )
                    }
                }
            }

            if (current.estradiol != null) {
                item {
                    Text(
                        text = stringResource(R.string.wear_estimate_disclaimer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                    )
                }
            }
        }
    }

    LaunchedEffect(feedback) {
        val sentFeedback = feedback as? WearFeedback.Sent
        if (sentFeedback != null) {
            delay(ACTION_FEEDBACK_MILLIS)
            locallyHandledTokens = locallyHandledTokens + sentFeedback.actionToken
            WearSyncManager.requestSnapshot(context)
            feedbackSlot = null
            feedback = null
        } else if (feedback is WearFeedback.Failed) {
            delay(2_000)
            feedbackSlot = null
            feedback = null
        }
    }

    LaunchedEffect(undoFeedback) {
        when (undoFeedback) {
            WearUndoFeedback.Sent -> {
                delay(ACTION_FEEDBACK_MILLIS)
                WearSyncManager.requestSnapshot(context)
            }
            WearUndoFeedback.Failed -> {
                delay(2_000)
                undoFeedback = null
            }
            else -> Unit
        }
    }
}

@Composable
private fun EmptyWearState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.wear_connect_phone),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun WearDoseCard(
    slot: WearDoseSlot,
    anchorDateEpochDay: Long,
    feedback: WearFeedback?,
    onLog: () -> Unit,
    onSkip: () -> Unit,
) {
    val isLoggable = slot.isQuickLoggable()
    val isThisSlot = feedback?.actionToken == slot.actionToken
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (slot.status) {
                WearDoseStatus.OVERDUE -> MaterialTheme.colorScheme.errorContainer
                WearDoseStatus.DUE_SOON -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = slot.groupName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                text = slot.medicationSummary,
                modifier = Modifier.padding(top = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 15.sp,
            )
            Text(
                text = "${slot.scheduledDateTimeText(anchorDateEpochDay)} · ${statusText(slot.status)}",
                modifier = Modifier.padding(top = 4.dp),
                color = statusColor(slot.status),
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
            )
            if (isLoggable) {
                val actionEnabled = !isThisSlot || feedback is WearFeedback.Failed
                if (isThisSlot && feedback !is WearFeedback.Failed) {
                    Text(
                        text = when {
                            isThisSlot && feedback is WearFeedback.Sending ->
                                stringResource(R.string.wear_syncing)

                            isThisSlot && feedback is WearFeedback.Sent ->
                                stringResource(R.string.wear_sent)

                            isThisSlot && feedback is WearFeedback.Failed ->
                                stringResource(R.string.wear_no_phone)

                            else -> stringResource(R.string.wear_log)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 6.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Button(
                            onClick = onLog,
                            enabled = actionEnabled,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                            contentPadding = PaddingValues(vertical = 7.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.wear_log),
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                        OutlinedButton(
                            onClick = onSkip,
                            enabled = actionEnabled,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 7.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.wear_skip),
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WearRecentDoseCard(
    recentDose: WearRecentDose,
    undoFeedback: WearUndoFeedback?,
    onUndo: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = recentDose.groupName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            if (recentDose.medicationSummary.isNotBlank()) {
                Text(
                    text = recentDose.medicationSummary,
                    modifier = Modifier.padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
            }
            Text(
                text = formatWearDateTime(recentDose.recordedAt),
                modifier = Modifier.padding(top = 3.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
            if (recentDose.entryUuids.isNotEmpty()) {
                OutlinedButton(
                    onClick = onUndo,
                    enabled = undoFeedback == null || undoFeedback is WearUndoFeedback.Failed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    Text(
                        text = when (undoFeedback) {
                            WearUndoFeedback.Sending -> stringResource(R.string.wear_syncing)
                            WearUndoFeedback.Sent -> stringResource(R.string.wear_sent)
                            WearUndoFeedback.Failed -> stringResource(R.string.wear_no_phone)
                            null -> stringResource(R.string.wear_undo_record)
                        },
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun WearEstradiolCard(estradiol: WearEstradiolSnapshot) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.wear_estimated_e2),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Text(
                text = "~${estradiol.currentValueText} ${estradiol.unitLabel}",
                modifier = Modifier.padding(top = 2.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
            )
            WearConcentrationChart(
                estradiol = estradiol,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .padding(top = 10.dp),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.wear_48_hours_ago),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.wear_now),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun WearConcentrationChart(
    estradiol: WearEstradiolSnapshot,
    modifier: Modifier = Modifier,
) {
    val normalized = estradiol.normalizedSamples()
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
    Canvas(modifier = modifier) {
        if (normalized.size < 2) return@Canvas
        drawLine(
            color = gridColor,
            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx(),
        )
        val path = Path()
        val points = normalized.mapIndexed { index, value ->
            androidx.compose.ui.geometry.Offset(
                x = size.width * index / normalized.lastIndex,
                y = size.height - value * size.height,
            )
        }
        path.moveTo(points.first().x, points.first().y)
        for (index in 0 until points.lastIndex) {
            val previous = points[(index - 1).coerceAtLeast(0)]
            val start = points[index]
            val end = points[index + 1]
            val next = points[(index + 2).coerceAtMost(points.lastIndex)]
            val control1 = androidx.compose.ui.geometry.Offset(
                x = start.x + (end.x - previous.x) / 6f,
                y = (start.y + (end.y - previous.y) / 6f).coerceIn(0f, size.height),
            )
            val control2 = androidx.compose.ui.geometry.Offset(
                x = end.x - (next.x - start.x) / 6f,
                y = (end.y - (next.y - start.y) / 6f).coerceIn(0f, size.height),
            )
            path.cubicTo(
                control1.x,
                control1.y,
                control2.x,
                control2.y,
                end.x,
                end.y,
            )
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawCircle(
            color = lineColor,
            radius = 4.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(
                x = size.width,
                y = size.height - normalized.last() * size.height,
            ),
        )
    }
}

@Composable
private fun WearPlanRow(
    slot: WearDoseSlot,
    anchorDateEpochDay: Long,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = "${slot.scheduledDateTimeText(anchorDateEpochDay)} · ${slot.groupName}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Text(
                text = slot.medicationSummary,
                modifier = Modifier.padding(top = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
            Text(
                text = statusText(slot.status),
                modifier = Modifier.padding(top = 3.dp),
                color = statusColor(slot.status),
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun WearSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 6.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
    )
}

@Composable
private fun statusText(status: WearDoseStatus): String = stringResource(
    when (status) {
        WearDoseStatus.DONE -> R.string.wear_status_done
        WearDoseStatus.DUE_SOON -> R.string.wear_status_due
        WearDoseStatus.OVERDUE -> R.string.wear_status_overdue
        WearDoseStatus.UPCOMING -> R.string.wear_status_upcoming
        WearDoseStatus.LOGGED_OUT_OF_WINDOW -> R.string.wear_status_logged_outside
    }
)

@Composable
private fun statusColor(status: WearDoseStatus): Color = when (status) {
    WearDoseStatus.OVERDUE -> MaterialTheme.colorScheme.error
    WearDoseStatus.DUE_SOON -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private sealed interface WearFeedback {
    val actionToken: String

    data class Sending(override val actionToken: String) : WearFeedback
    data class Sent(override val actionToken: String) : WearFeedback
    data class Failed(override val actionToken: String) : WearFeedback
}

private enum class WearAction { LOG, SKIP }

private sealed interface WearUndoFeedback {
    data object Sending : WearUndoFeedback
    data object Sent : WearUndoFeedback
    data object Failed : WearUndoFeedback
}

private fun formatWearDateTime(value: String): String =
    runCatching {
        val dateTime = LocalDateTime.parse(value)
        "%02d-%02d %s".format(
            dateTime.monthValue,
            dateTime.dayOfMonth,
            dateTime.toLocalTime().toString().take(5),
        )
    }.getOrDefault(value)

private const val ACTION_FEEDBACK_MILLIS = 1_500L

private val featherlineWearColorScheme = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF18204C),
    primaryContainer = Color(0xFF303868),
    onPrimaryContainer = Color(0xFFDDE1FF),
    surface = Color.Black,
    onSurface = Color(0xFFE4E2E9),
    surfaceVariant = Color(0xFF25262D),
    onSurfaceVariant = Color(0xFFC6C5D0),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5A1A19),
)
