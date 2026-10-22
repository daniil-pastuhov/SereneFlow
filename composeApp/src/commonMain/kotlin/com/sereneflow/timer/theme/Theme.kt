package com.sereneflow.timer.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.sereneflow.timer.model.AppThemeMode
import com.sereneflow.timer.ui.platform.SystemBarAppearance

val LocalSereneColors = staticCompositionLocalOf { ZenSagePalette }

@Composable
fun SereneFlowTheme(
    themeMode: AppThemeMode = AppThemeMode.ZEN_SAGE,
    isDark: Boolean = isSystemInDarkTheme() || themeMode == AppThemeMode.MIDNIGHT_STARLIGHT,
    content: @Composable () -> Unit
) {
    val palette = getSerenePalette(themeMode, isDark)

    val materialColors = if (palette.isDark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.onPrimaryContainer,
            secondary = palette.secondary,
            onSecondary = palette.onSecondary,
            background = palette.background,
            onBackground = palette.onBackground,
            surface = palette.surface,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.onSurfaceVariant
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.onPrimaryContainer,
            secondary = palette.secondary,
            onSecondary = palette.onSecondary,
            background = palette.background,
            onBackground = palette.onBackground,
            surface = palette.surface,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surfaceVariant,
            onSurfaceVariant = palette.onSurfaceVariant
        )
    }

    SystemBarAppearance(darkBackground = palette.isDark)

    CompositionLocalProvider(LocalSereneColors provides palette) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content
        )
    }
}
