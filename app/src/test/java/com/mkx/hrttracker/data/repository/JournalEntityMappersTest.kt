package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.TrackedDateEntity
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class JournalEntityMappersTest {
    @Test
    fun trackedDateEntity_toModel_parsesKeysAndDate() {
        val entity = TrackedDateEntity(
            uuid = "a",
            name = "On estradiol",
            iconKey = "medication",
            dateIso = "2024-04-01",
            paletteKey = "ROSE",
            heroBackgroundKey = null,
            pinnedOrder = 0,
            createdAtEpochMillis = 1000, updatedAtEpochMillis = 1000,
        )
        val model = entity.toModel()
        assertEquals(AnchorIcon.MEDICATION, model.icon)
        assertEquals(LocalDate.of(2024, 4, 1), model.date)
        assertEquals(MedicationGroupColorKey.ROSE, model.palette)
        assertEquals(0, model.pinnedOrder)
        assertEquals(1000L, model.createdAtEpochMillis)
    }

    @Test
    fun trackedDateEntity_unknownKeys_fallBackSafely() {
        val entity = TrackedDateEntity(
            uuid = "a",
            name = "x",
            iconKey = "???",
            dateIso = "2024-04-01",
            paletteKey = "???",
            heroBackgroundKey = null,
            pinnedOrder = null,
            createdAtEpochMillis = 1000, updatedAtEpochMillis = 1000,
        )
        val model = entity.toModel()
        assertEquals(AnchorIcon.EVENT, model.icon)
        assertNull(model.palette)
    }
}
