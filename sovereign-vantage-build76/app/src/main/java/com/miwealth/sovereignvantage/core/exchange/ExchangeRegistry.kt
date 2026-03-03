package com.miwealth.sovereignvantage.core.exchange

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import com.miwealth.sovereignvantage.core.*
import com.miwealth.sovereignvantage.core.trading.engine.OrderRequest
import com.miwealth.sovereignvantage.core.trading.engine.OrderExecutionResult
import com.miwealth.sovereignvantage.core.trading.engine.ExecutedOrder
import com.miwealth.sovereignvantage.core.trading.engine.TimeInForce
import com.miwealth.sovereignvantage.core.exchange.connectors.KrakenConnector
import com.miwealth.sovereignvantage.core.exchange.connectors.BinanceConnector
import com.miwealth.sovereignvantage.core.exchange.connectors.CoinbaseConnector
import com.miwealth.sovereignvantage.core.exchange.connectors.BybitConnector
import com.miwealth.sovereignvantage.core.exchange.connectors.OKXConnector
import com.miwealth.sovereignvantage.core.exchange.connectors.KuCoinConnector
import com.miwealth.sovereignvantage.core.exchange.connectors.GateIOConnector
import com.miwealth.sovereignvantage.core.exchange.connectors.MEXCConnector
import com.miwealth.sovereignvantage.core.exchange.connectors.BitgetConnector
import com.miwealth.sovereignvantage.core.exchange.connectors.HTXConnector
import com.miwealth.sovereignvantage.core.exchange.connectors.GeminiConnector
import com.miwealth.sovereignvantage.core.exchange.connectors.UpholdConnector
import com.miwealth.sovereignvantage.core.security.pqc.hybrid.*

/**
 * EXCHANGE REGISTRY (PQC-INTEGRATED)
 * 
 * Central registry and factory for all exchange connectors with
 * full hybrid post-quantum cryptography protection.
 * 
 * Provides:
 * - Easy creation of exchange connectors with PQC security
 * - Management of connected exchanges
 * - Persistence of exchange credentials (Kyber-encrypted in vault)
 * - Exchange health monitoring
 * - Security status reporting
 * 
 * Security Integration:
 * - PQCCredentialVault for API key storage (Android Keystore + Kyber)
 * - HybridSecureHttpClient for all exchange communications
 * - Dilithium-signed audit trail for all requests
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

/**
 * Supported exchanges
 */
