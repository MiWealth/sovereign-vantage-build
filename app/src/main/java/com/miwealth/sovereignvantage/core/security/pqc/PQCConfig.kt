package com.miwealth.sovereignvantage.core.security.pqc

/**
 * PQCE (Post-Quantum Computing Encryption) Configuration
 * 
 * Configuration constants for post-quantum cryptography operations.
 * Based on NIST standards: ML-KEM (Kyber), ML-DSA (Dilithium)
 * 
 * @author MiWealth Development Team
 * @version 1.0
 * @since 2026-01-19
 */
object PQCConfig {
    
    // ========================================================================
    // KYBER SECURITY LEVELS (ML-KEM)
    // ========================================================================
    
    /**
     * Kyber-512: NIST Security Level 1 (equivalent to AES-128)
     * Suitable for light encryption, lower resource environments
     */
    val KYBER_512 = KyberLevel(
        name = "Kyber-512",
        securityLevel = 1,
        publicKeySize = 800,
        privateKeySize = 1632,
        ciphertextSize = 768,
        sharedSecretSize = 32
    )
    
    /**
     * Kyber-768: NIST Security Level 3 (equivalent to AES-192)
     * Balanced security and performance
     */
    val KYBER_768 = KyberLevel(
        name = "Kyber-768",
        securityLevel = 3,
        publicKeySize = 1184,
        privateKeySize = 2400,
        ciphertextSize = 1088,
        sharedSecretSize = 32
    )
    
    /**
     * Kyber-1024: NIST Security Level 5 (equivalent to AES-256)
     * Maximum security - DEFAULT for Sovereign Vantage
     */
    val KYBER_1024 = KyberLevel(
        name = "Kyber-1024",
        securityLevel = 5,
        publicKeySize = 1568,
        privateKeySize = 3168,
        ciphertextSize = 1568,
        sharedSecretSize = 32
    )
    
    // ========================================================================
    // DILITHIUM SECURITY LEVELS (ML-DSA)
    // ========================================================================
    
    /**
     * Dilithium-2: NIST Security Level 2
     * Light signatures for high-frequency operations
     */
    val DILITHIUM_2 = DilithiumLevel(
        name = "Dilithium2",
        securityLevel = 2,
        publicKeySize = 1312,
        privateKeySize = 2528,
        signatureSize = 2420
    )
    
    /**
     * Dilithium-3: NIST Security Level 3
     * DEFAULT for Sovereign Vantage - balanced security
     */
    val DILITHIUM_3 = DilithiumLevel(
        name = "Dilithium3",
        securityLevel = 3,
        publicKeySize = 1952,
        privateKeySize = 4000,
        signatureSize = 3293
    )
    
    /**
     * Dilithium-5: NIST Security Level 5
     * Maximum security signatures
     */
    val DILITHIUM_5 = DilithiumLevel(
        name = "Dilithium5",
        securityLevel = 5,
        publicKeySize = 2592,
        privateKeySize = 4864,
        signatureSize = 4595
    )
    
    // ========================================================================
    // NOISE INJECTION CONFIGURATION
    // ========================================================================
    
    /** Rotate noise pattern every 30 seconds */
    const val NOISE_ROTATION_INTERVAL_MS: Long = 30_000L
    
    /** Entropy bits for noise generation */
    const val NOISE_ENTROPY_BITS: Int = 256
    
    /** Noise to signal ratio (15% noise) */
    const val NOISE_RATIO: Double = 0.15
    
    /** Polynomial degree for lattice operations */
    const val POLYNOMIAL_DEGREE: Int = 256
    
    /** Kyber's modulus q */
    const val KYBER_Q: Int = 3329
    
    // ========================================================================
    // SESSION CONFIGURATION
    // ========================================================================
    
    /** Rotate session keys every 5 minutes */
    const val SESSION_KEY_ROTATION_MS: Long = 300_000L
    
    /** Maximum session duration: 1 hour */
    const val MAX_SESSION_DURATION_MS: Long = 3_600_000L
    
    /** Session cleanup interval */
    const val SESSION_CLEANUP_INTERVAL_MS: Long = 60_000L
    
    // ========================================================================
    // ENCRYPTION CONFIGURATION
    // ========================================================================
    
    /** AES key size in bits */
    const val AES_KEY_SIZE_BITS: Int = 256
    
    /** AES-GCM nonce size in bytes */
    const val AES_GCM_NONCE_SIZE: Int = 12
    
    /** AES-GCM tag size in bytes */
    const val AES_GCM_TAG_SIZE: Int = 16
    
    /** Hash algorithm for key derivation */
    const val HASH_ALGORITHM: String = "SHA3-256"
    
    /** Extended hash for signatures */
    const val HASH_ALGORITHM_512: String = "SHA3-512"
    
    // ========================================================================
    // SIDE-CHANNEL DEFENSE
    // ========================================================================
    
    /** Minimum random delay for timing attack prevention (ms) */
    const val MIN_RANDOM_DELAY_MS: Int = 1
    
    /** Maximum random delay for timing attack prevention (ms) */
    const val MAX_RANDOM_DELAY_MS: Int = 10
    
    /** Number of passes for secure memory wipe */
    const val SECURE_WIPE_PASSES: Int = 3
    
    /** Dummy operations count for power analysis defense */
    const val DUMMY_OPERATIONS_COUNT: Int = 100
    
    // ========================================================================
    // DEFAULT SELECTIONS
    // ========================================================================
    
    /** Default Kyber level for key encapsulation */
    val DEFAULT_KYBER_LEVEL: KyberLevel = KYBER_1024
    
    /** Default Dilithium level for signatures */
    val DEFAULT_DILITHIUM_LEVEL: DilithiumLevel = DILITHIUM_3
}

/**
 * Kyber security level configuration
 */
data class KyberLevel(
    val name: String,
    val securityLevel: Int,
    val publicKeySize: Int,
    val privateKeySize: Int,
    val ciphertextSize: Int,
    val sharedSecretSize: Int
) {
    companion object {
        fun fromSecurityLevel(level: Int): KyberLevel = when (level) {
            1 -> PQCConfig.KYBER_512
            3 -> PQCConfig.KYBER_768
            5 -> PQCConfig.KYBER_1024
            else -> PQCConfig.KYBER_1024 // Default to maximum security
        }
    }
}

/**
 * Dilithium security level configuration
 */
data class DilithiumLevel(
    val name: String,
    val securityLevel: Int,
    val publicKeySize: Int,
    val privateKeySize: Int,
    val signatureSize: Int
) {
    companion object {
        fun fromSecurityLevel(level: Int): DilithiumLevel = when (level) {
            2 -> PQCConfig.DILITHIUM_2
            3 -> PQCConfig.DILITHIUM_3
            5 -> PQCConfig.DILITHIUM_5
            else -> PQCConfig.DILITHIUM_3 // Default to Level 3
        }
    }
}
