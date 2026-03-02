package com.miwealth.sovereignvantage

import android.app.Application
import android.util.Log
import com.miwealth.sovereignvantage.core.TradingSystemManager
import com.miwealth.sovereignvantage.core.security.ExchangeCredentialManager
import com.miwealth.sovereignvantage.service.UnifiedPriceFeedService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * APPLICATION CLASS — Diagnostic Build
 *
 * © 2025-2026 MiWealth Pty Ltd
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

@HiltAndroidApp
class SovereignVantageApp : Application() {

    companion object {
        private const val TAG = "SovereignVantageApp"
        const val CRASH_FILE_NAME = "last_crash.txt"
        const val BREADCRUMB_FILE = "startup_breadcrumbs.txt"

        private val DEFAULT_WATCHLIST = listOf(
            "BTC/USD", "ETH/USD", "SOL/USD", "XRP/USD", "ADA/USD"
        )
        private const val DEFAULT_PAPER_BALANCE = 10000.0

        @Volatile
        lateinit var instance: SovereignVantageApp
            private set
    }

    private val appScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── DIAGNOSTIC: Use lazy injection instead of eager @Inject ──
    // Eager @Inject fields are created during super.onCreate() which can
    // cascade failures if any transitive dependency crashes.
    // Using lazy means they're only created when first accessed.
    @Inject lateinit var tradingSystemManager: TradingSystemManager
    @Inject lateinit var credentialManager: ExchangeCredentialManager

    private var priceFeedService: UnifiedPriceFeedService? = null
    
    // Breadcrumb logger — writes to file at each startup stage
    private lateinit var breadcrumbs: File

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        // This runs BEFORE onCreate — earliest possible point
        try {
            breadcrumbs = File(base.filesDir, BREADCRUMB_FILE)
            breadcrumbs.writeText(buildString {
                appendLine("STARTUP LOG — ${java.util.Date()}")
                appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                appendLine("[OK] attachBaseContext completed")
            })
        } catch (_: Exception) { }
        