enum class SupportedExchange(
    val id: String,
    val displayName: String,
    val type: ExchangeType,
    val logoUrl: String = "",
    val baseUrl: String,
    val wsUrl: String,
    val testnetBaseUrl: String = "",
    val testnetWsUrl: String = ""
) {
    // =========================================================================
    // CENTRALISED EXCHANGES (CEX)
    // =========================================================================
    
    KRAKEN(
        id = "kraken",
        displayName = "Kraken",
        type = ExchangeType.CEX,
        baseUrl = "https://api.kraken.com",
        wsUrl = "wss://ws.kraken.com",
        testnetBaseUrl = "https://api.demo-futures.kraken.com",
        testnetWsUrl = "wss://demo-futures.kraken.com/ws/v1"
    ),
    
    COINBASE(
        id = "coinbase",
        displayName = "Coinbase",
        type = ExchangeType.CEX,
        baseUrl = "https://api.exchange.coinbase.com",
        wsUrl = "wss://ws-feed.exchange.coinbase.com",
        testnetBaseUrl = "https://api-public.sandbox.exchange.coinbase.com",
        testnetWsUrl = "wss://ws-feed-public.sandbox.exchange.coinbase.com"
    ),
    
    BINANCE(
        id = "binance",
        displayName = "Binance",
        type = ExchangeType.CEX,
        baseUrl = "https://api.binance.com",
        wsUrl = "wss://stream.binance.com:9443/ws",
        testnetBaseUrl = "https://testnet.binance.vision",
        testnetWsUrl = "wss://testnet.binance.vision/ws"
    ),
    
    BYBIT(
        id = "bybit",
        displayName = "Bybit",
        type = ExchangeType.CEX,
        baseUrl = "https://api.bybit.com",
        wsUrl = "wss://stream.bybit.com/v5/public/spot",
        testnetBaseUrl = "https://api-testnet.bybit.com",
        testnetWsUrl = "wss://stream-testnet.bybit.com/v5/public/spot"
    ),
    
    OKX(
        id = "okx",
        displayName = "OKX",
        type = ExchangeType.CEX,
        baseUrl = "https://www.okx.com",
        wsUrl = "wss://ws.okx.com:8443/ws/v5/public",
        testnetBaseUrl = "https://www.okx.com",
        testnetWsUrl = "wss://wspap.okx.com:8443/ws/v5/public?brokerId=9999"
    ),
    
    KUCOIN(
        id = "kucoin",
        displayName = "KuCoin",
        type = ExchangeType.CEX,
        baseUrl = "https://api.kucoin.com",
        wsUrl = "",  // KuCoin requires token for WS URL
        testnetBaseUrl = "https://openapi-sandbox.kucoin.com",
        testnetWsUrl = ""
    ),
    
    GATEIO(
        id = "gateio",
        displayName = "Gate.io",
        type = ExchangeType.CEX,
        baseUrl = "https://api.gateio.ws",
        wsUrl = "wss://api.gateio.ws/ws/v4/"
    ),
    
    MEXC(
        id = "mexc",
        displayName = "MEXC",
        type = ExchangeType.CEX,
        baseUrl = "https://api.mexc.com",
        wsUrl = "wss://wbs.mexc.com/ws"
    ),
    
    BITGET(
        id = "bitget",
        displayName = "Bitget",
        type = ExchangeType.CEX,
        baseUrl = "https://api.bitget.com",
        wsUrl = "wss://ws.bitget.com/v2/ws/public"
    ),
    
    HTX(
        id = "htx",
        displayName = "HTX (Huobi)",
        type = ExchangeType.CEX,
        baseUrl = "https://api.huobi.pro",
        wsUrl = "wss://api.huobi.pro/ws"
    ),
    
    GEMINI(
        id = "gemini",
        displayName = "Gemini",
        type = ExchangeType.CEX,
        baseUrl = "https://api.gemini.com",
        wsUrl = "wss://api.gemini.com/v2/marketdata",
        testnetBaseUrl = "https://api.sandbox.gemini.com",
        testnetWsUrl = "wss://api.sandbox.gemini.com/v2/marketdata"
    ),
    
    UPHOLD(
        id = "uphold",
        displayName = "Uphold",
        type = ExchangeType.CEX,
        baseUrl = "https://api.uphold.com",
        wsUrl = "",  // REST only
        testnetBaseUrl = "https://api-sandbox.uphold.com",
        testnetWsUrl = ""
    ),
    
    // =========================================================================
    // DECENTRALISED EXCHANGES (DEX) - AMM
    // =========================================================================
    
    UNISWAP_V3(
        id = "uniswap_v3",
        displayName = "Uniswap V3",
        type = ExchangeType.DEX_AMM,
        baseUrl = "https://api.thegraph.com/subgraphs/name/uniswap/uniswap-v3",
        wsUrl = ""
    ),
    
    SUSHISWAP(
        id = "sushiswap",
        displayName = "SushiSwap",
        type = ExchangeType.DEX_AMM,
        baseUrl = "https://api.thegraph.com/subgraphs/name/sushi-v3/v3-ethereum",
        wsUrl = ""
    ),
    
    PANCAKESWAP(
        id = "pancakeswap",
        displayName = "PancakeSwap",
        type = ExchangeType.DEX_AMM,
        baseUrl = "https://api.thegraph.com/subgraphs/name/pancakeswap/exchange-v3-bsc",
        wsUrl = ""
    ),
    
    CURVE(
        id = "curve",
        displayName = "Curve Finance",
        type = ExchangeType.DEX_AMM,
        baseUrl = "https://api.curve.fi",
        wsUrl = ""
    ),
    
    // =========================================================================
    // SOLANA DEXes
    // =========================================================================
    
    JUPITER(
        id = "jupiter",
        displayName = "Jupiter",
        type = ExchangeType.DEX_AGGREGATOR,
        baseUrl = "https://quote-api.jup.ag/v6",
        wsUrl = ""
    ),
    
    RAYDIUM(
        id = "raydium",
        displayName = "Raydium",
        type = ExchangeType.DEX_AMM,
        baseUrl = "https://api.raydium.io",
        wsUrl = ""
    ),
    
    ORCA(
        id = "orca",
        displayName = "Orca",
        type = ExchangeType.DEX_AMM,
        baseUrl = "https://api.orca.so",
        wsUrl = ""
    ),
    
    // =========================================================================
    // DEX ORDER BOOKS
    // =========================================================================
    
    DYDX(
        id = "dydx",
        displayName = "dYdX",
        type = ExchangeType.DEX_ORDERBOOK,
        baseUrl = "https://api.dydx.exchange",
        wsUrl = "wss://api.dydx.exchange/v3/ws",
        testnetBaseUrl = "https://api.stage.dydx.exchange",
        testnetWsUrl = "wss://api.stage.dydx.exchange/v3/ws"
    ),
    
    // =========================================================================
    // AGGREGATORS
    // =========================================================================
    
    ONEINCH(
        id = "1inch",
        displayName = "1inch",
        type = ExchangeType.DEX_AGGREGATOR,
        baseUrl = "https://api.1inch.dev/swap/v6.0",
        wsUrl = ""
    ),
    
    PARASWAP(
        id = "paraswap",
        displayName = "ParaSwap",
        type = ExchangeType.DEX_AGGREGATOR,
        baseUrl = "https://apiv5.paraswap.io",
        wsUrl = ""
    );
    
    /**
     * Get the appropriate URLs based on testnet flag
     */
    fun getUrls(testnet: Boolean): Pair<String, String> {
        return if (testnet && testnetBaseUrl.isNotEmpty()) {
            testnetBaseUrl to testnetWsUrl
        } else {
            baseUrl to wsUrl
        }
    }
}

