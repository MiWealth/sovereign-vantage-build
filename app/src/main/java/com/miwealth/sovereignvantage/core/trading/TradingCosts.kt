package com.miwealth.sovereignvantage.core.trading

/**
 * TRADING COSTS — FEES AND SPREAD
 *
 * Sovereign Vantage: Arthur Edition
 * © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 *
 * BUILD #266: Real-world trading cost model for paper trading simulation.
 *
 * FEES (Binance USDT-M Perpetual Futures — standard non-VIP):
 *   Maker (limit order, adds liquidity):  0.02% of notional position value
 *   Taker (market order, removes liquidity): 0.05% of notional position value
 *
 * SPREAD (Bid-Ask):
 *   The spread is the gap between the best bid and best ask price.
 *   You always ENTER at the ask (slightly above mid) and EXIT at the bid
 *   (slightly below mid) — so you pay half the spread on entry and half on exit.
 *   BTC/USDT on Binance: ~0.01-0.02% (extremely liquid)
 *   ETH/USDT:            ~0.02%
 *   SOL/USDT:            ~0.03% (slightly wider)
 *   XRP/USDT:            ~0.03%
 *
 * ROUND TRIP COST (open + close, market orders):
 *   = Taker fee (0.05%) × 2 + Spread (0.01-0.03%)
 *   ≈ 0.11% - 0.13% of position value total
 *
 * BREAK-EVEN MOVE:
 *   A trade must move ~0.11-0.13% in your favour just to cover costs.
 *   STAHL Stair Stop™ first level at 1.5% profit is well above break-even. ✅
 *
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */
object TradingCosts {

    // =========================================================================
    // FEE RATES (Binance USDT-M Futures, standard non-VIP)
    // =========================================================================

    /** Maker fee — limit orders that add liquidity to the order book */
    const val MAKER_FEE_RATE = 0.0002      // 0.02%

    /** Taker fee — market orders that remove liquidity immediately */
    const val TAKER_FEE_RATE = 0.0005      // 0.05%

    /**
     * Default order type for the board's autonomous trades.
     * Market orders = taker fee. We use taker since the board fires
     * immediately on signal — we don't wait for a limit fill.
     */
    const val DEFAULT_FEE_RATE = TAKER_FEE_RATE

    // =========================================================================
    // SPREAD RATES (half-spread per side, Binance USDT-M pairs)
    // =========================================================================

    /** Half-spread by symbol — paid on entry AND exit */
    private val HALF_SPREAD = mapOf(
        "BTC/USDT" to 0.00005,   // 0.005% each side — ~0.01% round trip
        "ETH/USDT" to 0.00010,   // 0.010% each side — ~0.02% round trip
        "SOL/USDT" to 0.00015,   // 0.015% each side — ~0.03% round trip
        "XRP/USDT" to 0.00015    // 0.015% each side — ~0.03% round trip
    )

    private const val DEFAULT_HALF_SPREAD = 0.00010  // 0.01% fallback

    // =========================================================================
    // MARGIN REQUIREMENTS (Binance isolated margin, standard tier)
    // =========================================================================

    /** Initial margin rate at 1x leverage = 100% of position value */
    const val BASE_INITIAL_MARGIN_RATE = 1.0

    /**
     * Maintenance margin rate — minimum equity to keep position open.
     * Below this triggers liquidation warning, then forced close.
     * Binance standard: 0.40% of notional for BTC, slightly higher for alts.
     */
    val MAINTENANCE_MARGIN_RATE = mapOf(
        "BTC/USDT" to 0.004,   // 0.40%
        "ETH/USDT" to 0.004,   // 0.40%
        "SOL/USDT" to 0.005,   // 0.50%
        "XRP/USDT" to 0.005    // 0.50%
    )

    private const val DEFAULT_MAINTENANCE_MARGIN_RATE = 0.005

    // =========================================================================
    // COST CALCULATIONS
    // =========================================================================

    /**
     * Entry cost = taker fee + half spread (paid when opening position).
     * Deducted from USDT balance immediately on trade open.
     *
     * @param symbol  Trading pair e.g. "BTC/USDT"
     * @param notionalValue  Full position value in USDT (margin × leverage)
     */
    fun entryCost(symbol: String, notionalValue: Double): Double {
        val fee = notionalValue * DEFAULT_FEE_RATE
        val halfSpread = notionalValue * (HALF_SPREAD[symbol] ?: DEFAULT_HALF_SPREAD)
        return fee + halfSpread
    }

