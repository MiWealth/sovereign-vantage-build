/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * COIN DETAIL SCREEN — Individual asset view with interactive chart
 *
 * Shows current price, selectable timeframe chart (30m → 1Y),
 * day/year ranges, ATH, cost basis, holdings, and coin description.
 *
 * © 2025-2026 MiWealth Pty Ltd
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

package com.miwealth.sovereignvantage.ui.portfolio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.miwealth.sovereignvantage.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinDetailScreen(
    symbol: String,
    amount: Double,
    pnlPercent: Double,
    onNavigateBack: () -> Unit,
    viewModel: CoinDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(symbol) {
        viewModel.loadCoin(symbol, amount, pnlPercent)
    }

    // BUILD #157: Removed nested Scaffold to fix nav bar obscuring
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VintageColors.EmeraldDeep)
    ) {
        TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Symbol badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = VintageColors.Gold.copy(alpha = 0.15f)
                        ) {
                            Text(
                                uiState.symbol.take(4),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = VintageColors.Gold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                uiState.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                uiState.category + " • " + uiState.primaryChain,
                                style = MaterialTheme.typography.bodySmall,
                                color = VintageColors.TextTertiary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VintageColors.EmeraldDeep)
            )
        
        // BUILD #157: Content area (no longer wrapped in Scaffold lambda)
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = VintageColors.Gold)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VintageColors.EmeraldDeep)
                    .verticalScroll(rememberScrollState())
            ) {
                // ═══════════════════════════════════════════════════
                // CURRENT PRICE HEADER
                // ═══════════════════════════════════════════════════
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        formatPrice(uiState.currentPrice),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isPositive = uiState.priceChange24hPercent >= 0
                        Icon(
                            if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (isPositive) VintageColors.ProfitGreen else VintageColors.LossRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "${if (isPositive) "+" else ""}${formatPrice(uiState.priceChange24h)} " +
                                    "(${if (isPositive) "+" else ""}${String.format("%.2f", uiState.priceChange24hPercent)}%)",
                            color = if (isPositive) VintageColors.ProfitGreen else VintageColors.LossRed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            "  24h",
                            color = VintageColors.TextPrimary.copy(alpha = 0.4f),
                            fontSize = 12.sp
                        )
                    }
                }

                // ═══════════════════════════════════════════════════
                // PRICE CHART
                // ═══════════════════════════════════════════════════
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        // Chart canvas
                        if (uiState.chartData.isNotEmpty()) {
                            PriceChart(
                                data = uiState.chartData,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(16.dp)
                            )
                        }

                        // Timeframe selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ChartTimeframe.entries.forEach { tf ->
                                val isSelected = tf == uiState.selectedTimeframe
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) VintageColors.Gold.copy(alpha = 0.2f) else Color.Transparent,
                                    modifier = Modifier.clickable { viewModel.selectTimeframe(tf) }
                                ) {
                                    Text(
                                        tf.label,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        color = if (isSelected) VintageColors.Gold else VintageColors.TextPrimary.copy(alpha = 0.4f),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ═══════════════════════════════════════════════════
                // YOUR HOLDINGS
                // ═══════════════════════════════════════════════════
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Your Position",
                            color = VintageColors.Gold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        StatsRow("Holdings", "${String.format("%,.6f", uiState.holdingAmount)} ${uiState.symbol}")
                        StatsRow("Market Value", formatAud(uiState.holdingValue))
                        StatsRow("Cost Basis", formatAud(uiState.costBasis) + " / unit")

                        HorizontalDivider(color = VintageColors.EmeraldMedium, modifier = Modifier.padding(vertical = 8.dp))

                        val isPnlPositive = uiState.unrealisedPnL >= 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Unrealised P&L", color = VintageColors.TextSecondary, fontSize = 14.sp)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${if (isPnlPositive) "+" else ""}${formatAud(uiState.unrealisedPnL)}",
                                    color = if (isPnlPositive) VintageColors.ProfitGreen else VintageColors.LossRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "${if (isPnlPositive) "+" else ""}${String.format("%.2f", uiState.unrealisedPnLPercent)}%",
                                    color = if (isPnlPositive) VintageColors.ProfitGreen else VintageColors.LossRed,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ═══════════════════════════════════════════════════
                // PRICE RANGES
                // ═══════════════════════════════════════════════════
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Price Ranges",
                            color = VintageColors.Gold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        RangeBar(
                            label = "Day Range",
                            low = uiState.dayLow,
                            high = uiState.dayHigh,
                            current = uiState.currentPrice
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        RangeBar(
                            label = "52-Week Range",
                            low = uiState.yearLow,
                            high = uiState.yearHigh,
                            current = uiState.currentPrice
                        )

                        HorizontalDivider(color = VintageColors.EmeraldMedium, modifier = Modifier.padding(vertical = 12.dp))

                        StatsRow("All-Time High", formatPrice(uiState.allTimeHigh))
                        StatsRow("ATH Date", uiState.allTimeHighDate)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ═══════════════════════════════════════════════════
                // MARKET STATISTICS
                // ═══════════════════════════════════════════════════
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Market Statistics",
                            color = VintageColors.Gold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        StatsRow("Market Cap", uiState.marketCap)
                        StatsRow("24h Volume", uiState.volume24h)
                        StatsRow("Circulating Supply", uiState.circulatingSupply)
                        StatsRow("Category", uiState.category)
                        StatsRow("Network", uiState.primaryChain)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ═══════════════════════════════════════════════════
                // ABOUT
                // ═══════════════════════════════════════════════════
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "About ${uiState.name}",
                            color = VintageColors.Gold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            uiState.description,
                            color = VintageColors.TextPrimary.copy(alpha = 0.75f),
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

// ============================================================================
// PRICE CHART — Canvas with gradient fill
// ============================================================================

@Composable
fun PriceChart(
    data: List<PricePoint>,
    modifier: Modifier = Modifier
) {
    if (data.size < 2) return

    val isPositive = data.last().price >= data.first().price
    val lineColor = if (isPositive) VintageColors.ProfitGreen else VintageColors.LossRed
    val fillColor = lineColor.copy(alpha = 0.15f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val prices = data.map { it.price }
        val minP = prices.min()
        val maxP = prices.max()
        val range = (maxP - minP).coerceAtLeast(minP * 0.001)

        val points = data.mapIndexed { i, point ->
            val x = (i.toFloat() / (data.size - 1)) * width
            val y = height - ((point.price - minP) / range).toFloat() * height * 0.9f - height * 0.05f
            Offset(x, y)
        }

        // Fill path
        val fillPath = Path().apply {
            moveTo(points.first().x, height)
            lineTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
            lineTo(points.last().x, height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(fillColor, Color.Transparent),
                startY = points.minOf { it.y },
                endY = height
            )
        )

        // Line path
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }

        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
        )

        // Current price dot
        val lastPoint = points.last()
        drawCircle(
            color = lineColor,
            radius = 5f,
            center = lastPoint
        )
        drawCircle(
            color = lineColor.copy(alpha = 0.3f),
            radius = 10f,
            center = lastPoint
        )
    }
}

// ============================================================================
// RANGE BAR — Visual low/current/high indicator
// ============================================================================

@Composable
fun RangeBar(
    label: String,
    low: Double,
    high: Double,
    current: Double
) {
    val range = (high - low).coerceAtLeast(0.001)
    val position = ((current - low) / range).toFloat().coerceIn(0f, 1f)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = VintageColors.TextSecondary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        ) {
            // Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(VintageColors.EmeraldMedium, RoundedCornerShape(3.dp))
            )
            // Filled portion
            Box(
                modifier = Modifier
                    .fillMaxWidth(position)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(VintageColors.LossRed, VintageColors.Gold, VintageColors.ProfitGreen)),
                        RoundedCornerShape(3.dp)
                    )
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatPrice(low), color = VintageColors.LossRed.copy(alpha = 0.7f), fontSize = 11.sp)
            Text(formatPrice(high), color = VintageColors.ProfitGreen.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}

// ============================================================================
// HELPER COMPOSABLES
// ============================================================================

@Composable
fun StatsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = VintageColors.TextSecondary, fontSize = 14.sp)
        Text(value, color = VintageColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

// ============================================================================
// FORMATTING HELPERS
// ============================================================================

private fun formatPrice(price: Double): String {
    return when {
        price >= 1000 -> "AU$${String.format("%,.2f", price)}"
        price >= 1 -> "AU$${String.format("%.4f", price)}"
        price >= 0.01 -> "AU$${String.format("%.6f", price)}"
        else -> "AU$${String.format("%.8f", price)}"
    }
}

private fun formatAud(value: Double): String {
    return when {
        value >= 1_000_000 -> "AU$${String.format("%,.0f", value)}"
        value >= 1000 -> "AU$${String.format("%,.2f", value)}"
        value >= 1 -> "AU$${String.format("%.4f", value)}"
        else -> "AU$${String.format("%.6f", value)}"
    }
}
