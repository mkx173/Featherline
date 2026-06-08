package com.mkx.hrttracker.ui.log

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.scheduleFulfillmentAllowedOffset
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.PostLogStockWarning
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationLogEntryViewModelTest {
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicineStockRepository: MedicineStockRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val dispatcher = StandardTestDispatcher()

    // Shared estradiol medicine with a fixed UUID so logged entries and the
    // group medications they fulfil resolve to the same MedicationSignature.
    private val estradiolMedicine = testMedicine(
        uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000001"),
        key = MedicationKey.ESTRADIOL,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { medicineStockRepository.observeProjections() } returns flowOf(emptyList())
        every { medicineStockRepository.getCachedProjections() } returns null
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun normalizeEditingEntryIds_filters_invalid_values_and_duplicates() {
        val entryId = UUID.fromString("3f77d0e1-76f2-4ba1-bbdf-61d3a0c2ed04")

        val normalizedIds = normalizeEditingEntryIds(
            listOf(
                entryId.toString(),
                "not-a-uuid",
                entryId.toString(),
                "4B2D6FD5-75EE-48FF-BFB4-0880B9894C03"
            )
        )

        assertEquals(
            listOf(
                entryId.toString(),
                UUID.fromString("4b2d6fd5-75ee-48ff-bfb4-0880b9894c03").toString()
            ),
            normalizedIds
        )
    }

    @Test
    fun buildEditingUiState_keeps_all_collapsed_duplicate_ids_and_schedule_metadata() {
        val appliedAt = LocalDateTime.of(2026, 4, 22, 21, 15)
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val firstId = UUID.fromString("59969483-c584-48ba-972a-8291e2ec4d55")
        val secondId = UUID.fromString("3b4dd714-6d0e-4293-b955-b89ab0b76386")
        val entries = listOf(
            testMedicationLogEntry(
                uuid = firstId,
                medicine = estradiolMedicine,
                applicationType = MedicationApplicationType.ORAL,
                equivalentE2Mg = 2.0,
                sourceGroupUuid = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6"),
                appliedAt = testInstant(appliedAt),
                scheduledFor = scheduledFor
            ),
            testMedicationLogEntry(
                uuid = secondId,
                medicine = estradiolMedicine,
                applicationType = MedicationApplicationType.ORAL,
                equivalentE2Mg = 2.0,
                sourceGroupUuid = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6"),
                appliedAt = testInstant(appliedAt),
                scheduledFor = scheduledFor
            )
        )

        val uiState = buildEditingUiState(entries)

        requireNotNull(uiState)
        assertEquals(listOf(firstId.toString(), secondId.toString()), uiState.editingEntryIds)
        assertEquals(scheduledFor, uiState.scheduledFor)
        assertEquals(LocalDate.of(2026, 4, 22), uiState.appliedDate)
        assertEquals(LocalTime.of(21, 15), uiState.appliedTime)
        assertEquals(1, uiState.count)
        assertTrue(uiState.isBulkEditing)
    }

    @Test
    fun buildEditingUiState_singleAdjustedInjectionLog_exposesReadOnlyDelta() {
        val medicine = testMedicine(
            preparation = MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = 40.0,
                vialVolumeMl = 5.0,
            ),
        )
        val entry = testMedicationLogEntry(
            medicine = medicine,
            applicationType = MedicationApplicationType.INJECTION,
            doseInstruction = DoseInstruction.VolumeMl(0.25),
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
        ).copy(doseAmountDelta = 0.1)

        val uiState = requireNotNull(buildEditingUiState(listOf(entry)))

        // Editing surfaces the frozen delta read-only; the interactive stepper
        // stays off (deltas are not re-editable after logging).
        assertEquals(0.1, uiState.doseAmountDelta!!, 1e-9)
        assertTrue(uiState.showActualDoseDeltaReadOnly)
        assertFalse(uiState.allowsActualDoseDelta)
        assertEquals(0.25, uiState.scheduledNativeAmount!!, 1e-9)
        assertEquals(0.35, uiState.effectiveActualAmount!!, 1e-9)
    }

    @Test
    fun buildEditingUiState_bulkEdit_dropsDeltaAndHidesReadOnlyLine() {
        val medicine = testMedicine(
            preparation = MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = 40.0,
                vialVolumeMl = 5.0,
            ),
        )
        val groupUuid = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15))
        val entries = listOf(
            testMedicationLogEntry(
                uuid = UUID.fromString("59969483-c584-48ba-972a-8291e2ec4d55"),
                medicine = medicine,
                applicationType = MedicationApplicationType.INJECTION,
                doseInstruction = DoseInstruction.VolumeMl(0.25),
                sourceGroupUuid = groupUuid,
                appliedAt = appliedAt,
                scheduledFor = scheduledFor,
            ).copy(doseAmountDelta = 0.1),
            testMedicationLogEntry(
                uuid = UUID.fromString("3b4dd714-6d0e-4293-b955-b89ab0b76386"),
                medicine = medicine,
                applicationType = MedicationApplicationType.INJECTION,
                doseInstruction = DoseInstruction.VolumeMl(0.25),
                sourceGroupUuid = groupUuid,
                appliedAt = appliedAt,
                scheduledFor = scheduledFor,
            ).copy(doseAmountDelta = 0.2),
        )

        val uiState = requireNotNull(buildEditingUiState(entries))

        // Bulk edits may span differing deltas, so neither the value nor the
        // read-only line is shown.
        assertTrue(uiState.isBulkEditing)
        assertNull(uiState.doseAmountDelta)
        assertFalse(uiState.showActualDoseDeltaReadOnly)
    }

    @Test
    fun buildEditingUiState_keeps_source_group_metadata_for_group_linked_entries() {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val entry = testMedicationLogEntry(
            medicine = estradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = groupId,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
            scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        )
        val sourceGroup = testMedicationGroup(
            groupId = groupId,
            name = "Nightly estradiol",
            colorKey = MedicationGroupColorKey.INDIGO
        )

        val uiState = buildEditingUiState(
            entries = listOf(entry),
            sourceGroup = sourceGroup
        )

        requireNotNull(uiState)
        assertEquals("Nightly estradiol", uiState.sourceGroupName)
        assertEquals(MedicationGroupColorKey.INDIGO, uiState.sourceGroupColorKey)
        assertFalse(uiState.sourceGroupIsArchived)
    }

    @Test
    fun buildEditingUiState_marks_sourceGroup_archived_from_live_group() {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val entry = testMedicationLogEntry(
            medicine = estradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = groupId,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
            scheduledFor = scheduledFor
        )
        val archivedGroup = testMedicationGroup(
            groupId = groupId,
            name = "Nightly estradiol",
            colorKey = MedicationGroupColorKey.INDIGO,
            archived = true,
        )

        val uiState = buildEditingUiState(
            entries = listOf(entry),
            sourceGroup = archivedGroup
        )

        requireNotNull(uiState)
        assertTrue(uiState.sourceGroupIsArchived)
    }

    @Test
    fun buildEditingUiState_uses_snapshot_group_metadata_when_sourceGroupIsUnavailable() {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val entry = testMedicationLogEntry(
            medicine = estradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = groupId,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
            scheduledFor = scheduledFor
        )

        val uiState = buildEditingUiState(
            entries = listOf(entry),
            sourceGroupName = "Snapshot estradiol",
            sourceGroupColorKey = MedicationGroupColorKey.PLUM,
            // The archived indicator must survive a cache miss / failed group
            // lookup, where only the row's snapshot metadata is available.
            sourceGroupIsArchived = true,
            sourceGroupPreviousScheduledFor = scheduledFor.minusDays(1),
            sourceGroupNextScheduledFor = scheduledFor.plusDays(1),
        )

        requireNotNull(uiState)
        assertEquals(groupId, uiState.sourceGroupUuid)
        assertEquals("Snapshot estradiol", uiState.sourceGroupName)
        assertEquals(MedicationGroupColorKey.PLUM, uiState.sourceGroupColorKey)
        assertTrue(uiState.sourceGroupIsArchived)
        assertEquals(scheduledFor.minusDays(1), uiState.sourceGroupPreviousScheduledFor)
        assertEquals(scheduledFor.plusDays(1), uiState.sourceGroupNextScheduledFor)
    }

    @Test
    fun buildEditingUiState_falls_back_to_single_entry_when_rows_do_not_match_exactly() {
        val firstId = UUID.fromString("58810b58-3176-428d-b361-e93e7e492a97")
        val secondId = UUID.fromString("693ecdb0-7414-41f2-b775-a79e7b1f2abf")
        val entries = listOf(
            testMedicationLogEntry(
                uuid = firstId,
                medicine = estradiolMedicine,
                applicationType = MedicationApplicationType.ORAL,
                equivalentE2Mg = 2.0,
                sourceGroupUuid = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6"),
                appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
                scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
            ),
            testMedicationLogEntry(
                uuid = secondId,
                medicine = estradiolMedicine,
                applicationType = MedicationApplicationType.ORAL,
                equivalentE2Mg = 2.0,
                sourceGroupUuid = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6"),
                appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 22, 0)),
                scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
            )
        )

        val uiState = buildEditingUiState(entries)

        requireNotNull(uiState)
        assertEquals(listOf(firstId.toString()), uiState.editingEntryIds)
        assertFalse(uiState.isBulkEditing)
    }

    @Test
    fun buildEditingUiState_preserves_existing_count_for_single_counted_entry() {
        val entry = testMedicationLogEntry(
            uuid = UUID.fromString("3ed5b4b7-fca2-4dff-ae06-e74cb15508a9"),
            medicine = estradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6"),
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
            scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0),
            count = 2
        )

        val uiState = buildEditingUiState(listOf(entry))

        requireNotNull(uiState)
        assertEquals(listOf(entry.uuid.toString()), uiState.editingEntryIds)
        assertEquals(2, uiState.count)
        assertFalse(uiState.isBulkEditing)
    }

    @Test
    fun buildEditingUiState_locks_count_editing_for_group_linked_entries() {
        val entry = testMedicationLogEntry(
            uuid = UUID.fromString("8b41b3a6-0d87-4f4f-94e2-d3f4a5b6c7d8"),
            medicine = estradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6"),
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
            count = 2
        )

        val uiState = buildEditingUiState(listOf(entry))

        requireNotNull(uiState)
        assertEquals(2, uiState.count)
    }

    @Test
    fun buildEditingUiState_coerces_unsupported_routes_to_count_one() {
        // GEL_CONTAINER is "one bottle dispenses N grams per dose" — count is
        // not a per-occurrence axis. (GEL_SACHET is the sibling preparation
        // where N sachets at once IS a real choice, so it now keeps the count.)
        val entry = testMedicationLogEntry(
            uuid = UUID.fromString("62f549eb-3870-4ce8-b476-6dd44759d78d"),
            medicine = testMedicine(
                key = MedicationKey.ESTRADIOL_GEL,
                preparation = MedicinePreparation.GelContainer(
                    concentrationPercent = 0.06,
                    containerWeightGrams = 80.0,
                ),
            ),
            applicationType = MedicationApplicationType.GEL,
            doseInstruction = DoseInstruction.WeightGrams(1.25),
            equivalentE2Mg = 5.0,
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
            count = 3
        )

        val uiState = buildEditingUiState(listOf(entry))

        requireNotNull(uiState)
        assertEquals(1, uiState.count)
    }

    @Test
    fun buildEditingUiState_preserves_count_for_gel_sachet() {
        // Per-occurrence "N sachets at once" is a real choice; the calculator
        // already multiplies the WholeUnit dose by count, so the editor
        // surfaces the saved value instead of coercing it to 1.
        val entry = testMedicationLogEntry(
            uuid = UUID.fromString("9c5a4e6d-8aaf-4f5c-9b41-f0a55b85d4fa"),
            medicine = testMedicine(
                key = MedicationKey.ESTRADIOL_GEL,
                preparation = MedicinePreparation.GelSachet(
                    concentrationPercent = 0.06,
                    sachetWeightGrams = 1.0,
                ),
            ),
            applicationType = MedicationApplicationType.GEL,
            doseInstruction = DoseInstruction.WholeUnit,
            equivalentE2Mg = 5.0,
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
            count = 3
        )

        val uiState = buildEditingUiState(listOf(entry))

        requireNotNull(uiState)
        assertEquals(3, uiState.count)
    }

    @Test
    fun medicationLogEntryUiState_allows_delete_only_while_editing() {
        assertFalse(MedicationLogEntryUiState().canDelete)
        assertTrue(
            MedicationLogEntryUiState(
                editingEntryIds = listOf(UUID.fromString("3885b7c7-45db-44ae-b512-429145f3bc6f").toString())
            ).canDelete
        )
    }

    @Test
    fun buildQuickLogUiState_uses_planned_dose_metadata() {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val group = testMedicationGroup(
            groupId = groupId,
            name = "Nightly estradiol",
            colorKey = MedicationGroupColorKey.INDIGO
        )
        val medicine = estradiolMedicine
        val doseInstruction = DoseInstruction.TabletFraction(1, 1)
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val appliedAt = LocalDateTime.of(2026, 4, 22, 21, 15, 30)

        val uiState = buildQuickLogUiState(
            groupId = groupId,
            group = group,
            scheduledFor = scheduledFor,
            medicine = medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = doseInstruction,
            medicationCount = 2,
            appliedAt = appliedAt
        )

        assertEquals(groupId, uiState.sourceGroupUuid)
        assertEquals("Nightly estradiol", uiState.sourceGroupName)
        assertEquals(MedicationGroupColorKey.INDIGO, uiState.sourceGroupColorKey)
        assertEquals(scheduledFor, uiState.scheduledFor)
        assertEquals(LocalDateTime.of(2026, 4, 21, 21, 0), uiState.sourceGroupPreviousScheduledFor)
        assertEquals(LocalDateTime.of(2026, 4, 23, 21, 0), uiState.sourceGroupNextScheduledFor)
        assertEquals(2, uiState.count)
        assertEquals(LocalDate.of(2026, 4, 22), uiState.appliedDate)
        assertEquals(LocalTime.of(21, 15), uiState.appliedTime)
        assertFalse(uiState.canDelete)
    }

    @Test
    fun buildQuickLogUiState_usesPatchOffPreparationForNullMedicinePatchOff() {
        val uiState = buildQuickLogUiState(
            groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6"),
            group = null,
            scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0),
            medicine = null,
            applicationType = MedicationApplicationType.PATCH_OFF,
            doseInstruction = DoseInstruction.Noop,
            medicationCount = 1,
            appliedAt = LocalDateTime.of(2026, 4, 22, 21, 15),
        )

        assertEquals(MedicinePreparationType.PATCH_OFF, uiState.doseInstructionDraft.preparationType)
    }

    @Test
    fun allowsActualDoseDelta_trueForMultiUseVialNewLog() {
        val uiState = buildQuickLogUiState(
            groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6"),
            group = null,
            scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0),
            medicine = testMedicine(
                preparation = MedicinePreparation.InjectionMultiUseVial(
                    concentrationMgPerMl = 40.0,
                    vialVolumeMl = 5.0,
                ),
            ),
            applicationType = MedicationApplicationType.INJECTION,
            doseInstruction = DoseInstruction.VolumeMl(0.2),
            medicationCount = 1,
            appliedAt = LocalDateTime.of(2026, 4, 22, 21, 15),
        )

        assertTrue(uiState.allowsActualDoseDelta)
        assertEquals(0.2, uiState.effectiveActualAmount ?: error("Missing effective actual amount"), 0.0)
    }

    @Test
    fun allowsActualDoseDelta_falseWhenEditing() {
        val entry = testMedicationLogEntry(
            medicine = testMedicine(
                preparation = MedicinePreparation.GelContainer(
                    concentrationPercent = 0.06,
                    containerWeightGrams = 80.0,
                ),
            ),
            applicationType = MedicationApplicationType.GEL,
            doseInstruction = DoseInstruction.WeightGrams(1.0),
            equivalentE2Mg = 0.6,
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
        )

        val uiState = buildEditingUiState(listOf(entry))

        requireNotNull(uiState)
        assertFalse(uiState.allowsActualDoseDelta)
        assertEquals(1.0, uiState.effectiveActualAmount ?: error("Missing effective actual amount"), 0.0)
    }

    @Test
    fun setDoseAmountDelta_clampsActualAboveZero() = runTest {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        every { medicationGroupRepository.getCachedGroup(groupId) } returns testMedicationGroup(
            groupId = groupId,
            name = "Nightly estradiol",
            colorKey = MedicationGroupColorKey.INDIGO,
        )
        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0),
            medicine = testMedicine(
                preparation = MedicinePreparation.InjectionMultiUseVial(
                    concentrationMgPerMl = 40.0,
                    vialVolumeMl = 5.0,
                ),
            ),
            applicationType = MedicationApplicationType.INJECTION,
            doseInstruction = DoseInstruction.VolumeMl(0.2),
            medicationCount = 1,
        )

        viewModel.setDoseAmountDelta(-999.0)

        val uiState = viewModel.uiState.value
        assertEquals(
            0.1,
            uiState.effectiveActualAmount ?: error("Missing effective actual amount"),
            1e-12,
        )
        assertEquals(
            -0.1,
            uiState.doseAmountDelta ?: error("Missing dose amount delta"),
            0.0,
        )
    }

    @Test
    fun setDoseAmountDelta_storesSanitizedDeltaForMeasuredForm() = runTest {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val medicine = testMedicine(
            preparation = MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = 40.0,
                vialVolumeMl = 5.0,
            ),
        )
        every { medicationGroupRepository.getCachedGroup(groupId) } returns testMedicationGroup(
            groupId = groupId,
            name = "Nightly estradiol",
            colorKey = MedicationGroupColorKey.INDIGO,
        )
        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduleTimeUuid = null,
            scheduledFor = scheduledFor,
            medicine = medicine,
            applicationType = MedicationApplicationType.INJECTION,
            doseInstruction = DoseInstruction.VolumeMl(0.5),
            medicationCount = 1,
        )
        advanceUntilIdle()

        viewModel.setDoseAmountDelta(0.05)
        assertEquals(0.05, viewModel.uiState.value.doseAmountDelta!!, 1e-9)

        viewModel.setDoseAmountDelta(0.0)
        assertNull(viewModel.uiState.value.doseAmountDelta)
    }

    @Test
    fun saveEntry_forMultiUseVialQuickLog_afterDoseDeltaPassesDelta() = runTest {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val medicine = testMedicine(
            preparation = MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = 40.0,
                vialVolumeMl = 5.0,
            ),
        )
        val doseInstruction = DoseInstruction.VolumeMl(0.2)
        every { medicationGroupRepository.getCachedGroup(groupId) } returns testMedicationGroup(
            groupId = groupId,
            name = "Nightly estradiol",
            colorKey = MedicationGroupColorKey.INDIGO,
        )
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.INJECTION,
                doseInstruction = doseInstruction,
                sourceGroupUuid = groupId,
                scheduleTimeUuid = null,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 1,
                appliedAtTimeZoneId = any(),
                doseAmountDelta = match { abs(it - 0.1) < 1e-12 },
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit
        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = scheduledFor,
            medicine = medicine,
            applicationType = MedicationApplicationType.INJECTION,
            doseInstruction = doseInstruction,
            medicationCount = 1,
        )
        advanceUntilIdle()
        viewModel.updateAppliedDate(scheduledFor.toLocalDate())
        viewModel.updateAppliedTime(scheduledFor.toLocalTime().plusMinutes(15))

        viewModel.setDoseAmountDelta(0.1)
        viewModel.saveEntry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.INJECTION,
                doseInstruction = doseInstruction,
                sourceGroupUuid = groupId,
                scheduleTimeUuid = null,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 1,
                appliedAtTimeZoneId = any(),
                doseAmountDelta = match { abs(it - 0.1) < 1e-12 },
            )
        }
    }

    @Test
    fun selectedStockProjection_staysFrozenDuringInFlightSave() = runTest {
        val entryId = UUID.fromString("fe0f5e91-9ad9-484d-9a21-46a74167e2f8")
        val entry = testMedicationLogEntry(
            uuid = entryId,
            medicine = estradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            equivalentE2Mg = 2.0,
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
        )
        val beforeSaveProjection = stockProjection(
            medicine = estradiolMedicine,
            unitsRemaining = 4.0,
        )
        val afterSaveProjection = stockProjection(
            medicine = estradiolMedicine.copy(
                stock = estradiolMedicine.stock.copy(unitsRemaining = 3.0),
            ),
            unitsRemaining = 3.0,
        )
        val stockProjections = MutableStateFlow(listOf(beforeSaveProjection))
        val saveStarted = CompletableDeferred<Unit>()
        val finishSave = CompletableDeferred<Unit>()
        every { medicineStockRepository.getCachedProjections() } returns listOf(beforeSaveProjection)
        every { medicineStockRepository.observeProjections() } returns stockProjections
        coEvery { medicationLogRepository.getEntries(listOf(entryId)) } returns listOf(entry)
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = entryId,
                medicineUuid = estradiolMedicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                sourceGroupUuid = null,
                scheduleTimeUuid = null,
                appliedAt = any(),
                scheduledFor = null,
                count = 1,
                appliedAtTimeZoneId = any(),
            )
        } coAnswers {
            saveStarted.complete(Unit)
            finishSave.await()
        }
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initialize(entryIds = listOf(entryId.toString()))
        advanceUntilIdle()
        assertEquals(beforeSaveProjection, viewModel.uiState.value.selectedStockProjection)

        viewModel.saveEntry()
        advanceUntilIdle()
        saveStarted.await()
        assertTrue(viewModel.uiState.value.isSaving)

        stockProjections.value = listOf(afterSaveProjection)
        advanceUntilIdle()

        assertEquals(beforeSaveProjection, viewModel.uiState.value.selectedStockProjection)

        finishSave.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(beforeSaveProjection, viewModel.uiState.value.selectedStockProjection)
    }

    @Test
    fun selectedStockProjection_staysFrozenDuringInFlightGroupLinkedSave() = runTest {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val scheduledFor = LocalDateTime.now().withSecond(0).withNano(0)
        val beforeSaveProjection = stockProjection(
            medicine = estradiolMedicine,
            unitsRemaining = 4.0,
        )
        val afterSaveProjection = stockProjection(
            medicine = estradiolMedicine.copy(
                stock = estradiolMedicine.stock.copy(unitsRemaining = 3.0),
            ),
            unitsRemaining = 3.0,
        )
        val stockProjections = MutableStateFlow(listOf(beforeSaveProjection))
        val saveStarted = CompletableDeferred<Unit>()
        val finishSave = CompletableDeferred<Unit>()
        every { medicineStockRepository.observeProjections() } returns stockProjections
        every { medicationGroupRepository.getCachedGroup(groupId) } returns testMedicationGroup(
            groupId = groupId,
            name = "Evening",
            colorKey = MedicationGroupColorKey.INDIGO,
        )
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = estradiolMedicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                sourceGroupUuid = groupId,
                scheduleTimeUuid = null,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 1,
                appliedAtTimeZoneId = any(),
            )
        } coAnswers {
            saveStarted.complete(Unit)
            finishSave.await()
        }

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = scheduledFor,
            medicine = estradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            medicationCount = 1,
        )
        advanceUntilIdle()

        assertEquals(beforeSaveProjection, viewModel.uiState.value.selectedStockProjection)

        viewModel.saveEntry()
        advanceUntilIdle()
        saveStarted.await()
        assertTrue(viewModel.uiState.value.isSaving)

        stockProjections.value = listOf(afterSaveProjection)
        advanceUntilIdle()

        assertEquals(beforeSaveProjection, viewModel.uiState.value.selectedStockProjection)

        finishSave.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(beforeSaveProjection, viewModel.uiState.value.selectedStockProjection)
    }

    @Test
    fun scheduleFulfillmentWindow_excludes_exact_previous_and_next_dose_offsets() {
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 9, 0)
        val previousScheduledFor = LocalDateTime.of(2026, 4, 22, 7, 0)
        val nextScheduledFor = LocalDateTime.of(2026, 4, 22, 13, 0)

        assertEquals(
            Duration.ofHours(2),
            scheduleFulfillmentAllowedOffset(
                scheduledFor = scheduledFor,
                adjacentScheduledFor = nextScheduledFor
            )
        )
        assertEquals(
            Duration.ofHours(1),
            scheduleFulfillmentAllowedOffset(
                scheduledFor = scheduledFor,
                adjacentScheduledFor = previousScheduledFor
            )
        )
        assertTrue(
            shouldWarnScheduleWillNotBeFulfilled(
                sourceGroupUuid = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6"),
                scheduledFor = scheduledFor,
                sourceGroupPreviousScheduledFor = previousScheduledFor,
                sourceGroupNextScheduledFor = nextScheduledFor,
                appliedAt = LocalDateTime.of(2026, 4, 22, 8, 0)
            )
        )
        assertFalse(
            shouldWarnScheduleWillNotBeFulfilled(
                sourceGroupUuid = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6"),
                scheduledFor = scheduledFor,
                sourceGroupPreviousScheduledFor = previousScheduledFor,
                sourceGroupNextScheduledFor = nextScheduledFor,
                appliedAt = LocalDateTime.of(2026, 4, 22, 8, 0, 1)
            )
        )
        assertTrue(
            shouldWarnScheduleWillNotBeFulfilled(
                sourceGroupUuid = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6"),
                scheduledFor = scheduledFor,
                sourceGroupPreviousScheduledFor = previousScheduledFor,
                sourceGroupNextScheduledFor = nextScheduledFor,
                appliedAt = LocalDateTime.of(2026, 4, 22, 7, 59)
            )
        )
        assertTrue(
            shouldWarnScheduleWillNotBeFulfilled(
                sourceGroupUuid = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6"),
                scheduledFor = scheduledFor,
                sourceGroupPreviousScheduledFor = previousScheduledFor,
                sourceGroupNextScheduledFor = nextScheduledFor,
                appliedAt = LocalDateTime.of(2026, 4, 22, 11, 0)
            )
        )
        assertFalse(
            shouldWarnScheduleWillNotBeFulfilled(
                sourceGroupUuid = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6"),
                scheduledFor = scheduledFor,
                sourceGroupPreviousScheduledFor = previousScheduledFor,
                sourceGroupNextScheduledFor = nextScheduledFor,
                appliedAt = LocalDateTime.of(2026, 4, 22, 10, 59, 59)
            )
        )
        assertTrue(
            shouldWarnScheduleWillNotBeFulfilled(
                sourceGroupUuid = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6"),
                scheduledFor = scheduledFor,
                sourceGroupPreviousScheduledFor = previousScheduledFor,
                sourceGroupNextScheduledFor = nextScheduledFor,
                appliedAt = LocalDateTime.of(2026, 4, 22, 11, 1)
            )
        )
    }

    @Test
    fun scheduleFulfillmentWindow_falls_back_to_next_dose_when_previous_is_missing() {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 9, 0)
        val nextScheduledFor = LocalDateTime.of(2026, 4, 22, 13, 0)

        assertTrue(
            shouldWarnScheduleWillNotBeFulfilled(
                sourceGroupUuid = groupId,
                scheduledFor = scheduledFor,
                sourceGroupPreviousScheduledFor = null,
                sourceGroupNextScheduledFor = nextScheduledFor,
                appliedAt = LocalDateTime.of(2026, 4, 22, 7, 0)
            )
        )
        assertFalse(
            shouldWarnScheduleWillNotBeFulfilled(
                sourceGroupUuid = groupId,
                scheduledFor = scheduledFor,
                sourceGroupPreviousScheduledFor = null,
                sourceGroupNextScheduledFor = nextScheduledFor,
                appliedAt = LocalDateTime.of(2026, 4, 22, 7, 0, 1)
            )
        )
        assertTrue(
            shouldWarnScheduleWillNotBeFulfilled(
                sourceGroupUuid = groupId,
                scheduledFor = scheduledFor,
                sourceGroupPreviousScheduledFor = null,
                sourceGroupNextScheduledFor = nextScheduledFor,
                appliedAt = LocalDateTime.of(2026, 4, 22, 6, 59)
            )
        )
    }

    @Test
    fun scheduleFulfillmentAllowedOffset_caps_at_twenty_four_hours() {
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 9, 0)

        assertEquals(
            Duration.ofHours(24),
            scheduleFulfillmentAllowedOffset(
                scheduledFor = scheduledFor,
                adjacentScheduledFor = LocalDateTime.of(2026, 4, 25, 9, 0)
            )
        )
    }

    @Test
    fun initializeQuickLog_uses_cached_group_metadata_immediately() = runTest {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val group = testMedicationGroup(
            groupId = groupId,
            name = "Nightly estradiol",
            colorKey = MedicationGroupColorKey.INDIGO
        )
        val medicine = estradiolMedicine
        val doseInstruction = DoseInstruction.TabletFraction(1, 1)
        every { medicationGroupRepository.getCachedGroup(groupId) } returns group

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0),
            medicine = medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = doseInstruction,
            medicationCount = 2
        )

        val uiState = viewModel.uiState.value
        assertFalse(uiState.isLoading)
        assertEquals(groupId, uiState.sourceGroupUuid)
        assertEquals("Nightly estradiol", uiState.sourceGroupName)
        assertEquals(2, uiState.count)
    }

    @Test
    fun initialize_uses_editSnapshotBeforeRoomEntryLoads() = runTest {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val entryId = UUID.fromString("59c02f09-381d-47df-8512-cf3af70d4eaf")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val entry = testMedicationLogEntry(
            uuid = entryId,
            medicine = estradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = groupId,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
            scheduledFor = scheduledFor,
        )
        coEvery { medicationLogRepository.getEntries(listOf(entryId)) } returns emptyList()

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initialize(
            entryIds = listOf(entryId.toString()),
            editSnapshot = MedicationLogEntryEditSnapshot(
                entries = listOf(entry),
                sourceGroupName = "Snapshot estradiol",
                sourceGroupColorKey = MedicationGroupColorKey.PLUM,
                sourceGroupPreviousScheduledFor = scheduledFor.minusDays(1),
                sourceGroupNextScheduledFor = scheduledFor.plusDays(1),
            ),
        )

        val initialState = viewModel.uiState.value
        assertTrue(initialState.isLoading)
        assertEquals(listOf(entryId.toString()), initialState.editingEntryIds)
        assertEquals(groupId, initialState.sourceGroupUuid)
        assertEquals("Snapshot estradiol", initialState.sourceGroupName)
        assertEquals(MedicationGroupColorKey.PLUM, initialState.sourceGroupColorKey)

        advanceUntilIdle()

        val fallbackState = viewModel.uiState.value
        assertFalse(fallbackState.isLoading)
        assertEquals("Snapshot estradiol", fallbackState.sourceGroupName)
        assertEquals(groupId, fallbackState.sourceGroupUuid)
    }

    @Test
    fun initializeQuickLog_uses_snapshot_group_metadata_beforeRepositoryGroupLoads() = runTest {
        val groupId = UUID.fromString("b6a391e2-c448-4d08-95a1-3451c7bf4060")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val previousScheduledFor = LocalDateTime.of(2026, 4, 21, 21, 0)
        val nextScheduledFor = LocalDateTime.of(2026, 4, 23, 21, 0)
        val medicine = estradiolMedicine
        val doseInstruction = DoseInstruction.TabletFraction(1, 1)
        every { medicationGroupRepository.getCachedGroup(groupId) } returns null
        coEvery { medicationGroupRepository.getGroup(groupId) } returns null

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = scheduledFor,
            medicine = medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = doseInstruction,
            medicationCount = 2,
            sourceGroupName = "Snapshot estradiol",
            sourceGroupColorKey = MedicationGroupColorKey.PLUM,
            sourceGroupPreviousScheduledFor = previousScheduledFor,
            sourceGroupNextScheduledFor = nextScheduledFor,
        )

        val initialState = viewModel.uiState.value
        assertFalse(initialState.isLoading)
        assertEquals(groupId, initialState.sourceGroupUuid)
        assertEquals("Snapshot estradiol", initialState.sourceGroupName)
        assertEquals(MedicationGroupColorKey.PLUM, initialState.sourceGroupColorKey)
        assertEquals(previousScheduledFor, initialState.sourceGroupPreviousScheduledFor)
        assertEquals(nextScheduledFor, initialState.sourceGroupNextScheduledFor)
        assertEquals(2, initialState.count)

        advanceUntilIdle()

        val resolvedState = viewModel.uiState.value
        assertFalse(resolvedState.isLoading)
        assertFalse(resolvedState.isSaved)
        assertEquals("Snapshot estradiol", resolvedState.sourceGroupName)
        assertEquals(MedicationGroupColorKey.PLUM, resolvedState.sourceGroupColorKey)
    }

    @Test
    fun saveEntry_whileLoading_defersWriteUntilRoomReturns() = runTest {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val entryId = UUID.fromString("59c02f09-381d-47df-8512-cf3af70d4eaf")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val medicine = estradiolMedicine
        val doseInstruction = DoseInstruction.TabletFraction(1, 1)
        val entry = testMedicationLogEntry(
            uuid = entryId,
            medicine = medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = doseInstruction,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = groupId,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
            scheduledFor = scheduledFor,
        )
        coEvery { medicationLogRepository.getEntries(listOf(entryId)) } returns listOf(entry)
        coEvery { medicationGroupRepository.getGroup(groupId) } returns null
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = entryId,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = doseInstruction,
                sourceGroupUuid = groupId,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 1,
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initialize(
            entryIds = listOf(entryId.toString()),
            editSnapshot = MedicationLogEntryEditSnapshot(
                entries = listOf(entry),
                sourceGroupName = "Snapshot estradiol",
                sourceGroupColorKey = MedicationGroupColorKey.PLUM,
            ),
        )
        viewModel.saveEntry()

        val queuedState = viewModel.uiState.value
        assertTrue(queuedState.isLoading)
        assertTrue(queuedState.isSaving)
        assertFalse(queuedState.isSaved)
        coVerify(exactly = 0) {
            medicationLogRepository.saveEntry(
                uuid = any(),
                medicineUuid = any(),
                applicationType = any(),
                doseInstruction = any(),
                sourceGroupUuid = any(),
                appliedAt = any(),
                scheduledFor = any(),
                count = any(),
            )
        }

        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertFalse(finalState.isSaving)
        assertTrue(finalState.isSaved)
        assertNull(finalState.saveEntryResult)
        coVerify(exactly = 1) {
            medicationLogRepository.saveEntry(
                uuid = entryId,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = doseInstruction,
                sourceGroupUuid = groupId,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 1,
            )
        }
    }

    @Test
    fun saveEntry_whileLoading_abortsWithFailureWhenEntryGoneFromRoom() = runTest {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val entryId = UUID.fromString("59c02f09-381d-47df-8512-cf3af70d4eaf")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val medicine = estradiolMedicine
        val doseInstruction = DoseInstruction.TabletFraction(1, 1)
        val entry = testMedicationLogEntry(
            uuid = entryId,
            medicine = medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = doseInstruction,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = groupId,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
            scheduledFor = scheduledFor,
        )
        coEvery { medicationLogRepository.getEntries(listOf(entryId)) } returns emptyList()

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initialize(
            entryIds = listOf(entryId.toString()),
            editSnapshot = MedicationLogEntryEditSnapshot(
                entries = listOf(entry),
                sourceGroupName = "Snapshot estradiol",
                sourceGroupColorKey = MedicationGroupColorKey.PLUM,
            ),
        )
        viewModel.saveEntry()
        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertFalse(finalState.isSaving)
        assertFalse(finalState.isSaved)
        assertEquals(SaveEntryResult.FAILURE, finalState.saveEntryResult)
        coVerify(exactly = 0) {
            medicationLogRepository.saveEntry(
                uuid = any(),
                medicineUuid = any(),
                applicationType = any(),
                doseInstruction = any(),
                sourceGroupUuid = any(),
                appliedAt = any(),
                scheduledFor = any(),
                count = any(),
            )
        }
    }

    @Test
    fun saveEntry_forQuickLog_whileLoading_defersWriteUntilGroupResolves() = runTest {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val group = testMedicationGroup(
            groupId = groupId,
            name = "Nightly estradiol",
            colorKey = MedicationGroupColorKey.INDIGO,
        )
        val medicine = estradiolMedicine
        val doseInstruction = DoseInstruction.TabletFraction(1, 1)
        every { medicationGroupRepository.getCachedGroup(groupId) } returns null
        coEvery { medicationGroupRepository.getGroup(groupId) } returns group
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = doseInstruction,
                sourceGroupUuid = groupId,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 2,
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = scheduledFor,
            medicine = medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = doseInstruction,
            medicationCount = 2,
        )
        viewModel.updateAppliedDate(scheduledFor.toLocalDate())
        viewModel.updateAppliedTime(scheduledFor.toLocalTime().plusMinutes(15))
        viewModel.saveEntry()

        val queuedState = viewModel.uiState.value
        assertTrue(queuedState.isLoading)
        assertTrue(queuedState.isSaving)
        assertFalse(queuedState.isSaved)
        coVerify(exactly = 0) {
            medicationLogRepository.saveEntry(
                uuid = any(),
                medicineUuid = any(),
                applicationType = any(),
                doseInstruction = any(),
                sourceGroupUuid = any(),
                appliedAt = any(),
                scheduledFor = any(),
                count = any(),
            )
        }

        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertFalse(finalState.isSaving)
        assertTrue(finalState.isSaved)
        coVerify(exactly = 1) {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = doseInstruction,
                sourceGroupUuid = groupId,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 2,
            )
        }
    }

    @Test
    fun saveEntry_forQuickLog_createsScheduledGroupEntry() = runTest {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val group = testMedicationGroup(
            groupId = groupId,
            name = "Nightly estradiol",
            colorKey = MedicationGroupColorKey.INDIGO
        )
        val medicine = estradiolMedicine
        val doseInstruction = DoseInstruction.TabletFraction(1, 1)
        every { medicationGroupRepository.getCachedGroup(groupId) } returns group
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = doseInstruction,
                sourceGroupUuid = groupId,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 2,
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = scheduledFor,
            medicine = medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = doseInstruction,
            medicationCount = 2
        )
        advanceUntilIdle()
        viewModel.updateAppliedDate(scheduledFor.toLocalDate())
        viewModel.updateAppliedTime(scheduledFor.toLocalTime().plusMinutes(15))

        viewModel.saveEntry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = doseInstruction,
                sourceGroupUuid = groupId,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 2,
            )
        }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun saveEntry_afterSuccessfulSaveSetsPostLogStockWarningWhenLoggedMedicineBecomesUserLow() = runTest {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val group = testMedicationGroup(
            groupId = groupId,
            name = "Nightly estradiol",
            colorKey = MedicationGroupColorKey.INDIGO
        )
        val medicine = estradiolMedicine
        val doseInstruction = DoseInstruction.TabletFraction(1, 1)
        every { medicationGroupRepository.getCachedGroup(groupId) } returns group
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = doseInstruction,
                sourceGroupUuid = groupId,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 1,
            )
        } returns Unit
        // Was healthy before this log (empty baseline); the post-log drop to
        // USER_LOW is what should surface the warning.
        coEvery { medicineStockRepository.projectAllOnce(any()) } returnsMany listOf(
            emptyList(),
            listOf(
                stockProjection(
                    medicine = medicine,
                    state = MedicineStockState.USER_LOW,
                )
            ),
        )
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = scheduledFor,
            medicine = medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = doseInstruction,
            medicationCount = 1
        )
        advanceUntilIdle()
        viewModel.updateAppliedDate(scheduledFor.toLocalDate())
        viewModel.updateAppliedTime(scheduledFor.toLocalTime().plusMinutes(15))

        viewModel.saveEntry()
        advanceUntilIdle()

        assertEquals(
            PostLogStockWarning.Single(medicine, MedicineStockState.USER_LOW),
            viewModel.uiState.value.postLogStockWarning,
        )

        viewModel.consumePostLogStockWarning()
        assertNull(viewModel.uiState.value.postLogStockWarning)
    }

    @Test
    fun saveEntry_afterHealthySaveLeavesPostLogStockWarningNull() = runTest {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val group = testMedicationGroup(
            groupId = groupId,
            name = "Nightly estradiol",
            colorKey = MedicationGroupColorKey.INDIGO
        )
        val medicine = estradiolMedicine
        val doseInstruction = DoseInstruction.TabletFraction(1, 1)
        every { medicationGroupRepository.getCachedGroup(groupId) } returns group
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = doseInstruction,
                sourceGroupUuid = groupId,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 1,
            )
        } returns Unit
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns listOf(
            stockProjection(
                medicine = medicine,
                state = MedicineStockState.HEALTHY,
            )
        )
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = scheduledFor,
            medicine = medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = doseInstruction,
            medicationCount = 1
        )
        advanceUntilIdle()
        viewModel.updateAppliedDate(scheduledFor.toLocalDate())
        viewModel.updateAppliedTime(scheduledFor.toLocalTime().plusMinutes(15))

        viewModel.saveEntry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertNull(viewModel.uiState.value.postLogStockWarning)
    }

    @Test
    fun saveEntry_editDoesNotComputePostLogStockWarning() = runTest {
        val entryId = UUID.fromString("8cc17f1e-3343-45dd-b3ce-5c8f20686f21")
        val entry = testMedicationLogEntry(
            uuid = entryId,
            medicine = estradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
        )
        coEvery { medicationLogRepository.getEntries(listOf(entryId)) } returns listOf(entry)
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = entryId,
                medicineUuid = estradiolMedicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                sourceGroupUuid = null,
                appliedAt = any(),
                scheduledFor = null,
                count = 1,
            )
        } returns Unit
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns listOf(
            stockProjection(
                medicine = estradiolMedicine,
                state = MedicineStockState.OUT,
            )
        )
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initialize(listOf(entryId.toString()))
        advanceUntilIdle()

        viewModel.saveEntry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertNull(viewModel.uiState.value.postLogStockWarning)
        coVerify(exactly = 0) { medicineStockRepository.projectAllOnce(any()) }
    }

    @Test
    fun saveEntry_bulkEditDoesNotComputePostLogStockWarning() = runTest {
        val firstEntryId = UUID.fromString("8cc17f1e-3343-45dd-b3ce-5c8f20686f22")
        val secondEntryId = UUID.fromString("8cc17f1e-3343-45dd-b3ce-5c8f20686f23")
        val entries = listOf(
            testMedicationLogEntry(
                uuid = firstEntryId,
                medicine = estradiolMedicine,
                applicationType = MedicationApplicationType.ORAL,
                sourceGroupUuid = null,
                appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
            ),
            testMedicationLogEntry(
                uuid = secondEntryId,
                medicine = estradiolMedicine,
                applicationType = MedicationApplicationType.ORAL,
                sourceGroupUuid = null,
                appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
            ),
        )
        coEvery { medicationLogRepository.getEntries(listOf(firstEntryId, secondEntryId)) } returns entries
        coEvery {
            medicationLogRepository.saveEntries(
                uuids = listOf(firstEntryId, secondEntryId),
                medicineUuid = estradiolMedicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                sourceGroupUuid = null,
                appliedAt = any(),
                scheduledFor = null,
                count = 1,
            )
        } returns Unit
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns listOf(
            stockProjection(
                medicine = estradiolMedicine,
                state = MedicineStockState.OUT,
            )
        )
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initialize(listOf(firstEntryId.toString(), secondEntryId.toString()))
        advanceUntilIdle()

        viewModel.saveEntry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertNull(viewModel.uiState.value.postLogStockWarning)
        coVerify(exactly = 0) { medicineStockRepository.projectAllOnce(any()) }
    }

    @Test
    fun saveEntry_whenPostLogStockProjectionFailsStillSavesAndLeavesWarningNull() = runTest {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val group = testMedicationGroup(
            groupId = groupId,
            name = "Nightly estradiol",
            colorKey = MedicationGroupColorKey.INDIGO
        )
        val medicine = estradiolMedicine
        val doseInstruction = DoseInstruction.TabletFraction(1, 1)
        every { medicationGroupRepository.getCachedGroup(groupId) } returns group
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = doseInstruction,
                sourceGroupUuid = groupId,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 1,
            )
        } returns Unit
        coEvery { medicineStockRepository.projectAllOnce(any()) } throws RuntimeException("projection failed")
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = scheduledFor,
            medicine = medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = doseInstruction,
            medicationCount = 1
        )
        advanceUntilIdle()
        viewModel.updateAppliedDate(scheduledFor.toLocalDate())
        viewModel.updateAppliedTime(scheduledFor.toLocalTime().plusMinutes(15))

        viewModel.saveEntry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertNull(viewModel.uiState.value.saveEntryResult)
        assertNull(viewModel.uiState.value.postLogStockWarning)
    }

    @Test
    fun saveEntry_whenPostLogStockProjectionIsCancelledRethrowsCancellation() = runTest {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val group = testMedicationGroup(
            groupId = groupId,
            name = "Nightly estradiol",
            colorKey = MedicationGroupColorKey.INDIGO
        )
        val medicine = estradiolMedicine
        val doseInstruction = DoseInstruction.TabletFraction(1, 1)
        val cancellation = CancellationException("projection cancelled")
        every { medicationGroupRepository.getCachedGroup(groupId) } returns group
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = doseInstruction,
                sourceGroupUuid = groupId,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 1,
            )
        } returns Unit
        coEvery { medicineStockRepository.projectAllOnce(any()) } throws cancellation
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = scheduledFor,
            medicine = medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = doseInstruction,
            medicationCount = 1
        )
        advanceUntilIdle()
        viewModel.updateAppliedDate(scheduledFor.toLocalDate())
        viewModel.updateAppliedTime(scheduledFor.toLocalTime().plusMinutes(15))

        viewModel.saveEntry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.isSaved)
        assertNull(viewModel.uiState.value.postLogStockWarning)
        coVerify(exactly = 0) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun saveEntry_forPatchOffQuickLogWithNullMedicineSavesPatchOffRoute() = runTest {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val group = testMedicationGroup(
            groupId = groupId,
            name = "Patch schedule",
            colorKey = MedicationGroupColorKey.INDIGO,
        )
        every { medicationGroupRepository.getCachedGroup(groupId) } returns group
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = null,
                applicationType = MedicationApplicationType.PATCH_OFF,
                doseInstruction = DoseInstruction.Noop,
                sourceGroupUuid = groupId,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 1,
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = scheduledFor,
            medicine = null,
            applicationType = MedicationApplicationType.PATCH_OFF,
            doseInstruction = DoseInstruction.Noop,
            medicationCount = 1,
        )
        advanceUntilIdle()
        viewModel.updateAppliedDate(scheduledFor.toLocalDate())
        viewModel.updateAppliedTime(scheduledFor.toLocalTime())

        viewModel.saveEntry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = null,
                applicationType = MedicationApplicationType.PATCH_OFF,
                doseInstruction = DoseInstruction.Noop,
                sourceGroupUuid = groupId,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 1,
            )
        }
    }

    @Test
    fun saveEntry_forFarQuickLog_showsFulfillmentWarning() = runTest {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 9, 0)
        val group = testMedicationGroup(
            groupId = groupId,
            name = "Morning estradiol",
            colorKey = MedicationGroupColorKey.INDIGO,
            times = listOf(LocalTime.of(9, 0), LocalTime.of(11, 0))
        )
        val medicine = estradiolMedicine
        val doseInstruction = DoseInstruction.TabletFraction(1, 1)
        every { medicationGroupRepository.getCachedGroup(groupId) } returns group

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = scheduledFor,
            medicine = medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = doseInstruction,
            medicationCount = 2
        )
        advanceUntilIdle()
        viewModel.updateAppliedDate(LocalDate.of(2026, 4, 22))
        viewModel.updateAppliedTime(LocalTime.of(10, 30))

        viewModel.saveEntry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isScheduleFulfillmentWarningVisible)
        assertFalse(viewModel.uiState.value.isSaved)
        coVerify(exactly = 0) {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = doseInstruction,
                sourceGroupUuid = any(),
                appliedAt = any(),
                scheduledFor = any(),
                count = any(),
            )
        }
    }

    @Test
    fun saveEntryAfterFulfillmentWarning_forFarQuickLog_keepsScheduledGroupEntry() = runTest {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 9, 0)
        val group = testMedicationGroup(
            groupId = groupId,
            name = "Morning estradiol",
            colorKey = MedicationGroupColorKey.INDIGO,
            times = listOf(LocalTime.of(9, 0), LocalTime.of(11, 0))
        )
        val medicine = estradiolMedicine
        val doseInstruction = DoseInstruction.TabletFraction(1, 1)
        every { medicationGroupRepository.getCachedGroup(groupId) } returns group
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = doseInstruction,
                sourceGroupUuid = groupId,
                scheduleTimeUuid = null,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 2,
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = scheduledFor,
            medicine = medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = doseInstruction,
            medicationCount = 2
        )
        advanceUntilIdle()
        viewModel.updateAppliedDate(LocalDate.of(2026, 4, 22))
        viewModel.updateAppliedTime(LocalTime.of(10, 30))

        viewModel.saveEntry()
        assertTrue(viewModel.uiState.value.isScheduleFulfillmentWarningVisible)
        viewModel.saveEntryAfterFulfillmentWarning()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) {
            medicationLogRepository.saveEntry(
                uuid = null,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = doseInstruction,
                sourceGroupUuid = groupId,
                scheduleTimeUuid = null,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 2,
            )
        }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun saveEntryAfterFulfillmentWarning_forFarEditedGroupEntry_keepsScheduledGroupEntry() = runTest {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val entryId = UUID.fromString("73ef6a29-2149-4f13-8b2c-7f4baf23e3a6")
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 9, 0)
        val medicine = estradiolMedicine
        val doseInstruction = DoseInstruction.TabletFraction(1, 1)
        val entry = testMedicationLogEntry(
            uuid = entryId,
            medicine = medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = doseInstruction,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = groupId,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 9, 15)),
            scheduledFor = scheduledFor
        )
        val group = testMedicationGroup(
            groupId = groupId,
            name = "Morning estradiol",
            colorKey = MedicationGroupColorKey.INDIGO,
            times = listOf(LocalTime.of(9, 0), LocalTime.of(11, 0))
        )
        coEvery { medicationLogRepository.getEntries(listOf(entryId)) } returns listOf(entry)
        coEvery { medicationGroupRepository.getGroup(groupId) } returns group
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = entryId,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = doseInstruction,
                sourceGroupUuid = groupId,
                scheduleTimeUuid = null,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 1,
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initialize(listOf(entryId.toString()))
        advanceUntilIdle()
        viewModel.updateAppliedDate(LocalDate.of(2026, 4, 22))
        viewModel.updateAppliedTime(LocalTime.of(10, 30))

        viewModel.saveEntry()
        assertTrue(viewModel.uiState.value.isScheduleFulfillmentWarningVisible)
        viewModel.saveEntryAfterFulfillmentWarning()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        coVerify(exactly = 1) {
            medicationLogRepository.saveEntry(
                uuid = entryId,
                medicineUuid = medicine.uuid,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = doseInstruction,
                sourceGroupUuid = groupId,
                scheduleTimeUuid = null,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 1,
            )
        }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun saveEntry_whenRepositoryFails_updatesUiStateWithFailureResult() = runTest {
        val entryId = UUID.fromString("8cc17f1e-3343-45dd-b3ce-5c8f20686f2d")
        val entry = testMedicationLogEntry(
            uuid = entryId,
            medicine = estradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15))
        )
        coEvery { medicationLogRepository.getEntries(listOf(entryId)) } returns listOf(entry)
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = entryId,
                medicineUuid = any(),
                applicationType = any(),
                doseInstruction = any(),
                sourceGroupUuid = null,
                appliedAt = any(),
                scheduledFor = null,
                count = 1,
            )
        } throws RuntimeException("save failed")

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initialize(listOf(entryId.toString()))
        advanceUntilIdle()

        viewModel.saveEntry()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.isSaved)
        assertEquals(SaveEntryResult.FAILURE, viewModel.uiState.value.saveEntryResult)

        viewModel.consumeSaveEntryResult()
        assertNull(viewModel.uiState.value.saveEntryResult)
        coVerify(exactly = 1) {
            medicationLogRepository.saveEntry(
                uuid = entryId,
                medicineUuid = any(),
                applicationType = any(),
                doseInstruction = any(),
                sourceGroupUuid = null,
                appliedAt = any(),
                scheduledFor = null,
                count = 1,
            )
        }
        coVerify(exactly = 0) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun saveEntry_whenSchedulerFails_marksEntrySaved() = runTest {
        val entryId = UUID.fromString("fa9154d0-af8d-44f8-a565-46d6708ebcf2")
        val entry = testMedicationLogEntry(
            uuid = entryId,
            medicine = estradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15))
        )
        coEvery { medicationLogRepository.getEntries(listOf(entryId)) } returns listOf(entry)
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = entryId,
                medicineUuid = any(),
                applicationType = any(),
                doseInstruction = any(),
                sourceGroupUuid = null,
                appliedAt = any(),
                scheduledFor = null,
                count = 1,
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } throws RuntimeException("schedule failed")

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initialize(listOf(entryId.toString()))
        advanceUntilIdle()

        viewModel.saveEntry()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.isSaved)
        assertNull(viewModel.uiState.value.saveEntryResult)
        coVerify(exactly = 1) {
            medicationLogRepository.saveEntry(
                uuid = entryId,
                medicineUuid = any(),
                applicationType = any(),
                doseInstruction = any(),
                sourceGroupUuid = null,
                appliedAt = any(),
                scheduledFor = null,
                count = 1,
            )
        }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun deleteEntry_whenRepositoryFails_updatesUiStateWithFailureResult() = runTest {
        val entryId = UUID.fromString("20de422b-b620-474f-b2d0-0e56389ebf74")
        val entry = testMedicationLogEntry(
            uuid = entryId,
            medicine = estradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15))
        )
        coEvery { medicationLogRepository.getEntries(listOf(entryId)) } returns listOf(entry)
        coEvery { medicationLogRepository.deleteEntries(listOf(entryId)) } throws RuntimeException("delete failed")

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initialize(listOf(entryId.toString()))
        advanceUntilIdle()

        viewModel.deleteEntry()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeleting)
        assertFalse(viewModel.uiState.value.isSaved)
        assertEquals(DeleteEntryResult.FAILURE, viewModel.uiState.value.deleteEntryResult)

        viewModel.consumeDeleteEntryResult()
        assertNull(viewModel.uiState.value.deleteEntryResult)
        coVerify(exactly = 1) { medicationLogRepository.deleteEntries(listOf(entryId)) }
        coVerify(exactly = 0) { medicationReminderScheduler.rescheduleAll(any()) }
    }

    @Test
    fun saveEntry_preserves_original_zone_when_editing_a_cross_zone_entry() = runTest(dispatcher) {
        // GIVEN: existing entry stored in Asia/Tokyo
        val entryId = UUID.randomUUID()
        val tokyo = ZoneId.of("Asia/Tokyo")
        val tokyoApplied = LocalDateTime.of(2026, 4, 15, 9, 0)
        val originalInstant = tokyoApplied.atZone(tokyo).toInstant()
        val existingEntry = testMedicationLogEntry(
            uuid = entryId,
            medicine = estradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            sourceGroupUuid = null,
            appliedAt = originalInstant,
            appliedAtTimeZoneId = "Asia/Tokyo"
        )
        coEvery { medicationLogRepository.getEntries(listOf(entryId)) } returns listOf(existingEntry)
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = any(),
                medicineUuid = any(),
                applicationType = any(),
                doseInstruction = any(),
                sourceGroupUuid = any(),
                scheduleTimeUuid = any(),
                appliedAt = any(),
                scheduledFor = any(),
                count = any(),
                appliedAtTimeZoneId = any()
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicineStockRepository = medicineStockRepository,
            medicationReminderScheduler = medicationReminderScheduler,
        )
        viewModel.initialize(listOf(entryId.toString()))
        advanceUntilIdle()

        // Verify state was loaded with Tokyo wall-clock
        val loadedState = viewModel.uiState.value
        assertEquals(tokyo, loadedState.appliedZoneId)
        assertEquals(LocalTime.of(9, 0), loadedState.appliedTime)
        assertEquals(LocalDate.of(2026, 4, 15), loadedState.appliedDate)

        // Bump time by +20 min and save
        viewModel.updateAppliedTime(LocalTime.of(9, 20))
        viewModel.saveEntry()
        advanceUntilIdle()

        // Verify the saved appliedAt and the preserved zone
        val expectedInstant = LocalDateTime.of(2026, 4, 15, 9, 20).atZone(tokyo).toInstant()
        coVerify {
            medicationLogRepository.saveEntry(
                uuid = entryId,
                medicineUuid = any(),
                applicationType = any(),
                doseInstruction = any(),
                sourceGroupUuid = null,
                scheduleTimeUuid = any(),
                appliedAt = expectedInstant,
                scheduledFor = any(),
                count = any(),
                appliedAtTimeZoneId = "Asia/Tokyo"
            )
        }
    }

    @Test
    fun saveEntry_emits_cross_zone_toast_payload_when_zone_differs_from_device() = runTest(dispatcher) {
        val entryId = UUID.randomUUID()
        // Use UTC-5/UTC-4 (New York) — guaranteed to differ from any Asia/Pacific device zone.
        val newYork = ZoneId.of("America/New_York")
        val originalInstant = LocalDateTime.of(2026, 4, 15, 9, 0).atZone(newYork).toInstant()
        val existingEntry = testMedicationLogEntry(
            uuid = entryId,
            medicine = estradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            sourceGroupUuid = null,
            appliedAt = originalInstant,
            appliedAtTimeZoneId = "America/New_York"
        )
        coEvery { medicationLogRepository.getEntries(listOf(entryId)) } returns listOf(existingEntry)
        every { medicationGroupRepository.getCachedGroup(any()) } returns null
        coEvery { medicationLogRepository.saveEntry(
            uuid = any(), medicineUuid = any(),
 applicationType = any(),
 doseInstruction = any(), sourceGroupUuid = any(),
            scheduleTimeUuid = any(), appliedAt = any(), scheduledFor = any(),
            count = any(), appliedAtTimeZoneId = any()
        ) } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicineStockRepository = medicineStockRepository,
            medicationReminderScheduler = medicationReminderScheduler,
        )
        viewModel.initialize(listOf(entryId.toString()))
        advanceUntilIdle()

        viewModel.saveEntry()
        advanceUntilIdle()

        val payload = viewModel.uiState.value.savedCrossZoneZoneText
        // Long name varies subtly across runtimes; assert we got SOMETHING and it's
        // not the bare IANA id.
        assertNotNull(payload)
        assertFalse(payload == "America/New_York")

        // After consume, the field clears.
        viewModel.consumeCrossZoneToast()
        assertNull(viewModel.uiState.value.savedCrossZoneZoneText)
    }

    @Test
    fun saveEntry_does_not_emit_cross_zone_toast_payload_for_same_zone() = runTest(dispatcher) {
        val entryId = UUID.randomUUID()
        val deviceZone = ZoneId.systemDefault()
        val originalInstant = LocalDateTime.of(2026, 4, 15, 9, 0).atZone(deviceZone).toInstant()
        val existingEntry = testMedicationLogEntry(
            uuid = entryId,
            medicine = estradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            sourceGroupUuid = null,
            appliedAt = originalInstant,
            appliedAtTimeZoneId = deviceZone.id
        )
        coEvery { medicationLogRepository.getEntries(listOf(entryId)) } returns listOf(existingEntry)
        every { medicationGroupRepository.getCachedGroup(any()) } returns null
        coEvery { medicationLogRepository.saveEntry(
            uuid = any(), medicineUuid = any(),
 applicationType = any(),
 doseInstruction = any(), sourceGroupUuid = any(),
            scheduleTimeUuid = any(), appliedAt = any(), scheduledFor = any(),
            count = any(), appliedAtTimeZoneId = any()
        ) } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicineStockRepository = medicineStockRepository,
            medicationReminderScheduler = medicationReminderScheduler,
        )
        viewModel.initialize(listOf(entryId.toString()))
        advanceUntilIdle()
        viewModel.saveEntry()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.savedCrossZoneZoneText)
    }

    @Test
    fun deleteEntry_whenSchedulerFails_marksEntryDeleted() = runTest {
        val entryId = UUID.fromString("d800fa8b-71a6-48f7-9424-275d6bb56243")
        val entry = testMedicationLogEntry(
            uuid = entryId,
            medicine = estradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15))
        )
        coEvery { medicationLogRepository.getEntries(listOf(entryId)) } returns listOf(entry)
        coEvery { medicationLogRepository.deleteEntries(listOf(entryId)) } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } throws RuntimeException("schedule failed")

        val viewModel = MedicationLogEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicineStockRepository = medicineStockRepository,
        )
        viewModel.initialize(listOf(entryId.toString()))
        advanceUntilIdle()

        viewModel.deleteEntry()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeleting)
        assertTrue(viewModel.uiState.value.isSaved)
        assertNull(viewModel.uiState.value.deleteEntryResult)
        coVerify(exactly = 1) { medicationLogRepository.deleteEntries(listOf(entryId)) }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
    }
}

