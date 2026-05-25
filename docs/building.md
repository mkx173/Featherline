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

This produces a signed APK at `app/build/outputs/apk/play/debug/`. The debug build type wires `signingConfig` to the same `release` signing config the release build uses ([`app/build.gradle.kts:88-91`](https://github.com/mkx173/Featherline/blob/642ffa739a76211a3e9dd422d66f329296055bf2/app/build.gradle.kts#L88-L91)), so a clean checkout fails at `packagePlayDebug` with `SigningConfig "release" is missing required property "storeFile"` unless a keystore is available. Contributors who do not have a release keystore can point at AGP's debug keystore (`~/.android/debug.keystore`, created by Android Studio on first run) by dropping a `keystore.properties` at the repo root:

```properties
storeFile=/Users/<you>/.android/debug.keystore
storePassword=android
keyAlias=androiddebugkey
keyPassword=android
```

These are the well-known AGP debug-keystore credentials. The file is gitignored. Maintainers with a real release keystore use the same `keystore.properties` / env-var setup described under [Signing setup](#signing-setup) — that satisfies the debug build automatically. Building from Android Studio runs the same Gradle tasks, so the same prerequisite applies there.

## Flavors and build types

Flavor dimension `distribution` ([`app/build.gradle.kts:33`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/build.gradle.kts#L33)):

- `play` — App Bundle target. No ABI filter; all ABIs bundled. Used for Play Store submission.
- `arm64` — APK target. ABI filter `arm64-v8a` only. Used for the GitHub Releases sideload APK.

Build types ([`app/build.gradle.kts:77-101`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/build.gradle.kts#L77-L101)):

- `debug` — `versionNameSuffix = "-<short-sha>"`; uses the release signing config, so the build requires a keystore (see [Quick start](#quick-start)).
- `release` — `isMinifyEnabled = true`, `isShrinkResources = true`, ProGuard rules applied.
- `benchmark` — initialized from `release`, `applicationIdSuffix = ".benchmark"`, debug-signed, `isDebuggable = false`. Used by the `:macrobenchmark` module.

Useful Gradle tasks:

- `./gradlew assemblePlayDebug` — debug APK for daily development.
- `./gradlew assembleArm64Release` — release APK for sideload (this is what CI builds).
- `./gradlew bundlePlayRelease` — Play Store App Bundle (maintainer builds locally for submission).
- `./gradlew installPlayDebug` — install debug APK on a connected device.

The output filename is set in the [`androidComponents`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/build.gradle.kts#L129-L147) block: `featherline-<abi>-<versionName>-<versionCode>.apk` (the root project is named `Featherline` in [`settings.gradle.kts:25`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/settings.gradle.kts#L25)). `<abi>` is `arm64-v8a` for the `arm64` flavor, `all-abis` for `play`.

## Signing setup

The `debug` and `release` build types both assign the `release` signing config explicitly. The `benchmark` build type, derived from `release` via `initWith`, overrides this to use the `debug` signing config — `:macrobenchmark` only needs a runnable APK. Contributors therefore still need *some* keystore on disk for normal debug and release builds; the debug keystore workaround under [Quick start](#quick-start) is the cheapest path for non-maintainers.

Maintainers populate signing one of two ways:

1. Create `keystore.properties` at the repo root (gitignored) with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
2. Set the environment variables `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` (CI uses this path — see [release-process.md](release-process.md)).

The [`signingValue(...)` helper](https://github.com/mkx173/Featherline/blob/642ffa739a76211a3e9dd422d66f329296055bf2/app/build.gradle.kts#L29-L32) checks `keystore.properties` first, falls back to env. If neither source supplies a `storeFile` path, the `release` signing config is *constructed* empty — but any build type that consumes it (`debug` and `release`) still fails at packaging with the missing-`storeFile` error. The benchmark build type derives from `release`, then overrides signing to the debug config. There is no truly unsigned debug/release build path today.

## See also

- [release-process.md](release-process.md) — versionCode derivation, the CI workflow, Play Store submission flow.
- [testing.md](testing.md) — how to run the test suites and macrobenchmarks.
