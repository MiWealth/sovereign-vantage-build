package com.miwealth.sovereignvantage.widget

/**
 * SOVEREIGN VANTAGE TRADING WIDGET
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Home screen widget displaying:
 * - Current P/L (profit/loss)
 * - Open positions count
 * - Today's trades count
 * - Margin health status
 * - EMERGENCY STOP button (prominent red)
 * 
 * Supports two themes:
 * - Transparent: Discrete, blends with any wallpaper
 * - Imperial: Dark green with 24k gold accents
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn 💘
 */



import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import com.miwealth.sovereignvantage.MainActivity
import com.miwealth.sovereignvantage.R
import com.miwealth.sovereignvantage.core.trading.TradingSystemIntegration
import com.miwealth.sovereignvantage.core.trading.engine.MarginRiskState
import kotlinx.coroutines.*
import java.text.NumberFormat
import java.util.*

/**
 * Widget theme options.
 */
enum class WidgetTheme {
    TRANSPARENT,  // Discrete, semi-transparent black
    IMPERIAL      // Dark green with gold accents
}

/**
 * Trading Widget Provider - Updates and manages the home screen widget.
 */
class TradingWidgetProvider : AppWidgetProvider() {
    
    companion object {
        private const val TAG = "TradingWidget"
        
        // Actions
        const val ACTION_EMERGENCY_STOP = "com.miwealth.sovereignvantage.EMERGENCY_STOP"
        const val ACTION_REFRESH = "com.miwealth.sovereignvantage.WIDGET_REFRESH"
        const val ACTION_OPEN_APP = "com.miwealth.sovereignvantage.OPEN_APP"
        
        // Preferences
        private const val PREFS_NAME = "SovereignVantageWidget"
        private const val PREF_THEME = "widget_theme"
        
        // Update interval (milliseconds) - actual update controlled by system
        const val UPDATE_INTERVAL_MS = 30_000L
        
        // Currency formatter
        private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
            maximumFractionDigits = 2
        }
        
