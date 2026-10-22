package com.snishaper.android.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Desktop theme ids, mirrored from `frontend/src/theme/index.ts:148-155`. */
object ThemeIds {
    const val DEFAULT = "default"
    const val SNISHAPER = "sniShaper"
    const val FOREST = "forest"
    const val SUNSET = "sunset"
    const val OCEAN = "ocean"
    const val GRAPHITE = "graphite"

    val all = listOf(DEFAULT, SNISHAPER, FOREST, SUNSET, OCEAN, GRAPHITE)
}

/** Desktop appearance modes: light / dark / system. */
enum class AppearanceMode {
    LIGHT,
    DARK,
    SYSTEM;

    companion object {
        fun fromRaw(raw: String?): AppearanceMode = when (raw?.lowercase()) {
            "light" -> LIGHT
            "dark" -> DARK
            else -> SYSTEM
        }
    }
}

/**
 * Persisted app preferences (DataStore). Only shell-level state lives here for stage 2;
 * proxy/rule settings will be persisted by the Go core via `settings.json` later.
 */
data class AppPreferences(
    val drawerOpen: Boolean = false,
    val themeId: String = ThemeIds.DEFAULT,
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
    /** Empty string = first launch, show the welcome language picker. */
    val language: String = "",
    val autoStartProxy: Boolean = false,
    val autoUpdateRules: Boolean = true,
    val useDynamicColor: Boolean = false,
)

private val Context.snishaperDataStore by preferencesDataStore(name = "snishaper_prefs")

/** Thin DataStore facade used by the Compose shell. */
class PreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.snishaperDataStore

    private object Keys {
        val DRAWER_OPEN = booleanPreferencesKey("drawer_open")
        val THEME_ID = stringPreferencesKey("theme_id")
        val APPEARANCE = stringPreferencesKey("appearance_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val AUTO_START_PROXY = booleanPreferencesKey("auto_start_proxy")
        val AUTO_UPDATE_RULES = booleanPreferencesKey("auto_update_rules")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    }

    val preferences: Flow<AppPreferences> = dataStore.data.map { prefs ->
        AppPreferences(
            drawerOpen = prefs[Keys.DRAWER_OPEN] ?: false,
            themeId = prefs[Keys.THEME_ID] ?: ThemeIds.DEFAULT,
            appearance = AppearanceMode.fromRaw(prefs[Keys.APPEARANCE]),
            language = prefs[Keys.LANGUAGE] ?: "",
            autoStartProxy = prefs[Keys.AUTO_START_PROXY] ?: false,
            autoUpdateRules = prefs[Keys.AUTO_UPDATE_RULES] ?: true,
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: false,
        )
    }

    suspend fun setDrawerOpen(open: Boolean) = dataStore.edit { it[Keys.DRAWER_OPEN] = open }

    suspend fun setThemeId(themeId: String) = dataStore.edit { it[Keys.THEME_ID] = themeId }

    suspend fun setAppearance(mode: AppearanceMode) = dataStore.edit { it[Keys.APPEARANCE] = mode.name.lowercase() }

    suspend fun setLanguage(language: String) = dataStore.edit { it[Keys.LANGUAGE] = language }

    suspend fun setAutoStartProxy(enabled: Boolean) = dataStore.edit { it[Keys.AUTO_START_PROXY] = enabled }

    suspend fun setAutoUpdateRules(enabled: Boolean) = dataStore.edit { it[Keys.AUTO_UPDATE_RULES] = enabled }

    suspend fun setUseDynamicColor(enabled: Boolean) = dataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
}
