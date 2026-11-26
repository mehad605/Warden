package com.warden.app.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Android [DeviceAdminReceiver] that provides OS-level anti-uninstall protection.
 *
 * Component FQCN (`com.warden.app.receivers.WardenDeviceAdminReceiver`) is preserved verbatim
 * so active Device Administrator status survives upgrades from `baseline-v1`.
 */
class WardenDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(
        context: Context,
        intent: Intent,
    ) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Anti-Uninstall Protection Activated", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(
        context: Context,
        intent: Intent,
    ) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Anti-Uninstall Protection Deactivated", Toast.LENGTH_SHORT).show()
    }
}
