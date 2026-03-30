package com.miwealth.sovereignvantage.core.ml

import androidx.room.*

/**
 * BUILD #335: DQN State Persistence Entity
 * 
 * Stores Q-table state for each DQN model so learning persists across app sessions.
 * 
 * KEY FORMAT: "{symbol}_{memberName}" or "{symbol}_{sharedKey}"
 * Examples:
 * - "BTC/USDT_Arthur" (solo DQN)
 * - "BTC/USDT_Volatility" (shared by Sentinel + Theta)
 * - "ETH/USDT_OnChain" (shared by Nexus + Moby)
 * 
 * TOTAL: 44 DQN models (4 symbols × 11 keys after cross-board sharing)
 * 
 * Q-TABLE STRUCTURE:
 * - Map<String, Double> where key = "state_action"
 * - State = price trend + volatility bucket
 * - Action = BUY/SELL/HOLD
 * - Value = expected future reward
 * 
 * PERSISTENCE STRATEGY:
 * - Save on app pause/stop (lifecycle)
 * - Auto-save every 5 minutes (prevent loss)
 * - Load on TradingCoordinator initialization
 * 
 * © 2025-2026 MiWealth Pty Ltd
 */
@Entity(
    tableName = "dqn_states",
    indices = [Index(value = ["dqn_key"], unique = true)]
)
data class DQNStateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * DQN identifier: "{symbol}_{memberName}"
     * Example: "BTC/USDT_Arthur", "ETH/USDT_Volatility"
     */
    @ColumnInfo(name = "dqn_key")
    val dqnKey: String,
    
    /**
     * Symbol this DQN is trained on
     * Example: "BTC/USDT", "ETH/USDT"
     */
    @ColumnInfo(name = "symbol")
    val symbol: String,
    
    /**
     * Member name or shared key
     * Example: "Arthur", "Volatility", "OnChain"
     */
    @ColumnInfo(name = "member_name")
    val memberName: String,
    
    /**
     * Q-table as JSON string
     * Format: {"state_action": value, ...}
     * Example: {"RISING_BUY": 0.85, "RISING_HOLD": 0.42, ...}
     */
    @ColumnInfo(name = "q_table_json")
    val qTableJson: String,
    
    /**
     * Last update timestamp (milliseconds)
     */
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis(),
    
    /**
     * Number of training episodes completed
     */
    @ColumnInfo(name = "training_episodes")
    val trainingEpisodes: Int = 0,
    
    /**
     * Current learning rate (ATR-scaled)
     */
    @ColumnInfo(name = "learning_rate")
    val learningRate: Double = 0.001
)
