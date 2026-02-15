package com.miwealth.sovereignvantage.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.miwealth.sovereignvantage.MainActivity
import com.miwealth.sovereignvantage.R
import com.miwealth.sovereignvantage.core.trading.engine.PositionEvent
import com.miwealth.sovereignvantage.core.trading.engine.RiskEvent
import com.miwealth.sovereignvantage.core.trading.scalping.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SOVEREIGN VANTAGE V5.5.17 "ARTHUR EDITION"
 * NOTIFICATION SERVICE
 * 
 * Push notifications for trading events including:
 * - Trade executions (swing + scalping)
 * - STAHL level advances
 * - Risk warnings
 * - Price alerts
 * - AI Board signals
 * - Scalping signals and results
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */

@Singleton
class NotificationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        // Channel IDs
        const val CHANNEL_TRADES = "sovereign_vantage_trades"
        const val CHANNEL_ALERTS = "sovereign_vantage_alerts"
        const val CHANNEL_SIGNALS = "sovereign_vantage_signals"
        const val CHANNEL_SYSTEM = "sovereign_vantage_system"
        
        // Notification IDs
        const val NOTIFICATION_TRADE_BASE = 1000
        const val NOTIFICATION_ALERT_BASE = 2000
        const val NOTIFICATION_SIGNAL_BASE = 3000
        const val NOTIFICATION_SYSTEM_BASE = 4000
        
        // Foreground service notification
        const val NOTIFICATION_FOREGROUND = 9999
    }
    
    private var notificationIdCounter = 0
    private var isInitialized = false
    
    /**
     * Initialize notification channels (required for Android 8+)
     */
    fun initialize() {
        if (isInitialized) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Trades channel - High importance
            val tradesChannel = NotificationChannel(
                CHANNEL_TRADES,
                "Trade Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for trade executions and position updates"
                enableVibration(true)
                enableLights(true)
            }
            
            // Alerts channel - High importance
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Trading Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Risk warnings, price alerts, and urgent notifications"
                enableVibration(true)
                enableLights(true)
            }
            
            // Signals channel - Default importance
            val signalsChannel = NotificationChannel(
                CHANNEL_SIGNALS,
                "AI Signals",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "AI Board trading signals and recommendations"
            }
            
            // System channel - Low importance
            val systemChannel = NotificationChannel(
                CHANNEL_SYSTEM,
                "System",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service and system notifications"
            }
            
            notificationManager.createNotificationChannels(
                listOf(tradesChannel, alertsChannel, signalsChannel, systemChannel)
            )
        }
        
        isInitialized = true
    }
    
    // ========================================================================
    // TRADE NOTIFICATIONS
    // ========================================================================
    
    /**
     * Notify trade executed
     */
    fun notifyTradeExecuted(
        symbol: String,
        side: String,
        quantity: Double,
        price: Double,
        pnl: Double? = null
    ) {
        val pnlText = pnl?.let { 
            val sign = if (it >= 0) "+" else ""
            " | P&L: $sign${"%.2f".format(it)}"
        } ?: ""
        
        val title = "Trade Executed: $symbol"
        val message = "$side ${"%.4f".format(quantity)} @ ${"%.2f".format(price)}$pnlText"
        
        showNotification(
            channelId = CHANNEL_TRADES,
            notificationId = NOTIFICATION_TRADE_BASE + getNextId(),
            title = title,
            message = message,
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }
    
    /**
     * Notify position opened
     */
    fun notifyPositionOpened(symbol: String, side: String, entryPrice: Double) {
        showNotification(
            channelId = CHANNEL_TRADES,
            notificationId = NOTIFICATION_TRADE_BASE + getNextId(),
            title = "Position Opened: $symbol",
            message = "$side position opened at ${"%.2f".format(entryPrice)}",
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }
    
    /**
     * Notify position closed
     */
    fun notifyPositionClosed(symbol: String, pnl: Double, pnlPercent: Double, reason: String) {
        val sign = if (pnl >= 0) "+" else ""
        val emoji = if (pnl >= 0) "✅" else "❌"
        
        showNotification(
            channelId = CHANNEL_TRADES,
            notificationId = NOTIFICATION_TRADE_BASE + getNextId(),
            title = "$emoji Position Closed: $symbol",
            message = "P&L: $sign${"%.2f".format(pnl)} ($sign${"%.2f".format(pnlPercent)}%) - $reason",
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }
    
    // ========================================================================
    // STAHL NOTIFICATIONS
    // ========================================================================
    
    /**
     * Notify STAHL level advanced
     */
    fun notifyStahlAdvanced(symbol: String, level: Int, lockedPercent: Double) {
        showNotification(
            channelId = CHANNEL_TRADES,
            notificationId = NOTIFICATION_TRADE_BASE + getNextId(),
            title = "🔒 STAHL Level $level: $symbol",
            message = "Profit locked at ${"%.1f".format(lockedPercent)}%",
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }
    
    /**
     * Notify breakeven reached
     */
    fun notifyBreakevenReached(symbol: String) {
        showNotification(
            channelId = CHANNEL_TRADES,
            notificationId = NOTIFICATION_TRADE_BASE + getNextId(),
            title = "⚖️ Breakeven: $symbol",
            message = "Stop moved to breakeven - risk eliminated",
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }
    
    // ========================================================================
    // ALERT NOTIFICATIONS
    // ========================================================================
    
    /**
     * Notify risk warning
     */
    fun notifyRiskWarning(type: String, current: Double, limit: Double) {
        showNotification(
            channelId = CHANNEL_ALERTS,
            notificationId = NOTIFICATION_ALERT_BASE + getNextId(),
            title = "⚠️ Risk Warning: $type",
            message = "Current: ${"%.1f".format(current)}% | Limit: ${"%.1f".format(limit)}%",
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }
    
    /**
     * Notify kill switch activated
     */
    fun notifyKillSwitch(reason: String) {
        showNotification(
            channelId = CHANNEL_ALERTS,
            notificationId = NOTIFICATION_ALERT_BASE + getNextId(),
            title = "🛑 KILL SWITCH ACTIVATED",
            message = reason,
            priority = NotificationCompat.PRIORITY_MAX
        )
    }
    
    /**
     * Notify price alert triggered
     */
    fun notifyPriceAlert(symbol: String, condition: String, price: Double) {
        showNotification(
            channelId = CHANNEL_ALERTS,
            notificationId = NOTIFICATION_ALERT_BASE + getNextId(),
            title = "📊 Price Alert: $symbol",
            message = "$condition ${"%.2f".format(price)}",
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }
    
    /**
     * Notify liquidation warning
     */
    fun notifyLiquidationWarning(symbol: String, currentPrice: Double, liquidationPrice: Double) {
        val distance = kotlin.math.abs(currentPrice - liquidationPrice) / currentPrice * 100
        
        showNotification(
            channelId = CHANNEL_ALERTS,
            notificationId = NOTIFICATION_ALERT_BASE + getNextId(),
            title = "🚨 LIQUIDATION WARNING: $symbol",
            message = "Only ${"%.1f".format(distance)}% from liquidation!",
            priority = NotificationCompat.PRIORITY_MAX
        )
    }
    
    // ========================================================================
    // AI SIGNAL NOTIFICATIONS
    // ========================================================================
    
    /**
     * Notify AI Board signal
     */
    fun notifyAISignal(
        symbol: String,
        signal: String,
        confidence: Double,
        unanimousCount: Int
    ) {
        val emoji = when (signal) {
            "STRONG_BUY" -> "🟢🟢"
            "BUY" -> "🟢"
            "SELL" -> "🔴"
            "STRONG_SELL" -> "🔴🔴"
            else -> "⚪"
        }
        
        showNotification(
            channelId = CHANNEL_SIGNALS,
            notificationId = NOTIFICATION_SIGNAL_BASE + getNextId(),
            title = "$emoji AI Signal: $symbol",
            message = "$signal | Confidence: ${"%.0f".format(confidence * 100)}% | $unanimousCount/8 agree",
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }
    
    // ========================================================================
    // SYSTEM NOTIFICATIONS
    // ========================================================================
    
    /**
     * Create foreground service notification
     */
    fun createForegroundNotification(): android.app.Notification {
        initialize()
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, CHANNEL_SYSTEM)
            .setContentTitle("Sovereign Vantage")
            .setContentText("Trading service active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * Notify trading service started
     */
    fun notifyServiceStarted() {
        showNotification(
            channelId = CHANNEL_SYSTEM,
            notificationId = NOTIFICATION_SYSTEM_BASE,
            title = "Trading Service Active",
            message = "Sovereign Vantage is monitoring markets",
            priority = NotificationCompat.PRIORITY_LOW,
            ongoing = true
        )
    }
    
    // ========================================================================
    // EVENT HANDLERS
    // ========================================================================
    
    /**
     * Handle position event
     */
    fun handlePositionEvent(event: PositionEvent) {
        when (event) {
            is PositionEvent.Opened -> notifyPositionOpened(
                event.position.symbol,
                event.position.side.name,
                event.position.averageEntryPrice
            )
            is PositionEvent.Closed -> notifyPositionClosed(
                event.position.symbol,
                event.pnl,
                (event.pnl / (event.position.averageEntryPrice * event.position.quantity)) * 100,
                "Closed"
            )
            is PositionEvent.StopUpdated -> notifyStahlAdvanced(
                event.position.symbol,
                event.stahlLevel,
                event.position.unrealizedPnlPercent
            )
            is PositionEvent.BreakevenReached -> notifyBreakevenReached(event.position.symbol)
            is PositionEvent.TakeProfitHit -> notifyPositionClosed(
                event.position.symbol,
                event.position.unrealizedPnl,
                event.position.unrealizedPnlPercent,
                "Take Profit"
            )
            is PositionEvent.StopLossHit -> notifyPositionClosed(
                event.position.symbol,
                event.position.unrealizedPnl,
                event.position.unrealizedPnlPercent,
                "Stop Loss"
            )
            is PositionEvent.LiquidationWarning -> notifyLiquidationWarning(
                event.position.symbol,
                event.currentPrice,
                event.position.liquidationPrice ?: 0.0
            )
            else -> { /* No notification needed */ }
        }
    }
    
    /**
     * Handle risk event
     */
    fun handleRiskEvent(event: RiskEvent) {
        when (event) {
            is RiskEvent.DrawdownWarning -> notifyRiskWarning(
                "Drawdown",
                event.currentDrawdown,
                event.limit
            )
            is RiskEvent.DailyLossWarning -> notifyRiskWarning(
                "Daily Loss",
                event.currentLoss,
                event.limit
            )
            is RiskEvent.KillSwitchActivated -> notifyKillSwitch(event.reason)
            else -> { /* No notification needed */ }
        }
    }
    
    /**
     * Handle scalping event - routes to appropriate notification
     */
    fun handleScalpingEvent(event: ScalpingEvent) {
        when (event) {
            is ScalpingEvent.SignalGenerated -> {
                val signal = event.signal
                notifyScalpSignal(
                    symbol = signal.symbol,
                    direction = signal.direction.name,
                    confidence = signal.confidence,
                    entryPrice = signal.entryPrice,
                    targetPercent = signal.targetPercent
                )
            }
            
            is ScalpingEvent.PositionOpened -> {
                val scalp = event.scalp
                notifyScalpOpened(
                    symbol = scalp.signal.symbol,
                    direction = scalp.signal.direction.name,
                    entryPrice = scalp.signal.executionPrice ?: scalp.signal.entryPrice,
                    quantity = scalp.quantity
                )
            }
            
            is ScalpingEvent.StahlLevelReached -> {
                notifyScalpStahlLevel(
                    symbol = event.scalp.signal.symbol,
                    level = event.level,
                    lockedPercent = getStahlLockPercent(event.level),
                    currentPnl = event.scalp.unrealizedPnlPercent
                )
            }
            
            is ScalpingEvent.PositionClosed -> {
                val result = event.result
                notifyScalpClosed(
                    symbol = result.signal.symbol,
                    pnlPercent = result.pnlPercent,
                    pnlAmount = result.pnlAmount,
                    exitReason = formatExitReason(result.exitReason),
                    holdSeconds = result.holdTimeSeconds
                )
            }
            
            is ScalpingEvent.RiskLimitHit -> {
                notifyScalpingRiskLimit(event.reason)
            }
            
            is ScalpingEvent.EngineStarted -> {
                notifyScalpingStarted(
                    mode = event.config.mode.name,
                    maxConcurrent = event.config.maxConcurrentScalps
                )
            }
            
            is ScalpingEvent.EngineStopped -> {
                val stats = event.stats
                notifyScalpingStopped(
                    totalTrades = stats.totalScalps,
                    winRate = stats.winRate,
                    totalPnl = stats.totalProfitPercent
                )
            }
            
            is ScalpingEvent.Error -> {
                // Log error but don't spam user with notifications
                android.util.Log.e("NotificationService", "Scalping error: ${event.message}", event.exception)
            }
            
            // These don't need notifications
            is ScalpingEvent.SignalConfirmed,
            is ScalpingEvent.SignalExpired,
            is ScalpingEvent.SignalRejected,
            is ScalpingEvent.PositionUpdated -> { /* No notification */ }
        }
    }
    
    /**
     * Get the lock percentage for a STAHL level (scalping version)
     */
    private fun getStahlLockPercent(level: Int): Double {
        // Scalping STAHL levels are tighter than swing trading
        return when (level) {
            1 -> 0.0    // Breakeven
            2 -> 0.3
            3 -> 0.5
            4 -> 0.8
            5 -> 1.0
            6 -> 1.5
            else -> 0.0
        }
    }
    
    /**
     * Format exit reason for display
     */
    private fun formatExitReason(reason: ScalpExitReason): String {
        return when (reason) {
            ScalpExitReason.TARGET_HIT -> "Target Hit"
            ScalpExitReason.STOP_LOSS -> "Stop Loss"
            ScalpExitReason.STAHL_STOP -> "STAHL Stop"
            ScalpExitReason.TIME_LIMIT -> "Time Limit"
            ScalpExitReason.MOMENTUM_REVERSAL -> "Momentum Reversal"
            ScalpExitReason.MANUAL -> "Manual Exit"
            ScalpExitReason.RISK_LIMIT -> "Risk Limit"
            ScalpExitReason.BREAKEVEN -> "Breakeven"
            ScalpExitReason.TRAILING_STOP -> "Trailing Stop"
        }
    }
    
    // ========================================================================
    // SCALPING NOTIFICATIONS
    // ========================================================================
    
    /**
     * Notify scalping signal generated (SIGNAL_ONLY mode)
     */
    fun notifyScalpSignal(
        symbol: String,
        direction: String,
        confidence: Int,
        entryPrice: Double,
        targetPercent: Double
    ) {
        val emoji = if (direction == "LONG") "🟢" else "🔴"
        val action = if (direction == "LONG") "BUY" else "SELL"
        
        showNotification(
            channelId = CHANNEL_SIGNALS,
            notificationId = NOTIFICATION_SIGNAL_BASE + getNextId(),
            title = "$emoji Scalp Signal: $symbol",
            message = "$action @ ${"%.2f".format(entryPrice)} | Target: +${"%.1f".format(targetPercent)}% | Confidence: $confidence%",
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }
    
    /**
     * Notify scalp position opened
     */
    fun notifyScalpOpened(
        symbol: String,
        direction: String,
        entryPrice: Double,
        quantity: Double
    ) {
        val emoji = if (direction == "LONG") "🟢" else "🔴"
        
        showNotification(
            channelId = CHANNEL_TRADES,
            notificationId = NOTIFICATION_TRADE_BASE + getNextId(),
            title = "$emoji Scalp Opened: $symbol",
            message = "$direction ${"%.6f".format(quantity)} @ ${"%.2f".format(entryPrice)}",
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }
    
    /**
     * Notify scalp STAHL level reached
     */
    fun notifyScalpStahlLevel(
        symbol: String,
        level: Int,
        lockedPercent: Double,
        currentPnl: Double
    ) {
        val emoji = when {
            level >= 5 -> "🏆"
            level >= 3 -> "🔒"
            else -> "📈"
        }
        
        showNotification(
            channelId = CHANNEL_TRADES,
            notificationId = NOTIFICATION_TRADE_BASE + getNextId(),
            title = "$emoji Scalp STAHL L$level: $symbol",
            message = "Locked: ${"%.2f".format(lockedPercent)}% | Current P&L: +${"%.2f".format(currentPnl)}%",
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }
    
    /**
     * Notify scalp position closed
     */
    fun notifyScalpClosed(
        symbol: String,
        pnlPercent: Double,
        pnlAmount: Double,
        exitReason: String,
        holdSeconds: Int
    ) {
        val emoji = if (pnlPercent >= 0) "✅" else "❌"
        val sign = if (pnlPercent >= 0) "+" else ""
        
        // Format hold time
        val holdTime = when {
            holdSeconds < 60 -> "${holdSeconds}s"
            holdSeconds < 3600 -> "${holdSeconds / 60}m ${holdSeconds % 60}s"
            else -> "${holdSeconds / 3600}h ${(holdSeconds % 3600) / 60}m"
        }
        
        showNotification(
            channelId = CHANNEL_TRADES,
            notificationId = NOTIFICATION_TRADE_BASE + getNextId(),
            title = "$emoji Scalp Closed: $symbol",
            message = "P&L: $sign${"%.2f".format(pnlPercent)}% ($sign${"%.2f".format(pnlAmount)}) | $exitReason | $holdTime",
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }
    
    /**
     * Notify scalping risk limit hit
     */
    fun notifyScalpingRiskLimit(reason: String) {
        showNotification(
            channelId = CHANNEL_ALERTS,
            notificationId = NOTIFICATION_ALERT_BASE + getNextId(),
            title = "⚠️ Scalping Paused",
            message = reason,
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }
    
    /**
     * Notify scalping engine started
     */
    fun notifyScalpingStarted(mode: String, maxConcurrent: Int) {
        showNotification(
            channelId = CHANNEL_SYSTEM,
            notificationId = NOTIFICATION_SYSTEM_BASE + getNextId(),
            title = "⚡ Scalping Active",
            message = "Mode: $mode | Max positions: $maxConcurrent",
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }
    
    /**
     * Notify scalping engine stopped with summary
     */
    fun notifyScalpingStopped(
        totalTrades: Int,
        winRate: Double,
        totalPnl: Double
    ) {
        val sign = if (totalPnl >= 0) "+" else ""
        val emoji = if (totalPnl >= 0) "📊" else "📉"
        
        showNotification(
            channelId = CHANNEL_SYSTEM,
            notificationId = NOTIFICATION_SYSTEM_BASE + getNextId(),
            title = "$emoji Scalping Stopped",
            message = "Trades: $totalTrades | Win: ${"%.0f".format(winRate)}% | P&L: $sign${"%.2f".format(totalPnl)}%",
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }
    
    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================
    
    private fun showNotification(
        channelId: String,
        notificationId: Int,
        title: String,
        message: String,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
        ongoing: Boolean = false
    ) {
        initialize()
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(priority)
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted
        }
    }
    
    private fun getNextId(): Int {
        return notificationIdCounter++
    }
    
    /**
     * Cancel all notifications
     */
    fun cancelAll() {
        NotificationManagerCompat.from(context).cancelAll()
    }
    
    /**
     * Cancel specific notification
     */
    fun cancel(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
    
}
