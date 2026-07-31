<p align="center">
  <img src=".github/app-icon.png" alt="Featherline icon" width="120" />
</p>

# Featherline

**English** · [简体中文](README.zh-CN.md)

HRT medication tracker for Android with PK projections and lab tracking. On-device, encrypted, no account required.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Build](https://github.com/mkx173/Featherline/actions/workflows/android-release.yml/badge.svg)](https://github.com/mkx173/Featherline/actions/workflows/android-release.yml)
![minSdk](https://img.shields.io/badge/minSdk-26-blue.svg)
![targetSdk](https://img.shields.io/badge/targetSdk-37-blue.svg)

Featherline logs doses across injection, patch, gel, oral, and sublingual routes; projects estradiol levels from your dose history using a three-compartment pharmacokinetic model; and tracks blood test results with automatic unit conversion across canonical and clinical units. Data stays in an encrypted local database by default, with no required account and no telemetry. Play builds can optionally synchronize an end-to-end encrypted snapshot through the user's Google Drive. Available in English and Simplified Chinese.

> ⚠️ **Not medical advice.** Featherline is a tracking tool, not a medical device, and using it does not establish a clinician relationship. The pharmacokinetic projection is a rough population-average estimate from your logged doses — it is not a substitute for blood tests or for a clinician's interpretation, and you should not use it to make dosing changes. See [docs/safety.md](docs/safety.md) for the full disclaimer.

## Get the app

<a href="https://play.google.com/store/apps/details?id=com.mkx.hrttracker"><img src=".github/GetItOnGooglePlay_Badge_Web_color_English.png" alt="Get it on Google Play" height="54" align="middle"></a>
<a href="https://f-droid.org/packages/com.mkx.hrttracker"><img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80" align="middle"></a>

- **Play Store**: [Link](https://play.google.com/store/apps/details?id=com.mkx.hrttracker)
- **F-Droid**: [Link](https://f-droid.org/packages/com.mkx.hrttracker)
- **GitHub Releases** (signed APK for sideload): [releases page](https://github.com/mkx173/Featherline/releases)
- Or build from source: see [docs/building.md](docs/building.md)

> **Note:** The Play Store or F-Droid version is recommended. The F-Droid/GitHub sideload builds hold no internet permission, so they cannot check for updates and do not include Google Drive sync — monitor the releases page manually for new GitHub builds.

## Features

- Log doses across injection, patch, gel, oral, and sublingual routes
- Configurable reminder schedules with snooze and exact-alarm handling
- Group medications and apply schedules to grouped doses
- Optional medicine stock tracking with low-stock warnings and schedule-aware "days remaining" estimates
- Estradiol pharmacokinetic projection from your dose history
- Blood test catalog with automatic unit conversion (pg/mL ↔ pmol/L, ng/dL ↔ nmol/L)
- Encrypted, compressed backup format with restore validation
- Optional end-to-end encrypted Google Drive sync in Play builds, with 1-, 3-, 7-, or 30-day automatic cadence, manual sync, and conflict protection
- App lock with biometric unlock
- Home-screen quick-log widget in two sizes, with progress, next-dose, and tap-to-log
- Journal tab to track meaningful dates on a timeline with milestones, plus per-day notes; pin any date to your home screen as an anchor widget or shortcut showing its running day count
- No required account and no telemetry; cloud sync is optional and off by default
- English and Simplified Chinese
- Material 3 with dynamic color

## Screenshots

<table>
  <tr>
    <td width="25%"><img src=".github/screenshots/home.png" alt="Home screen"></td>
    <td width="25%"><img src=".github/screenshots/plan.png" alt="Plan view"></td>
    <td width="25%"><img src=".github/screenshots/history.png" alt="History log"></td>
    <td width="25%"><img src=".github/screenshots/calibration.png" alt="Calibration screen"></td>
  </tr>
  <tr>
    <td align="center">Home</td>
    <td align="center">Plan</td>
    <td align="center">History</td>
    <td align="center">Calibration</td>
  </tr>
</table>

## How it works

Featherline is a single-module Android app written in Kotlin with Jetpack Compose. Doses, reminders, and lab results live in a SQLCipher-encrypted Room database. The pharmacokinetic engine is a three-compartment model that converts every logged dose into an estradiol contribution over time, then sums contributions across all routes to produce a projected curve. Reminders use AlarmManager with exact-alarm permission handling and snooze support; notifications survive reboots and time changes through a reconciliation layer. The blood test catalog defines analytes with bidirectional unit conversion via a canonical factor table. Backups are encrypted, compressed, and use a versioned format with full restore validation.

The full architecture, data model, and reminder pipeline are documented in [docs/architecture.md](docs/architecture.md).

## Roadmap

- Replace the pharmacokinetic engine with a more general multi-medication model
- Personal-PK calibration tuned from your own lab results
- Additional languages — translation contributions welcomed on [Hosted Weblate](https://hosted.weblate.org/projects/featherline/) (see [docs/localization.md](docs/localization.md))

## Tech stack

Kotlin, Jetpack Compose with Material 3, Hilt for dependency injection, Room with SQLCipher for encrypted persistence, Coroutines + Flow. See [gradle/libs.versions.toml](gradle/libs.versions.toml) for exact versions.

## Building from source

Prerequisites: JDK 17, a recent Android Studio, and an Android SDK whose API level matches the project's `compileSdk`. Standard Android Studio import; run `./gradlew assembleDebug` or use the IDE.

Detailed instructions, flavors, and CI behavior: see [docs/building.md](docs/building.md).

## Contributing

Contributions are welcome. Read the [Code of Conduct](CODE_OF_CONDUCT.md) first, then see [CONTRIBUTING.md](CONTRIBUTING.md) for development workflow, branching conventions, and how to propose changes.

Translation contributions are welcome on [Hosted Weblate](https://hosted.weblate.org/projects/featherline/).

## Privacy

Featherline stores data in an encrypted on-device database, requires no Featherline account, and performs no telemetry. Play builds can optionally send only an end-to-end encrypted snapshot to the user's private Google Drive application-data folder. See [docs/privacy.md](docs/privacy.md) for the full data-handling description.

## License

Featherline is released under the GNU General Public License, version 3.0. See [LICENSE](LICENSE) for the full text.
Third-party dependency, asset, and adapted-code notices are listed in [docs/third-party-notices.md](docs/third-party-notices.md).

## Acknowledgments

- The [Material Symbols](https://fonts.google.com/icons) icon set.
- The pharmacokinetic projection draws on the math reference from [HRT-Recorder-PKcomponent-Test](https://github.com/LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test).
- The estradiol undecylate pharmacokinetic parameters come from [Transmtf-HRT-Tracker](https://github.com/TransmtfTeam/Transmtf-HRT-Tracker).
- Plot display logic was adapted from [Oyama's HRT Tracker](https://github.com/SmirnovaOyama/Oyama-s-HRT-Tracker).
- The broader trans health community for testing, feedback, and the prior art that makes a tool like this possible.
