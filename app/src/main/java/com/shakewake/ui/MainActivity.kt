package com.shakewake.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shakewake.core.ShakeWakeAdmin
import com.shakewake.core.ShakeWakeService
import com.shakewake.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy {
        getSharedPreferences(ShakeWakeService.PREF_NAME, Context.MODE_PRIVATE)
    }
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    companion object {
        private const val REQUEST_ADMIN = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, ShakeWakeAdmin::class.java)

        loadPrefs()
        setupListeners()
        updateStatusUI()
        updateAdminUI()
        updateBatteryUI()
        checkBatteryOptimization()
    }

    override fun onResume() {
        super.onResume()
        updateAdminUI()
        updateBatteryUI()
    }

    private fun loadPrefs() {
        binding.switchEnable.isChecked = prefs.getBoolean("enabled", false)

        val sensibility = prefs.getFloat("sensibility", 1.5f)
        val sensPct = ((sensibility - 1.0f) / 2.0f * 100).toInt().coerceIn(0, 100)
        binding.seekSensibility.progress = sensPct
        updateSensLabel(sensibility)

        val shakeCount = prefs.getInt("shake_count", 2)
        binding.seekShakeCount.progress = shakeCount - 1
        updateShakeCountLabel(shakeCount)

        // Mode
        val mode = prefs.getString("mode", ShakeWakeService.MODE_WAKE) ?: ShakeWakeService.MODE_WAKE
        when (mode) {
            ShakeWakeService.MODE_WAKE -> binding.rbModeWake.isChecked = true
            ShakeWakeService.MODE_LOCK -> binding.rbModeLock.isChecked = true
            ShakeWakeService.MODE_BOTH -> binding.rbModeBoth.isChecked = true
        }
    }

    private fun setupListeners() {
        binding.switchEnable.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("enabled", checked).apply()
            if (checked) startService() else stopService()
            updateStatusUI()
        }

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

        // Mode radio buttons
        binding.rbModeWake.setOnCheckedChangeListener { _, checked ->
            if (checked) saveMode(ShakeWakeService.MODE_WAKE)
        }
        binding.rbModeLock.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!dpm.isAdminActive(adminComponent)) {
                    Toast.makeText(this, "Aktifkan Device Admin dulu!", Toast.LENGTH_SHORT).show()
                    binding.rbModeWake.isChecked = true
                } else {
                    saveMode(ShakeWakeService.MODE_LOCK)
                }
            }
        }
        binding.rbModeBoth.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!dpm.isAdminActive(adminComponent)) {
                    Toast.makeText(this, "Aktifkan Device Admin dulu!", Toast.LENGTH_SHORT).show()
                    binding.rbModeWake.isChecked = true
                } else {
                    saveMode(ShakeWakeService.MODE_BOTH)
                }
            }
        }

        // Tombol aktifkan Device Admin
        binding.btnAdmin.setOnClickListener {
            if (dpm.isAdminActive(adminComponent)) {
                Toast.makeText(this, "✓ Device Admin sudah aktif", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Diperlukan untuk mematikan layar saat HP digoyangkan (tanpa root)"
                    )
                }
                startActivityForResult(intent, REQUEST_ADMIN)
            }
        }

        binding.btnTest.setOnClickListener {
            Toast.makeText(this, "🔔 Goyangkan HP sekarang!", Toast.LENGTH_SHORT).show()
        }

        // Tombol minta izin Battery Unrestricted (penting untuk Samsung)
        binding.btnBattery.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }
    }

    private fun saveMode(mode: String) {
        prefs.edit().putString("mode", mode).apply()
        if (binding.switchEnable.isChecked) restartService()
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
        binding.tvShakeCount.text = "${count}× goyangan"
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

    private fun updateAdminUI() {
        val active = dpm.isAdminActive(adminComponent)
        binding.btnAdmin.text = if (active) "✓ Device Admin Aktif" else "Aktifkan Device Admin"
        binding.btnAdmin.alpha = if (active) 0.6f else 1.0f
        binding.rbModeLock.isEnabled = active
        binding.rbModeBoth.isEnabled = active
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun updateBatteryUI() {
        val unrestricted = isIgnoringBatteryOptimizations()
        binding.btnBattery.text = if (unrestricted)
            "✓ Battery Unrestricted Aktif" else "Aktifkan Battery Unrestricted"
        binding.btnBattery.alpha = if (unrestricted) 0.6f else 1.0f
    }

    private fun checkBatteryOptimization() {
        // Tampilkan reminder sekali via toast kalau belum unrestricted
        if (!isIgnoringBatteryOptimizations()) {
            Toast.makeText(
                this,
                "⚠ Aktifkan 'Battery Unrestricted' agar ShakeWake tidak dimatikan sistem",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Suppress("BatteryLife")
    private fun requestIgnoreBatteryOptimization() {
        if (isIgnoringBatteryOptimizations()) {
            Toast.makeText(this, "✓ Sudah unrestricted", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Exception) {
            // Fallback: buka halaman battery optimization settings biasa
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Buka manual: Setelan → Baterai → ShakeWake", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startService() {
        val intent = ShakeWakeService.buildIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun stopService() {
        stopService(ShakeWakeService.buildIntent(this))
    }

    private fun restartService() { stopService(); startService() }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADMIN) updateAdminUI()
    }
}
