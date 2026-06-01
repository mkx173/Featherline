package com.mkx.hrttracker.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import com.mkx.hrttracker.ui.theme.DefaultSeedColor
import com.mkx.hrttracker.ui.theme.resolveSeedColor
import com.mkx.hrttracker.util.currentAppLocale
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.action.actionStartActivity as actionStartActivityFromIntent
import androidx.glance.appwidget.updateAll as glanceUpdateAll
import androidx.glance.preview.Preview as GlancePreview


// ── Widget ────────────────────────────────────────────────────────────────────

private suspend fun GlanceAppWidget.provideHrtContent(
    context: Context,
    content: @Composable (snapshot: WidgetSnapshotRecord?) -> Unit,
) {
    provideContent {
        val state = currentState<WidgetSnapshotState>()
        val snapshot = state.record?.takeIf { it.schemaVersion == WIDGET_SNAPSHOT_SCHEMA_VERSION }
        HrtWidgetThemed(context, snapshot) { content(it) }
    }
}

// Shared theme + CompositionLocal scaffold for the widget content. Used both by the
// session-backed provideContent path and by the synchronous GlanceRemoteViews.compose
// path (pushHrtWidgets), so the two render identically.
@Composable
private fun HrtWidgetThemed(
    context: Context,
    snapshot: WidgetSnapshotRecord?,
    content: @Composable (snapshot: WidgetSnapshotRecord?) -> Unit,
) {
    val adaptiveEnabled = snapshot?.adaptiveColorEnabled ?: true
    val alpha = snapshot?.widgetBackgroundAlpha?.coerceIn(0.5f, 1f) ?: 1.0f
    val scale = snapshot?.widgetContentScale?.coerceIn(0.5f, 1.5f) ?: 1.0f
    val forcedDark = snapshot?.forcedDark
    // Resolve the live-rendered chrome strings against the snapshot's app language, not
    // the raw widget context. Below API 33 the widget process context stays on the system
    // locale, so without this the chrome ("TODAY", "DONE", E2 label) renders in the system
    // language while the snapshot's baked medication/dose strings are in the app language.
    val localizedContext = context.withLanguageTag(snapshot?.appLanguageTag)
    val seed = resolveSeedColor(localizedContext, adaptiveEnabled = adaptiveEnabled)
    val widgetColors = widgetColorScheme(seed, alpha, forcedDark)
    GlanceTheme {
        CompositionLocalProvider(
            // GlanceRemoteViews.compose does not seed LocalContext the way the session
            // path does, so provide it explicitly; harmless (same value) on the session path.
            LocalContext provides localizedContext,
            LocalWidgetColors provides widgetColors,
            LocalWidgetScale provides scale,
            LocalWidgetAlpha provides alpha,
            LocalWidgetForcedDark provides forcedDark,
        ) {
            content(snapshot)
        }
    }
}

// Returns a context whose resources resolve strings in [languageTag] (BCP-47). Mirrors
// WidgetSnapshotRepository.withAppLocale: createConfigurationContext + setLocale works on
// every supported API level, unlike reading the per-app locale back from the app context.
// Returns the receiver unchanged when the tag is null/blank or already the active locale.
private fun Context.withLanguageTag(languageTag: String?): Context {
    if (languageTag.isNullOrBlank()) return this
    val locale = Locale.forLanguageTag(languageTag)
    if (locale == currentAppLocale()) return this
    val config = Configuration(resources.configuration).apply { setLocale(locale) }
    return createConfigurationContext(config)
}

private suspend fun GlanceAppWidget.provideHrtPreviewContent(
    content: @Composable (snapshot: WidgetSnapshotRecord?) -> Unit,
) {
    provideContent {
        HrtPreviewContent(content)
    }
}

@Composable
private fun HrtPreviewContent(
    content: @Composable (snapshot: WidgetSnapshotRecord?) -> Unit,
) {
    val context = LocalContext.current
    GlanceTheme {
        CompositionLocalProvider(
            LocalWidgetColors provides widgetColorScheme(DefaultSeedColor),
            LocalWidgetScale provides WIDGET_PREVIEW_CONTENT_SCALE,
            LocalPreviewBaselineHeight provides WIDGET_BASELINE_REFERENCE_DP,
            LocalPreviewE2Text provides formatWidgetE2Text(
                currentConcentration = WIDGET_PREVIEW_E2_PG_PER_ML,
                concentrationUnit = PkConcentrationUnit.PG_PER_ML,
                displayUnit = BloodUnitKey.PG_ML,
            ),
        ) {
            content(previewSnapshot(context))
        }
    }
}

class HrtWidgetMedium : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = HrtWidgetStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(setOf(MEDIUM_WIDGET_PREVIEW_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideHrtContent(context) { snapshot -> MediumWidgetContent(snapshot) }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideHrtPreviewContent { snapshot -> MediumWidgetContent(snapshot) }
    }
}

