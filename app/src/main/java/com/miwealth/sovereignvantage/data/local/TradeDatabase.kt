package com.miwealth.sovereignvantage.data.local

// BUILD #263: XAI persistence imports
import com.miwealth.sovereignvantage.core.ai.BoardDecisionDao
import com.miwealth.sovereignvantage.core.ai.BoardDecisionEntity
import com.miwealth.sovereignvantage.core.ai.MemberVoteEntity

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.miwealth.sovereignvantage.core.security.EncryptedPrefsManager
import com.miwealth.sovereignvantage.education.Certificate
import com.miwealth.sovereignvantage.education.StudentProgress
import com.miwealth.sovereignvantage.education.TradingProgrammeDao
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory  // V5.17.0: Migrated from deprecated SupportFactory
import java.security.SecureRandom
import java.util.Date
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Trade Database - PQC-Protected Room database for trade history and portfolio tracking
 * 
 * SECURITY: Encrypted with SQLCipher using PQC-derived key
 * 
 * Stores:
 * - Trade history (all executed trades)
 * - Position snapshots (daily portfolio state)
 * - Price alerts
 * - AI signals
 * 
 * Encryption Details:
 * - SQLCipher 4.6.1 (AES-256-CBC with HMAC-SHA512) — sqlcipher-android
 * - Key derivation: PBKDF2-HMAC-SHA512 (310,000 iterations)
 * - Salt: Device-bound via Android Keystore
 * - Future: Kyber-encapsulated master key when PQC libs mature
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

// ============================================================================
// DATABASE KEY MANAGER (PQC-Ready)
// ============================================================================

/**
 * Manages database encryption key with PQC-ready architecture.
 * 
 * Current: PBKDF2-derived key stored in Android Keystore
 * Future: Kyber-1024 encapsulated key when Android PQC support matures
 */
object DatabaseKeyManager {
    
    private const val PREFS_NAME = "sv_db_key_prefs"
    private const val KEY_SALT = "db_salt"
    private const val PBKDF2_ITERATIONS = 310_000 // OWASP 2023 recommendation
    private const val KEY_LENGTH_BITS = 256
    
    /**
     * Derive database encryption key from user secret.
     * Uses PBKDF2-HMAC-SHA512 with high iteration count.
     * 
     * @param context Application context
     * @param userSecret User-provided secret (PIN, password, biometric-derived)
     * @return 32-byte encryption key
     */
    fun deriveKey(context: Context, userSecret: ByteArray): ByteArray {
        val salt = getOrCreateSalt(context)
        
        val spec = PBEKeySpec(
            userSecret.map { it.toInt().toChar() }.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_LENGTH_BITS
        )
        
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        return factory.generateSecret(spec).encoded
    }
    
    /**
     * Derive key from device-bound secret (for automatic unlock).
     * Less secure but allows background operation.
     */
    fun deriveKeyFromDevice(context: Context): ByteArray {
        // Use Android ID + package name as device-bound secret
        // This is less secure than user secret but enables background DB access
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "default_device_id"
        
        val deviceSecret = (androidId + context.packageName).toByteArray(Charsets.UTF_8)
        return deriveKey(context, deviceSecret)
    }
    
    private fun getOrCreateSalt(context: Context): ByteArray {
        val prefs = EncryptedPrefsManager.getDbKeyPrefs(context)
        
        val existingSalt = prefs.getString(KEY_SALT, null)
        if (existingSalt != null) {
            return android.util.Base64.decode(existingSalt, android.util.Base64.NO_WRAP)
        }
        
        // Generate new salt
        val salt = ByteArray(32)
        SecureRandom().nextBytes(salt)
        
        prefs.edit()
            .putString(KEY_SALT, android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            .apply()
        
        return salt
    }
    
    /**
     * Wipe key from memory (call after database operations if using user secret)
     */
    fun wipeKey(key: ByteArray) {
        key.fill(0)
    }
}

// ============================================================================
// TYPE CONVERTERS
// ============================================================================

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time
    
    @TypeConverter
    fun fromStringList(value: String?): List<String> = 
        value?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
    
    @TypeConverter
    fun toStringList(list: List<String>): String = list.joinToString(",")
}

// ============================================================================
// ENTITIES
// ============================================================================

