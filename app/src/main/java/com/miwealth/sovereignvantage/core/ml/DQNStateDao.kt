package com.miwealth.sovereignvantage.core.ml

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * BUILD #335: DQN State DAO
 * 
 * Database operations for persisting DQN Q-tables.
 * 
 * Operations:
 * - Save/update Q-table for specific DQN
 * - Load Q-table on startup
 * - Delete old/stale Q-tables
 * - Get all DQN states for backup
 */
@Dao
interface DQNStateDao {
    
    /**
     * Save or update DQN state.
     * If dqnKey exists, updates; otherwise inserts.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveState(state: DQNStateEntity)
    
    /**
     * Save multiple DQN states in batch (more efficient)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveStates(states: List<DQNStateEntity>)
    
    /**
     * Load Q-table for specific DQN
     * Returns null if not found (fresh DQN)
     */
    @Query("SELECT * FROM dqn_states WHERE dqn_key = :dqnKey LIMIT 1")
    suspend fun loadState(dqnKey: String): DQNStateEntity?
    
    /**
     * Load all states for a specific symbol
     * Example: Get all BTC/USDT DQNs
     */
    @Query("SELECT * FROM dqn_states WHERE symbol = :symbol")
    suspend fun loadStatesForSymbol(symbol: String): List<DQNStateEntity>
    
    /**
     * Get all DQN states (for backup/export)
     */
    @Query("SELECT * FROM dqn_states ORDER BY last_updated DESC")
    suspend fun getAllStates(): List<DQNStateEntity>
    
    /**
     * Observe all DQN states (for UI/monitoring)
     */
    @Query("SELECT * FROM dqn_states ORDER BY last_updated DESC")
    fun observeAllStates(): Flow<List<DQNStateEntity>>
    
    /**
     * Delete state for specific DQN
     * (useful for resetting a specific member)
     */
    @Query("DELETE FROM dqn_states WHERE dqn_key = :dqnKey")
    suspend fun deleteState(dqnKey: String)
    
    /**
     * Delete all states for a symbol
     * (useful when removing a trading pair)
     */
    @Query("DELETE FROM dqn_states WHERE symbol = :symbol")
    suspend fun deleteStatesForSymbol(symbol: String)
    
    /**
     * Delete all DQN states
     * (nuclear reset - use with caution!)
     */
    @Query("DELETE FROM dqn_states")
    suspend fun deleteAllStates()
    
    /**
     * Count total stored DQNs
     */
    @Query("SELECT COUNT(*) FROM dqn_states")
    suspend fun getStateCount(): Int
    
    /**
     * Delete states older than X days
     * (cleanup stale models for symbols no longer traded)
     */
    @Query("DELETE FROM dqn_states WHERE last_updated < :timestamp")
    suspend fun deleteStatesOlderThan(timestamp: Long)
    
    /**
     * Get total training episodes across all DQNs
     * (metric for overall system learning)
     */
    @Query("SELECT SUM(training_episodes) FROM dqn_states")
    suspend fun getTotalTrainingEpisodes(): Int?
}
