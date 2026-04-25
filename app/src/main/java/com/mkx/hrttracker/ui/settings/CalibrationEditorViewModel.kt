package com.mkx.hrttracker.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodTestResultInput
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.bloodtest.CustomBloodAnalyte
import com.mkx.hrttracker.model.medication.findLastEstradiolEntry
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.model.settings.calibrationDefaultUnitFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

internal val calibrationAnalytes = listOf(
    BloodAnalyteKey.E2,
    BloodAnalyteKey.T,
    BloodAnalyteKey.PROG,
    BloodAnalyteKey.PRL,
    BloodAnalyteKey.FSH,
    BloodAnalyteKey.LH,
)

internal val defaultCalibrationAnalytes = listOf(
    BloodAnalyteKey.E2,
    BloodAnalyteKey.T,
)

@HiltViewModel
class CalibrationEditorViewModel @Inject constructor(
    private val bloodTestRepository: BloodTestRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val editingPanelUuid = savedStateHandle.get<String>(PANEL_ID_ARG)?.let { panelId ->
        runCatching { UUID.fromString(panelId) }.getOrNull()
    }
    private val defaultZoneId = ZoneId.systemDefault()
    private var latestSettingsState = SettingsState()
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
        observeCalibrationDefaultUnits()
        refreshAvailableCustomAnalytes()
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
        updateDraftValue(
            matchesDraft = { draft -> draft.analyteKey == analyteKey },
            value = value,
        )
    }

    fun updateCustomAnalyteValue(customAnalyteUuid: UUID, value: String) {
        updateDraftValue(
            matchesDraft = { draft -> draft.customAnalyteUuid == customAnalyteUuid },
            value = value,
        )
    }

    private fun updateDraftValue(
        matchesDraft: (CalibrationResultDraftUiState) -> Boolean,
        value: String,
    ) {
        _uiState.update { state ->
            state.copy(
                drafts = state.drafts.map { draft ->
                    if (matchesDraft(draft)) {
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
                        unit = defaultCalibrationUnitFor(analyteKey, latestSettingsState),
                        defaultUnit = defaultCalibrationUnitFor(analyteKey, latestSettingsState),
                    )).sortedBy(::calibrationAnalyteSortIndex)
                )
            }
        }
    }

    fun addCustomAnalyte(customAnalyte: CustomBloodAnalyte) {
        _uiState.update { state ->
            if (state.drafts.any { draft -> draft.customAnalyteUuid == customAnalyte.uuid }) {
                state
            } else {
                state.copy(
                    drafts = (state.drafts + CalibrationResultDraftUiState(
                        customAnalyteUuid = customAnalyte.uuid,
                        customAnalyteAbbreviation = customAnalyte.abbreviation,
                        customAnalyteName = customAnalyte.name,
                        customUnitLabel = customAnalyte.unitLabel,
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

    fun removeCustomAnalyte(customAnalyteUuid: UUID) {
        _uiState.update { state ->
            state.copy(
                drafts = state.drafts.filterNot { draft ->
                    draft.customAnalyteUuid == customAnalyteUuid
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

    private fun observeCalibrationDefaultUnits() {
        viewModelScope.launch {
            settingsRepository.settingsState.collect { settingsState ->
                latestSettingsState = settingsState
                _uiState.update { state ->
                    val draftsWithUpdatedDefaults = state.drafts.map { draft ->
                        draft.analyteKey?.let { analyteKey ->
                            draft.copy(
                                defaultUnit = defaultCalibrationUnitFor(
                                    analyteKey,
                                    settingsState,
                                )
                            )
                        } ?: draft
                    }
                    if (
                        state.isEditing ||
                        state.notes.isNotBlank() ||
                        state.drafts.any { draft ->
                            draft.resultUuid != null || draft.valueText.isNotBlank()
                        }
                    ) {
                        state.copy(drafts = draftsWithUpdatedDefaults)
                    } else {
                        state.copy(
                            drafts = draftsWithUpdatedDefaults.map { draft ->
                                draft.analyteKey?.let {
                                    draft.copy(unit = draft.defaultUnit)
                                } ?: draft
                            }
                        )
                    }
                }
            }
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

            _uiState.update { state ->
                panel.toEditorState().copy(customAnalytes = state.customAnalytes)
            }
            refreshTimeSinceLastEstradiolDose()
        }
    }

    private fun refreshAvailableCustomAnalytes() {
        viewModelScope.launch {
            val customAnalytes = runCatching {
                bloodTestRepository.getActiveCustomAnalytes()
            }.getOrDefault(emptyList())

            _uiState.update { state ->
                state.copy(customAnalytes = customAnalytes)
            }
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
    ): List<BloodTestResultInput> {
        return state.drafts.mapNotNull { draft -> draft.toResultInput() }
    }

    private fun CalibrationResultDraftUiState.toResultInput(): BloodTestResultInput? {
        val parsedValue = parseCalibrationNumericInput(valueText) ?: return null
        return analyteKey?.let { builtinAnalyteKey ->
            BloodTestResultInput.Builtin(
                uuid = resultUuid,
                analyteKey = builtinAnalyteKey,
                unit = checkNotNull(unit),
                value = parsedValue,
            )
        } ?: BloodTestResultInput.Custom(
            uuid = resultUuid,
            customAnalyteUuid = checkNotNull(customAnalyteUuid),
            value = parsedValue,
        )
    }

    private fun BloodTestPanel.toEditorState(): CalibrationEditorUiState {
        val collectedDateTime = collectedAt.atZone(defaultZoneId).toLocalDateTime()
        val drafts = results.map { result ->
            when (val analyte = result.analyte) {
                is BloodTestResultAnalyte.Builtin -> {
                    val storedUnit = BloodUnitKey.fromStorageValue(result.unitSnapshot)
                    CalibrationResultDraftUiState(
                        analyteKey = analyte.key,
                        resultUuid = result.uuid,
                        valueText = formatCalibrationNumericValue(result.value),
                        unit = storedUnit ?: defaultCalibrationUnitFor(analyte.key, latestSettingsState),
                        defaultUnit = defaultCalibrationUnitFor(analyte.key, latestSettingsState),
                        originalUnit = storedUnit,
                    )
                }

                is BloodTestResultAnalyte.Custom -> CalibrationResultDraftUiState(
                    customAnalyteUuid = analyte.uuid,
                    customAnalyteAbbreviation = analyte.abbreviation,
                    customAnalyteName = analyte.name,
                    customUnitLabel = result.unitSnapshot,
                    resultUuid = result.uuid,
                    valueText = formatCalibrationNumericValue(result.value),
                )
            }
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
    val customAnalytes: List<CustomBloodAnalyte> = emptyList(),
    val drafts: List<CalibrationResultDraftUiState> = defaultCalibrationAnalytes.map { analyteKey ->
        CalibrationResultDraftUiState(analyteKey = analyteKey)
    },
)

data class CalibrationResultDraftUiState(
    val analyteKey: BloodAnalyteKey? = null,
    val customAnalyteUuid: UUID? = null,
    val customAnalyteAbbreviation: String? = null,
    val customAnalyteName: String? = null,
    val customUnitLabel: String? = null,
    val resultUuid: UUID? = null,
    val valueText: String = "",
    val unit: BloodUnitKey? = analyteKey?.let(::defaultCalibrationUnitFor),
    val defaultUnit: BloodUnitKey? = analyteKey?.let(::defaultCalibrationUnitFor),
    val originalUnit: BloodUnitKey? = null,
) {
    val draftKey: String
        get() = analyteKey?.storageValue ?: "custom:${checkNotNull(customAnalyteUuid)}"
}

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
    val presentAnalytes = state.drafts.mapNotNull(CalibrationResultDraftUiState::analyteKey).toSet()
    return calibrationAnalytes.filterNot(presentAnalytes::contains)
}

internal sealed interface CalibrationAddAnalyteOption {
    val optionKey: String

    data class Builtin(
        val analyteKey: BloodAnalyteKey,
    ) : CalibrationAddAnalyteOption {
        override val optionKey: String = analyteKey.storageValue
    }

    data class Custom(
        val customAnalyte: CustomBloodAnalyte,
    ) : CalibrationAddAnalyteOption {
        override val optionKey: String = "custom:${customAnalyte.uuid}"
    }
}

internal fun calibrationAddAnalyteOptions(
    state: CalibrationEditorUiState,
): List<CalibrationAddAnalyteOption> {
    val presentBuiltinAnalytes = state.drafts.mapNotNull(CalibrationResultDraftUiState::analyteKey).toSet()
    val presentCustomAnalytes = state.drafts.mapNotNull(CalibrationResultDraftUiState::customAnalyteUuid).toSet()
    val builtinOptions = calibrationAnalytes
        .filterNot(presentBuiltinAnalytes::contains)
        .map(CalibrationAddAnalyteOption::Builtin)
    val customOptions = state.customAnalytes
        .filterNot { customAnalyte -> customAnalyte.uuid in presentCustomAnalytes }
        .map(CalibrationAddAnalyteOption::Custom)
    return builtinOptions + customOptions
}

internal fun calibrationAllowedUnitsFor(analyteKey: BloodAnalyteKey): List<BloodUnitKey> {
    val canonicalUnit = BloodTestCatalog.canonicalUnitFor(analyteKey)
    return BloodTestCatalog.definitionFor(analyteKey).allowedUnits
        .sortedWith(compareBy<BloodUnitKey>({ it != canonicalUnit }, BloodUnitKey::ordinal))
}

internal fun defaultCalibrationUnitFor(
    analyteKey: BloodAnalyteKey,
    settingsState: SettingsState = SettingsState(),
): BloodUnitKey {
    return settingsState.calibrationDefaultUnitFor(analyteKey)
}

private fun calibrationAnalyteSortIndex(
    draft: CalibrationResultDraftUiState,
): Int {
    val analyteKey = draft.analyteKey ?: return Int.MAX_VALUE
    return calibrationAnalytes.indexOf(analyteKey).takeIf { index ->
        index >= 0
    } ?: Int.MAX_VALUE
}

private fun CalibrationEditorUiState.toCollectedAtInstant(zoneId: ZoneId): Instant {
    return LocalDateTime.of(collectedDate, collectedTime)
        .atZone(zoneId)
        .toInstant()
}
