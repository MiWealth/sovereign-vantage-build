/*
 * Sovereign Vantage - Arthur Edition
 * DeFiAssets.kt - DeFi protocol token definitions
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
 * DeFi protocol tokens - governance and utility tokens for decentralised finance.
 * 
 * Categories:
 * - DEX: Uniswap, SushiSwap, Curve, 1inch, PancakeSwap, Jupiter, Raydium
 * - Lending: Aave, Compound, Maker
 * - Derivatives: dYdX, GMX, Synthetix
 * - Liquid Staking: Lido, Rocket Pool
 * - Yield: Yearn, Convex, Pendle
 * 
 * Risk params: Kelly 0.6, max 8%, stop 15%
 */
object DeFiAssets {
    
    // ========== DEX TOKENS ==========
    
    val UNI = CryptoAsset(
        symbol = "UNI",
        name = "Uniswap",
        category = AssetCategory.DEFI,
        subcategory = "DEX",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.MID,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.6,
        maxPositionPercent = 0.08,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 4.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "uniswap"
    )
    
    val AAVE = CryptoAsset(
        symbol = "AAVE",
        name = "Aave",
        category = AssetCategory.DEFI,
        subcategory = "Lending",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.MID,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.6,
        maxPositionPercent = 0.08,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 4.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "aave"
    )
    
    val MKR = CryptoAsset(
        symbol = "MKR",
        name = "Maker",
        category = AssetCategory.DEFI,
        subcategory = "Lending",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.MID,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.6,
        maxPositionPercent = 0.08,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 4.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "maker"
    )
    
    val CRV = CryptoAsset(
        symbol = "CRV",
        name = "Curve DAO",
        category = AssetCategory.DEFI,
        subcategory = "DEX",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.5,
        maxPositionPercent = 0.06,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "curve-dao-token"
    )
    
    val SNX = CryptoAsset(
        symbol = "SNX",
        name = "Synthetix",
        category = AssetCategory.DEFI,
        subcategory = "Derivatives",
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
        coingeckoId = "havven"
    )
    
    val COMP = CryptoAsset(
        symbol = "COMP",
        name = "Compound",
        category = AssetCategory.DEFI,
        subcategory = "Lending",
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
        coingeckoId = "compound-governance-token"
    )
    
    val SUSHI = CryptoAsset(
        symbol = "SUSHI",
        name = "SushiSwap",
        category = AssetCategory.DEFI,
        subcategory = "DEX",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.4,
        maxPositionPercent = 0.04,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "sushi"
    )
    
    val ONEINCH = CryptoAsset(
        symbol = "1INCH",
        name = "1inch",
        category = AssetCategory.DEFI,
        subcategory = "DEX Aggregator",
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
        coingeckoId = "1inch"
    )
    
    val CAKE = CryptoAsset(
        symbol = "CAKE",
        name = "PancakeSwap",
        category = AssetCategory.DEFI,
        subcategory = "DEX",
        primaryChain = "BNB Chain",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.5,
        maxPositionPercent = 0.05,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.5,
        onKraken = false,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "pancakeswap-token"
    )
    
    val DYDX = CryptoAsset(
        symbol = "DYDX",
        name = "dYdX",
        category = AssetCategory.DEFI,
        subcategory = "Derivatives",
        primaryChain = "dYdX Chain",
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
        coingeckoId = "dydx-chain"
    )
    
    val GMX = CryptoAsset(
        symbol = "GMX",
        name = "GMX",
        category = AssetCategory.DEFI,
        subcategory = "Derivatives",
        primaryChain = "Arbitrum",
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
        coingeckoId = "gmx"
    )
    
    // ========== SOLANA DEFI ==========
    
    val JUP = CryptoAsset(
        symbol = "JUP",
        name = "Jupiter",
        category = AssetCategory.DEFI,
        subcategory = "DEX Aggregator",
        primaryChain = "Solana",
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
        coingeckoId = "jupiter-exchange-solana"
    )
    
    val RAY = CryptoAsset(
        symbol = "RAY",
        name = "Raydium",
        category = AssetCategory.DEFI,
        subcategory = "DEX",
        primaryChain = "Solana",
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
        coingeckoId = "raydium"
    )
    
