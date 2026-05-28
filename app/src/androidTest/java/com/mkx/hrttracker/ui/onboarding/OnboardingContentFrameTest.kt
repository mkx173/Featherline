package com.mkx.hrttracker.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.ui.components.AppContentMaxWidth
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Rule
import org.junit.Test

class OnboardingContentFrameTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentWidthIsConstrainedOnTablet() {
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                DeviceConfigurationOverride(
                    DeviceConfigurationOverride.ForcedSize(
                        DpSize(width = 800.dp, height = 600.dp)
                    )
                ) {
                    Box(Modifier.fillMaxSize()) {
                        OnboardingContentFrame(
                            modifier = Modifier.testTag(ContentFrameTag),
                        ) {}
                    }
                }
            }
        }

        composeRule
            .onNodeWithTag(ContentFrameTag, useUnmergedTree = true)
            .assertWidthIsEqualTo(AppContentMaxWidth)
            .assertLeftPositionInRootIsEqualTo((800.dp - AppContentMaxWidth) / 2)
    }

    @Test
    fun contentWidthFillsPhoneWidth() {
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                DeviceConfigurationOverride(
                    DeviceConfigurationOverride.ForcedSize(
                        DpSize(width = 420.dp, height = 600.dp)
                    )
                ) {
                    Box(Modifier.fillMaxSize()) {
                        OnboardingContentFrame(
                            modifier = Modifier.testTag(ContentFrameTag),
                        ) {}
                    }
                }
            }
        }

        composeRule
            .onNodeWithTag(ContentFrameTag, useUnmergedTree = true)
            .assertWidthIsEqualTo(420.dp)
            .assertLeftPositionInRootIsEqualTo(0.dp)
    }
}

private const val ContentFrameTag = "onboarding-content-frame"
