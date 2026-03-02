package com.miwealth.sovereignvantage.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.miwealth.sovereignvantage.ui.theme.VintageColors
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * EMERGENCY KILL SWITCH UI COMPONENT
 * 
 * A prominent, always-visible emergency stop button for the trading interface.
 * 
 * FEATURES:
 * - Large, unmissable red button
 * - Single tap: Shows confirmation dialog
 * - Long press (3 sec): Bypasses confirmation for true emergencies
 * - Visual feedback during long press
 * - Haptic feedback for tactile confirmation
 * - Shows summary after activation
 * 
 * PLACEMENT:
 * - Trading Screen: Fixed bottom-right corner (primary)
 * - Dashboard: Prominent card in quick actions
 * - Optional: Floating button across all screens (user setting)
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */

// ============================================================================
// COLORS
// ============================================================================

private val EmergencyRed = Color(0xFFDC2626)
private val EmergencyRedDark = Color(0xFF991B1B)
private val EmergencyRedLight = Color(0xFFFCA5A5)
private val WarningYellow = Color(0xFFFBBF24)
private val SafeGreen = Color(0xFF1B8A42)
// KillSwitchNavy removed — replaced with VintageColors.EmeraldDeep
private val KillSwitchGold = Color(0xFFFFD700)

// ============================================================================
// MAIN KILL SWITCH BUTTON (Floating Action Button Style)
// ============================================================================

/**
 * Floating Emergency Kill Switch Button
 * 
 * Place this in your screen's content with appropriate positioning.
 * Recommended: Bottom-right corner with padding.
 * 
 * @param onEmergencyStop Callback when emergency stop is confirmed
 * @param openPositions Number of open positions (shown in confirmation)
 * @param isActive Whether the kill switch is currently active
 * @param onReset Callback to reset the kill switch
 */