    /**
     * Exit cost = taker fee + half spread (paid when closing position).
     *
     * @param symbol  Trading pair
     * @param notionalValue  Full position value at exit price
     */
    fun exitCost(symbol: String, notionalValue: Double): Double {
        val fee = notionalValue * DEFAULT_FEE_RATE
        val halfSpread = notionalValue * (HALF_SPREAD[symbol] ?: DEFAULT_HALF_SPREAD)
        return fee + halfSpread
    }

    /**
     * Total round-trip cost for a complete open+close cycle.
     * Used to assess trade viability before execution.
     */
    fun roundTripCost(symbol: String, notionalValue: Double): Double {
        return entryCost(symbol, notionalValue) + exitCost(symbol, notionalValue)
    }

    /**
     * Break-even price move % needed to cover round-trip costs.
     * The trade must move at least this much to be profitable.
     */
    fun breakEvenMovePercent(symbol: String): Double {
        val totalRate = DEFAULT_FEE_RATE * 2 +
                        (HALF_SPREAD[symbol] ?: DEFAULT_HALF_SPREAD) * 2
        return totalRate * 100.0
    }

    // =========================================================================
    // MARGIN CALCULATIONS
    // =========================================================================

    /**
     * Required initial margin to open a position.
     *
     * @param notionalValue  Full position value (quantity × price)
     * @param leverage       Leverage multiplier (e.g. 10 for 10x)
     * @return               USDT margin required
     */
    fun initialMargin(notionalValue: Double, leverage: Int): Double {
        return notionalValue / leverage.toDouble()
    }

    /**
     * Maintenance margin — minimum USDT equity to avoid liquidation.
     *
     * @param symbol         Trading pair
     * @param notionalValue  Full position value at current price
     */
    fun maintenanceMargin(symbol: String, notionalValue: Double): Double {
        val rate = MAINTENANCE_MARGIN_RATE[symbol] ?: DEFAULT_MAINTENANCE_MARGIN_RATE
        return notionalValue * rate
    }

    /**
     * Liquidation price for a LONG position (isolated margin).
     *
     * Formula: liquidationPrice = entryPrice × (1 - 1/leverage + maintenanceRate)
     *
     * @param entryPrice     Price position was opened at
     * @param leverage       Leverage multiplier
     * @param symbol         Trading pair (for maintenance margin rate)
     */
    fun liquidationPriceLong(entryPrice: Double, leverage: Int, symbol: String): Double {
        val maintenanceRate = MAINTENANCE_MARGIN_RATE[symbol] ?: DEFAULT_MAINTENANCE_MARGIN_RATE
        return entryPrice * (1.0 - (1.0 / leverage) + maintenanceRate)
    }

    /**
     * Liquidation price for a SHORT position (isolated margin).
     *
     * Formula: liquidationPrice = entryPrice × (1 + 1/leverage - maintenanceRate)
     */
    fun liquidationPriceShort(entryPrice: Double, leverage: Int, symbol: String): Double {
        val maintenanceRate = MAINTENANCE_MARGIN_RATE[symbol] ?: DEFAULT_MAINTENANCE_MARGIN_RATE
        return entryPrice * (1.0 + (1.0 / leverage) - maintenanceRate)
    }

    /**
     * Net P&L after fees and spread for an open position.
     * This is what the UI should display as the true unrealised P&L.
     *
     * @param symbol        Trading pair
     * @param direction     TradeDirection.LONG or SHORT
     * @param entryPrice    Price opened at
     * @param currentPrice  Current market price
     * @param notionalValue Full position value (quantity × entryPrice)
     */
    fun netUnrealisedPnL(
        symbol: String,
        direction: TradeDirection,
        entryPrice: Double,
        currentPrice: Double,
        notionalValue: Double
    ): Double {
        // Raw P&L from price movement
        val rawPnL = if (direction == TradeDirection.LONG) {
            (currentPrice - entryPrice) * (notionalValue / entryPrice)
        } else {
            (entryPrice - currentPrice) * (notionalValue / entryPrice)
        }
        // Subtract entry costs already paid + projected exit costs
        val alreadyPaid = entryCost(symbol, notionalValue)
        val projectedExit = exitCost(symbol, notionalValue)
        return rawPnL - alreadyPaid - projectedExit
    }

    /**
     * Current position value marked to market.
     * = initial margin + unrealised P&L (net of fees)
     */
    fun markToMarketValue(
        symbol: String,
        direction: TradeDirection,
        entryPrice: Double,
        currentPrice: Double,
        notionalValue: Double,
        marginPosted: Double
    ): Double {
        val pnl = netUnrealisedPnL(symbol, direction, entryPrice, currentPrice, notionalValue)
        return marginPosted + pnl
    }
}
