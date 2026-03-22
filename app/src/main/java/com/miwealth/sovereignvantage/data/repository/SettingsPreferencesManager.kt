package com.miwealth.sovereignvantage.data.repository

import android.content.Context
import android.content.SharedPreferences
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
    
    fun clearAllSettings() {
        prefs.edit().clear().apply()
    }
}