/**
 * Exchange credentials
 * V5.17.0: Unified - now the single source of truth (AI duplicate removed)
 */
data class ExchangeCredentials(
    val exchangeId: String,
    val apiKey: String,
    val apiSecret: String,
    val passphrase: String? = null,    // For Coinbase, KuCoin
    val subaccountId: String? = null,
    val isTestnet: Boolean = false
)

/**
 * Exchange health status
 */
data class ExchangeHealth(
    val exchangeId: String,
    val status: ExchangeStatus,
    val latencyMs: Long,
    val lastSuccessfulCall: Long,
    val errorCount: Int,
    val isHealthy: Boolean
)

/**
 * Exchange Registry - manages all exchange connections with PQC security
 */
class ExchangeRegistry(
    private val context: Context,
    private val credentialStore: ExchangeCredentialStore,
    private val credentialVault: PQCCredentialVault? = null,  // PQC-encrypted storage
    private val pqcConfig: HybridPQCConfig = HybridPQCConfig.default()
) {
    
    private val connectors = mutableMapOf<String, UnifiedExchangeConnector>()
    private val aggregator = ExchangeAggregatorImpl()
    
    // Shared secure HTTP client (reused across connectors for efficiency)
    private val sharedSecureClient: HybridSecureHttpClient by lazy {
        HybridSecureHttpClient.create(pqcConfig)
    }
    
    /**
     * Get available exchanges
     */
    fun getAvailableExchanges(): List<SupportedExchange> = SupportedExchange.entries.toList()
    
    /**
     * Get exchanges by type
     */
    fun getExchangesByType(type: ExchangeType): List<SupportedExchange> =
        SupportedExchange.entries.filter { it.type == type }
    
    /**
     * Get CEX exchanges (includes CEX, CEX_SPOT, CEX_FUTURES, CEX_MARGIN)
     */
    fun getCEXExchanges(): List<SupportedExchange> =
        SupportedExchange.entries.filter { it.type.isCEX }
    
    /**
     * Get DEX exchanges (includes DEX_AMM, DEX_ORDERBOOK, DEX_AGGREGATOR)
     */
    fun getDEXExchanges(): List<SupportedExchange> =
        SupportedExchange.entries.filter { it.type.isDEX }
    
    /**
     * Create and connect to an exchange
     */
    suspend fun connectExchange(
        exchange: SupportedExchange,
        credentials: ExchangeCredentials
    ): UnifiedExchangeConnector? {
        // Store credentials securely
        credentialStore.saveCredentials(credentials)
        
        // Create connector
        val connector = createConnector(exchange, credentials) ?: return null
        
        // Connect
        if (!connector.connect()) {
            return null
        }
        
        // Register
        connectors[exchange.id] = connector
        aggregator.addExchange(connector)
        
        return connector
    }
    
    /**
     * Disconnect from an exchange
     */
    suspend fun disconnectExchange(exchangeId: String) {
        connectors[exchangeId]?.let { connector ->
            connector.disconnect()
            aggregator.removeExchange(exchangeId)
            connectors.remove(exchangeId)
        }
    }
    
    /**
     * Get connected exchanges
     */
    fun getConnectedExchanges(): List<UnifiedExchangeConnector> = connectors.values.toList()
    
    /**
     * Get specific connector
     */
    fun getConnector(exchangeId: String): UnifiedExchangeConnector? = connectors[exchangeId]
    
    /**
     * Get the aggregator
     */
    fun getAggregator(): ExchangeAggregator = aggregator
    
    /**
     * Check exchange health
     */
    suspend fun checkHealth(exchangeId: String): ExchangeHealth {
        val connector = connectors[exchangeId]
        
        if (connector == null) {
            return ExchangeHealth(
                exchangeId = exchangeId,
                status = ExchangeStatus.DISCONNECTED,
                latencyMs = 0,
                lastSuccessfulCall = 0,
                errorCount = 0,
                isHealthy = false
            )
        }
        
        val startTime = System.currentTimeMillis()
        val success = try {
            connector.getTicker("BTC/USD") != null
        } catch (e: Exception) {
            false
        }
        val latency = System.currentTimeMillis() - startTime
        
        return ExchangeHealth(
            exchangeId = exchangeId,
            status = connector.status.value,
            latencyMs = latency,
            lastSuccessfulCall = if (success) System.currentTimeMillis() else 0,
            errorCount = if (success) 0 else 1,
            isHealthy = success && latency < 5000
        )
    }
    
    /**
     * Reconnect all exchanges
     */
    suspend fun reconnectAll() {
        val storedCredentials = credentialStore.getAllCredentials()
        
        storedCredentials.forEach { credentials ->
            val exchange = SupportedExchange.entries.find { it.id == credentials.exchangeId }
            if (exchange != null && connectors[exchange.id]?.isConnected() != true) {
                connectExchange(exchange, credentials)
            }
        }
    }
    
    /**
     * Create connector for specific exchange with PQC security
     */
    private fun createConnector(
        exchange: SupportedExchange,
        credentials: ExchangeCredentials
    ): UnifiedExchangeConnector? {
        val (baseUrl, wsUrl) = exchange.getUrls(credentials.isTestnet)
        
        val config = ExchangeConfig(
            exchangeId = exchange.id,
            exchangeName = exchange.displayName,
            exchangeType = exchange.type,
            apiKey = credentials.apiKey,
            apiSecret = credentials.apiSecret,
            passphrase = credentials.passphrase ?: "",
            subaccountId = credentials.subaccountId ?: "",
            testnet = credentials.isTestnet,
            baseUrl = baseUrl,
            wsUrl = wsUrl
        )
        
        // Store credentials in PQC vault if available
        credentialVault?.let { vault ->
            if (vault.isUnlocked()) {
                vault.storeCredential(ExchangeCredential(
                    exchangeId = exchange.id,
                    exchangeName = exchange.displayName,
                    apiKey = credentials.apiKey,
                    apiSecret = credentials.apiSecret,
                    passphrase = credentials.passphrase ?: "",
                    subaccountId = credentials.subaccountId ?: "",
                    isTestnet = credentials.isTestnet
                ))
            }
        }
        
        return when (exchange) {
            SupportedExchange.KRAKEN -> KrakenConnector(
                config = config,
                secureHttpClient = sharedSecureClient,
                credentialVault = credentialVault
            )
            SupportedExchange.COINBASE -> CoinbaseConnector(
                config = config,
                secureHttpClient = sharedSecureClient,
                credentialVault = credentialVault
            )
            SupportedExchange.BINANCE -> BinanceConnector(
                config = config,
                secureHttpClient = sharedSecureClient,
                credentialVault = credentialVault
            )
            SupportedExchange.BYBIT -> BybitConnector(
                config = config,
                secureHttpClient = sharedSecureClient,
                credentialVault = credentialVault
            )
            SupportedExchange.OKX -> OKXConnector(
                config = config,
                secureHttpClient = sharedSecureClient,
                credentialVault = credentialVault
            )
            SupportedExchange.KUCOIN -> KuCoinConnector(
                config = config,
                secureHttpClient = sharedSecureClient,
                credentialVault = credentialVault
            )
            SupportedExchange.GATEIO -> GateIOConnector(
                config = config,
                secureHttpClient = sharedSecureClient,
                credentialVault = credentialVault
            )
            SupportedExchange.MEXC -> MEXCConnector(
                config = config,
                secureHttpClient = sharedSecureClient,
                credentialVault = credentialVault
            )
            SupportedExchange.BITGET -> BitgetConnector(
                config = config,
                secureHttpClient = sharedSecureClient,
                credentialVault = credentialVault
            )
            SupportedExchange.HTX -> HTXConnector(
                config = config,
                secureHttpClient = sharedSecureClient,
                credentialVault = credentialVault
            )
            SupportedExchange.GEMINI -> GeminiConnector(
                config = config,
                secureHttpClient = sharedSecureClient,
                credentialVault = credentialVault
            )
            SupportedExchange.UPHOLD -> UpholdConnector(
                config = config,
                secureHttpClient = sharedSecureClient,
                credentialVault = credentialVault
            )
            // DEX connectors - to be implemented
            else -> null
        }
    }
    
    /**
     * Get PQC security status for all connected exchanges
     */
    fun getSecurityStatus(): Map<String, ConnectorSecurityStatus> {
        return connectors.mapValues { (_, connector) ->
            (connector as? BaseCEXConnector)?.getSecurityStatus() 
                ?: ConnectorSecurityStatus(
                    exchangeId = connector.config.exchangeId,
                    pqcEnabled = false,
                    kemAlgorithm = "N/A",
                    signatureAlgorithm = "N/A",
                    hybridMode = "N/A",
                    nistSecurityLevel = 0,
                    sessionActive = false,
                    sessionExpiresAt = null,
                    credentialsFromVault = false,
                    requestSigningEnabled = false,
                    auditLogEntries = 0
                )
        }
    }
    
    /**
     * Get shared HTTP client security report
     */
    fun getHttpClientSecurityReport(): ClientSecurityReport = sharedSecureClient.getSecurityReport()
    
    /**
     * Shutdown registry and cleanup PQC resources
     */
    fun shutdown() {
        connectors.values.forEach { connector ->
            kotlinx.coroutines.runBlocking { connector.disconnect() }
        }
        connectors.clear()
        sharedSecureClient.shutdown()
    }
}

