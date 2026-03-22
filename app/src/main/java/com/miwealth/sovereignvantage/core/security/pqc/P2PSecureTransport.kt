package com.miwealth.sovereignvantage.core.security.pqc

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * P2P Secure Transport with Real PQC
 * 
 * Handles the cryptographic layer for P2P communication:
 * - Kyber-1024 key encapsulation for shared secret establishment
 * - Dilithium-5 mutual authentication
 * - AES-256-GCM encrypted channel
 * 
 * Protocol:
 * 1. Initiator sends: Kyber public key + Dilithium public key + signature
 * 2. Responder verifies, sends: Kyber ciphertext + Dilithium public key + signature
 * 3. Both derive shared secret from Kyber KEM
 * 4. All subsequent data encrypted with AES-256-GCM using shared secret
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 */
class P2PSecureTransport(
    private val kyberKeys: KyberKeyPair,
    private val dilithiumKeys: DilithiumKeyPair,
    private val securityLevel: Int = 5
) {
    private val kyberKem = KyberKEM(securityLevel)
    private val dilithiumDsa = DilithiumDSA(securityLevel)
    
    companion object {
        private const val PROTOCOL_VERSION: Byte = 1
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_NONCE_LENGTH = 12
        private const val HANDSHAKE_TIMEOUT_MS = 30_000
    }
    
    /**
     * Perform PQC handshake as the connection initiator
     * Returns SecureChannel on success, null on failure
     */
    fun handshakeAsInitiator(socket: Socket): SecureChannel? {
        return try {
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())
            
            // Step 1: Send our Kyber + Dilithium public keys with signature
            val initPayload = kyberKeys.publicKey + dilithiumKeys.publicKey
            val initSignature = dilithiumDsa.sign(initPayload, dilithiumKeys.privateKey)
            
            output.writeByte(PROTOCOL_VERSION.toInt())
            output.writeInt(kyberKeys.publicKey.size)
            output.write(kyberKeys.publicKey)
            output.writeInt(dilithiumKeys.publicKey.size)
            output.write(dilithiumKeys.publicKey)
            output.writeInt(initSignature.size)
            output.write(initSignature)
            output.flush()
            
            // Step 2: Receive responder's Kyber ciphertext + Dilithium public key + signature
            val version = input.readByte()
            if (version != PROTOCOL_VERSION) return null
            
            val ciphertextSize = input.readInt()
            val ciphertext = ByteArray(ciphertextSize).also { input.readFully(it) }
            
            val responderDilithiumPubSize = input.readInt()
            val responderDilithiumPub = ByteArray(responderDilithiumPubSize).also { input.readFully(it) }
            
            val responderSigSize = input.readInt()
            val responderSig = ByteArray(responderSigSize).also { input.readFully(it) }
            
            // Verify responder's signature
            val responderPayload = ciphertext + responderDilithiumPub
            if (!dilithiumDsa.verify(responderPayload, responderSig, responderDilithiumPub)) {
                return null
            }
            
            // Decapsulate to get shared secret
            val sharedSecret = kyberKem.decapsulate(ciphertext, kyberKeys.privateKey)
            
            // Derive peer ID from their Dilithium public key
            val peerId = DilithiumKeyPair(responderDilithiumPub, ByteArray(0), securityLevel).identityHex()
            
            socket.soTimeout = 0 // Reset timeout for normal operation
            SecureChannel(socket, sharedSecret, peerId)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Perform PQC handshake as the connection responder
     * Returns SecureChannel on success, null on failure
     */
    fun handshakeAsResponder(socket: Socket): SecureChannel? {
        return try {
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())
            
            // Step 1: Receive initiator's Kyber + Dilithium public keys with signature
            val version = input.readByte()
            if (version != PROTOCOL_VERSION) return null
            
            val initiatorKyberPubSize = input.readInt()
            val initiatorKyberPub = ByteArray(initiatorKyberPubSize).also { input.readFully(it) }
            
            val initiatorDilithiumPubSize = input.readInt()
            val initiatorDilithiumPub = ByteArray(initiatorDilithiumPubSize).also { input.readFully(it) }
            
            val initiatorSigSize = input.readInt()
            val initiatorSig = ByteArray(initiatorSigSize).also { input.readFully(it) }
            
            // Verify initiator's signature
            val initiatorPayload = initiatorKyberPub + initiatorDilithiumPub
            if (!dilithiumDsa.verify(initiatorPayload, initiatorSig, initiatorDilithiumPub)) {
                return null
            }
            
            // Step 2: Encapsulate with initiator's Kyber public key
            val encapResult = kyberKem.encapsulate(initiatorKyberPub)
            
            // Send our response: ciphertext + Dilithium public key + signature
            val respPayload = encapResult.ciphertext + dilithiumKeys.publicKey
            val respSignature = dilithiumDsa.sign(respPayload, dilithiumKeys.privateKey)
            
            output.writeByte(PROTOCOL_VERSION.toInt())
            output.writeInt(encapResult.ciphertext.size)
            output.write(encapResult.ciphertext)
            output.writeInt(dilithiumKeys.publicKey.size)
            output.write(dilithiumKeys.publicKey)
            output.writeInt(respSignature.size)
            output.write(respSignature)
            output.flush()
            
            // Derive peer ID from their Dilithium public key
            val peerId = DilithiumKeyPair(initiatorDilithiumPub, ByteArray(0), securityLevel).identityHex()
            
            socket.soTimeout = 0 // Reset timeout for normal operation
            SecureChannel(socket, encapResult.sharedSecret, peerId)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Secure communication channel with AES-256-GCM encryption
     */
    class SecureChannel(
        private val socket: Socket,
        private val sharedSecret: ByteArray,
        val peerId: String
    ) {
        private val input = DataInputStream(socket.getInputStream())
        private val output = DataOutputStream(socket.getOutputStream())
        private val secretKey = SecretKeySpec(sharedSecret, "AES")
        private var nonceCounter = 0L
        
        val isConnected: Boolean get() = socket.isConnected && !socket.isClosed
        
        /**
         * Send encrypted data to peer
         */
        fun send(data: ByteArray): Boolean {
            return try {
                synchronized(output) {
                    val nonce = generateNonce()
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, nonce))
                    val encrypted = cipher.doFinal(data)
                    
                    output.writeInt(nonce.size + encrypted.size)
                    output.write(nonce)
                    output.write(encrypted)
                    output.flush()
                    true
                }
            } catch (e: Exception) {
                false
            }
        }
        
        /**
         * Receive and decrypt data from peer
         * Returns null on connection close or error
         */
        fun receive(): ByteArray? {
            return try {
                synchronized(input) {
                    val totalSize = input.readInt()
                    if (totalSize <= GCM_NONCE_LENGTH) return null
                    
                    val nonce = ByteArray(GCM_NONCE_LENGTH).also { input.readFully(it) }
                    val encrypted = ByteArray(totalSize - GCM_NONCE_LENGTH).also { input.readFully(it) }
                    
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, nonce))
                    cipher.doFinal(encrypted)
                }
            } catch (e: Exception) {
                null
            }
        }
        
        /**
         * Close the secure channel
         */
        fun close() {
            try {
                SideChannelDefense.secureWipe(sharedSecret)
                socket.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
        }
        
        private fun generateNonce(): ByteArray {
            val nonce = ByteArray(GCM_NONCE_LENGTH)
            val counter = nonceCounter++
            for (i in 0 until 8) {
                nonce[i] = (counter shr (8 * (7 - i))).toByte()
            }
            // Add random bytes to remaining positions
            java.security.SecureRandom().nextBytes(nonce.copyOfRange(8, GCM_NONCE_LENGTH))
            return nonce
        }
    }
}
