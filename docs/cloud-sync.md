# Google Drive cloud sync

Featherline's optional cloud sync stores an encrypted database snapshot in the
signed-in user's private Google Drive `appDataFolder`. The feature is available
only in the `play` distribution flavor; the sideload `arm64` and `x64` flavors
do not include Google authorization code or request network access.

## User flow

1. Open **Settings > Google Drive sync** and enable the switch.
2. Choose a sync password. This password encrypts the cloud snapshot and is
   required to restore it on another device. Google and the Featherline
   developer cannot recover it.
3. Approve the Google Drive application-data permission when Android asks.
4. Select **Every day**, **Every 3 days**, **Every week**, or **Every month**.
   The monthly option is a 30-day interval.
5. Use **Sync now** at any time to run the same conflict-safe synchronization
   immediately.

The initial enable action performs a sync immediately. Later automatic work is
scheduled with WorkManager and requires a network connection and a non-low
battery. Android may defer periodic work to satisfy system power policies, so
the interval is a minimum cadence rather than an exact wall-clock appointment.

## Storage and encryption

Cloud sync reuses the versioned encrypted backup envelope documented in
[backup-format.md](backup-format.md): Argon2id derives an AES-256-GCM key from
the user's sync password, and only the encrypted snapshot is uploaded. The
password is wrapped locally with a separate Android Keystore AES-GCM key. It is
excluded from Android Auto Backup and is never uploaded.

Two files are maintained in Drive application data:

- `featherline-cloud-manifest.json`: revision, device ID, export timestamp,
  logical content hash, and the current encrypted snapshot file ID.
- `featherline-cloud-snapshot-<revision>.hrtbackup`: the encrypted database
  snapshot.

The application-data folder is hidden from the normal Drive UI and can only be
accessed by this application through the `drive.appdata` OAuth scope. It is not
a substitute for knowing the encryption password.

## Conflict rules

The coordinator compares the local logical-content hash, the current remote
hash, and the last successfully synchronized hash:

- only local changed: upload;
- only cloud changed: download and restore;
- neither changed: record the successful check without transferring data;
- both changed, or an existing cloud copy is first connected from a new local
  database: stop and ask the user to keep the local copy or use the cloud copy.

No automatic path silently overwrites a two-sided conflict. Choosing the cloud
copy is intentionally destructive to the current local database and is exposed
only from the explicit conflict dialog.

## Google Cloud setup

The Play build requires Google Drive API authorization to be configured for
the package being installed:

1. Create or select a Google Cloud project and enable the **Google Drive API**.
2. Configure the OAuth consent screen. Request only the non-sensitive
   `https://www.googleapis.com/auth/drive.appdata` scope.
3. Create Android OAuth client IDs for the production package
   `com.mkx.hrttracker` with the release signing-certificate SHA-1 and, for
   development, `com.mkx.hrttracker.debug` with the debug SHA-1.
4. Add every signing certificate used by CI or maintainers. A package/SHA-1
   mismatch causes Google authorization to fail even though the APK compiles.

No client secret belongs in the Android app or repository. The Google
AuthorizationClient resolves the Android package and signing certificate at
runtime.

## Disconnect and deletion

Disabling sync revokes the app's Google authorization, removes the locally
wrapped sync password, cancels scheduled work, and clears local sync metadata.
It intentionally does not delete the encrypted remote snapshot. Users can
remove all hidden application data by deleting Featherline's app data from
their Google Drive account settings, or reconnect and overwrite it with a new
encrypted revision.
