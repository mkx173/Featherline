package com.mkx.hrttracker.ui.medicine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicineIdentityCollisionException
import com.mkx.hrttracker.data.repository.MedicineLockedException
import com.mkx.hrttracker.data.repository.MedicineReferencedByActiveGroupException
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.isArchived
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Backs `MedicineDetailScreen`. Owns:
 *  - the currently-observed [Medicine] (live from
 *    [MedicineRepository.observeAllActive] / [MedicineRepository.observeAllArchived]);
 *  - the list of active groups that still reference the medicine — the input
 *    to the archive-blocking guard;
 *  - the locked flag (true if any log row references the medicine, which the
 *    repo enforces server-side for `updatePreparation`).
 *
 * Display-name edits and preparation edits each go through their own typed
 * result so the screen can render distinct messages — locked vs identity
 * collision vs referenced-by-group are each user-actionable in different
 * ways.
 */
@HiltViewModel
class MedicineDetailViewModel @Inject constructor(
    private val medicineRepository: MedicineRepository,
    medicationGroupRepository: MedicationGroupRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val medicineUuid: UUID = run {
        val raw = checkNotNull(savedStateHandle.get<String>(MEDICINE_ID_ARG)) {
            "MedicineDetailViewModel requires a medicineId nav argument."
        }
        UUID.fromString(raw)
    }

    // Tracks the lock state independently of the live medicine flow so a
    // user-initiated refresh re-asks the repo without waiting for an
    // upstream emission.
    private val isLockedFlow = MutableStateFlow(false)
    private val displayNameTextFlow = MutableStateFlow<String?>(null)
    private val archiveResultFlow = MutableStateFlow<MedicineArchiveResult?>(null)
    private val saveResultFlow = MutableStateFlow<MedicineDetailSaveResult?>(null)

    private val _uiState = MutableStateFlow(MedicineDetailUiState())
    val uiState: StateFlow<MedicineDetailUiState> = _uiState.asStateFlow()

    init {
        // One collector keeps the assembled UI state in lockstep with every
        // upstream flow — there is no second writer to _uiState other than
        // setPreparationDraft (UI-owned local state).
        viewModelScope.launch {
            combine(
                medicineRepository.observeAllActive(),
                medicineRepository.observeAllArchived(),
                medicationGroupRepository.observeGroups().map { it.orEmpty() },
                isLockedFlow,
                combine(
                    displayNameTextFlow,
                    archiveResultFlow,
                    saveResultFlow,
                ) { displayNameDraft, archiveResult, saveResult ->
                    Triple(displayNameDraft, archiveResult, saveResult)
                },
            ) { active, archived, groups, isLocked, triple ->
                buildState(
                    active = active,
                    archived = archived,
                    groups = groups,
                    isLocked = isLocked,
                    displayNameDraft = triple.first,
                    archiveResult = triple.second,
                    saveResult = triple.third,
                )
            }.collect { state ->
                _uiState.update { current ->
                    // Preserve preparationDraft from the UI side; nothing
                    // upstream owns it.
                    state.copy(preparationDraft = current.preparationDraft)
                }
            }
        }

        refreshLockState()
    }

    fun refreshLockState() {
        viewModelScope.launch {
            isLockedFlow.value = medicineRepository.isLocked(medicineUuid)
        }
    }

    fun updateDisplayNameText(text: String) {
        displayNameTextFlow.value = text
    }

    /** Drops the result toast/snackbar after the UI consumes it. */
    fun clearSaveResult() {
        saveResultFlow.value = null
    }

    fun clearArchiveResult() {
        archiveResultFlow.value = null
    }

    fun setPreparationDraft(draft: MedicinePreparationDraftUiState?) {
        _uiState.update { it.copy(preparationDraft = draft) }
    }

    fun saveDisplayName(): Job = viewModelScope.launch {
        val draft = displayNameTextFlow.value ?: return@launch
        val sanitized = draft.trim().takeIf(String::isNotBlank)
        runCatching {
            medicineRepository.setDisplayName(medicineUuid, sanitized)
        }.onSuccess {
            saveResultFlow.value = MedicineDetailSaveResult.SUCCESS
        }.onFailure {
            saveResultFlow.value = MedicineDetailSaveResult.FAILURE_OTHER
        }
    }

    fun savePreparation(preparation: MedicinePreparation): Job = viewModelScope.launch {
        runCatching {
            medicineRepository.updatePreparation(medicineUuid, preparation)
        }.onSuccess {
            saveResultFlow.value = MedicineDetailSaveResult.SUCCESS
            // The preparation update may have unlocked nothing, but the
            // medicine's identityKey changed — re-read isLocked to be safe.
            isLockedFlow.value = medicineRepository.isLocked(medicineUuid)
        }.onFailure { error ->
            saveResultFlow.value = when (error) {
                is MedicineLockedException -> MedicineDetailSaveResult.FAILURE_LOCKED
                is MedicineIdentityCollisionException ->
                    MedicineDetailSaveResult.FAILURE_IDENTITY_COLLISION
                else -> MedicineDetailSaveResult.FAILURE_OTHER
            }
        }
    }

    fun archive(): Job? {
        // Defensive client-side guard: even if the user taps archive when
        // the button was momentarily enabled (between an upstream emission
        // and the disabled state propagating), do not submit. The repo
        // still throws if a group is added concurrently — handled below.
        if (_uiState.value.linkedActiveGroups.isNotEmpty()) {
            return null
        }
        return viewModelScope.launch {
            runCatching { medicineRepository.archive(medicineUuid) }
                .onSuccess { archiveResultFlow.value = MedicineArchiveResult.SUCCESS }
                .onFailure { error ->
                    archiveResultFlow.value = when (error) {
                        is MedicineReferencedByActiveGroupException ->
                            MedicineArchiveResult.FAILURE_REFERENCED_BY_ACTIVE_GROUP
                        else -> MedicineArchiveResult.FAILURE_OTHER
                    }
                }
        }
    }

    private fun buildState(
        active: List<Medicine>,
        archived: List<Medicine>,
        groups: List<MedicationGroup>,
        isLocked: Boolean,
        displayNameDraft: String?,
        archiveResult: MedicineArchiveResult?,
        saveResult: MedicineDetailSaveResult?,
    ): MedicineDetailUiState {
        val medicine = (active + archived).firstOrNull { it.uuid == medicineUuid }
        val linkedActiveGroups = if (medicine == null) {
            emptyList()
        } else {
            groups.asSequence()
                .filterNot(MedicationGroup::isArchived)
                .filter { group ->
                    group.medications.any { it.medicineUuid == medicineUuid }
                }
                .toList()
        }
        val displayName = displayNameDraft ?: medicine?.displayName.orEmpty()
        return MedicineDetailUiState(
            medicine = medicine,
            isLocked = isLocked,
            linkedActiveGroups = linkedActiveGroups,
            displayNameText = displayName,
            archiveResult = archiveResult,
            saveResult = saveResult,
        )
    }

    companion object {
        const val MEDICINE_ID_ARG = "medicineId"
    }
}

