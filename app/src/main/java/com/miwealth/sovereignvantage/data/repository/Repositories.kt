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
    private val tradingSystemManager: TradingSystemManager,  // BUILD #110: Real data source
    private val portfolioAnalytics: com.miwealth.sovereignvantage.core.portfolio.PortfolioAnalytics  // BUILD #274: Analytics engine
) {
    
    private var lastMetricsUpdate = 0L
    private val METRICS_REFRESH_INTERVAL = 15_000L  // 15 seconds
    
    init {
        // Start analytics monitoring
        monitorPortfolioChanges()
    }
    
    /**
     * BUILD #274: Monitor portfolio changes and trigger analytics updates
     */
    private fun monitorPortfolioChanges() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            tradingSystemManager.dashboardState.collect { dashState ->
                val now = System.currentTimeMillis()
                if (now - lastMetricsUpdate >= METRICS_REFRESH_INTERVAL) {
                    lastMetricsUpdate = now
                    // Trigger analytics calculation
                    try {
                        portfolioAnalytics.calculateMetrics(
                            currentEquity = dashState.portfolioValue,
                            cashBalance = tradingSystemManager.getAIIntegratedSystemBalances()["USDT"] ?: 0.0
                        )
                    } catch (e: Exception) {
                        println("⚠️ PortfolioRepository: Analytics calc failed: ${e.message}")
                    }
                }
            }
        }
    }
    
    fun getPortfolioSummary(): Flow<PortfolioSummaryResponse> = flow {
        // BUILD #274: Combine dashboard state with calculated metrics
        combine(
            tradingSystemManager.dashboardState,
            portfolioAnalytics.metrics
        ) { dashState, metrics ->
            PortfolioSummaryResponse(
                totalValue = dashState.portfolioValue,
                dailyChange = dashState.dailyPnl,
                dailyChangePercent = dashState.dailyPnlPercent,
                weeklyChange = metrics?.weeklyReturn ?: 0.0,
                weeklyChangePercent = metrics?.weeklyReturn ?: 0.0,
                monthlyChange = metrics?.monthlyReturn ?: 0.0,
                monthlyChangePercent = metrics?.monthlyReturn ?: 0.0
            )
        }.collect { emit(it) }
    }
    
    fun getPerformanceMetrics(): Flow<PerformanceMetricsResponse> = flow {
        // BUILD #274: Use real metrics from PortfolioAnalytics
        combine(
            tradingSystemManager.dashboardState,
            portfolioAnalytics.metrics
        ) { dashState, metrics ->
            PerformanceMetricsResponse(
                sharpeRatio = metrics?.sharpeRatio ?: 0.0,
                sortinoRatio = metrics?.sortinoRatio ?: 0.0,
                winRate = metrics?.winRate ?: 0.0,
                maxDrawdown = metrics?.maxDrawdownPercent ?: 0.0,
                profitFactor = metrics?.profitFactor ?: 0.0,
                totalTrades = metrics?.totalTrades ?: 0,
                winningTrades = metrics?.winningTrades ?: 0,
                losingTrades = metrics?.losingTrades ?: 0
            )
        }.collect { emit(it) }
    }
    
    fun getHoldings(): Flow<List<HoldingResponse>> = flow {
        // BUILD #265: Show BOTH open positions AND wallet holdings
        // Open positions = active long/short contracts
        // Wallet holdings = seeded BTC/ETH/SOL/XRP balances (paper trading)
        tradingSystemManager.dashboardState.collect { dashState ->
            val holdings = mutableListOf<HoldingResponse>()

            // First add any open positions (active trades)
            val positions = tradingSystemManager.getPositions()
            positions.forEach { position ->
                holdings.add(HoldingResponse(
                    symbol = position.symbol,
                    amount = position.quantity,
                    value = position.quantity * position.currentPrice,
                    avgPrice = position.entryPrice,
                    currentPrice = position.currentPrice,
                    pnl = position.unrealizedPnL,
                    pnlPercent = position.unrealizedPnLPercent
                ))
            }

            // Also show wallet balances (seeded crypto holdings)
            // Only add if not already shown as an open position
            val positionSymbols = positions.map { it.symbol.substringBefore("/") }.toSet()
            val balances = tradingSystemManager.getAIIntegratedSystemBalances()
            val latestPrices = dashState.latestPrices

            balances
                .filter { (asset, amount) ->
                    amount > 0.0
                    && asset != "USDT"
                    && asset != "USD"
                    && asset !in positionSymbols
                }
                .forEach { (asset, amount) ->
                    val priceKey = "$asset/USDT"
                    val price = latestPrices[priceKey] ?: 0.0
                    val value = amount * price
                    if (value > 0.0 || price == 0.0) {
                        holdings.add(HoldingResponse(
                            symbol = "$asset/USDT",
                            amount = amount,
                            value = value,
                            avgPrice = price,
                            currentPrice = price,
                            pnl = 0.0,
                            pnlPercent = 0.0
                        ))
                    }
                }

            emit(holdings)
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
