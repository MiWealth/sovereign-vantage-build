package com.miwealth.sovereignvantage.core.ai

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * SOVEREIGN VANTAGE V5.5.42 "ARTHUR EDITION"
 * BOARD DECISION REPOSITORY
 * 
 * Persistence layer for AI Board decision records.
 * Critical for XAI (Explainable AI) compliance and tax audit trails.
 * 
 * Every trade executed by the AI Board generates a decision record that:
 * 1. Documents all 8 individual board member votes
 * 2. Records the reasoning behind each vote
 * 3. Captures the market context at decision time
 * 4. Links to the resulting trade for audit purposes
 * 
 * This data is encrypted at rest via SQLCipher (see TradeDatabase).
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 */

// ============================================================================
// ROOM ENTITIES
// ============================================================================

/**
 * Database entity for storing Board Decision records
 */
@Entity(tableName = "board_decisions")
data class BoardDecisionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "symbol")
    val symbol: String,
    
    @ColumnInfo(name = "timeframe")
    val timeframe: String,
    
    // Market Context (flattened for Room)
    @ColumnInfo(name = "context_price")
    val contextPrice: Double,
    
    @ColumnInfo(name = "context_change_24h")
    val contextChange24h: Double,
    
    @ColumnInfo(name = "context_volume_24h")
    val contextVolume24h: Double,
    
    @ColumnInfo(name = "context_high_24h")
    val contextHigh24h: Double,
    
    @ColumnInfo(name = "context_low_24h")
    val contextLow24h: Double,
    
    // Consensus Results
    @ColumnInfo(name = "final_decision")
    val finalDecision: String,  // BoardVote.name
    
    @ColumnInfo(name = "weighted_score")
    val weightedScore: Double,
    
    @ColumnInfo(name = "confidence")
    val confidence: Double,
    
    @ColumnInfo(name = "unanimous_count")
    val unanimousCount: Int,
    
    @ColumnInfo(name = "synthesis")
    val synthesis: String,
    
    // Action taken
    @ColumnInfo(name = "action_taken")
    val actionTaken: String,
    
    @ColumnInfo(name = "reason_for_action")
    val reasonForAction: String,
    
    // Individual votes stored as JSON
    @ColumnInfo(name = "individual_votes_json")
    val individualVotesJson: String,
    
    // Optional link to trade
    @ColumnInfo(name = "trade_id")
    val tradeId: String? = null,
    
    // Session ID for grouping related decisions
    @ColumnInfo(name = "session_id")
    val sessionId: String? = null
)

/**
 * Database entity for individual member votes
 * (Alternative to JSON storage for queryability)
 */
