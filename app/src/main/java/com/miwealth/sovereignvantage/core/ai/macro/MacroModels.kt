package com.miwealth.sovereignvantage.core.ai.macro

import androidx.room.*
import java.util.UUID

/**
 * SOVEREIGN VANTAGE V5.17.0 - MACRO SENTIMENT MODELS
 * © 2025-2026 MiWealth Pty Ltd
 */

// ============================================================================
// ENUMS
// ============================================================================

enum class CentralBank(val code: String, val currency: String, val region: String) {
    FED("FED", "USD", "United States"),
    ECB("ECB", "EUR", "Eurozone"),
    RBA("RBA", "AUD", "Australia"),
    BOE("BOE", "GBP", "United Kingdom"),
    BOJ("BOJ", "JPY", "Japan"),
    PBOC("PBOC", "CNY", "China"),
    BOC("BOC", "CAD", "Canada"),
    SNB("SNB", "CHF", "Switzerland"),
    RBNZ("RBNZ", "NZD", "New Zealand")
}

enum class IndicatorType(val displayName: String) {
    CPI("CPI"), CORE_CPI("Core CPI"), PPI("PPI"), PCE("PCE"),
    GDP("GDP"), GDP_GROWTH("GDP Growth"),
    UNEMPLOYMENT("Unemployment"), NFP("NFP"), JOBLESS_CLAIMS("Jobless Claims"),
    INTEREST_RATE("Interest Rate"), FUNDS_RATE("Fed Funds Rate"),
    PMI("PMI"), ISM("ISM"), RETAIL_SALES("Retail Sales"),
    CONSUMER_CONFIDENCE("Consumer Confidence"), TRADE_BALANCE("Trade Balance")
}

enum class SentimentDirection { HAWKISH, DOVISH, NEUTRAL, MIXED }
enum class ImpactLevel { LOW, MEDIUM, HIGH, CRITICAL }
enum class EventStatus { SCHEDULED, IN_PROGRESS, RELEASED, REVISED, CANCELLED }
enum class SurpriseDirection { BEAT, IN_LINE, MISS }
enum class RateDirection { HIKE, CUT, HOLD }
enum class MacroRiskLevel { LOW, ELEVATED, HIGH, EXTREME }

// ============================================================================
// ROOM ENTITIES
// ============================================================================

@Entity(tableName = "economic_indicators", indices = [
    Index(value = ["central_bank", "indicator_type"]),
    Index(value = ["release_date"])
])
data class EconomicIndicator(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "central_bank") val centralBank: CentralBank,
    @ColumnInfo(name = "indicator_type") val indicatorType: IndicatorType,
    @ColumnInfo(name = "actual_value") val actualValue: Double?,
    @ColumnInfo(name = "forecast_value") val forecastValue: Double?,
    @ColumnInfo(name = "previous_value") val previousValue: Double?,
    @ColumnInfo(name = "release_date") val releaseDate: Long,
    @ColumnInfo(name = "period") val period: String,
    @ColumnInfo(name = "unit") val unit: String,
    @ColumnInfo(name = "surprise") val surprise: Double? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "source") val source: String = ""
) {
    fun getSurpriseDirection(): SurpriseDirection {
        val diff = surprise ?: return SurpriseDirection.IN_LINE
        return when { diff > 0.1 -> SurpriseDirection.BEAT; diff < -0.1 -> SurpriseDirection.MISS; else -> SurpriseDirection.IN_LINE }
    }
    
    fun getSentimentImplication(): SentimentDirection {
        val dir = getSurpriseDirection()
        return when (indicatorType) {
            IndicatorType.CPI, IndicatorType.CORE_CPI, IndicatorType.PPI, IndicatorType.PCE ->
                when (dir) { SurpriseDirection.BEAT -> SentimentDirection.HAWKISH; SurpriseDirection.MISS -> SentimentDirection.DOVISH; else -> SentimentDirection.NEUTRAL }
            IndicatorType.UNEMPLOYMENT, IndicatorType.JOBLESS_CLAIMS ->
                when (dir) { SurpriseDirection.BEAT -> SentimentDirection.DOVISH; SurpriseDirection.MISS -> SentimentDirection.HAWKISH; else -> SentimentDirection.NEUTRAL }
            IndicatorType.GDP, IndicatorType.GDP_GROWTH, IndicatorType.NFP, IndicatorType.PMI, IndicatorType.ISM, IndicatorType.RETAIL_SALES ->
                when (dir) { SurpriseDirection.BEAT -> SentimentDirection.HAWKISH; SurpriseDirection.MISS -> SentimentDirection.DOVISH; else -> SentimentDirection.NEUTRAL }
            else -> SentimentDirection.NEUTRAL
        }
    }
}

