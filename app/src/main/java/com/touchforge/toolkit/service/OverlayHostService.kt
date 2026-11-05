package com.touchforge.toolkit.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.touchforge.toolkit.R
import com.touchforge.toolkit.core.monitor.CpuStatsParser
import com.touchforge.toolkit.core.monitor.FpsEstimator
import com.touchforge.toolkit.data.CrosshairSettings
import com.touchforge.toolkit.data.HudSettings
import com.touchforge.toolkit.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Hosts the floating crosshair and performance HUD as TYPE_APPLICATION_OVERLAY
 * windows and keeps the session alive as a special-use foreground service.
 */
@AndroidEntryPoint
class OverlayHostService : Service() {

    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var wm: WindowManager
    private var crosshairView: CrosshairView? = null
    private var hudView: HudView? = null
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()
        scope.launch {
            settings.crosshair.collectLatest { attachOrDetachCrosshair(it) }
        }
        scope.launch {
            settings.hud.collectLatest { attachOrDetachHud(it) }
        }
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notif: Notification =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setContentTitle(getString(R.string.notif_title))
                    .setContentText(getString(R.string.notif_text))
                    .setOngoing(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setOngoing(true)
                    .build()
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun overlayParams(xNorm: Float, yNorm: Float, wPx: Int, hPx: Int, touchable: Boolean): WindowManager.LayoutParams {
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            if (!touchable) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0
        return WindowManager.LayoutParams(
            wPx, hPx, type, flags, PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = xNorm.coerceIn(0f, 1f).times(resources.displayMetrics.widthPixels).toInt()
            this.y = yNorm.coerceIn(0f, 1f).times(resources.displayMetrics.heightPixels).toInt()
        }
    }

    private fun attachOrDetachCrosshair(cs: CrosshairSettings) {
        val allowed = Settings.canDrawOverlays(this)
        if (!cs.enabled || !allowed) {
            crosshairView?.let { runOnMain { wm.removeViewImmediate(it) } }
            crosshairView = null
            return
        }
        if (crosshairView == null) {
            runOnMain {
                val v = CrosshairView(this)
                v.config = cs
                val size = (cs.size * resources.displayMetrics.widthPixels).toInt().coerceAtLeast(24)
                val p = overlayParams(cs.x - cs.size / 2f, cs.y - cs.size / 2f, size, size, touchable = true)
                v.setTouchDragListener { dx, dy ->
                    val cur = v.tag as? WindowManager.LayoutParams ?: return@setTouchDragListener
                    cur.x += dx; cur.y += dy
                    runCatching { wm.updateViewLayout(v, cur) }
                    scope.launch {
                        val dm = resources.displayMetrics
                        settings.setCrosshair(
                            v.config.copy(
                                x = (cur.x + size / 2f) / dm.widthPixels,
                                y = (cur.y + size / 2f) / dm.heightPixels,
                            ),
                        )
                    }
                }
                runCatching { wm.addView(v, p) }
                v.tag = p
                crosshairView = v
            }
        } else {
            runOnMain {
                crosshairView?.config = cs
                crosshairView?.invalidate()
            }
        }
    }

    private fun attachOrDetachHud(hs: HudSettings) {
        val allowed = Settings.canDrawOverlays(this)
        if (!hs.enabled || !allowed) {
            hudView?.let { runOnMain { wm.removeViewImmediate(it) } }
            hudView = null
            monitorJob?.cancel()
            return
        }
        if (hudView == null) {
            runOnMain {
                val v = HudView(this)
                val p = overlayParams(0.02f, 0.02f, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, touchable = true)
                runCatching { wm.addView(v, p) }
                v.tag = p
                hudView = v
                startMonitor(v, hs)
            }
        }
    }

    private fun startMonitor(v: HudView, hs: HudSettings) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            var prev = CpuStatsParser.parse(readProcStat() ?: return@launch)
            while (true) {
                val line = readProcStat()
                val snapshot = line?.let { CpuStatsParser.parse(it) }
                val cpu = if (prev != null && snapshot != null) CpuStatsParser.busyFraction(prev, snapshot) else null
                if (snapshot != null) prev = snapshot
                val ram = ramUsedFraction()
                val battery = batteryPercent()
                withContext(Dispatchers.Main) {
                    v.update(cpu, ram, battery, hs)
                }
                delay(hs.intervalMs.coerceAtLeast(250).toLong())
            }
        }
    }

    private fun readProcStat(): String? =
        runCatching { File("/proc/stat").bufferedReader().readLine() }.getOrNull()

    private fun ramUsedFraction(): Float? {
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val total = mi.totalMem.toFloat()
        return if (total > 0) (total - mi.availMem) / total else null
    }

    private fun batteryPercent(): Int? {
        val i = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else null
    }

    private fun runOnMain(block: () -> Unit) {
        Handler(Looper.getMainLooper()).post(block)
    }

    override fun onDestroy() {
        crosshairView?.let { runCatching { wm.removeViewImmediate(it) } }
        hudView?.let { runCatching { wm.removeViewImmediate(it) } }
        crosshairView = null
        hudView = null
        monitorJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "touchforge_session"
        const val NOTIF_ID = 42

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayHostService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayHostService::class.java))
        }
    }
}

