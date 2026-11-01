package com.snishaper.android.core

/**
 * View models for the JSON payloads returned by the gomobile wrapper. Only the
 * models the Kotlin host actually consumes live here — everything else flows
 * through the WebView bridge as raw JSON (see EngineBridge: JSON passthrough).
 */

data class EngineStatus(
    val proxyRunning: Boolean = false,
    val tunRunning: Boolean = false,
    val tunSupported: Boolean = true,
    val tunMessage: String = "",
    val listenAddr: String = "127.0.0.1:8080",
    val listenPort: Int = 8080,
    val proxyMode: String = "",
    val ipv6Available: Boolean = true,
    val lastError: String = "",
) {
    companion object {
        fun fromJson(json: String): EngineStatus {
            val o = parseObject(json)
            return EngineStatus(
                proxyRunning = o.bool("proxyRunning"),
                tunRunning = o.bool("tunRunning"),
                tunSupported = o.bool(key = "tunSupported", fallback = true),
                tunMessage = o.str("tunMessage"),
                listenAddr = o.str("listenAddr", "127.0.0.1:8080"),
                listenPort = o.int("listenPort", 8080),
                proxyMode = o.str("proxyMode"),
                ipv6Available = o.bool(key = "ipv6Available", fallback = true),
                lastError = o.str("lastError"),
            )
        }
    }
}

data class BuildInfo(
    val version: String = "",
    val channel: String = "stable",
    val coreVersion: String = "",
    val goVersion: String = "",
    val platform: String = "android",
) {
    companion object {
        fun fromJson(json: String): BuildInfo {
            val o = parseObject(json)
            return BuildInfo(
                o.str("version"), o.str("channel", "stable"),
                o.str("coreVersion"), o.str("goVersion"), o.str("platform", "android"),
            )
        }
    }
}
