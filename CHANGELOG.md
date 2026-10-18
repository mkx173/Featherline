# Changelog

All notable changes to Featherline will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- After a planned dose's time passes, opening the app no longer shows an estradiol curve that looks like the population estimate, or the calibrated curve without its prediction interval. Home now works the estimate out again whenever the time, your data or the chart window moves it, usually in the background before you open the app.
- The home-screen widget no longer goes blank when you change a widget setting, such as its appearance or the estradiol unit, while the app's saved copy of your data is out of date, for example right after an app update or a time zone change. It keeps showing your doses while the app brings them up to date.
- When the device's clock moves ahead, for example when you set it forward, the app no longer sends a reminder for every dose time it skipped. Each group reminds you once, of its latest dose time if you haven't logged that dose yet, and its next reminder comes at the usual time.
- A dose reminder whose time passes while the phone is off, or while the system holds its alarm back, is no longer skipped. It comes as soon as the phone is back, once, for the group's latest dose that's due.

### Changed

- Estradiol cypionate injection estimates peak around day 4 again instead of day 2, matching cypionate in oil such as Depo-Estradiol. The day-2 peak came from studies of a different product, the cypionate suspension in monthly contraceptive injections. Total exposure is unchanged, and existing calibrations are refit on the first open after the update.
- Dose reminders now show the date and time of the dose they're for, so two reminders for the same group are easy to tell apart.
- When you come back to the app after the estimate has moved, for example after a planned dose's time passed with reminders off, the estradiol card and chart can show a loading placeholder for a moment before the new estimate appears. The calibration page can show its loading indicator for a moment the same way, for example when you come back to it after midnight.
- The app no longer works out your estimate calibration again each time it starts or you change something. It does so only when something the calibration uses changes (your estradiol results or how you reviewed them, your estradiol doses or your weight), after an app update that changes how estimates are worked out, or when you tap Try again.
- The Estimate calibration section and the estimate sheet on Home now use the same estradiol doses as the Home chart, with days that start at midnight like it, so where they overlap they show the same band. Near the ends of the calibration page's range, its estimate can differ very slightly from before.
- The first time you open the app after this update, Home's lists take a moment longer to appear, and the estradiol card shows a loading placeholder until the estimate is worked out. This happens once.

## [1.4.2] - 2026-10-14

### Fixed

- After you log a dose or add a result, the dose lists on Home and the results on the calibration page update right away. Only the estimate waits under the refresh indicator until the update is ready: the estradiol card and chart on Home, and the Estimate calibration section on the calibration page.
- The estradiol line on the Home chart no longer dips below a dose marker, and below the bottom of the chart, right after a sublingual dose.
- The 30-day chart labels five dates six days apart. As you zoom in, more dates appear between them, and the dates already shown no longer move or change. Dates at the chart's ends are no longer cut short on small screens.
- When you zoom far into the chart on a narrow screen, the start and end dates under it no longer overlap.

## [1.4.1] - 2026-10-13

### Changed

- Estradiol estimates were refit to the primary pharmacokinetic literature. Oral estradiol and valerate estimates are about 30% lower on average and peak about an hour later, matching blood levels measured by mass spectrometry in trans women. Sublingual estimates peak about a quarter lower and no longer fall below what the same tablet swallowed would give from 8 hours on. Benzoate, valerate, cypionate and enanthate injections now share one bioavailability: valerate estimates rise about 25%, cypionate falls about 18% and now peaks on day 2 instead of day 4, enanthate falls about 12%, benzoate is unchanged. Gel estimates are about 43% higher, in line with the Oestrogel label and a 2025 cohort of trans people on EstroGel. Existing calibrations are refit on the first open after the update.
- Patch routes in calibration show the patch icon instead of the "apply patch" icon, and the rows in the routes sheet are tidier.

### Fixed

- When you remove a medicine from a plan you are editing and then add another, the medicine list no longer counts the unsaved plan as still using the removed medicine.
- Right after the app starts, "About this estimate" shows a loading indicator and then grows smoothly to fit the routes, instead of blinking from a "still loading" row.
- In the date picker, tapping the next-month arrow quickly now moves forward one month per tap, as the previous-month arrow already did, and the month name updates right away.
- The colon in the time picker no longer sits below the digits with its lower dot cut off on some phones, such as the Galaxy S20.

