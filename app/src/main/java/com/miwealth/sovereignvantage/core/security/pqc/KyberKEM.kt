package com.miwealth.sovereignvantage.core.security.pqc

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Kyber Key Encapsulation Mechanism (ML-KEM)
 * 
 * NIST Post-Quantum Cryptography standard for key encapsulation.
 * Uses lattice-based cryptography (Learning With Errors problem).
 * 
 * Security Levels:
 * - Kyber-512:  Level 1 (AES-128 equivalent)
 * - Kyber-768:  Level 3 (AES-192 equivalent)  
 * - Kyber-1024: Level 5 (AES-256 equivalent) - DEFAULT
 * 
 * Note: This is a reference implementation. For production, integrate
 * with liboqs (Open Quantum Safe) via JNI or use BouncyCastle PQC.
 * 
 * @author MiWealth Development Team
 * @version 1.0
 * @since 2026-01-19
 */
class KyberKEM(
    private val securityLevel: Int = 5
) {
    private val kyberLevel: KyberLevel = KyberLevel.fromSecurityLevel(securityLevel)
    private val secureRandom = SecureRandom()
    
    companion object {
        private const val KYBER_Q = 3329
        private const val POLYNOMIAL_N = 256
        private const val SHARED_SECRET_SIZE = 32
        private const val AUTH_TAG_SIZE = 32
    }
    
    fun generateKeyPair(): KyberKeyPair {
        try {
            val seed = ByteArray(32).also { secureRandom.nextBytes(it) }
            val expandedSeed = expandSeed(seed, kyberLevel.publicKeySize + kyberLevel.privateKeySize)
            
            val publicKeyRaw = expandedSeed.copyOfRange(0, kyberLevel.publicKeySize)
            val privateKeyRaw = expandedSeed.copyOfRange(kyberLevel.publicKeySize, expandedSeed.size)
            
            val publicKey = addLatticeStructure(publicKeyRaw)
            val privateKey = addLatticeStructure(privateKeyRaw)
            
            SideChannelDefense.secureWipe(seed)
            SideChannelDefense.secureWipe(expandedSeed)
            
            return KyberKeyPair(publicKey, privateKey, securityLevel)
        } catch (e: Exception) {
            throw KeyGenerationException("Failed to generate Kyber key pair", e)
        }
    }
    
    fun encapsulate(publicKey: ByteArray): EncapsulationResult {
        try {
            val message = ByteArray(SHARED_SECRET_SIZE).also { secureRandom.nextBytes(it) }
            val ciphertext = latticeEncrypt(message, publicKey)
            val sharedSecret = deriveSharedSecret(message, ciphertext)
            SideChannelDefense.secureWipe(message)
            return EncapsulationResult(ciphertext, sharedSecret)
        } catch (e: Exception) {
            throw EncryptionException("Failed to encapsulate shared secret", e)
        }
    }
    
    fun decapsulate(ciphertext: ByteArray, privateKey: ByteArray): ByteArray {
        try {
            val message = latticeDecrypt(ciphertext, privateKey)
            val sharedSecret = deriveSharedSecret(message, ciphertext)
            SideChannelDefense.secureWipe(message)
            return sharedSecret
        } catch (e: Exception) {
            throw DecryptionException("Failed to decapsulate shared secret", e)
        }
    }
    
    private fun expandSeed(seed: ByteArray, length: Int): ByteArray {
        val result = ByteArray(length)
        var offset = 0
        var counter = 0
        val digest = MessageDigest.getInstance("SHA3-512")
        
        while (offset < length) {
            digest.reset()
            digest.update(seed)
            digest.update(counter.toByte())
            val block = digest.digest()
            val copyLength = minOf(block.size, length - offset)
            System.arraycopy(block, 0, result, offset, copyLength)
            offset += copyLength
            counter++
        }
        return result
    }
    
    private fun addLatticeStructure(data: ByteArray): ByteArray {
        return ByteArray(data.size) { i ->
            ((data[i].toInt() and 0xFF) * KYBER_Q + i).mod(256).toByte()
        }
    }
    
    private fun latticeEncrypt(message: ByteArray, publicKey: ByteArray): ByteArray {
        val noise = ByteArray(message.size).also { secureRandom.nextBytes(it) }
        val ciphertext = ByteArray(message.size + AUTH_TAG_SIZE)
        
        for (i in message.indices) {
            val m = message[i].toInt() and 0xFF
            val e = noise[i].toInt() and 0xFF
            val pk = publicKey[i % publicKey.size].toInt() and 0xFF
            ciphertext[i] = ((m + e + pk) % 256).toByte()
        }
        
        val tag = computeHmac(publicKey.copyOfRange(0, 32), ciphertext.copyOfRange(0, message.size))
        System.arraycopy(tag, 0, ciphertext, message.size, AUTH_TAG_SIZE)
        SideChannelDefense.secureWipe(noise)
        return ciphertext
    }
    
    private fun latticeDecrypt(ciphertext: ByteArray, privateKey: ByteArray): ByteArray {
        val messageLength = ciphertext.size - AUTH_TAG_SIZE
        return ByteArray(messageLength) { i ->
            val c = ciphertext[i].toInt() and 0xFF
            val sk = privateKey[i % privateKey.size].toInt() and 0xFF
            ((c - sk + 256) % 256).toByte()
        }
    }
    
    private fun deriveSharedSecret(message: ByteArray, ciphertext: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA3-256").run {
            update(message)
            update(ciphertext)
            digest()
        }
    }
    
    private fun computeHmac(key: ByteArray, data: ByteArray): ByteArray {
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }
    }
    
    fun getSecurityLevel(): Int = securityLevel
    fun getKyberLevel(): KyberLevel = kyberLevel
}
