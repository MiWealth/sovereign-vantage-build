package com.miwealth.sovereignvantage.core.gamification

import java.util.Calendar
import java.util.UUID

/**
 * Challenge System - Daily/Weekly/Monthly competitions
 * 
 * Generates and tracks challenges for users to complete.
 * Challenges are percentage-based (ROI%) as per spec.
 * 
 * Note: This file uses "DailyChallenge" to distinguish from SocialFeatures.Challenge
 * which handles 1v1 peer-to-peer battles.
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 */

data class DailyDailyChallenge(
    val id: String,
    val name: String,
    val description: String,
    val type: DailyChallengeType,
    val duration: DailyChallengeDuration,
    val target: Int,
    val xpReward: Long,
    val endTime: Long,
    val difficulty: DailyChallengeDifficulty = ChallengeDifficulty.NORMAL
)

enum class DailyChallengeType {
    TRADES,           // Execute X trades
    WINNING_TRADES,   // Execute X winning trades
    ROI_PERCENT,      // Achieve X% total ROI
    WIN_STREAK,       // Achieve X win streak
    LESSONS,          // Complete X lessons
    LOGIN_STREAK,     // Log in X days
    USE_STAHL,        // Use STAHL stops X times
    INDICATORS        // Use X different indicators
}

enum class ChallengeDuration {
    DAILY,
    WEEKLY,
    MONTHLY
}

enum class ChallengeDifficulty {
    EASY,
    NORMAL,
    HARD,
    EXTREME
}

object ChallengeGenerator {
    
    private val random = java.util.Random()
    
    // ========================================================================
    // DAILY CHALLENGES (reset at midnight)
    // ========================================================================
    
    fun generateDaily(): List<DailyChallenge> {
        val endTime = getEndOfDay()
        
        return listOf(
            // Pick 3 random daily challenges
            generateDailyTradesChallenge(endTime),
            generateDailyWinChallenge(endTime),
            generateDailyMiscChallenge(endTime)
        )
    }
    
    private fun generateDailyTradesChallenge(endTime: Long): DailyChallenge {
        val options = listOf(
            DailyChallenge(
                id = "daily_${UUID.randomUUID()}",
                name = "Day Trader",
                description = "Execute 3 trades today",
                type = DailyChallengeType.TRADES,
                duration = ChallengeDuration.DAILY,
                target = 3,
                xpReward = 100,
                endTime = endTime,
                difficulty = ChallengeDifficulty.EASY
            ),
            DailyChallenge(
                id = "daily_${UUID.randomUUID()}",
                name = "Active Markets",
                description = "Execute 5 trades today",
                type = DailyChallengeType.TRADES,
                duration = ChallengeDuration.DAILY,
                target = 5,
                xpReward = 150,
                endTime = endTime,
                difficulty = ChallengeDifficulty.NORMAL
            ),
            DailyChallenge(
                id = "daily_${UUID.randomUUID()}",
                name = "Market Mover",
                description = "Execute 10 trades today",
                type = DailyChallengeType.TRADES,
                duration = ChallengeDuration.DAILY,
                target = 10,
                xpReward = 250,
                endTime = endTime,
                difficulty = ChallengeDifficulty.HARD
            )
        )
        return options[random.nextInt(options.size)]
    }
    
    private fun generateDailyWinChallenge(endTime: Long): DailyChallenge {
        val options = listOf(
            DailyChallenge(
                id = "daily_${UUID.randomUUID()}",
                name = "First Win",
                description = "Win 1 trade today",
                type = DailyChallengeType.WINNING_TRADES,
                duration = ChallengeDuration.DAILY,
                target = 1,
                xpReward = 75,
                endTime = endTime,
                difficulty = ChallengeDifficulty.EASY
            ),
            DailyChallenge(
                id = "daily_${UUID.randomUUID()}",
                name = "Triple Threat",
                description = "Win 3 trades today",
                type = DailyChallengeType.WINNING_TRADES,
                duration = ChallengeDuration.DAILY,
                target = 3,
                xpReward = 175,
                endTime = endTime,
                difficulty = ChallengeDifficulty.NORMAL
            ),
            DailyChallenge(
                id = "daily_${UUID.randomUUID()}",
                name = "Hot Streak",
                description = "Achieve a 3-win streak today",
                type = DailyChallengeType.WIN_STREAK,
                duration = ChallengeDuration.DAILY,
                target = 3,
                xpReward = 200,
                endTime = endTime,
                difficulty = ChallengeDifficulty.HARD
            )
        )
        return options[random.nextInt(options.size)]
    }
    
