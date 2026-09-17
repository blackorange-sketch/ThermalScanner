package com.example.thermalscanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var output: TextView
    private lateinit var btnPause: Button
    private val handler = Handler(Looper.getMainLooper())
    private val refreshIntervalMs = 2000L
    private var isPaused = false
    private var lastReport = ""

    private val zoneTypes = LinkedHashMap<File, String>()

    private val tickRunnable = object : Runnable {
        override fun run() {
            lastReport = buildReport()
            output.text = lastReport
            if (!isPaused) {
                handler.postDelayed(this, refreshIntervalMs)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        output = findViewById(R.id.output)
        btnPause = findViewById(R.id.btnPause)
        val btnCopy: Button = findViewById(R.id.btnCopy)

        discoverThermalZones()

        btnPause.setOnClickListener {
            isPaused = !isPaused
            if (isPaused) {
                handler.removeCallbacks(tickRunnable)
                btnPause.text = "Продовжити"
            } else {
                btnPause.text = "Пауза"
                handler.post(tickRunnable)
            }
        }

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Thermal report", lastReport)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Скопійовано в буфер обміну", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isPaused) {
            handler.post(tickRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tickRunnable)
    }

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
