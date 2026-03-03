package com.miwealth.sovereignvantage.core.ai.sentiment

/**
 * SENTIMENT DATA PROVIDER — Adapter Interface
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Pluggable interface for sentiment data sources. SentimentEngine delegates
 * to whichever provider is configured — no changes needed upstream.
 * 
 * Data flow:
 *   SentimentDataProvider.fetchSentiment()
 *     → SentimentEngine.analyzeSentiment() (applies macro adjustments)
 *       → TradingCoordinator.analyzeSymbol() (enriches MarketContext)
 *         → AIBoardOrchestrator.buildDQNFeatures() (features 18+19)
 * 
 * Implementations:
 *   - PlaceholderSentimentProvider: Random noise (current default, zero cost)
 *   - ScrapingSentimentProvider:    Web scraping (previous approach, free but fragile)
 *   - ApiSentimentProvider:         Paid API (LunarCrush/Santiment/CryptoCompare)
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */


/**
 * Raw sentiment data from a provider, before macro adjustments.
 * 
 * @param score Base sentiment: -1.0 (extremely negative) to +1.0 (extremely positive)
 * @param mentionCount Number of social/news mentions found
 * @param sources Which sources contributed (e.g., "twitter", "reddit", "news")
 * @param confidence Provider's confidence in the score (0.0–1.0)
 */


data class RawSentimentData(
    val asset: String,
    val score: Double,
    val mentionCount: Int,
    val sources: List<String> = emptyList(),
    val confidence: Double = 1.0
)

/**
 * Pluggable sentiment data source.
 * 
 * SentimentEngine holds a reference to one provider and delegates all
 * base sentiment fetching to it. Macro adjustments (Fed/risk) are applied
 * by SentimentEngine after the provider returns.
 * 
 * Contract:
 * - fetchSentiment() should return quickly (cached or async-fetched data)
 * - initialize() is called once at engine start
 * - shutdown() is called once at engine stop
 * - getProviderName() identifies the source for logging/UI
 * - isAvailable() returns false if the provider can't function (no API key, no network, etc.)
 */
interface SentimentDataProvider {
    
    /** Human-readable provider name for logging and UI display */
    fun getProviderName(): String
    
    /** Whether this provider is currently functional */
    fun isAvailable(): Boolean
    
    /** One-time setup (API auth, scraper init, etc.) */
    suspend fun initialize()
    
    /** Clean shutdown (close connections, flush caches) */
    suspend fun shutdown()
    
    /**
     * Fetch base sentiment for an asset.
     * 
     * This should return cached/recent data — NOT block on a network call.
     * Providers should maintain their own refresh cycle internally.
     * 
     * @param asset Ticker symbol (e.g., "BTC", "ETH", "SOL")
     * @return Raw sentiment data, or null if no data available for this asset
     */
    suspend fun fetchSentiment(asset: String): RawSentimentData?
    
    /**
     * Which assets this provider can supply sentiment for.
     * Return empty list if the provider supports arbitrary assets.
     */
    fun getSupportedAssets(): List<String>
    
    /**
     * Estimated cost per query, in AUD.
     * Used for budgeting and provider selection.
     * Return 0.0 for free providers.
     */
    fun estimatedCostPerQuery(): Double = 0.0
}