    private fun generateDailyMiscChallenge(endTime: Long): DailyChallenge {
        val options = listOf(
            DailyChallenge(
                id = "daily_${UUID.randomUUID()}",
                name = "Student of Markets",
                description = "Complete 1 lesson today",
                type = DailyChallengeType.LESSONS,
                duration = ChallengeDuration.DAILY,
                target = 1,
                xpReward = 100,
                endTime = endTime,
                difficulty = ChallengeDifficulty.EASY
            ),
            DailyChallenge(
                id = "daily_${UUID.randomUUID()}",
                name = "STAHL Guardian",
                description = "Use STAHL stops on 2 trades",
                type = DailyChallengeType.USE_STAHL,
                duration = ChallengeDuration.DAILY,
                target = 2,
                xpReward = 125,
                endTime = endTime,
                difficulty = ChallengeDifficulty.NORMAL
            ),
            DailyChallenge(
                id = "daily_${UUID.randomUUID()}",
                name = "Indicator Explorer",
                description = "Use 3 different indicators",
                type = DailyChallengeType.INDICATORS,
                duration = ChallengeDuration.DAILY,
                target = 3,
                xpReward = 100,
                endTime = endTime,
                difficulty = ChallengeDifficulty.EASY
            )
        )
        return options[random.nextInt(options.size)]
    }
    
    // ========================================================================
    // WEEKLY CHALLENGES (reset on Monday)
    // ========================================================================
    
    fun generateWeekly(): List<DailyChallenge> {
        val endTime = getEndOfWeek()
        
        return listOf(
            DailyChallenge(
                id = "weekly_${UUID.randomUUID()}",
                name = "Weekly Warrior",
                description = "Execute 20 trades this week",
                type = DailyChallengeType.TRADES,
                duration = ChallengeDuration.WEEKLY,
                target = 20,
                xpReward = 500,
                endTime = endTime,
                difficulty = ChallengeDifficulty.NORMAL
            ),
            DailyChallenge(
                id = "weekly_${UUID.randomUUID()}",
                name = "Profit Hunter",
                description = "Win 10 trades this week",
                type = DailyChallengeType.WINNING_TRADES,
                duration = ChallengeDuration.WEEKLY,
                target = 10,
                xpReward = 600,
                endTime = endTime,
                difficulty = ChallengeDifficulty.NORMAL
            ),
            DailyChallenge(
                id = "weekly_${UUID.randomUUID()}",
                name = "Scholar's Week",
                description = "Complete 5 lessons this week",
                type = DailyChallengeType.LESSONS,
                duration = ChallengeDuration.WEEKLY,
                target = 5,
                xpReward = 400,
                endTime = endTime,
                difficulty = ChallengeDifficulty.NORMAL
            ),
            DailyChallenge(
                id = "weekly_${UUID.randomUUID()}",
                name = "Consistent Trader",
                description = "Log in all 7 days this week",
                type = DailyChallengeType.LOGIN_STREAK,
                duration = ChallengeDuration.WEEKLY,
                target = 7,
                xpReward = 350,
                endTime = endTime,
                difficulty = ChallengeDifficulty.NORMAL
            )
        )
    }
    
    // ========================================================================
    // MONTHLY CHALLENGES (reset on 1st)
    // ========================================================================
    
    fun generateMonthly(): List<DailyChallenge> {
        val endTime = getEndOfMonth()
        
        return listOf(
            DailyChallenge(
                id = "monthly_${UUID.randomUUID()}",
                name = "Monthly Master",
                description = "Execute 100 trades this month",
                type = DailyChallengeType.TRADES,
                duration = ChallengeDuration.MONTHLY,
                target = 100,
                xpReward = 2000,
                endTime = endTime,
                difficulty = ChallengeDifficulty.HARD
            ),
            DailyChallenge(
                id = "monthly_${UUID.randomUUID()}",
                name = "Profit Machine",
                description = "Win 50 trades this month",
                type = DailyChallengeType.WINNING_TRADES,
                duration = ChallengeDuration.MONTHLY,
                target = 50,
                xpReward = 2500,
                endTime = endTime,
                difficulty = ChallengeDifficulty.HARD
            ),
            DailyChallenge(
                id = "monthly_${UUID.randomUUID()}",
                name = "Green Month",
                description = "Achieve positive ROI this month",
                type = DailyChallengeType.ROI_PERCENT,
                duration = ChallengeDuration.MONTHLY,
                target = 1,  // Just be positive
                xpReward = 3000,
                endTime = endTime,
                difficulty = ChallengeDifficulty.NORMAL
            ),
            DailyChallenge(
                id = "monthly_${UUID.randomUUID()}",
                name = "Curriculum Progress",
                description = "Complete 20 lessons this month",
                type = DailyChallengeType.LESSONS,
                duration = ChallengeDuration.MONTHLY,
                target = 20,
                xpReward = 1500,
                endTime = endTime,
                difficulty = ChallengeDifficulty.NORMAL
            ),
            DailyChallenge(
                id = "monthly_${UUID.randomUUID()}",
                name = "Streak Legend",
                description = "Achieve a 10-win streak this month",
                type = DailyChallengeType.WIN_STREAK,
                duration = ChallengeDuration.MONTHLY,
                target = 10,
                xpReward = 3500,
                endTime = endTime,
                difficulty = ChallengeDifficulty.EXTREME
            )
        )
    }
    
    // ========================================================================
    // TIME HELPERS
    // ========================================================================
    
    private fun getEndOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }
    
    private fun getEndOfWeek(): Long {
        val calendar = Calendar.getInstance()
        // Move to next Sunday
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }
    
    private fun getEndOfMonth(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }
}
