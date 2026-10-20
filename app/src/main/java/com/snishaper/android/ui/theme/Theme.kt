package com.snishaper.android.ui.theme

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.snishaper.android.R

/**
 * One selectable theme of the desktop catalog
 * (`desktop-ui-inventory.md §5.1`, ids match `theme/index.ts:148-155`).
 */
data class AppTheme(
    val id: String,
    @StringRes val labelRes: Int,
    val light: androidx.compose.material3.ColorScheme,
    val dark: androidx.compose.material3.ColorScheme,
)

object SnishaperThemeCatalog {

    val themes: List<AppTheme> = listOf(
        AppTheme(ThemeSeeds.Default.id, R.string.theme_default, ThemeSeeds.Default.lightScheme(), ThemeSeeds.Default.darkScheme()),
        AppTheme(ThemeSeeds.SniShaper.id, R.string.theme_snishaper, ThemeSeeds.SniShaper.lightScheme(), ThemeSeeds.SniShaper.darkScheme()),
        AppTheme(ThemeSeeds.Forest.id, R.string.theme_forest, ThemeSeeds.Forest.lightScheme(), ThemeSeeds.Forest.darkScheme()),
        AppTheme(ThemeSeeds.Sunset.id, R.string.theme_sunset, ThemeSeeds.Sunset.lightScheme(), ThemeSeeds.Sunset.darkScheme()),
        AppTheme(ThemeSeeds.Ocean.id, R.string.theme_ocean, ThemeSeeds.Ocean.lightScheme(), ThemeSeeds.Ocean.darkScheme()),
        AppTheme(ThemeSeeds.Graphite.id, R.string.theme_graphite, ThemeSeeds.Graphite.lightScheme(), ThemeSeeds.Graphite.darkScheme()),
    )

    fun byId(id: String): AppTheme = themes.firstOrNull { it.id == id } ?: themes.first()
}

/** Semantic accents (success/warning/info/cyan) that are not part of the M3 scheme. */
val LocalSnishaperExtraColors = staticCompositionLocalOf { LightExtraColors }

@Composable
fun SnishaperTheme(
    themeId: String,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val appTheme = remember(themeId) { SnishaperThemeCatalog.byId(themeId) }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> appTheme.dark
        else -> appTheme.light
    }
    val extraColors = if (darkTheme) DarkExtraColors else LightExtraColors
    CompositionLocalProvider(LocalSnishaperExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SnishaperTypography,
            shapes = SnishaperShapes,
            content = content,
        )
    }
}
