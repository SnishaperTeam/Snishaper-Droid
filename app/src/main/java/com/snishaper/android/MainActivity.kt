package com.snishaper.android

import com.snishaper.android.webview.MainWebViewActivity

/**
 * Launcher activity.
 *
 * The UI is now the reused desktop React frontend hosted in a WebView, so this
 * class is a thin, manifest-stable alias for [MainWebViewActivity]. Keeping the
 * name means the launcher intent filter and the smoke-test script
 * (`com.snishaper.android/.MainActivity`) stay valid.
 */
class MainActivity : MainWebViewActivity()
