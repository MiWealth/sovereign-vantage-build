/**
 * Trading Hours - Market Session Definitions
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * Defines trading hours for different markets and asset types.
 * Supports 24/7 crypto, forex sessions, and traditional market hours.
 * 
 * DESIGN RATIONALE:
 * - Session-aware trading improves entry/exit timing
 * - Volatility and liquidity vary by session
 * - Prevents orders on closed markets
 * - Enables session-specific strategies (e.g., London/NY overlap for forex)
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 */

package com.miwealth.sovereignvantage.core.trading.assets

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Represents trading hours for an asset or market.
 * 
 * @property sessions List of trading sessions (can be multiple per day)
 * @property timezone Primary timezone for this market
 * @property is24x7 True if market trades continuously (crypto)
 * @property holidays List of dates when market is closed (ISO format: "2026-01-01")
 */
data class TradingHours(
    val sessions: List<TradingSession>,
    val timezone: ZoneId,
    val is24x7: Boolean = false,
    val holidays: List<String> = emptyList()
) {
    /**
     * Check if market is currently open.
     */
    fun isOpen(now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        if (is24x7) return true
        
        val localNow = now.withZoneSameInstant(timezone)
        val today = localNow.toLocalDate().toString()
        
        // Check holidays
        if (today in holidays) return false
        
        // Check if any session is active
        return sessions.any { session ->
            session.isActive(localNow.toLocalTime(), localNow.dayOfWeek)
        }
    }
    
    /**
     * Get the current active session, if any.
     */
    fun currentSession(now: ZonedDateTime = ZonedDateTime.now()): TradingSession? {
        if (!isOpen(now)) return null
        if (is24x7) return sessions.firstOrNull()
        
        val localNow = now.withZoneSameInstant(timezone)
        return sessions.find { session ->
            session.isActive(localNow.toLocalTime(), localNow.dayOfWeek)
        }
    }
    
    /**
     * Get time until next market open.
     * Returns Duration.ZERO if already open.
     */
    fun timeUntilOpen(now: ZonedDateTime = ZonedDateTime.now()): java.time.Duration {
        if (isOpen(now)) return java.time.Duration.ZERO
        
        val localNow = now.withZoneSameInstant(timezone)
        
        // Find next session start
        for (daysAhead in 0..7) {
            val checkDate = localNow.plusDays(daysAhead.toLong())
            val dateStr = checkDate.toLocalDate().toString()
            
            if (dateStr in holidays) continue
            
            for (session in sessions) {
                if (checkDate.dayOfWeek !in session.activeDays) continue
                
                val sessionStart = checkDate.toLocalDate().atTime(session.open)
                    .atZone(timezone)
                
                if (sessionStart.isAfter(now)) {
                    return java.time.Duration.between(now, sessionStart)
                }
            }
        }
        
        return java.time.Duration.ofDays(7) // Fallback
    }
    
    /**
     * Get time until market close.
     * Returns Duration.ZERO if already closed.
     */
    fun timeUntilClose(now: ZonedDateTime = ZonedDateTime.now()): java.time.Duration {
        if (!isOpen(now)) return java.time.Duration.ZERO
        if (is24x7) return java.time.Duration.ofDays(365) // Effectively never
        
        val session = currentSession(now) ?: return java.time.Duration.ZERO
        val localNow = now.withZoneSameInstant(timezone)
        
        val closeTime = localNow.toLocalDate().atTime(session.close).atZone(timezone)
        return java.time.Duration.between(now, closeTime)
    }
    
    companion object {
        val WEEKDAYS = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        )
        /**
         * 24/7 trading (cryptocurrencies).
         */
        val CRYPTO_24_7 = TradingHours(
            sessions = listOf(
                TradingSession(
                    name = "Continuous",
                    open = LocalTime.of(0, 0),
                    close = LocalTime.of(23, 59, 59),
                    activeDays = DayOfWeek.entries.toSet()
                )
            ),
            timezone = ZoneId.of("UTC"),
            is24x7 = true
        )
        
        /**
         * Forex market hours (Sunday 5pm - Friday 5pm ET).
         */
        val FOREX_STANDARD = TradingHours(
            sessions = listOf(
                TradingSession(
                    name = "Sydney",
                    open = LocalTime.of(17, 0),  // 5pm ET Sunday
                    close = LocalTime.of(2, 0),   // 2am ET Monday
                    activeDays = setOf(DayOfWeek.SUNDAY)
                ),
                TradingSession(
                    name = "Global",
                    open = LocalTime.of(0, 0),
                    close = LocalTime.of(23, 59, 59),
                    activeDays = setOf(
                        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, 
                        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY
                    )
                ),
                TradingSession(
                    name = "Final",
                    open = LocalTime.of(0, 0),
                    close = LocalTime.of(17, 0),  // 5pm ET Friday
                    activeDays = setOf(DayOfWeek.FRIDAY)
                )
            ),
            timezone = ZoneId.of("America/New_York"),
            is24x7 = false
        )
        
        /**
         * US Stock Market hours (9:30am - 4pm ET, Mon-Fri).
         */
        val US_EQUITIES = TradingHours(
            sessions = listOf(
                TradingSession(
                    name = "Pre-Market",
                    open = LocalTime.of(4, 0),
                    close = LocalTime.of(9, 30),
                    activeDays = WEEKDAYS,
                    isExtendedHours = true
                ),
                TradingSession(
                    name = "Regular",
                    open = LocalTime.of(9, 30),
                    close = LocalTime.of(16, 0),
                    activeDays = WEEKDAYS,
                    isExtendedHours = false
                ),
                TradingSession(
                    name = "After-Hours",
                    open = LocalTime.of(16, 0),
                    close = LocalTime.of(20, 0),
                    activeDays = WEEKDAYS,
                    isExtendedHours = true
                )
            ),
            timezone = ZoneId.of("America/New_York"),
            is24x7 = false
        )
        
        /**
         * CME Futures hours (Sunday 6pm - Friday 5pm CT with daily break).
         */
        val CME_FUTURES = TradingHours(
            sessions = listOf(
                TradingSession(
                    name = "Globex",
                    open = LocalTime.of(17, 0),
                    close = LocalTime.of(16, 0),
                    activeDays = setOf(
                        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, 
                        DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY
                    ),
                    dailyBreakStart = LocalTime.of(16, 0),
                    dailyBreakEnd = LocalTime.of(17, 0)
                )
            ),
            timezone = ZoneId.of("America/Chicago"),
            is24x7 = false
        )
        
    }
}

