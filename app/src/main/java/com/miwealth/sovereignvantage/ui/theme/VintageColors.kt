package com.miwealth.sovereignvantage.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SOVEREIGN VANTAGE - VINTAGE THEME COLORS
 * 
 * "Steam Punk meets top end of Wall Street"
 * 
 * Design Aesthetic:
 * - Swiss luxury watchmaking (skeleton clocks, gold mechanisms)
 * - Institutional finance (green leather, burr walnut)
 * - 24K gold accents throughout
 * - Blackened emerald leather backgrounds
 * 
 * Target Market: High-net-worth, self-sovereign traders
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

object VintageColors {
    
    // =========================================================================
    // PRIMARY GOLD PALETTE (24K Brushed Gold)
    // =========================================================================
    
    /** Primary gold - main accent color */
    val Gold = Color(0xFFD4AF37)
    
    /** Bright gold - highlights, active states */
    val GoldBright = Color(0xFFFFD700)
    
    /** Light gold - subtle accents */
    val GoldLight = Color(0xFFE6C55C)
    
    /** Dark gold - shadows, depth */
    val GoldDark = Color(0xFFB8860B)
    
    /** Muted gold - disabled/inactive states */
    val GoldMuted = Color(0xFF8B7355)
    
    /** Gold with transparency - overlays */
    val GoldAlpha50 = Color(0x80D4AF37)
    val GoldAlpha30 = Color(0x4DD4AF37)
    val GoldAlpha10 = Color(0x1AD4AF37)
    
    // =========================================================================
    // EMERALD GREEN PALETTE (Blackened Leather)
    // =========================================================================
    
    /** Deep emerald - primary background */
    val EmeraldDeep = Color(0xFF021508)
    
    /** Dark emerald - card backgrounds */
    val EmeraldDark = Color(0xFF0A2818)
    
    /** Medium emerald - elevated surfaces */
    val EmeraldMedium = Color(0xFF153D28)
    
    /** Light emerald - hover states */
    val EmeraldLight = Color(0xFF1E5038)
    
    /** Emerald accent - highlights */
    val EmeraldAccent = Color(0xFF2D6B4A)
    
    /** Emerald with transparency */
    val EmeraldAlpha80 = Color(0xCC021508)
    val EmeraldAlpha50 = Color(0x80021508)
    
    // =========================================================================
    // BURR WALNUT WOOD TONES
    // =========================================================================
    
    /** Dark walnut - deep wood tone */
    val WalnutDark = Color(0xFF3D2817)
    
    /** Medium walnut - standard wood */
    val WalnutMedium = Color(0xFF5C3D24)
    
    /** Light walnut - highlights */
    val WalnutLight = Color(0xFF7A5233)
    
    /** Walnut accent - figure grain highlights */
    val WalnutFigure = Color(0xFF8B6343)
    
    // =========================================================================
    // PARCHMENT / CREAM TONES
    // =========================================================================
    
    /** Aged parchment - notification backgrounds */
    val Parchment = Color(0xFFF4E4C1)
    
    /** Light parchment - text backgrounds */
    val ParchmentLight = Color(0xFFFAF3E3)
    
    /** Dark parchment - aged effect */
    val ParchmentDark = Color(0xFFE8D4A8)
    
    /** Cream - soft highlights */
    val Cream = Color(0xFFFFFDD0)
    
    /** Ivory - pure highlights */
    val Ivory = Color(0xFFFFFFF0)
    
    // =========================================================================
    // FUNCTIONAL COLORS
    // =========================================================================
    
    /** Profit green - gains, positive */
    val ProfitGreen = Color(0xFF00C853)
    
    /** Profit green bright - strong positive */
    val ProfitGreenBright = Color(0xFF00E676)
    
    /** Loss red - losses, negative */
    val LossRed = Color(0xFFFF1744)
    
    /** Loss red muted - warnings */
    val LossRedMuted = Color(0xFFCF4444)
    
    /** Warning amber - caution states */
    val WarningAmber = Color(0xFFFFAB00)
    
    /** Info blue - informational */
    val InfoBlue = Color(0xFF29B6F6)
    
    // =========================================================================
    // TEXT COLORS
    // =========================================================================
    
    /** Primary text - white on dark */
    val TextPrimary = Color(0xFFFFFFFF)
    
    /** Secondary text - slightly muted */
    val TextSecondary = Color(0xFFE0E0E0)
    
    /** Tertiary text - subtle */
    val TextTertiary = Color(0xFFB0B0B0)
    
