package com.sereneflow.timer.theme

import androidx.compose.ui.graphics.Color
import com.sereneflow.timer.model.AppThemeMode

data class SerenePalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val accentGlow: Color,
    val divider: Color,
    val isDark: Boolean
)

val ZenSagePalette = SerenePalette(
    primary = Color(0xFF436B54),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC7E2D1),
    onPrimaryContainer = Color(0xFF0F2618),
    secondary = Color(0xFF6B8A7A),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F8F5),
    onBackground = Color(0xFF1E2521),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E2521),
    surfaceVariant = Color(0xFFE5ECE7),
    onSurfaceVariant = Color(0xFF3F4A43),
    cardBackground = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFD3E0D7),
    accentGlow = Color(0x33436B54),
    divider = Color(0xFFE2EBE5),
    isDark = false
)

val ZenSageDarkPalette = SerenePalette(
    primary = Color(0xFF7FA890),
    onPrimary = Color(0xFF0E2217),
    primaryContainer = Color(0xFF284434),
    onPrimaryContainer = Color(0xFFBCE3CB),
    secondary = Color(0xFFA1BCAC),
    onSecondary = Color(0xFF172820),
    background = Color(0xFF141916),
    onBackground = Color(0xFFE4EDE7),
    surface = Color(0xFF1C231F),
    onSurface = Color(0xFFE4EDE7),
    surfaceVariant = Color(0xFF25302A),
    onSurfaceVariant = Color(0xFFB9C9C0),
    cardBackground = Color(0xFF1F2823),
    cardBorder = Color(0xFF2E3D35),
    accentGlow = Color(0x557FA890),
    divider = Color(0xFF2B3830),
    isDark = true
)

val OceanMistPalette = SerenePalette(
    primary = Color(0xFF2B6B8A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC2E4F4),
    onPrimaryContainer = Color(0xFF07212F),
    secondary = Color(0xFF568199),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF4F7F9),
    onBackground = Color(0xFF141E24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141E24),
    surfaceVariant = Color(0xFFE0EBF0),
    onSurfaceVariant = Color(0xFF394B55),
    cardBackground = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFCDE0E9),
    accentGlow = Color(0x332B6B8A),
    divider = Color(0xFFDCE8EE),
    isDark = false
)

val OceanMistDarkPalette = SerenePalette(
    primary = Color(0xFF64B5D6),
    onPrimary = Color(0xFF052331),
    primaryContainer = Color(0xFF1B4257),
    onPrimaryContainer = Color(0xFFBBE5F7),
    secondary = Color(0xFF8FBED6),
    onSecondary = Color(0xFF0D2533),
    background = Color(0xFF0E171E),
    onBackground = Color(0xFFDFEAF0),
    surface = Color(0xFF142029),
    onSurface = Color(0xFFDFEAF0),
    surfaceVariant = Color(0xFF1C2C39),
    onSurfaceVariant = Color(0xFFAEC4D1),
    cardBackground = Color(0xFF182632),
    cardBorder = Color(0xFF263C4E),
    accentGlow = Color(0x5564B5D6),
    divider = Color(0xFF213444),
    isDark = true
)

val SunsetClayPalette = SerenePalette(
    primary = Color(0xFF9E4B3D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFCD7D0),
    onPrimaryContainer = Color(0xFF350E09),
    secondary = Color(0xFFAD7266),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF7F5),
    onBackground = Color(0xFF271B19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF271B19),
    surfaceVariant = Color(0xFFF3E4E0),
    onSurfaceVariant = Color(0xFF533F3B),
    cardBackground = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE8D3CE),
    accentGlow = Color(0x339E4B3D),
    divider = Color(0xFFF0DDD8),
    isDark = false
)

val SunsetClayDarkPalette = SerenePalette(
    primary = Color(0xFFE28473),
    onPrimary = Color(0xFF3B150F),
    primaryContainer = Color(0xFF5E2B22),
    onPrimaryContainer = Color(0xFFFAC3BA),
    secondary = Color(0xFFD49E93),
    onSecondary = Color(0xFF331B16),
    background = Color(0xFF1F1513),
    onBackground = Color(0xFFF5E4E1),
    surface = Color(0xFF271B19),
    onSurface = Color(0xFFF5E4E1),
    surfaceVariant = Color(0xFF372623),
    onSurfaceVariant = Color(0xFFD6BEBA),
    cardBackground = Color(0xFF2C1E1B),
    cardBorder = Color(0xFF452F2B),
    accentGlow = Color(0x55E28473),
    divider = Color(0xFF3E2B27),
    isDark = true
)

val MidnightStarlightPalette = SerenePalette(
    primary = Color(0xFF7087F5),
    onPrimary = Color(0xFF0A133B),
    primaryContainer = Color(0xFF242E61),
    onPrimaryContainer = Color(0xFFCFD7FF),
    secondary = Color(0xFF9CAAF5),
    onSecondary = Color(0xFF161E47),
    background = Color(0xFF0D0F17),
    onBackground = Color(0xFFE3E5F3),
    surface = Color(0xFF151724),
    onSurface = Color(0xFFE3E5F3),
    surfaceVariant = Color(0xFF1E2133),
    onSurfaceVariant = Color(0xFFAEB2CF),
    cardBackground = Color(0xFF191B2B),
    cardBorder = Color(0xFF2B2F4A),
    accentGlow = Color(0x557087F5),
    divider = Color(0xFF23273D),
    isDark = true
)

fun getSerenePalette(mode: AppThemeMode, isDark: Boolean): SerenePalette {
    return when (mode) {
        AppThemeMode.ZEN_SAGE -> if (isDark) ZenSageDarkPalette else ZenSagePalette
        AppThemeMode.OCEAN_MIST -> if (isDark) OceanMistDarkPalette else OceanMistPalette
        AppThemeMode.SUNSET_CLAY -> if (isDark) SunsetClayDarkPalette else SunsetClayPalette
        AppThemeMode.MIDNIGHT_STARLIGHT -> MidnightStarlightPalette
    }
}
