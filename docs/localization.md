# Adding a New App Language

This guide documents the current i18n surface in Featherline and the steps needed to add another in-app language. It covers resource strings, app language selection, date/time formats, widgets, notifications, and known hard-coded formatting shapes that need review for every locale.

## Current State

- Default resources live in `app/src/main/res/values/strings.xml`.
- Default resource locale is English via `app/src/main/res/resources.properties`:

```properties
unqualifiedResLocale = en
```

- Simplified Chinese lives in `app/src/main/res/values-b+zh+Hans/strings.xml`.
- `app/build.gradle.kts` enables `androidResources.generateLocaleConfig = true`, so Android's locale config is generated from resource folders.
- App bundle language splits are disabled with `bundle.language.enableSplit = false`, so packaged resources stay available for in-app switching.
- The app currently exposes only `ENGLISH` and `SIMPLIFIED_CHINESE` in `AppLanguageOption`.
- The only default string intentionally missing from `zh-Hans` is `privacy_policy_url`, marked `translatable="false"`.

## Add the Resource Folder

1. Pick the Android resource qualifier:
   - Simple language: `values-fr`, `values-ja`, `values-de`.
   - BCP 47 script/region: `values-b+zh+Hant`, `values-b+pt+BR`.

2. Copy the default resource file:

```bash
cp app/src/main/res/values/strings.xml app/src/main/res/values-fr/strings.xml
```

3. Translate every translatable `<string>` and `<plurals>` item.

Keep these rules:

- Preserve placeholder indexes and types exactly, for example `%1$s`, `%2$d`.
- Preserve escaped newlines where present, for example `\n`.
- Do not copy `translatable="false"` strings into locale files unless there is a locale-specific non-translatable override.
- Keep `<plurals>` category names valid for the target language. Android can use only the categories required by the locale, but each referenced plural must contain all categories that locale needs.
- Translate widget preview strings too, including `widget_preview_dose_time` and `widget_preview_dose_time_evening`; those are static app-widget picker previews on Android 12-14.

## Add the Language Option

Update `app/src/main/java/com/mkx/hrttracker/model/settings/SettingsModels.kt`:

This example adds French and shows how to preserve a script-specific Chinese option if that variant is introduced:

```kotlin
enum class AppLanguageOption(val languageTag: String) {
    ENGLISH(languageTag = "en"),
    SIMPLIFIED_CHINESE(languageTag = "zh-Hans"),
    TRADITIONAL_CHINESE(languageTag = "zh-Hant"),
    FRENCH(languageTag = "fr");

    companion object {
        fun fromLocale(locale: Locale): AppLanguageOption {
            return when {
                locale.language == "zh" && locale.script == "Hant" -> TRADITIONAL_CHINESE
                locale.language == "zh" -> SIMPLIFIED_CHINESE
                locale.language == "fr" -> FRENCH
                else -> ENGLISH
            }
        }
    }
}
```

For languages with important script or region variants, do not rely only on `locale.language`; inspect `locale.toLanguageTag()`, `locale.script`, or `locale.country` so system/restored locales map to the correct option. For example, a naive `locale.language == "zh"` branch would incorrectly map Traditional Chinese to Simplified Chinese.

Then update `app/src/main/java/com/mkx/hrttracker/ui/settings/SettingsUiText.kt`:

```kotlin
AppLanguageOption.TRADITIONAL_CHINESE -> R.string.app_language_traditional_chinese
AppLanguageOption.FRENCH -> R.string.app_language_french
```

Add each new `app_language_*` label to every language's `strings.xml`. Use the native language name as the label.

## Language Switching Paths

The app uses `SettingsRepository.setAppLanguageOption()` to switch language:

- Calls `Locale.setDefault(locale)` so non-Compose helpers that read `Locale.getDefault()` format doses in the selected app language.
- Calls `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(option.languageTag))`.
- AppCompat auto-stores locales through `AppLocalesMetadataHolderService` in `AndroidManifest.xml`.

`currentAppLocale()` and `rememberAppLocale()` in `util/Localization.kt` read the active locale from Android resources. Use these in UI code instead of `Locale.getDefault()` unless the code is intentionally outside Compose and has already been given a localized context.

