# Status Overlay - ingres

Floating overlay pill (mengikuti desain gambar referensi) yang menampilkan:
- CPU: suhu tertinggi antar thermal zone yang type-nya mengandung "cpu"
- Battery: suhu baterai
- Current: arus baterai dalam mA

Pill bisa di-drag ke posisi mana pun di layar, auto-update tiap 2 detik, dan
opsional aktif otomatis saat boot.

## Kenapa floating overlay, bukan patch SystemUI asli?
Menaruh info ini langsung di status bar asli butuh smali/framework patch yang
terikat ke versi SystemUI AxionOS spesifik plus platform signing key (mirip
kasus bubbles-limit patch). Floating overlay lewat `TYPE_APPLICATION_OVERLAY`
jauh lebih portable, tidak butuh root, dan bisa langsung diiterasi dengan
`adb install -r` tanpa rebuild ROM.

## Build
Push repo ini ke GitHub, workflow `.github/workflows/App_StatusOverlay_Build.yml`
otomatis build APK debug lewat `gradle assembleDebug` (bukan `./gradlew`, jadi
tidak perlu generate gradle-wrapper.jar manual). Hasil ada di artifact
"StatusOverlay-debug".

Build lokal (kalau Android Studio/SDK sudah ada):
```
cd StatusOverlay
gradle assembleDebug
```

## Instalasi & pemakaian
1. Install APK, buka app-nya sekali dulu (wajib - kalau belum pernah dibuka,
   broadcast BOOT_COMPLETED akan diblokir oleh "stopped state" restriction Android)
2. Tap "Nyalakan Overlay" -> diarahkan ke izin "Display over other apps" kalau
   belum di-grant
3. Pill muncul di pojok kiri atas layar, tap-drag untuk pindah posisi
4. Disable battery optimization untuk app ini di pengaturan device, supaya
   servis tidak di-kill Doze/App Standby

## Kalibrasi (WAJIB dicek di device asli - kode ini belum pernah dicompile/dites)
Logika baca sensor mengikuti konvensi ABI standar kernel Linux/Android:
- `thermal_zone*/temp` -> millidegree Celsius (dibagi 1000)
- `battery/temp` -> per-sepuluh derajat Celsius (dibagi 10)
- `battery/current_now` -> microampere (dibagi 1000 kalau |raw| > 20000 - ini
  heuristik, bukan hasil test di hardware)

Semua thermal zone yang terdeteksi (nama + suhu) di-log ke Logcat saat servis
pertama kali start:
```
adb logcat -s StatusOverlay
```
Cek dari situ zone mana yang paling representasi suhu CPU ingres yang benar,
lalu kalau perlu sesuaikan keyword/index di `SysMonitor.kt` fungsi
`findCpuZones()`. Kalau skala current_now ternyata salah tebak, sesuaikan
threshold di `readBatteryCurrentMa()`.

## Belum termasuk (bisa nambah kalau perlu)
- Network speed & battery percentage dari baris kedua gambar referensi -
  scope sekarang fokus ke 3 metrik yang diminta (CPU temp, battery temp, mA)
- Signing/system-app placement via Magisk - masih APK biasa dulu buat
  iterasi cepat lewat adb install
