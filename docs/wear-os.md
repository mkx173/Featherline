# Wear OS companion

The `:wear` module is a paired companion, not a second medication database. It
shows the phone's widget-derived schedule and estimated estradiol projection,
exposes a next-dose Tile, and sends explicit quick-log or skip requests back
to the phone.

## Trust boundary

- Room + SQLCipher on the phone remains the only source of truth.
- The phone sends only display-ready scheduled-dose rows and a bounded
  estradiol projection: group and schedule identifiers, scheduled time,
  completion status, localized display text, the most recent dose, the next
  five planned slots across day boundaries, the current estimated value, and
  25 curve samples at two-hour intervals covering the previous 48 hours.
- If “Hide medication details” is enabled, medicine, route, and dose text are
  blanked before the payload leaves the phone.
- The watch stores the latest snapshot with AES-256-GCM under a key generated
  in its own Android Keystore. Watch backup is disabled.
- The watch never receives the phone's full PK projection, blood tests, journal
  notes, stock levels, database keys, or backup passwords.
- The displayed concentration is a pharmacokinetic estimate derived from
  logged doses, not a laboratory measurement or medical advice.

## Transport

`wear-protocol` owns the bounded binary codec and the four Data Layer paths:

- `/featherline/dose-snapshot` — phone-to-watch cached display snapshot.
- `/featherline/request-dose-snapshot` — watch-to-phone refresh request.
- `/featherline/log-dose` — watch-to-phone scheduled-slot quick log.
- `/featherline/skip-dose` — watch-to-phone scheduled-slot skip.

Only the phone's `play` flavor registers `GooglePlayWearListenerService` and
binds `GooglePlayWearSnapshotSink`. The other distribution flavors inject an
empty sink set and contain no Play Services Wearable dependency.

## Action validation

The watch command contains a group UUID, optional schedule-time UUID, and ISO
local scheduled time. The phone parses each field strictly, then calls
`MedicationReminderActionHandler.logNow` for a log action or
`MedicationSkipActionHandler.skip` for a skip action. Logging reloads current
groups and logs, computes only missing entries, deducts stock, clears snoozes,
reschedules reminders, and rebuilds snapshots. Skipping stores the exact
scheduled slot on the phone for 14 days, clears its snooze, reschedules the
group, and excludes the slot from subsequent reminder and Wear snapshots.
Due, overdue, and the displayed next upcoming slot can be acted on. A stale
or repeated Tile tap therefore cannot bypass slot validation.

## Surfaces

- `MainActivity` shows the current estimated estradiol value, a smooth
  48-hour curve, the most recent dose record, and the next five planned slots,
  with quick-log and skip actions on the first actionable slot.
- `FeatherlineTileService` shows the current estimated estradiol value and the
  next actionable slot, with one-tap quick-log and skip actions for due,
  overdue, or upcoming entries. It shows local sync feedback for three seconds
  before advancing to the following slot.

## Refresh and battery policy

Rendering the app or Tile does not unconditionally wake the phone. The watch
requests a snapshot only when its cache is missing or older than 30 minutes,
with a 30-second retry interval for an empty cache and a five-minute retry
interval for stale data. The Tile also declares a 30-minute freshness interval.
Explicit log or skip actions remain immediate and request a fresh snapshot
after their three-second local feedback window.

## Development validation

```bash
./gradlew \
  :wear-protocol:test \
  :wear:testDebugUnitTest \
  :wear:lintDebug \
  :wear:assembleDebug \
  :app:testPlayDebugUnitTest \
  :app:assemblePlayDebug
```

End-to-end Data Layer behavior must also be verified on a paired Wear OS
emulator/phone emulator pair or physical devices signed with the same key.