/** Drag-and-drop listener: reports pixel deltas while the user drags a window. */
private typealias DragListener = (dx: Int, dy: Int) -> Unit

private fun View.setTouchDragListener(listener: DragListener) {
    var lastX = 0f
    var lastY = 0f
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = e.rawX; lastY = e.rawY; true }
            MotionEvent.ACTION_MOVE -> {
                listener((e.rawX - lastX).toInt(), (e.rawY - lastY).toInt())
                lastX = e.rawX; lastY = e.rawY
                true
            }
            else -> false
        }
    }
}

/** Original crosshair rendering: two hairlines + center dot, fully user-styled. */
private class CrosshairView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var config: CrosshairSettings = CrosshairSettings(enabled = true, x = 0.5f, y = 0.5f, size = 0.06f, color = 0xFF00E5FF, opacity = 0.9f)

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val cfg = config
        paint.color = cfg.color.toInt()
        paint.alpha = (cfg.opacity * 255).toInt().coerceIn(0, 255)
        paint.strokeWidth = (width * 0.02f).coerceAtLeast(1f)
        val arm = width * 0.42f
        canvas.drawLine(cx - arm, cy, cx - width * 0.15f, cy, paint)
        canvas.drawLine(cx + width * 0.15f, cy, cx + arm, cy, paint)
        canvas.drawLine(cx, cy - arm, cx, cy - height * 0.15f, paint)
        canvas.drawLine(cx, cy + height * 0.15f, cx, cy + arm, paint)
        canvas.drawCircle(cx, cy, width * 0.06f, paint)
    }
}

/** HUD panel; FPS is an in-process Choreographer estimate, labelled as such. */
private class HudView(context: Context) : LinearLayout(context) {
    private val cpu = TextView(context)
    private val ram = TextView(context)
    private val fps = TextView(context)
    private val bat = TextView(context)
    private val estimator = FpsEstimator()

    init {
        orientation = VERTICAL
        val pad = (resources.displayMetrics.density * 6).toInt()
        setPadding(pad, pad, pad, pad)
        setBackgroundColor(0x66000000)
        listOf(cpu, ram, fps, bat).forEach {
            it.setTextColor(Color.WHITE)
            it.textSize = 12f
            addView(it)
        }
    }

    fun update(cpuFrac: Float?, ramFrac: Float?, battery: Int?, hs: HudSettings) {
        cpu.visibility = if (hs.showCpu && cpuFrac != null) VISIBLE else GONE
        ram.visibility = if (hs.showRam && ramFrac != null) VISIBLE else GONE
        fps.visibility = if (hs.showFps) VISIBLE else GONE
        bat.visibility = if (hs.showBattery && battery != null) VISIBLE else GONE
        cpuFrac?.let { cpu.text = "CPU ${(it * 100).toInt()}%" }
        ramFrac?.let { ram.text = "RAM ${(it * 100).toInt()}%" }
        if (hs.showFps) {
            estimatorTick()
            val est = estimator.fps()
            fps.text = if (est != null) "FPS~ ${est.toInt()} (est.)" else "FPS~ …"
        }
        battery?.let { bat.text = "BAT $it%" }
        alpha = hs.opacity.coerceIn(0.2f, 1f)
    }

    private fun estimatorTick() {
        Choreographer.getInstance().postFrameCallback { nanos ->
            estimator.onFrame(nanos)
        }
    }
}
