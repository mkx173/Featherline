package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockState
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class MedicineStockRepositoryTest {

    private lateinit var repository: MedicineStockRepository

    private val medicineUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val groupUuid = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @Before
    fun setUp() {
        repository = MedicineStockRepository(
            medicineRepository = mockk(relaxed = true),
            medicationGroupRepository = mockk(relaxed = true),
        )
    }

    @Test
    fun untrackedMedicineWithStock_returnsUntrackedState() {
        val medicine = pill(
            MedicineStock(
                trackingEnabled = false,
                unitsRemaining = 30.0,
                unitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                generation = 0L,
            )
        )

        val projection = repository.projectAll(
            medicines = listOf(medicine),
            activeGroups = listOf(dailyGroup(medicine)),
        ).single()

        assertEquals(MedicineStockState.UNTRACKED, projection.state)
        assertEquals(0.0, projection.totalStockUnits, 0.0)
        assertNull(projection.runwayDays)
    }

    @Test
    fun trackedNoGroups_returnsNoRunway() {
        val medicine = pill(
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 30.0,
                unitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                generation = 1L,
            )
        )

        val projection = repository.projectAll(listOf(medicine), emptyList()).single()

        assertEquals(MedicineStockState.NO_RUNWAY, projection.state)
        assertNull(projection.runwayDays)
    }

    @Test
    fun trackedHealthy_1PerDay_30Tabs_yields30Days() {
        val medicine = pill(
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 30.0,
                unitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                generation = 1L,
            )
        )

        val projection = repository.projectAll(
            medicines = listOf(medicine),
            activeGroups = listOf(dailyGroup(medicine)),
        ).single()

        assertEquals(1.0, projection.dosesPerDayMagnitude, 1e-9)
        assertEquals(30.0, projection.runwayDays!!, 1e-9)
        assertEquals(MedicineStockState.HEALTHY, projection.state)
    }

    @Test
    fun trackedLow_runwayAtThreshold() {
        val medicine = pill(
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 14.0,
                unitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                generation = 1L,
            )
        )

        val projection = repository.projectAll(
            medicines = listOf(medicine),
            activeGroups = listOf(dailyGroup(medicine)),
        ).single()

        assertEquals(MedicineStockState.LOW, projection.state)
    }

    @Test
    fun trackedOut_zeroStock() {
        val medicine = pill(
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 0.0,
                unitsLastTotal = 30.0,
                openContainerAmount = null,
                warnAtDaysRemaining = 14,
                generation = 1L,
            )
        )

        val projection = repository.projectAll(
            medicines = listOf(medicine),
            activeGroups = listOf(dailyGroup(medicine)),
        ).single()

        assertEquals(MedicineStockState.OUT, projection.state)
    }

    @Test
    fun containerTotalIncludesOpenAmountAndSealedContainerCapacity() {
        val medicine = vial(
            MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 2.0,
                unitsLastTotal = null,
                openContainerAmount = 0.5,
                warnAtDaysRemaining = 14,
                generation = 1L,
            )
        )

        val projection = repository.projectAll(
            medicines = listOf(medicine),
            activeGroups = listOf(
                dailyGroup(
                    medicine = medicine,
                    applicationType = MedicationApplicationType.INJECTION,
                    doseInstruction = DoseInstruction.VolumeMl(0.5),
                )
            ),
        ).single()

        assertEquals(2.5, projection.totalStockUnits, 1e-9)
        assertEquals(0.5, projection.dosesPerDayMagnitude, 1e-9)
        assertEquals(5.0, projection.runwayDays!!, 1e-9)
    }

    private fun pill(stock: MedicineStock): Medicine {
        val preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0)
        return medicine(preparation = preparation, stock = stock)
    }

    private fun vial(stock: MedicineStock): Medicine {
        val preparation = MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = 20.0,
            vialVolumeMl = 1.0,
        )
        return medicine(preparation = preparation, stock = stock)
    }

    private fun medicine(
        preparation: MedicinePreparation,
        stock: MedicineStock,
    ): Medicine {
        return Medicine(
            uuid = medicineUuid,
            selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL_VALERATE),
            category = MedicationCategory.ESTRADIOL,
            preparation = preparation,
            displayName = null,
            identityKey = MedicineIdentityKey.catalog(MedicationKey.ESTRADIOL_VALERATE, preparation),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            archivedAt = null,
            displayDoseUnit = MedicineDisplayDoseUnit.MG,
            stock = stock,
        )
    }

    private fun dailyGroup(
        medicine: Medicine,
        applicationType: MedicationApplicationType = MedicationApplicationType.ORAL,
        doseInstruction: DoseInstruction = DoseInstruction.TabletFraction(1, 1),
        count: Int = 1,
    ): MedicationGroup {
        return MedicationGroup(
            uuid = groupUuid,
            name = "Morning",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 1, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0)),
            ),
            medications = listOf(
                MedicationGroupMedication(
                    uuid = UUID.fromString("33333333-3333-3333-3333-333333333333"),
                    medicine = medicine,
                    applicationType = applicationType,
                    doseInstruction = doseInstruction,
                    count = count,
                )
            ),
            notificationsEnabled = true,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            archivedAt = null,
            includePastScheduledSlots = false,
        )
    }
}
