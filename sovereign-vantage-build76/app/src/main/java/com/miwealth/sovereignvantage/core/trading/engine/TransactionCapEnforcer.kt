package com.miwealth.sovereignvantage.core.trading.engine

/**
 * TransactionCapEnforcer
 * Enforces trading limits for lower tiers to drive upgrades.
 * 
 * LIMITS:
 * - STARTER: 0 Real Trades (Unlimited Paper)
 * - STANDARD: 50 Trades/Month
 * - PROFESSIONAL: 500 Trades/Month
 * - ELITE/APEX: Unlimited
 */
class TransactionCapEnforcer {

    companion object {
        const val LIMIT_STANDARD = 50
        const val LIMIT_PROFESSIONAL = 500
    }

    fun checkTransactionLimit(userId: String, tier: String, currentMonthTrades: Int): CapStatus {
        return when (tier.uppercase()) {
            "STARTER" -> CapStatus.BLOCKED("Starter tier is limited to Paper Trading. Upgrade to Standard to trade live.")
            "STANDARD" -> evaluateCap(currentMonthTrades, LIMIT_STANDARD, "Professional")
            "PROFESSIONAL" -> evaluateCap(currentMonthTrades, LIMIT_PROFESSIONAL, "Elite")
            "ELITE", "APEX" -> CapStatus.ALLOWED
            else -> CapStatus.BLOCKED("Unknown Tier")
        }
    }

    private fun evaluateCap(current: Int, limit: Int, nextTier: String): CapStatus {
        val warningThreshold = (limit * 0.9).toInt()
        
        return when {
            current >= limit -> CapStatus.BLOCKED("Monthly trade limit reached ($limit). Upgrade to $nextTier to continue.")
            current >= warningThreshold -> CapStatus.WARNING("You have used ${current}/${limit} trades. Upgrade to $nextTier to avoid interruption.")
            else -> CapStatus.ALLOWED
        }
    }

    sealed class CapStatus {
        object ALLOWED : CapStatus()
        data class WARNING(val message: String) : CapStatus()
        data class BLOCKED(val message: String) : CapStatus()
    }
}
