package com.example.thermalscanner

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File
import java.io.RandomAccessFile

/**
 * Reads system metrics: thermal zones (grouped), CPU load, GPU load (Adreno),
 * RAM usage and battery info. Designed for Snapdragon 8 Gen 2 zone naming,
 * but degrades gracefully (returns null) on other chips/permissions.
 */
object SysMonitor {

    data class ThermalZone(val dir: File, val type: String)

    private val zones = mutableListOf<ThermalZone>()
    private var zonesScanned = false

    private var lastCpuTotal: Long = -1
    private var lastCpuIdle: Long = -1

    fun ensureZonesScanned() {
        if (zonesScanned) return
        val thermalDir = File("/sys/class/thermal")
        val dirs = thermalDir.listFiles { f -> f.name.startsWith("thermal_zone") } ?: emptyArray()
        for (d in dirs) {
            val type = readFirstLine(File(d, "type")) ?: continue
            zones.add(ThermalZone(d, type))
        }
        zonesScanned = true
    }

    private fun readFirstLine(f: File): String? = try {
        if (f.exists() && f.canRead()) f.readText().trim() else null
    } catch (e: Exception) {
        null
    }

    private fun zoneTempCelsius(dir: File): Double? {
        val raw = readFirstLine(File(dir, "temp")) ?: return null
        val milli = raw.toLongOrNull() ?: return null
        return if (milli > 1000 || milli < -1000) milli / 1000.0 else milli.toDouble()
    }

    private fun maxTempForPrefix(prefix: String): Double? {
        ensureZonesScanned()
        return zones.filter { it.type.startsWith(prefix) }
            .mapNotNull { zoneTempCelsius(it.dir) }
            .filter { it > -100.0 } // drop inactive sensors reporting -273
            .maxOrNull()
    }

    fun cpuTemp(): Double? = maxTempForPrefix("cpuss-")
    fun gpuTemp(): Double? = maxTempForPrefix("gpuss-")

    fun chipTemp(): Double? {
        ensureZonesScanned()
        val zone = zones.firstOrNull { it.type == "pm8550_tz" } ?: return null
        return zoneTempCelsius(zone.dir)
    }

    /** CPU load 0..100, or null on first call / if /proc/stat unavailable. Call periodically. */
    fun cpuLoadPercent(): Double? {
        return try {
            RandomAccessFile("/proc/stat", "r").use { raf ->
                val line = raf.readLine() ?: return null
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.isEmpty() || parts[0] != "cpu") return null
                val nums = parts.drop(1).mapNotNull { it.toLongOrNull() }
                if (nums.size < 4) return null
                val idle = nums[3] + nums.getOrElse(4) { 0L }
                val total = nums.sum()

                if (lastCpuTotal < 0) {
                    lastCpuTotal = total
                    lastCpuIdle = idle
                    return null
                }

                val totalDiff = total - lastCpuTotal
                val idleDiff = idle - lastCpuIdle
                lastCpuTotal = total
                lastCpuIdle = idle

                if (totalDiff <= 0) return null
                ((totalDiff - idleDiff).toDouble() / totalDiff.toDouble() * 100.0).coerceIn(0.0, 100.0)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Adreno GPU busy percentage, or null if the sysfs node isn't readable on this device. */
    fun gpuLoadPercent(): Double? {
        readFirstLine(File("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"))?.let { raw ->
            raw.replace("%", "").trim().toDoubleOrNull()?.let { return it.coerceIn(0.0, 100.0) }
        }
        readFirstLine(File("/sys/class/kgsl/kgsl-3d0/gpubusy"))?.let { raw ->
            val parts = raw.trim().split(Regex("\\s+"))
            if (parts.size >= 2) {
                val busy = parts[0].toDoubleOrNull()
                val total = parts[1].toDoubleOrNull()
                if (busy != null && total != null && total > 0) {
                    return (busy / total * 100.0).coerceIn(0.0, 100.0)
                }
            }
        }
        return null
    }

    /** Pair(usedMB, totalMB), or null if ActivityManager is unavailable. */
    fun ramInfo(context: Context): Pair<Long, Long>? {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val totalMb = mi.totalMem / (1024 * 1024)
            val usedMb = totalMb - (mi.availMem / (1024 * 1024))
            Pair(usedMb, totalMb)
        } catch (e: Exception) {
            null
        }
    }

    data class BatteryInfo(val percent: Int, val watts: Double?, val tempC: Double?)

    fun batteryInfo(context: Context): BatteryInfo {
        var percent = -1
        var watts: Double? = null
        var tempC: Double? = null
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val currentNowUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            if (tempTenths >= 0) tempC = tempTenths / 10.0

            if (currentNowUa != Int.MIN_VALUE && voltageMv > 0) {
                watts = Math.abs((currentNowUa / 1_000_000.0) * (voltageMv / 1000.0))
            }
        } catch (e: Exception) {
            // keep defaults
        }
        return BatteryInfo(percent, watts, tempC)
    }
}
