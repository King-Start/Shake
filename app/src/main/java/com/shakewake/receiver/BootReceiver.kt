package com.shakewake.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.shakewake.core.ShakeWakeService

/**
 * BootReceiver — otomatis start ShakeWakeService setelah HP reboot.
 * Hanya aktif jika user sudah mengaktifkan fitur di MainActivity.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON") return

        // Cek apakah user sudah mengaktifkan ShakeWake
        val prefs = context.getSharedPreferences(ShakeWakeService.PREF_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("enabled", false)
        if (!isEnabled) return

        val serviceIntent = ShakeWakeService.buildIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
