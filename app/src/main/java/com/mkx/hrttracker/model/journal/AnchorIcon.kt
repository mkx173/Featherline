package com.mkx.hrttracker.model.journal

/**
 * Curated, stable icon keys for anchors. The string [storageKey] is what
 * persists in Room and backups; unknown keys resolve to [DEFAULT] = EVENT so a
 * forward-compatible or corrupted value never crashes a restore. Drawable resolution
 * lives in the UI layer (Phase 2).
 */
enum class AnchorIcon(val storageKey: String) {
    EVENT("event"),
    MEDICATION("medication"),
    VACCINES("vaccines"),
    FLAG("flag"),
    SCHEDULE("schedule"),
    SCIENCE("science"),
    FAVORITE("favorite"),
    SPA("spa"),
    SELF_CARE("self_care"),
    MOON("bedtime"),
    SUNRISE("wb_sunny"),
    PILL("pill");

    companion object {
        val DEFAULT = EVENT
        fun fromStorageValue(value: String?): AnchorIcon =
            entries.firstOrNull { it.storageKey == value } ?: DEFAULT
    }
}
