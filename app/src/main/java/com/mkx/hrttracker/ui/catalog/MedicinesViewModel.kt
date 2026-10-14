package com.mkx.hrttracker.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.data.repository.StockReceived
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.isArchived
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Plan tab → "Medicines" overflow action surface.
 *
 * Surfaces every active [Medicine] partitioned by [MedicationCategory], each
 * row decorated with a count of active [MedicationGroup]s that reference the
 * medicine (the value the detail screen uses to gate archive). The list pulls
 * live data from the two repositories directly; there is no in-VM cache, so an
 * upstream emission immediately reshapes the on-screen list.
 *
 * The PATCH_OFF singleton is a medicine-manager action row, not a regular
 * medicine. It is shown only while at least one active patch medicine exists.
 */
@HiltViewModel
class MedicinesViewModel @Inject constructor(
    private val medicineRepository: MedicineRepository,
    medicationGroupRepository: MedicationGroupRepository,
    private val stockRepository: MedicineStockRepository,
) : ViewModel() {

    val uiState: StateFlow<MedicinesUiState> = combine(
        medicineRepository.observeAllActive(),
        medicationGroupRepository.observeGroups().map { it.orEmpty() },
        stockRepository.observeProjections(),
    ) { activeMedicines, groups, stockProjections ->
        buildUiState(
            activeMedicines = activeMedicines,
            groups = groups,
            stockProjections = stockProjections,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        // Seed from the eagerly-cached repo StateFlows so the first
        // composition lands on a populated list — without this, even with
        // SharingStarted.Eagerly the upstream combine emits on the next
        // coroutine tick and Compose sees isLoading=true on frame 1.
        initialValue = buildInitialUiState(
            activeMedicines = medicineRepository.getCachedActiveMedicines(),
            groups = medicationGroupRepository.getCachedGroups(),
            stockProjections = stockRepository.getCachedProjections(),
        ),
    )

    private val stockOptInResultFlow = MutableStateFlow<MedicineStockMutationResult?>(null)
    val stockOptInResult: StateFlow<MedicineStockMutationResult?> =
        stockOptInResultFlow.asStateFlow()
    private var stockOptInJob: Job? = null

    private fun buildUiState(
        activeMedicines: List<Medicine>,
        groups: List<MedicationGroup>,
        stockProjections: List<MedicineStockProjection>,
    ): MedicinesUiState {
        return MedicinesUiState(
            activeSections = buildSections(
                activeMedicines = activeMedicines,
                referencingGroupIds = activeGroupIdsByMedicine(groups),
                stockProjections = stockProjections.associateBy { it.medicine.uuid },
            ),
            isLoading = false,
        )
    }

    private fun buildInitialUiState(
        activeMedicines: List<Medicine>?,
        groups: List<MedicationGroup>?,
        stockProjections: List<MedicineStockProjection>?,
    ): MedicinesUiState {
        if (activeMedicines == null || groups == null || stockProjections == null) {
            return MedicinesUiState()
        }
        return buildUiState(
            activeMedicines = activeMedicines,
            groups = groups,
            stockProjections = stockProjections,
        )
    }

    private fun buildSections(
        activeMedicines: List<Medicine>,
        referencingGroupIds: Map<UUID, Set<UUID>>,
        stockProjections: Map<UUID, MedicineStockProjection>,
    ): List<MedicineCategorySection> {
        val visibleMedicines = activeMedicines.visibleInMedicineManager()
        if (visibleMedicines.isEmpty()) {
            return emptyList()
        }
        val byCategory = visibleMedicines.groupBy { it.category }
        // Iterate enum order so the section order is deterministic and
        // doesn't drift with insertion order from the database. Within each
        // section, sort by creation time (oldest first) so the user sees their
        // medicines in the order they added them.
        return MedicationCategory.entries.mapNotNull { category ->
            val medicines = byCategory[category]?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            MedicineCategorySection(
                category = category,
                medicines = medicines
                    .sortedWith(
                        compareBy<Medicine> { it.createdAt }
                            .thenBy { it.preparation is MedicinePreparation.PatchOff }
                            .thenBy { it.uuid.toString() },
                    )
                    .map { medicine ->
                        MedicineListItem(
                            medicine = medicine,
                            activeGroupIds = referencingGroupIds[medicine.uuid].orEmpty(),
                            stockProjection = stockProjections[medicine.uuid],
                        )
                    },
            )
        }
    }

    private fun activeGroupIdsByMedicine(
        groups: List<MedicationGroup>,
    ): Map<UUID, Set<UUID>> {
        // Count over groups, not slots: a group with two slots pointing at
        // the same medicine still counts once per group. The archive guard
        // we mirror in MedicineRepository.archive uses the same semantics.
        return groups.asSequence()
            .filterNot(MedicationGroup::isArchived)
            .flatMap { group ->
                group.medications.asSequence()
                    .mapNotNull { it.medicineUuid }
                    .map { medicineUuid -> medicineUuid to group.uuid }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, groupIds) -> groupIds.toSet() }
    }

    fun previewRunwayFor(
        medicineUuid: UUID,
        hypotheticalStock: MedicineStock,
    ): RunwayProjection? {
        return stockRepository.previewRunway(medicineUuid, hypotheticalStock)
    }

    // Mirrors the detail screen's launchStockMutation: a single-flight mutation
    // that reports its outcome so the onboarding opt-in sheet closes only on
    // success and stays open (with a failure toast) on error, instead of
    // dismissing optimistically and dropping a failed enable.
    fun enableTrackingFromReceived(
        medicineUuid: UUID,
        currentUnitsRemaining: Double,
        received: StockReceived,
    ): Job {
        stockOptInJob?.takeIf(Job::isActive)?.let { return it }

        lateinit var job: Job
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val initialUnitsRemaining = currentUnitsRemaining + received.unitsReceived
                medicineRepository.enableTracking(
                    uuid = medicineUuid,
                    initialUnitsRemaining = initialUnitsRemaining,
                    initialOpenContainerAmount = null,
                    initialUnitsLastTotal = initialUnitsRemaining,
                )
                stockOptInResultFlow.value = MedicineStockMutationResult.SUCCESS
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                stockOptInResultFlow.value = MedicineStockMutationResult.FAILURE
            } finally {
                if (stockOptInJob == job) {
                    stockOptInJob = null
                }
            }
        }
        stockOptInJob = job
        job.start()
        return job
    }

    fun clearStockOptInResult() {
        stockOptInResultFlow.value = null
    }

    private fun List<Medicine>.visibleInMedicineManager(): List<Medicine> {
        val hasActivePatchMedicine = any { it.preparation is MedicinePreparation.Patch }
        return if (hasActivePatchMedicine) {
            this
        } else {
            filterNot { it.preparation is MedicinePreparation.PatchOff }
        }
    }

}

