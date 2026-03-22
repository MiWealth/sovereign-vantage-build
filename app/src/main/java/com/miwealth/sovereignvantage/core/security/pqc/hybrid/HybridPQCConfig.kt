package com.miwealth.sovereignvantage.core.security.pqc.hybrid

/**
 * HYBRID PQC CONFIGURATION
 * 
 * Central configuration for hybrid post-quantum cryptography.
 * Designed for easy algorithm upgrades as PQC standards evolve.
 * 
 * Current Implementation:
 * - KEMs: Kyber-512/768/1024 (ML-KEM FIPS 203)
 * - Signatures: Dilithium-2/3/5 (ML-DSA FIPS 204)
 * - Symmetric: AES-256-GCM
 * - Hash: SHA-3/SHAKE-256
 * 
 * Hybrid Approach:
 * - Classical + PQC in parallel for defense-in-depth
 * - Graceful degradation if PQC unavailable
 * - Upgrade path for future algorithms (e.g., BIKE, HQC, SPHINCS+)
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

/**
 * Supported Key Encapsulation Mechanisms
 */
enum class KEMAlgorithm(
    val id: String,
    val displayName: String,
    val nistLevel: Int,
    val publicKeySize: Int,
    val ciphertextSize: Int,
    val sharedSecretSize: Int,
    val isDefault: Boolean = false
) {
    KYBER_512("kyber512", "Kyber-512 (ML-KEM-512)", 1, 800, 768, 32),
    KYBER_768("kyber768", "Kyber-768 (ML-KEM-768)", 3, 1184, 1088, 32),
    KYBER_1024("kyber1024", "Kyber-1024 (ML-KEM-1024)", 5, 1568, 1568, 32, isDefault = true),
    
    // Future algorithms - placeholders for upgrade path
    // BIKE_L1("bike_l1", "BIKE Level 1", 1, 1541, 1573, 32),
    // HQC_128("hqc128", "HQC-128", 1, 2249, 4481, 64);
    ;
    
    companion object {
        fun getDefault(): KEMAlgorithm = entries.first { it.isDefault }
        fun fromId(id: String): KEMAlgorithm? = entries.find { it.id == id }
        fun forSecurityLevel(level: Int): KEMAlgorithm = when {
            level >= 5 -> KYBER_1024
            level >= 3 -> KYBER_768
            else -> KYBER_512
        }
    }
}

/**
 * Supported Digital Signature Algorithms
 */
enum class SignatureAlgorithm(
    val id: String,
    val displayName: String,
    val nistLevel: Int,
    val publicKeySize: Int,
    val signatureSize: Int,
    val isDefault: Boolean = false
) {
    DILITHIUM_2("dilithium2", "Dilithium-2 (ML-DSA-44)", 2, 1312, 2420),
    DILITHIUM_3("dilithium3", "Dilithium-3 (ML-DSA-65)", 3, 1952, 3293),
    DILITHIUM_5("dilithium5", "Dilithium-5 (ML-DSA-87)", 5, 2592, 4595, isDefault = true),
    
    // Future algorithms - placeholders for upgrade path
    // SPHINCS_SHA2_128F("sphincs_sha2_128f", "SPHINCS+-SHA2-128f", 1, 32, 17088),
    // SPHINCS_SHA2_256F("sphincs_sha2_256f", "SPHINCS+-SHA2-256f", 5, 64, 49856);
    ;
    
    companion object {
        fun getDefault(): SignatureAlgorithm = entries.first { it.isDefault }
        fun fromId(id: String): SignatureAlgorithm? = entries.find { it.id == id }
        fun forSecurityLevel(level: Int): SignatureAlgorithm = when {
            level >= 5 -> DILITHIUM_5
            level >= 3 -> DILITHIUM_3
            else -> DILITHIUM_2
        }
    }
}

/**
 * Supported Classical Algorithms (for hybrid mode)
 */
enum class ClassicalAlgorithm(
    val id: String,
    val displayName: String,
    val keySize: Int
) {
    ECDH_P256("ecdh_p256", "ECDH P-256", 256),
    ECDH_P384("ecdh_p384", "ECDH P-384", 384),
    X25519("x25519", "X25519", 256),
    RSA_4096("rsa_4096", "RSA-4096", 4096);
    
    companion object {
        fun getDefault(): ClassicalAlgorithm = X25519
    }
}

/**
 * Hybrid mode configuration
 */
enum class HybridMode {
    /** PQC only - maximum quantum resistance */
    PQC_ONLY,
    
    /** Classical only - fallback for compatibility */
    CLASSICAL_ONLY,
    
    /** Hybrid: PQC + Classical in parallel (recommended) */
    HYBRID_PARALLEL,
    
    /** Hybrid: PQC primary, classical backup */
    HYBRID_PQC_PRIMARY
}

/**
 * Security level presets
 */
