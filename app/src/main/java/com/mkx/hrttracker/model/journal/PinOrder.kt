package com.mkx.hrttracker.model.journal

/**
 * Pure operations over the single ordered pin list that is the source of truth
 * for the Home card, Journal stack card, full-page hero, and Pinned tray.
 */
object PinOrder {
    /** Pin-on-create appends to the bottom; tolerates gaps left by prior unpins. */
    fun appendOrder(existingOrders: List<Int>): Int =
        (existingOrders.maxOrNull()?.plus(1)) ?: 0

    /** Re-index a reordered/unpinned tray to contiguous 0..n-1 in list order. */
    fun normalize(pinnedIdsInOrder: List<String>): Map<String, Int> =
        pinnedIdsInOrder.withIndex().associate { (index, id) -> id to index }

    /** The hero is always `pinned[0]`. */
    fun hero(pinnedIdsInOrder: List<String>): String? = pinnedIdsInOrder.firstOrNull()
}
