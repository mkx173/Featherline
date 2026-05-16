# Backup format

How Featherline exports user data to a single encrypted file and how
that file is read back. The whole subsystem lives in
[`data/backup/`](https://github.com/mkx173/HRTTracker/tree/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup)
(five files, ~2 100 LOC).

## Two version numbers

- **Envelope format version** —
  [`CURRENT_BACKUP_CONTAINER_VERSION = 3`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt#L399).
  Describes the on-disk byte layout. Bumps are rare and crypto-
  breaking — they cover changes to the framing or to the
  cryptographic primitives.
- **Snapshot JSON version** —
  [`CURRENT_BACKUP_SNAPSHOT_VERSION = 1`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupSnapshot.kt#L158).
  Describes the plaintext payload — the `BackupSnapshot` data-class
  tree serialized as JSON. Bumps are reserved for renames, removals,
  or semantic changes to existing fields.

The envelope reader still accepts a legacy v2 envelope: same framing
without the compression byte or uncompressed-length field, payload
stored as-is. Writers only emit version `3`.

## v3 envelope structure

One contiguous byte sequence: a 65-byte header followed by AES-GCM
ciphertext with its 16-byte tag appended. Built by
[`buildArgon2Header`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt#L202)
and parsed by
[`parseContainer`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt#L226):

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
[`FIXED_HEADER_LENGTH_V3 = 37`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt#L415)
gates the minimum-bytes check; the legacy v2 header was 28 bytes.

## Encryption

### Key derivation

[`BackupArgon2KeyDeriver`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt#L427)
wraps the [argon2kt][argon2kt] library and calls `Argon2Mode.ARGON2_ID`
with the parameters read from the header and a 16-byte random salt.
Defaults from
[`DEFAULT_ARGON2_PARAMETERS`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt#L400):
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
[`encryptWithAesGcm`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt#L174):
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
[`MAX_BACKUP_JSON_BYTES = 128 MiB`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt#L416)
cap (decompression-bomb defence).

## Snapshot tree

Twelve `@JsonClass` data classes in
[`BackupSnapshot.kt`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupSnapshot.kt).
The tree is the wire-format mirror of the Room schema documented in
[`data-model.md`](data-model.md#entities); each row maps one
snapshot class to its entity. Nested-in-line snapshots are children
flattened into the parent's JSON.

- `BackupSnapshot` — top-level: `snapshotVersion`,
  `exportedAtEpochMillis`, plus the sub-snapshots below.
- `BackupAppSnapshot` — just `packageName`; restore rejects
  cross-app files.
- `BackupSettingsSnapshot` — flat DataStore values (dark mode,
  adaptive color, reminders, app-lock grace period, hide-screen-
  content, onboarding, language, home E2 display unit, per-analyte
  calibration default units, last-seen time-zone).
  `screenLockProtectionEnabled` is intentionally excluded — app-lock
  stays local to the device that set it.
- `BackupUserProfileSnapshot` →
  [`UserProfileEntity`](data-model.md#userprofileentity); carries
  `weightKg` plus the original value + unit for display round-trip.
- `BackupMedicationGroupSnapshot` →
  [`MedicationGroupEntity`](data-model.md#medicationgroupentity),
  with three child snapshots nested in-line:
  `BackupMedicationGroupScheduleSnapshot` (schedule shape + times
  list); `BackupMedicationGroupScheduleTimeSnapshot` →
  [`MedicationGroupScheduleTimeEntity`](data-model.md#medicationgroupscheduletimeentity);
  `BackupMedicationGroupItemSnapshot` →
  [`MedicationGroupItemEntity`](data-model.md#medicationgroupitementity).
  Weekly days are not a snapshot class — they reconstruct from
  `schedule.weeklyDaysOfWeek`.
- `BackupMedicationLogSnapshot` →
  [`MedicationLogEntryEntity`](data-model.md#medicationlogentryentity);
  carries `dosageMgAsEstradiol` and the slot-fulfillment link
  (`sourceGroupUuid` / `scheduleTimeUuid` / `scheduledForIso`).
- `BackupCustomBloodAnalyteSnapshot` →
  [`CustomBloodAnalyteEntity`](data-model.md#custombloodanalyteentity).
- `BackupBloodTestPanelSnapshot` →
  [`BloodTestPanelEntity`](data-model.md#bloodtestpanelentity);
  results nested in-line.
- `BackupBloodTestResultSnapshot` →
  [`BloodTestResultEntity`](data-model.md#bloodtestresultentity);
  carries either `builtinAnalyteKey` or `customAnalyteUuid`
  (exclusive), `value` + `unitSnapshot`, and `canonicalValue`.

Serialization is by Moshi via
[`BackupSnapshotJsonCodec`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupSnapshotJsonCodec.kt)
with `.serializeNulls()` enabled — `null` fields are written
explicitly and distinguishable from missing-on-read.

## Export flow

[`BackupExportService.kt`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupExportService.kt)
runs on `Dispatchers.IO`:

1. Read every backed-up table via its repository.
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

[`BackupRestoreService.kt`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupRestoreService.kt)
runs on `Dispatchers.IO`. Validation is layered into four passes so
incompatible files are rejected at the cheapest detection point.

1. **Buffer the file.** SAF picker grants are single-use, so the
   bytes are read into memory up front; later passes re-read the
   buffer.
2. **Pass 1 — envelope shape.**
   [`validateEncryptedBackupContainer`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt#L58)
   parses magic, version, KDF / cipher / compression identifiers, and
   Argon2 parameters. Failures wrap as
   `IncompatibleBackupFileException` so the UI can distinguish "not a
   backup" from "wrong password". Runs before the password dialog
   opens.
3. **Pass 2 — decrypt + auth-tag verify.**
   [`decrypt`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt#L109)
   derives the key from the entered password and the envelope's salt,
   runs AES-GCM with the header as AAD.
4. **Pass 3 — decompress + JSON decode.** Gunzip refuses payloads
   whose expansion exceeds the declared `uncompressedLengthBytes` or
   the 128 MiB cap; `BackupSnapshotJsonCodec.decode` throws on
   missing required fields.
5. **Pass 4 — semantic validation.**
   [`toValidatedSnapshot`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupRestoreService.kt#L243)
   runs these checks:
   - version + identity: `snapshotVersion`, `app.packageName`
   - parseability: enum-name resolution, UUID parsing, `DayOfWeek`
     and `ZoneId.of` round-trips
   - value sanity: positive-finite doses
   - FK consistency: every log's `sourceGroupUuid` references a
     restored group; every custom-analyte-keyed result references a
     custom analyte in the same snapshot; every `scheduleTimeUuid`
     belongs to its log's source group
   - canonical-value invariant: built-in results agree with
     `BloodTestCatalog.toCanonical` within `1e-6`; custom results
     have `value == canonicalValue`

   The settings sub-pass
   [`toValidatedSettings`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupRestoreService.kt#L620)
   constructs each unit choice through `AllowedAnalyteUnit.of`, so an
   unsupported unit fails before the database is touched.
6. **Cancel visible reminders.** Any posted dose-reminder notification
   references slot UUIDs from the pre-restore database;
   `cancelAllDoseReminderNotifications` runs before any mutation so a
   tap afterwards doesn't dispatch with stale state.
7. **Restore in one transaction.** Wrapped in
   `homeSnapshotRepository.runHomeDataMutation { databaseHolder
   .runTransaction { … } }`. The transaction deletes every backed-up
   table in dependency order, then inserts the validated entities.
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

**Adding fields at snapshot v1 without bumping anything.** New
`Backup*Snapshot` fields with a Kotlin default value at the
declaration are forward-compatible: Moshi reads missing fields as the
default. This is how `lastSeenTimeZoneId`, `archivedAtLocalIso`,
`includePastScheduledSlots`, `replacedByGroupUuid`, and
`recreatedFromGroupUuid` shipped without a snapshot-version bump.
Removing or renaming a field is *not* in this bucket.

**Bumping the snapshot version (envelope unchanged).** Required when
a field's *meaning* changes — a rename, removal, unit change, or a
constraint that breaks older payloads. The validator gates on
`snapshotVersion == CURRENT_BACKUP_SNAPSHOT_VERSION` exactly, so a
bump is the moment to add cross-version reader logic.

**Bumping the envelope version (crypto break).** Required when framing
or primitives change. KDF and cipher are recorded as identifier bytes
([`KDF_ARGON2_ID = 2`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt#L409),
[`CIPHER_AES_256_GCM = 1`](https://github.com/mkx173/HRTTracker/blob/914a73bdf897fb80c033a83c1c5e076410094a3b/app/src/main/java/com/mkx/hrttracker/data/backup/BackupCrypto.kt#L410)),
so additions can extend the readable set without bumping. A bump is
reserved for changes that break parser invariants — different field
ordering, a different fixed-header length, a different AAD contract.

**Cross-version restore matrix:**

| Envelope | Snapshot | Reader | Outcome |
| --- | --- | --- | --- |
| v2 | v1 | This version | Restores. Legacy framing, payload uncompressed. |
| v3 | v1 | This version | Restores normally. |
| v3 | v1 with omitted optional fields | This version | Restores; missing fields take their data-class defaults. |
| Future | any | This version | Rejected at `parseContainer` (`IllegalArgumentException("Unsupported backup file version: …")`). |
| v3 | Future snapshot version | This version | Rejected by `toValidatedSnapshot`'s `snapshotVersion` check. |

Writers only emit current envelope + current snapshot version. The
asymmetry — older-into-newer supported, newer-into-older rejected —
is enforced by version-number checks, not heuristics.

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
