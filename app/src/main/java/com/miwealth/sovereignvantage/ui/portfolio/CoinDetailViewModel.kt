/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * COIN DETAIL VIEWMODEL — Generates realistic asset statistics on the fly
 *
 * Uses deterministic seeded random based on symbol for consistent display,
 * plus time-based micro-variation for live feel.
 *
 * © 2025-2026 MiWealth Pty Ltd
 */

package com.miwealth.sovereignvantage.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.*
import kotlin.random.Random

// ============================================================================
// DATA MODELS
// ============================================================================

enum class ChartTimeframe(val label: String, val dataPoints: Int) {
    M30("30m", 30),
    H1("1H", 60),
    H2("2H", 60),
    H4("4H", 60),
    H8("8H", 60),
    D1("1D", 48),
    M1("1M", 30),
    M3("3M", 90),
    Y1("1Y", 52)
}

data class PricePoint(
    val timestamp: Long,
    val price: Double
)

data class CoinDetailUiState(
    val isLoading: Boolean = true,
    val symbol: String = "",
    val name: String = "",
    val currentPrice: Double = 0.0,
    val priceChange24h: Double = 0.0,
    val priceChange24hPercent: Double = 0.0,
    val selectedTimeframe: ChartTimeframe = ChartTimeframe.D1,
    val chartData: List<PricePoint> = emptyList(),
    // Ranges
    val dayLow: Double = 0.0,
    val dayHigh: Double = 0.0,
    val yearLow: Double = 0.0,
    val yearHigh: Double = 0.0,
    val allTimeHigh: Double = 0.0,
    val allTimeHighDate: String = "",
    // Holdings
    val holdingAmount: Double = 0.0,
    val holdingValue: Double = 0.0,
    val costBasis: Double = 0.0,
    val unrealisedPnL: Double = 0.0,
    val unrealisedPnLPercent: Double = 0.0,
    // Metadata
    val description: String = "",
    val marketCap: String = "",
    val volume24h: String = "",
    val circulatingSupply: String = "",
    val category: String = "",
    val primaryChain: String = ""
)

// ============================================================================
// COIN METADATA — Covers major assets
// ============================================================================

private data class CoinMeta(
    val name: String,
    val description: String,
    val basePrice: Double,        // Approximate current-era price
    val athPrice: Double,
    val athDate: String,
    val marketCapBillions: Double,
    val chain: String,
    val category: String,
    val supplyMillions: Double
)

