package com.miwealth.sovereignvantage.ui.trading

/**
 * SOVEREIGN VANTAGE™ — BUILD #270
 * ACTIVE POSITION CARD
 *
 * Per-trade live bar showing real-time P&L, STAHL stair level,
 * margin used, liquidation price, time elapsed, and a Close Trade button.
 *
 * Close button behaviour:
 *   confirmTradeClose = true  → single confirm dialog (default, safe)
 *   confirmTradeClose = false → immediate close, no dialog (power-user mode,
 *                               toggled in Settings → "Confirm Before Closing Trade")
 *
 * © 2025-2026 MiWealth Pty Ltd — Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966–2025)
 * Dedicated to Cathryn 💘
 */

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.miwealth.sovereignvantage.ui.theme.VintageColors
import kotlin.math.abs

/**
 * Full active-position bar. Displayed in AISignalsContent for every open trade.
 *
 * @param position      PositionInfo from TradingViewModel.uiState.positions
 * @param confirmClose  Mirror of TradingUiState.confirmTradeClose setting
 * @param onClose       Callback — invoked with positionKey when user confirms close
 */
@Composable
fun ActivePositionCard(
    position: PositionInfo,
    confirmClose: Boolean,
    onClose: (positionKey: String) -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    // ── Colour-code P&L ──────────────────────────────────────────────────────
    val isPnlPositive = position.unrealizedPnl >= 0.0
    val pnlColour by animateColorAsState(
        targetValue = if (isPnlPositive) Color(0xFF00E676) else Color(0xFFFF5252),
        animationSpec = tween(400),
        label = "pnl_colour"
    )

    // ── STAHL level indicator colour ─────────────────────────────────────────
    val stahlColour = when {
        position.stahlLevel >= 8  -> Color(0xFF00E676)   // Deep green — locked in profit
        position.stahlLevel >= 4  -> VintageColors.Gold  // Gold — progressing well
        position.stahlLevel >= 1  -> Color(0xFFFFD740)   // Amber — moving
        else                      -> VintageColors.TextTertiary
    }

    // ── Elapsed time ─────────────────────────────────────────────────────────
    val elapsedLabel = remember(position.entryTime) {
        val ms = System.currentTimeMillis() - position.entryTime
        val minutes = (ms / 60_000).toInt()
        when {
            minutes < 60   -> "${minutes}m"
            minutes < 1440 -> "${minutes / 60}h ${minutes % 60}m"
            else           -> "${minutes / 1440}d ${(minutes % 1440) / 60}h"
        }
    }

    // ── Direction label & colour ─────────────────────────────────────────────
    val isLong = position.side.uppercase().let { it == "LONG" || it == "BUY" }
    val directionColour = if (isLong) Color(0xFF00E676) else Color(0xFFFF5252)
    val directionLabel  = if (isLong) "LONG ▲" else "SHORT ▼"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        pnlColour.copy(alpha = 0.6f),
                        VintageColors.GoldDark.copy(alpha = 0.3f),
                        pnlColour.copy(alpha = 0.6f)
                    )
                ),
                shape = RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Row 1: Symbol + Direction + P&L ─────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Symbol
                Text(
                    text = position.symbol.replace("/USDT", "").replace("/USD", ""),
                    color = VintageColors.Gold,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.width(8.dp))
                // Direction badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(directionColour.copy(alpha = 0.15f))
                        .border(0.5.dp, directionColour.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = directionLabel,
                        color = directionColour,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                if (position.leverage > 1) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(VintageColors.Gold.copy(alpha = 0.1f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${position.leverage}×",
                            color = VintageColors.Gold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                // P&L
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (isPnlPositive) "+" else ""}A\$${String.format("%,.2f", position.unrealizedPnl)}",
                        color = pnlColour,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "${if (isPnlPositive) "+" else ""}${String.format("%.2f", position.unrealizedPnlPercent)}%",
                        color = pnlColour.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = VintageColors.EmeraldDeep)
            Spacer(Modifier.height(10.dp))

            // ── Row 2: Metrics grid ──────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth()) {
                MiniMetric(
                    label = "Entry",
                    value = "\$${String.format("%,.4f", position.entryPrice)}",
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = "Current",
                    value = "\$${String.format("%,.4f", position.currentPrice)}",
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = "Qty",
                    value = String.format("%.4f", position.quantity),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                MiniMetric(
                    label = "Margin",
                    value = "A\$${String.format("%,.0f", position.marginUsed)}",
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = "Liq. Price",
                    value = "\$${String.format("%,.2f", position.liquidationPrice)}",
                    valueColour = Color(0xFFFF7043),
                    modifier = Modifier.weight(1f)
                )
                MiniMetric(
                    label = "Open",
                    value = elapsedLabel,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Row 3: STAHL Stair + Close button ────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // STAHL stair level indicator
                Column {
                    Text(
                        text = "STAHL™ LEVEL",
                        color = VintageColors.TextTertiary,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 12 stair pips
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (i in 1..12) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 8.dp, height = 14.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            if (i <= position.stahlLevel) stahlColour
                                            else VintageColors.EmeraldDeep
                                        )
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${position.stahlLevel}/12",
                            color = stahlColour,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Close Trade button
                Button(
                    onClick = {
                        if (confirmClose) {
                            showConfirmDialog = true
                        } else {
                            onClose(position.positionKey)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB71C1C).copy(alpha = 0.85f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "CLOSE",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }

    // ── Confirm close dialog ─────────────────────────────────────────────────
    if (showConfirmDialog) {
        ClosePositionConfirmDialog(
            symbol = position.symbol,
            direction = directionLabel,
            pnl = position.unrealizedPnl,
            pnlPercent = position.unrealizedPnlPercent,
            onConfirm = {
                showConfirmDialog = false
                onClose(position.positionKey)
            },
            onDismiss = { showConfirmDialog = false }
        )
    }
}

// ── Confirm close dialog composable ─────────────────────────────────────────

@Composable
private fun ClosePositionConfirmDialog(
    symbol: String,
    direction: String,
    pnl: Double,
    pnlPercent: Double,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isPnlPositive = pnl >= 0.0
    val pnlColour = if (isPnlPositive) Color(0xFF00E676) else Color(0xFFFF5252)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, VintageColors.GoldDark.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = VintageColors.Gold,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "CLOSE POSITION",
                    color = VintageColors.Gold,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${symbol.replace("/USDT","").replace("/USD","")} · $direction",
                    color = VintageColors.TextPrimary,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                // P&L summary
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(pnlColour.copy(alpha = 0.1f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Realise P&L",
                            color = VintageColors.TextTertiary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "${if (isPnlPositive) "+" else ""}A\$${String.format("%,.2f", pnl)}",
                            color = pnlColour,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "${if (isPnlPositive) "+" else ""}${String.format("%.2f", pnlPercent)}%",
                            color = pnlColour.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "This will close at current market price.\nSTAHL stop will be bypassed.",
                    color = VintageColors.TextTertiary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VintageColors.TextSecondary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VintageColors.TextTertiary)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB71C1C),
                            contentColor = Color.White
                        )
                    ) {
                        Text("CLOSE NOW", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

// ── Mini metric cell ─────────────────────────────────────────────────────────

@Composable
private fun MiniMetric(
    label: String,
    value: String,
    valueColour: Color = VintageColors.TextPrimary,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = VintageColors.TextTertiary,
            fontSize = 9.sp,
            letterSpacing = 0.8.sp
        )
        Text(
            text = value,
            color = valueColour,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp
        )
    }
}
