package com.miwealth.sovereignvantage.ui.logs

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.miwealth.sovereignvantage.BuildConfig
import com.miwealth.sovereignvantage.core.utils.SystemLogger
import java.io.File

/**
 * SOVEREIGN VANTAGE V5.19.123 "ARTHUR EDITION"
 * SYSTEM LOGS VIEWER
 * 
 * BUILD #123: Added in-app log viewer for debugging
 * 
 * FEATURES:
 * - View all system logs in realtime
 * - Color-coded by level (DEBUG=gray, INFO=white, WARN=yellow, ERROR=red)
 * - Export logs to file
 * - Share logs via system share sheet
 * - Auto-scroll to bottom
 * 
 * © 2025-2026 MiWealth Pty Ltd
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val logs = remember { SystemLogger.getAllLogs() }
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom when logs update
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Logs (${logs.size})") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // BUILD #126: Copy All button
                    IconButton(onClick = { copyAllLogsToClipboard(context, logs) }) {
                        Icon(Icons.Filled.ContentCopy, "Copy All Logs")
                    }
                    IconButton(onClick = { exportAndShareLogs(context) }) {
                        Icon(Icons.Filled.Share, "Export & Share Logs")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF021508),
                    titleContentColor = Color(0xFFFFD700),
                    navigationIconContentColor = Color(0xFFFFD700),
                    actionIconContentColor = Color(0xFFFFD700)
                )
            )
        },
        containerColor = Color(0xFF021508)
    ) { padding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No logs yet. Start using the app to generate logs.",
                    color = Color(0xFF4ADE80),
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { log ->
                    LogEntryRow(log)
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(log: SystemLogger.LogEntry) {
    val textColor = when (log.level) {
        "E" -> Color(0xFFEF4444) // Red for errors
        "W" -> Color(0xFFFBBF24) // Yellow for warnings
        "I" -> Color(0xFFFFFFFF) // White for info
        "D" -> Color(0xFF9CA3AF) // Gray for debug
        else -> Color(0xFFFFFFFF)
    }
    
    val bgColor = when (log.level) {
        "E" -> Color(0xFF7F1D1D) // Dark red bg for errors
        "W" -> Color(0xFF78350F) // Dark yellow bg for warnings
        else -> Color(0xFF0F2419) // Dark green bg for normal
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = bgColor,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = log.format(),
            color = textColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(4.dp),
            lineHeight = 14.sp
        )
    }
}

/**
 * BUILD #126: Copy all logs to clipboard
 */
private fun copyAllLogsToClipboard(context: Context, logs: List<SystemLogger.LogEntry>) {
    try {
        val logText = buildString {
            append("SOVEREIGN VANTAGE - SYSTEM LOGS\n")
            append("Build Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
            append("Generated: ${java.util.Date()}\n")
            append("Total Entries: ${logs.size}\n")
            append("=".repeat(80) + "\n\n")
            
            logs.forEach { log ->
                append(log.format())
                append("\n")
            }
        }
        
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Sovereign Vantage Logs", logText)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(context, "📋 ${logs.size} logs copied to clipboard!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "❌ Failed to copy logs: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Export logs to file and open share sheet
 */
private fun exportAndShareLogs(context: Context) {
    try {
        val logs = SystemLogger.getAllLogs()
        val timestamp = System.currentTimeMillis()
        val filename = "sovereign_vantage_logs_$timestamp.txt"
        
        // Write to cache directory (accessible to FileProvider)
        val file = File(context.cacheDir, filename)
        file.bufferedWriter().use { writer ->
            writer.write("SOVEREIGN VANTAGE - SYSTEM LOGS\n")
            writer.write("Build Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
            writer.write("Generated: ${java.util.Date(timestamp)}\n")
            writer.write("Total Entries: ${logs.size}\n")
            writer.write("=".repeat(80) + "\n\n")
            
            logs.forEach { log ->
                writer.write(log.format())
                writer.write("\n")
            }
        }
        
        // Create shareable URI via FileProvider
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        // Open share sheet
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Sovereign Vantage System Logs")
            putExtra(Intent.EXTRA_TEXT, "System logs exported from Sovereign Vantage")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(shareIntent, "Share Logs"))
        
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private operator fun String.times(n: Int) = this.repeat(n)
