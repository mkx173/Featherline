# Privacy

The contributor-facing technical companion to the Play Store privacy policy. Explains the implementation behind each user-facing claim.

## Canonical policy is authoritative

The legally-binding privacy policy for Featherline lives at <https://asterismlabs.io/featherline/privacy/>. That document controls in any conflict. This page does not restate the legal commitments — it documents how those commitments are enforced in code, manifest, and resource files, so an auditor or contributor can verify each claim against source.

## What's stored on device

User data lives in three places under the app sandbox:

- The encrypted Room database `hrt_tracker.db`, with the SQLite WAL/SHM siblings `hrt_tracker.db-shm` and `hrt_tracker.db-wal`. Contents are described in [data-model.md](data-model.md).
- The `SharedPreferences` file [`hrt_tracker_secure_storage.xml`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/DatabasePassphraseProvider.kt#L132). Holds the AES-GCM-wrapped SQLCipher passphrase and, only when Play cloud sync is enabled, the separately AES-GCM-wrapped user sync password. Neither wrapping key is stored in the file.
- Nine DataStore files under `datastore/`: `settings.preferences_pb`, `widget_appearance.preferences_pb`, `home_snapshot.pb`, `widget_snapshot.pb`, `anchor_snapshot.pb`, `home_snapshot_metadata.preferences_pb`, `reminder_schedule.preferences_pb`, `medication_reminder_snoozes.preferences_pb`, and `cloud_sync.preferences_pb`. Settings, widget appearance, home-screen cache, widget cache, anchor-widget cache, reminder bookkeeping, and non-secret cloud-sync state.

**Debug builds only** also write a rolling diagnostics log at `files/diagnostics/app-diagnostics.log`. The [`AppDiagnosticsLogger`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/util/AppDiagnosticsLogger.kt) defaults to `enabled = BuildConfig.DEBUG`, and the export service that surfaces this file refuses to run unless `BuildConfig.DEBUG` is true. Release builds neither write nor expose this file. The log records timestamps, tags, and short event strings (reminder fires, snapshot rebuilds, widget refresh reasons) and is not user-visible in release builds.

Apart from the debug-only diagnostics log, nothing else under the sandbox holds user content.

## Encryption at rest

The Room database is encrypted with SQLCipher using a 256-bit passphrase. The passphrase itself is wrapped with a hardware-bound key:

- [`DatabasePassphraseProvider`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/DatabasePassphraseProvider.kt) generates the SQLCipher passphrase from [`SecureRandom`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/DatabasePassphraseProvider.kt#L49) on first launch.
- The passphrase is encrypted with an [`AES/GCM/NoPadding`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/DatabasePassphraseProvider.kt#L137) wrapping key whose alias is [`hrt_tracker_database_master_key`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/DatabasePassphraseProvider.kt#L135). The key is generated in and stored by the [`AndroidKeyStore`](https://github.com/mkx173/Featherline/blob/main/app/src/main/java/com/mkx/hrttracker/data/local/DatabasePassphraseProvider.kt#L136) provider; on devices with a hardware-backed Keystore, the key never leaves the secure element.
- The wrapped passphrase blob and its per-encryption IV are persisted to `hrt_tracker_secure_storage.xml`. The wrapping key itself is never persisted to app storage — it lives inside Keystore.

Consequences: a copy of `hrt_tracker.db` lifted off the device cannot be decrypted without the Keystore-bound wrapping key, which is bound to that specific device.

## Network behavior

The `play` flavor offers opt-in Google Drive cloud sync. Only after the user enables it and grants the `drive.appdata` scope does Featherline call Google Authorization Services and the Google Drive REST API. It uploads an Argon2id/AES-256-GCM encrypted backup snapshot plus a small manifest containing revision metadata and a logical-content hash. The app developer operates no relay server and receives neither the snapshot nor the encryption password.

The `android.permission.INTERNET` permission is declared only in `src/play/AndroidManifest.xml`. The `arm64` and `x64` sideload flavors compile an unavailable cloud gateway, include no Google authorization dependency, do not merge the Internet permission, and make no cloud-sync network calls. See [cloud-sync.md](cloud-sync.md) for the complete protocol and disconnect behavior.

## Android Auto Backup exclusions

The manifest declares [`android:allowBackup="true"`](https://github.com/mkx173/Featherline/blob/main/app/src/main/AndroidManifest.xml#L10), which would normally enroll the app in Google's Auto Backup service and Android-to-Android device transfer. The granular exclusion rules below remove every file that holds user data from both paths.

### DataStore exclusions

Three sensitive DataStore files are excluded to prevent device-to-device de-sync and re-identification:

- `datastore/home_snapshot.pb`: Cached home screen state. Contains encrypted medical data derived from the medication schedule (dose completion, estimated E2 level) and the first pinned Journal date. Encrypted at rest with AES-256-GCM. Excluded to prevent inconsistency when transferring to a device where the cached state no longer matches the authoritative database.
- `datastore/widget_snapshot.pb`: Cached dose status for widget display. Contains encrypted medical data derived from the medication schedule (dose completion status, E2 estimate). Encrypted at rest with AES-256-GCM. Excluded to prevent widget state divergence and re-identification risk if transferred alongside backup data.
- `datastore/anchor_snapshot.pb`: Cached anchor-widget state. Contains encrypted Journal data (tracked-date names, dates, icons, and palettes) that seeds the anchor widget and its milestones deep link before the database opens. Encrypted at rest with AES-256-GCM. Excluded to prevent inconsistency and re-identification risk when transferred alongside backup data.

The following rules enforce these exclusions across all Android versions. They also exclude `datastore/cloud_sync.preferences_pb`; the sync password is stored in the already-excluded `hrt_tracker_secure_storage.xml` under a separate Android Keystore wrapping key:

- [`res/xml/data_extraction_rules.xml`](https://github.com/mkx173/Featherline/blob/main/app/src/main/res/xml/data_extraction_rules.xml) (API 31+) excludes `hrt_tracker.db` + WAL/SHM siblings, `hrt_tracker_secure_storage.xml`, the sensitive/cache DataStore files named in the rule (all except `widget_appearance.preferences_pb`), and the `files/diagnostics/` directory from both `<cloud-backup>` and `<device-transfer>`.
- [`res/xml/backup_rules.xml`](https://github.com/mkx173/Featherline/blob/main/app/src/main/res/xml/backup_rules.xml) (pre-API-31 fallback) applies the same exclusions to `<full-backup-content>`.

Net effect: Google Auto Backup copies the app binary, resources, and widget appearance preferences, but no database, cache, reminder, or diagnostics data. Android-to-Android device transfer follows the same exclusions. The `allowBackup="true"` flag is kept rather than set to `false` because Android lint discourages a blanket `false` when granular rules are available, and the granular rules document the policy more clearly.

Even if Auto Backup did copy `hrt_tracker.db` (it doesn't), the file would be useless on a new device because the Keystore-wrapped passphrase is bound to the original device's secure element.

## User-initiated backups

The in-app Backup and Restore feature is the user-controlled path off the device. It produces an encrypted file (the v3 envelope described in [backup-format.md](backup-format.md)) and writes it to a SAF location of the user's choice — local storage, manual upload to a personal cloud, or transfer to another device.

The developer operates no backup server, never receives these files, and has no way to decrypt them; the v3 envelope is keyed by an Argon2-derived KDF from a passphrase the user supplies at export time.

## Optional Google Drive sync

Google Drive sync is a user-initiated remote backup path in the Play flavor. It reuses the same encrypted v3 envelope as manual backup and requests only Drive's private application-data scope. The sync password is locally wrapped with Android Keystore, excluded from Auto Backup, and never sent to Google. Automatic work can run every 1, 3, 7, or 30 days when network and battery constraints permit; manual sync uses the same conflict checks.

Disabling sync revokes authorization, deletes the locally wrapped sync password, cancels scheduled work, and clears local sync metadata. It does not silently delete the remote encrypted files. Users can delete Featherline's hidden app data from their Google Drive account settings. See [cloud-sync.md](cloud-sync.md).

## Permissions

Three permissions are declared for every flavor in [`AndroidManifest.xml`](https://github.com/mkx173/Featherline/blob/main/app/src/main/AndroidManifest.xml):

- `android.permission.POST_NOTIFICATIONS` — dose reminder notifications. On Android 13+ the system shows a runtime prompt the first time the app posts a notification; on earlier API levels the permission is granted at install.
- `android.permission.RECEIVE_BOOT_COMPLETED` — lets [`MedicationReminderRescheduleReceiver`](https://github.com/mkx173/Featherline/blob/main/app/src/main/AndroidManifest.xml#L47-L58) re-arm pending alarms after a reboot. Without this, reminders silently stop after the device restarts.
- `android.permission.SCHEDULE_EXACT_ALARM` — exact-time dose reminders. The reminder pipeline calls `setExactAndAllowWhileIdle` to fire alarms during Doze; the runtime requires this permission for that API. See [reminders.md](reminders.md) for the full pipeline.

The `play` flavor additionally declares `android.permission.INTERNET` for opt-in Google Drive sync. Android grants this normal permission at install; cloud access still requires an explicit Google authorization flow inside the app.

Biometric authentication for the optional app lock uses the AndroidX biometric library, which talks to `BiometricManager` and `BiometricPrompt` directly. No `<uses-permission>` entry is needed on modern Android. The canonical user-facing policy refers to this as "biometric permission" loosely; the technical reality is that no manifest permission is declared.

## Uninstall

Android removes the entire app sandbox on uninstall, including the encrypted database, the secure-storage SharedPreferences, and every DataStore file listed above. The Keystore-wrapped keys for the database passphrase and optional sync password are also discarded when the app's UID is removed. An encrypted Google Drive app-data copy, if the user enabled sync, remains in that Google account until the user deletes Featherline's application data there.

## See also

- [safety.md](safety.md) — sister page covering the medical-disclaimer side of user trust.
- [cloud-sync.md](cloud-sync.md) — encrypted sync protocol, scheduling, conflicts, and deletion behavior.
- [data-model.md](data-model.md) — Room entities held inside `hrt_tracker.db`.
- [backup-format.md](backup-format.md) — the user-controlled encrypted backup format.
- [reminders.md](reminders.md) — the reminder pipeline that consumes the three declared permissions.
