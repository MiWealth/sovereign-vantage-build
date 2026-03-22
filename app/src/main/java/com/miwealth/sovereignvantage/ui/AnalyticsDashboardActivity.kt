package com.miwealth.sovereignvantage.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Analytics Dashboard Activity - Stubbed to Compose (V5.17.0)
 * 
 * Replaces legacy View-based activity that referenced non-existent XML layouts
 * and missing AnalyticsService class.
 * TODO: Wire to PortfolioAnalytics when clean compile achieved.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 */
class AnalyticsDashboardActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnalyticsDashboardScreen()
        }
    }
}

@Composable
private fun AnalyticsDashboardScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Analytics Dashboard", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Total Return: --", style = MaterialTheme.typography.bodyLarge)
                Text("Win Rate: --", style = MaterialTheme.typography.bodyLarge)
                Text("Sharpe Ratio: --", style = MaterialTheme.typography.bodyLarge)
                Text("Sortino Ratio: --", style = MaterialTheme.typography.bodyLarge)
                Text("Max Drawdown: --", style = MaterialTheme.typography.bodyLarge)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Analytics service not yet wired — awaiting clean compile", 
             style = MaterialTheme.typography.bodySmall)
    }
}
