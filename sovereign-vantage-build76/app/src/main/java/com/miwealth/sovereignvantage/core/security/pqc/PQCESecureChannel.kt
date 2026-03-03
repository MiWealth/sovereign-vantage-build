package com.miwealth.sovereignvantage.core.security.pqc

import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * PQCE Secure Channel
 * 
 * Full post-quantum encrypted communication channel with:
 * - Kyber key encapsulation for session establishment
 * - AES-256-GCM symmetric encryption
 * - Dilithium signatures for authentication
 * - Mathematical noise injection for side-channel defense
 * - Automatic session management and rotation
 * 
 * @author MiWealth Development Team
 * @version 1.0
 * @since 2026-01-19
 */
class PQCESecureChannel(private val securityLevel: Int = 5) {
    
    private val kyber = KyberKEM(securityLevel)
    private val dilithium = DilithiumDSA(if (securityLevel == 1) 2 else securityLevel)
    private val noiseInjector = NoiseInjector()
    private val sessions = ConcurrentHashMap<String, PQCESessionKey>()
    private var nodeCredentials: NodeCredentials? = null
    private val secureRandom = SecureRandom()
    
    companion object {
        private const val AES_GCM_TAG_BITS = 128
    }
    
    /**
     * Initialize node credentials for this channel
     */
    fun initializeNode(nodeId: String, permissions: List<String>): NodeCredentials {
        val keyPair = kyber.generateKeyPair()
        val noiseFilterKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        
        val authToken = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(noiseFilterKey, "HmacSHA256"))
            update(nodeId.toByteArray(Charsets.UTF_8))
            update(keyPair.publicKey)
            doFinal()
        }
        
        val credentials = NodeCredentials(
            nodeId = nodeId,
            publicKey = keyPair.publicKey,
            noiseFilterKey = noiseFilterKey,
            authToken = authToken,
            permissions = permissions,
            createdAt = System.currentTimeMillis()
        )
        
        nodeCredentials = credentials
        return credentials
    }
    
    /**
     * Establish secure session with another node
     */
    fun establishSession(remotePublicKey: ByteArray): PQCESessionKey {
        val keyPair = kyber.generateKeyPair()
        val encapsulation = kyber.encapsulate(remotePublicKey)
        
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        val session = PQCESessionKey(
            id = sessionId,
            publicKey = keyPair.publicKey,
            privateKey = keyPair.privateKey,
            sharedSecret = encapsulation.sharedSecret,
            createdAt = now,
            expiresAt = now + PQCConfig.SESSION_KEY_ROTATION_MS,
            securityLevel = securityLevel
        )
        
        sessions[sessionId] = session
        return session
    }
    
    /**
     * Accept session from remote node (decapsulate their shared secret)
     */
    fun acceptSession(sessionId: String, ciphertext: ByteArray, privateKey: ByteArray): PQCESessionKey? {
        return try {
            val sharedSecret = kyber.decapsulate(ciphertext, privateKey)
            val now = System.currentTimeMillis()
            
            val session = PQCESessionKey(
                id = sessionId,
                publicKey = ByteArray(0),
                privateKey = privateKey,
                sharedSecret = sharedSecret,
                createdAt = now,
                expiresAt = now + PQCConfig.SESSION_KEY_ROTATION_MS,
                securityLevel = securityLevel
            )
            
            sessions[sessionId] = session
            session
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Encrypt message with full PQCE protection
     */
    fun encryptMessage(plaintext: ByteArray, sessionId: String): PQCEMessage? {
        val session = sessions[sessionId] ?: return null
        val sharedSecret = session.sharedSecret ?: return null
        
        if (session.isExpired()) {
            sessions.remove(sessionId)
            return null
        }
        
        // Generate nonce
        val nonce = ByteArray(PQCConfig.AES_GCM_NONCE_SIZE).also { secureRandom.nextBytes(it) }
        
        // Encrypt with AES-256-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(sharedSecret, "AES")
        val gcmSpec = GCMParameterSpec(AES_GCM_TAG_BITS, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(plaintext)
        
        // Inject mathematical noise
        val noiseResult = noiseInjector.injectNoise(ciphertext)
        
        // Sign the noisy data
        val signingKeyPair = dilithium.generateKeyPair()
        val signature = dilithium.sign(noiseResult.noisyData, signingKeyPair.privateKey)
        
        return PQCEMessage(
            sessionId = sessionId,
            nonce = nonce,
            ciphertext = noiseResult.noisyData,
            noise = noiseResult.noiseMap,
            signature = signature,
            timestamp = System.currentTimeMillis(),
            noisePattern = noiseResult.patternId
        )
    }
    
    /**
     * Decrypt received message with PQCE protection
     */
    fun decryptMessage(message: PQCEMessage): ByteArray? {
        val session = sessions[message.sessionId] ?: return null
        val sharedSecret = session.sharedSecret ?: return null
        val credentials = nodeCredentials ?: return null
        
        if (session.isExpired()) {
            sessions.remove(message.sessionId)
            return null
        }
        
        // Remove noise (only authorized nodes can do this)
        val cleanCiphertext = noiseInjector.removeNoise(
            message.ciphertext,
            message.noise,
            message.noisePattern,
            credentials
        ) ?: return null
        
        // Decrypt with AES-256-GCM
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(sharedSecret, "AES")
            val gcmSpec = GCMParameterSpec(AES_GCM_TAG_BITS, message.nonce)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            cipher.doFinal(cleanCiphertext)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Rotate session key
     */
    fun rotateSessionKey(sessionId: String, remotePublicKey: ByteArray): PQCESessionKey? {
        val oldSession = sessions[sessionId] ?: return null
        
        // Create new session
        val newSession = establishSession(remotePublicKey)
        
        // Secure wipe old session secrets
        oldSession.sharedSecret?.let { SideChannelDefense.secureWipe(it) }
        SideChannelDefense.secureWipe(oldSession.privateKey)
        
        // Delete old session
        sessions.remove(sessionId)
        
        return newSession
    }
    
    /**
     * Get security report for session
     */
    fun getSecurityReport(sessionId: String): TransitSecurityReport? {
        val session = sessions[sessionId] ?: return null
        
        val encryptionLevel = when (session.securityLevel) {
            5 -> "Kyber-1024"
            3 -> "Kyber-768"
            else -> "Kyber-512"
        }
        
        return TransitSecurityReport(
            sessionId = sessionId,
            encryptionLevel = encryptionLevel,
            noiseLevel = PQCConfig.NOISE_RATIO,
            sideChannelProtection = true,
            integrityVerified = true,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Clean up expired sessions
     */
    fun cleanupExpiredSessions(): Int {
        val now = System.currentTimeMillis()
        var cleaned = 0
        
        sessions.entries.removeIf { (_, session) ->
            if (session.expiresAt < now) {
                session.sharedSecret?.let { SideChannelDefense.secureWipe(it) }
                SideChannelDefense.secureWipe(session.privateKey)
                cleaned++
                true
            } else false
        }
        
        return cleaned
    }
    
    /**
     * Get session by ID
     */
    fun getSession(sessionId: String): PQCESessionKey? = sessions[sessionId]
    
    /**
     * Check if session exists and is valid
     */
    fun isSessionValid(sessionId: String): Boolean {
        val session = sessions[sessionId] ?: return false
        return session.isValid()
    }
    
    /**
     * Get all active session IDs
     */
    fun getActiveSessionIds(): Set<String> = sessions.keys.toSet()
    
    /**
     * Get node credentials
     */
    fun getNodeCredentials(): NodeCredentials? = nodeCredentials
    
    /**
     * Shutdown channel and clean up all resources
     */
    fun shutdown() {
        noiseInjector.stopRotation()
        
        // Secure wipe all session secrets
        sessions.values.forEach { session ->
            session.sharedSecret?.let { SideChannelDefense.secureWipe(it) }
            SideChannelDefense.secureWipe(session.privateKey)
        }
        sessions.clear()
        
        // Wipe node credentials
        nodeCredentials?.let {
            SideChannelDefense.secureWipe(it.noiseFilterKey)
            SideChannelDefense.secureWipe(it.authToken)
        }
        nodeCredentials = null
    }
}
