package com.mkx.hrttracker.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.ui.components.HazeModalBottomSheet
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.hideBottomSheet
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnchorSelectorSheet(
    title: String,
    anchors: List<TrackedDate>,
    today: LocalDate,
    onDismissRequest: () -> Unit,
    onSelect: (String) -> Unit,
    onAddDate: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    HazeModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismissRequest,
    ) {
        AnchorSelectorSheetContent(
            title = title,
            anchors = anchors,
            today = today,
            onDismissRequest = {
                hideBottomSheet(scope, sheetState, onDismissRequest)
            },
            onSelect = { anchorId ->
                onSelect(anchorId)
                hideBottomSheet(scope, sheetState, onDismissRequest)
            },
            onAddDate = {
                hideBottomSheet(scope, sheetState) {
                    onDismissRequest()
                    onAddDate()
                }
            },
        )
    }
}

@Composable
private fun AnchorSelectorSheetContent(
    title: String,
    anchors: List<TrackedDate>,
    today: LocalDate,
    onDismissRequest: () -> Unit,
    onSelect: (String) -> Unit,
    onAddDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = dimensionResource(R.dimen.padding_large),
                end = dimensionResource(R.dimen.padding_large),
                bottom = dimensionResource(R.dimen.padding_large),
            ),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            HrtFilledTonalButton(
                text = stringResource(R.string.cancel),
                onClick = onDismissRequest,
                compact = true,
            )
        }
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_xsmall)))
        AnchorSelectorList(
            anchors = anchors,
            today = today,
            onSelect = onSelect,
            onAddDate = onAddDate,
        )
    }
}

// Shared anchor picker content. One implementation, two hosts (timeline overflow sheet,
// widget config sheet). Empty list routes to the add-date flow when the host has one.
@Composable
fun AnchorSelectorList(
    anchors: List<TrackedDate>,
    today: LocalDate,
    onSelect: (String) -> Unit,
    onAddDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (anchors.isEmpty()) {
        Column(
            modifier = modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.anchor_selector_empty),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.size(8.dp))
            TextButton(onClick = onAddDate) {
                Text(stringResource(R.string.anchor_selector_add_date))
            }
        }
        return
    }

    val nodes = remember(anchors, today) {
        anchors.map { anchor ->
            TimelineNodeUiState(
                anchor = anchor.toAnchorRowUiState(today),
                isPinned = false,
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap)),
    ) {
        itemsIndexed(
            items = nodes,
            key = { _, node -> node.anchor.id },
        ) { index, node ->
            AnchorMilestoneCard(
                node = node,
                segIndex = index,
                segCount = nodes.size,
                isEditMode = false,
                today = today,
                onSetPinned = { _, _ -> },
                onUpdateDate = { anchor -> onSelect(anchor.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
