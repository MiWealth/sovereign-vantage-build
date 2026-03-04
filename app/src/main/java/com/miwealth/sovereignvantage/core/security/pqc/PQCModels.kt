package com.miwealth.sovereignvantage.core.security.pqc

import java.util.UUID

/**
 * PQCE Data Models
 * 
 * Data classes for post-quantum cryptographic operations including
 * session keys, encrypted messages, noise patterns, and credentials.
 * 
 * @author MiWealth Development Team
 * @version 1.0
 * @since 2026-01-19
 */

// ============================================================================
// KEY PAIR MODELS
// ============================================================================

/**
 * Kyber key pair for key encapsulation
 */
data class KyberKeyPair(
    val publicKey: ByteArray,
    val privateKey: ByteArray,
    val securityLevel: Int = 5
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KyberKeyPair) return false
        return publicKey.contentEquals(other.publicKey) &&
               privateKey.contentEquals(other.privateKey) &&
               securityLevel == other.securityLevel
    }
    
    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        result = 31 * result + securityLevel
        return result
    }
}

/**
 * Dilithium key pair for digital signatures
 */
data class DilithiumKeyPair(
    val publicKey: ByteArray,
    val privateKey: ByteArray,
    val securityLevel: Int = 3
) {
    /**
     * Generate peer identity from public key using SHA3-256
     * Used for P2P node identification
     */
    fun identityHex(): String {
        val digest = java.security.MessageDigest.getInstance("SHA3-256")
        val hash = digest.digest(publicKey)
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DilithiumKeyPair) return false
        return publicKey.contentEquals(other.publicKey) &&
               privateKey.contentEquals(other.privateKey) &&
               securityLevel == other.securityLevel
    }
    
    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        result = 31 * result + securityLevel
        return result
    }
}

/**
 * Encapsulation result from Kyber KEM
 */
data class EncapsulationResult(
    val ciphertext: ByteArray,
    val sharedSecret: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncapsulationResult) return false
        return ciphertext.contentEquals(other.ciphertext) &&
               sharedSecret.contentEquals(other.sharedSecret)
    }
    
    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + sharedSecret.contentHashCode()
        return result
    }
}

// ============================================================================
// SESSION MODELS
// ============================================================================

/**
 * PQCE Session Key for secure channel communication
 */
data class PQCESessionKey(
    val id: String = UUID.randomUUID().toString(),
    val publicKey: ByteArray,
    val privateKey: ByteArray,
    val sharedSecret: ByteArray?,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long,
    val securityLevel: Int = 5
) {
    /**
     * Check if session has expired
     */
    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
    
    /**
     * Check if session is valid (not expired and has shared secret)
     */
    fun isValid(): Boolean = !isExpired() && sharedSecret != null
    
    /**
     * Time remaining until expiry in milliseconds
     */
    fun timeRemaining(): Long = maxOf(0, expiresAt - System.currentTimeMillis())
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PQCESessionKey) return false
        return id == other.id
    }
    
    override fun hashCode(): Int = id.hashCode()
}

// ============================================================================
// MESSAGE MODELS
// ============================================================================

/**
 * Encrypted PQCE Message with noise injection
 */