## [1.4.0] - 2026-10-12

### Added

- The Calibration entry in Settings shows whether the estimate is calibrated or the population estimate.
- The icon beside the estradiol title on Home now opens "About this estimate", which lists the routes behind the estimate and the estimate disclaimer. A calibrated route that makes up only a small share of the curve is marked "Small share". It replaces the info button.

### Fixed

- After you log a dose, add a result, or switch the chart between 7 and 30 days, Home and the calibration page no longer flash in-between states such as the population estimate. They keep showing the previous state with a refresh indicator under the top bar until the update is ready, then switch to it all at once.
- Home-screen widgets placed before 1.4.0 no longer grow after the update. The widget content scale is lowered once to match the size the widget had before. If you already lowered the scale on 1.4.0-pre, check it again.
- Chinese text no longer sits below the icon next to it, and text fields no longer jump when a Chinese character follows Latin text. This affected phones whose Latin and Chinese system fonts differ (Pixel, Samsung); phones whose system font covers both, such as Xiaomi with MiSans, are unchanged. The "Adjust Chinese text display" setting is gone, since it is no longer needed.
- Estimate calibration fits only the routes that at least one result supports; a route no result supports is held at the population estimate instead of being fitted and then dropped, so the drawn curve and its prediction interval describe the same fit.
- When every logged estradiol dose is more than 180 days older than your earliest estradiol result, calibration now reports those results as below the modeled signal instead of saying no dose has been logged.
- Home and the calibration page call the estimate calibrated only when a calibrated route makes up a meaningful share of the curve somewhere on Home's visible chart, so a curve from a new, uncalibrated route (for example gel after only oral results) is no longer called calibrated. The prediction interval is drawn only where a calibrated route shapes the curve, so "calibrated" and a visible band go together, and both screens now agree.
- The prediction interval now breaks where calibration stops shaping the curve, instead of bridging short gaps with a straight line.
- The "results to check" sheet now slides away before opening a result instead of blinking off.
- Tapping a route in the calibration page's Routes card now opens the routes sheet, like tapping the card's header.
- The day you selected on the History calendar stays selected when you switch to another tab and back. Leaving History with the back button still resets the calendar.
- Turning reminders on from the "notifications are off" message no longer makes the switch flick off before it turns on after you grant the permission, and a failed save no longer leaves the switch showing on.
- The Calibration entry in Settings shows "calibrated" or "population estimate" as soon as Settings opens, instead of appearing a few seconds after the app starts. It now reads the same information as the estradiol title on Home.
- The refresh indicator under the top bar no longer appears a few seconds after the app starts when nothing changed. Rebuilds caused by the device booting, the clock or time zone changing, an app update, or the midnight refresh now run silently; the indicator shows only after you change data.

### Changed

- Oral and sublingual estradiol estimates now follow published blood levels. Oral estradiol estimates are about 43% higher on average with a peak about 36% lower, and no longer drop to almost zero before the next dose, so results drawn just before a dose now count toward calibration. Sublingual estradiol now drops quickly after its peak, as measured in published studies, instead of staying high for hours. Estradiol valerate tablets, swallowed or under the tongue, are now estimated like estradiol tablets with the same estradiol content. Swallowed valerate estimates rise by about 40%, and sublingual valerate peaks within the first hour. Patches entered by their total amount, such as Estrana tape, are now estimated about half as high, matching the package insert. Estradiol benzoate, cypionate and enanthate injection estimates are about 20–30% lower, in line with published blood levels and other estimators. Gel estimates are about 30% lower and peak sooner after each dose, in line with the EstroGel and Oestrogel labels. Existing calibrations are refit automatically.
- Estimate calibration solves about ten times faster. On a Galaxy S20, a five-route history with 20 results took about 5 seconds per Home refresh and now takes about half a second.
- The shaded band on the Home chart is now called a prediction interval and is drawn lighter, and its legend matches the chart.
- Calibration text is shorter and clearer in every language.
- Settings > About the model now opens Featherline's notes on how its model differs from the original, instead of the original model's repository.
- Sheets and cards look more consistent: calibration sheets use the same cards as the rest of the app, every editor sheet has the same gap under its title, sheet cards share one background color, and cards nested in cards have even margins.

