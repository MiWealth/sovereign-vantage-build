package com.miwealth.sovereignvantage.core.trading

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Subscription Manager
 * Handles 30-day free trial and subscription tiers
 * Free trial: Paper trading only
 * Paid tiers: Live trading enabled
 */

enum class SubscriptionTier {
    FREE_TRIAL,           // First 30 days - paper trading only
    FREE_PAPER_ONLY,      // After trial, no payment - paper trading only
    PRO,                  // $29/month - live crypto trading
    PREMIUM,              // $99/month - advanced features
    PROFESSIONAL,         // $99/month - Sovereign Vantage multi-asset
    INSTITUTIONAL         // $499/month - Sovereign Vantage enterprise
}

enum class SubscriptionStatus {
    ACTIVE,
    TRIAL,
    EXPIRED,
    CANCELLED,
    PAYMENT_FAILED
}

data class Money(
    val amount: Double,
    val currency: String = "USD"
)

data class UserAccount(
    val userId: String,
    val email: String,
    val accountCreatedDate: Instant,
    val trialStartDate: Instant,
    val trialEndDate: Instant,
    var subscriptionTier: SubscriptionTier = SubscriptionTier.FREE_TRIAL,
    var subscriptionStatus: SubscriptionStatus = SubscriptionStatus.TRIAL,
    var subscriptionExpiryDate: Instant? = null,
    var liveTradingEnabled: Boolean = false,
    var trialDaysRemaining: Int = 30,
    var dflpParticipation: Boolean = true
) {
    fun isTrialActive(): Boolean {
        return subscriptionStatus == SubscriptionStatus.TRIAL &&
               Instant.now().isBefore(trialEndDate)
    }
    
    fun canLiveTrade(): Boolean {
        return liveTradingEnabled && 
               subscriptionStatus == SubscriptionStatus.ACTIVE &&
               subscriptionTier != SubscriptionTier.FREE_PAPER_ONLY &&
               subscriptionTier != SubscriptionTier.FREE_TRIAL
    }
    
    fun requiresPayment(): Boolean {
        return !isTrialActive() && 
               subscriptionTier == SubscriptionTier.FREE_TRIAL
    }
}

class SubscriptionManager {
    
    private val userAccounts = mutableMapOf<String, UserAccount>()
    
    /**
     * Create new user account with 30-day free trial
     */
    fun createNewAccount(userId: String, email: String): UserAccount {
        val now = Instant.now()
        val trialEnd = now.plus(30, ChronoUnit.DAYS)
        
        val account = UserAccount(
            userId = userId,
            email = email,
            accountCreatedDate = now,
            trialStartDate = now,
            trialEndDate = trialEnd,
            subscriptionTier = SubscriptionTier.FREE_TRIAL,
            subscriptionStatus = SubscriptionStatus.TRIAL,
            liveTradingEnabled = false,
            trialDaysRemaining = 30,
            dflpParticipation = true
        )
        
        userAccounts[userId] = account
        
        // Send welcome email
        sendWelcomeEmail(account)
        
        return account
    }
    
    /**
     * Check trial expiration (run daily via background job)
     */
    fun checkTrialExpiration(userId: String) {
        val account = userAccounts[userId] ?: return
        
        if (account.subscriptionStatus == SubscriptionStatus.TRIAL) {
            val now = Instant.now()
            
            if (now.isAfter(account.trialEndDate)) {
                // Trial has expired
                handleTrialExpiration(account)
            } else {
                // Update days remaining
                val daysRemaining = ChronoUnit.DAYS.between(now, account.trialEndDate).toInt()
                account.trialDaysRemaining = daysRemaining
                
                // Send reminder notifications
                when (daysRemaining) {
                    7 -> sendTrialReminderEmail(account, "7 days")
                    3 -> sendTrialReminderEmail(account, "3 days")
                    1 -> sendTrialReminderEmail(account, "1 day")
                }
            }
        }
    }
    
    private fun handleTrialExpiration(account: UserAccount) {
        // Disable live trading
        account.liveTradingEnabled = false
        account.subscriptionStatus = SubscriptionStatus.EXPIRED
        account.trialDaysRemaining = 0
        
        // Send expiration notification
        sendTrialExpiredEmail(account)
        
        println("Trial expired for user: ${account.userId}")
    }
    
