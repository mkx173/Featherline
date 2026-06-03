package com.mkx.hrttracker.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the Auto Backup / device-transfer exclusion rules against drift.
 *
 * Every file that holds sensitive health data — the SQLCipher DB and its
 * WAL/SHM, the secure-storage SharedPreferences, every DataStore, and the
 * diagnostics dir — must be excluded from BOTH cloud backup and device
 * transfer, or that data silently becomes eligible for upload to the user's
 * Google account. The rules are a denylist, so the dangerous failure mode is
 * a new persistence file (or a new exclusion section) that someone forgets to
 * cover everywhere.
 *
 * This test pins the exclusion set and asserts all three sections
 * (`full-backup-content`, `cloud-backup`, `device-transfer`) match it exactly.
 * Adding a new DataStore/file is therefore a deliberate three-place edit plus
 * an update here — see notes/featherline_fix_list.md issue 3 and docs/privacy.md.
 */
class BackupExclusionRulesTest {

    private data class Exclusion(val domain: String, val path: String)

    private val expectedExclusions = setOf(
        Exclusion("database", "hrt_tracker.db"),
        Exclusion("database", "hrt_tracker.db-shm"),
        Exclusion("database", "hrt_tracker.db-wal"),
        Exclusion("sharedpref", "hrt_tracker_secure_storage.xml"),
        Exclusion("file", "datastore/settings.preferences_pb"),
        Exclusion("file", "datastore/home_snapshot.pb"),
        Exclusion("file", "datastore/widget_snapshot.pb"),
        Exclusion("file", "datastore/home_snapshot_metadata.preferences_pb"),
        Exclusion("file", "datastore/reminder_schedule.preferences_pb"),
        Exclusion("file", "datastore/medication_reminder_snoozes.preferences_pb"),
        Exclusion("file", "diagnostics"),
    )

    @Test
    fun full_backup_content_excludes_every_sensitive_store() {
        assertEquals(
            expectedExclusions,
            excludesIn("src/main/res/xml/backup_rules.xml", "full-backup-content"),
        )
    }

    @Test
    fun cloud_backup_excludes_every_sensitive_store() {
        assertEquals(
            expectedExclusions,
            excludesIn("src/main/res/xml/data_extraction_rules.xml", "cloud-backup"),
        )
    }

    @Test
    fun device_transfer_excludes_every_sensitive_store() {
        assertEquals(
            expectedExclusions,
            excludesIn("src/main/res/xml/data_extraction_rules.xml", "device-transfer"),
        )
    }

    private fun excludesIn(relativePath: String, sectionTag: String): Set<Exclusion> {
        val file = File(relativePath)
        require(file.isFile) {
            "Missing backup rules file at ${file.absolutePath}; unit tests must run " +
                "with the app module as the working directory."
        }
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(file)
        val sections = document.getElementsByTagName(sectionTag)
        require(sections.length == 1) {
            "Expected exactly one <$sectionTag> in ${file.name}, found ${sections.length}."
        }
        val excludeNodes = (sections.item(0) as Element).getElementsByTagName("exclude")
        return (0 until excludeNodes.length)
            .map { index ->
                val element = excludeNodes.item(index) as Element
                Exclusion(
                    domain = element.getAttribute("domain"),
                    path = element.getAttribute("path"),
                )
            }
            .toSet()
    }
}
