package com.mkx.hrttracker.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.home.HomeCardLayout
import com.mkx.hrttracker.model.home.HomeCardType
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.ui.components.MedicalDisclaimerKind
import com.mkx.hrttracker.ui.components.MedicalDisclaimerSets
import com.mkx.hrttracker.ui.components.MedicalDisclaimerText
import com.mkx.hrttracker.ui.components.appContentPaddingValues
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.journal.SimpleHomeCard
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.LocalDateFormatter
import com.mkx.hrttracker.util.TimeZoneChangeNotice
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.displayZoneOf
import com.mkx.hrttracker.util.medicationGroupScheduleDateFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import com.mkx.hrttracker.util.zoneDisplayName
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Whether [type]'s card has data to render. Mirrors each card's pre-existing
 * data-availability guard so a "visible but empty" card renders nothing — same as
 * the legacy hard-coded layout. The single source of truth for both the render
 * filter and the inter-card spacing.
 */
fun homeCardHasData(uiState: MainUiState, type: HomeCardType): Boolean = when (type) {
    HomeCardType.LOW_STOCK -> uiState.stockWarnings.isNotEmpty()
    HomeCardType.E2_HERO -> true
    HomeCardType.E2_CHART -> true
    HomeCardType.ANTIANDROGEN -> uiState.antiandrogenCards.isNotEmpty()
    HomeCardType.TIMELINE -> uiState.homeAnchor != null
}

