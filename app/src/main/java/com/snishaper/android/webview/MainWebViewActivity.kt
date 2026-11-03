package com.snishaper.android.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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

    /**
     * Null after the render process died (the object is unusable then) and
     * during teardown — always access through a safe call.
     */
    private var webView: WebView? = null
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
            // minSdk 24: ContextCompat falls back to startService below API 26
            runCatching { ContextCompat.startForegroundService(this, Intent(this, TunVpnService::class.java)) }
                .onFailure { Log.e(TAG, "vpn: start service failed: ${it.message}") }
        }
    }

    /** Called by [EngineBridge] when the TUN toggle needs the VPN consent dialog. */
    fun requestVpnConsent(consent: Intent) {
        runCatching { vpnConsentLauncher.launch(consent) }
            .onFailure { Log.e(TAG, "vpn: consent launch failed: ${it.message}") }
    }

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    /**
     * Backs `<input type="file">` in the WebView (profile import). WebChromeClient
     * must hand the picked URI back through the stored callback, otherwise the
     * picker opens and the page never receives a file.
     */
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = fileChooserCallback
        fileChooserCallback = null
        if (callback == null) return@registerForActivityResult
        val uri = result.data?.data
        callback.onReceiveValue(if (result.resultCode == RESULT_OK && uri != null) arrayOf(uri) else null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        SnishaperEngine.initialize(applicationContext)

        // Root container that turns window insets (system bars, display
        // cutout, IME) into padding around the WebView, so web content never
        // sits under the status / navigation bars. Doing this natively is more
        // reliable than CSS env(safe-area-inset-*), which WebView only exposes
        // from M136 (fullscreen WebViews only) / M144 (all WebViews). It also
        // restores keyboard resizing, which adjustResize no longer provides
        // once edge-to-edge is enabled (API 30+).
        val root = FrameLayout(this).apply {
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
                val types = WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
                val insets = windowInsets.getInsets(types)
                view.updatePadding(
                    left = insets.left,
                    top = insets.top,
                    right = insets.right,
                    bottom = insets.bottom,
                )
                // Zero out the handled types instead of consuming the whole
                // object: WebView still gets notified when insets change, but
                // never applies the same padding twice (no double padding and
                // no "ghost" padding left over when the keyboard hides).
                WindowInsetsCompat.Builder(windowInsets)
                    .setInsets(types, Insets.NONE)
                    .build()
            }
        }

        val view = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor("#0E1116".toColorInt())
            overScrollMode = WebView.OVER_SCROLL_NEVER
        }
        // Local non-null val: the field is nullable, and lambdas below would
        // lose the smart cast otherwise.
        webView = view
        root.addView(view)

        configureSettings(view.settings)

        bridge = EngineBridge(view)
        eventPump = WailsEventPump(bridge)

        // The runtime probes window.wails.invokeAsync before the bundle runs.
        view.addJavascriptInterface(WailsJavascriptInterface(bridge), "wails")

        view.webViewClient = object : WebViewClient() {
            /**
             * Serve the bundled SPA from a synthetic HTTPS origin.
             *
             * `file://` is an opaque origin, so Chromium rejects the
             * `<script type="module">` tags Vite emits and the page stays blank.
             * Serving over the synthetic HTTPS origin (host
             * `appassets.androidplatform.net`) restores ES-module loading plus
             * normal localStorage/sessionStorage.
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
                return !assetServer.handles(url) && runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                    true
                }.getOrDefault(defaultValue = false)
            }

            override fun onPageFinished(view: WebView, url: String) {
                Log.i(TAG, "webview: page finished ($url)")
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail,
            ): Boolean {
                // didCrash() is API 26+; minSdk is 24
                val crashed = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) && detail.didCrash()
                Log.e(TAG, "webview: render process gone (crashed=$crashed)")
                // The WebView object is unusable now: destroy it immediately
                // instead of letting the teardown lifecycle below touch dead
                // native state, then rebuild the whole activity.
                runCatching {
                    (view.parent as? android.view.ViewGroup)?.removeView(view)
                    view.destroy()
                }
                webView = null
                recreate()
                return true
            }
        }

        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.i(TAG, "webview console: ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                return true
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = params.acceptTypes
                        ?.firstOrNull { it.isNotBlank() && !it.contains(',') && it.contains('/') }
                        ?: "*/*"
                }
                return runCatching {
                    fileChooserLauncher.launch(intent)
                    true
                }.getOrElse {
                    fileChooserCallback = null
                    Log.e(TAG, "webview: file picker failed: ${it.message}")
                    false
                }
            }
        }

        setContentView(root)

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        view.loadUrl(AssetServer.START_URL)

        eventPump.start()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(enabled = true) {
                override fun handleOnBackPressed() {
                    // The SPA keeps a react-router history; prefer it over closing.
                    val view = webView
                    if (view != null && view.canGoBack()) view.goBack() else finish()
                }
            },
        )
    }

    // The bundled SPA is fully trusted (it ships inside the APK), so keeping
    // JavaScript enabled is intentional; the lint warning is expected here.
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureSettings(settings: WebSettings) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true // the frontend persists prefs in localStorage
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
        webView?.onResume()
        webView?.resumeTimers()
        // NOTE: no eventPump.start() here — every resume would stack another
        // set of collectors on the same flows (triple events). Started once in
        // onCreate, stopped in onDestroy.
    }

    override fun onPause() {
        webView?.onPause()
        webView?.pauseTimers()
        super.onPause()
    }

    override fun onDestroy() {
        eventPump.stop()
        webView?.let { view ->
            runCatching {
                view.removeJavascriptInterface("wails")
                view.destroy()
            }
        }
        webView = null
        super.onDestroy()
    }
}