## Notifications and Widgets

Notifications and widgets have extra locale handling because they often run from singleton application contexts.

- `ReminderNotificationManager.createNotificationChannel(languageTag)` resolves channel name/description through a locale-overridden context. `HrtTrackerApplication` re-registers the channel when `settingsState.appLanguageOption` changes.
- Reminder notification titles/bodies come from string resources and plurals in `ReminderNotificationText.kt` and `ReminderNotificationManager.kt`.
- `WidgetSnapshotRepository` creates a localized context from `settings.appLanguageOption.languageTag` before building snapshots. It also passes a locale-specific `localizedShortTimeFormatter`.
- Live widget rows are built from localized string resources plus fixed separators. Review these for the new language:
  - `HrtWidget.kt`: progress text currently builds `/$totalCount <DONE>` and `"<Today> · $doneCount/$totalCount <DONE>"`.
  - `HrtWidget.kt` and `WidgetRows.kt`: supporting text joins route and dose with `" · "`.
  - `WidgetE2Text.kt`: E2 summary uses literal `"E2 ~"` and a literal space before the unit; the unit label itself comes from `calibrationUnitLabel(displayUnit)`.

If the new language needs different grammar/order for these shapes, replace the hard-coded composition with a string resource.

## Date and Time Formatting

Most date/time formatting is centralized in `app/src/main/java/com/mkx/hrttracker/util/AppDateTimeFormatters.kt`.

Review and extend these helpers when adding a language whose date grammar is not covered by the current English/Chinese split:

- `localizedShortTimeFormatter(locale, uses24HourFormat)`
  - 24-hour: `HH:mm`.
  - Chinese 12-hour: `ah:mm`.
  - Other 12-hour locales: `h:mm a`.
  - The app observes Android's 12/24-hour system preference through `rememberUses24HourTimeFormat()` and `Context.observeUses24HourTimeFormat()`.

- `currentYearDateFormatter(locale)`
  - Chinese: `M月d日`.
  - Other locales: `MMM d`.

- `otherYearDateFormatter(locale)`
  - Chinese: `yyyy年M月d日`.
  - Other locales: `MMM d, yyyy`.

- `historyMonthLabelFormatter(locale)`
  - Chinese: `M月`.
  - Other locales: `LLLL`.

- `calendarMonthTitleFormatter()` and `monthHeaderFormatter()`
  - Chinese other-year format: `yyyy年M月`.
  - Other locales: `LLLL yyyy`.

- `calibrationPanelDateTimeFormatters()`
  - Chinese month: `M月`.
  - Other month: `MMM`.
  - Day: `d`.
  - Time: `localizedShortTimeFormatter`.

Other date/time call sites:

- Main E2 chart marker labels use `DateFormat.getBestDateTimePattern(locale, "Md")` and `"yMd"` in `MainContentComponents.kt`.
- Main E2 30-day chart axis labels intentionally use fixed `M/d` to keep ticks narrow. Revisit this if it reads poorly in the new locale.
- Schedule and history weekday labels use `DayOfWeek.getDisplayName(..., locale)` or `Month.getDisplayName(..., locale)`.
- `medicationGroupScheduleDateFormatter()` currently concatenates `<date> <weekday>`. If the new language needs a different order, make this branch locale-specific.
- File names use invariant timestamps (`yyyy-MM-dd_HH-mm-ss`) in backup and diagnostics export services. Do not localize file-name timestamps unless the storage contract changes.

Known non-locale-sensitive time constants:

- New schedule default time is `LocalTime.of(9, 0)` in `MedicationGroupEditorViewModel.kt`.
- Widget "last night"/"tonight" bucketing uses 06:00 and 18:00 cutoffs in `WidgetSnapshotBuilder.kt`.
- Calendar week calculations currently use Monday as the first day in `PlanViewModel.kt`, `HistoryViewModel.kt`, `PlanCalendarRange.kt`, and `PlanBatchAddViewModel.kt`. Schedule interval math also uses Monday week starts. This is behavioral, not just translation. Decide separately if the new locale should change calendar week starts.

