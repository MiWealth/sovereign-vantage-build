package com.miwealth.sovereignvantage.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * SOVEREIGN VANTAGE - VINTAGE UI COMPONENTS
 * 
 * Luxury-styled components for the Vintage theme:
 * - Gold beveled buttons
 * - Emerald leather cards
 * - Gold frame borders
 * - Parchment notification cards
 * - Navigation bar (brushed gold)
 * 
 * All components adapt to theme mode (Vintage vs Basic)
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

// =========================================================================
// GOLD BEVELED BUTTON
// =========================================================================

@Composable
fun VintageButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: @Composable (() -> Unit)? = null
) {
    val colors = VintageTheme.colors
    val isVintage = VintageTheme.isVintageMode
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val backgroundColor = when {
        !enabled -> colors.goldMuted
        isPressed -> colors.goldBright
        else -> colors.gold
    }
    
    val contentColor = when {
        !enabled -> colors.emeraldDark.copy(alpha = 0.6f)
        else -> colors.emeraldDeep
    }
    
    Box(
        modifier = modifier
            .height(if (isVintage) 56.dp else 48.dp)
            .clip(RoundedCornerShape(if (isVintage) 8.dp else 12.dp))
            .then(
                if (isVintage) {
                    Modifier.goldBeveledBorder()
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = backgroundColor,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            )
            .background(
                brush = if (isVintage) {
                    Brush.verticalGradient(
                        colors = listOf(
                            VintageColors.GradientGoldStart,
                            VintageColors.GradientGoldMid,
                            VintageColors.GradientGoldEnd
                        )
                    )
                } else {
                    Brush.linearGradient(listOf(backgroundColor, backgroundColor))
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                icon?.invoke()
                if (icon != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (isVintage) 16.sp else 14.sp
                )
            }
        }
    }
}

// =========================================================================
// EMERALD LEATHER CARD
// =========================================================================

@Composable
fun VintageCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    showGoldFrame: Boolean = VintageTheme.isVintageMode,
    elevation: Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = VintageTheme.colors
    val isVintage = VintageTheme.isVintageMode
    
    Card(
        modifier = modifier
            .then(
                if (showGoldFrame && isVintage) {
                    Modifier.goldBeveledBorder(width = 2.dp, cornerRadius = 12.dp)
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isVintage) colors.gold else colors.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (isVintage) {
                    VintageDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            content()
        }
    }
}

// =========================================================================
// PARCHMENT NOTIFICATION CARD (AI Board messages)
// =========================================================================

@Composable
fun ParchmentCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isVintage = VintageTheme.isVintageMode
    
    if (isVintage) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            VintageColors.ParchmentLight,
                            VintageColors.Parchment,
                            VintageColors.ParchmentDark
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            VintageColors.WalnutLight,
                            VintageColors.WalnutDark
                        )
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(16.dp)
        ) {
            Column {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = VintageColors.WalnutDark,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                CompositionLocalProvider(
                    LocalContentColor provides VintageColors.TextDark
                ) {
                    content()
                }
            }
        }
    } else {
        // Basic mode - simple elevated card
        VintageCard(
            modifier = modifier,
            title = title,
            showGoldFrame = false,
            content = content
        )
    }
}

// =========================================================================
// GOLD FRAME (for charts, images)
// =========================================================================

@Composable
fun GoldFrame(
    modifier: Modifier = Modifier,
    frameWidth: Dp = 4.dp,
    cornerRadius: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isVintage = VintageTheme.isVintageMode
    
    Box(
        modifier = modifier
            .then(
                if (isVintage) {
                    Modifier.goldBeveledBorder(width = frameWidth, cornerRadius = cornerRadius)
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = VintageTheme.colors.outline,
                        shape = RoundedCornerShape(cornerRadius)
                    )
                }
            )
            .clip(RoundedCornerShape(cornerRadius))
    ) {
        content()
    }
}

// =========================================================================
// VINTAGE DIVIDER (gold gradient line)
// =========================================================================

@Composable
fun VintageDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp
) {
    val isVintage = VintageTheme.isVintageMode
    
    if (isVintage) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(thickness)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            VintageColors.GoldDark,
                            VintageColors.Gold,
                            VintageColors.GoldDark,
                            Color.Transparent
                        )
                    )
                )
        )
    } else {
        HorizontalDivider(
            modifier = modifier,
            thickness = thickness,
            color = VintageTheme.colors.divider
        )
    }
}

