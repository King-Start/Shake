package com.shakewake.ui

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

/**
 * WakeActivity — Activity transparan yang hanya bertugas menyalakan layar.
 *
 * Samsung One UI memblokir SCREEN_BRIGHT_WAKE_LOCK dari background Service,
 * tapi mengizinkan Activity untuk menyalakan layar via:
 *   - FLAG_TURN_SCREEN_ON
 *   - FLAG_SHOW_WHEN_LOCKED
 *   - FLAG_KEEP_SCREEN_ON
 *
 * Activity ini tidak punya UI (tema transparan), langsung finish() setelah 1 detik.
 */
class WakeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set flag SEBELUM setContentView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON   or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON   or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        // Tambah FLAG_KEEP_SCREEN_ON agar layar tetap nyala sementara
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Dismiss keyguard jika tidak ada PIN/password (Samsung support ini via Activity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        }

        // Tidak perlu layout — langsung finish setelah 800ms
        // Cukup untuk "membangunkan" layar, WakeLock dari Service yang menahan
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 800L)
    }

    override fun onResume() {
        super.onResume()
        // Pastikan flag aktif saat resume juga
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
