package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanRegimenNotificationIconTest {
    @Test
    fun shouldShowRegimenNotificationIcon_shows_when_reminders_are_enabled() {
        assertTrue(
            shouldShowRegimenNotificationIcon(
                remindersEnabled = true,
                hasNotificationAccess = true
            )
        )
    }

    @Test
    fun shouldShowRegimenNotificationIcon_shows_when_permission_is_off() {
        assertTrue(
            shouldShowRegimenNotificationIcon(
                remindersEnabled = false,
                hasNotificationAccess = false
            )
        )
    }

    @Test
    fun shouldShowRegimenNotificationIcon_hides_when_master_is_off_and_permission_is_available() {
        assertFalse(
            shouldShowRegimenNotificationIcon(
                remindersEnabled = false,
                hasNotificationAccess = true
            )
        )
    }

    @Test
    fun isRegimenNotificationIconEnabled_requires_master_permission_and_group_enabled() {
        assertTrue(
            isRegimenNotificationIconEnabled(
                remindersEnabled = true,
                hasNotificationAccess = true,
                groupNotificationsEnabled = true
            )
        )
        assertFalse(
            isRegimenNotificationIconEnabled(
                remindersEnabled = true,
                hasNotificationAccess = false,
                groupNotificationsEnabled = true
            )
        )
        assertFalse(
            isRegimenNotificationIconEnabled(
                remindersEnabled = true,
                hasNotificationAccess = true,
                groupNotificationsEnabled = false
            )
        )
        assertFalse(
            isRegimenNotificationIconEnabled(
                remindersEnabled = false,
                hasNotificationAccess = true,
                groupNotificationsEnabled = true
            )
        )
    }
}
