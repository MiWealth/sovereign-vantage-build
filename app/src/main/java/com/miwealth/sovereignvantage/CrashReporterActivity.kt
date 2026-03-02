package com.miwealth.sovereignvantage

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * CRASH REPORTER — Plain Activity (no AppCompat, no Hilt, no Compose)
 *
 * Displays crash stacktrace from previous run.
 * Uses only Android framework classes — zero external dependencies.
 *
 * © 2025-2026 MiWealth Pty Ltd
 */
class CrashReporterActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashText = intent.getStringExtra("crash_text")
            ?: "No crash data received.\n\nTo get crash logs via Termux:\nrun-as com.miwealth.sovereignvantage cat files/last_crash.txt"

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }

        layout.addView(TextView(this).apply {
            text = "\u26A0\uFE0F Sovereign Vantage — Previous Crash Report"
            textSize = 18f
            setTextColor(0xFFFFD700.toInt())
            setPadding(0, 0, 0, 16)
        })

        layout.addView(TextView(this).apply {
            text = "Copy this text and paste it to Claude for diagnosis.\nPress Back to try launching the app."
            textSize = 13f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(0, 0, 0, 16)
        })

        layout.addView(Button(this).apply {
            text = "Copy to Clipboard"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("SV Crash", crashText))
                Toast.makeText(this@CrashReporterActivity, "Copied!", Toast.LENGTH_SHORT).show()
            }
        })

        layout.addView(TextView(this).apply {
            text = crashText
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
}
