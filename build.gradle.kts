// Top-level build file. All plugin versions are pinned here (single source of truth).
//
// AGP 9.0+ ships built-in Kotlin support, so `org.jetbrains.kotlin.android` must NOT be
// applied (it fails with "no longer required since AGP 9.0"). No Compose stack exists:
// the UI is the desktop React bundle served in a WebView.
plugins {
    id("com.android.application") version "9.6.0-alpha03" apply false
}
