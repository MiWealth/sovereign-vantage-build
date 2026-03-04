/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * EDUCATION SYSTEM - HILT DEPENDENCY INJECTION MODULE
 * 
 * Provides all education system dependencies to the DI graph.
 * 
 * © 2025-2026 MiWealth Pty Ltd
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */


package com.miwealth.sovereignvantage.education

import android.content.Context
import com.miwealth.sovereignvantage.data.local.TradeDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the repository interface to its implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EducationBindsModule {

    @Binds
    @Singleton
    abstract fun bindTradingProgrammeRepository(
        impl: TradingProgrammeRepositoryImpl
    ): TradingProgrammeRepository
}

/**
 * Provides concrete instances that can't be constructor-injected.
 */
@Module
@InstallIn(SingletonComponent::class)
object EducationProvidesModule {

    @Provides
    @Singleton
    fun provideTradingProgrammeDao(
        database: TradeDatabase
    ): TradingProgrammeDao {
        return database.tradingProgrammeDao()
    }

    @Provides
    @Singleton
    fun provideQuizQuestionBank(): QuizQuestionBank {
        return QuizQuestionBank
    }
}
