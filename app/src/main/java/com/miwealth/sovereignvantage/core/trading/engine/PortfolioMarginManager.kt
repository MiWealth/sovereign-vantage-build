package com.miwealth.sovereignvantage.core.trading.engine

/**
 * PORTFOLIO MARGIN MANAGER
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * CRITICAL SYSTEM - THIS RULE MUST NEVER BE BREACHED:
 * Always maintain sufficient funds to cover margin requirements.
 * 
 * This manager bridges the gap between:
 * - Exchange accounts (real balances)
 * - MarginSafeguard (protection logic)
 * - PositionManager (open positions)
 * 
 * It ensures margin status is always synchronized with reality.
 * 
 * KEY RESPONSIBILITIES:
 * 1. Sync balances from exchange in real-time
 * 2. Calculate total equity including unrealized P&L
 * 3. Calculate used margin from open positions with leverage
 * 4. Feed accurate data to MarginSafeguard
 * 5. Emit events for UI updates
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */



import android.util.Log
import com.miwealth.sovereignvantage.core.OrderStatus
import com.miwealth.sovereignvantage.core.TradeSide
import com.miwealth.sovereignvantage.core.exchange.Balance
import com.miwealth.sovereignvantage.core.exchange.UnifiedExchangeConnector
import com.miwealth.sovereignvantage.core.exchange.ai.AIExchangeConnector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.abs

/**
 * Portfolio and margin state snapshot.
 */
data class PortfolioState(
    /** Total balance across all assets (in quote currency, typically USDT) */
    val totalBalance: Double = 0.0,
    
    /** Unrealized P&L from open positions */
    val unrealizedPnl: Double = 0.0,
    
    /** Total equity = balance + unrealized P&L */
    val totalEquity: Double = 0.0,
    
    /** Margin used by open positions */
    val usedMargin: Double = 0.0,
    
    /** Available margin for new trades */
    val freeMargin: Double = 0.0,
    
    /** Margin level percentage (equity / used margin * 100) */
    val marginLevel: Double = Double.MAX_VALUE,
    
    /** Free margin as percentage of equity */
    val freeMarginPercent: Double = 100.0,
    
    /** Number of open positions */
    val openPositionCount: Int = 0,
    
    /** Total notional value of all positions */
    val totalNotionalValue: Double = 0.0,
    
    /** Average leverage across positions */
    val averageLeverage: Double = 1.0,
    
    /** Maximum leverage being used */
    val maxLeverageInUse: Double = 1.0,
    
    /** Individual asset balances */
    val balances: Map<String, AssetBalance> = emptyMap(),
    
    /** Open position summaries */
    val positions: List<MarginPositionSummary> = emptyList(),
    
    /** Last sync timestamp */
    val lastSyncTimestamp: Long = 0,
    
    /** Is data stale (sync failed or delayed)? */
    val isStale: Boolean = false,
    
    /** Sync error message if any */
    val syncError: String? = null
)

/**
 * Individual asset balance.
 */
data class AssetBalance(
    val asset: String,
    val free: Double,      // Available for trading
    val locked: Double,    // In open orders/positions
    val total: Double,     // Free + locked
    val usdValue: Double   // Estimated USD value
)

/**
 * Open position summary.
 */
data class MarginPositionSummary(
    val symbol: String,
    val side: PositionSide,
    val quantity: Double,
    val entryPrice: Double,
    val currentPrice: Double,
    val leverage: Double,
    val margin: Double,          // Margin locked for this position
    val notionalValue: Double,   // quantity * currentPrice
    val unrealizedPnl: Double,
    val unrealizedPnlPercent: Double,
    val liquidationPrice: Double?
)

enum class PositionSide { LONG, SHORT }

/**
 * Margin manager configuration.
 */
data class MarginManagerConfig(
    /** How often to sync balances (milliseconds) */
    val balanceSyncIntervalMs: Long = 5_000,
    
    /** How often to sync positions (milliseconds) */
    val positionSyncIntervalMs: Long = 2_000,
    
    /** Consider data stale after this many missed syncs */
    val staleThresholdMs: Long = 30_000,
    
    /** Primary quote currency for valuation */
    val quoteCurrency: String = "USDT",
    
    /** Enable WebSocket for real-time balance updates */
    val useWebSocketBalances: Boolean = true,
    
    /** Default leverage if not specified */
    val defaultLeverage: Double = 1.0,
    
    /** Maximum allowed leverage (exchange may have lower limits) */
    val maxAllowedLeverage: Double = 20.0
)

