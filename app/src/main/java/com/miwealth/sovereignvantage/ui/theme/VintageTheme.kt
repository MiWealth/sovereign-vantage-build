package com.miwealth.sovereignvantage.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * SOVEREIGN VANTAGE - VINTAGE THEME
 * 
 * Two modes available:
 * 1. VINTAGE MODE: Luxury aesthetic (leather, gold, burr walnut, ornate elements)
 * 2. BASIC MODE: Clean dark theme (minimal chrome, data-focused, faster rendering)
 * 
 * Typography:
 * - Vintage: Serif headings (Playfair Display), sans-serif data (Inter)
 * - Basic: Sans-serif throughout (Inter)
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

// =========================================================================
// THEME MODE
// =========================================================================

enum class ThemeMode {
    VINTAGE,    // Luxury aesthetic with full visual treatment
    BASIC       // Clean, minimal, data-focused
}

// =========================================================================
// THEME STATE (CompositionLocal)
// =========================================================================

data class VintageThemeState(
    val mode: ThemeMode = ThemeMode.VINTAGE,
    val isZenModeActive: Boolean = false,
    val showGoldFrames: Boolean = true,
    val useAnimations: Boolean = true
)

val LocalVintageTheme = staticCompositionLocalOf { VintageThemeState() }

// =========================================================================
// CUSTOM COLOR SCHEME EXTENSION
// =========================================================================

@Immutable
data class VintageColorScheme(
    // Core Material colors
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
    val error: Color,
    val onError: Color,
    val outline: Color,
    
    // Extended Vintage colors
    val gold: Color,
    val goldBright: Color,
    val goldMuted: Color,
    val emeraldDeep: Color,
    val emeraldDark: Color,
    val parchment: Color,
    val walnut: Color,
    val profitGreen: Color,
    val lossRed: Color,
    val warningAmber: Color,
    val navBarBackground: Color,
    val navBarText: Color,
    val chartGrid: Color,
    val divider: Color
)

val LocalVintageColors = staticCompositionLocalOf {
    VintageColorScheme(
        primary = VintageColors.Gold,
        onPrimary = VintageColors.EmeraldDeep,
        primaryContainer = VintageColors.GoldDark,
        onPrimaryContainer = VintageColors.TextPrimary,
        secondary = VintageColors.EmeraldAccent,
        onSecondary = VintageColors.TextPrimary,
        background = VintageColors.EmeraldDeep,
        onBackground = VintageColors.TextPrimary,
        surface = VintageColors.EmeraldDark,
        onSurface = VintageColors.TextPrimary,
        surfaceVariant = VintageColors.EmeraldMedium,
        onSurfaceVariant = VintageColors.TextSecondary,
        error = VintageColors.LossRed,
        onError = VintageColors.TextPrimary,
        outline = VintageColors.BorderGold,
        gold = VintageColors.Gold,
        goldBright = VintageColors.GoldBright,
        goldMuted = VintageColors.GoldMuted,
        emeraldDeep = VintageColors.EmeraldDeep,
        emeraldDark = VintageColors.EmeraldDark,
        parchment = VintageColors.Parchment,
        walnut = VintageColors.WalnutMedium,
        profitGreen = VintageColors.ProfitGreen,
        lossRed = VintageColors.LossRed,
        warningAmber = VintageColors.WarningAmber,
        navBarBackground = VintageColors.NavBarBackground,
        navBarText = VintageColors.NavBarText,
        chartGrid = VintageColors.ChartGrid,
        divider = VintageColors.Divider
    )
}

// =========================================================================
// COLOR SCHEMES
// =========================================================================

private val VintageDarkColorScheme = darkColorScheme(
    primary = VintageColors.Gold,
    onPrimary = VintageColors.EmeraldDeep,
    primaryContainer = VintageColors.GoldDark,
    onPrimaryContainer = VintageColors.TextPrimary,
    secondary = VintageColors.EmeraldAccent,
    onSecondary = VintageColors.TextPrimary,
    secondaryContainer = VintageColors.EmeraldMedium,
    onSecondaryContainer = VintageColors.TextPrimary,
    tertiary = VintageColors.GoldLight,
    onTertiary = VintageColors.EmeraldDeep,
    background = VintageColors.EmeraldDeep,
    onBackground = VintageColors.TextPrimary,
    surface = VintageColors.EmeraldDark,
    onSurface = VintageColors.TextPrimary,
    surfaceVariant = VintageColors.EmeraldMedium,
    onSurfaceVariant = VintageColors.TextSecondary,
    error = VintageColors.LossRed,
    onError = VintageColors.TextPrimary,
    outline = VintageColors.BorderGold,
    outlineVariant = VintageColors.BorderSubtle
)

