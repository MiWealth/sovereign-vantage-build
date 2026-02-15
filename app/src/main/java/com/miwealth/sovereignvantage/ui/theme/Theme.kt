package com.miwealth.sovereignvantage.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Sovereign Vantage Brand Colors
val GoldPrimary = Color(0xFFD4AF37)
val GoldLight = Color(0xFFFFE066)
val GoldDark = Color(0xFFB8860B)
val NavyDark = Color(0xFF0A1628)
val NavyMedium = Color(0xFF1A2744)
val NavyLight = Color(0xFF2A3B5C)
val SilverAccent = Color(0xFFC0C0C0)
val ProfitGreen = Color(0xFF00C853)
val LossRed = Color(0xFFFF1744)
val White = Color(0xFFFFFFFF)
val OffWhite = Color(0xFFF5F5F5)

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = NavyDark,
    primaryContainer = GoldDark,
    onPrimaryContainer = White,
    secondary = SilverAccent,
    onSecondary = NavyDark,
    secondaryContainer = NavyLight,
    onSecondaryContainer = White,
    tertiary = GoldLight,
    onTertiary = NavyDark,
    background = NavyDark,
    onBackground = White,
    surface = NavyMedium,
    onSurface = White,
    surfaceVariant = NavyLight,
    onSurfaceVariant = OffWhite,
    error = LossRed,
    onError = White,
    outline = SilverAccent
)

private val LightColorScheme = lightColorScheme(
    primary = GoldDark,
    onPrimary = White,
    primaryContainer = GoldLight,
    onPrimaryContainer = NavyDark,
    secondary = NavyMedium,
    onSecondary = White,
    secondaryContainer = NavyLight,
    onSecondaryContainer = White,
    tertiary = GoldPrimary,
    onTertiary = NavyDark,
    background = OffWhite,
    onBackground = NavyDark,
    surface = White,
    onSurface = NavyDark,
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = NavyMedium,
    error = LossRed,
    onError = White,
    outline = NavyLight
)

@Composable
fun SovereignVantageTheme(
    darkTheme: Boolean = true, // Default to dark theme for trading
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