data class PQCEMessage(
    val sessionId: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val noise: ByteArray,
    val signature: ByteArray,
    val timestamp: Long = System.currentTimeMillis(),
    val noisePattern: String
) {
    /**
     * Total message size in bytes
     */
    fun totalSize(): Int = nonce.size + ciphertext.size + noise.size + signature.size
    
    /**
     * Serialize message to ByteArray for transmission
     */
    fun serialize(): ByteArray {
        val sessionIdBytes = sessionId.toByteArray(Charsets.UTF_8)
        val patternBytes = noisePattern.toByteArray(Charsets.UTF_8)
        
        return ByteArray(
            4 + sessionIdBytes.size +  // Session ID length + data
            4 + nonce.size +           // Nonce length + data
            4 + ciphertext.size +      // Ciphertext length + data
            4 + noise.size +           // Noise length + data
            4 + signature.size +       // Signature length + data
            8 +                        // Timestamp
            4 + patternBytes.size      // Pattern length + data
        ).also { buffer ->
            var offset = 0
            
            // Write session ID
            writeInt(buffer, offset, sessionIdBytes.size)
            offset += 4
            sessionIdBytes.copyInto(buffer, offset)
            offset += sessionIdBytes.size
            
            // Write nonce
            writeInt(buffer, offset, nonce.size)
            offset += 4
            nonce.copyInto(buffer, offset)
            offset += nonce.size
            
            // Write ciphertext
            writeInt(buffer, offset, ciphertext.size)
            offset += 4
            ciphertext.copyInto(buffer, offset)
            offset += ciphertext.size
            
            // Write noise
            writeInt(buffer, offset, noise.size)
            offset += 4
            noise.copyInto(buffer, offset)
            offset += noise.size
            
            // Write signature
            writeInt(buffer, offset, signature.size)
            offset += 4
            signature.copyInto(buffer, offset)
            offset += signature.size
            
            // Write timestamp
            writeLong(buffer, offset, timestamp)
            offset += 8
            
            // Write noise pattern
            writeInt(buffer, offset, patternBytes.size)
            offset += 4
            patternBytes.copyInto(buffer, offset)
        }
    }
    
    companion object {
        /**
         * Deserialize message from ByteArray
         */
        fun deserialize(data: ByteArray): PQCEMessage {
            var offset = 0
            
            // Read session ID
            val sessionIdLen = readInt(data, offset)
            offset += 4
            val sessionId = String(data.copyOfRange(offset, offset + sessionIdLen), Charsets.UTF_8)
            offset += sessionIdLen
            
            // Read nonce
            val nonceLen = readInt(data, offset)
            offset += 4
            val nonce = data.copyOfRange(offset, offset + nonceLen)
            offset += nonceLen
            
            // Read ciphertext
            val ciphertextLen = readInt(data, offset)
            offset += 4
            val ciphertext = data.copyOfRange(offset, offset + ciphertextLen)
            offset += ciphertextLen
            
            // Read noise
            val noiseLen = readInt(data, offset)
            offset += 4
            val noise = data.copyOfRange(offset, offset + noiseLen)
            offset += noiseLen
            
            // Read signature
            val signatureLen = readInt(data, offset)
            offset += 4
            val signature = data.copyOfRange(offset, offset + signatureLen)
            offset += signatureLen
            
            // Read timestamp
            val timestamp = readLong(data, offset)
            offset += 8
            
            // Read noise pattern
            val patternLen = readInt(data, offset)
            offset += 4
            val noisePattern = String(data.copyOfRange(offset, offset + patternLen), Charsets.UTF_8)
            
            return PQCEMessage(
                sessionId = sessionId,
                nonce = nonce,
                ciphertext = ciphertext,
                noise = noise,
                signature = signature,
                timestamp = timestamp,
                noisePattern = noisePattern
            )
        }
        
        private fun readInt(data: ByteArray, offset: Int): Int {
            return ((data[offset].toInt() and 0xFF) shl 24) or
                   ((data[offset + 1].toInt() and 0xFF) shl 16) or
                   ((data[offset + 2].toInt() and 0xFF) shl 8) or
                   (data[offset + 3].toInt() and 0xFF)
        }
        
        private fun readLong(data: ByteArray, offset: Int): Long {
            return ((data[offset].toLong() and 0xFF) shl 56) or
                   ((data[offset + 1].toLong() and 0xFF) shl 48) or
                   ((data[offset + 2].toLong() and 0xFF) shl 40) or
                   ((data[offset + 3].toLong() and 0xFF) shl 32) or
                   ((data[offset + 4].toLong() and 0xFF) shl 24) or
                   ((data[offset + 5].toLong() and 0xFF) shl 16) or
                   ((data[offset + 6].toLong() and 0xFF) shl 8) or
                   (data[offset + 7].toLong() and 0xFF)
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PQCEMessage) return false
        return sessionId == other.sessionId && timestamp == other.timestamp
    }
    
    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

private fun writeInt(buffer: ByteArray, offset: Int, value: Int) {
    buffer[offset] = (value shr 24).toByte()
    buffer[offset + 1] = (value shr 16).toByte()
    buffer[offset + 2] = (value shr 8).toByte()
    buffer[offset + 3] = value.toByte()
}

private fun writeLong(buffer: ByteArray, offset: Int, value: Long) {
    buffer[offset] = (value shr 56).toByte()
    buffer[offset + 1] = (value shr 48).toByte()
    buffer[offset + 2] = (value shr 40).toByte()
    buffer[offset + 3] = (value shr 32).toByte()
    buffer[offset + 4] = (value shr 24).toByte()
    buffer[offset + 5] = (value shr 16).toByte()
    buffer[offset + 6] = (value shr 8).toByte()
    buffer[offset + 7] = value.toByte()
}

// ============================================================================
// NOISE PATTERN MODELS
// ============================================================================

/**
 * Noise pattern for side-channel attack defense
 */
data class NoisePattern(
    val id: String = UUID.randomUUID().toString(),
    val seed: ByteArray,
    val polynomial: IntArray,
    val rotationIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long
) {
    /**
     * Check if pattern has expired
     */
    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
    
    /**
     * Create next rotation of this pattern
     */
    fun rotate(): NoisePattern = copy(
        rotationIndex = rotationIndex + 1,
        createdAt = System.currentTimeMillis(),
        expiresAt = System.currentTimeMillis() + PQCConfig.NOISE_ROTATION_INTERVAL_MS
    )
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NoisePattern) return false
        return id == other.id && rotationIndex == other.rotationIndex
    }
    
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + rotationIndex
        return result
    }
}

