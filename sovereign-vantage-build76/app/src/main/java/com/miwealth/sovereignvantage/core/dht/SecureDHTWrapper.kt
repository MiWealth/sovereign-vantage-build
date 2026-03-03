package com.miwealth.sovereignvantage.core.dht

import com.miwealth.sovereignvantage.core.security.pqc.*
import kotlinx.coroutines.*
import java.io.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * SECURE DHT WRAPPER
 * 
 * Wraps DHTClient with mandatory noise injection and peer verification.
 * Ensures all DHT data is protected against traffic analysis even if
 * the PQC tunnel is compromised.
 * 
 * Security Layers:
 * 1. Polynomial noise injection (NoiseInjector) - prevents pattern analysis
 * 2. Peer credential verification - only authorized nodes can decrypt
 * 3. Data integrity signatures - detects tampering
 * 4. Pattern rotation - noise patterns rotate every 30 seconds
 * 
 * Usage:
 * ```
 * val secureDht = SecureDHTWrapper(dhtClient, noiseInjector, nodeCredentials)
 * secureDht.securePublish("key", myData)
 * val data = secureDht.secureQuery<MyType>("key")
 * ```
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 */
class SecureDHTWrapper(
    private val dhtClient: DHTClient,
    private val noiseInjector: NoiseInjector,
    private val localCredentials: NodeCredentials
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Cache of verified peer credentials (peerId -> credentials)
    private val verifiedPeers = ConcurrentHashMap<String, NodeCredentials>()
    
    // Metadata storage for noise removal (key -> SecureMetadata)
    private val metadataCache = ConcurrentHashMap<String, SecureMetadata>()
    
    companion object {
        private const val METADATA_SUFFIX = "_meta"
        private const val SIGNATURE_ALGORITHM = "HmacSHA256"
        private const val HASH_ALGORITHM = "SHA3-256"
        
        // Replication settings
        const val SECURE_REPLICATION_NODES = 10
        const val METADATA_REPLICATION_NODES = 5
    }
    
    /**
     * Securely publish data to DHT with noise injection.
     * 
     * Process:
     * 1. Serialize data to bytes
     * 2. Generate integrity signature
     * 3. Inject mathematical noise
     * 4. Store noisy data + metadata separately
     * 5. Only peers with DECRYPT permission can retrieve
     * 
     * @param key DHT key for storage
     * @param value Serializable data to store
     * @param replicationNodes Number of nodes to replicate to
     * @param allowedPeers Optional list of peer IDs allowed to decrypt (null = all verified peers)
     * @return true if published successfully
     */
    suspend fun <T : Serializable> securePublish(
        key: String,
        value: T,
        replicationNodes: Int = SECURE_REPLICATION_NODES,
        allowedPeers: List<String>? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Step 1: Serialize
            val rawBytes = serializeObject(value)
            
            // Step 2: Generate integrity signature
            val signature = generateSignature(rawBytes, key)
            
            // Step 3: Combine data + signature
            val signedData = combineWithSignature(rawBytes, signature)
            
            // Step 4: Inject noise
            val noiseResult = noiseInjector.injectNoise(signedData)
            
            // Step 5: Create secure metadata
            val metadata = SecureMetadata(
                key = key,
                patternId = noiseResult.patternId,
                noiseMap = noiseResult.noiseMap,
                originalSize = rawBytes.size,
                signatureSize = signature.size,
                publisherId = localCredentials.nodeId,
                allowedPeers = allowedPeers,
                timestamp = System.currentTimeMillis(),
                integrityHash = hashData(rawBytes)
            )
            
            // Step 6: Serialize and encrypt metadata (only for authorized peers)
            val encryptedMetadata = encryptMetadata(metadata)
            
            // Step 7: Publish noisy data
            val dataPublished = dhtClient.put(key, noiseResult.noisyData, replicationNodes)
            
            // Step 8: Publish encrypted metadata
            val metadataPublished = dhtClient.put(
                key + METADATA_SUFFIX, 
                encryptedMetadata, 
                METADATA_REPLICATION_NODES
            )
            
            // Cache locally for fast retrieval
            metadataCache[key] = metadata
            
            dataPublished && metadataPublished
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Securely query data from DHT with noise removal.
     * 
     * Process:
     * 1. Verify local node has DECRYPT permission
     * 2. Retrieve encrypted metadata
     * 3. Decrypt and validate metadata
     * 4. Check if we're in allowedPeers list
     * 5. Retrieve noisy data
     * 6. Remove noise using pattern + map
     * 7. Verify integrity signature
     * 8. Deserialize and return
     * 
     * @param key DHT key to query
     * @return Decrypted data or null if unauthorized/not found
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> secureQuery(key: String): T? = withContext(Dispatchers.IO) {
        try {
            // Step 1: Verify we have DECRYPT permission
            if (!localCredentials.hasPermission(NodePermissions.DECRYPT)) {
                return@withContext null
            }
            
            // Step 2: Try local cache first
            var metadata = metadataCache[key]
            
            // Step 3: If not cached, retrieve from DHT
            if (metadata == null) {
                val encryptedMetadata = dhtClient.get(key + METADATA_SUFFIX) 
                    ?: return@withContext null
                metadata = decryptMetadata(encryptedMetadata) 
                    ?: return@withContext null
            }
            
            // Step 4: Check if we're authorized
            if (!isAuthorizedToDecrypt(metadata)) {
                return@withContext null
            }
            
            // Step 5: Retrieve noisy data
            val noisyData = dhtClient.get(key) ?: return@withContext null
            
            // Step 6: Remove noise
            val signedData = noiseInjector.removeNoise(
                noisyData,
                metadata.noiseMap,
                metadata.patternId,
                localCredentials
            ) ?: return@withContext null
            
            // Step 7: Extract signature and verify
            val (rawBytes, signature) = extractSignature(signedData, metadata.signatureSize)
            if (!verifySignature(rawBytes, signature, key)) {
                return@withContext null
            }
            
            // Step 8: Verify integrity hash
            if (hashData(rawBytes) != metadata.integrityHash) {
                return@withContext null
            }
            
            // Step 9: Deserialize and return
            deserializeObject(rawBytes) as? T
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Securely delete data from DHT.
     * Only the original publisher or admin can delete.
     */
    suspend fun secureDelete(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val metadata = metadataCache[key] ?: run {
                val encryptedMetadata = dhtClient.get(key + METADATA_SUFFIX) ?: return@withContext false
                decryptMetadata(encryptedMetadata) ?: return@withContext false
            }
            
            // Only publisher or admin can delete
            if (metadata.publisherId != localCredentials.nodeId && 
                !localCredentials.hasPermission(NodePermissions.ADMIN)) {
                return@withContext false
            }
            
            val dataDeleted = dhtClient.delete(key)
            val metadataDeleted = dhtClient.delete(key + METADATA_SUFFIX)
            metadataCache.remove(key)
            
            dataDeleted && metadataDeleted
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Register a verified peer for decryption.
     * Peer must present valid credentials with DECRYPT permission.
     */
    fun registerVerifiedPeer(peerId: String, credentials: NodeCredentials): Boolean {
        if (!verifyPeerCredentials(credentials)) return false
        if (!credentials.hasPermission(NodePermissions.DECRYPT)) return false
        verifiedPeers[peerId] = credentials
        return true
    }
    
    /**
     * Revoke a peer's access to decrypt data.
     */
    fun revokePeer(peerId: String) {
        verifiedPeers.remove(peerId)
    }
    
    /**
     * Check if a peer is verified and authorized.
     */
    fun isPeerVerified(peerId: String): Boolean = verifiedPeers.containsKey(peerId)
    
    /**
     * Get current noise pattern ID for debugging/monitoring.
     */
    fun getCurrentPatternId(): String? = noiseInjector.getCurrentPatternId()
    
    /**
     * Force noise pattern rotation (for security events).
     */
    fun rotateNoisePattern(): NoisePattern = noiseInjector.rotatePattern()
    
    // ========================================================================
    // PRIVATE: Serialization
    // ========================================================================
    
    private fun serializeObject(obj: Any): ByteArray {
        return ByteArrayOutputStream().use { baos ->
            ObjectOutputStream(baos).use { oos ->
                oos.writeObject(obj)
            }
            baos.toByteArray()
        }
    }
    
    private fun deserializeObject(bytes: ByteArray): Any? {
        return try {
            ByteArrayInputStream(bytes).use { bais ->
                ObjectInputStream(bais).use { ois ->
                    ois.readObject()
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    // ========================================================================
    // PRIVATE: Signature & Integrity
    // ========================================================================
    
    private fun generateSignature(data: ByteArray, key: String): ByteArray {
        val mac = Mac.getInstance(SIGNATURE_ALGORITHM)
        val secretKey = SecretKeySpec(localCredentials.noiseFilterKey, SIGNATURE_ALGORITHM)
        mac.init(secretKey)
        mac.update(data)
        mac.update(key.toByteArray(Charsets.UTF_8))
        mac.update(localCredentials.nodeId.toByteArray(Charsets.UTF_8))
        return mac.doFinal()
    }
    
    private fun verifySignature(data: ByteArray, signature: ByteArray, key: String): Boolean {
        // For verification, we need the publisher's credentials
        // In this implementation, we trust the metadata's publisherId
        // and verify using our own key (symmetric for simplicity)
        val expectedSignature = generateSignature(data, key)
        return SideChannelDefense.constantTimeEquals(signature, expectedSignature)
    }
    
    private fun combineWithSignature(data: ByteArray, signature: ByteArray): ByteArray {
        return data + signature
    }
    
    private fun extractSignature(signedData: ByteArray, signatureSize: Int): Pair<ByteArray, ByteArray> {
        val dataSize = signedData.size - signatureSize
        val data = signedData.copyOfRange(0, dataSize)
        val signature = signedData.copyOfRange(dataSize, signedData.size)
        return Pair(data, signature)
    }
    
    private fun hashData(data: ByteArray): String {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    // ========================================================================
    // PRIVATE: Metadata Encryption
    // ========================================================================
    
    private fun encryptMetadata(metadata: SecureMetadata): ByteArray {
        // Serialize metadata
        val metadataBytes = serializeObject(metadata)
        
        // For now, use a simple XOR with the noise filter key
        // In production, this should use AES-256-GCM
        val key = localCredentials.noiseFilterKey
        return ByteArray(metadataBytes.size) { i ->
            (metadataBytes[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
    }
    
    private fun decryptMetadata(encryptedMetadata: ByteArray): SecureMetadata? {
        return try {
            val key = localCredentials.noiseFilterKey
            val decryptedBytes = ByteArray(encryptedMetadata.size) { i ->
                (encryptedMetadata[i].toInt() xor key[i % key.size].toInt()).toByte()
            }
            deserializeObject(decryptedBytes) as? SecureMetadata
        } catch (e: Exception) {
            null
        }
    }
    
    // ========================================================================
    // PRIVATE: Authorization
    // ========================================================================
    
    private fun isAuthorizedToDecrypt(metadata: SecureMetadata): Boolean {
        // Publisher always has access
        if (metadata.publisherId == localCredentials.nodeId) return true
        
        // Admin always has access
        if (localCredentials.hasPermission(NodePermissions.ADMIN)) return true
        
        // Check allowed peers list
        val allowedPeers = metadata.allowedPeers
        if (allowedPeers == null) {
            // Null means all verified peers
            return verifiedPeers.containsKey(localCredentials.nodeId) || 
                   localCredentials.hasPermission(NodePermissions.DECRYPT)
        }
        
        return localCredentials.nodeId in allowedPeers
    }
    
    private fun verifyPeerCredentials(credentials: NodeCredentials): Boolean {
        // Verify the auth token using HMAC
        val expectedToken = Mac.getInstance(SIGNATURE_ALGORITHM).run {
            init(SecretKeySpec(credentials.noiseFilterKey, SIGNATURE_ALGORITHM))
            update(credentials.nodeId.toByteArray(Charsets.UTF_8))
            update(credentials.publicKey)
            doFinal()
        }
        
        return SideChannelDefense.constantTimeEquals(credentials.authToken, expectedToken)
    }
    
    /**
     * Cleanup resources
     */
    fun shutdown() {
        metadataCache.clear()
        verifiedPeers.clear()
        scope.cancel()
    }
}

/**
 * Metadata for secure DHT entries.
 * Stores noise removal information and access control.
 */
data class SecureMetadata(
    val key: String,
    val patternId: String,
    val noiseMap: ByteArray,
    val originalSize: Int,
    val signatureSize: Int,
    val publisherId: String,
    val allowedPeers: List<String>?,
    val timestamp: Long,
    val integrityHash: String
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SecureMetadata) return false
        return key == other.key && timestamp == other.timestamp
    }
    
    override fun hashCode(): Int = key.hashCode() * 31 + timestamp.hashCode()
}
