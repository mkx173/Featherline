package com.mkx.hrttracker.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodTestResultInput
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.findLastEstradiolEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

private val calibrationAnalytes = listOf(
    BloodAnalyteKey.E2,
    BloodAnalyteKey.T,
    BloodAnalyteKey.PROG,
    BloodAnalyteKey.PRL,
    BloodAnalyteKey.FSH,
    BloodAnalyteKey.LH,
)

private val defaultCalibrationAnalytes = listOf(
    BloodAnalyteKey.E2,
    BloodAnalyteKey.T,
)

@HiltViewModel
class CalibrationEditorViewModel @Inject constructor(
    private val bloodTestRepository: BloodTestRepository,
    private val medicationLogRepository: MedicationLogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val editingPanelUuid = savedStateHandle.get<String>(PANEL_ID_ARG)?.let { panelId ->
        runCatching { UUID.fromString(panelId) }.getOrNull()
    }
    private val defaultZoneId = ZoneId.systemDefault()
    private val _uiState = MutableStateFlow(
        CalibrationEditorUiState(
            panelUuid = editingPanelUuid?.toString(),
            isEditing = editingPanelUuid != null,
            isLoading = editingPanelUuid != null,
            collectedDate = LocalDate.now(defaultZoneId),
            collectedTime = LocalTime.now(defaultZoneId).withSecond(0).withNano(0),
        )
    )
    val uiState: StateFlow<CalibrationEditorUiState> = _uiState.asStateFlow()

    init {
        refreshTimeSinceLastEstradiolDose()
        editingPanelUuid?.let(::loadPanelForEditing)
    }

    fun updateCollectedDate(date: LocalDate) {
        _uiState.update { state ->
            state.copy(collectedDate = date)
        }
        refreshTimeSinceLastEstradiolDose()
    }

    fun updateCollectedTime(time: LocalTime) {
        _uiState.update { state ->
            state.copy(collectedTime = time.withSecond(0).withNano(0))
        }
        refreshTimeSinceLastEstradiolDose()
    }

    fun updateNotes(value: String) {
        _uiState.update { state ->
            state.copy(notes = value)
        }
    }

    fun updateAnalyteValue(analyteKey: BloodAnalyteKey, value: String) {
        _uiState.update { state ->
            state.copy(
                drafts = state.drafts.map { draft ->
                    if (draft.analyteKey == analyteKey) {
                        draft.copy(valueText = value)
                    } else {
                        draft
                    }
                }
            )
        }
    }

    fun updateAnalyteUnit(analyteKey: BloodAnalyteKey, unit: BloodUnitKey) {
        _uiState.update { state ->
            state.copy(
                drafts = state.drafts.map { draft ->
                    if (draft.analyteKey == analyteKey) {
                        draft.copy(unit = unit)
                    } else {
                        draft
                    }
                }
            )
        }
    }

    fun addAnalyte(analyteKey: BloodAnalyteKey) {
        if (analyteKey !in calibrationAnalytes) {
            return
        }
        _uiState.update { state ->
            if (state.drafts.any { draft -> draft.analyteKey == analyteKey }) {
                state
            } else {
                state.copy(
                    drafts = (state.drafts + CalibrationResultDraftUiState(
                        analyteKey = analyteKey,
                        unit = defaultCalibrationUnitFor(analyteKey),
                    )).sortedBy(::calibrationAnalyteSortIndex)
                )
            }
        }
    }

    fun removeAnalyte(analyteKey: BloodAnalyteKey) {
        _uiState.update { state ->
            state.copy(
                drafts = state.drafts.filterNot { draft ->
                    draft.analyteKey == analyteKey
                }
            )
        }
    }

    fun save() {
        val currentState = uiState.value
        if (currentState.isSaving || currentState.isDeleting || !canSaveCalibrationEditorState(currentState)) {
            return
        }

        _uiState.update { state ->
            state.copy(isSaving = true)
        }

        viewModelScope.launch {
            val latestState = uiState.value
            val collectedAt = latestState.toCollectedAtInstant(defaultZoneId)
            val resultInputs = buildResultInputs(latestState)

            runCatching {
                bloodTestRepository.savePanel(
                    uuid = editingPanelUuid,
                    collectedAt = collectedAt,
                    collectedAtTimeZoneId = defaultZoneId.id,
                    notes = latestState.notes,
                    results = resultInputs,
                    now = Instant.now(),
                )
            }.onSuccess { savedPanelUuid ->
                _uiState.update { state ->
                    state.copy(
                        panelUuid = savedPanelUuid.toString(),
                        isEditing = true,
                        isSaving = false,
                        isSaved = true,
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(isSaving = false)
                }
            }
        }
    }

    fun delete() {
        val panelUuid = editingPanelUuid ?: return
        val currentState = uiState.value
        if (currentState.isSaving || currentState.isDeleting) {
            return
        }

        _uiState.update { state ->
            state.copy(isDeleting = true)
        }

        viewModelScope.launch {
            runCatching {
                bloodTestRepository.deletePanel(panelUuid)
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        isDeleting = false,
                        isDeleted = true,
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(isDeleting = false)
                }
            }
        }
    }

    fun consumeSavedState() {
        _uiState.update { state ->
            state.copy(isSaved = false)
        }
    }

    fun consumeDeletedState() {
        _uiState.update { state ->
            state.copy(isDeleted = false)
        }
    }

    private fun loadPanelForEditing(panelUuid: UUID) {
        viewModelScope.launch {
            val panel = bloodTestRepository.getPanel(panelUuid)
            if (panel == null) {
                _uiState.update { state ->
                    state.copy(isLoading = false)
                }
                return@launch
            }

            _uiState.value = panel.toEditorState()
            refreshTimeSinceLastEstradiolDose()
        }
    }

    private fun refreshTimeSinceLastEstradiolDose() {
        viewModelScope.launch {
            val targetState = uiState.value
            val targetCollectedAt = targetState.toCollectedAtInstant(defaultZoneId)
            val elapsedMillis = findLastEstradiolEntry(
                entries = medicationLogRepository.getEntries(),
                onOrBefore = targetCollectedAt,
            )?.let { lastEntry ->
                (targetCollectedAt.toEpochMilli() - lastEntry.appliedAt.toEpochMilli())
                    .coerceAtLeast(0L)
            }

            _uiState.update { state ->
                if (state.toCollectedAtInstant(defaultZoneId) != targetCollectedAt) {
                    state
                } else {
                    state.copy(timeSinceLastEstradiolDoseMillis = elapsedMillis)
                }
            }
        }
    }

    private fun buildResultInputs(
        state: CalibrationEditorUiState,
    ): List<BloodTestResultInput.Builtin> {
        return state.drafts.mapNotNull { draft -> draft.toResultInput() }
    }

    private fun CalibrationResultDraftUiState.toResultInput(): BloodTestResultInput.Builtin? {
        val parsedValue = parseCalibrationNumericInput(valueText) ?: return null
        return BloodTestResultInput.Builtin(
            uuid = resultUuid,
            analyteKey = analyteKey,
            unit = unit,
            value = parsedValue,
        )
    }

    private fun BloodTestPanel.toEditorState(): CalibrationEditorUiState {
        val collectedDateTime = collectedAt.atZone(defaultZoneId).toLocalDateTime()
        val drafts = results.mapNotNull { result ->
            val analyte = result.analyte as? BloodTestResultAnalyte.Builtin ?: return@mapNotNull null
            CalibrationResultDraftUiState(
                analyteKey = analyte.key,
                resultUuid = result.uuid,
                valueText = formatCalibrationNumericValue(result.value),
                unit = BloodUnitKey.fromStorageValue(result.unitSnapshot)
                    ?: defaultCalibrationUnitFor(analyte.key),
            )
        }.sortedBy(::calibrationAnalyteSortIndex)

        return CalibrationEditorUiState(
            panelUuid = uuid.toString(),
            isEditing = true,
            isLoading = false,
            collectedDate = collectedDateTime.toLocalDate(),
            collectedTime = collectedDateTime.toLocalTime().withSecond(0).withNano(0),
            notes = notes.orEmpty(),
            drafts = drafts,
        )
    }

    companion object {
        const val PANEL_ID_ARG = "panelId"
    }
}

