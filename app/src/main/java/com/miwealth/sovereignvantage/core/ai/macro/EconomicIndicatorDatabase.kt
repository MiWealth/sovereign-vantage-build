package com.miwealth.sovereignvantage.core.ai.macro

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * SOVEREIGN VANTAGE V5.17.0 - ECONOMIC INDICATOR DATABASE
 * Room database for storing macro economic data locally
 * © 2025-2026 MiWealth Pty Ltd
 */

@Database(
    entities = [EconomicIndicator::class, EconomicEvent::class, SentimentAnalysis::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(MacroTypeConverters::class)
abstract class EconomicIndicatorDatabase : RoomDatabase() {
    
    abstract fun indicatorDao(): EconomicIndicatorDao
    abstract fun eventDao(): EconomicEventDao
    abstract fun sentimentDao(): SentimentAnalysisDao
    
    companion object {
        @Volatile
        private var INSTANCE: EconomicIndicatorDatabase? = null
        
        fun getInstance(context: Context): EconomicIndicatorDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    EconomicIndicatorDatabase::class.java,
                    "economic_indicators.db"
                ).fallbackToDestructiveMigration()
                 .build()
                 .also { INSTANCE = it }
            }
        }
    }
}

// ============================================================================
// TYPE CONVERTERS
// ============================================================================

class MacroTypeConverters {
    @TypeConverter
    fun fromCentralBank(value: CentralBank?): String? = value?.name
    
    @TypeConverter
    fun toCentralBank(value: String?): CentralBank? = value?.let { 
        try { CentralBank.valueOf(it) } catch (e: Exception) { null }
    }
    
    @TypeConverter
    fun fromIndicatorType(value: IndicatorType?): String? = value?.name
    
    @TypeConverter
    fun toIndicatorType(value: String?): IndicatorType? = value?.let {
        try { IndicatorType.valueOf(it) } catch (e: Exception) { null }
    }
    
    @TypeConverter
    fun fromImpactLevel(value: ImpactLevel): String = value.name
    
    @TypeConverter
    fun toImpactLevel(value: String): ImpactLevel = ImpactLevel.valueOf(value)
    
    @TypeConverter
    fun fromEventStatus(value: EventStatus): String = value.name
    
    @TypeConverter
    fun toEventStatus(value: String): EventStatus = EventStatus.valueOf(value)
    
    @TypeConverter
    fun fromSentimentDirection(value: SentimentDirection): String = value.name
    
    @TypeConverter
    fun toSentimentDirection(value: String): SentimentDirection = 
        SentimentDirection.valueOf(value)
}

// ============================================================================
// DAOs
// ============================================================================

@Dao
interface EconomicIndicatorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(indicator: EconomicIndicator)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(indicators: List<EconomicIndicator>)
    
    @Query("SELECT * FROM economic_indicators WHERE central_bank = :bank ORDER BY release_date DESC LIMIT :limit")
    suspend fun getByBank(bank: CentralBank, limit: Int = 50): List<EconomicIndicator>
    
    @Query("SELECT * FROM economic_indicators WHERE indicator_type = :type ORDER BY release_date DESC LIMIT :limit")
    suspend fun getByType(type: IndicatorType, limit: Int = 50): List<EconomicIndicator>
    
    @Query("SELECT * FROM economic_indicators WHERE central_bank = :bank AND indicator_type = :type ORDER BY release_date DESC LIMIT 1")
    suspend fun getLatest(bank: CentralBank, type: IndicatorType): EconomicIndicator?
    
    @Query("SELECT * FROM economic_indicators WHERE release_date > :since ORDER BY release_date DESC")
    suspend fun getRecent(since: Long): List<EconomicIndicator>
    
    @Query("SELECT * FROM economic_indicators WHERE surprise IS NOT NULL AND release_date > :since ORDER BY ABS(surprise) DESC LIMIT :limit")
    suspend fun getBiggestSurprises(since: Long, limit: Int = 10): List<EconomicIndicator>
    
    @Query("SELECT * FROM economic_indicators ORDER BY release_date DESC")
    fun observeAll(): Flow<List<EconomicIndicator>>
    
    @Query("DELETE FROM economic_indicators WHERE release_date < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface EconomicEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EconomicEvent)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EconomicEvent>)
    
    @Update
    suspend fun update(event: EconomicEvent)
    
    @Query("SELECT * FROM economic_events WHERE scheduled_time > :now ORDER BY scheduled_time ASC LIMIT :limit")
    suspend fun getUpcoming(now: Long = System.currentTimeMillis(), limit: Int = 20): List<EconomicEvent>
    
    @Query("SELECT * FROM economic_events WHERE scheduled_time > :now AND impact_level IN ('HIGH', 'CRITICAL') ORDER BY scheduled_time ASC")
    suspend fun getUpcomingHighImpact(now: Long = System.currentTimeMillis()): List<EconomicEvent>
    
    @Query("SELECT * FROM economic_events WHERE central_bank = :bank AND scheduled_time > :now ORDER BY scheduled_time ASC")
    suspend fun getUpcomingByBank(bank: CentralBank, now: Long = System.currentTimeMillis()): List<EconomicEvent>
    
    @Query("SELECT * FROM economic_events WHERE scheduled_time BETWEEN :start AND :end ORDER BY scheduled_time ASC")
    suspend fun getInRange(start: Long, end: Long): List<EconomicEvent>
    
    @Query("SELECT * FROM economic_events ORDER BY scheduled_time DESC")
    fun observeAll(): Flow<List<EconomicEvent>>
    
    @Query("DELETE FROM economic_events WHERE scheduled_time < :before AND status = 'RELEASED'")
    suspend fun deleteOldReleased(before: Long)
}

@Dao
interface SentimentAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(analysis: SentimentAnalysis)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(analyses: List<SentimentAnalysis>)
    
    @Query("SELECT * FROM sentiment_analysis WHERE central_bank = :bank ORDER BY analyzed_at DESC LIMIT :limit")
    suspend fun getByBank(bank: CentralBank, limit: Int = 50): List<SentimentAnalysis>
    
    @Query("SELECT * FROM sentiment_analysis WHERE analyzed_at > :since ORDER BY analyzed_at DESC")
    suspend fun getRecent(since: Long): List<SentimentAnalysis>
    
    @Query("SELECT * FROM sentiment_analysis WHERE content_hash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): SentimentAnalysis?
    
    @Query("""
        SELECT direction, COUNT(*) as count, AVG(confidence) as avgConfidence 
        FROM sentiment_analysis 
        WHERE central_bank = :bank AND analyzed_at > :since 
        GROUP BY direction
    """)
    suspend fun getSentimentDistribution(bank: CentralBank, since: Long): List<SentimentCount>
    
    @Query("SELECT AVG(hawkish_score) FROM sentiment_analysis WHERE central_bank = :bank AND analyzed_at > :since")
    suspend fun getAverageHawkishScore(bank: CentralBank, since: Long): Double?
    
    @Query("DELETE FROM sentiment_analysis WHERE analyzed_at < :before")
    suspend fun deleteOlderThan(before: Long)
}

data class SentimentCount(
    val direction: SentimentDirection,
    val count: Int,
    val avgConfidence: Double
)
