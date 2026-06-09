package com.mkx.hrttracker.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R

enum class MedicalDisclaimerKind(@param:StringRes val textRes: Int) {
    HOME_ESTIMATES_AND_RANGES(R.string.medical_disclaimer_home_estimates_and_ranges),
    PRESET_DOSES(R.string.medical_disclaimer_preset_doses),
    REFERENCE_RANGES(R.string.medical_disclaimer_reference_ranges),
    WPATH_REFERENCE_RANGES(R.string.medical_disclaimer_wpath_reference_ranges),
    PLASMA_CONCENTRATION_ESTIMATES(R.string.medical_disclaimer_plasma_concentration_estimates),
}

object MedicalDisclaimerSets {
    val home = listOf(MedicalDisclaimerKind.HOME_ESTIMATES_AND_RANGES)
    val medicationEditor = listOf(MedicalDisclaimerKind.PRESET_DOSES)
    val calibration = listOf(MedicalDisclaimerKind.REFERENCE_RANGES)
    val calibrationEditor = listOf(MedicalDisclaimerKind.WPATH_REFERENCE_RANGES)
}

@Composable
fun MedicalDisclaimerText(
    kinds: List<MedicalDisclaimerKind>,
    modifier: Modifier = Modifier,
) {
    if (kinds.isEmpty()) {
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        kinds.forEach { kind ->
            val disclaimerText = stringResource(kind.textRes)
            Text(
                text = disclaimerText,
                modifier = Modifier
                    .fillMaxWidth()
                    .cjkTextOffset(disclaimerText),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
