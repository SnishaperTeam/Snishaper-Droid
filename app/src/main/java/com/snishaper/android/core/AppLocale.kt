package com.snishaper.android.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * In-app language override. The desktop client switches language at runtime
 * (`frontend/src/i18n`); Android replicates it by wrapping the base context with a
 * per-app configuration context, so every `stringResource` in the Compose tree uses
 * the selected locale without restarting the app or adding the AppCompat dependency.
 *
 * The activity base context is wrapped once in `MainActivity.attachBaseContext`
 * using the language persisted in DataStore.
 */
object AppLocale {

    /** Languages supported by the Android shell (same set as the desktop client). */
    val supported = listOf("zh", "en", "ru")

    fun wrap(context: Context, language: String): Context {
        val tag = if (language.isNotBlank() && language in supported) language else "zh"
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
