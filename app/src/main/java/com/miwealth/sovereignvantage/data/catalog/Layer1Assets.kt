/*
 * Sovereign Vantage - Arthur Edition
 * Layer1Assets.kt - Layer 1 blockchain asset definitions
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
 * Layer 1 blockchain assets - the foundation of crypto.
 * 
 * Tier 1: Market cap > $10B - highest liquidity, best for larger positions
 * Tier 2: Market cap $1B-$10B - good liquidity, moderate positions
 * Tier 3: Market cap < $1B - lower liquidity, smaller positions
 * 
 * Risk params from backtest: 5-6x leverage optimal with 3% initial stop
 */
object Layer1Assets {
    
    // ========== TIER 1 - MEGA/LARGE CAP (> $10B) ==========
    
    val BTC = CryptoAsset(
        symbol = "BTC",
        name = "Bitcoin",
        category = AssetCategory.LAYER_1,
        subcategory = "Store of Value",
        primaryChain = "Bitcoin",
        marketCapTier = MarketCapTier.MEGA,
        volatilityTier = VolatilityTier.MEDIUM,
        kellyMultiplier = 1.0,
        maxPositionPercent = 0.20,  // Can go larger on BTC
        defaultStopLossPercent = 0.035,  // 3% optimal from backtest
        recommendedLeverage = 5.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "bitcoin"
    )
    
    val ETH = CryptoAsset(
        symbol = "ETH",
        name = "Ethereum",
        category = AssetCategory.LAYER_1,
        subcategory = "Smart Contracts",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.MEGA,
        volatilityTier = VolatilityTier.MEDIUM,
        kellyMultiplier = 1.0,
        maxPositionPercent = 0.18,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 5.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "ethereum"
    )
    
    val SOL = CryptoAsset(
        symbol = "SOL",
        name = "Solana",
        category = AssetCategory.LAYER_1,
        subcategory = "High Performance",
        primaryChain = "Solana",
        marketCapTier = MarketCapTier.LARGE,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.9,
        maxPositionPercent = 0.15,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 5.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "solana"
    )
    
    val BNB = CryptoAsset(
        symbol = "BNB",
        name = "BNB",
        category = AssetCategory.LAYER_1,  // Also exchange token, but primarily L1 now
        subcategory = "Exchange Chain",
        primaryChain = "BNB Chain",
        marketCapTier = MarketCapTier.LARGE,
        volatilityTier = VolatilityTier.MEDIUM,
        kellyMultiplier = 0.9,
        maxPositionPercent = 0.12,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 5.0,
        onKraken = false,  // Not on Kraken
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "binancecoin"
    )
    
    val XRP = CryptoAsset(
        symbol = "XRP",
        name = "XRP",
        category = AssetCategory.LAYER_1,
        subcategory = "Payments",
        primaryChain = "XRPL",
        marketCapTier = MarketCapTier.LARGE,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.8,
        maxPositionPercent = 0.12,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 5.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "ripple"
    )
    
    val ADA = CryptoAsset(
        symbol = "ADA",
        name = "Cardano",
        category = AssetCategory.LAYER_1,
        subcategory = "Smart Contracts",
        primaryChain = "Cardano",
        marketCapTier = MarketCapTier.LARGE,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.8,
        maxPositionPercent = 0.10,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 5.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "cardano"
    )
    
    val AVAX = CryptoAsset(
        symbol = "AVAX",
        name = "Avalanche",
        category = AssetCategory.LAYER_1,
        subcategory = "Smart Contracts",
        primaryChain = "Avalanche",
        marketCapTier = MarketCapTier.LARGE,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.8,
        maxPositionPercent = 0.10,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 5.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "avalanche-2"
    )
    
    val DOT = CryptoAsset(
        symbol = "DOT",
        name = "Polkadot",
        category = AssetCategory.LAYER_1,
        subcategory = "Interoperability",
        primaryChain = "Polkadot",
        marketCapTier = MarketCapTier.LARGE,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.8,
        maxPositionPercent = 0.10,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 5.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "polkadot"
    )
    
    val TRX = CryptoAsset(
        symbol = "TRX",
        name = "TRON",
        category = AssetCategory.LAYER_1,
        subcategory = "Smart Contracts",
        primaryChain = "TRON",
        marketCapTier = MarketCapTier.LARGE,
        volatilityTier = VolatilityTier.MEDIUM,
        kellyMultiplier = 0.7,
        maxPositionPercent = 0.08,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 4.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "tron"
    )
    