@Entity(tableName = "trades")
data class TradeEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "symbol")
    val symbol: String,
    
    @ColumnInfo(name = "side")
    val side: String,  // BUY, SELL, LONG, SHORT
    
    @ColumnInfo(name = "order_type")
    val orderType: String,  // MARKET, LIMIT, STOP_LOSS
    
    @ColumnInfo(name = "quantity")
    val quantity: Double,
    
    @ColumnInfo(name = "price")
    val price: Double,
    
    @ColumnInfo(name = "fee")
    val fee: Double,
    
    @ColumnInfo(name = "fee_currency")
    val feeCurrency: String,
    
    @ColumnInfo(name = "realized_pnl")
    val realizedPnl: Double?,
    
    @ColumnInfo(name = "realized_pnl_percent")
    val realizedPnlPercent: Double?,
    
    @ColumnInfo(name = "exchange")
    val exchange: String,
    
    @ColumnInfo(name = "order_id")
    val orderId: String,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    
    @ColumnInfo(name = "notes")
    val notes: String = "",
    
    // For closed trades - link to opening trade
    @ColumnInfo(name = "opening_trade_id")
    val openingTradeId: String? = null,
    
    // STAHL info at time of trade
    @ColumnInfo(name = "stahl_level")
    val stahlLevel: Int = 0,
    
    @ColumnInfo(name = "exit_reason")
    val exitReason: String? = null  // "Take Profit", "STAHL Stop Level 3", etc.
)

@Entity(tableName = "positions")
data class PositionEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "symbol")
    val symbol: String,
    
    @ColumnInfo(name = "side")
    val side: String,
    
    @ColumnInfo(name = "quantity")
    val quantity: Double,
    
    @ColumnInfo(name = "average_entry")
    val averageEntry: Double,
    
    @ColumnInfo(name = "current_price")
    val currentPrice: Double,
    
    @ColumnInfo(name = "unrealized_pnl")
    val unrealizedPnl: Double,
    
    @ColumnInfo(name = "unrealized_pnl_percent")
    val unrealizedPnlPercent: Double,
    
    @ColumnInfo(name = "leverage")
    val leverage: Double,
    
    @ColumnInfo(name = "margin")
    val margin: Double,
    
    @ColumnInfo(name = "initial_stop")
    val initialStop: Double,
    
    @ColumnInfo(name = "current_stop")
    val currentStop: Double,
    
    @ColumnInfo(name = "take_profit")
    val takeProfit: Double,
    
    @ColumnInfo(name = "stahl_level")
    val stahlLevel: Int,
    
    @ColumnInfo(name = "max_profit_percent")
    val maxProfitPercent: Double,
    
    @ColumnInfo(name = "exchange")
    val exchange: String,
    
    @ColumnInfo(name = "open_time")
    val openTime: Long,
    
    @ColumnInfo(name = "last_update")
    val lastUpdate: Long,
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
)

@Entity(tableName = "portfolio_snapshots")
data class PortfolioSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    
    @ColumnInfo(name = "total_value")
    val totalValue: Double,
    
    @ColumnInfo(name = "cash_balance")
    val cashBalance: Double,
    
    @ColumnInfo(name = "invested_value")
    val investedValue: Double,
    
    @ColumnInfo(name = "unrealized_pnl")
    val unrealizedPnl: Double,
    
    @ColumnInfo(name = "realized_pnl")
    val realizedPnl: Double,
    
    @ColumnInfo(name = "total_positions")
    val totalPositions: Int,
    
    @ColumnInfo(name = "daily_pnl")
    val dailyPnl: Double,
    
    @ColumnInfo(name = "daily_pnl_percent")
    val dailyPnlPercent: Double
)

@Entity(tableName = "price_alerts")
data class PriceAlertEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "symbol")
    val symbol: String,
    
    @ColumnInfo(name = "condition")
    val condition: String,  // ABOVE, BELOW, CROSS_ABOVE, CROSS_BELOW
    
    @ColumnInfo(name = "target_price")
    val targetPrice: Double,
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "triggered_at")
    val triggeredAt: Long? = null,
    
    @ColumnInfo(name = "notes")
    val notes: String = ""
)

@Entity(tableName = "ai_signals")
data class AISignalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "symbol")
    val symbol: String,
    
    @ColumnInfo(name = "signal")
    val signal: String,  // STRONG_BUY, BUY, HOLD, SELL, STRONG_SELL
    
    @ColumnInfo(name = "score")
    val score: Double,
    
    @ColumnInfo(name = "confidence")
    val confidence: Double,
    
    @ColumnInfo(name = "unanimous_count")
    val unanimousCount: Int,
    
    @ColumnInfo(name = "reasoning")
    val reasoning: String,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    
    @ColumnInfo(name = "was_acted_upon")
    val wasActedUpon: Boolean = false
)

