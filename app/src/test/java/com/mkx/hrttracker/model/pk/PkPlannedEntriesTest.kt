package com.mkx.hrttracker.model.pk

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class PkPlannedEntriesTest {
    private val zoneId = ZoneId.systemDefault()

    @Test
    fun build_emptyInputs_returnsEmpty() {
        val result = buildEstradiolPkSimulationEntries(
            realEntries = emptyList(),
            activeGroups = emptyList(),
            now = LocalDateTime.of(2026, 5, 6, 10, 0),
            horizon = LocalDateTime.of(2026, 5, 20, 0, 0),
            zoneId = zoneId,
        )

        assertTrue(result.real.isEmpty())
        assertTrue(result.planned.isEmpty())
    }

    @Test
    fun build_horizonNotAfterNow_returnsRealEntriesUnchanged() {
        val now = LocalDateTime.of(2026, 5, 6, 10, 0)
        val real = listOf(realPastOralEntry(appliedAt = now.minusHours(1)))

        val result = buildEstradiolPkSimulationEntries(
            realEntries = real,
            activeGroups = listOf(dailyEstradiolGroup()),
            now = now,
            horizon = now,
            zoneId = zoneId,
        )

        assertEquals(real, result.real)
        assertTrue(result.planned.isEmpty())
    }

    @Test
    fun build_dailyEstradiolGroup_synthesizesEntryForEachFutureSlot() {
        val now = LocalDateTime.of(2026, 5, 6, 10, 0)
        val horizon = LocalDateTime.of(2026, 5, 10, 0, 0)
        val group = dailyEstradiolGroup(
            since = LocalDate.of(2026, 5, 1),
            time = LocalTime.of(8, 0),
        )

        val result = buildEstradiolPkSimulationEntries(
            realEntries = emptyList(),
            activeGroups = listOf(group),
            now = now,
            horizon = horizon,
            zoneId = zoneId,
        )

        // Expected future slots: May 7 8am, May 8 8am, May 9 8am. May 6 8am is
        // before now (10:00); May 10 0am equals horizon (strict isBefore).
        val expectedSlots = listOf(
            LocalDateTime.of(2026, 5, 7, 8, 0),
            LocalDateTime.of(2026, 5, 8, 8, 0),
            LocalDateTime.of(2026, 5, 9, 8, 0),
        )
        assertTrue(result.real.isEmpty())
        assertEquals(expectedSlots, result.planned.map { it.scheduledFor })
        assertTrue(result.planned.all { it.sourceGroupUuid == group.uuid })
        assertTrue(result.planned.all { it.scheduleTimeUuid == group.schedule.timeSlots.single().uuid })
    }

    @Test
    fun build_dailyEstradiolGroup_excludesSlotExactlyAtHorizon() {
        val now = LocalDateTime.of(2026, 5, 14, 12, 0)
        val horizon = LocalDateTime.of(2026, 5, 18, 0, 0)
        val group = dailyEstradiolGroup(
            since = LocalDate.of(2026, 5, 1),
            time = LocalTime.MIDNIGHT,
        )

        val result = buildEstradiolPkSimulationEntries(
            realEntries = emptyList(),
            activeGroups = listOf(group),
            now = now,
            horizon = horizon,
            zoneId = zoneId,
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 5, 15, 0, 0),
                LocalDateTime.of(2026, 5, 16, 0, 0),
                LocalDateTime.of(2026, 5, 17, 0, 0),
            ),
            result.planned.map { it.scheduledFor },
        )
    }

    @Test
    fun build_realEntryWithMatchingSlotTriple_suppressesVirtualForThatSlot() {
        val now = LocalDateTime.of(2026, 5, 6, 10, 0)
        val horizon = LocalDateTime.of(2026, 5, 9, 0, 0)
        val group = dailyEstradiolGroup(
            since = LocalDate.of(2026, 5, 1),
            time = LocalTime.of(8, 0),
        )
        val slotUuid = group.schedule.timeSlots.single().uuid
        val fulfilledSlot = LocalDateTime.of(2026, 5, 7, 8, 0)
        val real = listOf(
            testMedicationLogEntry(
                medicine = estradiolMedicine(),
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                sourceGroupUuid = group.uuid,
                appliedAt = testInstant(fulfilledSlot.plusMinutes(5)),
                scheduledFor = fulfilledSlot,
                scheduleTimeUuid = slotUuid,
            )
        )

        val result = buildEstradiolPkSimulationEntries(
            realEntries = real,
            activeGroups = listOf(group),
            now = now,
            horizon = horizon,
            zoneId = zoneId,
        )

        // Real entry stays, virtual for May 7 8am is suppressed, May 8 8am virtual
        // remains.
        assertEquals(real, result.real)
        assertEquals(
            listOf(LocalDateTime.of(2026, 5, 8, 8, 0)),
            result.planned.map { it.scheduledFor },
        )
    }

    @Test
    fun build_realEntryWithDifferentSlotTime_doesNotSuppressVirtual() {
        val now = LocalDateTime.of(2026, 5, 6, 10, 0)
        val horizon = LocalDateTime.of(2026, 5, 8, 0, 0)
        val group = dailyEstradiolGroup(
            since = LocalDate.of(2026, 5, 1),
            time = LocalTime.of(8, 0),
        )
        val slotUuid = group.schedule.timeSlots.single().uuid
        // Real entry has the same slot uuid but a different scheduledFor — must
        // not suppress the May 7 8am virtual.
        val real = listOf(
            testMedicationLogEntry(
                medicine = estradiolMedicine(),
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                sourceGroupUuid = group.uuid,
                appliedAt = testInstant(now.minusHours(2)),
                scheduledFor = LocalDateTime.of(2026, 5, 6, 8, 0),
                scheduleTimeUuid = slotUuid,
            )
        )

        val result = buildEstradiolPkSimulationEntries(
            realEntries = real,
            activeGroups = listOf(group),
            now = now,
            horizon = horizon,
            zoneId = zoneId,
        )

        assertEquals(real, result.real)
        assertEquals(1, result.planned.size)
        assertEquals(LocalDateTime.of(2026, 5, 7, 8, 0), result.planned.single().scheduledFor)
    }

    @Test
    fun build_manualRealEntry_doesNotSuppressAnyVirtual() {
        val now = LocalDateTime.of(2026, 5, 6, 10, 0)
        val horizon = LocalDateTime.of(2026, 5, 8, 0, 0)
        val group = dailyEstradiolGroup(
            since = LocalDate.of(2026, 5, 1),
            time = LocalTime.of(8, 0),
        )
        // Manual entry — no sourceGroupUuid, no scheduleTimeUuid, no scheduledFor.
        val real = listOf(
            testMedicationLogEntry(
                medicine = estradiolMedicine(),
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                sourceGroupUuid = null,
                appliedAt = testInstant(now.minusHours(2)),
                scheduledFor = null,
            )
        )

        val result = buildEstradiolPkSimulationEntries(
            realEntries = real,
            activeGroups = listOf(group),
            now = now,
            horizon = horizon,
            zoneId = zoneId,
        )

        assertEquals(real, result.real)
        assertEquals(1, result.planned.size)
        assertEquals(LocalDateTime.of(2026, 5, 7, 8, 0), result.planned.single().scheduledFor)
    }

    @Test
    fun build_antiandrogenGroup_synthesizesNothing() {
        val now = LocalDateTime.of(2026, 5, 6, 10, 0)
        val horizon = LocalDateTime.of(2026, 5, 9, 0, 0)
        val group = MedicationGroup(
            uuid = UUID.fromString("8a0d3c8f-1d11-4d10-9c9c-e51d9b09a111"),
            name = "Spiro",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 5, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0)),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("0b0d3c8f-1d11-4d10-9c9c-e51d9b09a222"),
                    medicine = spironolactoneMedicine(),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                ),
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
        )

        val result = buildEstradiolPkSimulationEntries(
            realEntries = emptyList(),
            activeGroups = listOf(group),
            now = now,
            horizon = horizon,
            zoneId = zoneId,
        )

        assertTrue(result.real.isEmpty())
        assertTrue(result.planned.isEmpty())
    }

    @Test
    fun build_groupWithIdenticalSignatureMeds_collapsesToOneVirtualWithSummedCount() {
        // Two MedicationGroupMedication instances with the same details merge
        // into a single virtual entry whose count equals the summed plan.
        val now = LocalDateTime.of(2026, 5, 6, 10, 0)
        val horizon = LocalDateTime.of(2026, 5, 8, 0, 0)
        val medA = testMedicationGroupMedication(
            uuid = UUID.fromString("aaa3c8f0-1d11-4d10-9c9c-e51d9b09a000"),
            medicine = estradiolMedicine(),
            applicationType = MedicationApplicationType.SUBLINGUAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
        )
        val medB = testMedicationGroupMedication(
            uuid = UUID.fromString("bbb3c8f0-1d11-4d10-9c9c-e51d9b09a000"),
            medicine = estradiolMedicine(),
            applicationType = MedicationApplicationType.SUBLINGUAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
        )
        val group = MedicationGroup(
            uuid = UUID.fromString("ccc3c8f0-1d11-4d10-9c9c-e51d9b09a000"),
            name = "Two pills",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 5, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0)),
            ),
            medications = listOf(medA, medB),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
        )

        val result = buildEstradiolPkSimulationEntries(
            realEntries = emptyList(),
            activeGroups = listOf(group),
            now = now,
            horizon = horizon,
            zoneId = zoneId,
        )

        assertTrue(result.real.isEmpty())
        assertEquals(1, result.planned.size)
        val virtual = result.planned.single()
        assertEquals(LocalDateTime.of(2026, 5, 7, 8, 0), virtual.scheduledFor)
        assertEquals(2, virtual.count)
    }

    @Test
    fun build_realEntryMatchingOneSignature_leavesOtherSignatureVirtual() {
        // A group with two different estradiol medications in the same slot:
        // logging one must NOT suppress the planned dose for the other. This
        // is the case the slot-only SlotKey approach got wrong.
        val now = LocalDateTime.of(2026, 5, 6, 10, 0)
        val horizon = LocalDateTime.of(2026, 5, 8, 0, 0)
        val groupUuid = UUID.fromString("ccc3c8f0-1d11-4d10-9c9c-e51d9b09a000")
        val oralMed = testMedicationGroupMedication(
            uuid = UUID.fromString("11111111-1d11-4d10-9c9c-e51d9b09a000"),
            medicine = estradiolMedicine(),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
        )
        val sublingualMed = testMedicationGroupMedication(
            uuid = UUID.fromString("22222222-1d11-4d10-9c9c-e51d9b09a000"),
            medicine = estradiolMedicine(),
            applicationType = MedicationApplicationType.SUBLINGUAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
        )
        val group = MedicationGroup(
            uuid = groupUuid,
            name = "Oral + sublingual",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 5, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0)),
            ),
            medications = listOf(oralMed, sublingualMed),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
        )

        // Log the oral dose for tomorrow's 8am slot — sublingual stays planned.
        val slotUuid = group.schedule.timeSlots.single().uuid
        val fulfilledSlot = LocalDateTime.of(2026, 5, 7, 8, 0)
        val real = listOf(
            testMedicationLogEntry(
                medicine = oralMed.medicine,
                applicationType = oralMed.applicationType,
                doseInstruction = oralMed.doseInstruction,
                sourceGroupUuid = group.uuid,
                appliedAt = testInstant(fulfilledSlot),
                scheduledFor = fulfilledSlot,
                scheduleTimeUuid = slotUuid,
            )
        )

        val result = buildEstradiolPkSimulationEntries(
            realEntries = real,
            activeGroups = listOf(group),
            now = now,
            horizon = horizon,
            zoneId = zoneId,
        )

        assertEquals(real, result.real)
        assertEquals(1, result.planned.size)
        val remaining = result.planned.single()
        assertEquals(MedicationApplicationType.SUBLINGUAL, remaining.applicationType)
        assertEquals(fulfilledSlot, remaining.scheduledFor)
        assertEquals(1, remaining.count)
    }

    @Test
    fun build_virtualEntriesHaveStableUuidsAcrossInvocations() {
        val now = LocalDateTime.of(2026, 5, 6, 10, 0)
        val horizon = LocalDateTime.of(2026, 5, 8, 0, 0)
        val group = dailyEstradiolGroup()

        val first = buildEstradiolPkSimulationEntries(
            realEntries = emptyList(),
            activeGroups = listOf(group),
            now = now,
            horizon = horizon,
            zoneId = zoneId,
        )
        val second = buildEstradiolPkSimulationEntries(
            realEntries = emptyList(),
            activeGroups = listOf(group),
            now = now,
            horizon = horizon,
            zoneId = zoneId,
        )

        assertEquals(first.planned.map { it.uuid }, second.planned.map { it.uuid })
    }

    @Test
    fun projection_includesFutureScheduledDose_inPredictionZone() {
        // Integration check: a daily 8am estradiol group with no past doses
        // should still produce a non-zero predicted concentration after `now`.
        val now = LocalDateTime.of(2026, 5, 6, 10, 0)
        val zoneId = ZoneId.systemDefault()
        val group = dailyEstradiolGroup(
            since = LocalDate.of(2026, 5, 1),
            time = LocalTime.of(8, 0),
        )

        val simulationEntries = buildEstradiolPkSimulationEntries(
            realEntries = emptyList(),
            activeGroups = listOf(group),
            now = now,
            horizon = now.toLocalDate().plusDays(14).atStartOfDay(),
            zoneId = zoneId,
        )
        val projection = PkMedicationSimulation.simulateMainEstradiolProjection(
            entries = simulationEntries.real,
            plannedEntries = simulationEntries.planned,
            bodyWeightKg = 70.0,
            generatedAt = now,
            zoneId = zoneId,
            futureDays = 14,
        )

        // windowStart = today - 3 days = May 3 at 0:00.
        // First synthesized slot is May 7 at 8am — that's
        // (May 7 8am - May 3 0am) = 4*24 + 8 = 104 hours from windowStart.
        // Check that concentration at t=110h (a couple hours after the dose) is
        // strictly higher than at t=100h (just before the dose), proving the
        // future dose contributes to the prediction curve.
        val before = projection.concentrationAtOrZero(100.0)
        val after = projection.concentrationAtOrZero(110.0)
        assertTrue("expected $after > $before", after > before)
        // Every dose marker in the future-only scenario must be marked planned,
        // since there are no real entries to fulfill any slot.
        assertTrue(projection.doseMarkers.isNotEmpty())
        assertTrue(projection.doseMarkers.all { it.isPlanned })
    }

    @Test
    fun projection_realEntryFulfillingPlannedSlot_marksItLoggedNotPlanned() {
        // The user pre-logs a dose for an upcoming planned slot. The dose
        // marker at that time must be drawn as "logged" (isPlanned = false),
        // even though it falls inside the prediction zone.
        val now = LocalDateTime.of(2026, 5, 6, 10, 0)
        val zoneId = ZoneId.systemDefault()
        val group = dailyEstradiolGroup(
            since = LocalDate.of(2026, 5, 1),
            time = LocalTime.of(8, 0),
        )
        val slotUuid = group.schedule.timeSlots.single().uuid
        val fulfilledSlot = LocalDateTime.of(2026, 5, 7, 8, 0)
        val real = listOf(
            testMedicationLogEntry(
                medicine = estradiolMedicine(),
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                sourceGroupUuid = group.uuid,
                appliedAt = testInstant(fulfilledSlot),
                scheduledFor = fulfilledSlot,
                scheduleTimeUuid = slotUuid,
            )
        )

        val simulationEntries = buildEstradiolPkSimulationEntries(
            realEntries = real,
            activeGroups = listOf(group),
            now = now,
            horizon = now.toLocalDate().plusDays(3).atStartOfDay(),
            zoneId = zoneId,
        )
        val projection = PkMedicationSimulation.simulateMainEstradiolProjection(
            entries = simulationEntries.real,
            plannedEntries = simulationEntries.planned,
            bodyWeightKg = 70.0,
            generatedAt = now,
            zoneId = zoneId,
            futureDays = 3,
        )

        // The dose marker at the fulfilled slot's time must be logged (not planned).
        // windowStart = May 3 0:00, fulfilled slot = May 7 8am → t = 104h.
        val markerAtFulfilledSlot = projection.doseMarkers.minByOrNull {
            kotlin.math.abs(it.timeH - 104.0)
        }
        assertTrue("marker missing", markerAtFulfilledSlot != null)
        assertTrue(
            "fulfilled-slot marker must be logged, not planned",
            !markerAtFulfilledSlot!!.isPlanned,
        )
        // The next planned slot (May 8 8am, t = 128h) must still be marked planned.
        val markerAtNextSlot = projection.doseMarkers.minByOrNull {
            kotlin.math.abs(it.timeH - 128.0)
        }
        assertTrue(
            "next-slot marker must remain planned",
            markerAtNextSlot != null && markerAtNextSlot.isPlanned,
        )
    }

    private fun PkProjectionResult.concentrationAtOrZero(hour: Double): Double {
        // Use the same linear interpolation the snapshot path uses.
        if (timeH.isEmpty() || timeH.size != concentrations.size) return 0.0
        if (hour <= timeH.first()) return concentrations.first()
        if (hour >= timeH.last()) return concentrations.last()
        var low = 0
        var high = timeH.lastIndex
        while (high - low > 1) {
            val mid = (low + high) / 2
            when {
                timeH[mid] == hour -> return concentrations[mid]
                timeH[mid] < hour -> low = mid
                else -> high = mid
            }
        }
        val t0 = timeH[low]
        val t1 = timeH[high]
        val c0 = concentrations[low]
        val c1 = concentrations[high]
        if (t1 <= t0) return c0
        val ratio = (hour - t0) / (t1 - t0)
        return c0 + (c1 - c0) * ratio
    }

    private fun realPastOralEntry(appliedAt: LocalDateTime) =
        testMedicationLogEntry(
            medicine = estradiolMedicine(),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            sourceGroupUuid = null,
            appliedAt = testInstant(appliedAt),
        )

    private fun dailyEstradiolGroup(
        since: LocalDate = LocalDate.of(2026, 5, 1),
        time: LocalTime = LocalTime.of(8, 0),
    ): MedicationGroup {
        return MedicationGroup(
            uuid = UUID.fromString("11111111-2222-3333-4444-555555555555"),
            name = "Daily E2",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = since,
                weeklyDaysOfWeek = emptySet(),
                times = listOf(time),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa"),
                    medicine = estradiolMedicine(),
                    applicationType = MedicationApplicationType.ORAL,
                    // Pills carry their dose via TabletFraction;
                    // DoseInstructionCalculator returns null mg for
                    // Pill + WholeUnit, which would flatten the PK projection
                    // to zero and the integration assertions would fail.
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                ),
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
        )
    }

    // Single shared estradiol medicine with a stable uuid so equal medicines
    // across fixtures produce equal MedicationSignatures.
    private fun estradiolMedicine(): Medicine {
        return testMedicine(
            uuid = UUID.fromString("e2e2e2e2-0000-0000-0000-000000000000"),
            key = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
    }

    private fun spironolactoneMedicine(): Medicine {
        return testMedicine(
            uuid = UUID.fromString("5a5a5a5a-0000-0000-0000-000000000000"),
            key = MedicationKey.SPIRONOLACTONE,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 100.0),
        )
    }
}
