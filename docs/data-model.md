# Data model

This page covers the on-device Room database: how it's set up, what
each entity stores, the cross-cutting patterns (UUIDs, soft-delete,
column conventions), the DAOs, and the migration discipline. The
schema is encrypted at rest with SQLCipher; nothing in this page
describes data that ever leaves the device. For the export/import
format used for manual backups, see [backup-format.md](backup-format.md).

## Database setup

- [`HrtTrackerDatabase`](https://github.com/mkx173/Featherline/blob/bf0f761debb69849638d5d0d01a85fe2809b6dcf/app/src/main/java/com/mkx/hrttracker/data/local/HrtTrackerDatabase.kt)
  is the Room database at schema version 29. It declares 9 entities
  and exposes 5 DAOs. `exportSchema` is off — schemas are tracked via
  migration objects rather than committed schema JSON.
- [`DatabaseHolder`](https://github.com/mkx173/Featherline/blob/bf0f761debb69849638d5d0d01a85fe2809b6dcf/app/src/main/java/com/mkx/hrttracker/data/local/DatabaseHolder.kt)
  builds the database via `Room.databaseBuilder`, installs a
  `SupportOpenHelperFactory` from SQLCipher's `net.zetetic` artifact,
  registers every migration declared in `DatabaseMigrations.kt`, and
  applies `fallbackToDestructiveMigration(dropAllTables = true)`
  **only in `BuildConfig.DEBUG`**. Release builds intentionally crash
  on a missing migration so silent data loss surfaces in stability
  metrics rather than wiping users.
- [`DatabasePassphraseProvider`](https://github.com/mkx173/Featherline/blob/bf0f761debb69849638d5d0d01a85fe2809b6dcf/app/src/main/java/com/mkx/hrttracker/data/local/DatabasePassphraseProvider.kt)
  produces the SQLCipher passphrase. A 32-byte random passphrase is
  generated on first launch, AES/GCM-encrypted with a key stored in
  the Android Keystore (`MASTER_KEY_ALIAS = "hrt_tracker_database_master_key"`),
  and the resulting ciphertext + IV is persisted to a regular
  `SharedPreferences` file (`hrt_tracker_secure_storage`). The
  passphrase itself never leaves the device.

## Entities

Nine `@Entity` classes across four files. Each blurb names the table,
what one row represents, key non-PK columns, FK relationships, and any
notable invariant.

### `UserProfileEntity`

Defined in [`UserProfileEntity.kt`](https://github.com/mkx173/Featherline/blob/bf0f761debb69849638d5d0d01a85fe2809b6dcf/app/src/main/java/com/mkx/hrttracker/data/local/UserProfileEntity.kt).
Annotated `@Entity(tableName = "user_profile")` — a singleton row
keyed on `id = "default"`. Stores
the body-weight inputs that feed PK simulation
(`weightKg`, `weightOriginalValue`, `weightOriginalUnit` — the
original value plus its display unit are kept so the UI can
round-trip the user's chosen unit without re-converting). Updated
timestamp via `updatedAtEpochMillis`.

### `MedicationGroupEntity`

Defined in [`MedicationGroupEntities.kt`](https://github.com/mkx173/Featherline/blob/bf0f761debb69849638d5d0d01a85fe2809b6dcf/app/src/main/java/com/mkx/hrttracker/data/local/MedicationGroupEntities.kt).
Annotated `@Entity(tableName = "medication_groups")` — one row per
medication group, where a group is a set of medications that share
a reminder schedule. Stores `name`,
`colorKey`, `notificationsEnabled`, schedule type / interval / weekly
anchor, and `includePastScheduledSlots`. Soft-deleted via
`archivedAtEpochMillis` and `archivedAtLocalIso` (both null while
active); replacement chains are tracked via `replacedByGroupUuid` and
`recreatedFromGroupUuid` so an edited group can preserve historical
fulfillment links.

### `MedicationGroupItemEntity`

Defined in [`MedicationGroupEntities.kt`](https://github.com/mkx173/Featherline/blob/bf0f761debb69849638d5d0d01a85fe2809b6dcf/app/src/main/java/com/mkx/hrttracker/data/local/MedicationGroupEntities.kt).
Annotated `@Entity(tableName = "medication_group_items")` — one row
per medication slot in a group. FK to `medication_groups.uuid` with
`ON DELETE CASCADE` and an
index on `groupUuid`. Stores the medication identity (category,
applicationType, selectionKind, builtin `medicationKey` or
`customMedicationName`), the dose shape (`doseKind` plus the
appropriate `doseValueMg` / `doseValuePercent` / `doseWeightGrams` /
`doseReleaseRateMcgPerDay`), and `gelApplicationArea`.

### `MedicationGroupScheduleTimeEntity`

Defined in [`MedicationGroupEntities.kt`](https://github.com/mkx173/Featherline/blob/bf0f761debb69849638d5d0d01a85fe2809b6dcf/app/src/main/java/com/mkx/hrttracker/data/local/MedicationGroupEntities.kt).
Annotated `@Entity(tableName = "medication_group_schedule_times")` —
one row per time-of-day a group's schedule fires (`hourOfDay`,
`minuteOfHour`). FK to
`medication_groups.uuid` with `ON DELETE CASCADE` and an index on
`groupUuid`. `effectiveFromLocalIso` lets a schedule-time change
apply only to future occurrences without rewriting history.

### `MedicationGroupWeeklyDayEntity`

Defined in [`MedicationGroupEntities.kt`](https://github.com/mkx173/Featherline/blob/bf0f761debb69849638d5d0d01a85fe2809b6dcf/app/src/main/java/com/mkx/hrttracker/data/local/MedicationGroupEntities.kt).
Annotated `@Entity(tableName = "medication_group_weekly_days")` with
a composite primary key `(groupUuid, dayOfWeek)`; one row per
(group, weekday) selected when
the schedule type is weekly. FK to `medication_groups.uuid` with
`ON DELETE CASCADE`. Joined to `MedicationGroupScheduleTimeEntity` at
query time via `groupUuid` to expand the weekly cross-product into
concrete occurrences.

### `MedicationLogEntryEntity`

Defined in [`MedicationLogEntryEntity.kt`](https://github.com/mkx173/Featherline/blob/bf0f761debb69849638d5d0d01a85fe2809b6dcf/app/src/main/java/com/mkx/hrttracker/data/local/MedicationLogEntryEntity.kt).
Annotated `@Entity(tableName = "medication_log_entries")` — one row
per logged dose. Stores the same medication-identity and dose-shape
columns as `MedicationGroupItemEntity`, plus `dosageMgAsEstradiol` (the
estradiol-equivalent dose precomputed at insert time so PK queries
don't recompute it), `appliedAtEpochMillis` /
`appliedAtTimeZoneId` (the wall-clock instant the user logged the
dose, plus the originating zone so backups round-trip), and the
slot-fulfillment link `sourceGroupUuid` / `scheduleTimeUuid` /
`scheduledForIso` (all nullable — a manual dose has no source group).
No FK to `medication_groups` so deleting a group does not cascade
into the log; instead the repository nulls these columns out via
`reclassifyEntriesForDeletedGroup`. The entity declares no secondary
indices — only the `@PrimaryKey` on `uuid` — so the home-screen fast
path `HomeDao.getLatestEstradiolEntryOnOrBefore` and the more general
`MedicationLogDao.getLatestEntryByCategoryOnOrBefore` both filter by
`category` and order by `appliedAtEpochMillis DESC LIMIT 1` against an
unindexed scan. The log table stays small enough that this is
acceptable; revisit if a future feature retains the full history at
scale.

### `BloodTestPanelEntity`

Defined in [`BloodTestEntities.kt`](https://github.com/mkx173/Featherline/blob/bf0f761debb69849638d5d0d01a85fe2809b6dcf/app/src/main/java/com/mkx/hrttracker/data/local/BloodTestEntities.kt).
Annotated `@Entity(tableName = "blood_test_panels")` — one row per
blood-test sitting. Stores
`collectedAtInstantEpochMillis` / `collectedAtTimeZoneId` (when the
draw occurred), free-form `notes`, and the two
`timeSinceLast*DoseMillis` columns that snapshot dose-to-draw offsets
at panel-save time. Indexed on `collectedAtInstantEpochMillis` to
support the trend chart's chronological scan.

### `BloodTestResultEntity`

Defined in [`BloodTestEntities.kt`](https://github.com/mkx173/Featherline/blob/bf0f761debb69849638d5d0d01a85fe2809b6dcf/app/src/main/java/com/mkx/hrttracker/data/local/BloodTestEntities.kt).
Annotated `@Entity(tableName = "blood_test_results")` — one row per
analyte result inside a panel. FK to `blood_test_panels.uuid`
(`ON DELETE CASCADE`) and an
optional FK to `custom_blood_analytes.uuid` (`ON DELETE RESTRICT`).
Each row carries either `builtinAnalyteKey` or `customAnalyteUuid`
(never both). Stores `value` and `unitSnapshot` (the user-entered
value and the unit it was entered in) plus `canonicalValue` — the
same value pre-converted to the analyte's canonical unit so trend
queries can sort across panels without re-running conversion. Indexed
on `panelUuid`, on each analyte key, and on three unique composite
indices: `(panelUuid, displayOrder)` enforces per-panel ordering, and
`(panelUuid, builtinAnalyteKey)` plus `(panelUuid, customAnalyteUuid)`
enforce one-row-per-analyte within a panel.

### `CustomBloodAnalyteEntity`

Defined in [`BloodTestEntities.kt`](https://github.com/mkx173/Featherline/blob/bf0f761debb69849638d5d0d01a85fe2809b6dcf/app/src/main/java/com/mkx/hrttracker/data/local/BloodTestEntities.kt).
Annotated `@Entity(tableName = "custom_blood_analytes")` — one row
per user-defined analyte not
present in the builtin `BloodTestCatalog`. Stores raw `name` /
`abbreviation` / `unitLabel` alongside their normalized siblings
(lowercased, trimmed) used for the unique
`(normalizedName, normalizedUnitLabel)` index. Soft-deleted via
`archivedAtEpochMillis`. Custom analytes have no canonical-unit
factor, so `BloodTestResultEntity.canonicalValue` for a custom-analyte
row is the raw value (no conversion).

### Projection / join data classes

Three `data class` declarations in the same files are not themselves
`@Entity` annotated; they are Room projections or DAO query result
types:

- `MedicationGroupWithItemsEntity` — `@Embedded` group plus
  `@Relation` lists of items, schedule times, and weekly days. Used
  by `MedicationGroupDao` and `HomeDao` to read a group and its
  children in one transaction.
- `BloodTestPanelWithResultsEntity` — `@Embedded` panel plus a
  `@Relation` list of results. Used by `BloodTestDao` for the panel
  detail view.
- `BloodTestTrendPointEntity` — flat projection returned by the
  chart-trend queries. Carries `panelUuid`, `resultUuid`,
  `collectedAtInstantEpochMillis`, `collectedAtTimeZoneId`, `value`,
  `unitSnapshot`, and `canonicalValue`. Not a row class; not joinable.

## Cross-cutting types

Patterns shared across entities:

- **Primary keys are `String` UUIDs** generated at insert time, not
  autoincrementing integers. This keeps IDs stable across exports,
  device migrations, and any future merge logic. The single exception
  is `UserProfileEntity`, which uses a hardcoded `"default"` ID
  because there is at most one row.
- **Soft-delete via `archivedAtEpochMillis`** rather than row
  deletion, so backups and history queries can still surface archived
  rows. Today this is used on `MedicationGroupEntity` (with a paired
  `archivedAtLocalIso` for time-zone-stable display) and on
  `CustomBloodAnalyteEntity`. Child rows (`MedicationGroupItemEntity`,
  schedule times, weekly days) are not soft-deleted because they
  cascade-delete with the parent group; archival is a parent-row
  concern.
- **Time is stored as epoch milliseconds plus an optional time-zone
  ID**, not as Room type-converted `Instant`. There is no
  `@TypeConverters` class in `data/local/`. Wall-clock dates and
  times that must survive a time-zone change are stored as ISO-8601
  strings (`scheduledForIso`, `archivedAtLocalIso`,
  `effectiveFromLocalIso`). Enums are stored as their `name()`
  string and parsed in the repository layer via `fromStorageValue`
  helpers on the model enums.
- **Indices are declared explicitly per entity** on the columns that
  back foreign keys and the columns that drive observed Flow queries
  (the home-screen panel-by-time scan, the trend-chart sort, the
  group-items lookup by `groupUuid`).

## DAOs

Five DAO interfaces, each backing the entities in its namesake area.

- [`UserProfileDao`](https://github.com/mkx173/Featherline/blob/bf0f761debb69849638d5d0d01a85fe2809b6dcf/app/src/main/java/com/mkx/hrttracker/data/local/UserProfileDao.kt)
  — singleton-row read / observe / upsert for `user_profile`.
- [`MedicationGroupDao`](https://github.com/mkx173/Featherline/blob/bf0f761debb69849638d5d0d01a85fe2809b6dcf/app/src/main/java/com/mkx/hrttracker/data/local/MedicationGroupDao.kt)
  — group plus items plus schedule times plus weekly days, observed
  via Flow as the combined `MedicationGroupWithItemsEntity` projection.
  The `upsertGroupWithItems` `@Transaction` rewrites every child
  table to match the supplied lists in one atomic step.
- [`MedicationLogDao`](https://github.com/mkx173/Featherline/blob/bf0f761debb69849638d5d0d01a85fe2809b6dcf/app/src/main/java/com/mkx/hrttracker/data/local/MedicationLogDao.kt)
  — log inserts, batch reads by ID, the
  `getLatestEntryByCategoryOnOrBefore` query, schedule-time renames
  (`updateScheduledForTimeForEntries`), and the
  `reclassifyEntriesForDeletedGroup` step that nulls out group
  references rather than dropping rows.
- [`BloodTestDao`](https://github.com/mkx173/Featherline/blob/bf0f761debb69849638d5d0d01a85fe2809b6dcf/app/src/main/java/com/mkx/hrttracker/data/local/BloodTestDao.kt)
  — panels, results, and custom analytes; the
  `upsertPanelWithResults` `@Transaction`; the
  `getBuiltinTrendPoints` / `getCustomTrendPoints` projections that
  feed the chart.
- [`HomeDao`](https://github.com/mkx173/Featherline/blob/bf0f761debb69849638d5d0d01a85fe2809b6dcf/app/src/main/java/com/mkx/hrttracker/data/local/HomeDao.kt)
  — the composite queries feeding the home screen:
  `observeActiveGroups`, `observeScheduleEntries`,
  `observeLatestAntiandrogenEntriesOnOrBefore` (one row per
  antiandrogen medication signature, picked from the latest applied
  entry on or before the cutoff), `observeEstradiolPkEntries` (a
  bounded window of estradiol doses for PK simulation),
  `observeLatestEstradiolEntryOnOrBefore`, and the singleton-profile
  observer.

## Migration policy

Every schema bump ships a `Migration` object in
[`DatabaseMigrations.kt`](https://github.com/mkx173/Featherline/blob/bf0f761debb69849638d5d0d01a85fe2809b6dcf/app/src/main/java/com/mkx/hrttracker/data/local/DatabaseMigrations.kt)
in the same change as the schema bump. As of this writing, the
migration list spans `MIGRATION_19_20` through `MIGRATION_28_29` —
10 migrations on top of the v19 baseline.

`fallbackToDestructiveMigration(dropAllTables = true)` is wired only
in `BuildConfig.DEBUG` builds, where side-loading older APKs during
development is common. **Release builds do not fall back** — a missing
migration crashes loudly on startup so the failure surfaces in
stability metrics rather than silently wiping user data.

Cross-version compatibility for exports is the responsibility of the
[backup format](backup-format.md), not of Room migrations alone. The
backup file carries its own version field and validation pass so an
older backup can restore into a newer schema.
