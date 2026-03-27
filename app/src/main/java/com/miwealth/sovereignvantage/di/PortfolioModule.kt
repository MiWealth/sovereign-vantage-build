package com.miwealth.sovereignvantage.di

import android.content.Context
import com.miwealth.sovereignvantage.core.portfolio.PortfolioAnalytics
import com.miwealth.sovereignvantage.core.portfolio.TradeRecorder
import com.miwealth.sovereignvantage.data.local.EnhancedTradeDao
import com.miwealth.sovereignvantage.data.local.EquitySnapshotDao
import com.miwealth.sovereignvantage.data.local.TaxLotDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * BUILD #274: PORTFOLIO MODULE
 * 
 * Provides portfolio analytics and trade recording infrastructure.
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */

@Module
@InstallIn(SingletonComponent::class)
object PortfolioModule {
    
    // BUILD #279: Provide DAOs from TradeDatabase
    @Provides
    @Singleton
    fun provideEnhancedTradeDao(database: com.miwealth.sovereignvantage.data.local.TradeDatabase): EnhancedTradeDao {
        return database.enhancedTradeDao()
    }
    
    @Provides
    @Singleton
    fun provideEquitySnapshotDao(database: com.miwealth.sovereignvantage.data.local.TradeDatabase): EquitySnapshotDao {
        return database.equitySnapshotDao()
    }
    
    @Provides
    @Singleton
    fun provideTaxLotDao(database: com.miwealth.sovereignvantage.data.local.TradeDatabase): TaxLotDao {
        return database.taxLotDao()
    }
    
    @Provides
    @Singleton
    fun providePortfolioAnalyticsScope(): CoroutineScope {
        return CoroutineScope(Dispatchers.Default + SupervisorJob())
    }
    
    @Provides
    @Singleton
    fun providePortfolioAnalytics(
        enhancedTradeDao: EnhancedTradeDao,
        equitySnapshotDao: EquitySnapshotDao,
        taxLotDao: TaxLotDao,
        scope: CoroutineScope
    ): PortfolioAnalytics {
        return PortfolioAnalytics(
            enhancedTradeDao = enhancedTradeDao,
            equitySnapshotDao = equitySnapshotDao,
            taxLotDao = taxLotDao,
            scope = scope
        )
    }
    
    @Provides
    @Singleton
    fun provideTradeRecorder(
        enhancedTradeDao: EnhancedTradeDao,
        scope: CoroutineScope
    ): TradeRecorder {
        return TradeRecorder(
            enhancedTradeDao = enhancedTradeDao,
            scope = scope
        )
    }
    
    @Provides
    @Singleton
    fun provideEquitySnapshotRecorder(
        equitySnapshotDao: EquitySnapshotDao,
        scope: CoroutineScope
    ): com.miwealth.sovereignvantage.core.portfolio.EquitySnapshotRecorder {
        return com.miwealth.sovereignvantage.core.portfolio.EquitySnapshotRecorder(
            equitySnapshotDao = equitySnapshotDao,
            scope = scope
        )
    }
}
