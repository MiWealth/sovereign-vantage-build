package com.miwealth.sovereignvantage.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miwealth.sovereignvantage.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    var showExchangeDialog by remember { mutableStateOf(false) }
    var showPurgeConfirmDialog by remember { mutableStateOf(false) }
    var comingSoonLabel by remember { mutableStateOf<String?>(null) }
    
    // Exchange API dialog
    if (showExchangeDialog) {
        ExchangeApiDialog(
            onDismiss = { showExchangeDialog = false },
            onSave = { exchange, apiKey, apiSecret, passphrase, isTestnet ->
                viewModel.saveExchangeCredentials(
                    exchangeId = exchange,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    passphrase = passphrase,
                    isTestnet = isTestnet,
                    testConnection = true
                )
                showExchangeDialog = false
            }
        )
    }
    
    // Purge data confirmation dialog
    if (showPurgeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmDialog = false },
            containerColor = VintageColors.EmeraldDark,
            title = {
                Text("Purge All Data?", color = VintageColors.LossRed, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will permanently delete all your:\n\n" +
                    "• Exchange API credentials\n" +
                    "• Trading history\n" +
                    "• Settings and preferences\n\n" +
                    "This action cannot be undone.",
                    color = VintageColors.TextPrimary.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.purgeAllData()
                        showPurgeConfirmDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VintageColors.LossRed)
                ) {
                    Text("PURGE ALL DATA", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmDialog = false }) {
                    Text("Cancel", color = VintageColors.TextSecondary)
                }
            }
        )
    }
    
    // Coming Soon dialog
    if (comingSoonLabel != null) {
        AlertDialog(
            onDismissRequest = { comingSoonLabel = null },
            containerColor = VintageColors.EmeraldDark,
            title = {
                Text(comingSoonLabel ?: "", color = VintageColors.Gold, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This feature is coming in a future update.",
                    color = VintageColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { comingSoonLabel = null }) {
                    Text("OK", color = VintageColors.Gold)
                }
            }
        )
    }
    
    // Connection result snackbar
    LaunchedEffect(uiState.connectionResult) {
        if (uiState.connectionResult != null) {
            // Auto-dismiss after 3 seconds
            kotlinx.coroutines.delay(3000)
            viewModel.clearConnectionResult()
        }
    }
    
    Scaffold(
        containerColor = VintageColors.EmeraldDeep,
        topBar = {
            Column {
            TopAppBar(
                title = { Text("SETTINGS", fontWeight = FontWeight.Bold, color = VintageColors.Gold, letterSpacing = 1.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = VintageColors.Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VintageColors.EmeraldDeep)
            )
            Spacer(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(brush = Brush.horizontalGradient(colors = listOf(Color.Transparent, VintageColors.GoldDark, VintageColors.Gold, VintageColors.GoldDark, Color.Transparent))))
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(VintageColors.EmeraldDeep),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Account Section
            item {
                Text(
                    "Account",
                    style = MaterialTheme.typography.titleSmall,
                    color = VintageColors.Gold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Person,
                        title = "Profile",
                        subtitle = "Manage your account details",
                        onClick = { comingSoonLabel = "Profile" }
                    )
                    HorizontalDivider(color = VintageColors.EmeraldDeep)
                    SettingsItem(
                        icon = Icons.Default.Wallet,
                        title = "Connected Wallets",
                        subtitle = "3 wallets connected",
                        onClick = { comingSoonLabel = "Connected Wallets" }
                    )
                    HorizontalDivider(color = VintageColors.EmeraldDeep)
                    SettingsItem(
                        icon = Icons.Default.Star,
                        title = "Subscription",
                        subtitle = "ELITE Tier - Active",
                        onClick = { comingSoonLabel = "Subscription" }
                    )
                }
            }
            
            // System Status Section - NEW in v5.5.73
            item {
                Text(
                    "System Status",
                    style = MaterialTheme.typography.titleSmall,
                    color = VintageColors.Gold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SystemStatusCard(
                    uiState = uiState,
                    onRunDiscovery = { viewModel.runAssetDiscovery() },
                    onSetPaperTradingDataSource = { source -> viewModel.setPaperTradingDataSource(source) }
                )
            }
            
            // Security Section
            item {
                Text(
                    "Security",
                    style = MaterialTheme.typography.titleSmall,
                    color = VintageColors.Gold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SettingsCard {
                    SettingsToggleItem(
                        icon = Icons.Default.Fingerprint,
                        title = "Biometric Login",
                        subtitle = "Use fingerprint or face to login",
                        checked = uiState.biometricEnabled,
                        onCheckedChange = { viewModel.setBiometricEnabled(it) }
                    )
                    HorizontalDivider(color = VintageColors.EmeraldDeep)
                    SettingsItem(
                        icon = Icons.Default.Lock,
                        title = "Change Password",
                        subtitle = "Update your password",
                        onClick = { comingSoonLabel = "Change Password" }
                    )
                    HorizontalDivider(color = VintageColors.EmeraldDeep)
                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = "Two-Factor Auth",
                        subtitle = "Enabled",
                        onClick = { comingSoonLabel = "Two-Factor Auth" }
                    )
                }
            }
            
            // Trading Section
            item {
                Text(
                    "Trading",
                    style = MaterialTheme.typography.titleSmall,
                    color = VintageColors.Gold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Key,
                        title = "Exchange API Keys",
                        subtitle = "Connect to Kraken, Coinbase, Binance...",
                        onClick = { showExchangeDialog = true }
                    )
                    HorizontalDivider(color = VintageColors.EmeraldDeep)
                    SettingsItem(
                        icon = Icons.Default.TrendingUp,
                        title = "Risk Settings",
                        subtitle = "Maximum Aggression",
                        onClick = { comingSoonLabel = "Risk Settings" }
                    )
                    HorizontalDivider(color = VintageColors.EmeraldDeep)
                    SettingsItem(
                        icon = Icons.Default.Shield,
                        title = "STAHL Stair Stop™",
                        subtitle = "Configure profit protection levels",
                        onClick = { comingSoonLabel = "STAHL Stair Stop™" }
                    )
                    HorizontalDivider(color = VintageColors.EmeraldDeep)
                    SettingsItem(
                        icon = Icons.Default.SmartToy,
                        title = "AI Trading Engine",
                        subtitle = "5 strategies active",
                        onClick = { comingSoonLabel = "AI Trading Engine" }
                    )
                }
            }
            
            // V5.17.0: Trading Mode & HYBRID Configuration
            item {
                Text(
                    "Operating Mode",
                    style = MaterialTheme.typography.titleSmall,
                    color = VintageColors.Gold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                TradingModeCard(
                    currentMode = uiState.tradingMode,
                    dailyLossLimit = uiState.dailyLossLimit,
                    hybridAutoThreshold = uiState.hybridAutoExecuteThreshold,
                    hybridConfirmBelow = uiState.hybridRequireConfirmationBelow,
                    hybridMaxAutoTrades = uiState.hybridMaxAutoTradesPerHour,
                    hybridValueThreshold = uiState.hybridAbsoluteValueThreshold,
                    onModeChange = { viewModel.setTradingMode(it) },
                    onAutoThresholdChange = { viewModel.setHybridAutoExecuteThreshold(it) },
                    onConfirmBelowChange = { viewModel.setHybridConfirmationThreshold(it) },
                    onMaxAutoTradesChange = { viewModel.setHybridMaxAutoTrades(it) },
                    onValueThresholdChange = { viewModel.setHybridValueThreshold(it) }
                )
            }
            
            // NEW V5.17.0: Advanced Strategies Section
            item {
                Text(
                    "Advanced Strategies",
                    style = MaterialTheme.typography.titleSmall,
                    color = VintageColors.Gold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                AdvancedStrategiesCard(
                    uiState = uiState,
                    onAlphaScannerEnabledChange = { viewModel.setAlphaScannerEnabled(it) },
                    onFundingArbEnabledChange = { viewModel.setFundingArbEnabled(it) },
                    onAlphaScannerIntervalChange = { viewModel.setAlphaScannerInterval(it) },
                    onFundingArbMinRateChange = { viewModel.setFundingArbMinRate(it) },
                    onFundingArbMaxCapitalChange = { viewModel.setFundingArbMaxCapital(it) },
                    onDailyLossLimitChange = { viewModel.setDailyLossLimit(it) }
                )
            }
            
            // Preferences Section
            item {
                Text(
                    "Preferences",
                    style = MaterialTheme.typography.titleSmall,
                    color = VintageColors.Gold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SettingsCard {
                    SettingsToggleItem(
                        icon = Icons.Default.Notifications,
                        title = "Notifications",
                        subtitle = "Trade alerts and updates",
                        checked = uiState.notificationsEnabled,
                        onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                    )
                    HorizontalDivider(color = VintageColors.EmeraldDeep)
                    SettingsToggleItem(
                        icon = Icons.Default.DarkMode,
                        title = "Dark Mode",
                        subtitle = "Use dark theme",
                        checked = uiState.darkModeEnabled,
                        onCheckedChange = { viewModel.setDarkModeEnabled(it) }
                    )
                    HorizontalDivider(color = VintageColors.EmeraldDeep)
                    SettingsItem(
                        icon = Icons.Default.Language,
                        title = "Language",
                        subtitle = "English",
                        onClick = { comingSoonLabel = "Language" }
                    )
                }
            }
            
            // Support Section
            item {
                Text(
                    "Support",
                    style = MaterialTheme.typography.titleSmall,
                    color = VintageColors.Gold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Help,
                        title = "Help Center",
                        subtitle = "FAQs and guides",
                        onClick = { comingSoonLabel = "Help Center" }
                    )
                    HorizontalDivider(color = VintageColors.EmeraldDeep)
                    SettingsItem(
                        icon = Icons.Default.Email,
                        title = "Contact Support",
                        subtitle = "Get help from our team",
                        onClick = { comingSoonLabel = "Contact Support" }
                    )
                    HorizontalDivider(color = VintageColors.EmeraldDeep)
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "About",
                        subtitle = "V5.17.0 Arthur Edition — Build 13",
                        onClick = { comingSoonLabel = "About" }
                    )
                }
            }
            
            // BUILD #123: Diagnostic Logs Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                var showLogs by remember { mutableStateOf(false) }
                var logContent by remember { mutableStateOf("") }
                
                Button(
                    onClick = {
                        try {
                            val process = Runtime.getRuntime().exec("logcat -d -v time *:D")
                            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                            val logs = reader.readText()
                            logContent = logs.takeLast(50000) // Last 50KB
                            showLogs = true
                        } catch (e: Exception) {
                            logContent = "Error reading logs: ${e.message}"
                            showLogs = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                        .border(1.dp, VintageColors.GoldDark.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VintageColors.EmeraldDeep.copy(alpha = 0.3f),
                        contentColor = VintageColors.GoldBright
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Diagnostic Logs", fontWeight = FontWeight.Bold)
                }
                
                if (showLogs) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showLogs = false }
                    ) {
                        androidx.compose.material3.Surface(
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
                            color = VintageColors.EmeraldBlack,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Diagnostic Logs",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = VintageColors.GoldBright
                                    )
                                    IconButton(onClick = { showLogs = false }) {
                                        Icon(Icons.Default.Close, "Close", tint = VintageColors.GoldDark)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                        .background(
                                            VintageColors.EmeraldDeep.copy(alpha = 0.3f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp)
                                ) {
                                    androidx.compose.foundation.lazy.LazyColumn {
                                        item {
                                            androidx.compose.material3.Text(
                                                logContent,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    fontSize = 10.sp
                                                ),
                                                color = VintageColors.TextSecondary
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val file = java.io.File(context.getExternalFilesDir(null), "sv_logs_${System.currentTimeMillis()}.txt")
                                        file.writeText(logContent)
                                        android.widget.Toast.makeText(context, "Logs saved to ${file.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Save, "Save")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Save Logs to File")
                                }
                            }
                        }
                    }
                }
            }
            
            // Logout Button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.logout()  // Clear session token
                        onLogout()          // Navigate to login
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                        .border(1.dp, VintageColors.GoldDark.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VintageColors.LossRed.copy(alpha = 0.2f),
                        contentColor = VintageColors.LossRed
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            }
            
            // Version Info
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Sovereign Vantage: Arthur Edition\nVersion 5.7.0 Build 13\n© 2025-2026 MiWealth Pty Ltd",
                    style = MaterialTheme.typography.bodySmall,
                    color = VintageColors.TextMuted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().border(0.5.dp, VintageColors.GoldDark.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = VintageColors.Gold,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = VintageColors.TextPrimary
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = VintageColors.TextTertiary
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = VintageColors.TextMuted
        )
    }
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = VintageColors.Gold,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = VintageColors.TextPrimary
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = VintageColors.TextTertiary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VintageColors.Gold,
                checkedTrackColor = VintageColors.Gold.copy(alpha = 0.3f)
            )
        )
    }
}

