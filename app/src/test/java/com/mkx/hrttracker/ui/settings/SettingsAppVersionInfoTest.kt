package com.mkx.hrttracker.ui.settings

import android.content.pm.PackageInfo
import android.os.Build
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsAppVersionInfoTest {
    @Test
    fun resolvePackageVersionCode_belowP_usesLegacyVersionCode() {
        val packageInfo = PackageInfo().apply {
            @Suppress("DEPRECATION")
            versionCode = 42
        }

        assertEquals(
            42L,
            resolvePackageVersionCode(packageInfo, sdkInt = Build.VERSION_CODES.O),
        )
    }

    @Test
    fun resolvePackageVersionCode_fromP_usesLongVersionCode() {
        val packageInfo = mockk<PackageInfo>()
        every { packageInfo.longVersionCode } returns 4_294_967_338L

        assertEquals(
            4_294_967_338L,
            resolvePackageVersionCode(packageInfo, sdkInt = Build.VERSION_CODES.P),
        )
    }
}
