package com.snishaper.android.webview

import android.util.Log
import android.webkit.JavascriptInterface
import com.snishaper.android.core.SnishaperEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The object injected into the WebView as `window.wails`.
 *
 * `@wailsio/runtime` looks for `window.wails.invokeAsync` and, when it is a
 * function, installs its Android transport automatically
 * (`@wailsio/runtime/dist/runtime.js`, `androidBridge`). The reused desktop
 * frontend therefore keeps calling `Call.ByName(...)` / `EventsOn(...)` with no
 * changes at all.
 *
 * Methods run on the WebView's JavaScript thread; all work is handed to
 * [EngineBridge], which replies through `window._wailsAndroidCallback(...)`
 * marshalled onto the UI thread.
 */
internal class WailsJavascriptInterface(private val bridge: EngineBridge) {

    /**
     * Entry point used by the Wails runtime.
     *
     * @param callId  id the runtime uses to match the pending promise.
     * @param payload `{"object","method","windowName","args","clientId"}`.
     */
    @JavascriptInterface
    fun invokeAsync(callId: String, payload: String) {
        Log.d(SnishaperEngine.LOG_TAG, "bridge: invokeAsync($callId) $payload")
        bridge.handle(callId, payload)
    }

    // NOTE: no `invoke(payload)` single-arg alias on purpose. The wails runtime
    // probes `window.wails.invoke` at module-eval time and, when present, calls
    // it with ONE argument (`ac('wails:runtime:ready')`). The Chromium
    // JavaBridge matches methods by name + arity, so a 2-arg `invoke(String,
    // String)` throws a synchronous `Error: Method not found`, which aborts the
    // whole module graph and leaves the page blank. Without `invoke` the
    // runtime falls back to a harmless console.warn and every real call goes
    // through `invokeAsync` (2 args, arity-matched).

    /** Lets the page report readiness; used by smoke-test diagnostics. */
    @JavascriptInterface
    fun ready(info: String) {
        Log.i(SnishaperEngine.LOG_TAG, "webview: frontend ready ($info)")
    }
}

/**
 * Pushes Go engine events into the page as Wails events.
 *
 * Names and payload shapes are copied from the desktop Go app so the reused
 * frontend's `EventsOn(...)` handlers behave identically:
 *
 *  * `app:state_changed` — proxyRunning / systemProxyActive / proxyMode /
 *    tunRunning / tunMessage / ipv6Available.
 *  * `app:state` — includes `listenPort`.
 *  * `app:route` — `{domain, mode}`.
 *  * `app:rules_changed` — `{source}`.
 *  * `evolution:progress` / `evolution:complete`.
 */
internal class WailsEventPump(private val bridge: EngineBridge) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        // --- app:state_changed + app:state -------------------------
        scope.launch {
            combine(
                SnishaperEngine.status,
                SnishaperEngine.evolution,
            ) { status, _ -> status }
                .distinctUntilChanged()
                .collect { status ->
                    val payload = JSONObject()
                        .put("proxyRunning", status.proxyRunning)
                        .put("proxyMode", status.proxyMode)
                        .put("tunRunning", status.tunRunning)
                        .put("tunMessage", status.tunMessage)
                        .put("ipv6Available", status.ipv6Available)
                        .put("listenPort", status.listenPort)
                        .put("listenAddr", status.listenAddr)
                        .put("systemProxyActive", false) // no system proxy on Android
                    bridge.emit("app:state_changed", payload.toString())
                    bridge.emit("app:state", payload.toString())
                }
        }

        // --- app:route ---------------------------------------------
        scope.launch {
            SnishaperEngine.routes.collect { route ->
                val payload = JSONObject()
                    .put("domain", route.domain)
                    .put("mode", route.mode)
                bridge.emit("app:route", payload.toString())
            }
        }

        // --- evolution:progress / evolution:complete ----------------
        scope.launch {
            SnishaperEngine.evolution
                .map { it?.rawJson ?: "" }
                .distinctUntilChanged()
                .collect { raw ->
                    if (raw.isBlank()) return@collect
                    val finished = runCatching { JSONObject(raw).optBoolean("finished", false) }
                        .getOrDefault(false)
                    bridge.emit(
                        if (finished) "evolution:complete" else "evolution:progress",
                        raw,
                    )
                }
        }
    }

    fun stop() {
        scope.cancel()
    }
}
