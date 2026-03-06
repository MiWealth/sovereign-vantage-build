package com.miwealth.sovereignvantage.ui.utils

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * SOVEREIGN VANTAGE V5.19.117 "ARTHUR EDITION"
 * TIME FORMATTING UTILITIES
 * 
 * BUILD #117 FIX 2: Professional time formatting
 * - Compact format: "2m ago", "1h ago" instead of "2 minutes ago"
 * - Smart relative vs absolute switching
 * - Consistent across all UI components
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * 
 * For Arthur. For Cathryn. 💚
 */

object TimeFormatUtils {
    
    /**
     * Format timestamp as relative time with compact notation
     * 
     * Examples:
     * - Just now (< 60s)
     * - 2m ago (minutes)
     * - 1h ago (hours)
     * - Yesterday at 14:30
     * - 2 days ago
     * - Mar 5 at 14:30 (this year)
     * - Mar 5, 2025 (previous years)
     * 
     * @param timestamp Unix timestamp in milliseconds
     * @return Formatted relative time string
     */
    fun formatRelativeTime(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        return formatRelativeTime(instant)
    }
    
    /**
     * Format Instant as relative time with compact notation
     * 
     * @param instant The instant to format
     * @return Formatted relative time string
     */
    fun formatRelativeTime(instant: Instant): String {
        val now = Instant.now()
        val duration = Duration.between(instant, now)
        val seconds = duration.seconds
        
        return when {
            // Just now (< 1 minute)
            seconds < 60 -> "Just now"
            
            // Minutes ago (< 1 hour)
            seconds < 3600 -> {
                val minutes = seconds / 60
                "${minutes}m ago"
            }
            
            // Hours ago (< 24 hours)
            seconds < 86400 -> {
                val hours = seconds / 3600
                "${hours}h ago"
            }
            
            // Yesterday
            seconds < 172800 -> {
                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                    .withZone(ZoneId.systemDefault())
                "Yesterday at ${formatter.format(instant)}"
            }
            
            // Days ago (< 7 days)
            seconds < 604800 -> {
                val days = seconds / 86400
                "${days} day${if (days > 1) "s" else ""} ago"
            }
            
            // This year (show month and day)
            instant.atZone(ZoneId.systemDefault()).year == now.atZone(ZoneId.systemDefault()).year -> {
                val formatter = DateTimeFormatter.ofPattern("MMM d 'at' HH:mm")
                    .withZone(ZoneId.systemDefault())
                formatter.format(instant)
            }
            
            // Previous years (show full date)
            else -> {
                val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
                    .withZone(ZoneId.systemDefault())
                formatter.format(instant)
            }
        }
    }
    
    /**
     * Format duration in compact notation
     * 
     * Examples:
     * - 45s
     * - 2m 30s
     * - 1h 15m
     * - 2d 3h
     * 
     * @param seconds Duration in seconds
     * @return Formatted duration string
     */
    fun formatDuration(seconds: Long): String {
        val absSec = abs(seconds)
        
        return when {
            absSec < 60 -> "${absSec}s"
            absSec < 3600 -> {
                val mins = absSec / 60
                val secs = absSec % 60
                if (secs == 0L) "${mins}m" else "${mins}m ${secs}s"
            }
            absSec < 86400 -> {
                val hours = absSec / 3600
                val mins = (absSec % 3600) / 60
                if (mins == 0L) "${hours}h" else "${hours}h ${mins}m"
            }
            else -> {
                val days = absSec / 86400
                val hours = (absSec % 86400) / 3600
                if (hours == 0L) "${days}d" else "${days}d ${hours}h"
            }
        }
    }
    
    /**
     * Format timestamp as absolute time
     * 
     * @param timestamp Unix timestamp in milliseconds
     * @param pattern DateTimeFormatter pattern (default: "MMM d, yyyy HH:mm")
     * @return Formatted absolute time string
     */
    fun formatAbsoluteTime(
        timestamp: Long,
        pattern: String = "MMM d, yyyy HH:mm"
    ): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val formatter = DateTimeFormatter.ofPattern(pattern)
            .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }
}
