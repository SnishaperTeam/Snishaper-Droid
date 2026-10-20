package com.snishaper.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Color seeds transcribed from the desktop MUI themes
 * (`SniShaper-main/frontend/src/theme/index.ts:16-155`).
 *
 * Each theme keeps the desktop values for primary/secondary/background/surface/text and
 * derives the remaining Material 3 container roles from them.
 */
data class ThemeSeed(
    val id: String,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val lightBackground: Color,
    val lightSurface: Color,
    val lightOnSurface: Color,
    val lightOnSurfaceVariant: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkSecondary: Color,
    val darkBackground: Color,
    val darkSurface: Color,
    val darkOnSurface: Color,
    val darkOnSurfaceVariant: Color,
)

object ThemeSeeds {

    private val lightTextPrimary = Color(0xDE000000)
    private val lightTextSecondary = Color(0x99000000)

    val Default = ThemeSeed(
        id = "default",
        primary = Color(0xFF1976D2),
        onPrimary = Color.White,
        secondary = Color(0xFF546E7A),
        lightBackground = Color(0xFFFFFFFF),
        lightSurface = Color(0xFFF5F5F5),
        lightOnSurface = lightTextPrimary,
        lightOnSurfaceVariant = lightTextSecondary,
        darkPrimary = Color(0xFF90CAF9),
        darkOnPrimary = Color(0xFF001B3D),
        darkSecondary = Color(0xFF90A4AE),
        darkBackground = Color(0xFF121212),
        darkSurface = Color(0xFF1E1E1E),
        darkOnSurface = Color.White,
        darkOnSurfaceVariant = Color(0xB3FFFFFF),
    )

    val SniShaper = ThemeSeed(
        id = "sniShaper",
        primary = Color(0xFF0B7BFF),
        onPrimary = Color.White,
        secondary = Color(0xFF00ACC1),
        lightBackground = Color(0xFFFFFFFF),
        lightSurface = Color(0xFFF5F5F5),
        lightOnSurface = lightTextPrimary,
        lightOnSurfaceVariant = lightTextSecondary,
        darkPrimary = Color(0xFF0B7BFF),
        darkOnPrimary = Color.White,
        darkSecondary = Color(0xFF4DD0E1),
        darkBackground = Color(0xFF0D1117),
        darkSurface = Color(0xFF161B22),
        darkOnSurface = Color(0xFFE6EDF3),
        darkOnSurfaceVariant = Color(0xFF8B949E),
    )

    val Forest = ThemeSeed(
        id = "forest",
        primary = Color(0xFF2E7D32),
        onPrimary = Color.White,
        secondary = Color(0xFF00695C),
        lightBackground = Color(0xFFF7FAF7),
        lightSurface = Color(0xFFFFFFFF),
        lightOnSurface = lightTextPrimary,
        lightOnSurfaceVariant = lightTextSecondary,
        darkPrimary = Color(0xFF66BB6A),
        darkOnPrimary = Color(0xFF0B160C),
        darkSecondary = Color(0xFF26A69A),
        darkBackground = Color(0xFF0F1A12),
        darkSurface = Color(0xFF152219),
        darkOnSurface = Color(0xFFE4EFE6),
        darkOnSurfaceVariant = Color(0xFF93A99A),
    )

    val Sunset = ThemeSeed(
        id = "sunset",
        primary = Color(0xFFED6C02),
        onPrimary = Color.White,
        secondary = Color(0xFF6D4C41),
        lightBackground = Color(0xFFFFFAF4),
        lightSurface = Color(0xFFFFFFFF),
        lightOnSurface = lightTextPrimary,
        lightOnSurfaceVariant = lightTextSecondary,
        darkPrimary = Color(0xFFFF9800),
        darkOnPrimary = Color(0xFF1A0F00),
        darkSecondary = Color(0xFFA1887F),
        darkBackground = Color(0xFF1A1410),
        darkSurface = Color(0xFF211A14),
        darkOnSurface = Color(0xFFF2ECE5),
        darkOnSurfaceVariant = Color(0xFFB3A89C),
    )

    val Ocean = ThemeSeed(
        id = "ocean",
        primary = Color(0xFF00897B),
        onPrimary = Color.White,
        secondary = Color(0xFF0277BD),
        lightBackground = Color(0xFFF4FAF9),
        lightSurface = Color(0xFFFFFFFF),
        lightOnSurface = lightTextPrimary,
        lightOnSurfaceVariant = lightTextSecondary,
        darkPrimary = Color(0xFF4DB6AC),
        darkOnPrimary = Color(0xFF0A1A18),
        darkSecondary = Color(0xFF4FC3F7),
        darkBackground = Color(0xFF0D1A19),
        darkSurface = Color(0xFF132322),
        darkOnSurface = Color(0xFFE0EFED),
        darkOnSurfaceVariant = Color(0xFF8FB0AC),
    )

