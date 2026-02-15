/**
 * AI EXCHANGE INTERFACE - Data Models
 * 
 * Sovereign Vantage: Arthur Edition V5.5.69
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Core data models for the AI-driven exchange connection system.
 * These models represent learned exchange schemas that enable
 * dynamic connection to any CEX or DEX without hardcoded connectors.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */
package com.miwealth.sovereignvantage.core.exchange.ai

import com.google.gson.annotations.SerializedName
import com.miwealth.sovereignvantage.core.exchange.ExchangeType
import java.time.Instant

// =============================================================================
// EXCHANGE SCHEMA - The learned "blueprint" for connecting to an exchange
// =============================================================================

/**
 * Complete learned schema for an exchange.
 * This replaces thousands of lines of hardcoded connector code.
 */
data class ExchangeSchema(
    /** Unique identifier (e.g., "binance", "kraken", "uniswap_v3_ethereum") */
    val exchangeId: String,
    
    /** Display name */
    val name: String,
    
    /** Exchange type */
    val type: ExchangeType,
    
    /** Base URL for REST API */
    val baseUrl: String,
    
    /** Sandbox/testnet URL if available */
    val sandboxUrl: String? = null,
    
    /** WebSocket URL */
    val wsUrl: String? = null,
    
    /** Authentication configuration */
    val auth: AuthSchema,
    
    /** API endpoints mapping */
    val endpoints: EndpointSchema,
    
    /** Response parsing rules */
    val responseSchemas: ResponseSchemas,
    
    /** Rate limiting configuration */
    val rateLimits: RateLimitSchema,
    
    /** Exchange capabilities */
    val capabilities: CapabilitySchema,
    
    /** Symbol format rules */
    val symbolFormat: SymbolFormatSchema,
    
    /** When this schema was learned/updated */
    val learnedAt: Instant = Instant.now(),
    
    /** Schema version for cache invalidation */
    val schemaVersion: Int = 1,
    
    /** Confidence score in the learned schema (0.0 - 1.0) */
    val confidence: Double = 0.0,
    
    /** Source of the schema (API_SPEC, DOCUMENTATION, INFERRED) */
    val source: SchemaSource = SchemaSource.INFERRED,
    
    /** Notes about quirks or special handling */
    val notes: List<String> = emptyList()
)

// ExchangeType: Now defined in core.exchange.UnifiedExchangeConnector (unified V5.5.78)
// Values: CEX, CEX_SPOT, CEX_FUTURES, CEX_MARGIN, DEX_AMM, DEX_ORDERBOOK, 
//         DEX_AGGREGATOR, FOREX_BROKER, HYBRID

enum class SchemaSource {
    API_SPEC,           // Learned from OpenAPI/Swagger spec
    DOCUMENTATION,      // Parsed from API documentation
    INFERRED,           // Inferred from trial and error
    MANUAL,             // Manually configured
    CCXT                // Derived from CCXT library definitions
}

// =============================================================================
// AUTHENTICATION SCHEMA
// =============================================================================

/**
 * Authentication configuration for an exchange.
 */
data class AuthSchema(
    /** Primary authentication method */
    val method: AuthMethod,
    
    /** Header name for API key (e.g., "X-MBX-APIKEY", "API-Key") */
    val apiKeyHeader: String? = null,
    
    /** Header name for signature (e.g., "X-MBX-SIGNATURE", "API-Sign") */
    val signatureHeader: String? = null,
    
    /** Header name for timestamp (e.g., "X-MBX-TIMESTAMP") */
    val timestampHeader: String? = null,
    
    /** Header name for passphrase (Coinbase, KuCoin) */
    val passphraseHeader: String? = null,
    
    /** Signature algorithm */
    val signatureAlgorithm: SignatureAlgorithm = SignatureAlgorithm.HMAC_SHA256,
    
    /** What to sign (URL, body, combination) */
    val signaturePayload: SignaturePayload = SignaturePayload.QUERY_STRING,
    
    /** Timestamp format */
    val timestampFormat: TimestampFormat = TimestampFormat.UNIX_MILLIS,
    
    /** Whether timestamp is required in request */
    val requiresTimestamp: Boolean = true,
    
    /** Timestamp parameter name in query/body */
    val timestampParam: String = "timestamp",
    
    /** Whether signature is required for authenticated endpoints */
    val requiresSignature: Boolean = true,
    
    /** Signature parameter name in query/body */
    val signatureParam: String = "signature",
    
    /** Nonce/request ID handling */
    val nonceHandling: NonceHandling = NonceHandling.NONE,
    
    /** Nonce parameter name */
    val nonceParam: String = "nonce",
    
    /** Whether to include body in signature for POST requests */
    val signBody: Boolean = false,
    
    /** OAuth2 configuration (if applicable) */
    val oauth2: OAuth2Config? = null,
    
    /** Additional static headers required */
    val staticHeaders: Map<String, String> = emptyMap()
)

