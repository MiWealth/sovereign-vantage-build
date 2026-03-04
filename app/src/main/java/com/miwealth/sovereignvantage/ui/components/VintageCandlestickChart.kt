package com.miwealth.sovereignvantage.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miwealth.sovereignvantage.ui.theme.*
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * SOVEREIGN VANTAGE - VINTAGE CANDLESTICK CHART
 * 
 * Professional trading chart with:
 * - Real-time candlestick updates via Flow
 * - Volume bars
 * - Grid lines
 * - Price axis with gold typography
 * - Time axis
 * - Crosshair on touch
 * - Multiple timeframes
 * - SMA/EMA overlays (optional)
 * 
 * Design: Matches mockup with emerald background, gold accents
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

// =============================================================================
// DATA CLASSES
// =============================================================================

data class CandleData(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

enum class ChartTimeframe(val label: String, val minutes: Int) {
    M1("1m", 1),
    M5("5m", 5),
    M15("15m", 15),
    H1("1H", 60),
    H4("4H", 240),
    D1("1D", 1440)
}

data class ChartOverlay(
    val name: String,
    val color: Color,
    val values: List<Double>
)

// =============================================================================
// MAIN CHART COMPOSABLE
// =============================================================================

@Composable
fun VintageCandlestickChart(
    symbol: String,
    candles: List<CandleData>,
    currentPrice: Double,
    priceChange24h: Double,
    selectedTimeframe: ChartTimeframe = ChartTimeframe.H4,
    onTimeframeChange: (ChartTimeframe) -> Unit = {},
    overlays: List<ChartOverlay> = emptyList(),
    showVolume: Boolean = true,
    showGrid: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = VintageTheme.colors
    val isVintage = VintageTheme.isVintageMode
    val isPositive = priceChange24h >= 0
    
    // Touch state for crosshair
    var touchPosition by remember { mutableStateOf<Offset?>(null) }
    var selectedCandle by remember { mutableStateOf<CandleData?>(null) }
    
    VintageCard(
        modifier = modifier.fillMaxWidth(),
        showGoldFrame = isVintage
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            ChartHeader(
                symbol = symbol,
                currentPrice = currentPrice,
                priceChange24h = priceChange24h,
                selectedTimeframe = selectedTimeframe,
                onTimeframeChange = onTimeframeChange
            )
            
            // Main chart area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(start = 8.dp, end = 48.dp, top = 8.dp, bottom = if (showVolume) 60.dp else 24.dp)
            ) {
                if (candles.isEmpty()) {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = colors.gold)
                    }
                } else {
                    // Chart canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    touchPosition = offset
                                    // Find nearest candle
                                    val candleWidth = size.width / candles.size.toFloat()
                                    val index = (offset.x / candleWidth).toInt()
                                        .coerceIn(0, candles.size - 1)
                                    selectedCandle = candles[index]
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragEnd = { touchPosition = null; selectedCandle = null },
                                    onDragCancel = { touchPosition = null; selectedCandle = null }
                                ) { change, _ ->
                                    touchPosition = change.position
                                    val candleWidth = size.width / candles.size.toFloat()
                                    val index = (change.position.x / candleWidth).toInt()
                                        .coerceIn(0, candles.size - 1)
                                    selectedCandle = candles[index]
                                }
                            }
                    ) {
                        val chartHeight = size.height
                        val chartWidth = size.width
                        
                        // Calculate price range
                        val priceHigh = candles.maxOf { it.high }
                        val priceLow = candles.minOf { it.low }
                        val priceRange = priceHigh - priceLow
                        val pricePadding = priceRange * 0.1
                        val adjustedHigh = priceHigh + pricePadding
                        val adjustedLow = priceLow - pricePadding
                        val adjustedRange = adjustedHigh - adjustedLow
                        
                        // Price to Y coordinate
                        fun priceToY(price: Double): Float {
                            return ((adjustedHigh - price) / adjustedRange * chartHeight).toFloat()
                        }
                        
                        // Draw grid
                        if (showGrid) {
                            drawGrid(
                                chartWidth = chartWidth,
                                chartHeight = chartHeight,
                                gridColor = Color(0xFF1A472A).copy(alpha = 0.4f),
                                horizontalLines = 5,
                                verticalLines = 6
                            )
                        }
                        
                        // Draw overlays (SMA/EMA)
                        overlays.forEach { overlay ->
                            if (overlay.values.size >= candles.size) {
                                val path = Path()
                                overlay.values.takeLast(candles.size).forEachIndexed { index, value ->
                                    val x = index * (chartWidth / candles.size) + (chartWidth / candles.size / 2)
                                    val y = priceToY(value)
                                    if (index == 0) {
                                        path.moveTo(x, y)
                                    } else {
                                        path.lineTo(x, y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = overlay.color,
                                    style = Stroke(width = 2f)
                                )
                            }
                        }
                        
                        // Draw candles
                        val candleWidth = chartWidth / candles.size
                        val bodyWidth = candleWidth * 0.7f
                        
                        candles.forEachIndexed { index, candle ->
                            val x = index * candleWidth + candleWidth / 2
                            val isGreen = candle.close >= candle.open
                            // BUILD #103: Updated candle colors for better visibility
                            // Green: Bright crisp green (StatusGreen = 0xFF00E676)
                            // Red: Crimson (0xFFDC143C) - easier on eyes than previous pink-ish red
                            val candleColor = if (isGreen) {
                                StatusGreen // Bright green for bullish candles
                            } else {
                                Color(0xFFDC143C) // Crimson for bearish candles
                            }
                            
                            // Wick
                            drawLine(
                                color = candleColor,
                                start = Offset(x, priceToY(candle.high)),
                                end = Offset(x, priceToY(candle.low)),
                                strokeWidth = 1.5f
                            )
                            
                            // Body
                            val bodyTop = priceToY(max(candle.open, candle.close))
                            val bodyBottom = priceToY(min(candle.open, candle.close))
                            val bodyHeight = max(bodyBottom - bodyTop, 1f)
                            
                            drawRect(
                                color = candleColor,
                                topLeft = Offset(x - bodyWidth / 2, bodyTop),
                                size = Size(bodyWidth, bodyHeight)
                            )
                        }
                        
                        // Current price line
                        val currentY = priceToY(currentPrice)
                        drawLine(
                            color = Color(0xFFD4AF37),
                            start = Offset(0f, currentY),
                            end = Offset(chartWidth, currentY),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
                        )
                        
                        // Crosshair
                        touchPosition?.let { pos ->
                            // Vertical line
                            drawLine(
                                color = Color.White.copy(alpha = 0.5f),
                                start = Offset(pos.x, 0f),
                                end = Offset(pos.x, chartHeight),
                                strokeWidth = 1f
                            )
                            // Horizontal line
                            drawLine(
                                color = Color.White.copy(alpha = 0.5f),
                                start = Offset(0f, pos.y),
                                end = Offset(chartWidth, pos.y),
                                strokeWidth = 1f
                            )
                        }
                    }
                    
                    // Price axis (right side)
                    PriceAxis(
                        candles = candles,
                        currentPrice = currentPrice,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = 48.dp)
                            .fillMaxHeight()
                            .width(48.dp)
                    )
                }
            }
            
            // Volume bars
            if (showVolume && candles.isNotEmpty()) {
                VolumeChart(
                    candles = candles,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(start = 8.dp, end = 48.dp, bottom = 8.dp)
                )
            }
            
            // Selected candle info
            selectedCandle?.let { candle ->
                CandleInfoBar(candle = candle)
            }
        }
    }
}

