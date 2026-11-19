package com.mkx.hrttracker.data.backup

import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.settings.BackupRestoreEvent
import com.mkx.hrttracker.ui.settings.messageRes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Verifies the bounded read that backs restore file loading: input up to the
 * cap is returned intact, and anything larger is rejected, not buffered
 * into memory, as a plain failure ("Unable to restore"): it may be a backup.
 * See notes/featherline_fix_list.md issue 2.
 */
class BackupRestoreBoundedReadTest {

    @Test
    fun readBoundedBytes_returns_input_at_the_limit() {
        val data = ByteArray(1024) { index -> index.toByte() }

        assertArrayEquals(data, readBoundedBytes(ByteArrayInputStream(data), maxBytes = 1024L))
    }

    @Test
    fun readBoundedBytes_rejects_input_larger_than_the_limit_as_a_plain_failure() {
        val data = ByteArray(1025)

        val error = runCatching { readBoundedBytes(ByteArrayInputStream(data), maxBytes = 1024L) }
            .exceptionOrNull()

        assertEquals(
            R.string.settings_backup_restore_unable,
            BackupRestoreEvent.Failure(checkNotNull(error)).messageRes(),
        )
    }
}
