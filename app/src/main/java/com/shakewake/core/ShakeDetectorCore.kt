package com.shakewake.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * ShakeDetectorCore - diport dari android-shake-detector-1.4 (Java) ke Kotlin
 * Menggunakan SensorManager.TYPE_ACCELEROMETER, membaca gForce,
 * lalu trigger callback setelah shakeCount tercapai dalam interval.
 */
class ShakeDetectorCore(
    private val options: ShakeOptions,
    private val onShake: () -> Unit
) : SensorEventListener {

    private var mShakeTimestamp: Long = 0
    private var mShakeCount: Int = 0

    companion object {
        // Minimum jeda antar shake agar tidak double-trigger
        private const val SHAKE_SLOP_TIME_MS = 500L
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // tidak dipakai
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Konversi ke G-force relatif
        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        // Hitung total gaya (magnitude vektor)
        val gForce = Math.sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        if (gForce > options.sensibility) {
            val now = System.currentTimeMillis()

            // Terlalu cepat → abaikan (anti-jitter)
            if (mShakeTimestamp + SHAKE_SLOP_TIME_MS > now) return

            // Reset count jika sudah lewat interval
            if (mShakeTimestamp + options.intervalMs < now) {
                mShakeCount = 0
            }

            mShakeTimestamp = now
            mShakeCount++

            if (mShakeCount >= options.shakeCount) {
                mShakeCount = 0 // reset setelah trigger
                onShake()
            }
        }
    }
}

/**
 * Konfigurasi shake detection
 * @param sensibility  threshold G-force (1.5 = normal, 2.0 = harus kencang, 1.2 = sangat sensitif)
 * @param shakeCount   berapa kali goyangan sebelum trigger
 * @param intervalMs   jendela waktu (ms) untuk menghitung shakeCount
 */
data class ShakeOptions(
    val sensibility: Float = 1.5f,
    val shakeCount: Int = 2,
    val intervalMs: Long = 1500L
)
