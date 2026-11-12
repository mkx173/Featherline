# Reminders

How Featherline turns a medication schedule into dose-reminder
notifications at the right wall-clock time, repeats them while the dose
is unlogged, and keeps that pipeline right across reboots, time zone
changes, and revoked permissions. The whole subsystem lives in
[`reminder/`](../app/src/main/java/com/mkx/hrttracker/reminder). For
where it sits in the layer map, see [architecture.md](architecture.md).
The design is `notes/2026-11-10-reminders-v2.md`; its rules are
model-checked by the `verify-reminders` skill.

## The rules

1. **One notification per plan.** It names that plan's dose due at its
   alert time and nothing from other plans; Log now logs that plan only.
2. **A dose rings a fixed number of times while it is unlogged.** Its
   alerts are instants: the dose's own (its wall time in the current
   zone: a time the clocks skip rings once they have, an hour later; a
   time they repeat rings on its first pass), then *count* repeats *i*
   minutes apart in real time (Settings → Repeat reminders, one setting
   for every plan, default 3 × 15 min; count Off or 1-4, interval 5-60
   min in 5-minute steps). The alerts stop at the first of: the count, a
   log of the dose, Skip, or the instant of the plan's next dose.
   Nothing is stored per alert: each pass works the alerts out from the
   schedule, the log and the clock, and arms one alarm per plan, at its
   next alert. Nothing reads an armed instant back as a wall time.
3. **Sound.** Every alert sounds, repeats included, except that of
   plans alerting at one time only the first to arrive sounds; the rest
   post silently, with no sound and no heads-up. A late post for an
   alert at or before the last one that sounded posts silently too.
   Reposts by the reconcile are silent.
4. **Skip** stops that dose's alerts and dismisses the notification. It
   logs nothing, so Home still shows the dose as missed. Toast:
   "Notifications will stop for this dose."
5. **A swipe** (or Clear all) dismisses the notification, and the alerts
   go on. A toast names the plan's next alert.

## Sequence

```mermaid
sequenceDiagram
  participant App
  participant Planner
  participant Scheduler
  participant AlarmManager
  participant Receiver
  participant Notifier
  participant User

  App->>Planner: buildNextMedicationReminderPlans(groups, entries, now, repeats, skips)
  Planner->>Scheduler: MedicationReminderPlan(groupUuid, alertAt: Instant, doseAt)
  Scheduler->>AlarmManager: setExactAndAllowWhileIdle(RTC_WAKEUP, alertAt)
  AlarmManager-->>Receiver: ACTION_MEDICATION_REMINDER (EXTRA_ALERT_AT)
  Receiver->>Notifier: the plan's latest due alert's dose, sound or silent
  Receiver->>Scheduler: rescheduleGroup(after = now): the next alert
  Notifier-->>User: one plan's reminder
  User->>Notifier: Log now / Skip / tap / swipe
```

## Pipeline

### Plan

[`MedicationReminderPlanner.kt`](../app/src/main/java/com/mkx/hrttracker/reminder/MedicationReminderPlanner.kt):

- `reminderAlerts(dosesFrom, entries, repeats, skippedThrough)` — every
  alert of a plan's doses from a time on, in order. A dose rings while
  `missingSlotCounts` says it misses some medication (a dose logged
  early or late, even outside its window, is taken) and it is after the
  plan's skip watermark. Its alerts are the dose time plus k × the
  interval, k = 0..count, before the plan's next dose (`withNextDoseAt`).
- `nextReminderAlert(after, …)` — the first alert strictly after a
  time. An alert at exactly `now` has rung, and the startup `now` is
  truncated to the minute, so arming it again would ring a just-tapped
  reminder again.
- `latestDueReminderAlert(since, now, …)` — the latest alert from the
  armed one up to now: what a delivery rings for. A late delivery rings
  only that one, not each alert it was late for; an earlier missed dose
  shows as overdue on Home.