    val ORCA = CryptoAsset(
        symbol = "ORCA",
        name = "Orca",
        category = AssetCategory.DEFI,
        subcategory = "DEX",
        primaryChain = "Solana",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.4,
        maxPositionPercent = 0.04,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.0,
        onKraken = true,
        onBinance = true,
        onBybit = false,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = false,
        canShortMargin = false,
        coingeckoId = "orca"
    )
    
    // ========== LIQUID STAKING ==========
    
    val LDO = CryptoAsset(
        symbol = "LDO",
        name = "Lido DAO",
        category = AssetCategory.DEFI,
        subcategory = "Liquid Staking",
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
        canShortMargin = true,
        coingeckoId = "lido-dao"
    )
    
    val RPL = CryptoAsset(
        symbol = "RPL",
        name = "Rocket Pool",
        category = AssetCategory.DEFI,
        subcategory = "Liquid Staking",
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
        canShortFutures = false,
        canShortMargin = false,
        coingeckoId = "rocket-pool"
    )
    
    val EIGEN = CryptoAsset(
        symbol = "EIGEN",
        name = "EigenLayer",
        category = AssetCategory.DEFI,
        subcategory = "Restaking",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.MID,
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
        coingeckoId = "eigenlayer"
    )
    
    // ========== YIELD ==========
    
    val YFI = CryptoAsset(
        symbol = "YFI",
        name = "yearn.finance",
        category = AssetCategory.DEFI,
        subcategory = "Yield",
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
        coingeckoId = "yearn-finance"
    )
    
    val CVX = CryptoAsset(
        symbol = "CVX",
        name = "Convex Finance",
        category = AssetCategory.DEFI,
        subcategory = "Yield",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.4,
        maxPositionPercent = 0.04,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = false,
        canShortMargin = false,
        coingeckoId = "convex-finance"
    )
    
    val PENDLE = CryptoAsset(
        symbol = "PENDLE",
        name = "Pendle",
        category = AssetCategory.DEFI,
        subcategory = "Yield",
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
        coingeckoId = "pendle"
    )
    
    val BAL = CryptoAsset(
        symbol = "BAL",
        name = "Balancer",
        category = AssetCategory.DEFI,
        subcategory = "DEX",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.4,
        maxPositionPercent = 0.04,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = false,
        canShortMargin = false,
        coingeckoId = "balancer"
    )
    
    val FXS = CryptoAsset(
        symbol = "FXS",
        name = "Frax Share",
        category = AssetCategory.DEFI,
        subcategory = "Stablecoin Protocol",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.4,
        maxPositionPercent = 0.04,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = false,
        canShortMargin = false,
        coingeckoId = "frax-share"
    )
    
    // ========== AGGREGATED LIST ==========
    
    /**
     * All DeFi assets for catalog seeding.
     */
    fun getAll(): List<CryptoAsset> = listOf(
        // DEX
        UNI, CRV, SUSHI, ONEINCH, CAKE, BAL,
        // Lending
        AAVE, MKR, COMP,
        // Derivatives
        SNX, DYDX, GMX,
        // Solana DeFi
        JUP, RAY, ORCA,
        // Liquid Staking
        LDO, RPL, EIGEN,
        // Yield
        YFI, CVX, PENDLE, FXS
    )
    
    /**
     * Blue chip DeFi (highest market cap, most established).
     */
    fun getBlueChip(): List<CryptoAsset> = listOf(UNI, AAVE, MKR, LDO)
    
    /**
     * DEX tokens only.
     */
    fun getDexTokens(): List<CryptoAsset> = listOf(UNI, CRV, SUSHI, ONEINCH, CAKE, JUP, RAY, ORCA, BAL)
    
    /**
     * Lending protocols.
     */
    fun getLending(): List<CryptoAsset> = listOf(AAVE, MKR, COMP)
    
    /**
     * Derivatives protocols.
     */
    fun getDerivatives(): List<CryptoAsset> = listOf(SNX, DYDX, GMX)
    
    /**
     * Liquid staking tokens.
     */
    fun getLiquidStaking(): List<CryptoAsset> = listOf(LDO, RPL, EIGEN)
    
    /**
     * Assets with futures shorting available.
     */
    fun getShortable(): List<CryptoAsset> = getAll().filter { it.canShortFutures }
    
    /**
     * Count of DeFi assets.
     */
    fun count(): Int = getAll().size
}
