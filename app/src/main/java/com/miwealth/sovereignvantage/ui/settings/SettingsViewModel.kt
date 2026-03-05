package com.miwealth.sovereignvantage.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miwealth.sovereignvantage.core.TradingSystemManager
import com.miwealth.sovereignvantage.core.AIConnectionTestResult
import com.miwealth.sovereignvantage.core.exchange.SupportedExchange
import com.miwealth.sovereignvantage.core.exchange.ExchangeCredentials
import com.miwealth.sovereignvantage.core.exchange.ai.ExchangeTestnetConfig
import com.miwealth.sovereignvantage.core.security.ExchangeCredentialManager
import com.miwealth.sovereignvantage.core.trading.assets.PipelineStatus
import com.miwealth.sovereignvantage.core.trading.HybridModeConfig
import com.miwealth.sovereignvantage.core.trading.TradingMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * SETTINGS VIEW MODEL
 * 
 * V5.17.0 CHANGES:
 * - Added pipeline/discovery status (PipelineStatusUi)
 * - Added execution mode display
 * - Added connected exchanges info
 * - Added runAssetDiscovery() method
 * 
 * Manages settings UI state including:
 * - Exchange API credentials
 * - Paper trading settings
 * - Preferred exchange selection
 * - Connection testing
 * - System status (pipeline, discovery, execution mode)
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */

data class ExchangeConfigState(
    val exchangeId: String,
    val displayName: String,
    val isConfigured: Boolean,
    val isTestnet: Boolean = false,
    val hasTestnet: Boolean = false,
    val requiresPassphrase: Boolean = false
)

data class SettingsUiState(
    // Exchange configuration
    val configuredExchanges: List<ExchangeConfigState> = emptyList(),
    val preferredExchange: String = "kraken",
    
    // Paper trading
    val paperTradingEnabled: Boolean = true,
    val paperTradingBalance: Double = 100000.0,
    /** V5.17.0: Data source for paper trading prices */
    val paperTradingDataSource: PaperTradingDataSource = PaperTradingDataSource.MOCK,
    
    // Connection status
    val isConnecting: Boolean = false,
    val connectionResult: ConnectionResult? = null,
    
    // General settings
    val biometricEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val darkModeEnabled: Boolean = true,
    
    // V5.17.0: Trading Mode & HYBRID Configuration
    val tradingMode: String = "SIGNAL_ONLY",  // AUTONOMOUS, SIGNAL_ONLY, HYBRID, SCALPING, ALPHA_SCANNER
    val hybridAutoExecuteThreshold: Double = 85.0,  // % confidence for auto-execution
    val hybridRequireConfirmationBelow: Double = 70.0,  // Below this → always confirm
    val hybridMaxAutoTradesPerHour: Int = 3,
    val hybridAbsoluteValueThreshold: Double = 5000.0,  // AU$ — above this always confirm
    
    // NEW V5.17.0: Advanced Strategy Settings
    val alphaScannerEnabled: Boolean = true,
    val alphaScannerInterval: Int = 60,         // Minutes
    val alphaScannerTopN: Int = 10,             // Top N assets to track
    val alphaScannerMinScore: Double = 0.5,     // Minimum factor score
    
    val fundingArbEnabled: Boolean = true,
    val fundingArbMinRate: Double = 0.01,       // 0.01% minimum funding rate
    val fundingArbMaxPositions: Int = 5,        // Max concurrent arb positions
    val fundingArbMaxCapital: Double = 50.0,    // % of capital for funding arb
    
    val killSwitchDrawdown: Double = 60.0,      // Daily loss kill switch (increased for Hedge engine)
    val dailyLossLimit: Double = 60.0,          // Daily loss limit % (increased for Alpha Factor Scanner)
    
    // NEW: System Status - Pipeline & Discovery
    val executionMode: String = "PAPER",
    val isUsingAIIntegration: Boolean = true,
    val pipelineStatus: PipelineStatusUi = PipelineStatusUi.IDLE,
    val discoveredAssets: Int = 0,
    val registeredAssets: Int = 0,
    val currentDiscoveryAsset: String? = null,
    val lastDiscoveryTime: String? = null,
    val connectedExchangeCount: Int = 0,
    val connectedExchangeNames: List<String> = emptyList(),
    val isRunningDiscovery: Boolean = false,
    
    // Error state
    val error: String? = null
)

