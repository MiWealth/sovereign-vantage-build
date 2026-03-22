/*
 * Sovereign Vantage - Arthur Edition
 * MemeAssets.kt - Meme coin asset definitions
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
 * Meme coins - highest volatility category.
 * 
 * EXTREME RISK: These assets can gain or lose 50%+ in a single day.
 * 
 * Risk parameters are conservative:
 * - Kelly multiplier: 0.3 (30% of calculated position)
 * - Max position: 3% of portfolio
 * - Stop loss: 20% (wider to avoid noise)
 * - Leverage: 3x max (lower than L1/L2)
 * 
 * Ideal for New Launch Strategy - pump/dump pattern is most pronounced.
 */
object MemeAssets {
    
    // ========== TIER 1 - ESTABLISHED MEMES ==========
    
    val DOGE = CryptoAsset(
        symbol = "DOGE",
        name = "Dogecoin",
        category = AssetCategory.MEME,
        subcategory = "OG Meme",
        primaryChain = "Dogecoin",
        marketCapTier = MarketCapTier.LARGE,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.4,
        maxPositionPercent = 0.05,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 4.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "dogecoin"
    )
    
    val SHIB = CryptoAsset(
        symbol = "SHIB",
        name = "Shiba Inu",
        category = AssetCategory.MEME,
        subcategory = "Dog Meme",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.MID,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.35,
        maxPositionPercent = 0.04,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "shiba-inu"
    )
    
    val PEPE = CryptoAsset(
        symbol = "PEPE",
        name = "Pepe",
        category = AssetCategory.MEME,
        subcategory = "Frog Meme",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.MID,
        volatilityTier = VolatilityTier.EXTREME,
        kellyMultiplier = 0.3,
        maxPositionPercent = 0.03,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "pepe"
    )
    
    val FLOKI = CryptoAsset(
        symbol = "FLOKI",
        name = "Floki",
        category = AssetCategory.MEME,
        subcategory = "Dog Meme",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.EXTREME,
        kellyMultiplier = 0.3,
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
        coingeckoId = "floki"
    )
    
    // ========== TIER 2 - ACTIVE SOLANA MEMES ==========
    
    val WIF = CryptoAsset(
        symbol = "WIF",
        name = "dogwifhat",
        category = AssetCategory.MEME,
        subcategory = "Dog Meme",
        primaryChain = "Solana",
        marketCapTier = MarketCapTier.MID,
        volatilityTier = VolatilityTier.EXTREME,
        kellyMultiplier = 0.3,
        maxPositionPercent = 0.03,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "dogwifcoin"
    )
    
    val BONK = CryptoAsset(
        symbol = "BONK",
        name = "Bonk",
        category = AssetCategory.MEME,
        subcategory = "Dog Meme",
        primaryChain = "Solana",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.EXTREME,
        kellyMultiplier = 0.3,
        maxPositionPercent = 0.03,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "bonk"
    )
    
    val POPCAT = CryptoAsset(
        symbol = "POPCAT",
        name = "Popcat",
        category = AssetCategory.MEME,
        subcategory = "Cat Meme",
        primaryChain = "Solana",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.EXTREME,
        kellyMultiplier = 0.25,
        maxPositionPercent = 0.02,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 2.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "popcat"
    )
    
    val MEW = CryptoAsset(
        symbol = "MEW",
        name = "cat in a dogs world",
        category = AssetCategory.MEME,
        subcategory = "Cat Meme",
        primaryChain = "Solana",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.EXTREME,
        kellyMultiplier = 0.25,
        maxPositionPercent = 0.02,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 2.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "cat-in-a-dogs-world"
    )
    
    val BOME = CryptoAsset(
        symbol = "BOME",
        name = "BOOK OF MEME",
        category = AssetCategory.MEME,
        subcategory = "Culture Meme",
        primaryChain = "Solana",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.EXTREME,
        kellyMultiplier = 0.25,
        maxPositionPercent = 0.02,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 2.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "book-of-meme"
    )
    
    val GIGA = CryptoAsset(
        symbol = "GIGA",
        name = "Gigachad",
        category = AssetCategory.MEME,
        subcategory = "Culture Meme",
        primaryChain = "Solana",
        marketCapTier = MarketCapTier.MICRO,
        volatilityTier = VolatilityTier.EXTREME,
        kellyMultiplier = 0.2,
        maxPositionPercent = 0.02,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 2.0,
        onKraken = false,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = false,
        canShortMargin = false,
        coingeckoId = "gigachad-2"
    )
    
