package com.miwealth.sovereignvantage.help.data

/**
 * Help System Data Models
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 */

data class HelpArticle(
    val id: String,
    val title: String,
    val category: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val relatedArticles: List<String> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

data class HelpCategory(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val articleCount: Int
)

data class HelpIndex(
    val version: String,
    val categories: List<HelpCategory>,
    val articles: List<HelpArticle>,
    val lastUpdated: Long
) {
    companion object {
        fun getDefault(): HelpIndex {
            return HelpIndex(
                version = "1.0.0",
                categories = listOf(
                    HelpCategory("getting-started", "Getting Started", "New to Sovereign Vantage? Start here.", "rocket", 5),
                    HelpCategory("trading", "Trading", "Learn about trading features and strategies.", "chart", 10),
                    HelpCategory("security", "Security", "Protect your account and assets.", "shield", 8),
                    HelpCategory("ai-board", "AI Board", "Understanding the AI Board of Directors.", "brain", 6),
                    HelpCategory("subscriptions", "Subscriptions", "Plans, pricing, and features.", "crown", 4),
                    HelpCategory("troubleshooting", "Troubleshooting", "Common issues and solutions.", "wrench", 7)
                ),
                articles = getDefaultArticles(),
                lastUpdated = System.currentTimeMillis()
            )
        }
        
        private fun getDefaultArticles(): List<HelpArticle> {
            return listOf(
                HelpArticle(
                    id = "gs-01",
                    title = "Welcome to Sovereign Vantage",
                    category = "getting-started",
                    content = """
                        Welcome to Sovereign Vantage: Arthur Edition - your self-sovereign AI-powered trading platform.
                        
                        Key Features:
                        • 8-member AI Board of Directors analyzes every trade
                        • STAHL Stair Stop™ progressive profit protection
                        • Post-quantum encryption for ultimate security
                        • Your keys, your coins - true self-custody
                        
                        This app runs entirely on your device. No centralized servers hold your data.
                    """.trimIndent(),
                    tags = listOf("welcome", "introduction", "overview")
                ),
                HelpArticle(
                    id = "gs-02",
                    title = "Connecting Your Exchange",
                    category = "getting-started",
                    content = """
                        Sovereign Vantage connects to your exchange accounts via API keys.
                        
                        Supported Exchanges:
                        • Kraken (Recommended)
                        • Coinbase Pro
                        • More coming soon
                        
                        Important: Only provide READ and TRADE permissions. Never share withdrawal permissions.
                    """.trimIndent(),
                    tags = listOf("exchange", "api", "kraken", "coinbase")
                ),
                HelpArticle(
                    id = "ai-01",
                    title = "Understanding the AI Board",
                    category = "ai-board",
                    content = """
                        The AI Board of Directors consists of 8 specialized experts who vote on every trade:
                        
                        1. Trend Follower - Identifies and follows market trends
                        2. Mean Reverter - Spots oversold/overbought conditions
                        3. Volatility Trader - Trades market volatility patterns
                        4. Sentiment Analyst - Analyzes market sentiment
                        5. On-Chain Analyst - Monitors blockchain data
                        6. Macro Strategist - Considers macro-economic factors
                        7. Pattern Recognizer - Identifies chart patterns
                        8. Liquidity Hunter - Finds "blood in streets" opportunities
                        
                        Trades require consensus before execution.
                    """.trimIndent(),
                    tags = listOf("ai", "board", "experts", "trading")
                ),
                HelpArticle(
                    id = "tr-01",
                    title = "STAHL Stair Stop™ Explained",
                    category = "trading",
                    content = """
                        The STAHL Stair Stop™ is our proprietary profit-locking system.
                        
                        As your trade becomes profitable, the stop-loss automatically adjusts:
                        
                        Level 1: At 5% profit → Breakeven stop
                        Level 2: At 10% profit → 5% locked in
                        Level 3: At 20% profit → 12% locked in
                        Level 4: At 35% profit → 25% locked in
                        Level 5: At 50% profit → 40% locked in
                        Level 6: At 100% profit → 75% locked in
                        
                        This system contributed 103% of net profit in backtesting.
                    """.trimIndent(),
                    tags = listOf("stahl", "stop-loss", "profit", "risk-management")
                ),
                HelpArticle(
                    id = "sec-01",
                    title = "Post-Quantum Security",
                    category = "security",
                    content = """
                        Sovereign Vantage uses Post-Quantum Cryptographic Envelope (PQCE):
                        
                        • Kyber-1024: Quantum-resistant key encapsulation
                        • Dilithium-5: Quantum-resistant digital signatures
                        • Hybrid encryption: Current + quantum-resistant algorithms
                        
                        Your data is protected against both current and future quantum threats.
                    """.trimIndent(),
                    tags = listOf("security", "quantum", "encryption", "pqce")
                )
            )
        }
    }
    
    fun searchArticles(query: String): List<HelpArticle> {
        val lowerQuery = query.lowercase()
        return articles.filter { article ->
            article.title.lowercase().contains(lowerQuery) ||
            article.content.lowercase().contains(lowerQuery) ||
            article.tags.any { it.lowercase().contains(lowerQuery) }
        }
    }
    
    fun getArticlesByCategory(categoryId: String): List<HelpArticle> {
        return articles.filter { it.category == categoryId }
    }
    
    fun getArticle(id: String): HelpArticle? {
        return articles.find { it.id == id }
    }
}
