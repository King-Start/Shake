package com.shakewake.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shakewake.core.ShakeWakeService
import com.shakewake.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy {
        getSharedPreferences(ShakeWakeService.PREF_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Supaya bisa muncul di atas lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadPrefs()
        setupListeners()
        updateStatusUI()
    }

    private fun loadPrefs() {
        val isEnabled   = prefs.getBoolean("enabled", false)
        val sensibility = prefs.getFloat("sensibility", 1.5f)
        val shakeCount  = prefs.getInt("shake_count", 2)

        binding.switchEnable.isChecked = isEnabled

        // sensibility range: 1.0–3.0 → seekbar 0–200 (step 0.01)
        val sensPct = ((sensibility - 1.0f) / 2.0f * 100).toInt().coerceIn(0, 100)
        binding.seekSensibility.progress = sensPct
        updateSensLabel(sensibility)

        // shakeCount seekbar: 1–5
        binding.seekShakeCount.progress = shakeCount - 1
        updateShakeCountLabel(shakeCount)
    }

    private fun setupListeners() {
        // Toggle aktif/nonaktif
        binding.switchEnable.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("enabled", checked).apply()
            if (checked) startService() else stopService()
            updateStatusUI()
        }

        // Slider sensitivitas
        binding.seekSensibility.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = 1.0f + progress / 100f * 2.0f
                prefs.edit().putFloat("sensibility", v).apply()
                updateSensLabel(v)
                if (binding.switchEnable.isChecked) restartService()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Slider shake count
        binding.seekShakeCount.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val count = progress + 1
                prefs.edit().putInt("shake_count", count).apply()
                updateShakeCountLabel(count)
                if (binding.switchEnable.isChecked) restartService()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Tombol test
        binding.btnTest.setOnClickListener {
            Toast.makeText(this, "🔔 Layar akan menyala saat HP digoyangkan!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSensLabel(v: Float) {
        val label = when {
            v < 1.3f -> "Sangat Sensitif"
            v < 1.7f -> "Normal"
            v < 2.3f -> "Keras"
            else     -> "Sangat Keras"
        }
        binding.tvSensibility.text = "%.1f  ·  $label".format(v)
    }

    private fun updateShakeCountLabel(count: Int) {
        val label = when (count) {
            1 -> "1× goyangan"
            2 -> "2× goyangan"
            3 -> "3× goyangan"
            else -> "${count}× goyangan"
        }
        binding.tvShakeCount.text = label
    }

    private fun updateStatusUI() {
        val on = prefs.getBoolean("enabled", false)
        binding.tvStatus.text = if (on) "● Aktif — Goyangkan HP" else "○ Nonaktif"
        binding.tvStatus.setTextColor(
            if (on) getColor(android.R.color.holo_green_light)
            else    getColor(android.R.color.darker_gray)
        )
        binding.cardStatus.strokeColor =
            if (on) getColor(android.R.color.holo_green_light)
            else    getColor(android.R.color.darker_gray)
    }

    private fun startService() {
        val intent = ShakeWakeService.buildIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopService() {
        stopService(ShakeWakeService.buildIntent(this))
    }

    private fun restartService() {
        stopService()
        startService()
    }
}
