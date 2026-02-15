package com.miwealth.sovereignvantage.core

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * BIOMETRIC AUTHENTICATION
 * 
 * Sovereign Vantage: Arthur Edition V5.5.97
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * MEMORY SAFETY WARNING (N2 — Memory Audit V5.5.95):
 * This class holds a direct reference to FragmentActivity. This is REQUIRED by
 * the BiometricPrompt API, but creates a risk of Activity leak if this instance
 * is retained beyond the Activity lifecycle.
 * 
 * RULES:
 * 1. NEVER cache BiometricAuth in a ViewModel, Singleton, or Hilt @Singleton
 * 2. NEVER store in a companion object or static field
 * 3. Create a FRESH instance per authentication attempt
 * 4. Let it be garbage collected after the callback completes
 * 
 * Safe usage:
 * ```kotlin
 * // In Activity/Fragment — create, use, discard
 * BiometricAuth(this).authenticate(
 *     onSuccess = { navigateToVault() },
 *     onError = { msg -> showError(msg) }
 * )
 * ```
 * 
 * @param activity Must be the CURRENT FragmentActivity — never a stale reference
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */
class BiometricAuth(private val activity: FragmentActivity) {

    fun authenticate(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)
        
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Sovereign Vantage Secure Login")
            .setSubtitle("Authenticate to access your Vault")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }
}
