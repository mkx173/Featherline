package com.mkx.hrttracker.ui.calibration

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.settings.testBloodTestPanel
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.calibrationPanelDateTimeFormatters
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PkCalibrationAcceptanceUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lookingRight_clearsQueueCard_listRowHasNoChip_andRowStillOpensEditor() {
        // The queue card and the list chip must agree: once accepted, the lab
        // leaves the queue and its row looks like any other included result.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val panel = testBloodTestPanel()
        val resultId = panel.results.first().uuid
        val outlier = PkCalibrationLabRowFlag.UnreviewedOutlier(
            resultId, listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL),
        )
        var flag: PkCalibrationLabRowFlag by mutableStateOf(outlier)
        var editorOpens = 0
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                Column {
                    // Queue card.
                    if (flag.needsReview) {
                        CalibrationPanelRow(
                            panel = panel,
                            settingsState = SettingsState(),
                            dateTimeFormatters = calibrationPanelDateTimeFormatters(Locale.US, true),
                            index = 0,
                            count = 1,
                            onClick = { },
                            pkFooter = {
                                PkCalibrationLabRowFooter(
                                    flag = flag,
                                    onCorrect = null,
                                    onExclude = { },
                                    onReinclude = { },
                                    onAccept = { flag = PkCalibrationLabRowFlag.Accepted(resultId) },
                                )
                            },
                        )
                    }
                    // List row.
                    CalibrationPanelRow(
                        panel = panel,
                        settingsState = SettingsState(),
                        dateTimeFormatters = calibrationPanelDateTimeFormatters(Locale.US, true),
                        index = 0,
                        count = 1,
                        onClick = { editorOpens++ },
                        pkChip = { PkCalibrationLabChip(flag) },
                    )
                }
            }
        }
        val check = context.getString(R.string.calibration_pk_lab_chip_check)
        composeRule.onNodeWithText(check).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.calibration_pk_lab_accept)).performClick()

        composeRule.onNodeWithText(context.getString(R.string.calibration_pk_lab_outlier_title)).assertDoesNotExist()
        composeRule.onNodeWithText(check).assertDoesNotExist()
        assertEquals(0, editorOpens)
        composeRule.onNodeWithText(panel.results.first().value.let { "%.0f".format(Locale.US, it) }, substring = true)
            .performClick()
        assertEquals(1, editorOpens)
    }
}
