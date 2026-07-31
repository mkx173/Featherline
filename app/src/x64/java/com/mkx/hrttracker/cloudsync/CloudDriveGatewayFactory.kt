package com.mkx.hrttracker.cloudsync

import android.content.Context

internal object CloudDriveGatewayFactory {
    fun create(context: Context): CloudDriveGateway = UnavailableCloudDriveGateway()
}
