package com.miwealth.sovereignvantage.core.security.pqc.hybrid

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.miwealth.sovereignvantage.core.security.pqc.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * PQC CREDENTIAL VAULT
 * 
 * Secure storage for exchange API credentials using hybrid encryption:
 * - Kyber key encapsulation for the master key
 * - AES-256-GCM for credential encryption
 * - Android Keystore for device binding
 * - Automatic key rotation
 * 
 * Defense Layers:
 * 1. Android EncryptedSharedPreferences (AES-256-GCM, Keystore-backed)
 * 2. Kyber-1024 encapsulation for vault master key
 * 3. Per-credential unique nonces
 * 4. Side-channel attack mitigations
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */
class PQCCredentialVault(
    private val context: Context,
    private val config: HybridPQCConfig = HybridPQCConfig.default()
) {
    
    private val kyber = KyberKEM(
        when (config.kemAlgorithm) {
            KEMAlgorithm.KYBER_512 -> 1
            KEMAlgorithm.KYBER_768 -> 3
            KEMAlgorithm.KYBER_1024 -> 5
        }
    )
    
    private val dilithium = DilithiumDSA(
        when (config.signatureAlgorithm) {
            SignatureAlgorithm.DILITHIUM_2 -> 2
            SignatureAlgorithm.DILITHIUM_3 -> 3
            SignatureAlgorithm.DILITHIUM_5 -> 5
        }
    )
    
    private val gson = Gson()
    private val secureRandom = SecureRandom()
    
    // Encrypted storage backed by Android Keystore
    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            VAULT_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    // Vault master key pair (Kyber)
    private var vaultKeyPair: KyberKeyPair? = null
    
    // Signing key for credential integrity
    private var signingKeyPair: DilithiumKeyPair? = null
    
    companion object {
        private const val VAULT_PREFS_NAME = "sv_pqc_credential_vault"
        private const val KEY_VAULT_PUBLIC_KEY = "vault_public_key"
        private const val KEY_VAULT_PRIVATE_KEY_ENC = "vault_private_key_enc"
        private const val KEY_SIGNING_PUBLIC_KEY = "signing_public_key"
        private const val KEY_SIGNING_PRIVATE_KEY_ENC = "signing_private_key_enc"
        private const val KEY_CREDENTIALS_PREFIX = "cred_"
        private const val KEY_METADATA_PREFIX = "meta_"
        private const val KEY_VAULT_CREATED = "vault_created"
        private const val KEY_LAST_ROTATION = "last_rotation"
        
        private const val GCM_TAG_BITS = 128
        private const val GCM_NONCE_SIZE = 12
    }
    
    // =========================================================================
    // VAULT INITIALIZATION
    // =========================================================================
    
    /**
     * Initialize or unlock the vault
     * 
     * @param userSecret User-provided secret (PIN, password, or biometric-derived)
     * @return true if vault is ready
     */
    fun initialize(userSecret: ByteArray): Boolean {
        return try {
            if (isVaultInitialized()) {
                unlockVault(userSecret)
            } else {
                createVault(userSecret)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Check if vault has been created
     */
    fun isVaultInitialized(): Boolean {
        return encryptedPrefs.contains(KEY_VAULT_PUBLIC_KEY)
    }
    
    /**
     * Check if vault is currently unlocked
     */
    fun isUnlocked(): Boolean {
        return vaultKeyPair != null && signingKeyPair != null
    }
    
    private fun createVault(userSecret: ByteArray) {
        // Generate Kyber key pair for vault encryption
        val kyberKeys = kyber.generateKeyPair()
        
        // Generate Dilithium key pair for credential signing
        val dilithiumKeys = dilithium.generateKeyPair()
        
        // Derive encryption key from user secret
        val derivedKey = deriveKey(userSecret, generateSalt())
        
        // Encrypt private keys with derived key
        val encryptedKyberPrivate = encryptWithKey(kyberKeys.privateKey, derivedKey)
        val encryptedDilithiumPrivate = encryptWithKey(dilithiumKeys.privateKey, derivedKey)
        
        // Store in encrypted preferences
        encryptedPrefs.edit()
            .putString(KEY_VAULT_PUBLIC_KEY, kyberKeys.publicKey.toBase64())
            .putString(KEY_VAULT_PRIVATE_KEY_ENC, encryptedKyberPrivate.toBase64())
            .putString(KEY_SIGNING_PUBLIC_KEY, dilithiumKeys.publicKey.toBase64())
            .putString(KEY_SIGNING_PRIVATE_KEY_ENC, encryptedDilithiumPrivate.toBase64())
            .putLong(KEY_VAULT_CREATED, System.currentTimeMillis())
            .putLong(KEY_LAST_ROTATION, System.currentTimeMillis())
            .apply()
        
        // Keep keys in memory
        vaultKeyPair = kyberKeys
        signingKeyPair = dilithiumKeys
        
        // Wipe derived key
        SideChannelDefense.secureWipe(derivedKey)
    }
    
    private fun unlockVault(userSecret: ByteArray) {
        val salt = getSalt() ?: throw SecurityException("Vault corrupted: no salt")
        val derivedKey = deriveKey(userSecret, salt)
        
        try {
            // Decrypt Kyber private key
            val encryptedKyberPrivate = encryptedPrefs.getString(KEY_VAULT_PRIVATE_KEY_ENC, null)
                ?.fromBase64() ?: throw SecurityException("Missing vault key")
            val kyberPrivate = decryptWithKey(encryptedKyberPrivate, derivedKey)
            val kyberPublic = encryptedPrefs.getString(KEY_VAULT_PUBLIC_KEY, null)
                ?.fromBase64() ?: throw SecurityException("Missing vault public key")
            
            // Decrypt Dilithium private key
            val encryptedDilithiumPrivate = encryptedPrefs.getString(KEY_SIGNING_PRIVATE_KEY_ENC, null)
                ?.fromBase64() ?: throw SecurityException("Missing signing key")
            val dilithiumPrivate = decryptWithKey(encryptedDilithiumPrivate, derivedKey)
            val dilithiumPublic = encryptedPrefs.getString(KEY_SIGNING_PUBLIC_KEY, null)
                ?.fromBase64() ?: throw SecurityException("Missing signing public key")
            
            vaultKeyPair = KyberKeyPair(kyberPublic, kyberPrivate)
            signingKeyPair = DilithiumKeyPair(dilithiumPublic, dilithiumPrivate)
            
        } finally {
            SideChannelDefense.secureWipe(derivedKey)
        }
    }
    
    // =========================================================================
    // CREDENTIAL STORAGE
    // =========================================================================
    
    /**
     * Store exchange credentials securely
     */
    fun storeCredential(credential: ExchangeCredential): Boolean {
        if (!isUnlocked()) return false
        
        val publicKey = vaultKeyPair?.publicKey ?: return false
        val signingKey = signingKeyPair?.privateKey ?: return false
        
        try {
            // Serialize credential
            val credentialJson = gson.toJson(credential)
            val credentialBytes = credentialJson.toByteArray(Charsets.UTF_8)
            
            // Encrypt with Kyber KEM
            val encapsulation = kyber.encapsulate(publicKey)
            val encryptedCredential = encryptWithKey(credentialBytes, encapsulation.sharedSecret)
            
            // Sign the encrypted credential
            val signature = dilithium.sign(encryptedCredential, signingKey)
            
            // Create storage envelope
            val envelope = CredentialEnvelope(
                encryptedData = encryptedCredential,
                encapsulatedKey = encapsulation.ciphertext,
                signature = signature,
                algorithm = config.kemAlgorithm.id,
                createdAt = System.currentTimeMillis(),
                version = 1
            )
            
            // Store envelope
            val envelopeJson = gson.toJson(envelope)
            encryptedPrefs.edit()
                .putString("${KEY_CREDENTIALS_PREFIX}${credential.exchangeId}", envelopeJson)
                .apply()
            
            // Store metadata (for listing without decryption)
            val metadata = CredentialMetadata(
                exchangeId = credential.exchangeId,
                exchangeName = credential.exchangeName,
                createdAt = envelope.createdAt,
                lastUsed = envelope.createdAt,
                isTestnet = credential.isTestnet
            )
            encryptedPrefs.edit()
                .putString("${KEY_METADATA_PREFIX}${credential.exchangeId}", gson.toJson(metadata))
                .apply()
            
            // Wipe shared secret
            SideChannelDefense.secureWipe(encapsulation.sharedSecret)
            
            return true
            
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Retrieve exchange credentials
     */
    fun getCredential(exchangeId: String): ExchangeCredential? {
        if (!isUnlocked()) return null
        
        val privateKey = vaultKeyPair?.privateKey ?: return null
        val signingPublicKey = signingKeyPair?.publicKey ?: return null
        
        try {
            val envelopeJson = encryptedPrefs.getString("${KEY_CREDENTIALS_PREFIX}$exchangeId", null)
                ?: return null
            val envelope = gson.fromJson(envelopeJson, CredentialEnvelope::class.java)
            
            // Verify signature
            if (!dilithium.verify(envelope.encryptedData, envelope.signature, signingPublicKey)) {
                return null  // Tampering detected
            }
            
            // Decapsulate shared secret
            val sharedSecret = kyber.decapsulate(envelope.encapsulatedKey, privateKey)
            
            // Decrypt credential
            val credentialBytes = decryptWithKey(envelope.encryptedData, sharedSecret)
            val credentialJson = String(credentialBytes, Charsets.UTF_8)
            val credential = gson.fromJson(credentialJson, ExchangeCredential::class.java)
            
            // Update last used
            updateLastUsed(exchangeId)
            
            // Wipe shared secret
            SideChannelDefense.secureWipe(sharedSecret)
            
            return credential
            
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Delete exchange credentials
     */
    fun deleteCredential(exchangeId: String): Boolean {
        encryptedPrefs.edit()
            .remove("${KEY_CREDENTIALS_PREFIX}$exchangeId")
            .remove("${KEY_METADATA_PREFIX}$exchangeId")
            .apply()
        return true
    }
    
    /**
     * List all stored credentials (metadata only, no decryption)
     */
    fun listCredentials(): List<CredentialMetadata> {
        return encryptedPrefs.all
            .filterKeys { it.startsWith(KEY_METADATA_PREFIX) }
            .mapNotNull { (_, value) ->
                try {
                    gson.fromJson(value as String, CredentialMetadata::class.java)
                } catch (e: Exception) {
                    null
                }
            }
    }
    
    /**
     * Check if credentials exist for an exchange
     */
    fun hasCredential(exchangeId: String): Boolean {
        return encryptedPrefs.contains("${KEY_CREDENTIALS_PREFIX}$exchangeId")
    }
    
    private fun updateLastUsed(exchangeId: String) {
        val metadataJson = encryptedPrefs.getString("${KEY_METADATA_PREFIX}$exchangeId", null)
            ?: return
        val metadata = gson.fromJson(metadataJson, CredentialMetadata::class.java)
        val updated = metadata.copy(lastUsed = System.currentTimeMillis())
        encryptedPrefs.edit()
            .putString("${KEY_METADATA_PREFIX}$exchangeId", gson.toJson(updated))
            .apply()
    }
    
    // =========================================================================
    // KEY ROTATION
    // =========================================================================
    
    /**
     * Rotate vault keys (re-encrypts all credentials)
     */
    fun rotateKeys(userSecret: ByteArray): Boolean {
        if (!isUnlocked()) return false
        
        try {
            // Get all current credentials
            val credentials = listCredentials().mapNotNull { meta ->
                getCredential(meta.exchangeId)
            }
            
            // Generate new keys
            val newKyberKeys = kyber.generateKeyPair()
            val newDilithiumKeys = dilithium.generateKeyPair()
            
            // Derive new encryption key
            val newSalt = generateSalt()
            val derivedKey = deriveKey(userSecret, newSalt)
            
            // Encrypt new private keys
            val encryptedKyberPrivate = encryptWithKey(newKyberKeys.privateKey, derivedKey)
            val encryptedDilithiumPrivate = encryptWithKey(newDilithiumKeys.privateKey, derivedKey)
            
            // Update keys in storage
            encryptedPrefs.edit()
                .putString(KEY_VAULT_PUBLIC_KEY, newKyberKeys.publicKey.toBase64())
                .putString(KEY_VAULT_PRIVATE_KEY_ENC, encryptedKyberPrivate.toBase64())
                .putString(KEY_SIGNING_PUBLIC_KEY, newDilithiumKeys.publicKey.toBase64())
                .putString(KEY_SIGNING_PRIVATE_KEY_ENC, encryptedDilithiumPrivate.toBase64())
                .putLong(KEY_LAST_ROTATION, System.currentTimeMillis())
                .apply()
            
            // Wipe old keys
            vaultKeyPair?.let { SideChannelDefense.secureWipe(it.privateKey) }
            signingKeyPair?.let { SideChannelDefense.secureWipe(it.privateKey) }
            
            // Set new keys
            vaultKeyPair = newKyberKeys
            signingKeyPair = newDilithiumKeys
            
            // Re-encrypt all credentials with new keys
            credentials.forEach { storeCredential(it) }
            
            SideChannelDefense.secureWipe(derivedKey)
            
            return true
            
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Check if key rotation is needed
     */
    fun needsKeyRotation(): Boolean {
        val lastRotation = encryptedPrefs.getLong(KEY_LAST_ROTATION, 0)
        return System.currentTimeMillis() - lastRotation > config.keyRotationIntervalMs * 24 // Daily rotation
    }
    
    // =========================================================================
    // VAULT MANAGEMENT
    // =========================================================================
    
    /**
     * Lock the vault (wipe keys from memory)
     */
    fun lock() {
        vaultKeyPair?.let { SideChannelDefense.secureWipe(it.privateKey) }
        signingKeyPair?.let { SideChannelDefense.secureWipe(it.privateKey) }
        vaultKeyPair = null
        signingKeyPair = null
    }
    
    /**
     * Destroy the vault completely
     */
    fun destroy(): Boolean {
        lock()
        encryptedPrefs.edit().clear().apply()
        return true
    }
    
    /**
     * Export vault for backup (encrypted)
     */
    fun exportVault(backupKey: ByteArray): ByteArray? {
        if (!isUnlocked()) return null
        
        val allData = mutableMapOf<String, String>()
        encryptedPrefs.all.forEach { (key, value) ->
            if (value is String) {
                allData[key] = value
            }
        }
        
        val json = gson.toJson(allData)
        return encryptWithKey(json.toByteArray(Charsets.UTF_8), backupKey)
    }
    
    /**
     * Import vault from backup
     */
    fun importVault(encryptedBackup: ByteArray, backupKey: ByteArray, userSecret: ByteArray): Boolean {
        return try {
            val json = String(decryptWithKey(encryptedBackup, backupKey), Charsets.UTF_8)
            val type = object : TypeToken<Map<String, String>>() {}.type
            val data: Map<String, String> = gson.fromJson(json, type)
            
            encryptedPrefs.edit().apply {
                data.forEach { (key, value) -> putString(key, value) }
                apply()
            }
            
            unlockVault(userSecret)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    // =========================================================================
    // CRYPTOGRAPHIC HELPERS
    // =========================================================================
    
    private fun deriveKey(secret: ByteArray, salt: ByteArray): ByteArray {
        // PBKDF2-like derivation using HMAC-SHA256
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        mac.update(salt)
        return mac.doFinal()
    }
    
    private fun generateSalt(): ByteArray {
        val salt = ByteArray(32)
        secureRandom.nextBytes(salt)
        // Store salt
        encryptedPrefs.edit().putString("vault_salt", salt.toBase64()).apply()
        return salt
    }
    
    private fun getSalt(): ByteArray? {
        return encryptedPrefs.getString("vault_salt", null)?.fromBase64()
    }
    
    private fun encryptWithKey(data: ByteArray, key: ByteArray): ByteArray {
        val nonce = ByteArray(GCM_NONCE_SIZE)
        secureRandom.nextBytes(nonce)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key.copyOfRange(0, 32), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, nonce))
        
        val ciphertext = cipher.doFinal(data)
        return nonce + ciphertext
    }
    
    private fun decryptWithKey(data: ByteArray, key: ByteArray): ByteArray {
        val nonce = data.copyOfRange(0, GCM_NONCE_SIZE)
        val ciphertext = data.copyOfRange(GCM_NONCE_SIZE, data.size)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key.copyOfRange(0, 32), "AES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, nonce))
        
        return cipher.doFinal(ciphertext)
    }
    
    private fun ByteArray.toBase64(): String = 
        android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
    
    private fun String.fromBase64(): ByteArray = 
        android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
}

// =========================================================================
// DATA CLASSES
// =========================================================================

/**
 * Exchange API credentials
 */
data class ExchangeCredential(
    val exchangeId: String,
    val exchangeName: String,
    val apiKey: String,
    val apiSecret: String,
    val passphrase: String = "",      // For Coinbase
    val subaccountId: String = "",
    val isTestnet: Boolean = false,
    val permissions: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null
)

/**
 * Credential metadata (stored separately for fast listing)
 */
data class CredentialMetadata(
    val exchangeId: String,
    val exchangeName: String,
    val createdAt: Long,
    val lastUsed: Long,
    val isTestnet: Boolean
)

/**
 * Encrypted credential storage envelope
 */
data class CredentialEnvelope(
    val encryptedData: ByteArray,
    val encapsulatedKey: ByteArray,
    val signature: ByteArray,
    val algorithm: String,
    val createdAt: Long,
    val version: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CredentialEnvelope) return false
        return encryptedData.contentEquals(other.encryptedData) && createdAt == other.createdAt
    }
    
    override fun hashCode(): Int = encryptedData.contentHashCode()
}