        // Install crash handler as early as possible — BEFORE Hilt runs in onCreate
        installCrashHandler()
        try {
            if (::breadcrumbs.isInitialized) breadcrumbs.appendText("[OK] Crash handler installed in attachBaseContext\n")
        } catch (_: Exception) { }
    }

    override fun onCreate() {
        // ─── STAGE 0: Write breadcrumb ───
        appendBreadcrumb("[OK] Stage 0: onCreate() entered")

        // Crash handler already installed in attachBaseContext()
        appendBreadcrumb("[OK] Stage 1: Crash handler active (installed in attachBaseContext)")

        // ─── STAGE 2: super.onCreate() — THIS IS WHERE HILT RUNS ───
        // If Hilt fails, we SWALLOW the exception and let DiagnosticActivity
        // show the breadcrumbs. Only DiagnosticActivity works without Hilt
        // (it's a plain Activity, not @AndroidEntryPoint).
        try {
            appendBreadcrumb("[..] Stage 2: Calling super.onCreate() (Hilt injection)...")
            super.onCreate()
            appendBreadcrumb("[OK] Stage 2: super.onCreate() completed — Hilt injection succeeded")
        } catch (e: Throwable) {
            // MUST catch Throwable, not Exception — Java Error types
            // (UnsatisfiedLinkError, NoClassDefFoundError, NoSuchMethodError)
            // extend Error, not Exception. catch(Exception) misses them entirely.
            appendBreadcrumb("[FAIL] Stage 2: super.onCreate() CRASHED: ${e.javaClass.simpleName}: ${e.message}")
            appendBreadcrumb(e.stackTraceToString().take(3000))
            writeCrashToFile("super.onCreate() / Hilt injection", e)
            // Do NOT re-throw — let DiagnosticActivity show the crash info
            Log.e(TAG, "Hilt injection failed — diagnostic mode only", e)
            instance = this
            return  // Skip remaining init — only DiagnosticActivity will work
        }

        instance = this
        appendBreadcrumb("[OK] Stage 3: Instance set")

        Log.i(TAG, "== SOVEREIGN VANTAGE V5.17.0 - ARTHUR EDITION ==")

        // ─── STAGE 4: Check injected fields ───
        try {
            val tsmOk = ::tradingSystemManager.isInitialized
            val cmOk = ::credentialManager.isInitialized
            appendBreadcrumb("[OK] Stage 4: Inject check — TSM=$tsmOk, CM=$cmOk")
        } catch (e: Throwable) {
            appendBreadcrumb("[FAIL] Stage 4: Inject field check crashed: ${e.javaClass.simpleName}: ${e.message}")
        }

        // ─── STAGE 5: Async trading init (non-blocking) ───
        appScope.launch(Dispatchers.IO) {
            try {
                appendBreadcrumb("[..] Stage 5: Trading init starting (async IO)...")
                initializeTradingSystem()
                appendBreadcrumb("[OK] Stage 5: Trading init completed")
            } catch (e: Throwable) {
                appendBreadcrumb("[FAIL] Stage 5: Trading init crashed: ${e.javaClass.simpleName}: ${e.message}")
                Log.e(TAG, "Non-fatal: Trading system init failed", e)
            }
        }

        appendBreadcrumb("[OK] Stage 6: Application.onCreate() completed successfully")
    }

    private fun appendBreadcrumb(msg: String) {
        try {
            if (::breadcrumbs.isInitialized) {
                breadcrumbs.appendText("$msg\n")
            }
        } catch (_: Exception) { }
        // Also write to external (accessible via file manager)
        try {
            getExternalFilesDir(null)?.let {
                File(it, BREADCRUMB_FILE).appendText("$msg\n")
            }
        } catch (_: Exception) { }
        Log.i(TAG, "BREADCRUMB: $msg")
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                appendBreadcrumb("[CRASH] Uncaught on '${thread.name}': ${throwable.javaClass.simpleName}: ${throwable.message}")
                writeCrashToFile("UncaughtException on '${thread.name}'", throwable)
            } catch (_: Exception) { }

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashToFile(context: String, throwable: Throwable) {
        val crashText = buildString {
            appendLine("SOVEREIGN VANTAGE CRASH REPORT")
            appendLine("V5.17.0 Build 12 — Arthur Edition")
            appendLine("Time: ${java.util.Date()}")
            appendLine("Context: $context")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("=== EXCEPTION ===")
            appendLine(throwable.stackTraceToString())

            var cause = throwable.cause
            var depth = 1
            while (cause != null && depth <= 5) {
                appendLine()
                appendLine("=== CAUSE $depth ===")
                appendLine(cause.stackTraceToString())
                cause = cause.cause
                depth++
            }
        }

        try { File(filesDir, CRASH_FILE_NAME).writeText(crashText) } catch (_: Exception) { }
        try { getExternalFilesDir(null)?.let { File(it, CRASH_FILE_NAME).writeText(crashText) } } catch (_: Exception) { }
        Log.e(TAG, crashText)
    }

    fun getCrashReport(): String? {
        return try {
            val file = File(filesDir, CRASH_FILE_NAME)
            if (file.exists()) file.readText() else null
        } catch (_: Exception) { null }
    }

    fun clearCrashReport() {
        try { File(filesDir, CRASH_FILE_NAME).delete() } catch (_: Exception) { }
        try { getExternalFilesDir(null)?.let { File(it, CRASH_FILE_NAME).delete() } } catch (_: Exception) { }
    }

    private suspend fun initializeTradingSystem() {
        try {
            val savedCredentials = credentialManager.getAllCredentials()
            val paperTradingEnabled = credentialManager.isPaperTradingEnabled()

            if (paperTradingEnabled || savedCredentials.isEmpty()) {
                val balance = credentialManager.getPaperTradingBalance()
                    .takeIf { it > 0 } ?: DEFAULT_PAPER_BALANCE
                tradingSystemManager.initializePaperTrading(balance)
                    .onSuccess { subscribeToDefaultWatchlist() }
            } else {
                val preferredExchange = credentialManager.getPreferredExchange()
                tradingSystemManager.initializeWithPQC(
                    credentials = savedCredentials,
                    preferredExchange = preferredExchange
                ).onSuccess { subscribeToDefaultWatchlist() }
                 .onFailure { tradingSystemManager.initializePaperTrading(DEFAULT_PAPER_BALANCE) }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Trading init error: ${e.message}", e)
        }
    }

    private fun subscribeToDefaultWatchlist() {
        try { tradingSystemManager.subscribeToPrices(DEFAULT_WATCHLIST) } catch (_: Exception) { }
    }

    fun getPriceFeedService(): UnifiedPriceFeedService? = priceFeedService

    fun reinitializeTradingSystem() {
        appScope.launch {
            tradingSystemManager.shutdown()
            initializeTradingSystem()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        try { tradingSystemManager.shutdown() } catch (_: Exception) { }
        try { priceFeedService?.shutdown() } catch (_: Exception) { }
    }
}