/**
 * Secure credential storage interface
 */
interface ExchangeCredentialStore {
    suspend fun saveCredentials(credentials: ExchangeCredentials)
    suspend fun getCredentials(exchangeId: String): ExchangeCredentials?
    suspend fun getAllCredentials(): List<ExchangeCredentials>
    suspend fun deleteCredentials(exchangeId: String)
    suspend fun hasCredentials(exchangeId: String): Boolean
}

// =============================================================================
// PLACEHOLDER CONNECTOR CLASSES (To be fully implemented)
// These are stubs for future implementation - use the full connectors in
// com.miwealth.sovereignvantage.core.exchange.connectors package
// =============================================================================

/**
 * Coinbase Connector placeholder - to be replaced with full implementation
 */
class CoinbaseConnectorPlaceholder(
    config: ExchangeConfig,
    secureHttpClient: HybridSecureHttpClient? = null,
    credentialVault: PQCCredentialVault? = null
) : BaseCEXConnector(config, secureHttpClient, credentialVault) {
    override val capabilities = ExchangeCapabilities(
        supportsSpotTrading = true,
        supportsFutures = false,
        supportsMargin = false,
        supportsWebSocket = true,
        maxOrdersPerSecond = 10,
        tradingFeeMaker = 0.004,
        tradingFeeTaker = 0.006
    )
    
    override val endpoints = ExchangeEndpoints(
        ticker = "/products/{symbol}/ticker",
        orderBook = "/products/{symbol}/book",
        trades = "/products/{symbol}/trades",
        candles = "/products/{symbol}/candles",
        pairs = "/products",
        balances = "/accounts",
        placeOrder = "/orders",
        cancelOrder = "/orders/{orderId}",
        getOrder = "/orders/{orderId}",
        openOrders = "/orders",
        orderHistory = "/orders",
        wsUrl = "wss://ws-feed.exchange.coinbase.com"
    )
    
    override fun signRequest(method: String, path: String, params: Map<String, String>, body: String?, timestamp: Long): Map<String, String> = emptyMap()
    override fun parseTicker(response: com.google.gson.JsonObject, symbol: String): PriceTick? = null
    override fun parseOrderBook(response: com.google.gson.JsonObject, symbol: String): OrderBook? = null
    override fun parseTradingPairs(response: com.google.gson.JsonObject): List<TradingPair> = emptyList()
    override fun parseBalances(response: com.google.gson.JsonObject): List<Balance> = emptyList()
    override fun parseOrder(response: com.google.gson.JsonObject): ExecutedOrder? = null
    override fun parsePlaceOrderResponse(response: com.google.gson.JsonObject, request: OrderRequest): OrderExecutionResult = 
        OrderExecutionResult.Error(Exception("Coinbase connector not yet implemented - use placeholder"))
    override fun buildOrderBody(request: OrderRequest): Map<String, String> = emptyMap()
    override fun handleWsMessage(text: String) {}
    override fun buildWsSubscription(channels: List<String>, symbols: List<String>): String = ""
}

