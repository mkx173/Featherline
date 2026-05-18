package com.mkx.hrttracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll as glanceUpdateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mkx.hrttracker.MainActivity
import com.mkx.hrttracker.R
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ── Colors ────────────────────────────────────────────────────────────────────

private val colorPrimary = ColorProvider(day = Color(0xFF8B2945), night = Color(0xFFFFB1C0))
private val colorOnPrimary = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF551D2C))
private val colorTertiary = ColorProvider(day = Color(0xFF7C5700), night = Color(0xFFECBE91))
private val colorOnSurfaceVariant = ColorProvider(day = Color(0xFF5C4146), night = Color(0xFFD6C2C4))
private val colorOutline = ColorProvider(day = Color(0xFF7F6669), night = Color(0xFF9F8C8F))
private val colorSurfaceContainerLow = ColorProvider(day = Color(0xFFF6EEF0), night = Color(0xFF261D1F))
private val colorOnSurface = ColorProvider(day = Color(0xFF201A1B), night = Color(0xFFEDE0E1))
private val colorSurface = ColorProvider(day = Color(0xFFF8F0F1), night = Color(0xFF201414))

// ── Widget ────────────────────────────────────────────────────────────────────

class HrtWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = HrtWidgetStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(MEDIUM_SIZE, LARGE_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = currentState<WidgetSnapshotState>()
            val snapshot = state.record?.takeIf { it.schemaVersion == WIDGET_SNAPSHOT_SCHEMA_VERSION }
            GlanceTheme {
                if (LocalSize.current.height >= LARGE_SIZE.height) {
                    LargeWidgetContent(snapshot)
                } else {
                    MediumWidgetContent(snapshot)
                }
            }
        }
    }

    suspend fun updateAll(context: Context) {
        glanceUpdateAll(context)
    }

    companion object {
        val MEDIUM_SIZE = DpSize(180.dp, 100.dp)
        val LARGE_SIZE = DpSize(180.dp, 180.dp)
    }
}

// ── State definition ──────────────────────────────────────────────────────────

internal object HrtWidgetStateDefinition : GlanceStateDefinition<WidgetSnapshotState> {
    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<WidgetSnapshotState> =
        context.widgetSnapshotDataStore

    override fun getLocation(context: Context, fileKey: String): File =
        File(context.filesDir, "datastore/widget_snapshot.pb")
}

// ── Receivers ─────────────────────────────────────────────────────────────────

class HrtWidgetMediumReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HrtWidget()
}

class HrtWidgetLargeReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HrtWidget()
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun dateLabel(scheduledAt: LocalDateTime, context: Context): String {
    val today = LocalDate.now()
    return when (scheduledAt.toLocalDate()) {
        today -> context.getString(R.string.widget_today)
        today.plusDays(1) -> context.getString(R.string.widget_tomorrow)
        else -> scheduledAt.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
}

private fun formatTime(scheduledAt: LocalDateTime): String =
    scheduledAt.format(DateTimeFormatter.ofPattern("HH:mm"))

private fun statusDotColor(status: WidgetDoseStatus) = when (status) {
    WidgetDoseStatus.DONE -> colorPrimary
    WidgetDoseStatus.DUE_SOON -> colorTertiary
    WidgetDoseStatus.OVERDUE -> colorTertiary
    WidgetDoseStatus.UPCOMING -> colorOutline
}

// ── Progress bar ──────────────────────────────────────────────────────────────

@Composable
private fun ProgressBar(fraction: Float, modifier: GlanceModifier = GlanceModifier) {
    val safeFraction = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(4.dp)
            .fillMaxWidth()
            .background(colorOutline),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (safeFraction > 0f) {
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .width((safeFraction * 100).dp) // approximated; will clip to parent
                    .background(colorPrimary),
            ) {}
        }
    }
}

// ── Log button ────────────────────────────────────────────────────────────────

