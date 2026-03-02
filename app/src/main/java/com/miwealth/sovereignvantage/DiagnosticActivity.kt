package com.miwealth.sovereignvantage

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/**
 * TEMPORARY DIAGNOSTIC LAUNCHER
 *
 * Zero dependencies: no AppCompat, no Hilt, no Compose.
 * If this screen appears, the APK and framework work fine.
 * Shows startup breadcrumbs and crash info for diagnosis.
 *
 * REMOVE THIS AFTER DIAGNOSIS — restore MainActivity as launcher.
 */
class DiagnosticActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val log = StringBuilder()
        log.appendLine("=== SV V5.17.0 DIAGNOSTIC REPORT ===")
        log.appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        log.appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        log.appendLine()

        // Read breadcrumbs (try internal first, then external)
        val breadcrumbText = readFile("startup_breadcrumbs.txt")
        if (breadcrumbText != null) {
            log.appendLine("=== STARTUP BREADCRUMBS ===")
            log.appendLine(breadcrumbText)
        } else {
            log.appendLine("=== NO BREADCRUMBS ===")
            log.appendLine("Application.attachBaseContext() never ran — APK/framework issue.")
        }
        log.appendLine()

        // Read crash file
        val crashText = readFile("last_crash.txt")
        if (crashText != null) {
            log.appendLine("=== CRASH REPORT (previous run) ===")
            log.appendLine(crashText)
        } else {
            log.appendLine("No crash file found — either first launch or crash handler failed.")
        }

        log.appendLine()
        log.appendLine("=== FILE PATHS (for manual access) ===")
        log.appendLine("Internal: ${filesDir}/startup_breadcrumbs.txt")
        log.appendLine("Internal: ${filesDir}/last_crash.txt")
        try {
            val ext = getExternalFilesDir(null)
            log.appendLine("External: $ext/startup_breadcrumbs.txt")
            log.appendLine("External: $ext/last_crash.txt")
        } catch (_: Exception) { }
        log.appendLine()
        log.appendLine("Via Termux:")
        log.appendLine("  run-as com.miwealth.sovereignvantage cat files/startup_breadcrumbs.txt")
        log.appendLine("  run-as com.miwealth.sovereignvantage cat files/last_crash.txt")

        val text = log.toString()

        // Build UI
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }

        layout.addView(TextView(this).apply {
            this.text = "SV V5.17.0 Diagnostic (NO HILT)"
            textSize = 20f
            setTextColor(0xFFFFD700.toInt())
            setPadding(0, 0, 0, 8)
        })

        layout.addView(TextView(this).apply {
            this.text = "If you see this screen, the APK works.\nCopy the text below and send to Claude."
            textSize = 13f
            setTextColor(0xFF90EE90.toInt())
            setPadding(0, 0, 0, 16)
        })

        layout.addView(Button(this).apply {
            this.text = "Copy All to Clipboard"
            setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("SV Diag", text))
                android.widget.Toast.makeText(this@DiagnosticActivity, "Copied!", android.widget.Toast.LENGTH_SHORT).show()
            }
        })

        layout.addView(TextView(this).apply {
            this.text = "⚠️ HILT-FREE DIAGNOSTIC BUILD\n" +
                "Application class: MinimalApp (no DI)\n" +
                "If you see this screen, the APK is functional.\n" +
                "The crash was in Hilt/DI injection.\n" +
                "Copy the text below and send to Claude."
            textSize = 12f
            setTextColor(0xFFFF9800.toInt())
            setPadding(0, 0, 0, 16)
        })

        layout.addView(Button(this).apply {
            this.text = "Try Launch Real App (will likely crash)"
            setOnClickListener {
                try {
                    startActivity(Intent(this@DiagnosticActivity, MainActivity::class.java))
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        this@DiagnosticActivity,
                        "Launch failed: ${e.javaClass.simpleName}: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        })

        layout.addView(TextView(this).apply {
            this.text = text
            textSize = 10f
            setTextColor(0xFFE0E0E0.toInt())
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 24, 0, 0)
        })

        scrollView.addView(layout)
        scrollView.setBackgroundColor(0xFF0A0A0A.toInt())
        setContentView(scrollView)
    }

    private fun readFile(name: String): String? {
        // Try internal first
        try {
            val file = File(filesDir, name)
            if (file.exists()) return file.readText()
        } catch (_: Exception) { }

        // Try external
        try {
            val ext = getExternalFilesDir(null)
            if (ext != null) {
                val file = File(ext, name)
                if (file.exists()) return file.readText()
            }
        } catch (_: Exception) { }

        return null
    }
}
