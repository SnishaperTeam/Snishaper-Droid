package com.snishaper.android.webview

import android.net.Uri
import android.webkit.WebResourceResponse
import android.webkit.WebView

/**
 * Serves the bundled frontend from a synthetic HTTPS origin instead of `file://`.
 *
 * ## Why
 * Chromium treats `file://` documents as an opaque (`null`) origin, so the
 * `<script type="module">` tags that Vite emits are rejected with
 * *"Cross origin requests are only supported for protocol schemes: http, data,
 * chrome, https…"*. The page then renders blank while the Go engine is perfectly
 * healthy — exactly the failure observed on the emulator.
 *
 * Loading the bundle from a real origin fixes ES modules, and as a bonus makes
 * `localStorage`, `sessionStorage` and `fetch` behave normally (they are all
 * partitioned/limited on `file://`).
 *
 * ## Why not WebViewAssetLoader
 * `androidx.webkit.WebViewAssetLoader` does the same thing, but it would add a
 * dependency for ~40 lines of code. Intercepting requests ourselves keeps the
 * dependency list unchanged.
 */
internal class AssetServer {

    companion object {
        /** Synthetic origin. `.net` avoids colliding with real domains. */
        const val ORIGIN = "https://appassets.androidplatform.net"
        const val ASSET_ROOT = "frontend"
        const val ENTRY_PATH = "/$ASSET_ROOT/index.html"

        /** The URL the WebView loads. */
        const val START_URL = ORIGIN + ENTRY_PATH

        private val MIME_TYPES = mapOf(
            "html" to "text/html",
            "js" to "application/javascript",
            "mjs" to "application/javascript",
            "css" to "text/css",
            "json" to "application/json",
            "map" to "application/json",
            "svg" to "image/svg+xml",
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "webp" to "image/webp",
            "ico" to "image/x-icon",
            "woff" to "font/woff",
            "woff2" to "font/woff2",
            "ttf" to "font/ttf",
            "wasm" to "application/wasm",
            "txt" to "text/plain",
        )
    }

    /** True when [url] belongs to the synthetic origin. */
    fun handles(url: Uri?): Boolean = url?.host == "appassets.androidplatform.net"

    /**
     * Maps a synthetic-origin URL onto a packaged asset and returns it, or
     * `null` when the request is not ours (the WebView then proceeds normally).
     */
    fun serve(webView: WebView, url: Uri?): WebResourceResponse? {
        if (!handles(url)) return null

        val path = url!!.path ?: return notFound()
        // "/frontend/assets/index.js" -> "frontend/assets/index.js"
        val assetPath = path.trimStart('/')
        if (assetPath.isBlank() || assetPath.contains("..")) return notFound()

        val extension = assetPath.substringAfterLast('.', "").lowercase()
        val mime = MIME_TYPES[extension] ?: "application/octet-stream"
        val encoding = if (mime.startsWith("text/") || mime.contains("json") ||
            mime.contains("javascript") || mime.contains("svg")
        ) "UTF-8" else null

        // The HTML entry point gets the bridge bootstrap injected so the Wails
        // runtime detects the Android transport deterministically.
        if (extension == "html") {
            val html = runCatching {
                webView.context.assets.open(assetPath).bufferedReader().use { it.readText() }
            }.getOrNull() ?: return notFound()
            return htmlResponse(html)
        }

        val stream = runCatching {
            webView.context.assets.open(assetPath)
        }.getOrNull() ?: return notFound()

        return WebResourceResponse(
            mime,
            encoding,
            stream,
        ).apply {
            responseHeaders = mapOf(
                "Cache-Control" to "no-cache",
                // Same-origin today, but keeps the door open for a future
                // cross-origin split bundle without a native change.
                "Access-Control-Allow-Origin" to "*",
            )
        }
    }

    /** Serves HTML with the bridge bootstrap prepended to `<head>`. */
    private fun htmlResponse(html: String): WebResourceResponse =
        WebResourceResponse(
            "text/html",
            "UTF-8",
            java.io.ByteArrayInputStream(injectBootstrap(html).toByteArray(Charsets.UTF_8)),
        ).apply {
            responseHeaders = mapOf("Cache-Control" to "no-cache")
        }
    /**
     * Inserts the bootstrap script as the first element of `<head>`.
     *
     * When the document has no `<head>` (malformed or partial), the script is
     * prepended to the body instead.
     */
    private fun injectBootstrap(html: String): String {
        val script = "<script>${bootstrapScript()}</script>"
        val headIndex = html.indexOf("<head>", ignoreCase = true)
        if (headIndex >= 0) {
            val insertAt = headIndex + "<head>".length
            return html.substring(0, insertAt) + script + html.substring(insertAt)
        }
        val htmlTag = html.indexOf("<html", ignoreCase = true)
        if (htmlTag >= 0) {
            val insertAt = html.indexOf(">", htmlTag) + 1
            return html.substring(0, insertAt) + script + html.substring(insertAt)
        }
        return script + html
    }

    private fun notFound(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "UTF-8",
        java.io.ByteArrayInputStream("not found".toByteArray()),
    )

    /**
     * Script injected into every HTML response, *before* the bundle's own
     * scripts run.
     *
     * `@wailsio/runtime` decides between the Android transport and its HTTP
     * fallback with a single top-level check that runs while the module is
     * being evaluated:
     *
     * ```js
     * const androidBridge = hasDOM && typeof window.wails?.invokeAsync === "function"
     *     ? window.wails : null;
     * ```
     *
     * The native object comes from `addJavascriptInterface`, which the WebView
     * installs on the page *after* the document starts loading. The module
     * check can therefore run before the native object exists, and the runtime
     * then silently falls back to HTTP — which surfaces as *"Method not
     * found"*, because there is no Wails server behind the synthetic origin.
     *
     * The fix is to make `window.wails.invokeAsync` a **function from the very
     * first moment**, and have it resolve the native implementation lazily, at
     * call time — by which point the WebView has certainly installed it.
     *
     * The shim deliberately does *not* replace `window.wails` when the native
     * object is already present, and it never captures the native object in a
     * closure: the lookup happens on every call, so a late injection is still
     * picked up.
     */
    fun bootstrapScript(): String = """
        (function () {
          if (window.__snishaperWailsReady) return;
          window.__snishaperWailsReady = true;

          // The native object, once the WebView installs it, is reachable as
          // `window.__snishaperNativeWails` (see the setter below).
          function nativeInvoke(id, payload) {
            var native = window.__snishaperNativeWails;
            if (!native && window.wails && !window.wails.__snishaperShim) {
              native = window.wails;
            }
            if (native && typeof native.invokeAsync === 'function') {
              return native.invokeAsync(id, payload);
            }
            console.error('[snishaper] native wails bridge is unavailable');
          }

          var shim = { __snishaperShim: true, invokeAsync: nativeInvoke };

          // Remember whatever the WebView installs as `window.wails`.
          var existing = window.wails;
          if (existing && typeof existing.invokeAsync === 'function') {
            window.__snishaperNativeWails = existing;
            return;
          }

          try {
            Object.defineProperty(window, 'wails', {
              configurable: true,
              enumerable: true,
              get: function () {
                return window.__snishaperNativeWails || shim;
              },
              set: function (value) {
                window.__snishaperNativeWails = value;
              }
            });
          } catch (e) {
            window.wails = shim;
          }
        })();
    """.trimIndent()
}