/**
 * UI representation of pipeline status.
 */
enum class PipelineStatusUi {
    IDLE,         // Not running
    DISCOVERING,  // Finding assets from exchanges
    ENRICHING,    // Adding DeFiLlama data
    ASSIGNING,    // Assigning risk parameters
    REGISTERING,  // Registering in AssetRegistry
    COMPLETE,     // Done
    ERROR         // Failed
}

/**
 * V5.17.0: Paper trading data source selection.
 * Controls where price data comes from in paper trading mode.
 */
enum class PaperTradingDataSource {
    /** Random walk simulation — works offline, 4 pairs, safe default */
    MOCK,
    /** Real exchange WebSocket (Binance public — no API key needed) */
    LIVE,
    /** Replay historical OHLCV data at configurable speed */
    BACKTEST
}

sealed class ConnectionResult {
    data class Success(val exchangeId: String, val message: String) : ConnectionResult()
    data class Error(val exchangeId: String, val message: String) : ConnectionResult()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentialManager: ExchangeCredentialManager,
    private val tradingSystemManager: TradingSystemManager,
    private val tokenManager: com.miwealth.sovereignvantage.data.repository.TokenManager,
    private val settingsPrefs: com.miwealth.sovereignvantage.data.repository.SettingsPreferencesManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    // All supported exchanges with display names
    private val exchangeDisplayNames = mapOf(
        "kraken" to "Kraken",
        "coinbase" to "Coinbase",
        "binance" to "Binance",
        "bybit" to "Bybit",
        "okx" to "OKX",
        "kucoin" to "KuCoin",
        "gateio" to "Gate.io",
        "mexc" to "MEXC",
        "bitget" to "Bitget",
        "htx" to "HTX (Huobi)",
        "gemini" to "Gemini",
        "uphold" to "Uphold"
    )
    
    init {
        loadSettings()
        loadSystemStatus()
        observePipelineState()
    }
    
    // ========================================================================
    // LOAD SETTINGS
    // ========================================================================
    
    private fun loadSettings() {
        viewModelScope.launch {
            // Load configured exchanges
            val configuredIds = credentialManager.getConfiguredExchangeIds()
            val exchangeStates = exchangeDisplayNames.map { (id, name) ->
                val creds = credentialManager.getCredentials(id)
                ExchangeConfigState(
                    exchangeId = id,
                    displayName = name,
                    isConfigured = creds != null,
                    isTestnet = creds?.isTestnet ?: false,
                    hasTestnet = ExchangeTestnetConfig.hasTestnet(id),
                    requiresPassphrase = ExchangeTestnetConfig.requiresPassphrase(id)
                )
            }
            
            _uiState.update { current ->
                current.copy(
                    configuredExchanges = exchangeStates,
                    preferredExchange = credentialManager.getPreferredExchange().name.lowercase(),
                    paperTradingEnabled = settingsPrefs.getPaperTradingEnabled(),
                    paperTradingBalance = settingsPrefs.getPaperTradingBalance(),
                    paperTradingDataSource = settingsPrefs.getPaperTradingDataSource(),
                    // General settings
                    biometricEnabled = settingsPrefs.getBiometricEnabled(),
                    notificationsEnabled = settingsPrefs.getNotificationsEnabled(),
                    darkModeEnabled = settingsPrefs.getDarkModeEnabled(),
                    // Trading mode & hybrid
                    tradingMode = settingsPrefs.getTradingMode(),
                    hybridAutoExecuteThreshold = settingsPrefs.getHybridAutoExecuteThreshold(),
                    hybridRequireConfirmationBelow = settingsPrefs.getHybridConfirmationThreshold(),
                    hybridMaxAutoTradesPerHour = settingsPrefs.getHybridMaxAutoTrades(),
                    hybridAbsoluteValueThreshold = settingsPrefs.getHybridValueThreshold(),
                    // Advanced strategy settings
                    alphaScannerEnabled = settingsPrefs.getAlphaScannerEnabled(),
                    alphaScannerInterval = settingsPrefs.getAlphaScannerInterval(),
                    alphaScannerTopN = settingsPrefs.getAlphaScannerTopN(),
                    alphaScannerMinScore = settingsPrefs.getAlphaScannerMinScore(),
                    fundingArbEnabled = settingsPrefs.getFundingArbEnabled(),
                    fundingArbMinRate = settingsPrefs.getFundingArbMinRate(),
                    fundingArbMaxPositions = settingsPrefs.getFundingArbMaxPositions(),
                    fundingArbMaxCapital = settingsPrefs.getFundingArbMaxCapital(),
                    dailyLossLimit = settingsPrefs.getDailyLossLimit()
                )
            }
        }
    }
    
