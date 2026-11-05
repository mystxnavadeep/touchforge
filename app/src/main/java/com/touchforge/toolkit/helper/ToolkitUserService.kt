package com.touchforge.toolkit.helper

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import com.touchforge.toolkit.aidl.IToolkitCallback
import com.touchforge.toolkit.aidl.IToolkitService
import com.touchforge.toolkit.aidl.TouchSpec
import com.touchforge.toolkit.core.shell.CommandPolicy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * TouchForge privileged helper. Runs inside the Shizuku server process (shell uid)
 * and exposes a strictly whitelisted, guarded surface.
 *
 * Safety invariants:
 *  - every shell command is validated against [CommandPolicy] (shared, unit-tested);
 *  - every settings/resolution mutation is tracked and auto-restored by the
 *    watchdog when the app stops heartbeating, and on destroy;
 *  - only the owning app's uid may call this binder (verified in onTransact).
 */
class ToolkitUserService(private val context: Context?) : IToolkitService.Stub() {

    companion object {
        const val VERSION = 1
        const val PROTOCOL = 1
        private const val WATCHDOG_MS = 15_000L
        private val ALLOWED_SETTING_KEYS = setOf(
            "show_touches", "pointer_location", "user_rotation", "accelerometer_rotation",
        )
    }

    @Volatile private var callback: IToolkitCallback? = null
    private val lastPingMs = AtomicLong(System.currentTimeMillis())
    private val running = AtomicBoolean(true)
    private val originals = HashMap<String, String?>() // guarded setting -> original value
    private var resolutionOriginal: Pair<String?, String?>? = null // (wm size out, wm density out)

    private val grabber = NativeGrabber
    private var grabThread: Thread? = null

    init {
        Thread({
            while (running.get()) {
                if (System.currentTimeMillis() - lastPingMs.get() > WATCHDOG_MS) {
                    restoreGuardedSettingsInternal()
                    NativeGrabber.stop()
                    running.set(false)
                    break
                }
                Thread.sleep(2_000)
            }
        }, "tf-watchdog").apply { isDaemon = true }.start()
    }

    /** Only our app may talk to this binder. */
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        val ctx = context
        if (ctx != null) {
            val uid = Binder.getCallingUid()
            val name = ctx.packageManager.getNameForUid(uid)
            if (name != ctx.packageName) return false
        }
        return super.onTransact(code, data, reply, flags)
    }

    override fun getProtocolVersion(): Int = PROTOCOL

    override fun getShellUid(): Int = android.os.Process.myUid()

    override fun isCommandAllowed(command: String): Boolean = CommandPolicy.isAllowed(command)

    override fun execCommand(command: String): String {
        if (!CommandPolicy.isAllowed(command)) return ""
        return runCatching {
            val parts = command.trim().split(Regex("\\s+"))
            val proc = ProcessBuilder(parts).redirectErrorStream(true).start()
            val out = proc.inputStream.buffered().readText()
            proc.waitFor(5, TimeUnit.SECONDS)
            out
        }.getOrDefault("")
    }

    override fun applySystemSetting(key: String, value: String): Boolean {
        if (key !in ALLOWED_SETTING_KEYS) return false
        if (value.any { !it.isDigit() }) return false
        val v = value.toLongOrNull() ?: return false
        val rangeOk = when (key) {
            "user_rotation" -> v in 0..3
            else -> v in 0..1
        }
        if (!rangeOk) return false
        synchronized(originals) {
            if (key !in originals) originals[key] = execCommand("settings get system $key").trim()
        }
        // `settings put` prints nothing on success; failures surface via the watchdog's restore.
        return runCatching {
            execCommand("settings put system $key $value")
            true
        }.getOrDefault(false)
    }

    override fun startResolutionScale(width: Int, height: Int, dpi: Int): Boolean {
        if (width !in 200..8000 || height !in 200..8000 || dpi !in 80..1000) return false
        synchronized(this) {
            if (resolutionOriginal == null) {
                val sizeOut = execCommand("wm size")
                val densityOut = execCommand("wm density")
                resolutionOriginal = CommandPolicy.parseWmSize(sizeOut)?.let { "${it.first}x${it.second}" } to
                    CommandPolicy.parseWmDensity(densityOut)?.toString()
            }
        }
        val okSize = runCatching { execCommand("wm size ${width}x${height}"); true }.getOrDefault(false)
        val okDensity = runCatching { execCommand("wm density $dpi"); true }.getOrDefault(false)
        return okSize && okDensity
    }

    override fun restoreGuardedSettings(): Boolean = restoreGuardedSettingsInternal()

    private fun restoreGuardedSettingsInternal(): Boolean {
        synchronized(originals) {
            for ((key, value) in originals) {
                val cmd = if (value.isNullOrBlank() || value == "null") "settings delete system $key"
                else "settings put system $key $value"
                execCommand(cmd)
            }
            originals.clear()
        }
        synchronized(this) {
            if (resolutionOriginal != null) {
                execCommand("wm size reset")
                execCommand("wm density reset")
                resolutionOriginal = null
            }
        }
        return true
    }

    override fun injectTouch(spec: TouchSpec): Boolean = TouchInjector.inject(spec)

    override fun canInjectProbe(): Boolean = TouchInjector.available()

    override fun canGrabProbe(): Boolean =
        NativeGrabber.available && !NativeGrabber.probe().isNullOrBlank()

    override fun startGrab(devicePath: String): Boolean {
        if (grabThread?.isAlive == true) return true
        val path = if (devicePath.isBlank()) NativeGrabber.autoDetectTouchDevice() else devicePath
        if (path == null) return false
        if (!NativeGrabber.start(path)) return false
        val cb = callback
        grabThread = Thread {
            while (NativeGrabber.isGrabbing()) {
                val chunk = NativeGrabber.read(4096)
                if (chunk.isNotEmpty()) {
                    runCatching { cb?.onGrabData(chunk) }
                } else break
            }
        }.apply { isDaemon = true; start() }
        return true
    }

    override fun stopGrab() {
        NativeGrabber.stop()
    }

    override fun isGrabbing(): Boolean = NativeGrabber.isGrabbing()

    override fun setCallback(cb: IToolkitCallback?) {
        callback = cb
    }

    override fun ping() {
        lastPingMs.set(System.currentTimeMillis())
    }

    override fun shutdown() {
        restoreGuardedSettingsInternal()
        NativeGrabber.stop()
        running.set(false)
    }

    fun destroy() {
        shutdown()
    }
}
