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
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Backtesting Activity - Stubbed to Compose (V5.17.0)
 * 
 * Replaces legacy View-based activity that referenced non-existent XML layouts.
 * TODO: Wire to BacktestingEngine when clean compile achieved.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 */
class BacktestingActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BacktestingScreen()
        }
    }
}

@Composable
private fun BacktestingScreen() {
    var symbol by remember { mutableStateOf("BTC/USDT") }
    var capital by remember { mutableStateOf("10000") }
    var status by remember { mutableStateOf("Ready") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Backtesting Engine", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(value = symbol, onValueChange = { symbol = it }, label = { Text("Symbol") })
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = capital, onValueChange = { capital = it }, label = { Text("Starting Capital (AUD)") })
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = { status = "Backtesting engine not yet wired — awaiting clean compile" }) {
            Text("Run Backtest")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Status: $status")
    }
}
