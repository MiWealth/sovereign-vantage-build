package com.miwealth.sovereignvantage.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.miwealth.sovereignvantage.ui.theme.*

/**
 * WALLET SCREEN — Self-Sovereign Asset Custody
 *
 * Separate from Portfolio: Wallet = keys/balances/transfers.
 * Portfolio = performance/analytics/P&L.
 *
 * © 2025-2026 MiWealth Pty Ltd
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    onNavigateBack: () -> Unit,
    onCoinClick: (String) -> Unit = {},
    viewModel: WalletViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // V5.17.0: Coming Soon feedback for unimplemented wallet actions
    var comingSoonAction by remember { mutableStateOf<String?>(null) }
    
    if (comingSoonAction != null) {
        AlertDialog(
            onDismissRequest = { comingSoonAction = null },
            title = { Text(comingSoonAction ?: "", color = VintageColors.Gold) },
            text = { Text("This feature is coming soon. Self-sovereign wallet operations will be available in a future update.", color = VintageColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { comingSoonAction = null }) {
                    Text("OK", color = VintageColors.Gold)
                }
            },
            containerColor = VintageColors.EmeraldDark
        )
    }

    // BUILD #157: Removed nested Scaffold to fix nav bar obscuring
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VintageColors.EmeraldDeep)
    ) {
        TopAppBar(
                title = {
                    Column {
                        Text("WALLET", fontWeight = FontWeight.Bold, color = VintageColors.Gold, letterSpacing = 1.sp)
                        Text(
                            "Self-Sovereign Custody",
                            style = MaterialTheme.typography.bodySmall,
                            color = VintageColors.Gold.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = VintageColors.Gold)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Future: QR scanner */ }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR",
                            tint = VintageColors.Gold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VintageColors.EmeraldDeep)
            )
        Spacer(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(brush = Brush.horizontalGradient(colors = listOf(Color.Transparent, VintageColors.GoldDark, VintageColors.Gold, VintageColors.GoldDark, Color.Transparent))))
        
        // BUILD #157: Content area (no longer wrapped in Scaffold lambda)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(VintageColors.EmeraldDeep),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Total Balance Card — Gold frame
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, VintageColors.GoldDark, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = VintageColors.Gold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "YOUR KEYS • YOUR CRYPTO",
                                style = MaterialTheme.typography.labelSmall,
                                color = VintageColors.Gold,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Total Balance",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VintageColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "A$${String.format("%,.2f", uiState.totalBalance)}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = VintageColors.Gold
                        )
                    }
                }
            }

            // Action Buttons — clearly wallet-specific
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f).clickable { comingSoonAction = "Send Crypto" },
                        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldMedium),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CallMade,
                                contentDescription = null,
                                tint = VintageColors.Gold,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Send", style = MaterialTheme.typography.bodySmall, color = VintageColors.TextPrimary)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f).clickable { comingSoonAction = "Receive Crypto" },
                        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldMedium),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CallReceived,
                                contentDescription = null,
                                tint = VintageColors.Gold,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Receive", style = MaterialTheme.typography.bodySmall, color = VintageColors.TextPrimary)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f).clickable { comingSoonAction = "Connect Exchange" },
                        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldMedium),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                tint = VintageColors.Gold,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Connect", style = MaterialTheme.typography.bodySmall, color = VintageColors.TextPrimary)
                        }
                    }
                }
            }

            // Connected Exchanges
            item {
                Text(
                    "CONNECTED EXCHANGES",
                    color = VintageColors.Gold,
                    letterSpacing = 1.sp,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.connectedExchanges.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.LinkOff,
                                contentDescription = null,
                                tint = VintageColors.TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No exchanges connected",
                                style = MaterialTheme.typography.bodyLarge,
                                color = VintageColors.TextSecondary
                            )
                            Text(
                                "Go to Settings → Exchange API to connect",
                                style = MaterialTheme.typography.bodySmall,
                                color = VintageColors.TextMuted
                            )
                        }
                    }
                }
            } else {
                items(uiState.connectedExchanges) { exchange ->
                    ExchangeCard(exchange = exchange)
                }
            }

            // Asset Balances
            item {
                Text(
                    "ASSET BALANCES",
                    color = VintageColors.Gold,
                    letterSpacing = 1.sp,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(uiState.assets) { asset ->
                AssetBalanceRow(
                    asset = asset,
                    onClick = { onCoinClick(asset.symbol) }
                )
            }

            // Self-Sovereignty Notice
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().border(1.dp, VintageColors.Gold, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = VintageColors.Gold.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = VintageColors.Gold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Self-Sovereign Custody",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = VintageColors.Gold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "All API keys are stored locally on this device with AES-256 encryption. " +
                                    "MiWealth never has access to your funds or credentials.",
                                style = MaterialTheme.typography.bodySmall,
                                color = VintageColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExchangeCard(exchange: ConnectedExchange) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(VintageColors.Gold.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    exchange.name.first().uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VintageColors.Gold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    exchange.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (exchange.isTestnet) "Testnet" else "Production",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (exchange.isTestnet) VintageColors.GoldDark else VintageColors.ProfitGreen
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "A$${String.format("%,.2f", exchange.balance)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = VintageColors.Gold
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (exchange.isConnected) VintageColors.ProfitGreen else VintageColors.LossRed)
                )
            }
        }
    }
}

@Composable
fun AssetBalanceRow(
    asset: WalletAsset,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = VintageColors.EmeraldMedium),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(VintageColors.Gold.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    asset.symbol.take(2),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = VintageColors.Gold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    asset.symbol,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${String.format("%,.6f", asset.amount)} ${asset.symbol}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VintageColors.TextTertiary
                )
            }
            Text(
                "A$${String.format("%,.2f", asset.valueUsd)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "View details",
                tint = VintageColors.Gold.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Data classes
data class ConnectedExchange(
    val id: String,
    val name: String,
    val isConnected: Boolean,
    val isTestnet: Boolean,
    val balance: Double
)

data class WalletAsset(
    val symbol: String,
    val amount: Double,
    val valueUsd: Double
)

data class WalletUiState(
    val totalBalance: Double = 0.0,
    val connectedExchanges: List<ConnectedExchange> = emptyList(),
    val assets: List<WalletAsset> = emptyList(),
    val cashBalance: Double = 0.0,
    val isPaperTrading: Boolean = true,
    val isLoading: Boolean = false
)