- `buildNextMedicationReminderPlans` — one plan per active group with
  reminders on and medications: an armed alert whose instant has passed
  is armed again as it is (it fires at once; see [Edge
  cases](#edge-cases)), else the next alert.

### Schedule

[`MedicationReminderScheduler.kt`](../app/src/main/java/com/mkx/hrttracker/reminder/MedicationReminderScheduler.kt)
is the single seat for setting and cancelling alarms. Every pass and
delivery runs under `withReminderScheduleLock`.

- `rescheduleAll(now, repostShown)` — full rebuild from Room. Used by
  the capability reconciler, startup, Settings, the editor's archive.
  `repostShown` reposts every reminder on screen, silently, after a
  restore. A pass that starts with the reconcile-owed flag set reposts
  too, under the lock, so a new process's first pass reposts whichever
  of its launches (the first resume's capability reconcile, or
  StartupPreloader) runs first.
- `rescheduleFromHomeSnapshot(snapshot, now)` — the same from an
  already-built `HomeSnapshotRecord`, before the database is warm.
- `rescheduleGroup(groupUuid, after)` — one plan, after a delivery, a
  log, an edit, a Skip.
- `skipThrough(groupUuid, doseAt)` — Skip: moves the plan's watermark
  (never back) and reschedules it, in one hold.
- `claimAlertSound(alertAt)` — the sound rule, with the last sounded
  alert instant kept in memory. A process death between two plans'
  alarms gives two sounds, which is the safe way to fail.

Alarms use `setExactAndAllowWhileIdle` on `RTC_WAKEUP` when
`canScheduleExactAlarms()`, with a fallback to `setAndAllowWhileIdle`
if the call throws `SecurityException`, or directly when exact access
is missing. A plan's alarm intent has the data URI
`hrttracker://medication-reminder/<groupUuid>`, so arming it again
replaces the previous one, and carries the alert's instant in
`EXTRA_ALERT_AT`.

### The schedule store

[`ReminderScheduleStore`](../app/src/main/java/com/mkx/hrttracker/reminder/ReminderScheduleStore.kt)
(`reminder_schedule`, a Preferences DataStore; a corrupt file reads as
empty) holds:

- each armed plan with its alert's **instant** (epoch millis). A key
  with no time, as v1.4.2 stored it, takes any delivery. A missing key
  means delivered, cancelled, or reminders off. A pass reads it to
  cancel orphans and to arm again an alert that passed undelivered;
- each plan's **skip watermark**: the latest dose time you tapped Skip
  on. Alerts consider only doses after it. One value per plan, so it
  never grows past the plans ever skipped; a restore clears them all;
- the reconcile-owed flag.

### A delivery is identified by its instant

The receiver posts only while the plan's stored instant is the alarm's
(`awaitsReminderDelivery`). Comparing local times, as v1.4.2 did, took
an old alarm delivered just after a zone change for the new one when
the two shared a local time: it rang at the wrong moment, and the
dose's ring at its new local time was lost.

### Receive

- [`MedicationReminderReceiver`](../app/src/main/java/com/mkx/hrttracker/reminder/MedicationReminderReceiver.kt)
  — reads the master switch, then in one hold of the lock: checks the
  stored instant, posts the plan's reminder for its latest due alert
  (reading the switch again just before), marks it delivered, and arms
  the next alert. With reminders off it posts nothing.
- [`MedicationReminderActionReceiver`](../app/src/main/java/com/mkx/hrttracker/reminder/MedicationReminderActionReceiver.kt)
  — Log now (`ACTION_MEDICATION_REMINDER_LOG_NOW`), Skip
  (`ACTION_MEDICATION_REMINDER_SKIP`), and the notification's delete
  intent (`ACTION_MEDICATION_REMINDER_DISMISSED`). A v1.4.2 Remind later
  tap or snooze alarm does nothing.
- [`MedicationReminderRescheduleReceiver`](../app/src/main/java/com/mkx/hrttracker/reminder/MedicationReminderRescheduleReceiver.kt)
  — `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`,
  `TIMEZONE_CHANGED` and `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`,
  all through `ReminderCapabilityReconciler.reconcile(forceReschedule =
  true)`.

### Act

[`MedicationReminderActionHandler.kt`](../app/src/main/java/com/mkx/hrttracker/reminder/MedicationReminderActionHandler.kt):

- `logNow` — logs what the reminder showed (the action carries the
  `(slot, MedicationSignature)` pairs, so an edit since drops out),
  shows a logged or a stock toast, cancels the notification and
  reschedules the plan. It logs an archived or muted plan's dose too:
  the tap is explicit.
- `skip` — in one hold: cancels the notification, moves the watermark,
  reschedules the plan; then the toast.
- `dismissed` — the swipe's toast: "Next reminder at 08:15", "Next
  reminder tomorrow at 08:00", or "Next reminder on Nov 14 at 08:00",
  from the plan's armed alert. No toast with reminders off or nothing
  armed ahead. Toasts within 2 s of the last one are dropped, as the
  Settings version copy does (`NEXT_REMINDER_TOAST_THROTTLE_MS`), so
  Clear all shows one. The last-shown time lives in memory.

### Notify

[`ReminderNotificationManager.kt`](../app/src/main/java/com/mkx/hrttracker/reminder/ReminderNotificationManager.kt)
owns one channel, `dose_reminders` at `IMPORTANCE_HIGH`, whose name and
description are in the app language. Every string, date and time a
reminder or toast shows is resolved in the app language, also in a
process an alarm woke (`SettingsRepository.appLanguageTag()`).

A reminder carries the plan name as its text, the dose's date and time
as its subtext, the medications in its expanded text (or only the plan
name with Hide medication details on), Skip and Log now actions, and a
delete intent. Its tag, `medication-reminder/<doseAt>/<hash of its
slots>`, is per plan and dose, so a repeat posts in place, and with no
`setOnlyAlertOnce` it alerts again. A silent post uses `setSilent`.

`reconcileDoseReminderNotifications`, run by every pass, brings the
reminders on screen in line with the plans, one per plan: one whose dose
is gone (logged, moved by an edit, plan archived, deleted or muted) is
cancelled, one whose medications changed is reposted silently in place.
With reminders off, every one goes. Turning Hide medication details on
or off reposts the reminders on screen
(`refreshDoseReminderNotifications`).

Tapping the body opens the app on the dose's row
(`hrttracker://medication-reminder-notification/<tag>`, with
`EXTRA_HIGHLIGHT_SCHEDULED_TARGETS`), as the widget's row tap does.

### Capability and permissions

- `NotificationPermissions.kt` — `canPostNotifications` and
  `canScheduleExactAlarms`.
- [`ReminderCapabilityReconciler.kt`](../app/src/main/java/com/mkx/hrttracker/reminder/ReminderCapabilityReconciler.kt)
  — re-reads both, publishes them, turns the master switch off when
  notification access is gone, and runs `rescheduleAll`. It skips the
  pass when nothing changed since its last run, unless forced (the
  receivers, a restore). `MainActivity.onResume` calls it.

## The upgrade from v1.4.2

v1.4.2 had Remind later and merged the reminders of plans that shared
a time. On the first start of a later version (the package replaced, or
the first resume):

1. The capability reconciler reads the old snooze store
   (`medication_reminder_snoozes`), cancels each snooze alarm (their
   PendingIntents used `hrttracker://medication-reminder-snooze/<time>/<hash>`),
   and deletes the store (`LegacySnoozeCleanup.kt`).
2. v1.4.2's reminders on screen, merged ones included, are gone: the
   system cancels a package's notifications when it replaces the
   package (they carried no doses in their extras, so the reconcile
   couldn't have read them anyway). The next alerts post one per plan.
3. A v1.4.2 alarm carries no instant; only a v1.4.2 key, with no time,
   takes it. The channel stays the same.

## Reboot and time-zone reconciliation

Boot and an APK replacement lose every alarm; a time or zone change
moves every alarm's instant. Each goes through the forced capability
reconcile, which re-derives every plan's next alert from the schedule:
"re-derive, don't replay". The time receivers also refresh
`AppTimeSource` and ask Home for a rebuild.

## Edge cases

- **Exact-alarm permission denied.** Alarms are inexact, so Doze can
  defer them; the reconciler exposes `hasExactAlarmAccess = false` so
  Settings can offer a fix. The repeats are as late as their alarms.
- **Notification permission denied.** Posts are skipped, and the
  reconciler turns the master switch off.
- **A time that passes while the device is off.** The receiver replaces
  the plan's stored alert with the next one after each delivery, so an
  alert still in the past at a pass was never delivered: it is armed
  again, fires at once, and the receiver rings the plan's latest due
  alert. An alarm in flight when the pass runs is delivered twice; the
  first delivery marks it, so the second posts nothing.
- **Clock moved forward, or a late delivery.** The reminder names the
  latest due alert's dose, so Log now saves the dose that is due; the
  next alarm is set from now.
- **The clocks go back or forward.** Alerts are instants, and every
  pass and delivery takes its `now` as an instant from the clock, never
  as a wall time. An hour that repeats: a dose in it has its alert on
  the first pass, so on the second it has passed and rings no more. A
  gap: a dose in it rings once the clocks have skipped, an hour later.
  Repeats are real time, so they stay i minutes apart across a change.
  Known limits:
  - on the spring-forward day, a dose in the gap and the plan's next
    dose under an hour after it (02:30 and 03:00 on Mar 8): the 02:30
    dose doesn't ring, because its moment (03:30) comes after the next
    dose's (03:00). The 03:00 reminder covers it, and Home shows 02:30
    as missed;
  - a dose in the hour first armed on its second pass, as for a plan
    created then, keeps its first-pass instant, so the part of its
    repeats still to come rings in the second pass, early on the clock
    (R10). In New York on Nov 1 2026, a plan created at the second
    01:10 with a 01:45 dose and the default repeats posts at 01:15 and
    01:30 on the clock, labelled 01:45, and nothing at 01:45; with
    repeats Off, nothing rings that day. It is one hour a year, and a
    fix would need stored state;
  - setting the clock back by hand, or moving to a zone further west,
    repeats wall times the zone rules don't, so an alert that rang can
    ring again (R11). A sounded alert later than the clock is then
    forgotten, so an alert that comes before it sounds rather than
    posting silently. If nothing else sounds first, the alert that had
    sounded posts silently when the clock reaches its time again: it
    has sounded once.
- **Repeats in Doze.** The platform limits how often an app's
  `setExactAndAllowWhileIdle` alarms go off: about once a minute in
  normal use, and in low-power idle modes much less often, such as
  once per 15 minutes. So 5- and 10-minute repeats on an idle phone
  can arrive late, and a late delivery rings only the latest due alert:
  two repeats can collapse into one ring.
- **A failed delivery.** `scheduleGroupReminder` does its reads before
  it cancels, so a failed reschedule leaves the plan's alarm and key as
  they were; the delivered plan's own alarm was the one consumed, so it
  has none until the next full pass (app start, boot, time change). A
  pass stores its plans before it arms them.