        /**
         * Request widget update from anywhere in the app.
         */
        fun requestUpdate(context: Context) {
            val intent = Intent(context, TradingWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val widgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = widgetManager.getAppWidgetIds(
                ComponentName(context, TradingWidgetProvider::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            context.sendBroadcast(intent)
        }
        
        /**
         * Get current widget theme.
         */
        fun getTheme(context: Context): WidgetTheme {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val themeName = prefs.getString(PREF_THEME, WidgetTheme.TRANSPARENT.name)
            return try {
                WidgetTheme.valueOf(themeName ?: WidgetTheme.TRANSPARENT.name)
            } catch (e: Exception) {
                WidgetTheme.TRANSPARENT
            }
        }
        
        /**
         * Set widget theme.
         */
        fun setTheme(context: Context, theme: WidgetTheme) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(PREF_THEME, theme.name).apply()
            requestUpdate(context)
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_EMERGENCY_STOP -> {
                Log.w(TAG, "🛑 EMERGENCY STOP triggered from widget!")
                handleEmergencyStop(context)
            }
            ACTION_REFRESH -> {
                Log.d(TAG, "Manual refresh requested")
                requestUpdate(context)
            }
            ACTION_OPEN_APP -> {
                openApp(context)
            }
        }
    }
    
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.i(TAG, "Widget enabled")
    }
    
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.i(TAG, "Widget disabled")
        scope.cancel()
    }
    
    /**
     * Update a single widget instance.
     */
    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val theme = getTheme(context)
        val views = RemoteViews(context.packageName, R.layout.widget_trading)
        
        // Get trading data
        val widgetData = getWidgetData(context)
        
        // Apply theme
        applyTheme(views, theme)
        
        // Update data displays
        updateDataDisplays(views, widgetData)
        
        // Set up click handlers
        setupClickHandlers(context, views)
        
        // Push update
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
    
    /**
     * Get current trading data for widget.
     */
    private fun getWidgetData(context: Context): WidgetData {
        return try {
            val tradingSystem = TradingSystemIntegration.getInstance(context)
            val portfolioState = tradingSystem.getPortfolioState()
            val marginStatus = tradingSystem.getMarginStatus()
            val isTrading = tradingSystem.isTradingAllowed()
            
            WidgetData(
                totalPnl = portfolioState?.unrealizedPnl ?: 0.0,
                pnlPercent = calculatePnlPercent(portfolioState?.unrealizedPnl, portfolioState?.totalBalance),
                openPositions = portfolioState?.openPositionCount ?: 0,
                todayTrades = 0, // TODO: Get from trade history
                marginLevel = portfolioState?.freeMarginPercent ?: 100.0,
                marginStatus = marginStatus?.riskState ?: MarginRiskState.HEALTHY,
                isTrading = isTrading,
                lastUpdate = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get trading data", e)
            WidgetData() // Return defaults
        }
    }
    
    private fun calculatePnlPercent(pnl: Double?, balance: Double?): Double {
        if (pnl == null || balance == null || balance == 0.0) return 0.0
        return (pnl / balance) * 100
    }
    
    /**
     * Apply visual theme to widget.
     */
    private fun applyTheme(views: RemoteViews, theme: WidgetTheme) {
        when (theme) {
            WidgetTheme.TRANSPARENT -> {
                views.setInt(R.id.widget_background, "setBackgroundColor", Color.parseColor("#BF000000"))
                views.setTextColor(R.id.widget_title, Color.WHITE)
                views.setTextColor(R.id.widget_pnl_value, Color.WHITE) // Will be overridden by P/L color
                views.setTextColor(R.id.widget_positions_value, Color.WHITE)
                views.setTextColor(R.id.widget_trades_value, Color.WHITE)
                views.setTextColor(R.id.widget_margin_value, Color.WHITE)
                views.setTextColor(R.id.widget_positions_label, Color.parseColor("#A0A0A0"))
                views.setTextColor(R.id.widget_trades_label, Color.parseColor("#A0A0A0"))
                views.setTextColor(R.id.widget_margin_label, Color.parseColor("#A0A0A0"))
            }
            WidgetTheme.IMPERIAL -> {
                views.setInt(R.id.widget_background, "setBackgroundColor", Color.parseColor("#FF021508"))
                views.setTextColor(R.id.widget_title, Color.parseColor("#FFD700"))
                views.setTextColor(R.id.widget_pnl_value, Color.parseColor("#FFD700"))
                views.setTextColor(R.id.widget_positions_value, Color.parseColor("#FFD700"))
                views.setTextColor(R.id.widget_trades_value, Color.parseColor("#FFD700"))
                views.setTextColor(R.id.widget_margin_value, Color.parseColor("#FFD700"))
                views.setTextColor(R.id.widget_positions_label, Color.parseColor("#C0A000"))
                views.setTextColor(R.id.widget_trades_label, Color.parseColor("#C0A000"))
                views.setTextColor(R.id.widget_margin_label, Color.parseColor("#C0A000"))
            }
        }
    }
    
    /**
     * Update data displays in widget.
     */
    private fun updateDataDisplays(views: RemoteViews, data: WidgetData) {
        // Title with status
        val statusIndicator = if (data.isTrading) "● LIVE" else "○ STOPPED"
        views.setTextViewText(R.id.widget_status, statusIndicator)
        views.setTextColor(R.id.widget_status, 
            if (data.isTrading) Color.parseColor("#22C55E") else Color.parseColor("#EF4444")
        )
        
        // P/L Display
        val pnlText = if (data.totalPnl >= 0) {
            "+${formatCurrency(data.totalPnl)}"
        } else {
            formatCurrency(data.totalPnl)
        }
        views.setTextViewText(R.id.widget_pnl_value, pnlText)
        views.setTextColor(R.id.widget_pnl_value,
            if (data.totalPnl >= 0) Color.parseColor("#22C55E") else Color.parseColor("#EF4444")
        )
        
        // P/L Percent
        val pnlPercentText = if (data.pnlPercent >= 0) {
            "↑ ${String.format("%.2f", data.pnlPercent)}%"
        } else {
            "↓ ${String.format("%.2f", kotlin.math.abs(data.pnlPercent))}%"
        }
        views.setTextViewText(R.id.widget_pnl_percent, pnlPercentText)
        views.setTextColor(R.id.widget_pnl_percent,
            if (data.pnlPercent >= 0) Color.parseColor("#22C55E") else Color.parseColor("#EF4444")
        )
        
        // Stats
        views.setTextViewText(R.id.widget_positions_value, data.openPositions.toString())
        views.setTextViewText(R.id.widget_trades_value, data.todayTrades.toString())
        views.setTextViewText(R.id.widget_margin_value, "${data.marginLevel.toInt()}%")
        
        // Margin color based on health
        val marginColor = when (data.marginStatus) {
            MarginRiskState.HEALTHY -> Color.parseColor("#22C55E")
            MarginRiskState.WARNING -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#EF4444")
        }
        views.setTextColor(R.id.widget_margin_value, marginColor)
        
        // Margin status text
        views.setTextViewText(R.id.widget_margin_status, data.marginStatus.name)
        views.setTextColor(R.id.widget_margin_status, marginColor)
        
        // Stop button text
        views.setTextViewText(R.id.widget_stop_button,
            if (data.isTrading) "🛑 EMERGENCY STOP" else "▶️ RESUME"
        )
    }
    
    /**
     * Set up click handlers for widget elements.
     */
    private fun setupClickHandlers(context: Context, views: RemoteViews) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        
        // Tap widget body -> Open app
        val openAppIntent = Intent(context, TradingWidgetProvider::class.java).apply {
            action = ACTION_OPEN_APP
        }
        val openAppPendingIntent = PendingIntent.getBroadcast(context, 0, openAppIntent, flags)
        views.setOnClickPendingIntent(R.id.widget_body, openAppPendingIntent)
        
        // EMERGENCY STOP button
        val stopIntent = Intent(context, TradingWidgetProvider::class.java).apply {
            action = ACTION_EMERGENCY_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(context, 1, stopIntent, flags)
        views.setOnClickPendingIntent(R.id.widget_stop_button, stopPendingIntent)
        
        // Refresh button (if present)
        val refreshIntent = Intent(context, TradingWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(context, 2, refreshIntent, flags)
        views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)
    }
    
    /**
     * Handle emergency stop action.
     */
    private fun handleEmergencyStop(context: Context) {
        scope.launch {
            try {
                val tradingSystem = TradingSystemIntegration.getInstance(context)
                val isCurrentlyTrading = tradingSystem.isTradingAllowed()
                
                if (isCurrentlyTrading) {
                    // STOP trading - close all positions
                    Log.w(TAG, "🛑 Executing EMERGENCY STOP from widget")
                    tradingSystem.emergencyStop("Widget EMERGENCY STOP button pressed")
                    
                    // Show notification
                    showNotification(context, 
                        "Trading Stopped", 
                        "Emergency stop activated. All positions closed."
                    )
                } else {
                    // Resume trading
                    Log.i(TAG, "▶️ Resuming trading from widget")
                    tradingSystem.start()
                    
                    showNotification(context,
                        "Trading Resumed",
                        "Trading has been resumed."
                    )
                }
                
                // Update widget
                requestUpdate(context)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle emergency stop", e)
                showNotification(context,
                    "Error",
                    "Failed to stop trading: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Open the main app.
     */
    private fun openApp(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }
    
    /**
     * Show a notification.
     */
    private fun showNotification(context: Context, title: String, message: String) {
        // TODO: Implement notification using NotificationManager
        Log.i(TAG, "Notification: $title - $message")
    }
    
    /**
     * Format currency value.
     */
    private fun formatCurrency(value: Double): String {
        return currencyFormat.format(value)
    }
}

/**
 * Data class for widget display.
 */
data class WidgetData(
    val totalPnl: Double = 0.0,
    val pnlPercent: Double = 0.0,
    val openPositions: Int = 0,
    val todayTrades: Int = 0,
    val marginLevel: Double = 100.0,
    val marginStatus: MarginRiskState = MarginRiskState.HEALTHY,
    val isTrading: Boolean = false,
    val lastUpdate: Long = 0
)
