package com.miwealth.sovereignvantage.core.security.pqc

/**
 * CryptoProvider - Wrapper for cryptographic sign/verify operations.
 * Used by DHT and gamification systems for proof generation.
 */
interface CryptoProvider {
    fun sign(data: ByteArray): ByteArray
    fun verify(data: ByteArray, signature: ByteArray, userId: String): Boolean
}

/**
 * Default no-op implementation for systems that don't require real crypto signing.
 */
class NoOpCryptoProvider : CryptoProvider {
    override fun sign(data: ByteArray): ByteArray = data
    override fun verify(data: ByteArray, signature: ByteArray, userId: String): Boolean = true
}
