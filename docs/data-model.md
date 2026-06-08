# Data model

This page covers the on-device Room database: how it's set up, what
each entity stores, the cross-cutting patterns (UUIDs, soft-delete,
column conventions), the DAOs, and the migration discipline. The
schema is encrypted at rest with SQLCipher; nothing in this page
describes data that ever leaves the device. For the export/import
format used for manual backups, see [backup-format.md](backup-format.md).

## Database setup

- [`HrtTrackerDatabase`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/HrtTrackerDatabase.kt)
  is the Room database at schema version 6. It declares 10 entities
  and exposes 6 DAOs. `exportSchema` is off — schemas are tracked via
  migration objects in source rather than committed schema JSON.
- [`DatabaseHolder`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/DatabaseHolder.kt)
  builds the database via `Room.databaseBuilder`, installs a
  `SupportOpenHelperFactory` from SQLCipher's `net.zetetic` artifact,
  and registers `MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`,
  `MIGRATION_4_5`, and `MIGRATION_5_6`.
  No `fallbackToDestructiveMigration`
  is wired — a missing migration crashes loudly in every build, debug
  and release, so silent data loss can't slip through.
- [`DatabasePassphraseProvider`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/DatabasePassphraseProvider.kt)
  produces the SQLCipher passphrase. A 32-byte random passphrase is
  generated on first launch, AES/GCM-encrypted with a key stored in
  the Android Keystore (`MASTER_KEY_ALIAS = "hrt_tracker_database_master_key"`),
  and the resulting ciphertext + IV is persisted to a regular
  `SharedPreferences` file (`hrt_tracker_secure_storage`). The
  passphrase itself never leaves the device.

## Entities

Ten `@Entity` classes across five files. Each blurb names the table,
what one row represents, key non-PK columns, FK relationships, and any
notable invariant.

### `MedicineEntity`

Defined in [`MedicineEntities.kt`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/MedicineEntities.kt).
Annotated `@Entity(tableName = "medicines")` — one row per known
medicine in the catalog (built-in plus user-defined custom), keyed on
a UUID. This is the canonical identity for "what substance, in what
package". Most group slots and log rows reference a row here by
`medicineUuid`; the app's normal write paths create **PATCH_OFF rows
with `medicineUuid = null`** and rely on
`applicationType = PATCH_OFF` for identity. Restore also accepts
compatible backups whose PATCH_OFF rows point at the singleton
medicine UUID. Stores the selection (`selectionKind` plus exactly one
of `medicationKey` for catalog rows or `customMedicationName` /
`customMedicationNameNormalized` for custom rows), the `category`
(estradiol, antiandrogen, etc.), the `preparationType` enum, the
preparation's numeric fields as a flat union of nullable columns
(`strengthMgPerTablet`, `strengthMgPerVial`, `concentrationMgPerMl`,
`vialVolumeMl`, `concentrationPercent`, `sachetWeightGrams`,
`containerWeightGrams`, `patchTotalMg`, `patchReleaseRateMcgPerDay`
— only the subset matching the preparation is populated), an optional
user-set `displayName` (hidden in the editor for built-in catalog
medicines), the `displayDoseUnit` the user picked for raw-mass entry
on custom medicines (mg / μg / g; catalog rows stay `MG`), and the
canonical `identityKey` fingerprint used by the repository to dedupe
on find-or-create. Soft-deleted via `archivedAtEpochMillis`. Indexed
unique on `identityKey`, plus secondary indices on
`archivedAtEpochMillis` and `category`. The `identityKey` index is
the dedup contract: two medicines that hash to the same canonical
string can't both exist, so editing a custom medicine's preparation
fields recomputes the key and may collide with an existing row.

