package com.mkx.hrttracker.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
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
import androidx.core.graphics.createBitmap
import androidx.glance.unit.ColorProvider

internal fun isEmptySetup(snapshot: WidgetSnapshotRecord?): Boolean =
    snapshot == null || !snapshot.hasActiveGroups

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
internal fun EmptyWidgetContent(
    iconRes: Int = R.drawable.ic_medication,
    textRes: Int = R.string.widget_no_medications,
    iconSize: Float = 24f,
    backgroundColor: ColorProvider = LocalWidgetColors.current.primary,
    foregroundColor: ColorProvider = LocalWidgetColors.current.onPrimary,
) {
    val colors = LocalWidgetColors.current
    val context = LocalContext.current
    val scale = LocalWidgetScale.current
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier.size((32f * scale).dp)
                .background(backgroundColor)
                .cornerRadius(999.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size((iconSize * scale).dp),
                colorFilter = ColorFilter.tint(foregroundColor),
            )
        }
        Spacer(GlanceModifier.height((8f * scale).dp))
        Text(
            text = context.getString(textRes),
            style = TextStyle(
                color = colors.onSurface,
                fontSize = (18f * scale).sp,
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
                    .background(if (i < doneCount) colors.primary else colors.outlineVariant)
                    .cornerRadius(999.dp),
            ) {}
        }
    }
}

@Composable
internal fun ProgressRing(
    doneCount: Int,
    totalCount: Int,
    sizeDp: Float = 32f,
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

    // Render two same-shape bitmaps and tint them via Glance ColorFilter so the
    // launcher resolves both light/dark variants of the ColorProvider at
    // RemoteViews apply time — baking a color into the bitmap leaves it stuck in
    // whichever uiMode our process was in when we composed.
    val trackBitmap = createBitmap(sizePx, sizePx)
    val trackCanvas = Canvas(trackBitmap)
    val progressBitmap = createBitmap(sizePx, sizePx)
    val progressCanvas = Canvas(progressBitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
        color = android.graphics.Color.WHITE
    }

    val inset = strokePx / 2f
    val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)

    if (totalCount == 1) {
        trackCanvas.drawArc(rect, -90f, 360f, false, paint)
        if (doneCount >= 1) {
            progressCanvas.drawArc(rect, -90f, 360f, false, paint)
        }
    } else {
        val segmentSweep = 360f / totalCount
        // Reserve enough gap so adjacent round caps don't merge: the cap radius is
        // strokePx/2, so each cap extends ~strokePx tangentially on the centerline.
        val radiusPx = (sizePx - strokePx) / 2.0
        val capSweep = Math.toDegrees(strokePx / radiusPx).toFloat()
        val gap = (capSweep + 10f)
            .coerceAtMost(segmentSweep * 0.5f)
            .coerceAtLeast(10f)
        val drawSweep = (segmentSweep - gap).coerceAtLeast(0.5f)

        for (i in 0 until totalCount) {
            val startAngle = -90f + segmentSweep * i + gap / 2f
            trackCanvas.drawArc(rect, startAngle, drawSweep, false, paint)
            if (i < doneCount) {
                progressCanvas.drawArc(rect, startAngle, drawSweep, false, paint)
            }
        }
    }

    Box(modifier = GlanceModifier.size(scaledSizeDp.dp)) {
        Image(
            provider = ImageProvider(trackBitmap),
            contentDescription = null,
            modifier = GlanceModifier.size(scaledSizeDp.dp),
            colorFilter = ColorFilter.tint(colors.outlineVariant),
        )
        Image(
            provider = ImageProvider(progressBitmap),
            contentDescription = null,
            modifier = GlanceModifier.size(scaledSizeDp.dp),
            colorFilter = ColorFilter.tint(colors.primary),
        )
    }
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
internal fun TrailingButton(
    row: WidgetDoseRow,
    showLogAction: Boolean,
    navigateIntent: Intent? = null,
    buttonSizeDp: Float = 32f,
    iconSizeDp: Float = 24f,
    arrowIconSizeDp: Float = 20f,
) {
    val colors = LocalWidgetColors.current
    val scale = LocalWidgetScale.current
    val buttonSize = (buttonSizeDp * scale).dp
    val iconSize = (iconSizeDp * scale).dp
    val arrowIconSize = (arrowIconSizeDp * scale).dp
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
            modifier = GlanceModifier.size(buttonSize)
                .background(colors.primaryContainer)
                .cornerRadius(999.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_check),
                contentDescription = null,
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(colors.onPrimaryContainer),
            )
        }

        WidgetDoseStatus.DUE_SOON -> Box(
            modifier = GlanceModifier.size(buttonSize)
                .background(colors.tertiaryContainer)
                .cornerRadius(999.dp)
                .then(logModifier),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_add),
                contentDescription = null,
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(colors.onTertiaryContainer),
            )
        }

        WidgetDoseStatus.OVERDUE -> Box(
            modifier = GlanceModifier.size(buttonSize)
                .background(colors.surfaceVariant)
                .cornerRadius(999.dp)
                .then(logModifier),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_add),
                contentDescription = null,
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
            )
        }

        WidgetDoseStatus.LOGGED_OUT_OF_WINDOW -> Box(
            modifier = GlanceModifier.size(buttonSize)
                .background(colors.surfaceVariant)
                .cornerRadius(999.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_check),
                contentDescription = null,
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
            )
        }

        else -> Box(
            modifier = GlanceModifier.size(buttonSize)
                .background(colors.surfaceVariant)
                .cornerRadius(999.dp)
                .then(navigateModifier),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_arrow_forward),
                contentDescription = null,
                modifier = GlanceModifier.size(arrowIconSize),
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
                .background(groupAccentColor(row.colorKey, LocalWidgetForcedDark.current))
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
