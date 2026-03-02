package com.miwealth.sovereignvantage.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * ENHANCED TRADE ENTITIES
 * 
 * Extended entities for comprehensive trade tracking including:
 * - Full cost basis tracking for tax purposes
 * - AI Board reasoning storage (all 8 experts)
 * - Tax lot management (FIFO/LIFO/Specific ID)
 * - STAHL stop progression history
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */

// ============================================================================
// ENHANCED TRADE WITH FULL COST BASIS
// ============================================================================

@Entity(tableName = "enhanced_trades")
data class EnhancedTradeEntity(
    @PrimaryKey
    val id: String,
    
    // Basic trade info
    @ColumnInfo(name = "symbol") val symbol: String,
    @ColumnInfo(name = "base_asset") val baseAsset: String,      // BTC in BTC/USD
    @ColumnInfo(name = "quote_asset") val quoteAsset: String,    // USD in BTC/USD
    @ColumnInfo(name = "side") val side: String,                 // BUY, SELL, LONG, SHORT
    @ColumnInfo(name = "order_type") val orderType: String,      // MARKET, LIMIT, STOP_LOSS
    
    // Quantities and prices
    @ColumnInfo(name = "quantity") val quantity: Double,
    @ColumnInfo(name = "executed_price") val executedPrice: Double,
    @ColumnInfo(name = "requested_price") val requestedPrice: Double?,
    @ColumnInfo(name = "quote_quantity") val quoteQuantity: Double,  // quantity × price
    
    // Cost basis (CRITICAL for taxes)
    @ColumnInfo(name = "cost_basis") val costBasis: Double,          // Total cost including fees
    @ColumnInfo(name = "cost_basis_per_unit") val costBasisPerUnit: Double,
    @ColumnInfo(name = "acquisition_date") val acquisitionDate: Long,
    @ColumnInfo(name = "disposal_date") val disposalDate: Long?,
    @ColumnInfo(name = "holding_period_days") val holdingPeriodDays: Int?,
    @ColumnInfo(name = "is_long_term") val isLongTerm: Boolean?,     // > 1 year for tax purposes
    
    // Fees breakdown
    @ColumnInfo(name = "trading_fee") val tradingFee: Double,
    @ColumnInfo(name = "trading_fee_currency") val tradingFeeCurrency: String,
    @ColumnInfo(name = "network_fee") val networkFee: Double?,
    @ColumnInfo(name = "total_fees") val totalFees: Double,
    
    // P&L tracking
    @ColumnInfo(name = "realized_pnl") val realizedPnl: Double?,
    @ColumnInfo(name = "realized_pnl_percent") val realizedPnlPercent: Double?,
    @ColumnInfo(name = "proceeds") val proceeds: Double?,            // For sell orders
    @ColumnInfo(name = "gain_loss") val gainLoss: Double?,           // proceeds - cost_basis
    
    // Tax lot linkage
    @ColumnInfo(name = "tax_lot_id") val taxLotId: String?,
    @ColumnInfo(name = "opening_trade_id") val openingTradeId: String?,
    @ColumnInfo(name = "closing_trade_ids") val closingTradeIds: String?,  // Comma-separated
    @ColumnInfo(name = "remaining_quantity") val remainingQuantity: Double,
    @ColumnInfo(name = "is_fully_closed") val isFullyClosed: Boolean,
    
    // Exchange info
    @ColumnInfo(name = "exchange") val exchange: String,
    @ColumnInfo(name = "exchange_order_id") val exchangeOrderId: String,
    @ColumnInfo(name = "client_order_id") val clientOrderId: String?,
    
    // STAHL tracking
    @ColumnInfo(name = "entry_stop_loss") val entryStopLoss: Double?,
    @ColumnInfo(name = "entry_take_profit") val entryTakeProfit: Double?,
    @ColumnInfo(name = "exit_stop_level") val exitStopLevel: Int?,
    @ColumnInfo(name = "exit_reason") val exitReason: String?,
    @ColumnInfo(name = "max_profit_during_trade") val maxProfitDuringTrade: Double?,
    
    // AI reasoning reference
    @ColumnInfo(name = "ai_decision_id") val aiDecisionId: Long?,
    @ColumnInfo(name = "signal_confidence") val signalConfidence: Int?,
    @ColumnInfo(name = "board_consensus_score") val boardConsensusScore: Double?,
    
    // Timestamps
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    
    // User notes
    @ColumnInfo(name = "notes") val notes: String?,
    @ColumnInfo(name = "tags") val tags: String?  // Comma-separated tags
)

