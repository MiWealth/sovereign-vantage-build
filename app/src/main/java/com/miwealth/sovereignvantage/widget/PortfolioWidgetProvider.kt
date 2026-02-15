package com.miwealth.sovereignvantage.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.miwealth.sovereignvantage.MainActivity
import com.miwealth.sovereignvantage.R
import com.miwealth.sovereignvantage.core.security.EncryptedPrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Portfolio Widget Provider - Home screen widget for portfolio summary
 * 
 * Displays:
 * - Total portfolio value (AU$)
 * - Daily P&L
 * - Active positions count
 * - Quick action buttons
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 */

class PortfolioWidgetProvider : AppWidgetProvider() {
    
    companion object {
        const val ACTION_REFRESH = "com.miwealth.sovereignvantage.WIDGET_REFRESH"
        const val ACTION_OPEN_TRADING = "com.miwealth.sovereignvantage.WIDGET_OPEN_TRADING"
        
        /**
         * Update all widgets
         */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetComponent = ComponentName(context, PortfolioWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
            
            val intent = Intent(context, PortfolioWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            }
            context.sendBroadcast(intent)
        }
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_REFRESH -> {
                updateAllWidgets(context)
            }
            ACTION_OPEN_TRADING -> {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("navigate_to", "trading")
                }
                context.startActivity(launchIntent)
            }
        }
    }
    
    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // Use goAsync() for proper BroadcastReceiver lifecycle — scope auto-cancels
        val pendingResult = goAsync()
        val updateScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        updateScope.launch {
            try {
            val views = RemoteViews(context.packageName, R.layout.widget_portfolio)
            
            // Get portfolio data
            val portfolioData = getPortfolioData(context)
            
            // Update views
            views.setTextViewText(R.id.widget_total_value, portfolioData.totalValueFormatted)
            views.setTextViewText(R.id.widget_daily_pnl, portfolioData.dailyPnlFormatted)
            views.setTextViewText(R.id.widget_positions_count, "${portfolioData.positionCount} Positions")
            
            // Set colors based on P&L
            val pnlColor = if (portfolioData.dailyPnl >= 0) {
                context.getColor(R.color.profit_green)
            } else {
                context.getColor(R.color.loss_red)
            }
            views.setTextColor(R.id.widget_daily_pnl, pnlColor)
            
            // Set up click intents
            
            // Open app on tap
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, openAppPendingIntent)
            
            // Refresh button
            val refreshIntent = Intent(context, PortfolioWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, 1, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)
            
            // Trading button
            val tradingIntent = Intent(context, PortfolioWidgetProvider::class.java).apply {
                action = ACTION_OPEN_TRADING
            }
            val tradingPendingIntent = PendingIntent.getBroadcast(
                context, 2, tradingIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_trading_button, tradingPendingIntent)
            
            // Update widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
            } finally {
                pendingResult.finish()
                updateScope.cancel()
            }
        }
    }
    
    private suspend fun getPortfolioData(context: Context): WidgetPortfolioData {
        // In production, this would fetch from repository/database
        // For now, return placeholder or cached data
        
        return try {
            // Try to get from encrypted preferences (cached data)
            val prefs = EncryptedPrefsManager.getWidgetPrefs(context)
            val totalValue = prefs.getFloat("total_value", 0f).toDouble()
            val dailyPnl = prefs.getFloat("daily_pnl", 0f).toDouble()
            val dailyPnlPercent = prefs.getFloat("daily_pnl_percent", 0f).toDouble()
            val positionCount = prefs.getInt("position_count", 0)
            
            WidgetPortfolioData(
                totalValue = totalValue,
                dailyPnl = dailyPnl,
                dailyPnlPercent = dailyPnlPercent,
                positionCount = positionCount
            )
        } catch (e: Exception) {
            WidgetPortfolioData()
        }
    }
    
    /**
     * Cache portfolio data for widget
     */
    fun cachePortfolioData(
        context: Context,
        totalValue: Double,
        dailyPnl: Double,
        dailyPnlPercent: Double,
        positionCount: Int
    ) {
        val prefs = EncryptedPrefsManager.getWidgetPrefs(context)
        prefs.edit()
            .putFloat("total_value", totalValue.toFloat())
            .putFloat("daily_pnl", dailyPnl.toFloat())
            .putFloat("daily_pnl_percent", dailyPnlPercent.toFloat())
            .putInt("position_count", positionCount)
            .putLong("last_update", System.currentTimeMillis())
            .apply()
    }
}

data class WidgetPortfolioData(
    val totalValue: Double = 0.0,
    val dailyPnl: Double = 0.0,
    val dailyPnlPercent: Double = 0.0,
    val positionCount: Int = 0
) {
    val totalValueFormatted: String
        get() = "$${"%,.2f".format(totalValue)}"
    
    val dailyPnlFormatted: String
        get() {
            val sign = if (dailyPnl >= 0) "+" else ""
            return "$sign${"%.2f".format(dailyPnl)} ($sign${"%.2f".format(dailyPnlPercent)}%)"
        }
}
