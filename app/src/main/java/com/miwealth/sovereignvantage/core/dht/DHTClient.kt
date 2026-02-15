package com.miwealth.sovereignvantage.core.dht

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.miwealth.sovereignvantage.core.dflp.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.util.concurrent.ConcurrentHashMap

/**
 * DHT Client with Real P2P Transport
 * 
 * Distributed Hash Table implementation using PQC-secured P2P connections.
 * 
 * Features:
 * - mDNS/DNS-SD peer discovery (Android NsdManager)
 * - PQC-secured peer connections via P2PCommunicator
 * - Kademlia-style XOR routing
 * - Replication to k nearest nodes
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 */
class DHTClient(
    private val context: Context,
    private val p2pCommunicator: P2PCommunicator
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _peerCount = MutableStateFlow(0)
    val peerCount: StateFlow<Int> = _peerCount.asStateFlow()
    
    val nodeId: String get() = p2pCommunicator.localPeerId
    
    // Local storage
    private val localStorage = ConcurrentHashMap<String, ByteArray>()
    
    // Known peers from discovery
    private val discoveredPeers = ConcurrentHashMap<String, DHTNode>()
    
    // Pending requests
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<ByteArray?>>()
    
    // mDNS discovery
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    
    companion object {
        const val SERVICE_TYPE = "_sovereignvantage._tcp."
        const val SERVICE_NAME = "SV-DHT"
        const val DHT_PORT = 42069
        const val REPLICATION_FACTOR = 3
        const val REQUEST_TIMEOUT_MS = 10_000L
    }
    
    init {
        // Listen for P2P messages
        scope.launch {
            p2pCommunicator.messageEvents.collect { event ->
                when (event) {
                    is MessageEvent.Received -> handleDHTMessage(event.peerId, event.message)
                    else -> {}
                }
            }
        }
        
        // Track peer count
        scope.launch {
            p2pCommunicator.connectionEvents.collect { event ->
                _peerCount.value = p2pCommunicator.connectedPeerCount
            }
        }
    }
    
    /**
     * Connect to DHT network
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Start P2P listener
            p2pCommunicator.startListening { msg, peerId -> handleDHTMessage(peerId, msg) }
            
            // Register service for discovery
            registerService()
            
            // Start discovering peers
            startDiscovery()
            
            _isConnected.value = true
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Disconnect from DHT network
     */
    suspend fun disconnect() {
        stopDiscovery()
        unregisterService()
        p2pCommunicator.stop()
        localStorage.clear()
        discoveredPeers.clear()
        _isConnected.value = false
        _peerCount.value = 0
    }
    
    /**
     * Full shutdown — cancels coroutine scope and disconnects.
     * Call this when the DHTClient is being permanently disposed.
     */
    fun shutdown() {
        scope.cancel()
        kotlinx.coroutines.runBlocking {
            runCatching { disconnect() }
        }
    }
    
    /**
     * Store key-value pair in DHT
     */
    suspend fun put(key: String, value: ByteArray, replicationNodes: Int = REPLICATION_FACTOR): Boolean {
        // Store locally
        localStorage[key] = value
        
        // Find nearest peers and replicate
        val targetPeers = findNearestPeers(key, replicationNodes)
        if (targetPeers.isEmpty()) return true // Local only is OK
        
        var replicated = 0
        targetPeers.forEach { peer ->
            if (sendPutRequest(peer, key, value)) replicated++
        }
        
        return replicated > 0 || targetPeers.isEmpty()
    }
    
    /**
     * Retrieve value from DHT
     */
    suspend fun get(key: String): ByteArray? {
        // Check local first
        localStorage[key]?.let { return it }
        
        // Query nearest peers
        val targetPeers = findNearestPeers(key, REPLICATION_FACTOR)
        
        for (peer in targetPeers) {
            val value = sendGetRequest(peer, key)
            if (value != null) {
                localStorage[key] = value // Cache locally
                return value
            }
        }
        
        return null
    }
    
    /**
     * Delete key from DHT
     */
    suspend fun delete(key: String): Boolean {
        localStorage.remove(key)
        // Note: DHT deletion is eventually consistent
        return true
    }
    
    /**
     * Publish data with replication (alias for put)
     */
    suspend fun <T : Serializable> publish(key: String, value: T, replicationNodes: Int = REPLICATION_FACTOR): Boolean {
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).writeObject(value) }.toByteArray()
        return put(key, bytes, replicationNodes)
    }
    
    /**
     * Query for typed value
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> query(key: String): T? {
        val bytes = get(key) ?: return null
        return try {
            ObjectInputStream(ByteArrayInputStream(bytes)).readObject() as T
        } catch (e: Exception) { null }
    }
    
    /**
     * Query with pattern (prefix match)
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> queryPattern(pattern: String): List<T> {
        val results = mutableListOf<T>()
        
        // Check local storage
        localStorage.entries
            .filter { it.key.startsWith(pattern.removeSuffix("*")) }
            .forEach { entry ->
                try {
                    val obj = ObjectInputStream(ByteArrayInputStream(entry.value)).readObject() as T
                    results.add(obj)
                } catch (e: Exception) { }
            }
        
        // Query peers for pattern
        val peers = p2pCommunicator.getConnectedPeers()
        // For brevity, pattern queries are local-first in this implementation
        
        return results
    }
    
    /**
     * Find peers for given count
     */
    suspend fun findPeers(count: Int): List<DHTNode> {
        return discoveredPeers.values.take(count)
    }
    
    /**
     * Broadcast to all connected peers
     */
    suspend fun broadcast(channel: String, data: ByteArray) {
        val msg = createDHTMessage(DFLPMessageType.DHT_PUT, channel, data)
        p2pCommunicator.broadcast(msg)
    }
    
    // ========================================================================
    // Private: Message Handling
    // ========================================================================
    
    private suspend fun handleDHTMessage(peerId: String, message: DFLPMessage) {
        when (message.messageType) {
            DFLPMessageType.DHT_PUT -> {
                val key = message.modelHash
                localStorage[key] = message.weightDeltas
            }
            DFLPMessageType.DHT_GET -> {
                val key = message.modelHash
                val value = localStorage[key]
                val response = createDHTMessage(DFLPMessageType.DHT_RESPONSE, message.updateId, value ?: ByteArray(0))
                p2pCommunicator.sendToPeer(peerId, response)
            }
            DFLPMessageType.DHT_RESPONSE -> {
                pendingRequests[message.updateId]?.complete(
                    if (message.weightDeltas.isEmpty()) null else message.weightDeltas
                )
            }
            else -> {}
        }
    }
    
    private suspend fun sendPutRequest(peer: DHTNode, key: String, value: ByteArray): Boolean {
        val peerId = peer.id
        if (!p2pCommunicator.getConnectedPeers().contains(peerId)) {
            p2pCommunicator.connectToPeer(peer.address, peer.port) ?: return false
        }
        
        val msg = createDHTMessage(DFLPMessageType.DHT_PUT, key, value)
        return p2pCommunicator.sendToPeer(peerId, msg)
    }
    
    private suspend fun sendGetRequest(peer: DHTNode, key: String): ByteArray? {
        val peerId = peer.id
        if (!p2pCommunicator.getConnectedPeers().contains(peerId)) {
            p2pCommunicator.connectToPeer(peer.address, peer.port) ?: return null
        }
        
        val requestId = java.util.UUID.randomUUID().toString()
        val deferred = CompletableDeferred<ByteArray?>()
        pendingRequests[requestId] = deferred
        
        val msg = createDHTMessage(DFLPMessageType.DHT_GET, key, ByteArray(0), requestId)
        if (!p2pCommunicator.sendToPeer(peerId, msg)) {
            pendingRequests.remove(requestId)
            return null
        }
        
        return try {
            withTimeout(REQUEST_TIMEOUT_MS) { deferred.await() }
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            null
        }
    }
    
    private fun createDHTMessage(type: DFLPMessageType, key: String, data: ByteArray, requestId: String? = null): DFLPMessage {
        return DFLPMessage(
            senderPeerId = nodeId,
            timestamp = System.currentTimeMillis(),
            modelHash = key,
            noiseLevel = 0.0,
            updateId = requestId ?: java.util.UUID.randomUUID().toString(),
            weightDeltas = data,
            messageType = type
        )
    }
    
    // ========================================================================
    // Private: Peer Discovery (mDNS)
    // ========================================================================
    
    private fun findNearestPeers(key: String, count: Int): List<DHTNode> {
        val keyHash = key.hashCode()
        return discoveredPeers.values
            .sortedBy { (it.id.hashCode() xor keyHash).toLong().and(0xFFFFFFFFL) }
            .take(count)
    }
    
    private fun registerService() {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$SERVICE_NAME-${nodeId.take(8)}"
            serviceType = SERVICE_TYPE
            port = DHT_PORT
        }
        
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        
        nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }
    
    private fun unregisterService() {
        registrationListener?.let { nsdManager?.unregisterService(it) }
    }
    
    private fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceName.startsWith(SERVICE_NAME)) {
                    resolveService(serviceInfo)
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val peerId = serviceInfo.serviceName.removePrefix("$SERVICE_NAME-")
                discoveredPeers.entries.removeIf { it.value.id.startsWith(peerId) }
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        
        nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }
    
    private fun stopDiscovery() {
        discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
    }
    
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(info: NsdServiceInfo) {
                val peerId = info.serviceName.removePrefix("$SERVICE_NAME-")
                val node = DHTNode(
                    id = peerId,
                    address = info.host?.hostAddress ?: return,
                    port = info.port,
                    lastSeen = System.currentTimeMillis(),
                    isOnline = true
                )
                discoveredPeers[peerId] = node
                _peerCount.value = discoveredPeers.size
                
                // Auto-connect
                scope.launch { p2pCommunicator.connectToPeer(node.address, node.port) }
            }
        })
    }
}

data class DHTNode(
    val id: String,
    val address: String,
    val port: Int,
    val lastSeen: Long,
    val reputation: Int = 0,
    val isOnline: Boolean = true
)

object DHTChannels {
    const val LEADERBOARD = "leaderboard"
    const val MODEL_UPDATES = "model_updates"
    const val PRICE_FEEDS = "price_feeds"
    const val NODE_DISCOVERY = "node_discovery"
    const val KEY_SHARDS = "key_shards"
    const val RECOVERY_INVITATIONS = "recovery_invitations"
    const val RECOVERY_REQUESTS = "recovery_requests"
    
    // Gamification channels (v5.5.35)
    const val GAM_PROFILES = "gam_profiles"
    const val GAM_BADGES = "gam_badges"
    const val GAM_CHALLENGES = "gam_challenges"
    const val GAM_ACTIVITY = "gam_activity"
    const val GAM_FOLLOWS = "gam_follows"
    const val GAM_REPUTATION = "gam_reputation"
}