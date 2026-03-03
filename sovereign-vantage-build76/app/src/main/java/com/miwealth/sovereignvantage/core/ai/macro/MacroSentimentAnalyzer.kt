package com.miwealth.sovereignvantage.core.ai.macro

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * SOVEREIGN VANTAGE V5.17.0 - MACRO SENTIMENT ANALYZER
 * 
 * Analyzes central bank communications and economic news for sentiment.
 * Uses keyword-based analysis (Phase 1) with hooks for future NLP/ML.
 * 
 * © 2025-2026 MiWealth Pty Ltd
 */
class MacroSentimentAnalyzer private constructor(context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val database = EconomicIndicatorDatabase.getInstance(context)
    private val rssParser = RSSFeedParser()
    private val keywords = SentimentKeywords
    
    // Cached sentiment by bank
    private val bankSentimentCache = ConcurrentHashMap<CentralBank, BankSentiment>()
    private var cachedMacroContext: MacroContext? = null
    private var lastUpdateTime = 0L
    
    // Live updates
    private val _macroContextFlow = MutableStateFlow<MacroContext?>(null)
    val macroContextFlow: StateFlow<MacroContext?> = _macroContextFlow.asStateFlow()
    
    companion object {
        @Volatile
        private var instance: MacroSentimentAnalyzer? = null
        
        fun getInstance(context: Context): MacroSentimentAnalyzer {
            return instance ?: synchronized(this) {
                instance ?: MacroSentimentAnalyzer(context).also { instance = it }
            }
        }
        
        private const val CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes
        private const val ANALYSIS_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
    }
    
    /**
     * Get current macro context (cached or fresh)
     */
    suspend fun getMacroContext(forceRefresh: Boolean = false): MacroContext {
        val now = System.currentTimeMillis()
        
        if (!forceRefresh && cachedMacroContext != null && 
            (now - lastUpdateTime) < CACHE_TTL_MS) {
            return cachedMacroContext!!
        }
        
        return refreshMacroContext()
    }
    
    /**
     * Refresh all macro data and sentiment analysis
     */
    suspend fun refreshMacroContext(): MacroContext = withContext(Dispatchers.Default) {
        // Fetch latest RSS feeds
        val feedItems = rssParser.fetchAllCentralBankFeeds()
        
        // Analyze sentiment for each item
        val newAnalyses = feedItems.mapNotNull { item ->
            analyzeFeedItem(item)
        }
        
        // Store in database
        if (newAnalyses.isNotEmpty()) {
            database.sentimentDao().insertAll(newAnalyses)
        }
        
        // Build sentiment for each major bank
        val fedSentiment = buildBankSentiment(CentralBank.FED)
        val ecbSentiment = buildBankSentiment(CentralBank.ECB)
        val rbaSentiment = buildBankSentiment(CentralBank.RBA)
        
        // Get upcoming high-impact events
        val upcomingEvents = database.eventDao().getUpcomingHighImpact()
        
        // Get recent data surprises
        val recentSurprises = database.indicatorDao()
            .getBiggestSurprises(System.currentTimeMillis() - ANALYSIS_WINDOW_MS, 5)
        
        // Calculate overall sentiment
        val (overallDirection, overallConfidence) = calculateOverallSentiment(
            listOfNotNull(fedSentiment, ecbSentiment, rbaSentiment)
        )
        
        // Determine risk level
        val riskLevel = assessMacroRisk(upcomingEvents, recentSurprises, overallDirection)
        
        // Build bankSentiments map for MacroContext
        val bankSentimentsMap = mutableMapOf<CentralBank, MacroSentimentScore>()
        fedSentiment?.let { bs ->
            bankSentimentsMap[CentralBank.FED] = MacroSentimentScore(
                centralBank = CentralBank.FED,
                direction = bs.direction,
                score = bs.confidence,
                confidence = bs.confidence,
                dataPoints = bs.recentHeadlines.size,
                lastUpdated = System.currentTimeMillis(),
                rateExpectation = RateExpectation.HOLD_LIKELY
            )
        }
        ecbSentiment?.let { bs ->
            bankSentimentsMap[CentralBank.ECB] = MacroSentimentScore(
                centralBank = CentralBank.ECB,
                direction = bs.direction,
                score = bs.confidence,
                confidence = bs.confidence,
                dataPoints = bs.recentHeadlines.size,
                lastUpdated = System.currentTimeMillis(),
                rateExpectation = RateExpectation.HOLD_LIKELY
            )
        }
        rbaSentiment?.let { bs ->
            bankSentimentsMap[CentralBank.RBA] = MacroSentimentScore(
                centralBank = CentralBank.RBA,
                direction = bs.direction,
                score = bs.confidence,
                confidence = bs.confidence,
                dataPoints = bs.recentHeadlines.size,
                lastUpdated = System.currentTimeMillis(),
                rateExpectation = RateExpectation.HOLD_LIKELY
            )
        }
        
        val context = MacroContext(
            timestamp = System.currentTimeMillis(),
            globalSentiment = overallDirection,
            globalScore = overallConfidence,
            bankSentiments = bankSentimentsMap,
            upcomingHighImpactEvents = upcomingEvents,
            recentSurprises = recentSurprises,
            riskLevel = riskLevel,
            narrative = buildSummary(overallDirection, fedSentiment, upcomingEvents)
        )
        
        cachedMacroContext = context
        lastUpdateTime = System.currentTimeMillis()
        _macroContextFlow.emit(context)
        
        context
    }
    
    /**
     * Analyze a single feed item for sentiment
     */
    private suspend fun analyzeFeedItem(item: RSSFeedItem): SentimentAnalysis? {
        val contentHash = hashContent(item.title + item.link)
        
        // Skip if already analyzed
        database.sentimentDao().findByHash(contentHash)?.let { return null }
        
        val text = "${item.title} ${item.description}".lowercase()
        
        // Count keyword matches
        var hawkishCount = 0
        var dovishCount = 0
        val matchedKeywords = mutableListOf<String>()
        
        keywords.HAWKISH.forEach { keyword ->
            if (text.contains(keyword)) {
                hawkishCount++
                matchedKeywords.add("+$keyword")
            }
        }
        
        keywords.DOVISH.forEach { keyword ->
            if (text.contains(keyword)) {
                dovishCount++
                matchedKeywords.add("-$keyword")
            }
        }
        
        // No relevant keywords found
        if (hawkishCount == 0 && dovishCount == 0) return null
        
        val total = hawkishCount + dovishCount
        val hawkishScore = hawkishCount.toDouble() / total
        val dovishScore = dovishCount.toDouble() / total
        
        val direction = when {
            hawkishScore > 0.6 -> SentimentDirection.HAWKISH
            dovishScore > 0.6 -> SentimentDirection.DOVISH
            hawkishScore > 0.4 && dovishScore > 0.4 -> SentimentDirection.MIXED
            else -> SentimentDirection.NEUTRAL
        }
        
        val confidence = kotlin.math.abs(hawkishScore - dovishScore) * 
                        kotlin.math.min(total / 3.0, 1.0) // More keywords = more confidence
        
        return SentimentAnalysis(
            contentHash = contentHash,
            sourceType = "RSS",
            sourceUrl = item.link,
            title = item.title.take(200),
            contentSnippet = item.description.take(500),
            centralBank = item.centralBank,
            direction = direction,
            confidence = confidence.coerceIn(0.0, 1.0),
            hawkishScore = hawkishScore,
            dovishScore = dovishScore,
            keywordsMatched = matchedKeywords.joinToString(",")
        )
    }
    
    /**
     * Build sentiment summary for a specific central bank
     */
    private suspend fun buildBankSentiment(bank: CentralBank): BankSentiment? {
        val since = System.currentTimeMillis() - ANALYSIS_WINDOW_MS
        val analyses = database.sentimentDao().getByBank(bank, 20)
        
        if (analyses.isEmpty()) return null
        
        // Weight recent analyses more heavily
        var hawkishWeight = 0.0
        var dovishWeight = 0.0
        var totalWeight = 0.0
        
        analyses.forEachIndexed { index, analysis ->
            val recencyWeight = 1.0 / (index + 1) // More recent = higher weight
            val weight = recencyWeight * analysis.confidence
            
            when (analysis.direction) {
                SentimentDirection.HAWKISH -> hawkishWeight += weight
                SentimentDirection.DOVISH -> dovishWeight += weight
                else -> {}
            }
            totalWeight += weight
        }
        
        val direction = when {
            totalWeight == 0.0 -> SentimentDirection.NEUTRAL
            hawkishWeight / totalWeight > 0.6 -> SentimentDirection.HAWKISH
            dovishWeight / totalWeight > 0.6 -> SentimentDirection.DOVISH
            else -> SentimentDirection.MIXED
        }
        
        val confidence = if (totalWeight > 0) {
            kotlin.math.abs(hawkishWeight - dovishWeight) / totalWeight
        } else 0.0
        
        // Get latest interest rate
        val latestRate = database.indicatorDao()
            .getLatest(bank, IndicatorType.INTEREST_RATE)?.actualValue
        
        val rateExpectation = when (direction) {
            SentimentDirection.HAWKISH -> RateExpectation.HIKE_LIKELY
            SentimentDirection.DOVISH -> RateExpectation.CUT_LIKELY
            SentimentDirection.NEUTRAL -> RateExpectation.HOLD_LIKELY
            SentimentDirection.MIXED -> RateExpectation.UNCERTAIN
        }
        
        return BankSentiment(
            bank = bank,
            direction = direction,
            confidence = confidence.coerceIn(0.0, 1.0),
            latestRate = latestRate ?: 0.0,
            rateChangeExpectation = rateExpectation,
            recentHeadlines = analyses.take(5).map { it.title }
        )
    }
    
    /**
     * Calculate overall macro sentiment from all banks
     */
    private fun calculateOverallSentiment(
        bankSentiments: List<BankSentiment>
    ): Pair<SentimentDirection, Double> {
        if (bankSentiments.isEmpty()) {
            return Pair(SentimentDirection.NEUTRAL, 0.0)
        }
        
        // Weight by bank importance (Fed dominates)
        val weights = mapOf(
            CentralBank.FED to 0.5,
            CentralBank.ECB to 0.25,
            CentralBank.RBA to 0.1,
            CentralBank.BOE to 0.1,
            CentralBank.BOJ to 0.05
        )
        
        var hawkishScore = 0.0
        var dovishScore = 0.0
        var totalWeight = 0.0
        
        bankSentiments.forEach { sentiment ->
            val weight = weights[sentiment.bank] ?: 0.05
            totalWeight += weight
            
            when (sentiment.direction) {
                SentimentDirection.HAWKISH -> hawkishScore += weight * sentiment.confidence
                SentimentDirection.DOVISH -> dovishScore += weight * sentiment.confidence
                else -> {}
            }
        }
        
        val direction = when {
            hawkishScore > dovishScore * 1.2 -> SentimentDirection.HAWKISH
            dovishScore > hawkishScore * 1.2 -> SentimentDirection.DOVISH
            hawkishScore > 0.1 && dovishScore > 0.1 -> SentimentDirection.MIXED
            else -> SentimentDirection.NEUTRAL
        }
        
        val confidence = if (totalWeight > 0) {
            (hawkishScore + dovishScore) / totalWeight
        } else 0.0
        
        return Pair(direction, confidence.coerceIn(0.0, 1.0))
    }
    
    /**
     * Assess overall macro risk level
     */
    private fun assessMacroRisk(
        upcomingEvents: List<EconomicEvent>,
        recentSurprises: List<EconomicIndicator>,
        sentiment: SentimentDirection
    ): MacroRiskLevel {
        var riskScore = 0
        
        // High-impact events in next 24 hours
        val imminent = upcomingEvents.filter { it.hoursUntil() < 24 }
        riskScore += imminent.count { it.impactLevel == ImpactLevel.CRITICAL } * 3
        riskScore += imminent.count { it.impactLevel == ImpactLevel.HIGH } * 2
        
        // Recent big surprises indicate volatility
        riskScore += recentSurprises.size
        
        // Mixed sentiment = uncertainty = risk
        if (sentiment == SentimentDirection.MIXED) riskScore += 2
        
        return when {
            riskScore >= 8 -> MacroRiskLevel.EXTREME
            riskScore >= 5 -> MacroRiskLevel.HIGH
            riskScore >= 3 -> MacroRiskLevel.ELEVATED
            else -> MacroRiskLevel.LOW
        }
    }
    
    /**
     * Build human-readable summary
     */
    private fun buildSummary(
        overall: SentimentDirection,
        fedSentiment: BankSentiment?,
        upcomingEvents: List<EconomicEvent>
    ): String {
        val parts = mutableListOf<String>()
        
        parts.add("Overall macro sentiment: ${overall.name.lowercase()}")
        
        fedSentiment?.let {
            parts.add("Fed stance: ${it.direction.name.lowercase()} (${(it.confidence * 100).toInt()}% confidence)")
            it.latestRate?.let { rate -> parts.add("Fed rate: ${rate}%") }
        }
        
        if (upcomingEvents.isNotEmpty()) {
            val next = upcomingEvents.first()
            parts.add("Next major event: ${next.title} in ${next.hoursUntil().toInt()}h")
        }
        
        return parts.joinToString(". ")
    }
    
    private fun hashContent(content: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }
    
    /**
     * Get sentiment for specific bank
     */
    suspend fun getBankSentiment(bank: CentralBank): BankSentiment? {
        return bankSentimentCache[bank] ?: buildBankSentiment(bank)?.also {
            bankSentimentCache[bank] = it
        }
    }
    
    /**
     * Cleanup old data
     */
    suspend fun cleanupOldData(olderThanDays: Int = 30) {
        val cutoff = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
        database.sentimentDao().deleteOlderThan(cutoff)
        database.indicatorDao().deleteOlderThan(cutoff)
        database.eventDao().deleteOldReleased(cutoff)
    }
    
    fun shutdown() {
        scope.cancel()
        rssParser.shutdown()
    }
}