// ============================================================================
// AI BOARD DECISION RECORD
// ============================================================================

@Entity(tableName = "ai_board_decisions")
data class AIBoardDecisionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Decision summary
    @ColumnInfo(name = "symbol") val symbol: String,
    @ColumnInfo(name = "timeframe") val timeframe: String,
    @ColumnInfo(name = "final_decision") val finalDecision: String,  // STRONG_BUY, BUY, HOLD, SELL, STRONG_SELL
    @ColumnInfo(name = "weighted_score") val weightedScore: Double,
    @ColumnInfo(name = "confidence") val confidence: Double,
    @ColumnInfo(name = "unanimous_count") val unanimousCount: Int,
    @ColumnInfo(name = "synthesis") val synthesis: String,
    
    // Individual expert votes (JSON compressed)
    @ColumnInfo(name = "expert_votes_json") val expertVotesJson: String,
    
    // Market context at decision time
    @ColumnInfo(name = "price_at_decision") val priceAtDecision: Double,
    @ColumnInfo(name = "volume_24h") val volume24h: Double?,
    @ColumnInfo(name = "volatility") val volatility: Double?,
    
    // Key indicators that drove decision
    @ColumnInfo(name = "key_indicators_json") val keyIndicatorsJson: String,
    
    // Outcome tracking
    @ColumnInfo(name = "was_acted_upon") val wasActedUpon: Boolean = false,
    @ColumnInfo(name = "trade_id") val tradeId: String?,
    @ColumnInfo(name = "outcome_pnl") val outcomePnl: Double?,
    @ColumnInfo(name = "outcome_recorded_at") val outcomeRecordedAt: Long?,
    
    // Timestamp
    @ColumnInfo(name = "timestamp") val timestamp: Long
)

// ============================================================================
// TAX LOT ENTITY (FIFO/LIFO/Specific ID)
// ============================================================================

@Entity(tableName = "tax_lots")
data class TaxLotEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "symbol") val symbol: String,
    @ColumnInfo(name = "base_asset") val baseAsset: String,
    
    // Acquisition info
    @ColumnInfo(name = "acquisition_date") val acquisitionDate: Long,
    @ColumnInfo(name = "acquisition_price") val acquisitionPrice: Double,
    @ColumnInfo(name = "acquisition_quantity") val acquisitionQuantity: Double,
    @ColumnInfo(name = "acquisition_cost_basis") val acquisitionCostBasis: Double,
    @ColumnInfo(name = "acquisition_trade_id") val acquisitionTradeId: String,
    
    // Current state
    @ColumnInfo(name = "remaining_quantity") val remainingQuantity: Double,
    @ColumnInfo(name = "remaining_cost_basis") val remainingCostBasis: Double,
    @ColumnInfo(name = "cost_per_unit") val costPerUnit: Double,
    
    // Disposal tracking
    @ColumnInfo(name = "disposal_records_json") val disposalRecordsJson: String?,  // JSON array
    @ColumnInfo(name = "total_disposed_quantity") val totalDisposedQuantity: Double,
    @ColumnInfo(name = "total_proceeds") val totalProceeds: Double,
    @ColumnInfo(name = "total_gain_loss") val totalGainLoss: Double,
    
    // Status
    @ColumnInfo(name = "is_fully_disposed") val isFullyDisposed: Boolean,
    @ColumnInfo(name = "is_long_term") val isLongTerm: Boolean,  // > 365 days from acquisition
    
    // Exchange
    @ColumnInfo(name = "exchange") val exchange: String,
    
    // Timestamps
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

// ============================================================================
// STAHL STOP HISTORY
// ============================================================================

