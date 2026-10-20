package com.snishaper.android.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Small helpers around `org.json` (bundled with Android, no extra dependency).
 * The Go bridge exchanges every complex structure as a JSON string; see
 * docs/android-bridge-api.md for the contract.
 */
internal fun JSONObject.str(key: String, fallback: String = ""): String =
    if (isNull(key)) fallback else optString(key, fallback)

internal fun JSONObject.strOrNull(key: String): String? =
    if (isNull(key)) null else optString(key, "").ifEmpty { null }

internal fun JSONObject.bool(key: String, fallback: Boolean = false): Boolean =
    if (isNull(key)) fallback else optBoolean(key, fallback)

internal fun JSONObject.int(key: String, fallback: Int = 0): Int =
    if (isNull(key)) fallback else optInt(key, fallback)

internal fun JSONObject.long(key: String, fallback: Long = 0L): Long =
    if (isNull(key)) fallback else optLong(key, fallback)

internal fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)

internal fun JSONObject.arr(key: String): JSONArray? = optJSONArray(key)

internal fun JSONObject.strList(key: String): List<String> {
    val array = arr(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optString(index, "").takeIf { it.isNotEmpty() }
    }
}

internal fun JSONObject.objList(key: String): List<JSONObject> {
    val array = arr(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { index -> array.optJSONObject(index) }
}

internal fun JSONArray.toStringList(): List<String> =
    (0 until length()).mapNotNull { index -> optString(index, "").takeIf { it.isNotEmpty() } }

internal fun JSONArray.toObjectList(): List<JSONObject> =
    (0 until length()).mapNotNull { index -> optJSONObject(index) }

internal fun parseObjectArray(json: String): List<JSONObject> {
    if (json.isBlank()) return emptyList()
    return runCatching { JSONArray(json).toObjectList() }.getOrDefault(emptyList())
}

internal fun parseObject(json: String): JSONObject =
    runCatching { JSONObject(json) }.getOrDefault(JSONObject())

internal fun jsonArrayOfStrings(values: List<String>): JSONArray {
    val array = JSONArray()
    values.forEach { array.put(it) }
    return array
}

internal fun JSONObject.putStringList(key: String, values: List<String>): JSONObject {
    put(key, jsonArrayOfStrings(values))
    return this
}
