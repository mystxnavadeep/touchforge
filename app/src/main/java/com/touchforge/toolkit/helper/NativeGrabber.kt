package com.touchforge.toolkit.helper

import java.io.File

/**
 * Kotlin façade over libtouchforge_native (see src/main/cpp). Loaded lazily;
 * `available == false` on any device/build without the .so → callers fall back
 * to injection-only mode. Native side is OEM-dependent by nature.
 */
object NativeGrabber {

    val available: Boolean by lazy { runCatching { System.loadLibrary("touchforge_native") }.isSuccess }

    fun probe(): String? = if (available) runCatching { nativeProbe() }.getOrNull() else null

    /** Heuristic: prefer devices whose EVIOCGNAME mentions touch/screen; else null. */
    fun autoDetectTouchDevice(): String? {
        val listing = probe() ?: return null
        // format: "path\tname\n" lines
        return listing.lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val (p, n) = line.split('\t').let { it.getOrNull(0) to (it.getOrNull(1) ?: "") }
                p to n.lowercase()
            }
            .firstOrNull { (_, n) -> n.contains("touch") || n.contains("screen") || n.contains("mt") }
            ?.first
            ?: File("/dev/input").listFiles { f -> f.name.startsWith("event") }
                ?.maxByOrNull { it.length() /* arbitrary stable pick */ }?.absolutePath
    }

    fun start(path: String): Boolean =
        available && runCatching { nativeStartGrab(path) }.getOrDefault(false)

    fun read(maxBytes: Int): ByteArray =
        if (available) runCatching { nativeRead(maxBytes) }.getOrDefault(ByteArray(0)) else ByteArray(0)

    fun stop() {
        if (available) runCatching { nativeStopGrab() }
    }

    fun isGrabbing(): Boolean =
        available && runCatching { nativeIsGrabbing() }.getOrDefault(false)

    private external fun nativeProbe(): String
    private external fun nativeStartGrab(path: String): Boolean
    private external fun nativeRead(maxBytes: Int): ByteArray
    private external fun nativeStopGrab()
    private external fun nativeIsGrabbing(): Boolean
}