data class MedicineDetailUiState(
    val medicine: Medicine? = null,
    val isLocked: Boolean = false,
    val linkedActiveGroups: List<MedicationGroup> = emptyList(),
    val displayNameText: String = "",
    val preparationDraft: MedicinePreparationDraftUiState? = null,
    val archiveResult: MedicineArchiveResult? = null,
    val saveResult: MedicineDetailSaveResult? = null,
)

/**
 * Mirrors the picker's preparation draft but stripped to what the detail
 * editor owns — the medicine's identity (selection + category) is fixed
 * here, only the physical preparation can change.
 */
data class MedicinePreparationDraftUiState(
    val preparationType: com.mkx.hrttracker.model.medication.MedicinePreparationType,
    val pillStrengthMg: String = "",
    val singleUseVialStrengthMg: String = "",
    val concentrationMgPerMl: String = "",
    val vialVolumeMl: String = "",
    val gelConcentrationPercent: String = "",
    val sachetWeightGrams: String = "",
    val containerWeightGrams: String = "",
    val patchTotalMg: String = "",
    val patchReleaseRateMcgPerDay: String = "",
)

enum class MedicineArchiveResult {
    SUCCESS,
    FAILURE_REFERENCED_BY_ACTIVE_GROUP,
    FAILURE_OTHER,
}

enum class MedicineDetailSaveResult {
    SUCCESS,
    FAILURE_LOCKED,
    FAILURE_IDENTITY_COLLISION,
    FAILURE_OTHER,
}