/**
 * Events from the margin manager.
 */
sealed class PortfolioEvent {
    data class BalanceUpdated(val state: PortfolioState) : PortfolioEvent()
    data class PositionOpened(val position: MarginPositionSummary) : PortfolioEvent()
    data class PositionClosed(val symbol: String, val realizedPnl: Double) : PortfolioEvent()
    data class PositionUpdated(val position: MarginPositionSummary) : PortfolioEvent()
    data class MarginWarning(val message: String, val marginLevel: Double) : PortfolioEvent()
    data class LeverageExceeded(val symbol: String, val requested: Double, val max: Double) : PortfolioEvent()
    data class SyncError(val message: String, val exception: Throwable?) : PortfolioEvent()
    data class StaleData(val lastSyncAgeMs: Long) : PortfolioEvent()
}

/**
 * PortfolioMarginManager - Real-time portfolio and margin synchronization.
 * 
 * This is the SOURCE OF TRUTH for portfolio state. It feeds data to:
 * - MarginSafeguard (for trade approval/rejection)
 * - UI (for display)
 * - RiskManager (for position sizing)
 * 
 * DESIGN PRINCIPLES:
 * 1. ACCURACY - Always sync with exchange, never assume
 * 2. FRESHNESS - Keep data as real-time as possible
 * 3. CONSERVATISM - If in doubt, assume worse case
 * 4. TRANSPARENCY - Expose all data for debugging/audit
 */
