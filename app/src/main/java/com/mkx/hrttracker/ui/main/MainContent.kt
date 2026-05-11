package com.mkx.hrttracker.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.ui.components.MedicalDisclaimerSets
import com.mkx.hrttracker.ui.components.MedicalDisclaimerText
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.TimeZoneChangeNotice
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.displayZoneOf
import com.mkx.hrttracker.util.medicationGroupScheduleDateFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import com.mkx.hrttracker.util.zoneDisplayName
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID

@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
    listState: LazyListState,
    onQuickLogDoseClick: (UUID, UUID?, LocalDateTime, MedicationDetails, Int) -> Unit,
    onEntryClick: (Set<UUID>) -> Unit,
    onDismissTimeZoneChangeNotice: () -> Unit = { },
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
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(dimensionResource(R.dimen.padding_medium)),
    ) {
        uiState.timeZoneChangeNotice?.let { notice ->
            item(key = "timezone-change-notice") {
                MainTimeZoneChangeNoticeBanner(
                    notice = notice,
                    appLocale = appLocale,
                    onDismiss = onDismissTimeZoneChangeNotice,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        item(key = "e2-hero") {
            MainE2HeroCard(
                section = uiState.e2Hero,
                now = uiState.now,
                displayUnit = uiState.homeE2DisplayUnit,
            )
        }

        item(key = "e2-chart") {
            Spacer(modifier = Modifier.height(8.dp))
            MainE2ChartCard(
                section = uiState.e2Chart,
                now = uiState.now,
                appLocale = appLocale,
                unit = uiState.e2Hero.unit,
                displayUnit = uiState.homeE2DisplayUnit,
                targetRangeLow = uiState.e2Hero.targetMin,
                targetRangeHigh = uiState.e2Hero.targetMax,
            )
        }

        if (uiState.antiandrogenCards.isNotEmpty()) {
            item(key = "antiandrogens") {
                Spacer(modifier = Modifier.height(8.dp))
                MainAntiandrogenCard(
                    cards = uiState.antiandrogenCards,
                    now = uiState.now,
                    dateFormatter = dateFormatter,
                    timeFormatter = timeFormatter,
                )
            }
        }

        if (uiState.lastNightSection.rows.isNotEmpty()) {
            item(key = "last-night") {
                Spacer(modifier = Modifier.height(16.dp))
                MainLastNightSection(
                    section = uiState.lastNightSection,
                    now = uiState.now,
                    dateFormatter = dayHeaderDateFormatter,
                    timeFormatter = timeFormatter,
                    onQuickLogDoseClick = onQuickLogDoseClick,
                    onEntryClick = onEntryClick
                )
            }
        }

        item(key = "today") {
            Spacer(modifier = Modifier.height(16.dp))
            MainTodaySection(
                section = uiState.todaySection,
                now = uiState.now,
                dateFormatter = dayHeaderDateFormatter,
                timeFormatter = timeFormatter,
                onQuickLogDoseClick = onQuickLogDoseClick,
                onEntryClick = onEntryClick
            )
        }

        item(key = "upcoming") {
            Spacer(modifier = Modifier.height(16.dp))
            MainUpcomingSection(
                section = uiState.upcomingSection,
                dateFormatter = dayHeaderDateFormatter,
                timeFormatter = timeFormatter
            )
        }

        item(key = "medical-disclaimer") {
            Spacer(modifier = Modifier.height(16.dp))
            MedicalDisclaimerText(kinds = MedicalDisclaimerSets.home)
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
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).cjkTextOffset(timeZoneChangeNoticeText)
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
                listState = rememberLazyListState(),
                onQuickLogDoseClick = { _, _, _, _, _ -> },
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
