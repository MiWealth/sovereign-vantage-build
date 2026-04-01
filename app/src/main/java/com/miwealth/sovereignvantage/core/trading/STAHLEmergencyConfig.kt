package com.miwealth.sovereignvantage.core.trading

/**
 * SOVEREIGN VANTAGE V5.19.362 "ARTHUR EDITION"
 * STAHL EMERGENCY CLOSE CONFIGURATION
 * 
 * BUILD #362: CLIENT-CONFIGURABLE EMERGENCY RETRY SYSTEM
 * 
 * USER SOVEREIGNTY DESIGN:
 * - Client configures emergency close behavior
 * - System executes client's instructions
 * - Reinforces SaaS nature (software tool, not financial service)
 * - Strengthens regulatory position (MiCA, GENIUS, CLARITY, AFSL)
 * 
 * REGULATORY BENEFITS:
 * - ASIC: "Client configures, we execute their instructions"
 * - MiCA: "Software tool with client-defined parameters"
 * - CLARITY: "User has ultimate control, software doesn't make autonomous decisions"
 * - AFSL: "No financial services - client controls all trading behavior"
 * 
 * © 2025-2026 MiWealth Pty Ltd
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */
data class STAHLEmergencyConfig(
    /**
     * PHASE 1: RAPID RESPONSE
     * Fast attempts to close position (exchange might have temporary glitch)
     */
    val rapidRetryAttempts: Int = 3,
    val rapidRetryDelayMs: Long = 1000L,  // 1 second between rapid attempts
    
    /**
     * PHASE 2: PERSISTENT ATTEMPTS
     * Keep fighting to close position (serious issue, never give up)
     */
    val enableUnlimitedRetries: Boolean = true,
    val persistentRetryDelayMs: Long = 2000L,  // Base delay in persistent phase
    val maxBackoffDelayMs: Long = 10000L,      // Cap exponential backoff at 10 seconds
    
    /**
     * ALERTING BEHAVIOR
     */
    val reAlertInterval: Int = 10,              // Re-alert every N attempts
    val enableEmergencySound: Boolean = true,   // Sound alarm during emergency
    val emergencySoundType: EmergencySoundType = EmergencySoundType.CRITICAL
) {
    
    companion object {
        /**
         * BALANCED (DEFAULT - Recommended for most users)
         * 
         * Phase 1: 3 rapid attempts at 1-second intervals
         * Phase 2: Unlimited retries with 2-second base delay, 10-second max backoff
         * 
         * RATE LIMITING ANALYSIS:
         * - Binance: 1200/min (20/sec) - 1/sec is SAFE ✅
         * - Kraken: 15-20/sec - 1/sec is SAFE ✅
         * - Coinbase: ~10/sec - 1/sec is SAFE ✅
         * - Gate.io: ~100/10sec - 1/sec is SAFE ✅
         */
        fun balanced() = STAHLEmergencyConfig(
            rapidRetryAttempts = 3,
            rapidRetryDelayMs = 1000L,
            enableUnlimitedRetries = true,
            persistentRetryDelayMs = 2000L,
            maxBackoffDelayMs = 10000L,
            reAlertInterval = 10,
            enableEmergencySound = true,
            emergencySoundType = EmergencySoundType.CRITICAL
        )
        
        /**
         * CONSERVATIVE (Cautious clients)
         * 
         * Phase 1: 5 rapid attempts at 2-second intervals
         * Phase 2: Unlimited retries with 5-second base delay, 15-second max backoff
         * 
         * Slower pace, still determined to close position.
         */
        fun conservative() = STAHLEmergencyConfig(
            rapidRetryAttempts = 5,
            rapidRetryDelayMs = 2000L,
            enableUnlimitedRetries = true,
            persistentRetryDelayMs = 5000L,
            maxBackoffDelayMs = 15000L,
            reAlertInterval = 5,
            enableEmergencySound = true,
            emergencySoundType = EmergencySoundType.URGENT
        )
        
        /**
         * AGGRESSIVE (Fight hard, fight fast)
         * 
         * Phase 1: 10 rapid attempts at 500ms intervals
         * Phase 2: Unlimited retries with 1-second base delay, 5-second max backoff
         * 
         * Maximum determination to close position quickly.
         * Still safe from rate limiting - exchanges can handle 2/sec easily.
         */
        fun aggressive() = STAHLEmergencyConfig(
            rapidRetryAttempts = 10,
            rapidRetryDelayMs = 500L,
            enableUnlimitedRetries = true,
            persistentRetryDelayMs = 1000L,
            maxBackoffDelayMs = 5000L,
            reAlertInterval = 20,
            enableEmergencySound = true,
            emergencySoundType = EmergencySoundType.CRITICAL
        )
        
        /**
         * MANUAL INTERVENTION (Human wants control)
         * 
         * Phase 1: 3 rapid attempts at 1-second intervals
         * Phase 2: DISABLED - Stop after rapid phase, wait for human
         * 
         * For users who prefer manual oversight of emergency situations.
         * NOT RECOMMENDED - defeats autonomous trading principle.
         */
        fun manualOnly() = STAHLEmergencyConfig(
            rapidRetryAttempts = 3,
            rapidRetryDelayMs = 1000L,
            enableUnlimitedRetries = false,  // STOP after rapid phase
            persistentRetryDelayMs = 1000L,
            maxBackoffDelayMs = 1000L,
            reAlertInterval = 1,  // Alert every attempt
            enableEmergencySound = true,
            emergencySoundType = EmergencySoundType.CRITICAL
        )
    }
    
    /**
     * Validate configuration to prevent invalid settings.
     */
    fun validate(): STAHLEmergencyConfig {
        require(rapidRetryAttempts in 1..100) { 
            "rapidRetryAttempts must be 1-100, got $rapidRetryAttempts" 
        }
        require(rapidRetryDelayMs in 100..30000) { 
            "rapidRetryDelayMs must be 100-30000ms, got $rapidRetryDelayMs" 
        }
        require(persistentRetryDelayMs in 100..60000) { 
            "persistentRetryDelayMs must be 100-60000ms, got $persistentRetryDelayMs" 
        }
        require(maxBackoffDelayMs in 1000..120000) { 
            "maxBackoffDelayMs must be 1000-120000ms, got $maxBackoffDelayMs" 
        }
        require(reAlertInterval in 1..1000) { 
            "reAlertInterval must be 1-1000, got $reAlertInterval" 
        }
        return this
    }
}