enum class AuthMethod {
    NONE,               // Public API only
    API_KEY_ONLY,       // Just API key, no signature
    HMAC,               // HMAC-based signature
    RSA,                // RSA signature
    ED25519,            // Ed25519 signature
    BASIC,              // HTTP Basic auth
    OAUTH2,             // OAuth2 flow
    JWT,                // JWT tokens
    WEB3                // Web3/wallet signature (DEX)
}

enum class SignatureAlgorithm {
    HMAC_SHA256,
    HMAC_SHA384,
    HMAC_SHA512,
    RSA_SHA256,
    ED25519,
    KECCAK256           // Ethereum signing
}

enum class SignaturePayload {
    QUERY_STRING,       // Sign the query parameters
    REQUEST_PATH,       // Sign path + query
    BODY_ONLY,          // Sign request body
    PATH_AND_BODY,      // Sign path + body
    FULL_REQUEST,       // Sign method + path + body
    CUSTOM              // Exchange-specific format
}

enum class TimestampFormat {
    UNIX_SECONDS,       // Unix timestamp in seconds
    UNIX_MILLIS,        // Unix timestamp in milliseconds
    UNIX_MICROS,        // Unix timestamp in microseconds
    ISO8601,            // ISO 8601 format
    RFC3339             // RFC 3339 format
}

enum class NonceHandling {
    NONE,               // No nonce required
    INCREMENTING,       // Must be larger than previous
    TIMESTAMP,          // Use timestamp as nonce
    UUID,               // Random UUID
    CUSTOM              // Exchange-specific
}

data class OAuth2Config(
    val authorizationUrl: String,
    val tokenUrl: String,
    val scopes: List<String>,
    val clientIdHeader: String = "client_id",
    val clientSecretHeader: String = "client_secret"
)

// =============================================================================
// ENDPOINT SCHEMA
// =============================================================================

/**
 * API endpoint mappings for standard operations.
 */
data class EndpointSchema(
    // Public endpoints
    val ticker: EndpointConfig? = null,
    val orderBook: EndpointConfig? = null,
    val trades: EndpointConfig? = null,
    val candles: EndpointConfig? = null,
    val tradingPairs: EndpointConfig? = null,
    val serverTime: EndpointConfig? = null,
    
    // Authenticated endpoints
    val balances: EndpointConfig? = null,
    val placeOrder: EndpointConfig? = null,
    val cancelOrder: EndpointConfig? = null,
    val getOrder: EndpointConfig? = null,
    val openOrders: EndpointConfig? = null,
    val orderHistory: EndpointConfig? = null,
    val depositAddress: EndpointConfig? = null,
    val withdrawalHistory: EndpointConfig? = null,
    
    // WebSocket channels
    val wsTickerChannel: String? = null,
    val wsOrderBookChannel: String? = null,
    val wsTradesChannel: String? = null,
    val wsUserChannel: String? = null
)

/**
 * Configuration for a single endpoint.
 */
data class EndpointConfig(
    /** HTTP method */
    val method: HttpMethod = HttpMethod.GET,
    
    /** Path template (e.g., "/api/v3/ticker?symbol={symbol}") */
    val path: String,
    
    /** Whether authentication is required */
    val authenticated: Boolean = false,
    
    /** Path parameters (extracted from path template) */
    val pathParams: List<String> = emptyList(),
    
    /** Required query parameters */
    val requiredParams: List<ParamConfig> = emptyList(),
    
    /** Optional query parameters */
    val optionalParams: List<ParamConfig> = emptyList(),
    
    /** Request body schema (for POST/PUT) */
    val bodySchema: BodySchema? = null,
    
    /** Rate limit weight for this endpoint */
    val weight: Int = 1
)

enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH
}

data class ParamConfig(
    val name: String,
    val type: ParamType = ParamType.STRING,
    val location: ParamLocation = ParamLocation.QUERY,
    val required: Boolean = true,
    val defaultValue: String? = null,
    val enumValues: List<String>? = null,
    val description: String? = null
)

enum class ParamType {
    STRING, INTEGER, LONG, DOUBLE, BOOLEAN, ENUM, ARRAY
}

enum class ParamLocation {
    QUERY, PATH, HEADER, BODY
}

data class BodySchema(
    val contentType: String = "application/json",
    val fields: List<ParamConfig> = emptyList()
)

// =============================================================================
// RESPONSE SCHEMA
// =============================================================================

/**
 * Response parsing rules for extracting data from API responses.
 */
data class ResponseSchemas(
    val ticker: ResponseSchema? = null,
    val orderBook: ResponseSchema? = null,
    val trades: ResponseSchema? = null,
    val candles: ResponseSchema? = null,
    val tradingPairs: ResponseSchema? = null,
    val balances: ResponseSchema? = null,
    val order: ResponseSchema? = null,
    val error: ErrorSchema? = null
)

/**
 * Schema for parsing a specific response type.
 */
data class ResponseSchema(
    /** JSON path to the data array/object (e.g., "data", "result.list") */
    val dataPath: String = "",
    
    /** Whether response is an array or object */
    val isArray: Boolean = false,
    
    /** Field mappings */
    val fields: Map<String, FieldMapping> = emptyMap()
)

/**
 * Mapping from our standard field to the exchange's field.
 */
data class FieldMapping(
    /** JSON path to the field in response */
    val path: String,
    
    /** Type of the field */
    val type: FieldType = FieldType.STRING,
    
    /** Transformation to apply */
    val transform: FieldTransform = FieldTransform.NONE,
    
    /** Default value if field is missing */
    val defaultValue: String? = null
)

enum class FieldType {
    STRING, INTEGER, LONG, DOUBLE, BOOLEAN, TIMESTAMP, ARRAY, OBJECT
}

enum class FieldTransform {
    NONE,
    TO_DOUBLE,
    TO_LONG,
    TO_BOOLEAN,
    TIMESTAMP_SECONDS_TO_MILLIS,
    TIMESTAMP_MILLIS_TO_SECONDS,
    DIVIDE_BY_100,          // Percentage conversion
    MULTIPLY_BY_100,
    BASE64_DECODE,
    NORMALISE_SYMBOL        // Exchange symbol to standard format
}

/**
 * Error response parsing.
 */
data class ErrorSchema(
    /** JSON path to error code */
    val codePath: String = "code",
    
    /** JSON path to error message */
    val messagePath: String = "msg",
    
    /** Known error codes */
    val errorCodes: Map<String, ErrorType> = emptyMap()
)

enum class ErrorType {
    UNKNOWN,
    RATE_LIMITED,
    INVALID_SIGNATURE,
    INVALID_API_KEY,
    INSUFFICIENT_BALANCE,
    ORDER_NOT_FOUND,
    INVALID_SYMBOL,
    INVALID_QUANTITY,
    INVALID_PRICE,
    MARKET_CLOSED,
    MAINTENANCE
}

// =============================================================================
// RATE LIMIT SCHEMA
// =============================================================================

/**
 * Rate limiting configuration.
 */
data class RateLimitSchema(
    /** Requests per second limit */
    val requestsPerSecond: Int = 10,
    
    /** Requests per minute limit */
    val requestsPerMinute: Int = 600,
    
    /** Weight-based limiting */
    val weightPerMinute: Int = 1200,
    
    /** Order rate limit */
    val ordersPerSecond: Int = 5,
    
    /** Header containing remaining requests */
    val remainingHeader: String? = null,
    
    /** Header containing reset time */
    val resetHeader: String? = null,
    
    /** Header containing used weight */
    val usedWeightHeader: String? = null,
    
    /** Retry-After header name */
    val retryAfterHeader: String = "Retry-After"
)

