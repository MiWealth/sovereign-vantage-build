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
import com.miwealth.sovereignvantage.core.utils.SystemLogger
import dagger.hilt.android.AndroidEntryPoint

/**
 * SOVEREIGN VANTAGE V5.19.107 "ARTHUR EDITION"
 * MAIN ACTIVITY — BUILD #107 Memory Management
 *
 * BUILD #107 CHANGES:
 * - Added lifecycle logging for diagnostics
 * - Added memory cleanup on pause (without stopping trading)
 * - SystemLogger integration for crash investigation
 * - Lifecycle state tracking for memory leak diagnosis
 *
 * BUILD #104 NOTES:
 * - Trading must continue in background (WebSockets stay active)
 * - Memory management balanced: cleanup on pause, restore on resume
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

    override fun onCreate(savedInstanceState: Bundle?) {
        SystemLogger.system("MainActivity.onCreate() - App starting")
        
        // ── Splash screen (wrapped — Samsung One UI can throw here) ──
        try {
            installSplashScreen()
        } catch (e: Exception) {
            SystemLogger.error("SplashScreen init failed (non-fatal)", e)
            Log.e(TAG, "SplashScreen init failed (non-fatal): ${e.message}")
        }

        super.onCreate(savedInstanceState)
        
        SystemLogger.system("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        SystemLogger.system("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")

        // NOTE: enableEdgeToEdge() deliberately NOT called.
        // Causes crashes on Samsung One UI. Colours set in Compose theme instead.

        // ── Check for crash report from previous run ──
        try {
            val app = application as? SovereignVantageApp
            val crashReport = app?.getCrashReport()
            if (crashReport != null) {
                SystemLogger.error("Previous crash detected")
                Log.w(TAG, "Previous crash detected — showing crash report")
                app.clearCrashReport()
                val intent = Intent(this, CrashReporterActivity::class.java)
                intent.putExtra("crash_text", crashReport)
                startActivity(intent)
                // Don't finish() — let user press back to try the app again
            }
        } catch (e: Exception) {
            SystemLogger.error("Crash report check failed", e)
            Log.e(TAG, "Crash report check failed: ${e.message}")
        }

        // ── Main Compose UI ──
        try {
            SystemLogger.system("Setting up Compose UI")
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
            SystemLogger.system("Compose UI initialized successfully")
        } catch (e: Throwable) {
            SystemLogger.error("FATAL: setContent failed", e)
            Log.e(TAG, "FATAL: setContent failed", e)
            showFallbackError(e)
        }
    }
    
    override fun onStart() {
        super.onStart()
        SystemLogger.system("MainActivity.onStart() - App visible")
    }
    
    override fun onResume() {
        super.onResume()
        SystemLogger.system("MainActivity.onResume() - App in foreground")
        SystemLogger.system("Memory: ${Runtime.getRuntime().totalMemory() / 1024 / 1024} MB total, ${Runtime.getRuntime().freeMemory() / 1024 / 1024} MB free")
    }
    
    override fun onPause() {
        super.onPause()
        SystemLogger.system("MainActivity.onPause() - App going to background")
        SystemLogger.system("Memory before GC: ${Runtime.getRuntime().totalMemory() / 1024 / 1024} MB total, ${Runtime.getRuntime().freeMemory() / 1024 / 1024} MB free")
        
        // BUILD #107: Suggest garbage collection (doesn't guarantee, but helps)
        System.gc()
        
        SystemLogger.system("Memory after GC: ${Runtime.getRuntime().totalMemory() / 1024 / 1024} MB total, ${Runtime.getRuntime().freeMemory() / 1024 / 1024} MB free")
    }
    
    override fun onStop() {
        super.onStop()
        SystemLogger.system("MainActivity.onStop() - App no longer visible")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        SystemLogger.system("MainActivity.onDestroy() - App being destroyed")
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
                    appendLine("V5.19.107 Arthur Edition")
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
            SystemLogger.error("Even fallback error display failed", e2)
            Log.e(TAG, "Even fallback error display failed", e2)
        }
    }
}
