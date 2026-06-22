package com.mkx.hrttracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.materialkolor.ktx.harmonize
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey

// Derive darkness from the resolved theme, not isSystemInDarkTheme(): on API < 31 the
// in-app dark-mode override is applied only via AppCompatDelegate.setDefaultNightMode and
// is never baked into the Configuration isSystemInDarkTheme() reads, so the system value
// ignores the override. The hero background colors resolve the same way.
@Composable
fun isAppInDarkTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

@Composable
fun rememberMedicationGroupColorScheme(
    darkTheme: Boolean = isAppInDarkTheme(),
    colorKey: MedicationGroupColorKey?,
    harmonize: Boolean = false,
): ColorScheme {
    // Read the base scheme outside remember so a theme change (its identity flips) re-derives;
    // the HCT math + copy() below is otherwise pure in (baseScheme, darkTheme, colorKey,
    // harmonize), so caching it skips the per-recomposition work on every row that uses it.
    val baseScheme = MaterialTheme.colorScheme
    return remember(baseScheme, darkTheme, colorKey, harmonize) {
        val palette = MedicationGroupPalettes.getValue(colorKey)
        val container = if (darkTheme) palette.darkContainer else palette.lightContainer
        val accent = if (darkTheme) palette.darkAccent else palette.lightAccent
        val onContainer = if (darkTheme) palette.darkOnContainer else palette.lightOnContainer
        val onPrimary = if (darkTheme) palette.lightOnContainer else palette.darkOnContainer
        val (finalContainer, finalAccent, finalOnContainer, finalOnPrimary) = if (harmonize) {
            val target = baseScheme.primary
            listOf(container, accent, onContainer, onPrimary).map { it.harmonize(target) }
        } else {
            listOf(container, accent, onContainer, onPrimary)
        }
        baseScheme.copy(
            primary = finalAccent.desaturateHct(),
            onPrimary = finalOnPrimary,
            primaryContainer = finalContainer.desaturateHct(),
            onPrimaryContainer = finalAccent.desaturateHct(),
            // Carries the high-contrast text-on-container color (used for chip body text;
            // icons keep using onPrimaryContainer = accent).
            onPrimaryFixed = finalOnContainer,
        )
    }
}

internal data class HuePalette(
    val lightContainer: Color,
    val lightAccent: Color,
    val lightOnContainer: Color,
    val darkContainer: Color,
    val darkAccent: Color,
    val darkOnContainer: Color,
)

internal val MedicationGroupPalettes: Map<MedicationGroupColorKey?, HuePalette> = mapOf(
    null to HuePalette(
        // slate · L4/11/12 D3/11/12
        lightContainer = Color(0xFFE7E8EC),
        lightAccent = Color(0xFF60646C),
        lightOnContainer = Color(0xFF1C2024),
        darkContainer = Color(0xFF212225),
        darkAccent = Color(0xFFB0B4BA),
        darkOnContainer = Color(0xFFEDEEF0),
    ),
    MedicationGroupColorKey.ROSE to HuePalette(
        // red · L4/11/12 D3/11/12
        lightContainer = Color(0xFFFFDBDC),
        lightAccent = Color(0xFFCE2C31),
        lightOnContainer = Color(0xFF641723),
        darkContainer = Color(0xFF3B1219),
        darkAccent = Color(0xFFFF8A88),
        darkOnContainer = Color(0xFFFFD1D9),
    ),
    MedicationGroupColorKey.CORAL to HuePalette(
        // orange · L4/11/12 D3/11/12
        lightContainer = Color(0xFFFFDDA9),
        lightAccent = Color(0xFFD14E00),
        lightOnContainer = Color(0xFF582D1D),
        darkContainer = Color(0xFF331E0B),
        darkAccent = Color(0xFFFF9B52),
        darkOnContainer = Color(0xFFFFE0C2),
    ),
    MedicationGroupColorKey.AMBER to HuePalette(
        // amber · L4/11/12 D3/9/12
        lightContainer = Color(0xFFFFEE9C),
        lightAccent = Color(0xFFAD6200),
        lightOnContainer = Color(0xFF4F3422),
        darkContainer = Color(0xFF302008),
        darkAccent = Color(0xFFFFC100),
        darkOnContainer = Color(0xFFFFE7B3),
    ),
    MedicationGroupColorKey.CITRON to HuePalette(
        // lime · L4/11/12 D3/9/12
        lightContainer = Color(0xFFE2F0BD),
        lightAccent = Color(0xFF5C7C2F),
        lightOnContainer = Color(0xFF37401C),
        darkContainer = Color(0xFF1F2917),
        darkAccent = Color(0xFFBDEE63),
        darkOnContainer = Color(0xFFE3F7BA),
    ),
    MedicationGroupColorKey.SAGE to HuePalette(
        // grass · L4/11/12 D3/11/12
        lightContainer = Color(0xFFDAF0DB),
        lightAccent = Color(0xFF2A7E3B),
        lightOnContainer = Color(0xFF203C25),
        darkContainer = Color(0xFF1B2A1E),
        darkAccent = Color(0xFF71D083),
        darkOnContainer = Color(0xFFC2F0C2),
    ),
    MedicationGroupColorKey.TEAL to HuePalette(
        // teal · L4/11/12 D3/11/12
        lightContainer = Color(0xFFCCF3EA),
        lightAccent = Color(0xFF00826D),
        lightOnContainer = Color(0xFF0D3D38),
        darkContainer = Color(0xFF0D2D2A),
        darkAccent = Color(0xFF0AD8B6),
        darkOnContainer = Color(0xFFADF0DD),
    ),
    MedicationGroupColorKey.SKY to HuePalette(
        // sky · L4/11/12 D3/9/12
        lightContainer = Color(0xFFD1F0FB),
        lightAccent = Color(0xFF00749E),
        lightOnContainer = Color(0xFF1D3E56),
        darkContainer = Color(0xFF112840),
        darkAccent = Color(0xFF7CE2FE),
        darkOnContainer = Color(0xFFC2F3FF),
    ),
    MedicationGroupColorKey.INDIGO to HuePalette(
        // indigo · L4/11/12 D3/11/12
        lightContainer = Color(0xFFE0E9FF),
        lightAccent = Color(0xFF3A5BC7),
        lightOnContainer = Color(0xFF1F2D5C),
        darkContainer = Color(0xFF182449),
        darkAccent = Color(0xFF9DB1FF),
        darkOnContainer = Color(0xFFD6E1FF),
    ),
    MedicationGroupColorKey.VIOLET to HuePalette(
        // purple · L4/11/12 D3/11/12
        lightContainer = Color(0xFFF2E2FC),
        lightAccent = Color(0xFF8145B5),
        lightOnContainer = Color(0xFF402060),
        darkContainer = Color(0xFF2F1C3B),
        darkAccent = Color(0xFFD59CFF),
        darkOnContainer = Color(0xFFECD9FA),
    ),
    MedicationGroupColorKey.PLUM to HuePalette(
        // pink · L4/11/12 D3/11/12
        lightContainer = Color(0xFFFBDCEF),
        lightAccent = Color(0xFFC1298A),
        lightOnContainer = Color(0xFF651249),
        darkContainer = Color(0xFF37172F),
        darkAccent = Color(0xFFFF80CA),
        darkOnContainer = Color(0xFFFDD1EA),
    ),
)
