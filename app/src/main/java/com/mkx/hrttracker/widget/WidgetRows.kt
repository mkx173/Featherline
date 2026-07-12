package com.mkx.hrttracker.widget

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
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
import androidx.glance.unit.ColorProvider
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
import androidx.glance.appwidget.action.actionStartActivity as actionStartActivityFromIntent

internal fun isEmptySetup(snapshot: WidgetSnapshotRecord?): Boolean =
    snapshot == null || (!snapshot.hasActiveGroups && snapshot.doseRows.isEmpty())

internal sealed interface WidgetListItem {
    data class Header(val text: String) : WidgetListItem
    data class Row(val row: WidgetDoseRow) : WidgetListItem
}

internal val WidgetShellPadding = 12.dp
internal const val WidgetDoseRowIndicatorSlotSizeDp = 20f

internal enum class WidgetRoundedShape(
    val maskRes: Int,
    val rippleRes: Int,
    private val radiusDp: Int,
) {
    Shell(R.drawable.widget_shell_rounded_mask, R.drawable.widget_shell_ripple, 22),
    Card(R.drawable.widget_card_rounded_mask, R.drawable.widget_card_ripple, 10),
    Pill(R.drawable.widget_pill_rounded_mask, R.drawable.widget_pill_ripple, 999);

    val radius: Dp get() = radiusDp.dp
}

@Composable
private fun RoundedMaskImage(
    shape: WidgetRoundedShape,
    color: ColorProvider,
    modifier: GlanceModifier = GlanceModifier.fillMaxSize(),
) {
    val resolvedColor = color.getColor(LocalContext.current)
    Image(
        provider = ImageProvider(shape.maskRes),
        contentDescription = null,
        alpha = resolvedColor.alpha,
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.tint(fixedColorProvider(resolvedColor.copy(alpha = 1f))),
    )
}

@Composable
internal fun RoundedBackgroundBox(
    modifier: GlanceModifier,
    color: ColorProvider,
    shape: WidgetRoundedShape,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit = {},
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Box(
            modifier = modifier
                .background(color)
                .cornerRadius(shape.radius),
            contentAlignment = contentAlignment,
        ) {
            content()
        }
    } else {
        Box(
            modifier = modifier,
            contentAlignment = contentAlignment,
        ) {
            RoundedMaskImage(shape = shape, color = color)
            content()
        }
    }
}

