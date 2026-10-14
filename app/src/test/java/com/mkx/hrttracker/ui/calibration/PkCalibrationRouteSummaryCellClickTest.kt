package com.mkx.hrttracker.ui.calibration

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.util.labelRes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A route sub-card in the section's route card opens the routes sheet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PkCalibrationRouteSummaryCellClickTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun routeSubCard_opensTheRoutesSheet() {
        var routeOpens = 0
        composeRule.setContent {
            PkCalibrationSection(
                uiState = previewPkAdjustedUiState,
                reviewCount = 0,
                onRetry = {},
                onOpenRoutes = { routeOpens += 1 },
                onOpenReview = {},
                onInfo = {},
            )
        }
        composeRule.onNodeWithText(context.getString(PkCalibrationRoute.GEL.applicationType.labelRes))
            .performClick()
        assertEquals(1, routeOpens)
    }
}
