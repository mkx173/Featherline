package com.mkx.hrttracker.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.isArchived
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
 * PATCH_OFF carries no medicine, so a patch-off slot/log never produces a row
 * here — the list shows only medicines, not application events.
 */
@HiltViewModel
class MedicinesViewModel @Inject constructor(
    medicineRepository: MedicineRepository,
    medicationGroupRepository: MedicationGroupRepository,
) : ViewModel() {

    val uiState: StateFlow<MedicinesUiState> = combine(
        medicineRepository.observeAllActive(),
        medicationGroupRepository.observeGroups().map { it.orEmpty() },
    ) { activeMedicines, groups ->
        MedicinesUiState(
            activeSections = buildSections(
                activeMedicines = activeMedicines,
                referenceCounts = activeGroupReferenceCounts(groups),
            ),
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(UI_STATE_STOP_TIMEOUT_MILLIS),
        initialValue = MedicinesUiState(),
    )

    private fun buildSections(
        activeMedicines: List<Medicine>,
        referenceCounts: Map<java.util.UUID, Int>,
    ): List<MedicineCategorySection> {
        if (activeMedicines.isEmpty()) {
            return emptyList()
        }
        val byCategory = activeMedicines.groupBy { it.category }
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
                            .thenBy { it.uuid.toString() },
                    )
                    .map { medicine ->
                        MedicineListItem(
                            medicine = medicine,
                            activeGroupReferenceCount = referenceCounts[medicine.uuid] ?: 0,
                        )
                    },
            )
        }
    }

    private fun activeGroupReferenceCounts(
        groups: List<MedicationGroup>,
    ): Map<java.util.UUID, Int> {
        // Count over groups, not slots: a group with two slots pointing at
        // the same medicine still counts once per group. The archive guard
        // we mirror in MedicineRepository.archive uses the same semantics.
        return groups.asSequence()
            .filterNot(MedicationGroup::isArchived)
            .flatMap { group ->
                group.medications.asSequence()
                    .mapNotNull { it.medicineUuid }
                    .distinct()
            }
            .groupingBy { it }
            .eachCount()
    }

    private companion object {
        const val UI_STATE_STOP_TIMEOUT_MILLIS = 5_000L
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
    val activeGroupReferenceCount: Int,
)
