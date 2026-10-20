package com.snishaper.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.snishaper.android.data.AppearanceMode
import com.snishaper.android.data.PreferencesRepository
import com.snishaper.android.data.ThemeIds
import com.snishaper.android.ui.MainScaffold
import com.snishaper.android.ui.pages.WelcomeScreen
import com.snishaper.android.ui.theme.SnishaperTheme
import kotlinx.coroutines.launch

/**
 * Application root: loads DataStore preferences, applies the M3 theme, then shows either the
 * first-run Welcome language picker or the main drawer + NavHost shell.
 */
@Composable
fun SnishaperApp() {
    val context = LocalContext.current.applicationContext
    val repository = remember { PreferencesRepository(context) }
    val preferences by repository.preferences.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val current = preferences
    if (current == null) {
        // DataStore not ready yet: keep the splash-ish loading state on the default theme.
        SnishaperTheme(themeId = ThemeIds.DEFAULT, darkTheme = isSystemInDarkTheme()) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        return
    }

    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (current.appearance) {
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
        AppearanceMode.SYSTEM -> systemDark
    }
    // Quick toggle resolves to an explicit light/dark mode (system stays selectable in Settings).
    val toggledMode = if (darkTheme) AppearanceMode.LIGHT else AppearanceMode.DARK

    SnishaperTheme(
        themeId = current.themeId,
        darkTheme = darkTheme,
        dynamicColor = current.useDynamicColor,
    ) {
        if (current.language.isEmpty()) {
            WelcomeScreen(
                onStart = { language ->
                    scope.launch { repository.setLanguage(language) }
                },
            )
        } else {
            MainScaffold(
                preferences = current,
                darkTheme = darkTheme,
                onDrawerOpenChange = { open -> scope.launch { repository.setDrawerOpen(open) } },
                onToggleTheme = { scope.launch { repository.setAppearance(toggledMode) } },
                onLanguageChange = { language -> scope.launch { repository.setLanguage(language) } },
                onThemeChange = { themeId -> scope.launch { repository.setThemeId(themeId) } },
                onAppearanceChange = { mode -> scope.launch { repository.setAppearance(mode) } },
                onAutoStartProxyChange = { enabled -> scope.launch { repository.setAutoStartProxy(enabled) } },
                onAutoUpdateRulesChange = { enabled -> scope.launch { repository.setAutoUpdateRules(enabled) } },
            )
        }
    }
}
