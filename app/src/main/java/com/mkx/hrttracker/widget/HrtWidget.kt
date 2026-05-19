package com.mkx.hrttracker.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview as GlancePreview
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity as actionStartActivityFromIntent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll as glanceUpdateAll
import androidx.glance.background
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
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


// ── Widget ────────────────────────────────────────────────────────────────────

private suspend fun GlanceAppWidget.provideHrtContent(
    context: Context,
    content: @Composable (snapshot: WidgetSnapshotRecord?) -> Unit,
) {
    provideContent {
        val state = currentState<WidgetSnapshotState>()
        val snapshot = state.record?.takeIf { it.schemaVersion == WIDGET_SNAPSHOT_SCHEMA_VERSION }
        val adaptiveEnabled = snapshot?.adaptiveColorEnabled ?: true
        val alpha = snapshot?.widgetBackgroundAlpha?.coerceIn(0.5f, 1f) ?: 1.0f
        val scale = snapshot?.widgetContentScale?.coerceIn(0.7f, 1.3f) ?: 1.0f
        val widgetColors = if (adaptiveEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicWidgetColorScheme(context, alpha)
        } else {
            hardcodedWidgetColorScheme(alpha)
        }
        GlanceTheme {
            CompositionLocalProvider(
                LocalWidgetColors provides widgetColors,
                LocalWidgetScale provides scale,
                LocalWidgetAlpha provides alpha,
            ) {
                content(snapshot)
            }
        }
    }
}

class HrtWidgetMedium : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = HrtWidgetStateDefinition
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(DpSize(150.dp, 150.dp)))
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideHrtContent(context) { snapshot -> MediumWidgetContent(snapshot) }
    }
}

class HrtWidgetLarge : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = HrtWidgetStateDefinition
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(DpSize(330.dp, 150.dp)))
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideHrtContent(context) { snapshot -> LargeWidgetContent(snapshot) }
    }
}

suspend fun updateAllHrtWidgets(context: Context) {
    HrtWidgetMedium().glanceUpdateAll(context)
    HrtWidgetLarge().glanceUpdateAll(context)
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
    override val glanceAppWidget: GlanceAppWidget = HrtWidgetMedium()
}

class HrtWidgetLargeReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HrtWidgetLarge()
}


// ── Medium widget (2×2) ───────────────────────────────────────────────────────

