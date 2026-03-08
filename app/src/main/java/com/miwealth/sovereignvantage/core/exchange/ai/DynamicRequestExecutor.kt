/**
 * AI EXCHANGE INTERFACE - Dynamic Request Executor
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Executes API requests dynamically based on learned exchange schemas.
 * Handles authentication, signing, rate limiting, and response parsing
 * without any hardcoded exchange-specific logic.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

package com.miwealth.sovereignvantage.core.exchange.ai

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import com.miwealth.sovereignvantage.core.exchange.ExchangeCredentials
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Standard operations that can be executed on any exchange.
 */
enum class StandardOperation {
    // Public endpoints
    GET_SERVER_TIME,
    GET_TRADING_PAIRS,
    GET_TICKER,
    GET_ALL_TICKERS,
    GET_ORDER_BOOK,
    GET_TRADES,
    GET_CANDLES,
    
    // Authenticated endpoints
    GET_BALANCES,
    PLACE_ORDER,
    CANCEL_ORDER,
    CANCEL_ALL_ORDERS,
    MODIFY_ORDER,
    GET_ORDER,
    GET_OPEN_ORDERS,
    GET_ORDER_HISTORY,
    GET_DEPOSIT_ADDRESS,
    GET_WITHDRAWAL_HISTORY
}

/**
 * Result of executing a request.
 */
sealed class ExecutionResult {
    data class ObjectSuccess(val data: Map<String, Any?>) : ExecutionResult()
    data class ArraySuccess(val data: List<Map<String, Any?>>) : ExecutionResult()
    data class RawSuccess(val json: JsonObject) : ExecutionResult()
    data class Error(val message: String, val errorType: ErrorType = ErrorType.UNKNOWN) : ExecutionResult()
    
    fun isSuccess() = this !is Error
    
    fun getDataOrNull(): Any? = when (this) {
        is ObjectSuccess -> data
        is ArraySuccess -> data
        is RawSuccess -> json
        is Error -> null
    }
}

/**
 * Executes API requests dynamically based on exchange schemas.
 */