@Composable
private fun LogButton(row: WidgetDoseRow) {
    val groupUuid = row.groupUuid ?: return
    val scheduleTimeUuid = row.scheduleTimeUuid ?: ""
    Box(
        modifier = GlanceModifier
            .size(32.dp)
            .background(colorPrimary)
            .clickable(
                actionRunCallback<QuickLogActionCallback>(
                    actionParametersOf(
                        GroupUuidKey to groupUuid,
                        ScheduleTimeUuidKey to scheduleTimeUuid,
                        ScheduledAtKey to row.scheduledAt.toString(),
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            style = TextStyle(
                color = colorOnPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

// ── Dose row ──────────────────────────────────────────────────────────────────

@Composable
private fun DoseRow(row: WidgetDoseRow) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status dot
        Box(
            modifier = GlanceModifier
                .size(8.dp)
                .background(statusDotColor(row.status)),
        ) {}

        Spacer(GlanceModifier.width(8.dp))

        // Name + subtitle
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .wrapContentHeight(),
        ) {
            Text(
                text = row.displayName,
                style = TextStyle(
                    color = colorOnSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            val subtitle = buildString {
                append(dateLabel(row.scheduledAt, context))
                append(" · ")
                append(formatTime(row.scheduledAt))
                if (row.count > 1) append(" +${row.count - 1}")
            }
            Text(
                text = subtitle,
                style = TextStyle(
                    color = colorOnSurfaceVariant,
                    fontSize = 11.sp,
                ),
                maxLines = 1,
            )
        }

        Spacer(GlanceModifier.width(8.dp))

        // Trailing: done time or log button
        when {
            row.status == WidgetDoseStatus.DONE -> {
                val doneAt = row.doneAtEpochMillis?.let {
                    LocalDateTime.ofEpochSecond(it / 1000, 0, java.time.ZoneOffset.UTC)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime()
                }
                Text(
                    text = if (doneAt != null) formatTime(doneAt) else "",
                    style = TextStyle(
                        color = colorOnSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                )
            }
            row.status == WidgetDoseStatus.DUE_SOON || row.status == WidgetDoseStatus.OVERDUE -> {
                LogButton(row)
            }
            else -> {
                Text(
                    text = formatTime(row.scheduledAt),
                    style = TextStyle(
                        color = colorOnSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                )
            }
        }
    }
}

// ── Medium widget (4×2) ───────────────────────────────────────────────────────

@Composable
private fun MediumWidgetContent(snapshot: WidgetSnapshotRecord?) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colorSurface)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        if (snapshot == null) {
            Text(
                text = context.getString(R.string.widget_no_medications),
                style = TextStyle(color = colorOnSurfaceVariant, fontSize = 13.sp),
            )
            return@Box
        }

        val doneCount = snapshot.doneCount
        val totalCount = snapshot.totalCount
        val allDone = doneCount >= totalCount && totalCount > 0
        val nextRow = snapshot.doseRows
            .filter { it.status == WidgetDoseStatus.DUE_SOON || it.status == WidgetDoseStatus.OVERDUE }
            .minByOrNull { it.scheduledAt }

        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: progress fraction + bar
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$doneCount / $totalCount",
                    style = TextStyle(
                        color = colorOnSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.height(4.dp))
                ProgressBar(
                    fraction = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f,
                )
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = context.getString(R.string.widget_today),
                    style = TextStyle(
                        color = colorOnSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                )
            }

            Spacer(GlanceModifier.width(12.dp))

            // Right: next dose or all-done
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start,
            ) {
                if (allDone || nextRow == null) {
                    Text(
                        text = context.getString(R.string.widget_all_done),
                        style = TextStyle(
                            color = colorPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                } else {
                    Text(
                        text = nextRow.displayName,
                        style = TextStyle(
                            color = colorOnSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = formatTime(nextRow.scheduledAt),
                        style = TextStyle(
                            color = colorOnSurfaceVariant,
                            fontSize = 11.sp,
                        ),
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    LogButton(nextRow)
                }
            }
        }
    }
}

// ── Large widget (4×3) ────────────────────────────────────────────────────────

private fun buildLargeWidgetRows(snapshot: WidgetSnapshotRecord): List<WidgetDoseRow> {
    // Priority: actionable (DUE_SOON/OVERDUE) first, then UPCOMING, then DONE; limit to 3.
    val actionable = snapshot.doseRows.filter {
        it.status == WidgetDoseStatus.DUE_SOON || it.status == WidgetDoseStatus.OVERDUE
    }
    val upcoming = snapshot.doseRows.filter { it.status == WidgetDoseStatus.UPCOMING }
    val done = snapshot.doseRows.filter { it.status == WidgetDoseStatus.DONE }
    return (actionable + upcoming + done).take(3)
}

@Composable
private fun LargeWidgetContent(snapshot: WidgetSnapshotRecord?) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colorSurface)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        if (snapshot == null) {
            Text(
                text = context.getString(R.string.widget_no_medications),
                style = TextStyle(color = colorOnSurfaceVariant, fontSize = 13.sp),
            )
            return@Box
        }

        val doneCount = snapshot.doneCount
        val totalCount = snapshot.totalCount
        val allDone = doneCount >= totalCount && totalCount > 0
        val rows = buildLargeWidgetRows(snapshot)

        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header: TODAY · X of Y done
            Row(
                modifier = GlanceModifier.fillMaxWidth().wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = context.getString(R.string.widget_today).uppercase(),
                    style = TextStyle(
                        color = colorOnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = "· $doneCount / $totalCount",
                    style = TextStyle(
                        color = colorOnSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                )
            }

            Spacer(GlanceModifier.height(4.dp))

            ProgressBar(
                fraction = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f,
            )

            Spacer(GlanceModifier.height(8.dp))

            // Dose rows
            for (row in rows) {
                DoseRow(row)
            }

            Spacer(GlanceModifier.defaultWeight())

            // Footer: all-done or next dose label
            if (allDone) {
                Text(
                    text = context.getString(R.string.widget_all_done),
                    style = TextStyle(
                        color = colorPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            } else {
                val next = snapshot.nextDueDose
                if (next != null) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().wrapContentHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Next:",
                            style = TextStyle(
                                color = colorOnSurfaceVariant,
                                fontSize = 11.sp,
                            ),
                        )
                        Spacer(GlanceModifier.width(4.dp))
                        Text(
                            text = "${next.name} · ${dateLabel(next.scheduledAt, context)} ${formatTime(next.scheduledAt)}",
                            style = TextStyle(
                                color = colorOnSurface,
                                fontSize = 11.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