private val VintageExtendedColors = VintageColorScheme(
    primary = VintageColors.Gold,
    onPrimary = VintageColors.EmeraldDeep,
    primaryContainer = VintageColors.GoldDark,
    onPrimaryContainer = VintageColors.TextPrimary,
    secondary = VintageColors.EmeraldAccent,
    onSecondary = VintageColors.TextPrimary,
    background = VintageColors.EmeraldDeep,
    onBackground = VintageColors.TextPrimary,
    surface = VintageColors.EmeraldDark,
    onSurface = VintageColors.TextPrimary,
    surfaceVariant = VintageColors.EmeraldMedium,
    onSurfaceVariant = VintageColors.TextSecondary,
    error = VintageColors.LossRed,
    onError = VintageColors.TextPrimary,
    outline = VintageColors.BorderGold,
    gold = VintageColors.Gold,
    goldBright = VintageColors.GoldBright,
    goldMuted = VintageColors.GoldMuted,
    emeraldDeep = VintageColors.EmeraldDeep,
    emeraldDark = VintageColors.EmeraldDark,
    parchment = VintageColors.Parchment,
    walnut = VintageColors.WalnutMedium,
    profitGreen = VintageColors.ProfitGreen,
    lossRed = VintageColors.LossRed,
    warningAmber = VintageColors.WarningAmber,
    navBarBackground = VintageColors.NavBarBackground,
    navBarText = VintageColors.NavBarText,
    chartGrid = VintageColors.ChartGrid,
    divider = VintageColors.Divider
)

private val BasicDarkColorScheme = darkColorScheme(
    primary = BasicColors.Primary,
    onPrimary = BasicColors.Background,
    primaryContainer = BasicColors.Primary.copy(alpha = 0.3f),
    onPrimaryContainer = BasicColors.TextPrimary,
    secondary = BasicColors.Secondary,
    onSecondary = BasicColors.Background,
    secondaryContainer = BasicColors.Secondary.copy(alpha = 0.3f),
    onSecondaryContainer = BasicColors.TextPrimary,
    tertiary = BasicColors.Accent,
    onTertiary = BasicColors.Background,
    background = BasicColors.Background,
    onBackground = BasicColors.TextPrimary,
    surface = BasicColors.Surface,
    onSurface = BasicColors.TextPrimary,
    surfaceVariant = BasicColors.SurfaceElevated,
    onSurfaceVariant = BasicColors.TextSecondary,
    error = BasicColors.LossRed,
    onError = BasicColors.TextPrimary,
    outline = BasicColors.Border,
    outlineVariant = BasicColors.Divider
)

private val BasicExtendedColors = VintageColorScheme(
    primary = BasicColors.Primary,
    onPrimary = BasicColors.Background,
    primaryContainer = BasicColors.Primary.copy(alpha = 0.3f),
    onPrimaryContainer = BasicColors.TextPrimary,
    secondary = BasicColors.Secondary,
    onSecondary = BasicColors.Background,
    background = BasicColors.Background,
    onBackground = BasicColors.TextPrimary,
    surface = BasicColors.Surface,
    onSurface = BasicColors.TextPrimary,
    surfaceVariant = BasicColors.SurfaceElevated,
    onSurfaceVariant = BasicColors.TextSecondary,
    error = BasicColors.LossRed,
    onError = BasicColors.TextPrimary,
    outline = BasicColors.Border,
    gold = BasicColors.Accent,
    goldBright = BasicColors.Accent,
    goldMuted = BasicColors.Accent.copy(alpha = 0.5f),
    emeraldDeep = BasicColors.Background,
    emeraldDark = BasicColors.Surface,
    parchment = BasicColors.SurfaceElevated,
    walnut = BasicColors.Surface,
    profitGreen = BasicColors.ProfitGreen,
    lossRed = BasicColors.LossRed,
    warningAmber = Color(0xFFFFAB00),
    navBarBackground = BasicColors.Surface,
    navBarText = BasicColors.TextPrimary,
    chartGrid = BasicColors.Border,
    divider = BasicColors.Divider
)

// =========================================================================
// TYPOGRAPHY
// =========================================================================

// Note: In production, load actual fonts from res/font
// For now, using system defaults with appropriate weights

val VintageTypography = Typography(
    // Display - Large headings (serif for Vintage)
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),
    
    // Headline - Section titles
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    
    // Title - Card titles, navigation
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    
    // Body - Main content (sans-serif)
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    
    // Label - Buttons, chips, data
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// =========================================================================
// MAIN THEME COMPOSABLE
// =========================================================================

@Composable
fun VintageTheme(
    themeMode: ThemeMode = ThemeMode.VINTAGE,
    isZenModeActive: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ThemeMode.VINTAGE -> VintageDarkColorScheme
        ThemeMode.BASIC -> BasicDarkColorScheme
    }
    
    val extendedColors = when (themeMode) {
        ThemeMode.VINTAGE -> VintageExtendedColors
        ThemeMode.BASIC -> BasicExtendedColors
    }
    
    val themeState = VintageThemeState(
        mode = themeMode,
        isZenModeActive = isZenModeActive,
        showGoldFrames = themeMode == ThemeMode.VINTAGE,
        useAnimations = true
    )
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = extendedColors.navBarBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = 
                themeMode == ThemeMode.VINTAGE
        }
    }
    
    CompositionLocalProvider(
        LocalVintageTheme provides themeState,
        LocalVintageColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VintageTypography,
            content = content
        )
    }
}

// =========================================================================
// THEME ACCESSORS
// =========================================================================

object VintageTheme {
    val colors: VintageColorScheme
        @Composable
        get() = LocalVintageColors.current
    
    val state: VintageThemeState
        @Composable
        get() = LocalVintageTheme.current
    
    val isVintageMode: Boolean
        @Composable
        get() = LocalVintageTheme.current.mode == ThemeMode.VINTAGE
}
