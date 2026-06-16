# Backup format

How Featherline exports user data to a single encrypted file and how
that file is read back. The whole subsystem lives in
[`data/backup/`](https://github.com/mkx173/Featherline/tree/main/app/src/main/java/com/mkx/hrttracker/data/backup).

## Two version numbers

- **Envelope format version** —
  [`CURRENT_BACKUP_CONTAINER_VERSION = 3`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt).
  Describes the on-disk byte layout. Bumps are rare and crypto-
  breaking — they cover changes to the framing or to the
  cryptographic primitives.
- **Snapshot JSON version** —
  [`CURRENT_BACKUP_SNAPSHOT_VERSION = 5`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupSnapshot.kt).
  Describes the plaintext payload — the `BackupSnapshot` data-class
  tree serialized as JSON. Bumps are reserved for renames, removals,
  or semantic changes to existing fields. The restore path also
  enforces a floor of
  [`MIN_SUPPORTED_BACKUP_SNAPSHOT_VERSION = 2`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupRestoreService.kt):
  v1 files are rejected with no migration path, because the medicine-
  identity refactor renamed and removed denormalized fields on group
  items and log entries (see "Cross-version restore matrix" below).

The envelope reader still accepts a legacy v2 envelope: same framing
without the compression byte or uncompressed-length field, payload
stored as-is. Writers only emit version `3`.

## v3 envelope structure

One contiguous byte sequence: a 65-byte header followed by AES-GCM
ciphertext with its 16-byte tag appended. Built by
[`buildArgon2Header`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt)
and parsed by
[`parseContainer`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt):

```text
offset  size  field
------  ----  -----------------------------------------------
   0      7   magic               "HRTBKP1" (ASCII)
   7      1   envelope version    3
   8      1   kdf identifier      2  (Argon2id)
   9      1   cipher identifier   1  (AES-256-GCM)
  10      1   compression         1  (gzip)
  11      8   uncompressed length big-endian int64, plaintext bytes
  19      4   argon2 time cost    big-endian int32, iterations
  23      4   argon2 memory cost  big-endian int32, KiB
  27      4   argon2 parallelism  big-endian int32, lanes
  31      4   argon2 hash length  big-endian int32, key bytes (32)
  35      1   salt length         16
  36      1   nonce length        12
  37     16   salt                Argon2 input salt
  53     12   nonce               AES-GCM IV
  65      N   ciphertext + tag    GCM ciphertext || 16-byte tag
```

All multi-byte integers are big-endian (`java.nio.ByteBuffer` default).
The full 65-byte header is fed to AES-GCM as Additional Authenticated
Data, so tampering with any declared parameter fails the auth check at
decrypt time. Salt and nonce lengths are read from the header rather
than assumed, so differently-sized envelopes still parse.
[`FIXED_HEADER_LENGTH_V3 = 37`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt)
gates the minimum-bytes check; the legacy v2 header was 28 bytes.

## Encryption

### Key derivation

[`BackupArgon2KeyDeriver`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt)
wraps the [argon2kt][argon2kt] library and calls `Argon2Mode.ARGON2_ID`
with the parameters read from the header and a 16-byte random salt.
Defaults from
[`DEFAULT_ARGON2_PARAMETERS`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt):
time cost `3` iterations, memory cost `65 536` KiB (64 MiB),
parallelism `1` lane, hash length `32` bytes (matches AES-256 key
length), mode Argon2id. Storing parameters in the envelope rather than
the binary means raising defaults later won't break old backups —
they decrypt with the parameters they were written with.

Passwords arrive as `CharArray`; intermediate buffers (UTF-8 password
bytes, derived key, salt, nonce, decompressed plaintext) are zeroed
via `fill(0)` in `finally` blocks. This bounds heap exposure but is
not a defence against an in-process attacker.

[argon2kt]: https://github.com/lambdapioneer/argon2kt

### Symmetric encryption

`AES/GCM/NoPadding` via platform JCA in
[`encryptWithAesGcm`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt):
32-byte key from Argon2; 12-byte nonce fresh per export from
`SecureRandom`, never reused; 128-bit auth tag appended to the
ciphertext by JCA. The full 65-byte header is the AAD, so tampering
with any declared parameter fails the tag check. On decrypt, a tag
mismatch becomes `IOException("Unable to decrypt the selected backup
file.")` — wrong password and tampered ciphertext are not
distinguished externally.

The plaintext fed into AES-GCM is `gzip(json)`, not the JSON. The
header's uncompressed-length field is the pre-gzip JSON byte count;
gunzip refuses payloads that don't match or that exceed the
[`MAX_BACKUP_JSON_BYTES = 128 MiB`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt)
cap (decompression-bomb defence).

## Snapshot tree

Sixteen `@JsonClass` data classes in
[`BackupSnapshot.kt`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupSnapshot.kt).
The tree is the wire-format mirror of the Room schema documented in
[`data-model.md`](data-model.md#entities); each row maps one
snapshot class to its entity. Nested-in-line snapshots are children
flattened into the parent's JSON.

- `BackupSnapshot` — top-level: `snapshotVersion`,
  `exportedAtEpochMillis`, plus the sub-snapshots below. The
  `medicines` list is positioned ahead of `medicationGroups` and
  `medicationLogs` so the importer can build its valid-medicine FK set
  before walking any item or log that references one. The
  `trackedDates` and `notes` lists are defaulted to `emptyList()` so
  old backups restore with an empty Journal. A same-version pre-field
  v5 reader may also restore a v5 backup containing those keys and
  ignore them; future snapshot versions are still rejected by version
  checks. They are additive fields, so
  `CURRENT_BACKUP_SNAPSHOT_VERSION` remains `5`.
- `BackupAppSnapshot` — just `packageName`; exports write the stable
  backup identity (`com.mkx.hrttracker`), and restore rejects other app
  identities.
- `BackupSettingsSnapshot` — flat DataStore values (dark mode,
  adaptive color, AMOLED pure-black, `hazeBlurEnabled`,
  `cjkTextOffsetEnabled`, reminders,
  archived-record visibility,
  reference-range visibility, app-lock grace period, hide-screen-content,
  onboarding, language, `firstDayOfWeekOption`, home E2 display unit,
  home E2 chart window, per-analyte calibration default units,
  last-seen time-zone, `hideMedicationDetails`, `widgetAppearance` (the
  encoded widget-appearance default — accent hue, saturation, light
  balance, scale, opacity, dark mode), the `groupNameCounter` used to
  suffix default group names, and the stock-tracking-nudge flags
  `stockNudgeEnabled` / `stockNudgeUserEnabled`). The legacy
  `widgetContentScale` / `widgetBackgroundAlpha` / `widgetDarkModeOption`
  fields are still written as mirrors of the appearance so older app
  versions can read new backups; on restore, a present `widgetAppearance`
  wins and backups predating it restore through the legacy fields.
  `screenLockProtectionEnabled`
  is intentionally excluded — app-lock stays local to the device that set
  it. The nudge's running dismiss count is also excluded: restore clears it
  so the auto-disable threshold starts fresh on the new device.
- `BackupUserProfileSnapshot` →
  [`UserProfileEntity`](data-model.md#userprofileentity); carries
  `weightKg` plus the original value + unit for display round-trip.
- `BackupMedicineSnapshot` →
  [`MedicineEntity`](data-model.md#medicineentity); the canonical
  identity row that most group items and log entries point at via
  `medicineUuid` (normal app-created PATCH_OFF rows store `null`
  instead and rely on `applicationType = PATCH_OFF`; restore also
  accepts compatible backups whose PATCH_OFF rows point at the
  singleton UUID). Carries the `selectionKind` discriminator plus
  `medicationKey` (catalog selections) or
  `customMedicationName` + `customMedicationNameNormalized` (custom
  selections); `category`; the `preparationType` enum plus its
  per-shape numeric strengths (`strengthMgPerTablet` — dual-purpose
  for PILL and CAPSULE — `strengthMgPerVial`, `concentrationMgPerMl`,
  `vialVolumeMl`, `concentrationPercent`, `sachetWeightGrams`,
  `containerWeightGrams`, `patchTotalMg`,
  `patchReleaseRateMcgPerDay`; import-only injection/gel preparations
  store their administered/applied mg in `strengthMgPerVial` and are
  discriminated by `preparationType`); optional `displayName`;
  `identityKey` for duplicate detection; created/updated/archived
  timestamps; an
  optional `displayDoseUnit` (added when the custom-medicine unit
  picker shipped; missing values default to `MG` on restore);
  `importedFromExternalTracker` (missing values default to `false`);
  and an
  optional nested `stock` →
  `BackupMedicineStockSnapshot` (`trackingEnabled`, `unitsRemaining`,
  `unitsLastTotal`, `openContainerAmount`, `warnAtDaysRemaining`,
  `stockGeneration`). The export path writes `stock` **only when
  tracking is enabled** for the medicine, otherwise `null`; restore maps
  the fields back with null-safe defaults (`warnAtDaysRemaining` → 14,
  `stockGeneration` → 0), so backups predating the stock feature simply
  restore as untracked.
- `BackupMedicationGroupSnapshot` →
  [`MedicationGroupEntity`](data-model.md#medicationgroupentity),
  with three child snapshots nested in-line:
  `BackupMedicationGroupScheduleSnapshot` (schedule shape + times
  list); `BackupMedicationGroupScheduleTimeSnapshot` →
  [`MedicationGroupScheduleTimeEntity`](data-model.md#medicationgroupscheduletimeentity);
  `BackupMedicationGroupItemSnapshot` →
  [`MedicationGroupItemEntity`](data-model.md#medicationgroupitementity).
  Group-item rows carry `count`, a nullable `medicineUuid` (PATCH_OFF
  rows may omit it; restore also accepts a UUID pointing at the
  PATCH_OFF sentinel), `applicationType`,
  `doseInstructionKind`, and the dose-shape numerics matching that
  kind (`tabletFractionNumerator` / `tabletFractionDenominator`,
  `doseVolumeMl`, `doseWeightGrams`, `gelApplicationArea`). The
  pre-refactor denormalized identity fields (`medicationKey`,
  `customMedicationName`, `doseKind`, `doseValueMg`, `customDoseUnit`,
  `doseValuePercent`, `doseReleaseRateMcgPerDay`) are gone — the
  referenced `BackupMedicineSnapshot` owns identity and per-medicine
  strength now. Weekly days are not a snapshot class — they
  reconstruct from `schedule.weeklyDaysOfWeek`.
- `BackupMedicationLogSnapshot` →
  [`MedicationLogEntryEntity`](data-model.md#medicationlogentryentity);
  carries the historical `category` (preserved so logs stay
  classifiable even after the referenced medicine is archived or
  recategorised), a nullable `medicineUuid` (PATCH_OFF logs may omit
  it; restore also accepts a UUID pointing at the PATCH_OFF sentinel),
  `applicationType`, `doseInstructionKind` plus matching
  dose-shape numerics (`tabletFractionNumerator` /
  `tabletFractionDenominator`, `doseVolumeMl`, `doseWeightGrams`,
  `gelApplicationArea`), `equivalentE2Mg` (the snapshotted PK input —
  nullable whenever `DoseInstructionCalculator` cannot derive a
  catalog estradiol equivalent: non-estradiol categories, custom
  medicines, all catalog estradiol patches, and PATCH_OFF), the
  slot-fulfillment link
  (`sourceGroupUuid` / `scheduleTimeUuid` / `scheduledForIso`),
  applied time + zone, `count`, and the nullable `doseAmountDelta`
  (the actual-vs-scheduled amount difference; null when taken as
  planned), plus nullable `importSourceApp` / `importExternalId`
  provenance for imported external rows. The pre-refactor denormalized
  identity fields (`selectionKind`, `medicationKey`,
  `customMedicationName`, `doseKind`, `doseValueMg`, `customDoseUnit`,
  `doseValuePercent`, `doseReleaseRateMcgPerDay`,
  `dosageMgAsEstradiol`) were dropped in the same v1 → v2 cut.
- `BackupCustomBloodAnalyteSnapshot` →
  [`CustomBloodAnalyteEntity`](data-model.md#custombloodanalyteentity).
- `BackupBloodTestPanelSnapshot` →
  [`BloodTestPanelEntity`](data-model.md#bloodtestpanelentity);
  carries panel timing/notes/dose-offset snapshots plus nullable
  `importSourceApp` / `importPanelKey` provenance; results are nested
  in-line.
- `BackupBloodTestResultSnapshot` →
  [`BloodTestResultEntity`](data-model.md#bloodtestresultentity);
  carries either `builtinAnalyteKey` or `customAnalyteUuid`
  (exclusive), `value` + `unitSnapshot`, `canonicalValue`, and
  nullable `importSourceApp` / `importExternalId` provenance.
- `BackupTrackedDateSnapshot` →
  [`TrackedDateEntity`](data-model.md#trackeddateentity); carries the
  journal anchor UUID, `name`, `iconKey`, wall-clock `dateIso`, nullable
  `paletteKey`, nullable `pinnedOrder`, and created/updated timestamps.
  Restore validates UUID shape, trims `name`, rejects blank names,
  rejects duplicate tracked-date UUIDs, and rejects negative
  `pinnedOrder` values. Duplicate `pinnedOrder` values are tolerated —
  it is a sort key, not a uniqueness invariant, and read-time ordering
  breaks ties deterministically — so a reorder/unpin that leaves two
  rows sharing an order still round-trips. `iconKey` and `paletteKey`
  are restored as stored, even when this build does not know the key
  yet; read-time mappers apply their fallbacks without rewriting the
  backup value.
- `BackupNoteSnapshot` →
  [`NoteEntity`](data-model.md#noteentity); carries the note UUID,
  unique wall-clock `dateIso`, `text`, and created/updated timestamps.
  Restore rejects duplicate note UUIDs and duplicate note dates in the
  same file, preserving the one-note-per-day invariant before SQLite's
  unique index is reached.

Serialization is by Moshi via
[`BackupSnapshotJsonCodec`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupSnapshotJsonCodec.kt)
with `.serializeNulls()` enabled — `null` fields are written
explicitly and distinguishable from missing-on-read.

## Export flow

[`BackupExportService.kt`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupExportService.kt)
runs on `Dispatchers.IO`:

1. Read every backed-up table via its repository, including journal
   tracked dates and notes through `JournalRepository`.
2. Build the in-memory `BackupSnapshot`, mapping each Room entity to
   its `Backup*Snapshot`.
3. Encode to JSON via `BackupSnapshotJsonCodec.encode`.
4. Gzip the JSON.
5. Derive a 32-byte AES-256 key with Argon2id over a fresh 16-byte
   salt.
6. Encrypt with AES-GCM under a fresh 12-byte nonce; the 65-byte
   header is the AAD.
7. Write the envelope to a temp file in `context.cacheDir`
   (`prepareBackupExport`).
8. On user confirmation of the destination folder, copy the temp file
   to a SAF document via `DocumentsContract.createDocument`
   (`exportPreparedBackup`); delete the temp file in `finally`
   whether the copy succeeded or failed.

Splitting at step 7 lets the password dialog and the directory picker
run as separate user steps without holding plaintext or unsealed keys
between them.

## Restore flow

[`BackupRestoreService.kt`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupRestoreService.kt)
runs on `Dispatchers.IO`. Validation is layered into four passes so
incompatible files are rejected at the cheapest detection point.

1. **Buffer the file.** SAF picker grants are single-use, so the
   bytes are read into memory up front; later passes re-read the
   buffer.
2. **Pass 1 — envelope shape.**
   [`validateEncryptedBackupContainer`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt)
   parses magic, version, KDF / cipher / compression identifiers, and
   Argon2 parameters. Failures wrap as
   `IncompatibleBackupFileException` so the UI can distinguish "not a
   backup" from "wrong password". Runs before the password dialog
   opens.
3. **Pass 2 — decrypt + auth-tag verify.**
   [`decrypt`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt)
   derives the key from the entered password and the envelope's salt,
   runs AES-GCM with the header as AAD.
4. **Pass 3 — decompress + JSON decode.** Gunzip refuses payloads
   whose expansion exceeds the declared `uncompressedLengthBytes` or
   the 128 MiB cap; `BackupSnapshotJsonCodec.decode` throws on
   missing required fields.
5. **Pass 4 — semantic validation.**
   [`toValidatedSnapshot`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupRestoreService.kt)
   runs these checks:
   - version + identity: `snapshotVersion` must fall in
     `MIN_SUPPORTED_BACKUP_SNAPSHOT_VERSION..CURRENT_BACKUP_SNAPSHOT_VERSION`
     (currently `2..5`); v1 backups are rejected here with no
     migration path because the medicine-identity refactor removed
     the denormalized identity fields older payloads relied on.
     `app.packageName` must match the stable backup identity
     (`com.mkx.hrttracker`), not the installed package name.
   - parseability: enum-name resolution, UUID parsing, `DayOfWeek`
     and `ZoneId.of` round-trips
   - value sanity: positive-finite doses, including `equivalentE2Mg`
     on log entries when present
   - FK consistency: the medicines section is consumed first, and the
     resulting `{medicineUuid → MedicineEntity}` map is what every
     subsequent group-item and log-entry check resolves against —
     non-PATCH_OFF rows must point at a medicine present in this
     backup, active groups must not reference an archived medicine,
     and medicine identity keys must be unique within the file. After
     that, every log's `sourceGroupUuid` must reference a restored
     group; every custom-analyte-keyed result must reference a custom
     analyte in the same snapshot; every `scheduleTimeUuid` must
     belong to its log's source group.
   - journal invariants: tracked-date UUIDs must parse and be unique;
     tracked-date names are trimmed and must not be blank; non-null
     tracked-date `pinnedOrder` values must not be negative (duplicates
     are allowed — read-time ordering tie-breaks); tracked-date and
     note `dateIso` values must parse as `LocalDate`; note UUIDs must
     parse and be unique; note text is trimmed and must not be blank;
     and note dates must be unique within the file. Restore also
     validates that journal `updatedAtEpochMillis` values are not before
     their corresponding `createdAtEpochMillis` values. Journal
     `iconKey` and `paletteKey` values are preserved as stored so newer
     key values can round-trip; the app's read-time mappers own
     fallbacks for unknown keys.
   - external-import invariants: imported medicines must use the
     `E|` identity namespace, cannot include stock blocks, cannot be
     referenced by medication groups, and are the only medicines that
     may carry `IMPORTED_INJECTION` or `IMPORTED_GEL` preparations.
     Imported medication logs must carry both provenance fields and
     either reference an imported medicine or be a PATCH_OFF row;
     imported panels/results likewise require complete source/key
     pairs. Duplicate provenance within a row type is rejected so
     restoring a backup preserves later reimport idempotency.
   - canonical-value invariant: built-in results agree with
     `BloodTestCatalog.toCanonical` within `1e-6`; custom results
     have `value == canonicalValue`

   The settings sub-pass
   [`toValidatedSettings`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupRestoreService.kt)
   constructs each unit choice through `AllowedAnalyteUnit.of`, so an
   unsupported unit fails before the database is touched. It also
   parses `homeE2ChartWindow` as a `HomeE2ChartWindowOption`, so an
   unknown chart-window name rejects the backup during validation.
6. **Cancel visible reminders.** Any posted dose-reminder notification
   references slot UUIDs from the pre-restore database;
   `cancelAllDoseReminderNotifications` runs before any mutation so a
   tap afterwards doesn't dispatch with stale state.
7. **Restore in one transaction.** Wrapped in
   `homeSnapshotRepository.runHomeDataMutation { databaseHolder
   .runTransaction { … } }`. The transaction deletes every backed-up
   table in dependency order, including `notes` and `tracked_dates`,
   then inserts the validated entities. Journal rows are restored via
   `JournalDao.insertTrackedDates` and `JournalDao.insertNotes` after
   the core medication and blood-test rows are back in place.
   Either the whole import lands or none of it does.
8. **Restore settings.** `settingsRepository.restoreSettings` writes
   the validated `BackupSettingsSnapshot` to DataStore. Outside the
   Room transaction — DataStore is a separate storage layer.
9. **Best-effort reminder re-scheduling.** `rescheduleAll` and
   `clearAllSnoozes` are called; any non-`CancellationException`
   throwable is logged via `AppDiagnosticsLogger.warning` rather than
   surfaced. The data has already committed, and the alarm scheduler
   reconciles on the next app launch.

## Forward-compatibility policy

**Adding fields without bumping the snapshot version.** New
`Backup*Snapshot` fields with a Kotlin default value at the
declaration are forward-compatible: Moshi reads missing fields as the
default. This is how `lastSeenTimeZoneId`, `hideReferenceRanges`,
`homeE2ChartWindow`, `archivedAtLocalIso`,
`includePastScheduledSlots`, `replacedByGroupUuid`,
`recreatedFromGroupUuid`, `BackupMedicineSnapshot.displayDoseUnit`, and
the optional `BackupMedicineSnapshot.stock` object (the entire stock
feature), and `BackupMedicationLogSnapshot.doseAmountDelta` (the
actual-amount feature) all shipped without a snapshot-version bump.
`BackupSnapshot.trackedDates` and `BackupSnapshot.notes` are the same
kind of additive change: both top-level lists default to `emptyList()`,
so v2-v5 backups that omit them restore with no journal rows, and
a same-version pre-field v5 reader may restore a v5 backup containing
them and ignore the unknown keys. This does not apply to future
snapshot versions, which are rejected by version checks. Because they
are additive/defaulted, the snapshot version stays
`CURRENT_BACKUP_SNAPSHOT_VERSION = 5`.
Removing or renaming a field is *not* in this bucket.
The external-import provenance fields themselves are nullable/defaulted
(`BackupMedicineSnapshot.importedFromExternalTracker`,
`BackupMedicationLogSnapshot.importSourceApp` / `importExternalId`,
`BackupBloodTestPanelSnapshot.importSourceApp` / `importPanelKey`, and
`BackupBloodTestResultSnapshot.importSourceApp` / `importExternalId`),
but the feature also introduced new preparation enum values, so the
snapshot version still bumped.

`widgetAppearance` is additive too, but with a caveat: it is an opaque codec
string carrying its own version, so bumping that version makes the field
undecodable by older apps — whose restore *hard-fails* on it rather than
defaulting — and must therefore ship with a snapshot-version bump. (Its legacy
scale / alpha / dark-mode mirrors only keep apps predating the field entirely
restoring.)

**Bumping the snapshot version (envelope unchanged).** Required when
a field's *meaning* changes — a rename, removal, unit change, or a
constraint that breaks older payloads. The medicine-identity refactor
is a textbook case: `medicationKey` / `customMedicationName` and the
old `doseKind` family on `BackupMedicationGroupItemSnapshot` /
`BackupMedicationLogSnapshot` were *removed* (their data is now
sourced from the new `BackupMedicineSnapshot` row via `medicineUuid`),
`dosageMgAsEstradiol` was renamed to `equivalentE2Mg` with PK-input
semantics, and a non-additive `BackupMedicineSnapshot` section was
introduced — so v1 → v2 was mandatory. The v2 → v3 bump added
`CAPSULE` to `preparationType`; older apps would coerce the unknown
enum to `PILL` and silently misclassify capsules. The v3 → v4 bump paired
with the `widgetAppearance` codec's own v3 → v4 (bidirectional light
balance), per the caveat above. The v4 → v5 bump added
`IMPORTED_INJECTION` and `IMPORTED_GEL` to `preparationType`; older
apps would coerce unknown preparation types to `PILL`, silently
corrupting imported medicine semantics. The validator gates
on `snapshotVersion in MIN_SUPPORTED_BACKUP_SNAPSHOT_VERSION..CURRENT_BACKUP_SNAPSHOT_VERSION`,
so bumping `MIN_SUPPORTED_BACKUP_SNAPSHOT_VERSION` is how we drop
support for a version when carrying its reader logic is no longer
worth it.

**Bumping the envelope version (crypto break).** Required when framing
or primitives change. KDF and cipher are recorded as identifier bytes
([`KDF_ARGON2_ID = 2`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt),
[`CIPHER_AES_256_GCM = 1`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt)),
so additions can extend the readable set without bumping. A bump is
reserved for changes that break parser invariants — different field
ordering, a different fixed-header length, a different AAD contract.
The medicine-identity refactor did *not* touch the envelope; only the
inner snapshot version moved.

**Cross-version restore matrix:**

| Envelope | Snapshot | Reader | Outcome |
| --- | --- | --- | --- |
| v2 | v2, v3, v4, or v5 | This version | Restores. Legacy framing, payload uncompressed. |
| v3 | v2 | This version | Restores normally. |
| v3 | v3, v4, or v5 | This version | Restores normally. |
| v3 | v3 with omitted optional fields | This version | Restores; missing fields take their data-class defaults, including omitted journal `trackedDates` / `notes` lists defaulting empty. |
| v3 | v4 or v5 with omitted external-import fields | This version | Restores; missing import fields default to non-imported rows. |
| v3 | v5 with additive same-version fields, such as `trackedDates` / `notes` | Same-version pre-field v5 reader | May restore after the version gate; Moshi ignores unknown keys, so the additive data is not imported. |
| Any | v1 | This version | Rejected by `toValidatedSnapshot`'s floor check — `MIN_SUPPORTED_BACKUP_SNAPSHOT_VERSION = 2`. No migration path: the medicine-identity refactor removed the denormalized fields v1 carried. |
| Future | any | This version | Rejected at `parseContainer` (`IllegalArgumentException("Unsupported backup file version: …")`). |
| v3 | Future snapshot version | This version | Rejected by `toValidatedSnapshot`'s `snapshotVersion` range check. |

Writers only emit current envelope + current snapshot version. The
asymmetry is intentional: older-into-newer restores are supported down
to the declared floor; future envelope versions and future
non-additive snapshot versions are rejected by version checks; and
additive fields kept on the same snapshot version may be accepted by
same-version pre-field readers and ignored by design.

## Relation to Room migrations

Room migrations in [`data-model.md`](data-model.md#migration-policy)
handle in-place schema upgrades: when a user updates the app on the
same device, every schema-version bump ships a `Migration` object
that rewrites the existing tables. The backup format handles the
cross-device / cross-app-version case — the file is read by whatever
app version is running on the target device, validated against that
app's `CURRENT_BACKUP_SNAPSHOT_VERSION`, and inserted through
current-schema DAOs. The two paths share no code; restore wipes and
refills, Room migration patches in place.