@Composable
fun EmergencyKillSwitchButton(
    onEmergencyStop: () -> Unit,
    openPositions: Int = 0,
    isActive: Boolean = false,
    onReset: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var showActivatedDialog by remember { mutableStateOf(false) }
    var longPressProgress by remember { mutableFloatStateOf(0f) }
    var isLongPressing by remember { mutableStateOf(false) }
    
    val haptic = LocalHapticFeedback.current
    
    // Animation for pulsing when active
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    // Long press progress animation
    LaunchedEffect(isLongPressing) {
        if (isLongPressing) {
            val startTime = System.currentTimeMillis()
            val duration = 3000L // 3 seconds
            
            while (isLongPressing && longPressProgress < 1f) {
                val elapsed = System.currentTimeMillis() - startTime
                longPressProgress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                
                // Haptic at milestones
                if (longPressProgress >= 0.33f && longPressProgress < 0.34f) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                if (longPressProgress >= 0.66f && longPressProgress < 0.67f) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                
                if (longPressProgress >= 1f) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEmergencyStop()
                    showActivatedDialog = true
                    isLongPressing = false
                }
                
                delay(16)
            }
        } else {
            longPressProgress = 0f
        }
    }
    
    Box(modifier = modifier) {
        if (isActive) {
            KillSwitchActiveButton(pulseAlpha = pulseAlpha, onReset = onReset)
        } else {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(EmergencyRed, EmergencyRedDark)
                        )
                    )
                    .border(
                        width = 3.dp,
                        color = if (isLongPressing) WarningYellow else EmergencyRedLight,
                        shape = CircleShape
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showConfirmationDialog = true
                            },
                            onPress = {
                                isLongPressing = true
                                try {
                                    awaitRelease()
                                } finally {
                                    isLongPressing = false
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLongPressing && longPressProgress > 0f) {
                    CircularProgressIndicator(
                        progress = { longPressProgress },
                        modifier = Modifier.size(76.dp),
                        color = WarningYellow,
                        strokeWidth = 4.dp
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Emergency Stop",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "STOP",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    
    if (showConfirmationDialog) {
        EmergencyConfirmationDialog(
            openPositions = openPositions,
            onConfirm = {
                showConfirmationDialog = false
                onEmergencyStop()
                showActivatedDialog = true
            },
            onDismiss = { showConfirmationDialog = false }
        )
    }
    
    if (showActivatedDialog) {
        EmergencyActivatedDialog(
            positionsClosed = openPositions,
            onDismiss = { showActivatedDialog = false }
        )
    }
}

@Composable
private fun KillSwitchActiveButton(
    pulseAlpha: Float,
    onReset: () -> Unit
) {
    Box {
        Box(
            modifier = Modifier
                .size(80.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(color = WarningYellow.copy(alpha = pulseAlpha))
                .border(width = 3.dp, color = Color.White, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Kill Switch Active",
                    tint = EmergencyRedDark,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "HALTED",
                    color = EmergencyRedDark,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Box(modifier = Modifier.offset(x = 50.dp, y = (-10).dp)) {
            SmallFloatingActionButton(
                onClick = onReset,
                containerColor = SafeGreen
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ============================================================================
// CONFIRMATION DIALOG
// ============================================================================

@Composable
fun EmergencyConfirmationDialog(
    openPositions: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDeep)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(EmergencyRed.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = EmergencyRed,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "🚨 EMERGENCY STOP",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmergencyRed
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "This will immediately:",
                    fontSize = 14.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = VintageColors.TextPrimary.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    ActionItem(Icons.Default.Stop, "Halt ALL trading activity")
                    ActionItem(Icons.Default.Close, "Close $openPositions open position${if (openPositions != 1) "s" else ""} at market")
                    ActionItem(Icons.Default.Cancel, "Cancel all pending orders")
                    ActionItem(Icons.Default.Lock, "Require manual reset to resume")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Are you sure?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("STOP NOW", fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "💡 Tip: Long-press the button for 3 seconds to bypass this dialog",
                    fontSize = 11.sp,
                    color = VintageColors.TextPrimary.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = EmergencyRedLight, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = VintageColors.TextPrimary.copy(alpha = 0.9f))
    }
}

// ============================================================================
// ACTIVATED DIALOG
// ============================================================================

@Composable
fun EmergencyActivatedDialog(
    positionsClosed: Int,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDeep)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(WarningYellow.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = WarningYellow, modifier = Modifier.size(40.dp))
                }
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = "EMERGENCY STOP ACTIVATED",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = WarningYellow
                )
                
                Spacer(Modifier.height(16.dp))
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VintageColors.TextPrimary.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    SummaryRow("Positions Closed", "$positionsClosed")
                    SummaryRow("Trading Status", "HALTED")
                    SummaryRow("Pending Orders", "Cancelled")
                }
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = "Trading is now halted. Use the reset button when you're ready to resume.",
                    fontSize = 13.sp,
                    color = VintageColors.TextPrimary.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = VintageColors.Gold)
                ) {
                    Text("OK", color = VintageColors.EmeraldDeep, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = VintageColors.TextPrimary.copy(alpha = 0.7f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}

// ============================================================================
// DASHBOARD CARD VERSION
// ============================================================================

/**
 * Emergency Kill Switch Card for Dashboard quick actions area.
 */
@Composable
fun EmergencyKillSwitchCard(
    onEmergencyStop: () -> Unit,
    openPositions: Int = 0,
    isActive: Boolean = false,
    onReset: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showConfirmationDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    
    Card(
        modifier = modifier.fillMaxWidth().height(100.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) WarningYellow.copy(alpha = 0.2f) else EmergencyRed.copy(alpha = 0.15f)
        ),
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (isActive) onReset() else showConfirmationDialog = true
        }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isActive) "⚠️ TRADING HALTED" else "🚨 EMERGENCY STOP",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) WarningYellow else EmergencyRed
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isActive) "Tap to reset and resume trading" 
                           else "Tap to halt trading & close $openPositions position${if (openPositions != 1) "s" else ""}",
                    fontSize = 12.sp,
                    color = VintageColors.TextPrimary.copy(alpha = 0.7f)
                )
            }
            
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (isActive) WarningYellow else EmergencyRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.Refresh else Icons.Default.PowerSettingsNew,
                    contentDescription = if (isActive) "Reset" else "Emergency Stop",
                    tint = if (isActive) VintageColors.EmeraldDeep else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
    
    if (showConfirmationDialog) {
        EmergencyConfirmationDialog(
            openPositions = openPositions,
            onConfirm = {
                showConfirmationDialog = false
                onEmergencyStop()
            },
            onDismiss = { showConfirmationDialog = false }
        )
    }
}

// ============================================================================
// COMPACT CHIP VERSION
// ============================================================================

/**
 * Compact emergency stop chip for toolbars/headers.
 */
@Composable
fun EmergencyStopChip(
    onEmergencyStop: () -> Unit,
    openPositions: Int = 0,
    isActive: Boolean = false,
    onReset: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showConfirmationDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    
    AssistChip(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (isActive) onReset() else showConfirmationDialog = true
        },
        label = {
            Text(
                text = if (isActive) "HALTED" else "STOP",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        },
        leadingIcon = {
            Icon(
                imageVector = if (isActive) Icons.Default.Warning else Icons.Default.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        modifier = modifier,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (isActive) WarningYellow else EmergencyRed,
            labelColor = if (isActive) VintageColors.EmeraldDeep else Color.White,
            leadingIconContentColor = if (isActive) VintageColors.EmeraldDeep else Color.White
        )
    )
    
    if (showConfirmationDialog) {
        EmergencyConfirmationDialog(
            openPositions = openPositions,
            onConfirm = {
                showConfirmationDialog = false
                onEmergencyStop()
            },
            onDismiss = { showConfirmationDialog = false }
        )
    }
}
