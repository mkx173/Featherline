package com.mkx.hrttracker.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupSnapshotJsonCodecTest {

    // A real v1 backup lacks the top-level `medicines` array that v2 added as
    // a required field. Full decode would throw on the missing field; the peek
    // path must still extract `snapshotVersion` so the restore flow can raise
    // the intended unsupported-version error.
    @Test
    fun peekSnapshotVersion_extractsVersionFromV1ShapeMissingMedicines() {
        val v1Json = """
            {
              "snapshotVersion": 1,
              "exportedAtEpochMillis": 0,
              "app": { "packageName": "com.mkx.hrttracker" }
            }
        """.trimIndent()

        assertEquals(1, BackupSnapshotJsonCodec.peekSnapshotVersion(v1Json))
    }

    @Test
    fun peekSnapshotVersion_returnsZeroWhenFieldAbsent() {
        assertEquals(0, BackupSnapshotJsonCodec.peekSnapshotVersion("""{"exportedAtEpochMillis":0}"""))
    }

    @Test
    fun peekSnapshotVersion_returnsNullForJsonNullLiteral() {
        assertNull(BackupSnapshotJsonCodec.peekSnapshotVersion("null"))
    }
}