/**
 * Binance Connector placeholder - to be replaced with full implementation
 */
class BinanceConnectorPlaceholder(
    config: ExchangeConfig,
    secureHttpClient: HybridSecureHttpClient? = null,
    credentialVault: PQCCredentialVault? = null
) : BaseCEXConnector(config, secureHttpClient, credentialVault) {
    override val capabilities = ExchangeCapabilities(
        supportsSpotTrading = true,
        supportsFutures = true,
        supportsMargin = true,
        supportsWebSocket = true,
        maxOrdersPerSecond = 10,
        tradingFeeMaker = 0.001,
        tradingFeeTaker = 0.001
    )
    
    override val endpoints = ExchangeEndpoints(
        ticker = "/api/v3/ticker/24hr",
        orderBook = "/api/v3/depth",
        trades = "/api/v3/trades",
        candles = "/api/v3/klines",
        pairs = "/api/v3/exchangeInfo",
        balances = "/api/v3/account",
        placeOrder = "/api/v3/order",
        cancelOrder = "/api/v3/order",
        getOrder = "/api/v3/order",
        openOrders = "/api/v3/openOrders",
        orderHistory = "/api/v3/allOrders",
        wsUrl = "wss://stream.binance.com:9443/ws"
    )
    
    override fun signRequest(method: String, path: String, params: Map<String, String>, body: String?, timestamp: Long): Map<String, String> = emptyMap()
    override fun parseTicker(response: com.google.gson.JsonObject, symbol: String): PriceTick? = null
    override fun parseOrderBook(response: com.google.gson.JsonObject, symbol: String): OrderBook? = null
    override fun parseTradingPairs(response: com.google.gson.JsonObject): List<TradingPair> = emptyList()
    override fun parseBalances(response: com.google.gson.JsonObject): List<Balance> = emptyList()
    override fun parseOrder(response: com.google.gson.JsonObject): ExecutedOrder? = null
    override fun parsePlaceOrderResponse(response: com.google.gson.JsonObject, request: OrderRequest): OrderExecutionResult = 
        OrderExecutionResult.Error(Exception("Binance connector not yet implemented - use placeholder"))
    override fun buildOrderBody(request: OrderRequest): Map<String, String> = emptyMap()
    override fun handleWsMessage(text: String) {}
    override fun buildWsSubscription(channels: List<String>, symbols: List<String>): String = ""
}