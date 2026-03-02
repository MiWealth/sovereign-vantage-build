/**
 * POWER-AWARE MANAGER
 *
 * Sovereign Vantage V5.17.0 "Arthur Edition"
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 *
 * Centralized battery/power management that all components observe.
 * Adapts trading frequency, polling intervals, AI computation depth,
 * and non-critical features based on battery state.
 *
 * Design principles:
 * - Trading safety NEVER degrades — stops, risk limits, kill switch always active
 * - Only non-critical work is throttled (sentiment polling, DHT gossip, analytics)
 * - Charging detection instantly restores full performance
 * - Thermal throttling prevents device overheating during heavy compute
 *
 * Integration points:
 * - TradingService: adjusts OODA loop interval
 * - SentimentEngine: adjusts polling frequency
 * - EconomicCalendarService: already uses WorkManager battery constraints
 * - DFLPNode: reduces DHT gossip frequency
 * - FinancialNLUEngine: reduces Monte Carlo simulation count
 * - ScalpingEngine: may pause in CRITICAL mode (with user consent)
 *
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */

package com.miwealth.sovereignvantage.core.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

// =============================================================================
// POWER PROFILE
// =============================================================================

/**
 * Power profiles define how aggressively SV conserves battery.
 *
 * FULL_PERFORMANCE: Charging or battery >70% — no restrictions
 * BALANCED:         Battery 30-70% — reduce non-critical polling
 * LOW_POWER:        Battery 15-30% — significant throttling
 * CRITICAL:         Battery <15% — essential trading only
 */
enum class PowerProfile {
    FULL_PERFORMANCE,
    BALANCED,
    LOW_POWER,
    CRITICAL
}

/**
 * Configuration parameters for each power profile.
 * Components read these to adjust their behaviour.
 */
