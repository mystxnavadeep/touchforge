package com.touchforge.toolkit.core.shell

/**
 * Whitelist policy for every shell command executed by the privileged helper.
 * Shared by the app (to preview) and the helper (to enforce). Defense in depth:
 * even though only our app can call the helper, the helper must still refuse
 * anything not on this list and reject metacharacters outright.
 */
object CommandPolicy {

    private val PREFIXES = listOf(
        "settings get ",
        "settings put system show_touches ",
        "settings put system pointer_location ",
        "settings put system user_rotation ",
        "settings put system accelerometer_rotation ",
        "settings delete system show_touches",
        "settings delete system pointer_location",
        "settings delete system user_rotation",
        "wm size",
        "wm density",
        "am send-trim-memory ",
        "am force-stop ",
        "dumpsys battery",
        "dumpsys activity",
        "dumpsys SurfaceFlinger --latency",
        "cmd package resolve-activity --brief",
        "cat /proc/stat",
        "cat /proc/meminfo",
        "cat /sys/class/thermal/",
        "getevent -c ",
    )

    private val FORBIDDEN_CHARS = charArrayOf(';', '|', '&', '`', '$', '\n', '\r', '<', '>', '(', ')', '{', '}', '"', '\'')

    /** True when the command is byte-for-byte covered by the whitelist. */
    fun isAllowed(command: String): Boolean {
        val cmd = command.trim()
        if (cmd.isEmpty()) return false
        if (cmd.indexOfAny(FORBIDDEN_CHARS) >= 0) return false
        if (cmd.contains("..")) return false
        return PREFIXES.any { p ->
            if (p.endsWith(" ")) cmd.startsWith(p)
            else cmd == p || cmd.startsWith("$p ") || cmd.startsWith(p)
        }
    }

    /** Parse "wm size" output → physical & override dimensions, e.g. 1080x2400. */
    fun parseWmSize(output: String): Pair<Int, Int>? {
        val phys = Regex("Physical size:\\s*(\\d+)x(\\d+)").find(output) ?: return null
        val (w, h) = phys.destructured
        return w.toInt() to h.toInt()
    }

    fun parseWmDensity(output: String): Int? =
        Regex("Physical density:\\s*(\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull()
}
