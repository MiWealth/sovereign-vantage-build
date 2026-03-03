/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * PROFIT FLASH MANAGER — Gold frame celebration on profit realisation
 *
 * When a profit is taken, the app flashes a gold border twice (2 seconds apart).
 * Any component in the app can trigger this via ProfitFlashManager.flash().
 *
 * © 2025-2026 MiWealth Pty Ltd
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 */

package com.miwealth.sovereignvantage.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Singleton manager — call ProfitFlashManager.flash() from anywhere
 * (ViewModels, trading engine callbacks, etc.) to trigger the gold frame.
 */
object ProfitFlashManager {

    private val _flashTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val flashTrigger = _flashTrigger.asSharedFlow()

    /**
     * Trigger two gold frame flashes. Safe to call from any coroutine scope.
     */
    fun flash() {
        _flashTrigger.tryEmit(Unit)
    }
}

/**
 * Wrap your root content with this composable.
 * It draws a gold border that flashes twice on profit events.
 *
 * Usage in MainActivity:
 * ```
 * ProfitFlashFrame {
 *     SovereignVantageNavHost(navController)
 * }
 * ```
 */
@Composable
fun ProfitFlashFrame(
    content: @Composable () -> Unit
) {
    // Track current flash alpha (0f = invisible, 1f = full gold)
    var flashAlpha by remember { mutableStateOf(0f) }

    // Animate the alpha smoothly
    val animatedAlpha by animateFloatAsState(
        targetValue = flashAlpha,
        animationSpec = tween(
            durationMillis = 250,
            easing = FastOutSlowInEasing
        ),
        label = "profitFlashAlpha"
    )

    // Listen for flash triggers
    LaunchedEffect(Unit) {
        ProfitFlashManager.flashTrigger.collect {
            // Flash 1: fade in → hold → fade out
            flashAlpha = 1f
            delay(400)
            flashAlpha = 0f
            delay(1600)  // 2 seconds between flash starts (400 on + 1600 gap)
            // Flash 2: fade in → hold → fade out
            flashAlpha = 1f
            delay(400)
            flashAlpha = 0f
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Content always visible
        content()

        // Gold frame overlay — only visible during flash
        if (animatedAlpha > 0.01f) {
            val goldColor = Color(0xFFD4AF37)
            val innerGold = Color(0xFFFFE066)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 3.dp,
                        color = goldColor.copy(alpha = animatedAlpha),
                        shape = RectangleShape
                    )
                    .border(
                        width = 1.dp,
                        color = innerGold.copy(alpha = animatedAlpha * 0.7f),
                        shape = RectangleShape
                    )
            )
        }
    }
}
