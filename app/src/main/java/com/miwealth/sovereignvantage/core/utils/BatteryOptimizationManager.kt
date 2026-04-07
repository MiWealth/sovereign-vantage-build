package com.miwealth.sovereignvantage.core.utils

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * BUILD #412: Battery Optimization Manager
 * 
 * Ensures Sovereign Vantage can continue trading when screen is off.
 * 
 * Android Battery Restrictions:
 * - Doze Mode: Restricts network/CPU when screen off for 5-10 minutes
 * - App Standby: Defers background operations for idle apps
 * - Battery Saver: Aggressively limits background activity
 * 
 * Solutions:
 * 1. REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: Exempts app from Doze Mode
 * 2. WAKE_LOCK: Keeps CPU awake during critical trading cycles
 * 3. Foreground Service: Already implemented (TradingService)
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */
object BatteryOptimizationManager {
    
    private const val TAG = "BatteryOptimization"
    
    /**
     * Check if app is exempt from battery optimization restrictions.
     * 
     * If FALSE, app may be killed when screen is off for extended periods.
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true // No Doze Mode on Android < 6.0
        }
        
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }
    
    /**
     * Get intent to open battery optimization settings for this app.
     * User must manually exempt the app.
     */
    @SuppressLint("BatteryLife") // We need this for 24/7 trading
    fun getRequestBatteryOptimizationIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            // Fallback for older Android - just open battery settings
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        }
    }
    
    /**
     * Check if app has WAKE_LOCK permission.
     * Required to keep CPU awake during trading cycles.
     */
    fun hasWakeLockPermission(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.WAKE_LOCK) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if trading service is running in foreground.
     */
    fun isTradingServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val runningServices = manager?.getRunningServices(Int.MAX_VALUE) ?: return false
        
        return runningServices.any { 
            it.service.className == "com.miwealth.sovereignvantage.service.TradingService" &&
            it.foreground
        }
    }
    
    /**
     * Get battery optimization status message for UI.
     */
    fun getBatteryOptimizationStatus(context: Context): BatteryStatus {
        val optimizationDisabled = isBatteryOptimizationDisabled(context)
        val hasWakeLock = hasWakeLockPermission(context)
        val serviceRunning = isTradingServiceRunning(context)
        
        return when {
            optimizationDisabled && serviceRunning -> BatteryStatus.OPTIMAL
            optimizationDisabled && !serviceRunning -> BatteryStatus.GOOD_BUT_SERVICE_STOPPED
            !optimizationDisabled && serviceRunning -> BatteryStatus.AT_RISK
            else -> BatteryStatus.NOT_CONFIGURED
        }
    }
    
    /**
     * Check if device is in battery saver mode.
     */
    fun isInPowerSaveMode(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isPowerSaveMode ?: false
    }
    
    /**
     * Log battery optimization status for debugging.
     */
    fun logBatteryStatus(context: Context) {
        val status = getBatteryOptimizationStatus(context)
        val inPowerSave = isInPowerSaveMode(context)
        
        SystemLogger.i(TAG, "🔋 Battery Optimization Status:")
        SystemLogger.i(TAG, "   Overall: ${status.name}")
        SystemLogger.i(TAG, "   Battery Opt Disabled: ${isBatteryOptimizationDisabled(context)}")
        SystemLogger.i(TAG, "   Has WAKE_LOCK: ${hasWakeLockPermission(context)}")
        SystemLogger.i(TAG, "   Service Running: ${isTradingServiceRunning(context)}")
        SystemLogger.i(TAG, "   Power Save Mode: $inPowerSave")
        
        if (status != BatteryStatus.OPTIMAL) {
            SystemLogger.w(TAG, "⚠️ BUILD #412: ${status.message}")
        }
    }
}

/**
 * Battery optimization status.
 */
enum class BatteryStatus(val message: String) {
    OPTIMAL(
        "✅ App optimized for 24/7 trading (battery optimization disabled + service running)"
    ),
    GOOD_BUT_SERVICE_STOPPED(
        "⚠️ Battery optimization disabled but trading service is not running"
    ),
    AT_RISK(
        "⚠️ Trading may pause when screen off! Battery optimization is enabled. " +
        "Please disable battery optimization in Settings."
    ),
    NOT_CONFIGURED(
        "❌ Trading service not running and battery optimization enabled. " +
        "App may be killed when screen is off!"
    )
}