data class CalibrationEditorUiState(
    val panelUuid: String? = null,
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val collectedDate: LocalDate = LocalDate.now(),
    val collectedTime: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val timeSinceLastEstradiolDoseMillis: Long? = null,
    val notes: String = "",
    val drafts: List<CalibrationResultDraftUiState> = defaultCalibrationAnalytes.map { analyteKey ->
        CalibrationResultDraftUiState(analyteKey = analyteKey)
    },
)

data class CalibrationResultDraftUiState(
    val analyteKey: BloodAnalyteKey,
    val resultUuid: UUID? = null,
    val valueText: String = "",
    val unit: BloodUnitKey = defaultCalibrationUnitFor(analyteKey),
)

internal fun canSaveCalibrationEditorState(state: CalibrationEditorUiState): Boolean {
    if (state.drafts.isEmpty()) return false
    return state.drafts.all { draft ->
        val trimmed = draft.valueText.trim()
        trimmed.isNotEmpty() && parseCalibrationNumericInput(trimmed) != null
    }
}

internal fun calibrationAnalyteOptions(
    state: CalibrationEditorUiState,
): List<BloodAnalyteKey> {
    val presentAnalytes = state.drafts.map(CalibrationResultDraftUiState::analyteKey).toSet()
    return calibrationAnalytes.filterNot(presentAnalytes::contains)
}

internal fun calibrationAllowedUnitsFor(analyteKey: BloodAnalyteKey): List<BloodUnitKey> {
    return BloodTestCatalog.definitionFor(analyteKey).allowedUnits
        .sortedBy(BloodUnitKey::ordinal)
}

internal fun defaultCalibrationUnitFor(analyteKey: BloodAnalyteKey): BloodUnitKey {
    return calibrationAllowedUnitsFor(analyteKey).first()
}

private fun calibrationAnalyteSortIndex(
    draft: CalibrationResultDraftUiState,
): Int {
    return calibrationAnalytes.indexOf(draft.analyteKey).takeIf { index ->
        index >= 0
    } ?: Int.MAX_VALUE
}

private fun CalibrationEditorUiState.toCollectedAtInstant(zoneId: ZoneId): Instant {
    return LocalDateTime.of(collectedDate, collectedTime)
        .atZone(zoneId)
        .toInstant()
}
