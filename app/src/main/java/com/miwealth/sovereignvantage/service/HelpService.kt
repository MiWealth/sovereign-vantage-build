// HelpService.kt - Core logic for the in-app searchable help system

package com.miwealth.sovereignvantage.service

import com.miwealth.sovereignvantage.help.data.HelpArticle
import com.miwealth.sovereignvantage.help.data.HelpIndex

/**
 * Service responsible for managing and searching the in-app documentation.
 *
 * The documentation is embedded locally (HelpIndex) to ensure it is always available
 * and does not require a server connection.
 */
class HelpService {

    private val helpArticles: List<HelpArticle> = HelpIndex.articles

    /**
     * Searches the local help index for articles matching the query.
     * @param query The search term.
     * @return A list of matching HelpArticle objects.
     */
    fun search(query: String): List<HelpArticle> {
        if (query.isBlank()) return emptyList()

        val lowerQuery = query.toLowerCase()

        // Simple search logic: match title or content
        return helpArticles.filter { article ->
            article.title.toLowerCase().contains(lowerQuery) ||
            article.content.toLowerCase().contains(lowerQuery) ||
            article.tags.any { it.toLowerCase().contains(lowerQuery) }
        }
    }

    /**
     * Retrieves a specific article by its ID.
     * @param id The unique ID of the article.
     * @return The HelpArticle or null if not found.
     */
    fun getArticle(id: String): HelpArticle? {
        return helpArticles.find { it.id == id }
    }

    /**
     * Provides a categorized list of all available help topics for browsing.
     * @return A map where the key is the category and the value is a list of articles in that category.
     */
    fun getCategories(): Map<String, List<HelpArticle>> {
        return helpArticles.groupBy { it.category }
    }
}

// --- Data Structures (Placeholder for actual content) ---

object HelpIndex {
    val articles = listOf(
        HelpArticle(
            id = "pqc_security",
            title = "Post-Quantum Cryptography (PQC) Security",
            category = "Security & Architecture",
            tags = listOf("pqc", "security", "quantum", "kyber", "dilithium"),
            content = "The Sovereign Vantage uses NIST-standard CRYSTALS-Kyber and CRYSTALS-Dilithium to future-proof your assets against quantum computing threats. This is a core, non-custodial feature. You hold your own keys."
        ),
        HelpArticle(
            id = "dflp_overview",
            title = "Decentralized Federated Learning Protocol (DFLP)",
            category = "AI & Intelligence",
            tags = listOf("dflp", "ai", "federated", "aggregator", "serverless"),
            content = "The DFLP creates a collective intelligence without compromising your privacy. It operates in a serverless, peer-to-peer manner using a Distributed Hash Table (DHT) for model aggregation. Your data never leaves your device."
        ),
        HelpArticle(
            id = "paper_trading",
            title = "Using Paper Trading Mode",
            category = "Simulation & Testing",
            tags = listOf("paper", "simulation", "test", "risk-free"),
            content = "Paper Trading Mode allows you to simulate live trading with real-time market data without risking actual capital. Use the toggle in the Simulation Settings to activate."
        ),
        HelpArticle(
            id = "stress_test",
            title = "Running a Stress Test",
            category = "Simulation & Testing",
            tags = listOf("stress", "test", "backtest", "drawdown", "black swan"),
            content = "The Stress Test Suite validates your current VPI risk configuration against historical Black Swan events (e.g., BlackThursday2020) to determine worst-case Max Drawdown."
        ),
        HelpArticle(
            id = "cost_basis",
            title = "Cost Basis and Performance Tracking",
            category = "Portfolio & Reporting",
            tags = listOf("cost basis", "tax", "performance", "ledger", "fifo", "hifo"),
            content = "The Portfolio Service tracks your cost basis using your preferred method (FIFO/LIFO/HIFO) and records all transactions in the local Trade Ledger for accurate performance and tax reporting."
        ),
        HelpArticle(
            id = "risk_disclosure",
            title = "Risk Disclosure and Responsibility",
            category = "Legal & Compliance",
            tags = listOf("risk", "loss", "responsibility", "decentralization"),
            content = "Investments made through this software can result in the loss of all assets. Sovereign Vantage is a non-custodial software interface; we do not hold your funds or keys. You are solely responsible for the security of your device and recovery phrases. With sovereignty comes responsibility."
        )
    )
}

data class HelpArticle(
    val id: String,
    val title: String,
    val category: String,
    val tags: List<String>,
    val content: String
)
