package com.miwealth.sovereignvantage.core.trading.utils

import com.miwealth.sovereignvantage.core.TradeSide

/**
 * LIQUIDATION PRICE VALIDATOR
 * 
 * Sovereign Vantage: Arthur Edition V5.18.21
 * Build #169 - P1 Safety Feature
 * 
 * CRITICAL SAFETY CHECK for leveraged positions:
 * Validates that user's stop loss is ABOVE liquidation price (LONG)
 * or BELOW liquidation price (SHORT).
 * 
 * WITHOUT THIS CHECK:
 * User can set SL that never triggers because position gets liquidated first!
 * 
 * EXAMPLE FAILURE CASE:
 * - 10x LONG BTC at $40,000
 * - Liquidation price: ~$36,000 (10% below entry)
 * - User sets SL: $35,000 (12.5% below entry)
 * - Price drops to $36,000 → LIQUIDATED
 * - SL at $35,000 never triggers
 * - User loses entire margin!
 * 
 * WITH THIS CHECK:
 * - System rejects SL at $35,000
 * - Enforces SL > $36,000 (above liquidation)
 * - User protected from margin loss
 * 
 * © 2025-2026 MiWealth Pty Ltd
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

object LiquidationValidator {
    
    /**
     * Validate that stop loss won't be bypassed by liquidation.
     * 
     * @param entryPrice Position entry price
     * @param stopLossPrice User's desired stop loss
     * @param leverage Position leverage (1.0 = no leverage)
     * @param side Trade side (BUY/LONG or SELL/SHORT)
     * @return Pair<Boolean, String> - (isValid, errorMessage)
     */
    fun validateStopLoss(
        entryPrice: Double,
        stopLossPrice: Double,
        leverage: Double,
        side: TradeSide
    ): Pair<Boolean, String> {
        
        if (leverage <= 1.0) {
            // No leverage = no liquidation risk
            return Pair(true, "")
        }
        
        // Calculate liquidation price
        val liquidationPrice = calculateLiquidationPrice(entryPrice, leverage, side)
        
        // Validate SL is on the safe side of liquidation
        return when (side) {
            TradeSide.BUY -> {
                // LONG: Stop loss must be ABOVE liquidation price
                if (stopLossPrice > liquidationPrice) {
                    Pair(true, "")
                } else {
                    val safeSL = liquidationPrice * 1.01 // 1% above liq for safety
                    Pair(
                        false,
                        "Stop loss ($${String.format("%.2f", stopLossPrice)}) is below liquidation price ($${String.format("%.2f", liquidationPrice)}). " +
                        "Minimum safe SL: $${String.format("%.2f", safeSL)}. " +
                        "Your position would be liquidated before SL triggers!"
                    )
                }
            }
            
            TradeSide.SELL -> {
                // SHORT: Stop loss must be BELOW liquidation price
                if (stopLossPrice < liquidationPrice) {
                    Pair(true, "")
                } else {
                    val safeSL = liquidationPrice * 0.99 // 1% below liq for safety
                    Pair(
                        false,
                        "Stop loss ($${String.format("%.2f", stopLossPrice)}) is above liquidation price ($${String.format("%.2f", liquidationPrice)}). " +
                        "Maximum safe SL: $${String.format("%.2f", safeSL)}. " +
                        "Your position would be liquidated before SL triggers!"
                    )
                }
            }
        }
    }
    
    /**
     * Calculate liquidation price for a leveraged position.
     * 
     * Formula: Entry ± (Entry × Margin × SafetyBuffer)
     * Where:
     * - Margin = 1 / leverage
     * - SafetyBuffer = 0.9 (reserves 10% for fees/slippage)
     * 
     * EXAMPLES:
     * - 10x LONG at $40,000:
     *   Margin = 10%, Buffer = 9%
     *   Liq = $40,000 × (1 - 0.09) = $36,400
     * 
     * - 5x SHORT at $40,000:
     *   Margin = 20%, Buffer = 18%
     *   Liq = $40,000 × (1 + 0.18) = $47,200
     * 
     * @param entryPrice Position entry price
     * @param leverage Position leverage
     * @param side Trade side
     * @return Liquidation price
     */
    fun calculateLiquidationPrice(
        entryPrice: Double,
        leverage: Double,
        side: TradeSide
    ): Double {
        
        if (leverage <= 1.0) {
            // No leverage = no liquidation
            return when (side) {
                TradeSide.BUY -> 0.0      // Can't be liquidated
                TradeSide.SELL -> Double.MAX_VALUE  // Can't be liquidated
            }
        }
        
        // Margin = portion of entry price we put up as collateral
        val margin = 1.0 / leverage
        
        // Safety buffer: reserve 10% of margin for fees/slippage
        // This makes liquidation more conservative (safer)
        val safetyBuffer = 0.90
        val liquidationPercent = margin * safetyBuffer
        
        return when (side) {
            TradeSide.BUY -> {
                // LONG: Liquidated if price drops below entry - margin
                entryPrice * (1.0 - liquidationPercent)
            }
            
            TradeSide.SELL -> {
                // SHORT: Liquidated if price rises above entry + margin
                entryPrice * (1.0 + liquidationPercent)
            }
        }
    }
    
    /**
     * Calculate maximum safe leverage for a given stop loss distance.
     * Helps user understand leverage limits for their risk tolerance.
     * 
     * @param entryPrice Position entry price
     * @param stopLossPrice Desired stop loss
     * @param side Trade side
     * @return Maximum safe leverage (will always be >= 1.0)
     */
    fun calculateMaxSafeLeverage(
        entryPrice: Double,
        stopLossPrice: Double,
        side: TradeSide
    ): Double {
        
        val stopLossPercent = when (side) {
            TradeSide.BUY -> {
                // LONG: How far is SL below entry?
                ((entryPrice - stopLossPrice) / entryPrice) * 100.0
            }
            TradeSide.SELL -> {
                // SHORT: How far is SL above entry?
                ((stopLossPrice - entryPrice) / entryPrice) * 100.0
            }
        }
        
        if (stopLossPercent <= 0.0) {
            // SL in wrong direction or at entry - no leverage safe
            return 1.0
        }
        
        // Max leverage = 1 / (stopLossPercent / 100) × safetyBuffer
        val safetyBuffer = 0.90
        val maxLeverage = (1.0 / (stopLossPercent / 100.0)) * safetyBuffer
        
        // Clamp to reasonable range
        return maxLeverage.coerceIn(1.0, 100.0)
    }
    
    /**
     * Generate user-friendly message about liquidation risk.
     * Helps users understand the danger before they place order.
     * 
     * @param entryPrice Position entry price
     * @param leverage Position leverage
     * @param side Trade side
     * @return Formatted warning message
     */
    fun getLiquidationWarning(
        entryPrice: Double,
        leverage: Double,
        side: TradeSide
    ): String {
        
        if (leverage <= 1.0) {
            return "No liquidation risk (spot trading, no leverage)."
        }
        
        val liqPrice = calculateLiquidationPrice(entryPrice, leverage, side)
        val liqDistance = when (side) {
            TradeSide.BUY -> ((entryPrice - liqPrice) / entryPrice) * 100.0
            TradeSide.SELL -> ((liqPrice - entryPrice) / entryPrice) * 100.0
        }
        
        val direction = when (side) {
            TradeSide.BUY -> "drops below"
            TradeSide.SELL -> "rises above"
        }
        
        return buildString {
            append("⚠️ LEVERAGE WARNING ⚠️\n\n")
            append("${leverage.toInt()}x leverage active.\n")
            append("Liquidation price: $${String.format("%.2f", liqPrice)}\n")
            append("If price $direction $${String.format("%.2f", liqPrice)} (${String.format("%.1f", liqDistance)}%), ")
            append("your position will be liquidated and you'll lose your margin.\n\n")
            append("Ensure your stop loss is set to protect against liquidation!")
        }
    }
}