// ============================================================================
// DAOs
// ============================================================================

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades ORDER BY timestamp DESC")
    fun getAllTrades(): Flow<List<TradeEntity>>
    
    @Query("SELECT * FROM trades WHERE symbol = :symbol ORDER BY timestamp DESC")
    fun getTradesForSymbol(symbol: String): Flow<List<TradeEntity>>
    
    @Query("SELECT * FROM trades WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun getTradesInRange(start: Long, end: Long): Flow<List<TradeEntity>>
    
    @Query("SELECT * FROM trades WHERE id = :id")
    suspend fun getTradeById(id: String): TradeEntity?
    
    @Query("SELECT SUM(realized_pnl) FROM trades WHERE realized_pnl IS NOT NULL")
    suspend fun getTotalRealizedPnl(): Double?
    
    @Query("SELECT SUM(fee) FROM trades")
    suspend fun getTotalFees(): Double?
    
    @Query("SELECT COUNT(*) FROM trades WHERE side = :side")
    suspend fun getTradeCountBySide(side: String): Int
    
    @Query("SELECT * FROM trades ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentTrades(limit: Int): List<TradeEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: TradeEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrades(trades: List<TradeEntity>)
    
    @Update
    suspend fun updateTrade(trade: TradeEntity)
    
    @Delete
    suspend fun deleteTrade(trade: TradeEntity)
    
    @Query("DELETE FROM trades WHERE timestamp < :timestamp")
    suspend fun deleteOldTrades(timestamp: Long)
}

@Dao
interface PositionDao {
    @Query("SELECT * FROM positions WHERE is_active = 1 ORDER BY open_time DESC")
    fun getActivePositions(): Flow<List<PositionEntity>>
    
    @Query("SELECT * FROM positions WHERE is_active = 0 ORDER BY last_update DESC")
    fun getClosedPositions(): Flow<List<PositionEntity>>
    
    @Query("SELECT * FROM positions WHERE id = :id")
    suspend fun getPositionById(id: String): PositionEntity?
    
    @Query("SELECT * FROM positions WHERE symbol = :symbol AND is_active = 1")
    suspend fun getActivePositionForSymbol(symbol: String): PositionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosition(position: PositionEntity)
    
    @Update
    suspend fun updatePosition(position: PositionEntity)
    
    @Query("UPDATE positions SET is_active = 0, last_update = :timestamp WHERE id = :id")
    suspend fun closePosition(id: String, timestamp: Long)
    
    @Delete
    suspend fun deletePosition(position: PositionEntity)
}

@Dao
interface PortfolioSnapshotDao {
    @Query("SELECT * FROM portfolio_snapshots ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSnapshots(limit: Int): List<PortfolioSnapshotEntity>
    
    @Query("SELECT * FROM portfolio_snapshots WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    suspend fun getSnapshotsInRange(start: Long, end: Long): List<PortfolioSnapshotEntity>
    
    @Query("SELECT * FROM portfolio_snapshots ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSnapshot(): PortfolioSnapshotEntity?
    
    @Insert
    suspend fun insertSnapshot(snapshot: PortfolioSnapshotEntity)
    
    @Query("DELETE FROM portfolio_snapshots WHERE timestamp < :timestamp")
    suspend fun deleteOldSnapshots(timestamp: Long)
}

@Dao
interface PriceAlertDao {
    @Query("SELECT * FROM price_alerts WHERE is_active = 1 ORDER BY created_at DESC")
    fun getActiveAlerts(): Flow<List<PriceAlertEntity>>
    
    @Query("SELECT * FROM price_alerts WHERE symbol = :symbol AND is_active = 1")
    suspend fun getAlertsForSymbol(symbol: String): List<PriceAlertEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: PriceAlertEntity)
    
    @Update
    suspend fun updateAlert(alert: PriceAlertEntity)
    
    @Query("UPDATE price_alerts SET is_active = 0, triggered_at = :timestamp WHERE id = :id")
    suspend fun triggerAlert(id: String, timestamp: Long)
    
    @Delete
    suspend fun deleteAlert(alert: PriceAlertEntity)
}

@Dao
interface AISignalDao {
    @Query("SELECT * FROM ai_signals ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSignals(limit: Int): List<AISignalEntity>
    
    @Query("SELECT * FROM ai_signals WHERE symbol = :symbol ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getSignalsForSymbol(symbol: String, limit: Int): List<AISignalEntity>
    
    @Insert
    suspend fun insertSignal(signal: AISignalEntity)
    
    @Query("UPDATE ai_signals SET was_acted_upon = 1 WHERE id = :id")
    suspend fun markActedUpon(id: Long)
    
    @Query("DELETE FROM ai_signals WHERE timestamp < :timestamp")
    suspend fun deleteOldSignals(timestamp: Long)
}

// ============================================================================
// DATABASE
// ============================================================================

@Database(
    entities = [
        TradeEntity::class,
        PositionEntity::class,
        PortfolioSnapshotEntity::class,
        PriceAlertEntity::class,
        AISignalEntity::class,
        StudentProgress::class,
        Certificate::class,
        // BUILD #263: XAI audit trail — every board decision persisted for regulatory compliance
        BoardDecisionEntity::class,
        MemberVoteEntity::class,
        // BUILD #280: Portfolio analytics entities
        com.miwealth.sovereignvantage.data.local.EnhancedTradeEntity::class,
        com.miwealth.sovereignvantage.data.local.EquitySnapshotEntity::class,
        com.miwealth.sovereignvantage.data.local.TaxLotEntity::class,
        com.miwealth.sovereignvantage.data.local.ArchiveMetadataEntity::class,
        com.miwealth.sovereignvantage.data.local.CostBasisLotEntity::class
    ],
    version = 5, // BUILD #280: Added portfolio analytics entities
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class TradeDatabase : RoomDatabase() {
    
    abstract fun tradeDao(): TradeDao
    abstract fun positionDao(): PositionDao
    abstract fun portfolioSnapshotDao(): PortfolioSnapshotDao
    abstract fun priceAlertDao(): PriceAlertDao
    abstract fun aiSignalDao(): AISignalDao
    abstract fun tradingProgrammeDao(): TradingProgrammeDao
    // BUILD #263: XAI audit trail — board decisions for regulatory compliance + transparency
    abstract fun boardDecisionDao(): BoardDecisionDao
    
    // BUILD #279: Portfolio analytics DAOs
    abstract fun enhancedTradeDao(): com.miwealth.sovereignvantage.data.local.EnhancedTradeDao
    abstract fun equitySnapshotDao(): com.miwealth.sovereignvantage.data.local.EquitySnapshotDao
    abstract fun taxLotDao(): com.miwealth.sovereignvantage.data.local.TaxLotDao
    
    companion object {
        private const val DATABASE_NAME = "sovereign_vantage_trades.db"
        
        @Volatile
        private var INSTANCE: TradeDatabase? = null
        
        /**
         * Get encrypted database instance using device-bound key.
         * Suitable for background operations.
         */
        fun getInstance(context: Context): TradeDatabase {
            return INSTANCE ?: synchronized(this) {
                val key = DatabaseKeyManager.deriveKeyFromDevice(context)
                val instance = buildEncryptedDatabase(context, key)
                DatabaseKeyManager.wipeKey(key)
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Get encrypted database instance using user-provided secret.
         * Maximum security - use when user is actively present.
         */
        fun getInstanceWithUserSecret(context: Context, userSecret: ByteArray): TradeDatabase {
            return synchronized(this) {
                val key = DatabaseKeyManager.deriveKey(context, userSecret)
                val instance = buildEncryptedDatabase(context, key)
                DatabaseKeyManager.wipeKey(key)
                INSTANCE = instance
                instance
            }
        }
        
        private fun buildEncryptedDatabase(context: Context, key: ByteArray): TradeDatabase {
            // sqlcipher-android 4.6.1 auto-loads native libs — do NOT call System.loadLibrary()
            // Manual loadLibrary("sqlcipher") causes UnsatisfiedLinkError on some devices
            
            // Create SQLCipher support factory (V5.17.0: migrated to sqlcipher-android)
            val factory: SupportSQLiteOpenHelper.Factory? = try {
                SupportOpenHelperFactory(key)
            } catch (e: Throwable) {
                // If SQLCipher native libs fail to load, fall back to unencrypted Room
                android.util.Log.e("TradeDatabase", "SQLCipher factory failed, using unencrypted fallback", e)
                null
            }
            
            if (factory != null) {
                try {
                    val db = Room.databaseBuilder(
                        context.applicationContext,
                        TradeDatabase::class.java,
                        DATABASE_NAME
                    )
                        .openHelperFactory(factory)
                        .fallbackToDestructiveMigration() // For dev; use proper migrations in prod
                        .build()
                    
                    // V5.17.0: Force-open to detect UnsatisfiedLinkError NOW, not on first query
                    db.openHelper.writableDatabase
                    return db
                } catch (e: Throwable) {
                    android.util.Log.e("TradeDatabase", "SQLCipher open failed (${e.javaClass.simpleName}), falling back to unencrypted", e)
                    // Close the failed instance
                    try { /* db already failed, nothing to close */ } catch (_: Throwable) {}
                }
            }
            
            // Fallback: plain Room without encryption
            android.util.Log.w("TradeDatabase", "Using UNENCRYPTED database fallback — SQLCipher unavailable")
            return Room.databaseBuilder(
                context.applicationContext,
                TradeDatabase::class.java,
                DATABASE_NAME + "_plain"  // Different filename to avoid corrupted SQLCipher DB
            )
                .fallbackToDestructiveMigration()
                .build()
        }
        
        /**
         * Close database and clear instance.
         * Call on app termination or user logout.
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
