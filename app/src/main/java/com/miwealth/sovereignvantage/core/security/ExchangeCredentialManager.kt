package com.miwealth.sovereignvantage.core.security

import android.content.Context
import com.miwealth.sovereignvantage.core.exchange.ExchangeCredentials
import com.miwealth.sovereignvantage.core.exchange.SupportedExchange
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * EXCHANGE CREDENTIAL MANAGER
 * 
 * Securely stores and retrieves exchange API credentials using
 * Android's EncryptedSharedPreferences (AES-256-GCM).
 * 
 * All credentials are encrypted at rest and never leave the device.
 * For additional security, consider adding PQC layer in future versions.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */

@Singleton
class ExchangeCredentialManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Prefix for credential keys
        private const val KEY_PREFIX = "exchange_cred_"
        private const val KEY_API_KEY_SUFFIX = "_api_key"
        private const val KEY_API_SECRET_SUFFIX = "_api_secret"
        private const val KEY_PASSPHRASE_SUFFIX = "_passphrase"
        private const val KEY_TESTNET_SUFFIX = "_testnet"
        private const val KEY_ENABLED_SUFFIX = "_enabled"
        
        // List of configured exchanges
        private const val KEY_CONFIGURED_EXCHANGES = "configured_exchanges"
        
        // Paper trading settings
        private const val KEY_PAPER_TRADING_ENABLED = "paper_trading_enabled"
        private const val KEY_PAPER_TRADING_BALANCE = "paper_trading_balance"
        private const val KEY_PREFERRED_EXCHANGE = "preferred_exchange"
    }
    
    private val prefs by lazy { EncryptedPrefsManager.getMainPrefs(context) }
    
    // ========================================================================
    // SAVE CREDENTIALS
    // ========================================================================
    
    /**
     * Save exchange credentials securely.
     * 
     * @param exchangeId Exchange identifier (e.g., "kraken", "coinbase")
     * @param apiKey The API key
     * @param apiSecret The API secret
     * @param passphrase Optional passphrase (required by some exchanges like Coinbase)
     * @param isTestnet Whether this is for testnet/sandbox environment
     */
    fun saveCredentials(
        exchangeId: String,
        apiKey: String,
        apiSecret: String,
        passphrase: String? = null,
        isTestnet: Boolean = false
    ) {
        val editor = prefs.edit()
        
        val prefix = "$KEY_PREFIX${exchangeId.lowercase()}"
        editor.putString("${prefix}${KEY_API_KEY_SUFFIX}", apiKey)
        editor.putString("${prefix}${KEY_API_SECRET_SUFFIX}", apiSecret)
        editor.putBoolean("${prefix}${KEY_TESTNET_SUFFIX}", isTestnet)
        editor.putBoolean("${prefix}${KEY_ENABLED_SUFFIX}", true)
        
        if (passphrase != null) {
            editor.putString("${prefix}${KEY_PASSPHRASE_SUFFIX}", passphrase)
        }
        
        // Update configured exchanges list
        val configured = getConfiguredExchangeIds().toMutableSet()
        configured.add(exchangeId.lowercase())
        editor.putStringSet(KEY_CONFIGURED_EXCHANGES, configured)
        
        editor.apply()
    }
    
    // ========================================================================
    // LOAD CREDENTIALS
    // ========================================================================
    
    /**
     * Load credentials for a specific exchange.
     * 
     * @param exchangeId Exchange identifier
     * @return ExchangeCredentials if found and enabled, null otherwise
     */
    fun getCredentials(exchangeId: String): ExchangeCredentials? {
        val prefix = "$KEY_PREFIX${exchangeId.lowercase()}"
        
        val enabled = prefs.getBoolean("${prefix}${KEY_ENABLED_SUFFIX}", false)
        if (!enabled) return null
        
        val apiKey = prefs.getString("${prefix}${KEY_API_KEY_SUFFIX}", null)
        val apiSecret = prefs.getString("${prefix}${KEY_API_SECRET_SUFFIX}", null)
        
        if (apiKey.isNullOrBlank() || apiSecret.isNullOrBlank()) return null
        
        val passphrase = prefs.getString("${prefix}${KEY_PASSPHRASE_SUFFIX}", null)
        val isTestnet = prefs.getBoolean("${prefix}${KEY_TESTNET_SUFFIX}", false)
        
        return ExchangeCredentials(
            exchangeId = exchangeId.lowercase(),
            apiKey = apiKey,
            apiSecret = apiSecret,
            passphrase = passphrase,
            isTestnet = isTestnet
        )
    }
    
    /**
     * Get credentials for a SupportedExchange enum.
     */
    fun getCredentials(exchange: SupportedExchange): ExchangeCredentials? {
        return getCredentials(exchange.name.lowercase())
    }
    
    /**
     * Load all configured exchange credentials.
     * 
     * @return Map of exchange ID to credentials
     */
    fun getAllCredentials(): Map<SupportedExchange, ExchangeCredentials> {
        val result = mutableMapOf<SupportedExchange, ExchangeCredentials>()
        
        for (exchangeId in getConfiguredExchangeIds()) {
            val credentials = getCredentials(exchangeId)
            if (credentials != null) {
                val exchange = try {
                    SupportedExchange.valueOf(exchangeId.uppercase())
                } catch (e: IllegalArgumentException) {
                    null
                }
                
                if (exchange != null) {
                    result[exchange] = credentials
                }
            }
        }
        
        return result
    }
    
    // ========================================================================
    // MANAGE CREDENTIALS
    // ========================================================================
    
    /**
     * Get list of configured exchange IDs.
     */
    fun getConfiguredExchangeIds(): Set<String> {
        return prefs.getStringSet(KEY_CONFIGURED_EXCHANGES, emptySet()) ?: emptySet()
    }
    
    /**
     * Check if an exchange is configured.
     */
    fun isExchangeConfigured(exchangeId: String): Boolean {
        val prefix = "$KEY_PREFIX${exchangeId.lowercase()}"
        return prefs.getBoolean("${prefix}${KEY_ENABLED_SUFFIX}", false)
    }
    
    /**
     * Disable an exchange (keeps credentials but marks as disabled).
     */
    fun disableExchange(exchangeId: String) {
        val prefix = "$KEY_PREFIX${exchangeId.lowercase()}"
        prefs.edit().putBoolean("${prefix}${KEY_ENABLED_SUFFIX}", false).apply()
    }
    
    /**
     * Enable a previously disabled exchange.
     */
    fun enableExchange(exchangeId: String) {
        val prefix = "$KEY_PREFIX${exchangeId.lowercase()}"
        prefs.edit().putBoolean("${prefix}${KEY_ENABLED_SUFFIX}", true).apply()
    }
    
    /**
     * Completely remove credentials for an exchange.
     */
    fun removeCredentials(exchangeId: String) {
        val prefix = "$KEY_PREFIX${exchangeId.lowercase()}"
        val editor = prefs.edit()
        
        editor.remove("${prefix}${KEY_API_KEY_SUFFIX}")
        editor.remove("${prefix}${KEY_API_SECRET_SUFFIX}")
        editor.remove("${prefix}${KEY_PASSPHRASE_SUFFIX}")
        editor.remove("${prefix}${KEY_TESTNET_SUFFIX}")
        editor.remove("${prefix}${KEY_ENABLED_SUFFIX}")
        
        // Update configured exchanges list
        val configured = getConfiguredExchangeIds().toMutableSet()
        configured.remove(exchangeId.lowercase())
        editor.putStringSet(KEY_CONFIGURED_EXCHANGES, configured)
        
        editor.apply()
    }
    
    /**
     * Remove all exchange credentials ("Purge My Data" feature).
     */
    fun clearAllCredentials() {
        val editor = prefs.edit()
        
        for (exchangeId in getConfiguredExchangeIds()) {
            val prefix = "$KEY_PREFIX${exchangeId.lowercase()}"
            editor.remove("${prefix}${KEY_API_KEY_SUFFIX}")
            editor.remove("${prefix}${KEY_API_SECRET_SUFFIX}")
            editor.remove("${prefix}${KEY_PASSPHRASE_SUFFIX}")
            editor.remove("${prefix}${KEY_TESTNET_SUFFIX}")
            editor.remove("${prefix}${KEY_ENABLED_SUFFIX}")
        }
        
        editor.remove(KEY_CONFIGURED_EXCHANGES)
        editor.apply()
    }
    
    // ========================================================================
    // PAPER TRADING SETTINGS
    // ========================================================================
    
    /**
     * Check if paper trading is enabled.
     */
    fun isPaperTradingEnabled(): Boolean {
        return prefs.getBoolean(KEY_PAPER_TRADING_ENABLED, true)  // Default to paper trading
    }
    
    /**
     * Set paper trading mode.
     */
    fun setPaperTradingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PAPER_TRADING_ENABLED, enabled).apply()
    }
    
    /**
     * Get paper trading starting balance.
     */
    fun getPaperTradingBalance(): Double {
        return prefs.getFloat(KEY_PAPER_TRADING_BALANCE, 10000f).toDouble()
    }
    
    /**
     * Set paper trading starting balance.
     */
    fun setPaperTradingBalance(balance: Double) {
        prefs.edit().putFloat(KEY_PAPER_TRADING_BALANCE, balance.toFloat()).apply()
    }
    
    // ========================================================================
    // PREFERRED EXCHANGE
    // ========================================================================
    
    /**
     * Get the preferred exchange for trading.
     */
    fun getPreferredExchange(): SupportedExchange {
        val exchangeId = prefs.getString(KEY_PREFERRED_EXCHANGE, "kraken") ?: "kraken"
        return try {
            SupportedExchange.valueOf(exchangeId.uppercase())
        } catch (e: IllegalArgumentException) {
            SupportedExchange.KRAKEN
        }
    }
    
    /**
     * Set the preferred exchange for trading.
     */
    fun setPreferredExchange(exchange: SupportedExchange) {
        prefs.edit().putString(KEY_PREFERRED_EXCHANGE, exchange.name.lowercase()).apply()
    }
    
    /**
     * Set preferred exchange by ID string.
     */
    fun setPreferredExchange(exchangeId: String) {
        prefs.edit().putString(KEY_PREFERRED_EXCHANGE, exchangeId.lowercase()).apply()
    }
    
    // ========================================================================
    // VALIDATION
    // ========================================================================
    
    /**
     * Check if we have any live trading credentials configured.
     */
    fun hasLiveTradingCredentials(): Boolean {
        return getAllCredentials().isNotEmpty()
    }
    
    /**
     * Get count of configured exchanges.
     */
    fun getConfiguredExchangeCount(): Int {
        return getConfiguredExchangeIds().size
    }
}
