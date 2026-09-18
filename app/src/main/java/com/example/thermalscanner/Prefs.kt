package com.example.thermalscanner

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "overlay_prefs"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    const val KEY_TEXT_SIZE = "text_size_sp"
    const val KEY_OPACITY = "opacity_percent"
    const val KEY_WIDTH_DP = "width_dp"
    const val KEY_REFRESH_MS = "refresh_ms"
    const val KEY_POS_X = "pos_x"
    const val KEY_POS_Y = "pos_y"
    const val KEY_SHOW_CPU = "show_cpu"
    const val KEY_SHOW_GPU = "show_gpu"
    const val KEY_SHOW_CHIP = "show_chip"
    const val KEY_SHOW_BATTERY = "show_battery"
    const val KEY_SHOW_RAM = "show_ram"
    const val KEY_COMPACT = "compact_format"

    const val DEFAULT_TEXT_SIZE = 12f
    const val DEFAULT_OPACITY = 85
    const val DEFAULT_WIDTH_DP = 160
    const val DEFAULT_REFRESH_MS = 1500L
}
