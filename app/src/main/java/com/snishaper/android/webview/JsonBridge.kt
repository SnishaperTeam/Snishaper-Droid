package com.snishaper.android.webview

import org.json.JSONArray
import org.json.JSONObject

/**
 * Small helpers around `org.json` (bundled with Android, no extra dependency).
 *
 * They build the two payloads the Wails runtime expects:
 *
 *  * **Responses** — `window._wailsAndroidCallback(id, response, error)` where
 *    `response` is a JSON *string* holding the envelope
 *    `{"ok":true,"data":<value>}` (or `{"ok":false,"error":"<message>"}`).
 *    The runtime does `JSON.parse(response)`, therefore the envelope has to be
 *    transported as a string, not as an object literal.
 *
 *  * **Events** — `window._wails.dispatchWailsEvent({name, data})` where `data`
 *    is a real JSON value (an object literal, *not* a JSON string).
 */
internal object JsonBridge {

    /** Quotes a string as a JavaScript/JSON string literal (quotes included). */
    fun quote(value: String): String = JSONObject.quote(value)

    /**
     * Serializes [value] into the JSON text that is embedded into the envelope.
     *
     * `String` values are quoted (they are plain text, e.g. the exported config
     * or the log buffer); `JSONObject` / `JSONArray` are inlined as JSON.
     */
    fun dataJson(value: Any?): String = when (value) {
        null -> "null"
        is JSONObject -> value.toString()
        is JSONArray -> value.toString()
        is String -> quote(value)
        is Boolean, is Int, is Long, is Short, is Byte, is Double, is Float -> value.toString()
        else -> quote(value.toString())
    }

    /** `{"ok":true,"data":<value>}` */
    fun successEnvelope(value: Any?): String = "{\"ok\":true,\"data\":" + dataJson(value) + "}"

    /** `{"ok":false,"error":"<message>"}` */
    fun errorEnvelope(message: String): String = "{\"ok\":false,\"error\":" + quote(message) + "}"

    /**
     * The JavaScript expression that resolves a pending Wails call.
     *
     * The second argument is the *string* form of the envelope (the runtime
     * parses it), the third one is `null` on success.
     */
    fun successCallback(callId: String, value: Any?): String {
        val envelope = successEnvelope(value)
        return "window._wailsAndroidCallback(${quote(callId)}, ${quote(envelope)}, null)"
    }

    /**
     * The JavaScript expression that rejects a pending Wails call.
     *
     * The envelope carries the machine readable error, the third argument is
     * the human readable message (the runtime rejects with `new Error(error)`).
     */
    fun errorCallback(callId: String, message: String): String {
        val envelope = errorEnvelope(message)
        return "window._wailsAndroidCallback(${quote(callId)}, ${quote(envelope)}, ${quote(message)})"
    }

    /**
     * The JavaScript expression that dispatches an event to the frontend.
     *
     * Two channels are used so that both possible frontend transports work:
     *  * `window._wails.dispatchWailsEvent({name, data})` — the Wails runtime
     *    API (used when the bundle still imports `@wailsio/runtime`).
     *  * `window.dispatchEvent(new CustomEvent('wails:event', {detail}))` —
     *    the native Android transport used by the ported frontend.
     */
    fun eventScript(name: String, dataJson: String): String {
        val event = "{\"name\":" + quote(name) + ",\"data\":" + dataJson + "}"
        return "(function(){var e=$event;" +
            "try{if(window._wails&&typeof window._wails.dispatchWailsEvent==='function'){" +
            "window._wails.dispatchWailsEvent(e);}}catch(x){}" +
            "try{window.dispatchEvent(new CustomEvent('wails:event',{detail:e}));}catch(x){}" +
            "})();"
    }

    /** Parses a JSON object, returning an empty object for broken input. */
    fun parseObject(raw: String): JSONObject =
        runCatching { JSONObject(raw) }.getOrElse { JSONObject() }

    /** Parses a JSON array, returning an empty array for broken input. */
    fun parseArray(raw: String): JSONArray =
        runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
}

/** A call that cannot be served (unknown method, bad arguments, engine error). */
internal class BridgeException(message: String) : Exception(message)