private val COIN_DATABASE = mapOf(
    "BTC" to CoinMeta("Bitcoin", "The original cryptocurrency and store of value. A peer-to-peer electronic cash system first described in 2008 by Satoshi Nakamoto, Bitcoin pioneered decentralised consensus through proof-of-work mining.", 98000.0, 109000.0, "Jan 2025", 1920.0, "Bitcoin", "Layer 1", 19.8),
    "ETH" to CoinMeta("Ethereum", "The leading smart contract platform enabling decentralised applications, DeFi, and NFTs. Transitioned from proof-of-work to proof-of-stake in 2022.", 3800.0, 4891.0, "Nov 2021", 456.0, "Ethereum", "Layer 1", 120.4),
    "SOL" to CoinMeta("Solana", "A high-performance Layer 1 blockchain designed for speed and low cost. Processes up to 65,000 TPS with sub-second finality. Ecosystem includes DeFi, NFTs, and memecoins.", 187.0, 295.0, "Jan 2025", 91.0, "Solana", "Layer 1", 487.0),
    "XRP" to CoinMeta("XRP", "Digital payment protocol designed for fast, low-cost international transfers. Created by Ripple Labs, XRP settles transactions in 3-5 seconds across borders.", 2.34, 3.84, "Jan 2018", 134.0, "XRPL", "Layer 1", 57300.0),
    "DOGE" to CoinMeta("Dogecoin", "Originally created as a joke in 2013, Dogecoin became a cultural phenomenon backed by a strong community. Uses Scrypt proof-of-work with fast block times.", 0.42, 0.7376, "May 2021", 60.0, "Dogecoin", "Meme", 147000.0),
    "ADA" to CoinMeta("Cardano", "A research-driven blockchain platform built on peer-reviewed academic papers. Employs Ouroboros proof-of-stake consensus for energy-efficient validation.", 1.05, 3.10, "Sep 2021", 37.0, "Cardano", "Layer 1", 35900.0),
    "AVAX" to CoinMeta("Avalanche", "A platform of platforms enabling custom blockchain networks (subnets). Offers sub-second finality through its novel consensus protocol.", 41.0, 146.22, "Nov 2021", 16.8, "Avalanche", "Layer 1", 409.0),
    "LINK" to CoinMeta("Chainlink", "The dominant decentralised oracle network, connecting smart contracts to real-world data. Critical infrastructure for DeFi price feeds, VRF, and cross-chain messaging.", 24.0, 52.88, "May 2021", 15.3, "Ethereum", "Infrastructure", 638.0),
    "DOT" to CoinMeta("Polkadot", "A heterogeneous multi-chain protocol enabling cross-blockchain transfers. Designed by Ethereum co-founder Gavin Wood for Web3 interoperability.", 8.5, 55.00, "Nov 2021", 12.4, "Polkadot", "Layer 1", 1460.0),
    "MATIC" to CoinMeta("Polygon", "Ethereum scaling solution providing faster and cheaper transactions. Supports multiple scaling approaches including PoS sidechains and zkEVM rollups.", 0.55, 2.92, "Dec 2021", 5.5, "Polygon", "Layer 2", 10000.0),
    "UNI" to CoinMeta("Uniswap", "The largest decentralised exchange by volume. Pioneered the automated market maker model that replaced traditional order books for on-chain trading.", 14.5, 44.97, "May 2021", 10.8, "Ethereum", "DeFi", 753.0),
    "AAVE" to CoinMeta("Aave", "Leading decentralised lending and borrowing protocol. Enables users to earn interest on deposits and take collateralised loans across multiple blockchains.", 320.0, 666.86, "Oct 2021", 4.8, "Ethereum", "DeFi", 15.0),
    "ARB" to CoinMeta("Arbitrum", "The largest Ethereum Layer 2 by TVL. An optimistic rollup that inherits Ethereum security while offering dramatically lower fees and faster confirmation.", 1.20, 2.39, "Jan 2024", 4.8, "Arbitrum", "Layer 2", 4000.0),
    "OP" to CoinMeta("Optimism", "An Ethereum Layer 2 optimistic rollup focused on scaling the Ethereum ecosystem. Powers the OP Stack used by Base, Worldcoin, and others.", 2.10, 4.85, "Mar 2024", 3.2, "Optimism", "Layer 2", 1500.0),
    "FET" to CoinMeta("Artificial Superintelligence Alliance", "AI-focused crypto project combining Fetch.ai, SingularityNET, and Ocean Protocol. Building autonomous economic agents powered by machine learning.", 2.30, 3.48, "Mar 2024", 5.8, "Ethereum", "AI/ML", 2520.0),
    "PEPE" to CoinMeta("Pepe", "A memecoin inspired by the Pepe the Frog internet meme. Launched in 2023 and rapidly gained a large community following on Ethereum.", 0.000024, 0.000028, "Dec 2024", 10.1, "Ethereum", "Meme", 420690000.0),
    "SHIB" to CoinMeta("Shiba Inu", "A decentralised meme token launched as an 'experiment in community building.' Ecosystem includes ShibaSwap DEX and Shibarium Layer 2.", 0.000029, 0.000088, "Oct 2021", 17.0, "Ethereum", "Meme", 589000000.0)
)

// ============================================================================
// VIEWMODEL
// ============================================================================

