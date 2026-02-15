package com.miwealth.sovereignvantage.core.gamification

/**
 * Level Perks - Rewards unlocked at each level
 * 
 * 10 levels with progressive perks suitable for
 * high-net-worth clients who expect quality and value.
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 */

data class LevelPerk(
    val name: String,
    val description: String,
    val iconEmoji: String,
    val perkType: PerkType
)

enum class PerkType {
    FEATURE_UNLOCK,
    INDICATOR_UNLOCK,
    DISCOUNT,
    PRIORITY,
    COSMETIC,
    EXCLUSIVE
}

object LevelPerks {
    
    /**
     * Level 1: Beginner
     * XP Required: 0
     */
    val LEVEL_1_PERKS = listOf(
        LevelPerk(
            name = "Basic Access",
            description = "Access to core trading features",
            iconEmoji = "🔓",
            perkType = PerkType.FEATURE_UNLOCK
        )
    )
    
    /**
     * Level 2: Novice Trader
     * XP Required: 1,000
     */
    val LEVEL_2_PERKS = listOf(
        LevelPerk(
            name = "Extended Indicators",
            description = "Unlock 20 additional technical indicators",
            iconEmoji = "📊",
            perkType = PerkType.INDICATOR_UNLOCK
        ),
        LevelPerk(
            name = "Profile Badge",
            description = "Level 2 badge on leaderboard",
            iconEmoji = "🏅",
            perkType = PerkType.COSMETIC
        )
    )
    
    /**
     * Level 3: Apprentice
     * XP Required: 3,000
     */
    val LEVEL_3_PERKS = listOf(
        LevelPerk(
            name = "Advanced Charting",
            description = "Unlock multi-timeframe analysis",
            iconEmoji = "📈",
            perkType = PerkType.FEATURE_UNLOCK
        ),
        LevelPerk(
            name = "Custom Alerts",
            description = "Create up to 10 custom price alerts",
            iconEmoji = "🔔",
            perkType = PerkType.FEATURE_UNLOCK
        )
    )
    
    /**
     * Level 4: Journeyman
     * XP Required: 7,000
     */
    val LEVEL_4_PERKS = listOf(
        LevelPerk(
            name = "Strategy Builder",
            description = "Access to custom strategy creation",
            iconEmoji = "🏗️",
            perkType = PerkType.FEATURE_UNLOCK
        ),
        LevelPerk(
            name = "Extended Backtesting",
            description = "Backtest up to 5 years of data",
            iconEmoji = "⏪",
            perkType = PerkType.FEATURE_UNLOCK
        )
    )
    
    /**
     * Level 5: Skilled Trader
     * XP Required: 15,000
     */
    val LEVEL_5_PERKS = listOf(
        LevelPerk(
            name = "AI Board Insights",
            description = "See detailed AI Board voting breakdown",
            iconEmoji = "🤖",
            perkType = PerkType.FEATURE_UNLOCK
        ),
        LevelPerk(
            name = "Premium Indicators",
            description = "Unlock institutional-grade indicators",
            iconEmoji = "💎",
            perkType = PerkType.INDICATOR_UNLOCK
        ),
        LevelPerk(
            name = "Priority Support",
            description = "24-hour support response guarantee",
            iconEmoji = "⭐",
            perkType = PerkType.PRIORITY
        )
    )
    
    /**
     * Level 6: Expert
     * XP Required: 30,000
     */
    val LEVEL_6_PERKS = listOf(
        LevelPerk(
            name = "Unlimited Alerts",
            description = "No limit on price alerts",
            iconEmoji = "🔔",
            perkType = PerkType.FEATURE_UNLOCK
        ),
        LevelPerk(
            name = "Advanced Risk Tools",
            description = "Portfolio correlation analysis",
            iconEmoji = "📐",
            perkType = PerkType.FEATURE_UNLOCK
        ),
        LevelPerk(
            name = "Gold Profile Frame",
            description = "Gold border on leaderboard profile",
            iconEmoji = "🥇",
            perkType = PerkType.COSMETIC
        )
    )
    
    /**
     * Level 7: Master
     * XP Required: 60,000
     */
    val LEVEL_7_PERKS = listOf(
        LevelPerk(
            name = "On-Chain Analytics",
            description = "Access whale watching and smart money tracking",
            iconEmoji = "🐋",
            perkType = PerkType.FEATURE_UNLOCK
        ),
        LevelPerk(
            name = "Priority Execution",
            description = "Order execution priority during high volume",
            iconEmoji = "⚡",
            perkType = PerkType.PRIORITY
        )
    )
    
