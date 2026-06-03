package com.mkx.hrttracker.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Verifies the bounded read that backs restore file loading: input up to the
 * cap is returned intact, and anything larger is rejected as an
 * [IncompatibleBackupFileException] — the type the picker maps to the
 * "not a compatible backup" message — rather than buffered into memory.
 * See notes/featherline_fix_list.md issue 2.
 */
class BackupRestoreBoundedReadTest {

    @Test
    fun readBoundedBytes_returns_input_at_the_limit() {
        val data = ByteArray(1024) { index -> index.toByte() }

        assertArrayEquals(data, readBoundedBytes(ByteArrayInputStream(data), maxBytes = 1024L))
    }

    @Test
    fun readBoundedBytes_rejects_input_larger_than_the_limit_as_incompatible() {
        val data = ByteArray(1025)

        try {
            readBoundedBytes(ByteArrayInputStream(data), maxBytes = 1024L)
            fail("Expected input larger than the cap to be rejected.")
        } catch (_: IncompatibleBackupFileException) {
            // Expected — surfaces to the UI as an incompatible file, not a
            // generic "wrong password or corrupted" failure.
        }
    }
}