@Composable
internal fun RoundedBackgroundRow(
    modifier: GlanceModifier,
    color: ColorProvider,
    shape: WidgetRoundedShape,
    contentModifier: GlanceModifier = GlanceModifier,
    content: @Composable RowScope.() -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Row(
            modifier = modifier
                .background(color)
                .cornerRadius(shape.radius)
                .then(contentModifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    } else {
        Box(modifier = modifier) {
            RoundedMaskImage(shape = shape, color = color)
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .then(contentModifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun WidgetShell(
    scale: Float,
    contentAlignment: Alignment = Alignment.TopStart,
    // Whole-shell tap target. Defaults to opening the app (dose widgets); the anchor
    // widget passes its own (open Milestones / reconfigure this instance).
    onClick: Action? = null,
    // Full-bleed layer under the padded content (the anchor widget's baked watermark).
    // The shell's padding moves to an inner box so this can reach the card edges.
    backdrop: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalWidgetColors.current
    CompositionLocalProvider(LocalWidgetScale provides scale) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .background(colors.widgetBackground)
                    .cornerRadius(WidgetRoundedShape.Shell.radius)
                    .clickable(
                        onClick = onClick ?: actionStartActivity<MainActivity>(),
                        rippleOverride = WidgetRoundedShape.Shell.rippleRes,
                    ),
                contentAlignment = contentAlignment,
            ) {
                backdrop?.invoke()
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(WidgetShellPadding),
                    contentAlignment = contentAlignment,
                ) {
                    content()
                }
            }
        } else {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(
                        onClick = onClick ?: actionStartActivity<MainActivity>(),
                        rippleOverride = WidgetRoundedShape.Shell.rippleRes,
                    ),
                contentAlignment = contentAlignment,
            ) {
                RoundedMaskImage(
                    shape = WidgetRoundedShape.Shell,
                    color = colors.widgetBackground,
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground(),
                )
                backdrop?.invoke()
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(WidgetShellPadding),
                    contentAlignment = contentAlignment,
                ) {
                    content()
                }
            }
        }
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
        RoundedBackgroundBox(
            modifier = GlanceModifier.size((32f * scale).dp),
            color = backgroundColor,
            shape = WidgetRoundedShape.Pill,
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
    val colors = LocalWidgetColors.current
    val scale = LocalWidgetScale.current
    // Treat 0/0 as 0/1 so we still draw an empty track instead of collapsing the
    // top panel layout when nothing is scheduled today.
    val renderTotal = totalCount.coerceAtLeast(1)
    Row(modifier = modifier.height((6f * scale).dp)) {
        for (i in 0 until renderTotal) {
            if (i > 0) Spacer(GlanceModifier.width((3f * scale).dp))
            RoundedBackgroundBox(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
                color = if (i < doneCount) colors.primary else colors.outlineVariant,
                shape = WidgetRoundedShape.Pill,
            ) {}
        }
    }
}

@Composable
internal fun ProgressRing(
    doneCount: Int,
    totalCount: Int,
    sizeDp: Float = 34f,
    strokeDp: Float = 4f,
) {
    val context = LocalContext.current
    val colors = LocalWidgetColors.current
    val scale = LocalWidgetScale.current
    val density = context.resources.displayMetrics.density

    val scaledSizeDp = sizeDp * scale
    val scaledBoxSizeDp = (sizeDp + 4) * scale
    val sizePx = (scaledSizeDp * density).toInt().coerceAtLeast(1)
    val strokePx = strokeDp * scale * density
    // Treat 0/0 as 0/1 so the ring still draws as an empty track instead of
    // disappearing entirely when nothing is scheduled today.
    val renderTotal = totalCount.coerceAtLeast(1)

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

    if (renderTotal == 1) {
        trackCanvas.drawArc(rect, -90f, 360f, false, paint)
        if (doneCount >= 1) {
            progressCanvas.drawArc(rect, -90f, 360f, false, paint)
        }
    } else {
        val segmentSweep = 360f / renderTotal
        // Reserve enough gap so adjacent round caps don't merge: the cap radius is
        // strokePx/2, so each cap extends ~strokePx tangentially on the centerline.
        val radiusPx = (sizePx - strokePx) / 2.0
        val capSweep = Math.toDegrees(strokePx / radiusPx).toFloat()
        val gap = (capSweep + 10f)
            .coerceAtMost(segmentSweep * 0.5f)
            .coerceAtLeast(10f)
        val drawSweep = (segmentSweep - gap).coerceAtLeast(0.5f)

        for (i in 0 until renderTotal) {
            val startAngle = -90f + segmentSweep * i + gap / 2f
            trackCanvas.drawArc(rect, startAngle, drawSweep, false, paint)
            if (i < doneCount) {
                progressCanvas.drawArc(rect, startAngle, drawSweep, false, paint)
            }
        }
    }

    Box(modifier = GlanceModifier.size(scaledBoxSizeDp.dp), contentAlignment = Alignment.BottomCenter) {
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
            onClick = actionRunCallback<QuickLogActionCallback>(
                actionParametersOf(
                    GroupUuidKey to groupUuid,
                    ScheduleTimeUuidKey to (row.scheduleTimeUuid ?: ""),
                    ScheduledAtKey to row.scheduledAt.toString(),
                    MedicationUuidKey to (row.medicationUuid ?: ""),
                    ArchivedGroupRowKey to row.isFromArchivedGroup,
                )
            ),
            rippleOverride = WidgetRoundedShape.Pill.rippleRes,
        )
    } else {
        GlanceModifier
    }
    val navigateModifier = if (navigateIntent != null) {
        GlanceModifier.clickable(
            onClick = actionStartActivityFromIntent(navigateIntent),
            rippleOverride = WidgetRoundedShape.Pill.rippleRes,
        )
    } else {
        GlanceModifier
    }

    when (row.status) {
        WidgetDoseStatus.DONE -> RoundedBackgroundBox(
            modifier = GlanceModifier.size(buttonSize),
            color = colors.primaryContainer,
            shape = WidgetRoundedShape.Pill,
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_check),
                contentDescription = null,
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(colors.onPrimaryContainer),
            )
        }

        WidgetDoseStatus.DUE_SOON -> RoundedBackgroundBox(
            modifier = GlanceModifier.size(buttonSize)
                .then(logModifier),
            color = colors.primary,
            shape = WidgetRoundedShape.Pill,
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_add),
                contentDescription = null,
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(colors.onPrimary),
            )
        }

        WidgetDoseStatus.OVERDUE -> RoundedBackgroundBox(
            modifier = GlanceModifier.size(buttonSize)
                .then(logModifier),
            color = colors.widgetControl,
            shape = WidgetRoundedShape.Pill,
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_add),
                contentDescription = null,
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
            )
        }

        WidgetDoseStatus.LOGGED_OUT_OF_WINDOW -> RoundedBackgroundBox(
            modifier = GlanceModifier.size(buttonSize),
            color = colors.widgetControl,
            shape = WidgetRoundedShape.Pill,
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_check),
                contentDescription = null,
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
            )
        }

        else -> RoundedBackgroundBox(
            modifier = GlanceModifier.size(buttonSize)
                .then(navigateModifier),
            color = colors.widgetControl,
            shape = WidgetRoundedShape.Pill,
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
        data =
            "hrttracker://widget-row-highlight/scheduled/${android.net.Uri.encode(stableKey)}".toUri()
        putExtra(EXTRA_HIGHLIGHT_KIND, HIGHLIGHT_KIND_SCHEDULED)
        putExtra(EXTRA_HIGHLIGHT_GROUP_UUID, groupUuid)
        row.scheduleTimeUuid?.let { putExtra(EXTRA_HIGHLIGHT_SCHEDULE_TIME_UUID, it) }
        putExtra(EXTRA_HIGHLIGHT_SCHEDULED_AT, row.scheduledAt.toString())
        row.medicationUuid?.let { putExtra(EXTRA_HIGHLIGHT_MEDICATION_UUID, it) }
    }
}