    // ========================================================================
    // EXCHANGE CREDENTIALS
    // ========================================================================
    
    /**
     * Save exchange credentials and optionally test the connection.
     */
    fun saveExchangeCredentials(
        exchangeId: String,
        apiKey: String,
        apiSecret: String,
        passphrase: String? = null,
        isTestnet: Boolean = false,
        testConnection: Boolean = true
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, connectionResult = null) }
            
            try {
                // Save credentials
                credentialManager.saveCredentials(
                    exchangeId = exchangeId,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    passphrase = passphrase,
                    isTestnet = isTestnet
                )
                
                // If this is the first exchange, set it as preferred
                if (credentialManager.getConfiguredExchangeCount() == 1) {
                    credentialManager.setPreferredExchange(exchangeId)
                }
                
                // Reload settings to update UI
                loadSettings()
                
                // Test connection if requested
                if (testConnection) {
                    testExchangeConnection(exchangeId)
                } else {
                    _uiState.update { 
                        it.copy(
                            isConnecting = false,
                            connectionResult = ConnectionResult.Success(
                                exchangeId,
                                "Credentials saved successfully"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isConnecting = false,
                        connectionResult = ConnectionResult.Error(
                            exchangeId,
                            e.message ?: "Failed to save credentials"
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Test connection to an exchange via the AI Exchange Interface.
     * 
     * Creates a temporary AI connector, connects to the exchange
     * (testnet or production), and verifies by loading trading pairs.
     */
    fun testExchangeConnection(exchangeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, connectionResult = null) }
            
            try {
                val storedCreds = credentialManager.getCredentials(exchangeId)
                if (storedCreds == null) {
                    _uiState.update { 
                        it.copy(
                            isConnecting = false,
                            connectionResult = ConnectionResult.Error(
                                exchangeId,
                                "No credentials found for $exchangeId"
                            )
                        )
                    }
                    return@launch
                }
                
                val result = tradingSystemManager.testAIConnection(
                    exchangeId = exchangeId,
                    credentials = storedCreds,
                    isTestnet = storedCreds.isTestnet
                )
                
                result.onSuccess { testResult ->
                    _uiState.update { 
                        it.copy(
                            isConnecting = false,
                            connectionResult = ConnectionResult.Success(
                                exchangeId,
                                testResult.message
                            )
                        )
                    }
                }.onFailure { error ->
                    _uiState.update { 
                        it.copy(
                            isConnecting = false,
                            connectionResult = ConnectionResult.Error(
                                exchangeId,
                                error.message ?: "Connection test failed"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isConnecting = false,
                        connectionResult = ConnectionResult.Error(
                            exchangeId,
                            e.message ?: "Connection test failed"
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Remove credentials for an exchange.
     */
    fun removeExchangeCredentials(exchangeId: String) {
        viewModelScope.launch {
            credentialManager.removeCredentials(exchangeId)
            loadSettings()
        }
    }
    
    /**
     * Set the preferred exchange for trading.
     */
    fun setPreferredExchange(exchangeId: String) {
        viewModelScope.launch {
            credentialManager.setPreferredExchange(exchangeId)
            _uiState.update { it.copy(preferredExchange = exchangeId) }
        }
    }
    
    // ========================================================================
    // PAPER TRADING
    // ========================================================================
    
    /**
     * Toggle paper trading mode.
     */
    fun setPaperTradingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            credentialManager.setPaperTradingEnabled(enabled)
            _uiState.update { it.copy(paperTradingEnabled = enabled) }
            
            // Reinitialize trading system
            if (enabled) {
                tradingSystemManager.initializePaperTrading(
                    credentialManager.getPaperTradingBalance()
                )
            } else {
                // Switch to live trading if credentials are available
                val credentials = credentialManager.getAllCredentials()
                if (credentials.isNotEmpty()) {
                    tradingSystemManager.initializeWithPQC(
                        credentials = credentials,
                        preferredExchange = credentialManager.getPreferredExchange()
                    )
                }
            }
        }
    }
    
    /**
     * Set paper trading starting balance.
     */
    fun setPaperTradingBalance(balance: Double) {
        viewModelScope.launch {
            credentialManager.setPaperTradingBalance(balance)
            _uiState.update { it.copy(paperTradingBalance = balance) }
        }
    }
    
    // ========================================================================
    // SYSTEM STATUS - Pipeline & Discovery
    // ========================================================================
    
    /**
     * Load current system status (execution mode, connected exchanges).
     */
    private fun loadSystemStatus() {
        viewModelScope.launch {
            // Execution mode
            val isAI = tradingSystemManager.isUsingAIIntegration()
            val mode = tradingSystemManager.getExecutionMode()?.name ?: "PAPER"
            
            // Connected exchanges
            val connectedExchanges = tradingSystemManager.getConnectedExchanges()
            
            // Registered assets count
            val registeredCount = tradingSystemManager.getRegisteredAssetCount()
            
            // Pipeline state
            val pipelineState = tradingSystemManager.getPipelineState()
            val pipelineStatusUi = pipelineState?.let { mapPipelineStatus(it.status) } 
                ?: PipelineStatusUi.IDLE
            
            _uiState.update { current ->
                current.copy(
                    executionMode = mode,
                    isUsingAIIntegration = isAI,
                    connectedExchangeCount = connectedExchanges.size,
                    connectedExchangeNames = connectedExchanges,
                    registeredAssets = registeredCount,
                    pipelineStatus = pipelineStatusUi,
                    discoveredAssets = pipelineState?.discoveredCount ?: 0,
                    lastDiscoveryTime = pipelineState?.lastRun?.let { formatTimestamp(it) }
                )
            }
        }
    }
    
    /**
     * Observe pipeline state changes for live updates during discovery.
     */
    private fun observePipelineState() {
        viewModelScope.launch {
            tradingSystemManager.observePipelineState()?.collect { pipelineState ->
                _uiState.update { current ->
                    current.copy(
                        pipelineStatus = mapPipelineStatus(pipelineState.status),
                        discoveredAssets = pipelineState.discoveredCount,
                        registeredAssets = pipelineState.registeredCount,
                        currentDiscoveryAsset = pipelineState.currentAsset,
                        lastDiscoveryTime = pipelineState.lastRun?.let { formatTimestamp(it) },
                        isRunningDiscovery = pipelineState.status != PipelineStatus.IDLE && 
                                            pipelineState.status != PipelineStatus.COMPLETE &&
                                            pipelineState.status != PipelineStatus.ERROR
                    )
                }
            }
        }
    }
    
    /**
     * Manually trigger asset discovery pipeline.
     */
    fun runAssetDiscovery() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunningDiscovery = true, pipelineStatus = PipelineStatusUi.DISCOVERING) }
            
            try {
                val registeredCount = tradingSystemManager.runAssetDiscovery()
                
                _uiState.update { current ->
                    current.copy(
                        registeredAssets = registeredCount,
                        pipelineStatus = PipelineStatusUi.COMPLETE,
                        isRunningDiscovery = false,
                        lastDiscoveryTime = formatTimestamp(Instant.now())
                    )
                }
            } catch (e: Exception) {
                _uiState.update { current ->
                    current.copy(
                        pipelineStatus = PipelineStatusUi.ERROR,
                        isRunningDiscovery = false,
                        error = "Discovery failed: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Refresh system status (call when returning to settings).
     */
    fun refreshSystemStatus() {
        loadSystemStatus()
    }
    
    /**
     * Map core PipelineStatus to UI enum.
     */
    private fun mapPipelineStatus(status: PipelineStatus): PipelineStatusUi {
        return when (status) {
            PipelineStatus.IDLE -> PipelineStatusUi.IDLE
            PipelineStatus.DISCOVERING -> PipelineStatusUi.DISCOVERING
            PipelineStatus.ENRICHING -> PipelineStatusUi.ENRICHING
            PipelineStatus.ASSIGNING_RISK -> PipelineStatusUi.ASSIGNING
            PipelineStatus.REGISTERING -> PipelineStatusUi.REGISTERING
            PipelineStatus.COMPLETE -> PipelineStatusUi.COMPLETE
            PipelineStatus.ERROR -> PipelineStatusUi.ERROR
        }
    }
    
    /**
     * Format timestamp for display.
     */
    private fun formatTimestamp(instant: Instant): String {
        val now = Instant.now()
        val diff = now.epochSecond - instant.epochSecond
        
        return when {
            diff < 60 -> "Just now"
            diff < 3600 -> "${diff / 60} minutes ago"
            diff < 86400 -> "${diff / 3600} hours ago"
            else -> {
                val formatter = DateTimeFormatter.ofPattern("dd MMM HH:mm")
                    .withZone(ZoneId.systemDefault())
                formatter.format(instant)
            }
        }
    }
    
    // ========================================================================
    // V5.17.0: PAPER TRADING DATA SOURCE
    // ========================================================================
    
    /**
     * Set paper trading data source and reinitialize trading system.
     * 
     * MOCK  → Random walk (offline, 4 pairs, safe)
     * LIVE  → Binance public WebSocket (no API key needed, real prices)
     * BACKTEST → Historical replay (TODO: BacktestDataProvider)
     */
    fun setPaperTradingDataSource(source: PaperTradingDataSource) {
        _uiState.update { it.copy(paperTradingDataSource = source) }
        
        viewModelScope.launch {
            // Shut down current system
            tradingSystemManager.shutdown()
            
            // Reinitialize with correct execution mode
            when (source) {
                PaperTradingDataSource.MOCK -> {
                    tradingSystemManager.initializePaperTrading(
                        startingBalance = _uiState.value.paperTradingBalance
                    )
                }
                PaperTradingDataSource.LIVE -> {
                    tradingSystemManager.initializeAIPaperTradingWithLiveData(
                        startingBalance = _uiState.value.paperTradingBalance
                    )
                }
                PaperTradingDataSource.BACKTEST -> {
                    // TODO: BacktestDataProvider — for now fall back to mock
                    tradingSystemManager.initializePaperTrading(
                        startingBalance = _uiState.value.paperTradingBalance
                    )
                    _uiState.update { it.copy(error = "Backtest replay coming soon — using mock data") }
                }
            }
        }
    }
    
    // ========================================================================
    // GENERAL SETTINGS
    // ========================================================================
    
    fun setBiometricEnabled(enabled: Boolean) {
        _uiState.update { it.copy(biometricEnabled = enabled) }
        settingsPrefs.setBiometricEnabled(enabled)
    }
    
    fun setNotificationsEnabled(enabled: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = enabled) }
        settingsPrefs.setNotificationsEnabled(enabled)
    }
    
    fun setDarkModeEnabled(enabled: Boolean) {
        _uiState.update { it.copy(darkModeEnabled = enabled) }
        settingsPrefs.setDarkModeEnabled(enabled)
    }
    
    // ========================================================================
    // ADVANCED STRATEGY SETTINGS - V5.17.0
    // ========================================================================
    
    /**
     * Enable/disable Alpha Factor Scanner.
     * Scans universe, ranks by momentum/quality/volatility/trend.
     */
    fun setAlphaScannerEnabled(enabled: Boolean) {
        _uiState.update { it.copy(alphaScannerEnabled = enabled) }
        settingsPrefs.setAlphaScannerEnabled(enabled)
    }
    
    /**
     * Set Alpha Scanner scan interval in minutes.
     */
    fun setAlphaScannerInterval(minutes: Int) {
        val validMinutes = minutes.coerceIn(15, 240) // 15 min to 4 hours
        _uiState.update { it.copy(alphaScannerInterval = validMinutes) }
        settingsPrefs.setAlphaScannerInterval(validMinutes)
    }
    
    /**
     * Set number of top assets to track from Alpha Scanner.
     */
    fun setAlphaScannerTopN(count: Int) {
        val validCount = count.coerceIn(5, 50)
        _uiState.update { it.copy(alphaScannerTopN = validCount) }
        settingsPrefs.setAlphaScannerTopN(validCount)
    }
    
    /**
     * Set minimum composite factor score for Alpha Scanner.
     */
    fun setAlphaScannerMinScore(score: Double) {
        val validScore = score.coerceIn(0.3, 0.9)
        _uiState.update { it.copy(alphaScannerMinScore = validScore) }
        settingsPrefs.setAlphaScannerMinScore(validScore)
    }
    
    /**
     * Enable/disable Delta-Neutral Funding Arbitrage.
     * LONG spot + SHORT perp = collect funding payments.
     */
    fun setFundingArbEnabled(enabled: Boolean) {
        _uiState.update { it.copy(fundingArbEnabled = enabled) }
        settingsPrefs.setFundingArbEnabled(enabled)
    }
    
    /**
     * Set minimum funding rate to enter position (%).
     */
    fun setFundingArbMinRate(rate: Double) {
        val validRate = rate.coerceIn(0.005, 0.1) // 0.005% to 0.1%
        _uiState.update { it.copy(fundingArbMinRate = validRate) }
        settingsPrefs.setFundingArbMinRate(validRate)
    }
    
    /**
     * Set maximum concurrent funding arb positions.
     */
    fun setFundingArbMaxPositions(count: Int) {
        val validCount = count.coerceIn(1, 10)
        _uiState.update { it.copy(fundingArbMaxPositions = validCount) }
        settingsPrefs.setFundingArbMaxPositions(validCount)
    }
    
    /**
     * Set maximum capital allocation for funding arb (%).
     */
    fun setFundingArbMaxCapital(percent: Double) {
        val validPercent = percent.coerceIn(10.0, 80.0)
        _uiState.update { it.copy(fundingArbMaxCapital = validPercent) }
        settingsPrefs.setFundingArbMaxCapital(validPercent)
    }
    
    /**
     * Set daily loss limit (%).
     * At this limit, all trading halts until next day.
     */
    fun setDailyLossLimit(percent: Double) {
        val validPercent = percent.coerceIn(1.0, 5.0) // Max 5%
        _uiState.update { it.copy(dailyLossLimit = validPercent) }
        settingsPrefs.setDailyLossLimit(validPercent)
    }
    
    /**
     * NOTE: Kill switch drawdown is FIXED at 5% and cannot be changed.
     * This is a NON-NEGOTIABLE safety feature.
     */
    
    // ========================================================================
    // V5.17.0: TRADING MODE & HYBRID CONFIGURATION
    // ========================================================================
    
    fun setTradingMode(mode: String) {
        _uiState.update { it.copy(tradingMode = mode) }
        settingsPrefs.setTradingMode(mode)
        // Propagate mode change to trading engine
        try {
            val tradingMode = TradingMode.valueOf(mode)
            tradingSystemManager.setTradingMode(tradingMode)
        } catch (_: IllegalArgumentException) {
            // Invalid mode string — UI state updated but don't propagate
        }
    }
    
    fun setHybridAutoExecuteThreshold(threshold: Double) {
        val validThreshold = threshold.coerceIn(50.0, 100.0)
        _uiState.update { it.copy(hybridAutoExecuteThreshold = validThreshold) }
        settingsPrefs.setHybridAutoExecuteThreshold(validThreshold)
        propagateHybridConfig()
    }
    
    fun setHybridConfirmationThreshold(threshold: Double) {
        val validThreshold = threshold.coerceIn(0.0, 95.0)
        _uiState.update { it.copy(hybridRequireConfirmationBelow = validThreshold) }
        settingsPrefs.setHybridConfirmationThreshold(validThreshold)
        propagateHybridConfig()
    }
    
    fun setHybridMaxAutoTrades(count: Int) {
        val validCount = count.coerceIn(1, 20)
        _uiState.update { it.copy(hybridMaxAutoTradesPerHour = validCount) }
        settingsPrefs.setHybridMaxAutoTrades(validCount)
        propagateHybridConfig()
    }
    
    fun setHybridValueThreshold(value: Double) {
        val validValue = value.coerceIn(100.0, 1_000_000.0)
        _uiState.update { it.copy(hybridAbsoluteValueThreshold = validValue) }
        settingsPrefs.setHybridValueThreshold(validValue)
        propagateHybridConfig()
    }
    
    /**
     * V5.17.0: Build HybridModeConfig from current UI state and push to TradingCoordinator
     * via TradingSystemManager → TradingSystem → TradingCoordinator.updateHybridConfig()
     */
    private fun propagateHybridConfig() {
        val state = _uiState.value
        val config = HybridModeConfig(
            bypassAllConfirmation = false,
            confidenceAutoExecuteThreshold = state.hybridAutoExecuteThreshold,
            confidenceRequireConfirmationBelow = state.hybridRequireConfirmationBelow,
            maxAutoTradesPerHour = state.hybridMaxAutoTradesPerHour,
            usePositionSizeThreshold = true,
            positionSizeThresholdPercent = 5.0,
            useRiskAmountThreshold = false,
            useAbsoluteValueThreshold = true,
            absoluteValueThreshold = state.hybridAbsoluteValueThreshold,
            multipleThresholdLogic = com.miwealth.sovereignvantage.core.trading.ThresholdLogic.ALL
        )
        tradingSystemManager.updateHybridConfig(config)
    }
    
    // ========================================================================
    // UTILITY
    // ========================================================================
    
    fun clearConnectionResult() {
        _uiState.update { it.copy(connectionResult = null) }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    /**
     * Purge all data - GDPR compliance.
     */
    fun purgeAllData() {
        viewModelScope.launch {
            credentialManager.clearAllCredentials()
            tradingSystemManager.shutdown()
            tokenManager.purgeAll()  // Clear auth session + email
            loadSettings()
        }
    }
    
    /**
     * Sign out — clears session token but preserves email for convenience.
     */
    fun logout() {
        viewModelScope.launch {
            tokenManager.clearTokens()
        }
    }
}
