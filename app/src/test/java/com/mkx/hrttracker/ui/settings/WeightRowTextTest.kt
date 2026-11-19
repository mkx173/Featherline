package com.mkx.hrttracker.ui.settings

import com.mkx.hrttracker.model.personalization.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** O4: the Settings and onboarding weight rows credit Health Connect only for its reading. */
class WeightRowTextTest {
    private val saved = Instant.parse("2026-11-10T08:00:00Z")

    private fun row(profile: UserProfile, summary: String, syncOn: Boolean = true) =
        weightRowText(summary = summary, withSource = "$summary · Health Connect", syncOn = syncOn, profile = profile)

    @Test
    fun aTypedWeightWithSyncOn_namesNoSource() {
        val typed = UserProfile(weightKg = 65.0, weightOriginalValue = 65.0, updatedAt = saved, weightMeasuredAt = saved)
        assertEquals("65 kg", row(typed, "65 kg"))
    }

    @Test
    fun aHealthConnectReading_namesItsSource_onlyWithSyncOn() {
        val reading = UserProfile(
            weightKg = 65.0, weightOriginalValue = 65.0, updatedAt = saved, weightMeasuredAt = saved.minusSeconds(3_600),
        )
        assertEquals("65 kg · Health Connect", row(reading, "65 kg"))
        assertEquals("65 kg", row(reading, "65 kg", syncOn = false))
    }

    @Test
    fun noWeight_isPlainNoRecords() {
        assertEquals("No records", row(UserProfile(updatedAt = saved), "No records"))
    }
}