data class MedicinesUiState(
    val activeSections: List<MedicineCategorySection> = emptyList(),
    val isLoading: Boolean = true,
)

data class MedicineCategorySection(
    val category: MedicationCategory,
    val medicines: List<MedicineListItem>,
)

data class MedicineListItem(
    val medicine: Medicine,
    val activeGroupIds: Set<UUID> = emptySet(),
    val stockProjection: MedicineStockProjection? = null,
) {
    val activeGroupReferenceCount: Int get() = activeGroupIds.size
}

/**
 * The unsaved plan open in the editor that launched the picker. [groupId] is
 * null for a plan that has never been saved.
 */
data class DraftGroupReferences(
    val groupId: UUID?,
    val medicineUuids: Set<UUID>,
)

// Stands in for a never-saved draft plan in the referencing-group sets.
private val UNSAVED_DRAFT_GROUP_ID = UUID(0L, 0L)

/**
 * Counts [draft] in place of its saved group, so a slot removed (or added) in
 * the editor changes the picker's per-medicine plan count before the plan is
 * saved.
 */
internal fun MedicinesUiState.withDraftGroup(draft: DraftGroupReferences?): MedicinesUiState {
    draft ?: return this
    val draftGroupId = draft.groupId ?: UNSAVED_DRAFT_GROUP_ID
    return copy(
        activeSections = activeSections.map { section ->
            section.copy(
                medicines = section.medicines.map { item ->
                    val otherGroupIds = item.activeGroupIds - draftGroupId
                    item.copy(
                        activeGroupIds = if (item.medicine.uuid in draft.medicineUuids) {
                            otherGroupIds + draftGroupId
                        } else {
                            otherGroupIds
                        },
                    )
                },
            )
        },
    )
}
