package com.miwealth.sovereignvantage.core.exchange.ai

/**
 * AI EXCHANGE INTERFACE - Schema Learner
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Learns exchange API schemas from:
 * - OpenAPI/Swagger specifications
 * - API documentation pages
 * - CCXT library definitions
 * - Trial and error probing
 * 
 * This eliminates the need for hardcoded exchange connectors by
 * dynamically understanding any exchange's API structure.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */



import android.content.Context
import android.util.Log
import com.miwealth.sovereignvantage.core.exchange.ExchangeType
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Schema Learner - Discovers and learns exchange API structures.
 * 
 * Usage:
 * ```kotlin
 * val learner = ExchangeSchemaLearner(context)
 * 
 * // Learn from OpenAPI spec
 * val schema = learner.learnFromOpenAPI("https://api.exchange.com/openapi.json")
 * 
 * // Learn from known exchange patterns
 * val schema = learner.learnExchange(SchemaLearnRequest(
 *     exchangeId = "newexchange",
 *     name = "New Exchange",
 *     type = ExchangeType.CEX_SPOT,
 *     baseUrl = "https://api.newexchange.com"
 * ))
 * ```
 */
class ExchangeSchemaLearner(
    private val context: Context
) {
    companion object {
        private const val TAG = "ExchangeSchemaLearner"
        
        // Known exchange patterns for quick matching
        private val KNOWN_PATTERNS = mapOf(
            // Binance-family exchanges (Binance, MEXC, Gate.io follow similar patterns)
            "binance" to ExchangePattern.BINANCE_STYLE,
            "mexc" to ExchangePattern.BINANCE_STYLE,
            "gateio" to ExchangePattern.BINANCE_STYLE,
            
            // Kraken-family
            "kraken" to ExchangePattern.KRAKEN_STYLE,
            
            // Coinbase-family
            "coinbase" to ExchangePattern.COINBASE_STYLE,
            "coinbasepro" to ExchangePattern.COINBASE_STYLE,
            
            // Bybit-family
            "bybit" to ExchangePattern.BYBIT_STYLE,
            "okx" to ExchangePattern.BYBIT_STYLE,
            
            // KuCoin-family
            "kucoin" to ExchangePattern.KUCOIN_STYLE,
            
            // Uphold (unique)
            "uphold" to ExchangePattern.UPHOLD_STYLE
        )
        
        // Common endpoint paths to probe
        private val PROBE_PATHS = listOf(
            // Server info
            "/api/v1/time", "/api/v3/time", "/v1/time", "/time",
            "/api/v1/ping", "/api/v3/ping", "/ping",
            
            // Exchange info / trading pairs
            "/api/v1/exchangeInfo", "/api/v3/exchangeInfo",
            "/api/v1/symbols", "/v1/symbols", "/symbols",
            "/api/v1/markets", "/v1/markets", "/markets",
            "/api/v1/instruments", "/v1/instruments", "/instruments",
            "/0/public/AssetPairs", // Kraken
            "/products", "/v2/products", // Coinbase
            
            // Ticker
            "/api/v1/ticker/24hr", "/api/v3/ticker/24hr",
            "/api/v1/ticker/price", "/api/v3/ticker/price",
            "/v1/ticker", "/ticker", "/tickers",
            "/0/public/Ticker", // Kraken
            
            // Order book
            "/api/v1/depth", "/api/v3/depth",
            "/v1/orderbook", "/orderbook",
            "/0/public/Depth" // Kraken
        )
        
        // Suffixes that indicate testnet/sandbox variants of a known exchange.
        // Stripped during pattern lookup so "binance_testnet" matches "binance".
        private val TESTNET_SUFFIXES = listOf("_testnet", "_sandbox", "_demo", "_test")
        
        /**
         * Normalize exchange ID for pattern lookup.
         * Strips testnet/sandbox suffixes so "binance_testnet" → "binance".
         */
        fun normalizeExchangeId(exchangeId: String): String {
            val lower = exchangeId.lowercase()
            for (suffix in TESTNET_SUFFIXES) {
                if (lower.endsWith(suffix)) {
                    return lower.removeSuffix(suffix)
                }
            }
            return lower
        }
        
        // Auth header patterns to try
        private val AUTH_PATTERNS = listOf(
            AuthPattern("X-MBX-APIKEY", null, SignatureAlgorithm.HMAC_SHA256),
            AuthPattern("API-Key", "API-Sign", SignatureAlgorithm.HMAC_SHA512),
            AuthPattern("KC-API-KEY", "KC-API-SIGN", SignatureAlgorithm.HMAC_SHA256),
            AuthPattern("CB-ACCESS-KEY", "CB-ACCESS-SIGN", SignatureAlgorithm.HMAC_SHA256),
            AuthPattern("X-BAPI-API-KEY", "X-BAPI-SIGN", SignatureAlgorithm.HMAC_SHA256),
            AuthPattern("OK-ACCESS-KEY", "OK-ACCESS-SIGN", SignatureAlgorithm.HMAC_SHA256)
        )
    }
    
    private val httpClient = com.miwealth.sovereignvantage.core.network.SharedHttpClient.fastClient
    
    private val gson = Gson()
    private val schemaCache = mutableMapOf<String, ExchangeSchema>()
    
    // =========================================================================
    // PUBLIC API
    // =========================================================================
    
    /**
     * Learn an exchange schema using all available methods.
     */
    suspend fun learnExchange(request: SchemaLearnRequest): SchemaLearnResult {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Learning schema for ${request.exchangeId}")
            
            try {
                // 1. Check cache first
                schemaCache[request.exchangeId]?.let {
                    Log.d(TAG, "Returning cached schema for ${request.exchangeId}")
                    return@withContext SchemaLearnResult.Success(it)
                }
                
                // 2. Try OpenAPI spec if provided
                if (request.openApiSpecUrl != null) {
                    val result = learnFromOpenAPI(request.openApiSpecUrl, request)
                    if (result is SchemaLearnResult.Success) {
                        schemaCache[request.exchangeId] = result.schema
                        return@withContext result
                    }
                }
                
                // 3. Try known patterns (normalize testnet/sandbox IDs)
                val normalizedId = normalizeExchangeId(request.exchangeId)
                val patternMatch = KNOWN_PATTERNS[normalizedId]
                if (patternMatch != null) {
                    val result = learnFromPattern(patternMatch, request)
                    if (result is SchemaLearnResult.Success) {
                        schemaCache[request.exchangeId] = result.schema
                        return@withContext result
                    }
                }
                
                // 4. Try probing
                val result = learnByProbing(request)
                if (result is SchemaLearnResult.Success) {
                    schemaCache[request.exchangeId] = result.schema
                }
                
                result
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to learn schema for ${request.exchangeId}", e)
                SchemaLearnResult.Failure(
                    reason = "Learning failed: ${e.message}",
                    suggestions = listOf(
                        "Provide OpenAPI spec URL",
                        "Check if base URL is correct",
                        "Ensure exchange API is accessible"
                    )
                )
            }
        }
    }
    
    /**
     * Learn from an OpenAPI/Swagger specification.
     */
    suspend fun learnFromOpenAPI(
        specUrl: String,
        request: SchemaLearnRequest? = null
    ): SchemaLearnResult {
        return withContext(Dispatchers.IO) {
            try {
                val specJson = fetchJson(specUrl) ?: return@withContext SchemaLearnResult.Failure(
                    "Failed to fetch OpenAPI spec from $specUrl"
                )
                
                val schema = parseOpenAPISpec(specJson, request)
                SchemaLearnResult.Success(schema)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to learn from OpenAPI", e)
                SchemaLearnResult.Failure("Failed to parse OpenAPI spec: ${e.message}")
            }
        }
    }
    
    /**
     * Get a cached schema or learn it.
     */
    suspend fun getOrLearnSchema(exchangeId: String, baseUrl: String): ExchangeSchema? {
        schemaCache[exchangeId]?.let { return it }
        
        val result = learnExchange(SchemaLearnRequest(
            exchangeId = exchangeId,
            name = exchangeId.replaceFirstChar { it.uppercase() },
            type = ExchangeType.CEX_SPOT,
            baseUrl = baseUrl
        ))
        
        return when (result) {
            is SchemaLearnResult.Success -> result.schema
            is SchemaLearnResult.PartialSuccess -> result.schema
            is SchemaLearnResult.Failure -> null
        }
    }
    
    /**
     * Clear cached schema (forces relearning).
     */
    fun clearCache(exchangeId: String? = null) {
        if (exchangeId != null) {
            schemaCache.remove(exchangeId)
        } else {
            schemaCache.clear()
        }
    }
    
    // =========================================================================
    // LEARNING METHODS
    // =========================================================================
    
    /**
     * Learn from a known exchange pattern.
     */
    private suspend fun learnFromPattern(
        pattern: ExchangePattern,
        request: SchemaLearnRequest
    ): SchemaLearnResult {
        val schema = when (pattern) {
            ExchangePattern.BINANCE_STYLE -> createBinanceStyleSchema(request)
            ExchangePattern.KRAKEN_STYLE -> createKrakenStyleSchema(request)
            ExchangePattern.COINBASE_STYLE -> createCoinbaseStyleSchema(request)
            ExchangePattern.BYBIT_STYLE -> createBybitStyleSchema(request)
            ExchangePattern.KUCOIN_STYLE -> createKuCoinStyleSchema(request)
            ExchangePattern.UPHOLD_STYLE -> createUpholdStyleSchema(request)
        }
        
        // Validate the schema by making test calls
        val validated = validateSchema(schema, request.baseUrl)
        
        return if (validated.first) {
            SchemaLearnResult.Success(
                schema = schema.copy(confidence = validated.second),
                warnings = if (validated.second < 0.8) listOf("Schema confidence is low, some endpoints may not work") else emptyList()
            )
        } else {
            SchemaLearnResult.PartialSuccess(
                schema = schema.copy(confidence = validated.second),
                missingCapabilities = listOf("Some endpoints failed validation"),
                errors = listOf("Schema partially validated")
            )
        }
    }
    
    /**
     * Learn by probing endpoints.
     */
    private suspend fun learnByProbing(request: SchemaLearnRequest): SchemaLearnResult {
        val discoveredEndpoints = mutableMapOf<String, String>()
        val warnings = mutableListOf<String>()
        
        // Probe for working endpoints
        for (path in PROBE_PATHS) {
            try {
                val response = fetchJson("${request.baseUrl}$path")
                if (response != null) {
                    val endpointType = classifyEndpoint(path, response)
                    if (endpointType != null) {
                        discoveredEndpoints[endpointType] = path
                        Log.d(TAG, "Discovered $endpointType at $path")
                    }
                }
            } catch (e: Exception) {
                // Expected for non-existent endpoints
            }
        }
        
        if (discoveredEndpoints.isEmpty()) {
            return SchemaLearnResult.Failure(
                reason = "Could not discover any working endpoints",
                suggestions = listOf(
                    "Verify base URL is correct",
                    "Check if API requires authentication for all endpoints",
                    "Provide OpenAPI specification URL"
                )
            )
        }
        
        // Build schema from discovered endpoints
        val schema = buildSchemaFromDiscovery(request, discoveredEndpoints)
        
        // Try to detect auth pattern
        val authSchema = detectAuthPattern(request)
        
        return SchemaLearnResult.Success(
            schema = schema.copy(
                auth = authSchema ?: schema.auth,
                confidence = discoveredEndpoints.size.toDouble() / PROBE_PATHS.size
            ),
            warnings = warnings
        )
    }
    
    /**
     * Parse OpenAPI specification into ExchangeSchema.
     */
    private fun parseOpenAPISpec(spec: JsonObject, request: SchemaLearnRequest?): ExchangeSchema {
        val info = spec.getAsJsonObject("info")
        val servers = spec.getAsJsonArray("servers")
        val paths = spec.getAsJsonObject("paths")
        val securitySchemes = spec.getAsJsonObject("components")
            ?.getAsJsonObject("securitySchemes")
        
        // Extract base URL
        val baseUrl = request?.baseUrl ?: servers?.firstOrNull()?.asJsonObject
            ?.get("url")?.asString ?: ""
        
        // Parse authentication
        val auth = parseOpenAPIAuth(securitySchemes)
        
        // Parse endpoints
        val endpoints = parseOpenAPIEndpoints(paths)
        
        // Parse response schemas
        val responseSchemas = parseOpenAPIResponses(spec)
        
        return ExchangeSchema(
            exchangeId = request?.exchangeId ?: info?.get("title")?.asString?.lowercase()?.replace(" ", "_") ?: "unknown",
            name = request?.name ?: info?.get("title")?.asString ?: "Unknown Exchange",
            type = request?.type ?: ExchangeType.CEX_SPOT,
            baseUrl = baseUrl,
            sandboxUrl = request?.sandboxUrl,
            wsUrl = request?.wsUrl,
            auth = auth,
            endpoints = endpoints,
            responseSchemas = responseSchemas,
            rateLimits = RateLimitSchema(),
            capabilities = CapabilitySchema(),
            symbolFormat = SymbolFormatSchema(),
            source = SchemaSource.API_SPEC,
            confidence = 0.9
        )
    }
    
    private fun parseOpenAPIAuth(securitySchemes: JsonObject?): AuthSchema {
        if (securitySchemes == null) {
            return AuthSchema(method = AuthMethod.NONE)
        }
        
        // Look for API key authentication
        for ((name, scheme) in securitySchemes.entrySet()) {
            val schemeObj = scheme.asJsonObject
            val type = schemeObj.get("type")?.asString
            
            when (type) {
                "apiKey" -> {
                    val inLocation = schemeObj.get("in")?.asString
                    val headerName = schemeObj.get("name")?.asString
                    
                    if (inLocation == "header" && headerName != null) {
                        return AuthSchema(
                            method = AuthMethod.API_KEY_ONLY,
                            apiKeyHeader = headerName
                        )
                    }
                }
                "http" -> {
                    val httpScheme = schemeObj.get("scheme")?.asString
                    if (httpScheme == "basic") {
                        return AuthSchema(method = AuthMethod.BASIC)
                    }
                }
                "oauth2" -> {
                    val flows = schemeObj.getAsJsonObject("flows")
                    val authCode = flows?.getAsJsonObject("authorizationCode")
                    if (authCode != null) {
                        return AuthSchema(
                            method = AuthMethod.OAUTH2,
                            oauth2 = OAuth2Config(
                                authorizationUrl = authCode.get("authorizationUrl")?.asString ?: "",
                                tokenUrl = authCode.get("tokenUrl")?.asString ?: "",
                                scopes = authCode.getAsJsonObject("scopes")?.keySet()?.toList() ?: emptyList()
                            )
                        )
                    }
                }
            }
        }
        
        // Default to HMAC if we found API key but signature method unclear
        return AuthSchema(
            method = AuthMethod.HMAC,
            signatureAlgorithm = SignatureAlgorithm.HMAC_SHA256
        )
    }
    
    private fun parseOpenAPIEndpoints(paths: JsonObject?): EndpointSchema {
        if (paths == null) return EndpointSchema()
        
        var ticker: EndpointConfig? = null
        var orderBook: EndpointConfig? = null
        var tradingPairs: EndpointConfig? = null
        var balances: EndpointConfig? = null
        var placeOrder: EndpointConfig? = null
        
        for ((path, methods) in paths.entrySet()) {
            val methodsObj = methods.asJsonObject
            
            // Try to classify each endpoint
            for ((method, details) in methodsObj.entrySet()) {
                val detailsObj = details.asJsonObject
                val summary = detailsObj.get("summary")?.asString?.lowercase() ?: ""
                val operationId = detailsObj.get("operationId")?.asString?.lowercase() ?: ""
                val tags = detailsObj.getAsJsonArray("tags")?.map { it.asString.lowercase() } ?: emptyList()
                
                val config = EndpointConfig(
                    method = HttpMethod.valueOf(method.uppercase()),
                    path = path,
                    authenticated = detailsObj.has("security")
                )
                
                when {
                    summary.contains("ticker") || operationId.contains("ticker") -> ticker = config
                    summary.contains("order book") || operationId.contains("orderbook") || operationId.contains("depth") -> orderBook = config
                    summary.contains("symbol") || summary.contains("market") || summary.contains("instrument") || operationId.contains("exchangeinfo") -> tradingPairs = config
                    summary.contains("balance") || operationId.contains("balance") || operationId.contains("account") -> balances = config.copy(authenticated = true)
                    (summary.contains("place") && summary.contains("order")) || operationId.contains("createorder") || operationId.contains("placeorder") -> placeOrder = config.copy(authenticated = true, method = HttpMethod.POST)
                }
            }
        }
        
        return EndpointSchema(
            ticker = ticker,
            orderBook = orderBook,
            tradingPairs = tradingPairs,
            balances = balances,
            placeOrder = placeOrder
        )
    }
    
    private fun parseOpenAPIResponses(spec: JsonObject): ResponseSchemas {
        // Basic response schemas - can be enhanced with actual schema parsing
        return ResponseSchemas(
            ticker = ResponseSchema(
                fields = mapOf(
                    "symbol" to FieldMapping("symbol", FieldType.STRING),
                    "lastPrice" to FieldMapping("lastPrice", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                    "bidPrice" to FieldMapping("bidPrice", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                    "askPrice" to FieldMapping("askPrice", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                    "volume" to FieldMapping("volume", FieldType.DOUBLE, FieldTransform.TO_DOUBLE)
                )
            ),
            error = ErrorSchema(
                codePath = "code",
                messagePath = "msg"
            )
        )
    }
    
    // =========================================================================
    // PATTERN-BASED SCHEMAS
    // =========================================================================
    
    private fun createBinanceStyleSchema(request: SchemaLearnRequest): ExchangeSchema {
        return ExchangeSchema(
            exchangeId = request.exchangeId,
            name = request.name,
            type = request.type,
            baseUrl = request.baseUrl,
            sandboxUrl = request.sandboxUrl,
            wsUrl = request.wsUrl ?: "${request.baseUrl.replace("https://", "wss://").replace("/api", "")}/ws",
            auth = AuthSchema(
                method = AuthMethod.HMAC,
                apiKeyHeader = "X-MBX-APIKEY",
                signatureAlgorithm = SignatureAlgorithm.HMAC_SHA256,
                signaturePayload = SignaturePayload.QUERY_STRING,
                timestampFormat = TimestampFormat.UNIX_MILLIS,
                timestampParam = "timestamp",
                signatureParam = "signature",
                requiresTimestamp = true,
                requiresSignature = true
            ),
            endpoints = EndpointSchema(
                serverTime = EndpointConfig(path = "/api/v3/time"),
                tradingPairs = EndpointConfig(path = "/api/v3/exchangeInfo"),
                ticker = EndpointConfig(path = "/api/v3/ticker/24hr", requiredParams = listOf(
                    ParamConfig("symbol", ParamType.STRING, ParamLocation.QUERY, false)
                )),
                orderBook = EndpointConfig(path = "/api/v3/depth", requiredParams = listOf(
                    ParamConfig("symbol", ParamType.STRING),
                    ParamConfig("limit", ParamType.INTEGER, required = false, defaultValue = "100")
                )),
                balances = EndpointConfig(path = "/api/v3/account", authenticated = true),
                placeOrder = EndpointConfig(
                    method = HttpMethod.POST,
                    path = "/api/v3/order",
                    authenticated = true,
                    requiredParams = listOf(
                        ParamConfig("symbol", ParamType.STRING),
                        ParamConfig("side", ParamType.ENUM, enumValues = listOf("BUY", "SELL")),
                        ParamConfig("type", ParamType.ENUM, enumValues = listOf("LIMIT", "MARKET", "STOP_LOSS", "STOP_LOSS_LIMIT", "TAKE_PROFIT", "TAKE_PROFIT_LIMIT")),
                        ParamConfig("quantity", ParamType.DOUBLE)
                    ),
                    optionalParams = listOf(
                        ParamConfig("price", ParamType.DOUBLE, required = false),
                        ParamConfig("timeInForce", ParamType.ENUM, required = false, enumValues = listOf("GTC", "IOC", "FOK"))
                    )
                ),
                cancelOrder = EndpointConfig(
                    method = HttpMethod.DELETE,
                    path = "/api/v3/order",
                    authenticated = true
                ),
                openOrders = EndpointConfig(path = "/api/v3/openOrders", authenticated = true),
                wsTickerChannel = "ticker",
                wsOrderBookChannel = "depth",
                wsTradesChannel = "trade"
            ),
            responseSchemas = ResponseSchemas(
                ticker = ResponseSchema(
                    fields = mapOf(
                        "symbol" to FieldMapping("symbol"),
                        "lastPrice" to FieldMapping("lastPrice", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "bidPrice" to FieldMapping("bidPrice", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "askPrice" to FieldMapping("askPrice", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "volume" to FieldMapping("volume", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "priceChange" to FieldMapping("priceChange", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "priceChangePercent" to FieldMapping("priceChangePercent", FieldType.DOUBLE, FieldTransform.TO_DOUBLE)
                    )
                ),
                tradingPairs = ResponseSchema(
                    dataPath = "symbols",
                    isArray = true,
                    fields = mapOf(
                        "symbol" to FieldMapping("symbol"),
                        "baseAsset" to FieldMapping("baseAsset"),
                        "quoteAsset" to FieldMapping("quoteAsset"),
                        "status" to FieldMapping("status")
                    )
                ),
                balances = ResponseSchema(
                    dataPath = "balances",
                    isArray = true,
                    fields = mapOf(
                        "asset" to FieldMapping("asset"),
                        "free" to FieldMapping("free", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "locked" to FieldMapping("locked", FieldType.DOUBLE, FieldTransform.TO_DOUBLE)
                    )
                ),
                error = ErrorSchema(
                    codePath = "code",
                    messagePath = "msg",
                    errorCodes = mapOf(
                        "-1003" to ErrorType.RATE_LIMITED,
                        "-1022" to ErrorType.INVALID_SIGNATURE,
                        "-2010" to ErrorType.INSUFFICIENT_BALANCE,
                        "-2011" to ErrorType.ORDER_NOT_FOUND
                    )
                )
            ),
            rateLimits = RateLimitSchema(
                requestsPerMinute = 1200,
                weightPerMinute = 1200,
                ordersPerSecond = 10,
                usedWeightHeader = "X-MBX-USED-WEIGHT-1M"
            ),
            capabilities = CapabilitySchema(
                supportsSpot = true,
                supportsFutures = true,
                supportsMargin = true,
                supportsMarketOrders = true,
                supportsLimitOrders = true,
                supportsStopOrders = true,
                supportsStopLimitOrders = true,
                supportsWebSocket = true,
                supportsOrderBookStream = true,
                supportsUserDataStream = true,
                supportsCancelAll = true,
                makerFee = 0.001,
                takerFee = 0.001
            ),
            symbolFormat = SymbolFormatSchema(
                separator = "",
                order = SymbolOrder.BASE_QUOTE,
                case = SymbolCase.UPPER
            ),
            source = SchemaSource.INFERRED,
            notes = listOf("Binance-style API pattern")
        )
    }
    
    private fun createKrakenStyleSchema(request: SchemaLearnRequest): ExchangeSchema {
        return ExchangeSchema(
            exchangeId = request.exchangeId,
            name = request.name,
            type = request.type,
            baseUrl = request.baseUrl,
            sandboxUrl = request.sandboxUrl,
            wsUrl = request.wsUrl ?: "wss://ws.kraken.com",
            auth = AuthSchema(
                method = AuthMethod.HMAC,
                apiKeyHeader = "API-Key",
                signatureHeader = "API-Sign",
                signatureAlgorithm = SignatureAlgorithm.HMAC_SHA512,
                signaturePayload = SignaturePayload.PATH_AND_BODY,
                timestampFormat = TimestampFormat.UNIX_MILLIS,
                nonceHandling = NonceHandling.INCREMENTING,
                nonceParam = "nonce",
                requiresTimestamp = false,
                requiresSignature = true
            ),
            endpoints = EndpointSchema(
                serverTime = EndpointConfig(path = "/0/public/Time"),
                tradingPairs = EndpointConfig(path = "/0/public/AssetPairs"),
                ticker = EndpointConfig(path = "/0/public/Ticker", requiredParams = listOf(
                    ParamConfig("pair", ParamType.STRING)
                )),
                orderBook = EndpointConfig(path = "/0/public/Depth", requiredParams = listOf(
                    ParamConfig("pair", ParamType.STRING),
                    ParamConfig("count", ParamType.INTEGER, required = false, defaultValue = "100")
                )),
                balances = EndpointConfig(method = HttpMethod.POST, path = "/0/private/Balance", authenticated = true),
                placeOrder = EndpointConfig(method = HttpMethod.POST, path = "/0/private/AddOrder", authenticated = true),
                cancelOrder = EndpointConfig(method = HttpMethod.POST, path = "/0/private/CancelOrder", authenticated = true),
                openOrders = EndpointConfig(method = HttpMethod.POST, path = "/0/private/OpenOrders", authenticated = true)
            ),
            responseSchemas = ResponseSchemas(
                ticker = ResponseSchema(
                    dataPath = "result",
                    fields = mapOf(
                        "lastPrice" to FieldMapping("c.0", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "bidPrice" to FieldMapping("b.0", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "askPrice" to FieldMapping("a.0", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "volume" to FieldMapping("v.1", FieldType.DOUBLE, FieldTransform.TO_DOUBLE)
                    )
                ),
                error = ErrorSchema(
                    codePath = "error.0",
                    messagePath = "error.0"
                )
            ),
            rateLimits = RateLimitSchema(
                requestsPerSecond = 1,
                requestsPerMinute = 60
            ),
            capabilities = CapabilitySchema(
                supportsSpot = true,
                supportsFutures = true,
                supportsMargin = true,
                supportsWebSocket = true,
                makerFee = 0.0016,
                takerFee = 0.0026
            ),
            symbolFormat = SymbolFormatSchema(
                separator = "",
                order = SymbolOrder.BASE_QUOTE,
                case = SymbolCase.UPPER,
                symbolMappings = mapOf("BTC" to "XBT", "DOGE" to "XDG"),
                reverseSymbolMappings = mapOf("XBT" to "BTC", "XDG" to "DOGE")
            ),
            source = SchemaSource.INFERRED,
            notes = listOf("Kraken-style API pattern", "Uses nonce instead of timestamp", "BTC = XBT")
        )
    }
    
    private fun createCoinbaseStyleSchema(request: SchemaLearnRequest): ExchangeSchema {
        return ExchangeSchema(
            exchangeId = request.exchangeId,
            name = request.name,
            type = request.type,
            baseUrl = request.baseUrl,
            sandboxUrl = request.sandboxUrl ?: "https://api-public.sandbox.exchange.coinbase.com",
            wsUrl = request.wsUrl ?: "wss://ws-feed.exchange.coinbase.com",
            auth = AuthSchema(
                method = AuthMethod.HMAC,
                apiKeyHeader = "CB-ACCESS-KEY",
                signatureHeader = "CB-ACCESS-SIGN",
                timestampHeader = "CB-ACCESS-TIMESTAMP",
                passphraseHeader = "CB-ACCESS-PASSPHRASE",
                signatureAlgorithm = SignatureAlgorithm.HMAC_SHA256,
                signaturePayload = SignaturePayload.FULL_REQUEST,
                timestampFormat = TimestampFormat.UNIX_SECONDS,
                requiresTimestamp = true,
                requiresSignature = true
            ),
            endpoints = EndpointSchema(
                serverTime = EndpointConfig(path = "/time"),
                tradingPairs = EndpointConfig(path = "/products"),
                ticker = EndpointConfig(path = "/products/{product_id}/ticker", pathParams = listOf("product_id")),
                orderBook = EndpointConfig(path = "/products/{product_id}/book", pathParams = listOf("product_id")),
                balances = EndpointConfig(path = "/accounts", authenticated = true),
                placeOrder = EndpointConfig(method = HttpMethod.POST, path = "/orders", authenticated = true),
                cancelOrder = EndpointConfig(method = HttpMethod.DELETE, path = "/orders/{order_id}", authenticated = true),
                openOrders = EndpointConfig(path = "/orders", authenticated = true)
            ),
            responseSchemas = ResponseSchemas(
                ticker = ResponseSchema(
                    fields = mapOf(
                        "price" to FieldMapping("price", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "bid" to FieldMapping("bid", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "ask" to FieldMapping("ask", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "volume" to FieldMapping("volume", FieldType.DOUBLE, FieldTransform.TO_DOUBLE)
                    )
                ),
                error = ErrorSchema(
                    codePath = "message",
                    messagePath = "message"
                )
            ),
            rateLimits = RateLimitSchema(
                requestsPerSecond = 10,
                requestsPerMinute = 600
            ),
            capabilities = CapabilitySchema(
                supportsSpot = true,
                supportsMarketOrders = true,
                supportsLimitOrders = true,
                supportsStopOrders = true,
                supportsWebSocket = true,
                makerFee = 0.004,
                takerFee = 0.006
            ),
            symbolFormat = SymbolFormatSchema(
                separator = "-",
                order = SymbolOrder.BASE_QUOTE,
                case = SymbolCase.UPPER
            ),
            source = SchemaSource.INFERRED,
            notes = listOf("Coinbase-style API pattern", "Requires passphrase", "Uses seconds timestamp")
        )
    }
    
    private fun createBybitStyleSchema(request: SchemaLearnRequest): ExchangeSchema {
        return ExchangeSchema(
            exchangeId = request.exchangeId,
            name = request.name,
            type = request.type,
            baseUrl = request.baseUrl,
            sandboxUrl = request.sandboxUrl,
            wsUrl = request.wsUrl ?: "wss://stream.bybit.com/v5/public/spot",
            auth = AuthSchema(
                method = AuthMethod.HMAC,
                apiKeyHeader = "X-BAPI-API-KEY",
                signatureHeader = "X-BAPI-SIGN",
                timestampHeader = "X-BAPI-TIMESTAMP",
                signatureAlgorithm = SignatureAlgorithm.HMAC_SHA256,
                signaturePayload = SignaturePayload.QUERY_STRING,
                timestampFormat = TimestampFormat.UNIX_MILLIS,
                requiresTimestamp = true,
                requiresSignature = true,
                staticHeaders = mapOf("X-BAPI-RECV-WINDOW" to "5000")
            ),
            endpoints = EndpointSchema(
                serverTime = EndpointConfig(path = "/v5/market/time"),
                tradingPairs = EndpointConfig(path = "/v5/market/instruments-info", requiredParams = listOf(
                    ParamConfig("category", ParamType.ENUM, enumValues = listOf("spot", "linear", "inverse", "option"))
                )),
                ticker = EndpointConfig(path = "/v5/market/tickers", requiredParams = listOf(
                    ParamConfig("category", ParamType.STRING),
                    ParamConfig("symbol", ParamType.STRING, required = false)
                )),
                orderBook = EndpointConfig(path = "/v5/market/orderbook", requiredParams = listOf(
                    ParamConfig("category", ParamType.STRING),
                    ParamConfig("symbol", ParamType.STRING)
                )),
                balances = EndpointConfig(path = "/v5/account/wallet-balance", authenticated = true),
                placeOrder = EndpointConfig(method = HttpMethod.POST, path = "/v5/order/create", authenticated = true),
                cancelOrder = EndpointConfig(method = HttpMethod.POST, path = "/v5/order/cancel", authenticated = true),
                openOrders = EndpointConfig(path = "/v5/order/realtime", authenticated = true)
            ),
            responseSchemas = ResponseSchemas(
                ticker = ResponseSchema(
                    dataPath = "result.list",
                    isArray = true,
                    fields = mapOf(
                        "symbol" to FieldMapping("symbol"),
                        "lastPrice" to FieldMapping("lastPrice", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "bid1Price" to FieldMapping("bid1Price", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "ask1Price" to FieldMapping("ask1Price", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "volume24h" to FieldMapping("volume24h", FieldType.DOUBLE, FieldTransform.TO_DOUBLE)
                    )
                ),
                error = ErrorSchema(
                    codePath = "retCode",
                    messagePath = "retMsg",
                    errorCodes = mapOf(
                        "10001" to ErrorType.INVALID_SIGNATURE,
                        "10003" to ErrorType.INVALID_API_KEY,
                        "110007" to ErrorType.INSUFFICIENT_BALANCE
                    )
                )
            ),
            rateLimits = RateLimitSchema(
                requestsPerSecond = 20,
                requestsPerMinute = 600
            ),
            capabilities = CapabilitySchema(
                supportsSpot = true,
                supportsFutures = true,
                supportsMargin = true,
                supportsOptions = true,
                supportsWebSocket = true,
                makerFee = 0.001,
                takerFee = 0.001
            ),
            symbolFormat = SymbolFormatSchema(
                separator = "",
                order = SymbolOrder.BASE_QUOTE,
                case = SymbolCase.UPPER
            ),
            source = SchemaSource.INFERRED,
            notes = listOf("Bybit V5 API pattern", "Requires category parameter")
        )
    }
    
    private fun createKuCoinStyleSchema(request: SchemaLearnRequest): ExchangeSchema {
        return ExchangeSchema(
            exchangeId = request.exchangeId,
            name = request.name,
            type = request.type,
            baseUrl = request.baseUrl,
            sandboxUrl = request.sandboxUrl ?: "https://openapi-sandbox.kucoin.com",
            wsUrl = request.wsUrl,
            auth = AuthSchema(
                method = AuthMethod.HMAC,
                apiKeyHeader = "KC-API-KEY",
                signatureHeader = "KC-API-SIGN",
                timestampHeader = "KC-API-TIMESTAMP",
                passphraseHeader = "KC-API-PASSPHRASE",
                signatureAlgorithm = SignatureAlgorithm.HMAC_SHA256,
                signaturePayload = SignaturePayload.FULL_REQUEST,
                timestampFormat = TimestampFormat.UNIX_MILLIS,
                requiresTimestamp = true,
                requiresSignature = true,
                staticHeaders = mapOf("KC-API-KEY-VERSION" to "2")
            ),
            endpoints = EndpointSchema(
                serverTime = EndpointConfig(path = "/api/v1/timestamp"),
                tradingPairs = EndpointConfig(path = "/api/v2/symbols"),
                ticker = EndpointConfig(path = "/api/v1/market/stats", requiredParams = listOf(
                    ParamConfig("symbol", ParamType.STRING)
                )),
                orderBook = EndpointConfig(path = "/api/v1/market/orderbook/level2_100", requiredParams = listOf(
                    ParamConfig("symbol", ParamType.STRING)
                )),
                balances = EndpointConfig(path = "/api/v1/accounts", authenticated = true),
                placeOrder = EndpointConfig(method = HttpMethod.POST, path = "/api/v1/orders", authenticated = true),
                cancelOrder = EndpointConfig(method = HttpMethod.DELETE, path = "/api/v1/orders/{orderId}", authenticated = true),
                openOrders = EndpointConfig(path = "/api/v1/orders", authenticated = true)
            ),
            responseSchemas = ResponseSchemas(
                ticker = ResponseSchema(
                    dataPath = "data",
                    fields = mapOf(
                        "symbol" to FieldMapping("symbol"),
                        "last" to FieldMapping("last", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "buy" to FieldMapping("buy", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "sell" to FieldMapping("sell", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "vol" to FieldMapping("vol", FieldType.DOUBLE, FieldTransform.TO_DOUBLE)
                    )
                ),
                error = ErrorSchema(
                    codePath = "code",
                    messagePath = "msg"
                )
            ),
            rateLimits = RateLimitSchema(
                requestsPerSecond = 10,
                requestsPerMinute = 600
            ),
            capabilities = CapabilitySchema(
                supportsSpot = true,
                supportsFutures = true,
                supportsMargin = true,
                supportsWebSocket = true,
                makerFee = 0.001,
                takerFee = 0.001
            ),
            symbolFormat = SymbolFormatSchema(
                separator = "-",
                order = SymbolOrder.BASE_QUOTE,
                case = SymbolCase.UPPER
            ),
            source = SchemaSource.INFERRED,
            notes = listOf("KuCoin-style API pattern", "Requires passphrase", "Symbol format: BTC-USDT")
        )
    }
    
    private fun createUpholdStyleSchema(request: SchemaLearnRequest): ExchangeSchema {
        return ExchangeSchema(
            exchangeId = request.exchangeId,
            name = request.name,
            type = ExchangeType.FOREX_BROKER,
            baseUrl = request.baseUrl,
            sandboxUrl = request.sandboxUrl ?: "https://api-sandbox.uphold.com",
            wsUrl = null,
            auth = AuthSchema(
                method = AuthMethod.BASIC,
                requiresTimestamp = false,
                requiresSignature = false
            ),
            endpoints = EndpointSchema(
                ticker = EndpointConfig(path = "/v0/ticker/{pair}", pathParams = listOf("pair")),
                tradingPairs = EndpointConfig(path = "/v0/assets"),
                balances = EndpointConfig(path = "/v0/me/cards", authenticated = true),
                placeOrder = EndpointConfig(
                    method = HttpMethod.POST,
                    path = "/v0/me/cards/{cardId}/transactions",
                    authenticated = true,
                    pathParams = listOf("cardId")
                )
            ),
            responseSchemas = ResponseSchemas(
                ticker = ResponseSchema(
                    fields = mapOf(
                        "pair" to FieldMapping("pair"),
                        "bid" to FieldMapping("bid", FieldType.DOUBLE, FieldTransform.TO_DOUBLE),
                        "ask" to FieldMapping("ask", FieldType.DOUBLE, FieldTransform.TO_DOUBLE)
                    )
                )
            ),
            rateLimits = RateLimitSchema(
                requestsPerSecond = 5,
                requestsPerMinute = 300
            ),
            capabilities = CapabilitySchema(
                supportsSpot = true,
                supportsMarketOrders = true,
                supportsLimitOrders = false,
                supportsStopOrders = false,
                supportsWebSocket = false,
                makerFee = 0.0,
                takerFee = 0.0
            ),
            symbolFormat = SymbolFormatSchema(
                separator = "-",
                order = SymbolOrder.BASE_QUOTE,
                case = SymbolCase.UPPER
            ),
            source = SchemaSource.INFERRED,
            notes = listOf("Uphold API pattern", "Supports FOREX and metals", "Card-based transaction model")
        )
    }
    
    // =========================================================================
    // HELPERS
    // =========================================================================
    
    private suspend fun fetchJson(url: String): JsonObject? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                // BUILD #156: Use .use{} to auto-close response body
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            JsonParser.parseString(body).asJsonObject
                        } else null
                    } else null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch $url", e)
                null
            }
        }
    }
    
    private fun classifyEndpoint(path: String, response: JsonObject): String? {
        val pathLower = path.lowercase()
        
        return when {
            pathLower.contains("time") || pathLower.contains("ping") -> "serverTime"
            pathLower.contains("exchangeinfo") || pathLower.contains("symbols") || 
            pathLower.contains("markets") || pathLower.contains("assetpairs") ||
            pathLower.contains("products") || pathLower.contains("instruments") -> "tradingPairs"
            pathLower.contains("ticker") -> "ticker"
            pathLower.contains("depth") || pathLower.contains("orderbook") -> "orderBook"
            else -> null
        }
    }
    
    private fun buildSchemaFromDiscovery(
        request: SchemaLearnRequest,
        discoveredEndpoints: Map<String, String>
    ): ExchangeSchema {
        return ExchangeSchema(
            exchangeId = request.exchangeId,
            name = request.name,
            type = request.type,
            baseUrl = request.baseUrl,
            sandboxUrl = request.sandboxUrl,
            wsUrl = request.wsUrl,
            auth = AuthSchema(method = AuthMethod.HMAC),
            endpoints = EndpointSchema(
                serverTime = discoveredEndpoints["serverTime"]?.let { EndpointConfig(path = it) },
                tradingPairs = discoveredEndpoints["tradingPairs"]?.let { EndpointConfig(path = it) },
                ticker = discoveredEndpoints["ticker"]?.let { EndpointConfig(path = it) },
                orderBook = discoveredEndpoints["orderBook"]?.let { EndpointConfig(path = it) }
            ),
            responseSchemas = ResponseSchemas(),
            rateLimits = RateLimitSchema(),
            capabilities = CapabilitySchema(),
            symbolFormat = SymbolFormatSchema(),
            source = SchemaSource.INFERRED
        )
    }
    
    private suspend fun detectAuthPattern(request: SchemaLearnRequest): AuthSchema? {
        // Would need sample credentials to actually test auth patterns
        // For now, return null to use default
        return null
    }
    
    private suspend fun validateSchema(schema: ExchangeSchema, baseUrl: String): Pair<Boolean, Double> {
        var successCount = 0
        var totalTests = 0
        
        // Test server time endpoint
        schema.endpoints.serverTime?.let { endpoint ->
            totalTests++
            if (fetchJson("$baseUrl${endpoint.path}") != null) successCount++
        }
        
        // Test trading pairs endpoint
        schema.endpoints.tradingPairs?.let { endpoint ->
            totalTests++
            if (fetchJson("$baseUrl${endpoint.path}") != null) successCount++
        }
        
        val confidence = if (totalTests > 0) successCount.toDouble() / totalTests else 0.0
        return Pair(confidence >= 0.5, confidence)
    }
    
    private enum class ExchangePattern {
        BINANCE_STYLE,
        KRAKEN_STYLE,
        COINBASE_STYLE,
        BYBIT_STYLE,
        KUCOIN_STYLE,
        UPHOLD_STYLE
    }
    
    private data class AuthPattern(
        val apiKeyHeader: String,
        val signatureHeader: String?,
        val algorithm: SignatureAlgorithm
    )
}
