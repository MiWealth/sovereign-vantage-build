package com.miwealth.sovereignvantage.core.wallet

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class KeyStoreManager {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val KEY_ALIAS = "SovereignMasterKey"

    init {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKey()
        }
    }

    private fun generateKey() {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true) // Requires Biometric Auth to use key
            .build()
        
        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }

    fun retrieveKey(): SecretKey {
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }
}
