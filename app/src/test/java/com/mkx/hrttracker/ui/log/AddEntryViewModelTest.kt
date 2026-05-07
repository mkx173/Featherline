package com.mkx.hrttracker.ui.log

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.scheduleFulfillmentAllowedOffset
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AddEntryViewModelTest {
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
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
                details = testCatalogMedicationDetails(
                    key = MedicationKey.ESTRADIOL,
                    applicationType = MedicationApplicationType.ORAL,
                    dose = MedicationDose.MgAsMedicine(2.0)
                ),
                dosageMgAsEstradiol = 2.0,
                sourceGroupUuid = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6"),
                appliedAt = testInstant(appliedAt),
                scheduledFor = scheduledFor
            ),
            testMedicationLogEntry(
                uuid = secondId,
                details = testCatalogMedicationDetails(
                    key = MedicationKey.ESTRADIOL,
                    applicationType = MedicationApplicationType.ORAL,
                    dose = MedicationDose.MgAsMedicine(2.0)
                ),
                dosageMgAsEstradiol = 2.0,
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
        assertFalse(uiState.canEditMedicationIdentity)
    }

    @Test
    fun buildEditingUiState_keeps_source_group_metadata_for_group_linked_entries() {
        val groupId = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6")
        val entry = testMedicationLogEntry(
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            dosageMgAsEstradiol = 2.0,
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
    }

    @Test
    fun buildEditingUiState_falls_back_to_single_entry_when_rows_do_not_match_exactly() {
        val firstId = UUID.fromString("58810b58-3176-428d-b361-e93e7e492a97")
        val secondId = UUID.fromString("693ecdb0-7414-41f2-b775-a79e7b1f2abf")
        val entries = listOf(
            testMedicationLogEntry(
                uuid = firstId,
                details = testCatalogMedicationDetails(
                    key = MedicationKey.ESTRADIOL,
                    applicationType = MedicationApplicationType.ORAL,
                    dose = MedicationDose.MgAsMedicine(2.0)
                ),
                dosageMgAsEstradiol = 2.0,
                sourceGroupUuid = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6"),
                appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
                scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
            ),
            testMedicationLogEntry(
                uuid = secondId,
                details = testCatalogMedicationDetails(
                    key = MedicationKey.ESTRADIOL,
                    applicationType = MedicationApplicationType.ORAL,
                    dose = MedicationDose.MgAsMedicine(2.0)
                ),
                dosageMgAsEstradiol = 2.0,
                sourceGroupUuid = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6"),
                appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 22, 0)),
                scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
            )
        )

        val uiState = buildEditingUiState(entries)

        requireNotNull(uiState)
        assertEquals(listOf(firstId.toString()), uiState.editingEntryIds)
        assertFalse(uiState.isBulkEditing)
        assertFalse(uiState.canEditMedicationIdentity)
    }

    @Test
    fun buildEditingUiState_keeps_medication_identity_editable_for_manual_entries() {
        val entry = testMedicationLogEntry(
            uuid = UUID.fromString("3885b7c7-45db-44ae-b512-429145f3bc6f"),
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15))
        )

        val uiState = buildEditingUiState(listOf(entry))

        requireNotNull(uiState)
        assertTrue(uiState.canEditMedicationIdentity)
    }

    @Test
    fun buildEditingUiState_preserves_existing_count_for_single_counted_entry() {
        val entry = testMedicationLogEntry(
            uuid = UUID.fromString("3ed5b4b7-fca2-4dff-ae06-e74cb15508a9"),
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            dosageMgAsEstradiol = 2.0,
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
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = UUID.fromString("67b2057c-9271-461d-a30d-b28fd7624fb6"),
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
            count = 2
        )

        val uiState = buildEditingUiState(listOf(entry))

        requireNotNull(uiState)
        assertEquals(2, uiState.count)
        assertFalse(uiState.canEditMedicationIdentity)
    }

    @Test
    fun buildEditingUiState_coerces_unsupported_routes_to_count_one() {
        val entry = testMedicationLogEntry(
            uuid = UUID.fromString("62f549eb-3870-4ce8-b476-6dd44759d78d"),
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL_VALERATE,
                applicationType = MedicationApplicationType.INJECTION,
                dose = MedicationDose.MgAsMedicine(5.0)
            ),
            dosageMgAsEstradiol = 5.0,
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15)),
            count = 3
        )

        val uiState = buildEditingUiState(listOf(entry))

        requireNotNull(uiState)
        assertEquals(1, uiState.count)
    }

    @Test
    fun addEntryUiState_allows_delete_only_while_editing() {
        assertFalse(AddEntryUiState().canDelete)
        assertTrue(
            AddEntryUiState(
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
        val details = testCatalogMedicationDetails(
            key = MedicationKey.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            dose = MedicationDose.MgAsMedicine(2.0)
        )
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)
        val appliedAt = LocalDateTime.of(2026, 4, 22, 21, 15, 30)

        val uiState = buildQuickLogUiState(
            groupId = groupId,
            group = group,
            scheduledFor = scheduledFor,
            medicationDetails = details,
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
        assertFalse(uiState.canEditMedicationIdentity)
        assertFalse(uiState.canDelete)
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
        val details = testCatalogMedicationDetails(
            key = MedicationKey.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            dose = MedicationDose.MgAsMedicine(2.0)
        )
        every { medicationGroupRepository.getCachedGroup(groupId) } returns group

        val viewModel = AddEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0),
            medicationDetails = details,
            medicationCount = 2
        )

        val uiState = viewModel.uiState.value
        assertFalse(uiState.isLoading)
        assertEquals(groupId, uiState.sourceGroupUuid)
        assertEquals("Nightly estradiol", uiState.sourceGroupName)
        assertEquals(2, uiState.count)
        assertFalse(uiState.canEditMedicationIdentity)
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
        val details = testCatalogMedicationDetails(
            key = MedicationKey.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            dose = MedicationDose.MgAsMedicine(2.0)
        )
        every { medicationGroupRepository.getCachedGroup(groupId) } returns group
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medication = details,
                sourceGroupUuid = groupId,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 2,
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = AddEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = scheduledFor,
            medicationDetails = details,
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
                medication = details,
                sourceGroupUuid = groupId,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 2,
            )
        }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
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
        val details = testCatalogMedicationDetails(
            key = MedicationKey.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            dose = MedicationDose.MgAsMedicine(2.0)
        )
        every { medicationGroupRepository.getCachedGroup(groupId) } returns group

        val viewModel = AddEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = scheduledFor,
            medicationDetails = details,
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
                medication = details,
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
        val details = testCatalogMedicationDetails(
            key = MedicationKey.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            dose = MedicationDose.MgAsMedicine(2.0)
        )
        every { medicationGroupRepository.getCachedGroup(groupId) } returns group
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = null,
                medication = details,
                sourceGroupUuid = groupId,
                scheduleTimeUuid = null,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 2,
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = AddEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
        )
        viewModel.initializeQuickLog(
            groupId = groupId,
            scheduledFor = scheduledFor,
            medicationDetails = details,
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
                medication = details,
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
        val details = testCatalogMedicationDetails(
            key = MedicationKey.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            dose = MedicationDose.MgAsMedicine(2.0)
        )
        val entry = testMedicationLogEntry(
            uuid = entryId,
            details = details,
            dosageMgAsEstradiol = 2.0,
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
                medication = details,
                sourceGroupUuid = groupId,
                scheduleTimeUuid = null,
                appliedAt = any(),
                scheduledFor = scheduledFor,
                count = 1,
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit

        val viewModel = AddEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
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
                medication = details,
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
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15))
        )
        coEvery { medicationLogRepository.getEntries(listOf(entryId)) } returns listOf(entry)
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = entryId,
                medication = any(),
                sourceGroupUuid = null,
                appliedAt = any(),
                scheduledFor = null,
                count = 1,
            )
        } throws RuntimeException("save failed")

        val viewModel = AddEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
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
                medication = any(),
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
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15))
        )
        coEvery { medicationLogRepository.getEntries(listOf(entryId)) } returns listOf(entry)
        coEvery {
            medicationLogRepository.saveEntry(
                uuid = entryId,
                medication = any(),
                sourceGroupUuid = null,
                appliedAt = any(),
                scheduledFor = null,
                count = 1,
            )
        } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } throws RuntimeException("schedule failed")

        val viewModel = AddEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
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
                medication = any(),
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
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15))
        )
        coEvery { medicationLogRepository.getEntries(listOf(entryId)) } returns listOf(entry)
        coEvery { medicationLogRepository.deleteEntries(listOf(entryId)) } throws RuntimeException("delete failed")

        val viewModel = AddEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
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
    fun deleteEntry_whenSchedulerFails_marksEntryDeleted() = runTest {
        val entryId = UUID.fromString("d800fa8b-71a6-48f7-9424-275d6bb56243")
        val entry = testMedicationLogEntry(
            uuid = entryId,
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            sourceGroupUuid = null,
            appliedAt = testInstant(LocalDateTime.of(2026, 4, 22, 21, 15))
        )
        coEvery { medicationLogRepository.getEntries(listOf(entryId)) } returns listOf(entry)
        coEvery { medicationLogRepository.deleteEntries(listOf(entryId)) } returns Unit
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } throws RuntimeException("schedule failed")

        val viewModel = AddEntryViewModel(
            medicationLogRepository = medicationLogRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationReminderScheduler = medicationReminderScheduler,
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
        updatedAt = testInstant(LocalDateTime.of(2026, 4, 22, 12, 0))
    )
}
