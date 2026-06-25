package com.mkx.hrttracker.model.home

/** The five configurable home-screen cards. Enum name is the persisted token. */
enum class HomeCardType {
    LOW_STOCK,
    E2_HERO,
    E2_CHART,
    ANTIANDROGEN,
    TIMELINE,
}

/** Default order matches today's hard-coded home layout. */
val DEFAULT_HOME_CARD_ORDER: List<HomeCardType> = listOf(
    HomeCardType.LOW_STOCK,
    HomeCardType.E2_HERO,
    HomeCardType.E2_CHART,
    HomeCardType.ANTIANDROGEN,
    HomeCardType.TIMELINE,
)

/** Persisted home-card preferences: display [order] plus the [hidden] set. */
data class HomeCardLayout(
    val order: List<HomeCardType> = DEFAULT_HOME_CARD_ORDER,
    val hidden: Set<HomeCardType> = emptySet(),
) {
    companion object {
        /**
         * Single tolerant decoder shared by the DataStore flow and backup restore:
         * - unknown names are ignored (forward/back compat),
         * - duplicate order entries are de-duped by first occurrence,
         * - enum values missing from [orderNames] are appended in [DEFAULT_HOME_CARD_ORDER] position
         *   (so a card type added in a future version slots in automatically),
         * - unknown hidden names are ignored.
         */
        fun decode(orderNames: List<String>, hiddenNames: Collection<String>): HomeCardLayout {
            val parsed = orderNames.mapNotNull { it.toHomeCardTypeOrNull() }.distinct()
            val order = parsed + DEFAULT_HOME_CARD_ORDER.filter { it !in parsed }
            val hidden = hiddenNames.mapNotNull { it.toHomeCardTypeOrNull() }.toSet()
            return HomeCardLayout(order = order, hidden = hidden)
        }

        private fun String.toHomeCardTypeOrNull(): HomeCardType? =
            runCatching { HomeCardType.valueOf(this) }.getOrNull()
    }
}
