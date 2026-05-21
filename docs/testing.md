# Testing

Test suite layout and how to run.

## Suite layout

Three test source roots and one build-type override set:

- [`app/src/test/`](https://github.com/mkx173/Featherline/tree/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/src/test) — JVM unit tests. About a hundred test classes mirror the main-package tree (`data/`, `model/`, `reminder/`, `startup/`, `ui/`, `util/`). Pure-Kotlin domain math, repository wiring with mocks, validation logic. The cheapest tests to add and run; runs as part of every `:app` JVM build.
- [`app/src/androidTest/`](https://github.com/mkx173/Featherline/tree/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/src/androidTest) — instrumented tests that need an Android runtime (device or emulator). Currently small; UI tests use `androidx.compose.ui.test.junit4`. Most behaviour stays unit-testable, so this directory grows slowly.
- [`macrobenchmark/src/main/`](https://github.com/mkx173/Featherline/tree/096ce12612596e7968dd8314bd18b3566b2c2ed1/macrobenchmark/src/main) — separate `:macrobenchmark` Gradle module hosting system-level startup measurement and baseline-profile generation. Three files: [`BaselineProfileGenerator`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/macrobenchmark/src/main/java/com/mkx/hrttracker/macrobenchmark/BaselineProfileGenerator.kt) (generates the baseline profile), [`StartupBenchmark`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/macrobenchmark/src/main/java/com/mkx/hrttracker/macrobenchmark/StartupBenchmark.kt) (measures cold-start), [`BenchmarkFixture`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/macrobenchmark/src/main/java/com/mkx/hrttracker/macrobenchmark/BenchmarkFixture.kt). No scroll or interaction benchmarks exist yet; the module is intentionally narrow.
- [`app/src/benchmark/`](https://github.com/mkx173/Featherline/tree/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/src/benchmark) — **not a separate test suite.** A source-set override that activates when the `benchmark` build type is selected. Provides [`StartupFixtureActivity`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/src/benchmark/java/com/mkx/hrttracker/benchmark/StartupFixtureActivity.kt), a deterministic startup target the macrobenchmark module instruments via [`matchingFallbacks`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/macrobenchmark/build.gradle.kts#L18-L24).

## How to run

```bash
# JVM unit tests (fast; runs on the host JVM)
./gradlew testPlayDebugUnitTest

# Single test class or method
./gradlew testPlayDebugUnitTest --tests "BloodTestCatalogTest"
./gradlew testPlayDebugUnitTest --tests "BloodTestCatalogTest.fromCanonical_inverts_toCanonical_for_every_analyte_and_allowed_unit"

# Instrumented tests (needs a connected device or emulator)
./gradlew connectedPlayDebugAndroidTest

# Macrobenchmarks (needs a connected device; runs the benchmark build type of :app)
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

Add `--info` for verbose Gradle logs; the `--tests` flag accepts wildcards.

JVM unit tests set [`unitTests.isReturnDefaultValues = true`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/build.gradle.kts#L123-L125) — un-mocked Android framework calls return `null` / `0` / `false` rather than throwing. This keeps pure-Kotlin tests JVM-runnable but means you can't rely on default-return semantics for behaviour verification. Mock or instrument when the test depends on framework state.

After a run, Gradle writes HTML reports for browsing:

- Unit tests: `app/build/reports/tests/testPlayDebugUnitTest/index.html`.
- Instrumented tests: `app/build/reports/androidTests/connected/`.
- Macrobenchmark results: `macrobenchmark/build/outputs/connected_android_test_additional_output/` (per-iteration timing JSON + trace files).

Open the report HTML directly in a browser; the failure stack trace there is more readable than the Gradle console output.

## Where to put new tests

- **Pure-Kotlin domain logic** (`model/`, time math, fulfillment predicates, factor-table conversions, validation predicates) → `app/src/test/`. This is where the bulk of the suite lives. The math itself is JVM-runnable so these tests stay fast (sub-second).
- **Repository / DAO / DataStore interactions, framework-free** → `app/src/test/` with mocks ([mockk](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/gradle/libs.versions.toml#L25)) where the test exercises only Kotlin code paths. Use `kotlinx-coroutines-test` (`runTest { ... }`) to drive suspend functions; use `TestScope` for cancellation discipline.
- **Repository / DAO / DataStore interactions, framework-dependent** → `app/src/androidTest/` when the test needs real Room migration behaviour, real DataStore I/O, or `SQLCipher` decryption against a temp file.
- **Compose UI tests** → `app/src/androidTest/` with `ComposeRule` from [`androidx.compose.ui.test.junit4`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/gradle/libs.versions.toml#L58).
- **BroadcastReceiver / Service / AlarmManager tests** → `app/src/androidTest/`. Robolectric is not configured; instrumentation is the only path that exercises the Android system services these classes integrate with (`AlarmManager.setExactAndAllowWhileIdle`, `NotificationManager.createNotificationChannel`, etc.).
- **Benchmark or startup-cost regressions** → `macrobenchmark/`. Macrobenchmarks need a release-shaped build (the `benchmark` build type of `:app`) and a connected device.

Test class naming follows `<ClassUnderTest>Test` — for example, `BloodTestCatalogTest`, `MedicationGroupSlotFulfillmentTest`, `BackupRestoreValidationTest`. Unit tests use JUnit 4 (`@Test`), [mockk](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/gradle/libs.versions.toml#L25) for mocks, [`kotlinx-coroutines-test`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/gradle/libs.versions.toml#L26) for coroutine dispatchers.

## Tests in CI

[`.github/workflows/android-release.yml`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/.github/workflows/android-release.yml) does **not** run tests. It builds and uploads the release sideload APK only. Test gating is by maintainer review and local execution — contributors are expected to run `./gradlew testPlayDebugUnitTest` before opening a PR, and the maintainer re-runs the full unit-test suite locally before tagging a release. There is no `pull_request` workflow today; if test gating becomes a recurring problem, adding one is a small, well-scoped follow-up.

## Baseline profiles

A baseline profile lists hot code paths the runtime should pre-compile, shrinking cold-start time on first launch. The `:macrobenchmark` module's [`BaselineProfileGenerator`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/macrobenchmark/src/main/java/com/mkx/hrttracker/macrobenchmark/BaselineProfileGenerator.kt) generates one by exercising the app's hot paths against the `benchmark` build type (a release-shaped build that the [`:macrobenchmark` module's `matchingFallbacks`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/macrobenchmark/build.gradle.kts#L18-L24) targets via [self-instrumentation](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/macrobenchmark/build.gradle.kts#L15-L16)).

Generate locally with:

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  --tests "com.mkx.hrttracker.macrobenchmark.BaselineProfileGenerator"
```

The generator writes the profile to the macrobenchmark module's output; the maintainer copies it to `app/src/main/baseline-prof.txt` and commits it. **No baseline profile is currently checked into the repo** — `find app/src/main -name 'baseline-prof*'` returns nothing. The generator is wired up and runnable; landing a checked-in profile is a planned follow-up, not a blocker.

Re-run when: top-level screens are restructured, the navigation graph changes shape, a hot library is replaced, or [`StartupBenchmark`](https://github.com/mkx173/Featherline/blob/096ce12612596e7968dd8314bd18b3566b2c2ed1/macrobenchmark/src/main/java/com/mkx/hrttracker/macrobenchmark/StartupBenchmark.kt) shows a release-build startup-time regression.

## See also

- [architecture.md](architecture.md) — layer map and named-thing context for what each layer's tests target.
- [data-model.md](data-model.md) — Room schema for tests that touch the database.
- [building.md](building.md) — Gradle commands and flavors.
- [release-process.md](release-process.md) — pre-release verification.
