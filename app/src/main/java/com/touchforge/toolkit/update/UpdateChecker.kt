package com.touchforge.toolkit.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Optional GitHub-releases update check. Disabled until the user (or a build
 * flavor) configures a URL. Pure functions are unit-tested.
 */
object UpdateChecker {

    @Serializable
    data class Release(val tag_name: String = "", val body: String = "")

    private val json = Json { ignoreUnknownKeys = true }

    data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

        companion object {
            fun parse(tag: String): SemVer? {
                val m = Regex("v?(\\d+)\\.(\\d+)\\.(\\d+)").find(tag) ?: return null
                val (a, b, c) = m.destructured
                return SemVer(a.toInt(), b.toInt(), c.toInt())
            }
        }
    }

    fun isUpdateAvailable(current: String, latestTag: String): Boolean {
        val c = SemVer.parse(current) ?: return false
        val l = SemVer.parse(latestTag) ?: return false
        return l > c
    }

    suspend fun fetch(url: String): Result<Release> = withContext(Dispatchers.IO) {
        runCatching {
            if (url.isBlank()) error("update check URL not configured")
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            json.decodeFromString<Release>(text)
        }
    }
}
