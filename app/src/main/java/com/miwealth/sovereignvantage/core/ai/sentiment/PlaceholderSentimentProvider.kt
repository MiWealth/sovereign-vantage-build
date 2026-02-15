/**
 * PLACEHOLDER SENTIMENT PROVIDER
 * 
 * Sovereign Vantage: Arthur Edition V5.5.91
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Default provider that generates synthetic sentiment data.
 * Zero cost, zero latency, zero signal value.
 * 
 * Use this as the fallback when no real provider is configured.
 * The random base score means the DQN's socialVolume and newsImpact
 * features carry noise rather than signal — the OHLCV-derived proxies
 * in buildDQNFeatures() are actually more useful than this.
 * 
 * TODO: Replace with ScrapingSentimentProvider or ApiSentimentProvider
 *       once a data source decision is made (cost-dependent).
 */

package com.miwealth.sovereignvantage.core.ai.sentiment

class PlaceholderSentimentProvider : SentimentDataProvider {
    
    override fun getProviderName(): String = "Placeholder (synthetic)"
    
    override fun isAvailable(): Boolean = true  // Always available
    
    override suspend fun initialize() {
        // Nothing to initialize
    }
    
    override suspend fun shutdown() {
        // Nothing to clean up
    }
    
    override suspend fun fetchSentiment(asset: String): RawSentimentData {
        // Original SentimentEngine logic: random fluctuation around 0.5
        val randomFluctuation = Math.random() * 0.4 - 0.2
        val baseScore = 0.5 + randomFluctuation
        val volume = (1000 + Math.random() * 5000).toInt()
        
        return RawSentimentData(
            asset = asset,
            score = baseScore,
            mentionCount = volume,
            sources = listOf("synthetic"),
            confidence = 0.1  // Low confidence — it's random noise
        )
    }
    
    override fun getSupportedAssets(): List<String> = emptyList()  // "Supports" anything
    
    override fun estimatedCostPerQuery(): Double = 0.0
}
