package com.miwealth.sovereignvantage

import android.app.Application
import android.util.Log
import java.io.File

/**
 * MINIMAL APPLICATION — DIAGNOSTIC BUILD
 *
 * This class has ZERO dependencies:
 * - No Hilt (@HiltAndroidApp removed)
 * - No imports of any SV module
 * - No EncryptedSharedPreferences
 * - No SQLCipher
 * - No Coroutines
 *
 * PURPOSE: Prove the APK installs and launches correctly.
 * If DiagnosticActivity shows with this Application class,
 * the crash is in Hilt/DI injection (SovereignVantageApp).
 *
 * REMOVE THIS after diagnosis — restore SovereignVantageApp.
 *
 * © 2025-2026 MiWealth Pty Ltd
 */
class MinimalApp : Application() {

    companion object {
        private const val TAG = "MinimalApp"
        const val BREADCRUMB_FILE = "startup_breadcrumbs.txt"
        const val CRASH_FILE_NAME = "last_crash.txt"

        @Volatile
        lateinit var instance: MinimalApp
            private set
    }

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        try {
            val f = File(base.filesDir, BREADCRUMB_FILE)
            f.writeText(buildString {
                appendLine("=== MINIMAL APP STARTUP ===")
                appendLine("Time: ${java.util.Date()}")
                appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                appendLine("[OK] attachBaseContext completed (NO HILT)")
            })
        } catch (e: Exception) {
            Log.e(TAG, "Breadcrumb write failed", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Install crash handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashText = buildString {
                    appendLine("MINIMAL APP CRASH REPORT")
                    appendLine("Time: ${java.util.Date()}")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
                    appendLine()
                    appendLine(throwable.stackTraceToString())
                }
                File(filesDir, CRASH_FILE_NAME).writeText(crashText)
                getExternalFilesDir(null)?.let {
                    File(it, CRASH_FILE_NAME).writeText(crashText)
                }
            } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Write success breadcrumb
        try {
            File(filesDir, BREADCRUMB_FILE).appendText(
                "[OK] MinimalApp.onCreate() completed — NO HILT, NO DI\n" +
                "[OK] DiagnosticActivity should appear now\n"
            )
        } catch (_: Exception) { }

        Log.i(TAG, "=== MINIMAL APP V5.17.0 DIAGNOSTIC — NO HILT ===")
    }
}
