// Top-level build file. All plugin versions are pinned here (single source of truth).
//
// AGP 9.0+ ships built-in Kotlin support, so `org.jetbrains.kotlin.android` must NOT be
// applied (it fails with "no longer required since AGP 9.0"). Only the Compose compiler
// Gradle plugin is needed on top of AGP's built-in Kotlin.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