    val TON = CryptoAsset(
        symbol = "TON",
        name = "Toncoin",
        category = AssetCategory.LAYER_1,
        subcategory = "Messaging",
        primaryChain = "TON",
        marketCapTier = MarketCapTier.LARGE,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.8,
        maxPositionPercent = 0.10,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 5.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "the-open-network"
    )
    
    val LINK = CryptoAsset(
        symbol = "LINK",
        name = "Chainlink",
        category = AssetCategory.INFRASTRUCTURE,  // Oracle, but often grouped with L1
        subcategory = "Oracle",
        primaryChain = "Ethereum",
        marketCapTier = MarketCapTier.LARGE,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.8,
        maxPositionPercent = 0.10,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 5.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = true,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = true,
        coingeckoId = "chainlink"
    )
    
    // ========== TIER 2 - MID CAP ($1B - $10B) ==========
    
    val LTC = CryptoAsset(
        symbol = "LTC",
        name = "Litecoin",
        category = AssetCategory.LAYER_1,
        subcategory = "Payments",
        primaryChain = "Litecoin",
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
        coingeckoId = "litecoin"
    )
    
    val BCH = CryptoAsset(
        symbol = "BCH",
        name = "Bitcoin Cash",
        category = AssetCategory.LAYER_1,
        subcategory = "Payments",
        primaryChain = "Bitcoin Cash",
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
        coingeckoId = "bitcoin-cash"
    )
    
    val XLM = CryptoAsset(
        symbol = "XLM",
        name = "Stellar",
        category = AssetCategory.LAYER_1,
        subcategory = "Payments",
        primaryChain = "Stellar",
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
        canShortMargin = false,
        coingeckoId = "stellar"
    )
    
    val ATOM = CryptoAsset(
        symbol = "ATOM",
        name = "Cosmos",
        category = AssetCategory.LAYER_1,
        subcategory = "Interoperability",
        primaryChain = "Cosmos Hub",
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
        coingeckoId = "cosmos"
    )
    
    val HBAR = CryptoAsset(
        symbol = "HBAR",
        name = "Hedera",
        category = AssetCategory.LAYER_1,
        subcategory = "Enterprise",
        primaryChain = "Hedera",
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
        coingeckoId = "hedera-hashgraph"
    )
    
    val ICP = CryptoAsset(
        symbol = "ICP",
        name = "Internet Computer",
        category = AssetCategory.LAYER_1,
        subcategory = "Web3",
        primaryChain = "Internet Computer",
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
        coingeckoId = "internet-computer"
    )
    
    val FIL = CryptoAsset(
        symbol = "FIL",
        name = "Filecoin",
        category = AssetCategory.INFRASTRUCTURE,
        subcategory = "Storage",
        primaryChain = "Filecoin",
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
        coingeckoId = "filecoin"
    )
    
    val APT = CryptoAsset(
        symbol = "APT",
        name = "Aptos",
        category = AssetCategory.LAYER_1,
        subcategory = "Move VM",
        primaryChain = "Aptos",
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
        coingeckoId = "aptos"
    )
    
    val NEAR = CryptoAsset(
        symbol = "NEAR",
        name = "NEAR Protocol",
        category = AssetCategory.LAYER_1,
        subcategory = "Sharding",
        primaryChain = "NEAR",
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
        coingeckoId = "near"
    )
    
    val VET = CryptoAsset(
        symbol = "VET",
        name = "VeChain",
        category = AssetCategory.LAYER_1,
        subcategory = "Enterprise",
        primaryChain = "VeChain",
        marketCapTier = MarketCapTier.MID,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.6,
        maxPositionPercent = 0.06,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 4.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "vechain"
    )
    
    val ALGO = CryptoAsset(
        symbol = "ALGO",
        name = "Algorand",
        category = AssetCategory.LAYER_1,
        subcategory = "Pure PoS",
        primaryChain = "Algorand",
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
        coingeckoId = "algorand"
    )
    
    val FTM = CryptoAsset(
        symbol = "FTM",
        name = "Fantom",
        category = AssetCategory.LAYER_1,
        subcategory = "DAG",
        primaryChain = "Fantom",
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
        coingeckoId = "fantom"
    )
    
    val KAS = CryptoAsset(
        symbol = "KAS",
        name = "Kaspa",
        category = AssetCategory.LAYER_1,
        subcategory = "BlockDAG",
        primaryChain = "Kaspa",
        marketCapTier = MarketCapTier.MID,
        volatilityTier = VolatilityTier.EXTREME,
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
        coingeckoId = "kaspa"
    )
    
