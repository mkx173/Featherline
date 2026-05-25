package com.mkx.hrttracker.ui.catalog.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R

private val PRESETS = listOf(3, 5, 7, 10, 14, 21)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarnAtThresholdSheet(
    initialValue: Int,
    onSubmit: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var selected by remember(initialValue) { mutableIntStateOf(initialValue) }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stock_warnat_sheet_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PRESETS.forEach { days ->
                    FilterChip(
                        selected = days == selected,
                        onClick = { selected = days },
                        label = {
                            Text(stringResource(R.string.stock_warnat_chip_days, days))
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.stock_cancel))
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { onSubmit(selected) }) {
                    Text(stringResource(R.string.stock_adjust_confirm))
                }
            }
        }
    }
}
