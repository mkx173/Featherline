# Changelog

All notable changes to HRTTracker will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

_Nothing yet._

## [1.0.3] - 2026-05-31

### Added

- 1/8 tablet fraction option when entering a tablet dose.

### Fixed

- Numeric inputs for medicine and stock now accept a comma as the decimal separator.
- The Home screen and widget now update immediately after settings or data changes, including while the app is in the background.
- The estradiol projection no longer treats a future-dated dose as the most recent dose.
- The dose highlight animation no longer replays when switching tabs.
- The Home screen now reflects system date and time changes after the app returns to the foreground.
- Doses from archived medication groups can now be logged from the widget and from the reminder "Log all" action.
- The archive indicator now also appears on the medium widget's active dose.
- The widget no longer shows blank rows after a background refresh or when medication-detail privacy is toggled.

## [1.0.2] - 2026-05-30

### Added

- Doses from archived medication groups can now appear on the Home screen and widget when "show archived group records" is enabled, marked with an archive indicator.
- 1/3 tablet fraction option when entering a tablet dose.

### Changed

- Reordered the display settings and clarified their summaries.

## [1.0.1] - 2026-05-30

### Fixed

- Restoring a backup no longer reports a valid file as incompatible when its widget content scale was set outside the previous narrower range.
- Home-screen widget content can no longer render illegibly small when an unexpectedly small cell baseline is captured.

### Changed

- Updated icons on the medicine details page.

## [1.0.0] - 2026-05-30

First public release on Google Play.

### Added

- Dose logging across injection, patch, gel, oral, and sublingual routes.
- Configurable daily and weekly reminder schedules with snooze and exact-alarm handling; reminders survive reboots and time changes.
- Medication grouping, with schedules applied to grouped doses.
- Optional medicine stock tracking with low-stock warnings and schedule-aware "days remaining" estimates.
- Estradiol pharmacokinetic projection from your dose history, using a three-compartment population model.
- Blood test catalog with automatic unit conversion (pg/mL ↔ pmol/L, ng/dL ↔ nmol/L).
- Encrypted, compressed backup format with restore validation.
- App lock with biometric unlock.
- Home-screen quick-log widget in two sizes, with progress, next-dose, and tap-to-log.
- English and Simplified Chinese localization.
- Material 3 interface with dynamic color.
- Fully on-device, encrypted local storage — no accounts, no telemetry, no network calls.

[Unreleased]: https://github.com/mkx173/Featherline/compare/v1.0.3...HEAD
[1.0.3]: https://github.com/mkx173/Featherline/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/mkx173/Featherline/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/mkx173/Featherline/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/mkx173/Featherline/releases/tag/v1.0.0
