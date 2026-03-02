package com.miwealth.sovereignvantage.data.api

import com.miwealth.sovereignvantage.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface SovereignVantageApi {
    
    // Authentication
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
    
    @POST("auth/biometric")
    suspend fun biometricLogin(@Body request: BiometricLoginRequest): Response<AuthResponse>
    
    @POST("auth/logout")
    suspend fun logout(): Response<Unit>
    
    @GET("auth/session")
    suspend fun validateSession(): Response<SessionResponse>
    
    // Portfolio
    @GET("portfolio/summary")
    suspend fun getPortfolioSummary(): Response<PortfolioSummaryResponse>
    
    @GET("portfolio/holdings")
    suspend fun getHoldings(): Response<List<HoldingResponse>>
    
    @GET("portfolio/performance")
    suspend fun getPerformanceMetrics(): Response<PerformanceMetricsResponse>
    
    // Trading
    @GET("market/overview")
    suspend fun getMarketOverview(): Response<List<MarketResponse>>
    
    @GET("market/price/{symbol}")
    suspend fun getPrice(@Path("symbol") symbol: String): Response<PriceResponse>
    
    @POST("trading/order")
    suspend fun placeOrder(@Body request: OrderRequest): Response<OrderResponse>
    
    @GET("trading/orders")
    suspend fun getOrders(): Response<List<OrderResponse>>
    
    @GET("trading/trades")
    suspend fun getRecentTrades(): Response<List<TradeResponse>>
    
    // AI Signals
    @GET("signals/active")
    suspend fun getActiveSignals(): Response<List<SignalResponse>>
    
    @POST("signals/execute/{signalId}")
    suspend fun executeSignal(@Path("signalId") signalId: String): Response<OrderResponse>
    
    // User
    @GET("user/profile")
    suspend fun getUserProfile(): Response<UserProfileResponse>
    
    @PUT("user/profile")
    suspend fun updateUserProfile(@Body request: UpdateProfileRequest): Response<UserProfileResponse>
    
    @GET("user/subscription")
    suspend fun getSubscription(): Response<SubscriptionResponse>
}
