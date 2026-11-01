package com.snishaper.android.core

import org.json.JSONObject

/**
 * Small helpers around `org.json` (bundled with Android, no extra dependency)
 * for the two models the Kotlin host parses itself (EngineStatus, BuildInfo).
 */
internal fun JSONObject.str(key: String, fallback: String = ""): String =
    if (isNull(key)) fallback else optString(key, fallback)

internal fun JSONObject.bool(key: String, fallback: Boolean = false): Boolean =
    if (isNull(key)) fallback else optBoolean(key, fallback)

internal fun JSONObject.int(key: String, fallback: Int = 0): Int =
    if (isNull(key)) fallback else optInt(key, fallback)

internal fun parseObject(json: String): JSONObject =
    runCatching { JSONObject(json) }.getOrDefault(JSONObject())
