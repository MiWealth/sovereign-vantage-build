package com.miwealth.sovereignvantage.data.models

/**
 * Cryptocurrency asset categories for risk classification and filtering.
 * Used by UpholdAssetLoader, AssetDiscoveryPipeline, and DynamicRiskAssigner.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 */
enum class CryptoCategory {
    LAYER1,             // BTC, ETH, SOL, ADA, etc.
    LAYER2,             // MATIC, ARB, OP, IMX
    DEFI,               // AAVE, UNI, LINK, MKR, CRV
    MEME,               // DOGE, SHIB, PEPE, FLOKI
    AI,                 // FET, AGIX, OCEAN, RNDR
    GAMING,             // AXS, SAND, MANA, GALA
    STABLECOIN,         // USDT, USDC, DAI, BUSD
    EXCHANGE_TOKEN,     // BNB, FTT, CRO
    INFRASTRUCTURE,     // ATOM, NEAR, ICP
    ORACLE,             // LINK, BAND, API3
    PRIVACY,            // XMR, ZEC, SCRT
    OTHER               // Everything else
}