## Number, Unit, and Medical Text

- Medication dose numbers use `Double.formatDose(locale)` in `model/medication/DoseFormatting.kt`, which swaps the decimal separator for the active locale.
- Compose medication text passes `rememberAppLocale()`.
- Non-Compose medication text uses `Locale.getDefault()` after `SettingsRepository` syncs it.
- Blood-test unit labels in `BloodValueFormatters.kt` are scientific abbreviations such as `pg/mL` and `pmol/L`; these are currently not Android string resources.
- Some calibration numeric values intentionally use invariant formatting (`Locale.US` or `Locale.ROOT`) for compact medical values. Review before changing, because these values are also used in chart labels and summaries.
- Medication catalog labels, routes, categories, analyte names, weight units, and settings option labels are all string resources mapped from enum helpers. If a new enum is added while adding a language, add resource mappings in every locale.

## Hard-Coded String Audit

The current runtime code is mostly resource-backed. Hard-coded text found during the audit falls into these categories:

- Compose preview/sample literals, for example `"Today"`, `"Edit medication"`, `"Add entry"`, `"19:00"`, `"9:30 AM"`. These do not ship as normal runtime UI.
- Animation/debug labels, log messages, storage keys, backup field names, notification tags, and DataStore keys. Do not translate these.
- Symbolic UI text, for example `"."`, `"—"`, percentages, and static counters.
- Runtime composition separators, especially `" · "`, `"/"`, `" - "`, and count suffix `"x"`. These are usually acceptable, but should be reviewed for grammar and readability in each new language.

If a hard-coded runtime phrase is found while adding a language, move it to `strings.xml` instead of adding another branch in UI code.

Useful searches:

```bash
rg --pcre2 -n 'Text\(\s*"|text\s*=\s*"(?![a-z0-9_-]+")|title\s*=\s*"|contentDescription\s*=\s*"' app/src/main/java/com/mkx/hrttracker -g '*.kt'
rg --pcre2 -n 'android:text="(?!@string)' app/src/main/res -g '*.xml'
rg -n 'DateTimeFormatter\.ofPattern|DateFormat|getBestDateTimePattern|Locale\.US|Locale\.ROOT|Locale\.getDefault\(\)' app/src/main/java/com/mkx/hrttracker
rg -n 'LocalTime\.of\(|09:00|21:00|AM|PM' app/src/main/java/com/mkx/hrttracker app/src/main/res
```

## Validation Checklist

Run these checks after adding the locale.

1. Validate XML:

```bash
xmllint --noout app/src/main/res/values/strings.xml
xmllint --noout app/src/main/res/values-fr/strings.xml
```

2. Check for missing string/plural names. The only expected missing key in existing `zh-Hans` is `privacy_policy_url` because it is `translatable="false"`. If a locale intentionally omits a translated value, such as a widget preview time left in English, this command will still flag it; document those exceptions. Locale-specific `translatable="false"` overrides will also surface in this raw name comparison.

```bash
comm -23 \
  <(rg -o 'name="[^"]+"' app/src/main/res/values/strings.xml | sort -u) \
  <(rg -o 'name="[^"]+"' app/src/main/res/values-fr/strings.xml | sort -u)
```

3. Run focused unit tests:

```bash
./gradlew :app:testPlayDebugUnitTest --tests '*AppDateTimeFormattersTest' --tests '*MedicationGroupScheduleFormattingTest' --tests '*DoseFormattingTest'
```

4. Run the broader app tests before merge:

```bash
./gradlew :app:testPlayDebugUnitTest
```

5. Manually verify these screens in the new language:
   - Onboarding.
   - Home, including E2 chart marker labels and 7/30-day axis labels.
   - Plan calendar, schedule editor, date picker, time picker, and batch add.
   - History calendar/month picker and selected-day records.
   - Blood test calibration screens.
   - Settings language menu, backup/restore dialogs, app lock, reminders, widget appearance.
   - Reminder notification body/action text.
   - Medium and large home widgets, including static picker previews and live widget rows.

6. Check layout at narrow widths. Medical disclaimers, backup dialogs, notification permission copy, and widget preview strings are the most likely to overflow.
