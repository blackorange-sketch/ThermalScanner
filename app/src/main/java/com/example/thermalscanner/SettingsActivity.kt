package com.example.thermalscanner

import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs.get(this)

        setupWidthSeek()
        setupTextSizeSeek()
        setupOpacitySeek()
        setupRefreshSeek()
        setupSwitches()

        findViewById<Button>(R.id.btnResetPosition).setOnClickListener {
            prefs.edit().putInt(Prefs.KEY_POS_X, 50).putInt(Prefs.KEY_POS_Y, 150).apply()
        }

        findViewById<Button>(R.id.btnDone).setOnClickListener { finish() }
    }

    private fun setupWidthSeek() {
        val seek = findViewById<SeekBar>(R.id.seekWidth)
        val label = findViewById<TextView>(R.id.labelWidth)
        val current = prefs.getInt(Prefs.KEY_WIDTH_DP, Prefs.DEFAULT_WIDTH_DP)
        seek.progress = (current - 100).coerceIn(0, 200)
        label.text = "Ширина вікна: ${current}dp"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val widthDp = 100 + progress
                label.text = "Ширина вікна: ${widthDp}dp"
                if (fromUser) prefs.edit().putInt(Prefs.KEY_WIDTH_DP, widthDp).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun setupTextSizeSeek() {
        val seek = findViewById<SeekBar>(R.id.seekTextSize)
        val label = findViewById<TextView>(R.id.labelTextSize)
        val current = prefs.getFloat(Prefs.KEY_TEXT_SIZE, Prefs.DEFAULT_TEXT_SIZE)
        seek.progress = (current.toInt() - 8).coerceIn(0, 16)
        label.text = "Розмір тексту: ${current.toInt()}sp"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = 8 + progress
                label.text = "Розмір тексту: ${size}sp"
                if (fromUser) prefs.edit().putFloat(Prefs.KEY_TEXT_SIZE, size.toFloat()).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun setupOpacitySeek() {
        val seek = findViewById<SeekBar>(R.id.seekOpacity)
        val label = findViewById<TextView>(R.id.labelOpacity)
        val current = prefs.getInt(Prefs.KEY_OPACITY, Prefs.DEFAULT_OPACITY)
        seek.progress = current
        label.text = "Прозорість фону: ${current}%"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                label.text = "Прозорість фону: ${progress}%"
                if (fromUser) prefs.edit().putInt(Prefs.KEY_OPACITY, progress).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun setupRefreshSeek() {
        val seek = findViewById<SeekBar>(R.id.seekRefresh)
        val label = findViewById<TextView>(R.id.labelRefresh)
        val current = prefs.getLong(Prefs.KEY_REFRESH_MS, Prefs.DEFAULT_REFRESH_MS)
        seek.progress = ((current - 500) / 500).toInt().coerceIn(0, 9)
        label.text = "Інтервал оновлення: ${current}мс"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val ms = 500L + progress * 500L
                label.text = "Інтервал оновлення: ${ms}мс"
                if (fromUser) prefs.edit().putLong(Prefs.KEY_REFRESH_MS, ms).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun setupSwitches() {
        bindSwitch(R.id.switchCpu, Prefs.KEY_SHOW_CPU, true)
        bindSwitch(R.id.switchGpu, Prefs.KEY_SHOW_GPU, true)
        bindSwitch(R.id.switchChip, Prefs.KEY_SHOW_CHIP, true)
        bindSwitch(R.id.switchRam, Prefs.KEY_SHOW_RAM, true)
        bindSwitch(R.id.switchBattery, Prefs.KEY_SHOW_BATTERY, true)
        bindSwitch(R.id.switchCompact, Prefs.KEY_COMPACT, false)
    }

    private fun bindSwitch(viewId: Int, key: String, default: Boolean) {
        val sw = findViewById<SwitchMaterial>(viewId)
        sw.isChecked = prefs.getBoolean(key, default)
        sw.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(key, isChecked).apply()
        }
    }
}
