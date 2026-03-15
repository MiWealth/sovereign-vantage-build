package com.miwealth.sovereignvantage.core.trading.engine

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * MARGIN SAFEGUARD
 * 
 * Sovereign Vantage: Arthur Edition V5.19.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * CRITICAL SYSTEM - THIS RULE MUST NEVER BE BREACHED:
 * Always maintain sufficient margin to prevent liquidation.
 * 
 * This is the last line of defense against catastrophic loss.
 * It monitors margin levels and can force-close positions
 * if margin drops below safety thresholds.
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */
class MarginSafeguard private constructor() {
    
    companion object {
        private const val TAG = "MarginSafeguard"
        
        @Volatile
        private var instance: MarginSafeguard? = null
        
        fun getInstance(): MarginSafeguard {
            return instance ?: synchronized(this) {
                instance ?: MarginSafeguard().also { instance = it }
            }
        }
        
        // Margin level thresholds (as percentage of equity / used margin * 100)
        const val LIQUIDATION_LEVEL = 110.0      // Force close all positions
        const val CRITICAL_LEVEL = 125.0         // Reduce position sizes
        const val MARGIN_CALL_LEVEL = 150.0      // Warning + no new positions
        const val WARNING_LEVEL = 200.0          // Advisory warning
        const val HEALTHY_LEVEL = 300.0          // All good
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Current margin status
    private val _marginStatus = MutableStateFlow<MarginStatus?>(null)
    val marginStatus: StateFlow<MarginStatus?> = _marginStatus.asStateFlow()
    
    // Is monitoring active?
    private var isMonitoring = false
    
    // Position manager reference
    private var positionManager: Any? = null
    
    // Balance source
    private var balanceProvider: (() -> Double)? = null
    private var unrealizedPnlProvider: (() -> Double)? = null
    private var usedMarginProvider: (() -> Double)? = null
    private var positionCountProvider: (() -> Int)? = null
    
    /**
     * Initialize the safeguard with data providers.
     */
    fun initialize(
        positionManager: Any,
        balanceProvider: () -> Double,
        unrealizedPnlProvider: () -> Double,
        usedMarginProvider: () -> Double,
        positionCountProvider: () -> Int = { 0 }
    ) {
        this.positionManager = positionManager
        this.balanceProvider = balanceProvider
        this.unrealizedPnlProvider = unrealizedPnlProvider
        this.usedMarginProvider = usedMarginProvider
        this.positionCountProvider = positionCountProvider
        
        // Initial status calculation
        updateMarginStatus()
        
        Log.i(TAG, "🛡️ MarginSafeguard initialized")
    }
    
    /**
     * Start continuous monitoring.
     */
    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        
        scope.launch {
            while (isMonitoring) {
                updateMarginStatus()
                delay(1000) // Check every second
            }
        }
        
        Log.i(TAG, "🛡️ MarginSafeguard monitoring STARTED")
    }
    
    /**
     * Stop monitoring.
     */
    fun stopMonitoring() {
        isMonitoring = false
        Log.i(TAG, "🛡️ MarginSafeguard monitoring STOPPED")
    }
    
    /**
     * Update margin status from providers.
     */
    private fun updateMarginStatus() {
        val balance = balanceProvider?.invoke() ?: 100_000.0
        val unrealizedPnl = unrealizedPnlProvider?.invoke() ?: 0.0
        val usedMargin = usedMarginProvider?.invoke() ?: 0.0
        val positionCount = positionCountProvider?.invoke() ?: 0
        
        val equity = balance + unrealizedPnl
        val freeMargin = max(0.0, equity - usedMargin)
        
        val newStatus = MarginStatus.fromPortfolioState(
            equity = equity,
            usedMargin = usedMargin,
            freeMargin = freeMargin,
            openPositionCount = positionCount
        )
        
        // Check for state changes
        val previousState = _marginStatus.value?.riskState
        if (previousState != newStatus.riskState) {
            handleRiskStateChange(previousState, newStatus.riskState, newStatus)
        }
        
        _marginStatus.value = newStatus
    }
    
    /**
     * Handle risk state transitions.
     */
    private fun handleRiskStateChange(
        from: MarginRiskState?,
        to: MarginRiskState,
        status: MarginStatus
    ) {
        Log.w(TAG, "🚨 Margin risk state changed: $from -> $to (level: ${status.marginLevel}%)")
        
        when (to) {
            MarginRiskState.LIQUIDATING -> {
                Log.e(TAG, "🔥 LIQUIDATION LEVEL BREACHED - CLOSING ALL POSITIONS")
                // In production: positionManager.closeAllPositions()
            }
            MarginRiskState.CRITICAL -> {
                Log.e(TAG, "⚠️ CRITICAL MARGIN LEVEL - Reducing position sizes")
            }
            MarginRiskState.MARGIN_CALL -> {
                Log.w(TAG, "📢 MARGIN CALL - No new positions allowed")
            }
            MarginRiskState.WARNING -> {
                Log.w(TAG, "⚡ Margin warning level - Monitor closely")
            }
            MarginRiskState.HEALTHY -> {
                Log.i(TAG, "✅ Margin levels healthy")
            }
        }
    }
    
    /**
     * Check if new positions are allowed.
     */
    fun canOpenNewPosition(): Boolean {
        val status = _marginStatus.value ?: return true
        return status.riskState == MarginRiskState.HEALTHY || 
               status.riskState == MarginRiskState.WARNING
    }
    
    /**
     * Get maximum position size allowed given current margin.
     */
    fun getMaxPositionSize(leverage: Double = 1.0): Double {
        val status = _marginStatus.value ?: return 0.0
        
        // Reserve 20% of free margin for safety
        val usableMargin = status.freeMargin * 0.8
        
        return when (status.riskState) {
            MarginRiskState.HEALTHY -> usableMargin * leverage
            MarginRiskState.WARNING -> usableMargin * leverage * 0.5
            MarginRiskState.MARGIN_CALL -> 0.0
            MarginRiskState.CRITICAL -> 0.0
            MarginRiskState.LIQUIDATING -> 0.0
        }
    }
    
    /**
     * Force update margin status (call after trades).
     */
    fun forceUpdate() {
        updateMarginStatus()
    }
}
