/*
 * Sovereign Vantage - Arthur Edition
 * Layer2Assets.kt - Layer 2 scaling solution asset definitions
 * 
 * Copyright 2025-2026 MiWealth Pty Ltd
 * Creator: Mike Stahl
 * Co-Founder (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */


package com.miwealth.sovereignvantage.data.catalog

import com.miwealth.sovereignvantage.data.models.AssetCategory
import com.miwealth.sovereignvantage.data.models.CryptoAsset
import com.miwealth.sovereignvantage.data.models.MarketCapTier
import com.miwealth.sovereignvantage.data.models.VolatilityTier

/**
 * Layer 2 scaling solutions - built on top of Layer 1 blockchains.
 * 
 * Categories:
 * - Optimistic Rollups: ARB, OP, BASE, MANTLE, METIS, BOBA, BLAST, MODE
 * - ZK Rollups: ZK (zkSync), STRK (Starknet), LRC (Loopring), SCROLL
 * - Sidechains: MATIC/POL (Polygon)
 * - Validiums: IMX (Immutable X)
 * 
 * Generally higher volatility than L1 but strong growth potential.
 * Risk params: Kelly 0.7, max 8%, stop 12%
 */
object Layer2Assets {
    
    // ========== OPTIMISTIC ROLLUPS ==========
    
    val ARB = CryptoAsset(
        symbol = "ARB",
        name = "Arbitrum",
        category = AssetCategory.LAYER_2,
        subcategory = "Optimistic Rollup",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.MID,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.7,
        maxPositionPercent = 0.08,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 4.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "arbitrum"
    )
    
    val OP = CryptoAsset(
        symbol = "OP",
        name = "Optimism",
        category = AssetCategory.LAYER_2,
        subcategory = "Optimistic Rollup",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.MID,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.7,
        maxPositionPercent = 0.08,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 4.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "optimism"
    )
    
    val MNT = CryptoAsset(
        symbol = "MNT",
        name = "Mantle",
        category = AssetCategory.LAYER_2,
        subcategory = "Optimistic Rollup",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.MID,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.6,
        maxPositionPercent = 0.06,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 4.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "mantle"
    )
    
    val METIS = CryptoAsset(
        symbol = "METIS",
        name = "Metis",
        category = AssetCategory.LAYER_2,
        subcategory = "Optimistic Rollup",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.5,
        maxPositionPercent = 0.04,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.5,
        onKraken = false,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "metis-token"
    )
    
    val BOBA = CryptoAsset(
        symbol = "BOBA",
        name = "Boba Network",
        category = AssetCategory.LAYER_2,
        subcategory = "Optimistic Rollup",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.MICRO,
        volatilityTier = VolatilityTier.EXTREME,
        kellyMultiplier = 0.3,
        maxPositionPercent = 0.02,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 2.5,
        onKraken = false,
        onBinance = true,
        onBybit = false,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = false,
        canShortMargin = false,
        coingeckoId = "boba-network"
    )
    
    val BLAST = CryptoAsset(
        symbol = "BLAST",
        name = "Blast",
        category = AssetCategory.LAYER_2,
        subcategory = "Optimistic Rollup",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.EXTREME,
        kellyMultiplier = 0.4,
        maxPositionPercent = 0.03,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "blast"
    )
    
    val MODE = CryptoAsset(
        symbol = "MODE",
        name = "Mode",
        category = AssetCategory.LAYER_2,
        subcategory = "Optimistic Rollup",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.MICRO,
        volatilityTier = VolatilityTier.EXTREME,
        kellyMultiplier = 0.3,
        maxPositionPercent = 0.02,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 2.5,
        onKraken = false,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = false,
        canShortMargin = false,
        coingeckoId = "mode"
    )
    
    // ========== ZK ROLLUPS ==========
    
    val ZK = CryptoAsset(
        symbol = "ZK",
        name = "zkSync",
        category = AssetCategory.LAYER_2,
        subcategory = "ZK Rollup",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.5,
        maxPositionPercent = 0.05,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "zksync"
    )
    
    val STRK = CryptoAsset(
        symbol = "STRK",
        name = "Starknet",
        category = AssetCategory.LAYER_2,
        subcategory = "ZK Rollup",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.5,
        maxPositionPercent = 0.05,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "starknet"
    )
    
    val LRC = CryptoAsset(
        symbol = "LRC",
        name = "Loopring",
        category = AssetCategory.LAYER_2,
        subcategory = "ZK Rollup",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.5,
        maxPositionPercent = 0.04,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "loopring"
    )
    
    // ========== SIDECHAINS ==========
    
    val POL = CryptoAsset(
        symbol = "POL",
        name = "Polygon",
        category = AssetCategory.LAYER_2,
        subcategory = "Sidechain",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.MID,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.7,
        maxPositionPercent = 0.08,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 4.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "polygon-ecosystem-token"
    )
    
    // Note: MATIC has migrated to POL, but keeping for compatibility
    val MATIC = CryptoAsset(
        symbol = "MATIC",
        name = "Polygon (Legacy)",
        category = AssetCategory.LAYER_2,
        subcategory = "Sidechain",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.MID,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.7,
        maxPositionPercent = 0.08,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 4.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        isActive = false,  // Deprecated - use POL
        coingeckoId = "matic-network"
    )
    
    // ========== VALIDIUMS ==========
    
    val IMX = CryptoAsset(
        symbol = "IMX",
        name = "Immutable",
        category = AssetCategory.LAYER_2,
        subcategory = "Validium",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.MID,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.6,
        maxPositionPercent = 0.06,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 4.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "immutable-x"
    )
    
    // ========== AGGREGATED LIST ==========
    
    /**
     * All Layer 2 assets for catalog seeding.
     * Excludes deprecated MATIC (use POL instead).
     */
    fun getAll(): List<CryptoAsset> = listOf(
        // Optimistic Rollups
        ARB, OP, MNT, METIS, BOBA, BLAST, MODE,
        // ZK Rollups
        ZK, STRK, LRC,
        // Sidechains
        POL,
        // Validiums
        IMX
    )
    
    /**
     * All including deprecated for migration support.
     */
    fun getAllIncludingDeprecated(): List<CryptoAsset> = getAll() + MATIC
    
    /**
     * Optimistic rollups only.
     */
    fun getOptimisticRollups(): List<CryptoAsset> = listOf(
        ARB, OP, MNT, METIS, BOBA, BLAST, MODE
    )
    
    /**
     * ZK rollups only.
     */
    fun getZkRollups(): List<CryptoAsset> = listOf(
        ZK, STRK, LRC
    )
    
    /**
     * Top L2s by market cap (for conservative strategies).
     */
    fun getTopTier(): List<CryptoAsset> = listOf(
        ARB, OP, POL, IMX
    )
    
    /**
     * Assets with futures shorting available.
     */
    fun getShortable(): List<CryptoAsset> = getAll().filter { it.canShortFutures }
    
    /**
     * Count of Layer 2 assets.
     */
    fun count(): Int = getAll().size
}