private fun testMedicationGroup(
    groupId: UUID,
    name: String,
    colorKey: MedicationGroupColorKey,
    times: List<LocalTime> = listOf(LocalTime.of(21, 0)),
    archived: Boolean = false,
): MedicationGroup {
    return MedicationGroup(
        uuid = groupId,
        name = name,
        colorKey = colorKey,
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 4, 1),
            weeklyDaysOfWeek = emptySet(),
            times = times
        ),
        medications = emptyList(),
        createdAt = testInstant(LocalDateTime.of(2026, 4, 1, 12, 0)),
        updatedAt = testInstant(LocalDateTime.of(2026, 4, 22, 12, 0)),
        archivedAt = if (archived) testInstant(LocalDateTime.of(2026, 4, 23, 12, 0)) else null,
    )
}

private fun stockProjection(
    medicine: Medicine,
    unitsRemaining: Double = 4.0,
    state: MedicineStockState = MedicineStockState.NO_RUNWAY,
): MedicineStockProjection {
    return MedicineStockProjection(
        medicine = medicine,
        dosesPerDayMagnitude = 1.0,
        totalStockUnits = unitsRemaining,
        runway = RunwayProjection.NoSchedule,
        intervalDays = null,
        maxPerAdministration = 1.0,
        state = state,
    )
}
