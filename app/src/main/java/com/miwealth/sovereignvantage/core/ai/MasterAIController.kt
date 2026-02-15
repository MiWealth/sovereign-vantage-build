// MasterAIController.kt (Updated for Sentiment Engine Integration)

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import java.math.BigDecimal
import com.miwealth.sovereignvantage.core.dflp.DFLPAggregator
import com.miwealth.sovereignvantage.core.onchain.CEXReputationFilter
import com.miwealth.sovereignvantage.core.onchain.DEXReputationFilter
import com.miwealth.sovereignvantage.core.BrokerageAPIAdapter
import com.miwealth.sovereignvantage.core.AssetType
import com.miwealth.sovereignvantage.core.TradeResult
import com.miwealth.sovereignvantage.core.TradeLedger
import com.miwealth.sovereignvantage.core.PortfolioService
import com.miwealth.sovereignvantage.core.TradeSide
import com.miwealth.sovereignvantage.core.wallet.UniversalTransactionManager
import java.time.Instant

class MasterAIController(
    private val context: Context,
    private val brokerageAdapter: BrokerageAPIAdapter // NEW: Adapter for TradFi
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val proposalChannel = MutableSharedFlow<TradeProposal>() // The central pipeline
    private val tradeLedger = TradeLedger(context) // NEW: Local PQC-encrypted ledger
    private val portfolioService = PortfolioService(context, tradeLedger) // NEW: Cost Basis and Performance Tracking

    private val executionService = ExecutionService(context, brokerageAdapter, this, tradeLedger) // The central executor
    private val dflpAggregator = DFLPAggregator(context) // The DFLP system
    private val historicalDataManager = HistoricalDataManager(context)
    private val backtestingEngine = BacktestingEngine(context)
    private val aiModelService = AIModelService()
    private val riskManagementService = RiskManagementService(context)
    private val sentimentEngine = SentimentEngine.getInstance(context) // V5.5.92: Shared singleton

    // The specialist engines, now running concurrently
    private val trendTrader = TrendMomentumEngine(context, proposalChannel)
    private val cexReputationFilter = CEXReputationFilter(context) // NEW: CEX Reputation Filter
    private val dexReputationFilter = DEXReputationFilter(context) // NEW: DEX Reputation Filter
    private val arbitrageHunter = ArbitrageEngine(context, proposalChannel, cexReputationFilter, dexReputationFilter)
    private val futuresTrader = FuturesEngine(context, proposalChannel)

    private var controllerJob: Job? = null
    private var continuousLearningJob: Job? = null
    private var totalCapital = BigDecimal("10000.00") // User's total portfolio
    private var deployedCapital = BigDecimal.ZERO
    private var isPaperTradingMode = false // NEW: Flag for Paper Trading Mode
    
    private var config: RiskManagementConfig = riskManagementService.loadConfig()

    fun startBot() {
        if (controllerJob?.isActive == true) return

        // Reload config before starting
        config = riskManagementService.loadConfig()

        // --- PROMINENT RISK DISCLOSURE ---
        println("""
            
            ================================================================================
            WARNING: Investments made through and by this software tool set can result in the 
            loss of all assets and more. The control of your use of the software is now 
            entirely in your hands. With decentralization comes responsibility. That 
            responsibility is now entirely yours!
            ================================================================================
            
        """.trimIndent())
        // --- END RISK DISCLOSURE ---

        // 1. Start the DFLP system
        dflpAggregator.start()
        println("MASTER AI: Decentralized Federated Learning Protocol (DFLP) started.")

        // 2. Start continuous historical data learning
        continuousLearningJob = scope.launch { startContinuousLearning() }

        // 3. Start the Sentiment Engine
        sentimentEngine.start()
        println("MASTER AI: Sentiment Engine started.")

        controllerJob = scope.launch {
            // 4. Start all specialist engines concurrently
            trendTrader.start()
            arbitrageHunter.start()
            futuresTrader.start()

            // 5. The CIO AI listens to the proposal pipeline
            proposalChannel.collect { proposal ->
                // Also check for proposals from the new TradFi engine (simulated here)
                val currentSentiment = sentimentEngine.getSentiment("GLOBAL")?.score ?: 0.0
                runTradFiEngines(currentSentiment).forEach { tradFiProposal ->
                    // For simplicity, we directly process TradFi proposals here instead of pushing to the channel
                    if (shouldApprove(tradFiProposal)) {
                        println("MASTER AI: TradFi Proposal approved. Executing trade: ${tradFiProposal.details}")
                        deployedCapital += tradFiProposal.requiredCapital
                        executionService.executeTrade(tradFiProposal)
                    } else {
                        println("MASTER AI: TradFi Proposal denied. Insufficient capital or low score.")
                    }
                }
                println("MASTER AI: Received proposal from ${proposal.sourceEngine}.")
                
                // 6. Evaluate the proposal using the current risk configuration and sentiment
                if (shouldApprove(proposal)) {
                    println("MASTER AI: Proposal approved. Executing trade: ${proposal.details}")
                    
                    // 7. Allocate capital and execute
                    deployedCapital += proposal.requiredCapital
                    executionService.executeTrade(proposal)
                    
                    // Capital will be released when ExecutionService closes the trade
                } else {
                    println("MASTER AI: Proposal denied. Insufficient capital or low score.")
                }
            }
        }
        println("MasterAIController: All engines and DFLP started at ${Instant.now()}")
    }

    fun stopBot() {
        // Stop the DFLP system
        dflpAggregator.stop()
        println("MASTER AI: Decentralized Federated Learning Protocol (DFLP) stopped.")

        // Stop the Sentiment Engine
        sentimentEngine.stop()
        println("MASTER AI: Sentiment Engine stopped.")

        continuousLearningJob?.cancel() // Stop continuous learning
        controllerJob?.cancel()
        trendTrader.stop()
        arbitrageHunter.stop()
        futuresTrader.stop()
        println("MasterAIController: All operations stopped.")
    }

    fun isBotRunning(): Boolean {
        return controllerJob?.isActive == true
    }

    fun setPaperTradingMode(enabled: Boolean) {
        this.isPaperTradingMode = enabled
        println("MasterAIController: Paper Trading Mode set to $enabled")
    }

    fun updateConfig(newConfig: RiskManagementConfig) {
        this.config = newConfig
        riskManagementService.saveConfig(newConfig)
        println("MasterAIController: Risk configuration updated via VPI.")
    }

    // Placeholder for a new engine that generates proposals for traditional assets
    private fun runTradFiEngines(sentiment: Double): List<TradeProposal> {
        val proposals = mutableListOf<TradeProposal>()
        
        // Example: Simple value investing strategy based on sentiment (Stocks)
        if (sentiment < -0.5) {
            // Buy a defensive stock if sentiment is very low
            proposals.add(TradeProposal(
                asset = Asset(symbol = "SPY", type = AssetType.STOCK),
                sourceEngine = "TradFiValueEngine",
                requiredCapital = BigDecimal("500.00"),
                estimatedProfit = BigDecimal("50.00"),
                confidenceScore = 0.85,
                riskScore = 0.15,
                details = "Buy SPY based on low sentiment"
            ))
        }

        // Example: Simple momentum strategy for FOREX
        if (sentiment > 0.7) {
            // Go long EUR/USD if sentiment is very high
            proposals.add(TradeProposal(
                asset = Asset(symbol = "EURUSD", type = AssetType.FOREX),
                sourceEngine = "ForexMomentumEngine",
                requiredCapital = BigDecimal("10000.00"), // Standard lot size placeholder
                estimatedProfit = BigDecimal("150.00"),
                confidenceScore = 0.92,
                riskScore = 0.08,
                details = "Buy EURUSD based on high sentiment"
            ))
        }

        return proposals
    }

    private suspend fun startContinuousLearning() {
        while (isActive) {
            // 1. Select a random, high-volume pair and timeframe
            val symbol = "BTC/USDT" // Placeholder
            val timeframe = "4h" // Placeholder

            // 2. Fetch data (data is stored temporarily)
            val historicalData = historicalDataManager.fetchAndStoreData(symbol, timeframe, 500)

            // 3. AI trains on the data
            aiModelService.trainOnHistoricalData(historicalData)

            // 4. Data is immediately discarded
            historicalDataManager.clearAllData()

            println("MasterAIController: Continuous learning cycle complete for $symbol. Model updated.")

            // Wait for a period before the next learning cycle
            delay(4 * 3600 * 1000L) // Learn every 4 hours
        }
    }

    suspend fun runBacktest(symbol: String, timeframe: String, startingCapital: BigDecimal): BacktestReport {
        return backtestingEngine.runBacktest(symbol, timeframe, startingCapital)
    }

    private fun shouldApprove(proposal: TradeProposal): Boolean {
        // Check if we have enough available capital based on VPI config
        val maxDeployableCapital = totalCapital.multiply(BigDecimal(config.maxCapitalAllocationPercent))
        val availableCapital = maxDeployableCapital.subtract(deployedCapital)

        if (proposal.requiredCapital > availableCapital) {
            return false
        }

        // --- NEW: Sentiment-Adjusted Approval ---
        val asset = proposal.details.substringBefore("/") // Crude way to get the asset
        val sentiment = sentimentEngine.getSentiment(asset)?.score ?: 0.0

        // Adjust the confidence score based on sentiment (e.g., 10% boost for strong positive sentiment)
        val sentimentAdjustment = if (sentiment > 0.5) 1.1 else if (sentiment < -0.5) 0.9 else 1.0
        val adjustedConfidence = proposal.confidenceScore * sentimentAdjustment
        // --- END NEW ---

        // Calculate the Risk-Adjusted Return Score
        val riskAdjustedScore = (proposal.estimatedProfit.toDouble() * adjustedConfidence) / proposal.riskScore
        
        // Only approve trades with a score above the VPI-configured threshold
        return riskAdjustedScore > config.approvalThreshold
    }
}