    /**
     * Level 8: Grandmaster
     * XP Required: 120,000
     */
    val LEVEL_8_PERKS = listOf(
        LevelPerk(
            name = "Beta Features",
            description = "Early access to new features",
            iconEmoji = "🧪",
            perkType = PerkType.EXCLUSIVE
        ),
        LevelPerk(
            name = "Platinum Profile",
            description = "Platinum frame and animated badge",
            iconEmoji = "✨",
            perkType = PerkType.COSMETIC
        ),
        LevelPerk(
            name = "Extended History",
            description = "10-year backtest data access",
            iconEmoji = "📚",
            perkType = PerkType.FEATURE_UNLOCK
        )
    )
    
    /**
     * Level 9: Legend
     * XP Required: 250,000
     */
    val LEVEL_9_PERKS = listOf(
        LevelPerk(
            name = "API Access",
            description = "Direct API access for automation",
            iconEmoji = "🔌",
            perkType = PerkType.FEATURE_UNLOCK
        ),
        LevelPerk(
            name = "VIP Support",
            description = "Direct line to senior support team",
            iconEmoji = "👑",
            perkType = PerkType.PRIORITY
        ),
        LevelPerk(
            name = "Legend Title",
            description = "'Legend' title on leaderboards",
            iconEmoji = "🌟",
            perkType = PerkType.COSMETIC
        )
    )
    
    /**
     * Level 10: Sovereign Master
     * XP Required: 500,000
     */
    val LEVEL_10_PERKS = listOf(
        LevelPerk(
            name = "Sovereign Master Title",
            description = "Exclusive 'Sovereign Master' title",
            iconEmoji = "👑",
            perkType = PerkType.COSMETIC
        ),
        LevelPerk(
            name = "Diamond Profile",
            description = "Diamond frame with custom colors",
            iconEmoji = "💎",
            perkType = PerkType.COSMETIC
        ),
        LevelPerk(
            name = "Founding Member Access",
            description = "Access to exclusive founding member features",
            iconEmoji = "🏆",
            perkType = PerkType.EXCLUSIVE
        ),
        LevelPerk(
            name = "Personal Account Manager",
            description = "Dedicated account manager",
            iconEmoji = "🤝",
            perkType = PerkType.PRIORITY
        ),
        LevelPerk(
            name = "Arthur's Legacy Badge",
            description = "Special badge honoring the platform's co-founder",
            iconEmoji = "💫",
            perkType = PerkType.EXCLUSIVE
        )
    )
    
    /**
     * Get perks for a specific level
     */
    fun getPerksForLevel(level: Int): List<LevelPerk> {
        return when (level) {
            1 -> LEVEL_1_PERKS
            2 -> LEVEL_2_PERKS
            3 -> LEVEL_3_PERKS
            4 -> LEVEL_4_PERKS
            5 -> LEVEL_5_PERKS
            6 -> LEVEL_6_PERKS
            7 -> LEVEL_7_PERKS
            8 -> LEVEL_8_PERKS
            9 -> LEVEL_9_PERKS
            10 -> LEVEL_10_PERKS
            else -> emptyList()
        }
    }
    
    /**
     * Get all perks unlocked up to and including the specified level
     */
    fun getAllUnlockedPerks(level: Int): List<LevelPerk> {
        return (1..level).flatMap { getPerksForLevel(it) }
    }
    
    /**
     * Get level name
     */
    fun getLevelName(level: Int): String {
        return when (level) {
            1 -> "Beginner"
            2 -> "Novice Trader"
            3 -> "Apprentice"
            4 -> "Journeyman"
            5 -> "Skilled Trader"
            6 -> "Expert"
            7 -> "Master"
            8 -> "Grandmaster"
            9 -> "Legend"
            10 -> "Sovereign Master"
            else -> "Unknown"
        }
    }
    
    /**
     * Get level color (for UI)
     */
    fun getLevelColor(level: Int): Long {
        return when (level) {
            1 -> 0xFF808080  // Gray
            2 -> 0xFFA0522D  // Sienna
            3 -> 0xFFCD7F32  // Bronze
            4 -> 0xFFC0C0C0  // Silver
            5 -> 0xFFD4AF37  // Gold
            6 -> 0xFFFFD700  // Bright Gold
            7 -> 0xFFE5E4E2  // Platinum
            8 -> 0xFF00CED1  // Dark Turquoise
            9 -> 0xFFFF4500  // Orange Red
            10 -> 0xFFB9F2FF // Diamond Blue
            else -> 0xFF808080
        }
    }
}