Stock tracking is per-medicine state stored on this same row (added by
`MIGRATION_2_3`): `trackingEnabled` (opt-in flag, default `0`),
`stockUnitsRemaining` (the on-hand count — whole units for pool
preparations, or the count of *sealed* containers for multi-use vials /
gel containers), `stockUnitsLastTotal` (the gauge denominator set at the
last recount), `openContainerAmount` (current mL/g in the open container,
null for pool preparations), `warnAtDaysRemaining` (the per-medicine
low-stock threshold in days, default `14` — this is not a global
setting), and `stockGeneration` (a session token bumped on
enable/disable/recount/clear, but not on plain logging or top-ups). For
container preparations exactly one container stays open while sealed
stock remains — `openContainerAmount ≤ 0` with `stockUnitsRemaining ≥ 1`
is never persisted; `normalizeOpenContainer` heals it on every stock
write and model-read boundary by cracking one sealed container. All
are nullable or carry column defaults so pre-feature rows migrate
cleanly. The domain projection of these fields is `MedicineStock` in
`model/medication/MedicineStockModels.kt`; the runway/state math derived
from them lives in the stock repositories (see
[architecture.md](architecture.md#within-data)).

The PATCH_OFF sentinel — a singleton row with
`identityKey = "P|PATCH_OFF"`, `selectionKind = CATALOG`,
`preparationType = PATCH_OFF`, `category = ESTRADIOL` — is
auto-created the first time any patch medicine is inserted. Current
app-created PATCH_OFF slot/log rows do not FK to it; the row serves
the catalog manager, find-or-create lifecycle, and restore
compatibility. The repository enforces one-row-per-database semantics
by looking up that fixed key before inserting.

### `UserProfileEntity`

Defined in [`UserProfileEntity.kt`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/UserProfileEntity.kt).
Annotated `@Entity(tableName = "user_profile")` — a singleton row
keyed on `id = "default"`. Stores
the body-weight inputs that feed PK simulation
(`weightKg`, `weightOriginalValue`, `weightOriginalUnit` — the
original value plus its display unit are kept so the UI can
round-trip the user's chosen unit without re-converting). Updated
timestamp via `updatedAtEpochMillis`.

### `MedicationGroupEntity`

Defined in [`MedicationGroupEntities.kt`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/MedicationGroupEntities.kt).
Annotated `@Entity(tableName = "medication_groups")` — one row per
medication group, where a group is a set of medications that share
a reminder schedule. Stores `name`,
`colorKey`, `notificationsEnabled`, schedule type / interval / weekly
anchor, and `includePastScheduledSlots`. Soft-deleted via
`archivedAtEpochMillis` and `archivedAtLocalIso` (both null while
active); the archived-at timestamp also acts as the plan's
slot-generation end cutoff, and is user-selectable — it may be
backdated to an earlier day's end of day instead of the archive
moment. Replacement chains are tracked via `replacedByGroupUuid` and
`recreatedFromGroupUuid` so an edited group can preserve historical
fulfillment links.

### `MedicationGroupItemEntity`

Defined in [`MedicationGroupEntities.kt`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/MedicationGroupEntities.kt).
Annotated `@Entity(tableName = "medication_group_items")` — one row
per medication slot in a group. Two foreign keys: to
`medication_groups.uuid` with `ON DELETE CASCADE` (a deleted group
takes its slots with it), and to `medicines.uuid` with
`ON DELETE RESTRICT` (any medicine referenced by a group item, active
or archived, cannot be hard-deleted; repository checks separately
block archiving while active groups still reference it). Indexed on
both `groupUuid` and `medicineUuid`. The slot stores a nullable
`medicineUuid` pointing at the `medicines` row, the
`applicationType` route (oral, sublingual, injection, gel, patch
on/off), `count`, `gelApplicationArea`, and the `DoseInstruction` —
persisted as a
`doseInstructionKind` discriminator plus exactly one of
`tabletFractionNumerator` + `tabletFractionDenominator`, `doseVolumeMl`,
or `doseWeightGrams` (the `WHOLE_UNIT` and `NOOP` kinds carry no
numeric payload). Identity and per-medicine preparation strength used
to be denormalized onto each slot; those now resolve through the
`medicineUuid` FK, while the per-administration dose instruction
remains persisted on the slot. `medicineUuid` is nullable because
normal app-created **PATCH_OFF persisted slots store `null` here**
and rely on `applicationType = PATCH_OFF` for identity; restore also
accepts a UUID pointing at the PATCH_OFF sentinel for compatible
backups. All other persisted slots populate it.

### `MedicationGroupScheduleTimeEntity`

Defined in [`MedicationGroupEntities.kt`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/MedicationGroupEntities.kt).
Annotated `@Entity(tableName = "medication_group_schedule_times")` —
one row per time-of-day a group's schedule fires (`hourOfDay`,
`minuteOfHour`). FK to
`medication_groups.uuid` with `ON DELETE CASCADE` and an index on
`groupUuid`. `effectiveFromLocalIso` lets a schedule-time change
apply only to future occurrences without rewriting history.

### `MedicationGroupWeeklyDayEntity`

Defined in [`MedicationGroupEntities.kt`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/MedicationGroupEntities.kt).
Annotated `@Entity(tableName = "medication_group_weekly_days")` with
a composite primary key `(groupUuid, dayOfWeek)`; one row per
(group, weekday) selected when
the schedule type is weekly. FK to `medication_groups.uuid` with
`ON DELETE CASCADE`. Joined to `MedicationGroupScheduleTimeEntity` at
query time via `groupUuid` to expand the weekly cross-product into
concrete occurrences.

### `MedicationLogEntryEntity`

Defined in [`MedicationLogEntryEntity.kt`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/MedicationLogEntryEntity.kt).
Annotated `@Entity(tableName = "medication_log_entries")` — one row
per logged dose. Identity is a nullable `medicineUuid` reference
(no Room FK constraint on this column: the log is intentionally
decoupled from `medicines` so a future hard-delete of a stranded
medicine wouldn't cascade into history; integrity is enforced at the
repository layer instead). Also stores the snapshot category (kept
for fast filtering without joining the medicine row), the
`applicationType` route, `count`, `gelApplicationArea`, the
`DoseInstruction` (same `doseInstructionKind` + value-column scheme
as `MedicationGroupItemEntity`: `tabletFractionNumerator` /
`tabletFractionDenominator`, `doseVolumeMl`, or `doseWeightGrams`),
and `equivalentE2Mg` — the estradiol-equivalent mass precomputed by
`DoseInstructionCalculator` at insert time so PK queries don't
recompute it. Nullable whenever the calculator cannot derive a
catalog estradiol equivalent: non-estradiol categories, custom
medicines, all catalog estradiol patches (the simulator reads the
patch preparation directly), and PATCH_OFF. The nullable
`doseAmountDelta` records the signed difference between the actually
administered amount and the scheduled amount (set by the actual-amount
ruler at log time); it is folded into stock deduction and `equivalentE2Mg`
at insert, and null means the dose was taken as planned. Wall-clock timing is
`appliedAtEpochMillis` plus `appliedAtTimeZoneId` so backups
round-trip the originating zone.
The slot-fulfillment link is `sourceGroupUuid` / `scheduleTimeUuid` /
`scheduledForIso`, all nullable — a manual dose has no source group.
No FK to `medication_groups` either, so deleting a group does not
cascade into the log; the repository nulls these columns out via
`reclassifyEntriesForDeletedGroup`. The entity declares one secondary
index, `(category, appliedAtEpochMillis)`, which serves the
latest/category-bounded log reads and the home snapshot's
latest-antiandrogen lookup (a single-pass `ROW_NUMBER()` window query
that picks the latest entry per `(applicationType, medicineUuid,
sourceGroupUuid)` identity). The primary key remains `uuid`.

### `BloodTestPanelEntity`

Defined in [`BloodTestEntities.kt`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/BloodTestEntities.kt).
Annotated `@Entity(tableName = "blood_test_panels")` — one row per
blood-test sitting. Stores
`collectedAtInstantEpochMillis` / `collectedAtTimeZoneId` (when the
draw occurred), free-form `notes`, and the two
`timeSinceLast*DoseMillis` columns that snapshot dose-to-draw offsets
at panel-save time. Indexed on `collectedAtInstantEpochMillis` to
support the trend chart's chronological scan.

### `BloodTestResultEntity`

Defined in [`BloodTestEntities.kt`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/BloodTestEntities.kt).
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

Defined in [`BloodTestEntities.kt`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/BloodTestEntities.kt).
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

## Medicine identity and dose shape

`MedicineEntity` is a flat union of nullable columns, but the
in-memory model splits cleanly along two axes — defined in
[`MedicineModels.kt`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/medication/MedicineModels.kt).

- **`MedicinePreparation`** (sealed) describes what's in the package:
  `Pill`, `Capsule`, `InjectionSingleUseVial`,
  `InjectionMultiUseVial`, `GelSachet`, `GelContainer`, and `Patch`
  (the patch carries a `PatchSpecification` of either `TotalMg` or
  `ReleaseRateMcgPerDay`). The `PatchOff` data object is a sentinel
  preparation that lives on the global PATCH_OFF singleton; the PK
  simulator routes patch removals on `applicationType` alone, so it
  carries no numeric data.
- **`DoseInstruction`** (sealed) describes how much per administration:
  `TabletFraction(numerator, denominator)`, `WholeUnit`,
  `VolumeMl`, `WeightGrams`, `Noop`. Compatibility against a
  preparation type is encoded in
  [`MedicinePreparationForm.kt`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/medication/MedicinePreparationForm.kt)
  (e.g., `VolumeMl` is only valid with `INJECTION_MULTI_USE_VIAL`,
  `Noop` only with PATCH_OFF). Group slots and log entries persist
  the discriminator + value columns described in their entity
  sections; the model converts both ways.

Find-or-create dedup uses
[`MedicineIdentityKey`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/model/medication/MedicineIdentityKey.kt),
a canonical-string fingerprint over (selection, preparation type,
preparation fields). Catalog keys take the form
`C|<medicationKey>|<prep-type>|field=value...`, custom keys
`X|<normalized-name>|<prep-type>|field=value...`, and the PATCH_OFF
singleton has the fixed string `P|PATCH_OFF`. Numeric fields are
serialized via `BigDecimal` at scale 6 with `HALF_UP` rounding and
trailing zeros stripped, so 1.0 mg and 1.000000 mg hash identically.

Mass-equivalent dosing — what gets snapshotted into
`equivalentE2Mg` on each log row — is computed by
[`DoseInstructionCalculator`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/repository/DoseInstructionCalculator.kt)
from the medicine's preparation plus the dose instruction. It
replaces the per-entry estradiol math that used to live in
`EstradiolEquivalentCalculator` (deleted). Patch release-rate doses
have no scalar mass-equivalent and store `null`.

## Cross-cutting types

Patterns shared across entities:

- **Primary keys are `String` UUIDs** generated at insert time, not
  autoincrementing integers. This keeps IDs stable across exports,
  device migrations, and any future merge logic. The single exception
  is `UserProfileEntity`, which uses a hardcoded `"default"` ID
  because there is at most one row.
- **Soft-delete via `archivedAtEpochMillis`** rather than row
  deletion, so backups and history queries can still surface archived
  rows. Today this is used on `MedicineEntity` (there is no
  per-medicine hard-delete path in `MedicineRepository`;
  `logReferenceCount` locks preparation edits once any log row
  references the medicine, and `activeGroupReferenceCount` blocks
  archiving while any active group still references it — the
  `ON DELETE RESTRICT` FK on `medication_group_items.medicineUuid` is
  the database-side guard rail), `MedicationGroupEntity` (with a
  paired `archivedAtLocalIso` for time-zone-stable display), and
  `CustomBloodAnalyteEntity`. Child rows
  (`MedicationGroupItemEntity`, schedule times, weekly days) are not
  soft-deleted because they cascade-delete with the parent group;
  archival is a parent-row concern.
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

Six DAO interfaces, each backing the entities in its namesake area.

- [`MedicineDao`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/MedicineDao.kt)
  — catalog of `medicines`. Active/archived list observers,
  by-UUID and by-identity-key lookups, targeted updates
  (`updateDisplayName`, `updatePreparationFields`, `archive`,
  `unarchive`), and the two reference-count queries
  (`logReferenceCount`, `activeGroupReferenceCount`) the repository
  uses to lock preparation edits and reject archive attempts while
  active groups still reference the medicine. Stock support adds
  `updateStockFields` / `updateWarnAtDaysRemaining` (the targeted
  mutations behind recount, top-up, and threshold edits) plus
  `getAllActiveTrackedEntities` (the tracking-enabled medicines that
  feed low-stock projection). Also
  exposes `observeMedicineChangeVersion`, a `SELECT COUNT(*)` Flow used as a
  change-only signal: repositories that resolve medicines as a
  separate fetch off a group or log observation join on this so
  display-name / preparation / archive edits propagate without
  needing the primary table to change too.
- [`UserProfileDao`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/UserProfileDao.kt)
  — singleton-row read / observe / upsert for `user_profile`.
- [`MedicationGroupDao`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/MedicationGroupDao.kt)
  — group plus items plus schedule times plus weekly days, observed
  via Flow as the combined `MedicationGroupWithItemsEntity` projection.
  The `upsertGroupWithItems` `@Transaction` rewrites every child
  table to match the supplied lists in one atomic step.
- [`MedicationLogDao`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/MedicationLogDao.kt)
  — log inserts, batch reads by ID, the
  `getLatestEntryByCategoryOnOrBefore` query, schedule-time renames
  (`updateScheduledForTimeForEntries`), the
  `reclassifyEntriesForDeletedGroup` step that nulls out group
  references rather than dropping rows, and the
  `observeScheduledEntriesInWindow` / `getScheduledEntriesInWindow`
  reads that index already-logged scheduled doses for the stock
  runway projection.
- [`BloodTestDao`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/BloodTestDao.kt)
  — panels, results, and custom analytes; the
  `upsertPanelWithResults` `@Transaction`; the
  `getBuiltinTrendPoints` / `getCustomTrendPoints` projections that
  feed the chart.
- [`HomeDao`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/HomeDao.kt)
  — the composite queries feeding the home screen:
  `observeActiveGroups`, `observeScheduleEntries`,
  `observeLatestAntiandrogenEntriesOnOrBefore` (one row per
  antiandrogen medication signature, picked from the latest applied
  entry on or before the cutoff), `observeEstradiolPkEntries` (a
  bounded window of estradiol doses for PK simulation),
  `observeLatestEstradiolEntryOnOrBefore`, and the singleton-profile
  observer.

## Migration policy

The medicine-identity refactor reset the schema: v29 → v30 was
abandoned mid-flight and the database was collapsed to v1, with
`DatabaseMigrations.kt` deleted entirely. The current chain starts
fresh at v1 and bumps via `Migration` objects declared inline in
[`HrtTrackerDatabase.kt`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/HrtTrackerDatabase.kt).
The chain today is `MIGRATION_1_2` → `MIGRATION_2_3` → `MIGRATION_3_4`
→ `MIGRATION_4_5` → `MIGRATION_5_6`.
`MIGRATION_1_2` adds the `displayDoseUnit` column to `medicines` with a
`MG` default. `MIGRATION_2_3` adds the stock feature: the six stock
columns on `medicines` (see [`MedicineEntity`](#medicineentity) above),
all with column defaults or nullable, plus two now-abandoned per-log
columns (`stockDeductionUnits`, `stockGeneration`) on
`medication_log_entries`. `MIGRATION_3_4` then table-recreates
`medication_log_entries` to **drop** those two log columns: the design
moved away from per-entry deduction records and delete-time refunds, so
deduction is applied directly to the medicine row and the log table
carries no stock state. (This is why a logged dose is not refunded when
its entry is later edited or deleted — the user re-syncs via Adjust
Stock instead.) `MIGRATION_4_5` adds the nullable `doseAmountDelta`
column to `medication_log_entries` for the actual-amount feature.
`MIGRATION_5_6` adds the `(category, appliedAtEpochMillis)` index that
serves category/latest reads and the latest-antiandrogen home snapshot
query. The
reset deliberately did not register a `MIGRATION_29_*` shim, and no
`fallbackToDestructiveMigration` is wired in any build flavor: a
pre-refactor database does not migrate, it fails to open at startup
(in debug as well as release). The user has to manually clear app
data or reinstall to recover, so silent data loss is impossible. The
app is pre-release, so this only affects users who side-loaded a
pre-refactor build during development.

Cross-version compatibility for exports is the responsibility of the
[backup format](backup-format.md), not of Room migrations alone. The
backup file carries its own version field and validation pass so an
older backup can restore into a newer schema.
