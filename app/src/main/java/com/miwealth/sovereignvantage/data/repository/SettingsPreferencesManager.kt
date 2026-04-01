package com.miwealth.sovereignvantage.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.miwealth.sovereignvantage.core.trading.EmergencySoundType
import com.miwealth.sovereignvantage.core.trading.STAHLEmergencyConfig
import com.miwealth.sovereignvantage.core.trading.STAHLEmergencyPreset
import com.miwealth.sovereignvantage.core.trading.TradingMode
import com.miwealth.sovereignvantage.ui.settings.PaperTradingDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SOVEREIGN VANTAGE V5.19.105 "ARTHUR EDITION"
 * SETTINGS PREFERENCES MANAGER
 * 
 * BUILD #105 FIX:
 * Settings were not persisting because SettingsViewModel had TODOs.
 * This manager handles all settings persistence to SharedPreferences.
 * 
 * © 2025-2026 MiWealth Pty Ltd
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */
@Singleton
class SettingsPreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "sovereign_vantage_settings",
        Context.MODE_PRIVATE
    )
    
    companion object {
        // General Settings
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
        
        // Trading Mode & Hybrid Config
        private const val KEY_TRADING_MODE = "trading_mode"
        private const val KEY_HYBRID_AUTO_EXECUTE_THRESHOLD = "hybrid_auto_execute_threshold"
        private const val KEY_HYBRID_CONFIRMATION_THRESHOLD = "hybrid_confirmation_threshold"
        private const val KEY_HYBRID_MAX_AUTO_TRADES = "hybrid_max_auto_trades"
        private const val KEY_HYBRID_VALUE_THRESHOLD = "hybrid_value_threshold"
        
        // Advanced Strategy Settings
        private const val KEY_ALPHA_SCANNER_ENABLED = "alpha_scanner_enabled"
        private const val KEY_ALPHA_SCANNER_INTERVAL = "alpha_scanner_interval"
        private const val KEY_ALPHA_SCANNER_TOP_N = "alpha_scanner_top_n"
        private const val KEY_ALPHA_SCANNER_MIN_SCORE = "alpha_scanner_min_score"
        
        private const val KEY_FUNDING_ARB_ENABLED = "funding_arb_enabled"
        private const val KEY_FUNDING_ARB_MIN_RATE = "funding_arb_min_rate"
        private const val KEY_FUNDING_ARB_MAX_POSITIONS = "funding_arb_max_positions"
        private const val KEY_FUNDING_ARB_MAX_CAPITAL = "funding_arb_max_capital"
        
        private const val KEY_DAILY_LOSS_LIMIT = "daily_loss_limit"
        
        // Paper Trading Settings
        private const val KEY_PAPER_TRADING_ENABLED = "paper_trading_enabled"
        private const val KEY_PAPER_TRADING_BALANCE = "paper_trading_balance"
        private const val KEY_PAPER_TRADING_DATA_SOURCE = "paper_trading_data_source"
        
        // Defaults
        private const val DEFAULT_BIOMETRIC = true
        private const val DEFAULT_NOTIFICATIONS = true
        private const val DEFAULT_DARK_MODE = true
        private const val DEFAULT_TRADING_MODE = "SIGNAL_ONLY"
        private const val DEFAULT_HYBRID_AUTO_THRESHOLD = 85.0
        private const val DEFAULT_HYBRID_CONFIRM_THRESHOLD = 70.0
        private const val DEFAULT_HYBRID_MAX_TRADES = 3
        private const val DEFAULT_HYBRID_VALUE_THRESHOLD = 5000.0
        private const val DEFAULT_ALPHA_SCANNER_ENABLED = true
        private const val DEFAULT_ALPHA_SCANNER_INTERVAL = 60
        private const val DEFAULT_ALPHA_SCANNER_TOP_N = 10
        private const val DEFAULT_ALPHA_SCANNER_MIN_SCORE = 0.5
        private const val DEFAULT_FUNDING_ARB_ENABLED = true
        private const val DEFAULT_FUNDING_ARB_MIN_RATE = 0.01
        private const val DEFAULT_FUNDING_ARB_MAX_POSITIONS = 5
        private const val DEFAULT_FUNDING_ARB_MAX_CAPITAL = 50.0
        private const val DEFAULT_DAILY_LOSS_LIMIT = 3.0
        private const val DEFAULT_PAPER_TRADING = true
        private const val DEFAULT_PAPER_BALANCE = 100000.0
        private const val DEFAULT_PAPER_DATA_SOURCE = "LIVE"  // MOCK, LIVE, BACKTEST

        // BUILD #270: Per-trade close confirmation
        private const val KEY_CONFIRM_TRADE_CLOSE = "confirm_trade_close"
        private const val DEFAULT_CONFIRM_TRADE_CLOSE = true
        
        // BUILD #273: Trading Aggressiveness (confidence & board agreement thresholds)
        private const val KEY_TRADING_AGGRESSIVENESS = "trading_aggressiveness"
        private const val DEFAULT_TRADING_AGGRESSIVENESS = "MODERATE"  // CONSERVATIVE, MODERATE, AGGRESSIVE
        
        // BUILD #362: STAHL Emergency Close Configuration
        private const val KEY_STAHL_PRESET = "stahl_emergency_preset"
        private const val KEY_STAHL_RAPID_ATTEMPTS = "stahl_rapid_attempts"
        private const val KEY_STAHL_RAPID_DELAY = "stahl_rapid_delay_ms"
        private const val KEY_STAHL_UNLIMITED_RETRIES = "stahl_unlimited_retries"
        private const val KEY_STAHL_PERSISTENT_DELAY = "stahl_persistent_delay_ms"
        private const val KEY_STAHL_MAX_BACKOFF = "stahl_max_backoff_ms"
        private const val KEY_STAHL_REALERT_INTERVAL = "stahl_realert_interval"
        private const val KEY_STAHL_SOUND_ENABLED = "stahl_sound_enabled"
        private const val KEY_STAHL_SOUND_TYPE = "stahl_sound_type"
        private const val DEFAULT_STAHL_PRESET = "BALANCED"
        
        // BUILD #364: Multi-Position Configuration
        private const val KEY_MAX_POSITIONS_PER_SYMBOL = "max_positions_per_symbol"
        private const val KEY_MAX_TOTAL_POSITIONS = "max_total_positions"
        private const val KEY_MAX_CONCENTRATION_PERCENT = "max_concentration_percent"
        private const val KEY_MIN_MARGIN_PERCENT = "min_margin_percent"
        private const val KEY_MIN_CONFIDENCE_FOR_MULTIPLE = "min_confidence_for_multiple"
        private const val KEY_LIMIT_CORRELATED_POSITIONS = "limit_correlated_positions"
        private const val KEY_MAX_CORRELATION = "max_correlation"
        private const val DEFAULT_MAX_POSITIONS_PER_SYMBOL = -1  // -1 = unlimited (AI decides)
        private const val DEFAULT_MAX_TOTAL_POSITIONS = 10
        private const val DEFAULT_MAX_CONCENTRATION = 30.0
        private const val DEFAULT_MIN_MARGIN = 20.0
        private const val DEFAULT_MIN_CONFIDENCE_MULTIPLE = 75.0
        private const val DEFAULT_LIMIT_CORRELATED = true
        private const val DEFAULT_MAX_CORRELATION = 0.85
        
        // BUILD #364: Symbol Filter Configuration
        private const val KEY_ENABLE_STABLE_TO_STABLE = "enable_stable_to_stable"
        private const val KEY_AUTO_ENABLE_ON_DEPEG = "auto_enable_on_depeg"
        private const val KEY_DEPEG_THRESHOLD = "depeg_threshold_percent"
        private const val KEY_ENABLED_CATEGORIES = "enabled_symbol_categories"
        private const val DEFAULT_ENABLE_STABLE_TO_STABLE = false
        private const val DEFAULT_AUTO_ENABLE_DEPEG = true
        private const val DEFAULT_DEPEG_THRESHOLD = 2.0
    }
    
    // ========================================================================
    // GENERAL SETTINGS
    // ========================================================================
    
    fun getBiometricEnabled(): Boolean = 
        prefs.getBoolean(KEY_BIOMETRIC_ENABLED, DEFAULT_BIOMETRIC)
    
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }
    
    fun getNotificationsEnabled(): Boolean = 
        prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, DEFAULT_NOTIFICATIONS)
    
    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }
    
    fun getDarkModeEnabled(): Boolean = 
        prefs.getBoolean(KEY_DARK_MODE_ENABLED, DEFAULT_DARK_MODE)
    
    fun setDarkModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE_ENABLED, enabled).apply()
    }
    
    // ========================================================================
    // TRADING MODE & HYBRID CONFIG
    // ========================================================================
    
    fun getTradingMode(): String = 
        prefs.getString(KEY_TRADING_MODE, DEFAULT_TRADING_MODE) ?: DEFAULT_TRADING_MODE
    
    fun setTradingMode(mode: String) {
        prefs.edit().putString(KEY_TRADING_MODE, mode).apply()
    }
    
    fun getHybridAutoExecuteThreshold(): Double = 
        prefs.getFloat(KEY_HYBRID_AUTO_EXECUTE_THRESHOLD, DEFAULT_HYBRID_AUTO_THRESHOLD.toFloat()).toDouble()
    
    fun setHybridAutoExecuteThreshold(threshold: Double) {
        prefs.edit().putFloat(KEY_HYBRID_AUTO_EXECUTE_THRESHOLD, threshold.toFloat()).apply()
    }
    
    fun getHybridConfirmationThreshold(): Double = 
        prefs.getFloat(KEY_HYBRID_CONFIRMATION_THRESHOLD, DEFAULT_HYBRID_CONFIRM_THRESHOLD.toFloat()).toDouble()
    
    fun setHybridConfirmationThreshold(threshold: Double) {
        prefs.edit().putFloat(KEY_HYBRID_CONFIRMATION_THRESHOLD, threshold.toFloat()).apply()
    }
    
    fun getHybridMaxAutoTrades(): Int = 
        prefs.getInt(KEY_HYBRID_MAX_AUTO_TRADES, DEFAULT_HYBRID_MAX_TRADES)
    
    fun setHybridMaxAutoTrades(count: Int) {
        prefs.edit().putInt(KEY_HYBRID_MAX_AUTO_TRADES, count).apply()
    }
    
    fun getHybridValueThreshold(): Double = 
        prefs.getFloat(KEY_HYBRID_VALUE_THRESHOLD, DEFAULT_HYBRID_VALUE_THRESHOLD.toFloat()).toDouble()
    
    fun setHybridValueThreshold(value: Double) {
        prefs.edit().putFloat(KEY_HYBRID_VALUE_THRESHOLD, value.toFloat()).apply()
    }
    
    // ========================================================================
    // ADVANCED STRATEGY SETTINGS
    // ========================================================================
    
    fun getAlphaScannerEnabled(): Boolean = 
        prefs.getBoolean(KEY_ALPHA_SCANNER_ENABLED, DEFAULT_ALPHA_SCANNER_ENABLED)
    
    fun setAlphaScannerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALPHA_SCANNER_ENABLED, enabled).apply()
    }
    
    fun getAlphaScannerInterval(): Int = 
        prefs.getInt(KEY_ALPHA_SCANNER_INTERVAL, DEFAULT_ALPHA_SCANNER_INTERVAL)
    
    fun setAlphaScannerInterval(minutes: Int) {
        prefs.edit().putInt(KEY_ALPHA_SCANNER_INTERVAL, minutes).apply()
    }
    
    fun getAlphaScannerTopN(): Int = 
        prefs.getInt(KEY_ALPHA_SCANNER_TOP_N, DEFAULT_ALPHA_SCANNER_TOP_N)
    
    fun setAlphaScannerTopN(count: Int) {
        prefs.edit().putInt(KEY_ALPHA_SCANNER_TOP_N, count).apply()
    }
    
    fun getAlphaScannerMinScore(): Double = 
        prefs.getFloat(KEY_ALPHA_SCANNER_MIN_SCORE, DEFAULT_ALPHA_SCANNER_MIN_SCORE.toFloat()).toDouble()
    
    fun setAlphaScannerMinScore(score: Double) {
        prefs.edit().putFloat(KEY_ALPHA_SCANNER_MIN_SCORE, score.toFloat()).apply()
    }
    
    fun getFundingArbEnabled(): Boolean = 
        prefs.getBoolean(KEY_FUNDING_ARB_ENABLED, DEFAULT_FUNDING_ARB_ENABLED)
    
    fun setFundingArbEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FUNDING_ARB_ENABLED, enabled).apply()
    }
    
    fun getFundingArbMinRate(): Double = 
        prefs.getFloat(KEY_FUNDING_ARB_MIN_RATE, DEFAULT_FUNDING_ARB_MIN_RATE.toFloat()).toDouble()
    
    fun setFundingArbMinRate(rate: Double) {
        prefs.edit().putFloat(KEY_FUNDING_ARB_MIN_RATE, rate.toFloat()).apply()
    }
    
    fun getFundingArbMaxPositions(): Int = 
        prefs.getInt(KEY_FUNDING_ARB_MAX_POSITIONS, DEFAULT_FUNDING_ARB_MAX_POSITIONS)
    
    fun setFundingArbMaxPositions(count: Int) {
        prefs.edit().putInt(KEY_FUNDING_ARB_MAX_POSITIONS, count).apply()
    }
    
    fun getFundingArbMaxCapital(): Double = 
        prefs.getFloat(KEY_FUNDING_ARB_MAX_CAPITAL, DEFAULT_FUNDING_ARB_MAX_CAPITAL.toFloat()).toDouble()
    
    fun setFundingArbMaxCapital(percent: Double) {
        prefs.edit().putFloat(KEY_FUNDING_ARB_MAX_CAPITAL, percent.toFloat()).apply()
    }
    
    fun getDailyLossLimit(): Double = 
        prefs.getFloat(KEY_DAILY_LOSS_LIMIT, DEFAULT_DAILY_LOSS_LIMIT.toFloat()).toDouble()
    
    fun setDailyLossLimit(percent: Double) {
        prefs.edit().putFloat(KEY_DAILY_LOSS_LIMIT, percent.toFloat()).apply()
    }
    
    // ========================================================================
    // PAPER TRADING SETTINGS
    // ========================================================================
    
    fun getPaperTradingEnabled(): Boolean = 
        prefs.getBoolean(KEY_PAPER_TRADING_ENABLED, DEFAULT_PAPER_TRADING)
    
    fun setPaperTradingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PAPER_TRADING_ENABLED, enabled).apply()
    }
    
    fun getPaperTradingBalance(): Double = 
        prefs.getFloat(KEY_PAPER_TRADING_BALANCE, DEFAULT_PAPER_BALANCE.toFloat()).toDouble()
    
    fun setPaperTradingBalance(balance: Double) {
        prefs.edit().putFloat(KEY_PAPER_TRADING_BALANCE, balance.toFloat()).apply()
    }
    
    fun getPaperTradingDataSource(): PaperTradingDataSource {
        val value = prefs.getString(KEY_PAPER_TRADING_DATA_SOURCE, DEFAULT_PAPER_DATA_SOURCE)
        return try {
            PaperTradingDataSource.valueOf(value ?: DEFAULT_PAPER_DATA_SOURCE)
        } catch (e: IllegalArgumentException) {
            PaperTradingDataSource.LIVE
        }
    }
    
    fun setPaperTradingDataSource(source: PaperTradingDataSource) {
        prefs.edit().putString(KEY_PAPER_TRADING_DATA_SOURCE, source.name).apply()
    }
    
    // ========================================================================
    // UTILITY
    // ========================================================================

    // BUILD #270: Confirm before closing a trade
    fun getConfirmTradeClose(): Boolean =
        prefs.getBoolean(KEY_CONFIRM_TRADE_CLOSE, DEFAULT_CONFIRM_TRADE_CLOSE)

    fun setConfirmTradeClose(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONFIRM_TRADE_CLOSE, enabled).apply()
    }
    
    // ========================================================================
    // BUILD #273: TRADING AGGRESSIVENESS
    // ========================================================================
    
    /**
     * Get trading aggressiveness level.
     * Controls AI Board confidence and agreement thresholds.
     * 
     * CONSERVATIVE: High confidence required (60%), 5 of 8 board members (62.5%)
     * MODERATE: Medium confidence (40%), 4 of 8 board members (50%)
     * AGGRESSIVE: Low confidence (25%), 3 of 8 board members (37.5%)
     */
    fun getTradingAggressiveness(): TradingAggressiveness {
        val value = prefs.getString(KEY_TRADING_AGGRESSIVENESS, DEFAULT_TRADING_AGGRESSIVENESS)
        return try {
            TradingAggressiveness.valueOf(value ?: DEFAULT_TRADING_AGGRESSIVENESS)
        } catch (e: IllegalArgumentException) {
            TradingAggressiveness.MODERATE
        }
    }
    
    fun setTradingAggressiveness(level: TradingAggressiveness) {
        prefs.edit().putString(KEY_TRADING_AGGRESSIVENESS, level.name).apply()
    }
    
    /**
     * Get minimum confidence threshold based on aggressiveness setting.
     */
    fun getMinConfidenceThreshold(): Double {
        return when (getTradingAggressiveness()) {
            TradingAggressiveness.CONSERVATIVE -> 0.60  // 60%
            TradingAggressiveness.MODERATE -> 0.40      // 40%
            TradingAggressiveness.AGGRESSIVE -> 0.25    // 25%
        }
    }
    
    /**
     * Get minimum board agreement (out of 8) based on aggressiveness setting.
     */
    fun getMinBoardAgreement(): Int {
        return when (getTradingAggressiveness()) {
            TradingAggressiveness.CONSERVATIVE -> 5  // 5/8 = 62.5%
            TradingAggressiveness.MODERATE -> 4      // 4/8 = 50%
            TradingAggressiveness.AGGRESSIVE -> 3    // 3/8 = 37.5%
        }
    }
    
    // ========================================================================
    // BUILD #362: STAHL EMERGENCY CLOSE CONFIGURATION
    // ========================================================================
    
    /**
     * Get STAHL emergency configuration preset.
     * Controls retry behavior when STAHL stop needs to close a position.
     * 
     * CLIENT SOVEREIGNTY:
     * - User configures emergency behavior
     * - System executes user's instructions
     * - Reinforces SaaS nature (software tool, not financial service)
     */
    fun getSTAHLEmergencyPreset(): STAHLEmergencyPreset {
        val value = prefs.getString(KEY_STAHL_PRESET, DEFAULT_STAHL_PRESET)
        return try {
            STAHLEmergencyPreset.valueOf(value ?: DEFAULT_STAHL_PRESET)
        } catch (e: IllegalArgumentException) {
            STAHLEmergencyPreset.BALANCED
        }
    }
    
    fun setSTAHLEmergencyPreset(preset: STAHLEmergencyPreset) {
        prefs.edit().putString(KEY_STAHL_PRESET, preset.name).apply()
        // Also save the preset's config values
        val config = preset.createConfig()
        setSTAHLEmergencyConfig(config)
    }
    
    /**
     * Get full STAHL emergency configuration.
     * Returns config from stored values, or default if not set.
     */
    fun getSTAHLEmergencyConfig(): STAHLEmergencyConfig {
        // If custom values exist, use them
        val rapidAttempts = prefs.getInt(KEY_STAHL_RAPID_ATTEMPTS, -1)
        
        return if (rapidAttempts == -1) {
            // No custom config, use preset
            getSTAHLEmergencyPreset().createConfig()
        } else {
            // Use custom config
            STAHLEmergencyConfig(
                rapidRetryAttempts = prefs.getInt(KEY_STAHL_RAPID_ATTEMPTS, 3),
                rapidRetryDelayMs = prefs.getLong(KEY_STAHL_RAPID_DELAY, 1000L),
                enableUnlimitedRetries = prefs.getBoolean(KEY_STAHL_UNLIMITED_RETRIES, true),
                persistentRetryDelayMs = prefs.getLong(KEY_STAHL_PERSISTENT_DELAY, 2000L),
                maxBackoffDelayMs = prefs.getLong(KEY_STAHL_MAX_BACKOFF, 10000L),
                reAlertInterval = prefs.getInt(KEY_STAHL_REALERT_INTERVAL, 10),
                enableEmergencySound = prefs.getBoolean(KEY_STAHL_SOUND_ENABLED, true),
                emergencySoundType = getEmergencySoundType()
            ).validate()
        }
    }
    
    /**
     * Set custom STAHL emergency configuration.
     * Allows advanced users to fine-tune retry behavior.
     */
    fun setSTAHLEmergencyConfig(config: STAHLEmergencyConfig) {
        val validated = config.validate()
        prefs.edit().apply {
            putInt(KEY_STAHL_RAPID_ATTEMPTS, validated.rapidRetryAttempts)
            putLong(KEY_STAHL_RAPID_DELAY, validated.rapidRetryDelayMs)
            putBoolean(KEY_STAHL_UNLIMITED_RETRIES, validated.enableUnlimitedRetries)
            putLong(KEY_STAHL_PERSISTENT_DELAY, validated.persistentRetryDelayMs)
            putLong(KEY_STAHL_MAX_BACKOFF, validated.maxBackoffDelayMs)
            putInt(KEY_STAHL_REALERT_INTERVAL, validated.reAlertInterval)
            putBoolean(KEY_STAHL_SOUND_ENABLED, validated.enableEmergencySound)
            putString(KEY_STAHL_SOUND_TYPE, validated.emergencySoundType.name)
            apply()
        }
    }
    
    private fun getEmergencySoundType(): EmergencySoundType {
        val value = prefs.getString(KEY_STAHL_SOUND_TYPE, EmergencySoundType.CRITICAL.name)
        return try {
            EmergencySoundType.valueOf(value ?: EmergencySoundType.CRITICAL.name)
        } catch (e: IllegalArgumentException) {
            EmergencySoundType.CRITICAL
        }
    }
    
    // ========================================================================
    // BUILD #364: MULTI-POSITION CONFIGURATION
    // ========================================================================
    
    /**
     * Get maximum positions per symbol.
     * Returns null if unlimited (AI Board decides).
     */
    fun getMaxPositionsPerSymbol(): Int? {
        val value = prefs.getInt(KEY_MAX_POSITIONS_PER_SYMBOL, DEFAULT_MAX_POSITIONS_PER_SYMBOL)
        return if (value == -1) null else value
    }
    
    fun setMaxPositionsPerSymbol(max: Int?) {
        prefs.edit().putInt(KEY_MAX_POSITIONS_PER_SYMBOL, max ?: -1).apply()
    }
    
    fun getMaxTotalPositions(): Int = 
        prefs.getInt(KEY_MAX_TOTAL_POSITIONS, DEFAULT_MAX_TOTAL_POSITIONS)
    
    fun setMaxTotalPositions(max: Int) {
        prefs.edit().putInt(KEY_MAX_TOTAL_POSITIONS, max).apply()
    }
    
    fun getMaxConcentrationPercent(): Double = 
        prefs.getFloat(KEY_MAX_CONCENTRATION_PERCENT, DEFAULT_MAX_CONCENTRATION.toFloat()).toDouble()
    
    fun setMaxConcentrationPercent(percent: Double) {
        prefs.edit().putFloat(KEY_MAX_CONCENTRATION_PERCENT, percent.toFloat()).apply()
    }
    
    fun getMinMarginPercent(): Double = 
        prefs.getFloat(KEY_MIN_MARGIN_PERCENT, DEFAULT_MIN_MARGIN.toFloat()).toDouble()
    
    fun setMinMarginPercent(percent: Double) {
        prefs.edit().putFloat(KEY_MIN_MARGIN_PERCENT, percent.toFloat()).apply()
    }
    
    fun getMinConfidenceForMultiple(): Double = 
        prefs.getFloat(KEY_MIN_CONFIDENCE_FOR_MULTIPLE, DEFAULT_MIN_CONFIDENCE_MULTIPLE.toFloat()).toDouble()
    
    fun setMinConfidenceForMultiple(confidence: Double) {
        prefs.edit().putFloat(KEY_MIN_CONFIDENCE_FOR_MULTIPLE, confidence.toFloat()).apply()
    }
    
    fun getLimitCorrelatedPositions(): Boolean = 
        prefs.getBoolean(KEY_LIMIT_CORRELATED_POSITIONS, DEFAULT_LIMIT_CORRELATED)
    
    fun setLimitCorrelatedPositions(limit: Boolean) {
        prefs.edit().putBoolean(KEY_LIMIT_CORRELATED_POSITIONS, limit).apply()
    }
    
    fun getMaxCorrelation(): Double = 
        prefs.getFloat(KEY_MAX_CORRELATION, DEFAULT_MAX_CORRELATION.toFloat()).toDouble()
    
    fun setMaxCorrelation(correlation: Double) {
        prefs.edit().putFloat(KEY_MAX_CORRELATION, correlation.toFloat()).apply()
    }
    
    /**
     * Get complete multi-position configuration.
     */
    fun getMultiPositionConfig(): com.miwealth.sovereignvantage.core.trading.MultiPositionConfig {
        return com.miwealth.sovereignvantage.core.trading.MultiPositionConfig(
            maxPositionsPerSymbol = getMaxPositionsPerSymbol(),
            maxTotalPositions = getMaxTotalPositions(),
            maxConcentrationPercent = getMaxConcentrationPercent(),
            minMarginPercent = getMinMarginPercent(),
            minConfidenceForMultiple = getMinConfidenceForMultiple(),
            limitCorrelatedPositions = getLimitCorrelatedPositions(),
            maxCorrelation = getMaxCorrelation()
        )
    }
    
    /**
     * Set complete multi-position configuration.
     */
    fun setMultiPositionConfig(config: com.miwealth.sovereignvantage.core.trading.MultiPositionConfig) {
        prefs.edit().apply {
            putInt(KEY_MAX_POSITIONS_PER_SYMBOL, config.maxPositionsPerSymbol ?: -1)
            putInt(KEY_MAX_TOTAL_POSITIONS, config.maxTotalPositions)
            putFloat(KEY_MAX_CONCENTRATION_PERCENT, config.maxConcentrationPercent.toFloat())
            putFloat(KEY_MIN_MARGIN_PERCENT, config.minMarginPercent.toFloat())
            putFloat(KEY_MIN_CONFIDENCE_FOR_MULTIPLE, config.minConfidenceForMultiple.toFloat())
            putBoolean(KEY_LIMIT_CORRELATED_POSITIONS, config.limitCorrelatedPositions)
            putFloat(KEY_MAX_CORRELATION, config.maxCorrelation.toFloat())
            apply()
        }
    }
    
    // ========================================================================
    // BUILD #364: SYMBOL FILTER CONFIGURATION
    // ========================================================================
    
    fun getEnableStableToStable(): Boolean = 
        prefs.getBoolean(KEY_ENABLE_STABLE_TO_STABLE, DEFAULT_ENABLE_STABLE_TO_STABLE)
    
    fun setEnableStableToStable(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_STABLE_TO_STABLE, enabled).apply()
    }
    
    fun getAutoEnableOnDepeg(): Boolean = 
        prefs.getBoolean(KEY_AUTO_ENABLE_ON_DEPEG, DEFAULT_AUTO_ENABLE_DEPEG)
    
    fun setAutoEnableOnDepeg(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ENABLE_ON_DEPEG, enabled).apply()
    }
    
    fun getDepegThresholdPercent(): Double = 
        prefs.getFloat(KEY_DEPEG_THRESHOLD, DEFAULT_DEPEG_THRESHOLD.toFloat()).toDouble()
    
    fun setDepegThresholdPercent(threshold: Double) {
        prefs.edit().putFloat(KEY_DEPEG_THRESHOLD, threshold.toFloat()).apply()
    }
    
    /**
     * Get enabled symbol categories as comma-separated string.
     */
    fun getEnabledCategories(): Set<String> {
        val defaultCategories = com.miwealth.sovereignvantage.core.trading.SymbolCategory.values()
            .filter { it.defaultEnabled }
            .map { it.name }
            .joinToString(",")
        
        val saved = prefs.getString(KEY_ENABLED_CATEGORIES, defaultCategories) ?: defaultCategories
        return saved.split(",").filter { it.isNotBlank() }.toSet()
    }
    
    fun setEnabledCategories(categories: Set<String>) {
        prefs.edit().putString(KEY_ENABLED_CATEGORIES, categories.joinToString(",")).apply()
    }
    
    /**
     * Get complete symbol filter configuration.
     */
    fun getSymbolFilterConfig(): com.miwealth.sovereignvantage.core.trading.SymbolFilterConfig {
        val enabledCategoryNames = getEnabledCategories()
        val enabledCategories = com.miwealth.sovereignvantage.core.trading.SymbolCategory.values()
            .filter { it.name in enabledCategoryNames }
            .toSet()
        
        return com.miwealth.sovereignvantage.core.trading.SymbolFilterConfig(
            enabledCategories = enabledCategories,
            enableStableToStable = getEnableStableToStable(),
            autoEnableOnDepeg = getAutoEnableOnDepeg(),
            depegThresholdPercent = getDepegThresholdPercent()
        )
    }

    fun clearAllSettings() {
        prefs.edit().clear().apply()
    }
}

/**
 * BUILD #273: Trading Aggressiveness Levels
 * 
 * Controls AI Board confidence and agreement thresholds.
 * Gives users sovereign control over their risk appetite.
 * 
 * | Level        | Min Confidence | Min Agreement | Use Case                              |
 * |--------------|----------------|---------------|---------------------------------------|
 * | CONSERVATIVE | 60%            | 5 of 8 (62%)  | Risk-averse, high conviction only     |
 * | MODERATE     | 40%            | 4 of 8 (50%)  | Balanced approach (DEFAULT)           |
 * | AGGRESSIVE   | 25%            | 3 of 8 (38%)  | Opportunistic, more trades            |
 */
enum class TradingAggressiveness(
    val displayName: String,
    val description: String
) {
    CONSERVATIVE(
        "Conservative",
        "Higher confidence required. Fewer, higher-conviction trades."
    ),
    MODERATE(
        "Moderate", 
        "Balanced approach. Good for most traders."
    ),
    AGGRESSIVE(
        "Aggressive",
        "Lower confidence threshold. More trading opportunities."
    )
}
