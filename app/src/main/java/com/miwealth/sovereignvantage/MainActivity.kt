package com.miwealth.sovereignvantage

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.ScrollView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.miwealth.sovereignvantage.ui.navigation.SovereignVantageNavHost
import com.miwealth.sovereignvantage.ui.components.ProfitFlashFrame
import com.miwealth.sovereignvantage.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import com.miwealth.sovereignvantage.core.TradingSystemManager
import javax.inject.Inject

/**
 * SOVEREIGN VANTAGE V5.19.103 "ARTHUR EDITION"
 * MAIN ACTIVITY — Defensive Startup + Lifecycle Management
 *
 * BUILD #103 CHANGES:
 * - Added onPause/onResume lifecycle handlers to fix memory leak
 * - Stops trading system (WebSockets, coroutine jobs) when app minimizes
 * - Resumes trading when app returns to foreground
 * - Prevents OutOfMemoryError crashes from background resource accumulation
 *
 * © 2025-2026 MiWealth Pty Ltd
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }
    
    @Inject
    lateinit var tradingSystemManager: TradingSystemManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // ── Splash screen (wrapped — Samsung One UI can throw here) ──
        try {
            installSplashScreen()
        } catch (e: Exception) {
            Log.e(TAG, "SplashScreen init failed (non-fatal): ${e.message}")
        }

        super.onCreate(savedInstanceState)

        // NOTE: enableEdgeToEdge() deliberately NOT called.
        // Causes crashes on Samsung One UI. Colours set in Compose theme instead.

        // ── Check for crash report from previous run ──
        try {
            val app = application as? SovereignVantageApp
            val crashReport = app?.getCrashReport()
            if (crashReport != null) {
                Log.w(TAG, "Previous crash detected — showing crash report")
                app.clearCrashReport()
                val intent = Intent(this, CrashReporterActivity::class.java)
                intent.putExtra("crash_text", crashReport)
                startActivity(intent)
                // Don't finish() — let user press back to try the app again
            }
        } catch (e: Exception) {
            Log.e(TAG, "Crash report check failed: ${e.message}")
        }

        // ── Main Compose UI ──
        try {
            setContent {
                SovereignVantageTheme {
                    VintageTheme(themeMode = ThemeMode.VINTAGE) {
                    // ═══════════════════════════════════════════════════════
                    // NO STATIC GOLD FRAME — clean dark surface
                    // Gold frame ONLY flashes on profit realisation
                    // via ProfitFlashManager.flash() (2 pulses, 2s apart)
                    // ═══════════════════════════════════════════════════════
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ProfitFlashFrame {
                            val navController = rememberNavController()
                            SovereignVantageNavHost(navController = navController)
                        }
                    }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "FATAL: setContent failed", e)
            showFallbackError(e)
        }
    }

    /**
     * If Compose completely fails, show a plain Android error screen.
     */
    private fun showFallbackError(error: Throwable) {
        try {
            val scrollView = ScrollView(this)
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 64, 32, 32)
            }

            val title = TextView(this).apply {
                text = "Sovereign Vantage — Compose Failed"
                textSize = 18f
                setTextColor(0xFFFFD700.toInt())
            }

            val body = TextView(this).apply {
                text = buildString {
                    appendLine("V5.17.0 Arthur Edition")
                    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                    appendLine()
                    appendLine(error.stackTraceToString())
                }
                textSize = 11f
                setTextColor(0xFFE0E0E0.toInt())
                setTextIsSelectable(true)
                typeface = android.graphics.Typeface.MONOSPACE
            }

            layout.addView(title)
            layout.addView(body)
            scrollView.addView(layout)
            scrollView.setBackgroundColor(0xFF0A0A0A.toInt())
            setContentView(scrollView)
        } catch (e2: Exception) {
            Log.e(TAG, "Even fallback error display failed", e2)
        }
    }
    
    /**
     * BUILD #103: Pause trading when app goes to background.
     * 
     * CRITICAL FIX FOR MEMORY LEAK:
     * - Stops WebSocket connections (BinancePublicPriceFeed)
     * - Cancels coroutine jobs (balance polling, price feeds, analysis)
     * - Prevents OutOfMemoryError from resource accumulation
     * - Allows Android to reclaim resources when app is minimized
     */
    override fun onPause() {
        super.onPause()
        Log.i(TAG, "📴 App pausing - stopping trading system to prevent memory leak")
        tradingSystemManager.pauseTrading()
    }
    
    /**
     * BUILD #103: Resume trading when app returns to foreground.
     * 
     * Restarts all trading infrastructure that was paused:
     * - WebSocket connections
     * - Price feeds
     * - Balance polling
     * - Analysis loop
     */
    override fun onResume() {
        super.onResume()
        Log.i(TAG, "📱 App resuming - restarting trading system")
        tradingSystemManager.resumeTrading()
    }
}