/**
 * Represents a single trading session within a day.
 * 
 * @property name Session identifier (e.g., "London", "Pre-Market")
 * @property open Session opening time
 * @property close Session closing time
 * @property activeDays Days when this session is active
 * @property isExtendedHours True if this is extended/after-hours trading
 * @property dailyBreakStart Start of daily maintenance break (null if none)
 * @property dailyBreakEnd End of daily maintenance break (null if none)
 */
data class TradingSession(
    val name: String,
    val open: LocalTime,
    val close: LocalTime,
    val activeDays: Set<DayOfWeek>,
    val isExtendedHours: Boolean = false,
    val dailyBreakStart: LocalTime? = null,
    val dailyBreakEnd: LocalTime? = null
) {
    /**
     * Check if this session is currently active.
     */
    fun isActive(time: LocalTime, day: DayOfWeek): Boolean {
        if (day !in activeDays) return false
        
        // Handle overnight sessions (close < open)
        val inSession = if (close < open) {
            time >= open || time < close
        } else {
            time >= open && time < close
        }
        
        if (!inSession) return false
        
        // Check for daily break
        if (dailyBreakStart != null && dailyBreakEnd != null) {
            if (time >= dailyBreakStart && time < dailyBreakEnd) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Get session duration in hours.
     */
    fun durationHours(): Double {
        val minutes = if (close < open) {
            // Overnight session
            (24 * 60) - java.time.Duration.between(close, open).toMinutes()
        } else {
            java.time.Duration.between(open, close).toMinutes()
        }
        
        // Subtract break time if present
        val breakMinutes = if (dailyBreakStart != null && dailyBreakEnd != null) {
            java.time.Duration.between(dailyBreakStart, dailyBreakEnd).toMinutes()
        } else {
            0
        }
        
        return (minutes - breakMinutes) / 60.0
    }
}

/**
 * Forex trading sessions with their characteristics.
 * Useful for session-specific strategies.
 */
enum class ForexSession(
    val displayName: String,
    val openUtc: LocalTime,
    val closeUtc: LocalTime,
    val majorPairs: List<String>,
    val avgVolatilityPips: Int
) {
    SYDNEY(
        displayName = "Sydney",
        openUtc = LocalTime.of(21, 0),  // 9pm UTC (previous day)
        closeUtc = LocalTime.of(6, 0),
        majorPairs = listOf("AUD/USD", "NZD/USD", "AUD/JPY"),
        avgVolatilityPips = 30
    ),
    
    TOKYO(
        displayName = "Tokyo",
        openUtc = LocalTime.of(0, 0),
        closeUtc = LocalTime.of(9, 0),
        majorPairs = listOf("USD/JPY", "EUR/JPY", "GBP/JPY"),
        avgVolatilityPips = 40
    ),
    
    LONDON(
        displayName = "London",
        openUtc = LocalTime.of(7, 0),
        closeUtc = LocalTime.of(16, 0),
        majorPairs = listOf("EUR/USD", "GBP/USD", "EUR/GBP"),
        avgVolatilityPips = 80
    ),
    
    NEW_YORK(
        displayName = "New York",
        openUtc = LocalTime.of(12, 0),
        closeUtc = LocalTime.of(21, 0),
        majorPairs = listOf("EUR/USD", "USD/CAD", "USD/CHF"),
        avgVolatilityPips = 70
    );
    
    companion object {
        /**
         * Get sessions active at a given UTC time.
         * Multiple sessions can overlap (e.g., London/NY overlap).
         */
        fun activeSessions(utcTime: LocalTime): List<ForexSession> =
            entries.filter { session ->
                if (session.closeUtc < session.openUtc) {
                    // Overnight session
                    utcTime >= session.openUtc || utcTime < session.closeUtc
                } else {
                    utcTime >= session.openUtc && utcTime < session.closeUtc
                }
            }
        
        /**
         * Check if we're in a session overlap period (high liquidity).
         */
        fun isOverlapPeriod(utcTime: LocalTime): Boolean =
            activeSessions(utcTime).size >= 2
    }
}