@Entity(
    tableName = "board_member_votes",
    foreignKeys = [
        ForeignKey(
            entity = BoardDecisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["decision_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("decision_id"), Index("member_id")]
)
data class MemberVoteEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "decision_id")
    val decisionId: String,
    
    @ColumnInfo(name = "member_id")
    val memberId: String,
    
    @ColumnInfo(name = "display_name")
    val displayName: String,
    
    @ColumnInfo(name = "role")
    val role: String,
    
    @ColumnInfo(name = "vote")
    val vote: String,  // BoardVote.name
    
    @ColumnInfo(name = "sentiment")
    val sentiment: Double,
    
    @ColumnInfo(name = "confidence")
    val confidence: Double,
    
    @ColumnInfo(name = "weight")
    val weight: Double,
    
    @ColumnInfo(name = "reasoning")
    val reasoning: String,
    
    @ColumnInfo(name = "key_indicators")
    val keyIndicators: String  // JSON array or comma-separated
)

// ============================================================================
// DAO INTERFACE
// ============================================================================

@Dao
interface BoardDecisionDao {
    
    // ========================================================================
    // INSERT OPERATIONS
    // ========================================================================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecision(decision: BoardDecisionEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVotes(votes: List<MemberVoteEntity>)
    
    @Transaction
    suspend fun insertDecisionWithVotes(
        decision: BoardDecisionEntity,
        votes: List<MemberVoteEntity>
    ) {
        insertDecision(decision)
        insertVotes(votes)
    }
    
    // ========================================================================
    // QUERY OPERATIONS
    // ========================================================================
    
    @Query("SELECT * FROM board_decisions WHERE id = :id")
    suspend fun getById(id: String): BoardDecisionEntity?
    
    @Query("SELECT * FROM board_decisions WHERE symbol = :symbol ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getBySymbol(symbol: String, limit: Int = 100): List<BoardDecisionEntity>
    
    @Query("SELECT * FROM board_decisions WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun getByTimeRange(start: Long, end: Long): List<BoardDecisionEntity>
    
    @Query("SELECT * FROM board_decisions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<BoardDecisionEntity>
    
    @Query("SELECT * FROM board_decisions WHERE action_taken = :action ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByAction(action: String, limit: Int = 100): List<BoardDecisionEntity>
    
    @Query("SELECT * FROM board_decisions WHERE trade_id = :tradeId")
    suspend fun getByTradeId(tradeId: String): BoardDecisionEntity?
    
    @Query("SELECT * FROM board_member_votes WHERE decision_id = :decisionId")
    suspend fun getVotesForDecision(decisionId: String): List<MemberVoteEntity>
    
    // ========================================================================
    // FLOW QUERIES (Reactive)
    // ========================================================================
    
    @Query("SELECT * FROM board_decisions ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<BoardDecisionEntity>>
    
    @Query("SELECT * FROM board_decisions WHERE symbol = :symbol ORDER BY timestamp DESC LIMIT :limit")
    fun observeBySymbol(symbol: String, limit: Int = 50): Flow<List<BoardDecisionEntity>>
    
    // ========================================================================
    // AGGREGATE QUERIES
    // ========================================================================
    
    @Query("SELECT COUNT(*) FROM board_decisions WHERE timestamp >= :since")
    suspend fun countDecisionsSince(since: Long): Int
    
    @Query("SELECT COUNT(*) FROM board_decisions WHERE final_decision = :decision AND timestamp >= :since")
    suspend fun countByDecisionSince(decision: String, since: Long): Int
    
    @Query("""
        SELECT member_id, AVG(confidence) as avg_confidence, COUNT(*) as vote_count
        FROM board_member_votes
        WHERE decision_id IN (SELECT id FROM board_decisions WHERE timestamp >= :since)
        GROUP BY member_id
    """)
    suspend fun getMemberStatsSince(since: Long): List<MemberStatsResult>
    
    // ========================================================================
    // TAX/AUDIT EXPORTS
    // ========================================================================
    
    @Query("""
        SELECT * FROM board_decisions 
        WHERE timestamp >= :startOfYear AND timestamp < :endOfYear
        AND action_taken IN ('TRADE_EXECUTED', 'SIGNAL_CONFIRMED')
        ORDER BY timestamp ASC
    """)
    suspend fun getForTaxYear(startOfYear: Long, endOfYear: Long): List<BoardDecisionEntity>
    
    // ========================================================================
    // DELETE OPERATIONS
    // ========================================================================
    
    @Query("DELETE FROM board_decisions WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long): Int
    
    @Delete
    suspend fun delete(decision: BoardDecisionEntity)
    
    @Query("DELETE FROM board_decisions")
    suspend fun deleteAll()
}

/**
 * Result class for member stats aggregation
 */
data class MemberStatsResult(
    @ColumnInfo(name = "member_id")
    val memberId: String,
    
    @ColumnInfo(name = "avg_confidence")
    val avgConfidence: Double,
    
    @ColumnInfo(name = "vote_count")
    val voteCount: Int
)

// ============================================================================
// REPOSITORY INTERFACE
// ============================================================================

/**
 * Repository interface for Board Decision storage
 */
interface BoardDecisionRepository {
    
    /**
     * Save a complete board decision record
     */
    suspend fun save(record: BoardDecisionRecord, tradeId: String? = null)
    
    /**
     * Get decisions for a specific symbol
     */
    suspend fun getBySymbol(symbol: String, limit: Int = 100): List<BoardDecisionRecord>
    
    /**
     * Get decisions within a time range
     */
    suspend fun getByTimeRange(start: Long, end: Long): List<BoardDecisionRecord>
    
    /**
     * Get recent decisions
     */
    suspend fun getRecent(limit: Int = 50): List<BoardDecisionRecord>
    
    /**
     * Get the decision that led to a specific trade
     */
    suspend fun getByTradeId(tradeId: String): BoardDecisionRecord?
    
    /**
     * Export decisions for a tax year (Australian financial year: July 1 - June 30)
     */
    suspend fun exportForTaxYear(year: Int): List<BoardDecisionRecord>
    
    /**
     * Get board member performance statistics
     */
    suspend fun getMemberStats(sinceDays: Int = 30): Map<String, MemberStats>
    
    /**
     * Observe recent decisions reactively
     */
    fun observeRecent(limit: Int = 50): Flow<List<BoardDecisionRecord>>
    
    /**
     * Delete old records (data retention policy)
     */
    suspend fun purgeOlderThan(days: Int): Int
}

data class MemberStats(
    val memberId: String,
    val avgConfidence: Double,
    val voteCount: Int,
    val winRate: Double? = null  // If trade outcomes are available
)

// ============================================================================
// REPOSITORY IMPLEMENTATION
// ============================================================================

/**
 * Implementation of BoardDecisionRepository using Room
 */
class BoardDecisionRepositoryImpl(
    private val dao: BoardDecisionDao
) : BoardDecisionRepository {
    
    override suspend fun save(record: BoardDecisionRecord, tradeId: String?) {
        // Convert domain model to entity
        val entity = record.toEntity(tradeId)
        
        // Convert individual votes
        val voteEntities = record.individualVotes.map { vote ->
            MemberVoteEntity(
                decisionId = record.id,
                memberId = vote.memberId,
                displayName = vote.displayName,
                role = vote.role,
                vote = vote.vote.name,
                sentiment = vote.sentiment,
                confidence = vote.confidence,
                weight = vote.weight,
                reasoning = vote.reasoning,
                keyIndicators = vote.keyIndicators.joinToString(",")
            )
        }
        
        // Insert both in transaction
        dao.insertDecisionWithVotes(entity, voteEntities)
    }
    
    override suspend fun getBySymbol(symbol: String, limit: Int): List<BoardDecisionRecord> {
        return dao.getBySymbol(symbol, limit).map { it.toDomainModel(dao) }
    }
    
    override suspend fun getByTimeRange(start: Long, end: Long): List<BoardDecisionRecord> {
        return dao.getByTimeRange(start, end).map { it.toDomainModel(dao) }
    }
    
    override suspend fun getRecent(limit: Int): List<BoardDecisionRecord> {
        return dao.getRecent(limit).map { it.toDomainModel(dao) }
    }
    
    override suspend fun getByTradeId(tradeId: String): BoardDecisionRecord? {
        return dao.getByTradeId(tradeId)?.toDomainModel(dao)
    }
    
    override suspend fun exportForTaxYear(year: Int): List<BoardDecisionRecord> {
        // Australian financial year: July 1 - June 30
        val startOfYear = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, year)
            set(java.util.Calendar.MONTH, java.util.Calendar.JULY)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        val endOfYear = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, year + 1)
            set(java.util.Calendar.MONTH, java.util.Calendar.JUNE)
            set(java.util.Calendar.DAY_OF_MONTH, 30)
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }.timeInMillis
        
        return dao.getForTaxYear(startOfYear, endOfYear).map { it.toDomainModel(dao) }
    }
    
    override suspend fun getMemberStats(sinceDays: Int): Map<String, MemberStats> {
        val since = System.currentTimeMillis() - (sinceDays.toLong() * 24 * 60 * 60 * 1000)
        val results = dao.getMemberStatsSince(since)
        
        return results.associate { result ->
            result.memberId to MemberStats(
                memberId = result.memberId,
                avgConfidence = result.avgConfidence,
                voteCount = result.voteCount
            )
        }
    }
    
    override fun observeRecent(limit: Int): Flow<List<BoardDecisionRecord>> {
        return kotlinx.coroutines.flow.map(dao.observeRecent(limit)) { entities ->
            entities.map { it.toDomainModelSync() }
        }
    }
    
    override suspend fun purgeOlderThan(days: Int): Int {
        val cutoff = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        return dao.deleteOlderThan(cutoff)
    }
}

// ============================================================================
// EXTENSION FUNCTIONS FOR CONVERSION
// ============================================================================

/**
 * Convert domain model to entity
 */
private fun BoardDecisionRecord.toEntity(tradeId: String? = null): BoardDecisionEntity {
    // Simple JSON serialization for votes (could use Moshi/Gson in production)
    val votesJson = individualVotes.joinToString("|") { vote ->
        "${vote.memberId}:${vote.vote.name}:${vote.confidence}:${vote.reasoning}"
    }
    
    return BoardDecisionEntity(
        id = this.id,
        timestamp = this.timestamp,
        symbol = this.symbol,
        timeframe = this.timeframe,
        contextPrice = this.marketContext.currentPrice,
        contextChange24h = this.marketContext.change24h,
        contextVolume24h = this.marketContext.volume24h,
        contextHigh24h = this.marketContext.high24h,
        contextLow24h = this.marketContext.low24h,
        finalDecision = this.consensus.finalDecision.name,
        weightedScore = this.consensus.weightedScore,
        confidence = this.consensus.confidence,
        unanimousCount = this.consensus.unanimousCount,
        synthesis = this.consensus.synthesis,
        actionTaken = this.actionTaken,
        reasonForAction = this.reasonForAction,
        individualVotesJson = votesJson,
        tradeId = tradeId,
        sessionId = this.consensus.sessionId
    )
}

/**
 * Convert entity to domain model (with DAO for vote lookup)
 */
private suspend fun BoardDecisionEntity.toDomainModel(dao: BoardDecisionDao): BoardDecisionRecord {
    val voteEntities = dao.getVotesForDecision(this.id)
    
    return BoardDecisionRecord(
        id = this.id,
        timestamp = this.timestamp,
        symbol = this.symbol,
        timeframe = this.timeframe,
        marketContext = MarketContextSnapshot(
            currentPrice = this.contextPrice,
            change24h = this.contextChange24h,
            volume24h = this.contextVolume24h,
            high24h = this.contextHigh24h,
            low24h = this.contextLow24h
        ),
        individualVotes = voteEntities.map { it.toDomainModel() },
        consensus = BoardConsensus(
            finalDecision = BoardVote.valueOf(this.finalDecision),
            weightedScore = this.weightedScore,
            confidence = this.confidence,
            unanimousCount = this.unanimousCount,
            synthesis = this.synthesis,
            sessionId = this.sessionId ?: ""
        ),
        actionTaken = this.actionTaken,
        reasonForAction = this.reasonForAction
    )
}

/**
 * Synchronous version for Flow mapping (uses cached JSON)
 */
private fun BoardDecisionEntity.toDomainModelSync(): BoardDecisionRecord {
    // Parse votes from JSON (simple format: memberId:vote:confidence:reasoning|...)
    val votes = this.individualVotesJson.split("|").mapNotNull { voteStr ->
        val parts = voteStr.split(":", limit = 4)
        if (parts.size >= 4) {
            MemberVoteRecord(
                memberId = parts[0],
                displayName = parts[0],  // Will be updated on full load
                role = "",
                vote = try { BoardVote.valueOf(parts[1]) } catch (e: Exception) { BoardVote.HOLD },
                sentiment = 0.0,
                confidence = parts[2].toDoubleOrNull() ?: 0.0,
                weight = 0.0,
                reasoning = parts[3],
                keyIndicators = emptyList()
            )
        } else null
    }
    
    return BoardDecisionRecord(
        id = this.id,
        timestamp = this.timestamp,
        symbol = this.symbol,
        timeframe = this.timeframe,
        marketContext = MarketContextSnapshot(
            currentPrice = this.contextPrice,
            change24h = this.contextChange24h,
            volume24h = this.contextVolume24h,
            high24h = this.contextHigh24h,
            low24h = this.contextLow24h
        ),
        individualVotes = votes,
        consensus = BoardConsensus(
            finalDecision = try { BoardVote.valueOf(this.finalDecision) } catch (e: Exception) { BoardVote.HOLD },
            weightedScore = this.weightedScore,
            confidence = this.confidence,
            unanimousCount = this.unanimousCount,
            synthesis = this.synthesis,
            sessionId = this.sessionId ?: ""
        ),
        actionTaken = this.actionTaken,
        reasonForAction = this.reasonForAction
    )
}

/**
 * Convert vote entity to domain model
 */
private fun MemberVoteEntity.toDomainModel(): MemberVoteRecord {
    return MemberVoteRecord(
        memberId = this.memberId,
        displayName = this.displayName,
        role = this.role,
        vote = try { BoardVote.valueOf(this.vote) } catch (e: Exception) { BoardVote.HOLD },
        sentiment = this.sentiment,
        confidence = this.confidence,
        weight = this.weight,
        reasoning = this.reasoning,
        keyIndicators = this.keyIndicators.split(",").filter { it.isNotBlank() }
    )
}