class HrtWidgetLarge : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = HrtWidgetStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(setOf(LARGE_WIDGET_PREVIEW_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideHrtContent(context) { snapshot -> LargeWidgetContent(snapshot) }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideHrtPreviewContent { snapshot -> LargeWidgetContent(snapshot) }
    }
}

suspend fun updateAllHrtWidgets(context: Context) {
    coroutineScope {
        launch { HrtWidgetMedium().glanceUpdateAll(context) }
        launch { HrtWidgetLarge().glanceUpdateAll(context) }
    }
}

// Widget push entry point. Android 13+ uses a synchronous push that bypasses Glance's lazy session.
// Glance's update()/updateAll()
// only signal the session, whose recomposition is driven by the app process's frame
// clock — backgrounded, no frames are produced and the update stalls until the launcher
// next draws or the app relaunches. GlanceRemoteViews.compose() runs a one-shot,
// frame-clock-independent composition; pushing the result via AppWidgetManager updates
// the (foreground) launcher immediately, even from the background.
// API 26-32 use the original session-backed update path because the synchronous RemoteViews
// composition path is unreliable there.
//
// Tradeoff: we compose a single RemoteViews for the current orientation's size rather
// than Glance's automatic portrait/landscape variants. Acceptable here because content
// scale is frozen to the captured per-device baseline.
//
// A null record renders the empty-setup state, so clearWidgetSnapshot can reuse the same
// API-selected widget update path.
@OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)
suspend fun pushHrtWidgets(context: Context, record: WidgetSnapshotRecord?) {
    if (!shouldUseSynchronousWidgetPush()) {
        updateAllHrtWidgets(context)
        return
    }
    val appWidgetManager = AppWidgetManager.getInstance(context)
    coroutineScope {
        launch { pushHrtWidget(context, appWidgetManager, HrtWidgetMediumReceiver::class.java, record, isMedium = true) }
        launch { pushHrtWidget(context, appWidgetManager, HrtWidgetLargeReceiver::class.java, record, isMedium = false) }
    }
}

internal fun shouldUseSynchronousWidgetPush(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    sdkInt >= Build.VERSION_CODES.TIRAMISU

@OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)
private suspend fun pushHrtWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    receiverClass: Class<*>,
    record: WidgetSnapshotRecord?,
    isMedium: Boolean,
) {
    val appWidgetIds = runCatching {
        appWidgetManager.getAppWidgetIds(ComponentName(context, receiverClass))
    }.getOrElse { intArrayOf() }
    appWidgetIds.forEach { appWidgetId ->
        val size = currentWidgetSizeDp(context, appWidgetManager, appWidgetId, isMedium)
        val result = runCatching {
            GlanceRemoteViews().compose(context = context, size = size) {
                HrtWidgetThemed(context, record) { snapshot ->
                    if (isMedium) MediumWidgetContent(snapshot) else LargeWidgetContent(snapshot)
                }
            }
        }.getOrNull() ?: return@forEach
        runCatching { appWidgetManager.updateAppWidget(appWidgetId, result.remoteViews) }
    }
}

// The widget size for the current orientation, derived from the launcher's options.
// Portrait uses (minWidth, maxHeight); landscape uses (maxWidth, minHeight) — matching
// how SizeMode.Exact picks the size Glance composes against.
private fun currentWidgetSizeDp(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    isMedium: Boolean,
): DpSize {
    val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
    val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
    val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
    val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
    val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val widthDp = if (landscape) maxWidth else minWidth
    val heightDp = if (landscape) minHeight else maxHeight
    if (widthDp > 0 && heightDp > 0) {
        return DpSize(widthDp.dp, heightDp.dp)
    }
    // Options not yet reported (e.g. freshly added widget): fall back to a sane size so the
    // one-shot compose still produces a usable layout. The height matches the baseline
    // reference, so if this fallback happens to be the first render to capture the device
    // baseline, it resolves to scale 1.0 rather than a slightly-off value. Widths are
    // nominal — content fills the launcher-allocated cell width regardless.
    val fallbackHeight = WIDGET_BASELINE_REFERENCE_DP.dp
    return if (isMedium) DpSize(280.dp, fallbackHeight) else DpSize(483.dp, fallbackHeight)
}

// ── Group-aware row collapsing ────────────────────────────────────────────────

// Aggregates several per-medication rows that belong to the same group + scheduled slot
// into a single representative row. The output has medicationUuid = null so the widget's
// quick-log action targets the entire group, and trailingText is sourced from a member
// that still needs attention (so half-fulfilled groups don't display the addressed
// member's empty trailing text).
internal fun collapseToGroupRow(rows: List<WidgetDoseRow>): WidgetDoseRow {
    require(rows.isNotEmpty()) { "collapseToGroupRow requires at least one row." }
    val representativeStatus = when {
        rows.all { it.status == WidgetDoseStatus.DONE } -> WidgetDoseStatus.DONE
        rows.any { it.status == WidgetDoseStatus.OVERDUE } -> WidgetDoseStatus.OVERDUE
        rows.any { it.status == WidgetDoseStatus.DUE_SOON } -> WidgetDoseStatus.DUE_SOON
        rows.any { it.status == WidgetDoseStatus.UPCOMING } -> WidgetDoseStatus.UPCOMING
        else -> WidgetDoseStatus.LOGGED_OUT_OF_WINDOW
    }
    val identitySource = rows.firstOrNull { it.groupUuid != null } ?: rows.first()
    val trailingText = when (representativeStatus) {
        WidgetDoseStatus.DONE, WidgetDoseStatus.LOGGED_OUT_OF_WINDOW -> null
        else -> rows.firstOrNull { it.status == representativeStatus }?.trailingText
            ?: rows.firstOrNull { it.trailingText != null }?.trailingText
    }
    return rows.first().copy(
        status = representativeStatus,
        trailingText = trailingText,
        groupUuid = identitySource.groupUuid,
        scheduleTimeUuid = identitySource.scheduleTimeUuid,
        medicationUuid = null,
    )
}

