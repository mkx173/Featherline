package com.mkx.hrttracker.ui.medication

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** O6: the log editor's offset from the plan's slot, as the sheet shows it, counts real minutes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MedicationLogEntryEditorSheetOffsetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun springForward_a0130DoseLoggedAt0310_shows40MinutesLater() {
        // The wall clock says 100 minutes ("+1h").
        showSheet(scheduledFor = LocalDateTime.of(2026, 3, 8, 1, 30), applied = LocalDateTime.of(2026, 3, 8, 3, 10))
        assertShown("+40m")
    }

    @Test
    fun fallBack_a0030DoseLoggedAt0240_shows3HoursLater() {
        // 02:40 EST is 3 h 10 min after 00:30 EDT; the wall clock says 2 h 10 ("+2h").
        showSheet(scheduledFor = LocalDateTime.of(2026, 11, 1, 0, 30), applied = LocalDateTime.of(2026, 11, 1, 2, 40))
        assertShown("+3h")
    }

    @Test
    fun fallBack_a0030DoseLoggedAtTheSecond0140_shows2HoursLater() {
        // 01:40 EST, the second pass, is 06:40Z: 130 minutes after 00:30 EDT. The first pass
        // would say "+1h".
        showSheet(
            scheduledFor = LocalDateTime.of(2026, 11, 1, 0, 30),
            applied = LocalDateTime.of(2026, 11, 1, 1, 40),
            appliedOffset = ZoneOffset.ofHours(-5),
        )
        assertShown("+2h")
    }

    private fun assertShown(offset: String) {
        composeRule.waitForIdle()
        assertEquals(
            1,
            composeRule.onAllNodesWithText(offset, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun showSheet(scheduledFor: LocalDateTime, applied: LocalDateTime, appliedOffset: ZoneOffset? = null) {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
        )
        composeRule.setContent {
            MedicationLogEntryEditorSheet(
                title = "Edit record",
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                confirmButtonText = "Save",
                onDismissRequest = { },
                onCloseClick = { },
                medicineDraft = draft,
                doseInstructionDraft = draft.toDoseInstructionDraft(),
                lockedMedicine = testMedicine(),
                sourceGroupName = "Nightly estradiol",
                sourceGroupColorKey = MedicationGroupColorKey.INDIGO,
                sourceGroupScheduledFor = scheduledFor,
                countText = "1",
                appliedDate = applied.toLocalDate(),
                appliedTime = applied.toLocalTime(),
                appliedZoneId = newYork,
                appliedOffset = appliedOffset,
                onAppliedDateChange = { },
                onAppliedTimeChange = { },
                onConfirm = { },
            )
        }
    }
}
