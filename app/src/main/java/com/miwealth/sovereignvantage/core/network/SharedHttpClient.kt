package com.miwealth.sovereignvantage.core.network

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * SOVEREIGN VANTAGE V5.5.98 "ARTHUR EDITION"
 * Shared OkHttpClient Singleton
 *
 * All components should use [baseClient] or derive custom clients via
 * [baseClient.newBuilder()] which shares the connection pool and dispatcher.
 *
 * MEMORY IMPACT: Single connection pool (10 idle, 5-min keepalive) instead of
 * 11 separate pools. Saves ~30-50MB RAM under load.
 *
 * © 2025-2026 MiWealth Pty Ltd
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 */
object SharedHttpClient {

    /**
     * Base OkHttpClient — NO auth interceptors, suitable for exchange APIs.
     * Components needing custom timeouts should call [baseClient.newBuilder()].
     */
    val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Create a derived client with custom timeouts.
     * SHARES the connection pool and thread dispatcher with [baseClient].
     */
    fun withTimeouts(
        connectSeconds: Long = 30,
        readSeconds: Long = 30,
        writeSeconds: Long = 30
    ): OkHttpClient = baseClient.newBuilder()
        .connectTimeout(connectSeconds, TimeUnit.SECONDS)
        .readTimeout(readSeconds, TimeUnit.SECONDS)
        .writeTimeout(writeSeconds, TimeUnit.SECONDS)
        .build()

    /**
     * Fast client for quick API probes (schema learning, discovery).
     */
    val fastClient: OkHttpClient by lazy {
        withTimeouts(connectSeconds = 10, readSeconds = 15, writeSeconds = 10)
    }

    /**
     * Sentiment/scraping client with moderate timeouts.
     */
    val sentimentClient: OkHttpClient by lazy {
        withTimeouts(connectSeconds = 15, readSeconds = 15, writeSeconds = 15)
    }
}
