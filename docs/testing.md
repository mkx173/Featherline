# Testing

Test suite layout and how to run.

## Suite layout

Two test source roots:

- [`app/src/test/`](https://github.com/mkx173/Featherline/tree/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/src/test) — JVM unit tests. 200+ test classes mirror the main-package tree (`data/`, `model/`, `reminder/`, `startup/`, `ui/`, `util/`). Pure-Kotlin domain math, repository wiring with mocks, validation logic. The cheapest tests to add and run; runs as part of every `:app` JVM build.
- [`app/src/androidTest/`](https://github.com/mkx173/Featherline/tree/096ce12612596e7968dd8314bd18b3566b2c2ed1/app/src/androidTest) — instrumented tests that need an Android runtime (device or emulator). Smaller Android-runtime coverage lives here; UI tests use `androidx.compose.ui.test.junit4`. Most behaviour stays unit-testable, so this directory grows slowly.

## How to run

```bash
# JVM unit tests (fast; runs on the host JVM)
./gradlew testPlayDebugUnitTest

# Single test class or method
./gradlew testPlayDebugUnitTest --tests "BloodTestCatalogTest"
./gradlew testPlayDebugUnitTest --tests "BloodTestCatalogTest.fromCanonical_inverts_toCanonical_for_every_analyte_and_allowed_unit"

# Instrumented tests (needs a connected device or emulator)
./gradlew connectedPlayDebugAndroidTest
```

Add `--info` for verbose Gradle logs; the `--tests` flag accepts wildcards.

JVM unit tests set [`unitTests.isReturnDefaultValues = true`](https://github.com/mkx173/Featherline/blob/main/app/build.gradle.kts#L149-L151) — un-mocked Android framework calls return `null` / `0` / `false` rather than throwing. This keeps pure-Kotlin tests JVM-runnable but means you can't rely on default-return semantics for behaviour verification. Mock or instrument when the test depends on framework state.

After a run, Gradle writes HTML reports for browsing:

- Unit tests: `app/build/reports/tests/testPlayDebugUnitTest/index.html`.
- Instrumented tests: `app/build/reports/androidTests/connected/`.

Open the report HTML directly in a browser; the failure stack trace there is more readable than the Gradle console output.

## End-to-end tests

E2E tests live in `app/src/androidTest/java/com/mkx/hrttracker/e2e/`. Run them with:

```bash
devenv tasks run android:e2e

# Use a different project emulator port.
ANDROID_EMULATOR_PORT=5556 devenv tasks run android:e2e
```

The task builds and launches the Debug app on the project emulator, then runs
every instrumented test in `com.mkx.hrttracker.e2e`. Put future feature suites
in that package to include them automatically. To run one case on an already
running emulator:

```bash
ANDROID_SERIAL=emulator-5556 devenv shell -- ./gradlew connectedPlayDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.mkx.hrttracker.e2e.MedicationDayE2eTest#defaultMidnightKeepsDawnInCivilDay
```

The medication-day suite starts the real `MainActivity` and uses production
Hilt bindings, SQLCipher/Room, DataStore, navigation, clock broadcasts and
application lifecycle. A Debug-only entry point provides fixture access; no
repositories or time sources are mocked. Tests require an emulator, reset the
Debug app's database, configure its settings, and temporarily change the device
clock, timezone and 12/24-hour format. Device settings are restored in teardown,
including on assertion failure. The suite also pins the Debug app's per-app
locale to `en` for the run (`cmd locale set-app-locales`, API 33+), after
marking AppCompat's one-time locale migration as done so it can't clear the pin.

Night owl is set per plan: the suite saves each plan with its own day ending
(`MedicationGroupScheduleInput.dayBoundary`) and, wherever it shows the
difference, puts a midnight plan next to the night-owl one. Records not from a
plan always stay on their calendar day.

Coverage includes:

| Area | Scenarios |
| --- | --- |
| Plan editor | Turning Night owl on for a plan, the saved ending surviving recreation and relaunch, and the toggle locking once the plan has a record |
| Boundaries | Default midnight; a night-owl plan's 00:00–03:59 doses on the evening and its 04:00 dose on the new day, next to a midnight plan; manual records on their calendar day at 03:59:59 and 04:00; fulfillment-window neighbours, stock runway and planned PK doses for a night-owl plan every 2 days |
| Home and plans | Each plan's own ordering (a midnight plan's 01:00 first, a night-owl plan's last), the moon without a day offset, no duplicated context rows, quick-log confirmation, completion counts, weekly Monday 01:00 recurrence; at 00:30 the Tomorrow header without a date until 04:00, the antiandrogen due label and the Coming up title each following its own plan, and the Plan tab's evening showing the 02:00 dose as Planned and the untaken 22:00 as Missed |
| History and Plan | A night-owl 01:00 record on the evening's date with the moon and no "(+1)" in History and in the Plan tab's selected day, manual versus linked records, records never rewritten, edit/delete, archive visibility, month/year transitions and stored entry timezone |
| Lifecycle | Natural foreground tick across 04:00, background clock changes across 00:00 and 04:00, and a relaunch |
| System integration | Actual 01:00 notification delivery on Tuesday for a night-owl Monday plan; real Android widget host, rendered counts, the 04:00 re-anchor next to a midnight plan, Coming up parity with manual and archived rows, and Last night removal at noon |
| Persistence | An encrypted backup restoring a plan's night owl, and an older backup (v7, without the field) restoring plans as midnight |
| Other date features | Journal notes retain their civil day |

Backup tests invoke the real export/encryption/restore services with generated
payloads and verify the resulting UI. They do not drive Android's system document
picker. Widget tests use an `AppWidgetHost` rather than automating a particular
launcher's widget placement gestures. Device reboot, launcher placement, lab-test
and milestone screens remain manual checks.

Reports are written to `app/build/reports/androidTests/connected/debug/flavors/play/`.
Per-case logcat and JUnit XML are in
`app/build/outputs/androidTest-results/connected/debug/flavors/play/`. Failed UI
waits include the Compose semantics tree in the failure message.

## Where to put new tests

- **Pure-Kotlin domain logic** (`model/`, time math, fulfillment predicates, factor-table conversions, validation predicates) → `app/src/test/`. This is where the bulk of the suite lives. The math itself is JVM-runnable so these tests stay fast (sub-second).
- **Repository / DAO / DataStore interactions, framework-free** → `app/src/test/` with mocks ([mockk](https://github.com/mkx173/Featherline/blob/main/gradle/libs.versions.toml#L25)) where the test exercises only Kotlin code paths. Use `kotlinx-coroutines-test` (`runTest { ... }`) to drive suspend functions; use `TestScope` for cancellation discipline.
- **Repository / DAO / DataStore interactions, framework-dependent** → `app/src/androidTest/` when the test needs real Room migration behaviour, real DataStore I/O, or `SQLCipher` decryption against a temp file.
- **Compose UI render tests** → fast JVM tests in `app/src/test/` via Robolectric (`@RunWith(RobolectricTestRunner)` + `@GraphicsMode(NATIVE)` + `createComposeRule()`) for layout/structure assertions (e.g. `HrtSectionRenderTest`); tests that need a real device stay in `app/src/androidTest/` with `ComposeRule` from `androidx.compose.ui.test.junit4`.
- **BroadcastReceiver / Service / AlarmManager tests** → `app/src/androidTest/`. Robolectric is configured only for Compose render tests, not the Android system services these classes integrate with (`AlarmManager.setExactAndAllowWhileIdle`, `NotificationManager.createNotificationChannel`, etc.), so instrumentation is the only path.

Test class naming follows `<ClassUnderTest>Test` — for example, `BloodTestCatalogTest`, `MedicationGroupSlotFulfillmentTest`, `BackupRestoreValidationTest`. Unit tests use JUnit 4 (`@Test`), [mockk](https://github.com/mkx173/Featherline/blob/main/gradle/libs.versions.toml#L25) for mocks, [`kotlinx-coroutines-test`](https://github.com/mkx173/Featherline/blob/main/gradle/libs.versions.toml#L26) for coroutine dispatchers.

## Tests in CI

[`.github/workflows/android-release.yml`](https://github.com/mkx173/Featherline/blob/main/.github/workflows/android-release.yml) does **not** run tests. It builds and uploads the release sideload APK only. Test gating is by maintainer review and local execution — contributors are expected to run `./gradlew testPlayDebugUnitTest` before opening a PR, and the maintainer re-runs the full unit-test suite locally before tagging a release. There is no `pull_request` workflow today; if test gating becomes a recurring problem, adding one is a small, well-scoped follow-up.

## See also

- [architecture.md](architecture.md) — layer map and named-thing context for what each layer's tests target.
- [data-model.md](data-model.md) — Room schema for tests that touch the database.
- [building.md](building.md) — Gradle commands and flavors.
- [release-process.md](release-process.md) — pre-release verification.
