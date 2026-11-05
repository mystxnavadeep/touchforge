package com.touchforge.toolkit.core.monitor

/**
 * Parses /proc/stat "cpu" aggregate lines into busy/idle ratios.
 * Pure logic, fed by the app or the privileged helper.
 */
object CpuStatsParser {

    data class Snapshot(val total: Long, val idle: Long)

    fun parse(procStatLine: String): Snapshot? {
        val parts = procStatLine.trim().split(Regex("\\s+"))
        if (parts.size < 5 || parts[0] != "cpu") return null
        val v = parts.drop(1).mapNotNull { it.toLongOrNull() }
        if (v.size < 4) return null
        val idle = v[3] + v.getOrElse(4) { 0L } // idle + iowait
        val total = v.sum()
        if (total <= 0) return null
        return Snapshot(total, idle)
    }

    /** Busy fraction (0..1) between two snapshots, or null when not computable. */
    fun busyFraction(prev: Snapshot, now: Snapshot): Float? {
        val dTotal = now.total - prev.total
        val dIdle = now.idle - prev.idle
        if (dTotal <= 0) return null
        val busy = (dTotal - dIdle).coerceAtLeast(0)
        return busy.toFloat() / dTotal
    }
}

/**
 * FPS estimator fed by frame callbacks (e.g. Choreographer in an overlay view).
 * Reports an EWMA estimate — explicitly an ESTIMATE, never an engine value.
 */
class FpsEstimator(private val alpha: Float = 0.15f) {
    private var lastNs = 0L
    private var emaNs = 0L

    fun onFrame(nowNs: Long) {
        if (lastNs == 0L || nowNs - lastNs > 1_000_000_000L) { // reset on big gap
            lastNs = nowNs
            emaNs = 0L
            return
        }
        val dt = nowNs - lastNs
        lastNs = nowNs
        emaNs = if (emaNs == 0L) dt else (alpha * dt + (1 - alpha) * emaNs).toLong()
    }

    /** Estimated frames per second, or null while warming up. */
    fun fps(): Float? = if (emaNs > 0) 1_000_000_000f / emaNs else null
}

/**
 * Derives game open/close events from a stream of (packageName, timestamp)
 * observations (usage-stats polling or shell process events), with debounce.
 */
class GameDetector(private val debounceMs: Long = 1500) {
    sealed class Event {
        data class Opened(val packageName: String) : Event()
        data class Closed(val packageName: String) : Event()
    }

    private var current: String? = null
    private var lastChangeMs = 0L
    private var pending: String? = null

    fun onObservation(packageName: String?, atMs: Long): List<Event> {
        val out = ArrayList<Event>(1)
        if (packageName == current) {
            pending = null
            return out
        }
        if (pending == packageName) {
            if (atMs - lastChangeMs >= debounceMs || current == null) {
                val old = current
                current = packageName
                pending = null
                lastChangeMs = atMs
                if (!packageName.isNullOrBlank()) out += Event.Opened(packageName)
                if (old != null) out += Event.Closed(old)
            }
        } else {
            pending = packageName
        }
        return out
    }
}
