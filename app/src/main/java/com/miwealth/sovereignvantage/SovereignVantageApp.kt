package com.miwealth.sovereignvantage

import android.app.Application
import android.util.Log
import com.miwealth.sovereignvantage.core.TradingSystemManager
import com.miwealth.sovereignvantage.core.exchange.ExchangeRegistry
import com.miwealth.sovereignvantage.core.security.ExchangeCredentialManager
import com.miwealth.sovereignvantage.service.UnifiedPriceFeedService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SOVEREIGN VANTAGE V5.5.38 "ARTHUR EDITION"
 * APPLICATION CLASS
 * 
 * Initializes core services on app startup:
 * - TradingSystemManager (paper trading mode by default)
 * - UnifiedPriceFeedService (market data aggregation)
 * - ExchangeRegistry (PQC-secured exchange connections)
 * 
 * Default behaviour:
 * - Starts in PAPER TRADING mode (FREE tier)
 * - Subscribes to default watchlist (BTC, ETH, SOL)
 * - Users can upgrade to live trading via Settings
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */

@HiltAndroidApp
class SovereignVantageApp : Application() {
    
    companion object {
        private const val TAG = "SovereignVantageApp"
        
        // Default watchlist for paper trading
        private val DEFAULT_WATCHLIST = listOf(
            "BTC/USD", "ETH/USD", "SOL/USD", "XRP/USD", "ADA/USD",
            "DOGE/USD", "DOT/USD", "AVAX/USD", "LINK/USD", "MATIC/USD"
        )
        
        // Default paper trading balance (AUD)
        private const val DEFAULT_PAPER_BALANCE = 10000.0
        
        @Volatile
        lateinit var instance: SovereignVantageApp
            private set
    }
    
    // Application-scoped coroutine scope
    private val appScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Injected dependencies
    @Inject lateinit var tradingSystemManager: TradingSystemManager
    @Inject lateinit var credentialManager: ExchangeCredentialManager
    
    // Price feed service (lazy initialized)
    private var priceFeedService: UnifiedPriceFeedService? = null
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Log.i(TAG, "╔══════════════════════════════════════════════════════════╗")
        Log.i(TAG, "║     SOVEREIGN VANTAGE V5.5.38 - ARTHUR EDITION           ║")
        Log.i(TAG, "║     © 2025-2026 MiWealth Pty Ltd                         ║")
        Log.i(TAG, "║     Creator: Mike Stahl                                   ║")
        Log.i(TAG, "║     In Memory of Arthur Iain McManus (1966-2025)         ║")
        Log.i(TAG, "╚══════════════════════════════════════════════════════════╝")
        
        // Initialize trading system after Hilt injection completes
        appScope.launch {
            initializeTradingSystem()
        }
    }
    
    /**
     * Initialize trading system based on saved credentials.
     * - If no credentials: Start paper trading (FREE tier)
     * - If credentials exist: Initialize with PQC security
     */
    private suspend fun initializeTradingSystem() {
        try {
            Log.i(TAG, "Initializing trading system...")
            
            // Check if we have saved credentials
            val savedCredentials = credentialManager.getAllCredentials()
            val paperTradingEnabled = credentialManager.isPaperTradingEnabled()
            
            if (paperTradingEnabled || savedCredentials.isEmpty()) {
                // Start in paper trading mode
                val balance = credentialManager.getPaperTradingBalance()
                    .takeIf { it > 0 } ?: DEFAULT_PAPER_BALANCE
                
                Log.i(TAG, "Starting PAPER TRADING mode with balance: $${"%.2f".format(balance)}")
                
                val result = tradingSystemManager.initializePaperTrading(balance)
                result.onSuccess {
                    Log.i(TAG, "✓ Paper trading initialized successfully")
                    subscribeToDefaultWatchlist()
                }.onFailure { error ->
                    Log.e(TAG, "✗ Paper trading initialization failed: ${error.message}")
                }
            } else {
                // Start with live credentials (PQC-secured)
                val preferredExchange = credentialManager.getPreferredExchange()
                
                Log.i(TAG, "Starting LIVE TRADING mode with ${savedCredentials.size} exchange(s)")
                Log.i(TAG, "Preferred exchange: ${preferredExchange.name}")
                
                val result = tradingSystemManager.initializeWithPQC(
                    credentials = savedCredentials,
                    preferredExchange = preferredExchange
                )
                result.onSuccess {
                    Log.i(TAG, "✓ Live trading initialized with PQC security")
                    subscribeToDefaultWatchlist()
                }.onFailure { error ->
                    Log.e(TAG, "✗ Live trading initialization failed: ${error.message}")
                    // Fall back to paper trading
                    Log.i(TAG, "Falling back to paper trading...")
                    tradingSystemManager.initializePaperTrading(DEFAULT_PAPER_BALANCE)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during initialization: ${e.message}", e)
        }
    }
    
    /**
     * Subscribe to default watchlist for price updates.
     */
    private fun subscribeToDefaultWatchlist() {
        try {
            tradingSystemManager.subscribeToPrices(DEFAULT_WATCHLIST)
            Log.i(TAG, "Subscribed to ${DEFAULT_WATCHLIST.size} symbols")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to watchlist: ${e.message}")
        }
    }
    
    /**
     * Get the UnifiedPriceFeedService instance.
     * Creates one if it doesn't exist.
     */
    fun getPriceFeedService(): UnifiedPriceFeedService? {
        return priceFeedService
    }
    
    /**
     * Reinitialize trading system (e.g., after credential change).
     */
    fun reinitializeTradingSystem() {
        appScope.launch {
            Log.i(TAG, "Reinitializing trading system...")
            tradingSystemManager.shutdown()
            initializeTradingSystem()
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        Log.i(TAG, "Application terminating - shutting down trading system")
        tradingSystemManager.shutdown()
        priceFeedService?.shutdown()
    }
}
