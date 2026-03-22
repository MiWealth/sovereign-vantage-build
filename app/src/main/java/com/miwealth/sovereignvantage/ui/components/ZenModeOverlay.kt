package com.miwealth.sovereignvantage.ui.components

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miwealth.sovereignvantage.ui.theme.VintageColors
import com.miwealth.sovereignvantage.ui.theme.VintageTheme
import kotlinx.coroutines.delay
import java.util.*
import kotlin.math.abs

/**
 * SOVEREIGN VANTAGE - ZEN MODE OVERLAY
 * 
 * Full-screen animated skeleton clock for relaxation and quick-hide.
 * 
 * Features:
 * - Gold gear mechanism background (slowly rotating)
 * - Ornate baroque clock hands synced to real time
 * - Multiple activation methods (button, triple-tap, shake)
 * - Exit via button or tap anywhere
 * 
 * Purpose:
 * - Relaxation / mental break from trading
 * - Quick-hide trading activity when someone walks by
 * - Visual showcase of luxury aesthetic
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

// =========================================================================
// ZEN MODE STATE
// =========================================================================

data class ZenModeConfig(
    val enableTripleTap: Boolean = true,
    val enableShake: Boolean = true,
    val shakeThreshold: Float = 15f,    // Acceleration threshold
    val tripleTapTimeoutMs: Long = 500  // Time window for triple tap
)

class ZenModeState(
    private val config: ZenModeConfig = ZenModeConfig()
) {
    var isActive by mutableStateOf(false)
        private set
    
    private var lastTapTime = 0L
    private var tapCount = 0
    
    fun activate() {
        isActive = true
    }
    
    fun deactivate() {
        isActive = false
    }
    
    fun toggle() {
        isActive = !isActive
    }
    
    fun onTap() {
        if (!config.enableTripleTap) return
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTapTime < config.tripleTapTimeoutMs) {
            tapCount++
            if (tapCount >= 3) {
                activate()
                tapCount = 0
            }
        } else {
            tapCount = 1
        }
        lastTapTime = currentTime
    }
    
    fun onShake(acceleration: Float) {
        if (!config.enableShake) return
        if (abs(acceleration) > config.shakeThreshold) {
            activate()
        }
    }
}

@Composable
fun rememberZenModeState(
    config: ZenModeConfig = ZenModeConfig()
): ZenModeState {
    return remember { ZenModeState(config) }
}

// =========================================================================
// ZEN MODE OVERLAY
// =========================================================================

@Composable
fun ZenModeOverlay(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(500)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Background: Slowly rotating gear mechanism
            GearMechanismBackground()
            
            // Foreground: Animated clock face
            SkeletonClock()
            
            // Close button (top right)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .alpha(0.6f)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit Zen Mode",
                    tint = VintageColors.Gold,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Subtle branding at bottom
            Text(
                text = "SOVEREIGN VANTAGE",
                color = VintageColors.Gold.copy(alpha = 0.3f),
                fontSize = 12.sp,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            )
        }
    }
}

// =========================================================================
// GEAR MECHANISM BACKGROUND
// =========================================================================

@Composable
private fun GearMechanismBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "gears")
    
    // Slow rotation for main gear (1 rotation per 60 seconds)
    val mainGearRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mainGear"
    )
    
    // Counter-rotation for visual interest
    val counterRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(90000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counterGear"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(0.4f)  // Subdued background
    ) {
        // In production, load actual gear mechanism image
        // Image(
        //     painter = painterResource(id = R.drawable.gold_gear_mechanism),
        //     contentDescription = null,
        //     modifier = Modifier
        //         .fillMaxSize()
        //         .rotate(mainGearRotation),
        //     contentScale = ContentScale.Crop
        // )
        
        // Fallback: Programmatic gear representation
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(mainGearRotation)
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = minOf(size.width, size.height) * 0.4f
            
            // Draw gear teeth pattern
            val teethCount = 24
            for (i in 0 until teethCount) {
                val angle = (i * 360f / teethCount) * (Math.PI / 180f)
                val innerRadius = radius * 0.85f
                val outerRadius = radius
                
                val startX = centerX + (innerRadius * kotlin.math.cos(angle)).toFloat()
                val startY = centerY + (innerRadius * kotlin.math.sin(angle)).toFloat()
                val endX = centerX + (outerRadius * kotlin.math.cos(angle)).toFloat()
                val endY = centerY + (outerRadius * kotlin.math.sin(angle)).toFloat()
                
                drawLine(
                    color = VintageColors.GoldDark,
                    start = androidx.compose.ui.geometry.Offset(startX, startY),
                    end = androidx.compose.ui.geometry.Offset(endX, endY),
                    strokeWidth = 8f
                )
            }
            
            // Central hub
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        VintageColors.Gold,
                        VintageColors.GoldDark
                    ),
                    center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                    radius = radius * 0.3f
                ),
                radius = radius * 0.3f,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY)
            )
        }
    }
}

// =========================================================================
// SKELETON CLOCK (Real-time animated hands)
// =========================================================================

@Composable
private fun SkeletonClock() {
    // Get current time and update every second
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance()
            delay(1000)
        }
    }
    
    val hours = currentTime.get(Calendar.HOUR)
    val minutes = currentTime.get(Calendar.MINUTE)
    val seconds = currentTime.get(Calendar.SECOND)
    
    // Calculate rotation angles
    val hourRotation = (hours * 30f) + (minutes * 0.5f)  // 360/12 = 30° per hour
    val minuteRotation = (minutes * 6f) + (seconds * 0.1f)  // 360/60 = 6° per minute
    val secondRotation = seconds * 6f  // 360/60 = 6° per second
    
    // Smooth animation for second hand
    val animatedSecondRotation by animateFloatAsState(
        targetValue = secondRotation,
        animationSpec = tween(200, easing = LinearEasing),
        label = "secondHand"
    )
    
    Box(
        modifier = Modifier
            .size(300.dp),
        contentAlignment = Alignment.Center
    ) {
        // Clock face (Roman numerals)
        ClockFace()
        
        // Hour hand (baroque/ornate style)
        ClockHand(
            rotation = hourRotation,
            length = 0.5f,
            width = 12.dp,
            color = VintageColors.Gold
        )
        
        // Minute hand (baroque/ornate style)
        ClockHand(
            rotation = minuteRotation,
            length = 0.7f,
            width = 8.dp,
            color = VintageColors.Gold
        )
        
        // Second hand (simple, thin)
        ClockHand(
            rotation = animatedSecondRotation,
            length = 0.75f,
            width = 2.dp,
            color = VintageColors.GoldBright
        )
        
        // Center hub
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            VintageColors.GoldBright,
                            VintageColors.Gold,
                            VintageColors.GoldDark
                        )
                    ),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
    }
}

// =========================================================================
// CLOCK FACE
// =========================================================================

@Composable
private fun ClockFace() {
    val romanNumerals = listOf("XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI")
    
    Box(
        modifier = Modifier.size(280.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = VintageColors.GoldDark,
                radius = size.minDimension / 2,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
            )
            
            // Inner ring
            drawCircle(
                color = VintageColors.Gold.copy(alpha = 0.3f),
                radius = size.minDimension / 2 - 20f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
        }
        
        // Roman numerals
        romanNumerals.forEachIndexed { index, numeral ->
            val angle = (index * 30f - 90f) * (Math.PI / 180f)
            val radius = 110.dp
            
            Text(
                text = numeral,
                color = VintageColors.Gold,
                fontSize = if (numeral == "XII" || numeral == "VI") 18.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .offset(
                        x = (radius.value * kotlin.math.cos(angle)).dp,
                        y = (radius.value * kotlin.math.sin(angle)).dp
                    )
            )
        }
        
        // Hour markers (small dots between numerals)
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (i in 0 until 60) {
                if (i % 5 != 0) {  // Skip positions with numerals
                    val angle = (i * 6f - 90f) * (Math.PI / 180f)
                    val radius = size.minDimension / 2 - 30f
                    val dotSize = 2f
                    
                    drawCircle(
                        color = VintageColors.GoldDark,
                        radius = dotSize,
                        center = androidx.compose.ui.geometry.Offset(
                            (size.width / 2 + radius * kotlin.math.cos(angle)).toFloat(),
                            (size.height / 2 + radius * kotlin.math.sin(angle)).toFloat()
                        )
                    )
                }
            }
        }
    }
}

// =========================================================================
// CLOCK HAND
// =========================================================================

@Composable
private fun ClockHand(
    rotation: Float,
    length: Float,  // As fraction of clock radius
    width: androidx.compose.ui.unit.Dp,
    color: Color
) {
    Box(
        modifier = Modifier
            .size(280.dp)
            .graphicsLayer {
                rotationZ = rotation
            },
        contentAlignment = Alignment.Center
    ) {
        // Hand body
        Box(
            modifier = Modifier
                .offset(y = -(280.dp * length / 2 - 20.dp))
                .width(width)
                .height(280.dp * length / 2)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color,
                            color.copy(alpha = 0.8f)
                        )
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(width / 2)
                )
        )
        
        // Counter-balance (small tail)
        Box(
            modifier = Modifier
                .offset(y = 30.dp)
                .width(width * 0.7f)
                .height(40.dp)
                .background(
                    color = color.copy(alpha = 0.6f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(width / 2)
                )
        )
    }
}

// =========================================================================
// SHAKE DETECTOR HOOK
// =========================================================================

@Composable
fun rememberShakeDetector(
    threshold: Float = 15f,
    onShake: () -> Unit
): SensorEventListener? {
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
    }
    
    var lastUpdate by remember { mutableLongStateOf(0L) }
    var lastX by remember { mutableFloatStateOf(0f) }
    var lastY by remember { mutableFloatStateOf(0f) }
    var lastZ by remember { mutableFloatStateOf(0f) }
    
    val listener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdate < 100) return
                
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                val deltaX = x - lastX
                val deltaY = y - lastY
                val deltaZ = z - lastZ
                
                val acceleration = kotlin.math.sqrt(
                    deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ
                )
                
                if (acceleration > threshold) {
                    onShake()
                }
                
                lastX = x
                lastY = y
                lastZ = z
                lastUpdate = currentTime
            }
            
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }
    
    DisposableEffect(sensorManager) {
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            sensorManager.registerListener(
                listener,
                accelerometer,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }
    
    return listener
}