private sealed interface WidgetRowGroupKey {
    data class GroupSlot(val groupUuid: String, val scheduledAt: LocalDateTime) : WidgetRowGroupKey
    data class SingleRow(val index: Int) : WidgetRowGroupKey
}

internal fun groupRowsByScheduledGroupSlot(rows: List<WidgetDoseRow>): List<List<WidgetDoseRow>> =
    rows.withIndex()
        .groupBy { (index, row) ->
            row.groupUuid?.let { WidgetRowGroupKey.GroupSlot(it, row.scheduledAt) }
                ?: WidgetRowGroupKey.SingleRow(index)
        }
        .values
        .map { indexedRows -> indexedRows.map { it.value } }

// ── State definition ──────────────────────────────────────────────────────────

internal object HrtWidgetStateDefinition : GlanceStateDefinition<WidgetSnapshotState> {
    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<WidgetSnapshotState> =
        context.widgetSnapshotDataStore

    override fun getLocation(context: Context, fileKey: String): File =
        File(context.filesDir, "datastore/widget_snapshot.pb")
}

// ── Receivers ─────────────────────────────────────────────────────────────────

class HrtWidgetMediumReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HrtWidgetMedium()
}

class HrtWidgetLargeReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HrtWidgetLarge()
}


// ── Per-device baseline scaling ───────────────────────────────────────────────

// Launcher cell sizes vary by device, so a 2-cell-tall widget renders at different
// dp heights on different launchers. We capture the first-render target-cell height
// per widget type and reuse it forever, so resize doesn't change the visual scale.
private const val MEDIUM_WIDGET_PREVIEW_WIDTH_DP = 306
private const val LARGE_WIDGET_PREVIEW_WIDTH_DP = 624
private const val WIDGET_PREVIEW_HEIGHT_DP = 276
private const val WIDGET_PREVIEW_CONTENT_SCALE = 0.6f
private const val WIDGET_PREVIEW_E2_PG_PER_ML = 120.0
private val MEDIUM_WIDGET_PREVIEW_SIZE = DpSize(MEDIUM_WIDGET_PREVIEW_WIDTH_DP.dp, WIDGET_PREVIEW_HEIGHT_DP.dp)
private val LARGE_WIDGET_PREVIEW_SIZE = DpSize(LARGE_WIDGET_PREVIEW_WIDTH_DP.dp, WIDGET_PREVIEW_HEIGHT_DP.dp)
private const val WIDGET_BASELINE_PREFS = "hrt_widget_baseline"
// Key suffix is bumped (_v2) when the capture logic changes, so installs carrying a
// baseline persisted by the old buggy logic discard it and re-capture cleanly.
private const val WIDGET_BASELINE_KEY_MEDIUM = "medium_height_dp_v2"
private const val WIDGET_BASELINE_KEY_LARGE = "large_height_dp_v2"
// Matches the preview viewport height: scale == 1.0 corresponds to the fully
// laid-out widget size seen in @GlancePreview / Live Preview.
private const val WIDGET_BASELINE_REFERENCE_DP = 276f
// Reject obviously bogus first-render sizes (e.g. transient 0dp loading frames)
// so we don't permanently lock the device baseline to nonsense.
private const val WIDGET_BASELINE_MIN_SANE_DP = 50f
private const val WIDGET_BASELINE_MAX_SANE_DP = 400f
// Floor for the device-baseline component (baseline / reference) so an unexpectedly
// small captured baseline can't collapse content to an illegible size. Sits below the
// normal placed-widget ratio (~0.4–0.58) so it never oversizes a real cell, while still
// catching pathologically tiny baselines. The user's own scale choice multiplies on top.
private const val WIDGET_MIN_BASELINE_SCALE_RATIO = 0.35f
private val LocalPreviewBaselineHeight = compositionLocalOf<Float?> { null }
// Pre-formatted E2 trend label shown in previews. Bypasses the real
// PkProjection path (whose windowing is unfriendly to fabricated data) so the
// preview can demonstrate the trend pill without seeding a full projection.
private val LocalPreviewE2Text = compositionLocalOf<String?> { null }

