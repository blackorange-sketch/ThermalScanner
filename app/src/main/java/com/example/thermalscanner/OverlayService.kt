package com.example.thermalscanner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "overlay_channel"
        const val NOTIF_ID = 1
        const val ACTION_STOP = "com.example.thermalscanner.action.STOP"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var textView: TextView
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            Prefs.KEY_WIDTH_DP, Prefs.KEY_OPACITY, Prefs.KEY_TEXT_SIZE -> applyVisualPrefs()
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateText()
            val interval = prefs.getLong(Prefs.KEY_REFRESH_MS, Prefs.DEFAULT_REFRESH_MS)
            handler.postDelayed(this, interval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        createNotificationChannel()
        SysMonitor.ensureZonesScanned()
        setupOverlayView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification())
        handler.post(tickRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tickRunnable)
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        try {
            windowManager.removeView(overlayView)
        } catch (e: Exception) {
            // view may already be detached
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupOverlayView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = View.inflate(this, R.layout.overlay_layout, null)
        textView = overlayView.findViewById(R.id.overlayText)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = prefs.getInt(Prefs.KEY_POS_X, 50)
        params.y = prefs.getInt(Prefs.KEY_POS_Y, 150)

        applyVisualPrefs()

        overlayView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit()
                        .putInt(Prefs.KEY_POS_X, params.x)
                        .putInt(Prefs.KEY_POS_Y, params.y)
                        .apply()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(overlayView, params)
    }

    private fun applyVisualPrefs() {
        val widthDp = prefs.getInt(Prefs.KEY_WIDTH_DP, Prefs.DEFAULT_WIDTH_DP)
        val widthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, widthDp.toFloat(), resources.displayMetrics
        ).toInt()
        textView.layoutParams = textView.layoutParams.apply { width = widthPx }
        textView.requestLayout()

        val textSize = prefs.getFloat(Prefs.KEY_TEXT_SIZE, Prefs.DEFAULT_TEXT_SIZE)
        textView.textSize = textSize

        val opacity = prefs.getInt(Prefs.KEY_OPACITY, Prefs.DEFAULT_OPACITY)
        val alphaValue = (255 * opacity / 100).coerceIn(0, 255)
        textView.background?.mutate()?.alpha = alphaValue
    }

    private fun updateText() {
        val showCpu = prefs.getBoolean(Prefs.KEY_SHOW_CPU, true)
        val showGpu = prefs.getBoolean(Prefs.KEY_SHOW_GPU, true)
        val showChip = prefs.getBoolean(Prefs.KEY_SHOW_CHIP, true)
        val showRam = prefs.getBoolean(Prefs.KEY_SHOW_RAM, true)
        val showBattery = prefs.getBoolean(Prefs.KEY_SHOW_BATTERY, true)
        val compact = prefs.getBoolean(Prefs.KEY_COMPACT, false)

        val rows = mutableListOf<String>()

        if (showCpu) {
            rows.add("CPU " + fmtPercent(SysMonitor.cpuLoadPercent()) + " " + fmtTemp(SysMonitor.cpuTemp()))
        }
        if (showGpu) {
            rows.add("GPU " + fmtPercent(SysMonitor.gpuLoadPercent()) + " " + fmtTemp(SysMonitor.gpuTemp()))
        }
        if (showChip) {
            rows.add("SoC " + fmtTemp(SysMonitor.chipTemp()))
        }
        if (showRam) {
            val ram = SysMonitor.ramInfo(this)
            rows.add(if (ram != null) "RAM ${ram.first}/${ram.second}MB" else "RAM н/д")
        }
        if (showBattery) {
            val bat = SysMonitor.batteryInfo(this)
            val wattsStr = bat.watts?.let { String.format("%.1fW", it) } ?: "н/д"
            rows.add("Батарея ${bat.percent}% $wattsStr")
        }

        textView.text = if (compact) {
            rows.chunked(2).joinToString("\n") { it.joinToString("   ") }
        } else {
            rows.joinToString("\n")
        }
    }

    private fun fmtPercent(v: Double?): String = if (v != null) String.format("%.0f%%", v) else "н/д"
    private fun fmtTemp(v: Double?): String = if (v != null) String.format("%.0f°C", v) else "н/д"

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Overlay monitor", NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val settingsIntent = Intent(this, SettingsActivity::class.java)
        val settingsPending = PendingIntent.getActivity(
            this, 0, settingsIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Моніторинг активний")
            .setContentText("CPU/GPU/RAM/батарея — оверлей увімкнено")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setContentIntent(settingsPending)
            .addAction(0, "Налаштування", settingsPending)
            .addAction(0, "Зупинити", stopPending)
            .build()
    }
}
