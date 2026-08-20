package com.kingd2n.statusoverlay

import android.util.Log
import java.io.File

/**
 * Membaca data sensor CPU dan baterai langsung dari sysfs.
 *
 * Konvensi standar kernel Linux/Android:
 *  - thermal_zone*/temp             -> millidegree Celsius (dibagi 1000)
 *  - power_supply/battery/temp      -> per-sepuluh derajat Celsius (dibagi 10)
 *  - power_supply/battery/current_now -> microampere (dibagi 1000 untuk mA)
 *
 * Skala current_now & pemilihan thermal zone CPU bisa berbeda antar kernel/vendor.
 * Semua zone yang terdeteksi dicatat ke Logcat (tag: StatusOverlay) saat servis
 * pertama kali jalan, supaya bisa dicek manual di device:
 *   adb logcat -s StatusOverlay
 */
object SysMonitor {

    private const val TAG = "StatusOverlay"
    private const val THERMAL_DIR = "/sys/class/thermal"
    private const val BATTERY_TEMP_PATH = "/sys/class/power_supply/battery/temp"
    private val CURRENT_PATHS = listOf(
        "/sys/class/power_supply/battery/current_now",
        "/sys/class/power_supply/bms/current_now",
        "/sys/class/power_supply/battery/current_avg"
    )

    private var cpuZoneCache: List<File>? = null

    private fun readLong(path: String): Long? = try {
        File(path).readText().trim().toLongOrNull()
    } catch (e: Exception) {
        null
    }

    private fun readText(path: String): String? = try {
        File(path).readText().trim()
    } catch (e: Exception) {
        null
    }

    /** Cari semua thermal_zone yang nilai type-nya mengandung kata kunci CPU. */
    private fun findCpuZones(): List<File> {
        cpuZoneCache?.let { return it }
        val keywords = listOf("cpu", "cpuss", "apps")
        val dir = File(THERMAL_DIR)
        val zones = dir.listFiles { f -> f.name.startsWith("thermal_zone") } ?: emptyArray()
        val matched = mutableListOf<File>()
        val logLines = StringBuilder()
        for (zone in zones) {
            val type = readText("${zone.path}/type") ?: continue
            val temp = readLong("${zone.path}/temp")
            logLines.append("${zone.name}: type=$type temp=$temp\n")
            if (keywords.any { type.contains(it, ignoreCase = true) }) {
                matched.add(File("${zone.path}/temp"))
            }
        }
        Log.d(TAG, "Daftar thermal zone terdeteksi:\n$logLines")
        cpuZoneCache = matched
        return matched
    }

    /** Suhu CPU tertinggi antar semua cluster yang terdeteksi (Celsius), null jika gagal baca. */
    fun readCpuTempC(): Double? {
        val zones = findCpuZones()
        if (zones.isEmpty()) return null
        val temps = zones.mapNotNull { readLong(it.path) }
        val maxTemp = temps.maxOrNull() ?: return null
        return maxTemp / 1000.0
    }

    /** Suhu baterai (Celsius). */
    fun readBatteryTempC(): Double? {
        val raw = readLong(BATTERY_TEMP_PATH) ?: return null
        return raw / 10.0
    }

    /** Arus baterai dalam mA (nilai absolut, tanpa tanda +/-). */
    fun readBatteryCurrentMa(): Double? {
        for (path in CURRENT_PATHS) {
            val raw = readLong(path) ?: continue
            val ma = if (kotlin.math.abs(raw) > 20000) raw / 1000.0 else raw.toDouble()
            return kotlin.math.abs(ma)
        }
        return null
    }
}
