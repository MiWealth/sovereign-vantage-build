package com.miwealth.sovereignvantage.core.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * SOVEREIGN VANTAGE V5.19.107 "ARTHUR EDITION"
 * SYSTEM LOGGER
 * 
 * BUILD #107: Comprehensive system-wide logging for diagnostics
 * 
 * FEATURES:
 * - In-memory log buffer (last 500 entries)
 * - Categorized logging (INIT, TRADE, RISK, ERROR, SYSTEM)
 * - Thread-safe concurrent queue
 * - Exportable for debugging
 * - Accessible from Settings → Advanced Diagnostics
 * 
 * © 2025-2026 MiWealth Pty Ltd
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

object SystemLogger {
    
    private const val TAG = "SystemLogger"
    private const val MAX_BUFFER_SIZE = 500
    
    private val logBuffer = ConcurrentLinkedQueue<LogEntry>()
    private val dateFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    
    enum class Category {
        INIT,      // System initialization
        BOARD,     // AI Board voting and decisions
        TRADE,     // Trade execution
        RISK,      // Risk management
        ERROR,     // Errors and exceptions
        SYSTEM,    // General system events
        PRICE,     // Price feed updates
        WEBSOCKET, // WebSocket connection events
        KILLSWITCH // Kill switch activation/reset
    }
    
    data class LogEntry(
        val timestamp: Long,
        val category: Category,
        val message: String,
        val level: String // D, I, W, E
    ) {
        fun format(): String {
            val time = dateFormatter.format(Date(timestamp))
            return "$time [$level] [${category.name}] $message"
        }
    }
    
    /**
     * Log INIT message
     */
    fun init(message: String) {
        log(Category.INIT, "I", message)
        Log.i(TAG, "[INIT] $message")
    }
    
    /**
     * Log BOARD message (AI Board voting and decisions)
     */
    fun board(message: String) {
        log(Category.BOARD, "I", message)
        Log.i(TAG, "[BOARD] $message")
    }
    
    /**
     * Log TRADE message
     */
    fun trade(message: String) {
        log(Category.TRADE, "I", message)
        Log.i(TAG, "[TRADE] $message")
    }
    
    /**
     * Log RISK message
     */
    fun risk(message: String) {
        log(Category.RISK, "W", message)
        Log.w(TAG, "[RISK] $message")
    }
    
    /**
     * Log ERROR message
     */
    fun error(message: String, exception: Throwable? = null) {
        log(Category.ERROR, "E", message)
        if (exception != null) {
            Log.e(TAG, "[ERROR] $message", exception)
        } else {
            Log.e(TAG, "[ERROR] $message")
        }
    }
    
    /**
     * Log SYSTEM message
     */
    fun system(message: String) {
        log(Category.SYSTEM, "I", message)
        Log.i(TAG, "[SYSTEM] $message")
    }
    
    /**
     * Log PRICE message (debug only)
     */
    fun price(message: String) {
        log(Category.PRICE, "D", message)
        Log.d(TAG, "[PRICE] $message")
    }
    
    /**
     * Log WEBSOCKET message
     */
    fun websocket(message: String) {
        log(Category.WEBSOCKET, "I", message)
        Log.i(TAG, "[WEBSOCKET] $message")
    }
    
    /**
     * Log KILLSWITCH message
     */
    fun killswitch(message: String) {
        log(Category.KILLSWITCH, "W", message)
        Log.w(TAG, "[KILLSWITCH] $message")
    }
    
    /**
     * BUILD #126: Generic logging methods to capture ALL Android logs
     * These should be used throughout the app instead of Log.d/i/w/e
     */
    fun d(tag: String, message: String) {
        log(Category.SYSTEM, "D", "[$tag] $message")
        Log.d(tag, message)
    }
    
    fun i(tag: String, message: String) {
        log(Category.SYSTEM, "I", "[$tag] $message")
        Log.i(tag, message)
    }
    
    fun w(tag: String, message: String) {
        log(Category.SYSTEM, "W", "[$tag] $message")
        Log.w(tag, message)
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) {
            "[$tag] $message: ${throwable.message}"
        } else {
            "[$tag] $message"
        }
        log(Category.ERROR, "E", msg)
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
    
    /**
     * Internal logging
     */
    private fun log(category: Category, level: String, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            category = category,
            message = message,
            level = level
        )
        
        logBuffer.offer(entry)
        
        // Keep buffer size under limit
        while (logBuffer.size > MAX_BUFFER_SIZE) {
            logBuffer.poll()
        }
    }
    
    /**
     * Get all logs
     */
    fun getAllLogs(): List<LogEntry> {
        return logBuffer.toList()
    }
    
    /**
     * Get logs by category
     */
    fun getLogsByCategory(category: Category): List<LogEntry> {
        return logBuffer.filter { it.category == category }
    }
    
    /**
     * Get recent logs (last N entries)
     */
    fun getRecentLogs(count: Int = 50): List<LogEntry> {
        return logBuffer.toList().takeLast(count)
    }
    
    /**
     * Get logs as formatted text
     */
    fun getLogsAsText(): String {
        return logBuffer.joinToString("\n") { it.format() }
    }
    
    /**
     * Clear all logs
     */
    fun clear() {
        logBuffer.clear()
        Log.i(TAG, "Log buffer cleared")
    }
    
    /**
     * Get log statistics
     */
    fun getStats(): LogStats {
        val logs = logBuffer.toList()
        return LogStats(
            totalLogs = logs.size,
            errorCount = logs.count { it.level == "E" },
            warningCount = logs.count { it.level == "W" },
            initLogs = logs.count { it.category == Category.INIT },
            boardLogs = logs.count { it.category == Category.BOARD },
            tradeLogs = logs.count { it.category == Category.TRADE },
            riskLogs = logs.count { it.category == Category.RISK }
        )
    }
    
    data class LogStats(
        val totalLogs: Int,
        val errorCount: Int,
        val warningCount: Int,
        val initLogs: Int,
        val boardLogs: Int,
        val tradeLogs: Int,
        val riskLogs: Int
    )
}
