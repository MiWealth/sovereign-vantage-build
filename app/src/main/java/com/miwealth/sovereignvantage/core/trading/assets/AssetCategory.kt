package com.miwealth.sovereignvantage.core.trading.assets

import com.miwealth.sovereignvantage.core.AssetType

/**
 * Asset Category - Granular Sub-Classification
 * 
 * Sovereign Vantage: Arthur Edition V5.17.0
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * MASSIVE EXPANSION: Now covers comprehensive asset classes including:
 * - Full FOREX coverage (Major, Minor, Exotic, Scandinavian, Asian, LATAM, EMEA)
 * - Precious Metals (XAU, XAG, XPT, XPD)
 * - Industrial Metals (Copper, Aluminium, Nickel, Zinc, Lead, Tin)
 * - Rare Earth ETF Proxies (REMX, LIT, etc.)
 * - Energy Commodities (Crude, Natural Gas, Brent)
 * - Agricultural Commodities
 * - Complete Cryptocurrency coverage (Majors, L1, L2, DeFi, Meme, NFT, AI, Privacy, etc.)
 * - Gold-backed Crypto (PAXG, XAUT)
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn - for tolerating me and my quirks 💘
 */

/**
 * Granular asset categories for filtering and portfolio construction.
 * 
 * @property displayName Human-readable name for UI
 * @property parentType The broad AssetType this category belongs to
 * @property riskTier Risk classification (1=lowest, 5=highest volatility/risk)
 * @property description Brief explanation of the category
 */