/**
 * Emergency sound types for STAHL stop alerts.
 */
enum class EmergencySoundType(val displayName: String) {
    CRITICAL("Critical Alert"),
    URGENT("Urgent Alert"),
    WARNING("Warning Alert"),
    SILENT("Silent (Visual Only)")
}

/**
 * Preset descriptions for UI display.
 */
enum class STAHLEmergencyPreset(
    val displayName: String,
    val description: String,
    val configFactory: () -> STAHLEmergencyConfig
) {
    BALANCED(
        "Balanced (Recommended)",
        "3 rapid attempts, then unlimited retries. Good balance of speed and persistence.",
        STAHLEmergencyConfig.Companion::balanced
    ),
    CONSERVATIVE(
        "Conservative",
        "Slower pace (5 rapid, 2-5s delays). Still fights to close position.",
        STAHLEmergencyConfig.Companion::conservative
    ),
    AGGRESSIVE(
        "Aggressive",
        "Maximum determination (10 rapid, 500ms delays). Closes positions fast.",
        STAHLEmergencyConfig.Companion::aggressive
    ),
    MANUAL_ONLY(
        "Manual Intervention",
        "3 attempts then stop. You handle emergencies manually. NOT RECOMMENDED.",
        STAHLEmergencyConfig.Companion::manualOnly
    );
    
    fun createConfig(): STAHLEmergencyConfig = configFactory()
}
