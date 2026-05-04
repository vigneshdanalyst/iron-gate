package com.irongate

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking

class IronGateDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val lockState = LockStateStore.read(context)
        if (lockState.active) {
            runBlocking {
                IronGateDatabase.getInstance(context).dao().insertAttemptLog(
                    AttemptLogEntity(
                        timestampMillis = System.currentTimeMillis(),
                        type = "ADMIN_DISABLE_ATTEMPT",
                        details = "Attempted to disable Device Admin while lock was active."
                    )
                )
            }
            return "Iron Gate lock is active. Disable lock session before removing device admin."
        }
        return super.onDisableRequested(context, intent) ?: ""
    }

    companion object {
        fun component(context: Context): ComponentName {
            return ComponentName(context, IronGateDeviceAdminReceiver::class.java)
        }
    }
}
