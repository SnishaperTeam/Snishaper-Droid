package com.snishaper.android.webview

import android.util.Log
import android.webkit.WebView
import com.snishaper.android.core.SnishaperEngine
import com.snishaper.core.mobile.App
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serves the desktop frontend's `Call.ByName("snishaper/app.App.<Name>")` calls
 * on top of the CA-free gomobile AAR.
 *
 * ## Transport contract
 * Verified against `@wailsio/runtime/dist/runtime.js`:
 *
 *  * The runtime detects `window.wails.invokeAsync` and installs its Android
 *    transport, so the frontend keeps using the stock `Call` / `Events` API.
 *  * A bound-method call arrives as
 *    `invokeAsync(id, JSON.stringify({object, method, windowName, args, clientId}))`
 *    with `object = 0` (objectNames.Call), `method = 0` (CallBinding) and
 *    `args = [{"call-id": id, "methodName": "snishaper/app.App.Foo", "args": [...]}]`.
 *  * The reply is `window._wailsAndroidCallback(id, "<envelope json>", null)`
 *    where the envelope is `{"ok":true,"data":<value>}` — transported as a
 *    *string* because the runtime calls `JSON.parse` on it.
 *
 * ## Why JSON passthrough
 * The desktop app binds Go structs directly (`proxy.SiteGroup`, `proxy.TUNConfig`,
 * …), and the gomobile wrapper exposes the very same structs through its `*JSON`
 * methods. Forwarding those JSON strings untouched guarantees the frontend sees
 * exactly the shapes the desktop build produces, including fields the old
 * Compose UI never needed. Re-encoding through `Models.kt` would silently drop
 * them.
 *
 * ## CA
 * This bridge exposes **no** certificate-authority surface. The desktop methods
 * `ExportCert`, `GetCACertPEM`, `GetCACertPath`, `GetCAInstallStatus`,
 * `GetInstalledCerts`, `InstallCA`, `OpenCAFile`, `OpenCertDir`,
 * `RegenerateCert` and `UninstallCert` are absent here and their frontend
 * counterparts were deleted, so no such call can be produced.
 */
internal class EngineBridge(private val webView: WebView) {

    private companion object {
        const val TAG = SnishaperEngine.LOG_TAG
        const val BINDING_PREFIX = "snishaper/app.App."

        // objectNames.Call -> CallBinding
        const val OBJECT_CALL = 0
        const val METHOD_CALL_BINDING = 0
        // objectNames.CancelCall / objectNames.Events
        const val OBJECT_CANCEL_CALL = 10
        const val OBJECT_EVENTS = 3
    }

    // ------------------------------------------------------------------
    // Entry point (called from the @JavascriptInterface on the JS thread)
    // ------------------------------------------------------------------

    fun handle(callId: String, payload: String) {
        val request = runCatching { JSONObject(payload) }.getOrNull()
        if (request == null) {
            reply(callId, error = "malformed bridge payload")
            return
        }

        val objectId = request.optInt("object", -1)
        val methodId = request.optInt("method", -1)

        // Calls that carry no engine work: settle them so the JS promise does
        // not leak, exactly like a successful no-op on the desktop side.
        if (objectId != OBJECT_CALL || methodId != METHOD_CALL_BINDING) {
            if (objectId != OBJECT_CANCEL_CALL && objectId != OBJECT_EVENTS) {
                Log.w(TAG, "bridge: unsupported runtime object=$objectId method=$methodId")
            }
            reply(callId, value = null)
            return
        }

        // The runtime version in the bundle sends the call descriptor as the
        // `args` object directly; older builds wrapped it as `args[0]`.
        // Accept both shapes.
        val argsNode = request.opt("args")
        val descriptor = when (argsNode) {
            is JSONArray -> argsNode.optJSONObject(0)
            is JSONObject -> argsNode
            else -> null
        }
        if (descriptor == null) {
            reply(callId, error = "bridge payload has no call descriptor")
            return
        }

        val rawName = descriptor.optString("methodName", "")
        val name = rawName.removePrefix(BINDING_PREFIX).ifBlank { rawName }
        val args = descriptor.optJSONArray("args") ?: JSONArray()

        EngineWorker.run(
            onResult = { value -> reply(callId, value = value) },
            onError = { message ->
                Log.e(TAG, "bridge: $name failed: $message")
                reply(callId, error = message)
            },
        ) {
            dispatch(name, Args(args))
        }
    }

