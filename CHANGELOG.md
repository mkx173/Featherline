# Changelog

All notable changes to Featherline will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

_Nothing yet._

## [1.3.1] - 2026-06-27

### Fixed

- The year selector in date pickers no longer turns see-through, showing the calendar behind it, when the background blur effect is enabled. Dialogs no longer use the blur effect as a result.
- Switching a date picker between its calendar and text-entry modes no longer stutters.

## [1.3.0] - 2026-06-25

### Added

- You can now import your dose history and blood test results from another HRT tracker's exported file.
- A new Journal tab lets you track meaningful dates, each with a custom icon and color, and see how long it has been on a timeline with upcoming milestones.
- You can write a short note for each day from the Journal tab, then browse, edit, or delete past notes.
- You can now reorder and hide the home-screen cards from a "Customize cards" option in the top-bar menu.

### Fixed

- The medium home-screen widget no longer shows "0/0" above an empty panel on a day with a logged manual dose but nothing scheduled.
- Custom medication group and journal date colors now follow the in-app dark/light theme instead of the system setting.
- On Android 12 and below, the navigation bar no longer keeps the wrong width for one rotation before correcting itself.

## [1.2.0] - 2026-06-13

### Added

- On Android 12+, the top app bar, bottom navigation bar, bottom sheets, and dialogs now show a translucent blur of the content behind them, with an appearance setting ("Background blur effect") to turn it off.
- The home-screen widget's theme can now be customized in Settings or the widget editor: accent color, saturation, and light balance, with a button in the editor to preview both the light and dark look. The chosen appearance is included in backups.

### Changed

- App content now draws edge-to-edge behind the bottom navigation bar, so lists scroll under it instead of stopping above it.
- The top app bar's background now fades in as content scrolls beneath it, instead of switching color at a threshold.
- Scrolling to the top (double-tapping the top app bar, or re-tapping the current tab) now plays the same smooth animation on every screen, and extra taps while it is running no longer restart it.
- Switching between the Home, Plan, and Settings tabs is now instant instead of cross-fading.
- Rotating, folding, or resizing the window now updates the layout in place instead of recreating the screen.
- In the group schedule editor, "Also generate past records as planned" is now a checkbox shown while "Show" is selected, and it remembers its choice when switching to "Do not show" and back.
- Reconfiguring a home-screen widget (long-press → settings) now opens a full-screen editor with a live widget preview over your wallpaper.
- The home-screen widget now tints its background and cards from the color theme (your wallpaper colors on Android 12+) instead of neutral grays, matching how system widgets blend with the home screen.

### Fixed

- Smoother scrolling through long lists on the plan, history, and medicines screens.
- The delete button shown while selecting history entries no longer flickers during slow scrolls.
- History entries no longer play a stray icon crossfade while scrolling.
- Opening a sheet or dialog at the same moment as navigating no longer flashes it over the wrong screen or makes it reappear unexpectedly when returning to the page.
- Saving, deleting, or archiving can no longer be cut short by navigating away at the same moment, and a deleted group's editor no longer stays on screen after quick navigation.
- Notification permission changes are now picked up reliably when returning to the app, and reminders are rebuilt only when permissions actually change instead of on every return.
- Returning to a tab after data changed elsewhere (such as restoring a backup) no longer shows the old data for a brief moment before updating.
- The home estradiol chart now updates in the same frame as the rest of the screen after a change, such as deleting a plan or the date rolling over, instead of briefly keeping the old curve.
- The home estradiol chart's opening animation no longer gets cut short on slow app starts, and no longer interrupts a touch in progress when it finishes.
- Per-minute screen updates now pause while the app is in the background instead of running continuously; date and time zone changes are still picked up immediately.
- Disabled rows in settings and editors now gray out their labels in step with their switches instead of a moment behind.
- The home-screen widget no longer briefly flips back to out-of-date content — such as a dose row reverting to its previous state — when you return to the launcher page or close the widget editor.

## [1.1.4] - 2026-06-09

### Added

- The antiandrogen card now lists an ad-hoc (manual) dose as its own row after the planned doses, marked "manual", instead of replacing a planned dose's details.

### Fixed

- The antiandrogen card no longer treats a dose pre-logged for later today as the most recent dose.
- When a group tracks the same antiandrogen at more than one dose, each dose now shows its own last dose, including doses logged on earlier days.
- Restoring a backup now reliably refreshes the home screen, blood tests, low-stock warnings, and medicine details, instead of occasionally showing stale data until the app was restarted.

## [1.1.3] - 2026-06-08

### Added

- The medication log editor now shows an archive icon when logging or editing a dose from an archived group, matching the indicator on the home, plan, and history lists.

### Changed

- Multi-unit dose summaries now fold the count into the dose (e.g. "1.5 tablets · 15 mg") instead of showing a separate count multiplier like "2x".
- General UI polish: refresh icons and unify paddings.

### Fixed

- Stock and dose numbers now use the selected app language's number format (such as a decimal comma) consistently across the app, reminders, and the widget.
- Reminder and widget text, including the toast shown after logging a dose, now appears in the chosen app language on older Android versions, instead of occasionally falling back to the system language.

