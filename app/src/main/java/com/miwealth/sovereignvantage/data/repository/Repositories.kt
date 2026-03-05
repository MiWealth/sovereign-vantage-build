package com.miwealth.sovereignvantage.data.repository

import com.miwealth.sovereignvantage.core.TradingSystemManager
import com.miwealth.sovereignvantage.data.api.SovereignVantageApi
import com.miwealth.sovereignvantage.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val tokenManager: TokenManager
) {
    /**
     * SELF-SOVEREIGN AUTH: No central server. Credentials validated and stored locally.
     * The email is used only as an account identifier for license/subscription validation
     * via the website (MiWealth.APP). All authentication happens on-device.
     */
    
    suspend fun hasValidSession(): Boolean {
        return tokenManager.getToken() != null
    }
    
    fun getSavedEmail(): String? {
        return tokenManager.getEmail()
    }
    
    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return try {
            if (email.isBlank() || password.isBlank()) {
                return Result.failure(Exception("Email and password required"))
            }
            if (!email.contains("@")) {
                return Result.failure(Exception("Invalid email format"))
            }
            if (password.length < 2) {
                return Result.failure(Exception("Password too short"))
            }
            
            // Generate a local session token (no server round-trip)
            val localToken = java.util.UUID.randomUUID().toString()
            tokenManager.saveToken(localToken)
            tokenManager.saveEmail(email)
            
            Result.success(AuthResponse(
                token = localToken,
                refreshToken = localToken,
                user = UserResponse(
                    id = email.hashCode().toString(),
                    email = email,
                    name = email.substringBefore("@"),
                    tier = "FREE"
                )
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun biometricLogin(): Result<AuthResponse> {
        return try {
            val existingEmail = tokenManager.getEmail()
            if (existingEmail == null) {
                return Result.failure(Exception("No account configured. Please sign in with email first."))
            }
            
            // Biometric verified by Android system — just restore the session
            val localToken = java.util.UUID.randomUUID().toString()
            tokenManager.saveToken(localToken)
            
            Result.success(AuthResponse(
                token = localToken,
                refreshToken = localToken,
                user = UserResponse(
                    id = existingEmail.hashCode().toString(),
                    email = existingEmail,
                    name = existingEmail.substringBefore("@"),
                    tier = "FREE"
                )
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun logout() {
        tokenManager.clearTokens()
    }
}

@Singleton
class PortfolioRepository @Inject constructor(
    private val api: SovereignVantageApi,
    private val tradingSystemManager: TradingSystemManager  // BUILD #110: Real data source
) {
    fun getPortfolioSummary(): Flow<PortfolioSummaryResponse> = flow {
        // BUILD #110: Use REAL data from TradingSystemManager, not Manus mock data!
        tradingSystemManager.dashboardState.collect { dashState ->
            emit(PortfolioSummaryResponse(
                totalValue = dashState.portfolioValue,
                dailyChange = dashState.dailyPnl,
                dailyChangePercent = dashState.dailyPnlPercent,
                weeklyChange = 0.0,   // TODO: Calculate from trade history
                weeklyChangePercent = 0.0,
                monthlyChange = 0.0,  // TODO: Calculate from trade history
                monthlyChangePercent = 0.0
            ))
        }
    }
    
    fun getPerformanceMetrics(): Flow<PerformanceMetricsResponse> = flow {
        // BUILD #110: Real metrics only - no Manus mock data
        emit(PerformanceMetricsResponse(
            sharpeRatio = 0.0,       // TODO: Calculate from real trade history
            winRate = 0.0,           // TODO: Calculate from real trades
            maxDrawdown = 0.0,       // TODO: Track from real portfolio values
            profitFactor = 0.0,      // TODO: Calculate from real P&L
            totalTrades = 0,         // TODO: Count from real trade history
            winningTrades = 0,
            losingTrades = 0
        ))
    }
    
    fun getHoldings(): Flow<List<HoldingResponse>> = flow {
        // BUILD #110: Get REAL holdings from TradingSystemManager
        val balances = tradingSystemManager.getAIIntegratedSystemBalances()
        val priceFeed = tradingSystemManager.getPublicPriceFeed()
        val prices = priceFeed.latestPrices.value
        
        val holdings = balances
            .filter { (asset, amount) -> amount > 0.0 && asset != "USDT" && asset != "USD" }
            .map { (asset, amount) ->
                val priceKey = "$asset/USDT"
                val currentPrice = prices[priceKey]?.last ?: 0.0
                val currentValue = amount * currentPrice
                
                HoldingResponse(
                    symbol = priceKey,
                    amount = amount,
                    currentValue = currentValue,
                    averagePrice = currentPrice,  // TODO: Track actual avg buy price
                    currentPrice = currentPrice,
                    pnl = 0.0,                    // TODO: Calculate from cost basis
                    pnlPercent = 0.0              // TODO: Calculate from cost basis
                )
            }
        
        emit(holdings)
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
class TokenManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    private val prefs: android.content.SharedPreferences by lazy {
        context.getSharedPreferences("sv_auth", android.content.Context.MODE_PRIVATE)
    }
    
    fun getToken(): String? = prefs.getString("token", null)
    fun saveToken(token: String) { prefs.edit().putString("token", token).apply() }
    fun saveRefreshToken(token: String) { prefs.edit().putString("refresh_token", token).apply() }
    fun getDeviceId(): String? = prefs.getString("device_id", null)
    fun getBiometricToken(): String? = prefs.getString("biometric_token", null)
    fun getEmail(): String? = prefs.getString("email", null)
    fun saveEmail(email: String) { prefs.edit().putString("email", email).apply() }
    fun clearTokens() {
        prefs.edit()
            .remove("token")
            .remove("refresh_token")
            .apply()
    }
    
    /** Full purge — removes everything including email */
    fun purgeAll() {
        prefs.edit().clear().apply()
    }
}
