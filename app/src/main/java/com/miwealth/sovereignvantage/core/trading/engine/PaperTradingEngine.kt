package com.miwealth.sovereignvantage.core.trading.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * Paper Trading Engine.
 * Simulates the Matching Engine environment for user practice without real capital.
 * 
 * UPDATE V4.0:
 * - Unlocked for ALL tiers (including Starter/Free).
 * - Unlimited resets and virtual capital.
 * - Full access to Level 2 Data simulation.
 */
class PaperTradingEngine {

    private val virtualPortfolios = ConcurrentHashMap<String, Double>() // UserID -> Virtual Balance
    private val portfolioHistory = ConcurrentHashMap<String, MutableList<Double>>() // Track performance

    fun initializeAccount(userId: String, initialBalance: Double = 100000.0) {
        virtualPortfolios[userId] = initialBalance
        portfolioHistory[userId] = mutableListOf(initialBalance)
        println("Paper Trading Account Initialized for $userId with $$initialBalance")
    }

    /**
     * Allows users to reset their paper trading account at any time.
     * Crucial for the "Free Tier Hook" strategy.
     */
    fun resetAccount(userId: String, newBalance: Double = 100000.0) {
        virtualPortfolios[userId] = newBalance
        portfolioHistory[userId]?.add(newBalance)
        println("Paper Trading Account Reset for $userId")
    }

    fun executeSimulatedTrade(userId: String, assetId: String, amount: Double, price: Double): Boolean {
        val balance = virtualPortfolios[userId] ?: return false
        val cost = amount * price

        if (balance >= cost) {
            virtualPortfolios[userId] = balance - cost
            // In a real engine, we would add the asset to a holding map here
            // and simulate slippage/fees.
            return true
        }
        return false
    }

    fun getVirtualBalance(userId: String): Double {
        return virtualPortfolios[userId] ?: 0.0
    }
}
