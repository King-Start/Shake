package com.shakewake.core

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.shakewake.R
import com.shakewake.ui.MainActivity

/**
 * ShakeWakeService — Foreground Service utama.
 * - Mendaftarkan SensorManager untuk accelerometer
 * - Saat shake terdeteksi: bangunkan layar dengan WakeLock
 * - START_STICKY agar Android restart service jika di-kill
 */
class ShakeWakeService : LifecycleService() {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var shakeDetector: ShakeDetectorCore
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "shakewake_channel"
        const val NOTIF_ID = 1001
        const val EXTRA_SENSIBILITY = "sensibility"
        const val EXTRA_SHAKE_COUNT = "shake_count"
        const val EXTRA_INTERVAL = "interval"
        const val PREF_NAME = "shakewake_prefs"

        fun buildIntent(context: Context): Intent = Intent(context, ShakeWakeService::class.java)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        initShakeDetector()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Baca opsi dari SharedPreferences (diset oleh MainActivity)
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val sensibility = prefs.getFloat("sensibility", 1.5f)
        val shakeCount  = prefs.getInt("shake_count", 2)
        val interval    = prefs.getLong("interval", 1500L)

        // Re-init dengan opsi terbaru
        unregisterSensor()
        shakeDetector = ShakeDetectorCore(
            ShakeOptions(sensibility, shakeCount, interval)
        ) { onShakeDetected() }
        registerSensor()

        return START_STICKY
    }

    private fun initShakeDetector() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        shakeDetector = ShakeDetectorCore(
            ShakeOptions(
                prefs.getFloat("sensibility", 1.5f),
                prefs.getInt("shake_count", 2),
                prefs.getLong("interval", 1500L)
            )
        ) { onShakeDetected() }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        registerSensor()
    }

    private fun registerSensor() {
        accelerometer?.let {
            sensorManager.registerListener(shakeDetector, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun unregisterSensor() {
        try { sensorManager.unregisterListener(shakeDetector) } catch (_: Exception) {}
    }

    /**
     * Dipanggil saat shake terdeteksi.
     * Gunakan WakeLock FULL_WAKE_LOCK untuk menyalakan layar,
     * lalu keyguardManager untuk dismiss lock screen (jika tidak ada PIN).
     */
    private fun onShakeDetected() {
        vibratePhone()
        wakeScreen()
    }

    private fun wakeScreen() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager

        // Lepas wakeLock lama jika masih aktif
        wakeLock?.let { if (it.isHeld) it.release() }

        @Suppress("DEPRECATION")
        val flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE

        wakeLock = pm.newWakeLock(flags, "ShakeWake::WakeLock")
        wakeLock?.acquire(3000L) // Tahan 3 detik lalu lepas otomatis

        // Untuk Android 8.0+ coba dismiss keyguard juga
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(null, null)
        }
    }

    private fun vibratePhone() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(80)
                }
            }
        } catch (_: Exception) {}
    }

    // ---- NOTIFICATION ----

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID,
                "ShakeWake Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Berjalan di background untuk mendeteksi goyangan"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(chan)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ShakeWake Aktif")
            .setContentText("Goyangkan HP untuk menyalakan layar")
            .setSmallIcon(R.drawable.ic_shake_notif)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        unregisterSensor()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
