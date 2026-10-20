package com.snishaper.android.core

import android.content.Context
import java.io.File

/**
 * Seeds the Go engine data directory on first launch.
 *
 * The Go wrapper expects `<filesDir>/config/settings.json` and
 * `<filesDir>/rules/config.json` (see docs/android-bridge-api.md §2). The APK ships
 * CA-free defaults under `assets/defaults/`; they are copied once and never
 * overwritten afterwards (the Go engine persists user changes back to the same
 * files).
 */
object AssetSeeder {

    fun seed(context: Context) {
        val filesDir = context.filesDir
        copyIfMissing(context, "defaults/settings.json", File(filesDir, "config/settings.json"))
        copyIfMissing(context, "defaults/rules.json", File(filesDir, "rules/config.json"))
    }

    private fun copyIfMissing(context: Context, assetPath: String, target: File) {
        if (target.exists() && target.length() > 0L) return
        target.parentFile?.mkdirs()
        runCatching {
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}
