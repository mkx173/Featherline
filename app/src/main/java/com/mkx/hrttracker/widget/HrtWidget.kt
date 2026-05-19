package com.mkx.hrttracker.widget

import android.content.Context
import android.os.Build
import com.materialkolor.dynamicColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
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
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll as glanceUpdateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
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
import androidx.glance.unit.ColorProvider
import com.mkx.hrttracker.MainActivity
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ── Widget color scheme ───────────────────────────────────────────────────────

private data class WidgetColorScheme(
    val primary: ColorProvider,
    val secondaryContainer: ColorProvider,
    val onSecondaryContainer: ColorProvider,
    val tertiaryContainer: ColorProvider,
    val onTertiaryContainer: ColorProvider,
    val surfaceVariant: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val surfaceContainerLow: ColorProvider,
    val surface: ColorProvider,
    val onSurface: ColorProvider,
    val outline: ColorProvider,
    val outlineVariant: ColorProvider,
)

private fun hardcodedWidgetColorScheme(): WidgetColorScheme {
    fun p(day: Color, night: Color) = DayNightColorProvider(day, night)
    return WidgetColorScheme(
        primary             = p(Color(0xFF8D4959), Color(0xFFFFB1C0)),
        secondaryContainer  = p(Color(0xFFFFD9DF), Color(0xFF5B3F44)),
        onSecondaryContainer = p(Color(0xFF5B3F44), Color(0xFFFFD9DF)),
        tertiaryContainer   = p(Color(0xFFFFDCBC), Color(0xFF5F401D)),
        onTertiaryContainer = p(Color(0xFF5F401D), Color(0xFFFFDCBC)),
        surfaceVariant      = p(Color(0xFFF5E4E6), Color(0xFF312829)),
        onSurfaceVariant    = p(Color(0xFF524345), Color(0xFFD6C2C4)),
        surfaceContainerLow = p(Color(0xFFFFF0F1), Color(0xFF22191B)),
        surface             = p(Color(0xFFFFF8F7), Color(0xFF191113)),
        onSurface           = p(Color(0xFF22191B), Color(0xFFEFDEE0)),
        outline             = p(Color(0xFF847375), Color(0xFF9F8C8F)),
        outlineVariant      = p(Color(0xFFD6C2C4), Color(0xFF524345)),
    )
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
private fun dynamicWidgetColorScheme(context: Context): WidgetColorScheme {
    val seed = Color(context.getColor(android.R.color.system_accent1_500))
    val light = dynamicColorScheme(seed, isDark = false)
    val dark  = dynamicColorScheme(seed, isDark = true)
    fun p(l: Color, d: Color) = DayNightColorProvider(l, d)
    return WidgetColorScheme(
        primary             = p(light.primary,              dark.primary),
        secondaryContainer  = p(light.secondaryContainer,   dark.secondaryContainer),
        onSecondaryContainer = p(light.onSecondaryContainer, dark.onSecondaryContainer),
        tertiaryContainer   = p(light.tertiaryContainer,    dark.tertiaryContainer),
        onTertiaryContainer = p(light.onTertiaryContainer,  dark.onTertiaryContainer),
        surfaceVariant      = p(light.surfaceVariant,       dark.surfaceVariant),
        onSurfaceVariant    = p(light.onSurfaceVariant,     dark.onSurfaceVariant),
        surfaceContainerLow = p(light.surfaceContainerLow,  dark.surfaceContainerLow),
        surface             = p(light.surface,              dark.surface),
        onSurface           = p(light.onSurface,            dark.onSurface),
        outline             = p(light.outline,              dark.outline),
        outlineVariant      = p(light.outlineVariant,       dark.outlineVariant),
    )
}

private val LocalWidgetColors = compositionLocalOf { hardcodedWidgetColorScheme() }

private val colorGroupRose = DayNightColorProvider(day = Color(0xFFCE2C31), night = Color(0xFFFF8A88))
private val colorGroupCoral = DayNightColorProvider(day = Color(0xFFD14E00), night = Color(0xFFFF9B52))
private val colorGroupAmber = DayNightColorProvider(day = Color(0xFFA06E00), night = Color(0xFFD9C600))
private val colorGroupCitron = DayNightColorProvider(day = Color(0xFF5C7C2F), night = Color(0xFFBDEE63))
private val colorGroupSage = DayNightColorProvider(day = Color(0xFF00824D), night = Color(0xFF3DD68C))
private val colorGroupTeal = DayNightColorProvider(day = Color(0xFF00826D), night = Color(0xFF0AD8B6))
private val colorGroupSky = DayNightColorProvider(day = Color(0xFF00749E), night = Color(0xFF7CE2FE))
private val colorGroupIndigo = DayNightColorProvider(day = Color(0xFF3A5BC7), night = Color(0xFF9DB1FF))
private val colorGroupViolet = DayNightColorProvider(day = Color(0xFF8145B5), night = Color(0xFFD59CFF))
private val colorGroupPlum = DayNightColorProvider(day = Color(0xFFC1298A), night = Color(0xFFFF80CA))

// ── Widget ────────────────────────────────────────────────────────────────────

class HrtWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = HrtWidgetStateDefinition

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = currentState<WidgetSnapshotState>()
            val snapshot = state.record?.takeIf { it.schemaVersion == WIDGET_SNAPSHOT_SCHEMA_VERSION }
            val size = LocalSize.current
            val adaptiveEnabled = snapshot?.adaptiveColorEnabled ?: true
            val widgetColors = if (adaptiveEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicWidgetColorScheme(context)
            } else {
                hardcodedWidgetColorScheme()
            }
            GlanceTheme {
                CompositionLocalProvider(LocalWidgetColors provides widgetColors) {
                    if (size.usesLargeWidgetLayout()) {
                        LargeWidgetContent(snapshot)
                    } else {
                        MediumWidgetContent(snapshot)
                    }
                }
            }
        }
    }

    suspend fun updateAll(context: Context) {
        glanceUpdateAll(context)
    }

    companion object {
        val LARGE_LAYOUT_MIN_SIZE = DpSize(240.dp, 180.dp)
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

internal fun DpSize.usesLargeWidgetLayout(): Boolean =
    width >= HrtWidget.LARGE_LAYOUT_MIN_SIZE.width &&
        height >= HrtWidget.LARGE_LAYOUT_MIN_SIZE.height

private fun groupAccentColor(colorKey: MedicationGroupColorKey): ColorProvider = when (colorKey) {
    MedicationGroupColorKey.ROSE -> colorGroupRose
    MedicationGroupColorKey.CORAL -> colorGroupCoral
    MedicationGroupColorKey.AMBER -> colorGroupAmber
    MedicationGroupColorKey.CITRON -> colorGroupCitron
    MedicationGroupColorKey.SAGE -> colorGroupSage
    MedicationGroupColorKey.TEAL -> colorGroupTeal
    MedicationGroupColorKey.SKY -> colorGroupSky
    MedicationGroupColorKey.INDIGO -> colorGroupIndigo
    MedicationGroupColorKey.VIOLET -> colorGroupViolet
    MedicationGroupColorKey.PLUM -> colorGroupPlum
}

private fun isEmptySetup(snapshot: WidgetSnapshotRecord?): Boolean =
    snapshot == null || snapshot.doseRows.isEmpty()

private sealed interface WidgetListItem {
    data class Header(val text: String) : WidgetListItem
    data class Row(val row: WidgetDoseRow) : WidgetListItem
}

@Composable
private fun WidgetShell(contentAlignment: Alignment = Alignment.TopStart, content: @Composable () -> Unit) {
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
private fun WidgetLabel(text: String, modifier: GlanceModifier = GlanceModifier) {
    val colors = LocalWidgetColors.current
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = TextStyle(
            color = colors.onSurfaceVariant,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
    )
}

@Composable
private fun EmptyWidgetContent() {
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
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

// ── Progress bar ──────────────────────────────────────────────────────────────

@Composable
private fun ProgressBar(fraction: Float, modifier: GlanceModifier = GlanceModifier.fillMaxWidth()) {
    val colors = LocalWidgetColors.current
    val safeFraction = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(4.dp)
            .background(colors.surfaceVariant)
            .cornerRadius(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (safeFraction > 0f) {
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .width(LocalSize.current.width * safeFraction)
                    .background(colors.primary)
                    .cornerRadius(2.dp),
            ) {}
        }
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    val colors = LocalWidgetColors.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetLabel(text)
        Spacer(GlanceModifier.width(6.dp))
        Box(
            modifier = GlanceModifier.defaultWeight().height(1.dp)
                .background(colors.outlineVariant),
        ) {}
    }
}

// ── Trailing button ───────────────────────────────────────────────────────────

@Composable
private fun TrailingButton(row: WidgetDoseRow, showLogAction: Boolean) {
    val colors = LocalWidgetColors.current
    val groupUuid = row.groupUuid
    val scheduleTimeUuid = row.scheduleTimeUuid ?: ""
    val clickModifier = if (showLogAction && groupUuid != null) {
        GlanceModifier.clickable(
            actionRunCallback<QuickLogActionCallback>(
                actionParametersOf(
                    GroupUuidKey to groupUuid,
                    ScheduleTimeUuidKey to scheduleTimeUuid,
                    ScheduledAtKey to row.scheduledAt.toString(),
                    MedicationUuidKey to (row.medicationUuid ?: ""),
                )
            )
        )
    } else {
        GlanceModifier
    }

    when (row.status) {
        WidgetDoseStatus.DONE -> Box(
            modifier = GlanceModifier.size(26.dp)
                .background(colors.secondaryContainer)
                .cornerRadius(13.dp)
                .then(clickModifier),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_check),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(colors.onSecondaryContainer),
            )
        }

        WidgetDoseStatus.DUE_SOON -> Box(
            modifier = GlanceModifier.size(26.dp)
                .background(colors.tertiaryContainer)
                .cornerRadius(13.dp)
                .then(clickModifier),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_add),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(colors.onTertiaryContainer),
            )
        }

        WidgetDoseStatus.OVERDUE -> Box(
            modifier = GlanceModifier.size(26.dp)
                .background(colors.surfaceVariant)
                .cornerRadius(13.dp)
                .then(clickModifier),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_add),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
            )
        }

        WidgetDoseStatus.LOGGED_OUT_OF_WINDOW -> Box(
            modifier = GlanceModifier.size(26.dp)
                .background(colors.surfaceVariant)
                .cornerRadius(13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_check),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
            )
        }

        else -> {
            // UPCOMING: outlined empty circle; 1dp border via nested boxes
            val borderColor = colors.outline
            val fillColor = colors.surface
            Box(
                modifier = GlanceModifier.size(26.dp)
                    .background(borderColor)
                    .cornerRadius(13.dp)
                    .then(clickModifier),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = GlanceModifier.size(24.dp)
                        .background(fillColor)
                        .cornerRadius(12.dp),
                ) {}
            }
        }
    }
}

