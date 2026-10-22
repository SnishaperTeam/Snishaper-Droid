package com.snishaper.android.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.snishaper.android.BuildConfig
import com.snishaper.android.core.SnishaperEngine
import com.snishaper.android.vpn.TunVpnService

/**
 * Hosts the reused desktop React frontend inside a WebView.
 *
 * The whole UI is the desktop bundle (`app/src/main/assets/frontend`), rendered
 * by the same React 19 + MUI 9 code the desktop build ships. Kotlin's only job
 * is to expose the gomobile engine through the Wails Android transport, so the
 * frontend talks to it exactly as it talks to the desktop Go app.
 *
 * The activity is the launcher entry point, so it is still named `MainActivity`.
 */
open class MainWebViewActivity : ComponentActivity() {

    private companion object {
        const val TAG = SnishaperEngine.LOG_TAG
    }

    private lateinit var webView: WebView
    private lateinit var bridge: EngineBridge
    private lateinit var eventPump: WailsEventPump
    private val assetServer = AssetServer()

    /**
     * VPN consent flow: `VpnService.prepare()` returns an intent until the user
     * grants permission. The bridge calls [requestVpnConsent]; on RESULT_OK the
     * TUN service is started.
     */
    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            runCatching { startForegroundService(Intent(this, TunVpnService::class.java)) }
                .onFailure { Log.e(TAG, "vpn: start service failed: ${it.message}") }
        }
    }

    /** Called by [EngineBridge] when the TUN toggle needs the VPN consent dialog. */
    fun requestVpnConsent(consent: Intent) {
        runCatching { vpnConsentLauncher.launch(consent) }
            .onFailure { Log.e(TAG, "vpn: consent launch failed: ${it.message}") }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        SnishaperEngine.initialize(applicationContext)

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.parseColor("#0E1116"))
            overScrollMode = WebView.OVER_SCROLL_NEVER
        }

        configureSettings(webView.settings)

        bridge = EngineBridge(webView)
        eventPump = WailsEventPump(bridge)

        // The runtime probes window.wails.invokeAsync before the bundle runs.
        webView.addJavascriptInterface(WailsJavascriptInterface(bridge), "wails")

        webView.webViewClient = object : WebViewClient() {
            /**
             * Serve the bundled SPA from a synthetic HTTPS origin.
             *
             * `file://` is an opaque origin, so Chromium rejects the
             * `<script type="module">` tags Vite emits and the page stays blank.
             * Serving over `https://appassets.androidplatform.net` restores
             * ES-module loading plus normal localStorage/sessionStorage.
             */
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? = assetServer.serve(view, request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val url = request.url
                // Everything served by the asset server stays in the WebView;
                // external links go to the system browser.
                if (assetServer.handles(url)) return false
                return runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                    true
                }.getOrDefault(false)
            }

            override fun onPageFinished(view: WebView, url: String) {
                Log.i(TAG, "webview: page finished ($url)")
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail,
            ): Boolean {
                Log.e(TAG, "webview: render process gone (crashed=${detail.didCrash()})")
                recreate()
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.i(TAG, "webview console: ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                return true
            }
        }

        setContentView(webView)

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.loadUrl(AssetServer.START_URL)

        eventPump.start()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // The SPA keeps a react-router history; prefer it over closing.
                    if (webView.canGoBack()) webView.goBack() else finish()
                }
            },
        )
    }

    private fun configureSettings(settings: WebSettings) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true // the frontend persists prefs in localStorage
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = false
        settings.textZoom = 100
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        // The bundle is local; no remote origin is ever needed.
        settings.blockNetworkImage = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
        eventPump.start()
    }

    override fun onPause() {
        webView.onPause()
        webView.pauseTimers()
        super.onPause()
    }

    override fun onDestroy() {
        eventPump.stop()
        runCatching {
            webView.removeJavascriptInterface("wails")
            webView.destroy()
        }
        super.onDestroy()
    }
}
