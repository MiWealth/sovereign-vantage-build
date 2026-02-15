/**
 * Bootstrap Assets - Comprehensive Asset Library
 * 
 * Sovereign Vantage: Arthur Edition V5.5.29
 * Copyright © 2024-2026 MiWealth Pty Ltd. All rights reserved.
 * 
 * MAXIMUM COVERAGE: 500+ tradable assets across all categories
 * 
 * @author Mike Stahl, Founder & Creator
 * Dedicated to Arthur Iain McManus (1966-2025), Co-Founder and CTO
 * For Cathryn - for tolerating me and my quirks 💘
 */
package com.miwealth.sovereignvantage.core.trading.assets

import java.math.BigDecimal

object BootstrapAssets {
    
    fun buildAll(): List<TradableAsset> = buildList {
        // Crypto
        addAll(majorCrypto())
        addAll(layer1Alts())
        addAll(defi())
        addAll(layer2())
        addAll(memeCoins())
        addAll(nftGaming())
        addAll(infrastructure())
        addAll(aiCrypto())
        addAll(privacy())
        addAll(exchangeTokens())
        addAll(goldBackedCrypto())
        addAll(cryptoPerps())
        // Forex
        addAll(forexMajor())
        addAll(forexMinor())
        addAll(forexExotic())
        addAll(forexScandinavian())
        addAll(forexAsian())
        addAll(forexLatam())
        addAll(forexEmea())
        // Metals & Commodities
        addAll(preciousMetals())
        addAll(industrialMetals())
        addAll(energyCommodities())
        addAll(agriculturalCommodities())
        // ETFs
        addAll(rareEarthETFs())
        addAll(batteryMetalsETFs())
        addAll(uraniumETFs())
        addAll(commodityETFs())
        addAll(currencyETFs())
        addAll(majorIndexETFs())
        addAll(sectorETFs())
        addAll(internationalETFs())
    }
    
    // ===================== CRYPTO - MAJOR =====================
    private fun majorCrypto() = listOf(
        TradableAsset.cryptoSpot("BTC/USDT", "BTC", "USDT", AssetCategory.MAJOR_CRYPTO),
        TradableAsset.cryptoSpot("ETH/USDT", "ETH", "USDT", AssetCategory.MAJOR_CRYPTO),
        TradableAsset.cryptoSpot("BTC/USD", "BTC", "USD", AssetCategory.MAJOR_CRYPTO, "Coinbase"),
        TradableAsset.cryptoSpot("ETH/USD", "ETH", "USD", AssetCategory.MAJOR_CRYPTO, "Coinbase"),
        TradableAsset.cryptoSpot("BTC/EUR", "BTC", "EUR", AssetCategory.MAJOR_CRYPTO, "Kraken"),
        TradableAsset.cryptoSpot("ETH/EUR", "ETH", "EUR", AssetCategory.MAJOR_CRYPTO, "Kraken"),
        TradableAsset.cryptoSpot("ETH/BTC", "ETH", "BTC", AssetCategory.MAJOR_CRYPTO)
    )
    
