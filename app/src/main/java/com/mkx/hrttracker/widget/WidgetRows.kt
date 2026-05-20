package com.mkx.hrttracker.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity as actionStartActivityFromIntent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mkx.hrttracker.EXTRA_HIGHLIGHT_ENTRY_UUID
import com.mkx.hrttracker.EXTRA_HIGHLIGHT_GROUP_UUID
import com.mkx.hrttracker.EXTRA_HIGHLIGHT_KIND
import com.mkx.hrttracker.EXTRA_HIGHLIGHT_MEDICATION_UUID
import com.mkx.hrttracker.EXTRA_HIGHLIGHT_SCHEDULED_AT
import com.mkx.hrttracker.EXTRA_HIGHLIGHT_SCHEDULE_TIME_UUID
import com.mkx.hrttracker.HIGHLIGHT_KIND_MANUAL
import com.mkx.hrttracker.HIGHLIGHT_KIND_SCHEDULED
import com.mkx.hrttracker.MainActivity
import com.mkx.hrttracker.R

internal fun isEmptySetup(snapshot: WidgetSnapshotRecord?): Boolean =
    snapshot == null || snapshot.doseRows.isEmpty()

internal sealed interface WidgetListItem {
    data class Header(val text: String) : WidgetListItem
    data class Row(val row: WidgetDoseRow) : WidgetListItem
}

@Composable
internal fun WidgetShell(contentAlignment: Alignment = Alignment.TopStart, content: @Composable () -> Unit) {
    val colors = LocalWidgetColors.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.surface)
            .cornerRadius(22.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
        contentAlignment = contentAlignment,
    ) {
        content()
    }
}

@Composable
internal fun WidgetLabel(
    text: String,
    modifier: GlanceModifier = GlanceModifier,
    fontSize: TextUnit = 18.sp,
) {
    val colors = LocalWidgetColors.current
    val scale = LocalWidgetScale.current
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = TextStyle(
            color = colors.onSurfaceVariant,
            fontSize = (fontSize.value * scale).sp,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
    )
}

@Composable
internal fun EmptyWidgetContent() {
    val colors = LocalWidgetColors.current
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_medication),
            contentDescription = null,
            modifier = GlanceModifier.size(30.dp),
            colorFilter = ColorFilter.tint(colors.outlineVariant),
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = context.getString(R.string.widget_no_medications),
            style = TextStyle(
                color = colors.onSurfaceVariant,
                fontSize = (13f * LocalWidgetScale.current).sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

@Composable
internal fun ProgressBar(
    doneCount: Int,
    totalCount: Int,
    modifier: GlanceModifier = GlanceModifier.fillMaxWidth(),
) {
    if (totalCount <= 0) return
    val colors = LocalWidgetColors.current
    val scale = LocalWidgetScale.current
    Row(modifier = modifier.height((6f * scale).dp)) {
        for (i in 0 until totalCount) {
            if (i > 0) Spacer(GlanceModifier.width((3f * scale).dp))
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .background(if (i < doneCount) colors.primary else colors.surfaceVariant)
                    .cornerRadius(999.dp),
            ) {}
        }
    }
}

@Composable
internal fun ProgressRing(
    doneCount: Int,
    totalCount: Int,
    sizeDp: Float = 28f,
    strokeDp: Float = 4f,
) {
    if (totalCount <= 0) return
    val context = LocalContext.current
    val colors = LocalWidgetColors.current
    val scale = LocalWidgetScale.current
    val density = context.resources.displayMetrics.density

    val scaledSizeDp = sizeDp * scale
    val sizePx = (scaledSizeDp * density).toInt().coerceAtLeast(1)
    val strokePx = strokeDp * scale * density

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
    }

    val inset = strokePx / 2f
    val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)

    paint.color = colors.surfaceVariant.getColor(context).toArgb()
    canvas.drawArc(rect, 0f, 360f, false, paint)

    val fraction = (doneCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    if (fraction > 0f) {
        paint.color = colors.primary.getColor(context).toArgb()
        canvas.drawArc(rect, -90f, 360f * fraction, false, paint)
    }

    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        modifier = GlanceModifier.size(scaledSizeDp.dp),
    )
}

@Composable
internal fun SectionHeader(text: String, topPadding: Dp = 4.dp) {
    val colors = LocalWidgetColors.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(top = topPadding, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetLabel(text, fontSize = 16.sp)
        Spacer(GlanceModifier.width(8.dp))
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .height(1.dp)
                .background(colors.outlineVariant),
        ) {}
    }
}

