package com.kingd2n.statusoverlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvCpu: TextView
    private lateinit var tvBatt: TextView
    private lateinit var tvCurrent: TextView

    private val updateIntervalMs = 2000L

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateStats()
            handler.postDelayed(this, updateIntervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addOverlayView()
        handler.post(updateRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // view sudah lepas, aman diabaikan
            }
        }
        overlayView = null
    }

    private fun addOverlayView() {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.overlay_pill, null)

        tvCpu = view.findViewById(R.id.tvCpuTemp)
        tvBatt = view.findViewById(R.id.tvBattTemp)
        tvCurrent = view.findViewById(R.id.tvCurrent)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 24
        params.y = 80

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(view, params)
        overlayView = view
    }

    private fun updateStats() {
        val cpu = SysMonitor.readCpuTempC()
        val batt = SysMonitor.readBatteryTempC()
        val current = SysMonitor.readBatteryCurrentMa()

        val fire = "\uD83D\uDD25"
        val battery = "\uD83D\uDD0B"
        val degree = "\u00b0C"
        val dot = "\u2022"

        tvCpu.text = if (cpu != null) "$fire${cpu.toInt()}$degree" else "$fire--$degree"
        tvBatt.text = if (batt != null) "$battery${batt.toInt()}$degree" else "$battery--$degree"
        tvCurrent.text = if (current != null) "$dot${current.toInt()}mA" else "$dot--mA"
    }

    private fun startForegroundWithNotification() {
        val channelId = "status_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Status Overlay",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Status Overlay aktif")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }
}
