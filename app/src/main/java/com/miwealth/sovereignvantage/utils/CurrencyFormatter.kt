package com.miwealth.sovereignvantage.utils

import java.text.NumberFormat
import java.util.Locale

/**
 * SOVEREIGN VANTAGE V5.19.105 "ARTHUR EDITION"
 * CURRENCY FORMATTER
 * 
 * BUILD #105 FIX:
 * Centralized currency formatting for Australian Dollars (A$).
 * Replaces inconsistent US Dollar formatting throughout the app.
 * 
 * All currency amounts in Sovereign Vantage are displayed in AUD
 * as per Mike Stahl's requirement and user skill configuration.
 * 
 * © 2025-2026 MiWealth Pty Ltd
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */
object CurrencyFormatter {
    
    /**
     * Australian locale for currency formatting.
     * Formats as: A$12,345.67
     */
    private val audLocale = Locale.forLanguageTag("en-AU")
    
    /**
     * Standard currency formatter for AUD.
     */
    private val currencyFormat = NumberFormat.getCurrencyInstance(audLocale).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }
    
    /**
     * Compact formatter for large amounts (no cents).
     * Formats as: A$12,345
     */
    private val compactFormat = NumberFormat.getCurrencyInstance(audLocale).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }
    
    /**
     * High precision formatter for small amounts (4 decimal places).
     * Formats as: A$0.0123
     */
    private val preciseFormat = NumberFormat.getCurrencyInstance(audLocale).apply {
        maximumFractionDigits = 4
        minimumFractionDigits = 2
    }
    
    /**
     * Format value as AUD with standard 2 decimal places.
     * 
     * Examples:
     * - 1234.56 → A$1,234.56
     * - -500.25 → -A$500.25
     * - 0.0 → A$0.00
     */
    fun format(value: Double): String {
        return currencyFormat.format(value)
    }
    
    /**
     * Format value as AUD without cents (whole dollars).
     * Use for large portfolio values or summaries.
     * 
     * Examples:
     * - 123456.78 → A$123,457
     * - 1000.00 → A$1,000
     */
    fun formatCompact(value: Double): String {
        return compactFormat.format(value)
    }
    
    /**
     * Format value as AUD with high precision (4 decimals).
     * Use for small amounts, price differences, or precise calculations.
     * 
     * Examples:
     * - 0.0123 → A$0.0123
     * - 1.23456 → A$1.2346
     */
    fun formatPrecise(value: Double): String {
        return preciseFormat.format(value)
    }
    
    /**
     * Format percentage value.
     * 
     * Examples:
     * - 0.1234 → +12.34%
     * - -0.0567 → -5.67%
     * - 0.0 → 0.00%
     */
    fun formatPercent(value: Double): String {
        val sign = if (value > 0) "+" else ""
        return "${sign}%.2f%%".format(value * 100)
    }
    
    /**
     * Format change amount with sign and color context.
     * 
     * Examples:
     * - 123.45 → "+A$123.45" (green in UI)
     * - -67.89 → "-A$67.89" (red in UI)
     * - 0.0 → "A$0.00" (neutral in UI)
     */
    fun formatChange(value: Double): String {
        return when {
            value > 0 -> "+${currencyFormat.format(value)}"
            value < 0 -> currencyFormat.format(value) // Already has minus sign
            else -> currencyFormat.format(value)
        }
    }
    
    /**
     * Format large amounts with K/M/B suffixes.
     * Use for abbreviated displays (e.g., charts, summaries).
     * 
     * Examples:
     * - 1234 → A$1.23K
     * - 1234567 → A$1.23M
     * - 1234567890 → A$1.23B
     * - 123 → A$123
     */
    fun formatAbbreviated(value: Double): String {
        val absValue = kotlin.math.abs(value)
        val sign = if (value < 0) "-" else ""
        
        return when {
            absValue >= 1_000_000_000 -> {
                "${sign}A$%.2fB".format(absValue / 1_000_000_000)
            }
            absValue >= 1_000_000 -> {
                "${sign}A$%.2fM".format(absValue / 1_000_000)
            }
            absValue >= 1_000 -> {
                "${sign}A$%.2fK".format(absValue / 1_000)
            }
            else -> {
                currencyFormat.format(value)
            }
        }
    }
}