    // ------------------------------------------------------------------
    // Reply
    // ------------------------------------------------------------------

    private fun reply(callId: String, value: Any? = null, error: String? = null) {
        val script = if (error != null) {
            JsonBridge.errorCallback(callId, error)
        } else {
            JsonBridge.successCallback(callId, value)
        }
        postScript(script)
    }

    /** Runs a JS expression on the UI thread. */
    fun postScript(script: String) {
        webView.post { runCatching { webView.evaluateJavascript(script, null) } }
    }

    /** Dispatches a Wails event to the page (data is serialized JSON). */
    fun emit(name: String, dataJson: String) {
        postScript(JsonBridge.eventScript(name, dataJson))
    }

    // ------------------------------------------------------------------
    // Method routing
    // ------------------------------------------------------------------

    /**
     * Executes one bridge call. Runs on a background thread.
     *
     * JSON-returning gomobile methods are parsed here so the JS side receives a
     * real JSON value (not a doubly-encoded string). Methods whose desktop
     * counterpart returns a plain string (e.g. `ExportConfig`, `FetchECHConfig`,
     * `GetRecentLogs`) keep returning a string.
     */
    private fun dispatch(name: String, a: Args): Any? = withApp { app ->
        when (name) {
            // ---- proxy lifecycle -------------------------------------
            "StartProxy" -> { app.startProxy(); null }
            "StopProxy" -> { app.stopProxy(); null }
            "IsProxyRunning" -> app.isProxyRunning()
            "GetListenPort" -> JSONObject(app.getStatusJSON()).optInt("listenPort", 0)
            "SetListenPort" -> { app.setListenPort(a.int(0)); null }
            "GetProxyMode" -> app.getProxyMode()
            "SetProxyMode" -> { app.setProxyMode(a.string(0)); null }
            "GetStats" -> parse(app.getStatsJSON())

            // ---- status / logs ---------------------------------------
            "GetIPv6Available" -> app.getIPv6Available()
            "RefreshIPv6Check" -> app.refreshIPv6Check()
            "GetRecentLogs" -> app.getRecentLogs(a.int(0, 500).takeIf { it > 0 } ?: 500)
            "ClearLogs" -> { app.clearLogs(); null }

            // ---- site groups -----------------------------------------
            "GetSiteGroups" -> parse(app.getSiteGroupsJSON())
            "AddSiteGroup" -> app.addSiteGroupJSON(a.obj(0).toString())
            "UpdateSiteGroup" -> { app.updateSiteGroupJSON(a.obj(0).toString()); null }
            "DeleteSiteGroup" -> { app.deleteSiteGroup(a.string(0)); null }

            // ---- upstreams -------------------------------------------
            "GetUpstreams" -> parse(app.getUpstreamsJSON())
            "AddUpstream" -> app.addUpstreamJSON(a.obj(0).toString())
            "UpdateUpstream" -> { app.updateUpstreamJSON(a.obj(0).toString()); null }
            "DeleteUpstream" -> { app.deleteUpstream(a.string(0)); null }

            // ---- DNS -------------------------------------------------
            "GetDNSNodes" -> parse(app.getDNSNodesJSON())
            "AddDNSNode" -> app.addDNSNodeJSON(a.obj(0).toString())
            "UpdateDNSNode" -> { app.updateDNSNodeJSON(a.obj(0).toString()); null }
            "DeleteDNSNode" -> { app.deleteDNSNode(a.string(0)); null }
            "SetDNSNodePriority" -> { app.setDNSNodePriority(a.string(0), a.int(1)); null }
            "TestDNSNode" -> parse(app.testDNSNode(a.string(0)))

            // ---- ECH -------------------------------------------------
            "GetECHProfiles" -> parse(app.getECHProfilesJSON())
            "UpsertECHProfile" -> { app.upsertECHProfileJSON(a.obj(0).toString()); null }
            "DeleteECHProfile" -> { app.deleteECHProfile(a.string(0)); null }
            "FetchECHConfig" -> app.fetchECHConfigJSON(a.string(0), a.string(1))

            // ---- NAT64 -----------------------------------------------
            "GetNAT64Profiles" -> parse(app.getNAT64ProfilesJSON())
            "AddNAT64Profile" -> app.addNAT64ProfileJSON(a.obj(0).toString())
            "UpdateNAT64Profile" -> { app.updateNAT64ProfileJSON(a.obj(0).toString()); null }
            "DeleteNAT64Profile" -> { app.deleteNAT64Profile(a.string(0)); null }
            "TestNAT64Profile" -> parse(app.testNAT64Profile(a.string(0)))

            // ---- fragment / TUN / Cloudflare -------------------------
            "GetFragmentConfig" -> parse(app.getFragmentConfigJSON())
            "SetFragmentConfig" -> { app.setFragmentConfigJSON(a.obj(0).toString()); null }
            "ResetFragmentConfig" -> { app.resetFragmentConfig(); null }

            "GetTUNConfig" -> parse(app.getTUNConfigJSON())
            "UpdateTUNConfig" -> { app.updateTUNConfigJSON(a.obj(0).toString()); null }
            "GetTUNStatus" -> parse(app.getTUNStatusJSON())

            // ---- TUN lifecycle (Android VpnService) -------------------
            // Start: consent already granted -> start the service directly;
            // otherwise hand the consent intent to the host activity, the
            // frontend polls GetTUNStatus until the tunnel is actually up.
            "StartTUN" -> {
                val context = webView.context
                val consent = com.snishaper.android.vpn.VpnInstaller.prepare(context)
                if (consent == null) {
                    // minSdk 24: ContextCompat falls back to startService below API 26
                    androidx.core.content.ContextCompat.startForegroundService(
                        context,
                        android.content.Intent(context, com.snishaper.android.vpn.TunVpnService::class.java),
                    )
                    "started"
                } else {
                    val activity = context as? MainWebViewActivity
                        ?: throw BridgeException("VPN consent requires the main activity")
                    activity.requestVpnConsent(consent)
                    "consent"
                }
            }
            "StopTUN" -> {
                val context = webView.context
                context.startService(
                    android.content.Intent(context, com.snishaper.android.vpn.TunVpnService::class.java)
                        .setAction(com.snishaper.android.vpn.TunVpnService.ACTION_STOP),
                )
                "stopped"
            }

            "GetCloudflareConfig" -> parse(app.getCloudflareConfigJSON())
            "UpdateCloudflareConfig" -> { app.updateCloudflareConfigJSON(a.obj(0).toString()); null }
            "GetCloudflareIPStats" -> parse(app.getCloudflareIPStatsJSON())
            "ForceFetchCloudflareIPs" -> { app.forceFetchCloudflareIPs(); null }
            "RefreshCloudflareIPPool" -> { app.forceFetchCloudflareIPs(); null }
            "TriggerCFHealthCheck" -> { app.triggerCFHealthCheck(); null }
            "RemoveInvalidCFIPs" -> { app.removeInvalidCFIPs(); null }

            // ---- routing (Android: read-only status) -----------------
            "GetAutoRoutingStatus" -> parse(app.getAutoRoutingStatusJSON())
            "GetAutoRoutingConfig" -> parse(app.getAutoRoutingConfigJSON())
            "UpdateAutoRoutingConfig" -> { app.updateAutoRoutingConfigJSON(a.obj(0).toString()); null }

            // ---- evolution -------------------------------------------
            "StartEvolutionTest" -> {
                app.startEvolutionTest(a.stringList(0).toString(), a.bool(1))
                parse(app.getEvolutionStatusJSON())
            }
            "StopEvolutionTest" -> { app.stopEvolutionTest(); null }
            "GetEvolutionTestStatus" -> parse(app.getEvolutionStatusJSON())
            "ApplyEvolutionRule" -> { app.applyEvolutionRule(a.string(0)); null }

            // ---- import / export -------------------------------------
            // Desktop `ExportConfig` returns a JSON *string*: keep that shape.
            "ExportConfig" -> app.exportConfigJSON()
            "ImportConfig" -> { app.importConfigWithSummaryJSON(a.string(0)); null }
            "ImportConfigWithSummary" -> parse(app.importConfigWithSummaryJSON(a.string(0)))

            // ---- settings --------------------------------------------
            "GetLanguage" -> settings().optString("language", "")
            "SetLanguage" -> { patchSettings { it.put("language", a.string(0)) }; null }
            "GetTheme" -> settings().optString("theme", "")
            "SetTheme" -> { patchSettings { it.put("theme", a.string(0)) }; null }
            "GetAppearanceMode" -> settings().optString("appearance_mode", "")
            "SetAppearanceMode" -> { patchSettings { it.put("appearance_mode", a.string(0)) }; null }
            "GetAutoUpdateRules" -> settings().optBoolean("auto_update_rules", false)
            "SetAutoUpdateRules" -> { patchSettings { it.put("auto_update_rules", a.bool(0)) }; null }
            "UpdateRules" -> { app.forceFetchCloudflareIPs(); null }

            "GetAppVersion" -> appVersion()

            else -> throw BridgeException("unsupported Android bridge method: $name")
        }
    }

