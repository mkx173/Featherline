# Privacy

The contributor-facing technical companion to the Play Store privacy policy. Explains the implementation behind each user-facing claim.

## Canonical policy is authoritative

The legally-binding privacy policy for Featherline lives at <https://asterismlabs.io/featherline/privacy/>. That document controls in any conflict. This page does not restate the legal commitments — it documents how those commitments are enforced in code, manifest, and resource files, so an auditor or contributor can verify each claim against source.

## What's stored on device

User data lives in three places under the app sandbox:

- The encrypted Room database `hrt_tracker.db`, with the SQLite WAL/SHM siblings `hrt_tracker.db-shm` and `hrt_tracker.db-wal`. Contents are described in [data-model.md](data-model.md).
- The `SharedPreferences` file [`hrt_tracker_secure_storage.xml`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/DatabasePassphraseProvider.kt#L132). Holds the AES-GCM-wrapped SQLCipher passphrase. No user-visible data, only the wrapped key envelope.
- Eight DataStore files under `datastore/`: `settings.preferences_pb`, `widget_appearance.preferences_pb`, `home_snapshot.pb`, `widget_snapshot.pb`, `anchor_snapshot.pb`, `home_snapshot_metadata.preferences_pb`, `reminder_schedule.preferences_pb`, `medication_reminder_snoozes.preferences_pb`. Settings, widget appearance, home-screen cache, widget cache, anchor-widget cache, and reminder bookkeeping.

**Debug builds only** also write a rolling diagnostics log at `files/diagnostics/app-diagnostics.log`. The [`AppDiagnosticsLogger`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/util/AppDiagnosticsLogger.kt) defaults to `enabled = BuildConfig.DEBUG`, and the export service that surfaces this file refuses to run unless `BuildConfig.DEBUG` is true. Release builds neither write nor expose this file. The log records timestamps, tags, and short event strings (reminder fires, snapshot rebuilds, widget refresh reasons) and is not user-visible in release builds.

Apart from the debug-only diagnostics log, nothing else under the sandbox holds user content.

## Encryption at rest

The Room database is encrypted with SQLCipher using a 256-bit passphrase. The passphrase itself is wrapped with a hardware-bound key:

- [`DatabasePassphraseProvider`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/DatabasePassphraseProvider.kt) generates the SQLCipher passphrase from [`SecureRandom`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/DatabasePassphraseProvider.kt#L49) on first launch.
- The passphrase is encrypted with an [`AES/GCM/NoPadding`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/DatabasePassphraseProvider.kt#L137) wrapping key whose alias is [`hrt_tracker_database_master_key`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/DatabasePassphraseProvider.kt#L135). The key is generated in and stored by the [`AndroidKeyStore`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/DatabasePassphraseProvider.kt#L136) provider; on devices with a hardware-backed Keystore, the key never leaves the secure element.
- The wrapped passphrase blob and its per-encryption IV are persisted to `hrt_tracker_secure_storage.xml`. The wrapping key itself is never persisted to app storage — it lives inside Keystore.

Consequences: a copy of `hrt_tracker.db` lifted off the device cannot be decrypted without the Keystore-bound wrapping key, which is bound to that specific device.

## Network behavior

No network calls. The `android.permission.INTERNET` permission is **not declared** in [`AndroidManifest.xml`](https://github.com/mkx173/Featherline/blob/main/app/src/main/AndroidManifest.xml), so the Android runtime would block any networking syscall before the app could attempt it. The layer map in [architecture.md](architecture.md) reflects this — no networking sub-package exists under `data/`, and no library in [`gradle/libs.versions.toml`](https://github.com/mkx173/Featherline/blob/main/gradle/libs.versions.toml) is a networking client (no Retrofit, OkHttp-as-client, Ktor, etc.).

## Android Auto Backup exclusions

The manifest declares [`android:allowBackup="true"`](https://github.com/mkx173/Featherline/blob/main/app/src/main/AndroidManifest.xml#L10), which would normally enroll the app in Google's Auto Backup service and Android-to-Android device transfer. The granular exclusion rules below remove every file that holds user data from both paths.

### DataStore exclusions

Three sensitive DataStore files are excluded to prevent device-to-device de-sync and re-identification:

- `datastore/home_snapshot.pb`: Cached home screen state. Contains encrypted medical data derived from the medication schedule (dose completion, estimated E2 level) and the first pinned Journal date. Encrypted at rest with AES-256-GCM. Excluded to prevent inconsistency when transferring to a device where the cached state no longer matches the authoritative database.
- `datastore/widget_snapshot.pb`: Cached dose status for widget display. Contains encrypted medical data derived from the medication schedule (dose completion status, E2 estimate). Encrypted at rest with AES-256-GCM. Excluded to prevent widget state divergence and re-identification risk if transferred alongside backup data.
- `datastore/anchor_snapshot.pb`: Cached anchor-widget state. Contains encrypted Journal data (tracked-date names, dates, icons, and palettes) that seeds the anchor widget and its milestones deep link before the database opens. Encrypted at rest with AES-256-GCM. Excluded to prevent inconsistency and re-identification risk when transferred alongside backup data.

The following rules enforce these exclusions across all Android versions:

- [`res/xml/data_extraction_rules.xml`](https://github.com/mkx173/Featherline/blob/main/app/src/main/res/xml/data_extraction_rules.xml) (API 31+) excludes `hrt_tracker.db` + WAL/SHM siblings, `hrt_tracker_secure_storage.xml`, the sensitive/cache DataStore files named in the rule (all except `widget_appearance.preferences_pb`), and the `files/diagnostics/` directory from both `<cloud-backup>` and `<device-transfer>`.
- [`res/xml/backup_rules.xml`](https://github.com/mkx173/Featherline/blob/main/app/src/main/res/xml/backup_rules.xml) (pre-API-31 fallback) applies the same exclusions to `<full-backup-content>`.

Net effect: Google Auto Backup copies the app binary, resources, and widget appearance preferences, but no database, cache, reminder, or diagnostics data. Android-to-Android device transfer follows the same exclusions. The `allowBackup="true"` flag is kept rather than set to `false` because Android lint discourages a blanket `false` when granular rules are available, and the granular rules document the policy more clearly.

Even if Auto Backup did copy `hrt_tracker.db` (it doesn't), the file would be useless on a new device because the Keystore-wrapped passphrase is bound to the original device's secure element.

## User-initiated backups

The in-app Backup and Restore feature is the user-controlled path off the device. It produces an encrypted file (the v3 envelope described in [backup-format.md](backup-format.md)) and writes it to a SAF location of the user's choice — local storage, manual upload to a personal cloud, or transfer to another device.

The developer operates no backup server, never receives these files, and has no way to decrypt them; the v3 envelope is keyed by an Argon2-derived KDF from a passphrase the user supplies at export time.

## Permissions

Three permissions are declared in [`AndroidManifest.xml`](https://github.com/mkx173/Featherline/blob/main/app/src/main/AndroidManifest.xml):

- `android.permission.POST_NOTIFICATIONS` — dose reminder notifications. On Android 13+ the system shows a runtime prompt the first time the app posts a notification; on earlier API levels the permission is granted at install.
- `android.permission.RECEIVE_BOOT_COMPLETED` — lets [`MedicationReminderRescheduleReceiver`](https://github.com/mkx173/Featherline/blob/main/app/src/main/AndroidManifest.xml#L47-L58) re-arm pending alarms after a reboot. Without this, reminders silently stop after the device restarts.
- `android.permission.SCHEDULE_EXACT_ALARM` — exact-time dose reminders. The reminder pipeline calls `setExactAndAllowWhileIdle` to fire alarms during Doze; the runtime requires this permission for that API. See [reminders.md](reminders.md) for the full pipeline.

Biometric authentication for the optional app lock uses the AndroidX biometric library, which talks to `BiometricManager` and `BiometricPrompt` directly. No `<uses-permission>` entry is needed on modern Android. The canonical user-facing policy refers to this as "biometric permission" loosely; the technical reality is that no manifest permission is declared.

## Uninstall

Android removes the entire app sandbox on uninstall, including the encrypted database, the secure-storage SharedPreferences, and every DataStore file listed above. The Keystore-wrapped key for the database passphrase is also discarded when the app's UID is removed. Because no remote copy of any of this data exists, no deletion request is needed.

## See also

- [safety.md](safety.md) — sister page covering the medical-disclaimer side of user trust.
- [architecture.md](architecture.md) — layer map confirming the absence of a networking sub-package.
- [data-model.md](data-model.md) — Room entities held inside `hrt_tracker.db`.
- [backup-format.md](backup-format.md) — the user-controlled encrypted backup format.
- [reminders.md](reminders.md) — the reminder pipeline that consumes the three declared permissions.