internal fun widgetDoseRowShowsManualTrailingIcon(
    row: WidgetDoseRow,
    hideMedicationDetails: Boolean,
): Boolean {
    return widgetDoseRowTrailingIconDrawableRes(
        row = row,
        hideMedicationDetails = hideMedicationDetails,
    ) == R.drawable.ic_edit_square
}

@DrawableRes
internal fun widgetDoseRowTrailingIconDrawableRes(
    row: WidgetDoseRow,
    hideMedicationDetails: Boolean,
): Int? {
    if (row.trailingText == null) return null
    return when {
        row.isImportedRecord -> R.drawable.ic_download
        row.isManualRecord -> R.drawable.ic_edit_square
        else -> null
    }
}

internal fun widgetDoseRowIndicatorGlyphSizeDp(@DrawableRes drawableRes: Int): Float =
    when (drawableRes) {
        R.drawable.ic_download -> WidgetDoseRowIndicatorSlotSizeDp - 2f
        R.drawable.ic_edit_square -> WidgetDoseRowIndicatorSlotSizeDp - 1f

        else -> WidgetDoseRowIndicatorSlotSizeDp
    }

@StringRes
private fun widgetDoseRowTrailingIconContentDescriptionRes(
    row: WidgetDoseRow,
    hideMedicationDetails: Boolean,
): Int? {
    if (widgetDoseRowTrailingIconDrawableRes(row, hideMedicationDetails) == null) return null
    return when {
        row.isImportedRecord -> R.string.external_tracker_record_indicator
        row.isManualRecord -> R.string.plan_entry_label_manual
        else -> null
    }
}

internal fun widgetDoseRowTrailingText(
    row: WidgetDoseRow,
    hideMedicationDetails: Boolean,
): String? {
    return when {
        widgetDoseRowTrailingIconDrawableRes(row, hideMedicationDetails) != null -> null
        hideMedicationDetails && row.isManualRecord -> null
        else -> row.trailingText
    }
}

