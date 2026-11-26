package com.warden.app.core.platform

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.warden.app.receivers.WardenDeviceAdminReceiver

/**
 * Encapsulates Android [DevicePolicyManager] queries and activation/deactivation intents
 * for [WardenDeviceAdminReceiver].
 */
class DeviceAdminController(private val appContext: Context) {
    private val policyManager: DevicePolicyManager by lazy {
        appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(appContext, WardenDeviceAdminReceiver::class.java)
    }

    /** Returns `true` if Warden is currently an active Device Administrator. */
    fun isAdminActive(): Boolean =
        try {
            policyManager.isAdminActive(adminComponent)
        } catch (_: SecurityException) {
            false
        }

    /** Builds the system consent [Intent] requesting Device Administrator privileges. */
    fun buildActivationIntent(explanation: String): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation)
        }

    /** Revokes active Device Administrator status for Warden. */
    fun removeActiveAdmin() {
        policyManager.removeActiveAdmin(adminComponent)
    }
}
