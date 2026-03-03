package com.miwealth.sovereignvantage.core.security.pqc

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow

/**
 * Mathematical Noise Injector for Side-Channel Attack Defense
 * 
 * Injects deterministic mathematical noise into encrypted data to prevent
 * timing attacks, power analysis, and traffic analysis attacks.
 * 
 * @author MiWealth Development Team
 * @version 1.0
 * @since 2026-01-19
 */
class NoiseInjector {
    
    private var currentPattern: NoisePattern? = null
    private val patternHistory = ConcurrentHashMap<String, NoisePattern>()
    private var rotationTimer: Timer? = null
    private val secureRandom = SecureRandom()
    
    companion object {
        private const val POLYNOMIAL_DEGREE = 16
    }
    
    init {
        rotatePattern()
        startRotationTimer()
    }
    
    private fun generatePattern(): NoisePattern {
        val seed = ByteArray(32).also { secureRandom.nextBytes(it) }
        val polynomial = generatePolynomial(POLYNOMIAL_DEGREE)
        val now = System.currentTimeMillis()
        
        return NoisePattern(
            id = UUID.randomUUID().toString(),
            seed = seed,
            polynomial = polynomial,
            rotationIndex = 0,
            createdAt = now,
            expiresAt = now + (PQCConfig.NOISE_ROTATION_INTERVAL_MS * 2)
        )
    }
    
    private fun generatePolynomial(degree: Int): IntArray {
        val seedBytes = ByteArray(degree * 4).also { secureRandom.nextBytes(it) }
        return IntArray(degree) { i ->
            val offset = i * 4
            ((seedBytes[offset].toInt() and 0xFF) shl 24) or
            ((seedBytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((seedBytes[offset + 2].toInt() and 0xFF) shl 8) or
            (seedBytes[offset + 3].toInt() and 0xFF)
        }
    }
    
    @Synchronized
    fun rotatePattern(): NoisePattern {
        currentPattern?.let { oldPattern ->
            patternHistory[oldPattern.id] = oldPattern
            val now = System.currentTimeMillis()
            patternHistory.entries.removeIf { it.value.expiresAt < now }
        }
        
        val newPattern = generatePattern()
        currentPattern = newPattern
        return newPattern
    }
    
    private fun startRotationTimer() {
        rotationTimer = Timer("NoisePatternRotation", true).apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() { rotatePattern() }
            }, PQCConfig.NOISE_ROTATION_INTERVAL_MS, PQCConfig.NOISE_ROTATION_INTERVAL_MS)
        }
    }
    
    fun stopRotation() {
        rotationTimer?.cancel()
        rotationTimer = null
    }
    
    fun injectNoise(data: ByteArray): NoiseInjectionResult {
        val pattern = currentPattern ?: rotatePattern()
        val noiseLength = ceil(data.size * PQCConfig.NOISE_RATIO).toInt()
        val noise = generateNoiseFromPolynomial(noiseLength, pattern)
        val noiseMap = generateNoiseMap(data.size, noiseLength, pattern.seed)
        val noisyData = interleaveWithNoise(data, noise, noiseMap)
        
        return NoiseInjectionResult(noisyData, noiseMap, pattern.id)
    }
    
    fun removeNoise(
        noisyData: ByteArray,
        noiseMap: ByteArray,
        patternId: String,
        nodeCredentials: NodeCredentials
    ): ByteArray? {
        if (!verifyNodeAuthorization(nodeCredentials)) return null
        
        val pattern = if (patternId == currentPattern?.id) currentPattern
                      else patternHistory[patternId]
        
        if (pattern == null || pattern.isExpired()) return null
        
        return deinterleaveNoise(noisyData, noiseMap)
    }
    
    private fun generateNoiseFromPolynomial(length: Int, pattern: NoisePattern): ByteArray {
        val noise = ByteArray(length)
        val polynomial = pattern.polynomial
        val seed = pattern.seed
        val rotationIndex = pattern.rotationIndex
        
        for (i in 0 until length) {
            val x = (i + rotationIndex).toDouble() / length
            var value = 0.0
            for (j in polynomial.indices) {
                val coefficient = polynomial[j].toDouble() / Int.MAX_VALUE
                value += coefficient * x.pow(j.toDouble())
            }
            val seedByte = seed[i % seed.size].toInt() and 0xFF
            val finalValue = ((value * 127) + seedByte).toInt() % 256
            noise[i] = abs(finalValue).toByte()
        }
        return noise
    }
    
    private fun generateNoiseMap(dataLength: Int, noiseLength: Int, seed: ByteArray): ByteArray {
        val totalLength = dataLength + noiseLength
        val map = ByteArray(totalLength)
        val positions = mutableSetOf<Int>()
        var counter = 0
        val digest = MessageDigest.getInstance("SHA-256")
        
        while (positions.size < noiseLength) {
            digest.reset()
            digest.update(seed)
            digest.update(counter.toByte())
            val hash = digest.digest()
            
            val position = ((hash[0].toInt() and 0xFF) shl 24 or
                           (hash[1].toInt() and 0xFF) shl 16 or
                           (hash[2].toInt() and 0xFF) shl 8 or
                           (hash[3].toInt() and 0xFF)) and Int.MAX_VALUE
            val normalizedPosition = position % totalLength
            
            if (normalizedPosition !in positions) {
                positions.add(normalizedPosition)
                map[normalizedPosition] = 1
            }
            counter++
        }
        return map
    }
    
    private fun interleaveWithNoise(data: ByteArray, noise: ByteArray, noiseMap: ByteArray): ByteArray {
        val result = ByteArray(noiseMap.size)
        var dataIndex = 0
        var noiseIndex = 0
        
        for (i in noiseMap.indices) {
            result[i] = if (noiseMap[i] == 1.toByte()) noise[noiseIndex++]
                        else data[dataIndex++]
        }
        return result
    }
    
    private fun deinterleaveNoise(noisyData: ByteArray, noiseMap: ByteArray): ByteArray {
        val dataLength = noiseMap.count { it == 0.toByte() }
        val data = ByteArray(dataLength)
        var dataIndex = 0
        
        for (i in noiseMap.indices) {
            if (noiseMap[i] == 0.toByte()) data[dataIndex++] = noisyData[i]
        }
        return data
    }
    
    private fun verifyNodeAuthorization(credentials: NodeCredentials): Boolean {
        if (!credentials.hasPermission("noise_filter") && 
            !credentials.hasPermission(NodePermissions.DECRYPT)) return false
        
        val expectedToken = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(credentials.noiseFilterKey, "HmacSHA256"))
            update(credentials.nodeId.toByteArray(Charsets.UTF_8))
            update(credentials.publicKey)
            doFinal()
        }
        
        return SideChannelDefense.constantTimeEquals(credentials.authToken, expectedToken)
    }
    
    fun getCurrentPatternId(): String? = currentPattern?.id
    fun getCurrentPattern(): NoisePattern? = currentPattern
    fun isPatternValid(patternId: String): Boolean {
        if (patternId == currentPattern?.id) return true
        return patternHistory[patternId]?.isExpired() == false
    }
}

data class NoiseInjectionResult(
    val noisyData: ByteArray,
    val noiseMap: ByteArray,
    val patternId: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NoiseInjectionResult) return false
        return patternId == other.patternId
    }
    override fun hashCode(): Int = patternId.hashCode()
}