@Entity(tableName = "stahl_history")
data class StahlHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "trade_id") val tradeId: String,
    @ColumnInfo(name = "symbol") val symbol: String,
    
    @ColumnInfo(name = "stahl_level") val stahlLevel: Int,
    @ColumnInfo(name = "stop_price") val stopPrice: Double,
    @ColumnInfo(name = "price_at_update") val priceAtUpdate: Double,
    @ColumnInfo(name = "profit_percent") val profitPercent: Double,
    @ColumnInfo(name = "is_breakeven") val isBreakeven: Boolean,
    
    @ColumnInfo(name = "timestamp") val timestamp: Long
)

// ============================================================================
// EQUITY CURVE SNAPSHOTS
// ============================================================================

@Entity(tableName = "equity_snapshots")
data class EquitySnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "snapshot_type") val snapshotType: String,  // HOURLY, DAILY, WEEKLY, MONTHLY
    
    // Portfolio values
    @ColumnInfo(name = "total_equity") val totalEquity: Double,
    @ColumnInfo(name = "cash_balance") val cashBalance: Double,
    @ColumnInfo(name = "positions_value") val positionsValue: Double,
    @ColumnInfo(name = "unrealized_pnl") val unrealizedPnl: Double,
    
    // Period performance
    @ColumnInfo(name = "period_pnl") val periodPnl: Double,
    @ColumnInfo(name = "period_pnl_percent") val periodPnlPercent: Double,
    @ColumnInfo(name = "period_trades") val periodTrades: Int,
    @ColumnInfo(name = "period_wins") val periodWins: Int,
    @ColumnInfo(name = "period_losses") val periodLosses: Int,
    
    // Cumulative metrics
    @ColumnInfo(name = "cumulative_pnl") val cumulativePnl: Double,
    @ColumnInfo(name = "cumulative_pnl_percent") val cumulativePnlPercent: Double,
    @ColumnInfo(name = "high_water_mark") val highWaterMark: Double,
    @ColumnInfo(name = "drawdown") val drawdown: Double,
    @ColumnInfo(name = "drawdown_percent") val drawdownPercent: Double,
    
    // Risk metrics at snapshot
    @ColumnInfo(name = "sharpe_ratio") val sharpeRatio: Double?,
    @ColumnInfo(name = "sortino_ratio") val sortinoRatio: Double?,
    @ColumnInfo(name = "win_rate") val winRate: Double?,
    @ColumnInfo(name = "profit_factor") val profitFactor: Double?,
    @ColumnInfo(name = "max_drawdown") val maxDrawdown: Double?,
    
    // Asset allocation JSON
    @ColumnInfo(name = "allocation_json") val allocationJson: String?,
    
    @ColumnInfo(name = "timestamp") val timestamp: Long
)

// ============================================================================
// ARCHIVE METADATA
// ============================================================================

@Entity(tableName = "archive_metadata")
data class ArchiveMetadataEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "archive_type") val archiveType: String,  // TRADES, DECISIONS, TAX_LOTS
    @ColumnInfo(name = "period_start") val periodStart: Long,
    @ColumnInfo(name = "period_end") val periodEnd: Long,
    @ColumnInfo(name = "record_count") val recordCount: Int,
    
    @ColumnInfo(name = "storage_location") val storageLocation: String,  // LOCAL, GOOGLE_DRIVE, ICLOUD
    @ColumnInfo(name = "cloud_file_id") val cloudFileId: String?,
    @ColumnInfo(name = "cloud_file_path") val cloudFilePath: String?,
    
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long,
    @ColumnInfo(name = "is_compressed") val isCompressed: Boolean,
    @ColumnInfo(name = "is_encrypted") val isEncrypted: Boolean,
    @ColumnInfo(name = "checksum") val checksum: String,
    
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_verified_at") val lastVerifiedAt: Long?
)

// ============================================================================
// ENHANCED DAOs
// ============================================================================

@Dao
interface EnhancedTradeDao {
    // Queries
    @Query("SELECT * FROM enhanced_trades ORDER BY created_at DESC")
    fun getAllTrades(): Flow<List<EnhancedTradeEntity>>
    
    @Query("SELECT * FROM enhanced_trades WHERE symbol = :symbol ORDER BY created_at DESC")
    fun getTradesForSymbol(symbol: String): Flow<List<EnhancedTradeEntity>>
    