class PortfolioMarginManager(
    private val config: MarginManagerConfig = MarginManagerConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        private const val TAG = "PortfolioMarginManager"
        
        @Volatile
        private var INSTANCE: PortfolioMarginManager? = null
        
        fun getInstance(): PortfolioMarginManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PortfolioMarginManager().also { INSTANCE = it }
            }
        }
    }
    
    // Exchange connector for fetching data
    private var exchangeConnector: UnifiedExchangeConnector? = null
    private var aiConnector: AIExchangeConnector? = null
    
    // Margin safeguard integration
    private var marginSafeguard: MarginSafeguard? = null
    
    // State
    private val _portfolioState = MutableStateFlow(PortfolioState())
    val portfolioState: StateFlow<PortfolioState> = _portfolioState.asStateFlow()
    
    private val _events = MutableSharedFlow<PortfolioEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<PortfolioEvent> = _events.asSharedFlow()
    
    // Internal tracking
    private var balanceSyncJob: Job? = null
    private var positionSyncJob: Job? = null
    private var isRunning = false
    private val positionCache = mutableMapOf<String, MarginPositionSummary>()
    private val balanceCache = mutableMapOf<String, AssetBalance>()
    
    // =========================================================================
    // INITIALIZATION
    // =========================================================================
    
    /**
     * Initialize with exchange connector and margin safeguard.
     */
    fun initialize(
        connector: UnifiedExchangeConnector?,
        aiConnector: AIExchangeConnector?,
        marginSafeguard: MarginSafeguard
    ) {
        this.exchangeConnector = connector
        this.aiConnector = aiConnector
        this.marginSafeguard = marginSafeguard
        
        Log.i(TAG, "Initialized with connector=${connector != null}, aiConnector=${aiConnector != null}")
    }
    
    /**
     * Start real-time synchronization.
     */
    fun startSync() {
        if (isRunning) return
        isRunning = true
        
        // Start balance sync loop
        balanceSyncJob = scope.launch {
            while (isActive && isRunning) {
                syncBalances()
                delay(config.balanceSyncIntervalMs)
            }
        }
        
        // Start position sync loop (more frequent)
        positionSyncJob = scope.launch {
            while (isActive && isRunning) {
                syncPositions()
                delay(config.positionSyncIntervalMs)
            }
        }
        
        Log.i(TAG, "Started sync (balance: ${config.balanceSyncIntervalMs}ms, positions: ${config.positionSyncIntervalMs}ms)")
    }
    
    /**
     * Stop synchronization.
     */
    fun stopSync() {
        isRunning = false
        balanceSyncJob?.cancel()
        positionSyncJob?.cancel()
        Log.i(TAG, "Sync stopped")
    }
    
    // =========================================================================
    // BALANCE SYNCHRONIZATION
    // =========================================================================
    
    /**
     * Sync balances from exchange.
     */
    private suspend fun syncBalances() {
        try {
            val balances = fetchBalances()
            
            if (balances.isNotEmpty()) {
                balanceCache.clear()
                balanceCache.putAll(balances.associateBy { it.asset })
                updatePortfolioState()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync balances", e)
            markDataStale("Balance sync failed: ${e.message}")
            emitEvent(PortfolioEvent.SyncError("Balance sync failed", e))
        }
    }
    
    /**
     * Fetch balances from exchange.
     */
    private suspend fun fetchBalances(): List<AssetBalance> {
        // Try AI connector first (unified interface)
        aiConnector?.let { connector ->
            try {
                val balanceList = connector.getBalances()
                return balanceList.map { balance ->
                    AssetBalance(
                        asset = balance.asset,
                        free = balance.free,
                        locked = balance.locked,
                        total = balance.total,
                        usdValue = estimateUsdValue(balance.asset, balance.total)
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "AI connector balance fetch failed, trying unified connector", e)
            }
        }
        
        // Fallback to unified connector
        exchangeConnector?.let { connector ->
            val balances = connector.getBalances()
            return balances.map { balance ->
                AssetBalance(
                    asset = balance.asset,
                    free = balance.free,
                    locked = balance.locked,
                    total = balance.total,
                    usdValue = estimateUsdValue(balance.asset, balance.total)
                )
            }
        }
        
        return emptyList()
    }
    
    // =========================================================================
    // POSITION SYNCHRONIZATION
    // =========================================================================
    
    /**
     * Sync open positions from exchange.
     */
    private suspend fun syncPositions() {
        try {
            val positions = fetchPositions()
            
            positionCache.clear()
            positions.forEach { position ->
                positionCache[position.symbol] = position
            }
            
            updatePortfolioState()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync positions", e)
            emitEvent(PortfolioEvent.SyncError("Position sync failed", e))
        }
    }
    
    /**
     * Fetch positions from exchange.
     */
    private suspend fun fetchPositions(): List<MarginPositionSummary> {
        // Try AI connector first
        aiConnector?.let { connector ->
            try {
                val openOrders = connector.getOpenOrders()
                // Convert filled orders to positions (simplified)
                return openOrders.filter { it.status == OrderStatus.FILLED || it.status == OrderStatus.OPEN }
                    .map { order ->
                        val leverage = 1.0  // Would come from exchange
                        val notional = order.quantity * order.price
                        val margin = notional / leverage
                        
                        MarginPositionSummary(
                            symbol = order.symbol,
                            side = if (order.side == TradeSide.BUY || order.side == TradeSide.LONG) PositionSide.LONG else PositionSide.SHORT,
                            quantity = order.quantity,
                            entryPrice = order.price,
                            currentPrice = order.price,  // Would need real-time price
                            leverage = leverage,
                            margin = margin,
                            notionalValue = notional,
                            unrealizedPnl = 0.0,
                            unrealizedPnlPercent = 0.0,
                            liquidationPrice = null
                        )
                    }
            } catch (e: Exception) {
                Log.w(TAG, "AI connector position fetch failed", e)
            }
        }
        
        // For spot trading, positions are derived from balances
        // For futures, we'd query the futures API
        return emptyList()
    }
    
    // =========================================================================
    // STATE CALCULATION
    // =========================================================================
    
    /**
     * Update portfolio state from cached data.
     */
    private fun updatePortfolioState() {
        // Calculate total balance in quote currency
        var totalBalance = 0.0
        balanceCache.values.forEach { balance ->
            if (balance.asset == config.quoteCurrency) {
                totalBalance += balance.total
            } else {
                totalBalance += balance.usdValue
            }
        }
        
        // Calculate position metrics
        var totalUnrealizedPnl = 0.0
        var totalUsedMargin = 0.0
        var totalNotional = 0.0
        var maxLeverage = 1.0
        var sumLeverage = 0.0
        
        positionCache.values.forEach { position ->
            totalUnrealizedPnl += position.unrealizedPnl
            totalUsedMargin += position.margin
            totalNotional += position.notionalValue
            maxLeverage = maxOf(maxLeverage, position.leverage)
            sumLeverage += position.leverage
        }
        
        val avgLeverage = if (positionCache.isNotEmpty()) {
            sumLeverage / positionCache.size
        } else 1.0
        
        // Calculate equity and margins
        val totalEquity = totalBalance + totalUnrealizedPnl
        val freeMargin = totalEquity - totalUsedMargin
        val marginLevel = if (totalUsedMargin > 0) {
            (totalEquity / totalUsedMargin) * 100
        } else Double.MAX_VALUE
        val freeMarginPercent = if (totalEquity > 0) {
            (freeMargin / totalEquity) * 100
        } else 100.0
        
        // Create new state
        val newState = PortfolioState(
            totalBalance = totalBalance,
            unrealizedPnl = totalUnrealizedPnl,
            totalEquity = totalEquity,
            usedMargin = totalUsedMargin,
            freeMargin = freeMargin,
            marginLevel = marginLevel,
            freeMarginPercent = freeMarginPercent,
            openPositionCount = positionCache.size,
            totalNotionalValue = totalNotional,
            averageLeverage = avgLeverage,
            maxLeverageInUse = maxLeverage,
            balances = balanceCache.toMap(),
            positions = positionCache.values.toList(),
            lastSyncTimestamp = System.currentTimeMillis(),
            isStale = false,
            syncError = null
        )
        
        _portfolioState.value = newState
        
        // Update margin safeguard (triggers status recalculation)
        marginSafeguard?.forceUpdate()
        
        // Emit event
        emitEvent(PortfolioEvent.BalanceUpdated(newState))
        
        Log.d(TAG, "Portfolio updated: equity=${"%.2f".format(totalEquity)}, " +
                "usedMargin=${"%.2f".format(totalUsedMargin)}, " +
                "freeMargin=${"%.2f".format(freeMargin)} (${freeMarginPercent.toInt()}%)")
    }
    
    // =========================================================================
    // POSITION TRACKING
    // =========================================================================
    
    /**
     * Record a new position (called after order fill).
     */
    fun recordPositionOpened(
        symbol: String,
        side: PositionSide,
        quantity: Double,
        entryPrice: Double,
        leverage: Double = config.defaultLeverage
    ) {
        // Validate leverage
        if (leverage > config.maxAllowedLeverage) {
            emitEvent(PortfolioEvent.LeverageExceeded(symbol, leverage, config.maxAllowedLeverage))
            Log.w(TAG, "Leverage $leverage exceeds max ${config.maxAllowedLeverage}")
            // Still record but cap leverage
        }
        
        val effectiveLeverage = minOf(leverage, config.maxAllowedLeverage)
        val notional = quantity * entryPrice
        val margin = notional / effectiveLeverage
        
        val position = MarginPositionSummary(
            symbol = symbol,
            side = side,
            quantity = quantity,
            entryPrice = entryPrice,
            currentPrice = entryPrice,
            leverage = effectiveLeverage,
            margin = margin,
            notionalValue = notional,
            unrealizedPnl = 0.0,
            unrealizedPnlPercent = 0.0,
            liquidationPrice = calculateLiquidationPrice(entryPrice, effectiveLeverage, side)
        )
        
        positionCache[symbol] = position
        updatePortfolioState()
        emitEvent(PortfolioEvent.PositionOpened(position))
        
        Log.i(TAG, "Position opened: $symbol $side ${quantity}@${entryPrice} (${effectiveLeverage}x)")
    }
    
    /**
     * Update position with current price.
     */
    fun updatePositionPrice(symbol: String, currentPrice: Double) {
        val position = positionCache[symbol] ?: return
        
        val pnl = calculateUnrealizedPnl(position, currentPrice)
        val pnlPercent = if (position.entryPrice > 0) {
            (pnl / (position.quantity * position.entryPrice)) * 100
        } else 0.0
        
        val updated = position.copy(
            currentPrice = currentPrice,
            notionalValue = position.quantity * currentPrice,
            unrealizedPnl = pnl,
            unrealizedPnlPercent = pnlPercent
        )
        
        positionCache[symbol] = updated
        updatePortfolioState()
        emitEvent(PortfolioEvent.PositionUpdated(updated))
    }
    
    /**
     * Record position closed.
     */
    fun recordPositionClosed(symbol: String, exitPrice: Double) {
        val position = positionCache.remove(symbol) ?: return
        
        val realizedPnl = calculateUnrealizedPnl(position, exitPrice)
        updatePortfolioState()
        emitEvent(PortfolioEvent.PositionClosed(symbol, realizedPnl))
        
        Log.i(TAG, "Position closed: $symbol, realized P&L: ${"%.2f".format(realizedPnl)}")
    }
    
    // =========================================================================
    // LEVERAGE VALIDATION
    // =========================================================================
    
    /**
     * Check if requested leverage is allowed.
     */
    fun validateLeverage(symbol: String, requestedLeverage: Double): Boolean {
        if (requestedLeverage > config.maxAllowedLeverage) {
            emitEvent(PortfolioEvent.LeverageExceeded(
                symbol, requestedLeverage, config.maxAllowedLeverage
            ))
            return false
        }
        
        // Additional exchange-specific checks could go here
        return true
    }
    
    /**
     * Get maximum allowed leverage for a symbol.
     */
    fun getMaxLeverage(symbol: String): Double {
        // Could query exchange for symbol-specific limits
        return config.maxAllowedLeverage
    }
    
    // =========================================================================
    // MARGIN CALCULATIONS
    // =========================================================================
    
    /**
     * Calculate margin required for a potential trade.
     */
    fun calculateMarginRequired(
        quantity: Double,
        price: Double,
        leverage: Double
    ): Double {
        val notional = quantity * price
        return notional / leverage
    }
    
    /**
     * Check if a trade can be made with current margin.
     */
    fun canAffordTrade(
        quantity: Double,
        price: Double,
        leverage: Double
    ): Boolean {
        val required = calculateMarginRequired(quantity, price, leverage)
        val state = _portfolioState.value
        return required <= state.freeMargin * 0.95  // 5% buffer
    }
    
    /**
     * Get maximum affordable quantity at given price and leverage.
     */
    fun getMaxAffordableQuantity(price: Double, leverage: Double): Double {
        val state = _portfolioState.value
        // Use 70% of free margin for safety
        val usableMargin = state.freeMargin * 0.70
        val maxNotional = usableMargin * leverage
        return maxNotional / price
    }
    
    // =========================================================================
    // HELPERS
    // =========================================================================
    
    /**
     * Calculate unrealized P&L for a position.
     */
    private fun calculateUnrealizedPnl(position: MarginPositionSummary, currentPrice: Double): Double {
        val priceDiff = currentPrice - position.entryPrice
        return when (position.side) {
            PositionSide.LONG -> position.quantity * priceDiff
            PositionSide.SHORT -> position.quantity * -priceDiff
        }
    }
    
    /**
     * Calculate liquidation price.
     */
    private fun calculateLiquidationPrice(
        entryPrice: Double,
        leverage: Double,
        side: PositionSide
    ): Double? {
        if (leverage <= 1.0) return null  // No liquidation for 1x
        
        // Simplified calculation (exchanges have more complex formulas)
        val marginPercent = 1.0 / leverage
        val liquidationDistance = entryPrice * (marginPercent * 0.9)  // 90% of margin
        
        return when (side) {
            PositionSide.LONG -> entryPrice - liquidationDistance
            PositionSide.SHORT -> entryPrice + liquidationDistance
        }
    }
    
    /**
     * Estimate USD value of an asset.
     */
    private fun estimateUsdValue(asset: String, amount: Double): Double {
        // For stablecoins, 1:1
        if (asset in setOf("USDT", "USDC", "BUSD", "DAI", "TUSD")) {
            return amount
        }
        
        // Would normally fetch price from cache/exchange
        // For now, return 0 for unknown
        return 0.0
    }
    
    /**
     * Mark data as stale.
     */
    private fun markDataStale(reason: String) {
        _portfolioState.update { it.copy(isStale = true, syncError = reason) }
        emitEvent(PortfolioEvent.StaleData(_portfolioState.value.lastSyncTimestamp))
    }
    
    private fun emitEvent(event: PortfolioEvent) {
        scope.launch {
            _events.emit(event)
        }
    }
    
    /**
     * Shutdown manager.
     */
    fun shutdown() {
        stopSync()
        scope.cancel()
        INSTANCE = null
    }
}

/**
 * Extension to get portfolio state from margin safeguard.
 */
fun MarginSafeguard.withPortfolioManager(manager: PortfolioMarginManager): MarginSafeguard {
    // The manager updates this safeguard automatically
    return this
}