// ── Dose row ──────────────────────────────────────────────────────────────────

@Composable
private fun DoseRow(
    row: WidgetDoseRow,
    showLogAction: Boolean,
    hideMedicationDetails: Boolean,
) {
    val colors = LocalWidgetColors.current
    val rowModifier = GlanceModifier
        .fillMaxWidth()
        .height(40.dp)
        .background(colors.surfaceContainerLow)
        .cornerRadius(10.dp)
        .padding(horizontal = 8.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: color bar (neutral outline when no group color)
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(22.dp)
                .background(if (row.colorKey != null) groupAccentColor(row.colorKey) else colors.outlineVariant)
                .cornerRadius(2.dp),
        ) {}

        Spacer(GlanceModifier.width(10.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            val context = LocalContext.current
            val fullName = if (hideMedicationDetails && !row.isManualRecord) row.groupName else row.medicationName
            Text(
                text = fullName,
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(
                    color = if (row.status == WidgetDoseStatus.UPCOMING) {
                        colors.onSurfaceVariant
                    } else {
                        colors.onSurface
                    },
                    fontSize = 13.sp,
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
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(GlanceModifier.width(8.dp))

        if (row.trailingText != null) {
            Text(
                text = row.trailingText,
                style = TextStyle(
                    color = if (row.trailingIsDelta) {
                        colors.onSurfaceVariant
                    } else {
                        colors.onSurface
                    },
                    fontSize = if (row.trailingIsDelta) 11.sp else 13.sp,
                    fontWeight = if (row.trailingIsDelta) FontWeight.Normal else FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(8.dp))
        }
        TrailingButton(row, showLogAction)
    }
}

// ── Medium widget (4×2) ───────────────────────────────────────────────────────

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
        val e2Value = record.pkProjection
            ?.toPkProjectionResult(now, zoneId)
            ?.toMainEstradiolTrend(now, zoneId)
            ?.currentConcentration
        val doneCount = record.doneCount
        val totalCount = record.totalCount
        val allDone = totalCount > 0 && doneCount >= totalCount

        // Active row: first non-DONE today row (not LAST_NIGHT), or COMING_UP fallback
        val activeRow = record.doseRows
            .firstOrNull { it.contextChip != WidgetDoseChip.LAST_NIGHT && it.status != WidgetDoseStatus.DONE }
            ?: record.doseRows.firstOrNull { it.contextChip == WidgetDoseChip.COMING_UP }

        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left column: unchanged progress area
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
            ) {
                WidgetLabel(context.getString(R.string.widget_today))
                Spacer(GlanceModifier.height(4.dp))
                if (totalCount == 0) {
                    Text(
                        text = context.getString(R.string.widget_no_doses_today),
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = doneCount.toString(),
                            style = TextStyle(
                                color = if (allDone) {
                                    colors.primary
                                } else {
                                    colors.onSurface
                                },
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Spacer(GlanceModifier.width(4.dp))
                        Text(
                            text = "/$totalCount done",
                            style = TextStyle(
                                color = colors.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                        )
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    ProgressBar(
                        fraction = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f,
                    )
                }
                if (e2Value != null) {
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        text = "E2 ~%.0f pg/mL".format(e2Value),
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = 11.sp,
                        ),
                    )
                }
            }

            Spacer(GlanceModifier.width(14.dp))
            Box(
                modifier = GlanceModifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(colors.outlineVariant),
            ) {}
            Spacer(GlanceModifier.width(14.dp))

            // Right column: single active medicine
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start,
            ) {
                if (allDone && activeRow == null) {
                    Box(
                        modifier = GlanceModifier.size(26.dp)
                            .background(colors.secondaryContainer)
                            .cornerRadius(13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_check),
                            contentDescription = null,
                            modifier = GlanceModifier.size(18.dp),
                            colorFilter = ColorFilter.tint(colors.onSecondaryContainer),
                        )
                    }
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        text = context.getString(R.string.widget_all_done),
                        style = TextStyle(
                            color = colors.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                } else if (activeRow != null) {
                    val labelText = when {
                        activeRow.contextChip == WidgetDoseChip.COMING_UP ->
                            context.getString(R.string.widget_chip_coming_up).uppercase()
                        activeRow.status == WidgetDoseStatus.OVERDUE -> "OVERDUE"
                        else -> context.getString(R.string.widget_next_dose)
                    }
                    WidgetLabel(labelText)
                    Spacer(GlanceModifier.height(4.dp))
                    val displayName = if (record.hideMedicationDetails && !activeRow.isManualRecord) {
                        activeRow.groupName
                    } else {
                        activeRow.medicationName
                    }
                    Text(
                        text = displayName,
                        style = TextStyle(
                            color = colors.onSurface,
                            fontSize = 13.sp,
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
                            Spacer(GlanceModifier.height(2.dp))
                            Text(
                                text = supporting,
                                style = TextStyle(
                                    color = colors.onSurfaceVariant,
                                    fontSize = 11.sp,
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
                        if (activeRow.trailingText != null) {
                            Text(
                                text = activeRow.trailingText,
                                style = TextStyle(
                                    color = colors.onSurfaceVariant,
                                    fontSize = 11.sp,
                                ),
                                maxLines = 1,
                            )
                            Spacer(GlanceModifier.width(6.dp))
                        }
                        val isActionable = activeRow.groupUuid != null &&
                            (activeRow.status == WidgetDoseStatus.DUE_SOON || activeRow.status == WidgetDoseStatus.OVERDUE)
                        TrailingButton(activeRow, isActionable)
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
        val e2Value = record.pkProjection
            ?.toPkProjectionResult(now, zoneId)
            ?.toMainEstradiolTrend(now, zoneId)
            ?.currentConcentration
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
                    rows.first().copy(
                        medicationName = if (count > 1) "${rows.first().groupName} · $count" else rows.first().groupName
                    )
                }
            val manualRows = regularRows.filter { it.isManualRecord }
            val comingUpRows = record.doseRows
                .filter { it.contextChip == WidgetDoseChip.COMING_UP }
                .groupBy { it.groupName }
                .values.map { rows -> rows.first() }
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

        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().wrapContentHeight(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = GlanceModifier.defaultWeight().wrapContentHeight()) {
                    WidgetLabel("${context.getString(R.string.widget_today)} · $doneCount of $totalCount done")
                    Spacer(GlanceModifier.height(7.dp))
                    ProgressBar(
                        fraction = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f,
                        modifier = GlanceModifier.width(140.dp),
                    )
                }
                if (e2Value != null) {
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = "E2 ~%.0f pg/mL".format(e2Value),
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = 11.sp,
                        ),
                    )
                }
            }

            Spacer(GlanceModifier.height(10.dp))

            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                itemsIndexed(
                    items = listItems,
                    itemId = { index, _ -> (index + 1).toLong() },
                ) { _, item ->
                    when (item) {
                        is WidgetListItem.Header -> SectionHeader(item.text)
                        is WidgetListItem.Row -> DoseRow(
                            row = item.row,
                            showLogAction = item.row.groupUuid != null &&
                                (item.row.status == WidgetDoseStatus.DUE_SOON || item.row.status == WidgetDoseStatus.OVERDUE),
                            hideMedicationDetails = record.hideMedicationDetails,
                        )
                    }
                }
            }
        }
    }
}
