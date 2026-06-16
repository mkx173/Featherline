package com.mkx.hrttracker.model.journal

/**
 * Curated, stable icon keys for anchors (spec §3.1). The string [storageKey] is what
 * persists in Room and backups; unknown keys resolve to [DEFAULT] = EVENT so a
 * forward-compatible or corrupted value never crashes a restore or read-time mapping.
 *
 * Design-backed set: every [storageKey] maps to an `ic_<storageKey>` vector drawable,
 * so the UI-layer resolver (Phase 2) is the mechanical `R.drawable.ic_$storageKey`.
 * All keys ship in `res/drawable` today except `flag`, whose `ic_flag` drawable must be
 * added (Material Symbols Rounded) before Phase 2 wires the resolver. The keys cover the
 * anchor use cases the spec names — starting a medication, a first injection, blood
 * tests, home/at-home care, scheduled days, a forward goal, and personal milestones —
 * and stay on-voice: calm and factual, no celebratory imagery (spec §1).
 *
 * Storage keys are immutable once shipped (they persist in user data and backups);
 * add new icons by appending, never by renaming an existing key.
 */
enum class AnchorIcon(val storageKey: String) {
    EVENT("event"),                       // generic date / appointment / planned day (default + fallback)
    MEDICATION("medication"),             // started or on a medication
    PILL("pill"),                         // an oral medication
    VACCINES("vaccines"),                 // an injection / immunisation
    BLOODTYPE("bloodtype"),               // a blood draw
    LABS("labs"),                         // a lab panel / results
    MONITOR_WEIGHT("monitor_weight"),     // a weight or body-metric milestone
    SCHEDULE("schedule"),                 // a duration / "since" anchor
    FLAG("flag"),                         // a forward goal / future target counted down to
    BOOKMARK("bookmark"),                 // a marked personal date ("marked, not celebrated")
    HOME_HEALTH("home_health");           // home / at-home care

    companion object {
        val DEFAULT = EVENT
        fun fromStorageValueOrNull(value: String?): AnchorIcon? =
            entries.firstOrNull { it.storageKey == value }

        fun fromStorageValue(value: String?): AnchorIcon =
            fromStorageValueOrNull(value) ?: DEFAULT
    }
}
