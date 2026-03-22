package com.miwealth.sovereignvantage.core.security.mpc

import com.miwealth.sovereignvantage.core.security.pqc.PQCEngine
import com.miwealth.sovereignvantage.core.security.pqc.EncryptedPackage

/**
 * Post-Quantum Cryptography (PQC) Wrapper
 * 
 * Backward-compatible wrapper that maintains the original API
 * while delegating to the full PQC implementation.
 * 
 * Uses:
 * - Kyber-1024 for Key Encapsulation Mechanism (KEM)
 * - Dilithium-3 for Digital Signatures
 * - AES-256-GCM for symmetric encryption
 * 
 * @see com.miwealth.sovereignvantage.core.security.pqc.PQCEngine
 * 
 * @author MiWealth Development Team
 * @version 2.0
 * @since 2026-01-19
 */
class PostQuantumCrypto {
    
    private val engine = PQCEngine(securityLevel = 5) // Maximum security
    
    // Cache for key pairs (generate once, reuse)
    private var cachedKeyPair: com.miwealth.sovereignvantage.core.security.pqc.KyberKeyPair? = null
    
    /**
     * Generate a new Kyber key pair for encryption
     * 
     * @return Pair of (publicKey, privateKey)
     */
    fun generateKeyPair(): Pair<ByteArray, ByteArray> {
        val keyPair = engine.generateEncryptionKeyPair()
        cachedKeyPair = keyPair
        return Pair(keyPair.publicKey, keyPair.privateKey)
    }
    
    /**
     * Encrypts data using Kyber-1024 public key.
     * 
     * Full implementation using:
     * 1. Kyber key encapsulation to establish shared secret
     * 2. AES-256-GCM for symmetric encryption
     * 
     * @param data The plaintext data.
     * @param publicKey The recipient's PQC public key.
     * @return Encrypted ciphertext (serialized package).
     */
    fun encrypt(data: ByteArray, publicKey: ByteArray): ByteArray {
        val encrypted = engine.encrypt(data, publicKey)
        return encrypted.serialize()
    }
    
    /**
     * Decrypts data using Kyber-1024 private key.
     * 
     * @param ciphertext The encrypted data (serialized package).
     * @param privateKey The recipient's PQC private key.
     * @return Decrypted plaintext.
     */
    fun decrypt(ciphertext: ByteArray, privateKey: ByteArray): ByteArray {
        val encrypted = EncryptedPackage.deserialize(ciphertext)
        return engine.decrypt(encrypted, privateKey)
    }
    
    /**
     * Sign data using Dilithium-3
     * 
     * @param data Data to sign
     * @param privateKey Signing private key (Dilithium)
     * @return Signature bytes
     */
    fun sign(data: ByteArray, privateKey: ByteArray): ByteArray {
        return engine.sign(data, privateKey)
    }
    
    /**
     * Verify a Dilithium signature
     * 
     * @param data Original data
     * @param signature Signature to verify
     * @param publicKey Signing public key (Dilithium)
     * @return true if valid, false otherwise
     */
    fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return engine.verify(data, signature, publicKey)
    }
    
    /**
     * Generate Dilithium signing key pair
     * 
     * @return Pair of (publicKey, privateKey)
     */
    fun generateSigningKeyPair(): Pair<ByteArray, ByteArray> {
        val keyPair = engine.generateSigningKeyPair()
        return Pair(keyPair.publicKey, keyPair.privateKey)
    }
    
    /**
     * Get the security level description
     */
    fun getSecurityLevel(): String = engine.getSecurityLevelDescription()
    
    /**
     * Encrypt data for MPC shard distribution
     * 
     * Encrypts a key shard with the recipient's public key
     * for secure distribution across the MPC network.
     */
    fun encryptShard(shard: ByteArray, recipientPublicKey: ByteArray): ByteArray {
        return encrypt(shard, recipientPublicKey)
    }
    
    /**
     * Decrypt a received MPC shard
     */
    fun decryptShard(encryptedShard: ByteArray, privateKey: ByteArray): ByteArray {
        return decrypt(encryptedShard, privateKey)
    }
    
    /**
     * Clean up resources
     */
    fun shutdown() {
        engine.shutdown()
        cachedKeyPair = null
    }
    
    companion object {
        /** Singleton instance for app-wide use */
        @Volatile
        private var instance: PostQuantumCrypto? = null
        
        fun getInstance(): PostQuantumCrypto {
            return instance ?: synchronized(this) {
                instance ?: PostQuantumCrypto().also { instance = it }
            }
        }
    }
}
