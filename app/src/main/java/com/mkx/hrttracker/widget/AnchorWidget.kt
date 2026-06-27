package com.mkx.hrttracker.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.model.journal.dayCount
import com.mkx.hrttracker.ui.journal.anchorIconRes
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.glance.appwidget.updateAll as glanceUpdateAll

// 2×1 anchor widget. Per-instance state is just the anchorId (Task 4); name/icon/palette/
// date are read live from the journal at composition. Chrome (colour/scale/alpha/dark)
// comes from the shared widget appearance via HrtWidgetThemed.
internal val ANCHOR_WIDGET_PREVIEW_SIZE = DpSize(306.dp, 138.dp)

class HrtAnchorWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact
    override val previewSizeMode: PreviewSizeMode =
        SizeMode.Responsive(setOf(ANCHOR_WIDGET_PREVIEW_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId =
            runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
        val entry = EntryPointAccessors
            .fromApplication(context, WidgetEntryPoint::class.java)
        val appearanceRepository = entry.widgetAppearanceRepository()
        val journalRepository = entry.journalRepository()
        val initialAppearance = runCatching {
            appearanceRepository.currentEffective(appWidgetId)
        }.getOrDefault(WidgetAppearance.Default)

        provideContent {
            val anchorId = currentState(ANCHOR_ID_KEY)
            val appearanceFlow = remember(appWidgetId) {
                appearanceRepository.effectiveFor(appWidgetId)
            }
            val appearance by appearanceFlow.collectAsState(initial = initialAppearance)
            val anchors by remember(journalRepository) { journalRepository.observeTrackedDates() }
                .collectAsState(initial = journalRepository.getCachedTrackedDates().orEmpty())
            val anchor = anchorId?.let { id -> anchors.firstOrNull { it.id == id } }
            HrtWidgetThemed(context, snapshot = null, appearance = appearance) {
                AnchorWidgetContent(
                    anchor = anchor,
                    hasSelection = anchorId != null,
                    appWidgetId = appWidgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID,
                )
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            GlanceTheme {
                // A neutral preview anchor for the launcher widget picker.
                AnchorWidgetContent(anchor = null, hasSelection = false)
            }
        }
    }
}

class HrtAnchorWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HrtAnchorWidget()
}

suspend fun updateAllAnchorWidgets(context: Context) {
    HrtAnchorWidget().glanceUpdateAll(context)
}

// glyph · name · big day count · since/planned-for line. Empty / removed states route to
// the config Activity (re-pick) or the app. Rolls to full days past a year (resolved
// micro-decision); only the shortcut icon rolls to "Ny".
@Composable
internal fun AnchorWidgetContent(
    anchor: TrackedDate?,
    hasSelection: Boolean,
    appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
) {
    val colors = LocalWidgetColors.current
    val context = LocalContext.current

    if (anchor == null) {
        // No selection yet, or the selected anchor was deleted: distinct copy, both tap to
        // the config Activity for this instance so the user can (re)choose.
        val message = if (hasSelection) {
            context.getString(R.string.anchor_widget_removed)
        } else {
            context.getString(R.string.anchor_widget_empty)
        }
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .padding(16.dp)
                .clickable(actionStartActivity(anchorReconfigureIntent(context, appWidgetId))),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 15.sp),
            )
        }
        return
    }

    val today = LocalDate.now()
    val count = dayCount(date = anchor.date, today = today)
    val dateText = anchor.date.format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    )
    val directionLine = if (count.isFuture) {
        context.getString(R.string.anchor_widget_planned_for, dateText)
    } else {
        context.getString(R.string.anchor_widget_since, dateText)
    }
    val daysText = context.resources.getQuantityString(
        R.plurals.anchor_widget_days, count.magnitude.toInt(), count.magnitude.toInt()
    )

    Row(
        modifier = GlanceModifier.fillMaxSize()
            .padding(16.dp)
            .clickable(actionStartActivity(anchorOpenMilestonesIntent(context))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(anchorIconRes(anchor.icon)),
            contentDescription = null,
            modifier = GlanceModifier.size(36.dp),
            colorFilter = ColorFilter.tint(
                groupAccentColor(anchor.palette, LocalWidgetForcedDark.current)
            ),
        )
        Spacer(GlanceModifier.width(14.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = anchor.name,
                style = TextStyle(color = colors.onSurface, fontSize = 15.sp,
                    fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Text(
                text = daysText,
                style = TextStyle(color = colors.onSurface, fontSize = 26.sp,
                    fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = directionLine,
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }
}

// Opens this widget instance's config so the user can pick/replace its anchor. Falls back
// to the app's Milestones screen when no valid id is available (e.g. the picker preview).
fun anchorReconfigureIntent(context: Context, appWidgetId: Int): android.content.Intent =
    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
        anchorOpenMilestonesIntent(context)
    } else {
        android.content.Intent(context, WidgetConfigActivity::class.java).apply {
            // WidgetConfigActivity is exported with the APPWIDGET_CONFIGURE intent-filter,
            // so launching it directly with the id reconfigures this instance.
            action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
