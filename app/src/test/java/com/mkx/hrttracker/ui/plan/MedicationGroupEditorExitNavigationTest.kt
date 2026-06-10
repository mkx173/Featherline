package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exit navigation must be derivable from retained UI state rather than a
 * one-shot completion event: a back-stack pop is silently dropped when it
 * races an in-flight navigation, and a dropped one-shot event left the editor
 * busy-locked forever on a deleted group. Deriving the exit from state lets
 * the navigation effect re-fire when the editor is restored.
 */
class MedicationGroupEditorExitNavigationTest {

    @Test
    fun editorStaysPut_whileNoCompletionFlagIsSet() {
        assertNull(
            resolveMedicationGroupEditorExitNavigationTarget(
                uiState = MedicationGroupEditorUiState(),
                openedFromArchivedGroupsPage = false,
            )
        )
    }

    @Test
    fun editorStaysPut_whileSaveOrDeleteIsStillInFlight() {
        // In-flight work is not a completion: navigating out early would
        // abandon the editor before the repository reports back.
        assertNull(
            resolveMedicationGroupEditorExitNavigationTarget(
                uiState = MedicationGroupEditorUiState(
                    isSaving = true,
                    isDeleting = true,
                ),
                openedFromArchivedGroupsPage = false,
            )
        )
    }

    @Test
    fun deleteOrArchiveCompletion_navigatesBack_regardlessOfLaunchOrigin() {
        val finishedState = MedicationGroupEditorUiState(
            isDeleted = true,
            isFinishingAfterDeleteOrArchive = true,
        )

        assertEquals(
            MedicationGroupEditorSaveNavigationTarget.BACK,
            resolveMedicationGroupEditorExitNavigationTarget(
                uiState = finishedState,
                openedFromArchivedGroupsPage = true,
            )
        )
    }

    @Test
    fun saveCompletion_navigatesBack_byDefault() {
        assertEquals(
            MedicationGroupEditorSaveNavigationTarget.BACK,
            resolveMedicationGroupEditorExitNavigationTarget(
                uiState = MedicationGroupEditorUiState(
                    isSaved = true,
                    isFinishingAfterSave = true,
                ),
                openedFromArchivedGroupsPage = false,
            )
        )
    }

    @Test
    fun navigationLocked_fromMutationStart_untilExitFires() {
        // The lock must hold continuously from the moment the user confirms a
        // mutation until the exit pop fires, so a chrome tap landing right
        // after the confirm dialog closes cannot navigate away mid-delete.
        assertTrue(
            isMedicationGroupEditorNavigationLocked(
                MedicationGroupEditorUiState(isDeleting = true)
            )
        )
        assertTrue(
            isMedicationGroupEditorNavigationLocked(
                MedicationGroupEditorUiState(
                    isDeleted = true,
                    isFinishingAfterDeleteOrArchive = true,
                )
            )
        )
        assertTrue(
            isMedicationGroupEditorNavigationLocked(
                MedicationGroupEditorUiState(isSaving = true)
            )
        )
    }

    @Test
    fun navigationNotLocked_whileMerelyLoadingOrEditing() {
        // Initial load deliberately does not lock: a slow group load must not
        // trap the user on the editor.
        assertFalse(
            isMedicationGroupEditorNavigationLocked(
                MedicationGroupEditorUiState(isLoadingGroupForEditing = true)
            )
        )
        assertFalse(
            isMedicationGroupEditorNavigationLocked(MedicationGroupEditorUiState())
        )
    }

    @Test
    fun saveCompletion_fromArchivedGroupsPage_returnsToPlanForRecreatedActiveGroup() {
        assertEquals(
            MedicationGroupEditorSaveNavigationTarget.PLAN,
            resolveMedicationGroupEditorExitNavigationTarget(
                uiState = MedicationGroupEditorUiState(
                    isSaved = true,
                    isFinishingAfterSave = true,
                    isArchived = false,
                ),
                openedFromArchivedGroupsPage = true,
            )
        )
    }
}