// ============================================================================
// SYSTEM STATUS CARD - Pipeline & Discovery Status (v5.5.73)
// ============================================================================

@Composable
fun SystemStatusCard(
    uiState: SettingsUiState,
    onRunDiscovery: () -> Unit,
    onSetPaperTradingDataSource: (PaperTradingDataSource) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Execution Mode Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = VintageColors.Gold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Execution Mode",
                        style = MaterialTheme.typography.bodyLarge,
                        color = VintageColors.TextPrimary
                    )
                }
                
                ExecutionModeBadgeSettings(
                    mode = uiState.executionMode,
                    isAI = uiState.isUsingAIIntegration
                )
            }
            
            HorizontalDivider(color = VintageColors.EmeraldDeep)
            
            // V5.17.0: Paper Trading Data Source Selector
            if (uiState.executionMode == "PAPER" || uiState.executionMode == "PAPER_WITH_LIVE_DATA") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Timeline,
                            contentDescription = null,
                            tint = VintageColors.Gold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Price Data Source",
                                style = MaterialTheme.typography.bodyLarge,
                                color = VintageColors.TextPrimary
                            )
                            Text(
                                when (uiState.paperTradingDataSource) {
                                    PaperTradingDataSource.MOCK -> "Simulated random walk (offline)"
                                    PaperTradingDataSource.LIVE -> "Live exchange prices (Binance)"
                                    PaperTradingDataSource.BACKTEST -> "Historical replay"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = VintageColors.TextTertiary
                            )
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaperTradingDataSource.entries.forEach { source ->
                        val isSelected = uiState.paperTradingDataSource == source
                        val label = when (source) {
                            PaperTradingDataSource.MOCK -> "Mock"
                            PaperTradingDataSource.LIVE -> "Live"
                            PaperTradingDataSource.BACKTEST -> "Backtest"
                        }
                        
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSetPaperTradingDataSource(source) },
                            label = { 
                                Text(
                                    label.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    letterSpacing = 1.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VintageColors.Gold,
                                selectedLabelColor = VintageColors.EmeraldDeep,
                                containerColor = VintageColors.EmeraldDeep,
                                labelColor = VintageColors.TextSecondary
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) VintageColors.Gold else VintageColors.EmeraldDark
                            )
                        )
                    }
                }
                
                HorizontalDivider(color = VintageColors.EmeraldDeep)
            }
            
            // Asset Discovery Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = VintageColors.Gold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Asset Discovery",
                            style = MaterialTheme.typography.bodyLarge,
                            color = VintageColors.TextPrimary
                        )
                        Text(
                            "Discovered: ${uiState.discoveredAssets} • Registered: ${uiState.registeredAssets}",
                            style = MaterialTheme.typography.bodySmall,
                            color = VintageColors.TextTertiary
                        )
                    }
                }
                
                PipelineStatusBadge(status = uiState.pipelineStatus)
            }
            
            // Progress indicator when running
            if (uiState.isRunningDiscovery) {
                Column {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = VintageColors.Gold,
                        trackColor = VintageColors.EmeraldDeep
                    )
                    uiState.currentDiscoveryAsset?.let { asset ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Processing: $asset",
                            style = MaterialTheme.typography.bodySmall,
                            color = VintageColors.TextTertiary
                        )
                    }
                }
            }
            
            // Last discovery time
            uiState.lastDiscoveryTime?.let { time ->
                Text(
                    "Last run: $time",
                    style = MaterialTheme.typography.bodySmall,
                    color = VintageColors.TextMuted
                )
            }
            
            // Run Discovery Button
            Button(
                onClick = onRunDiscovery,
                modifier = Modifier.fillMaxWidth()
                    .border(1.dp, VintageColors.GoldDark, RoundedCornerShape(8.dp)),
                enabled = !uiState.isRunningDiscovery,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VintageColors.Gold.copy(alpha = 0.2f),
                    contentColor = VintageColors.Gold,
                    disabledContainerColor = VintageColors.EmeraldDeep,
                    disabledContentColor = VintageColors.TextMuted
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (uiState.isRunningDiscovery) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = VintageColors.TextTertiary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Discovering...")
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run Discovery", fontWeight = FontWeight.Medium)
                }
            }
            
            HorizontalDivider(color = VintageColors.EmeraldDeep)
            
            // Connected Exchanges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        tint = VintageColors.Gold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Connected Exchanges",
                            style = MaterialTheme.typography.bodyLarge,
                            color = VintageColors.TextPrimary
                        )
                        if (uiState.connectedExchangeNames.isNotEmpty()) {
                            Text(
                                uiState.connectedExchangeNames.joinToString(" • ") { it.replaceFirstChar { c -> c.uppercase() } },
                                style = MaterialTheme.typography.bodySmall,
                                color = VintageColors.TextTertiary
                            )
                        } else {
                            Text(
                                "No exchanges connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = VintageColors.TextMuted
                            )
                        }
                    }
                }
                
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (uiState.connectedExchangeCount > 0) VintageColors.ProfitGreen.copy(alpha = 0.2f) else VintageColors.EmeraldDeep
                ) {
                    Text(
                        text = "${uiState.connectedExchangeCount} active",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (uiState.connectedExchangeCount > 0) VintageColors.ProfitGreen else VintageColors.TextPrimary.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun ExecutionModeBadgeSettings(
    mode: String,
    isAI: Boolean
) {
    val (backgroundColor, textColor, label) = when {
        mode == "PAPER" -> Triple(
            VintageColors.TextSecondary.copy(alpha = 0.2f),
            VintageColors.TextSecondary,
            "📝 PAPER"
        )
        mode == "LIVE_AI" || isAI -> Triple(
            VintageColors.ProfitGreen.copy(alpha = 0.2f),
            VintageColors.ProfitGreen,
            "🤖 LIVE AI"
        )
        else -> Triple(
            VintageColors.Gold.copy(alpha = 0.2f),
            VintageColors.Gold,
            "⚡ LIVE"
        )
    }
    
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
fun PipelineStatusBadge(status: PipelineStatusUi) {
    val (backgroundColor, textColor, label) = when (status) {
        PipelineStatusUi.IDLE -> Triple(
            VintageColors.EmeraldDeep,
            VintageColors.TextPrimary.copy(alpha = 0.5f),
            "Idle"
        )
        PipelineStatusUi.DISCOVERING -> Triple(
            VintageColors.Gold.copy(alpha = 0.2f),
            VintageColors.Gold,
            "Discovering..."
        )
        PipelineStatusUi.ENRICHING -> Triple(
            VintageColors.EmeraldAccent.copy(alpha = 0.2f),
            VintageColors.EmeraldAccent,
            "Enriching..."
        )
        PipelineStatusUi.ASSIGNING -> Triple(
            VintageColors.Gold.copy(alpha = 0.2f),
            VintageColors.Gold,
            "Assigning..."
        )
        PipelineStatusUi.REGISTERING -> Triple(
            VintageColors.GoldDark.copy(alpha = 0.2f),
            VintageColors.GoldDark,
            "Registering..."
        )
        PipelineStatusUi.COMPLETE -> Triple(
            VintageColors.ProfitGreen.copy(alpha = 0.2f),
            VintageColors.ProfitGreen,
            "✓ Complete"
        )
        PipelineStatusUi.ERROR -> Triple(
            VintageColors.LossRed.copy(alpha = 0.2f),
            VintageColors.LossRed,
            "✗ Error"
        )
    }
    
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = backgroundColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

// ============================================================================
// EXCHANGE API DIALOG
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeApiDialog(
    onDismiss: () -> Unit,
    onSave: (exchange: String, apiKey: String, apiSecret: String, passphrase: String?, isTestnet: Boolean) -> Unit
) {
    var selectedExchange by remember { mutableStateOf("binance") }
    var apiKey by remember { mutableStateOf("") }
    var apiSecret by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var isTestnet by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var showApiSecret by remember { mutableStateOf(false) }
    var showPassphrase by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    
    // Exchanges with testnet indicator
    val exchanges = listOf(
        Triple("binance", "Binance", true),
        Triple("bybit", "Bybit", true),
        Triple("coinbase", "Coinbase", true),
        Triple("kraken", "Kraken", false),
        Triple("okx", "OKX", false),
        Triple("kucoin", "KuCoin", true),
        Triple("gateio", "Gate.io", false),
        Triple("mexc", "MEXC", false),
        Triple("bitget", "Bitget", false),
        Triple("htx", "HTX (Huobi)", false),
        Triple("gemini", "Gemini", true),
        Triple("uphold", "Uphold", true)
    )
    
    // Exchanges requiring passphrase
    val needsPassphrase = selectedExchange in listOf("coinbase", "kucoin")
    val selectedHasTestnet = exchanges.find { it.first == selectedExchange }?.third ?: false
    
    // Reset testnet toggle when switching to exchange without testnet
    LaunchedEffect(selectedExchange) {
        if (!selectedHasTestnet) isTestnet = false
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VintageColors.EmeraldDark,
        title = {
            Text(
                "Connect Exchange",
                color = VintageColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Exchange selector
                Text(
                    "Select Exchange",
                    style = MaterialTheme.typography.labelMedium,
                    color = VintageColors.Gold
                )
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = exchanges.find { it.first == selectedExchange }?.second ?: "",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VintageColors.TextPrimary,
                            unfocusedTextColor = VintageColors.TextPrimary,
                            focusedBorderColor = VintageColors.Gold,
                            unfocusedBorderColor = VintageColors.TextMuted
                        )
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        exchanges.forEach { (id, name, hasTestnet) ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(name)
                                        if (hasTestnet) {
                                            Text(
                                                "TESTNET",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = StatusGreen,
                                                modifier = Modifier
                                                    .background(
                                                        StatusGreen.copy(alpha = 0.15f),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedExchange = id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                // Testnet toggle (only shown for exchanges that support it)
                if (selectedHasTestnet) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Testnet / Sandbox",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VintageColors.TextPrimary
                            )
                            Text(
                                "Use test environment (no real funds)",
                                style = MaterialTheme.typography.bodySmall,
                                color = VintageColors.TextTertiary
                            )
                        }
                        Switch(
                            checked = isTestnet,
                            onCheckedChange = { isTestnet = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VintageColors.EmeraldDeep,
                                checkedTrackColor = StatusGreen,
                                uncheckedThumbColor = VintageColors.TextPrimary.copy(alpha = 0.5f),
                                uncheckedTrackColor = VintageColors.TextPrimary.copy(alpha = 0.1f)
                            )
                        )
                    }
                    
                    if (isTestnet) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = StatusGreen.copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "⚠ Testnet mode — orders will execute against the sandbox environment. " +
                                "Testnet API keys are different from production keys.",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusGreen.copy(alpha = 0.9f),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                
                // API Key input
                Text(
                    if (isTestnet) "Testnet API Key" else "API Key",
                    style = MaterialTheme.typography.labelMedium,
                    color = VintageColors.Gold
                )
                
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter your API key", color = VintageColors.TextMuted) },
                    visualTransformation = if (showApiKey) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiKey) "Hide" else "Show",
                                tint = VintageColors.TextPrimary.copy(alpha = 0.5f)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VintageColors.TextPrimary,
                        unfocusedTextColor = VintageColors.TextPrimary,
                        focusedBorderColor = VintageColors.Gold,
                        unfocusedBorderColor = VintageColors.TextMuted
                    ),
                    singleLine = true
                )
                
                // API Secret input
                Text(
                    if (isTestnet) "Testnet API Secret" else "API Secret",
                    style = MaterialTheme.typography.labelMedium,
                    color = VintageColors.Gold
                )
                
                OutlinedTextField(
                    value = apiSecret,
                    onValueChange = { apiSecret = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter your API secret", color = VintageColors.TextMuted) },
                    visualTransformation = if (showApiSecret) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showApiSecret = !showApiSecret }) {
                            Icon(
                                if (showApiSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiSecret) "Hide" else "Show",
                                tint = VintageColors.TextPrimary.copy(alpha = 0.5f)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VintageColors.TextPrimary,
                        unfocusedTextColor = VintageColors.TextPrimary,
                        focusedBorderColor = VintageColors.Gold,
                        unfocusedBorderColor = VintageColors.TextMuted
                    ),
                    singleLine = true
                )
                
                // Passphrase input (only for Coinbase, KuCoin)
                if (needsPassphrase) {
                    Text(
                        "API Passphrase",
                        style = MaterialTheme.typography.labelMedium,
                        color = VintageColors.Gold
                    )
                    
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter your API passphrase", color = VintageColors.TextMuted) },
                        visualTransformation = if (showPassphrase) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                Icon(
                                    if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassphrase) "Hide" else "Show",
                                    tint = VintageColors.TextPrimary.copy(alpha = 0.5f)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = VintageColors.TextPrimary,
                            unfocusedTextColor = VintageColors.TextPrimary,
                            focusedBorderColor = VintageColors.Gold,
                            unfocusedBorderColor = VintageColors.TextMuted
                        ),
                        singleLine = true
                    )
                }
                
                // Security note
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = VintageColors.Gold.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = VintageColors.Gold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Your API credentials are encrypted locally using post-quantum cryptography (Kyber-1024). They never leave your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VintageColors.TextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            val canSave = apiKey.isNotBlank() && apiSecret.isNotBlank() &&
                          (!needsPassphrase || passphrase.isNotBlank())
            Button(
                onClick = { 
                    if (canSave) {
                        onSave(
                            selectedExchange,
                            apiKey,
                            apiSecret,
                            passphrase.ifBlank { null },
                            isTestnet
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTestnet) StatusGreen else VintageColors.Gold
                ),
                enabled = canSave
            ) {
                Text(
                    if (isTestnet) "CONNECT (TESTNET)" else "CONNECT",
                    color = VintageColors.EmeraldDeep,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VintageColors.TextSecondary)
            }
        }
    )
}

// =============================================================================
// TRADING MODE CARD - V5.17.0
// =============================================================================

@Composable
fun TradingModeCard(
    currentMode: String,
    dailyLossLimit: Double,
    hybridAutoThreshold: Double,
    hybridConfirmBelow: Double,
    hybridMaxAutoTrades: Int,
    hybridValueThreshold: Double,
    onModeChange: (String) -> Unit,
    onAutoThresholdChange: (Double) -> Unit,
    onConfirmBelowChange: (Double) -> Unit,
    onMaxAutoTradesChange: (Int) -> Unit,
    onValueThresholdChange: (Double) -> Unit
) {
    val modes = listOf(
        Triple("PAPER", "Paper Trading", "Practice with virtual funds — no real money at risk"),
        Triple("SIGNAL_ONLY", "Signal Only", "AI suggests, you confirm every trade"),
        Triple("HYBRID", "Hybrid", "Auto-execute high-confidence, confirm the rest"),
        Triple("AUTONOMOUS", "Autonomous", "AI decides and executes automatically"),
        Triple("SCALPING", "Scalping", "High-frequency with ultra-tight stops"),
        Triple("ALPHA_SCANNER", "Alpha Scanner", "Multi-factor quantitative ranking")
    )
    
    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = VintageColors.Gold,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Operating Mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = VintageColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Mode selector
            modes.forEach { (modeId, displayName, description) ->
                val isSelected = currentMode == modeId
                val borderColor = if (isSelected) VintageColors.Gold else VintageColors.EmeraldDeep
                val bgColor = if (isSelected) VintageColors.EmeraldDeep.copy(alpha = 0.8f) else VintageColors.EmeraldDeep.copy(alpha = 0.3f)
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = bgColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onModeChange(modeId) },
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = borderColor
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onModeChange(modeId) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = VintageColors.Gold,
                                unselectedColor = VintageColors.TextPrimary.copy(alpha = 0.5f)
                            )
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) VintageColors.Gold else VintageColors.TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                description,
                                style = MaterialTheme.typography.bodySmall,
                                color = VintageColors.TextSecondary
                            )
                        }
                    }
                }
            }
            
            // HYBRID configuration — only show when HYBRID mode selected
            if (currentMode == "HYBRID") {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = VintageColors.Gold.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "HYBRID MODE SETTINGS",
                    style = MaterialTheme.typography.labelLarge,
                    color = VintageColors.Gold,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Auto-execute threshold
                Text(
                    "Auto-execute above: ${hybridAutoThreshold.toInt()}% confidence",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VintageColors.TextPrimary
                )
                Slider(
                    value = hybridAutoThreshold.toFloat(),
                    onValueChange = { onAutoThresholdChange(it.toDouble()) },
                    valueRange = 50f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = VintageColors.Gold,
                        activeTrackColor = VintageColors.Gold
                    )
                )
                
                // Require confirmation below
                Text(
                    "Always confirm below: ${hybridConfirmBelow.toInt()}% confidence",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VintageColors.TextPrimary
                )
                Slider(
                    value = hybridConfirmBelow.toFloat(),
                    onValueChange = { onConfirmBelowChange(it.toDouble()) },
                    valueRange = 0f..95f,
                    colors = SliderDefaults.colors(
                        thumbColor = VintageColors.Gold,
                        activeTrackColor = VintageColors.Gold
                    )
                )
                
                // Max auto-trades per hour
                Text(
                    "Max auto-trades/hour: $hybridMaxAutoTrades",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VintageColors.TextPrimary
                )
                Slider(
                    value = hybridMaxAutoTrades.toFloat(),
                    onValueChange = { onMaxAutoTradesChange(it.toInt()) },
                    valueRange = 1f..20f,
                    steps = 18,
                    colors = SliderDefaults.colors(
                        thumbColor = VintageColors.Gold,
                        activeTrackColor = VintageColors.Gold
                    )
                )
                
                // Value threshold (AU$)
                Text(
                    "Confirm trades above: AU$${"%,.0f".format(hybridValueThreshold)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VintageColors.TextPrimary
                )
                Slider(
                    value = hybridValueThreshold.toFloat(),
                    onValueChange = { onValueThresholdChange(it.toDouble()) },
                    valueRange = 100f..100_000f,
                    colors = SliderDefaults.colors(
                        thumbColor = VintageColors.Gold,
                        activeTrackColor = VintageColors.Gold
                    )
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Trades between ${hybridConfirmBelow.toInt()}% and ${hybridAutoThreshold.toInt()}% confidence " +
                    "will auto-execute UNLESS the trade value exceeds AU$${"%,.0f".format(hybridValueThreshold)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VintageColors.TextTertiary
                )
            }
            
            // Safety warning for AUTONOMOUS mode
            if (currentMode == "AUTONOMOUS") {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "⚠️ AUTONOMOUS MODE: The AI will execute all trades without confirmation. " +
                    "The ${dailyLossLimit.toInt()}% daily loss kill switch still applies. Ensure your risk settings are correct.",
                    style = MaterialTheme.typography.bodySmall,
                    color = VintageColors.LossRed.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// =============================================================================
// ADVANCED STRATEGIES CARD - V5.17.0
// =============================================================================

@Composable
fun AdvancedStrategiesCard(
    uiState: SettingsUiState,
    onAlphaScannerEnabledChange: (Boolean) -> Unit,
    onFundingArbEnabledChange: (Boolean) -> Unit,
    onAlphaScannerIntervalChange: (Int) -> Unit,
    onFundingArbMinRateChange: (Double) -> Unit,
    onFundingArbMaxCapitalChange: (Double) -> Unit,
    onDailyLossLimitChange: (Double) -> Unit
) {
    var showAlphaScannerDetails by remember { mutableStateOf(false) }
    var showFundingArbDetails by remember { mutableStateOf(false) }
    var showRiskDetails by remember { mutableStateOf(false) }
    
    SettingsCard {
        // Alpha Factor Scanner
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.QueryStats,
                        contentDescription = null,
                        tint = VintageColors.Gold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Alpha Factor Scanner",
                            style = MaterialTheme.typography.titleSmall,
                            color = VintageColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Quantitative asset ranking",
                            style = MaterialTheme.typography.bodySmall,
                            color = VintageColors.TextSecondary
                        )
                    }
                }
                Switch(
                    checked = uiState.alphaScannerEnabled,
                    onCheckedChange = onAlphaScannerEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VintageColors.Gold,
                        checkedTrackColor = VintageColors.Gold.copy(alpha = 0.5f)
                    )
                )
            }
            
            // Expand/collapse details
            if (uiState.alphaScannerEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showAlphaScannerDetails = !showAlphaScannerDetails }
                ) {
                    Text(
                        if (showAlphaScannerDetails) "Hide Settings" else "Show Settings",
                        color = VintageColors.Gold.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Icon(
                        if (showAlphaScannerDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = VintageColors.Gold.copy(alpha = 0.8f)
                    )
                }
                
                if (showAlphaScannerDetails) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Scan interval slider
                    Text(
                        "Scan Interval: ${uiState.alphaScannerInterval} minutes",
                        style = MaterialTheme.typography.bodySmall,
                        color = VintageColors.TextPrimary.copy(alpha = 0.8f)
                    )
                    Slider(
                        value = uiState.alphaScannerInterval.toFloat(),
                        onValueChange = { onAlphaScannerIntervalChange(it.toInt()) },
                        valueRange = 15f..240f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = VintageColors.Gold,
                            activeTrackColor = VintageColors.Gold
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Top N assets
                    Text(
                        "Track Top: ${uiState.alphaScannerTopN} assets",
                        style = MaterialTheme.typography.bodySmall,
                        color = VintageColors.TextPrimary.copy(alpha = 0.8f)
                    )
                    
                    // Factor weights info
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = VintageColors.EmeraldDeep.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Factor Weights:",
                                style = MaterialTheme.typography.labelMedium,
                                color = VintageColors.Gold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "• Momentum: 35%\n• Quality: 25%\n• Trend: 25%\n• Volatility: 15%",
                                style = MaterialTheme.typography.bodySmall,
                                color = VintageColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }
        
        HorizontalDivider(color = VintageColors.EmeraldDeep)
        
        // Delta-Neutral Funding Arbitrage
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = VintageColors.ProfitGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Funding Arbitrage",
                            style = MaterialTheme.typography.titleSmall,
                            color = VintageColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Delta-neutral funding printer",
                            style = MaterialTheme.typography.bodySmall,
                            color = VintageColors.TextSecondary
                        )
                    }
                }
                Switch(
                    checked = uiState.fundingArbEnabled,
                    onCheckedChange = onFundingArbEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VintageColors.ProfitGreen,
                        checkedTrackColor = VintageColors.ProfitGreen.copy(alpha = 0.5f)
                    )
                )
            }
            
            if (uiState.fundingArbEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showFundingArbDetails = !showFundingArbDetails }
                ) {
                    Text(
                        if (showFundingArbDetails) "Hide Settings" else "Show Settings",
                        color = VintageColors.ProfitGreen.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Icon(
                        if (showFundingArbDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = VintageColors.ProfitGreen.copy(alpha = 0.8f)
                    )
                }
                
                if (showFundingArbDetails) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Min funding rate
                    Text(
                        "Min Rate to Enter: ${String.format("%.3f", uiState.fundingArbMinRate)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = VintageColors.TextPrimary.copy(alpha = 0.8f)
                    )
                    Slider(
                        value = (uiState.fundingArbMinRate * 100).toFloat(),
                        onValueChange = { onFundingArbMinRateChange(it.toDouble() / 100) },
                        valueRange = 0.5f..10f,
                        colors = SliderDefaults.colors(
                            thumbColor = VintageColors.ProfitGreen,
                            activeTrackColor = VintageColors.ProfitGreen
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Max capital allocation
                    Text(
                        "Max Capital: ${uiState.fundingArbMaxCapital.toInt()}% of portfolio",
                        style = MaterialTheme.typography.bodySmall,
                        color = VintageColors.TextPrimary.copy(alpha = 0.8f)
                    )
                    Slider(
                        value = uiState.fundingArbMaxCapital.toFloat(),
                        onValueChange = { onFundingArbMaxCapitalChange(it.toDouble()) },
                        valueRange = 10f..80f,
                        colors = SliderDefaults.colors(
                            thumbColor = VintageColors.ProfitGreen,
                            activeTrackColor = VintageColors.ProfitGreen
                        )
                    )
                    
                    // How it works
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = VintageColors.ProfitGreen.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "How It Works:",
                                style = MaterialTheme.typography.labelMedium,
                                color = VintageColors.ProfitGreen
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "LONG spot + SHORT perp = Δ0\nCollect 8-hour funding payments\nPotential 10-110% APY",
                                style = MaterialTheme.typography.bodySmall,
                                color = VintageColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }
        
        HorizontalDivider(color = VintageColors.EmeraldDeep)
        
        // Risk Controls (HARD LIMITS)
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = VintageColors.LossRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Risk Controls",
                            style = MaterialTheme.typography.titleSmall,
                            color = VintageColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Kill switch at ${uiState.dailyLossLimit.toInt()}% daily loss",
                            style = MaterialTheme.typography.bodySmall,
                            color = VintageColors.LossRed.copy(alpha = 0.8f)
                        )
                    }
                }
                IconButton(onClick = { showRiskDetails = !showRiskDetails }) {
                    Icon(
                        if (showRiskDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = VintageColors.TextPrimary.copy(alpha = 0.6f)
                    )
                }
            }
            
            if (showRiskDetails) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Kill switch - FIXED (Cannot be changed)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = VintageColors.LossRed.copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "KILL SWITCH",
                                style = MaterialTheme.typography.labelMedium,
                                color = VintageColors.LossRed,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Liquidate ALL to USDT",
                                style = MaterialTheme.typography.bodySmall,
                                color = VintageColors.TextSecondary
                            )
                        }
                        Text(
                            "${uiState.dailyLossLimit.toInt()}%",
                            style = MaterialTheme.typography.headlineSmall,
                            color = VintageColors.LossRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Daily loss limit (adjustable)
                Text(
                    "Daily Loss Halt: ${uiState.dailyLossLimit.toInt()}%",  // BUILD #114: Show as integer
                    style = MaterialTheme.typography.bodySmall,
                    color = VintageColors.TextPrimary.copy(alpha = 0.8f)
                )
                Slider(
                    value = uiState.dailyLossLimit.toFloat(),
                    onValueChange = { onDailyLossLimitChange(it.toDouble()) },
                    valueRange = 0f..100f,  // BUILD #114: Changed from 5f..100f to allow 0%
                    colors = SliderDefaults.colors(
                        thumbColor = VintageColors.LossRed,
                        activeTrackColor = VintageColors.LossRed
                    )
                )
                
                // Warning
                Text(
                    "⚠️ Kill switch activates when daily loss exceeds ${uiState.dailyLossLimit.toInt()}%. All positions liquidated to USDT. Manual restart required. (Paper trading: kill switch optional)",
                    style = MaterialTheme.typography.bodySmall,
                    color = VintageColors.TextTertiary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
