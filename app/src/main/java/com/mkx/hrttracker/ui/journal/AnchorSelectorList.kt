package com.mkx.hrttracker.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.model.journal.dayCount
import java.time.LocalDate

// Shared anchor picker: rows of glyph · name · day count. One implementation, two hosts
// (timeline overflow sheet, widget config sheet). Empty list → an "add a date first"
// affordance that routes to the add-date flow.
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

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(anchors, key = { it.id }) { anchor ->
            val count = dayCount(date = anchor.date, today = today)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(anchor.id) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(anchorIconRes(anchor.icon)),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = anchor.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(
                        R.string.anchor_selector_days, count.magnitude.toInt()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
