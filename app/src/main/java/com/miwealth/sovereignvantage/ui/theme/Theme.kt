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

// =========================================================================
// SOVEREIGN VANTAGE — BLACKENED EMERALD & GOLD
// "Steam Punk meets top end of Wall Street"
// Fine leather & burr walnut aesthetic
// =========================================================================

// Gold palette — 24K brushed gold
val GoldPrimary = Color(0xFFD4AF37)
val GoldLight = Color(0xFFFFE066)
val GoldDark = Color(0xFFB8860B)

// Blackened Emerald — unmistakably green, not navy
// Green channel deliberately visible on AMOLED screens
val EmeraldBlack = Color(0xFF011208)   // Near-black emerald — primary bg
val EmeraldDeep = Color(0xFF031E0E)    // Very dark emerald — card/surface bg
val EmeraldDark = Color(0xFF053218)    // Dark emerald — elevated surfaces

// Legacy aliases — DO NOT USE in new code, use Emerald names above
@Deprecated("Use EmeraldBlack", ReplaceWith("EmeraldBlack"))
val NavyDark = EmeraldBlack
@Deprecated("Use EmeraldDeep", ReplaceWith("EmeraldDeep"))
val NavyMedium = EmeraldDeep
@Deprecated("Use EmeraldDark", ReplaceWith("EmeraldDark"))
val NavyLight = EmeraldDark

val SilverAccent = Color(0xFFC0C0C0)
val ProfitGreen = Color(0xFF00A843)
val LossRed = Color(0xFFFF1744)

// BUILD #103: Updated for better candle visibility
// Changed from dark forest green (0xFF2E7D46) to bright crisp green (0xFF00E676)
val StatusGreen = Color(0xFF00E676)  // Bright green for bullish candles

// Parchment text palette
val White = Color(0xFFF4E4C1)
val OffWhite = Color(0xFFE8D4A8)
val PureWhite = Color(0xFFFFFFFF)

// Gold frame colors
val GoldFrameOuter = Color(0xFFB8860B)
val GoldFrameMain = Color(0xFFD4AF37)
val GoldFrameInner = Color(0xFFFFE066)
val GoldGlow = Color(0x40D4AF37)

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = EmeraldBlack,
    primaryContainer = GoldDark,
    onPrimaryContainer = White,
    secondary = GoldLight,
    onSecondary = EmeraldBlack,
    secondaryContainer = EmeraldDark,
    onSecondaryContainer = White,
    tertiary = GoldLight,
    onTertiary = EmeraldBlack,
    background = EmeraldBlack,
    onBackground = White,
    surface = EmeraldDeep,
    onSurface = White,
    surfaceVariant = EmeraldDark,
    onSurfaceVariant = OffWhite,
    error = LossRed,
    onError = PureWhite,
    outline = GoldDark
)

private val LightColorScheme = lightColorScheme(
    primary = GoldDark,
    onPrimary = White,
    primaryContainer = GoldLight,
    onPrimaryContainer = EmeraldBlack,
    secondary = EmeraldDeep,
    onSecondary = White,
    secondaryContainer = EmeraldDark,
    onSecondaryContainer = White,
    tertiary = GoldPrimary,
    onTertiary = EmeraldBlack,
    background = OffWhite,
    onBackground = EmeraldBlack,
    surface = White,
    onSurface = EmeraldBlack,
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = EmeraldDeep,
    error = LossRed,
    onError = White,
    outline = EmeraldDark
)

@Composable
fun SovereignVantageTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = Color(0xFF000000).toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color(0xFF000000).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
