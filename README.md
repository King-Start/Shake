# ShakeWake 📱

Aplikasi Android untuk **membangunkan layar HP dengan cara menggoyangkan HP** — solusi untuk HP dengan tombol Power yang rusak.

## Fitur
- ✅ Deteksi goyangan via Accelerometer (port dari android-shake-detector-1.4)
- ✅ Berjalan di background sebagai Foreground Service
- ✅ Auto-start setelah HP reboot
- ✅ Slider sensitivitas (1.0 – 3.0 G-force)
- ✅ Slider jumlah goyangan (1–5×)
- ✅ Feedback getar saat layar dinyalakan
- ✅ UI dark premium
- ✅ Support **Android 7 – 14** (minSdk 24)

## Cara Pakai
1. Install APK
2. Buka app → aktifkan toggle
3. Matikan layar HP dengan cara lain (ketuk dua kali lock screen jika ada, atau biarkan sleep otomatis)
4. Goyangkan HP → layar menyala

## Catatan Penting per Brand

| Brand | Langkah Tambahan |
|-------|-----------------|
| Xiaomi MIUI/HyperOS | Setelan → App → ShakeWake → Autostart → Aktifkan |
| Samsung One UI | Setelan → Baterai → Batas penggunaan app → Izinkan |
| OPPO ColorOS | Setelan → Manajemen Aplikasi → ShakeWake → Izinkan Autostart |
| Infinix XOS | Setelan → App → ShakeWake → Background Running → Izinkan |
| Vivo FuntouchOS | iManager → Izin App → Autostart |

## Build
```bash
./gradlew assembleDebug
```
APK tersedia di `app/build/outputs/apk/debug/app-debug.apk`

## Sumber
Logika shake detection diport dari [android-shake-detector v1.4](https://github.com/netodevel/android-shake-detector) oleh @netodevel.