@Composable
private fun widgetScale(widgetKey: String): Float {
    val previewBaselineDp = LocalPreviewBaselineHeight.current
    val baselineDp = previewBaselineDp ?: run {
        val context = LocalContext.current
        val currentHeightDp = LocalSize.current.height.value
        val prefs = context.getSharedPreferences(WIDGET_BASELINE_PREFS, Context.MODE_PRIVATE)
        val storedDp = prefs.getFloat(widgetKey, 0f)
        if (shouldPersistWidgetBaselineHeight(storedDp, currentHeightDp)) {
            SideEffect {
                val existingDp = prefs.getFloat(widgetKey, 0f)
                val mergedDp = mergeWidgetBaselineHeightDp(existingDp, currentHeightDp)
                if (mergedDp != existingDp) {
                    prefs.edit { putFloat(widgetKey, mergedDp) }
                }
            }
        }
        resolveWidgetBaselineHeightDp(storedDp, currentHeightDp)
    }
    return widgetBaselineScaleRatio(baselineDp) * LocalWidgetScale.current
}

// The device-baseline component of the widget scale, floored so an unexpectedly small
// captured baseline can't collapse content to an illegible size. The user's own scale
// choice (LocalWidgetScale) multiplies on top of this.
internal fun widgetBaselineScaleRatio(baselineDp: Float): Float =
    (baselineDp / WIDGET_BASELINE_REFERENCE_DP).coerceAtLeast(WIDGET_MIN_BASELINE_SCALE_RATIO)

// SizeMode.Exact composes the widget once per size (portrait + landscape) in a single
// update, and every composition reads the stored baseline before any persists. Merging
// by max makes the persists order-independent so the tallest (portrait) height wins
// rather than whichever composition's SideEffect happens to run last.
internal fun mergeWidgetBaselineHeightDp(existingDp: Float, currentHeightDp: Float): Float =
    maxOf(existingDp, currentHeightDp)

// The device baseline is the launcher's portrait target-cell height, captured on the
// first update and reused forever. Once stored, later (resized) heights are ignored, so
// a resize relayouts the widget frame without rescaling its content.
internal fun resolveWidgetBaselineHeightDp(
    storedDp: Float,
    currentHeightDp: Float,
): Float {
    if (storedDp > 0f) {
        return storedDp
    }
    if (currentHeightDp !in WIDGET_BASELINE_MIN_SANE_DP..WIDGET_BASELINE_MAX_SANE_DP) {
        return WIDGET_BASELINE_REFERENCE_DP
    }
    return currentHeightDp
}

// Register a baseline persist while none is stored yet — i.e. on every size composition
// of the first update. Their persists merge by max, so the tallest sane height wins. A
// bogus frame is skipped so a later sane size still gets captured.
internal fun shouldPersistWidgetBaselineHeight(
    storedDp: Float,
    currentHeightDp: Float,
): Boolean =
    storedDp <= 0f && currentHeightDp in WIDGET_BASELINE_MIN_SANE_DP..WIDGET_BASELINE_MAX_SANE_DP

// ── Medium widget (2×2) ───────────────────────────────────────────────────────

