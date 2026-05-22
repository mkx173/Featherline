package com.mkx.hrttracker.widget

import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class HrtWidgetRowGroupingTest {
    @Test
    fun nullGroupUuidRowsAtSameTimeRemainSeparate() {
        val scheduledAt = LocalDateTime.of(2026, 1, 1, 9, 0)
        val rows = listOf(
            widgetDoseRow(
                medicationName = "Cyproterone acetate",
                status = WidgetDoseStatus.DONE,
                scheduledAt = scheduledAt,
                groupUuid = null,
            ),
            widgetDoseRow(
                medicationName = "Estradiol valerate",
                status = WidgetDoseStatus.DUE_SOON,
                scheduledAt = scheduledAt,
                groupUuid = null,
            ),
        )

        val groups = groupRowsByScheduledGroupSlot(rows)

        assertEquals(listOf(listOf(rows[0]), listOf(rows[1])), groups)
    }

    @Test
    fun rowsWithSameGroupUuidAndTimeCollapseTogether() {
        val scheduledAt = LocalDateTime.of(2026, 1, 1, 9, 0)
        val rows = listOf(
            widgetDoseRow(
                medicationName = "Estradiol valerate",
                status = WidgetDoseStatus.DONE,
                scheduledAt = scheduledAt,
                groupUuid = "group-1",
            ),
            widgetDoseRow(
                medicationName = "Progesterone",
                status = WidgetDoseStatus.DUE_SOON,
                scheduledAt = scheduledAt,
                groupUuid = "group-1",
            ),
        )

        val groups = groupRowsByScheduledGroupSlot(rows)

        assertEquals(listOf(rows), groups)
    }

    private fun widgetDoseRow(
        medicationName: String,
        status: WidgetDoseStatus,
        scheduledAt: LocalDateTime,
        groupUuid: String?,
    ): WidgetDoseRow {
        return WidgetDoseRow(
            medicationName = medicationName,
            groupName = medicationName,
            colorKey = MedicationGroupColorKey.ROSE,
            routeLabel = "Oral",
            doseText = "2 mg",
            status = status,
            scheduledAt = scheduledAt,
            trailingText = null,
            isManualRecord = false,
            contextChip = null,
            groupUuid = groupUuid,
            scheduleTimeUuid = null,
        )
    }
}
