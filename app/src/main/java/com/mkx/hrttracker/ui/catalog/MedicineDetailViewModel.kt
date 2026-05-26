package com.mkx.hrttracker.ui.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicineIdentityCollisionException
import com.mkx.hrttracker.data.repository.MedicineLockedException
import com.mkx.hrttracker.data.repository.MedicineReferencedByActiveGroupException
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.StockReceived
import com.mkx.hrttracker.data.repository.StockRecount
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.isArchived
import com.mkx.hrttracker.ui.medication.PatchSpecKind
import com.mkx.hrttracker.util.systemLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
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
    private val stockRepository: MedicineStockRepository,
    settingsRepository: SettingsRepository,
    private val savedStateHandle: SavedStateHandle,
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
                    settingsRepository.settingsState,
                    stockRepository.observeProjections()
                        .map { projections ->
                            projections.firstOrNull { projection ->
                                projection.medicine.uuid == medicineUuid
                            }
                        },
                ) { displayNameDraft, archiveResult, saveResult, settingsState, stockProjection ->
                    ResultsAndSettings(
                        displayNameDraft = displayNameDraft,
                        archiveResult = archiveResult,
                        saveResult = saveResult,
                        firstDayOfWeek = settingsState.firstDayOfWeekOption
                            .resolve(systemLocale()),
                        stockProjection = stockProjection,
                    )
                },
            ) { active, archived, groups, isLocked, derived ->
                buildState(
                    active = active,
                    archived = archived,
                    groups = groups,
                    isLocked = isLocked,
                    displayNameDraft = derived.displayNameDraft,
                    archiveResult = derived.archiveResult,
                    saveResult = derived.saveResult,
                    firstDayOfWeek = derived.firstDayOfWeek,
                    stockProjection = derived.stockProjection,
                )
            }.collect { state ->
                _uiState.update { current ->
                    // Preserve preparationDraft from the UI side; nothing
                    // upstream owns it.
                    val preservedState = state.copy(
                        preparationDraft = current.preparationDraft,
                        showAdjustSheet = current.showAdjustSheet,
                        showDisableConfirmation = current.showDisableConfirmation,
                        adjustSheetActiveTab = current.adjustSheetActiveTab,
                        pendingEnableTracking = current.pendingEnableTracking,
                    )
                    consumeOpenOptInRequestIfReady(preservedState)
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

    fun openAdjustSheet(initialTab: AdjustSheetTab = AdjustSheetTab.RECEIVED) {
        _uiState.update {
            it.copy(
                showAdjustSheet = true,
                adjustSheetActiveTab = initialTab,
                pendingEnableTracking = false,
            )
        }
    }

    fun closeAdjustSheet() {
        _uiState.update {
            it.copy(
                showAdjustSheet = false,
                pendingEnableTracking = false,
            )
        }
    }

    fun openOptIn() {
        _uiState.update {
            it.copy(
                showAdjustSheet = true,
                adjustSheetActiveTab = AdjustSheetTab.RECEIVED,
                pendingEnableTracking = true,
            )
        }
    }

    fun openOpenContainerDialog() {
        _uiState.update { it.copy(showOpenContainerDialog = true) }
    }

    fun closeOpenContainerDialog() {
        _uiState.update { it.copy(showOpenContainerDialog = false) }
    }

    fun submitOpenContainerAmount(amount: Double): Job = viewModelScope.launch {
        medicineRepository.applySetOpenContainerAmount(medicineUuid, amount)
        _uiState.update { it.copy(showOpenContainerDialog = false) }
    }

    fun openDisableConfirmation() {
        _uiState.update { it.copy(showDisableConfirmation = true) }
    }

    fun closeDisableConfirmation() {
        _uiState.update { it.copy(showDisableConfirmation = false) }
    }

    fun confirmDisableTracking(): Job = viewModelScope.launch {
        medicineRepository.disableTracking(medicineUuid)
        _uiState.update { it.copy(showDisableConfirmation = false) }
    }

    fun confirmEnableTracking(
        unitsRemaining: Double?,
        openContainerAmount: Double?,
        unitsLastTotal: Double?,
    ): Job = viewModelScope.launch {
        medicineRepository.enableTracking(
            uuid = medicineUuid,
            initialUnitsRemaining = unitsRemaining,
            initialOpenContainerAmount = openContainerAmount,
            initialUnitsLastTotal = unitsLastTotal,
        )
    }

    fun submitWarnAt(days: Int): Job = viewModelScope.launch {
        medicineRepository.updateWarnAtDaysRemaining(medicineUuid, days)
    }

    fun submitRecount(recount: StockRecount): Job = viewModelScope.launch {
        if (closePendingEnableAdjustSheet()) return@launch
        medicineRepository.applyRecount(medicineUuid, recount)
        _uiState.update { it.copy(showAdjustSheet = false) }
    }

    fun submitReceived(received: StockReceived): Job = viewModelScope.launch {
        if (_uiState.value.pendingEnableTracking) {
            val medicine = _uiState.value.stockProjection?.medicine
            val initialUnitsRemaining = (medicine?.stock?.unitsRemaining ?: 0.0) +
                received.unitsReceived
            // Container opt-in always starts with everything sealed; a vial is
            // promoted to "open" only when the first deduction happens. Both
            // pool and container preparations seed unitsLastTotal so the
            // sealed-row / now-have gauge has a snap-to-full baseline.
            medicineRepository.enableTracking(
                uuid = medicineUuid,
                initialUnitsRemaining = initialUnitsRemaining,
                initialOpenContainerAmount = null,
                initialUnitsLastTotal = initialUnitsRemaining,
            )
            _uiState.update {
                it.copy(
                    showAdjustSheet = false,
                    pendingEnableTracking = false,
                )
            }
        } else {
            medicineRepository.applyReceived(medicineUuid, received)
            _uiState.update { it.copy(showAdjustSheet = false) }
        }
    }

    private fun closePendingEnableAdjustSheet(): Boolean {
        if (!_uiState.value.pendingEnableTracking) return false
        _uiState.update {
            it.copy(
                showAdjustSheet = false,
                pendingEnableTracking = false,
            )
        }
        return true
    }

    private fun consumeOpenOptInRequestIfReady(
        state: MedicineDetailUiState,
    ): MedicineDetailUiState {
        if (savedStateHandle.get<Boolean>(OPEN_OPT_IN_ARG) != true) return state
        val projection = state.stockProjection ?: return state
        savedStateHandle[OPEN_OPT_IN_ARG] = false
        if (projection.medicine.stock.trackingEnabled) return state
        if (projection.medicine.preparation is MedicinePreparation.PatchOff) return state
        return state.copy(
            showAdjustSheet = true,
            adjustSheetActiveTab = AdjustSheetTab.RECEIVED,
            pendingEnableTracking = true,
        )
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

    // One-shot orchestration for the merged display-name + preparation sheet:
    // saves the buffered display-name and, when the medicine is unlocked, the
    // user-edited preparation. Emits a single saveResult so the toast reflects
    // the final outcome instead of racing two independent saves.
    fun saveAll(
        preparation: MedicinePreparation?,
        displayDoseUnit: com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit? = null,
    ): Job = viewModelScope.launch {
        val displayNameDraft = displayNameTextFlow.value
        if (displayNameDraft != null) {
            val sanitized = displayNameDraft.trim().takeIf(String::isNotBlank)
            val result = runCatching {
                medicineRepository.setDisplayName(medicineUuid, sanitized)
            }
            if (result.isFailure) {
                saveResultFlow.value = MedicineDetailSaveResult.FAILURE_OTHER
                return@launch
            }
        }
        if (preparation != null) {
            val result = runCatching {
                medicineRepository.updatePreparation(
                    uuid = medicineUuid,
                    preparation = preparation,
                    displayDoseUnit = displayDoseUnit,
                )
            }
            if (result.isFailure) {
                saveResultFlow.value = when (result.exceptionOrNull()) {
                    is MedicineLockedException -> MedicineDetailSaveResult.FAILURE_LOCKED
                    is MedicineIdentityCollisionException ->
                        MedicineDetailSaveResult.FAILURE_IDENTITY_COLLISION
                    else -> MedicineDetailSaveResult.FAILURE_OTHER
                }
                return@launch
            }
            isLockedFlow.value = medicineRepository.isLocked(medicineUuid)
        }
        saveResultFlow.value = MedicineDetailSaveResult.SUCCESS
    }

    fun savePreparation(
        preparation: MedicinePreparation,
        displayDoseUnit: com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit? = null,
    ): Job = viewModelScope.launch {
        runCatching {
            medicineRepository.updatePreparation(
                uuid = medicineUuid,
                preparation = preparation,
                displayDoseUnit = displayDoseUnit,
            )
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
        if (_uiState.value.linkedActiveSlots.isNotEmpty()) {
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
        firstDayOfWeek: DayOfWeek,
        stockProjection: MedicineStockProjection?,
    ): MedicineDetailUiState {
        val medicine = (active + archived).firstOrNull { it.uuid == medicineUuid }
        val linkedActiveSlots = if (medicine == null) {
            emptyList()
        } else {
            groups.asSequence()
                .filterNot(MedicationGroup::isArchived)
                .flatMap { group ->
                    group.medications.asSequence()
                        .filter { medication -> medication.medicineUuid == medicineUuid }
                        .map { medication ->
                            LinkedSlotRow(
                                group = group,
                                doseInstruction = medication.doseInstruction,
                                applicationType = medication.applicationType,
                                count = medication.count,
                            )
                        }
                }
                .sortedWith(
                    compareBy<LinkedSlotRow> { it.group.name.lowercase() }
                        .thenBy { it.applicationType }
                )
                .toList()
        }
        val displayName = displayNameDraft ?: medicine?.displayName.orEmpty()
        return MedicineDetailUiState(
            medicine = medicine,
            isLocked = isLocked,
            linkedActiveSlots = linkedActiveSlots,
            displayNameText = displayName,
            archiveResult = archiveResult,
            saveResult = saveResult,
            firstDayOfWeek = firstDayOfWeek,
            stockProjection = stockProjection,
        )
    }

    private data class ResultsAndSettings(
        val displayNameDraft: String?,
        val archiveResult: MedicineArchiveResult?,
        val saveResult: MedicineDetailSaveResult?,
        val firstDayOfWeek: DayOfWeek,
        val stockProjection: MedicineStockProjection?,
    )

    companion object {
        const val MEDICINE_ID_ARG = "medicineId"
        const val OPEN_OPT_IN_ARG = "openOptIn"
    }
}


data class MedicineDetailUiState(
    val medicine: Medicine? = null,
    val isLocked: Boolean = false,
    val linkedActiveSlots: List<LinkedSlotRow> = emptyList(),
    val displayNameText: String = "",
    val preparationDraft: MedicinePreparationDraftUiState? = null,
    val archiveResult: MedicineArchiveResult? = null,
    val saveResult: MedicineDetailSaveResult? = null,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val stockProjection: MedicineStockProjection? = null,
    val showAdjustSheet: Boolean = false,
    val showDisableConfirmation: Boolean = false,
    val showOpenContainerDialog: Boolean = false,
    val adjustSheetActiveTab: AdjustSheetTab = AdjustSheetTab.RECEIVED,
    val pendingEnableTracking: Boolean = false,
)

enum class AdjustSheetTab {
    RECEIVED,
    RECOUNT,
}

data class LinkedSlotRow(
    val group: MedicationGroup,
    val doseInstruction: DoseInstruction,
    val applicationType: MedicationApplicationType,
    val count: Int,
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
    val patchSpecKind: PatchSpecKind = PatchSpecKind.TOTAL_MG,
    val patchTotalMg: String = "",
    val patchReleaseRateMcgPerDay: String = "",
    // Custom-medicine raw-mass unit; ignored on catalog medicines (always MG).
    // The typed mass string is interpreted in this unit on save and rescaled
    // back from stored mg when the dialog re-opens.
    val displayDoseUnit: com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit =
        com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit.MG,
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