// Placeholder for AIModelService and TradingEngine methods used above
// NOTE: These are simplified placeholders for integration demonstration.
fun AIModelService.trainOnHistoricalData(data: List<Candlestick>) {
    // Logic to update the TFLite model weights based on the historical data
    println("AIModelService: Training on ${data.size} candles...")
}

class ExecutionService(
    context: Context, 
    private val brokerageAdapter: BrokerageAPIAdapter,
    private val masterAIController: MasterAIController,
    private val tradeLedger: TradeLedger // NEW: Ledger for recording trades
) {
    private val ccxt = CCXTBridge(context)
    
    suspend fun executeTrade(proposal: TradeProposal) {
        if (masterAIController.isPaperTradingMode) {
            // --- PAPER TRADING MODE ---
            println("EXECUTION SERVICE: [PAPER TRADE] Simulating execution for: ${proposal.details}")
            // In a real implementation, this would update a simulated P&L ledger.
            val simulatedResult = TradeResult(
                orderId = "PAPER-${System.currentTimeMillis()}", 
                asset = proposal.asset, 
                side = Order.Side.BUY, 
                executedPrice = 0.0, // Placeholder for simulated price
                executedQuantity = 0.0, // Placeholder for simulated quantity
                status = TradeResult.Status.FILLED
            )
            println("EXECUTION SERVICE: [PAPER TRADE] Trade Executed: ${simulatedResult.orderId} Status: ${simulatedResult.status}")
            return
        }

        // --- LIVE TRADING MODE ---
        val assetType = proposal.asset.type
        val result: TradeResult = when (assetType) {
            AssetType.CRYPTO, AssetType.TOKEN -> {
                // Contains the logic to parse the proposal details and use the CCXTBridge
                // to place the actual orders on the exchanges.
                println("EXECUTION SERVICE: Placing Crypto orders for trade: ${proposal.details}")
                // Placeholder for actual crypto execution logic
                TradeResult(orderId = "CRYPTO-${System.currentTimeMillis()}", asset = proposal.asset, side = Order.Side.BUY, executedPrice = 45000.0, executedQuantity = 0.01, status = TradeResult.Status.FILLED)
            }
            AssetType.STOCK, AssetType.BOND, AssetType.FOREX -> {
                // Logic to execute order via the BrokerageAPIAdapter
                println("EXECUTION SERVICE: Placing TradFi orders for trade: ${proposal.details}")
                // Placeholder for actual TradFi execution logic
                TradeResult(orderId = "TRADFI-${System.currentTimeMillis()}", asset = proposal.asset, side = Order.Side.BUY, executedPrice = 100.0, executedQuantity = 5.0, status = TradeResult.Status.FILLED)
            }
            else -> throw IllegalArgumentException("Unsupported asset type for execution.")
        }
        
        // Record the live trade
        tradeLedger.recordTrade(
            asset = result.asset,
            side = if (result.side == Order.Side.BUY) TradeSide.BUY else TradeSide.SELL,
            quantity = BigDecimal(result.executedQuantity),
            price = BigDecimal(result.executedPrice),
            timestamp = Instant.now(),
            source = "LIVE_TRADING",
            transactionId = result.orderId
        )
        
        println("EXECUTION SERVICE: Trade Executed and Recorded: ${result.orderId} Status: ${result.status}")
    }
}

