package com.miwealth.sovereignvantage.core.security.pqc

import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.delay

/**
 * Side-Channel Attack Defense Utilities
 * 
 * Provides protection against:
 * - Timing attacks (constant-time operations)
 * - Power analysis attacks (dummy operations)
 * - Cache timing attacks (operation shuffling)
 * - Memory extraction (secure wipe)
 * 
 * @author MiWealth Development Team
 * @version 1.0
 * @since 2026-01-19
 */
object SideChannelDefense {
    
    private val secureRandom = SecureRandom()
    
    /**
     * Constant-time comparison to prevent timing attacks
     * 
     * Compares two byte arrays in constant time regardless of
     * where the first difference occurs.
     */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
    
    /**
     * Add random delay to prevent timing analysis
     * 
     * @param minMs Minimum delay in milliseconds
     * @param maxMs Maximum delay in milliseconds
     */
    suspend fun addRandomDelay(
        minMs: Int = PQCConfig.MIN_RANDOM_DELAY_MS,
        maxMs: Int = PQCConfig.MAX_RANDOM_DELAY_MS
    ) {
        val delayMs = minMs + secureRandom.nextInt(maxMs - minMs + 1)
        delay(delayMs.toLong())
    }
    
    /**
     * Add random delay (blocking version for non-coroutine contexts)
     */
    fun addRandomDelayBlocking(
        minMs: Int = PQCConfig.MIN_RANDOM_DELAY_MS,
        maxMs: Int = PQCConfig.MAX_RANDOM_DELAY_MS
    ) {
        val delayMs = minMs + secureRandom.nextInt(maxMs - minMs + 1)
        Thread.sleep(delayMs.toLong())
    }
    
    /**
     * Mask sensitive data in memory using XOR
     * 
     * Returns masked data and the mask needed to unmask it.
     * The original data should be wiped after masking.
     */
    fun maskData(data: ByteArray): MaskedData {
        val mask = ByteArray(data.size).also { secureRandom.nextBytes(it) }
        val masked = ByteArray(data.size) { i -> (data[i].toInt() xor mask[i].toInt()).toByte() }
        return MaskedData(masked, mask)
    }
    
    /**
     * Unmask previously masked data
     */
    fun unmaskData(masked: ByteArray, mask: ByteArray): ByteArray {
        require(masked.size == mask.size) { "Masked data and mask must be same size" }
        return ByteArray(masked.size) { i -> (masked[i].toInt() xor mask[i].toInt()).toByte() }
    }
    
    /**
     * Securely wipe memory by overwriting with random data multiple times
     * 
     * Uses multiple passes to defeat potential memory recovery techniques.
     */
    fun secureWipe(buffer: ByteArray) {
        // Multiple passes with random data
        repeat(PQCConfig.SECURE_WIPE_PASSES) {
            secureRandom.nextBytes(buffer)
        }
        // Final zero fill
        buffer.fill(0)
    }
    
    /**
     * Add dummy operations to mask power consumption patterns
     * 
     * Performs meaningless cryptographic operations to make power
     * analysis attacks more difficult.
     */
    fun addDummyOperations(iterations: Int = PQCConfig.DUMMY_OPERATIONS_COUNT) {
        val dummy = ByteArray(32)
        val digest = MessageDigest.getInstance("SHA-256")
        
        repeat(iterations) {
            digest.reset()
            digest.update(dummy)
            digest.digest(dummy)
        }
        
        // Wipe dummy buffer
        secureWipe(dummy)
    }
    
    /**
     * Shuffle and execute operations in random order
     * 
     * Prevents cache timing attacks by randomizing the order
     * of operations.
     */
    fun <T> shuffleOperations(operations: List<() -> T>): List<T> {
        val shuffled = operations.toMutableList()
        
        // Fisher-Yates shuffle
        for (i in shuffled.lastIndex downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val temp = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = temp
        }
        
        return shuffled.map { it() }
    }
    
    /**
     * Execute operation with timing normalization
     * 
     * Ensures operation takes at least minDurationMs to complete,
     * adding delay if it completes faster.
     */
    fun <T> executeWithNormalizedTiming(minDurationMs: Long, operation: () -> T): T {
        val startTime = System.nanoTime()
        val result = operation()
        val elapsed = (System.nanoTime() - startTime) / 1_000_000
        
        if (elapsed < minDurationMs) {
            Thread.sleep(minDurationMs - elapsed)
        }
        
        return result
    }
    
    /**
     * Create a blinded copy of data for processing
     * 
     * Returns blinded data that can be processed, then unblinded
     * to get the actual result.
     */
    fun blindData(data: ByteArray): BlindedData {
        val blindingFactor = ByteArray(data.size).also { secureRandom.nextBytes(it) }
        val blinded = ByteArray(data.size) { i -> 
            (data[i].toInt() + blindingFactor[i].toInt()).toByte() 
        }
        return BlindedData(blinded, blindingFactor)
    }
    
    /**
     * Remove blinding from processed data
     */
    fun unblindData(blinded: ByteArray, blindingFactor: ByteArray): ByteArray {
        require(blinded.size == blindingFactor.size) { "Blinded data and factor must be same size" }
        return ByteArray(blinded.size) { i ->
            (blinded[i].toInt() - blindingFactor[i].toInt()).toByte()
        }
    }
    
    /**
     * Constant-time conditional select
     * 
     * Returns a if condition is true, b otherwise, in constant time.
     */
    fun constantTimeSelect(condition: Boolean, a: ByteArray, b: ByteArray): ByteArray {
        require(a.size == b.size) { "Arrays must be same size" }
        
        // Convert boolean to mask: true -> 0xFF, false -> 0x00
        val mask = if (condition) 0xFF else 0x00
        val inverseMask = mask xor 0xFF
        
        return ByteArray(a.size) { i ->
            ((a[i].toInt() and mask) or (b[i].toInt() and inverseMask)).toByte()
        }
    }
}

/**
 * Result of masking operation
 */
data class MaskedData(
    val masked: ByteArray,
    val mask: ByteArray
) {
    fun unmask(): ByteArray = SideChannelDefense.unmaskData(masked, mask)
    
    fun wipe() {
        SideChannelDefense.secureWipe(masked)
        SideChannelDefense.secureWipe(mask)
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MaskedData) return false
        return masked.contentEquals(other.masked) && mask.contentEquals(other.mask)
    }
    
    override fun hashCode(): Int = masked.contentHashCode()
}

/**
 * Result of blinding operation
 */
data class BlindedData(
    val blinded: ByteArray,
    val blindingFactor: ByteArray
) {
    fun unblind(): ByteArray = SideChannelDefense.unblindData(blinded, blindingFactor)
    
    fun wipe() {
        SideChannelDefense.secureWipe(blinded)
        SideChannelDefense.secureWipe(blindingFactor)
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlindedData) return false
        return blinded.contentEquals(other.blinded)
    }
    
    override fun hashCode(): Int = blinded.contentHashCode()
}
