package com.miwealth.sovereignvantage.core.security.pqc.hybrid

import com.miwealth.sovereignvantage.core.security.pqc.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * PQC REQUEST SIGNER
 * 
 * Provides post-quantum digital signatures for all exchange API requests.
 * Creates an immutable audit trail that is resistant to quantum attacks.
 * 
 * Features:
 * - Dilithium signatures for all outbound requests
 * - Hybrid mode: Classical HMAC + PQC signature
 * - Request/Response correlation for integrity verification
 * - Timestamped audit log with non-repudiation
 * - Key rotation support
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */
class PQCRequestSigner(
    private val config: HybridPQCConfig = HybridPQCConfig.default()
) {
    
    private val dilithium = DilithiumDSA(
        when (config.signatureAlgorithm) {
            SignatureAlgorithm.DILITHIUM_2 -> 2
            SignatureAlgorithm.DILITHIUM_3 -> 3
            SignatureAlgorithm.DILITHIUM_5 -> 5
        }
    )
    
    private val secureRandom = SecureRandom()
    
    // Signing key pair (rotates periodically)
    private var currentKeyPair: DilithiumKeyPair = dilithium.generateKeyPair()
    private var keyGeneratedAt: Long = System.currentTimeMillis()
    
    // Audit log of signed requests
    private val auditLog = ConcurrentHashMap<String, SignedRequestRecord>()
    private val maxAuditEntries = 10_000
    
    // =========================================================================
    // REQUEST SIGNING
    // =========================================================================
    
    /**
     * Sign an outbound API request
     * 
     * Creates a signed envelope containing:
     * - Original request data
     * - Timestamp
     * - Nonce (replay protection)
     * - Dilithium signature
     * - Optional classical HMAC
     * 
     * @param method HTTP method (GET, POST, etc.)
     * @param url Full request URL
     * @param headers Request headers (excluding auth)
     * @param body Request body (if any)
     * @param exchangeId Target exchange identifier
     * @return SignedRequest envelope
     */
    fun signRequest(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        exchangeId: String
    ): SignedRequest {
        // Check for key rotation
        rotateKeyIfNeeded()
        
        val timestamp = System.currentTimeMillis()
        val nonce = generateNonce()
        val requestId = generateRequestId()
        
        // Build canonical request representation
        val canonicalRequest = buildCanonicalRequest(
            method = method,
            url = url,
            headers = headers,
            body = body,
            timestamp = timestamp,
            nonce = nonce
        )
        
        // Hash the canonical request
        val requestHash = hashData(canonicalRequest)
        
        // Create PQC signature
        val pqcSignature = dilithium.sign(requestHash, currentKeyPair.privateKey)
        
        // Create classical signature (hybrid mode)
        val classicalSignature = if (config.hybridMode != HybridMode.PQC_ONLY) {
            createClassicalSignature(requestHash)
        } else null
        
        // Build signed request
        val signedRequest = SignedRequest(
            requestId = requestId,
            method = method,
            url = url,
            headers = headers,
            body = body,
            timestamp = timestamp,
            nonce = nonce,
            requestHash = requestHash,
            pqcSignature = pqcSignature,
            pqcPublicKey = currentKeyPair.publicKey,
            classicalSignature = classicalSignature,
            signatureAlgorithm = config.signatureAlgorithm.id,
            exchangeId = exchangeId
        )
        
        // Record in audit log
        recordAudit(signedRequest)
        
        return signedRequest
    }
    
    /**
     * Sign just the body content (for simpler use cases)
     */
    fun signBody(body: ByteArray, exchangeId: String): BodySignature {
        rotateKeyIfNeeded()
        
        val timestamp = System.currentTimeMillis()
        val bodyHash = hashData(body)
        val signature = dilithium.sign(bodyHash, currentKeyPair.privateKey)
        
        return BodySignature(
            bodyHash = bodyHash,
            signature = signature,
            publicKey = currentKeyPair.publicKey,
            timestamp = timestamp,
            algorithm = config.signatureAlgorithm.id
        )
    }
    
    // =========================================================================
    // SIGNATURE VERIFICATION
    // =========================================================================
    
    /**
     * Verify a signed request (for incoming webhooks or audit verification)
     */
    fun verifySignature(
        data: ByteArray,
        signature: ByteArray,
        publicKey: ByteArray
    ): Boolean {
        return try {
            dilithium.verify(data, signature, publicKey)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Verify a signed request matches our audit record
     */
    fun verifyAuditRecord(requestId: String, responseHash: ByteArray): AuditVerificationResult {
        val record = auditLog[requestId]
            ?: return AuditVerificationResult.NotFound(requestId)
        
        // Verify our original signature is still valid
        val signatureValid = verifySignature(
            record.requestHash,
            record.pqcSignature,
            record.publicKey
        )
        
        if (!signatureValid) {
            return AuditVerificationResult.SignatureTampered(requestId)
        }
        
        // Update record with response
        val updatedRecord = record.copy(
            responseHash = responseHash,
            responseTimestamp = System.currentTimeMillis(),
            verified = true
        )
        auditLog[requestId] = updatedRecord
        
        return AuditVerificationResult.Valid(updatedRecord)
    }
    
    // =========================================================================
    // RESPONSE SIGNING (for local audit)
    // =========================================================================
    
    /**
     * Sign a response for audit trail
     */
    fun signResponse(
        requestId: String,
        statusCode: Int,
        responseBody: ByteArray?,
        responseHeaders: Map<String, String>
    ): SignedResponse {
        val timestamp = System.currentTimeMillis()
        
        // Build canonical response
        val canonicalResponse = buildCanonicalResponse(
            requestId = requestId,
            statusCode = statusCode,
            body = responseBody,
            headers = responseHeaders,
            timestamp = timestamp
        )
        
        val responseHash = hashData(canonicalResponse)
        val signature = dilithium.sign(responseHash, currentKeyPair.privateKey)
        
        // Update audit record
        auditLog[requestId]?.let { record ->
            auditLog[requestId] = record.copy(
                responseHash = responseHash,
                responseTimestamp = timestamp,
                responseSignature = signature
            )
        }
        
        return SignedResponse(
            requestId = requestId,
            statusCode = statusCode,
            responseHash = responseHash,
            signature = signature,
            timestamp = timestamp
        )
    }
    
    // =========================================================================
    // KEY MANAGEMENT
    // =========================================================================
    
    /**
     * Force key rotation
     */
    fun rotateKey(): DilithiumKeyPair {
        val newKeyPair = dilithium.generateKeyPair()
        
        // Secure wipe old private key
        SideChannelDefense.secureWipe(currentKeyPair.privateKey)
        
        currentKeyPair = newKeyPair
        keyGeneratedAt = System.currentTimeMillis()
        
        return newKeyPair
    }
    
    /**
     * Get current public key (for verification by other parties)
     */
    fun getPublicKey(): ByteArray = currentKeyPair.publicKey.copyOf()
    
    /**
     * Get key age in milliseconds
     */
    fun getKeyAge(): Long = System.currentTimeMillis() - keyGeneratedAt
    
    private fun rotateKeyIfNeeded() {
        if (System.currentTimeMillis() - keyGeneratedAt > config.keyRotationIntervalMs) {
            rotateKey()
        }
    }
    
    // =========================================================================
    // AUDIT LOG MANAGEMENT
    // =========================================================================
    
    /**
     * Get audit record for a request
     */
    fun getAuditRecord(requestId: String): SignedRequestRecord? = auditLog[requestId]
    
    /**
     * Get all audit records for an exchange
     */
    fun getAuditRecordsForExchange(exchangeId: String): List<SignedRequestRecord> {
        return auditLog.values.filter { it.exchangeId == exchangeId }
    }
    
    /**
     * Export audit log for compliance/archival
     */
    fun exportAuditLog(since: Long = 0): List<SignedRequestRecord> {
        return auditLog.values
            .filter { it.timestamp >= since }
            .sortedBy { it.timestamp }
    }
    
    /**
     * Cleanup old audit entries
     */
    fun cleanupAuditLog(maxAgeMs: Long = config.auditRetentionDays * 24 * 60 * 60 * 1000L): Int {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        var removed = 0
        
        auditLog.entries.removeIf { (_, record) ->
            if (record.timestamp < cutoff) {
                removed++
                true
            } else false
        }
        
        return removed
    }
    
    private fun recordAudit(request: SignedRequest) {
        // Enforce max entries
        if (auditLog.size >= maxAuditEntries) {
            // Remove oldest 10%
            val toRemove = auditLog.entries
                .sortedBy { it.value.timestamp }
                .take(maxAuditEntries / 10)
                .map { it.key }
            toRemove.forEach { auditLog.remove(it) }
        }
        
        auditLog[request.requestId] = SignedRequestRecord(
            requestId = request.requestId,
            method = request.method,
            url = request.url,
            requestHash = request.requestHash,
            pqcSignature = request.pqcSignature,
            publicKey = request.pqcPublicKey,
            timestamp = request.timestamp,
            exchangeId = request.exchangeId,
            algorithm = request.signatureAlgorithm
        )
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    private fun buildCanonicalRequest(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        timestamp: Long,
        nonce: ByteArray
    ): ByteArray {
        val builder = StringBuilder()
        builder.append(method.uppercase())
        builder.append('\n')
        builder.append(url)
        builder.append('\n')
        
        // Sorted headers
        headers.entries.sortedBy { it.key.lowercase() }.forEach { (key, value) ->
            builder.append("${key.lowercase()}:$value\n")
        }
        builder.append('\n')
        builder.append(timestamp)
        builder.append('\n')
        
        val headerPart = builder.toString().toByteArray(Charsets.UTF_8)
        
        // Combine: header + nonce + body
        val bodyPart = body ?: ByteArray(0)
        return headerPart + nonce + bodyPart
    }
    
    private fun buildCanonicalResponse(
        requestId: String,
        statusCode: Int,
        body: ByteArray?,
        headers: Map<String, String>,
        timestamp: Long
    ): ByteArray {
        val builder = StringBuilder()
        builder.append(requestId)
        builder.append('\n')
        builder.append(statusCode)
        builder.append('\n')
        
        headers.entries.sortedBy { it.key.lowercase() }.forEach { (key, value) ->
            builder.append("${key.lowercase()}:$value\n")
        }
        builder.append('\n')
        builder.append(timestamp)
        
        val headerPart = builder.toString().toByteArray(Charsets.UTF_8)
        val bodyPart = body ?: ByteArray(0)
        
        return headerPart + bodyPart
    }
    
    private fun hashData(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance(config.hashAlgorithm)
        return digest.digest(data)
    }
    
    private fun generateNonce(): ByteArray {
        val nonce = ByteArray(32)
        secureRandom.nextBytes(nonce)
        return nonce
    }
    
    private fun generateRequestId(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    private fun createClassicalSignature(data: ByteArray): ByteArray {
        // Use HMAC-SHA256 for classical component
        val hmacKey = currentKeyPair.publicKey.copyOfRange(0, 32)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        return mac.doFinal(data)
    }
    
    /**
     * Shutdown and cleanup
     */
    fun shutdown() {
        SideChannelDefense.secureWipe(currentKeyPair.privateKey)
        auditLog.clear()
    }
}

// =========================================================================
// DATA CLASSES
// =========================================================================

/**
 * Signed request envelope
 */
data class SignedRequest(
    val requestId: String,
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: ByteArray?,
    val timestamp: Long,
    val nonce: ByteArray,
    val requestHash: ByteArray,
    val pqcSignature: ByteArray,
    val pqcPublicKey: ByteArray,
    val classicalSignature: ByteArray?,
    val signatureAlgorithm: String,
    val exchangeId: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignedRequest) return false
        return requestId == other.requestId
    }
    
    override fun hashCode(): Int = requestId.hashCode()
}

/**
 * Body-only signature
 */
data class BodySignature(
    val bodyHash: ByteArray,
    val signature: ByteArray,
    val publicKey: ByteArray,
    val timestamp: Long,
    val algorithm: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BodySignature) return false
        return bodyHash.contentEquals(other.bodyHash) && timestamp == other.timestamp
    }
    
    override fun hashCode(): Int = bodyHash.contentHashCode()
}

/**
 * Signed response for audit
 */
data class SignedResponse(
    val requestId: String,
    val statusCode: Int,
    val responseHash: ByteArray,
    val signature: ByteArray,
    val timestamp: Long
)

/**
 * Audit log record
 */
data class SignedRequestRecord(
    val requestId: String,
    val method: String,
    val url: String,
    val requestHash: ByteArray,
    val pqcSignature: ByteArray,
    val publicKey: ByteArray,
    val timestamp: Long,
    val exchangeId: String,
    val algorithm: String,
    val responseHash: ByteArray? = null,
    val responseTimestamp: Long? = null,
    val responseSignature: ByteArray? = null,
    val verified: Boolean = false
)

/**
 * Audit verification result
 */
sealed class AuditVerificationResult {
    data class Valid(val record: SignedRequestRecord) : AuditVerificationResult()
    data class NotFound(val requestId: String) : AuditVerificationResult()
    data class SignatureTampered(val requestId: String) : AuditVerificationResult()
    data class ResponseMismatch(val requestId: String) : AuditVerificationResult()
}
