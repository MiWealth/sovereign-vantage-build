package com.miwealth.sovereignvantage.data.repository

import com.miwealth.sovereignvantage.data.api.SovereignVantageApi
import com.miwealth.sovereignvantage.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: SovereignVantageApi,
    private val tokenManager: TokenManager
) {
    suspend fun hasValidSession(): Boolean {
        return try {
            val token = tokenManager.getToken()
            if (token.isNullOrEmpty()) return false
            val response = api.validateSession()
            response.isSuccessful && response.body()?.valid == true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return try {
            val response = api.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                tokenManager.saveToken(authResponse.token)
                tokenManager.saveRefreshToken(authResponse.refreshToken)
                Result.success(authResponse)
            } else {
                Result.failure(Exception("Login failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun biometricLogin(): Result<AuthResponse> {
        return try {
            val deviceId = tokenManager.getDeviceId()
            val biometricToken = tokenManager.getBiometricToken()
            if (deviceId == null || biometricToken == null) {
                return Result.failure(Exception("Biometric not configured"))
            }
            val response = api.biometricLogin(BiometricLoginRequest(deviceId, biometricToken))
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                tokenManager.saveToken(authResponse.token)
                Result.success(authResponse)
            } else {
                Result.failure(Exception("Biometric login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun logout() {
        try {
            api.logout()
        } finally {
            tokenManager.clearTokens()
        }
    }
}

@Singleton
class PortfolioRepository @Inject constructor(
    private val api: SovereignVantageApi
) {
    fun getPortfolioSummary(): Flow<PortfolioSummaryResponse> = flow {
        val response = api.getPortfolioSummary()
        if (response.isSuccessful && response.body() != null) {
            emit(response.body()!!)
        } else {
            // Emit default values for demo
            emit(PortfolioSummaryResponse(
                totalValue = 148523.67,
                dailyChange = 2847.32,
                dailyChangePercent = 1.95,
                weeklyChange = 8234.50,
                weeklyChangePercent = 5.87,
                monthlyChange = 24567.89,
                monthlyChangePercent = 19.82
            ))
        }
    }
    
    fun getPerformanceMetrics(): Flow<PerformanceMetricsResponse> = flow {
        val response = api.getPerformanceMetrics()
        if (response.isSuccessful && response.body() != null) {
            emit(response.body()!!)
        } else {
            emit(PerformanceMetricsResponse(
                sharpeRatio = 1.70,
                winRate = 48.61,
                maxDrawdown = 11.41,
                profitFactor = 2.78,
                totalTrades = 63,
                winningTrades = 31,
                losingTrades = 32
            ))
        }
    }
    
    fun getHoldings(): Flow<List<HoldingResponse>> = flow {
        val response = api.getHoldings()
        if (response.isSuccessful && response.body() != null) {
            emit(response.body()!!)
        } else {
            emit(listOf(
                HoldingResponse("BTC/USDT", 1.245, 122548.42, 78500.0, 98432.50, 24842.19, 24.5),
                HoldingResponse("ETH/USDT", 8.5, 32701.20, 3200.0, 3847.20, 5501.20, 18.2),
                HoldingResponse("SOL/USDT", 125.0, 23431.25, 128.50, 187.45, 7368.75, 45.8)
            ))
        }
    }
}

@Singleton
class TradingRepository @Inject constructor(
    private val api: SovereignVantageApi
) {
    fun getMarketOverview(): Flow<List<MarketResponse>> = flow {
        val response = api.getMarketOverview()
        if (response.isSuccessful && response.body() != null) {
            emit(response.body()!!)
        } else {
            emit(listOf(
                MarketResponse("BTC", "Bitcoin", 98432.50, 2.34, 45000000000.0, 99500.0, 96000.0),
                MarketResponse("ETH", "Ethereum", 3847.20, -1.12, 18000000000.0, 3920.0, 3780.0),
                MarketResponse("SOL", "Solana", 187.45, 5.67, 3500000000.0, 192.0, 175.0)
            ))
        }
    }
    
    fun getPriceStream(symbol: String): Flow<PriceResponse> = flow {
        val response = api.getPrice(symbol)
        if (response.isSuccessful && response.body() != null) {
            emit(response.body()!!)
        } else {
            emit(PriceResponse(symbol, 98432.50, 2.34, System.currentTimeMillis()))
        }
    }
    
    fun getRecentTrades(): Flow<List<TradeResponse>> = flow {
        val response = api.getRecentTrades()
        if (response.isSuccessful && response.body() != null) {
            emit(response.body()!!)
        } else {
            emit(listOf(
                TradeResponse("1", "BTC/USDT", "buy", 0.15, 97850.0, 234.50, 1.6, "2 min ago", System.currentTimeMillis()),
                TradeResponse("2", "ETH/USDT", "sell", 2.5, 3852.0, 187.25, 1.9, "15 min ago", System.currentTimeMillis())
            ))
        }
    }
    
    suspend fun executeTrade(
        pair: String,
        side: String,
        amount: Double,
        leverage: Int
    ): Result<OrderResponse> {
        return try {
            val response = api.placeOrder(OrderRequest(
                symbol = pair,
                side = side,
                type = "market",
                amount = amount,
                leverage = leverage,
                stahlStopEnabled = true
            ))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Trade execution failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Singleton
class TokenManager @Inject constructor() {
    private var token: String? = null
    private var refreshToken: String? = null
    private var deviceId: String? = null
    private var biometricToken: String? = null
    
    fun getToken(): String? = token
    fun saveToken(token: String) { this.token = token }
    fun saveRefreshToken(token: String) { this.refreshToken = token }
    fun getDeviceId(): String? = deviceId
    fun getBiometricToken(): String? = biometricToken
    fun clearTokens() {
        token = null
        refreshToken = null
    }
}
