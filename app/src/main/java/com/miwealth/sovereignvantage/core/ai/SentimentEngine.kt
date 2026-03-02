/**
 * SENTIMENT ENGINE
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * V5.17.0 CHANGES:
 * - Singleton pattern via companion object getInstance()
 *   → All consumers (TradingSystem, TradingSystemIntegration, MasterAIController)
 *     now share ONE instance instead of three separate polling cycles
 * - Default provider changed: PlaceholderSentimentProvider → ScrapingSentimentProvider
 *   → Fear & Greed Index + CoinGecko now feed real signals
 * 
 * V5.17.0 CHANGES:
 * - Refactored to use SentimentDataProvider adapter pattern
 * - Base sentiment now delegated to pluggable provider
 * - Macro adjustments (Fed/risk) still applied by this engine
 * 
 * Data flow:
 *   SentimentDataProvider.fetchSentiment()  ← pluggable
 *     → SentimentEngine applies macro adjustments
 *       → SentimentScore cached per asset
 *         → TradingCoordinator.analyzeSymbol() reads cache
 *           → MarketContext.socialVolume + newsImpactScore
 *             → DQN features 18 + 19
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */


package com.miwealth.sovereignvantage.core.ai

import android.content.Context
import com.miwealth.sovereignvantage.core.trading.engine.CCXTBridge
import com.miwealth.sovereignvantage.core.ai.macro.*
import com.miwealth.sovereignvantage.core.ai.sentiment.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant

/**
 * Data class to hold the real-time sentiment score for a given asset.
 * Score ranges from -1.0 (Extremely Negative) to +1.0 (Extremely Positive).
 */
data class SentimentScore(
    val asset: String,
    val score: Double,
    val volume: Int, // Number of mentions/posts analyzed
    val timestamp: Instant,
    val macroAdjustment: Double = 0.0, // Adjustment from macro sentiment
    val providerName: String = "unknown", // V5.17.0: Which provider supplied the data
    val providerConfidence: Double = 1.0  // V5.17.0: Provider's confidence in score
)

/**
 * The specialist engine for real-time social media sentiment analysis.
 * 
 * V5.17.0: Singleton pattern — use SentimentEngine.getInstance(context) to
 * obtain the shared instance. All consumers share one polling cycle, one
 * provider, and one cache. Default provider is now ScrapingSentimentProvider
 * (Fear & Greed Index + CoinGecko — free, real signals).
 * 
 * To swap providers, pass a different SentimentDataProvider to getInstance():
 *   - ScrapingSentimentProvider()       — web scraping (default, free, 2 live sources)
 *   - PlaceholderSentimentProvider()    — synthetic noise (for testing only)
 *   - ApiSentimentProvider(apiKey=...) — paid API (reliable, costs AU$30-70/mo)
 */