    @Query("SELECT * FROM enhanced_trades WHERE created_at BETWEEN :start AND :end ORDER BY created_at DESC")
    fun getTradesInRange(start: Long, end: Long): Flow<List<EnhancedTradeEntity>>
    
    @Query("SELECT * FROM enhanced_trades WHERE is_fully_closed = 0 ORDER BY created_at DESC")
    fun getOpenTaxLots(): Flow<List<EnhancedTradeEntity>>
    
    @Query("SELECT * FROM enhanced_trades WHERE side IN ('BUY', 'LONG') AND remaining_quantity > 0 AND symbol = :symbol ORDER BY created_at ASC")
    suspend fun getOpenLotsForSymbolFIFO(symbol: String): List<EnhancedTradeEntity>
    
    @Query("SELECT * FROM enhanced_trades WHERE side IN ('BUY', 'LONG') AND remaining_quantity > 0 AND symbol = :symbol ORDER BY created_at DESC")
    suspend fun getOpenLotsForSymbolLIFO(symbol: String): List<EnhancedTradeEntity>
    
    @Query("SELECT * FROM enhanced_trades WHERE id = :id")
    suspend fun getTradeById(id: String): EnhancedTradeEntity?
    
    @Query("SELECT SUM(realized_pnl) FROM enhanced_trades WHERE realized_pnl IS NOT NULL AND disposal_date BETWEEN :start AND :end")
    suspend fun getRealizedPnlInRange(start: Long, end: Long): Double?
    
    @Query("SELECT SUM(gain_loss) FROM enhanced_trades WHERE gain_loss IS NOT NULL AND is_long_term = :isLongTerm AND disposal_date BETWEEN :start AND :end")
    suspend fun getGainLossByTermInRange(isLongTerm: Boolean, start: Long, end: Long): Double?
    
    @Query("SELECT COUNT(*) FROM enhanced_trades WHERE realized_pnl > 0 AND disposal_date BETWEEN :start AND :end")
    suspend fun getWinCountInRange(start: Long, end: Long): Int
    
    @Query("SELECT COUNT(*) FROM enhanced_trades WHERE realized_pnl < 0 AND disposal_date BETWEEN :start AND :end")
    suspend fun getLossCountInRange(start: Long, end: Long): Int
    
    @Query("SELECT * FROM enhanced_trades WHERE created_at < :cutoff ORDER BY created_at ASC LIMIT :limit")
    suspend fun getTradesForArchive(cutoff: Long, limit: Int): List<EnhancedTradeEntity>
    
    // Mutations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: EnhancedTradeEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrades(trades: List<EnhancedTradeEntity>)
    
    @Update
    suspend fun updateTrade(trade: EnhancedTradeEntity)
    
