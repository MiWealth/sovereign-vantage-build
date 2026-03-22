/*
 * Sovereign Vantage - Arthur Edition
 * AssetCatalogSeeder.kt - Populates Room database with curated asset catalog
 * 
 * Copyright 2025-2026 MiWealth Pty Ltd
 * Creator: Mike Stahl
 * Co-Founder (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */


package com.miwealth.sovereignvantage.data.catalog

import android.util.Log
import com.miwealth.sovereignvantage.data.models.AssetCategory
import com.miwealth.sovereignvantage.data.models.CryptoAsset
import com.miwealth.sovereignvantage.data.repository.AssetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds the asset database with curated assets on first app launch.
 * 
 * Uses hybrid approach:
 * 1. Core curated list loaded from catalog files (200+ assets)
 * 2. Dynamic expansion can add more from exchange APIs later
 * 
 * Seeding only occurs if database is empty, preserving user modifications.
 */
@Singleton
class AssetCatalogSeeder @Inject constructor(
    private val assetRepository: AssetRepository
) {
    companion object {
        private const val TAG = "AssetCatalogSeeder"
    }
    
    /**
     * Seed the database if needed.
     * Call this at app startup, before any trading operations.
     * 
     * @return Number of assets seeded (0 if already populated)
     */
    suspend fun seedIfNeeded(): Int = withContext(Dispatchers.IO) {
        if (!assetRepository.needsSeeding()) {
            Log.d(TAG, "Database already seeded, skipping")
            return@withContext 0
        }
        
        Log.i(TAG, "Seeding asset database...")
        val allAssets = getAllCuratedAssets()
        assetRepository.saveAll(allAssets)
        
        Log.i(TAG, "Seeded ${allAssets.size} assets across ${getCategories().size} categories")
        logCategoryCounts(allAssets)
        
        return@withContext allAssets.size
    }
    
    /**
     * Force re-seed the database, replacing all assets.
     * USE WITH CAUTION: This will reset user modifications.
     * 
     * @return Number of assets seeded
     */
    suspend fun forceReseed(): Int = withContext(Dispatchers.IO) {
        Log.w(TAG, "Force reseeding asset database...")
        val allAssets = getAllCuratedAssets()
        assetRepository.saveAll(allAssets)
        
        Log.i(TAG, "Force reseeded ${allAssets.size} assets")
        return@withContext allAssets.size
    }
    
    /**
     * Add new assets without replacing existing ones.
     * Useful for adding newly supported assets in app updates.
     * 
     * @param assets List of new assets to add
     * @return Number of assets added (excludes duplicates)
     */
    suspend fun addNewAssets(assets: List<CryptoAsset>): Int = withContext(Dispatchers.IO) {
        var added = 0
        assets.forEach { asset ->
            if (!assetRepository.exists(asset.symbol)) {
                assetRepository.save(asset)
                added++
            }
        }
        Log.i(TAG, "Added $added new assets (${assets.size - added} already existed)")
        return@withContext added
    }
    
    /**
     * Get all curated assets from catalog files.
     */
    fun getAllCuratedAssets(): List<CryptoAsset> {
        return buildList {
            addAll(Layer1Assets.getAll())
            addAll(Layer2Assets.getAll())
            addAll(DeFiAssets.getAll())
            addAll(MemeAssets.getAll())
            addAll(getAiMlAssets())
            addAll(getGamingNftAssets())
            addAll(getInfrastructureAssets())
            addAll(getPrivacyAssets())
            addAll(getRwaGoldAssets())
            addAll(getExchangeTokenAssets())
            addAll(getStablecoins())
        }
    }
    
    /**
     * Get category breakdown.
     */
    fun getCategories(): Map<AssetCategory, Int> {
        return getAllCuratedAssets()
            .groupBy { it.category }
            .mapValues { it.value.size }
    }
    
    private fun logCategoryCounts(assets: List<CryptoAsset>) {
        assets.groupBy { it.category }
            .forEach { (category, list) ->
                Log.d(TAG, "  $category: ${list.size} assets")
            }
    }
    
    // ========== AI/ML ASSETS (Inline - smaller category) ==========
    
    private fun getAiMlAssets(): List<CryptoAsset> = listOf(
        CryptoAsset(
            symbol = "FET",
            name = "Fetch.ai",
            category = AssetCategory.AI_ML,
            subcategory = "AI Agents",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.MID,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.5,
            maxPositionPercent = 0.05,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 4.0,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "fetch-ai"
        ),
        CryptoAsset(
            symbol = "RNDR",
            name = "Render",
            category = AssetCategory.AI_ML,
            subcategory = "GPU Computing",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.MID,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.5,
            maxPositionPercent = 0.05,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 4.0,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "render-token"
        ),
        CryptoAsset(
            symbol = "TAO",
            name = "Bittensor",
            category = AssetCategory.AI_ML,
            subcategory = "Decentralised ML",
            primaryChain = "Bittensor",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.MID,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.EXTREME,
            kellyMultiplier = 0.4,
            maxPositionPercent = 0.04,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.5,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = false, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "bittensor"
        ),
        CryptoAsset(
            symbol = "WLD",
            name = "Worldcoin",
            category = AssetCategory.AI_ML,
            subcategory = "Identity",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.MID,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.5,
            maxPositionPercent = 0.05,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.5,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "worldcoin-wld"
        ),
        CryptoAsset(
            symbol = "AKT",
            name = "Akash Network",
            category = AssetCategory.AI_ML,
            subcategory = "Cloud Computing",
            primaryChain = "Cosmos",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.4,
            maxPositionPercent = 0.04,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.0,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = false, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "akash-network"
        ),
        CryptoAsset(
            symbol = "OCEAN",
            name = "Ocean Protocol",
            category = AssetCategory.AI_ML,
            subcategory = "Data Exchange",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.4,
            maxPositionPercent = 0.04,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.0,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = false, canShortMargin = false,
            coingeckoId = "ocean-protocol"
        ),
        CryptoAsset(
            symbol = "NMR",
            name = "Numeraire",
            category = AssetCategory.AI_ML,
            subcategory = "Quant/ML",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.4,
            maxPositionPercent = 0.03,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.0,
            onKraken = true, onBinance = true, onBybit = false, onCoinbase = true, onOkx = true,
            canShortFutures = false, canShortMargin = false,
            coingeckoId = "numeraire"
        ),
        CryptoAsset(
            symbol = "VIRTUAL",
            name = "Virtuals Protocol",
            category = AssetCategory.AI_ML,
            subcategory = "AI Agents",
            primaryChain = "Base",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.EXTREME,
            kellyMultiplier = 0.3,
            maxPositionPercent = 0.03,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 2.5,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = false, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "virtual-protocol"
        )
    )
    
    // ========== GAMING/NFT ASSETS ==========
    
    private fun getGamingNftAssets(): List<CryptoAsset> = listOf(
        CryptoAsset(
            symbol = "AXS",
            name = "Axie Infinity",
            category = AssetCategory.GAMING_NFT,
            subcategory = "Gaming",
            primaryChain = "Ronin",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.4,
            maxPositionPercent = 0.05,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.5,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "axie-infinity"
        ),
        CryptoAsset(
            symbol = "SAND",
            name = "The Sandbox",
            category = AssetCategory.GAMING_NFT,
            subcategory = "Metaverse",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.4,
            maxPositionPercent = 0.05,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.5,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "the-sandbox"
        ),
        CryptoAsset(
            symbol = "MANA",
            name = "Decentraland",
            category = AssetCategory.GAMING_NFT,
            subcategory = "Metaverse",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.4,
            maxPositionPercent = 0.05,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.5,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "decentraland"
        ),
        CryptoAsset(
            symbol = "GALA",
            name = "Gala",
            category = AssetCategory.GAMING_NFT,
            subcategory = "Gaming Platform",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.4,
            maxPositionPercent = 0.04,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.5,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "gala"
        ),
        CryptoAsset(
            symbol = "ENJ",
            name = "Enjin Coin",
            category = AssetCategory.GAMING_NFT,
            subcategory = "NFT Infrastructure",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.4,
            maxPositionPercent = 0.04,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.5,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "enjincoin"
        ),
        CryptoAsset(
            symbol = "RON",
            name = "Ronin",
            category = AssetCategory.GAMING_NFT,
            subcategory = "Gaming Chain",
            primaryChain = "Ronin",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.4,
            maxPositionPercent = 0.04,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.0,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "ronin"
        ),
        CryptoAsset(
            symbol = "BLUR",
            name = "Blur",
            category = AssetCategory.GAMING_NFT,
            subcategory = "NFT Marketplace",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.EXTREME,
            kellyMultiplier = 0.3,
            maxPositionPercent = 0.03,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 2.5,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "blur"
        ),
        CryptoAsset(
            symbol = "APE",
            name = "ApeCoin",
            category = AssetCategory.GAMING_NFT,
            subcategory = "NFT Ecosystem",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.EXTREME,
            kellyMultiplier = 0.3,
            maxPositionPercent = 0.03,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 2.5,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "apecoin"
        )
    )
    
    // ========== INFRASTRUCTURE ASSETS ==========
    
    private fun getInfrastructureAssets(): List<CryptoAsset> = listOf(
        // Note: LINK is in Layer1Assets but categorised as INFRASTRUCTURE
        CryptoAsset(
            symbol = "GRT",
            name = "The Graph",
            category = AssetCategory.INFRASTRUCTURE,
            subcategory = "Indexing",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.MID,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.5,
            maxPositionPercent = 0.06,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 4.0,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "the-graph"
        ),
        CryptoAsset(
            symbol = "AR",
            name = "Arweave",
            category = AssetCategory.INFRASTRUCTURE,
            subcategory = "Storage",
            primaryChain = "Arweave",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.MID,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.5,
            maxPositionPercent = 0.05,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.5,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "arweave"
        ),
        CryptoAsset(
            symbol = "PYTH",
            name = "Pyth Network",
            category = AssetCategory.INFRASTRUCTURE,
            subcategory = "Oracle",
            primaryChain = "Solana",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.5,
            maxPositionPercent = 0.05,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.5,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "pyth-network"
        ),
        CryptoAsset(
            symbol = "STORJ",
            name = "Storj",
            category = AssetCategory.INFRASTRUCTURE,
            subcategory = "Storage",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.4,
            maxPositionPercent = 0.04,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.0,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = false, canShortMargin = false,
            coingeckoId = "storj"
        )
    )
    
    // ========== PRIVACY ASSETS ==========
    
    private fun getPrivacyAssets(): List<CryptoAsset> = listOf(
        CryptoAsset(
            symbol = "XMR",
            name = "Monero",
            category = AssetCategory.PRIVACY,
            subcategory = "Ring Signatures",
            primaryChain = "Monero",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.MID,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.5,
            maxPositionPercent = 0.05,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 4.0,
            onKraken = true, onBinance = false, onBybit = false, onCoinbase = false, onOkx = false,
            canShortFutures = false, canShortMargin = false,
            isPrivacyCoin = true,
            coingeckoId = "monero"
        ),
        CryptoAsset(
            symbol = "ZEC",
            name = "Zcash",
            category = AssetCategory.PRIVACY,
            subcategory = "zk-SNARKs",
            primaryChain = "Zcash",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.4,
            maxPositionPercent = 0.04,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.5,
            onKraken = true, onBinance = false, onBybit = false, onCoinbase = true, onOkx = false,
            canShortFutures = false, canShortMargin = false,
            isPrivacyCoin = true,
            coingeckoId = "zcash"
        ),
        CryptoAsset(
            symbol = "DASH",
            name = "Dash",
            category = AssetCategory.PRIVACY,
            subcategory = "CoinJoin",
            primaryChain = "Dash",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.4,
            maxPositionPercent = 0.04,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.0,
            onKraken = true, onBinance = false, onBybit = false, onCoinbase = true, onOkx = false,
            canShortFutures = false, canShortMargin = false,
            isPrivacyCoin = true,
            coingeckoId = "dash"
        )
    )
    
    // ========== RWA/GOLD ASSETS ==========
    
    private fun getRwaGoldAssets(): List<CryptoAsset> = listOf(
        CryptoAsset(
            symbol = "PAXG",
            name = "PAX Gold",
            category = AssetCategory.RWA_GOLD,
            subcategory = "Gold-Backed",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.LOW,
            kellyMultiplier = 0.8,
            maxPositionPercent = 0.10,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.0,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = false, onOkx = true,
            canShortFutures = false, canShortMargin = false,
            coingeckoId = "pax-gold"
        ),
        CryptoAsset(
            symbol = "ONDO",
            name = "Ondo Finance",
            category = AssetCategory.RWA_GOLD,
            subcategory = "RWA Protocol",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.5,
            maxPositionPercent = 0.05,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.5,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "ondo-finance"
        )
    )
    
    // ========== EXCHANGE TOKENS ==========
    
    private fun getExchangeTokenAssets(): List<CryptoAsset> = listOf(
        // Note: BNB is in Layer1Assets
        CryptoAsset(
            symbol = "CRO",
            name = "Cronos",
            category = AssetCategory.EXCHANGE_TOKEN,
            subcategory = "Crypto.com",
            primaryChain = "Cronos",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.MID,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.5,
            maxPositionPercent = 0.05,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 4.0,
            onKraken = true, onBinance = false, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "crypto-com-chain"
        ),
        CryptoAsset(
            symbol = "OKB",
            name = "OKB",
            category = AssetCategory.EXCHANGE_TOKEN,
            subcategory = "OKX",
            primaryChain = "OKC",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.MID,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.MEDIUM,
            kellyMultiplier = 0.5,
            maxPositionPercent = 0.05,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 4.0,
            onKraken = false, onBinance = false, onBybit = false, onCoinbase = false, onOkx = true,
            canShortFutures = false, canShortMargin = false,
            coingeckoId = "okb"
        ),
        CryptoAsset(
            symbol = "WOO",
            name = "WOO",
            category = AssetCategory.EXCHANGE_TOKEN,
            subcategory = "WOO X",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.SMALL,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.HIGH,
            kellyMultiplier = 0.4,
            maxPositionPercent = 0.04,
            defaultStopLossPercent = 0.035,
            recommendedLeverage = 3.0,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = false, onOkx = true,
            canShortFutures = true, canShortMargin = false,
            coingeckoId = "woo-network"
        )
    )
    
    // ========== STABLECOINS (for reference, not trading) ==========
    
    private fun getStablecoins(): List<CryptoAsset> = listOf(
        CryptoAsset(
            symbol = "USDT",
            name = "Tether",
            category = AssetCategory.STABLECOIN,
            subcategory = "Fiat-Backed",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.MEGA,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.LOW,
            kellyMultiplier = 0.0,
            maxPositionPercent = 0.0,
            defaultStopLossPercent = 0.0,
            recommendedLeverage = 1.0,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = false, canShortMargin = false,
            isStablecoin = true,
            coingeckoId = "tether"
        ),
        CryptoAsset(
            symbol = "USDC",
            name = "USD Coin",
            category = AssetCategory.STABLECOIN,
            subcategory = "Fiat-Backed",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.MEGA,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.LOW,
            kellyMultiplier = 0.0,
            maxPositionPercent = 0.0,
            defaultStopLossPercent = 0.0,
            recommendedLeverage = 1.0,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = false, canShortMargin = false,
            isStablecoin = true,
            coingeckoId = "usd-coin"
        ),
        CryptoAsset(
            symbol = "DAI",
            name = "Dai",
            category = AssetCategory.STABLECOIN,
            subcategory = "Algorithmic",
            primaryChain = "Ethereum",
            marketCapTier = com.miwealth.sovereignvantage.data.models.MarketCapTier.MID,
            volatilityTier = com.miwealth.sovereignvantage.data.models.VolatilityTier.LOW,
            kellyMultiplier = 0.0,
            maxPositionPercent = 0.0,
            defaultStopLossPercent = 0.0,
            recommendedLeverage = 1.0,
            onKraken = true, onBinance = true, onBybit = true, onCoinbase = true, onOkx = true,
            canShortFutures = false, canShortMargin = false,
            isStablecoin = true,
            coingeckoId = "dai"
        )
    )
}
