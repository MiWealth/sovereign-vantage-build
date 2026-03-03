/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * COIN DETAIL SCREEN — Deep-dive into individual asset
 *
 * Shows: current price, interactive chart with selectable timeframes,
 * day/year ranges, ATH, cost basis, description, and key statistics.
 * All data generated on-the-fly until live exchange connections are wired.
 *
 * © 2025-2026 MiWealth Pty Ltd
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

package com.miwealth.sovereignvantage.ui.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miwealth.sovereignvantage.ui.theme.*
import kotlin.math.*
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════
// DATA: Coin metadata generated on the fly
// ═══════════════════════════════════════════════════════════════════

data class CoinInfo(
    val symbol: String,
    val name: String,
    val description: String,
    val currentPrice: Double,
    val priceChange24h: Double,
    val priceChange7d: Double,
    val dayLow: Double,
    val dayHigh: Double,
    val yearLow: Double,
    val yearHigh: Double,
    val allTimeHigh: Double,
    val allTimeHighDate: String,
    val marketCap: Double,
    val volume24h: Double,
    val circulatingSupply: Double,
    val maxSupply: Double?,
    val dominance: Double?,
    val costBasis: Double,
    val holdingAmount: Double
)

/** Generate realistic coin metadata on the fly. */
fun generateCoinInfo(symbol: String): CoinInfo {
    return when (symbol.uppercase()) {
        "BTC" -> CoinInfo(
            symbol = "BTC", name = "Bitcoin",
            description = "Bitcoin is the first decentralised cryptocurrency, created in 2009 by Satoshi Nakamoto. It operates on a proof-of-work blockchain and has a hard cap of 21 million coins. Bitcoin serves as both a store of value and a medium of exchange, often termed 'digital gold' by institutional investors.",
            currentPrice = 97_842.50, priceChange24h = 2.34, priceChange7d = -1.12,
            dayLow = 96_100.00, dayHigh = 98_450.00,
            yearLow = 38_500.00, yearHigh = 109_114.00,
            allTimeHigh = 109_114.88, allTimeHighDate = "20 Jan 2025",
            marketCap = 1_935_000_000_000.0, volume24h = 42_500_000_000.0,
            circulatingSupply = 19_800_000.0, maxSupply = 21_000_000.0,
            dominance = 58.2, costBasis = 64_200.00, holdingAmount = 1.245
        )
        "ETH" -> CoinInfo(
            symbol = "ETH", name = "Ethereum",
            description = "Ethereum is a decentralised platform for smart contracts and dApps, created by Vitalik Buterin in 2015. After transitioning to proof-of-stake in 2022 ('The Merge'), it processes transactions more efficiently. Ethereum's EVM is the backbone of DeFi, NFTs, and Layer 2 scaling solutions.",
            currentPrice = 3_847.30, priceChange24h = 1.87, priceChange7d = 3.45,
            dayLow = 3_780.00, dayHigh = 3_890.00,
            yearLow = 1_520.00, yearHigh = 4_106.96,
            allTimeHigh = 4_891.70, allTimeHighDate = "16 Nov 2021",
            marketCap = 463_000_000_000.0, volume24h = 18_200_000_000.0,
            circulatingSupply = 120_400_000.0, maxSupply = null,
            dominance = 13.9, costBasis = 2_180.00, holdingAmount = 8.5
        )
        "SOL" -> CoinInfo(
            symbol = "SOL", name = "Solana",
            description = "Solana is a high-performance Layer 1 blockchain using proof-of-history consensus, capable of processing 65,000+ transactions per second at sub-cent fees. Founded by Anatoly Yakovenko in 2020, it has become the leading platform for retail DeFi, memecoins, and high-frequency on-chain trading.",
            currentPrice = 187.45, priceChange24h = -0.82, priceChange7d = 5.67,
            dayLow = 184.20, dayHigh = 191.30,
            yearLow = 18.50, yearHigh = 295.83,
            allTimeHigh = 295.83, allTimeHighDate = "24 Jan 2025",
            marketCap = 89_400_000_000.0, volume24h = 4_800_000_000.0,
            circulatingSupply = 476_900_000.0, maxSupply = null,
            dominance = 2.7, costBasis = 42.80, holdingAmount = 125.0
        )
        "XRP" -> CoinInfo(
            symbol = "XRP", name = "XRP",
            description = "XRP is the native token of the XRP Ledger, designed for fast, low-cost cross-border payments. Developed by Ripple Labs, it settles transactions in 3-5 seconds. Following a landmark SEC court victory in 2023, XRP's regulatory clarity has attracted institutional interest.",
            currentPrice = 2.48, priceChange24h = 3.12, priceChange7d = 8.90,
            dayLow = 2.38, dayHigh = 2.55,
            yearLow = 0.42, yearHigh = 3.40,
            allTimeHigh = 3.84, allTimeHighDate = "7 Jan 2018",
            marketCap = 142_000_000_000.0, volume24h = 8_900_000_000.0,
            circulatingSupply = 57_200_000_000.0, maxSupply = 100_000_000_000.0,
            dominance = 4.3, costBasis = 0.65, holdingAmount = 15_000.0
        )
        "ADA" -> CoinInfo(
            symbol = "ADA", name = "Cardano",
            description = "Cardano is a research-driven Layer 1 blockchain using proof-of-stake, founded by Charles Hoskinson (Ethereum co-founder) in 2017. Known for its academic rigour and peer-reviewed development, Cardano focuses on scalability, interoperability, and sustainability.",
            currentPrice = 0.78, priceChange24h = -1.45, priceChange7d = 2.30,
            dayLow = 0.76, dayHigh = 0.81,
            yearLow = 0.24, yearHigh = 1.32,
            allTimeHigh = 3.10, allTimeHighDate = "2 Sep 2021",
            marketCap = 27_800_000_000.0, volume24h = 1_200_000_000.0,
            circulatingSupply = 35_600_000_000.0, maxSupply = 45_000_000_000.0,
            dominance = 0.84, costBasis = 0.35, holdingAmount = 50_000.0
        )
        else -> CoinInfo(
            symbol = symbol.uppercase(), name = symbol.uppercase(),
            description = "A digital asset traded on decentralised and centralised exchanges worldwide. Connect your exchange API keys in Settings to view live data and enable trading for this asset.",
            currentPrice = 1.50 + Random.nextDouble() * 100,
            priceChange24h = Random.nextDouble() * 10 - 5,
            priceChange7d = Random.nextDouble() * 20 - 10,
            dayLow = 1.20, dayHigh = 2.80,
            yearLow = 0.50, yearHigh = 5.00,
            allTimeHigh = 8.50, allTimeHighDate = "Unknown",
            marketCap = 500_000_000.0, volume24h = 50_000_000.0,
            circulatingSupply = 1_000_000_000.0, maxSupply = null,
            dominance = null, costBasis = 1.00, holdingAmount = 1000.0
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// CHART DATA: Synthetic price history per timeframe
// ═══════════════════════════════════════════════════════════════════

enum class ChartPeriod(val label: String, val points: Int) {
    M30("30m", 30),
    H1("1H", 60),
    H2("2H", 60),
    H4("4H", 60),
    H8("8H", 60),
    D1("1D", 96),
    M1("1M", 30),
    M3("3M", 90),
    Y1("1Y", 365)
}

/** Generate synthetic price series seeded by symbol+period for consistency. */
fun generatePriceSeries(coin: CoinInfo, period: ChartPeriod): List<Double> {
    val seed = coin.symbol.hashCode().toLong() + period.ordinal * 1000L
    val rng = Random(seed)
    val n = period.points
    val prices = mutableListOf<Double>()

    // Walk backward from currentPrice with realistic volatility
    val dailyVol = when (period) {
        ChartPeriod.M30, ChartPeriod.H1, ChartPeriod.H2 -> 0.002
        ChartPeriod.H4, ChartPeriod.H8 -> 0.004
        ChartPeriod.D1 -> 0.008
        ChartPeriod.M1 -> 0.025
        ChartPeriod.M3 -> 0.035
        ChartPeriod.Y1 -> 0.045
    }

    var price = coin.currentPrice
    prices.add(price)
    for (i in 1 until n) {
        val drift = (rng.nextDouble() - 0.48) * dailyVol * price  // slight upward bias
        price = max(price * 0.7, price + drift)
        prices.add(price)
    }
    // Reverse so newest is last
    prices.reverse()
    // Ensure last point matches current price
    val scale = coin.currentPrice / prices.last()
    return prices.map { it * scale }
}

// ═══════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinDetailScreen(
    symbol: String,
    onNavigateBack: () -> Unit
) {
    val coin = remember(symbol) { generateCoinInfo(symbol) }
    var selectedPeriod by remember { mutableStateOf(ChartPeriod.D1) }
    val priceData = remember(symbol, selectedPeriod) { generatePriceSeries(coin, selectedPeriod) }

    val scrollState = rememberScrollState()
    val isPositive = coin.priceChange24h >= 0
    val changeColor = if (isPositive) VintageColors.ProfitGreen else VintageColors.LossRed

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(VintageColors.Gold.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                coin.symbol.take(2),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = VintageColors.Gold
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(coin.name, fontWeight = FontWeight.Bold, color = VintageColors.TextPrimary)
                            Text(coin.symbol, style = MaterialTheme.typography.bodySmall, color = VintageColors.TextTertiary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VintageColors.EmeraldDeep)
            )
        },
        containerColor = VintageColors.EmeraldDeep
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // ── PRICE HEADER ──
            Text(
                "A$${String.format("%,.2f", coin.currentPrice)}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = White
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    null, tint = changeColor, modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "${if (isPositive) "+" else ""}${String.format("%.2f", coin.priceChange24h)}% today",
                    style = MaterialTheme.typography.bodyLarge,
                    color = changeColor
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "${if (coin.priceChange7d >= 0) "+" else ""}${String.format("%.2f", coin.priceChange7d)}% 7d",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (coin.priceChange7d >= 0) VintageColors.ProfitGreen.copy(alpha = 0.7f) else VintageColors.LossRed.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── PRICE CHART ──
            PriceChart(
                data = priceData,
                isPositive = priceData.last() >= priceData.first(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── TIMEFRAME SELECTOR ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ChartPeriod.values().forEach { period ->
                    val isSelected = period == selectedPeriod
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) VintageColors.Gold.copy(alpha = 0.2f) else Color.Transparent,
                        modifier = Modifier.clickable { selectedPeriod = period }
                    ) {
                        Text(
                            period.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) VintageColors.Gold else VintageColors.TextPrimary.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── RANGE BARS ──
            RangeBar("Day Range", coin.dayLow, coin.dayHigh, coin.currentPrice)
            Spacer(modifier = Modifier.height(12.dp))
            RangeBar("52-Week Range", coin.yearLow, coin.yearHigh, coin.currentPrice)

            Spacer(modifier = Modifier.height(24.dp))

            // ── KEY STATISTICS ──
            Text(
                "Key Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VintageColors.Gold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StatRow("Year High", "A$${String.format("%,.2f", coin.yearHigh)}")
                    StatRow("All-Time High", "A$${String.format("%,.2f", coin.allTimeHigh)}")
                    StatRow("ATH Date", coin.allTimeHighDate)
                    StatRow("Market Cap", formatLargeNumber(coin.marketCap))
                    StatRow("24h Volume", formatLargeNumber(coin.volume24h))
                    StatRow("Circulating Supply", formatLargeNumber(coin.circulatingSupply) + " ${coin.symbol}")
                    if (coin.maxSupply != null) {
                        StatRow("Max Supply", formatLargeNumber(coin.maxSupply) + " ${coin.symbol}")
                    }
                    if (coin.dominance != null) {
                        StatRow("Dominance", "${String.format("%.1f", coin.dominance)}%")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── YOUR POSITION ──
            Text(
                "Your Position",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VintageColors.Gold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val holdingValue = coin.holdingAmount * coin.currentPrice
                    val costValue = coin.holdingAmount * coin.costBasis
                    val pnl = holdingValue - costValue
                    val pnlPercent = if (costValue > 0) (pnl / costValue) * 100 else 0.0

                    StatRow("Holdings", "${String.format("%,.6f", coin.holdingAmount)} ${coin.symbol}")
                    StatRow("Current Value", "A$${String.format("%,.2f", holdingValue)}")
                    StatRow("Cost Basis", "A$${String.format("%,.2f", coin.costBasis)}")
                    StatRow("Avg Cost Total", "A$${String.format("%,.2f", costValue)}")

                    HorizontalDivider(color = VintageColors.EmeraldDeep, modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Unrealised P&L", color = VintageColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${if (pnl >= 0) "+" else ""}$${String.format("%,.2f", pnl)}",
                                fontWeight = FontWeight.Bold,
                                color = if (pnl >= 0) VintageColors.ProfitGreen else VintageColors.LossRed
                            )
                            Text(
                                "${if (pnlPercent >= 0) "+" else ""}${String.format("%.2f", pnlPercent)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (pnlPercent >= 0) VintageColors.ProfitGreen else VintageColors.LossRed
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── ABOUT ──
            Text(
                "About ${coin.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = VintageColors.Gold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                coin.description,
                style = MaterialTheme.typography.bodyMedium,
                color = VintageColors.TextPrimary.copy(alpha = 0.8f),
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// PRICE CHART — Smooth line with gradient fill
// ═══════════════════════════════════════════════════════════════════

@Composable
fun PriceChart(
    data: List<Double>,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = if (isPositive) VintageColors.ProfitGreen else VintageColors.LossRed
    val fillColor = lineColor.copy(alpha = 0.15f)

    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas

        val minVal = data.min()
        val maxVal = data.max()
        val range = if (maxVal - minVal < 0.01) 1.0 else maxVal - minVal

        val stepX = size.width / (data.size - 1).toFloat()
        val padding = 4f

        fun yOf(value: Double): Float {
            val norm = ((value - minVal) / range).toFloat()
            return size.height - padding - norm * (size.height - 2 * padding)
        }

        // Build path
        val linePath = Path()
        val fillPath = Path()

        linePath.moveTo(0f, yOf(data[0]))
        fillPath.moveTo(0f, size.height)
        fillPath.lineTo(0f, yOf(data[0]))

        for (i in 1 until data.size) {
            val x = i * stepX
            val y = yOf(data[i])
            linePath.lineTo(x, y)
            fillPath.lineTo(x, y)
        }

        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        // Draw gradient fill
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillColor, Color.Transparent),
                startY = 0f,
                endY = size.height
            )
        )

        // Draw line
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
        )

        // Current price dot
        val lastX = (data.size - 1) * stepX
        val lastY = yOf(data.last())
        drawCircle(lineColor, radius = 5f, center = Offset(lastX, lastY))
        drawCircle(Color.White, radius = 2.5f, center = Offset(lastX, lastY))
    }
}

// ═══════════════════════════════════════════════════════════════════
// RANGE BAR — Visual position within low/high range
// ═══════════════════════════════════════════════════════════════════

@Composable
fun RangeBar(
    label: String,
    low: Double,
    high: Double,
    current: Double
) {
    val range = high - low
    val position = if (range > 0) ((current - low) / range).toFloat().coerceIn(0f, 1f) else 0.5f

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = VintageColors.TextSecondary)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("A$${String.format("%,.2f", low)}", style = MaterialTheme.typography.labelSmall, color = VintageColors.LossRed.copy(alpha = 0.8f))
            Text("A$${String.format("%,.2f", high)}", style = MaterialTheme.typography.labelSmall, color = VintageColors.ProfitGreen.copy(alpha = 0.8f))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(VintageColors.EmeraldMedium)
        ) {
            // Fill up to position
            Box(
                modifier = Modifier
                    .fillMaxWidth(position)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(VintageColors.LossRed.copy(alpha = 0.6f), VintageColors.Gold, VintageColors.ProfitGreen.copy(alpha = 0.6f))
                        )
                    )
            )
            // Position marker
            Box(
                modifier = Modifier
                    .fillMaxWidth(position)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(12.dp)
                        .offset(x = 6.dp)
                        .clip(CircleShape)
                        .background(VintageColors.Gold)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// HELPER COMPOSABLES
// ═══════════════════════════════════════════════════════════════════

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = VintageColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = VintageColors.TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

fun formatLargeNumber(value: Double): String {
    return when {
        value >= 1_000_000_000_000 -> "A$${String.format("%.2f", value / 1_000_000_000_000)}T"
        value >= 1_000_000_000 -> "A$${String.format("%.2f", value / 1_000_000_000)}B"
        value >= 1_000_000 -> "A$${String.format("%.2f", value / 1_000_000)}M"
        value >= 1_000 -> "A$${String.format("%.1f", value / 1_000)}K"
        else -> String.format("%,.0f", value)
    }
}
