package com.miwealth.sovereignvantage.core.security.pqc

import java.security.SecureRandom

/**
 * PQCE Engine - Main Entry Point for Post-Quantum Cryptography
 * 
 * Provides a unified interface for all post-quantum cryptographic operations:
 * - Key generation (Kyber for encryption, Dilithium for signatures)
 * - Encryption/Decryption with noise injection
 * - Digital signatures
 * - Secure channel establishment
 * 
 * Usage:
 * ```kotlin
 * val engine = PQCEngine()
 * 
 * // Generate keys
 * val encryptionKeys = engine.generateEncryptionKeyPair()
 * val signingKeys = engine.generateSigningKeyPair()
 * 
 * // Encrypt data
 * val encrypted = engine.encrypt(data, recipientPublicKey)
 * val decrypted = engine.decrypt(encrypted, recipientPrivateKey)
 * 
 * // Sign and verify
 * val signature = engine.sign(message, signingKeys.privateKey)
 * val valid = engine.verify(message, signature, signingKeys.publicKey)
 * ```
 * 
 * @author MiWealth Development Team
 * @version 1.0
 * @since 2026-01-19
 */
class PQCEngine(
    private val securityLevel: Int = 5
) {
    private val kyber = KyberKEM(securityLevel)
    private val dilithium = DilithiumDSA(if (securityLevel == 1) 2 else securityLevel)
    private val secureRandom = SecureRandom()
    
    // Lazy initialization of secure channel (resource-heavy)
    private val secureChannel: PQCESecureChannel by lazy { 
        PQCESecureChannel(securityLevel) 
    }
    
    // ========================================================================
    // KEY GENERATION
    // ========================================================================
    
    /**
     * Generate Kyber key pair for encryption/key encapsulation
     */
    fun generateEncryptionKeyPair(): KyberKeyPair = kyber.generateKeyPair()
    
    /**
     * Generate Dilithium key pair for digital signatures
     */
    fun generateSigningKeyPair(): DilithiumKeyPair = dilithium.generateKeyPair()
    
    /**
     * Generate both encryption and signing key pairs
     */
    fun generateKeyPairs(): KeyPairs {
        return KeyPairs(
            encryption = generateEncryptionKeyPair(),
            signing = generateSigningKeyPair()
        )
    }
    
    // ========================================================================
    // ENCRYPTION / DECRYPTION
    // ========================================================================
    
    /**
     * Encrypt data using Kyber key encapsulation + AES-256-GCM
     * 
     * @param plaintext Data to encrypt
     * @param recipientPublicKey Recipient's Kyber public key
     * @return EncryptedPackage containing ciphertext and encapsulated key
     */
    fun encrypt(plaintext: ByteArray, recipientPublicKey: ByteArray): EncryptedPackage {
        // Encapsulate shared secret
        val encapsulation = kyber.encapsulate(recipientPublicKey)
        
        // Generate nonce
        val nonce = ByteArray(PQCConfig.AES_GCM_NONCE_SIZE).also { secureRandom.nextBytes(it) }
        
        // Encrypt with AES-256-GCM
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = javax.crypto.spec.SecretKeySpec(encapsulation.sharedSecret, "AES")
        val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, nonce)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(plaintext)
        
        // Secure wipe shared secret
        SideChannelDefense.secureWipe(encapsulation.sharedSecret)
        
        return EncryptedPackage(
            ciphertext = ciphertext,
            encapsulatedKey = encapsulation.ciphertext,
            nonce = nonce
        )
    }
    
    /**
     * Decrypt data using Kyber decapsulation + AES-256-GCM
     * 
     * @param encrypted Encrypted package from encrypt()
     * @param recipientPrivateKey Recipient's Kyber private key
     * @return Decrypted plaintext
     */
    fun decrypt(encrypted: EncryptedPackage, recipientPrivateKey: ByteArray): ByteArray {
        // Decapsulate shared secret
        val sharedSecret = kyber.decapsulate(encrypted.encapsulatedKey, recipientPrivateKey)
        
        // Decrypt with AES-256-GCM
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = javax.crypto.spec.SecretKeySpec(sharedSecret, "AES")
        val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, encrypted.nonce)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        val plaintext = cipher.doFinal(encrypted.ciphertext)
        
        // Secure wipe shared secret
        SideChannelDefense.secureWipe(sharedSecret)
        
        return plaintext
    }
    
    // ========================================================================
    // DIGITAL SIGNATURES
    // ========================================================================
    
    /**
     * Sign a message using Dilithium
     */
    fun sign(message: ByteArray, privateKey: ByteArray): ByteArray {
        return dilithium.sign(message, privateKey)
    }
    
    /**
     * Verify a Dilithium signature
     */
    fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return dilithium.verify(message, signature, publicKey)
    }
    
    /**
     * Sign and encrypt (authenticated encryption)
     */
    fun signAndEncrypt(
        plaintext: ByteArray,
        senderSigningKey: ByteArray,
        recipientPublicKey: ByteArray
    ): SignedEncryptedPackage {
        val signature = sign(plaintext, senderSigningKey)
        val encrypted = encrypt(plaintext, recipientPublicKey)
        return SignedEncryptedPackage(encrypted, signature)
    }
    
    /**
     * Decrypt and verify signature
     */
    fun decryptAndVerify(
        package_: SignedEncryptedPackage,
        recipientPrivateKey: ByteArray,
        senderPublicKey: ByteArray
    ): ByteArray? {
        val plaintext = decrypt(package_.encrypted, recipientPrivateKey)
        val valid = verify(plaintext, package_.signature, senderPublicKey)
        return if (valid) plaintext else null
    }
    
    // ========================================================================
    // SECURE CHANNEL OPERATIONS
    // ========================================================================
    
    /**
     * Initialize as a network node with credentials
     */
    fun initializeNode(nodeId: String, permissions: List<String>): NodeCredentials {
        return secureChannel.initializeNode(nodeId, permissions)
    }
    
    /**
     * Establish secure session with remote node
     */
    fun establishSession(remotePublicKey: ByteArray): PQCESessionKey {
        return secureChannel.establishSession(remotePublicKey)
    }
    
    /**
     * Send encrypted message through secure channel
     */
    fun sendSecureMessage(plaintext: ByteArray, sessionId: String): PQCEMessage? {
        return secureChannel.encryptMessage(plaintext, sessionId)
    }
    
    /**
     * Receive and decrypt message from secure channel
     */
    fun receiveSecureMessage(message: PQCEMessage): ByteArray? {
        return secureChannel.decryptMessage(message)
    }
    
    /**
     * Get security report for a session
     */
    fun getSecurityReport(sessionId: String): TransitSecurityReport? {
        return secureChannel.getSecurityReport(sessionId)
    }
    
    // ========================================================================
    // UTILITY METHODS
    // ========================================================================
    
    /**
     * Get the current security level
     */
    fun getSecurityLevel(): Int = securityLevel
    
    /**
     * Get security level description
     */
    fun getSecurityLevelDescription(): String = when (securityLevel) {
        5 -> "Maximum (NIST Level 5 - AES-256 equivalent)"
        3 -> "High (NIST Level 3 - AES-192 equivalent)"
        1 -> "Standard (NIST Level 1 - AES-128 equivalent)"
        else -> "Unknown"
    }
    
    /**
     * Clean up resources
     */
    fun shutdown() {
        secureChannel.shutdown()
    }
    
    /**
     * Clean up expired sessions
     */
    fun cleanupSessions(): Int = secureChannel.cleanupExpiredSessions()
}

