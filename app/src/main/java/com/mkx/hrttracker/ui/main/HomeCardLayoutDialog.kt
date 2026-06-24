package com.mkx.hrttracker.ui.main

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.home.HomeCardLayout
import com.mkx.hrttracker.model.home.HomeCardType
import com.mkx.hrttracker.ui.components.HazeAlertDialog
import com.mkx.hrttracker.ui.components.cjkTextOffset
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableColumn

@Composable
fun HomeCardLayoutDialog(
    layout: HomeCardLayout,
    onConfirm: (order: List<HomeCardType>, hidden: Set<HomeCardType>) -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    // Draft state: order + hidden mutate freely; nothing escapes until Done.
    var draftOrder by remember { mutableStateOf(layout.order) }
    var draftHidden by remember { mutableStateOf(layout.hidden) }

    // Mirror the pinned tray's settle gate: onDragStopped fires at release, BEFORE
    // ReorderableColumn's onSettle applies the new order. Done stays enabled; a tap
    // during settle is buffered (confirmPending) and fired once the order settles, so
    // the committed order is never the stale (pre-settle) one.
    var settlingAfterDrag by remember { mutableStateOf(false) }
    var confirmPending by remember { mutableStateOf(false) }
    LaunchedEffect(settlingAfterDrag) {
        if (settlingAfterDrag) {
            // Safety net for a drop with no reorder (onSettle never fires).
            delay(1000)
            settlingAfterDrag = false
        }
    }
    LaunchedEffect(settlingAfterDrag, confirmPending) {
        if (confirmPending && !settlingAfterDrag) {
            onConfirm(draftOrder, draftHidden)
            confirmPending = false
        }
    }

    HazeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val title = stringResource(R.string.home_card_layout_title)
            Text(text = title, modifier = Modifier.cjkTextOffset(title))
        },
        text = {
            ReorderableColumn(
                modifier = Modifier.fillMaxWidth(),
                list = draftOrder,
                onSettle = { fromIndex, toIndex ->
                    draftOrder = draftOrder.moved(fromIndex, toIndex)
                    settlingAfterDrag = false
                },
                onMove = {
                    ViewCompat.performHapticFeedback(
                        view,
                        HapticFeedbackConstantsCompat.SEGMENT_FREQUENT_TICK,
                    )
                },
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.list_segment_gap),
                ),
            ) { index, type, _ ->
                key(type) {
                    val rowInteraction = remember { MutableInteractionSource() }
                    val gripInteraction = remember { MutableInteractionSource() }
                    val onDragStopped: (Float) -> Unit = { settlingAfterDrag = true }
                    val name = stringResource(homeCardNameRes(type))
                    val leadingPainter = homeCardLeadingPainter(type)
                    val hidden = type in draftHidden
                    val moveActions = homeCardMoveActions(
                        name = name,
                        index = index,
                        count = draftOrder.size,
                        order = draftOrder,
                        onReorder = { newOrder -> draftOrder = newOrder },
                    )
                    HomeCardLayoutRow(
                        name = name,
                        leadingPainter = leadingPainter,
                        hidden = hidden,
                        onToggleHidden = {
                            draftHidden = if (hidden) draftHidden - type else draftHidden + type
                        },
                        gripModifier = Modifier.draggableHandle(
                            interactionSource = gripInteraction,
                            onDragStopped = onDragStopped,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .longPressDraggableHandle(
                                interactionSource = rowInteraction,
                                onDragStopped = onDragStopped,
                            )
                            .semantics { customActions = moveActions },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Always enabled: if a drag is still settling, buffer the commit
                    // until onSettle (or the safety net) clears the gate.
                    if (settlingAfterDrag) confirmPending = true
                    else onConfirm(draftOrder, draftHidden)
                },
            ) {
                Text(stringResource(R.string.journal_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun HomeCardLayoutRow(
    name: String,
    leadingPainter: Painter,
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    gripModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    // Container reflects visibility: shown -> secondaryContainer, hidden -> surfaceContainerHighest.
    val containerColor = if (hidden) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(
        onClick = onToggleHidden,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Leading icon mirrors the card's own header icon (card identity).
            Icon(
                painter = leadingPainter,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_small)))
            Text(
                text = name,
                // Same text style as the real card titles (labelLarge + SemiBold).
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .cjkTextOffset(name),
            )
            // Visibility toggle first, then drag grip — same cluster order/styling as the
            // journal pinned tray's unpin -> grip cluster (EditTrailingCluster). No row dimming.
            IconButton(onClick = onToggleHidden) {
                Icon(
                    painter = painterResource(
                        if (hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                    ),
                    contentDescription = stringResource(
                        if (hidden) R.string.home_card_show else R.string.home_card_hide,
                        name,
                    ),
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_xsmall)))
            // DragGrip replicated inline (JournalComponents.DragGrip is private).
            Icon(
                painter = painterResource(R.drawable.ic_drag_indicator),
                contentDescription = null,
                modifier = gripModifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun homeCardNameRes(type: HomeCardType): Int = when (type) {
    HomeCardType.LOW_STOCK -> R.string.home_card_name_low_stock
    HomeCardType.E2_HERO -> R.string.home_card_name_e2_hero
    HomeCardType.E2_CHART -> R.string.home_card_name_e2_chart
    HomeCardType.ANTIANDROGEN -> R.string.home_card_name_antiandrogen
    HomeCardType.TIMELINE -> R.string.home_card_name_timeline
}

@Composable
private fun homeCardLeadingPainter(type: HomeCardType): Painter = when (type) {
    HomeCardType.LOW_STOCK -> painterResource(R.drawable.ic_inventory_2)
    HomeCardType.E2_HERO -> rememberVectorPainter(Icons.Rounded.MonitorHeart)
    HomeCardType.E2_CHART -> rememberVectorPainter(Icons.AutoMirrored.Rounded.ShowChart)
    HomeCardType.ANTIANDROGEN -> painterResource(R.drawable.ic_medication)
    HomeCardType.TIMELINE -> painterResource(R.drawable.ic_event)
}

@Composable
private fun homeCardMoveActions(
    name: String,
    index: Int,
    count: Int,
    order: List<HomeCardType>,
    onReorder: (List<HomeCardType>) -> Unit,
): List<CustomAccessibilityAction> {
    val moveTop = stringResource(R.string.home_card_move_to_top, name)
    val moveUp = stringResource(R.string.home_card_move_up, name)
    val moveDown = stringResource(R.string.home_card_move_down, name)
    return buildList {
        if (index > 0) {
            add(CustomAccessibilityAction(moveTop) { onReorder(order.moved(index, 0)); true })
            add(CustomAccessibilityAction(moveUp) { onReorder(order.moved(index, index - 1)); true })
        }
        if (index < count - 1) {
            add(CustomAccessibilityAction(moveDown) { onReorder(order.moved(index, index + 1)); true })
        }
    }
}

/** Single reorder math source for drag settle + a11y moves; out-of-range leaves the list unchanged. */
private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