@Entity(tableName = "economic_events", indices = [Index(value = ["scheduled_time"]), Index(value = ["impact_level"])])
data class EconomicEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    @ColumnInfo(name = "central_bank") val centralBank: CentralBank?,
    @ColumnInfo(name = "indicator_type") val indicatorType: IndicatorType?,
    @ColumnInfo(name = "scheduled_time") val scheduledTime: Long,
    @ColumnInfo(name = "impact_level") val impactLevel: ImpactLevel,
    val status: EventStatus = EventStatus.SCHEDULED,
    val forecast: String? = null,
    val previous: String? = null,
    val actual: String? = null,
    @ColumnInfo(name = "currency_impact") val currencyImpact: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
) {
    fun isUpcoming(): Boolean = scheduledTime > System.currentTimeMillis()
    fun hoursUntil(): Double = (scheduledTime - System.currentTimeMillis()).toDouble() / 3600000
}

@Entity(tableName = "sentiment_analysis", indices = [Index(value = ["analyzed_at"]), Index(value = ["central_bank"])])
data class SentimentAnalysis(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    val title: String,
    @ColumnInfo(name = "content_snippet") val contentSnippet: String,
    @ColumnInfo(name = "central_bank") val centralBank: CentralBank?,
    val direction: SentimentDirection,
    val confidence: Double,
    @ColumnInfo(name = "hawkish_score") val hawkishScore: Double,
    @ColumnInfo(name = "dovish_score") val dovishScore: Double,
    @ColumnInfo(name = "keywords_matched") val keywordsMatched: String,
    @ColumnInfo(name = "analyzed_at") val analyzedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "published_at") val publishedAt: Long? = null,
    @ColumnInfo(name = "content_hash") val contentHash: String? = null  // V5.17.0: Used by SentimentAnalysisDao.findByHash()
)

// ============================================================================
// DATA CLASSES
// ============================================================================

data class MacroSentimentScore(
    val centralBank: CentralBank,
    val direction: SentimentDirection,
    val score: Double,
    val confidence: Double,
    val dataPoints: Int,
    val lastUpdated: Long,
    val rateExpectation: RateExpectation
)

data class RateExpectation(
    val currentRate: Double,
    val expectedDirection: RateDirection,
    val probability: Double,
    val expectedMagnitude: Int
) {
    companion object {
        val HIKE_LIKELY = RateExpectation(0.0, RateDirection.HIKE, 0.7, 25)
        val CUT_LIKELY = RateExpectation(0.0, RateDirection.CUT, 0.7, 25)
        val HOLD_LIKELY = RateExpectation(0.0, RateDirection.HOLD, 0.7, 0)
        val UNCERTAIN = RateExpectation(0.0, RateDirection.HOLD, 0.3, 0)
    }
}

data class RSSFeedItem(
    val title: String,
    val link: String,
    val description: String,
    val pubDate: Long?,
    val source: String,
    val centralBank: CentralBank?
)

data class RSSFeedConfig(
    val url: String,
    val name: String,
    val centralBank: CentralBank?,
    val refreshIntervalMs: Long = 1800000,
    val enabled: Boolean = true
)

data class MacroContext(
    val timestamp: Long,
    val globalSentiment: SentimentDirection,
    val globalScore: Double,
    val bankSentiments: Map<CentralBank, MacroSentimentScore>,
    val upcomingHighImpactEvents: List<EconomicEvent>,
    val recentSurprises: List<EconomicIndicator>,
    val riskLevel: MacroRiskLevel,
    val narrative: String
)

/**
 * Aggregated sentiment for a specific central bank.
 * Built by MacroSentimentAnalyzer from headline analysis.
 */
data class BankSentiment(
    val bank: CentralBank,
    val direction: SentimentDirection,
    val confidence: Double,
    val latestRate: Double,
    val rateChangeExpectation: RateExpectation,
    val recentHeadlines: List<String> = emptyList()
)

object SentimentKeywords {
    val HAWKISH = setOf(
        "hike", "hiking", "raise", "raising", "tighten", "tightening", "restrictive",
        "inflation concerns", "price pressures", "overheating", "above target",
        "persistent inflation", "wage pressures", "strong labor", "robust growth",
        "quantitative tightening", "QT", "higher for longer", "more work to do",
        "vigilant", "committed to target", "data dependent"
    )
    val DOVISH = setOf(
        "cut", "cutting", "lower", "lowering", "ease", "easing", "accommodative",
        "support growth", "downside risks", "cooling", "softening", "below target",
        "disinflation", "recession", "slowdown", "weakness", "job losses",
        "quantitative easing", "QE", "pivot", "pause", "patient", "flexible"
    )
    val NEUTRAL = setOf(
        "unchanged", "hold", "maintain", "steady", "stable", "balanced",
        "wait and see", "monitoring", "assessing", "on track", "as expected"
    )
}

// MacroTypeConverters defined in EconomicIndicatorDatabase.kt