@Composable
private fun MediumWidgetContent(snapshot: WidgetSnapshotRecord?) {
    val colors = LocalWidgetColors.current
    val context = LocalContext.current
    val scale = widgetScale(WIDGET_BASELINE_KEY_MEDIUM)
    WidgetShell(
        scale = scale,
        contentAlignment = Alignment.Center,
    ) {
        if (isEmptySetup(snapshot)) {
            EmptyWidgetContent(iconSize = 22f, backgroundColor = colors.secondary, foregroundColor = colors.onSecondary)
            return@WidgetShell
        }
        val record = checkNotNull(snapshot)
        val now = LocalDateTime.now()
        val zoneId = ZoneId.systemDefault()
        val e2Trend = record.pkProjection
            ?.toPkProjectionResult(now, zoneId)
            ?.toMainEstradiolTrend(now, zoneId)
        val e2DisplayUnit = BloodUnitKey.fromStorageValue(record.e2DisplayUnit) ?: BloodUnitKey.PG_ML
        val e2Text = LocalPreviewE2Text.current
            ?: e2Trend?.let { formatWidgetE2Text(it.currentConcentration, it.concentrationUnit, e2DisplayUnit) }
        val doneCount = record.doneCount
        val totalCount = record.totalCount
        // Only treat the day as "nothing scheduled" when there are no rows at all to
        // surface — including last-night carry-overs and tonight's coming-up entries,
        // which aren't counted in totalCount but still represent real activity.
        val nothingScheduledToday = record.doseRows.isEmpty()

        // Treat LOGGED_OUT_OF_WINDOW as addressed for activeRow/all-done: the slot has an
        // entry attached (even though it's outside the fulfillment window), so prompting
        // the user to log it again would just produce another out-of-window record.
        fun WidgetDoseRow.isAddressed(): Boolean =
            status == WidgetDoseStatus.DONE || status == WidgetDoseStatus.LOGGED_OUT_OF_WINDOW

        // Past the 1-hour grace period — logging from the widget would only create an
        // out-of-window record, so we drop these from active-row selection and from the
        // "still actionable" check that gates the final-state badge.
        fun WidgetDoseRow.isExpired(): Boolean = status == WidgetDoseStatus.OVERDUE

        // Group scheduled today rows by (groupUuid, scheduledAt) so the medium widget's
        // single action button logs the entire group rather than one medication at a time.
        // groupName is not unique across groups; using groupUuid guarantees we collapse
        // only true siblings. A group only surfaces while it has at least one member
        // that's still actionable (neither addressed nor past its grace period).
        val activeScheduledGroup: List<WidgetDoseRow>? = groupRowsByScheduledGroupSlot(
            record.doseRows.filter { it.contextChip != WidgetDoseChip.LAST_NIGHT && !it.isManualRecord }
        )
            .sortedBy { it.first().scheduledAt }
            .firstOrNull { rows -> rows.any { !it.isAddressed() && !it.isExpired() } }
        val activeRow: WidgetDoseRow? = activeScheduledGroup?.let { collapseToGroupRow(it) }
            ?: record.doseRows.firstOrNull { it.contextChip == WidgetDoseChip.COMING_UP }
        val isMultiMedGroup = (activeScheduledGroup?.size ?: 1) > 1
        val todayRows = record.doseRows.filter {
            it.contextChip != WidgetDoseChip.LAST_NIGHT && it.contextChip != WidgetDoseChip.COMING_UP
        }
        // Nothing left to act on today (every slot is either addressed or expired).
        // The empty-schedule case is handled separately via nothingScheduledToday so
        // we don't conflate it with a perfect-adherence "all in window" finish.
        val noActionableRemaining = totalCount > 0 &&
                todayRows.none { !it.isAddressed() && !it.isExpired() }
        // Three final-state variants:
        //   allInWindow → every slot fulfilled within its window (perfect adherence).
        //   everythingLogged → every slot has a log attached, but at least one is
        //     out-of-window (took the dose, just timed imperfectly). No missed slots.
        //   otherwise → at least one OVERDUE slot with no log (missed).
        val allInWindow = noActionableRemaining && todayRows.all { it.status == WidgetDoseStatus.DONE }
        val everythingLogged = noActionableRemaining && todayRows.none { it.isExpired() }

        Column(modifier = GlanceModifier.fillMaxSize()) {
            // ── Top panel: progress ───────────────────────────────────────────
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                WidgetLabel(context.getString(R.string.widget_today))
                if (e2Text != null) {
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        text = e2Text,
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = (16f * scale).sp,
                        ),
                        maxLines = 1,
                    )
                }
            }
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = (-6 * scale).dp, bottom = (-4 * scale).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = doneCount.toString(),
                        style = TextStyle(
                            color = if (allInWindow) colors.primary else colors.onSurface,
                            fontSize = (42f * scale).sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(GlanceModifier.width(2.dp))
                    Text(
                        text = "/$totalCount ${context.getString(R.string.main_today_summary_done_label)}",
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = (18f * scale).sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.defaultWeight())
                ProgressRing(doneCount = doneCount, totalCount = totalCount)
            }

            // ── Bottom panel: next dose ───────────────────────────────────────
            if ((noActionableRemaining || nothingScheduledToday) && activeRow == null) {
                val useCelebrationColor = allInWindow || nothingScheduledToday
                val badgeBackground = if (useCelebrationColor) colors.primary else colors.secondary
                val badgeForeground = if (useCelebrationColor) colors.onPrimary else colors.onSecondary
                Box(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundedBackgroundBox(
                            modifier = GlanceModifier.size((30f * scale).dp),
                            color = badgeBackground,
                            shape = WidgetRoundedShape.Pill,
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                provider = ImageProvider(
                                    when {
                                        nothingScheduledToday || allInWindow -> R.drawable.ic_check
                                        everythingLogged -> R.drawable.ic_done_all
                                        else -> R.drawable.ic_exclamation
                                    }
                                ),
                                contentDescription = null,
                                modifier = GlanceModifier.size((22f * scale).dp),
                                colorFilter = ColorFilter.tint(badgeForeground),
                            )
                        }
                        Spacer(GlanceModifier.height((6f * scale).dp))
                        Text(
                            text = context.getString(
                                when {
                                    nothingScheduledToday -> R.string.widget_no_doses_today
                                    allInWindow -> R.string.widget_all_done
                                    everythingLogged -> R.string.widget_all_logged
                                    else -> R.string.widget_nothing_more_today
                                }
                            ),
                            style = TextStyle(
                                color = colors.onSurface,
                                fontSize = (18f * scale).sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            } else if (activeRow != null) {
                val displayName = when {
                    record.hideMedicationDetails && activeRow.isManualRecord ->
                        context.getString(R.string.widget_manual_record)
                    isMultiMedGroup || record.hideMedicationDetails -> activeRow.groupName
                    else -> activeRow.medicationName
                }
                // Preserve the underlying medicationUuid only for navigation, only when we're
                // actually showing a single specific medication. activeRow itself keeps
                // medicationUuid = null so taps on the action button log the whole group.
                val highlightIntentRow = if (
                    !isMultiMedGroup &&
                    !record.hideMedicationDetails &&
                    !activeRow.isManualRecord
                ) {
                    activeRow.copy(medicationUuid = activeScheduledGroup?.firstOrNull()?.medicationUuid)
                } else {
                    activeRow
                }
                val highlightIntent = widgetRowHighlightIntent(context, highlightIntentRow)
                val cardClickModifier = if (highlightIntent != null) {
                    GlanceModifier.clickable(
                        onClick = actionStartActivityFromIntent(highlightIntent),
                        rippleOverride = WidgetRoundedShape.Card.rippleRes,
                    )
                } else {
                    GlanceModifier
                }
                Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    SectionHeader(text = context.getString(R.string.widget_upcoming), topPadding = 0.dp)
                    Spacer(modifier = GlanceModifier.height((4 * scale).dp))
                    RoundedBackgroundRow(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height((64f * scale).dp),
                        color = colors.surfaceContainerLow,
                        shape = WidgetRoundedShape.Card,
                        contentModifier = GlanceModifier
                            .padding(horizontal = (16f * scale).dp)
                            .then(cardClickModifier),
                    ) {
                        RoundedBackgroundBox(
                            modifier = GlanceModifier
                                .width((6f * scale).dp)
                                .height((44f * scale).dp),
                            color = groupAccentColor(activeRow.colorKey, LocalWidgetForcedDark.current),
                            shape = WidgetRoundedShape.Pill,
                        ) {}
                        Spacer(GlanceModifier.width((10f * scale).dp))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            val supporting = listOfNotNull(
                                activeRow.routeLabel.takeIf(String::isNotBlank),
                                activeRow.doseText.takeIf(String::isNotBlank),
                            ).joinToString(" · ")
                            val showSupporting = !record.hideMedicationDetails &&
                                    !isMultiMedGroup &&
                                    supporting.isNotBlank()
                            Text(
                                text = displayName,
                                modifier = GlanceModifier.fillMaxWidth(),
                                style = TextStyle(
                                    color = colors.onSurface,
                                    fontSize = (18f * scale).sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                            )
                            // Keep this node even when hidden. The synchronous GlanceRemoteViews
                            // path can reuse layout IDs across updates, so privacy toggles must
                            // not change the RemoteViews tree.
                            Text(
                                text = supporting.takeIf { showSupporting }.orEmpty(),
                                style = TextStyle(
                                    color = colors.onSurfaceVariant,
                                    fontSize = ((if (showSupporting) 14f else 1f) * scale).sp,
                                    fontWeight = FontWeight.Normal,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (activeRow.isFromArchivedGroup) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_archive),
                                contentDescription = context.getString(R.string.archived_group_record_indicator),
                                modifier = GlanceModifier.size((20f * scale).dp),
                                colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
                            )
                            Spacer(GlanceModifier.width(8.dp))
                        }
                        val showTrailingText = activeRow.trailingText != null &&
                                !(record.hideMedicationDetails && activeRow.isManualRecord)
                        if (showTrailingText) {
                            Text(
                                text = activeRow.trailingText,
                                style = TextStyle(
                                    color = colors.onSurface,
                                    fontSize = (18f * scale).sp,
                                ),
                                maxLines = 1,
                            )
                            Spacer(GlanceModifier.width(8.dp))
                        }
                        val isActionable = activeRow.groupUuid != null &&
                                (activeRow.status == WidgetDoseStatus.DUE_SOON ||
                                        activeRow.status == WidgetDoseStatus.OVERDUE)
                        TrailingButton(
                            row = activeRow,
                            showLogAction = isActionable,
                            navigateIntent = highlightIntent,
                            buttonSizeDp = 44f,
                            iconSizeDp = 32f,
                            arrowIconSizeDp = 26f,
                        )
                    }
                }
            }
        }
    }
}