enum class SecurityPreset(
    val kem: KEMAlgorithm,
    val signature: SignatureAlgorithm,
    val classical: ClassicalAlgorithm,
    val mode: HybridMode,
    val description: String
) {
    /** Maximum security - NIST Level 5 */
    MAXIMUM(
        KEMAlgorithm.KYBER_1024,
        SignatureAlgorithm.DILITHIUM_5,
        ClassicalAlgorithm.ECDH_P384,
        HybridMode.HYBRID_PARALLEL,
        "Maximum security (NIST Level 5) - Recommended for high-value transactions"
    ),
    
    /** High security - NIST Level 3 */
    HIGH(
        KEMAlgorithm.KYBER_768,
        SignatureAlgorithm.DILITHIUM_3,
        ClassicalAlgorithm.X25519,
        HybridMode.HYBRID_PARALLEL,
        "High security (NIST Level 3) - Good balance of security and performance"
    ),
    
    /** Standard security - NIST Level 1 */
    STANDARD(
        KEMAlgorithm.KYBER_512,
        SignatureAlgorithm.DILITHIUM_2,
        ClassicalAlgorithm.X25519,
        HybridMode.HYBRID_PQC_PRIMARY,
        "Standard security (NIST Level 1) - Best performance"
    ),
    
    /** Compatibility mode - classical with PQC backup */
    COMPATIBILITY(
        KEMAlgorithm.KYBER_768,
        SignatureAlgorithm.DILITHIUM_3,
        ClassicalAlgorithm.X25519,
        HybridMode.CLASSICAL_ONLY,
        "Compatibility mode - Classical crypto with PQC prepared"
    )
}

/**
 * Main configuration class for Hybrid PQC
 */
data class HybridPQCConfig(
    val kemAlgorithm: KEMAlgorithm = KEMAlgorithm.getDefault(),
    val signatureAlgorithm: SignatureAlgorithm = SignatureAlgorithm.getDefault(),
    val classicalAlgorithm: ClassicalAlgorithm = ClassicalAlgorithm.getDefault(),
    val hybridMode: HybridMode = HybridMode.HYBRID_PARALLEL,
    
    // Symmetric encryption settings
    val symmetricAlgorithm: String = "AES/GCM/NoPadding",
    val symmetricKeySize: Int = 256,
    val gcmTagSize: Int = 128,
    val gcmNonceSize: Int = 12,
    
    // Hash settings
    val hashAlgorithm: String = "SHA-256",
    val pqcHashAlgorithm: String = "SHAKE256",
    
    // Session settings
    val sessionTimeoutMs: Long = 3600_000,  // 1 hour
    val keyRotationIntervalMs: Long = 900_000,  // 15 minutes
    val maxSessionsPerEndpoint: Int = 5,
    
    // Audit settings
    val enableAuditSigning: Boolean = true,
    val auditRetentionDays: Int = 90,
    
    // Feature flags
    val enableHybridKEM: Boolean = true,
    val enableRequestSigning: Boolean = true,
    val enableResponseVerification: Boolean = true,
    val enableCredentialEncryption: Boolean = true
) {
    companion object {
        /** Default configuration - Maximum security */
        fun default(): HybridPQCConfig = fromPreset(SecurityPreset.MAXIMUM)
        
        /** Create from security preset */
        fun fromPreset(preset: SecurityPreset): HybridPQCConfig = HybridPQCConfig(
            kemAlgorithm = preset.kem,
            signatureAlgorithm = preset.signature,
            classicalAlgorithm = preset.classical,
            hybridMode = preset.mode
        )
        
        /** Create for specific NIST security level */
        fun forSecurityLevel(level: Int): HybridPQCConfig = HybridPQCConfig(
            kemAlgorithm = KEMAlgorithm.forSecurityLevel(level),
            signatureAlgorithm = SignatureAlgorithm.forSecurityLevel(level)
        )
        
        /** Exchange-specific configuration */
        fun forExchange(exchangeId: String): HybridPQCConfig {
            // Can customize per-exchange if needed
            return when (exchangeId.lowercase()) {
                "kraken", "coinbase" -> fromPreset(SecurityPreset.MAXIMUM)
                "binance" -> fromPreset(SecurityPreset.HIGH)
                else -> default()
            }
        }
    }
    
    /** Get NIST security level based on configuration */
    fun getNISTLevel(): Int = minOf(kemAlgorithm.nistLevel, signatureAlgorithm.nistLevel)
    
    /** Get human-readable security description */
    fun getSecurityDescription(): String {
        val level = getNISTLevel()
        val equivalent = when {
            level >= 5 -> "AES-256"
            level >= 3 -> "AES-192"
            else -> "AES-128"
        }
        return "NIST Level $level ($equivalent equivalent) - ${hybridMode.name}"
    }
    
    /** Validate configuration consistency */
    fun validate(): ConfigValidationResult {
        val issues = mutableListOf<String>()
        
        if (hybridMode == HybridMode.PQC_ONLY && !enableHybridKEM) {
            issues.add("PQC_ONLY mode requires enableHybridKEM=true")
        }
        
        if (keyRotationIntervalMs > sessionTimeoutMs) {
            issues.add("Key rotation interval should be less than session timeout")
        }
        
        if (gcmNonceSize < 12) {
            issues.add("GCM nonce size should be at least 12 bytes")
        }
        
        return if (issues.isEmpty()) {
            ConfigValidationResult.Valid
        } else {
            ConfigValidationResult.Invalid(issues)
        }
    }
}

/** Configuration validation result */
sealed class ConfigValidationResult {
    object Valid : ConfigValidationResult()
    data class Invalid(val issues: List<String>) : ConfigValidationResult()
}

/** Algorithm upgrade notification */
data class AlgorithmUpgradeNotice(
    val currentAlgorithm: String,
    val recommendedAlgorithm: String,
    val reason: String,
    val urgency: UpgradeUrgency,
    val effectiveDate: Long? = null
)

enum class UpgradeUrgency {
    INFO,       // Informational only
    ADVISORY,   // Recommended upgrade
    WARNING,    // Security concern, upgrade soon
    CRITICAL    // Immediate upgrade required
}
