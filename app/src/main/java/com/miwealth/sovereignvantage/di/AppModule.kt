package com.miwealth.sovereignvantage.di

import android.content.Context
import com.miwealth.sovereignvantage.core.TradingSystemManager
import com.miwealth.sovereignvantage.data.api.SovereignVantageApi
import com.miwealth.sovereignvantage.data.repository.TokenManager
import com.miwealth.sovereignvantage.data.local.TradeDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * Hilt Dependency Injection Module
 * 
 * © 2025-2026 MiWealth Pty Ltd
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    private const val BASE_URL = "https://api.sovereignvantage.com/v1/"
    
    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenManager: TokenManager): Interceptor {
        return Interceptor { chain ->
            val request = chain.request().newBuilder()
            tokenManager.getToken()?.let { token ->
                request.addHeader("Authorization", "Bearer $token")
            }
            request.addHeader("X-Client-Version", "5.7.0")
            request.addHeader("X-Client-Platform", "android")
            chain.proceed(request.build())
        }
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: Interceptor): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideSovereignVantageApi(retrofit: Retrofit): SovereignVantageApi {
        return retrofit.create(SovereignVantageApi::class.java)
    }

    @Provides
    @Singleton
    fun provideTradeDatabase(@ApplicationContext context: Context): TradeDatabase {
        return TradeDatabase.getInstance(context)
    }
}