@HiltViewModel
class CoinDetailViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CoinDetailUiState())
    val uiState: StateFlow<CoinDetailUiState> = _uiState.asStateFlow()

    private var baseSymbol: String = ""
    private var holdingAmount: Double = 0.0
    private var holdingPnlPercent: Double = 0.0

    fun loadCoin(symbol: String, amount: Double, pnlPercent: Double) {
        baseSymbol = symbol.split("/").first().uppercase()
        holdingAmount = amount
        holdingPnlPercent = pnlPercent

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val meta = COIN_DATABASE[baseSymbol] ?: generateFallbackMeta(baseSymbol)
            val seed = baseSymbol.hashCode().toLong()
            val rng = Random(seed + System.currentTimeMillis() / 60000) // varies per minute

            // Current price with small random variation
            val priceJitter = 1.0 + (rng.nextDouble() - 0.5) * 0.02 // ±1%
            val currentPrice = meta.basePrice * priceJitter

            // 24h change
            val change24hPct = (rng.nextDouble() - 0.45) * 8.0 // slight upward bias, ±4%
            val change24h = currentPrice * change24hPct / 100.0

            // Ranges
            val daySpread = currentPrice * (0.02 + rng.nextDouble() * 0.03) // 2-5% spread
            val dayLow = currentPrice - daySpread * 0.6
            val dayHigh = currentPrice + daySpread * 0.4

            val yearLow = currentPrice * (0.35 + rng.nextDouble() * 0.25) // 35-60% of current
            val yearHigh = currentPrice * (1.1 + rng.nextDouble() * 0.4)  // 110-150% of current

            // Cost basis — derived from pnlPercent
            val costBasis = if (holdingPnlPercent != 0.0) {
                currentPrice / (1.0 + holdingPnlPercent / 100.0)
            } else {
                currentPrice * 0.95
            }

            val holdingValue = holdingAmount * currentPrice
            val costTotal = holdingAmount * costBasis
            val unrealisedPnL = holdingValue - costTotal
            val unrealisedPnLPercent = if (costTotal > 0) (unrealisedPnL / costTotal) * 100.0 else 0.0

            // Volume and market cap display
            val vol24h = meta.marketCapBillions * (0.03 + rng.nextDouble() * 0.07) // 3-10% of mcap
            val mcapStr = formatLargeNumber(meta.marketCapBillions * 1_000_000_000)
            val volStr = formatLargeNumber(vol24h * 1_000_000_000)
            val supplyStr = formatLargeNumber(meta.supplyMillions * 1_000_000)

            // Chart data for default timeframe
            val chartData = generateChartData(currentPrice, ChartTimeframe.D1, seed)

            _uiState.update {
                CoinDetailUiState(
                    isLoading = false,
                    symbol = baseSymbol,
                    name = meta.name,
                    currentPrice = currentPrice,
                    priceChange24h = change24h,
                    priceChange24hPercent = change24hPct,
                    selectedTimeframe = ChartTimeframe.D1,
                    chartData = chartData,
                    dayLow = dayLow,
                    dayHigh = dayHigh,
                    yearLow = yearLow,
                    yearHigh = yearHigh,
                    allTimeHigh = meta.athPrice,
                    allTimeHighDate = meta.athDate,
                    holdingAmount = holdingAmount,
                    holdingValue = holdingValue,
                    costBasis = costBasis,
                    unrealisedPnL = unrealisedPnL,
                    unrealisedPnLPercent = unrealisedPnLPercent,
                    description = meta.description,
                    marketCap = mcapStr,
                    volume24h = volStr,
                    circulatingSupply = supplyStr,
                    category = meta.category,
                    primaryChain = meta.chain
                )
            }

            // Start live price ticker
            startPriceTicker(meta.basePrice, seed)
        }
    }

    fun selectTimeframe(tf: ChartTimeframe) {
        val seed = baseSymbol.hashCode().toLong()
        val price = _uiState.value.currentPrice
        val chartData = generateChartData(price, tf, seed)
        _uiState.update {
            it.copy(selectedTimeframe = tf, chartData = chartData)
        }
    }

    // Simulate live price updates every 5 seconds
    private fun startPriceTicker(basePrice: Double, seed: Long) {
        viewModelScope.launch {
            while (isActive) {
                delay(5000)
                val rng = Random(seed + System.currentTimeMillis() / 5000)
                val jitter = 1.0 + (rng.nextDouble() - 0.5) * 0.004 // ±0.2%
                val newPrice = _uiState.value.currentPrice * jitter
                val holdingValue = holdingAmount * newPrice
                val costTotal = holdingAmount * _uiState.value.costBasis
                val pnl = holdingValue - costTotal
                val pnlPct = if (costTotal > 0) (pnl / costTotal) * 100.0 else 0.0

                _uiState.update {
                    it.copy(
                        currentPrice = newPrice,
                        holdingValue = holdingValue,
                        unrealisedPnL = pnl,
                        unrealisedPnLPercent = pnlPct
                    )
                }
            }
        }
    }

    // ========================================================================
    // CHART DATA GENERATION — Geometric Brownian Motion
    // ========================================================================

    private fun generateChartData(
        endPrice: Double,
        tf: ChartTimeframe,
        seed: Long
    ): List<PricePoint> {
        val n = tf.dataPoints
        val rng = Random(seed + tf.ordinal * 1000L)
        val now = System.currentTimeMillis()

        // Volatility scales with timeframe
        val vol = when (tf) {
            ChartTimeframe.M30, ChartTimeframe.H1 -> 0.002
            ChartTimeframe.H2, ChartTimeframe.H4 -> 0.005
            ChartTimeframe.H8 -> 0.008
            ChartTimeframe.D1 -> 0.012
            ChartTimeframe.M1 -> 0.025
            ChartTimeframe.M3 -> 0.04
            ChartTimeframe.Y1 -> 0.06
        }

        // Time interval per data point in ms
        val intervalMs = when (tf) {
            ChartTimeframe.M30 -> 60_000L        // 1 min
            ChartTimeframe.H1 -> 60_000L          // 1 min
            ChartTimeframe.H2 -> 120_000L         // 2 min
            ChartTimeframe.H4 -> 240_000L         // 4 min
            ChartTimeframe.H8 -> 480_000L         // 8 min
            ChartTimeframe.D1 -> 1_800_000L       // 30 min
            ChartTimeframe.M1 -> 86_400_000L      // 1 day
            ChartTimeframe.M3 -> 86_400_000L      // 1 day
            ChartTimeframe.Y1 -> 604_800_000L     // 1 week
        }

        // Generate path backwards from endPrice
        val prices = mutableListOf(endPrice)
        for (i in 1 until n) {
            val drift = (rng.nextDouble() - 0.48) * vol // slight upward bias
            val prev = prices.last()
            val next = prev / (1.0 + drift) // reverse walk
            prices.add(next.coerceAtLeast(endPrice * 0.3))
        }
        prices.reverse()

        return prices.mapIndexed { i, price ->
            PricePoint(
                timestamp = now - (n - 1 - i) * intervalMs,
                price = price
            )
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private fun formatLargeNumber(value: Double): String {
        return when {
            value >= 1_000_000_000_000 -> String.format("AU$%.2fT", value / 1_000_000_000_000)
            value >= 1_000_000_000 -> String.format("AU$%.2fB", value / 1_000_000_000)
            value >= 1_000_000 -> String.format("AU$%.2fM", value / 1_000_000)
            value >= 1_000 -> String.format("AU$%.2fK", value / 1_000)
            else -> String.format("AU$%.2f", value)
        }
    }

    private fun generateFallbackMeta(symbol: String): CoinMeta {
        val hash = symbol.hashCode().toLong().absoluteValue
        val rng = Random(hash)
        val price = 0.01 + rng.nextDouble() * 500
        return CoinMeta(
            name = symbol,
            description = "$symbol is a cryptocurrency traded across major exchanges. Check your exchange for the latest information and market data.",
            basePrice = price,
            athPrice = price * (1.5 + rng.nextDouble()),
            athDate = "2024",
            marketCapBillions = 0.1 + rng.nextDouble() * 10,
            chain = "Unknown",
            category = "Other",
            supplyMillions = 10.0 + rng.nextDouble() * 10000
        )
    }
}
