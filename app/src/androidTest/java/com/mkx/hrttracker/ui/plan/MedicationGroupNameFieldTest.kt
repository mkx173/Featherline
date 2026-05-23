package com.mkx.hrttracker.ui.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Rule
import org.junit.Test

class MedicationGroupNameFieldTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun groupNameFieldShowsDefaultNamePlaceholderAfterItResolves() {
        var defaultGroupName by mutableStateOf("")

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MedicationGroupNameField(
                    groupName = "",
                    defaultGroupName = defaultGroupName,
                    isEditing = false,
                    selectedColorKey = MedicationGroupColorKey.ROSE,
                    enabled = true,
                    focusRequester = remember { FocusRequester() },
                    onGroupNameChange = { },
                    onColorSelected = { },
                )
            }
        }

        composeRule.onNodeWithText("Group 1", useUnmergedTree = true).assertDoesNotExist()

        composeRule.runOnIdle {
            defaultGroupName = "Group 1"
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Group 1", useUnmergedTree = true).assertIsDisplayed()
    }
}