// =============================================================================
// CHART HEADER
// =============================================================================

@Composable
private fun ChartHeader(
    symbol: String,
    currentPrice: Double,
    priceChange24h: Double,
    selectedTimeframe: ChartTimeframe,
    onTimeframeChange: (ChartTimeframe) -> Unit
) {
    val colors = VintageTheme.colors
    val isPositive = priceChange24h >= 0
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Symbol and price
            Column {
                Text(
                    symbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.gold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "A$${formatPrice(currentPrice)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = if (isPositive) colors.profitGreen.copy(alpha = 0.15f)
                                else colors.lossRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "${if (isPositive) "+" else ""}${String.format("%.2f", priceChange24h)}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isPositive) colors.profitGreen else colors.lossRed,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            // Timeframe selector
            TimeframeSelector(
                selected = selectedTimeframe,
                onSelect = onTimeframeChange
            )
        }
    }
}

@Composable
private fun TimeframeSelector(
    selected: ChartTimeframe,
    onSelect: (ChartTimeframe) -> Unit
) {
    val colors = VintageTheme.colors
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ChartTimeframe.entries.forEach { tf ->
            val isSelected = tf == selected
            Surface(
                color = if (isSelected) colors.gold else Color.Transparent,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onSelect(tf) }
                    .border(
                        width = 1.dp,
                        color = if (isSelected) colors.gold else colors.gold.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(4.dp)
                    )
            ) {
                Text(
                    tf.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) colors.emeraldDeep else colors.gold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// =============================================================================
// PRICE AXIS
// =============================================================================

@Composable
private fun PriceAxis(
    candles: List<CandleData>,
    currentPrice: Double,
    modifier: Modifier = Modifier
) {
    val colors = VintageTheme.colors
    
    if (candles.isEmpty()) return
    
    val priceHigh = candles.maxOf { it.high }
    val priceLow = candles.minOf { it.low }
    val priceRange = priceHigh - priceLow
    val pricePadding = priceRange * 0.1
    val adjustedHigh = priceHigh + pricePadding
    val adjustedLow = priceLow - pricePadding
    
    // Calculate price levels (5 levels)
    val levels = (0..4).map { i ->
        adjustedHigh - (adjustedHigh - adjustedLow) * i / 4
    }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        levels.forEach { price ->
            Text(
                formatPrice(price),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                fontSize = 9.sp
            )
        }
    }
}

// =============================================================================
// VOLUME CHART
// =============================================================================

@Composable
private fun VolumeChart(
    candles: List<CandleData>,
    modifier: Modifier = Modifier
) {
    val maxVolume = candles.maxOfOrNull { it.volume } ?: 1.0
    
    Canvas(modifier = modifier) {
        val barWidth = size.width / candles.size
        val bodyWidth = barWidth * 0.7f
        
        candles.forEachIndexed { index, candle ->
            val x = index * barWidth + barWidth / 2
            val isGreen = candle.close >= candle.open
            val barColor = if (isGreen) {
                StatusGreen.copy(alpha = 0.5f)
            } else {
                Color(0xFFE57373).copy(alpha = 0.5f)
            }
            
            val barHeight = (candle.volume / maxVolume * size.height).toFloat()
            
            drawRect(
                color = barColor,
                topLeft = Offset(x - bodyWidth / 2, size.height - barHeight),
                size = Size(bodyWidth, barHeight)
            )
        }
    }
}

// =============================================================================
// CANDLE INFO BAR
// =============================================================================

@Composable
private fun CandleInfoBar(candle: CandleData) {
    val colors = VintageTheme.colors
    val isGreen = candle.close >= candle.open
    
    Surface(
        color = colors.surface.copy(alpha = 0.9f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            InfoItem("O", formatPrice(candle.open), colors.onSurfaceVariant)
            InfoItem("H", formatPrice(candle.high), colors.profitGreen)
            InfoItem("L", formatPrice(candle.low), colors.lossRed)
            InfoItem("C", formatPrice(candle.close), if (isGreen) colors.profitGreen else colors.lossRed)
            InfoItem("Vol", formatVolume(candle.volume), colors.gold)
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = valueColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// =============================================================================
// DRAWING HELPERS
// =============================================================================

private fun DrawScope.drawGrid(
    chartWidth: Float,
    chartHeight: Float,
    gridColor: Color,
    horizontalLines: Int,
    verticalLines: Int
) {
    // Horizontal lines
    for (i in 0..horizontalLines) {
        val y = chartHeight * i / horizontalLines
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(chartWidth, y),
            strokeWidth = 1f
        )
    }
    
    // Vertical lines
    for (i in 0..verticalLines) {
        val x = chartWidth * i / verticalLines
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, chartHeight),
            strokeWidth = 1f
        )
    }
}

// =============================================================================
// FORMATTING HELPERS
// =============================================================================

private fun formatPrice(price: Double): String {
    return when {
        price >= 10000 -> String.format("%.0f", price)
        price >= 1000 -> String.format("%.1f", price)
        price >= 100 -> String.format("%.2f", price)
        price >= 1 -> String.format("%.3f", price)
        else -> String.format("%.6f", price)
    }
}

private fun formatVolume(volume: Double): String {
    return when {
        volume >= 1_000_000_000 -> String.format("%.1fB", volume / 1_000_000_000)
        volume >= 1_000_000 -> String.format("%.1fM", volume / 1_000_000)
        volume >= 1_000 -> String.format("%.1fK", volume / 1_000)
        else -> String.format("%.0f", volume)
    }
}

// =============================================================================
// MOCK DATA GENERATOR (For testing)
// =============================================================================

fun generateMockCandles(count: Int = 60, basePrice: Double = 100000.0): List<CandleData> {
    val candles = mutableListOf<CandleData>()
    var currentPrice = basePrice
    var timestamp = System.currentTimeMillis() - (count * 4 * 60 * 60 * 1000L) // 4h candles
    
    repeat(count) {
        val volatility = currentPrice * 0.02 // 2% volatility
        val open = currentPrice
        val change = (Math.random() - 0.48) * volatility // Slight upward bias
        val close = open + change
        val high = maxOf(open, close) + Math.random() * volatility * 0.5
        val low = minOf(open, close) - Math.random() * volatility * 0.5
        val volume = 1000000 + Math.random() * 5000000
        
        candles.add(
            CandleData(
                timestamp = timestamp,
                open = open,
                high = high,
                low = low,
                close = close,
                volume = volume
            )
        )
        
        currentPrice = close
        timestamp += 4 * 60 * 60 * 1000L // 4 hours
    }
    
    return candles
}