class DynamicRequestExecutor(
    private val schema: ExchangeSchema,
    private val credentials: ExchangeCredentials? = null,
    private val useTestnet: Boolean = false,
    // V5.17.0: PQC integration (Gap 2) — forward PQC credentials to AI path
    private val pqcRequestSigner: com.miwealth.sovereignvantage.core.security.pqc.hybrid.PQCRequestSigner? = null,
    private val secureHttpClient: com.miwealth.sovereignvantage.core.security.pqc.hybrid.HybridSecureHttpClient? = null
) {
    companion object {
        private const val TAG = "DynamicRequestExecutor"
        private const val DEFAULT_TIMEOUT_SECONDS = 30L
    }
    
    private val gson = Gson()
    private val httpClient = com.miwealth.sovereignvantage.core.network.SharedHttpClient.baseClient
    
    // Rate limiting
    private val requestCount = AtomicInteger(0)
    private val rateLimitMutex = Mutex()
    private var lastRequestTime = 0L
    private var rateLimitedUntil = 0L
    
    // Nonce tracking
    private var lastNonce = System.currentTimeMillis()
    private val nonceMutex = Mutex()
    
    // =========================================================================
    // PUBLIC API
    // =========================================================================
    
    /**
     * Execute a standard operation.
     */
    suspend fun execute(
        operation: StandardOperation,
        params: Map<String, Any> = emptyMap()
    ): ExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val endpoint = getEndpointConfig(operation)
                    ?: return@withContext ExecutionResult.Error(
                        "Operation $operation not supported by ${schema.name}"
                    )
                
                enforceRateLimit(endpoint.weight)
                val response = executeRequest(endpoint, params)
                parseResponse(operation, response)
                
            } catch (e: Exception) {
                Log.e(TAG, "Execution failed for $operation", e)
                ExecutionResult.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Execute a raw request (for custom endpoints).
     */
    suspend fun executeRaw(
        method: HttpMethod,
        path: String,
        params: Map<String, Any> = emptyMap(),
        authenticated: Boolean = false
    ): ExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                val endpoint = EndpointConfig(
                    method = method,
                    path = path,
                    authenticated = authenticated
                )
                
                enforceRateLimit(1)
                val response = executeRequest(endpoint, params)
                
                if (response != null) {
                    ExecutionResult.RawSuccess(response)
                } else {
                    ExecutionResult.Error("Request failed")
                }
                
            } catch (e: Exception) {
                ExecutionResult.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Test connectivity to the exchange.
     */
    suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                schema.endpoints.serverTime?.let { endpoint ->
                    val response = executeRequest(endpoint, emptyMap())
                    return@withContext response != null
                }
                
                schema.endpoints.tradingPairs?.let { endpoint ->
                    val response = executeRequest(endpoint, emptyMap())
                    return@withContext response != null
                }
                
                false
            } catch (e: Exception) {
                Log.e(TAG, "Connection test failed", e)
                false
            }
        }
    }
    
    // =========================================================================
    // ENDPOINT MAPPING
    // =========================================================================
    
    private fun getEndpointConfig(operation: StandardOperation): EndpointConfig? {
        return when (operation) {
            StandardOperation.GET_SERVER_TIME -> schema.endpoints.serverTime
            StandardOperation.GET_TRADING_PAIRS -> schema.endpoints.tradingPairs
            StandardOperation.GET_TICKER, StandardOperation.GET_ALL_TICKERS -> schema.endpoints.ticker
            StandardOperation.GET_ORDER_BOOK -> schema.endpoints.orderBook
            StandardOperation.GET_TRADES -> schema.endpoints.trades
            StandardOperation.GET_CANDLES -> schema.endpoints.candles
            StandardOperation.GET_BALANCES -> schema.endpoints.balances
            StandardOperation.PLACE_ORDER -> schema.endpoints.placeOrder
            StandardOperation.CANCEL_ORDER -> schema.endpoints.cancelOrder
            StandardOperation.GET_ORDER -> schema.endpoints.getOrder
            StandardOperation.GET_OPEN_ORDERS -> schema.endpoints.openOrders
            StandardOperation.GET_ORDER_HISTORY -> schema.endpoints.orderHistory
            StandardOperation.GET_DEPOSIT_ADDRESS -> schema.endpoints.depositAddress
            StandardOperation.GET_WITHDRAWAL_HISTORY -> schema.endpoints.withdrawalHistory
            else -> null
        }
    }
    
    // =========================================================================
    // REQUEST EXECUTION
    // =========================================================================
    
    private suspend fun executeRequest(
        endpoint: EndpointConfig,
        params: Map<String, Any>
    ): JsonObject? {
        // Resolve base URL: use sandbox/testnet URL when in testnet mode
        val baseUrl = if (useTestnet && !schema.sandboxUrl.isNullOrBlank()) {
            schema.sandboxUrl.trimEnd('/')
        } else {
            schema.baseUrl.trimEnd('/')
        }
        var path = endpoint.path
        
        // Substitute path parameters
        for (pathParam in endpoint.pathParams) {
            val value = params[pathParam]?.toString() ?: continue
            path = path.replace("{$pathParam}", value)
        }
        
        // Build query and body params
        val queryParams = mutableMapOf<String, String>()
        val bodyParams = mutableMapOf<String, Any>()
        
        for (param in endpoint.requiredParams) {
            val value = params[param.name] ?: param.defaultValue
            if (value != null) {
                if (param.location == ParamLocation.QUERY || endpoint.method == HttpMethod.GET) {
                    queryParams[param.name] = value.toString()
                } else {
                    bodyParams[param.name] = value
                }
            }
        }
        
        for (param in endpoint.optionalParams) {
            val value = params[param.name]
            if (value != null) {
                if (param.location == ParamLocation.QUERY || endpoint.method == HttpMethod.GET) {
                    queryParams[param.name] = value.toString()
                } else {
                    bodyParams[param.name] = value
                }
            }
        }
        
        // Add extra params
        for ((key, value) in params) {
            if (key !in endpoint.pathParams && 
                endpoint.requiredParams.none { it.name == key } &&
                endpoint.optionalParams.none { it.name == key }) {
                if (endpoint.method == HttpMethod.GET) {
                    queryParams[key] = value.toString()
                } else {
                    bodyParams[key] = value
                }
            }
        }
        
        // Build authentication
        val headers = mutableMapOf<String, String>()
        var body: String? = null
        
        if (endpoint.authenticated && credentials != null) {
            val authResult = buildAuthentication(endpoint.method, path, queryParams, bodyParams)
            headers.putAll(authResult.headers)
            queryParams.putAll(authResult.queryParams)
            body = authResult.body
        } else if (bodyParams.isNotEmpty()) {
            body = gson.toJson(bodyParams)
        }
        
        headers.putAll(schema.auth.staticHeaders)
        
        // Build URL
        val queryString = if (queryParams.isNotEmpty()) {
            queryParams.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
        } else ""
        
        val url = if (queryString.isNotEmpty()) "$baseUrl$path?$queryString" else "$baseUrl$path"
        
        // Build request
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        
        when (endpoint.method) {
            HttpMethod.GET -> requestBuilder.get()
            HttpMethod.POST -> {
                val contentType = endpoint.bodySchema?.contentType ?: "application/json"
                requestBuilder.post((body ?: "{}").toRequestBody(contentType.toMediaType()))
            }
            HttpMethod.PUT -> {
                val contentType = endpoint.bodySchema?.contentType ?: "application/json"
                requestBuilder.put((body ?: "{}").toRequestBody(contentType.toMediaType()))
            }
            HttpMethod.DELETE -> {
                if (body != null) {
                    requestBuilder.delete(body.toRequestBody("application/json".toMediaType()))
                } else {
                    requestBuilder.delete()
                }
            }
            HttpMethod.PATCH -> {
                val contentType = endpoint.bodySchema?.contentType ?: "application/json"
                requestBuilder.patch((body ?: "{}").toRequestBody(contentType.toMediaType()))
            }
        }
        
        // V5.17.0: PQC request signing (Gap 2) — sign ALL authenticated requests
        // Header names and encoding standardised to match HybridSecureHttpClient conventions:
        //   - Dashed names: X-PQC-Public-Key, X-Request-ID (not camelCase)
        //   - Hex encoding for signature/publicKey/nonce (consistent with HybridSecureHttpClient)
        if (pqcRequestSigner != null && endpoint.authenticated) {
            val builtRequest = requestBuilder.build()
            val headerMap = mutableMapOf<String, String>()
            for (i in 0 until builtRequest.headers.size) {
                headerMap[builtRequest.headers.name(i)] = builtRequest.headers.value(i)
            }
            val signedReq = pqcRequestSigner.signRequest(
                method = endpoint.method.name,
                url = url,
                headers = headerMap,
                body = body?.toByteArray(),
                exchangeId = schema.exchangeId
            )
            requestBuilder.addHeader("X-PQC-Signature", signedReq.pqcSignature.toHexString())
            requestBuilder.addHeader("X-PQC-Public-Key", signedReq.pqcPublicKey.toHexString())
            requestBuilder.addHeader("X-PQC-Nonce", signedReq.nonce.toHexString())
            requestBuilder.addHeader("X-PQC-Timestamp", signedReq.timestamp.toString())
            requestBuilder.addHeader("X-Request-ID", signedReq.requestId)
        }
        
        // Execute — use PQC-secured HTTP client when available (V5.17.0)
        // BUILD #156: Use .use{} to auto-close response body
        val effectiveClient = secureHttpClient?.getOkHttpClient() ?: httpClient
        return effectiveClient.newCall(requestBuilder.build()).execute().use { response ->
            updateRateLimitFromHeaders(response.headers)
            
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful) {
                Log.w(TAG, "Request failed: ${response.code} - $responseBody")
                
                if (response.code == 429) {
                    val retryAfter = response.header(schema.rateLimits.retryAfterHeader)?.toLongOrNull() ?: 60
                    rateLimitedUntil = System.currentTimeMillis() + (retryAfter * 1000)
                }
                
                if (responseBody != null) {
                    try {
                        return@use JsonParser.parseString(responseBody).asJsonObject
                    } catch (e: Exception) { }
                }
                return@use null
            }
            
            if (responseBody != null) {
                try {
                    val parsed = JsonParser.parseString(responseBody)
                    when {
                        parsed.isJsonObject -> parsed.asJsonObject
                        parsed.isJsonArray -> JsonObject().apply { add("data", parsed) }
                        else -> null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse response", e)
                    null
                }
            } else null
        }
    }
    
    // =========================================================================
    // AUTHENTICATION
    // =========================================================================
    
    private data class AuthResult(
        val headers: Map<String, String>,
        val queryParams: Map<String, String>,
        val body: String?
    )
    
    private suspend fun buildAuthentication(
        method: HttpMethod,
        path: String,
        queryParams: MutableMap<String, String>,
        bodyParams: Map<String, Any>
    ): AuthResult {
        val headers = mutableMapOf<String, String>()
        val extraQueryParams = mutableMapOf<String, String>()
        var body: String? = null
        
        val creds = credentials ?: return AuthResult(headers, extraQueryParams, gson.toJson(bodyParams))
        val auth = schema.auth
        
        // Add API key header
        auth.apiKeyHeader?.let { headers[it] = creds.apiKey }
        
        // Add passphrase header
        if (auth.passphraseHeader != null && creds.passphrase != null) {
            headers[auth.passphraseHeader!!] = creds.passphrase!!
        }
        
        // Add timestamp
        val timestamp = when (auth.timestampFormat) {
            TimestampFormat.UNIX_SECONDS -> (System.currentTimeMillis() / 1000).toString()
            TimestampFormat.UNIX_MILLIS -> System.currentTimeMillis().toString()
            TimestampFormat.UNIX_MICROS -> (System.currentTimeMillis() * 1000).toString()
            TimestampFormat.ISO8601, TimestampFormat.RFC3339 -> Instant.now().toString()
        }
        
        if (auth.requiresTimestamp) {
            auth.timestampHeader?.let { headers[it] = timestamp }
            if (auth.timestampHeader == null) {
                extraQueryParams[auth.timestampParam] = timestamp
            }
        }
        
        // Add nonce
        if (auth.nonceHandling != NonceHandling.NONE) {
            val nonce = generateNonce()
            extraQueryParams[auth.nonceParam] = nonce
        }
        
        // Build signature
        if (auth.requiresSignature) {
            val allQueryParams = queryParams + extraQueryParams
            val signature = buildSignature(method, path, allQueryParams, bodyParams, timestamp)
            
            auth.signatureHeader?.let { headers[it] = signature }
            if (auth.signatureHeader == null) {
                extraQueryParams[auth.signatureParam] = signature
            }
        }
        
        body = if (bodyParams.isNotEmpty()) gson.toJson(bodyParams) else null
        
        return AuthResult(headers, extraQueryParams, body)
    }
    
    private fun buildSignature(
        method: HttpMethod,
        path: String,
        queryParams: Map<String, String>,
        bodyParams: Map<String, Any>,
        timestamp: String
    ): String {
        val auth = schema.auth
        val creds = credentials ?: return ""
        
        val payload = when (auth.signaturePayload) {
            SignaturePayload.QUERY_STRING -> {
                val allParams = queryParams.toSortedMap()
                allParams.entries.joinToString("&") { "${it.key}=${it.value}" }
            }
            SignaturePayload.REQUEST_PATH -> {
                val queryString = queryParams.entries.joinToString("&") { "${it.key}=${it.value}" }
                if (queryString.isNotEmpty()) "$path?$queryString" else path
            }
            SignaturePayload.BODY_ONLY -> {
                if (bodyParams.isNotEmpty()) gson.toJson(bodyParams) else ""
            }
            SignaturePayload.PATH_AND_BODY -> {
                queryParams.entries.joinToString("&") { "${it.key}=${it.value}" }
            }
            SignaturePayload.FULL_REQUEST -> {
                val body = if (bodyParams.isNotEmpty()) gson.toJson(bodyParams) else ""
                "$timestamp${method.name}$path$body"
            }
            SignaturePayload.CUSTOM -> {
                queryParams.entries.joinToString("&") { "${it.key}=${it.value}" }
            }
        }
        
        return when (auth.signatureAlgorithm) {
            SignatureAlgorithm.HMAC_SHA256 -> hmacSign(payload, creds.apiSecret, "HmacSHA256")
            SignatureAlgorithm.HMAC_SHA384 -> hmacSign(payload, creds.apiSecret, "HmacSHA384")
            SignatureAlgorithm.HMAC_SHA512 -> {
                if (auth.signaturePayload == SignaturePayload.PATH_AND_BODY) {
                    krakenSign(path, payload, creds.apiSecret)
                } else {
                    hmacSign(payload, creds.apiSecret, "HmacSHA512")
                }
            }
            SignatureAlgorithm.RSA_SHA256, SignatureAlgorithm.ED25519, SignatureAlgorithm.KECCAK256 -> payload
        }
    }
    
    private fun hmacSign(payload: String, secret: String, algorithm: String): String {
        val mac = Mac.getInstance(algorithm)
        val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), algorithm)
        mac.init(secretKey)
        val hash = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    private fun krakenSign(path: String, postData: String, secret: String): String {
        val nonce = postData.substringAfter("nonce=").substringBefore("&")
        
        val sha256 = MessageDigest.getInstance("SHA-256")
        val shaDigest = sha256.digest((nonce + postData).toByteArray(Charsets.UTF_8))
        
        val message = path.toByteArray(Charsets.UTF_8) + shaDigest
        val decodedSecret = Base64.decode(secret, Base64.DEFAULT)
        
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(decodedSecret, "HmacSHA512"))
        val signature = mac.doFinal(message)
        
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }
    
    private suspend fun generateNonce(): String {
        return nonceMutex.withLock {
            val nonce = maxOf(System.currentTimeMillis(), lastNonce + 1)
            lastNonce = nonce
            nonce.toString()
        }
    }
    
    // =========================================================================
    // RESPONSE PARSING
    // =========================================================================
    
    private fun parseResponse(operation: StandardOperation, response: JsonObject?): ExecutionResult {
        if (response == null) {
            return ExecutionResult.Error("Empty response")
        }
        
        // Check for error
        val errorSchema = schema.responseSchemas.error
        if (errorSchema != null) {
            val errorCode = extractValue(response, errorSchema.codePath)
            if (errorCode != null && errorCode != "0" && errorCode != "200" && errorCode != "OK" && errorCode.isNotEmpty()) {
                val errorMessage = extractValue(response, errorSchema.messagePath) ?: "Unknown error"
                val errorType = errorSchema.errorCodes[errorCode] ?: ErrorType.UNKNOWN
                return ExecutionResult.Error("[$errorCode] $errorMessage", errorType)
            }
        }
        
        val responseSchema = when (operation) {
            StandardOperation.GET_TICKER, StandardOperation.GET_ALL_TICKERS -> schema.responseSchemas.ticker
            StandardOperation.GET_ORDER_BOOK -> schema.responseSchemas.orderBook
            StandardOperation.GET_TRADES -> schema.responseSchemas.trades
            StandardOperation.GET_CANDLES -> schema.responseSchemas.candles
            StandardOperation.GET_TRADING_PAIRS -> schema.responseSchemas.tradingPairs
            StandardOperation.GET_BALANCES -> schema.responseSchemas.balances
            StandardOperation.PLACE_ORDER, StandardOperation.GET_ORDER, 
            StandardOperation.CANCEL_ORDER -> schema.responseSchemas.order
            else -> null
        }
        
        if (responseSchema == null) {
            return ExecutionResult.RawSuccess(response)
        }
        
        val data = if (responseSchema.dataPath.isNotEmpty()) {
            extractElement(response, responseSchema.dataPath)
        } else {
            response
        }
        
        if (data == null) {
            return ExecutionResult.RawSuccess(response)
        }
        
        return if (responseSchema.isArray && data.isJsonArray) {
            val items = data.asJsonArray.map { item ->
                if (item.isJsonObject) {
                    parseFields(item.asJsonObject, responseSchema.fields)
                } else {
                    mapOf("value" to item.toString())
                }
            }
            ExecutionResult.ArraySuccess(items)
        } else if (data.isJsonObject) {
            val parsed = parseFields(data.asJsonObject, responseSchema.fields)
            ExecutionResult.ObjectSuccess(parsed)
        } else {
            ExecutionResult.RawSuccess(response)
        }
    }
    
    private fun parseFields(obj: JsonObject, fields: Map<String, FieldMapping>): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        
        for ((fieldName, mapping) in fields) {
            val rawValue = extractValue(obj, mapping.path) ?: mapping.defaultValue
            result[fieldName] = transformValue(rawValue, mapping.type, mapping.transform)
        }
        
        return result
    }
    
    private fun extractElement(obj: JsonObject, path: String): JsonElement? {
        var current: JsonElement = obj
        
        for (key in path.split(".")) {
            current = when {
                current.isJsonObject -> current.asJsonObject.get(key) ?: return null
                current.isJsonArray && key.toIntOrNull() != null -> {
                    val index = key.toInt()
                    if (index < current.asJsonArray.size()) {
                        current.asJsonArray.get(index)
                    } else return null
                }
                else -> return null
            }
        }
        
        return current
    }
    
    private fun extractValue(obj: JsonObject, path: String): String? {
        val element = extractElement(obj, path) ?: return null
        
        return when {
            element.isJsonPrimitive -> element.asJsonPrimitive.asString
            element.isJsonNull -> null
            else -> element.toString()
        }
    }
    
    private fun transformValue(value: String?, type: FieldType, transform: FieldTransform): Any? {
        if (value == null) return null
        
        val transformed = when (transform) {
            FieldTransform.NONE -> value
            FieldTransform.TO_DOUBLE -> value.toDoubleOrNull()?.toString() ?: value
            FieldTransform.TO_LONG -> value.toLongOrNull()?.toString() ?: value
            FieldTransform.TO_BOOLEAN -> value.toBoolean().toString()
            FieldTransform.TIMESTAMP_SECONDS_TO_MILLIS -> {
                val seconds = value.toLongOrNull() ?: return value
                (seconds * 1000).toString()
            }
            FieldTransform.TIMESTAMP_MILLIS_TO_SECONDS -> {
                val millis = value.toLongOrNull() ?: return value
                (millis / 1000).toString()
            }
            FieldTransform.DIVIDE_BY_100 -> {
                val num = value.toDoubleOrNull() ?: return value
                (num / 100).toString()
            }
            FieldTransform.MULTIPLY_BY_100 -> {
                val num = value.toDoubleOrNull() ?: return value
                (num * 100).toString()
            }
            FieldTransform.BASE64_DECODE -> {
                try {
                    String(Base64.decode(value, Base64.DEFAULT))
                } catch (e: Exception) { value }
            }
            FieldTransform.NORMALISE_SYMBOL -> normaliseSymbol(value)
        }
        
        return when (type) {
            FieldType.STRING -> transformed
            FieldType.INTEGER -> transformed.toString().toIntOrNull()
            FieldType.LONG -> transformed.toString().toLongOrNull()
            FieldType.DOUBLE -> transformed.toString().toDoubleOrNull()
            FieldType.BOOLEAN -> transformed.toString().toBoolean()
            FieldType.TIMESTAMP -> transformed.toString().toLongOrNull()
            FieldType.ARRAY, FieldType.OBJECT -> transformed
        }
    }
    
    private fun normaliseSymbol(exchangeSymbol: String): String {
        // Apply reverse symbol mappings
        var symbol = exchangeSymbol
        for ((exchange, standard) in schema.symbolFormat.reverseSymbolMappings) {
            symbol = symbol.replace(exchange, standard)
        }
        
        // Remove separator and standardise
        val sep = schema.symbolFormat.separator
        if (sep.isNotEmpty()) {
            val parts = symbol.split(sep)
            if (parts.size == 2) {
                return "${parts[0]}/${parts[1]}"
            }
        }
        
        return symbol
    }
    
    // =========================================================================
    // RATE LIMITING
    // =========================================================================
    
    private suspend fun enforceRateLimit(weight: Int) {
        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()
            
            // Check if we're rate limited
            if (now < rateLimitedUntil) {
                val waitTime = rateLimitedUntil - now
                Log.w(TAG, "Rate limited, waiting ${waitTime}ms")
                delay(waitTime)
            }
            
            // Simple rate limiting based on requests per second
            val minInterval = 1000L / schema.rateLimits.requestsPerSecond
            val elapsed = now - lastRequestTime
            
            if (elapsed < minInterval) {
                delay(minInterval - elapsed)
            }
            
            lastRequestTime = System.currentTimeMillis()
            requestCount.incrementAndGet()
        }
    }
    
    private fun updateRateLimitFromHeaders(headers: Headers) {
        // Update rate limit tracking from response headers
        schema.rateLimits.remainingHeader?.let { header ->
            headers[header]?.toIntOrNull()?.let { remaining ->
                if (remaining <= 1) {
                    Log.w(TAG, "Approaching rate limit, remaining: $remaining")
                }
            }
        }
        
        schema.rateLimits.usedWeightHeader?.let { header ->
            headers[header]?.toIntOrNull()?.let { used ->
                if (used >= schema.rateLimits.weightPerMinute * 0.8) {
                    Log.w(TAG, "High rate limit usage: $used/${schema.rateLimits.weightPerMinute}")
                }
            }
        }
    }
}

// V5.17.0: Hex encoding extension — matches HybridSecureHttpClient.toHex() convention
private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
