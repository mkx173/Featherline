package com.mkx.hrttracker.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import java.time.LocalDateTime
import java.util.UUID

@Composable
fun MainContent(
    uiState: MainUiState,
    listState: LazyListState,
    onQuickLogDoseClick: (UUID, UUID?, LocalDateTime, MedicationDetails, Int) -> Unit,
    onEntryClick: (Set<UUID>) -> Unit,
    modifier: Modifier = Modifier
) {
    val appLocale = rememberAppLocale()
    val today = uiState.now.toLocalDate()
    val dateFormatter = remember(appLocale, today) {
        dateLabelFormatter(appLocale, today)
    }
    val timeFormatter = rememberLocalizedShortTimeFormatter(appLocale)
    if (uiState.isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(dimensionResource(R.dimen.padding_medium)),
    ) {
        item(key = "e2-hero") {
            MainE2HeroCard(
                section = uiState.e2Hero,
                now = uiState.now
            )
        }

        item(key = "e2-chart") {
            Spacer(modifier = Modifier.height(8.dp))
            MainE2ChartCard(
                section = uiState.e2Chart,
                now = uiState.now,
                appLocale = appLocale
            )
        }

        uiState.antiandrogenCards.forEach { card ->
            item(key = "antiandrogen-${card.id}") {
                Spacer(modifier = Modifier.height(8.dp))
                MainAntiandrogenCard(
                    card = card,
                    now = uiState.now,
                    dateFormatter = dateFormatter,
                    timeFormatter = timeFormatter
                )
            }
        }

        item(key = "today") {
            Spacer(modifier = Modifier.height(16.dp))
            MainTodaySection(
                section = uiState.todaySection,
                now = uiState.now,
                dateFormatter = dateFormatter,
                timeFormatter = timeFormatter,
                onQuickLogDoseClick = onQuickLogDoseClick,
                onEntryClick = onEntryClick
            )
        }

        item(key = "upcoming") {
            Spacer(modifier = Modifier.height(16.dp))
            MainUpcomingSection(
                section = uiState.upcomingSection,
                dateFormatter = dateFormatter,
                timeFormatter = timeFormatter
            )
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
                uiState = buildMainContentPreviewUiState(),
                listState = rememberLazyListState(),
                onQuickLogDoseClick = { _, _, _, _, _ -> },
                onEntryClick = { },
            )
        }
    }
}
