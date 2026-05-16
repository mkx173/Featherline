# Building

How to build Featherline from source. Two Gradle modules (`:app`, `:macrobenchmark`) under one root project `Featherline`.

## Prerequisites

- JDK 17. The build enforces it via [`jvmToolchain(17)`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/build.gradle.kts#L149-L151); newer JDKs work as long as Gradle's `foojay-resolver-convention` plugin (set up in [`settings.gradle.kts`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/settings.gradle.kts)) can auto-provision JDK 17.
- A recent Android Studio (Ladybug Feature Drop or newer). The Gradle wrapper pins everything else — the IDE just needs to recognize AGP 9.2.1.
- Android SDK with `compileSdk = 37` and `targetSdk = 37` available (`minSdk = 31`). Read from [`app/build.gradle.kts`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/build.gradle.kts#L30-L55).
- Exact library and plugin versions live in [`gradle/libs.versions.toml`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/gradle/libs.versions.toml). Use this as the source of truth — never hand-edit version strings in `build.gradle.kts`.

## Quick start

```bash
./gradlew assemblePlayDebug
```

This produces a debug-signed APK at `app/build/outputs/apk/play/debug/`. The debug build does not require `keystore.properties` — the `release` signing config (used for all built APKs, including debug, via [`buildTypes.debug.signingConfig`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/build.gradle.kts#L77-L101)) skips signing if no keystore is configured.

Or open the project in Android Studio and run from the IDE.

## Flavors and build types

Flavor dimension `distribution` ([`app/build.gradle.kts:33`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/build.gradle.kts#L33)):

- `play` — App Bundle target. No ABI filter; all ABIs bundled. Used for Play Store submission.
- `arm64` — APK target. ABI filter `arm64-v8a` only. Used for the GitHub Releases sideload APK.

Build types ([`app/build.gradle.kts:77-101`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/build.gradle.kts#L77-L101)):

- `debug` — `versionNameSuffix = "-<short-sha>"`; uses the release signing config (signing optional).
- `release` — `isMinifyEnabled = true`, `isShrinkResources = true`, ProGuard rules applied.
- `benchmark` — initialized from `release`, `applicationIdSuffix = ".benchmark"`, debug-signed, `isDebuggable = false`. Used by the `:macrobenchmark` module.

Useful Gradle tasks:

- `./gradlew assemblePlayDebug` — debug APK for daily development.
- `./gradlew assembleArm64Release` — release APK for sideload (this is what CI builds).
- `./gradlew bundlePlayRelease` — Play Store App Bundle (maintainer builds locally for submission).
- `./gradlew installPlayDebug` — install debug APK on a connected device.

The output filename is set in the [`androidComponents`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/build.gradle.kts#L129-L147) block: `featherline-<abi>-<versionName>-<versionCode>.apk` (the root project is named `Featherline` in [`settings.gradle.kts:25`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/settings.gradle.kts#L25)). `<abi>` is `arm64-v8a` for the `arm64` flavor, `all-abis` for `play`.

## Signing setup

Contributors do not need release signing. Debug and unsigned release builds work without configuration.

Maintainers populate signing one of two ways:

1. Create `keystore.properties` at the repo root (gitignored) with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
2. Set the environment variables `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` (CI uses this path — see [release-process.md](release-process.md)).

The [`signingValue(...)` helper](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/build.gradle.kts#L25-L28) checks `keystore.properties` first, falls back to env. If `storeFile` resolves to a non-existent path or is absent entirely, the signing config is constructed empty and the build proceeds unsigned without failing.

## See also

- [release-process.md](release-process.md) — versionCode derivation, the CI workflow, Play Store submission flow.
- [testing.md](testing.md) — how to run the test suites and macrobenchmarks.