@Composable
private fun MediumWidgetContent(snapshot: WidgetSnapshotRecord?) {
    val colors = LocalWidgetColors.current
    val context = LocalContext.current
    WidgetShell {
        if (isEmptySetup(snapshot)) {
            EmptyWidgetContent()
            return@WidgetShell
        }
        val record = checkNotNull(snapshot)
        val now = LocalDateTime.now()
        val zoneId = ZoneId.systemDefault()
        val e2Trend = record.pkProjection
            ?.toPkProjectionResult(now, zoneId)
            ?.toMainEstradiolTrend(now, zoneId)
        val e2DisplayUnit = BloodUnitKey.fromStorageValue(record.e2DisplayUnit) ?: BloodUnitKey.PG_ML
        val e2Text = e2Trend?.let { formatWidgetE2Text(it.currentConcentration, it.concentrationUnit, e2DisplayUnit) }
        val doneCount = record.doneCount
        val totalCount = record.totalCount
        val allDone = totalCount > 0 && doneCount >= totalCount

        val activeRow = record.doseRows
            .firstOrNull { it.contextChip != WidgetDoseChip.LAST_NIGHT && it.status != WidgetDoseStatus.DONE }
            ?: record.doseRows.firstOrNull { it.contextChip == WidgetDoseChip.COMING_UP }

        Column(modifier = GlanceModifier.fillMaxSize()) {
            // ── Top panel: progress ───────────────────────────────────────────
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetLabel(context.getString(R.string.widget_today))
                if (e2Text != null) {
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        text = e2Text,
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = (16f * LocalWidgetScale.current).sp,
                        ),
                        maxLines = 1,
                    )
                }
            }
            if (totalCount == 0) {
                Text(
                    text = context.getString(R.string.widget_no_doses_today),
                    style = TextStyle(
                        color = colors.onSurfaceVariant,
                        fontSize = (13f * LocalWidgetScale.current).sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = doneCount.toString(),
                        style = TextStyle(
                            color = if (allDone) colors.primary else colors.onSurface,
                            fontSize = (40f * LocalWidgetScale.current).sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(GlanceModifier.width(2.dp))
                    Text(
                        text = "/$totalCount ${context.getString(R.string.main_today_summary_done_label)}",
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = (18f * LocalWidgetScale.current).sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.height(4.dp))
                ProgressBar(doneCount = doneCount, totalCount = totalCount)
            }

            // ── Divider ───────────────────────────────────────────────────────
            Spacer(GlanceModifier.height(12.dp))

            // ── Bottom panel: next dose ───────────────────────────────────────
            if (allDone && activeRow == null) {
                Box(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = GlanceModifier.size(18.dp)
                                .background(colors.secondaryContainer)
                                .cornerRadius(9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = GlanceModifier.size(12.dp),
                                colorFilter = ColorFilter.tint(colors.onSecondaryContainer),
                            )
                        }
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            text = context.getString(R.string.widget_all_done),
                            style = TextStyle(
                                color = colors.primary,
                                fontSize = (13f * LocalWidgetScale.current).sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            } else if (activeRow != null) {
                val displayName = when {
                    record.hideMedicationDetails && activeRow.isManualRecord ->
                        context.getString(R.string.widget_manual_record)
                    record.hideMedicationDetails -> activeRow.groupName
                    else -> activeRow.medicationName
                }
                val highlightRow = if (record.hideMedicationDetails && !activeRow.isManualRecord) {
                    activeRow.copy(medicationUuid = null)
                } else {
                    activeRow
                }
                val highlightIntent = widgetRowHighlightIntent(context, highlightRow)
                val cardClickModifier = if (highlightIntent != null) {
                    GlanceModifier.clickable(actionStartActivityFromIntent(highlightIntent))
                } else {
                    GlanceModifier
                }
                Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height((64f * LocalWidgetScale.current).dp)
                            .background(colors.surfaceContainerLow)
                            .cornerRadius(10.dp)
                            .padding(horizontal = (16f * LocalWidgetScale.current).dp)
                            .then(cardClickModifier),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .width(6.dp)
                                .height((44f * LocalWidgetScale.current).dp)
                                .background(groupAccentColor(activeRow.colorKey))
                                .cornerRadius(999.dp),
                        ) {}
                        Spacer(GlanceModifier.width(10.dp))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = displayName,
                                modifier = GlanceModifier.fillMaxWidth(),
                                style = TextStyle(
                                    color = colors.onSurface,
                                    fontSize = (18f * LocalWidgetScale.current).sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                            )
                            if (!record.hideMedicationDetails) {
                                val supporting = listOfNotNull(
                                    activeRow.routeLabel.takeIf(String::isNotBlank),
                                    activeRow.doseText.takeIf(String::isNotBlank),
                                ).joinToString(" · ")
                                if (supporting.isNotBlank()) {
                                    Text(
                                        text = supporting,
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
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val showTrailingText = activeRow.trailingText != null &&
                            !(record.hideMedicationDetails && activeRow.isManualRecord)
                        if (showTrailingText) {
                            Text(
                                text = activeRow.trailingText,
                                style = TextStyle(
                                    color = colors.onSurfaceVariant,
                                    fontSize = (16f * LocalWidgetScale.current).sp,
                                ),
                                maxLines = 1,
                            )
                            Spacer(GlanceModifier.width(6.dp))
                        }
                        val isActionable = activeRow.groupUuid != null &&
                            (activeRow.status == WidgetDoseStatus.DUE_SOON ||
                                activeRow.status == WidgetDoseStatus.OVERDUE)
                        TrailingButton(activeRow, isActionable, highlightIntent)
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
    WidgetShell {
        if (isEmptySetup(snapshot)) {
            EmptyWidgetContent()
            return@WidgetShell
        }
        val record = checkNotNull(snapshot)
        val now = LocalDateTime.now()
        val zoneId = ZoneId.systemDefault()
        val e2Trend = record.pkProjection
            ?.toPkProjectionResult(now, zoneId)
            ?.toMainEstradiolTrend(now, zoneId)
        val e2DisplayUnit = BloodUnitKey.fromStorageValue(record.e2DisplayUnit) ?: BloodUnitKey.PG_ML
        val e2Text = e2Trend?.let { formatWidgetE2Text(it.currentConcentration, it.concentrationUnit, e2DisplayUnit) }
        val doneCount = record.doneCount
        val totalCount = record.totalCount

        // When hideMedicationDetails, collapse consecutive per-medication rows into per-group rows
        val displayRows: List<WidgetDoseRow> = if (record.hideMedicationDetails) {
            val regularRows = record.doseRows.filter { it.contextChip != WidgetDoseChip.COMING_UP }
            val collapsed = regularRows
                .filter { !it.isManualRecord }
                .groupBy { it.groupName to it.scheduledAt }
                .values
                .map { rows ->
                    val count = rows.size
                    val representativeStatus = when {
                        rows.all { it.status == WidgetDoseStatus.DONE } -> WidgetDoseStatus.DONE
                        rows.any { it.status == WidgetDoseStatus.OVERDUE } -> WidgetDoseStatus.OVERDUE
                        rows.any { it.status == WidgetDoseStatus.DUE_SOON } -> WidgetDoseStatus.DUE_SOON
                        rows.any { it.status == WidgetDoseStatus.UPCOMING } -> WidgetDoseStatus.UPCOMING
                        else -> WidgetDoseStatus.LOGGED_OUT_OF_WINDOW
                    }
                    val isActionable = representativeStatus == WidgetDoseStatus.DUE_SOON ||
                        representativeStatus == WidgetDoseStatus.OVERDUE
                    val identitySource = rows.firstOrNull { it.groupUuid != null }
                    val trailingText = when (representativeStatus) {
                        WidgetDoseStatus.DONE, WidgetDoseStatus.LOGGED_OUT_OF_WINDOW -> null
                        else -> rows.firstOrNull { it.status == representativeStatus }?.trailingText
                            ?: rows.firstOrNull { it.trailingText != null }?.trailingText
                    }
                    rows.first().copy(
                        medicationName = if (count > 1) "${rows.first().groupName} · $count" else rows.first().groupName,
                        status = representativeStatus,
                        trailingText = trailingText,
                        groupUuid = identitySource?.groupUuid,
                        scheduleTimeUuid = identitySource?.scheduleTimeUuid,
                        medicationUuid = null,
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
        val listItems = buildList<WidgetListItem> {
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

        Column(modifier = GlanceModifier.fillMaxSize().padding(4.dp)) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight().wrapContentHeight()) {
                    WidgetLabel("${context.getString(R.string.widget_today)} · $doneCount/$totalCount ${context.getString(R.string.main_today_summary_done_label)}")
                    Spacer(GlanceModifier.height(8.dp))
                    ProgressBar(doneCount = doneCount, totalCount = totalCount)
                }
                if (e2Text != null) {
                    Spacer(GlanceModifier.width((64f / LocalWidgetScale.current).dp))
                    Text(
                        text = e2Text,
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = (16f * LocalWidgetScale.current).sp,
                        ),
                    )
                }
            }

            Spacer(GlanceModifier.height(8.dp))

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

// ── Previews ──────────────────────────────────────────────────────────────────

private fun previewSnapshot(): WidgetSnapshotRecord {
    val now = LocalDateTime.now()
    return WidgetSnapshotRecord(
        schemaVersion = WIDGET_SNAPSHOT_SCHEMA_VERSION,
        zoneId = "UTC",
        doneCount = 1,
        totalCount = 3,
        manualCount = 0,
        hideMedicationDetails = false,
        adaptiveColorEnabled = false,
        widgetContentScale = 1.0f,
        widgetBackgroundAlpha = 1.0f,
        e2DisplayUnit = BloodUnitKey.PG_ML.storageValue,
        doseRows = listOf(
            WidgetDoseRow(
                medicationName = "Estradiol Valerate",
                groupName = "Estradiol",
                colorKey = MedicationGroupColorKey.ROSE,
                routeLabel = "IM injection",
                doseText = "4 mg",
                status = WidgetDoseStatus.DONE,
                scheduledAt = now.minusHours(2),
                trailingText = null,
                isManualRecord = false,
                contextChip = null,
                groupUuid = null,
                scheduleTimeUuid = null,
            ),
            WidgetDoseRow(
                medicationName = "Progesterone",
                groupName = "Progesterone",
                colorKey = MedicationGroupColorKey.INDIGO,
                routeLabel = "Oral",
                doseText = "200 mg",
                status = WidgetDoseStatus.DUE_SOON,
                scheduledAt = now.plusMinutes(30),
                trailingText = "+30 min",
                isManualRecord = false,
                contextChip = null,
                groupUuid = "g1",
                scheduleTimeUuid = "s1",
                medicationUuid = "m1",
            ),
            WidgetDoseRow(
                medicationName = "Spironolactone",
                groupName = "Spiro",
                colorKey = MedicationGroupColorKey.TEAL,
                routeLabel = "Oral",
                doseText = "100 mg",
                status = WidgetDoseStatus.UPCOMING,
                scheduledAt = now.plusHours(4),
                trailingText = now.plusHours(4).format(DateTimeFormatter.ofPattern("h:mm a")),
                isManualRecord = false,
                contextChip = WidgetDoseChip.COMING_UP,
                groupUuid = null,
                scheduleTimeUuid = null,
            ),
        ),
        pkProjection = null,
    )
}

@OptIn(ExperimentalGlancePreviewApi::class)
@GlancePreview(widthDp = 150, heightDp = 150)
@Composable
private fun MediumWidgetPreview() {
    CompositionLocalProvider(LocalWidgetColors provides hardcodedWidgetColorScheme()) {
        MediumWidgetContent(previewSnapshot())
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@GlancePreview(widthDp = 330, heightDp = 150)
@Composable
private fun LargeWidgetPreview() {
    CompositionLocalProvider(LocalWidgetColors provides hardcodedWidgetColorScheme()) {
        LargeWidgetContent(previewSnapshot())
    }
}