## [1.1.2] - 2026-06-07

### Changed

- On Android 12+, the app and widget now follow the system's full Material You palette (its secondary and tertiary accents, not just the primary color).

## [1.1.1] - 2026-06-07

### Fixed

- Restoring a backup now reliably refreshes the Plan screen (and the medicines list) instead of occasionally showing stale data until the app was restarted.

## [1.1.0] - 2026-06-07

### Added

- Logging an injection or gel dose can record the actual administered amount as an offset from the plan, set with a snap ruler; it feeds stock and the estradiol projection and shows across Home, Plan, and History.
- A nudge to start stock tracking after creating a medicine, with a manager toggle and auto-opt-out after three dismissals.
- A post-log low-stock snackbar with a "View" action and auto-dismiss.
- Backdated archiving: set the date a plan ended instead of always archiving as of now.
- An AMOLED pure-black dark theme toggle.
- Renaming of custom medicines.
- An appearance setting to adjust Chinese text alignment.
- Support for Android 8.0 (Oreo) and later.
- Optional stock deduction when batch-adding backfilled doses, with a per-medicine before/after preview.
- The blood-test result editor now keeps an in-progress entry if the app is killed in the background.
- The home-screen widget's appearance can now be adjusted from the launcher's long-press reconfigure menu (Android 12+), not just in Settings.

### Changed

- The app and the home-screen widget now share one Material You palette.
- Operation and save failures now show as toasts instead of inline red text.
- Creating a medicine from the manager now opens its detail screen.
- Group slot removal moved into the slot editor sheet; cards show a tap-to-edit chevron.
- Single-use vials (ampules) hide the count editor; the amount is set via the dose adjustment.
- Sealed stock now shows as an inline "(+x)" instead of a separate chip.
- Dose summaries no longer repeat the group name.
- The post-log low-stock warning now fires only when a medicine drops to a worse stock tier, and several medicines collapse into one message.
- Stock counts now take on the low/out status color, and the dose editor's projected after-dose value blinks when the dose would change the status tier.
- The batch-add date range is now limited to today and earlier.
- Deleting selected History records now reports how many were removed.
- Opening a dose from the home-screen widget now scrolls the highlighted dose near the center of the screen instead of just into view.
- General UI polish: refreshed icons, the pre-Android-12 splash icon, widget previews, stock chip labels, and assorted spacing and wording.

### Fixed

- In-app language and theme changes now apply reliably in place, without a blank-screen flash, and the system bars stay correct.
- The Home screen now reflects system timezone changes while in foreground.
- Stock counts for multi-use vials and gels are no longer off by one container in some cases.
- Restoring a backup now safely rejects oversized or corrupt files instead of hanging or running out of memory.
- The estradiol projection chart fill no longer renders above the curve or disappears while panning.
- Onboarding now reflects already-granted permissions, marking the reminder and exact-alarm steps as done instead of prompting again.
- Stock and dose unit labels are now correctly pluralized and localized.
- Removed a spurious preset-dose disclaimer in the group slot editor.
- Event-driven toasts now appear in the app's current language instead of the one active at launch.
- Editing or renaming a medicine's preparation no longer clears its stock or turns off tracking.
- Batch-add no longer shows an inverted range for a not-yet-started plan, and the range stays correct across a date rollover.
- The medication-group editor now restores the group you were editing after the app is killed, instead of an archived original.
- Batch-add now keeps your selection when a save fails, instead of discarding it.
- The home-screen widget no longer renders its content too small on some devices and launchers.
- The widget content-scale and opacity sliders now save the exact percentage shown.
- The large home-screen widget no longer sometimes renders the medium widget layout in release builds.
- A dose that crosses into a freshly opened multi-use vial or gel container now carries the leftover amount across instead of discarding it, and a dose larger than one container draws from as many as needed.
- Stock runway and status no longer jump the moment a scheduled dose's time passes unlogged; the dose still counts for the rest of that day.
- Batch-add no longer loses its post-log low-stock warning after a rotation, or shows a duplicate toast alongside the snackbar.
- On Android 8, editor sheets no longer open the keyboard on entry or leave it stuck open; editing a medicine also steps through its fields with the keyboard's Next button.
- On Android 8–12, the smaller home-screen widget no longer briefly flashes a loading spinner when it refreshes.

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

[Unreleased]: https://github.com/mkx173/Featherline/compare/v1.3.1...HEAD
[1.3.1]: https://github.com/mkx173/Featherline/compare/v1.3.0...v1.3.1
[1.3.0]: https://github.com/mkx173/Featherline/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/mkx173/Featherline/compare/v1.1.4...v1.2.0
[1.1.4]: https://github.com/mkx173/Featherline/compare/v1.1.3...v1.1.4
[1.1.3]: https://github.com/mkx173/Featherline/compare/v1.1.2...v1.1.3
[1.1.2]: https://github.com/mkx173/Featherline/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/mkx173/Featherline/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/mkx173/Featherline/compare/v1.0.3...v1.1.0
[1.0.3]: https://github.com/mkx173/Featherline/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/mkx173/Featherline/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/mkx173/Featherline/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/mkx173/Featherline/releases/tag/v1.0.0