## [1.4.0-pre] - 2026-10-07

### Added

- Estimate calibration for the estradiol estimate. Featherline can now tune its E2 curve to your own E2 blood test results. Each result counts toward the routes (injection, patch, gel, oral, sublingual) that were active when the blood was drawn, and each route's level is adjusted separately while its absorption timing stays the same.
- Each adjusted route shows how many results support it, a low, medium, or high confidence, and warnings such as a large correction or a poor fit.
- Results that disagree strongly with the estimate, or that are zero or below, are collected in a "results to check" list where you can keep them, exclude them, or undo your choice.
- The Home chart shows whether the curve is calibrated or the population estimate, and draws a shaded range where a new result would likely fall. This range is not a target.
- A short "How estimate calibration works" guide opens the first time you visit, with tips for getting more useful results.
- A new small home-screen widget shows a chosen Journal date and its running day count. You can also create a new tracked date while setting up the widget.
- You can pin any Journal date to your home screen as a shortcut that shows its day count and opens straight to that date's milestones.
- The milestones timeline now marks every 100 days, interleaved with anniversaries.
- New catalog medications: Finasteride and Dutasteride (antiandrogens), Raloxifene and Tamoxifen (a new SERM category), GnRH-agonist depot injections, and estradiol undecylate.
- Bicalutamide now offers an 80 mg dose-assist preset.
- Traditional Chinese and Cantonese (Hong Kong) translations.

### Changed

- Home now shows antiandrogen, SERM, and GnRH-agonist doses as separate grouped cards.
- Medicines with a custom route are labelled "Custom" and use a generic medication icon in the app, reminders, and widgets.
- Home-screen widgets resize more smoothly, keep their rounded corners while dragging, and no longer scale with the launcher's grid size.
- Backups now include your calibration review choices. Older versions of the app cannot restore backups made with this version.

### Fixed

- The medium home-screen widget keeps a missed dose visible until its next scheduled window opens, instead of dropping it when the day rolls over.
- Last night's doses stay on the medium home-screen widget through midnight instead of disappearing at the date change.
- Logging a scheduled dose after its window has closed no longer records a duplicate entry.
- SERM and GnRH-agonist doses now appear in last-dose history.
- Pinch-zooming the E2 chart no longer jumps between pages or snaps unexpectedly.
- Widgets now respect the correct minimum size on Android 12.
- Widget previews now show the correct colors instead of always using dynamic color.
- Restoring a backup with an unknown app language now falls back to the system language.

## [1.3.3] - 2026-06-27

### Added

- Added F-Droid listing metadata and a reproducible release build path for F-Droid verification.

## [1.3.2] - 2026-06-27

### Changed

- Re-enabled background blur effect for dialogs by forking material3 DatePicker
- Added background blur effect for HomeCardLayoutRow

### Fixed

- Fixed the wording for future-dated journal timeline milestones, which now read "Planned for <date>" instead of "Since <date>".

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

[Unreleased]: https://github.com/mkx173/Featherline/compare/v1.4.2...HEAD
[1.4.2]: https://github.com/mkx173/Featherline/compare/v1.4.1...v1.4.2
[1.4.1]: https://github.com/mkx173/Featherline/compare/v1.4.0...v1.4.1
[1.4.0]: https://github.com/mkx173/Featherline/compare/v1.4.0-pre...v1.4.0
[1.4.0-pre]: https://github.com/mkx173/Featherline/compare/v1.3.3...v1.4.0-pre
[1.3.3]: https://github.com/mkx173/Featherline/compare/v1.3.2...v1.3.3
[1.3.2]: https://github.com/mkx173/Featherline/compare/v1.3.1...v1.3.2
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