enum class AssetCategory(
    val displayName: String,
    val parentType: AssetType,
    val riskTier: Int,
    val description: String
) {
    // ============================================================================
    // CRYPTOCURRENCY - SPOT
    // ============================================================================
    
    MAJOR_CRYPTO(
        displayName = "Major Crypto",
        parentType = AssetType.CRYPTO_SPOT,
        riskTier = 3,
        description = "Top market cap cryptocurrencies (BTC, ETH)"
    ),
    
    ALT_LAYER1(
        displayName = "Layer 1 Altcoins",
        parentType = AssetType.CRYPTO_SPOT,
        riskTier = 4,
        description = "Alternative Layer 1 blockchains (SOL, AVAX, ADA, DOT, NEAR, SUI, SEI, APT)"
    ),
    
    DEFI(
        displayName = "DeFi Tokens",
        parentType = AssetType.CRYPTO_SPOT,
        riskTier = 4,
        description = "Decentralized finance protocols (UNI, AAVE, CRV, SUSHI, MKR, COMP, SNX, YFI)"
    ),
    
    LAYER2(
        displayName = "Layer 2 Scaling",
        parentType = AssetType.CRYPTO_SPOT,
        riskTier = 4,
        description = "Layer 2 scaling solutions (ARB, OP, MATIC, IMX, STRK, ZK, MANTA)"
    ),
    
    MEME(
        displayName = "Meme Coins",
        parentType = AssetType.CRYPTO_SPOT,
        riskTier = 5,
        description = "Community-driven meme tokens (DOGE, SHIB, PEPE, FLOKI, BONK, WIF, BRETT, TURBO)"
    ),
    
    NFT_GAMING(
        displayName = "NFT & Gaming",
        parentType = AssetType.CRYPTO_SPOT,
        riskTier = 5,
        description = "NFT platforms and gaming tokens (APE, BLUR, GALA, SAND, MANA, AXS, ENJ, ILV, MAGIC)"
    ),
    
    INFRASTRUCTURE(
        displayName = "Infrastructure",
        parentType = AssetType.CRYPTO_SPOT,
        riskTier = 3,
        description = "Blockchain infrastructure (LINK, GRT, FIL, AR, RNDR, OCEAN, THETA)"
    ),
    
    AI_CRYPTO(
        displayName = "AI & Data",
        parentType = AssetType.CRYPTO_SPOT,
        riskTier = 4,
        description = "AI and data-focused tokens (FET, AGIX, OCEAN, RNDR, TAO, ARKM, WLD, AKT)"
    ),
    
    PRIVACY(
        displayName = "Privacy Coins",
        parentType = AssetType.CRYPTO_SPOT,
        riskTier = 4,
        description = "Privacy-focused cryptocurrencies (XMR, ZEC, DASH, SCRT, ZEN, ARRR)"
    ),
    
    EXCHANGE_TOKENS(
        displayName = "Exchange Tokens",
        parentType = AssetType.CRYPTO_SPOT,
        riskTier = 3,
        description = "Centralized exchange tokens (BNB, CRO, OKB, KCS, GT, MX, WOO)"
    ),
    
    GOLD_BACKED_CRYPTO(
        displayName = "Gold-Backed Crypto",
        parentType = AssetType.CRYPTO_SPOT,
        riskTier = 2,
        description = "Tokenized gold and precious metals (PAXG, XAUT, DGX, PMGT)"
    ),
    
    STABLECOIN(
        displayName = "Stablecoins",
        parentType = AssetType.CRYPTO_SPOT,
        riskTier = 1,
        description = "Fiat-pegged stablecoins (USDT, USDC, DAI, TUSD, FRAX, BUSD, USDP)"
    ),
    
    RWA(
        displayName = "Real World Assets",
        parentType = AssetType.CRYPTO_SPOT,
        riskTier = 3,
        description = "Tokenized real-world assets (ONDO, MPL, CFG, CPOOL)"
    ),
    
    SOCIAL_TOKENS(
        displayName = "Social & Creator",
        parentType = AssetType.CRYPTO_SPOT,
        riskTier = 5,
        description = "Social and creator economy tokens (FRIEND, DESO, RLY, AUDIO)"
    ),
    
    // ============================================================================
    // CRYPTOCURRENCY - PERPETUALS/FUTURES
    // ============================================================================
    
    CRYPTO_PERP(
        displayName = "Crypto Perpetuals",
        parentType = AssetType.CRYPTO_FUTURES,
        riskTier = 5,
        description = "Perpetual futures contracts on cryptocurrencies"
    ),
    
    CRYPTO_DATED_FUTURE(
        displayName = "Crypto Dated Futures",
        parentType = AssetType.CRYPTO_FUTURES,
        riskTier = 4,
        description = "Expiring futures contracts on cryptocurrencies"
    ),
    
    // ============================================================================
    // FOREX - COMPREHENSIVE COVERAGE
    // ============================================================================
    
    FOREX_MAJOR(
        displayName = "Major Pairs",
        parentType = AssetType.FOREX,
        riskTier = 2,
        description = "G7 currency pairs (EUR/USD, GBP/USD, USD/JPY, USD/CHF, AUD/USD, USD/CAD, NZD/USD)"
    ),
    
    FOREX_MINOR(
        displayName = "Minor/Cross Pairs",
        parentType = AssetType.FOREX,
        riskTier = 3,
        description = "Cross pairs without USD (EUR/GBP, EUR/JPY, GBP/JPY, EUR/CHF, GBP/CHF, AUD/NZD)"
    ),
    
    FOREX_EXOTIC(
        displayName = "Exotic Pairs",
        parentType = AssetType.FOREX,
        riskTier = 4,
        description = "High volatility EM pairs (USD/TRY, USD/ZAR, EUR/TRY, GBP/ZAR)"
    ),
    
    FOREX_SCANDINAVIAN(
        displayName = "Scandinavian Pairs",
        parentType = AssetType.FOREX,
        riskTier = 3,
        description = "Nordic currencies (USD/SEK, USD/NOK, USD/DKK, EUR/SEK, EUR/NOK, EUR/DKK)"
    ),
    
    FOREX_ASIAN(
        displayName = "Asian Pairs",
        parentType = AssetType.FOREX,
        riskTier = 3,
        description = "Asian currencies (USD/SGD, USD/HKD, USD/CNH, USD/THB, USD/KRW, USD/INR, USD/TWD, USD/PHP)"
    ),
    
    FOREX_LATAM(
        displayName = "Latin America Pairs",
        parentType = AssetType.FOREX,
        riskTier = 4,
        description = "Latin American currencies (USD/BRL, USD/MXN, USD/ARS, USD/CLP, USD/COP, USD/PEN)"
    ),
    
    FOREX_EMEA(
        displayName = "EMEA Exotic Pairs",
        parentType = AssetType.FOREX,
        riskTier = 4,
        description = "Europe/Middle East/Africa (USD/PLN, USD/HUF, USD/CZK, USD/RUB, USD/ILS, USD/AED, USD/SAR)"
    ),
    
    // ============================================================================
    // PRECIOUS METALS - XAU, XAG, XPT, XPD
    // ============================================================================
    
    PRECIOUS_METALS(
        displayName = "Precious Metals",
        parentType = AssetType.COMMODITIES,
        riskTier = 2,
        description = "Gold, Silver, Platinum, Palladium (XAU, XAG, XPT, XPD)"
    ),
    
    // ============================================================================
    // INDUSTRIAL METALS - CRITICAL FOR SUPPLY CHAIN
    // ============================================================================
    
    INDUSTRIAL_METALS(
        displayName = "Industrial Metals",
        parentType = AssetType.COMMODITIES,
        riskTier = 3,
        description = "Base/industrial metals (XCU/Copper, XAL/Aluminium, XNI/Nickel, XZN/Zinc, XPB/Lead, XSN/Tin)"
    ),
    
    // ============================================================================
    // RARE EARTH / STRATEGIC METALS (via ETFs)
    // ============================================================================
    
    RARE_EARTH_PROXY(
        displayName = "Rare Earth ETFs",
        parentType = AssetType.ETFS,
        riskTier = 4,
        description = "Rare earth and strategic metals exposure (REMX, PICK, COPX, XME)"
    ),
    
    BATTERY_METALS_PROXY(
        displayName = "Battery Metals ETFs",
        parentType = AssetType.ETFS,
        riskTier = 4,
        description = "Lithium, Cobalt, Nickel exposure (LIT, BATT, DRIV, KARS)"
    ),
    
    URANIUM_PROXY(
        displayName = "Uranium ETFs",
        parentType = AssetType.ETFS,
        riskTier = 4,
        description = "Uranium and nuclear energy (URA, URNM, NLR, NUCL)"
    ),
    
    // ============================================================================
    // ENERGY COMMODITIES
    // ============================================================================
    
    ENERGY(
        displayName = "Energy",
        parentType = AssetType.COMMODITIES,
        riskTier = 3,
        description = "Crude Oil, Natural Gas, Brent, Heating Oil, Gasoline (XTI, XNG, XBR)"
    ),
    
    // ============================================================================
    // AGRICULTURAL COMMODITIES
    // ============================================================================
    
    AGRICULTURE(
        displayName = "Agriculture",
        parentType = AssetType.COMMODITIES,
        riskTier = 3,
        description = "Grains - Wheat, Corn, Soybeans, Rice, Oats"
    ),
    
    SOFT_COMMODITIES(
        displayName = "Soft Commodities",
        parentType = AssetType.COMMODITIES,
        riskTier = 3,
        description = "Coffee, Sugar, Cotton, Cocoa, Orange Juice, Lumber"
    ),
    
    LIVESTOCK(
        displayName = "Livestock",
        parentType = AssetType.COMMODITIES,
        riskTier = 3,
        description = "Cattle, Hogs, Feeder Cattle"
    ),
    
    // ============================================================================
    // EQUITIES
    // ============================================================================
    
    STOCKS_LARGE_CAP(
        displayName = "Large Cap Stocks",
        parentType = AssetType.STOCKS,
        riskTier = 2,
        description = "Large capitalization equities"
    ),
    
    STOCKS_MID_CAP(
        displayName = "Mid Cap Stocks",
        parentType = AssetType.STOCKS,
        riskTier = 3,
        description = "Medium capitalization equities"
    ),
    
    STOCKS_SMALL_CAP(
        displayName = "Small Cap Stocks",
        parentType = AssetType.STOCKS,
        riskTier = 4,
        description = "Small capitalization equities"
    ),
    
    STOCKS_MINING(
        displayName = "Mining Stocks",
        parentType = AssetType.STOCKS,
        riskTier = 4,
        description = "Gold, silver, copper, rare earth miners"
    ),
    
    // ============================================================================
    // FIXED INCOME
    // ============================================================================
    
    GOVERNMENT_BONDS(
        displayName = "Government Bonds",
        parentType = AssetType.BONDS,
        riskTier = 1,
        description = "Sovereign debt instruments"
    ),
    
    CORPORATE_BONDS(
        displayName = "Corporate Bonds",
        parentType = AssetType.BONDS,
        riskTier = 2,
        description = "Corporate debt instruments"
    ),
    
    // ============================================================================
    // INDICES & ETFs - COMPREHENSIVE
    // ============================================================================
    
    INDEX_MAJOR(
        displayName = "Major Indices",
        parentType = AssetType.ETFS,
        riskTier = 2,
        description = "Major market indices (SPY, QQQ, DIA, IWM, VTI)"
    ),
    
    INDEX_SECTOR(
        displayName = "Sector ETFs",
        parentType = AssetType.ETFS,
        riskTier = 3,
        description = "Sector-specific ETFs (XLF, XLE, XLK, XLV, XLI, XLB, XLP, XLU, XLY, XLRE)"
    ),
    
    INDEX_COMMODITY(
        displayName = "Commodity ETFs",
        parentType = AssetType.ETFS,
        riskTier = 3,
        description = "Commodity tracking ETFs (GLD, SLV, PPLT, PALL, USO, UNG, DBA, DBC)"
    ),
    
    INDEX_CURRENCY(
        displayName = "Currency ETFs",
        parentType = AssetType.ETFS,
        riskTier = 2,
        description = "Currency tracking ETFs (UUP, FXE, FXY, FXB, FXA, FXC, CYB)"
    ),
    
    INDEX_INTERNATIONAL(
        displayName = "International ETFs",
        parentType = AssetType.ETFS,
        riskTier = 3,
        description = "International market ETFs (EEM, VWO, EFA, VEA, IEMG)"
    ),
    
    // ============================================================================
    // OTHER
    // ============================================================================
    
    OTHER(
        displayName = "Other",
        parentType = AssetType.DERIVATIVES,
        riskTier = 3,
        description = "Uncategorized assets"
    );
    
    companion object {
        /**
         * Get all categories belonging to a specific AssetType.
         */
        fun forAssetType(type: AssetType): List<AssetCategory> =
            entries.filter { it.parentType == type }
        
        /**
         * Get all categories at or below a specific risk tier.
         */
        fun atOrBelowRisk(maxRisk: Int): List<AssetCategory> =
            entries.filter { it.riskTier <= maxRisk }
        
        /**
         * Get all cryptocurrency-related categories.
         */
        fun allCrypto(): List<AssetCategory> =
            entries.filter { 
                it.parentType == AssetType.CRYPTO_SPOT || 
                it.parentType == AssetType.CRYPTO_FUTURES 
            }
        
        /**
         * Get all forex categories.
         */
        fun allForex(): List<AssetCategory> =
            entries.filter { it.parentType == AssetType.FOREX }
        
        /**
         * Get all metals-related categories (precious + industrial + proxies).
         */
        fun allMetals(): List<AssetCategory> =
            listOf(PRECIOUS_METALS, INDUSTRIAL_METALS, RARE_EARTH_PROXY, BATTERY_METALS_PROXY, URANIUM_PROXY)
        
        /**
         * Get all commodity categories.
         */
        fun allCommodities(): List<AssetCategory> =
            entries.filter { it.parentType == AssetType.COMMODITIES }
        
        /**
         * Get all ETF categories.
         */
        fun allETFs(): List<AssetCategory> =
            entries.filter { it.parentType == AssetType.ETFS }
        
        /**
         * Get high-volatility scalping favorites.
         */
        fun scalpingFavorites(): List<AssetCategory> =
            listOf(MAJOR_CRYPTO, ALT_LAYER1, MEME, FOREX_MAJOR, PRECIOUS_METALS, INDUSTRIAL_METALS)
    }
}