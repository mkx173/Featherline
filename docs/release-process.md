# Release process

How HRTTracker releases get built, versioned, and published.

## Version scheme

`versionCode` is derived from the git commit count at build time:

```kotlin
val gitCommitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toInt()
```

See [`app/build.gradle.kts:10-12`](https://github.com/mkx173/HRTTracker/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/build.gradle.kts#L10-L12). The `play` and `arm64` flavors share the same `versionCode` at any given commit, so Play Bundle and sideload APK for the same commit map cleanly to each other.

`versionName` is static (currently `1.0.0`, [`app/build.gradle.kts:57`](https://github.com/mkx173/HRTTracker/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/build.gradle.kts#L57)). Debug builds append the git short SHA via [`versionNameSuffix = "-$gitCommitHash"`](https://github.com/mkx173/HRTTracker/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/build.gradle.kts#L78-L81); the suffix is omitted on release builds.

## Release artifacts

Two artifacts are produced per release commit, but **only the sideload APK is built by CI**. The maintainer builds the Play Bundle locally for submission.

- **Play Bundle** (`bundlePlayRelease`) — produced locally; submitted to Play Console outside the repo.
- **Sideload APK** (`assembleArm64Release`) — produced by [`.github/workflows/android-release.yml`](https://github.com/mkx173/HRTTracker/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/.github/workflows/android-release.yml); uploaded as a workflow artifact for the maintainer to attach to a GitHub Release manually.

The output filename pattern (`featherline-<abi>-<versionName>-<versionCode>.apk`) makes the artifact's flavor and version unambiguous at a glance. See the [`androidComponents`](https://github.com/mkx173/HRTTracker/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/build.gradle.kts#L129-L147) block for the naming logic.

## CI workflow

[`.github/workflows/android-release.yml`](https://github.com/mkx173/HRTTracker/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/.github/workflows/android-release.yml) triggers on push to `main` and on manual `workflow_dispatch`. It:

1. Checks out with `fetch-depth: 0` (required so `git rev-list --count HEAD` produces a stable `versionCode`).
2. Sets up Temurin Java 21 with Gradle caching. The runtime is Java 21, but [`jvmToolchain(17)`](https://github.com/mkx173/HRTTracker/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/build.gradle.kts#L149-L151) makes Gradle auto-provision JDK 17 for compilation.
3. Sets up the Android SDK via `android-actions/setup-android@v3`.
4. Decodes `RELEASE_KEYSTORE_BASE64` (a GitHub Actions secret) into the keystore path that the build uses via the `RELEASE_KEYSTORE_PATH` env var. Three more secrets (`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) flow into the build via env.
5. Runs `./gradlew assembleArm64Release`.
6. Uploads `app/build/outputs/apk/arm64/release/*.apk` as the `arm64-release-apk` workflow artifact.

The maintainer downloads the workflow artifact and creates a GitHub Release manually from the UI. **Play Store upload is not automated** — the maintainer handles Play submission via Play Console after building the Play Bundle locally. This is deliberate per the parent OSS docs design; no Play API credentials live in the repo.

## Changelog discipline

`CHANGELOG.md` follows the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) format. User-visible changes accumulate under `[Unreleased]` between releases. On release, the maintainer:

1. Renames the `[Unreleased]` heading to `[<versionName>] - <YYYY-MM-DD>` (the release date).
2. Adds a fresh empty `[Unreleased]` section above it.
3. Commits the changelog edit and tags the commit if appropriate.

This convention is not enforced by tooling — adherence is by review.

## Out of scope

- Play Console upload steps (maintainer-only; varies by track).
- Play Store store-listing maintenance (screenshots, description, content rating).
- F-Droid submission. Not planned for v1 per the parent OSS docs design.
- Automated release notes generation. The GitHub Release body is filled in by hand.

## See also

- [building.md](building.md) — flavors, build types, signing setup.
- [testing.md](testing.md) — pre-release test commands.
