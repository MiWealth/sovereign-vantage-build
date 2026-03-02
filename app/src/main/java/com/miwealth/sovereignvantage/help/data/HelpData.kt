package com.miwealth.sovereignvantage.help.data

/**
 * Help System Data Models
 * 
 * Provides offline-first help documentation for users.
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 */

data class HelpArticle(
    val id: String,
    val title: String,
    val summary: String,
    val content: String,
    val category: HelpCategory,
    val tags: List<String> = emptyList(),
    val relatedArticles: List<String> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

data class HelpIndex(
    val articles: List<HelpArticle>,
    val categories: List<HelpCategory>,
    val version: String = "1.0.0"
)

enum class HelpCategory(val displayName: String) {
    GETTING_STARTED("Getting Started"),
    TRADING("Trading"),
    PORTFOLIO("Portfolio"),
    AI_BOARD("AI Board of Directors"),
    STAHL_SYSTEM("STAHL Stair Stop™"),
    SECURITY("Security & Privacy"),
    SUBSCRIPTIONS("Subscriptions"),
    TROUBLESHOOTING("Troubleshooting"),
    FAQ("FAQ")
}

object HelpContent {
    
    val gettingStarted = listOf(
        HelpArticle(
            id = "gs-001",
            title = "Welcome to Sovereign Vantage",
            summary = "Introduction to autonomous wealth management",
            content = """
                # Welcome to Sovereign Vantage: Arthur Edition
                
                Sovereign Vantage is an AI-powered autonomous trading platform designed 
                to help you build generational wealth with complete sovereignty over 
                your investments.
                
                ## Key Features
                - 8 AI Expert Advisers analyzing every trade
                - STAHL Stair Stop™ profit protection
                - Post-Quantum Security (PQCE)
                - Complete self-sovereignty
                
                ## Getting Started
                1. Connect your exchange API keys
                2. Configure your risk profile
                3. Review AI recommendations
                4. Let the system work for you
            """.trimIndent(),
            category = HelpCategory.GETTING_STARTED,
            tags = listOf("intro", "setup", "welcome")
        ),
        HelpArticle(
            id = "gs-002",
            title = "Connecting Your Exchange",
            summary = "How to safely connect exchange API keys",
            content = """
                # Connecting Your Exchange
                
                Sovereign Vantage supports Kraken (primary) and Coinbase (secondary).
                
                ## Important: API Key Safety
                - Keys are stored ONLY on your device
                - Use encrypted storage (Android Keystore)
                - Never share your keys
                
                ## Steps
                1. Go to Settings > Exchange Integration
                2. Select your exchange
                3. Enter API Key and Secret
                4. Enable only required permissions
                5. Test connection
            """.trimIndent(),
            category = HelpCategory.GETTING_STARTED,
            tags = listOf("api", "exchange", "kraken", "coinbase")
        )
    )
    
    val stahlArticles = listOf(
        HelpArticle(
            id = "stahl-001",
            title = "Understanding STAHL Stair Stop™",
            summary = "How the proprietary profit-protection system works",
            content = """
                # STAHL Stair Stop™ System
                
                The STAHL (Stop Trading At High Levels) system is a proprietary 
                progressive profit-locking mechanism that contributed 103% of 
                net profit in backtesting.
                
                ## How It Works
                As your trade moves into profit, the system automatically 
                adjusts your stop loss to lock in gains:
                
                | Profit Level | Lock In |
                |--------------|---------|
                | 5% | Breakeven |
                | 10% | 5% profit |
                | 20% | 12% profit |
                | 35% | 25% profit |
                | 50% | 40% profit |
                | 100% | 75% profit |
                
                ## Benefits
                - Winners stay winners
                - Automatic profit protection
                - No manual monitoring needed
            """.trimIndent(),
            category = HelpCategory.STAHL_SYSTEM,
            tags = listOf("stahl", "stop-loss", "profit", "protection")
        )
    )
    
    val aiBoardArticles = listOf(
        HelpArticle(
            id = "ai-001",
            title = "Meet the AI Board of Directors",
            summary = "Understanding the 8 expert advisers",
            content = """
                # AI Board of Directors
                
                Every trade decision is analyzed by 8 specialized AI experts:
                
                ## The Experts
                1. **Trend Follower** - Captures long-duration moves
                2. **Mean Reverter** - Snipes overextended moves
                3. **Volatility Trader** - Trades compression/expansion
                4. **Sentiment Analyst** - Social & momentum signals
                5. **On-Chain Analyst** - Whale accumulation tracking
                6. **Macro Strategist** - Cross-asset correlations
                7. **Pattern Recognizer** - Chart patterns & breakouts
                8. **Liquidity Hunter** - "Blood in streets" buying
                
                ## Consensus Voting
                Experts vote on each trade opportunity:
                - STRONG_BUY (+2 points)
                - BUY (+1 point)
                - HOLD (0 points)
                - SELL (-1 point)
                - STRONG_SELL (-2 points)
                
                Trade execution requires consensus threshold (default 60%).
            """.trimIndent(),
            category = HelpCategory.AI_BOARD,
            tags = listOf("ai", "experts", "trading", "consensus")
        )
    )
    
    fun getAllArticles(): List<HelpArticle> {
        return gettingStarted + stahlArticles + aiBoardArticles
    }
    
    fun getIndex(): HelpIndex {
        return HelpIndex(
            articles = getAllArticles(),
            categories = HelpCategory.values().toList()
        )
    }
    
    fun searchArticles(query: String): List<HelpArticle> {
        val lowerQuery = query.lowercase()
        return getAllArticles().filter { article ->
            article.title.lowercase().contains(lowerQuery) ||
            article.summary.lowercase().contains(lowerQuery) ||
            article.content.lowercase().contains(lowerQuery) ||
            article.tags.any { it.lowercase().contains(lowerQuery) }
        }
    }
    
    fun getArticleById(id: String): HelpArticle? {
        return getAllArticles().find { it.id == id }
    }
    
    fun getArticlesByCategory(category: HelpCategory): List<HelpArticle> {
        return getAllArticles().filter { it.category == category }
    }
}
