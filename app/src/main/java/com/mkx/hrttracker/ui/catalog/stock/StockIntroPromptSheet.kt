package com.mkx.hrttracker.ui.catalog.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.ui.medication.medicineDisplayName

data class StockIntroEntry(
    val medicine: Medicine,
    val initialCount: Double?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockIntroPromptSheet(
    medicines: List<Medicine>,
    onDone: (List<StockIntroEntry>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val countTexts = remember(medicines) { mutableStateMapOf<String, String>() }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.stock_intro_prompt_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.stock_intro_prompt_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                medicines.forEach { medicine ->
                    val key = medicine.uuid.toString()
                    val value = countTexts[key].orEmpty()
                    StockIntroMedicineRow(
                        medicine = medicine,
                        value = value,
                        onValueChange = { countTexts[key] = it },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.stock_intro_prompt_not_now))
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = {
                        onDone(
                            medicines.map { medicine ->
                                StockIntroEntry(
                                    medicine = medicine,
                                    initialCount = countTexts[medicine.uuid.toString()]
                                        .toNonNegativeDoubleOrNull(),
                                )
                            }
                        )
                    },
                ) {
                    Text(stringResource(R.string.stock_intro_prompt_done))
                }
            }
        }
    }
}

@Composable
private fun StockIntroMedicineRow(
    medicine: Medicine,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = medicineDisplayName(medicine),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(stringResource(R.string.stock_intro_prompt_hint))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier
                .widthIn(min = 104.dp, max = 128.dp)
                .testTag("stock-intro-count-${medicine.uuid}"),
        )
    }
}

private fun String?.toNonNegativeDoubleOrNull(): Double? {
    return this
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.toDoubleOrNull()
        ?.takeIf { value -> value.isFinite() && value >= 0.0 }
}
