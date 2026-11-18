# Adding a New App Language

This guide documents the current i18n surface in Featherline and the steps needed to add another in-app language. It covers resource strings, app language selection, date/time formats, widgets, notifications, and known hard-coded formatting shapes that need review for every locale.

## Current State

- Default resources live in `app/src/main/res/values/strings.xml`.
- Simplified Chinese lives in `app/src/main/res/values-b+zh+Hans/strings.xml`.
- Traditional Chinese lives in `app/src/main/res/values-b+zh+Hant/strings.xml`.
- Cantonese (Hong Kong and Macau) lives in
  `app/src/main/res/values-b+yue+Hant+HK/strings.xml`.
- Czech lives in `app/src/main/res/values-cs/strings.xml`. It is offered, but its translation is kept by a contributor, not with the four above: a string added here reaches Czech only when the contributor translates it, and until then Czech shows it in English (`values`). Only the four above are maintained with each change (`AGENTS.md`).
- Android's per-app language setting (API 33+) offers the languages in `app/src/main/res/xml/locales_config.xml` (`android:localeConfig`), the same tags as `AppLanguageOption`. `androidResources.generateLocaleConfig` is off: a generated config would list every resource folder, including translations the screens can't show (`values-es`, `values-ja`, `values-ko`, `values-ta` map to English).
- App bundle language splits are disabled with `bundle.language.enableSplit = false`, so packaged resources stay available for in-app switching.
- The app currently exposes `ENGLISH`, `SIMPLIFIED_CHINESE`,
  `TRADITIONAL_CHINESE`, `CANTONESE`, and `CZECH` in `AppLanguageOption`.
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
- A folder alone doesn't make the language selectable: add it to `locales_config.xml` with the option (below).
- Translate widget preview strings too, including `widget_preview_dose_time`, `widget_preview_dose_time_evening`, and the anchor-widget preview strings `anchor_widget_preview_since` and `anchor_widget_preview_days`; those are static app-widget picker previews on Android 12-14. (`anchor_widget_preview_name` is `translatable="false"` — leave it.)

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

Cantonese uses the `yue` language code. The `廣東話` option uses `yue-Hant-HK`
and Hong Kong/Macau wording; `fromLocale()` maps explicit `yue` locales to it,
while `zh-Hant` and scriptless `zh-HK`/`zh-MO` remain Traditional Chinese.
Czech is `cs`, and `fromLocale()` maps any `cs` locale to it.

Then update `app/src/main/java/com/mkx/hrttracker/ui/settings/SettingsUiText.kt`:

```kotlin
AppLanguageOption.TRADITIONAL_CHINESE -> R.string.app_language_traditional_chinese
AppLanguageOption.FRENCH -> R.string.app_language_french
```

Add each new `app_language_*` label to the four maintained languages' `strings.xml`; a language that lacks it, as Czech does, falls back to `values`. Use the native language name as the label.

Add the option's `languageTag` to `app/src/main/res/xml/locales_config.xml`, so Android's per-app language setting offers it too.

## Language Switching Paths

The choice is the app locale list, whichever screen set it: an empty list follows the system (Follow system, first in the picker, and what a fresh install shows), a set one is that language. From API 33 the list is the framework's, so Android's per-app language setting changes it too, and `appLocales()` (`util/Localization.kt`) reads it through `LocaleManagerCompat` with the application context: `AppCompatDelegate.getApplicationLocales()` reaches the framework only through an attached activity and reports an empty list without one. Below 33 it is the one AppCompat stores (`autoStoreLocales`), which AppCompat loads only when an activity attaches: until then `appLocales()` reads AppCompat's file through `LocaleManagerCompat.getApplicationLocales(context)`, so a widget refresh or an alarm with no activity sees the choice too. AppCompat's in-memory list wins once it holds one, and always once this process has set it (`setAppLocales()`): it writes the file later, and deletes it for an empty list. `SettingsState.appLanguageOption` is the language shown, and with `appLanguageFollowsSystem` it is the phone's, mapped by `fromLocale()` (`systemAppLanguage()`).

The picker calls `SettingsRepository.setAppLanguageOption()`, null for Follow system:

- Calls `Locale.setDefault(locale)` with the language shown, so any non-Compose code still reading `Locale.getDefault()` formats in it until something resets it: below API 33 AppCompat sets the default to the phone's locales inside `setApplicationLocales` when the list is emptied, and from API 33 the framework sets it from the new configuration at the configuration change.
- Calls `AppCompatDelegate.setApplicationLocales()` (through `setAppLocales()`) with the option's tag, or `LocaleListCompat.getEmptyLocaleList()` for Follow system.
- AppCompat auto-stores locales through `AppLocalesMetadataHolderService` in `AndroidManifest.xml`.

`SettingsRepository.refreshAppLanguageOption()` reads the list back: at `MainActivity.onCreate` and `WidgetConfigActivity.onCreate`, at each return to the foreground, and on each configuration change (`HrtTrackerApplication.onConfigurationChanged` → `followAndroidAppLanguageSetting`), which is how a change in Android's per-app setting, or of the phone's language while following it, arrives (`MainActivity` handles `locale` itself).

Both write DataStore's `app_language_tag`, the language shown (the chosen tags, or the phone's language mapped when following it), as does the repository once it has read the last one at the start. Only the next start reads it, to repost the reminders when it differs (see [reminders](reminders.md#notify)). Text posted outside an activity takes the language from `appLanguageTag()`, which reads `appLocales()` as the screens do.

`MainActivity` and `WidgetConfigActivity` provide their content with a context in the language shown (`rememberAppLanguageContext()`), so under Follow system a phone language the app doesn't offer (`ja`, say) shows English there as everywhere, not the unmaintained translation the activity's own resources would pick.

`currentAppLocale()` and `rememberAppLocale()` in `util/Localization.kt` read the active locale from Android resources. Use these in UI code instead of `Locale.getDefault()` unless the code is intentionally outside Compose and has already been given a localized context.

## Notifications and Widgets

Notifications and widgets have extra locale handling because they often run from singleton application contexts.

- `ReminderNotificationManager.createNotificationChannel(languageTag)` resolves channel name/description through a locale-overridden context. `HrtTrackerApplication` re-registers the channel when the language shown (`SettingsRepository.shownAppLanguage`) changes.
- Reminder notification titles, bodies, dose details, and actions are resolved through `ReminderNotificationManager.localizedContext()`, a context pinned to `SettingsRepository.appLanguageTag()` (the app language: the chosen one, or with Follow system the phone's mapped to a supported one, as the screens show it), so they localize below API 33; the text comes from string resources and plurals in `ReminderNotificationManager.kt` and `MedicationDetailText.kt`.
- Any other string a singleton resolves off the application context (the duplicate-medicine toast through `ToastManager.showMessageRes`, the disabled anchor-shortcut message) goes through `withAppLanguage()` too: the application context's own resources are in the phone's language below API 33, and under Follow system in any packaged translation.
- Post-log toasts fired after a reminder's "Log now" and widget quick-log resolve through the same `localizedContext()` (like the notification content), then come from string resources and plurals in `ReminderNotificationManager.kt` (the `showStock*Toast` helpers); the count variants use plurals, so translate the plural forms.
- Stock UI strings — runway/"days remaining" labels, stock state labels, unit nouns (vials, containers, sachets, etc.), and the onboarding opt-in copy — live in `strings.xml` alongside the rest. Translate the new block and check the plural-bearing count strings.
- `WidgetSnapshotRepository` creates a localized context from `settings.appLanguageOption.languageTag` before building snapshots. It also passes a locale-specific `localizedShortTimeFormatter`.
- Live widget rows are built from localized string resources plus fixed separators. Review these for the new language:
  - `WidgetTodaySummary.kt`: the today count builds `/$totalCount <DONE>`, with ` ($manualCount)` inserted when manual records exist, or `$manualCount <MANUAL>` on a day with no planned doses; the large widget header prefixes it with `"<Today> · "`.
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
  - Czech: `d. M.` (CLDR's shape for the skeleton).
  - Other locales: `MMM d`.

- `otherYearDateFormatter(locale)`
  - Chinese: `yyyy年M月d日`.
  - Czech: `d. M. yyyy`.
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

- New schedule default time is `LocalTime.of(9, 0)` in `MedicationGroupEditorViewModel.kt` (and `DailyScheduleEditor.kt`).
- Home and widget day sections use fixed buckets in `MedicationDayTimeRange.kt` (00:00, 04:00, 12:00 and 18:00 starts; "Last night" until noon).
- Calendar week start is user-configurable via `SettingsState.firstDayOfWeekOption` (`FirstDayOfWeekOption` enum: `FOLLOW_SYSTEM`, `SATURDAY`, `SUNDAY`, `MONDAY`). Plan and History calendars and the weekly schedule editor's weekday chips all read it. `FOLLOW_SYSTEM` resolves via `WeekFields.of(systemLocale()).firstDayOfWeek`, where `systemLocale()` (in `util/Localization.kt`) reads the phone's first language so the resolved day reflects the device region, **not** any per-app language override: from API 33 through `LocaleManagerCompat.getSystemLocales`, since `Resources.getSystem()` carries the per-app language first there; below, `Resources.getSystem()`. The 24-hour check for a "use locale default" phone (`uses24HourTimeFormat()`) reads the same `systemLocales()`. Adding a language therefore does not change calendar behavior for existing users; the setting is independent of `AppLanguageOption`. Weekly schedule cadence math in `MedicationGroupSchedule.isScheduledOn` and `PlanBatchAddViewModel` is anchored on each schedule's own `since` date — there is no global Monday/Sunday week boundary at the domain layer.

## Number, Unit, and Medical Text

- Medication dose numbers use `Double.formatDose(locale)` and stock quantities/rates use `formatStockCount(value, locale)`, both in `model/medication/DoseFormatting.kt`, which swap the decimal separator for the active locale.
- **Numeric input** is the inverse: European Decimal keyboards emit a comma (e.g. `1,5`), so each input site normalizes comma to dot via `.trim().replace(',', '.')` before `toDoubleOrNull()` (medicine strength/dose, open-container amount, weight/stock/calibration). New numeric fields must do the same or comma entries silently fail to parse.
- Compose medication text passes `rememberAppLocale()`.
- Non-Compose medication text (reminders, widget) resolves the locale from the caller's context via `Context.currentAppLocale()`, which follows the context pinned to the app language.
- Blood-test unit labels in `BloodValueFormatters.kt` are scientific abbreviations such as `pg/mL` and `pmol/L`; these are currently not Android string resources.
- Some calibration numeric values intentionally use invariant formatting (`Locale.US` or `Locale.ROOT`) for compact medical values. Review before changing, because these values are also used in chart labels and summaries.
- Medication catalog labels, routes, categories, analyte names, weight units, and settings option labels are all string resources mapped from enum helpers. If a new enum is added while adding a language, add resource mappings in every locale.

## Hard-Coded String Audit

The current runtime code is mostly resource-backed. Hard-coded text found during the audit falls into these categories:

- Compose preview/sample literals, for example `"Today"`, `"Edit medication"`, `"Add entry"`, `"19:00"`, `"9:30 AM"`. These do not ship as normal runtime UI.
- Animation/debug labels, log messages, storage keys, backup field names, notification tags, and DataStore keys. Do not translate these.
- Symbolic UI text, for example `"."`, `"—"`, percentages, and static counters.
- Runtime composition separators, especially `" · "`, `"/"`, and `" - "`. These are usually acceptable, but should be reviewed for grammar and readability in each new language.

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
   - Onboarding, including the stock-tracking opt-in step.
   - Home, including E2 chart marker labels, 7/30-day axis labels, and the low-stock section.
   - Medicine manager and medicine detail stock controls: stock subcard, Adjust stock sheet (recount/received), open-container dialog, runway and stock-state labels.
   - Plan calendar, schedule editor, date picker, time picker, and batch add.
   - History calendar/month picker and selected-day records.
   - Blood test calibration screens.
   - Settings language menu, backup/restore dialogs, app lock, reminders, widget appearance.
   - Reminder notification body/action text.
   - Medium and large home widgets, including static picker previews and live widget rows.

6. Check layout at narrow widths. Medical disclaimers, backup dialogs, notification permission copy, and widget preview strings are the most likely to overflow.