// Placeholder classes for the specialist engines
class TrendMomentumEngine(context: Context, channel: MutableSharedFlow<TradeProposal>) {
    fun start() { /* start coroutine */ }
    fun stop() { /* stop coroutine */ }
}
class ArbitrageEngine(context: Context, channel: MutableSharedFlow<TradeProposal>, private val cexReputationFilter: CEXReputationFilter, private val dexReputationFilter: DEXReputationFilter) {
    fun start() { /* start coroutine */ }
    fun stop() { /* stop coroutine */ }
}

class FuturesEngine(context: Context, channel: MutableSharedFlow<TradeProposal>) {
    fun start() { /* start coroutine */ }
    fun stop() { /* stop coroutine */ }
}

// Placeholder data classes
data class TradeProposal(
    val asset: Asset, // NEW: The asset being traded
    val sourceEngine: String,
    val requiredCapital: BigDecimal,
    val estimatedProfit: BigDecimal,
    val confidenceScore: Double,
    val riskScore: Double,
    val details: String
)

data class Candlestick(
    val timestamp: Long,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal
)

data class BacktestReport(
    val startingCapital: BigDecimal,
    val finalCapital: BigDecimal,
    val totalReturnPercent: BigDecimal,
    val totalTrades: Int,
    val profitableTrades: Int,
    val winRate: BigDecimal,
    val maxDrawdownPercent: BigDecimal,
    val sharpeRatio: BigDecimal
)
