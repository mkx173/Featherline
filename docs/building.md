# Building

How to build Featherline from source. The phone app lives in `:app`, the paired
watch APK in `:wear`, and their platform-free payload contract in
`:wear-protocol`.

## Prerequisites

- JDK 17. The build enforces it via [`jvmToolchain(17)`](https://github.com/mkx173/Featherline/blob/main/app/build.gradle.kts#L177-L179); newer JDKs work as long as Gradle's `foojay-resolver-convention` plugin (set up in [`settings.gradle.kts`](https://github.com/mkx173/Featherline/blob/main/settings.gradle.kts)) can auto-provision JDK 17.
- A recent Android Studio (Ladybug Feature Drop or newer). The Gradle wrapper pins everything else — the IDE just needs to recognize AGP 9.2.1.
- Android SDK with `compileSdk = 37` and `targetSdk = 37` available (`minSdk = 26`). Read from [`app/build.gradle.kts`](https://github.com/mkx173/Featherline/blob/main/app/build.gradle.kts#L34-L61).
- Exact library and plugin versions live in [`gradle/libs.versions.toml`](https://github.com/mkx173/Featherline/blob/main/gradle/libs.versions.toml). Use this as the source of truth — never hand-edit version strings in `build.gradle.kts`.

## Quick start

```bash
./gradlew assemblePlayDebug
```

This produces a signed APK at `app/build/outputs/apk/play/debug/`. The debug build type installs as `com.mkx.hrttracker.debug`, is labeled `Featherline Debug`, and uses AGP's debug signing config, so it can live alongside the release app without touching release app data or requiring release signing keys.

## Flavors and build types

Flavor dimension `distribution` ([`app/build.gradle.kts:37`](https://github.com/mkx173/Featherline/blob/main/app/build.gradle.kts#L37)):

- `play` — App Bundle target. No ABI filter; all ABIs bundled. Used for Play Store submission.
- `arm64` — APK target. ABI filter `arm64-v8a` only. Used for the GitHub Releases sideload APK.
- `x64` — APK target. ABI filter `x86_64` only. Used for local emulator or x86_64-device sideload builds.

Build types ([`app/build.gradle.kts:95-110`](https://github.com/mkx173/Featherline/blob/main/app/build.gradle.kts#L95-L110)):

- `debug` — `applicationIdSuffix = ".debug"`, `versionNameSuffix = "-<short-sha>"`; uses AGP's debug signing config and the debug-only app name `Featherline Debug`.
- `release` — `isMinifyEnabled = true`, `isShrinkResources = true`, ProGuard rules applied.
- `benchmark` — release-equivalent (`initWith(release)`) but debug-signed, profileable, and `applicationIdSuffix = ".benchmark"`; the target the `:benchmark` macrobenchmark module (cold-start timing) runs against, installable alongside a production build without release keys.

Useful Gradle tasks:

- `./gradlew assemblePlayDebug` — debug APK for daily development.
- `./gradlew assembleArm64Release` — release APK for sideload (this is what CI builds).
- `./gradlew assembleX64Debug` — x86_64 debug APK for local emulator/device testing.
- `./gradlew assembleX64Release` — x86_64 release APK for sideload testing on x86_64 targets.
- `./gradlew bundlePlayRelease` — Play Store App Bundle (maintainer builds locally for submission).
- `./gradlew installPlayDebug` — install debug APK on a connected device.

Manual backup export/restore uses a stable backup app identity (`com.mkx.hrttracker`), not the installed package ID. Backups therefore remain portable between release and debug builds even though their app sandboxes are separate.

The output filename is set in the [`androidComponents`](https://github.com/mkx173/Featherline/blob/main/app/build.gradle.kts#L156-L175) block: `featherline-<abi>-<versionName>-<versionCode>.apk` (the root project is named `Featherline` in [`settings.gradle.kts:25`](https://github.com/mkx173/Featherline/blob/main/settings.gradle.kts#L25)). `<abi>` is `arm64-v8a` for the `arm64` flavor, `x86_64` for the `x64` flavor, and `all-abis` for `play`.

## Wear OS pairing

Build the paired watch APK and its focused tests with:

```bash
./gradlew :wear-protocol:test :wear:testDebugUnitTest :wear:assembleDebug
```

The APK is written to `wear/build/outputs/apk/debug/wear-debug.apk`. The watch
and phone APKs intentionally use the same application ID and signing identity,
as required by the Wearable Data Layer. Debug builds add the same `.debug`
suffix and use the same standard debug key when built together.

Only the phone's `play` flavor contains the Google Play Services Wearable
bridge. The `arm64` and `x64` flavors keep their existing dependency boundary,
so the F-Droid and GitHub sideload phone APKs do not gain a proprietary Play
Services dependency. Install `:app:installPlayDebug` on the phone and
`:wear:installDebug` on the paired watch for end-to-end development.

## Signing setup

The `release` build type assigns the `release` signing config explicitly. Contributors only need release signing material when building release variants; debug variants use AGP's debug signing config.

Maintainers populate signing one of two ways:

1. Create `keystore.properties` at the repo root (gitignored) with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
2. Set the environment variables `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` (CI uses this path — see [release-process.md](release-process.md)).

The [`signingValue(...)` helper](https://github.com/mkx173/Featherline/blob/main/app/build.gradle.kts#L29-L32) checks `keystore.properties` first, falls back to env. If neither source supplies a `storeFile` path, the `release` signing config is *constructed* empty, but release variants still fail at packaging with the missing-`storeFile` error. There is no truly unsigned release build path today.

## See also

- [release-process.md](release-process.md) — versionCode derivation, the CI workflow, Play Store submission flow.
- [testing.md](testing.md) — how to run the test suites.
