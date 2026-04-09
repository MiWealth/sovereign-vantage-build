package com.miwealth.sovereignvantage.data.model

import com.google.gson.annotations.SerializedName

// Auth Models
data class LoginRequest(
    val email: String,
    val password: String
)

data class BiometricLoginRequest(
    val deviceId: String,
    val biometricToken: String
)

data class AuthResponse(
    val token: String,
    val refreshToken: String,
    val user: UserResponse
)

data class SessionResponse(
    val valid: Boolean,
    val expiresAt: Long
)

data class UserResponse(
    val id: String,
    val email: String,
    val name: String,
    val tier: String
)

// Portfolio Models
data class PortfolioSummaryResponse(
    val totalValue: Double,
    val dailyChange: Double,
    val dailyChangePercent: Double,
    val weeklyChange: Double,
    val weeklyChangePercent: Double,
    val monthlyChange: Double,
    val monthlyChangePercent: Double
)

data class HoldingResponse(
    val symbol: String,
    val amount: Double,
    val value: Double,
    val avgPrice: Double,
    val currentPrice: Double,
    val pnl: Double,
    val pnlPercent: Double
)

data class PerformanceMetricsResponse(
    val sharpeRatio: Double,
    val sortinoRatio: Double,
    val winRate: Double,
    val maxDrawdown: Double,
    val profitFactor: Double,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int
)

// Market Models
data class MarketResponse(
    val symbol: String,
    val name: String,
    val price: Double,
    val change24h: Double,
    val volume24h: Double,
    val high24h: Double,
    val low24h: Double
)

data class PriceResponse(
    val symbol: String,
    val price: Double,
    val change24h: Double,
    val timestamp: Long
)

// Trading Models
data class OrderRequest(
    val symbol: String,
    val side: String, // "buy" or "sell"
    val type: String, // "market", "limit", "stop"
    val amount: Double,
    val price: Double? = null,
    val stopPrice: Double? = null,
    val leverage: Int = 1,
    @SerializedName("stahl_stop_enabled")
    val stahlStopEnabled: Boolean = true,
    // BUILD #428: Board tagging for position tracking
    val board: String? = null  // "MAIN" or "HEDGE_FUND"
)

data class OrderResponse(
    val id: String,
    val symbol: String,
    val side: String,
    val type: String,
    val amount: Double,
    val price: Double,
    val status: String,
    val createdAt: Long,
    val filledAt: Long?
)

data class TradeResponse(
    val id: String,
    val symbol: String,
    val type: String,
    val amount: Double,
    val price: Double,
    val profit: Double,
    val profitPercent: Double,
    val timeAgo: String,
    val timestamp: Long
)

// Signal Models
data class SignalResponse(
    val id: String,
    val symbol: String,
    val action: String, // "long" or "short"
    val entry: Double,
    val target: Double,
    val stop: Double,
    val confidence: Int,
    val reason: String,
    val createdAt: Long
)

// User Models
data class UserProfileResponse(
    val id: String,
    val email: String,
    val name: String,
    val avatar: String?,
    val tier: String,
    val createdAt: Long
)

data class UpdateProfileRequest(
    val name: String?,
    val avatar: String?
)

data class SubscriptionResponse(
    val tier: String,
    val status: String,
    val expiresAt: Long,
    val features: List<String>
)
