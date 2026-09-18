package com.example.thermalscanner

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var output: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refreshIntervalMs = 2000L

    // Cache of thermal_zone -> type, so we only read the "type" file once
    // (it never changes at runtime, only "temp" does).
    private val zoneTypes = LinkedHashMap<File, String>()

    private val tickRunnable = object : Runnable {
        override fun run() {
            output.text = buildReport()
            handler.postDelayed(this, refreshIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        output = findViewById(R.id.output)
        discoverThermalZones()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tickRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
    }

    /** Finds every /sys/class/thermal/thermal_zoneN directory and reads its "type" once. */
    private fun discoverThermalZones() {
        val thermalDir = File("/sys/class/thermal")
        val zoneDirs = thermalDir.listFiles { f -> f.name.startsWith("thermal_zone") }
            ?.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: 0 }
            ?: emptyList()

        for (zoneDir in zoneDirs) {
            val typeFile = File(zoneDir, "type")
            val type = readFirstLine(typeFile) ?: "unreadable"
            zoneTypes[zoneDir] = type
        }
    }

    private fun buildReport(): String {
        val sb = StringBuilder()

        sb.append("Знайдено зон: ${zoneTypes.size}\n\n")

        if (zoneTypes.isEmpty()) {
            sb.append("Жодної thermal_zone не знайдено або каталог недоступний.\n")
        }

        for ((zoneDir, type) in zoneTypes) {
            val tempFile = File(zoneDir, "temp")
            val raw = readFirstLine(tempFile)
            val tempStr = if (raw != null) {
                val milli = raw.trim().toLongOrNull()
                if (milli != null) {
                    // Most vendors report milli-degrees C; some report raw degrees.
                    // Heuristic: values above 1000 are almost certainly milli-degrees.
                    val celsius = if (milli > 1000 || milli < -1000) milli / 1000.0 else milli.toDouble()
                    String.format("%.1f°C", celsius)
                } else {
                    "н/д (${raw.trim()})"
                }
            } else {
                "недоступно (permission denied)"
            }

            sb.append("${zoneDir.name.padEnd(16)} ${type.padEnd(20)} $tempStr\n")
        }

        sb.append("\n--- Батарея (для довідки) ---\n")
        sb.append(batteryInfo())

        return sb.toString()
    }

    private fun batteryInfo(): String {
        val sb = StringBuilder()
        try {
            val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
            val currentNowUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val capacity = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            sb.append("Заряд: $capacity%\n")
            sb.append("Струм зараз: ${currentNowUa / 1000.0} mA\n")

            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            if (tempTenths >= 0) sb.append("Темп. батареї: ${tempTenths / 10.0}°C\n")
            if (voltageMv >= 0) sb.append("Напруга: ${voltageMv} mV\n")

            if (currentNowUa != Int.MIN_VALUE && voltageMv > 0) {
                val watts = (currentNowUa / 1_000_000.0) * (voltageMv / 1000.0)
                sb.append("Оцінка потужності: ${String.format("%.2f", Math.abs(watts))} W\n")
            }
        } catch (e: Exception) {
            sb.append("Не вдалося прочитати дані батареї: ${e.message}\n")
        }
        return sb.toString()
    }

    private fun readFirstLine(file: File): String? {
        return try {
            if (file.exists() && file.canRead()) file.readText().trim() else null
        } catch (e: Exception) {
            null
        }
    }
}
