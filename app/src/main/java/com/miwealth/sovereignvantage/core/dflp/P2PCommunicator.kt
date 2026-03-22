package com.miwealth.sovereignvantage.core.dflp

import android.content.Context
import com.miwealth.sovereignvantage.core.security.pqc.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * P2P Communicator with Real PQC Security
 * 
 * Handles PQC-secured peer-to-peer communication for:
 * - DFLP model weight sharing
 * - DHT key-value operations
 * - Trusted associate key recovery
 * - Leaderboard synchronization
 * - P2P chat
 * 
 * Security:
 * - Kyber-1024 key encapsulation (real Bouncy Castle)
 * - Dilithium-5 mutual authentication
 * - AES-256-GCM encrypted channels
 * - Peer identity = SHA3-256(Dilithium public key)
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 */
class P2PCommunicator(
    private val context: Context,
    private val port: Int = 42069
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeChannels = ConcurrentHashMap<String, P2PSecureTransport.SecureChannel>()
    
    private var serverSocket: ServerSocket? = null
    private var isListening = false
    
    private lateinit var kyberKeys: KyberKeyPair
    private lateinit var dilithiumKeys: DilithiumKeyPair
    private lateinit var transport: P2PSecureTransport
    
    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>()
    val connectionEvents: SharedFlow<ConnectionEvent> = _connectionEvents.asSharedFlow()
    
    private val _messageEvents = MutableSharedFlow<MessageEvent>()
    val messageEvents: SharedFlow<MessageEvent> = _messageEvents.asSharedFlow()
    
    val localPeerId: String get() = if (::dilithiumKeys.isInitialized) dilithiumKeys.identityHex() else ""
    val connectedPeerCount: Int get() = activeChannels.size
    
    init { initializeKeys() }
    
    private fun initializeKeys() {
        kyberKeys = KyberKEM(5).generateKeyPair()
        dilithiumKeys = DilithiumDSA(5).generateKeyPair()
        transport = P2PSecureTransport(kyberKeys, dilithiumKeys, 5)
    }
    
    fun startListening(onMessage: suspend (DFLPMessage, String) -> Unit) {
        if (isListening) return
        isListening = true
        
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                _connectionEvents.emit(ConnectionEvent.ServerStarted(port))
                
                while (isActive && isListening) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        handleIncomingConnection(clientSocket, onMessage)
                    } catch (e: Exception) {
                        if (isListening) _connectionEvents.emit(ConnectionEvent.Error("Accept: ${e.message}"))
                    }
                }
            } catch (e: Exception) {
                _connectionEvents.emit(ConnectionEvent.Error("Server: ${e.message}"))
            }
        }
    }
    
    private fun handleIncomingConnection(socket: Socket, onMessage: suspend (DFLPMessage, String) -> Unit) = scope.launch {
        val addr = socket.inetAddress.hostAddress ?: "unknown"
        try {
            val channel = transport.handshakeAsResponder(socket)
            if (channel == null) {
                _connectionEvents.emit(ConnectionEvent.HandshakeFailed(addr, "PQC failed"))
                socket.close()
                return@launch
            }
            
            activeChannels[channel.peerId] = channel
            _connectionEvents.emit(ConnectionEvent.PeerConnected(channel.peerId, addr))
            
            while (channel.isConnected) {
                val data = channel.receive() ?: break
                deserializeMessage(data)?.let { msg ->
                    _messageEvents.emit(MessageEvent.Received(channel.peerId, msg))
                    onMessage(msg, channel.peerId)
                }
            }
        } finally {
            socket.close()
        }
    }
    
    suspend fun connectToPeer(address: String, peerPort: Int = port): String? = withContext(Dispatchers.IO) {
        try {
            val socket = Socket(address, peerPort)
            val channel = transport.handshakeAsInitiator(socket)
            
            if (channel == null) {
                _connectionEvents.emit(ConnectionEvent.HandshakeFailed(address, "PQC failed"))
                socket.close()
                return@withContext null
            }
            
            activeChannels[channel.peerId] = channel
            _connectionEvents.emit(ConnectionEvent.PeerConnected(channel.peerId, address))
            
            scope.launch {
                while (channel.isConnected) {
                    channel.receive()?.let { data ->
                        deserializeMessage(data)?.let { msg ->
                            _messageEvents.emit(MessageEvent.Received(channel.peerId, msg))
                        }
                    } ?: break
                }
                activeChannels.remove(channel.peerId)
                _connectionEvents.emit(ConnectionEvent.PeerDisconnected(channel.peerId))
            }
            
            channel.peerId
        } catch (e: Exception) {
            _connectionEvents.emit(ConnectionEvent.Error("Connect: ${e.message}"))
            null
        }
    }
    
    suspend fun sendToPeer(peerId: String, message: DFLPMessage): Boolean {
        val channel = activeChannels[peerId] ?: return false
        return channel.send(serializeMessage(message)).also { if (it) _messageEvents.emit(MessageEvent.Sent(peerId, message)) }
    }
    
    suspend fun broadcast(message: DFLPMessage): Int {
        val data = serializeMessage(message)
        return activeChannels.values.count { it.send(data) }
    }
    
    suspend fun sendRawToPeer(peerId: String, data: ByteArray): Boolean = activeChannels[peerId]?.send(data) ?: false
    
    fun getChannel(peerId: String) = activeChannels[peerId]
    fun getConnectedPeers(): List<String> = activeChannels.keys().toList()
    fun disconnectPeer(peerId: String) { activeChannels.remove(peerId)?.close() }
    
    fun stop() {
        isListening = false
        serverSocket?.close()
        activeChannels.values.forEach { it.close() }
        activeChannels.clear()
        scope.cancel()
    }
    
    private fun serializeMessage(msg: DFLPMessage): ByteArray = 
        ByteArrayOutputStream().also { ObjectOutputStream(it).writeObject(msg) }.toByteArray()
    
    private fun deserializeMessage(data: ByteArray): DFLPMessage? = 
        try { ObjectInputStream(ByteArrayInputStream(data)).readObject() as DFLPMessage } catch (e: Exception) { null }
}

data class DFLPMessage(
    val senderPeerId: String,
    val timestamp: Long,
    val modelHash: String,
    val noiseLevel: Double,
    val updateId: String,
    val weightDeltas: ByteArray,
    val messageType: DFLPMessageType = DFLPMessageType.MODEL_UPDATE
) : Serializable {
    override fun equals(other: Any?) = (other as? DFLPMessage)?.let { updateId == it.updateId } ?: false
    override fun hashCode() = updateId.hashCode()
}

enum class DFLPMessageType { MODEL_UPDATE, GRADIENT_SHARE, PEER_DISCOVERY, DHT_PUT, DHT_GET, DHT_RESPONSE, CHAT, KEY_RECOVERY_REQUEST, KEY_RECOVERY_RESPONSE }

sealed class ConnectionEvent {
    data class ServerStarted(val port: Int) : ConnectionEvent()
    data class PeerConnected(val peerId: String, val address: String) : ConnectionEvent()
    data class PeerDisconnected(val peerId: String) : ConnectionEvent()
    data class HandshakeFailed(val address: String, val reason: String) : ConnectionEvent()
    data class Error(val message: String) : ConnectionEvent()
}

sealed class MessageEvent {
    data class Received(val peerId: String, val message: DFLPMessage) : MessageEvent()
    data class Sent(val peerId: String, val message: DFLPMessage) : MessageEvent()
    data class Error(val peerId: String, val reason: String) : MessageEvent()
}