// ============================================================================
// NODE CREDENTIALS
// ============================================================================

/**
 * Credentials for a network node
 */
data class NodeCredentials(
    val nodeId: String,
    val publicKey: ByteArray,
    val noiseFilterKey: ByteArray,
    val authToken: ByteArray,
    val permissions: List<String>,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Check if node has specific permission
     */
    fun hasPermission(permission: String): Boolean = permissions.contains(permission)
    
    /**
     * Check if node has any of the specified permissions
     */
    fun hasAnyPermission(vararg perms: String): Boolean = perms.any { permissions.contains(it) }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NodeCredentials) return false
        return nodeId == other.nodeId
    }
    
    override fun hashCode(): Int = nodeId.hashCode()
}

/**
 * Standard node permissions
 */
object NodePermissions {
    const val READ = "read"
    const val WRITE = "write"
    const val DECRYPT = "decrypt"
    const val SIGN = "sign"
    const val ADMIN = "admin"
    const val AGGREGATE = "aggregate"
    const val BROADCAST = "broadcast"
}

// ============================================================================
// SECURITY REPORT
// ============================================================================

/**
 * Security status report for a session
 */
data class TransitSecurityReport(
    val sessionId: String,
    val encryptionLevel: String,
    val noiseLevel: Double,
    val sideChannelProtection: Boolean,
    val integrityVerified: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Overall security score (0.0 - 1.0)
     */
    fun securityScore(): Double {
        var score = 0.0
        
        // Encryption level contribution (40%)
        score += when {
            encryptionLevel.contains("1024") -> 0.40
            encryptionLevel.contains("768") -> 0.30
            encryptionLevel.contains("512") -> 0.20
            else -> 0.10
        }
        
        // Noise level contribution (20%)
        score += noiseLevel.coerceIn(0.0, 0.20)
        
        // Side channel protection (20%)
        if (sideChannelProtection) score += 0.20
        
        // Integrity verification (20%)
        if (integrityVerified) score += 0.20
        
        return score.coerceIn(0.0, 1.0)
    }
    
    /**
     * Human-readable security status
     */
    fun statusDescription(): String = when {
        securityScore() >= 0.9 -> "MAXIMUM SECURITY"
        securityScore() >= 0.7 -> "HIGH SECURITY"
        securityScore() >= 0.5 -> "MODERATE SECURITY"
        else -> "LOW SECURITY - UPGRADE RECOMMENDED"
    }
}

// ============================================================================
// EXCEPTIONS
// ============================================================================

/**
 * Base exception for PQC operations
 */
open class PQCException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Key generation failed
 */
class KeyGenerationException(message: String, cause: Throwable? = null) : PQCException(message, cause)

/**
 * Encryption failed
 */
class EncryptionException(message: String, cause: Throwable? = null) : PQCException(message, cause)

/**
 * Decryption failed
 */
class DecryptionException(message: String, cause: Throwable? = null) : PQCException(message, cause)

/**
 * Signature operation failed
 */
class SignatureException(message: String, cause: Throwable? = null) : PQCException(message, cause)

/**
 * Session expired or invalid
 */
class SessionException(message: String, cause: Throwable? = null) : PQCException(message, cause)

/**
 * Noise pattern error
 */
class NoisePatternException(message: String, cause: Throwable? = null) : PQCException(message, cause)

/**
 * Node credentials error
 */
class CredentialsException(message: String, cause: Throwable? = null) : PQCException(message, cause)