    val SUI = CryptoAsset(
        symbol = "SUI",
        name = "Sui",
        category = AssetCategory.LAYER_1,
        subcategory = "Move VM",
        primaryChain = "Sui",
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
        coingeckoId = "sui"
    )
    
    val SEI = CryptoAsset(
        symbol = "SEI",
        name = "Sei",
        category = AssetCategory.LAYER_1,
        subcategory = "Trading Optimised",
        primaryChain = "Sei",
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
        coingeckoId = "sei-network"
    )
    
    val INJ = CryptoAsset(
        symbol = "INJ",
        name = "Injective",
        category = AssetCategory.LAYER_1,
        subcategory = "DeFi Optimised",
        primaryChain = "Injective",
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
        coingeckoId = "injective-protocol"
    )
    
    val TIA = CryptoAsset(
        symbol = "TIA",
        name = "Celestia",
        category = AssetCategory.LAYER_1,
        subcategory = "Modular DA",
        primaryChain = "Celestia",
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
        coingeckoId = "celestia"
    )
    
    val THETA = CryptoAsset(
        symbol = "THETA",
        name = "Theta Network",
        category = AssetCategory.LAYER_1,
        subcategory = "Video Streaming",
        primaryChain = "Theta",
        marketCapTier = MarketCapTier.MID,
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
        coingeckoId = "theta-token"
    )
    
    // ========== TIER 3 - SMALL CAP (Notable mentions) ==========
    
    val EOS = CryptoAsset(
        symbol = "EOS",
        name = "EOS",
        category = AssetCategory.LAYER_1,
        subcategory = "DPoS",
        primaryChain = "EOS",
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
        coingeckoId = "eos"
    )
    
    val NEO = CryptoAsset(
        symbol = "NEO",
        name = "NEO",
        category = AssetCategory.LAYER_1,
        subcategory = "Smart Economy",
        primaryChain = "NEO",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.5,
        maxPositionPercent = 0.04,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.5,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = true,
        canShortMargin = false,
        coingeckoId = "neo"
    )
    
    val IOTA = CryptoAsset(
        symbol = "IOTA",
        name = "IOTA",
        category = AssetCategory.LAYER_1,
        subcategory = "IoT",
        primaryChain = "IOTA",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.4,
        maxPositionPercent = 0.03,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = false,
        canShortMargin = false,
        coingeckoId = "iota"
    )
    
    val ZIL = CryptoAsset(
        symbol = "ZIL",
        name = "Zilliqa",
        category = AssetCategory.LAYER_1,
        subcategory = "Sharding",
        primaryChain = "Zilliqa",
        marketCapTier = MarketCapTier.SMALL,
        volatilityTier = VolatilityTier.HIGH,
        kellyMultiplier = 0.4,
        maxPositionPercent = 0.03,
        defaultStopLossPercent = 0.035,
        recommendedLeverage = 3.0,
        onKraken = true,
        onBinance = true,
        onBybit = true,
        onCoinbase = false,
        onOkx = true,
        canShortFutures = false,
        canShortMargin = false,
        coingeckoId = "zilliqa"
    )
    
    // ========== AGGREGATED LIST ==========
    
    /**
     * All Layer 1 assets for catalog seeding.
     */
    fun getAll(): List<CryptoAsset> = listOf(
        // Tier 1 - Mega/Large Cap
        BTC, ETH, SOL, BNB, XRP, ADA, AVAX, DOT, TRX, TON, LINK,
        // Tier 2 - Mid Cap
        LTC, BCH, XLM, ATOM, HBAR, ICP, FIL, APT, NEAR, VET, 
        ALGO, FTM, KAS, SUI, SEI, INJ, TIA, THETA,
        // Tier 3 - Small Cap
        EOS, NEO, IOTA, ZIL
    )
    
    /**
     * Tier 1 only (for conservative strategies).
     */
    fun getTier1(): List<CryptoAsset> = listOf(
        BTC, ETH, SOL, BNB, XRP, ADA, AVAX, DOT, TRX, TON, LINK
    )
    
    /**
     * Assets with futures shorting available.
     */
    fun getShortable(): List<CryptoAsset> = getAll().filter { it.canShortFutures }
    
    /**
     * Count of Layer 1 assets.
     */
    fun count(): Int = getAll().size
}