class SentimentEngine(
    private val context: Context,
    private val provider: SentimentDataProvider = ScrapingSentimentProvider(),
    private val assetsToMonitor: List<String> = listOf("BTC", "ETH", "SOL", "XRP"),
    private val pollingIntervalMs: Long = 300_000L  // 5 minutes
) {

    private var engineJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val ccxtBridge = CCXTBridge(context)
    
    // Macro sentiment analyzer integration
    private val macroAnalyzer: MacroSentimentAnalyzer by lazy {
        MacroSentimentAnalyzer.getInstance(context)
    }
    private var cachedMacroContext: MacroContext? = null

    var isRunning: Boolean = false
        private set

    private val sentimentCache = mutableMapOf<String, SentimentScore>()
    
    // V5.17.0: Reference counting for singleton lifecycle management.
    // Multiple consumers call start()/stop() independently — we only
    // actually stop when the last consumer releases.
    private var refCount = 0

    fun start() {
        synchronized(this) {
            refCount++
            if (isRunning) {
                println("SentimentEngine: start() called (refCount=$refCount, already running)")
                return
            }
            isRunning = true
        }
        println("SentimentEngine: Starting (refCount=$refCount)")
        engineJob = scope.launch {
            // Initialize the provider
            try {
                provider.initialize()
                println("SentimentEngine: Started with provider '${provider.getProviderName()}' " +
                        "(available: ${provider.isAvailable()})")
            } catch (e: Exception) {
                println("SentimentEngine: Provider initialization failed: ${e.message}")
            }
            
            while (isRunning) {
                try {
                    // 1. Update macro context periodically
                    cachedMacroContext = macroAnalyzer.getMacroContext()
                    
                    // 2. Determine which assets to poll
                    val assets = if (provider.getSupportedAssets().isNotEmpty()) {
                        // Use intersection of monitored + provider-supported
                        assetsToMonitor.filter { it in provider.getSupportedAssets() }
                            .ifEmpty { assetsToMonitor }  // Fall back to all if no overlap
                    } else {
                        assetsToMonitor  // Provider supports anything
                    }
                    
                    // 3. Fetch sentiment from provider + apply macro adjustments
                    assets.forEach { asset ->
                        val score = analyzeSentiment(asset)
                        sentimentCache[asset] = score
                        println("SentimentEngine: [$asset] score=${score.score} " +
                                "volume=${score.volume} macro=${score.macroAdjustment} " +
                                "provider=${score.providerName} confidence=${score.providerConfidence}")
                    }

                } catch (e: Exception) {
                    println("SentimentEngine Error: ${e.message}")
                }
                delay(pollingIntervalMs)
            }
        }
    }

    fun stop() {
        synchronized(this) {
            refCount = (refCount - 1).coerceAtLeast(0)
            if (refCount > 0) {
                println("SentimentEngine: stop() called but still has $refCount consumers, staying alive")
                return
            }
        }
        println("SentimentEngine: All consumers released, shutting down")
        isRunning = false
        engineJob?.cancel()
        scope.launch {
            try {
                provider.shutdown()
            } catch (e: Exception) {
                println("SentimentEngine: Provider shutdown error: ${e.message}")
            }
        }
    }
    
    /**
     * Force stop regardless of reference count (for app shutdown).
     */
    fun forceStop() {
        synchronized(this) { refCount = 0 }
        isRunning = false
        engineJob?.cancel()
        scope.launch {
            try {
                provider.shutdown()
            } catch (e: Exception) {
                println("SentimentEngine: Provider shutdown error: ${e.message}")
            }
        }
    }

    /**
     * Retrieves the latest sentiment score for a given asset.
     */
    fun getSentiment(asset: String): SentimentScore? {
        return sentimentCache[asset]
    }

    /**
     * Fetch base sentiment from provider, then apply macro adjustments.
     * 
     * The provider supplies the raw social/news sentiment.
     * This method layers on macro context (Fed stance, risk level).
     */
    private suspend fun analyzeSentiment(asset: String): SentimentScore {
        // 1. Get base sentiment from provider
        val rawData = provider.fetchSentiment(asset)
        val baseScore = rawData?.score ?: 0.5  // Neutral default if provider returns nothing
        val mentionCount = rawData?.mentionCount ?: 0
        val providerConfidence = rawData?.confidence ?: 0.0
        
        // 2. Calculate macro adjustment based on Fed sentiment
        val macroAdj = cachedMacroContext?.let { macro ->
            when (macro.globalSentiment) {
                SentimentDirection.HAWKISH -> -0.15 * macro.globalScore
                SentimentDirection.DOVISH -> 0.15 * macro.globalScore
                SentimentDirection.MIXED -> -0.05
                SentimentDirection.NEUTRAL -> 0.0
            }
        } ?: 0.0
        
        // 3. Risk level adjustment
        val riskAdj = cachedMacroContext?.let { macro ->
            when (macro.riskLevel) {
                MacroRiskLevel.EXTREME -> -0.2
                MacroRiskLevel.HIGH -> -0.1
                MacroRiskLevel.ELEVATED -> -0.05
                MacroRiskLevel.LOW -> 0.0
            }
        } ?: 0.0
        
        val finalScore = (baseScore + macroAdj + riskAdj).coerceIn(-1.0, 1.0)

        return SentimentScore(
            asset = asset,
            score = BigDecimal(finalScore).setScale(4, BigDecimal.ROUND_HALF_UP).toDouble(),
            volume = mentionCount,
            timestamp = Instant.now(),
            macroAdjustment = macroAdj + riskAdj,
            providerName = provider.getProviderName(),
            providerConfidence = providerConfidence
        )
    }
    
    /**
     * Get current macro context for external use (e.g., AI Board)
     */
    fun getMacroContext(): MacroContext? = cachedMacroContext
    
    /**
     * Get the active provider name (for UI display / logging)
     */
    fun getProviderName(): String = provider.getProviderName()
    
    /**
     * Check if the provider is functional
     */
    fun isProviderAvailable(): Boolean = provider.isAvailable()
    
    companion object {
        @Volatile
        private var instance: SentimentEngine? = null
        
        /**
         * Get the shared SentimentEngine instance (singleton).
         * 
         * V5.17.0: All consumers share one instance to avoid:
         *   - Multiple polling cycles (3x network load)
         *   - Multiple provider instances (3x API calls / rate limit risk)
         *   - Cache duplication (3x memory for same data)
         * 
         * First call creates the instance with the given provider.
         * Subsequent calls return the existing instance (provider param ignored).
         * Call resetInstance() to change provider at runtime.
         * 
         * @param context Android context (application context preferred)
         * @param provider Sentiment data provider (default: ScrapingSentimentProvider)
         * @return Shared SentimentEngine instance
         */
        fun getInstance(
            context: Context,
            provider: SentimentDataProvider = ScrapingSentimentProvider()
        ): SentimentEngine {
            return instance ?: synchronized(this) {
                instance ?: SentimentEngine(
                    context = context.applicationContext,
                    provider = provider
                ).also { 
                    instance = it
                    println("SentimentEngine: Singleton created with provider '${provider.getProviderName()}'")
                }
            }
        }
        
        /**
         * Reset the singleton (e.g., to swap providers at runtime).
         * Caller must stop() the old instance first, then call getInstance() again.
         */
        fun resetInstance() {
            synchronized(this) {
                instance?.forceStop()
                instance = null
                println("SentimentEngine: Singleton reset")
            }
        }
        
        /**
         * Check if singleton has been created (for lifecycle management).
         */
        fun hasInstance(): Boolean = instance != null
    }
}
