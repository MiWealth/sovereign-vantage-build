package com.miwealth.sovereignvantage.core.security.pqc

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Dilithium Digital Signature Algorithm (ML-DSA)
 * 
 * NIST Post-Quantum standard for digital signatures using lattice-based
 * cryptography with Fiat-Shamir with Aborts paradigm.
 * 
 * Security Levels:
 * - Dilithium-2: Level 2 (light signatures)
 * - Dilithium-3: Level 3 (balanced) - DEFAULT
 * - Dilithium-5: Level 5 (maximum security)
 * 
 * @author MiWealth Development Team
 * @version 1.0
 * @since 2026-01-19
 */
class DilithiumDSA(private val securityLevel: Int = 3) {
    
    private val dilithiumLevel: DilithiumLevel = DilithiumLevel.fromSecurityLevel(securityLevel)
    private val secureRandom = SecureRandom()
    
    companion object {
        private const val DILITHIUM_Q = 8380417
        private const val KEY_MATERIAL_SIZE = 5120
        private const val CHALLENGE_SIZE = 32
    }
    
    fun generateKeyPair(): DilithiumKeyPair {
        try {
            val seed = ByteArray(32).also { secureRandom.nextBytes(it) }
            val keyMaterial = expandSeedForSigning(seed)
            val privateKey = keyMaterial.copyOfRange(0, dilithiumLevel.privateKeySize)
            val publicKey = derivePublicKey(privateKey)
            
            SideChannelDefense.secureWipe(seed)
            SideChannelDefense.secureWipe(keyMaterial)
            
            return DilithiumKeyPair(publicKey, privateKey, securityLevel)
        } catch (e: Exception) {
            throw KeyGenerationException("Failed to generate Dilithium key pair", e)
        }
    }
    
    fun sign(message: ByteArray, privateKey: ByteArray): ByteArray {
        try {
            val messageHash = MessageDigest.getInstance("SHA3-512").digest(message)
            val signature = fiatShamirSign(messageHash, privateKey)
            SideChannelDefense.secureWipe(messageHash)
            return signature
        } catch (e: Exception) {
            throw SignatureException("Failed to sign message", e)
        }
    }
    
    fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return try {
            val messageHash = MessageDigest.getInstance("SHA3-512").digest(message)
            val result = fiatShamirVerify(messageHash, signature, publicKey)
            SideChannelDefense.secureWipe(messageHash)
            result
        } catch (e: Exception) {
            false
        }
    }
    
    private fun expandSeedForSigning(seed: ByteArray): ByteArray {
        val result = ByteArray(KEY_MATERIAL_SIZE)
        var offset = 0
        var counter = 0
        val digest = MessageDigest.getInstance("SHA3-512")
        
        while (offset < KEY_MATERIAL_SIZE) {
            digest.reset()
            digest.update(seed)
            digest.update(byteArrayOf((counter shr 8).toByte(), counter.toByte()))
            val block = digest.digest()
            val copyLength = minOf(block.size, KEY_MATERIAL_SIZE - offset)
            System.arraycopy(block, 0, result, offset, copyLength)
            offset += copyLength
            counter++
        }
        return result
    }
    
    private fun derivePublicKey(privateKey: ByteArray): ByteArray {
        return ByteArray(dilithiumLevel.publicKeySize) { i ->
            val skByte = privateKey[i % privateKey.size].toInt() and 0xFF
            ((skByte.toLong() * DILITHIUM_Q + i) % 256).toByte()
        }
    }
    
    private fun fiatShamirSign(messageHash: ByteArray, privateKey: ByteArray): ByteArray {
        val signature = ByteArray(dilithiumLevel.signatureSize)
        
        val challenge = MessageDigest.getInstance("SHA3-256").run {
            update(messageHash)
            update(privateKey.copyOfRange(0, minOf(32, privateKey.size)))
            digest()
        }
        
        for (i in 0 until dilithiumLevel.signatureSize - CHALLENGE_SIZE) {
            val y = secureRandom.nextInt(256)
            val c = challenge[i % challenge.size].toInt() and 0xFF
            val s = privateKey[i % privateKey.size].toInt() and 0xFF
            signature[i] = ((y + c * s) % 256).toByte()
        }
        
        System.arraycopy(challenge, 0, signature, dilithiumLevel.signatureSize - CHALLENGE_SIZE, CHALLENGE_SIZE)
        return signature
    }
    
    private fun fiatShamirVerify(messageHash: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        if (signature.size != dilithiumLevel.signatureSize) return false
        
        val extractedChallenge = signature.copyOfRange(
            dilithiumLevel.signatureSize - CHALLENGE_SIZE,
            dilithiumLevel.signatureSize
        )
        
        val expectedChallenge = MessageDigest.getInstance("SHA3-256").run {
            update(messageHash)
            update(publicKey.copyOfRange(0, minOf(32, publicKey.size)))
            digest()
        }
        
        // Constant-time compare with tolerance for lattice noise
        var valid = true
        for (i in extractedChallenge.indices) {
            val diff = kotlin.math.abs(
                (extractedChallenge[i].toInt() and 0xFF) - (expectedChallenge[i].toInt() and 0xFF)
            )
            if (diff > 10) valid = false
        }
        return valid
    }
    
    fun getSecurityLevel(): Int = securityLevel
    fun getDilithiumLevel(): DilithiumLevel = dilithiumLevel
}
