package com.miwealth.sovereignvantage.core.exchange.ai

/**
 * AI EXCHANGE INTERFACE - Testnet Configuration
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Maps production exchange IDs to their testnet/sandbox variants
 * for use with AIConnectionManager.KNOWN_EXCHANGES.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

/**
 * Testnet configuration for supported exchanges.
 * 
 * Maps production exchange IDs to their AIConnectionManager KNOWN_EXCHANGES
 * testnet key, and tracks which exchanges require a passphrase.
 * 
 * Usage:
 * ```kotlin
 * val testnetId = ExchangeTestnetConfig.getTestnetId("binance")  // "binance_testnet"
 * val hasTestnet = ExchangeTestnetConfig.hasTestnet("gateio")    // false
 * val needsPass = ExchangeTestnetConfig.requiresPassphrase("coinbase")  // true
 * ```
 */


object ExchangeTestnetConfig {
    
    /**
     * Production exchange ID → testnet KNOWN_EXCHANGES key.
     * Only exchanges with a testnet/sandbox environment are listed.
     */
    private val testnetMapping = mapOf(
        "binance"  to "binance_testnet",
        "bybit"    to "bybit_testnet",
        "coinbase"  to "coinbase_sandbox",
        "kucoin"   to "kucoin_sandbox",
        "gemini"   to "gemini_sandbox",
        "uphold"   to "uphold_sandbox"
    )
    
    /**
     * Exchanges that require a passphrase in addition to API key + secret.
     */
    private val passphraseExchanges = setOf(
        "coinbase",
        "kucoin"
    )
    
    /**
     * Get the testnet KNOWN_EXCHANGES key for a production exchange ID.
     * 
     * @param exchangeId Production exchange ID (e.g. "binance")
     * @return Testnet key (e.g. "binance_testnet") or null if no testnet exists
     */
    fun getTestnetId(exchangeId: String): String? {
        return testnetMapping[exchangeId.lowercase()]
    }
    
    /**
     * Check if an exchange has a testnet/sandbox environment.
     */
    fun hasTestnet(exchangeId: String): Boolean {
        return testnetMapping.containsKey(exchangeId.lowercase())
    }
    
    /**
     * Check if an exchange requires a passphrase.
     */
    fun requiresPassphrase(exchangeId: String): Boolean {
        return passphraseExchanges.contains(exchangeId.lowercase())
    }
    
    /**
     * Resolve the correct AIConnectionManager key based on testnet flag.
     * 
     * When testnet is true and the exchange has a testnet variant,
     * returns the testnet key. Otherwise returns the production key.
     * 
     * @param exchangeId Production exchange ID
     * @param isTestnet Whether to use testnet
     * @return The AIConnectionManager KNOWN_EXCHANGES key to use
     */
    fun resolveConnectionId(exchangeId: String, isTestnet: Boolean): String {
        if (!isTestnet) return exchangeId.lowercase()
        return getTestnetId(exchangeId) ?: exchangeId.lowercase()
    }
    
    /**
     * Get all exchanges that support testnet, with display info.
     */
    fun getTestnetCapableExchanges(): List<TestnetExchangeInfo> {
        return testnetMapping.map { (prodId, testnetId) ->
            val info = AIConnectionManager.KNOWN_EXCHANGES[testnetId]
            TestnetExchangeInfo(
                productionId = prodId,
                testnetId = testnetId,
                testnetName = info?.name ?: "${prodId.replaceFirstChar { it.uppercase() }} Testnet",
                testnetBaseUrl = info?.baseUrl ?: "",
                requiresPassphrase = requiresPassphrase(prodId)
            )
        }
    }
}

/**
 * Display info for a testnet-capable exchange.
 */
data class TestnetExchangeInfo(
    val productionId: String,
    val testnetId: String,
    val testnetName: String,
    val testnetBaseUrl: String,
    val requiresPassphrase: Boolean
)
