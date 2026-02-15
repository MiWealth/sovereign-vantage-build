/**
 * SCRAPING SENTIMENT PROVIDER
 * 
 * Sovereign Vantage: Arthur Edition V5.5.92
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Sentiment data via web scraping / free JSON APIs.
 * 
 * V5.5.92 CHANGES:
 * - FearGreedIndexSource: IMPLEMENTED — alternative.me JSON endpoint
 * - CoinGeckoSocialSource: IMPLEMENTED — public /coins/{id} API
 * - RedditSentimentSource: IMPLEMENTED — uses Reddit's .json endpoints (no Jsoup!)
 *   Searches r/cryptocurrency and asset-specific subs for mentions
 *   Keyword sentiment analysis on post titles (bullish/bearish word lists)
 *   Weighted by upvote ratio and post score
 * - Added isImplemented flag to ScrapingSource interface (skip stubs cleanly)
 * - Shared OkHttpClient with generous timeouts for background fetches
 * 
 * ACTIVE SOURCES (3/4):
 *   - Fear & Greed Index (alternative.me) — market-wide, JSON, high reliability
 *   - CoinGecko (coingecko.com) — per-asset sentiment votes + community data
 *   - Reddit (reddit.com) — per-asset keyword sentiment from post titles
 * 
 * STUBBED SOURCES (1/4):
 *   - Google Trends (anti-bot, unreliable)
 * 
 * IMPLEMENTATION NOTES:
 *   - Uses OkHttp for requests (already in project dependencies)
 *   - All sources use JSON APIs — no HTML parsing / Jsoup needed
 *   - Cache aggressively — sentiment doesn't change by the second
 *   - Run scraping on background thread, serve cached data to callers
 *   - Fear & Greed is market-wide — applied to ALL assets as baseline
 *   - CoinGecko is per-asset — provides asset-specific sentiment votes
 *   - Reddit is per-asset — keyword sentiment from r/cryptocurrency + r/bitcoin
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

package com.miwealth.sovereignvantage.core.ai.sentiment

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ScrapingSentimentProvider(
    private val scrapingIntervalMs: Long = 600_000L,  // 10 minutes (be respectful)
    private val sources: List<ScrapingSource> = defaultSources()
) : SentimentDataProvider {
    
    private val cache = ConcurrentHashMap<String, RawSentimentData>()
    private var scrapingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var _isAvailable = false
    
    override fun getProviderName(): String {
        val activeSources = sources.count { it.isImplemented }
        val totalSources = sources.size
        return "Web Scraping ($activeSources/$totalSources sources active)"
    }
    
    override fun isAvailable(): Boolean = _isAvailable
    
    override suspend fun initialize() {
        _isAvailable = true
        
        // Initial scrape to warm cache before first query
        try {
            println("ScrapingSentimentProvider: Warming cache with initial scrape...")
            scrapeAllSources()
            println("ScrapingSentimentProvider: Cache warmed with ${cache.size} assets")
        } catch (e: Exception) {
            println("ScrapingSentimentProvider: Initial scrape failed (will retry): ${e.message}")
        }
        
        // Start background scraping cycle
        scrapingJob = scope.launch {
            delay(scrapingIntervalMs)  // First cycle already done above
            while (isActive) {
                try {
                    scrapeAllSources()
                } catch (e: Exception) {
                    println("ScrapingSentimentProvider: Error during scrape cycle: ${e.message}")
                }
                delay(scrapingIntervalMs)
            }
        }
    }
    
    override suspend fun shutdown() {
        scrapingJob?.cancel()
        scope.cancel()
        cache.clear()
        _isAvailable = false
    }
    
    override suspend fun fetchSentiment(asset: String): RawSentimentData? {
        return cache[asset.uppercase()]
    }
    
    override fun getSupportedAssets(): List<String> = listOf(
        "BTC", "ETH", "SOL", "XRP", "ADA", "DOGE", "AVAX", "DOT", "MATIC", "LINK"
    )
    
    override fun estimatedCostPerQuery(): Double = 0.0  // Free
    
    // ========================================================================
    // SCRAPING IMPLEMENTATION
    // ========================================================================
    
    private suspend fun scrapeAllSources() {
        val assets = getSupportedAssets()
        
        for (asset in assets) {
            val results = mutableListOf<SourceResult>()
            
            for (source in sources) {
                if (!source.isImplemented) continue  // Skip stubs cleanly
                try {
                    val result = source.scrape(asset)
                    if (result != null) results.add(result)
                } catch (e: Exception) {
                    println("ScrapingSentimentProvider: ${source.name} failed for $asset: ${e.message}")
                }
                // Small delay between sources to avoid burst requests
                delay(200)
            }
            
            if (results.isNotEmpty()) {
                cache[asset] = aggregateResults(asset, results)
            }
        }
    }
    
    private fun aggregateResults(asset: String, results: List<SourceResult>): RawSentimentData {
        // Weighted average by source reliability
        val totalWeight = results.sumOf { it.weight }
        val weightedScore = if (totalWeight > 0) {
            results.sumOf { it.score * it.weight } / totalWeight
        } else 0.0
        
        val totalMentions = results.sumOf { it.mentionCount }
        val sourceNames = results.map { it.sourceName }
        val avgConfidence = results.map { it.confidence }.average()
        
        return RawSentimentData(
            asset = asset,
            score = weightedScore.coerceIn(-1.0, 1.0),
            mentionCount = totalMentions,
            sources = sourceNames,
            confidence = avgConfidence
        )
    }
    
    // ========================================================================
    // SOURCE ABSTRACTION
    // ========================================================================
    
    data class SourceResult(
        val sourceName: String,
        val score: Double,          // -1.0 to +1.0
        val mentionCount: Int,
        val weight: Double,         // Source reliability weight
        val confidence: Double      // 0.0 to 1.0
    )
    
    /**
     * Individual scraping source. Each source knows how to:
     * 1. Build the URL for a given asset
     * 2. Parse the response into a SourceResult
     * 3. Handle its own rate limiting
     */
    interface ScrapingSource {
        val name: String
        val weight: Double          // Reliability weight (higher = more trusted)
        val isImplemented: Boolean  // V5.5.92: false for stubs, true for live sources
        suspend fun scrape(asset: String): SourceResult?
    }
    
    // ========================================================================
    // SHARED HTTP CLIENT
    // ========================================================================
    
    companion object {
        /**
         * Shared OkHttpClient for all sources. Timeouts are generous because
         * these are background fetches — we'd rather wait than miss data.
         */
        internal val httpClient = com.miwealth.sovereignvantage.core.network.SharedHttpClient.sentimentClient
        
        fun defaultSources(): List<ScrapingSource> = listOf(
            FearGreedIndexSource(),
            CoinGeckoSocialSource(),
            RedditSentimentSource()
            // GoogleTrendsSource() — disabled by default (fragile)
        )
    }
    
    // ========================================================================
    // LIVE SOURCE: Fear & Greed Index (alternative.me)
    // ========================================================================
    
    /**
     * Fear & Greed Index from alternative.me
     * Returns JSON directly — not really scraping.
     * 
     * URL: https://api.alternative.me/fng/?limit=1
     * Response: { "data": [{ "value": "73", "value_classification": "Greed", "timestamp": "..." }] }
     * 
     * NOTE: This is market-wide (crypto Fear & Greed), not per-asset.
     * We apply it to ALL assets as a baseline sentiment signal, with
     * slightly reduced weight for non-BTC assets since the index is
     * heavily BTC-correlated.
     * 
     * Value mapping:
     *   0-24  = Extreme Fear  → score ~ -1.0 to -0.52
     *   25-49 = Fear          → score ~ -0.50 to -0.02
     *   50    = Neutral       → score = 0.0
     *   51-74 = Greed         → score ~ +0.02 to +0.48
     *   75-100 = Extreme Greed → score ~ +0.50 to +1.0
     * 
     * Rate limit: No documented limit, but we poll every 10 min max.
     * Index updates once daily, so 30-min cache is more than adequate.
     */
    class FearGreedIndexSource : ScrapingSource {
        override val name = "Fear & Greed Index"
        override val weight = 0.8
        override val isImplemented = true
        
        // Cache the result — it only updates once daily anyway
        private var cachedResult: SourceResult? = null
        private var lastFetchMs: Long = 0
        private val cacheValidityMs = 1_800_000L  // 30 minutes
        
        override suspend fun scrape(asset: String): SourceResult? {
            return withContext(Dispatchers.IO) {
                // Return cached if still fresh
                val now = System.currentTimeMillis()
                if (cachedResult != null && (now - lastFetchMs) < cacheValidityMs) {
                    return@withContext adjustForAsset(cachedResult!!, asset)
                }
                
                try {
                    val request = Request.Builder()
                        .url("https://api.alternative.me/fng/?limit=1")
                        .header("User-Agent", "SovereignVantage/5.5.93")
                        .build()
                    
                    val response = httpClient.newCall(request).execute()
                    
                    if (!response.isSuccessful) {
                        println("FearGreedIndex: HTTP ${response.code}")
                        return@withContext cachedResult?.let { adjustForAsset(it, asset) }
                    }
                    
                    val body = response.body?.string() ?: return@withContext null
                    val json = JSONObject(body)
                    val dataArray = json.getJSONArray("data")
                    
                    if (dataArray.length() == 0) return@withContext null
                    
                    val entry = dataArray.getJSONObject(0)
                    val value = entry.getString("value").toIntOrNull() ?: return@withContext null
                    val classification = entry.optString("value_classification", "Unknown")
                    
                    // Normalize 0-100 to -1.0/+1.0
                    // 0 → -1.0, 50 → 0.0, 100 → +1.0
                    val normalizedScore = (value - 50.0) / 50.0
                    
                    // Confidence is high — this is a well-established index
                    val confidence = when {
                        value <= 10 || value >= 90 -> 0.9   // Extreme readings are clear signals
                        value <= 25 || value >= 75 -> 0.8   // Strong readings
                        value in 40..60 -> 0.5              // Near neutral, less informative
                        else -> 0.7                          // Moderate readings
                    }
                    
                    val result = SourceResult(
                        sourceName = "Fear&Greed($value/$classification)",
                        score = normalizedScore,
                        mentionCount = 1,  // Not a mention-based metric
                        weight = weight,
                        confidence = confidence
                    )
                    
                    cachedResult = result
                    lastFetchMs = now
                    println("FearGreedIndex: value=$value ($classification) → score=${"%.3f".format(normalizedScore)}")
                    
                    adjustForAsset(result, asset)
                    
                } catch (e: Exception) {
                    println("FearGreedIndex: Error: ${e.message}")
                    // Return stale cache if available
                    cachedResult?.let { adjustForAsset(it, asset) }
                }
            }
        }
        
        /**
         * Fear & Greed is market-wide. For BTC it's directly applicable.
         * For altcoins, dampen slightly — they correlate but not perfectly.
         */
        private fun adjustForAsset(result: SourceResult, asset: String): SourceResult {
            val dampening = when (asset.uppercase()) {
                "BTC" -> 1.0    // Index is BTC-centric
                "ETH" -> 0.9   // High BTC correlation
                else -> 0.7    // Altcoins correlate less
            }
            return result.copy(
                score = (result.score * dampening).coerceIn(-1.0, 1.0),
                weight = result.weight * dampening
            )
        }
    }
    
    // ========================================================================
    // LIVE SOURCE: CoinGecko Community Data
    // ========================================================================
    
    /**
     * CoinGecko community/sentiment data via free public API.
     * 
     * URL: https://api.coingecko.com/api/v3/coins/{id}
     * 
     * Relevant fields:
     *   - sentiment_votes_up_percentage (0-100, null if no votes)
     *   - sentiment_votes_down_percentage (0-100)
     *   - community_data.twitter_followers (absolute count)
     *   - community_data.reddit_subscribers (absolute count)
     * 
     * We primarily use sentiment_votes_up_percentage as the core signal,
     * with community size as a mention count proxy.
     * 
     * Rate limit: Free tier ~10-30 calls/min (undocumented, varies).
     * With 10 assets on a 10-min cycle = 1 call/min — well within limits.
     * 
     * NOTE: CoinGecko IDs differ from ticker symbols. We maintain a
     * mapping table for supported assets.
     */
    class CoinGeckoSocialSource : ScrapingSource {
        override val name = "CoinGecko"
        override val weight = 0.7
        override val isImplemented = true
        
        // Per-asset cache with individual TTLs
        private val assetCache = ConcurrentHashMap<String, Pair<Long, SourceResult>>()
        private val cacheValidityMs = 600_000L  // 10 minutes per asset
        
        // Ticker → CoinGecko ID mapping
        private val tickerToId = mapOf(
            "BTC" to "bitcoin",
            "ETH" to "ethereum",
            "SOL" to "solana",
            "XRP" to "ripple",
            "ADA" to "cardano",
            "DOGE" to "dogecoin",
            "AVAX" to "avalanche-2",
            "DOT" to "polkadot",
            "MATIC" to "matic-network",
            "LINK" to "chainlink",
            "BNB" to "binancecoin",
            "LTC" to "litecoin",
            "UNI" to "uniswap",
            "ATOM" to "cosmos",
            "NEAR" to "near",
            "APT" to "aptos",
            "ARB" to "arbitrum",
            "OP" to "optimism",
            "SUI" to "sui",
            "SEI" to "sei-network"
        )
        
        override suspend fun scrape(asset: String): SourceResult? {
            val geckoId = tickerToId[asset.uppercase()] ?: return null
            
            return withContext(Dispatchers.IO) {
                // Return cached if still fresh
                val now = System.currentTimeMillis()
                val cached = assetCache[asset.uppercase()]
                if (cached != null && (now - cached.first) < cacheValidityMs) {
                    return@withContext cached.second
                }
                
                try {
                    val request = Request.Builder()
                        .url("https://api.coingecko.com/api/v3/coins/$geckoId" +
                                "?localization=false&tickers=false&market_data=false" +
                                "&community_data=true&developer_data=false&sparkline=false")
                        .header("User-Agent", "SovereignVantage/5.5.93")
                        .header("Accept", "application/json")
                        .build()
                    
                    val response = httpClient.newCall(request).execute()
                    
                    if (response.code == 429) {
                        // Rate limited — back off and return cache
                        println("CoinGecko: Rate limited for $asset, using cache")
                        return@withContext cached?.second
                    }
                    
                    if (!response.isSuccessful) {
                        println("CoinGecko: HTTP ${response.code} for $asset")
                        return@withContext cached?.second
                    }
                    
                    val body = response.body?.string() ?: return@withContext null
                    val json = JSONObject(body)
                    
                    // Primary signal: sentiment votes
                    val sentimentUp = json.optDouble("sentiment_votes_up_percentage", Double.NaN)
                    val sentimentDown = json.optDouble("sentiment_votes_down_percentage", Double.NaN)
                    
                    // Community data for mention count proxy
                    val communityData = json.optJSONObject("community_data")
                    val twitterFollowers = communityData?.optInt("twitter_followers", 0) ?: 0
                    val redditSubscribers = communityData?.optInt("reddit_subscribers", 0) ?: 0
                    
                    // Calculate sentiment score
                    val score: Double
                    val confidence: Double
                    
                    if (!sentimentUp.isNaN() && !sentimentDown.isNaN() && sentimentUp + sentimentDown > 0) {
                        // sentiment_votes_up_percentage is 0-100
                        // Normalize: 50% → 0.0, 100% → +1.0, 0% → -1.0
                        score = (sentimentUp - 50.0) / 50.0
                        
                        // Higher confidence when there's a clear directional lean
                        val spread = Math.abs(sentimentUp - sentimentDown)
                        confidence = when {
                            spread > 60 -> 0.85  // Very one-sided
                            spread > 30 -> 0.7   // Clear lean
                            spread > 10 -> 0.5   // Mild lean
                            else -> 0.35          // Near 50/50, not very informative
                        }
                    } else {
                        // No sentiment vote data — use neutral with low confidence
                        score = 0.0
                        confidence = 0.1
                    }
                    
                    // Mention count = rough proxy from community size
                    // Scaled down to reasonable range (not raw follower counts)
                    val mentionProxy = ((twitterFollowers + redditSubscribers) / 10_000)
                        .coerceIn(0, 1000)
                    
                    val result = SourceResult(
                        sourceName = "CoinGecko($asset)",
                        score = score.coerceIn(-1.0, 1.0),
                        mentionCount = mentionProxy,
                        weight = weight,
                        confidence = confidence
                    )
                    
                    assetCache[asset.uppercase()] = Pair(now, result)
                    println("CoinGecko: $asset → sentUp=${"%.1f".format(sentimentUp)}% " +
                            "score=${"%.3f".format(score)} confidence=${"%.2f".format(confidence)}")
                    
                    result
                    
                } catch (e: Exception) {
                    println("CoinGecko: Error for $asset: ${e.message}")
                    cached?.second  // Return stale cache if available
                }
            }
        }
    }
    
    // ========================================================================
    // LIVE SOURCE: Reddit Cryptocurrency Sentiment
    // ========================================================================
    
    /**
     * Reddit sentiment from cryptocurrency subreddits via JSON API.
     * 
     * V5.5.92: No Jsoup needed! Reddit exposes JSON endpoints by appending
     * .json to any URL. We search r/cryptocurrency and r/bitcoin for recent
     * posts mentioning each asset, then score titles using keyword sentiment.
     * 
     * Endpoints used:
     *   https://www.reddit.com/r/cryptocurrency/search.json?q=BTC&sort=new&t=day&limit=25
     *   https://www.reddit.com/r/bitcoin/new.json?limit=25
     * 
     * Response structure:
     *   { "data": { "children": [{ "data": { "title": "...", "score": 42, 
     *     "upvote_ratio": 0.85, "num_comments": 15 } }] } }
     * 
     * Sentiment scoring:
     *   - Each post title is scored against bullish/bearish keyword lists
     *   - Score is weighted by upvote_ratio (community agreement signal)
     *   - High comment count posts get more weight (engagement = signal)
     *   - Final score is normalized to -1.0/+1.0
     * 
     * Rate limit: ~10 requests/min unauthenticated. We hit 2 subreddits
     * per asset but cache for 15 minutes, so ~10 assets = 20 requests every
     * 15 min = ~1.3 requests/min — well within limits.
     * 
     * IMPORTANT: Reddit requires a custom User-Agent or returns 429.
     */
    class RedditSentimentSource : ScrapingSource {
        override val name = "Reddit"
        override val weight = 0.6
        override val isImplemented = true  // V5.5.92: Now live via JSON API
        
        // Per-asset cache
        private val assetCache = ConcurrentHashMap<String, Pair<Long, SourceResult>>()
        private val cacheValidityMs = 900_000L  // 15 minutes (respectful rate)
        
        // Subreddits to search per asset
        private val subreddits = listOf("cryptocurrency", "CryptoMarkets")
        
        // BTC gets its own dedicated subreddit too
        private val assetSpecificSubs = mapOf(
            "BTC" to listOf("bitcoin", "cryptocurrency", "CryptoMarkets"),
            "ETH" to listOf("ethereum", "cryptocurrency", "CryptoMarkets"),
            "SOL" to listOf("solana", "cryptocurrency", "CryptoMarkets"),
            "DOGE" to listOf("dogecoin", "cryptocurrency", "CryptoMarkets"),
            "ADA" to listOf("cardano", "cryptocurrency", "CryptoMarkets"),
            "XRP" to listOf("XRP", "cryptocurrency", "CryptoMarkets")
        )
        
        // Asset name mappings for search queries (tickers + full names)
        private val assetSearchTerms = mapOf(
            "BTC" to "bitcoin OR BTC",
            "ETH" to "ethereum OR ETH",
            "SOL" to "solana OR SOL",
            "XRP" to "XRP OR ripple",
            "ADA" to "cardano OR ADA",
            "DOGE" to "dogecoin OR DOGE",
            "AVAX" to "avalanche OR AVAX",
            "DOT" to "polkadot OR DOT",
            "MATIC" to "polygon OR MATIC",
            "LINK" to "chainlink OR LINK"
        )
        
        // ================================================================
        // KEYWORD SENTIMENT LISTS
        // ================================================================
        
        // Bullish keywords with intensity weights (higher = stronger signal)
        private val bullishKeywords = mapOf(
            // Strong bullish
            "moon" to 1.5, "mooning" to 1.5, "rocket" to 1.3, "🚀" to 1.3,
            "breakout" to 1.2, "parabolic" to 1.4, "ath" to 1.3,
            "all time high" to 1.3, "all-time high" to 1.3,
            // Moderate bullish
            "bullish" to 1.0, "bull" to 0.8, "buy" to 0.7, "buying" to 0.7,
            "accumulate" to 0.9, "accumulating" to 0.9, "hodl" to 0.8,
            "long" to 0.6, "pump" to 0.9, "pumping" to 1.0,
            "rally" to 0.9, "surge" to 0.9, "soar" to 1.0,
            "undervalued" to 0.8, "gem" to 0.7, "opportunity" to 0.6,
            // Mild bullish
            "up" to 0.3, "green" to 0.4, "gain" to 0.5, "gains" to 0.5,
            "profit" to 0.4, "support" to 0.3, "recovery" to 0.5,
            "bounce" to 0.5, "uptick" to 0.4, "adoption" to 0.6
        )
        
        // Bearish keywords with intensity weights
        private val bearishKeywords = mapOf(
            // Strong bearish
            "crash" to -1.5, "crashing" to -1.5, "collapse" to -1.4,
            "scam" to -1.3, "rug" to -1.4, "rugpull" to -1.5, "rug pull" to -1.5,
            "ponzi" to -1.3, "fraud" to -1.2, "hack" to -1.2, "hacked" to -1.3,
            // Moderate bearish
            "bearish" to -1.0, "bear" to -0.8, "sell" to -0.6, "selling" to -0.7,
            "dump" to -0.9, "dumping" to -1.0, "short" to -0.6,
            "overvalued" to -0.8, "bubble" to -0.9, "dead" to -1.0,
            "rekt" to -1.1, "liquidat" to -1.0, "capitulat" to -1.1,
            // Mild bearish
            "down" to -0.3, "red" to -0.4, "loss" to -0.5, "dip" to -0.3,
            "drop" to -0.5, "fell" to -0.4, "falling" to -0.5,
            "resistance" to -0.2, "fear" to -0.5, "risk" to -0.3,
            "warning" to -0.4, "concern" to -0.3, "decline" to -0.5
        )
        
        override suspend fun scrape(asset: String): SourceResult? {
            return withContext(Dispatchers.IO) {
                // Return cached if still fresh
                val now = System.currentTimeMillis()
                val cached = assetCache[asset.uppercase()]
                if (cached != null && (now - cached.first) < cacheValidityMs) {
                    return@withContext cached.second
                }
                
                try {
                    val searchTerm = assetSearchTerms[asset.uppercase()] ?: asset
                    val subsToSearch = assetSpecificSubs[asset.uppercase()] ?: subreddits
                    
                    val allPosts = mutableListOf<RedditPost>()
                    
                    // Search each relevant subreddit
                    for (sub in subsToSearch.take(2)) {  // Max 2 subs per asset to limit requests
                        try {
                            val posts = fetchSubredditPosts(sub, searchTerm)
                            allPosts.addAll(posts)
                            delay(500)  // Respectful delay between requests
                        } catch (e: Exception) {
                            println("Reddit: Error fetching r/$sub for $asset: ${e.message}")
                        }
                    }
                    
                    if (allPosts.isEmpty()) {
                        return@withContext cached?.second  // Return stale cache
                    }
                    
                    // Score sentiment from post titles
                    val sentimentResult = scorePosts(asset, allPosts)
                    
                    assetCache[asset.uppercase()] = Pair(now, sentimentResult)
                    println("Reddit: $asset → ${allPosts.size} posts, " +
                            "score=${"%.3f".format(sentimentResult.score)} " +
                            "confidence=${"%.2f".format(sentimentResult.confidence)}")
                    
                    sentimentResult
                    
                } catch (e: Exception) {
                    println("Reddit: Error for $asset: ${e.message}")
                    cached?.second
                }
            }
        }
        
        /**
         * Fetch recent posts from a subreddit search via JSON API.
         */
        private fun fetchSubredditPosts(subreddit: String, query: String): List<RedditPost> {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://www.reddit.com/r/$subreddit/search.json" +
                    "?q=$encodedQuery&sort=new&t=day&limit=25&restrict_sr=on"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SovereignVantage/5.5.93 (by MiWealth Pty Ltd)")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.code == 429) {
                println("Reddit: Rate limited on r/$subreddit")
                return emptyList()
            }
            
            if (!response.isSuccessful) {
                println("Reddit: HTTP ${response.code} for r/$subreddit")
                return emptyList()
            }
            
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: return emptyList()
            val children = data.optJSONArray("children") ?: return emptyList()
            
            val posts = mutableListOf<RedditPost>()
            
            for (i in 0 until children.length()) {
                try {
                    val postData = children.getJSONObject(i)
                        .optJSONObject("data") ?: continue
                    
                    posts.add(RedditPost(
                        title = postData.optString("title", ""),
                        score = postData.optInt("score", 0),
                        upvoteRatio = postData.optDouble("upvote_ratio", 0.5),
                        numComments = postData.optInt("num_comments", 0),
                        subreddit = postData.optString("subreddit", subreddit)
                    ))
                } catch (e: Exception) {
                    // Skip malformed posts
                }
            }
            
            return posts
        }
        
        /**
         * Score a collection of posts using keyword sentiment analysis.
         * 
         * Each post title is scanned for bullish/bearish keywords.
         * The sentiment of each post is weighted by:
         *   - Upvote ratio (community agreement — 0.9+ means strong consensus)
         *   - Post score (visibility / engagement)
         *   - Keyword intensity (stronger words get more weight)
         */
        private fun scorePosts(asset: String, posts: List<RedditPost>): SourceResult {
            if (posts.isEmpty()) {
                return SourceResult(
                    sourceName = "Reddit($asset)",
                    score = 0.0,
                    mentionCount = 0,
                    weight = weight,
                    confidence = 0.1
                )
            }
            
            var totalWeightedScore = 0.0
            var totalPostWeight = 0.0
            
            for (post in posts) {
                val titleLower = post.title.lowercase()
                
                // Score this post's title
                var postSentiment = 0.0
                var keywordsFound = 0
                
                for ((keyword, intensity) in bullishKeywords) {
                    if (titleLower.contains(keyword)) {
                        postSentiment += intensity
                        keywordsFound++
                    }
                }
                for ((keyword, intensity) in bearishKeywords) {
                    if (titleLower.contains(keyword)) {
                        postSentiment += intensity  // Already negative
                        keywordsFound++
                    }
                }
                
                if (keywordsFound == 0) continue  // Neutral post, skip
                
                // Normalize by keywords found (avoid double-counting)
                postSentiment /= keywordsFound
                
                // Weight by community signals
                val engagementWeight = when {
                    post.score > 500 -> 3.0   // Hot post
                    post.score > 100 -> 2.0   // Popular
                    post.score > 20 -> 1.5    // Moderate
                    post.score > 5 -> 1.0     // Some engagement
                    else -> 0.5               // Low engagement
                }
                
                // Upvote ratio > 0.8 means community agrees with the post
                val agreementMultiplier = if (post.upvoteRatio > 0.8) 1.2
                    else if (post.upvoteRatio > 0.6) 1.0
                    else 0.7  // Controversial post, dampen signal
                
                val postWeight = engagementWeight * agreementMultiplier
                totalWeightedScore += postSentiment * postWeight
                totalPostWeight += postWeight
            }
            
            // Normalize final score to -1.0/+1.0
            val rawScore = if (totalPostWeight > 0) {
                totalWeightedScore / totalPostWeight
            } else 0.0
            
            // Clamp and apply sigmoid-like compression for extreme values
            val normalizedScore = (rawScore / 1.5).coerceIn(-1.0, 1.0)
            
            // Confidence based on post count and keyword coverage
            val postsWithKeywords = posts.count { post ->
                val t = post.title.lowercase()
                bullishKeywords.keys.any { t.contains(it) } || 
                    bearishKeywords.keys.any { t.contains(it) }
            }
            
            val confidence = when {
                postsWithKeywords >= 15 -> 0.8   // Lots of opinionated posts
                postsWithKeywords >= 8 -> 0.65   // Good sample
                postsWithKeywords >= 4 -> 0.5    // Decent sample
                postsWithKeywords >= 2 -> 0.35   // Thin data
                postsWithKeywords >= 1 -> 0.2    // Very thin
                else -> 0.1                       // No keyword matches
            }
            
            return SourceResult(
                sourceName = "Reddit($asset/${posts.size}posts/${postsWithKeywords}scored)",
                score = normalizedScore,
                mentionCount = posts.size,
                weight = weight,
                confidence = confidence
            )
        }
        
        /**
         * Lightweight data class for Reddit post data.
         */
        private data class RedditPost(
            val title: String,
            val score: Int,           // Net upvotes
            val upvoteRatio: Double,  // 0.0-1.0
            val numComments: Int,
            val subreddit: String
        )
    }
    
    // ========================================================================
    // STUB SOURCE: Google Trends (anti-bot, unreliable)
    // ========================================================================
    
    /**
     * Google Trends relative search interest.
     * Higher search volume often precedes price moves.
     * Unofficial API via trends.google.com
     */
    class GoogleTrendsSource : ScrapingSource {
        override val name = "Google Trends"
        override val weight = 0.4  // Lagging indicator, lower weight
        override val isImplemented = false  // Anti-bot measures, unreliable
        
        override suspend fun scrape(asset: String): SourceResult? {
            // TODO: Implement
            // This is tricky — Google Trends has anti-bot measures
            // May need pytrends via Python bridge or direct HTTP with cookies
            return null
        }
    }
}
