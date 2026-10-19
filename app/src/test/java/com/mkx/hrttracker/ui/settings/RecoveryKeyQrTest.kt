package com.mkx.hrttracker.ui.settings

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.mkx.hrttracker.data.backup.RecoveryKey
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryKeyQrTest {
    @Test
    fun qr_decodesBackToTheKey_andIsVersion1WithAQuietZone() {
        val recoveryKey = RecoveryKey.generate()
        val matrix = recoveryKeyQrMatrix(recoveryKey)

        // Version 1 is 21 modules, plus 4 of quiet zone on each side.
        assertEquals(21 + 2 * 4, matrix.width)
        assertEquals(21 + 2 * 4, matrix.height)
        val scale = 4
        val size = matrix.width * scale
        val pixels = IntArray(size * size) { index ->
            if (matrix[(index % size) / scale, (index / size) / scale]) BLACK else WHITE
        }
        val result = QRCodeReader().decode(
            BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels))),
            mapOf(DecodeHintType.PURE_BARCODE to true),
        )
        assertEquals(recoveryKey, result.text)
    }

    private companion object {
        private const val BLACK = 0xFF000000.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
    }
}
