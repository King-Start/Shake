package com.shakewake.core

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * ShakeWakeAdmin — Device Admin Receiver.
 * Diperlukan untuk bisa memanggil lockNow() (matikan layar tanpa root).
 * User perlu aktifkan sekali di: Setelan → Biometrik & Keamanan → Admin Aplikasi
 */
class ShakeWakeAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "✓ ShakeWake Admin aktif", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "ShakeWake Admin dinonaktifkan", Toast.LENGTH_SHORT).show()
    }
}
