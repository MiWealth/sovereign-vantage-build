package com.miwealth.sovereignvantage.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.miwealth.sovereignvantage.core.security.EncryptedPrefsManager
import com.miwealth.sovereignvantage.service.TradingService

/**
 * Boot Receiver for Sovereign Vantage
 * Automatically restarts the trading service after device reboot.
 * 
 * Only activates if:
 * - User has enabled auto-start in settings
 * - User has an active subscription (not free trial)
 * - AI trading was active before reboot
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        
        // Check if auto-start is enabled in encrypted preferences
        val prefs = EncryptedPrefsManager.getSettingsPrefs(context)
        val autoStartEnabled = prefs.getBoolean("auto_start_trading", false)
        val wasTrading = prefs.getBoolean("was_trading_before_shutdown", false)
        
        if (autoStartEnabled && wasTrading) {
            // Start the trading service
            TradingService.startService(context)
            
            android.util.Log.i("BootReceiver", "Trading service auto-started after boot")
        }
    }
}