// =============================================================================
// CAPABILITY SCHEMA
// =============================================================================

/**
 * Exchange capabilities.
 */
data class CapabilitySchema(
    val supportsSpot: Boolean = true,
    val supportsFutures: Boolean = false,
    val supportsMargin: Boolean = false,
    val supportsOptions: Boolean = false,
    val supportsLending: Boolean = false,
    val supportsStaking: Boolean = false,
    
    val supportsMarketOrders: Boolean = true,
    val supportsLimitOrders: Boolean = true,
    val supportsStopOrders: Boolean = false,
    val supportsStopLimitOrders: Boolean = false,
    val supportsTrailingStop: Boolean = false,
    val supportsPostOnly: Boolean = false,
    val supportsReduceOnly: Boolean = false,
    
    val supportsWebSocket: Boolean = false,
    val supportsOrderBookStream: Boolean = false,
    val supportsUserDataStream: Boolean = false,
    
    val supportsCancelAll: Boolean = false,
    val supportsEditOrder: Boolean = false,
    val supportsBatchOrders: Boolean = false,
    
    val minOrderValue: Double = 0.0,
    val maxOrderValue: Double = Double.MAX_VALUE,
    
    val makerFee: Double = 0.001,
    val takerFee: Double = 0.001
)

// =============================================================================
// SYMBOL FORMAT SCHEMA
// =============================================================================

/**
 * Symbol formatting rules.
 */
data class SymbolFormatSchema(
    /** Separator between base and quote (e.g., "", "/", "_", "-") */
    val separator: String = "",
    
    /** Order of base/quote in symbol */
    val order: SymbolOrder = SymbolOrder.BASE_QUOTE,
    
    /** Case transformation */
    val case: SymbolCase = SymbolCase.UPPER,
    
    /** Whether to include separator in symbol */
    val includeSeparator: Boolean = true,
    
    /** Special symbol mappings (e.g., "BTC" -> "XBT" for Kraken) */
    val symbolMappings: Map<String, String> = emptyMap(),
    
    /** Reverse mappings (exchange -> standard) */
    val reverseSymbolMappings: Map<String, String> = emptyMap()
)

enum class SymbolOrder {
    BASE_QUOTE,     // BTCUSDT
    QUOTE_BASE      // USDTBTC (rare)
}

enum class SymbolCase {
    UPPER,          // BTCUSDT
    LOWER,          // btcusdt
    MIXED           // Mixed case
}

// =============================================================================
// SCHEMA LEARNING REQUEST/RESULT
// =============================================================================

/**
 * Request to learn a new exchange schema.
 */
data class SchemaLearnRequest(
    val exchangeId: String,
    val name: String,
    val type: ExchangeType,
    val baseUrl: String,
    val sandboxUrl: String? = null,
    val wsUrl: String? = null,
    val openApiSpecUrl: String? = null,
    val documentationUrl: String? = null,
    val sampleCredentials: SampleCredentials? = null,
    val hints: List<String> = emptyList()
)

data class SampleCredentials(
    val apiKey: String,
    val apiSecret: String,
    val passphrase: String? = null
)

/**
 * Result of schema learning.
 */
sealed class SchemaLearnResult {
    data class Success(
        val schema: ExchangeSchema,
        val warnings: List<String> = emptyList()
    ) : SchemaLearnResult()
    
    data class PartialSuccess(
        val schema: ExchangeSchema,
        val missingCapabilities: List<String>,
        val errors: List<String>
    ) : SchemaLearnResult()
    
    data class Failure(
        val reason: String,
        val suggestions: List<String> = emptyList()
    ) : SchemaLearnResult()
}

// =============================================================================
// CONNECTION HEALTH
// =============================================================================

/**
 * Connection health status for an exchange.
 */
data class ConnectionHealth(
    val exchangeId: String,
    val status: ConnectionStatus,
    val latencyMs: Long,
    val successRate: Double,
    val lastSuccessAt: Instant?,
    val lastFailureAt: Instant?,
    val lastError: String?,
    val consecutiveFailures: Int,
    val schemaValid: Boolean,
    val needsRelearning: Boolean
)

enum class ConnectionStatus {
    HEALTHY,
    DEGRADED,
    FAILING,
    DISCONNECTED,
    RELEARNING
}
