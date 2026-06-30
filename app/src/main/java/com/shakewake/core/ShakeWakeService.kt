package com.shakewake.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.shakewake.R
import com.shakewake.ui.MainActivity
import com.shakewake.ui.WakeActivity

/**
 * ShakeWakeService — Foreground Service utama.
 *
 * Fix Samsung One UI (sensor freeze saat layar mati / Doze):
 * 1. Partial WakeLock dipegang TERUS selama service aktif (bukan cuma 5 detik saat shake)
 *    → mencegah CPU sleep yang membuat sensor listener "diam"
 * 2. Sensor diregister di HandlerThread terpisah (bukan main thread)
 *    → Samsung kadang throttle sensor callback di main thread saat screen off
 * 3. Watchdog: cek tiap 30 detik apakah sensor masih registered, re-register jika perlu
 * 4. onTaskRemoved(): re-start service sendiri jika di-swipe dari recent apps
 */
class ShakeWakeService : LifecycleService() {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var shakeDetector: ShakeDetectorCore
    private var screenWakeLock: PowerManager.WakeLock? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null

    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null

    private val watchdogHandler = Handler(android.os.Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null

    companion object {
        const val CHANNEL_ID = "shakewake_channel"
        const val NOTIF_ID   = 1001
        const val PREF_NAME  = "shakewake_prefs"

        const val MODE_WAKE = "wake"
        const val MODE_LOCK = "lock"
        const val MODE_BOTH = "both"

        private const val WATCHDOG_INTERVAL_MS = 30_000L

        fun buildIntent(context: Context): Intent =
            Intent(context, ShakeWakeService::class.java)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        acquireCpuWakeLock()
        startSensorThread()
        initShakeDetector()
        startWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        reloadAndRegisterSensor()
        return START_STICKY
    }

    /**
     * CPU WakeLock dipegang selama service hidup.
     * Ini KUNCI fix Samsung: tanpa ini, CPU bisa deep-sleep saat layar mati
     * sehingga sensor accelerometer callback tidak terpanggil sama sekali.
     */
    private fun acquireCpuWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        cpuWakeLock?.let { if (it.isHeld) it.release() }
        cpuWakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ShakeWake::CpuWakeLock"
        )
        cpuWakeLock?.setReferenceCounted(false)
        cpuWakeLock?.acquire()
    }

    /**
     * Register sensor di thread terpisah dengan prioritas tinggi,
     * bukan main thread — supaya Samsung tidak throttle callback-nya.
     */
    private fun startSensorThread() {
        sensorThread?.quitSafely()
        sensorThread = HandlerThread(
            "ShakeWakeSensorThread",
            Process.THREAD_PRIORITY_URGENT_DISPLAY
        ).apply { start() }
        sensorHandler = Handler(sensorThread!!.looper)
    }

    private fun initShakeDetector() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        reloadAndRegisterSensor()
    }

    private fun reloadAndRegisterSensor() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        shakeDetector = ShakeDetectorCore(
            ShakeOptions(
                prefs.getFloat("sensibility", 1.5f),
                prefs.getInt("shake_count", 2),
                prefs.getLong("interval", 1500L)
            )
        ) { onShakeDetected() }

        unregisterSensor()
        registerSensor()
    }

    private fun registerSensor() {
        if (!::sensorManager.isInitialized) {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }
        accelerometer?.let {
            sensorManager.registerListener(
                shakeDetector, it,
                SensorManager.SENSOR_DELAY_GAME,
                sensorHandler
            )
        }
    }

    private fun unregisterSensor() {
        try {
            if (::sensorManager.isInitialized && ::shakeDetector.isInitialized) {
                sensorManager.unregisterListener(shakeDetector)
            }
        } catch (_: Exception) {}
    }

    /**
     * Watchdog: tiap 30 detik, paksa re-register sensor.
     * Ini jaring pengaman terakhir kalau Samsung diam-diam "membekukan" listener.
     */
    private fun startWatchdog() {
        watchdogRunnable = object : Runnable {
            override fun run() {
                // re-acquire CPU wakelock kalau somehow terlepas
                if (cpuWakeLock?.isHeld != true) acquireCpuWakeLock()
                // re-register sensor untuk jaga-jaga
                unregisterSensor()
                registerSensor()
                watchdogHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
            }
        }
        watchdogHandler.postDelayed(watchdogRunnable!!, WATCHDOG_INTERVAL_MS)
    }

    private fun stopWatchdog() {
        watchdogRunnable?.let { watchdogHandler.removeCallbacks(it) }
    }

    private fun onShakeDetected() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val mode  = prefs.getString("mode", MODE_WAKE) ?: MODE_WAKE
        val pm    = getSystemService(Context.POWER_SERVICE) as PowerManager

        when (mode) {
            MODE_WAKE -> wakeScreen()
            MODE_LOCK -> lockScreen()
            MODE_BOTH -> if (pm.isInteractive) lockScreen() else wakeScreen()
        }
    }

    // ---- WAKE SCREEN ----
    private fun wakeScreen() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        screenWakeLock?.let { if (it.isHeld) it.release() }

        @Suppress("DEPRECATION")
        val flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP   or
                    PowerManager.ON_AFTER_RELEASE

        screenWakeLock = pm.newWakeLock(flags, "ShakeWake::ScreenWakeLock")
        screenWakeLock?.acquire(5000L)

        // Launch WakeActivity untuk Samsung One UI
        val wakeIntent = Intent(this, WakeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(wakeIntent)
    }

    // ---- LOCK SCREEN (Device Admin) ----
    private fun lockScreen() {
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(this, ShakeWakeAdmin::class.java)
            if (dpm.isAdminActive(admin)) {
                dpm.lockNow()
            }
        } catch (_: Exception) {}
    }

    // ---- NOTIFICATION ----
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID, "ShakeWake Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Berjalan di background untuk mendeteksi goyangan"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ShakeWake Aktif")
            .setContentText("Goyangkan HP untuk kontrol layar")
            .setSmallIcon(R.drawable.ic_shake_notif)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Dipanggil saat user swipe app dari recent apps.
     * Paksa restart service agar tidak benar-benar mati di Samsung.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartIntent = buildIntent(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(restartIntent)
        } else {
            applicationContext.startService(restartIntent)
        }
    }

    override fun onDestroy() {
        stopWatchdog()
        unregisterSensor()
        sensorThread?.quitSafely()
        screenWakeLock?.let { if (it.isHeld) it.release() }
        cpuWakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
