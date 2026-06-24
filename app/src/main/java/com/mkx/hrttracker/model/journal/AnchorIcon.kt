package com.mkx.hrttracker.model.journal

/**
 * Curated, stable icon keys for anchors (spec §3.1). The string [storageKey] is what
 * persists in Room and backups; unknown keys resolve to [DEFAULT] = EVENT so a
 * forward-compatible or corrupted value never crashes a restore or read-time mapping.
 *
 * Each entry resolves to a Material Symbols Rounded vector via `anchorIconRes` and a
 * label via `anchorIconLabelRes`. Most keys map to the matching `ic_<storageKey>`
 * drawable, but `home_health` deliberately points at a different glyph
 * (`ic_local_hospital`). The set spans clinical anchors (medication, injection,
 * labs, appointment, hospital) and personal life events (a goal, a trip, a birthday,
 * a highlight) — broadened beyond the original clinical-only, no-celebration voice.
 *
 * Storage keys are immutable once shipped (they persist in user data and backups);
 * add new icons by appending, never by renaming an existing key.
 */
enum class AnchorIcon(val storageKey: String) {
    EVENT("event"),                       // generic date / appointment / planned day (default + fallback)
    // Clinical: medication forms, then visits, then results / body metrics.
    MEDICATION("medication"),             // started or on a medication
    PILL("pill"),                         // an oral medication
    VACCINES("vaccines"),                 // an injection / immunisation
    WATER_DROPS("water_drops"),           // a topical gel dose
    HOME_HEALTH("home_health"),           // a hospital / clinic visit (uses ic_local_hospital)
    LABS("labs"),                         // a lab panel / results
    STETHOSCOPE("stethoscope"),           // a medical appointment / check-up
    MONITOR_WEIGHT("monitor_weight"),     // a weight or body-metric milestone
    MIC("mic"),                           // a voice milestone
    // Personal: a goal, travel, then loved-ones / highlights.
    FLAG("flag"),                         // a forward goal / future target counted down to
    TRAVEL("travel"),                     // a trip / time away
    PASSPORT("passport"),                 // a passport / travel-document marker
    FAVORITE("favorite"),                 // a personal / loved-one milestone
    STAR("star"),                         // a personal highlight
    CAKE("cake");                         // a birthday

    companion object {
        val DEFAULT = EVENT
        fun fromStorageValueOrNull(value: String?): AnchorIcon? =
            entries.firstOrNull { it.storageKey == value }

        fun fromStorageValue(value: String?): AnchorIcon =
            fromStorageValueOrNull(value) ?: DEFAULT
    }
}