    val Graphite = ThemeSeed(
        id = "graphite",
        primary = Color(0xFF546E7A),
        onPrimary = Color.White,
        secondary = Color(0xFF8D6E63),
        lightBackground = Color(0xFFF7F8FA),
        lightSurface = Color(0xFFFFFFFF),
        lightOnSurface = lightTextPrimary,
        lightOnSurfaceVariant = lightTextSecondary,
        darkPrimary = Color(0xFF90A4AE),
        darkOnPrimary = Color(0xFF101418),
        darkSecondary = Color(0xFFA1887F),
        darkBackground = Color(0xFF121418),
        darkSurface = Color(0xFF1A1D22),
        darkOnSurface = Color(0xFFE8EAEE),
        darkOnSurfaceVariant = Color(0xFF9AA1AB),
    )

    val all = listOf(Default, SniShaper, Forest, Sunset, Ocean, Graphite)

    fun byId(id: String): ThemeSeed = all.firstOrNull { it.id == id } ?: Default
}

/** Semantic accent colors used by the desktop pages next to the M3 scheme. */
data class SnishaperExtraColors(
    val success: Color,
    val warning: Color,
    val info: Color,
    val cyan: Color,
)

val LightExtraColors = SnishaperExtraColors(
    success = Color(0xFF22C55E),
    warning = Color(0xFFF59E0B),
    info = Color(0xFF0288D1),
    cyan = Color(0xFF06B6D4),
)

val DarkExtraColors = SnishaperExtraColors(
    success = Color(0xFF4ADE80),
    warning = Color(0xFFFBBF24),
    info = Color(0xFF38BDF8),
    cyan = Color(0xFF22D3EE),
)

private val LightError = Color(0xFFB3261E)
private val LightOnError = Color.White
private val LightErrorContainer = Color(0xFFF9DEDC)
private val LightOnErrorContainer = Color(0xFF410E0B)

private val DarkError = Color(0xFFF2B8B5)
private val DarkOnError = Color(0xFF601410)
private val DarkErrorContainer = Color(0xFF8C1D18)
private val DarkOnErrorContainer = Color(0xFFF9DEDC)

/** Builds the light ColorScheme for one desktop theme seed. */
fun ThemeSeed.lightScheme(): ColorScheme {
    val primary = this.primary
    val secondary = this.secondary
    val background = lightBackground
    val surface = lightSurface
    val onSurface = lightOnSurface
    val onSurfaceVariant = lightOnSurfaceVariant
    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = lerp(surface, primary, 0.16f),
        onPrimaryContainer = lerp(primary, Color.Black, 0.55f),
        inversePrimary = lerp(primary, Color.White, 0.55f),
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = lerp(surface, secondary, 0.18f),
        onSecondaryContainer = lerp(secondary, Color.Black, 0.55f),
        tertiary = secondary,
        onTertiary = Color.White,
        tertiaryContainer = lerp(surface, secondary, 0.14f),
        onTertiaryContainer = lerp(secondary, Color.Black, 0.55f),
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = lerp(surface, onSurface, 0.08f),
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = Color(0xFF303030),
        inverseOnSurface = Color(0xFFF2F2F2),
        error = LightError,
        onError = LightOnError,
        errorContainer = LightErrorContainer,
        onErrorContainer = LightOnErrorContainer,
        outline = lerp(onSurface, surface, 0.55f),
        outlineVariant = lerp(onSurface, surface, 0.82f),
        scrim = Color.Black,
    )
}

/** Builds the dark ColorScheme for one desktop theme seed. */
fun ThemeSeed.darkScheme(): ColorScheme {
    val primary = darkPrimary
    val secondary = darkSecondary
    val background = darkBackground
    val surface = darkSurface
    val onSurface = darkOnSurface
    val onSurfaceVariant = darkOnSurfaceVariant
    return darkColorScheme(
        primary = primary,
        onPrimary = darkOnPrimary,
        primaryContainer = lerp(surface, primary, 0.30f),
        onPrimaryContainer = lerp(primary, Color.White, 0.65f),
        inversePrimary = lerp(primary, Color.Black, 0.55f),
        secondary = secondary,
        onSecondary = Color(0xFF0B1416),
        secondaryContainer = lerp(surface, secondary, 0.26f),
        onSecondaryContainer = lerp(secondary, Color.White, 0.70f),
        tertiary = secondary,
        onTertiary = Color(0xFF0B1416),
        tertiaryContainer = lerp(surface, secondary, 0.20f),
        onTertiaryContainer = lerp(secondary, Color.White, 0.70f),
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = lerp(surface, onSurface, 0.10f),
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = Color(0xFFE6E6E6),
        inverseOnSurface = Color(0xFF303030),
        error = DarkError,
        onError = DarkOnError,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkOnErrorContainer,
        outline = lerp(onSurface, surface, 0.55f),
        outlineVariant = lerp(onSurface, surface, 0.80f),
        scrim = Color.Black,
    )
}
