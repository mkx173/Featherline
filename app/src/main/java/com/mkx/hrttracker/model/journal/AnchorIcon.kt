package com.mkx.hrttracker.model.journal

/**
 * Curated, stable icon keys for anchors (spec §3.1). The string [storageKey] is what
 * persists in Room and backups; unknown keys resolve to [DEFAULT] = EVENT so a
 * forward-compatible or corrupted value never crashes a restore or read-time mapping.
 *
 * Each entry resolves to a Material Symbols Rounded vector via `anchorIconRes` and a
 * label via `anchorIconLabelRes`. Most keys map to the matching `ic_<storageKey>`
 * drawable, but a few deliberately point at a different glyph (`schedule` →
 * `ic_schedule_filled`, `home_health` → `ic_local_hospital`). The set spans clinical
 * anchors (medication, injection, blood draw, labs, appointment, hospital) and personal
 * life events (a goal, a trip, a birthday, a highlight) — broadened beyond the original
 * clinical-only, no-celebration voice.
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
    HOME_HEALTH("home_health"),           // a hospital / clinic visit (uses ic_local_hospital)
    FAVORITE("favorite"),                 // a personal / loved-one milestone
    STETHOSCOPE("stethoscope"),           // a medical appointment / check-up
    TRAVEL("travel"),                     // a trip / time away
    CAKE("cake"),                         // a birthday
    STAR("star");                         // a personal highlight

    companion object {
        val DEFAULT = EVENT
        fun fromStorageValueOrNull(value: String?): AnchorIcon? =
            entries.firstOrNull { it.storageKey == value }

        fun fromStorageValue(value: String?): AnchorIcon =
            fromStorageValueOrNull(value) ?: DEFAULT
    }
}
