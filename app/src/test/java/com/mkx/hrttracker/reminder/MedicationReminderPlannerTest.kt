package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.ReminderRepeats
import com.mkx.hrttracker.util.MedicationDayBoundary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class MedicationReminderPlannerTest {
    // Shared so the planner's fulfillment signature matches between scheduled
    // group slots and the seeded log entries — random per-call UUIDs would
    // always mismatch and the test would never exercise the "skip fulfilled"
    // or "scan further" branches.
    private val estradiolMedicineUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Test
    fun buildNextMedicationReminderPlans_returns_next_plan_for_enabled_groups() {
        val firstGroup = medicationGroup(
            uuid = UUID.fromString("0d03cb47-f36c-4dce-853b-d2427dc8069b"),
            name = "Morning estradiol",
            time = LocalTime.of(9, 0),
            notificationsEnabled = true
        )
        val secondGroup = medicationGroup(
            uuid = UUID.fromString("419f894f-d432-4e1f-b23e-bdc92048d6e1"),
            name = "Evening spiro",
            time = LocalTime.of(20, 0),
            notificationsEnabled = true
        )

        val plans = plansAt(
            groups = listOf(secondGroup, firstGroup),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 20, 8, 0),
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 20, 9, 0),
                LocalDateTime.of(2026, 4, 20, 20, 0)
            ),
            plans.map { it.scheduledAt }
        )
        assertEquals(
            listOf(firstGroup.uuid, secondGroup.uuid),
            plans.map { it.groupUuid }
        )
    }

    @Test
    fun buildNextMedicationReminderPlans_rearms_an_armed_time_that_has_passed() {
        val group = medicationGroup(
            uuid = UUID.fromString("5b1c7a0e-2d3f-4e5a-9b6c-7d8e9f0a1b2c"),
            name = "Evening estradiol",
            time = LocalTime.of(20, 0),
            notificationsEnabled = true
        )
        // The device was off at 20:00 on the 19th: the alarm never fired.
        val missed = LocalDateTime.of(2026, 4, 19, 20, 0)

        val plans = plansAt(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 20, 8, 0),
            armed = mapOf(group.uuid to missed),
        )

        assertEquals(listOf(missed), plans.map { it.scheduledAt })
        assertEquals(listOf(null), plans.map { it.doseAt })
    }

    @Test
    fun buildNextMedicationReminderPlans_plans_past_an_armed_time_still_ahead() {
        val group = medicationGroup(
            uuid = UUID.fromString("6c2d8b1f-3e4a-4f6b-8c7d-8e9f0a1b2c3d"),
            name = "Evening estradiol",
            time = LocalTime.of(20, 0),
            notificationsEnabled = true
        )

        val plans = plansAt(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 20, 8, 0),
            armed = mapOf(group.uuid to LocalDateTime.of(2026, 4, 20, 20, 0)),
        )

        assertEquals(listOf(LocalDateTime.of(2026, 4, 20, 20, 0)), plans.map { it.scheduledAt })
        assertTrue(plans.single().doseAt != null)
    }

    @Test
    fun buildNextMedicationReminderPlans_ignores_disabled_groups() {
        val group = medicationGroup(
            uuid = UUID.fromString("8509fd6e-51d1-4f58-b3e0-4283357ad0bb"),
            name = "Disabled group",
            time = LocalTime.of(9, 0),
            notificationsEnabled = false
        )

        val plans = plansAt(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 20, 8, 0),
        )

        assertEquals(emptyList<MedicationReminderPlan>(), plans)
    }

    @Test
    fun buildNextMedicationReminderPlans_skips_fulfilled_occurrence_and_uses_next_one() {
        val group = medicationGroup(
            uuid = UUID.fromString("d7bdfbf8-722d-4a81-b921-9169257873aa"),
            name = "Daily estradiol",
            time = LocalTime.of(9, 0),
            notificationsEnabled = true
        )

        val plans = plansAt(
            groups = listOf(group),
            entries = listOf(
                scheduledEntry(
                    groupUuid = group.uuid,
                    appliedAt = LocalDateTime.of(2026, 4, 20, 9, 5),
                    scheduledFor = LocalDateTime.of(2026, 4, 20, 9, 0)
                )
            ),
            now = LocalDateTime.of(2026, 4, 20, 8, 30),
        )

        assertEquals(
            listOf(LocalDateTime.of(2026, 4, 21, 9, 0)),
            plans.map { it.scheduledAt }
        )
    }

    @Test
    fun buildNextMedicationReminderPlans_skips_occurrence_logged_early_outside_its_window() {
        // Tomorrow's 9:00 dose logged at 20:30, 12.5 h early: not on time, but taken.
        val group = medicationGroup(
            uuid = UUID.fromString("0b7e3c1a-5d24-4f86-9a13-6e2f8c4d7b95"),
            name = "Daily estradiol",
            time = LocalTime.of(9, 0),
            notificationsEnabled = true
        )

        val plans = plansAt(
            groups = listOf(group),
            entries = listOf(
                scheduledEntry(
                    groupUuid = group.uuid,
                    appliedAt = LocalDateTime.of(2026, 4, 20, 20, 30),
                    scheduledFor = LocalDateTime.of(2026, 4, 21, 9, 0)
                )
            ),
            now = LocalDateTime.of(2026, 4, 20, 20, 30),
        )

        assertEquals(
            listOf(LocalDateTime.of(2026, 4, 22, 9, 0)),
            plans.map { it.scheduledAt }
        )
    }

    @Test
    fun buildNextMedicationReminderPlans_skips_occurrence_at_exactly_now_to_avoid_past_alarm() {
        // The startup reschedule passes a minute-truncated `now`. When the app
        // is reopened within the same minute a dose fired, the occurrence at
        // exactly `now` would otherwise be re-armed as an exact alarm in the
        // past, which AlarmManager fires immediately -> the reminder re-fires.
        val group = medicationGroup(
            uuid = UUID.fromString("b1f0c2d3-4e5f-4a6b-8c7d-9e0f1a2b3c4d"),
            name = "Daily estradiol",
            time = LocalTime.of(9, 0),
            notificationsEnabled = true
        )

        val plans = plansAt(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 20, 9, 0),
        )

        assertEquals(
            listOf(LocalDateTime.of(2026, 4, 21, 9, 0)),
            plans.map { it.scheduledAt }
        )
    }

    @Test
    fun buildNextMedicationReminderPlans_scans_entire_90_day_window_for_next_unfulfilled_occurrence() {
        val group = medicationGroup(
            uuid = UUID.fromString("c873b736-521a-4f35-b207-258fd697c5fd"),
            name = "Daily estradiol",
            time = LocalTime.of(9, 0),
            notificationsEnabled = true
        )
        val now = LocalDateTime.of(2026, 4, 20, 8, 30)
        val fulfilledEntries = (0 until 32).map { dayOffset ->
            val scheduledFor = LocalDateTime.of(
                LocalDate.of(2026, 4, 20).plusDays(dayOffset.toLong()),
                LocalTime.of(9, 0)
            )
            scheduledEntry(
                groupUuid = group.uuid,
                appliedAt = scheduledFor.plusMinutes(5),
                scheduledFor = scheduledFor
            )
        }

        val plans = plansAt(
            groups = listOf(group),
            entries = fulfilledEntries,
            now = now,
        )

        assertEquals(
            listOf(LocalDateTime.of(2026, 5, 22, 9, 0)),
            plans.map { it.scheduledAt }
        )
    }

    @Test
    fun buildNextMedicationReminderPlans_returns_empty_when_no_future_unfulfilled_occurrence_exists() {
        // Weekly on no day: no dose, however far it looks.
        val group = medicationGroup(
            uuid = UUID.fromString("8d49d0f2-7daf-458f-9995-9a2b5db32ce0"),
            name = "Weekly estradiol",
            time = LocalTime.of(9, 0),
            notificationsEnabled = true
        ).let { group ->
            group.copy(schedule = group.schedule.copy(type = MedicationGroupScheduleType.WEEKLY))
        }

        val plans = plansAt(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 20, 10, 0),
        )

        assertEquals(emptyList<MedicationReminderPlan>(), plans)
    }

    @Test
    fun buildNextMedicationReminderPlans_uses_next_selected_weekday_for_weekly_multi_day_group() {
        val group = MedicationGroup(
            uuid = UUID.fromString("548ee741-c2db-4126-a79d-a761131f8be9"),
            name = "Twice weekly estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 1,
                since = LocalDate.of(2026, 4, 14),
                weeklyDaysOfWeek = setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.THURSDAY),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("56f1a6b4-dcdb-448b-8f08-9cb5746206d5"),
                    medicine = testMedicine(
                        uuid = estradiolMedicineUuid,
                        key = MedicationKey.ESTRADIOL,
                    ),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                )
            ),
            notificationsEnabled = true,
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )

        val plans = plansAt(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 21, 8, 0),
        )

        assertEquals(
            listOf(LocalDateTime.of(2026, 4, 23, 9, 0)),
            plans.map { it.scheduledAt }
        )
    }

    @Test
    fun buildNextMedicationReminderPlans_follows_each_plans_own_day_ending() {
        // Both weekly on Monday at 01:00. The 04:00 plan's Monday 01:00 falls on
        // calendar Tuesday, the early hours of its Monday; the midnight plan's
        // stays on calendar Monday.
        fun weeklyMondayGroup(uuid: UUID, dayBoundary: MedicationDayBoundary) = medicationGroup(
            uuid = uuid,
            name = dayBoundary.name,
            time = LocalTime.of(1, 0),
            notificationsEnabled = true,
        ).let { group ->
            group.copy(
                schedule = group.schedule.copy(
                    type = MedicationGroupScheduleType.WEEKLY,
                    since = LocalDate.of(2026, 10, 26),
                    weeklyDaysOfWeek = setOf(java.time.DayOfWeek.MONDAY),
                    dayBoundary = dayBoundary,
                ),
            )
        }
        val nightOwl = weeklyMondayGroup(
            UUID.fromString("2f0c6a51-7d3e-4b8a-9c1d-0e5f6a7b8c9d"),
            MedicationDayBoundary.FourAm,
        )
        val midnight = weeklyMondayGroup(
            UUID.fromString("8a9b0c1d-2e3f-4a5b-8c6d-7e8f9a0b1c2d"),
            MedicationDayBoundary.Midnight,
        )

        val plans = plansAt(
            groups = listOf(nightOwl, midnight),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 11, 2, 0, 30),
        )

        assertEquals(
            mapOf(
                nightOwl.uuid to LocalDateTime.of(2026, 11, 3, 1, 0),
                midnight.uuid to LocalDateTime.of(2026, 11, 2, 1, 0),
            ),
            plans.associate { plan -> plan.groupUuid to plan.scheduledAt },
        )
    }

    @Test
    fun buildNextMedicationReminderPlans_arms_the_next_dose_however_far_apart_the_doses_are() {
        // A 13-week depot as every 91 days, and one every 14 weeks: each just dosed.
        val depot = medicationGroup(
            uuid = UUID.fromString("3a7e1c52-9b04-4d6f-8e21-5c0d9f4b7a13"),
            name = "Depot",
            time = LocalTime.of(9, 0),
            notificationsEnabled = true,
        ).let { group -> group.copy(schedule = group.schedule.copy(interval = 91)) }
        val fortnightly = medicationGroup(
            uuid = UUID.fromString("4b8f2d63-0c15-4e7a-9f32-6d1e0a5c8b24"),
            name = "Every 14 weeks",
            time = LocalTime.of(9, 0),
            notificationsEnabled = true,
        ).let { group ->
            group.copy(
                schedule = group.schedule.copy(
                    type = MedicationGroupScheduleType.WEEKLY,
                    interval = 14,
                    weeklyDaysOfWeek = setOf(java.time.DayOfWeek.WEDNESDAY),
                ),
            )
        }

        val plans = plansAt(
            groups = listOf(depot, fortnightly),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 1, 10, 0),
        )

        assertEquals(
            mapOf(
                depot.uuid to LocalDateTime.of(2026, 7, 1, 9, 0),
                fortnightly.uuid to LocalDateTime.of(2026, 7, 8, 9, 0),
            ),
            plans.associate { plan -> plan.groupUuid to plan.scheduledAt },
        )
    }

    @Test
    fun buildNextMedicationReminderPlans_arms_the_first_dose_of_a_plan_starting_after_90_days() {
        val group = medicationGroup(
            uuid = UUID.fromString("5c9a3e74-1d26-4f8b-8a43-7e2f1b6d9c35"),
            name = "Starts next year",
            time = LocalTime.of(9, 0),
            notificationsEnabled = true,
        ).let { group -> group.copy(schedule = group.schedule.copy(since = LocalDate.of(2027, 1, 15))) }

        val plans = plansAt(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 20, 8, 0),
        )

        assertEquals(listOf(LocalDateTime.of(2027, 1, 15, 9, 0)), plans.map { it.scheduledAt })
    }

    @Test
    fun afterTheClocksGoBack_aDoseThatRangOnTheFirstPassIsNotArmedAgain() {
        // New York's clocks go back from 02:00 to 01:00 on Nov 1 2026, so 01:10 comes twice.
        val group = dailyAt(LocalTime.of(1, 30))
        fun planAt(clock: Instant) = buildNextMedicationReminderPlans(
            groups = listOf(group),
            entries = emptyList(),
            now = clock,
            repeats = ReminderRepeats(count = 0),
            zone = newYork,
        ).map { it.alertAt }

        // The first 01:10 (EDT): the 01:30 dose is ahead, on its first pass.
        assertEquals(listOf(Instant.parse("2026-11-01T05:30:00Z")), planAt(Instant.parse("2026-11-01T05:10:00Z")))
        // The second (EST): it rang already; tomorrow's 01:30 EST is next.
        assertEquals(listOf(Instant.parse("2026-11-02T06:30:00Z")), planAt(Instant.parse("2026-11-01T06:10:00Z")))
    }

    @Test
    fun aDoseInTheSpringForwardGap_ringsOnceTheClocksHaveSkipped_withItsRepeatsInRealTime() {
        // New York skips 02:00 to 03:00 on Mar 8 2026: a 02:30 dose's instant is 03:30 EDT.
        val group = dailyAt(LocalTime.of(2, 30))

        assertEquals(
            listOf("07:30", "07:45", "08:00", "08:15").map { Instant.parse("2026-03-08T${it}:00Z") },
            alertsAfter(group, Instant.parse("2026-03-08T06:00:00Z"), ReminderRepeats(3, 15), count = 4),
        )
        // The delivery of its alarm finds the alert, with repeats or without.
        listOf(ReminderRepeats(3, 15), ReminderRepeats(0, 15)).forEach { repeats ->
            val at = Instant.parse("2026-03-08T07:30:00Z")
            assertEquals(at, group.latestDueReminderAlert(at, at.plusSeconds(1), emptyList(), repeats, null, newYork)?.alertAt)
        }
    }

    @Test
    fun repeatsAcrossTheSpringForwardGap_eachRing() {
        // A 01:50 EST dose, 3 × 15 min: 01:50 EST, then 03:05, 03:20, 03:35 EDT.
        val group = dailyAt(LocalTime.of(1, 50))
        val alerts = alertsAfter(group, Instant.parse("2026-03-08T06:00:00Z"), ReminderRepeats(3, 15), count = 4)

        assertEquals(listOf("06:50", "07:05", "07:20", "07:35").map { Instant.parse("2026-03-08T${it}:00Z") }, alerts)
        // Each alarm, delivered on time, rings its own alert.
        alerts.forEach { at ->
            assertEquals(at, group.latestDueReminderAlert(at, at, emptyList(), ReminderRepeats(3, 15), null, newYork)?.alertAt)
        }
    }

    @Test
    fun hourlyRepeatsAcrossTheSpringForwardGap_areThreeAlerts() {
        // A 01:30 EST dose, 2 × 60 min: its 02:30 and 03:30 wall times would be one instant.
        val group = dailyAt(LocalTime.of(1, 30))

        assertEquals(
            listOf("06:30", "07:30", "08:30").map { Instant.parse("2026-03-08T${it}:00Z") },
            alertsAfter(group, Instant.parse("2026-03-08T06:00:00Z"), ReminderRepeats(2, 60), count = 3),
        )
    }

    @Test
    fun repeatsAcrossTheFallBack_areInRealTime() {
        // A 01:30 dose on Nov 1 2026, 3 × 15 min: 01:30 and 01:45 EDT, then 01:00 and 01:15 EST.
        val group = dailyAt(LocalTime.of(1, 30))

        assertEquals(
            listOf("05:30", "05:45", "06:00", "06:15").map { Instant.parse("2026-11-01T${it}:00Z") },
            alertsAfter(group, Instant.parse("2026-11-01T05:00:00Z"), ReminderRepeats(3, 15), count = 4),
        )
    }

    @Test
    fun aZoneChangeBetweenTwoRepeats_movesTheDoseToItsWallTimeInTheNewZone() {
        // A 09:00 dose rang in Tokyo (00:00 UTC); its 00:15 UTC repeat is armed. You land in London.
        val group = dailyAtNine()
        val london = ZoneId.of("Europe/London")
        val repeats = ReminderRepeats(3, 15)
        val tokyoRepeat = Instant.parse("2026-04-20T00:15:00Z")

        // In London that instant is no alert: its alarm posts nothing.
        assertNull(group.latestDueReminderAlert(tokyoRepeat, tokyoRepeat, emptyList(), repeats, null, london))
        // The next is the 09:00 dose in London, 08:00 UTC.
        assertEquals(
            Instant.parse("2026-04-20T08:00:00Z"),
            group.nextReminderAlert(tokyoRepeat, emptyList(), repeats, null, london)?.alertAt,
        )
    }

    @Test
    fun aDose_ringsAtItsTime_thenEveryIntervalUntilItsCap() {
        val group = dailyAtNine()
        val dose = LocalDateTime.of(2026, 4, 20, 9, 0)
        val repeats = ReminderRepeats(count = 3, intervalMinutes = 15)

        val alertTimes = generateSequence(dose.minusMinutes(1)) { after ->
            plansAt(listOf(group), emptyList(), now = after, repeats = repeats).single().scheduledAt
        }.drop(1).take(5).toList()

        // Four alerts for the 20th, then the 21st's dose.
        assertEquals(
            listOf(dose, dose.plusMinutes(15), dose.plusMinutes(30), dose.plusMinutes(45), dose.plusDays(1)),
            alertTimes,
        )
    }

    @Test
    fun repeatsOff_ringsOnce() {
        val plans = plansAt(
            listOf(dailyAtNine()),
            emptyList(),
            now = LocalDateTime.of(2026, 4, 20, 9, 0),
            repeats = ReminderRepeats(count = 0, intervalMinutes = 15),
        )

        assertEquals(listOf(LocalDateTime.of(2026, 4, 21, 9, 0)), plans.map { it.scheduledAt })
    }

    @Test
    fun aLoggedDose_stopsItsRepeats() {
        val group = dailyAtNine()
        val dose = LocalDateTime.of(2026, 4, 20, 9, 0)

        val plans = plansAt(
            listOf(group),
            listOf(scheduledEntry(group.uuid, appliedAt = dose.plusMinutes(5), scheduledFor = dose)),
            now = dose.plusMinutes(10),
            repeats = ReminderRepeats(count = 3, intervalMinutes = 15),
        )

        assertEquals(listOf(dose.plusDays(1)), plans.map { it.scheduledAt })
    }

    @Test
    fun aSkippedDose_stopsItsRepeats_andTheNextDoseStillRings() {
        val group = dailyAtNine()
        val dose = LocalDateTime.of(2026, 4, 20, 9, 0)

        val plans = plansAt(
            listOf(group),
            emptyList(),
            now = dose.plusMinutes(10),
            repeats = ReminderRepeats(count = 3, intervalMinutes = 15),
            skippedThrough = mapOf(group.uuid to dose),
        )

        assertEquals(listOf(dose.plusDays(1)), plans.map { it.scheduledAt })
    }

    @Test
    fun repeats_stopAtThePlansNextDose_evenBeforeTheirCap() {
        // Doses an hour apart, repeats every 30 minutes: the 09:00 dose rings at 09:00 and 09:30
        // only; its 10:00 and 10:30 repeats would land on or past the 10:00 dose.
        val group = medicationGroup(
            uuid = UUID.fromString("7a3e9c1d-5b2f-4e8a-9d6c-1f0b2a3c4d5e"),
            name = "Hourly",
            time = LocalTime.of(9, 0),
            notificationsEnabled = true,
        ).let { base ->
            base.copy(
                schedule = MedicationGroupSchedule(
                    type = MedicationGroupScheduleType.DAILY,
                    interval = 1,
                    since = LocalDate.of(2026, 4, 1),
                    weeklyDaysOfWeek = emptySet(),
                    times = listOf(LocalTime.of(9, 0), LocalTime.of(10, 0)),
                )
            )
        }
        val repeats = ReminderRepeats(count = 4, intervalMinutes = 30)
        val nine = LocalDateTime.of(2026, 4, 20, 9, 0)

        val alerts = group.reminderAlerts(dosesFrom = nine, entries = emptyList(), repeats = repeats, skippedThrough = null, zone = utc)
            .take(3).toList()

        assertEquals(
            listOf(
                ReminderAlert(doseAt = nine, alertAt = nine.toInstant(ZoneOffset.UTC)),
                ReminderAlert(doseAt = nine, alertAt = nine.plusMinutes(30).toInstant(ZoneOffset.UTC)),
                ReminderAlert(doseAt = nine.plusHours(1), alertAt = nine.plusHours(1).toInstant(ZoneOffset.UTC)),
            ),
            alerts,
        )
    }

    @Test
    fun anArmedAlert_isComparedAsAnInstant() {
        // After a zone change the armed alert's instant, not its old local time, says whether it
        // has passed: 09:00 in Tokyo is 00:00 UTC, still ahead at 23:00 UTC the day before.
        val group = dailyAtNine()
        val armed = Instant.parse("2026-04-20T00:00:00Z")

        val plans = buildNextMedicationReminderPlans(
            groups = listOf(group),
            entries = emptyList(),
            now = Instant.parse("2026-04-19T23:00:00Z"),
            armed = mapOf(group.uuid to armed),
            zone = utc,
        )

        // Not taken as passed, so planned afresh in the new zone.
        assertEquals(listOf(Instant.parse("2026-04-20T09:00:00Z")), plans.map { it.alertAt })
    }

    @Test
    fun latestDueReminderAlert_isTheLatestOneSinceTheArmedAlert() {
        val group = dailyAtNine()
        val dose = Instant.parse("2026-04-20T09:00:00Z")
        val repeats = ReminderRepeats(count = 3, intervalMinutes = 15)

        val alert = group.latestDueReminderAlert(dose, dose.plusSeconds(40 * 60), emptyList(), repeats, null, utc)

        assertEquals(dose.plusSeconds(30 * 60), alert?.alertAt)
    }

    @Test
    fun latestDueReminderAlert_ignoresAlertsBeforeTheArmedOne() {
        // Armed past the dose's last repeat (09:45): the alarm rings nothing from before it.
        val group = dailyAtNine()
        val since = Instant.parse("2026-04-20T09:46:00Z")

        assertNull(
            group.latestDueReminderAlert(since, since.plusSeconds(44 * 60), emptyList(), ReminderRepeats(3, 15), null, utc)
        )
    }

    private val newYork: ZoneId = ZoneId.of("America/New_York")
    private val utc: ZoneId = ZoneOffset.UTC

    /** The first [count] alerts of [group] after [after], walking the planner as each rings. */
    private fun alertsAfter(group: MedicationGroup, after: Instant, repeats: ReminderRepeats, count: Int): List<Instant> =
        generateSequence(after) { at -> group.nextReminderAlert(at, emptyList(), repeats, null, newYork)?.alertAt }
            .drop(1).take(count).toList()

    private fun dailyAt(time: LocalTime) = medicationGroup(
        uuid = UUID.fromString("3c5e7a91-2b4d-4f6a-8c0e-1d3f5b7a9c2e"),
        name = "Night estradiol",
        time = time,
        notificationsEnabled = true,
    ).let { base ->
        base.copy(
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 1, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(time),
            )
        )
    }

    private fun dailyAtNine() = medicationGroup(
        uuid = UUID.fromString("2b4d6f8a-1c3e-4a5b-9d7f-0e2c4a6b8d1f"),
        name = "Estradiol",
        time = LocalTime.of(9, 0),
        notificationsEnabled = true,
    )

    /** The planner in the default zone, with the clock at [now] and repeats off unless given. */
    private fun plansAt(
        groups: List<MedicationGroup>,
        entries: List<MedicationLogEntry>,
        now: LocalDateTime,
        armed: Map<UUID, LocalDateTime> = emptyMap(),
        repeats: ReminderRepeats = ReminderRepeats(count = 0),
        skippedThrough: Map<UUID, LocalDateTime> = emptyMap(),
    ) = buildNextMedicationReminderPlans(
        groups = groups,
        entries = entries,
        now = now.atZone(ZoneId.systemDefault()).toInstant(),
        armed = armed.mapValues { (_, at) -> at.atZone(ZoneId.systemDefault()).toInstant() },
        repeats = repeats,
        skippedThrough = skippedThrough,
    )

    private val MedicationReminderPlan.scheduledAt: LocalDateTime
        get() = LocalDateTime.ofInstant(alertAt, ZoneId.systemDefault())

    private fun medicationGroup(
        uuid: UUID,
        name: String,
        time: LocalTime,
        notificationsEnabled: Boolean
    ): MedicationGroup {
        return MedicationGroup(
            uuid = uuid,
            name = name,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(time)
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("56f1a6b4-dcdb-448b-8f08-9cb5746206d5"),
                    medicine = testMedicine(
                        uuid = estradiolMedicineUuid,
                        key = MedicationKey.ESTRADIOL,
                    ),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                )
            ),
            notificationsEnabled = notificationsEnabled,
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
    }

    private fun scheduledEntry(
        groupUuid: UUID,
        appliedAt: LocalDateTime,
        scheduledFor: LocalDateTime
    ): MedicationLogEntry {
        return testMedicationLogEntry(
            uuid = UUID.randomUUID(),
            medicine = testMedicine(
                uuid = estradiolMedicineUuid,
                key = MedicationKey.ESTRADIOL,
            ),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            equivalentE2Mg = 2.0,
            sourceGroupUuid = groupUuid,
            appliedAt = testInstant(appliedAt),
            scheduledFor = scheduledFor
        )
    }
}
