package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class PlanBatchAddScreenSourceTest {
    @Test
    fun rangeSelectorConfirmButtonUsesListAddDrawable() {
        val source = Files.readString(planBatchAddScreenSourcePath())
        val rangeSelector = source
            .substringAfter("private fun PlanBatchAddRangeSelector(")
            .substringBefore("\n@Composable\nprivate fun PlanBatchAddStockSection(")
        val confirmButton = rangeSelector
            .substringAfter("HrtButton(")
            .substringBefore("\n        )")

        assertTrue(
            confirmButton.contains(
                "text = stringResource(R.string.plan_batch_add_confirm_button),"
            ),
        )
        assertTrue(
            confirmButton.contains(
                "icon = ImageVector.vectorResource(R.drawable.ic_list_alt_add),"
            ),
        )
        assertFalse(confirmButton.contains("icon = Icons.Rounded.Add,"))
    }

    private fun planBatchAddScreenSourcePath(): Path {
        val userDir = Path.of(System.getProperty("user.dir"))
        return listOf(
            userDir.resolve("src/main/java/com/mkx/hrttracker/ui/plan/PlanBatchAddScreen.kt"),
            userDir.resolve("app/src/main/java/com/mkx/hrttracker/ui/plan/PlanBatchAddScreen.kt"),
        ).first(Files::exists)
    }
}