/** Configured order, minus hidden cards, minus cards with no data to show. */
fun visibleHomeCards(layout: HomeCardLayout, uiState: MainUiState): List<HomeCardType> =
    layout.order.filter { it !in layout.hidden && homeCardHasData(uiState, it) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
    scrollState: ScrollState,
    highlightRequest: DoseRowHighlightRequest? = null,
    highlightEffectsEnabled: Boolean = true,
    highlightFlashReady: Boolean = true,
    onQuickLogDoseClick: (MainQuickLogDoseRequest) -> Unit,
    onEntryClick: (MainEditEntryRequest) -> Unit,
    onMedicineDetailClick: (UUID) -> Unit = { },
    onDismissTimeZoneChangeNotice: () -> Unit = { },
    onE2ChartWindowOptionSelected: (HomeE2ChartWindowOption) -> Unit = { },
    onLowStockSectionExpandedChange: (Boolean) -> Unit = { },
    onOpenTimeline: () -> Unit = { },
    claimE2ChartIntroAnimation: () -> Boolean = { false },
    contentPadding: PaddingValues? = null,
) {
    val appLocale = rememberAppLocale()
    val today = uiState.now.toLocalDate()
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }
    val dayHeaderDateFormatter = remember(appLocale, today) {
        medicationGroupScheduleDateFormatter(appLocale, today)
    }
    val timeFormatter = rememberLocalizedShortTimeFormatter(appLocale)
    val highlightScrollTargetKey = remember(uiState, highlightRequest, highlightEffectsEnabled) {
        if (highlightEffectsEnabled) {
            mainDoseRowHighlightScrollTargetKey(uiState, highlightRequest)
        } else {
            null
        }
    }
    // Anchor the deep-link highlight target slightly above center instead of
    // edge-aligning it: the dose rows trigger bringIntoView() when highlighted,
    // and the default spec scrolls the minimum to make the row just visible.
    // Placing the row's center at the upper third of the viewport keeps it clear
    // of the bottom sheet that opens when the row is tapped, while still reading
    // as roughly centered. The scroll container clamps at content bounds, so a
    // target near the top/bottom lands as close to this anchor as it can.
    val highlightBringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float = offset - (containerSize * 0.33f - size / 2f)
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides highlightBringIntoViewSpec) {
        val resolvedContentPadding = contentPadding ?: appContentPaddingValues()
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(resolvedContentPadding),
        ) {
            uiState.timeZoneChangeNotice?.let { notice ->
                MainTimeZoneChangeNoticeBanner(
                    notice = notice,
                    appLocale = appLocale,
                    onDismiss = onDismissTimeZoneChangeNotice,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            val visibleCards = visibleHomeCards(uiState.homeCardLayout, uiState)
            visibleCards.forEachIndexed { index, type ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                RenderHomeCard(
                    type = type,
                    uiState = uiState,
                    appLocale = appLocale,
                    today = today,
                    dateFormatter = dateFormatter,
                    timeFormatter = timeFormatter,
                    onMedicineDetailClick = onMedicineDetailClick,
                    onLowStockSectionExpandedChange = onLowStockSectionExpandedChange,
                    onE2ChartWindowOptionSelected = onE2ChartWindowOptionSelected,
                    claimE2ChartIntroAnimation = claimE2ChartIntroAnimation,
                    onOpenTimeline = onOpenTimeline,
                )
            }

            if (uiState.lastNightSection.rows.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                MainLastNightSection(
                    section = uiState.lastNightSection,
                    now = uiState.now,
                    dateFormatter = dayHeaderDateFormatter,
                    timeFormatter = timeFormatter,
                    highlightRequest = highlightRequest,
                    highlightEffectsEnabled = highlightEffectsEnabled,
                    highlightFlashReady = highlightFlashReady,
                    highlightScrollTargetKey = highlightScrollTargetKey,
                    onQuickLogDoseClick = onQuickLogDoseClick,
                    onEntryClick = onEntryClick
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            MainTodaySection(
                section = uiState.todaySection,
                now = uiState.now,
                dateFormatter = dayHeaderDateFormatter,
                timeFormatter = timeFormatter,
                highlightRequest = highlightRequest,
                highlightEffectsEnabled = highlightEffectsEnabled,
                highlightFlashReady = highlightFlashReady,
                highlightScrollTargetKey = highlightScrollTargetKey,
                onQuickLogDoseClick = onQuickLogDoseClick,
                onEntryClick = onEntryClick
            )

            Spacer(modifier = Modifier.height(16.dp))
            MainUpcomingSection(
                section = uiState.upcomingSection,
                dateFormatter = dayHeaderDateFormatter,
                timeFormatter = timeFormatter,
                highlightRequest = highlightRequest,
                highlightEffectsEnabled = highlightEffectsEnabled,
                highlightFlashReady = highlightFlashReady,
                highlightScrollTargetKey = highlightScrollTargetKey,
            )

            Spacer(modifier = Modifier.height(16.dp))
            val disclaimerKinds = if (uiState.hideReferenceRanges) {
                listOf(MedicalDisclaimerKind.PLASMA_CONCENTRATION_ESTIMATES)
            } else {
                MedicalDisclaimerSets.home
            }
            MedicalDisclaimerText(kinds = disclaimerKinds)
        }
    }
}

@Composable
private fun RenderHomeCard(
    type: HomeCardType,
    uiState: MainUiState,
    appLocale: Locale,
    today: LocalDate,
    dateFormatter: LocalDateFormatter,
    timeFormatter: DateTimeFormatter,
    onMedicineDetailClick: (UUID) -> Unit,
    onLowStockSectionExpandedChange: (Boolean) -> Unit,
    onE2ChartWindowOptionSelected: (HomeE2ChartWindowOption) -> Unit,
    claimE2ChartIntroAnimation: () -> Boolean,
    onOpenTimeline: () -> Unit,
) {
    when (type) {
        HomeCardType.LOW_STOCK -> MainLowStockSection(
            warnings = uiState.stockWarnings,
            expanded = uiState.lowStockSectionExpanded,
            onExpandedChange = onLowStockSectionExpandedChange,
            onMedicineClick = onMedicineDetailClick,
        )

        HomeCardType.E2_HERO -> MainE2HeroCard(
            section = uiState.e2Hero,
            now = uiState.now,
            displayUnit = uiState.homeE2DisplayUnit,
            trendReady = uiState.e2TrendReady,
            hideReferenceRanges = uiState.hideReferenceRanges,
        )

        HomeCardType.E2_CHART -> MainE2ChartCard(
            section = uiState.e2Chart,
            now = uiState.now,
            appLocale = appLocale,
            unit = uiState.e2Hero.unit,
            displayUnit = uiState.homeE2DisplayUnit,
            targetRangeLow = uiState.e2Hero.targetMin,
            targetRangeHigh = uiState.e2Hero.targetMax,
            trendReady = uiState.e2TrendReady,
            hideReferenceRanges = uiState.hideReferenceRanges,
            onChartWindowOptionSelected = onE2ChartWindowOptionSelected,
            claimIntroAnimation = claimE2ChartIntroAnimation,
        )

        HomeCardType.ANTIANDROGEN -> MainAntiandrogenCard(
            cards = uiState.antiandrogenCards,
            now = uiState.now,
            dateFormatter = dateFormatter,
            timeFormatter = timeFormatter,
        )

        HomeCardType.TIMELINE -> uiState.homeAnchor?.let { anchor ->
            SimpleHomeCard(
                anchor = anchor,
                today = today,
                onClick = onOpenTimeline,
            )
        }
    }
}

@Composable
private fun MainTimeZoneChangeNoticeBanner(
    notice: TimeZoneChangeNotice,
    appLocale: Locale,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previousLabel = remember(notice.previousZoneId, appLocale) {
        zoneDisplayName(displayZoneOf(notice.previousZoneId), appLocale)
    }
    val currentLabel = remember(notice.currentZoneId, appLocale) {
        zoneDisplayName(displayZoneOf(notice.currentZoneId), appLocale)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = null,
            )
            val timeZoneChangeNoticeText = stringResource(
                R.string.timezone_change_notice,
                previousLabel,
                currentLabel,
            )
            Text(
                text = timeZoneChangeNoticeText,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .weight(1f)
                    .cjkTextOffset(timeZoneChangeNoticeText)
            )
            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.timezone_change_notice_dismiss),
                    )
                }
            }
        }
    }
}

@Preview(
    name = "Main Content",
    showBackground = true,
    widthDp = 420,
    heightDp = 900
)
@Composable
private fun MainContentPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Surface {
            MainContent(
                uiState = buildMainContentPreviewUiState().copy(
                    timeZoneChangeNotice = PreviewTimeZoneChangeNotice,
                ),
                scrollState = rememberScrollState(),
                onQuickLogDoseClick = { },
                onEntryClick = { },
            )
        }
    }
}

@Preview(
    name = "Time Zone Change Notice",
    showBackground = true,
    widthDp = 420,
)
@Composable
private fun MainTimeZoneChangeNoticeBannerPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Surface {
            MainTimeZoneChangeNoticeBanner(
                notice = PreviewTimeZoneChangeNotice,
                appLocale = Locale.ENGLISH,
                onDismiss = { },
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

private val PreviewTimeZoneChangeNotice = TimeZoneChangeNotice(
    previousZoneId = "Asia/Tokyo",
    currentZoneId = "America/Los_Angeles",
)
