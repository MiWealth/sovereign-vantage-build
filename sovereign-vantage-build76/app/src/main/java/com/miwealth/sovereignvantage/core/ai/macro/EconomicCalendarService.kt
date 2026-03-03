package com.miwealth.sovereignvantage.core.ai.macro

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * SOVEREIGN VANTAGE V5.17.0 - ECONOMIC CALENDAR SERVICE
 * 
 * Background service for tracking economic events and updating sentiment.
 * Uses WorkManager for reliable periodic updates.
 * 
 * © 2025-2026 MiWealth Pty Ltd
 */
class EconomicCalendarService(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        const val WORK_NAME = "economic_calendar_sync"
        private const val UPDATE_INTERVAL_MINUTES = 30L
        
        /**
         * Schedule periodic updates
         */
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val request = PeriodicWorkRequestBuilder<EconomicCalendarService>(
                UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .addTag("macro_sentiment")
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
        
        /**
         * Request immediate refresh
         */
        fun requestImmediateSync(context: Context) {
            val request = OneTimeWorkRequestBuilder<EconomicCalendarService>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag("macro_sentiment_immediate")
                .build()
            
            WorkManager.getInstance(context).enqueue(request)
        }
        
        /**
         * Cancel all scheduled work
         */
        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val analyzer = MacroSentimentAnalyzer.getInstance(context)
            
            // Refresh macro context (fetches RSS, analyzes sentiment)
            analyzer.refreshMacroContext()
            
            // Cleanup old data periodically
            if (runAttemptCount == 0) {
                analyzer.cleanupOldData(30)
            }
            
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}

/**
 * Bootstrap economic calendar with known scheduled events
 */
object EconomicCalendarBootstrap {
    
    /**
     * Add known recurring events (FOMC meetings, RBA meetings, etc.)
     */
    suspend fun seedRecurringEvents(context: Context) {
        val database = EconomicIndicatorDatabase.getInstance(context)
        val now = System.currentTimeMillis()
        
        // This would be populated from a reliable calendar API in production
        // For now, seed with placeholder structure
        val events = listOf(
            // Example: Next FOMC meeting (placeholder - real dates from Fed calendar)
            EconomicEvent(
                title = "FOMC Rate Decision",
                description = "Federal Reserve interest rate announcement",
                centralBank = CentralBank.FED,
                indicatorType = IndicatorType.INTEREST_RATE,
                scheduledTime = now + (7 * 24 * 60 * 60 * 1000L), // 7 days out
                impactLevel = ImpactLevel.CRITICAL,
                currencyImpact = "USD"
            ),
            // Example: RBA meeting
            EconomicEvent(
                title = "RBA Rate Decision",
                description = "Reserve Bank of Australia interest rate announcement",
                centralBank = CentralBank.RBA,
                indicatorType = IndicatorType.INTEREST_RATE,
                scheduledTime = now + (14 * 24 * 60 * 60 * 1000L), // 14 days out
                impactLevel = ImpactLevel.HIGH,
                currencyImpact = "AUD"
            )
        )
        
        database.eventDao().insertAll(events)
    }
    
    /**
     * Seed historical interest rates for context
     */
    suspend fun seedHistoricalRates(context: Context) {
        val database = EconomicIndicatorDatabase.getInstance(context)
        val now = System.currentTimeMillis()
        
        // Current rates as of Jan 2026 (approximate)
        val rates = listOf(
            EconomicIndicator(
                centralBank = CentralBank.FED,
                indicatorType = IndicatorType.INTEREST_RATE,
                actualValue = 4.5,
                forecastValue = null,
                previousValue = 4.75,
                releaseDate = now,
                period = "Jan 2026",
                unit = "%",
                source = "Bootstrap"
            ),
            EconomicIndicator(
                centralBank = CentralBank.RBA,
                indicatorType = IndicatorType.INTEREST_RATE,
                actualValue = 4.1,
                forecastValue = null,
                previousValue = 4.35,
                releaseDate = now,
                period = "Jan 2026",
                unit = "%",
                source = "Bootstrap"
            ),
            EconomicIndicator(
                centralBank = CentralBank.ECB,
                indicatorType = IndicatorType.INTEREST_RATE,
                actualValue = 3.0,
                forecastValue = null,
                previousValue = 3.25,
                releaseDate = now,
                period = "Jan 2026",
                unit = "%",
                source = "Bootstrap"
            )
        )
        
        database.indicatorDao().insertAll(rates)
    }
}