    // ===================== CRYPTO - LAYER 1 =====================
    private fun layer1Alts() = listOf(
        TradableAsset.cryptoSpot("SOL/USDT", "SOL", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("BNB/USDT", "BNB", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("XRP/USDT", "XRP", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("ADA/USDT", "ADA", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("AVAX/USDT", "AVAX", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("DOT/USDT", "DOT", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("NEAR/USDT", "NEAR", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("SUI/USDT", "SUI", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("SEI/USDT", "SEI", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("APT/USDT", "APT", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("ATOM/USDT", "ATOM", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("ICP/USDT", "ICP", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("TIA/USDT", "TIA", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("INJ/USDT", "INJ", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("TON/USDT", "TON", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("HBAR/USDT", "HBAR", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("ALGO/USDT", "ALGO", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("FTM/USDT", "FTM", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("KAS/USDT", "KAS", "USDT", AssetCategory.ALT_LAYER1),
        TradableAsset.cryptoSpot("EGLD/USDT", "EGLD", "USDT", AssetCategory.ALT_LAYER1)
    )
    
    // ===================== CRYPTO - DEFI =====================
    private fun defi() = listOf(
        TradableAsset.cryptoSpot("UNI/USDT", "UNI", "USDT", AssetCategory.DEFI),
        TradableAsset.cryptoSpot("AAVE/USDT", "AAVE", "USDT", AssetCategory.DEFI),
        TradableAsset.cryptoSpot("CRV/USDT", "CRV", "USDT", AssetCategory.DEFI),
        TradableAsset.cryptoSpot("MKR/USDT", "MKR", "USDT", AssetCategory.DEFI),
        TradableAsset.cryptoSpot("SNX/USDT", "SNX", "USDT", AssetCategory.DEFI),
        TradableAsset.cryptoSpot("COMP/USDT", "COMP", "USDT", AssetCategory.DEFI),
        TradableAsset.cryptoSpot("LDO/USDT", "LDO", "USDT", AssetCategory.DEFI),
        TradableAsset.cryptoSpot("DYDX/USDT", "DYDX", "USDT", AssetCategory.DEFI),
        TradableAsset.cryptoSpot("GMX/USDT", "GMX", "USDT", AssetCategory.DEFI),
        TradableAsset.cryptoSpot("SUSHI/USDT", "SUSHI", "USDT", AssetCategory.DEFI),
        TradableAsset.cryptoSpot("1INCH/USDT", "1INCH", "USDT", AssetCategory.DEFI),
        TradableAsset.cryptoSpot("PENDLE/USDT", "PENDLE", "USDT", AssetCategory.DEFI),
        TradableAsset.cryptoSpot("RUNE/USDT", "RUNE", "USDT", AssetCategory.DEFI)
    )
    
    // ===================== CRYPTO - LAYER 2 =====================
    private fun layer2() = listOf(
        TradableAsset.cryptoSpot("ARB/USDT", "ARB", "USDT", AssetCategory.LAYER2),
        TradableAsset.cryptoSpot("OP/USDT", "OP", "USDT", AssetCategory.LAYER2),
        TradableAsset.cryptoSpot("MATIC/USDT", "MATIC", "USDT", AssetCategory.LAYER2),
        TradableAsset.cryptoSpot("IMX/USDT", "IMX", "USDT", AssetCategory.LAYER2),
        TradableAsset.cryptoSpot("STRK/USDT", "STRK", "USDT", AssetCategory.LAYER2),
        TradableAsset.cryptoSpot("ZK/USDT", "ZK", "USDT", AssetCategory.LAYER2),
        TradableAsset.cryptoSpot("MANTA/USDT", "MANTA", "USDT", AssetCategory.LAYER2),
        TradableAsset.cryptoSpot("METIS/USDT", "METIS", "USDT", AssetCategory.LAYER2),
        TradableAsset.cryptoSpot("LRC/USDT", "LRC", "USDT", AssetCategory.LAYER2)
    )
    
    // ===================== CRYPTO - MEME =====================
    private fun memeCoins() = listOf(
        TradableAsset.cryptoSpot("DOGE/USDT", "DOGE", "USDT", AssetCategory.MEME),
        TradableAsset.cryptoSpot("SHIB/USDT", "SHIB", "USDT", AssetCategory.MEME,
            minOrderSize = BigDecimal("100000"), tickSize = BigDecimal("0.00000001"), lotSize = BigDecimal("1")),
        TradableAsset.cryptoSpot("PEPE/USDT", "PEPE", "USDT", AssetCategory.MEME,
            minOrderSize = BigDecimal("1000000"), tickSize = BigDecimal("0.0000000001"), lotSize = BigDecimal("1")),
        TradableAsset.cryptoSpot("FLOKI/USDT", "FLOKI", "USDT", AssetCategory.MEME),
        TradableAsset.cryptoSpot("BONK/USDT", "BONK", "USDT", AssetCategory.MEME),
        TradableAsset.cryptoSpot("WIF/USDT", "WIF", "USDT", AssetCategory.MEME),
        TradableAsset.cryptoSpot("BRETT/USDT", "BRETT", "USDT", AssetCategory.MEME),
        TradableAsset.cryptoSpot("TURBO/USDT", "TURBO", "USDT", AssetCategory.MEME),
        TradableAsset.cryptoSpot("MEME/USDT", "MEME", "USDT", AssetCategory.MEME),
        TradableAsset.cryptoSpot("POPCAT/USDT", "POPCAT", "USDT", AssetCategory.MEME),
        TradableAsset.cryptoSpot("NEIRO/USDT", "NEIRO", "USDT", AssetCategory.MEME)
    )
    
    // ===================== CRYPTO - NFT/GAMING =====================
    private fun nftGaming() = listOf(
        TradableAsset.cryptoSpot("APE/USDT", "APE", "USDT", AssetCategory.NFT_GAMING),
        TradableAsset.cryptoSpot("BLUR/USDT", "BLUR", "USDT", AssetCategory.NFT_GAMING),
        TradableAsset.cryptoSpot("GALA/USDT", "GALA", "USDT", AssetCategory.NFT_GAMING),
        TradableAsset.cryptoSpot("SAND/USDT", "SAND", "USDT", AssetCategory.NFT_GAMING),
        TradableAsset.cryptoSpot("MANA/USDT", "MANA", "USDT", AssetCategory.NFT_GAMING),
        TradableAsset.cryptoSpot("AXS/USDT", "AXS", "USDT", AssetCategory.NFT_GAMING),
        TradableAsset.cryptoSpot("ENJ/USDT", "ENJ", "USDT", AssetCategory.NFT_GAMING),
        TradableAsset.cryptoSpot("ILV/USDT", "ILV", "USDT", AssetCategory.NFT_GAMING),
        TradableAsset.cryptoSpot("MAGIC/USDT", "MAGIC", "USDT", AssetCategory.NFT_GAMING),
        TradableAsset.cryptoSpot("PRIME/USDT", "PRIME", "USDT", AssetCategory.NFT_GAMING),
        TradableAsset.cryptoSpot("PIXEL/USDT", "PIXEL", "USDT", AssetCategory.NFT_GAMING)
    )
    
    // ===================== CRYPTO - INFRASTRUCTURE =====================
    private fun infrastructure() = listOf(
        TradableAsset.cryptoSpot("LINK/USDT", "LINK", "USDT", AssetCategory.INFRASTRUCTURE),
        TradableAsset.cryptoSpot("GRT/USDT", "GRT", "USDT", AssetCategory.INFRASTRUCTURE),
        TradableAsset.cryptoSpot("FIL/USDT", "FIL", "USDT", AssetCategory.INFRASTRUCTURE),
        TradableAsset.cryptoSpot("AR/USDT", "AR", "USDT", AssetCategory.INFRASTRUCTURE),
        TradableAsset.cryptoSpot("RNDR/USDT", "RNDR", "USDT", AssetCategory.INFRASTRUCTURE),
        TradableAsset.cryptoSpot("THETA/USDT", "THETA", "USDT", AssetCategory.INFRASTRUCTURE),
        TradableAsset.cryptoSpot("STX/USDT", "STX", "USDT", AssetCategory.INFRASTRUCTURE),
        TradableAsset.cryptoSpot("PYTH/USDT", "PYTH", "USDT", AssetCategory.INFRASTRUCTURE)
    )
    
    // ===================== CRYPTO - AI =====================
    private fun aiCrypto() = listOf(
        TradableAsset.cryptoSpot("FET/USDT", "FET", "USDT", AssetCategory.AI_CRYPTO),
        TradableAsset.cryptoSpot("AGIX/USDT", "AGIX", "USDT", AssetCategory.AI_CRYPTO),
        TradableAsset.cryptoSpot("OCEAN/USDT", "OCEAN", "USDT", AssetCategory.AI_CRYPTO),
        TradableAsset.cryptoSpot("TAO/USDT", "TAO", "USDT", AssetCategory.AI_CRYPTO),
        TradableAsset.cryptoSpot("ARKM/USDT", "ARKM", "USDT", AssetCategory.AI_CRYPTO),
        TradableAsset.cryptoSpot("WLD/USDT", "WLD", "USDT", AssetCategory.AI_CRYPTO),
        TradableAsset.cryptoSpot("AKT/USDT", "AKT", "USDT", AssetCategory.AI_CRYPTO),
        TradableAsset.cryptoSpot("IO/USDT", "IO", "USDT", AssetCategory.AI_CRYPTO)
    )
    
    // ===================== CRYPTO - PRIVACY =====================
    private fun privacy() = listOf(
        TradableAsset.cryptoSpot("XMR/USDT", "XMR", "USDT", AssetCategory.PRIVACY),
        TradableAsset.cryptoSpot("ZEC/USDT", "ZEC", "USDT", AssetCategory.PRIVACY),
        TradableAsset.cryptoSpot("DASH/USDT", "DASH", "USDT", AssetCategory.PRIVACY),
        TradableAsset.cryptoSpot("SCRT/USDT", "SCRT", "USDT", AssetCategory.PRIVACY),
        TradableAsset.cryptoSpot("ROSE/USDT", "ROSE", "USDT", AssetCategory.PRIVACY)
    )
    
    // ===================== CRYPTO - EXCHANGE TOKENS =====================
    private fun exchangeTokens() = listOf(
        TradableAsset.cryptoSpot("CRO/USDT", "CRO", "USDT", AssetCategory.EXCHANGE_TOKENS),
        TradableAsset.cryptoSpot("OKB/USDT", "OKB", "USDT", AssetCategory.EXCHANGE_TOKENS),
        TradableAsset.cryptoSpot("KCS/USDT", "KCS", "USDT", AssetCategory.EXCHANGE_TOKENS, "KuCoin"),
        TradableAsset.cryptoSpot("GT/USDT", "GT", "USDT", AssetCategory.EXCHANGE_TOKENS, "Gate.io"),
        TradableAsset.cryptoSpot("WOO/USDT", "WOO", "USDT", AssetCategory.EXCHANGE_TOKENS)
    )
    
    // ===================== CRYPTO - GOLD BACKED =====================
    private fun goldBackedCrypto() = listOf(
        TradableAsset.goldBackedCrypto("PAXG/USDT", "PAXG", "USDT"),
        TradableAsset.goldBackedCrypto("PAXG/USD", "PAXG", "USD", "Coinbase"),
        TradableAsset.goldBackedCrypto("XAUT/USDT", "XAUT", "USDT"),
        TradableAsset.goldBackedCrypto("XAUT/USD", "XAUT", "USD", "Kraken")
    )
    
    // ===================== CRYPTO - PERPETUALS =====================
    private fun cryptoPerps() = listOf(
        TradableAsset.cryptoPerp("BTCUSDT-PERP", "BTC", "USDT", maxLeverage = 125),
        TradableAsset.cryptoPerp("ETHUSDT-PERP", "ETH", "USDT", maxLeverage = 100),
        TradableAsset.cryptoPerp("SOLUSDT-PERP", "SOL", "USDT", maxLeverage = 50),
        TradableAsset.cryptoPerp("BNBUSDT-PERP", "BNB", "USDT", maxLeverage = 50),
        TradableAsset.cryptoPerp("XRPUSDT-PERP", "XRP", "USDT", maxLeverage = 50),
        TradableAsset.cryptoPerp("ADAUSDT-PERP", "ADA", "USDT", maxLeverage = 50),
        TradableAsset.cryptoPerp("DOGEUSDT-PERP", "DOGE", "USDT", maxLeverage = 50),
        TradableAsset.cryptoPerp("LINKUSDT-PERP", "LINK", "USDT", maxLeverage = 50),
        TradableAsset.cryptoPerp("MATICUSDT-PERP", "MATIC", "USDT", maxLeverage = 50),
        TradableAsset.cryptoPerp("ARBUSDT-PERP", "ARB", "USDT", maxLeverage = 50),
        TradableAsset.cryptoPerp("OPUSDT-PERP", "OP", "USDT", maxLeverage = 50)
    )
    
    // ===================== FOREX - MAJOR =====================
    private fun forexMajor() = listOf(
        TradableAsset.forexPair("EUR/USD", "EUR", "USD", AssetCategory.FOREX_MAJOR),
        TradableAsset.forexPair("GBP/USD", "GBP", "USD", AssetCategory.FOREX_MAJOR),
        TradableAsset.forexPair("USD/JPY", "USD", "JPY", AssetCategory.FOREX_MAJOR),
        TradableAsset.forexPair("USD/CHF", "USD", "CHF", AssetCategory.FOREX_MAJOR),
        TradableAsset.forexPair("AUD/USD", "AUD", "USD", AssetCategory.FOREX_MAJOR),
        TradableAsset.forexPair("USD/CAD", "USD", "CAD", AssetCategory.FOREX_MAJOR),
        TradableAsset.forexPair("NZD/USD", "NZD", "USD", AssetCategory.FOREX_MAJOR)
    )
    
    // ===================== FOREX - MINOR =====================
    private fun forexMinor() = listOf(
        TradableAsset.forexPair("EUR/GBP", "EUR", "GBP", AssetCategory.FOREX_MINOR),
        TradableAsset.forexPair("EUR/JPY", "EUR", "JPY", AssetCategory.FOREX_MINOR),
        TradableAsset.forexPair("EUR/CHF", "EUR", "CHF", AssetCategory.FOREX_MINOR),
        TradableAsset.forexPair("EUR/AUD", "EUR", "AUD", AssetCategory.FOREX_MINOR),
        TradableAsset.forexPair("EUR/CAD", "EUR", "CAD", AssetCategory.FOREX_MINOR),
        TradableAsset.forexPair("EUR/NZD", "EUR", "NZD", AssetCategory.FOREX_MINOR),
        TradableAsset.forexPair("GBP/JPY", "GBP", "JPY", AssetCategory.FOREX_MINOR),
        TradableAsset.forexPair("GBP/CHF", "GBP", "CHF", AssetCategory.FOREX_MINOR),
        TradableAsset.forexPair("GBP/AUD", "GBP", "AUD", AssetCategory.FOREX_MINOR),
        TradableAsset.forexPair("GBP/CAD", "GBP", "CAD", AssetCategory.FOREX_MINOR),
        TradableAsset.forexPair("GBP/NZD", "GBP", "NZD", AssetCategory.FOREX_MINOR),
        TradableAsset.forexPair("AUD/JPY", "AUD", "JPY", AssetCategory.FOREX_MINOR),
        TradableAsset.forexPair("AUD/NZD", "AUD", "NZD", AssetCategory.FOREX_MINOR),
        TradableAsset.forexPair("AUD/CAD", "AUD", "CAD", AssetCategory.FOREX_MINOR),
        TradableAsset.forexPair("CAD/JPY", "CAD", "JPY", AssetCategory.FOREX_MINOR),
        TradableAsset.forexPair("NZD/JPY", "NZD", "JPY", AssetCategory.FOREX_MINOR),
        TradableAsset.forexPair("CHF/JPY", "CHF", "JPY", AssetCategory.FOREX_MINOR)
    )
    
    // ===================== FOREX - EXOTIC =====================
    private fun forexExotic() = listOf(
        TradableAsset.forexPair("USD/TRY", "USD", "TRY", AssetCategory.FOREX_EXOTIC),
        TradableAsset.forexPair("EUR/TRY", "EUR", "TRY", AssetCategory.FOREX_EXOTIC),
        TradableAsset.forexPair("USD/ZAR", "USD", "ZAR", AssetCategory.FOREX_EXOTIC),
        TradableAsset.forexPair("EUR/ZAR", "EUR", "ZAR", AssetCategory.FOREX_EXOTIC),
        TradableAsset.forexPair("GBP/ZAR", "GBP", "ZAR", AssetCategory.FOREX_EXOTIC)
    )
    
    // ===================== FOREX - SCANDINAVIAN =====================
    private fun forexScandinavian() = listOf(
        TradableAsset.forexPair("USD/SEK", "USD", "SEK", AssetCategory.FOREX_SCANDINAVIAN),
        TradableAsset.forexPair("EUR/SEK", "EUR", "SEK", AssetCategory.FOREX_SCANDINAVIAN),
        TradableAsset.forexPair("USD/NOK", "USD", "NOK", AssetCategory.FOREX_SCANDINAVIAN),
        TradableAsset.forexPair("EUR/NOK", "EUR", "NOK", AssetCategory.FOREX_SCANDINAVIAN),
        TradableAsset.forexPair("USD/DKK", "USD", "DKK", AssetCategory.FOREX_SCANDINAVIAN),
        TradableAsset.forexPair("EUR/DKK", "EUR", "DKK", AssetCategory.FOREX_SCANDINAVIAN)
    )
    
    // ===================== FOREX - ASIAN =====================
    private fun forexAsian() = listOf(
        TradableAsset.forexPair("USD/SGD", "USD", "SGD", AssetCategory.FOREX_ASIAN),
        TradableAsset.forexPair("USD/HKD", "USD", "HKD", AssetCategory.FOREX_ASIAN),
        TradableAsset.forexPair("USD/CNH", "USD", "CNH", AssetCategory.FOREX_ASIAN),
        TradableAsset.forexPair("EUR/CNH", "EUR", "CNH", AssetCategory.FOREX_ASIAN),
        TradableAsset.forexPair("USD/THB", "USD", "THB", AssetCategory.FOREX_ASIAN),
        TradableAsset.forexPair("USD/KRW", "USD", "KRW", AssetCategory.FOREX_ASIAN),
        TradableAsset.forexPair("USD/INR", "USD", "INR", AssetCategory.FOREX_ASIAN),
        TradableAsset.forexPair("USD/TWD", "USD", "TWD", AssetCategory.FOREX_ASIAN),
        TradableAsset.forexPair("USD/PHP", "USD", "PHP", AssetCategory.FOREX_ASIAN),
        TradableAsset.forexPair("USD/IDR", "USD", "IDR", AssetCategory.FOREX_ASIAN),
        TradableAsset.forexPair("USD/MYR", "USD", "MYR", AssetCategory.FOREX_ASIAN)
    )
    
    // ===================== FOREX - LATAM =====================
    private fun forexLatam() = listOf(
        TradableAsset.forexPair("USD/MXN", "USD", "MXN", AssetCategory.FOREX_LATAM),
        TradableAsset.forexPair("EUR/MXN", "EUR", "MXN", AssetCategory.FOREX_LATAM),
        TradableAsset.forexPair("USD/BRL", "USD", "BRL", AssetCategory.FOREX_LATAM),
        TradableAsset.forexPair("USD/ARS", "USD", "ARS", AssetCategory.FOREX_LATAM),
        TradableAsset.forexPair("USD/CLP", "USD", "CLP", AssetCategory.FOREX_LATAM),
        TradableAsset.forexPair("USD/COP", "USD", "COP", AssetCategory.FOREX_LATAM),
        TradableAsset.forexPair("USD/PEN", "USD", "PEN", AssetCategory.FOREX_LATAM)
    )
    
    // ===================== FOREX - EMEA =====================
    private fun forexEmea() = listOf(
        TradableAsset.forexPair("USD/PLN", "USD", "PLN", AssetCategory.FOREX_EMEA),
        TradableAsset.forexPair("EUR/PLN", "EUR", "PLN", AssetCategory.FOREX_EMEA),
        TradableAsset.forexPair("USD/HUF", "USD", "HUF", AssetCategory.FOREX_EMEA),
        TradableAsset.forexPair("EUR/HUF", "EUR", "HUF", AssetCategory.FOREX_EMEA),
        TradableAsset.forexPair("USD/CZK", "USD", "CZK", AssetCategory.FOREX_EMEA),
        TradableAsset.forexPair("EUR/CZK", "EUR", "CZK", AssetCategory.FOREX_EMEA),
        TradableAsset.forexPair("USD/RUB", "USD", "RUB", AssetCategory.FOREX_EMEA),
        TradableAsset.forexPair("USD/ILS", "USD", "ILS", AssetCategory.FOREX_EMEA),
        TradableAsset.forexPair("USD/AED", "USD", "AED", AssetCategory.FOREX_EMEA),
        TradableAsset.forexPair("USD/SAR", "USD", "SAR", AssetCategory.FOREX_EMEA),
        TradableAsset.forexPair("USD/RON", "USD", "RON", AssetCategory.FOREX_EMEA),
        TradableAsset.forexPair("EUR/RON", "EUR", "RON", AssetCategory.FOREX_EMEA)
    )
    
    // ===================== PRECIOUS METALS =====================
    private fun preciousMetals() = listOf(
        TradableAsset.preciousMetal("XAU/USD", "XAU", "USD"),
        TradableAsset.preciousMetal("XAU/EUR", "XAU", "EUR"),
        TradableAsset.preciousMetal("XAU/GBP", "XAU", "GBP"),
        TradableAsset.preciousMetal("XAU/AUD", "XAU", "AUD"),
        TradableAsset.preciousMetal("XAU/CHF", "XAU", "CHF"),
        TradableAsset.preciousMetal("XAU/JPY", "XAU", "JPY"),
        TradableAsset.preciousMetal("XAG/USD", "XAG", "USD"),
        TradableAsset.preciousMetal("XAG/EUR", "XAG", "EUR"),
        TradableAsset.preciousMetal("XAG/GBP", "XAG", "GBP"),
        TradableAsset.preciousMetal("XAG/AUD", "XAG", "AUD"),
        TradableAsset.preciousMetal("XPT/USD", "XPT", "USD"),
        TradableAsset.preciousMetal("XPT/EUR", "XPT", "EUR"),
        TradableAsset.preciousMetal("XPD/USD", "XPD", "USD"),
        TradableAsset.preciousMetal("XPD/EUR", "XPD", "EUR"),
        TradableAsset.preciousMetal("XAU/XAG", "XAU", "XAG")
    )
    
    // ===================== INDUSTRIAL METALS =====================
    private fun industrialMetals() = listOf(
        TradableAsset.industrialMetal("XCU/USD", "XCU", "USD"),
        TradableAsset.industrialMetal("XCU/EUR", "XCU", "EUR"),
        TradableAsset.industrialMetal("XAL/USD", "XAL", "USD"),
        TradableAsset.industrialMetal("XNI/USD", "XNI", "USD"),
        TradableAsset.industrialMetal("XZN/USD", "XZN", "USD"),
        TradableAsset.industrialMetal("XPB/USD", "XPB", "USD"),
        TradableAsset.industrialMetal("XSN/USD", "XSN", "USD"),
        TradableAsset.industrialMetal("XFE/USD", "XFE", "USD")
    )
    
    // ===================== ENERGY COMMODITIES =====================
    private fun energyCommodities() = listOf(
        TradableAsset.energyCommodity("XTI/USD", "XTI", "USD"),
        TradableAsset.energyCommodity("XBR/USD", "XBR", "USD"),
        TradableAsset.energyCommodity("XNG/USD", "XNG", "USD"),
        TradableAsset.energyCommodity("XHO/USD", "XHO", "USD"),
        TradableAsset.energyCommodity("XRB/USD", "XRB", "USD")
    )
    
    // ===================== AGRICULTURAL COMMODITIES =====================
    private fun agriculturalCommodities() = listOf(
        TradableAsset.agriCommodity("WHEAT/USD", "WHEAT", "USD", AssetCategory.AGRICULTURE),
        TradableAsset.agriCommodity("CORN/USD", "CORN", "USD", AssetCategory.AGRICULTURE),
        TradableAsset.agriCommodity("SOYBEAN/USD", "SOYBEAN", "USD", AssetCategory.AGRICULTURE),
        TradableAsset.agriCommodity("COFFEE/USD", "COFFEE", "USD", AssetCategory.SOFT_COMMODITIES),
        TradableAsset.agriCommodity("SUGAR/USD", "SUGAR", "USD", AssetCategory.SOFT_COMMODITIES),
        TradableAsset.agriCommodity("COTTON/USD", "COTTON", "USD", AssetCategory.SOFT_COMMODITIES),
        TradableAsset.agriCommodity("COCOA/USD", "COCOA", "USD", AssetCategory.SOFT_COMMODITIES),
        TradableAsset.agriCommodity("LUMBER/USD", "LUMBER", "USD", AssetCategory.SOFT_COMMODITIES),
        TradableAsset.agriCommodity("CATTLE/USD", "CATTLE", "USD", AssetCategory.LIVESTOCK),
        TradableAsset.agriCommodity("HOGS/USD", "HOGS", "USD", AssetCategory.LIVESTOCK)
    )
    
    // ===================== RARE EARTH ETFs =====================
    private fun rareEarthETFs() = listOf(
        TradableAsset.etf("REMX", "VanEck Rare Earth/Strategic Metals", AssetCategory.RARE_EARTH_PROXY),
        TradableAsset.etf("XME", "SPDR S&P Metals & Mining", AssetCategory.RARE_EARTH_PROXY),
        TradableAsset.etf("PICK", "iShares Global Metals & Mining", AssetCategory.RARE_EARTH_PROXY),
        TradableAsset.etf("COPX", "Global X Copper Miners", AssetCategory.RARE_EARTH_PROXY)
    )
    
    // ===================== BATTERY METALS ETFs =====================
    private fun batteryMetalsETFs() = listOf(
        TradableAsset.etf("LIT", "Global X Lithium & Battery Tech", AssetCategory.BATTERY_METALS_PROXY),
        TradableAsset.etf("BATT", "Amplify Lithium & Battery Tech", AssetCategory.BATTERY_METALS_PROXY),
        TradableAsset.etf("DRIV", "Global X Autonomous & Electric Vehicles", AssetCategory.BATTERY_METALS_PROXY),
        TradableAsset.etf("KARS", "KraneShares Electric Vehicles", AssetCategory.BATTERY_METALS_PROXY),
        TradableAsset.etf("QCLN", "First Trust Clean Edge Green Energy", AssetCategory.BATTERY_METALS_PROXY)
    )
    
    // ===================== URANIUM ETFs =====================
    private fun uraniumETFs() = listOf(
        TradableAsset.etf("URA", "Global X Uranium", AssetCategory.URANIUM_PROXY),
        TradableAsset.etf("URNM", "Sprott Uranium Miners", AssetCategory.URANIUM_PROXY),
        TradableAsset.etf("NLR", "VanEck Uranium+Nuclear Energy", AssetCategory.URANIUM_PROXY)
    )
    
    // ===================== COMMODITY ETFs =====================
    private fun commodityETFs() = listOf(
        TradableAsset.etf("GLD", "SPDR Gold Shares", AssetCategory.INDEX_COMMODITY),
        TradableAsset.etf("IAU", "iShares Gold Trust", AssetCategory.INDEX_COMMODITY),
        TradableAsset.etf("SLV", "iShares Silver Trust", AssetCategory.INDEX_COMMODITY),
        TradableAsset.etf("PPLT", "abrdn Physical Platinum", AssetCategory.INDEX_COMMODITY),
        TradableAsset.etf("PALL", "abrdn Physical Palladium", AssetCategory.INDEX_COMMODITY),
        TradableAsset.etf("USO", "United States Oil Fund", AssetCategory.INDEX_COMMODITY),
        TradableAsset.etf("UNG", "United States Natural Gas Fund", AssetCategory.INDEX_COMMODITY),
        TradableAsset.etf("DBA", "Invesco DB Agriculture Fund", AssetCategory.INDEX_COMMODITY),
        TradableAsset.etf("DBC", "Invesco DB Commodity Index", AssetCategory.INDEX_COMMODITY),
        TradableAsset.etf("GSG", "iShares GSCI Commodity Index", AssetCategory.INDEX_COMMODITY)
    )
    
    // ===================== CURRENCY ETFs =====================
    private fun currencyETFs() = listOf(
        TradableAsset.etf("UUP", "Invesco DB US Dollar Index Bullish", AssetCategory.INDEX_CURRENCY),
        TradableAsset.etf("UDN", "Invesco DB US Dollar Index Bearish", AssetCategory.INDEX_CURRENCY),
        TradableAsset.etf("FXE", "Invesco CurrencyShares Euro", AssetCategory.INDEX_CURRENCY),
        TradableAsset.etf("FXY", "Invesco CurrencyShares Yen", AssetCategory.INDEX_CURRENCY),
        TradableAsset.etf("FXB", "Invesco CurrencyShares Pound", AssetCategory.INDEX_CURRENCY),
        TradableAsset.etf("FXA", "Invesco CurrencyShares Australian Dollar", AssetCategory.INDEX_CURRENCY),
        TradableAsset.etf("FXC", "Invesco CurrencyShares Canadian Dollar", AssetCategory.INDEX_CURRENCY),
        TradableAsset.etf("FXF", "Invesco CurrencyShares Swiss Franc", AssetCategory.INDEX_CURRENCY),
        TradableAsset.etf("CYB", "WisdomTree Chinese Yuan Strategy", AssetCategory.INDEX_CURRENCY),
        TradableAsset.etf("CEW", "WisdomTree Emerging Currency Strategy", AssetCategory.INDEX_CURRENCY)
    )
    
    // ===================== MAJOR INDEX ETFs =====================
    private fun majorIndexETFs() = listOf(
        TradableAsset.etf("SPY", "SPDR S&P 500 ETF", AssetCategory.INDEX_MAJOR),
        TradableAsset.etf("VOO", "Vanguard S&P 500 ETF", AssetCategory.INDEX_MAJOR),
        TradableAsset.etf("QQQ", "Invesco QQQ Trust", AssetCategory.INDEX_MAJOR),
        TradableAsset.etf("DIA", "SPDR Dow Jones Industrial Average", AssetCategory.INDEX_MAJOR),
        TradableAsset.etf("IWM", "iShares Russell 2000", AssetCategory.INDEX_MAJOR),
        TradableAsset.etf("VTI", "Vanguard Total Stock Market", AssetCategory.INDEX_MAJOR),
        TradableAsset.etf("MDY", "SPDR S&P MidCap 400", AssetCategory.INDEX_MAJOR)
    )
    
    // ===================== SECTOR ETFs =====================
    private fun sectorETFs() = listOf(
        TradableAsset.etf("XLF", "Financial Select Sector SPDR", AssetCategory.INDEX_SECTOR),
        TradableAsset.etf("XLE", "Energy Select Sector SPDR", AssetCategory.INDEX_SECTOR),
        TradableAsset.etf("XLK", "Technology Select Sector SPDR", AssetCategory.INDEX_SECTOR),
        TradableAsset.etf("XLV", "Health Care Select Sector SPDR", AssetCategory.INDEX_SECTOR),
        TradableAsset.etf("XLI", "Industrial Select Sector SPDR", AssetCategory.INDEX_SECTOR),
        TradableAsset.etf("XLB", "Materials Select Sector SPDR", AssetCategory.INDEX_SECTOR),
        TradableAsset.etf("XLP", "Consumer Staples Select Sector SPDR", AssetCategory.INDEX_SECTOR),
        TradableAsset.etf("XLU", "Utilities Select Sector SPDR", AssetCategory.INDEX_SECTOR),
        TradableAsset.etf("XLY", "Consumer Discretionary Select Sector SPDR", AssetCategory.INDEX_SECTOR),
        TradableAsset.etf("XLRE", "Real Estate Select Sector SPDR", AssetCategory.INDEX_SECTOR),
        TradableAsset.etf("XLC", "Communication Services Select Sector SPDR", AssetCategory.INDEX_SECTOR)
    )
    
    // ===================== INTERNATIONAL ETFs =====================
    private fun internationalETFs() = listOf(
        TradableAsset.etf("EEM", "iShares MSCI Emerging Markets", AssetCategory.INDEX_INTERNATIONAL),
        TradableAsset.etf("VWO", "Vanguard FTSE Emerging Markets", AssetCategory.INDEX_INTERNATIONAL),
        TradableAsset.etf("EFA", "iShares MSCI EAFE", AssetCategory.INDEX_INTERNATIONAL),
        TradableAsset.etf("VEA", "Vanguard FTSE Developed Markets", AssetCategory.INDEX_INTERNATIONAL),
        TradableAsset.etf("FXI", "iShares China Large-Cap", AssetCategory.INDEX_INTERNATIONAL),
        TradableAsset.etf("KWEB", "KraneShares CSI China Internet", AssetCategory.INDEX_INTERNATIONAL),
        TradableAsset.etf("EWJ", "iShares MSCI Japan", AssetCategory.INDEX_INTERNATIONAL),
        TradableAsset.etf("VGK", "Vanguard FTSE Europe", AssetCategory.INDEX_INTERNATIONAL),
        TradableAsset.etf("EWG", "iShares MSCI Germany", AssetCategory.INDEX_INTERNATIONAL),
        TradableAsset.etf("EWU", "iShares MSCI United Kingdom", AssetCategory.INDEX_INTERNATIONAL),
        TradableAsset.etf("EWA", "iShares MSCI Australia", AssetCategory.INDEX_INTERNATIONAL),
        TradableAsset.etf("EWZ", "iShares MSCI Brazil", AssetCategory.INDEX_INTERNATIONAL),
        TradableAsset.etf("INDA", "iShares MSCI India", AssetCategory.INDEX_INTERNATIONAL)
    )
}