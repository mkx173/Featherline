package com.mkx.hrttracker.healthconnect

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import com.mkx.hrttracker.R

/**
 * Health Connect opens this from the privacy-policy link on its permission
 * screen (ACTION_SHOW_PERMISSIONS_RATIONALE, and VIEW_PERMISSION_USAGE on API 34+
 * through the manifest alias). It hands the app's privacy policy to the browser.
 */
class HealthConnectPermissionsRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, getString(R.string.privacy_policy_url).toUri()))
        }
        finish()
    }
}