    /**
     * Upgrade to paid subscription
     */
    fun upgradeSubscription(
        userId: String,
        tier: SubscriptionTier,
        paymentMethodId: String
    ): Result<String> {
        val account = userAccounts[userId] ?: return Result.failure(Exception("User not found"))
        
        // Process payment
        val paymentResult = processPayment(
            userId = userId,
            amount = getTierPrice(tier),
            paymentMethodId = paymentMethodId
        )
        
        return if (paymentResult.isSuccess) {
            // Activate subscription
            account.subscriptionTier = tier
            account.subscriptionStatus = SubscriptionStatus.ACTIVE
            account.liveTradingEnabled = true
            account.subscriptionExpiryDate = Instant.now().plus(30, ChronoUnit.DAYS)
            
            // Send confirmation
            sendSubscriptionConfirmationEmail(account, tier)
            
            Result.success("Subscription activated: $tier")
        } else {
            Result.failure(Exception("Payment failed: ${paymentResult.exceptionOrNull()?.message}"))
        }
    }
    
    /**
     * Continue with free paper trading (no payment)
     */
    fun continueWithFreeTier(userId: String): Result<String> {
        val account = userAccounts[userId] ?: return Result.failure(Exception("User not found"))
        
        account.subscriptionTier = SubscriptionTier.FREE_PAPER_ONLY
        account.subscriptionStatus = SubscriptionStatus.ACTIVE
        account.liveTradingEnabled = false
        
        return Result.success("Continuing with free paper trading")
    }
    
    /**
     * Get subscription tier pricing
     */
    fun getTierPrice(tier: SubscriptionTier): Money {
        return when (tier) {
            SubscriptionTier.PRO -> Money(29.00, "USD")
            SubscriptionTier.PREMIUM -> Money(99.00, "USD")
            SubscriptionTier.PROFESSIONAL -> Money(99.00, "USD")
            SubscriptionTier.INSTITUTIONAL -> Money(499.00, "USD")
            else -> Money(0.00, "USD")
        }
    }
    
    /**
     * Process payment via Stripe (placeholder)
     */
    private fun processPayment(
        userId: String,
        amount: Money,
        paymentMethodId: String
    ): Result<String> {
        // In production, integrate with Stripe API
        println("Processing payment: $${amount.amount} for user: $userId")
        
        // Simulate payment success
        return Result.success("payment_intent_${System.currentTimeMillis()}")
    }
    
    /**
     * Check if user can execute live trade
     */
    fun canExecuteLiveTrade(userId: String): Result<Boolean> {
        val account = userAccounts[userId] ?: return Result.failure(Exception("User not found"))
        
        return when {
            account.canLiveTrade() -> Result.success(true)
            account.requiresPayment() -> Result.failure(
                Exception("Your trial has ended. Upgrade to enable live trading.")
            )
            account.subscriptionStatus == SubscriptionStatus.PAYMENT_FAILED -> Result.failure(
                Exception("Payment failed. Please update your payment method.")
            )
            account.subscriptionTier == SubscriptionTier.FREE_PAPER_ONLY -> Result.failure(
                Exception("Upgrade to Pro to enable live trading.")
            )
            else -> Result.failure(
                Exception("Live trading is not available on your current plan.")
            )
        }
    }
    
    fun getUserAccount(userId: String): UserAccount? {
        return userAccounts[userId]
    }
    
    // Email notification methods (placeholders)
    private fun sendWelcomeEmail(account: UserAccount) {
        println("Welcome email sent to: ${account.email}")
    }
    
    private fun sendTrialReminderEmail(account: UserAccount, timeRemaining: String) {
        println("Trial reminder sent to: ${account.email} ($timeRemaining remaining)")
    }
    
    private fun sendTrialExpiredEmail(account: UserAccount) {
        println("Trial expired email sent to: ${account.email}")
    }
    
    private fun sendSubscriptionConfirmationEmail(account: UserAccount, tier: SubscriptionTier) {
        println("Subscription confirmation sent to: ${account.email} (Tier: $tier)")
    }
}