// ── Large widget (4×3) ────────────────────────────────────────────────────────

@Composable
private fun LargeWidgetContent(snapshot: WidgetSnapshotRecord?) {
    val colors = LocalWidgetColors.current
    val context = LocalContext.current
    val scale = widgetScale(WIDGET_BASELINE_KEY_LARGE)
    WidgetShell(
        scale = scale,
        contentAlignment = Alignment.Center,
    ) {
        if (isEmptySetup(snapshot)) {
            EmptyWidgetContent(iconSize = 22f, backgroundColor = colors.secondary, foregroundColor = colors.onSecondary)
            return@WidgetShell
        }
        val record = checkNotNull(snapshot)
        val now = LocalDateTime.now()
        val zoneId = ZoneId.systemDefault()
        val e2Trend = record.pkProjection
            ?.toPkProjectionResult(now, zoneId)
            ?.toMainEstradiolTrend(now, zoneId)
        val e2DisplayUnit = BloodUnitKey.fromStorageValue(record.e2DisplayUnit) ?: BloodUnitKey.PG_ML
        val e2Text = LocalPreviewE2Text.current
            ?: e2Trend?.let { formatWidgetE2Text(it.currentConcentration, it.concentrationUnit, e2DisplayUnit) }
        val doneCount = record.doneCount
        val totalCount = record.totalCount
        // Only treat the day as "nothing scheduled" when there are no rows at all to
        // surface — including last-night carry-overs and tonight's coming-up entries,
        // which aren't counted in totalCount but still represent real activity.
        val nothingScheduledToday = record.doseRows.isEmpty()
        // Match the medium widget's "addressed" definition so the badge appears whenever
        // every today slot is either DONE or LOGGED_OUT_OF_WINDOW (no actionable rows left).
//        fun WidgetDoseRow.isAddressed(): Boolean =
//            status == WidgetDoseStatus.DONE || status == WidgetDoseStatus.LOGGED_OUT_OF_WINDOW
//        val allDone = totalCount > 0 &&
//            record.doseRows.none {
//                it.contextChip != WidgetDoseChip.LAST_NIGHT &&
//                    it.contextChip != WidgetDoseChip.COMING_UP &&
//                    !it.isAddressed()
//            }

        // When hideMedicationDetails, collapse same-(group, slot) rows into per-group rows so
        // the displayed name reflects group identity rather than a single medication.
        // Keyed on groupUuid (not groupName) since group names are not unique.
        val displayRows: List<WidgetDoseRow> = if (record.hideMedicationDetails) {
            val regularRows = record.doseRows.filter { it.contextChip != WidgetDoseChip.COMING_UP }
            val collapsed = regularRows
                .filter { !it.isManualRecord }
                .let(::groupRowsByScheduledGroupSlot)
                .map { rows ->
                    val count = rows.size
                    collapseToGroupRow(rows).copy(
                        medicationName = if (count > 1) {
                            "${rows.first().groupName} · $count"
                        } else {
                            rows.first().groupName
                        },
                    )
                }
            val manualRows = regularRows.filter { it.isManualRecord }
            val comingUpRows = record.doseRows
                .filter { it.contextChip == WidgetDoseChip.COMING_UP }
            (collapsed + manualRows).sortedBy { it.scheduledAt } + comingUpRows
        } else {
            record.doseRows
        }

        val lastNightRows = displayRows.filter { it.contextChip == WidgetDoseChip.LAST_NIGHT }
        val todayRows = displayRows.filter { it.contextChip == null }
        val comingUpRows = displayRows.filter { it.contextChip == WidgetDoseChip.COMING_UP }
        val listItems = buildList {
            if (lastNightRows.isNotEmpty()) {
                add(WidgetListItem.Header(context.getString(R.string.widget_last_night)))
                lastNightRows.forEach { add(WidgetListItem.Row(it)) }
            }
            if (todayRows.isNotEmpty()) {
                if (lastNightRows.isNotEmpty()) {
                    add(WidgetListItem.Header(context.getString(R.string.widget_today)))
                }
                todayRows.forEach { add(WidgetListItem.Row(it)) }
            }
            if (comingUpRows.isNotEmpty()) {
                add(WidgetListItem.Header(context.getString(R.string.widget_tonight)))
                comingUpRows.forEach { add(WidgetListItem.Row(it)) }
            }
        }

        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight().wrapContentHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WidgetLabel("${context.getString(R.string.widget_today)} · $doneCount/$totalCount ${context.getString(R.string.main_today_summary_done_label)}")
//                        if (allDone) {
//                            Spacer(GlanceModifier.width((8f * scale).dp))
//                            Image(
//                                provider = ImageProvider(R.drawable.ic_check_circle_filled),
//                                contentDescription = null,
//                                modifier = GlanceModifier.size((22f * scale).dp),
//                                colorFilter = ColorFilter.tint(colors.primary),
//                            )
//                        }
                    }
                    Spacer(GlanceModifier.height((8 * scale).dp))
                    ProgressBar(doneCount = doneCount, totalCount = totalCount)
                }
                if (e2Text != null) {
                    Spacer(GlanceModifier.width((64f * scale).dp))
                    Text(
                        text = e2Text,
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = (16f * scale).sp,
                        ),
                    )
                }
            }

            val largeWidgetProgressBarBottomPadding = if (listItems.firstOrNull() is WidgetListItem.Header) 8 else 12
            Spacer(GlanceModifier.height((largeWidgetProgressBarBottomPadding * scale).dp))

            if (nothingScheduledToday) {
                Box(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundedBackgroundBox(
                            modifier = GlanceModifier.size((30f * scale).dp),
                            color = colors.primary,
                            shape = WidgetRoundedShape.Pill,
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = GlanceModifier.size((22f * scale).dp),
                                colorFilter = ColorFilter.tint(colors.onPrimary),
                            )
                        }
                        Spacer(GlanceModifier.height((6f * scale).dp))
                        Text(
                            text = context.getString(R.string.widget_no_doses_today),
                            style = TextStyle(
                                color = colors.onSurface,
                                fontSize = (18f * scale).sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    itemsIndexed(
                        items = listItems,
                        itemId = { index, _ -> (index + 1).toLong() },
                    ) { index, item ->
                        Column(modifier = GlanceModifier.fillMaxWidth().padding(top = if (index > 0) 2.dp else 0.dp)) {
                            when (item) {
                                is WidgetListItem.Header -> SectionHeader(item.text, topPadding = if (index == 0) 0.dp else 4.dp)
                                is WidgetListItem.Row -> DoseRow(
                                    row = item.row,
                                    showLogAction = item.row.status == WidgetDoseStatus.DUE_SOON ||
                                            item.row.status == WidgetDoseStatus.OVERDUE,
                                    hideMedicationDetails = record.hideMedicationDetails,
                                    highlightIntent = widgetRowHighlightIntent(context, item.row),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

private fun previewSnapshot(context: Context): WidgetSnapshotRecord {
    val morningDoseTime = LocalDateTime.of(2026, 1, 1, 9, 0)
    val eveningDoseTime = LocalDateTime.of(2026, 1, 1, 21, 0)
    val timeFormatter = localizedShortTimeFormatter(
        Locale.getDefault(),
        uses24HourFormat = android.text.format.DateFormat.is24HourFormat(context),
    )
    val estradiolName = context.getString(R.string.medication_name_estradiol)
    val progesteroneName = context.getString(R.string.settings_calibration_analyte_prog)
    val cpaName = context.getString(R.string.medication_name_cyproterone_acetate)
    val oralLabel = context.getString(R.string.medication_application_oral)
    return WidgetSnapshotRecord(
        schemaVersion = WIDGET_SNAPSHOT_SCHEMA_VERSION,
        zoneId = "UTC",
        anchorDateEpochDay = morningDoseTime.toLocalDate().toEpochDay(),
        doneCount = 1,
        totalCount = 3,
        manualCount = 0,
        hasActiveGroups = true,
        hideMedicationDetails = false,
        adaptiveColorEnabled = false,
        widgetContentScale = 1.0f,
        widgetBackgroundAlpha = 1.0f,
        e2DisplayUnit = BloodUnitKey.PG_ML.storageValue,
        appLanguageTag = context.currentAppLocale().toLanguageTag(),
        forcedDark = null,
        doseRows = listOf(
            WidgetDoseRow(
                medicationName = cpaName,
                groupName = cpaName,
                colorKey = MedicationGroupColorKey.TEAL,
                routeLabel = oralLabel,
                doseText = context.getString(R.string.widget_preview_dose_text_cpa),
                status = WidgetDoseStatus.DONE,
                scheduledAt = morningDoseTime,
                trailingText = null,
                isManualRecord = false,
                contextChip = null,
                groupUuid = "00000000-0000-0000-0000-000000000101",
                scheduleTimeUuid = "00000000-0000-0000-0000-000000000201",
                medicationUuid = "00000000-0000-0000-0000-000000000301",
            ),
            WidgetDoseRow(
                medicationName = context.getString(R.string.medication_name_estradiol_valerate),
                groupName = estradiolName,
                colorKey = MedicationGroupColorKey.ROSE,
                routeLabel = oralLabel,
                doseText = "2 mg",
                status = WidgetDoseStatus.DUE_SOON,
                scheduledAt = morningDoseTime,
                trailingText = morningDoseTime.format(timeFormatter),
                isManualRecord = false,
                contextChip = null,
                groupUuid = "00000000-0000-0000-0000-000000000102",
                scheduleTimeUuid = "00000000-0000-0000-0000-000000000202",
                medicationUuid = "00000000-0000-0000-0000-000000000302",
            ),
            WidgetDoseRow(
                medicationName = progesteroneName,
                groupName = progesteroneName,
                colorKey = MedicationGroupColorKey.INDIGO,
                routeLabel = oralLabel,
                doseText = "200 mg",
                status = WidgetDoseStatus.UPCOMING,
                scheduledAt = eveningDoseTime,
                trailingText = eveningDoseTime.format(timeFormatter),
                isManualRecord = false,
                contextChip = null,
                groupUuid = "00000000-0000-0000-0000-000000000103",
                scheduleTimeUuid = "00000000-0000-0000-0000-000000000203",
                medicationUuid = "00000000-0000-0000-0000-000000000303",
            ),
        ),
        pkProjection = null,
    )
}

@OptIn(ExperimentalGlancePreviewApi::class)
@GlancePreview(widthDp = MEDIUM_WIDGET_PREVIEW_WIDTH_DP, heightDp = WIDGET_PREVIEW_HEIGHT_DP)
@Composable
private fun MediumWidgetPreview() {
    HrtPreviewContent { snapshot -> MediumWidgetContent(snapshot) }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@GlancePreview(widthDp = LARGE_WIDGET_PREVIEW_WIDTH_DP, heightDp = WIDGET_PREVIEW_HEIGHT_DP)
@Composable
private fun LargeWidgetPreview() {
    HrtPreviewContent { snapshot -> LargeWidgetContent(snapshot) }
}