@Composable
internal fun TrailingButton(row: WidgetDoseRow, showLogAction: Boolean, navigateIntent: Intent? = null) {
    val colors = LocalWidgetColors.current
    val scale = LocalWidgetScale.current
    val groupUuid = row.groupUuid
    val logModifier = if (showLogAction && groupUuid != null) {
        GlanceModifier.clickable(
            actionRunCallback<QuickLogActionCallback>(
                actionParametersOf(
                    GroupUuidKey to groupUuid,
                    ScheduleTimeUuidKey to (row.scheduleTimeUuid ?: ""),
                    ScheduledAtKey to row.scheduledAt.toString(),
                    MedicationUuidKey to (row.medicationUuid ?: ""),
                )
            )
        )
    } else {
        GlanceModifier
    }
    val navigateModifier = if (navigateIntent != null) {
        GlanceModifier.clickable(actionStartActivityFromIntent(navigateIntent))
    } else {
        GlanceModifier
    }

    when (row.status) {
        WidgetDoseStatus.DONE -> Box(
            modifier = GlanceModifier.size((32f * scale).dp)
                .background(colors.secondaryContainer)
                .cornerRadius(999.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_check),
                contentDescription = null,
                modifier = GlanceModifier.size((24f * scale).dp),
                colorFilter = ColorFilter.tint(colors.onSecondaryContainer),
            )
        }

        WidgetDoseStatus.DUE_SOON -> Box(
            modifier = GlanceModifier.size((32f * scale).dp)
                .background(colors.tertiaryContainer)
                .cornerRadius(999.dp)
                .then(logModifier),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_add),
                contentDescription = null,
                modifier = GlanceModifier.size((24f * scale).dp),
                colorFilter = ColorFilter.tint(colors.onTertiaryContainer),
            )
        }

        WidgetDoseStatus.OVERDUE -> Box(
            modifier = GlanceModifier.size((32f * scale).dp)
                .background(colors.surfaceVariant)
                .cornerRadius(999.dp)
                .then(logModifier),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_add),
                contentDescription = null,
                modifier = GlanceModifier.size((24f * scale).dp),
                colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
            )
        }

        WidgetDoseStatus.LOGGED_OUT_OF_WINDOW -> Box(
            modifier = GlanceModifier.size((32f * scale).dp)
                .background(colors.surfaceVariant)
                .cornerRadius(999.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_check),
                contentDescription = null,
                modifier = GlanceModifier.size((24f * scale).dp),
                colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
            )
        }

        else -> Box(
            modifier = GlanceModifier.size((32f * scale).dp)
                .background(colors.surfaceVariant)
                .cornerRadius(999.dp)
                .then(navigateModifier),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_arrow_forward),
                contentDescription = null,
                modifier = GlanceModifier.size((20f * scale).dp),
                colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
            )
        }
    }
}

internal fun widgetRowHighlightIntent(context: Context, row: WidgetDoseRow): Intent? {
    val entryUuid = row.entryUuid
    if (entryUuid != null) {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = "hrttracker://widget-row-highlight/manual/$entryUuid".toUri()
            putExtra(EXTRA_HIGHLIGHT_KIND, HIGHLIGHT_KIND_MANUAL)
            putExtra(EXTRA_HIGHLIGHT_ENTRY_UUID, entryUuid)
        }
    }
    val groupUuid = row.groupUuid ?: return null
    val stableKey = listOf(
        groupUuid,
        row.scheduleTimeUuid.orEmpty(),
        row.scheduledAt.toString(),
        row.medicationUuid.orEmpty(),
    ).joinToString(":")
    return Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data = "hrttracker://widget-row-highlight/scheduled/${android.net.Uri.encode(stableKey)}".toUri()
        putExtra(EXTRA_HIGHLIGHT_KIND, HIGHLIGHT_KIND_SCHEDULED)
        putExtra(EXTRA_HIGHLIGHT_GROUP_UUID, groupUuid)
        row.scheduleTimeUuid?.let { putExtra(EXTRA_HIGHLIGHT_SCHEDULE_TIME_UUID, it) }
        putExtra(EXTRA_HIGHLIGHT_SCHEDULED_AT, row.scheduledAt.toString())
        row.medicationUuid?.let { putExtra(EXTRA_HIGHLIGHT_MEDICATION_UUID, it) }
    }
}

@Composable
internal fun DoseRow(
    row: WidgetDoseRow,
    showLogAction: Boolean,
    hideMedicationDetails: Boolean,
    highlightIntent: Intent? = null,
) {
    val colors = LocalWidgetColors.current
    val scale = LocalWidgetScale.current
    val rowClickModifier = if (highlightIntent != null) {
        GlanceModifier.clickable(actionStartActivityFromIntent(highlightIntent))
    } else {
        GlanceModifier
    }
    val rowModifier = GlanceModifier
        .fillMaxWidth()
        .height((64f * scale).dp)
        .background(colors.surfaceContainerLow)
        .cornerRadius(10.dp)
        .padding(horizontal = (16f * scale).dp)
        .then(rowClickModifier)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(6.dp)
                .height((44f * scale).dp)
                .background(groupAccentColor(row.colorKey))
                .cornerRadius(999.dp),
        ) {}

        Spacer(GlanceModifier.width(10.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            val context = LocalContext.current
            val fullName = when {
                hideMedicationDetails && row.isManualRecord -> context.getString(R.string.widget_manual_record)
                hideMedicationDetails -> row.groupName
                else -> row.medicationName
            }
            Text(
                text = fullName,
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(
                    color = if (row.status == WidgetDoseStatus.UPCOMING) {
                        colors.onSurfaceVariant
                    } else {
                        colors.onSurface
                    },
                    fontSize = (18f * LocalWidgetScale.current).sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            if (!hideMedicationDetails && (row.routeLabel.isNotBlank() || row.doseText.isNotBlank())) {
                val supportingText = listOfNotNull(
                    row.routeLabel.takeIf(String::isNotBlank),
                    row.doseText.takeIf(String::isNotBlank),
                ).joinToString(" · ")
                if (supportingText.isNotBlank()) {
                    Text(
                        text = supportingText,
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = (14f * LocalWidgetScale.current).sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(GlanceModifier.width(8.dp))

        val showTrailingText = row.trailingText != null && !(hideMedicationDetails && row.isManualRecord)
        if (showTrailingText) {
            Text(
                text = row.trailingText,
                style = TextStyle(
                    color = colors.onSurface,
                    fontSize = (16f * LocalWidgetScale.current).sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(8.dp))
        }
        TrailingButton(row, showLogAction, highlightIntent)
    }
}