data class PowerConfig(
    val profile: PowerProfile,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val batteryTemperature: Float,

    // ── Interval multipliers (1.0 = normal) ──
    /** Multiplier for OODA loop interval. Trading safety unaffected. */
    val oodaIntervalMultiplier: Double,
    /** Multiplier for sentiment/news polling interval */
    val sentimentPollingMultiplier: Double,
    /** Multiplier for DHT gossip interval */
    val dhtGossipMultiplier: Double,
    /** Multiplier for analytics/portfolio recalculation interval */
    val analyticsIntervalMultiplier: Double,

    // ── Feature flags ──
    /** Whether Monte Carlo simulations should run */
    val monteCarloEnabled: Boolean,
    /** Maximum concurrent Monte Carlo simulations (0 = disabled) */
    val maxMonteCarloSimulations: Int,
    /** Whether NLU news analysis should run */
    val nluAnalysisEnabled: Boolean,
    /** Whether DHT federated learning should participate */
    val dhtFederatedLearningEnabled: Boolean,
    /** Whether non-essential UI animations should run */
    val uiAnimationsEnabled: Boolean,
    /** Whether background price caching for non-active pairs should run */
    val backgroundPriceCachingEnabled: Boolean,
    /** Whether scalping engine is allowed to operate */
    val scalpingAllowed: Boolean,

    // ── Thermal ──
    /** Whether thermal throttling is active */
    val isThermalThrottling: Boolean
) {
    companion object {
        fun forProfile(
            profile: PowerProfile,
            batteryPercent: Int,
            isCharging: Boolean,
            batteryTemperature: Float = 25f
        ): PowerConfig {
            val thermal = batteryTemperature > 42f  // Android thermal threshold

            return when {
                // Charging always gets full performance (unless overheating)
                isCharging && !thermal -> PowerConfig(
                    profile = PowerProfile.FULL_PERFORMANCE,
                    batteryPercent = batteryPercent,
                    isCharging = true,
                    batteryTemperature = batteryTemperature,
                    oodaIntervalMultiplier = 1.0,
                    sentimentPollingMultiplier = 1.0,
                    dhtGossipMultiplier = 1.0,
                    analyticsIntervalMultiplier = 1.0,
                    monteCarloEnabled = true,
                    maxMonteCarloSimulations = 10_000,
                    nluAnalysisEnabled = true,
                    dhtFederatedLearningEnabled = true,
                    uiAnimationsEnabled = true,
                    backgroundPriceCachingEnabled = true,
                    scalpingAllowed = true,
                    isThermalThrottling = false
                )

                profile == PowerProfile.FULL_PERFORMANCE -> PowerConfig(
                    profile = profile,
                    batteryPercent = batteryPercent,
                    isCharging = isCharging,
                    batteryTemperature = batteryTemperature,
                    oodaIntervalMultiplier = 1.0,
                    sentimentPollingMultiplier = 1.0,
                    dhtGossipMultiplier = 1.0,
                    analyticsIntervalMultiplier = 1.0,
                    monteCarloEnabled = !thermal,
                    maxMonteCarloSimulations = if (thermal) 2_000 else 10_000,
                    nluAnalysisEnabled = true,
                    dhtFederatedLearningEnabled = true,
                    uiAnimationsEnabled = !thermal,
                    backgroundPriceCachingEnabled = true,
                    scalpingAllowed = true,
                    isThermalThrottling = thermal
                )

                profile == PowerProfile.BALANCED -> PowerConfig(
                    profile = profile,
                    batteryPercent = batteryPercent,
                    isCharging = isCharging,
                    batteryTemperature = batteryTemperature,
                    oodaIntervalMultiplier = 1.0,     // Trading unchanged
                    sentimentPollingMultiplier = 2.0,  // Poll half as often
                    dhtGossipMultiplier = 1.5,
                    analyticsIntervalMultiplier = 2.0,
                    monteCarloEnabled = !thermal,
                    maxMonteCarloSimulations = if (thermal) 1_000 else 5_000,
                    nluAnalysisEnabled = true,
                    dhtFederatedLearningEnabled = true,
                    uiAnimationsEnabled = !thermal,
                    backgroundPriceCachingEnabled = true,
                    scalpingAllowed = true,
                    isThermalThrottling = thermal
                )

                profile == PowerProfile.LOW_POWER -> PowerConfig(
                    profile = profile,
                    batteryPercent = batteryPercent,
                    isCharging = isCharging,
                    batteryTemperature = batteryTemperature,
                    oodaIntervalMultiplier = 1.5,     // Slightly slower loop
                    sentimentPollingMultiplier = 4.0,  // Poll 4x less often
                    dhtGossipMultiplier = 3.0,
                    analyticsIntervalMultiplier = 5.0,
                    monteCarloEnabled = false,         // Disable heavy compute
                    maxMonteCarloSimulations = 0,
                    nluAnalysisEnabled = false,         // Disable NLU
                    dhtFederatedLearningEnabled = false, // Save network + CPU
                    uiAnimationsEnabled = false,
                    backgroundPriceCachingEnabled = false,
                    scalpingAllowed = true,             // Still allowed but user can disable
                    isThermalThrottling = thermal
                )

                else /* CRITICAL */ -> PowerConfig(
                    profile = PowerProfile.CRITICAL,
                    batteryPercent = batteryPercent,
                    isCharging = isCharging,
                    batteryTemperature = batteryTemperature,
                    oodaIntervalMultiplier = 2.0,     // 2x slower — still safe
                    sentimentPollingMultiplier = 10.0, // Minimal polling
                    dhtGossipMultiplier = 10.0,
                    analyticsIntervalMultiplier = 10.0,
                    monteCarloEnabled = false,
                    maxMonteCarloSimulations = 0,
                    nluAnalysisEnabled = false,
                    dhtFederatedLearningEnabled = false,
                    uiAnimationsEnabled = false,
                    backgroundPriceCachingEnabled = false,
                    scalpingAllowed = false,           // Pause scalping to save battery
                    isThermalThrottling = thermal || true  // Always conservative
                )
            }
        }
    }
}

// =============================================================================
// LISTENER INTERFACE
// =============================================================================

/**
 * Callback for components that need immediate notification of power changes.
 * Prefer observing [PowerAwareManager.powerConfigFlow] via coroutines where possible.
 */
interface PowerChangeListener {
    fun onPowerConfigChanged(config: PowerConfig)
}

// =============================================================================
// POWER-AWARE MANAGER
// =============================================================================

/**
 * Singleton power manager. Register once from Application or TradingService,
 * then observe from any component.
 *
 * Usage:
 * ```kotlin
 * // In TradingService onCreate:
 * PowerAwareManager.getInstance(context).register()
 *
 * // In any component:
 * val power = PowerAwareManager.getInstance(context)
 * power.powerConfigFlow.collect { config ->
 *     pollingInterval = baseInterval * config.sentimentPollingMultiplier.toLong()
 * }
 *
 * // Or check current state:
 * if (power.currentConfig.monteCarloEnabled) { runSimulation() }
 * ```
 */
class PowerAwareManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PowerAwareManager"

        @Volatile
        private var instance: PowerAwareManager? = null

        fun getInstance(context: Context): PowerAwareManager {
            return instance ?: synchronized(this) {
                instance ?: PowerAwareManager(context.applicationContext).also { instance = it }
            }
        }

        // Battery thresholds
        private const val THRESHOLD_FULL = 70
        private const val THRESHOLD_BALANCED = 30
        private const val THRESHOLD_LOW = 15
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val listeners = CopyOnWriteArrayList<PowerChangeListener>()

    // ── State ──
    private val _powerConfigFlow = MutableStateFlow(
        PowerConfig.forProfile(PowerProfile.FULL_PERFORMANCE, 100, false)
    )
    val powerConfigFlow: StateFlow<PowerConfig> = _powerConfigFlow.asStateFlow()

    /** Current power config snapshot — safe to read from any thread */
    val currentConfig: PowerConfig get() = _powerConfigFlow.value

    private var registered = false

    // ── Battery receiver ──
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED,
                Intent.ACTION_BATTERY_LOW,
                Intent.ACTION_BATTERY_OKAY,
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED -> {
                    updateFromIntent(intent)
                }
            }
        }
    }

    // ── Lifecycle ──

    /**
     * Register battery monitoring. Call from TradingService.onCreate() or Application.
     * Safe to call multiple times — only registers once.
     */
    fun register() {
        if (registered) return
        registered = true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        // Get initial state from sticky broadcast
        val sticky = context.registerReceiver(batteryReceiver, filter)
        sticky?.let { updateFromIntent(it) }

        Log.i(TAG, "Power monitoring registered. Initial: ${currentConfig.profile}")
    }

    /**
     * Unregister battery monitoring. Call from TradingService.onDestroy().
     */
    fun unregister() {
        if (!registered) return
        registered = false
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
            // Already unregistered
        }
        Log.i(TAG, "Power monitoring unregistered")
    }

    // ── Listeners (for non-coroutine consumers) ──

    fun addListener(listener: PowerChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: PowerChangeListener) {
        listeners.remove(listener)
    }

    // ── Core logic ──

    private fun updateFromIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (scale > 0) (level * 100) / scale else 50

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        // Temperature in tenths of degrees C
        val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250)
        val temperature = tempRaw / 10f

        val profile = when {
            isCharging -> PowerProfile.FULL_PERFORMANCE
            percent >= THRESHOLD_FULL -> PowerProfile.FULL_PERFORMANCE
            percent >= THRESHOLD_BALANCED -> PowerProfile.BALANCED
            percent >= THRESHOLD_LOW -> PowerProfile.LOW_POWER
            else -> PowerProfile.CRITICAL
        }

        val newConfig = PowerConfig.forProfile(profile, percent, isCharging, temperature)
        val oldConfig = _powerConfigFlow.value

        _powerConfigFlow.value = newConfig

        // Notify on profile change
        if (oldConfig.profile != newConfig.profile) {
            Log.i(TAG, "Power profile changed: ${oldConfig.profile} → ${newConfig.profile} " +
                    "(battery=$percent%, charging=$isCharging, temp=${temperature}°C)")

            scope.launch {
                listeners.forEach { it.onPowerConfigChanged(newConfig) }
            }
        }
    }

    // ── Convenience queries ──

    /** Whether heavy computation (Monte Carlo, NLU, DFLP) should run */
    val isHeavyComputeAllowed: Boolean get() = currentConfig.monteCarloEnabled

    /** Whether the device is charging */
    val isCharging: Boolean get() = currentConfig.isCharging

    /** Whether we're in a critical/low-power situation */
    val isLowPower: Boolean get() = currentConfig.profile == PowerProfile.LOW_POWER ||
            currentConfig.profile == PowerProfile.CRITICAL

    /** Whether device is thermally throttling */
    val isThermal: Boolean get() = currentConfig.isThermalThrottling

    /** Get adjusted interval for a given base interval in milliseconds */
    fun adjustedOodaInterval(baseMs: Long): Long =
        (baseMs * currentConfig.oodaIntervalMultiplier).toLong()

    fun adjustedSentimentInterval(baseMs: Long): Long =
        (baseMs * currentConfig.sentimentPollingMultiplier).toLong()

    fun adjustedDhtInterval(baseMs: Long): Long =
        (baseMs * currentConfig.dhtGossipMultiplier).toLong()

    fun adjustedAnalyticsInterval(baseMs: Long): Long =
        (baseMs * currentConfig.analyticsIntervalMultiplier).toLong()

    /**
     * Human-readable status for UI display.
     */
    fun getStatusString(): String {
        val c = currentConfig
        return buildString {
            append("${c.profile.name} | ${c.batteryPercent}%")
            if (c.isCharging) append(" ⚡")
            if (c.isThermalThrottling) append(" 🌡️")
        }
    }
}