    @Query("UPDATE enhanced_trades SET remaining_quantity = :remaining, closing_trade_ids = :closingIds, is_fully_closed = :isClosed, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateTaxLotDisposal(id: String, remaining: Double, closingIds: String, isClosed: Boolean, updatedAt: Long)
    
    @Delete
    suspend fun deleteTrade(trade: EnhancedTradeEntity)
    
    @Query("DELETE FROM enhanced_trades WHERE created_at < :cutoff")
    suspend fun deleteTradesOlderThan(cutoff: Long): Int
}

@Dao
interface AIBoardDecisionDao {
    @Query("SELECT * FROM ai_board_decisions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentDecisions(limit: Int): List<AIBoardDecisionEntity>
    
    @Query("SELECT * FROM ai_board_decisions WHERE symbol = :symbol ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getDecisionsForSymbol(symbol: String, limit: Int): List<AIBoardDecisionEntity>
    
    @Query("SELECT * FROM ai_board_decisions WHERE id = :id")
    suspend fun getDecisionById(id: Long): AIBoardDecisionEntity?
    
    @Query("SELECT * FROM ai_board_decisions WHERE trade_id = :tradeId")
    suspend fun getDecisionForTrade(tradeId: String): AIBoardDecisionEntity?
    
    @Insert
    suspend fun insertDecision(decision: AIBoardDecisionEntity): Long
    
    @Query("UPDATE ai_board_decisions SET was_acted_upon = 1, trade_id = :tradeId WHERE id = :id")
    suspend fun linkDecisionToTrade(id: Long, tradeId: String)
    
    @Query("UPDATE ai_board_decisions SET outcome_pnl = :pnl, outcome_recorded_at = :timestamp WHERE id = :id")
    suspend fun recordOutcome(id: Long, pnl: Double, timestamp: Long)
    
    @Query("DELETE FROM ai_board_decisions WHERE timestamp < :cutoff")
    suspend fun deleteDecisionsOlderThan(cutoff: Long): Int
}

@Dao
interface TaxLotDao {
    @Query("SELECT * FROM tax_lots WHERE is_fully_disposed = 0 ORDER BY acquisition_date ASC")
    fun getOpenLots(): Flow<List<TaxLotEntity>>
    
    @Query("SELECT * FROM tax_lots WHERE symbol = :symbol AND is_fully_disposed = 0 ORDER BY acquisition_date ASC")
    suspend fun getOpenLotsForSymbolFIFO(symbol: String): List<TaxLotEntity>
    
    @Query("SELECT * FROM tax_lots WHERE symbol = :symbol AND is_fully_disposed = 0 ORDER BY acquisition_date DESC")
    suspend fun getOpenLotsForSymbolLIFO(symbol: String): List<TaxLotEntity>
    
    @Query("SELECT * FROM tax_lots WHERE id = :id")
    suspend fun getLotById(id: String): TaxLotEntity?
    
    @Query("SELECT SUM(total_gain_loss) FROM tax_lots WHERE is_long_term = :isLongTerm")
    suspend fun getTotalGainLossByTerm(isLongTerm: Boolean): Double?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLot(lot: TaxLotEntity)
    
    @Update
    suspend fun updateLot(lot: TaxLotEntity)
}

@Dao
interface StahlHistoryDao {
    @Query("SELECT * FROM stahl_history WHERE trade_id = :tradeId ORDER BY timestamp ASC")
    suspend fun getHistoryForTrade(tradeId: String): List<StahlHistoryEntity>
    
    @Insert
    suspend fun insertHistory(history: StahlHistoryEntity)
    
    @Query("DELETE FROM stahl_history WHERE timestamp < :cutoff")
    suspend fun deleteHistoryOlderThan(cutoff: Long): Int
}

@Dao
interface EquitySnapshotDao {
    @Query("SELECT * FROM equity_snapshots WHERE snapshot_type = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getSnapshots(type: String, limit: Int): List<EquitySnapshotEntity>
    
    @Query("SELECT * FROM equity_snapshots WHERE snapshot_type = :type AND timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    suspend fun getSnapshotsInRange(type: String, start: Long, end: Long): List<EquitySnapshotEntity>
    
    @Query("SELECT * FROM equity_snapshots WHERE snapshot_type = 'DAILY' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestDailySnapshot(): EquitySnapshotEntity?
    
    @Insert
    suspend fun insertSnapshot(snapshot: EquitySnapshotEntity)
    
    @Query("DELETE FROM equity_snapshots WHERE timestamp < :cutoff AND snapshot_type = :type")
    suspend fun deleteSnapshotsOlderThan(cutoff: Long, type: String): Int
}

@Dao
interface ArchiveMetadataDao {
    @Query("SELECT * FROM archive_metadata ORDER BY created_at DESC")
    fun getAllArchives(): Flow<List<ArchiveMetadataEntity>>
    
    @Query("SELECT * FROM archive_metadata WHERE archive_type = :type ORDER BY period_end DESC")
    suspend fun getArchivesByType(type: String): List<ArchiveMetadataEntity>
    
    @Query("SELECT * FROM archive_metadata WHERE id = :id")
    suspend fun getArchiveById(id: String): ArchiveMetadataEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArchive(archive: ArchiveMetadataEntity)
    
    @Update
    suspend fun updateArchive(archive: ArchiveMetadataEntity)
    
    @Delete
    suspend fun deleteArchive(archive: ArchiveMetadataEntity)
}