    val PNUT = CryptoAsset(
        symbol = "PNUT",
        name = "Peanut the Squirrel",
        category = AssetCategory.MEME,
        subcategory = "Animal Meme",
        primaryChain = "Solana",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.EXTREME,
        kellyMultiplier = 0.25,
        maxPositionPercent = 0.02,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 2.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "peanut-the-squirrel"
    )
    
    // ========== TIER 2 - ETHEREUM/BASE MEMES ==========
    
    val BRETT = CryptoAsset(
        symbol = "BRETT",
        name = "Brett",
        category = AssetCategory.MEME,
        subcategory = "Frog Meme",
        primaryChain = "Base",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.EXTREME,
        kellyMultiplier = 0.25,
        maxPositionPercent = 0.02,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 2.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "brett"
    )
    
    val MOG = CryptoAsset(
        symbol = "MOG",
        name = "Mog Coin",
        category = AssetCategory.MEME,
        subcategory = "Cat Meme",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.EXTREME,
        kellyMultiplier = 0.25,
        maxPositionPercent = 0.02,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 2.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "mog-coin"
    )
    
    val SPX = CryptoAsset(
        symbol = "SPX",
        name = "SPX6900",
        category = AssetCategory.MEME,
        subcategory = "Finance Meme",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.EXTREME,
        kellyMultiplier = 0.2,
        maxPositionPercent = 0.02,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 2.0,
        onKraken = false,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = false,
        canShortMargin = false,
        coingeckoId = "spx6900"
    )
    
    // ========== TIER 3 - AI AGENT MEMES ==========
    
    val AI16Z = CryptoAsset(
        symbol = "AI16Z",
        name = "ai16z",
        category = AssetCategory.MEME,
        subcategory = "AI Meme",
        primaryChain = "Solana",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.EXTREME,
        kellyMultiplier = 0.25,
        maxPositionPercent = 0.02,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 2.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "ai16z"
    )
    
    val GOAT = CryptoAsset(
        symbol = "GOAT",
        name = "Goatseus Maximus",
        category = AssetCategory.MEME,
        subcategory = "AI Meme",
        primaryChain = "Solana",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.EXTREME,
        kellyMultiplier = 0.2,
        maxPositionPercent = 0.02,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 2.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "goatseus-maximus"
    )
    
    val FARTCOIN = CryptoAsset(
        symbol = "FARTCOIN",
        name = "Fartcoin",
        category = AssetCategory.MEME,
        subcategory = "AI Meme",
        primaryChain = "Solana",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.EXTREME,
        kellyMultiplier = 0.2,
        maxPositionPercent = 0.02,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 2.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "fartcoin"
    )
    
    // ========== AGGREGATED LIST ==========
    
    /**
     * All meme assets for catalog seeding.
     */
    fun getAll(): List<CryptoAsset> = listOf(
        // Tier 1 - Established
        DOGE, SHIB, PEPE, FLOKI,
        // Tier 2 - Solana
        WIF, BONK, POPCAT, MEW, BOME, GIGA, PNUT,
        // Tier 2 - Ethereum/Base
        BRETT, MOG, SPX,
        // Tier 3 - AI Agent Memes
        AI16Z, GOAT, FARTCOIN
    )
    
    /**
     * Established memes only (for conservative meme exposure).
     */
    fun getEstablished(): List<CryptoAsset> = listOf(DOGE, SHIB, PEPE, FLOKI)
    
    /**
     * Solana ecosystem memes.
     */
    fun getSolanaMemes(): List<CryptoAsset> = listOf(
        WIF, BONK, POPCAT, MEW, BOME, GIGA, PNUT, AI16Z, GOAT, FARTCOIN
    )
    
    /**
     * Assets suitable for New Launch Strategy (can be shorted).
     */
    fun getShortable(): List<CryptoAsset> = getAll().filter { it.canShortFutures }
    
    /**
     * Dog-themed memes.
     */
    fun getDogMemes(): List<CryptoAsset> = listOf(DOGE, SHIB, FLOKI, WIF, BONK)
    
    /**
     * Cat-themed memes.
     */
    fun getCatMemes(): List<CryptoAsset> = listOf(POPCAT, MEW, MOG)
    
    /**
     * AI agent memes (emerging category).
     */
    fun getAiMemes(): List<CryptoAsset> = listOf(AI16Z, GOAT, FARTCOIN)
    
    /**
     * Count of meme assets.
     */
    fun count(): Int = getAll().size
}