/**
 * Container for both encryption and signing key pairs
 */
data class KeyPairs(
    val encryption: KyberKeyPair,
    val signing: DilithiumKeyPair
)

/**
 * Encrypted data package
 */
data class EncryptedPackage(
    val ciphertext: ByteArray,
    val encapsulatedKey: ByteArray,
    val nonce: ByteArray
) {
    /**
     * Serialize to byte array for transmission
     */
    fun serialize(): ByteArray {
        val result = ByteArray(12 + ciphertext.size + encapsulatedKey.size + nonce.size)
        var offset = 0
        
        // Write lengths
        writeInt(result, offset, ciphertext.size); offset += 4
        writeInt(result, offset, encapsulatedKey.size); offset += 4
        writeInt(result, offset, nonce.size); offset += 4
        
        // Write data
        ciphertext.copyInto(result, offset); offset += ciphertext.size
        encapsulatedKey.copyInto(result, offset); offset += encapsulatedKey.size
        nonce.copyInto(result, offset)
        
        return result
    }
    
    companion object {
        fun deserialize(data: ByteArray): EncryptedPackage {
            var offset = 0
            
            val ciphertextLen = readInt(data, offset); offset += 4
            val encapsulatedKeyLen = readInt(data, offset); offset += 4
            val nonceLen = readInt(data, offset); offset += 4
            
            val ciphertext = data.copyOfRange(offset, offset + ciphertextLen); offset += ciphertextLen
            val encapsulatedKey = data.copyOfRange(offset, offset + encapsulatedKeyLen); offset += encapsulatedKeyLen
            val nonce = data.copyOfRange(offset, offset + nonceLen)
            
            return EncryptedPackage(ciphertext, encapsulatedKey, nonce)
        }
        
        private fun readInt(data: ByteArray, offset: Int): Int {
            return ((data[offset].toInt() and 0xFF) shl 24) or
                   ((data[offset + 1].toInt() and 0xFF) shl 16) or
                   ((data[offset + 2].toInt() and 0xFF) shl 8) or
                   (data[offset + 3].toInt() and 0xFF)
        }
    }
    
    private fun writeInt(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value shr 24).toByte()
        buffer[offset + 1] = (value shr 16).toByte()
        buffer[offset + 2] = (value shr 8).toByte()
        buffer[offset + 3] = value.toByte()
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedPackage) return false
        return ciphertext.contentEquals(other.ciphertext) &&
               encapsulatedKey.contentEquals(other.encapsulatedKey) &&
               nonce.contentEquals(other.nonce)
    }
    
    override fun hashCode(): Int = ciphertext.contentHashCode()
}

/**
 * Signed and encrypted package
 */
data class SignedEncryptedPackage(
    val encrypted: EncryptedPackage,
    val signature: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignedEncryptedPackage) return false
        return encrypted == other.encrypted && signature.contentEquals(other.signature)
    }
    
    override fun hashCode(): Int {
        var result = encrypted.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}