    /** Muted text - hints, placeholders */
    val TextMuted = Color(0xFF808080)
    
    /** Gold text - accent text */
    val TextGold = Color(0xFFD4AF37)
    
    /** Dark text - on light backgrounds */
    val TextDark = Color(0xFF1A1A1A)
    
    /** Dark text secondary */
    val TextDarkSecondary = Color(0xFF4A4A4A)
    
    // =========================================================================
    // SURFACE COLORS
    // =========================================================================
    
    /** Primary surface - main background */
    val SurfacePrimary = EmeraldDeep
    
    /** Elevated surface - cards, dialogs */
    val SurfaceElevated = EmeraldDark
    
    /** Overlay surface - modals, sheets */
    val SurfaceOverlay = Color(0xE6021508)
    
    /** Scrim - dimming backgrounds */
    val Scrim = Color(0x80000000)
    
    // =========================================================================
    // BORDER / OUTLINE COLORS
    // =========================================================================
    
    /** Gold border - primary borders */
    val BorderGold = GoldDark
    
    /** Subtle border - card edges */
    val BorderSubtle = Color(0xFF2A4A3A)
    
    /** Divider - separators */
    val Divider = Color(0xFF1A3A2A)
    
    // =========================================================================
    // GRADIENT DEFINITIONS
    // =========================================================================
    
    /** Gold gradient stops */
    val GradientGoldStart = Color(0xFFFFD700)
    val GradientGoldMid = Color(0xFFD4AF37)
    val GradientGoldEnd = Color(0xFFB8860B)
    
    /** Emerald gradient stops */
    val GradientEmeraldStart = Color(0xFF1E5038)
    val GradientEmeraldMid = Color(0xFF0A2818)
    val GradientEmeraldEnd = Color(0xFF021508)
    
    // =========================================================================
    // CHART COLORS
    // =========================================================================
    
    /** Candlestick up - bullish */
    val CandleUp = ProfitGreen
    
    /** Candlestick down - bearish */
    val CandleDown = LossRed
    
    /** Candlestick wick */
    val CandleWick = Color(0xFF808080)
    
    /** Chart grid lines */
    val ChartGrid = Color(0xFF1A3A2A)
    
    /** Chart axis text */
    val ChartAxis = TextTertiary
    
    /** Volume bars up */
    val VolumeUp = Color(0x8000C853)
    
    /** Volume bars down */
    val VolumeDown = Color(0x80FF1744)
    
    /** EMA line */
    val IndicatorEMA = Color(0xFFFFD700)
    
    /** Bollinger bands */
    val IndicatorBollinger = Color(0xFF29B6F6)
    
    /** RSI line */
    val IndicatorRSI = Color(0xFFAB47BC)
    
    /** MACD line */
    val IndicatorMACD = Color(0xFF26A69A)
    
    /** MACD signal */
    val IndicatorMACDSignal = Color(0xFFEF5350)
    
    // =========================================================================
    // COMPONENT SPECIFIC
    // =========================================================================
    
    /** Navigation bar background - brushed gold */
    val NavBarBackground = Color(0xFFD4AF37)
    
    /** Navigation bar text - dark on gold */
    val NavBarText = Color(0xFF1A1A1A)
    
    /** Navigation bar icon selected */
    val NavBarIconSelected = Color(0xFF021508)
    
    /** Navigation bar icon unselected */
    val NavBarIconUnselected = Color(0xFF5C4A32)
    
    /** Button active state */
    val ButtonActive = Gold
    
    /** Button inactive/disabled */
    val ButtonInactive = GoldMuted
    
    /** Button pressed */
    val ButtonPressed = GoldDark
    
    /** Toggle on */
    val ToggleOn = ProfitGreen
    
    /** Toggle off */
    val ToggleOff = Color(0xFF4A4A4A)
}

// =========================================================================
// BASIC MODE COLORS (Clean, minimal alternative)
// =========================================================================

object BasicColors {
    
    val Background = Color(0xFF121212)
    val Surface = Color(0xFF1E1E1E)
    val SurfaceElevated = Color(0xFF2A2A2A)
    
    val Primary = Color(0xFF4CAF50)
    val Secondary = Color(0xFF2196F3)
    val Accent = Color(0xFFFFD700)
    
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB0B0B0)
    
    val ProfitGreen = Color(0xFF00C853)
    val LossRed = Color(0xFFFF1744)
    
    val Border = Color(0xFF3A3A3A)
    val Divider = Color(0xFF2A2A2A)
}