    // ------------------------------------------------------------------
    // Engine access helpers
    // ------------------------------------------------------------------

    /** Runs [block] with the live gomobile `App` on the IO dispatcher. */
    private fun <T> withApp(block: (App) -> T): T = runBlocking {
        SnishaperEngine.withApp { app -> block(app) }
    }

    private fun settings(): JSONObject = SnishaperEngine.settings.value.rawJson
        .let { runCatching { JSONObject(it) }.getOrElse { JSONObject() } }

    /**
     * Applies a single settings key. `UpdateSettingsJSON` replaces every section
     * it receives, so the complete current document is patched and sent back.
     */
    private fun patchSettings(edit: (JSONObject) -> Unit) {
        val current = settings()
        edit(current)
        runBlocking { SnishaperEngine.withApp { app -> app.updateSettingsJSON(current.toString()) } }
        runBlocking { SnishaperEngine.loadSettings() }
    }

    private fun appVersion(): String =
        runCatching {
            webView.context.packageManager
                .getPackageInfo(webView.context.packageName, 0)
                .versionName ?: ""
        }.getOrDefault("")

    /** Parses a gomobile JSON payload into a real JSON value for the envelope. */
    private fun parse(raw: String): Any? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (!text.startsWith("{") && !text.startsWith("[")) return text
        return runCatching { org.json.JSONTokener(text).nextValue() }.getOrNull() ?: text
    }

    /** Positional view over the bridge call's `args` array. */
    private class Args(private val array: JSONArray) {
        fun obj(index: Int): JSONObject =
            array.optJSONObject(index) ?: throw BridgeException("argument $index is missing")

        fun string(index: Int): String = array.optString(index, "")

        fun int(index: Int, fallback: Int = 0): Int =
            if (array.isNull(index)) fallback else array.optInt(index, fallback)

        fun bool(index: Int): Boolean = array.optBoolean(index, false)

        fun stringList(index: Int): JSONArray {
            val source = array.optJSONArray(index) ?: return JSONArray()
            val out = JSONArray()
            for (i in 0 until source.length()) {
                val value = source.optString(i, "")
                if (value.isNotBlank()) out.put(value)
            }
            return out
        }
    }
}

/** Runs bridge work off the UI thread. */
internal object EngineWorker {

    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "snishaper-bridge").apply { isDaemon = true }
    }

    fun <T> run(onResult: (T) -> Unit, onError: (String) -> Unit, block: () -> T) {
        executor.execute {
            try {
                onResult(block())
            } catch (error: Throwable) {
                onError(error.message ?: error.javaClass.simpleName)
            }
        }
    }
}