// =========================================================================
// NAVIGATION BAR
// =========================================================================

data class NavItem(
    val label: String,
    val icon: @Composable () -> Unit,
    val route: String
)

@Composable
fun VintageNavigationBar(
    items: List<NavItem>,
    selectedRoute: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = VintageTheme.colors
    val isVintage = VintageTheme.isVintageMode
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isVintage) colors.navBarBackground else colors.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isVintage) 72.dp else 64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = item.route == selectedRoute
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onItemSelected(item.route) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isVintage) 40.dp else 36.dp)
                            .then(
                                if (isSelected && isVintage) {
                                    Modifier
                                        .clip(CircleShape)
                                        .background(colors.emeraldDeep.copy(alpha = 0.2f))
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides if (isSelected) {
                                if (isVintage) colors.emeraldDeep else colors.primary
                            } else {
                                if (isVintage) colors.emeraldDark.copy(alpha = 0.6f) else colors.onSurface.copy(alpha = 0.6f)
                            }
                        ) {
                            item.icon()
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            if (isVintage) colors.emeraldDeep else colors.primary
                        } else {
                            if (isVintage) colors.emeraldDark.copy(alpha = 0.6f) else colors.onSurface.copy(alpha = 0.6f)
                        },
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// =========================================================================
// PROFIT/LOSS INDICATOR
// =========================================================================

@Composable
fun ProfitLossText(
    value: Double,
    prefix: String = "",
    suffix: String = "",
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    val colors = VintageTheme.colors
    val isProfit = value >= 0
    
    val color = if (isProfit) colors.profitGreen else colors.lossRed
    val sign = if (isProfit) "+" else ""
    
    Text(
        text = "$prefix$sign${String.format("%.2f", value)}$suffix",
        color = color,
        style = style,
        modifier = modifier
    )
}

@Composable
fun ProfitLossChip(
    value: Double,
    modifier: Modifier = Modifier
) {
    val colors = VintageTheme.colors
    val isProfit = value >= 0
    
    val backgroundColor = if (isProfit) {
        colors.profitGreen.copy(alpha = 0.15f)
    } else {
        colors.lossRed.copy(alpha = 0.15f)
    }
    
    val textColor = if (isProfit) colors.profitGreen else colors.lossRed
    val sign = if (isProfit) "+" else ""
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor
    ) {
        Text(
            text = "$sign${String.format("%.2f", value)}%",
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// =========================================================================
// LOADING INDICATOR
// =========================================================================

@Composable
fun VintageLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val colors = VintageTheme.colors
    val isVintage = VintageTheme.isVintageMode
    
    if (isVintage) {
        // Gold spinning indicator
        val infiniteTransition = rememberInfiniteTransition(label = "loading")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
        
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size)) {
                val strokeWidth = size.toPx() / 10
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            colors.gold,
                            colors.goldBright
                        )
                    ),
                    startAngle = rotation,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
    } else {
        CircularProgressIndicator(
            modifier = modifier.size(size),
            color = colors.primary
        )
    }
}

// =========================================================================
// MODIFIER EXTENSIONS
// =========================================================================

/**
 * Gold beveled border modifier
 */
fun Modifier.goldBeveledBorder(
    width: Dp = 3.dp,
    cornerRadius: Dp = 8.dp
): Modifier = this.then(
    Modifier.drawBehind {
        val strokeWidth = width.toPx()
        val radius = cornerRadius.toPx()
        
        // Outer highlight (lighter gold - simulates light source)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    VintageColors.GoldBright,
                    VintageColors.Gold
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
            style = Stroke(width = strokeWidth)
        )
        
        // Inner shadow (darker gold - simulates depth)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    VintageColors.GoldDark,
                    VintageColors.Gold.copy(alpha = 0.5f)
                ),
                start = Offset(size.width, size.height),
                end = Offset.Zero
            ),
            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
            size = androidx.compose.ui.geometry.Size(
                size.width - strokeWidth,
                size.height - strokeWidth
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius - strokeWidth / 2),
            style = Stroke(width = strokeWidth / 2)
        )
    }
)

/**
 * Leather texture background modifier (requires drawable resource)
 * For now, uses gradient approximation
 */
fun Modifier.leatherBackground(): Modifier = this.then(
    Modifier.background(
        brush = Brush.verticalGradient(
            colors = listOf(
                VintageColors.EmeraldDark,
                VintageColors.EmeraldDeep,
                VintageColors.EmeraldDark
            )
        )
    )
)