@Composable
internal fun WidgetTrailingIcon(
    @DrawableRes drawableRes: Int,
    @StringRes contentDescriptionRes: Int,
    scale: Float,
    modifier: GlanceModifier = GlanceModifier,
) {
    val colors = LocalWidgetColors.current
    Box(
        modifier = modifier.size((WidgetDoseRowIndicatorSlotSizeDp * scale).dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(drawableRes),
            contentDescription = LocalContext.current.getString(contentDescriptionRes),
            modifier = GlanceModifier.size(
                (widgetDoseRowIndicatorGlyphSizeDp(drawableRes) * scale).dp
            ),
            colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
        )
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
        GlanceModifier.clickable(
            onClick = actionStartActivityFromIntent(highlightIntent),
            rippleOverride = WidgetRoundedShape.Card.rippleRes,
        )
    } else {
        GlanceModifier
    }
    val rowModifier = GlanceModifier
        .fillMaxWidth()
        .height((64f * scale).dp)

    RoundedBackgroundRow(
        modifier = rowModifier,
        color = colors.widgetContainer,
        shape = WidgetRoundedShape.Card,
        contentModifier = GlanceModifier
            .padding(horizontal = (16f * scale).dp)
            .then(rowClickModifier),
    ) {
        RoundedBackgroundBox(
            modifier = GlanceModifier
                .width((6f * scale).dp)
                .height((44f * scale).dp),
            color = groupAccentColor(row.colorKey, LocalWidgetForcedDark.current),
            shape = WidgetRoundedShape.Pill,
        ) {}

        Spacer(GlanceModifier.width((10f * scale).dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            val context = LocalContext.current
            val fullName = when {
                hideMedicationDetails && row.isManualRecord -> context.getString(R.string.widget_manual_record)
                hideMedicationDetails -> row.groupName
                else -> row.medicationName
            }
            val supportingText = listOfNotNull(
                row.routeLabel.takeIf(String::isNotBlank),
                row.doseText.takeIf(String::isNotBlank),
            ).joinToString(" · ")
            val showSupportingText = !hideMedicationDetails && supportingText.isNotBlank()
            Text(
                text = fullName,
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(
                    color = colors.onSurface,
                    fontSize = (18f * scale).sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            // Keep this node even when hidden. The synchronous GlanceRemoteViews path can reuse
            // layout IDs across updates, so toggling privacy must not change the RemoteViews tree.
            Text(
                text = supportingText.takeIf { showSupportingText }.orEmpty(),
                style = TextStyle(
                    color = colors.onSurfaceVariant,
                    fontSize = ((if (showSupportingText) 14f else 1f) * scale).sp,
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 1,
            )
        }

        val trailingIconRes = widgetDoseRowTrailingIconDrawableRes(
            row = row,
            hideMedicationDetails = hideMedicationDetails,
        )
        val trailingIconContentDescriptionRes = widgetDoseRowTrailingIconContentDescriptionRes(
            row = row,
            hideMedicationDetails = hideMedicationDetails,
        )
        val trailingText = widgetDoseRowTrailingText(
            row = row,
            hideMedicationDetails = hideMedicationDetails,
        )
        val showTrailingIcon = trailingIconRes != null
        val showTrailingText = trailingText != null

        Spacer(GlanceModifier.width(8.dp))

        if (row.isFromArchivedGroup) {
            Box(
                modifier = GlanceModifier.size((WidgetDoseRowIndicatorSlotSizeDp * scale).dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_archive),
                    contentDescription = LocalContext.current.getString(
                        R.string.archived_group_record_indicator
                    ),
                    modifier = GlanceModifier.size(
                        (widgetDoseRowIndicatorGlyphSizeDp(R.drawable.ic_archive) * scale).dp
                    ),
                    colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
                )
            }
            // Only separate the archive icon from the trailing text when that text is shown.
            // The text node is structurally retained even when empty, so an unconditional spacer
            // here would double up with the pre-button spacer and over-pad an icon-only row.
            Spacer(GlanceModifier.width((if (showTrailingText || showTrailingIcon) 8 else 0).dp))
        }
        if (trailingIconRes != null && trailingIconContentDescriptionRes != null) {
            WidgetTrailingIcon(
                drawableRes = trailingIconRes,
                contentDescriptionRes = trailingIconContentDescriptionRes,
                scale = scale,
            )
        }
        // Keep this node even when hidden. The synchronous GlanceRemoteViews path can reuse
        // layout IDs across updates, so toggling privacy must not change the RemoteViews tree
        // (a manual record's trailing label is hidden under hideMedicationDetails).
        Text(
            text = trailingText.orEmpty(),
            style = TextStyle(
                color = colors.onSurface,
                fontSize = ((if (showTrailingText) 16f else 1f) * scale).sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.width(8.dp))
        TrailingButton(row, showLogAction, highlightIntent)
    }
}
