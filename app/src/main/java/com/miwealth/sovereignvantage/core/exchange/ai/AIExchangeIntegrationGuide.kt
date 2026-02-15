package com.miwealth.sovereignvantage.core.exchange.ai

import android.content.Context
import com.miwealth.sovereignvantage.core.ExchangeCredentials
import com.miwealth.sovereignvantage.core.trading.engine.OrderRequest
import kotlinx.coroutines.*

/**
 * AI EXCHANGE INTERFACE - INTEGRATION GUIDE
 * 
 * Step-by-step guide for integrating AI Exchange Interface
 * into TradingCoordinator for production use.
 * 
 * V5.5.87: Production integration examples
 * 
 * @author MiWealth Pty Ltd
 * @version 5.5.87 "Arthur Edition"
 */
object AIExchangeIntegrationGuide {
    
    /**
     * Example 1: Basic Setup
     * Add exchanges and connect
     */
    suspend fun basicSetup(context: Context) {
        println("=== BASIC SETUP ===\n")
        
        // Create manager
        val manager = AIConnectionManager(context)
        
        // Add Binance Testnet (for testing)
        manager.addKnownExchange(
            exchangeId = "binance_testnet",
            credentials = null,  // Public endpoints only
            autoConnect = true
        )
        
        println("✓ Added Binance Testnet")
        
        // Add Gate.io
        manager.addKnownExchange(
            exchangeId = "gateio",
            credentials = null,  // Public endpoints only
            autoConnect = true
        )
        
        println("✓ Added Gate.io")
        
        // Wait for connections
        delay(2000)
        
        // Check status
        val status = manager.connectionState.value
        println("\nConnection Status:")
        status.forEach { (exchange, state) ->
            println("  $exchange: $state")
        }
    }
    
    /**
     * Example 2: Authenticated Trading
     * Connect with API keys for live trading
     */
    suspend fun authenticatedTrading(context: Context) {
        println("\n=== AUTHENTICATED TRADING ===\n")
        
        val manager = AIConnectionManager(context)
        
        // Add exchange with credentials
        val credentials = ExchangeCredentials(
            exchangeId = "binance_testnet",
            apiKey = "YOUR_API_KEY_HERE",
            apiSecret = "YOUR_API_SECRET_HERE"
        )
        
        manager.addKnownExchange(
            exchangeId = "binance_testnet",
            credentials = credentials,
            autoConnect = true
        )
        
        println("✓ Connected with authentication")
        
        // Get balances
        val balances = manager.getAllBalances()
        println("\nBalances:")
        balances.take(5).forEach { balance ->
            println("  ${balance.asset}: ${balance.free} (locked: ${balance.locked})")
        }
    }
    
    /**
     * Example 3: Multi-Exchange Price Discovery
     * Get best price across multiple exchanges
     */
    suspend fun multiExchangePriceDiscovery(context: Context) {
        println("\n=== MULTI-EXCHANGE PRICE DISCOVERY ===\n")
        
        val manager = AIConnectionManager(context)
        
        // Add multiple exchanges
        listOf("binance_testnet", "kraken", "coinbase").forEach { exchangeId ->
            manager.addKnownExchange(exchangeId, autoConnect = true)
        }
        
        delay(2000)
        
        // Get best ticker for BTC/USDT
        val symbol = "BTC/USDT"
        val ticker = manager.getBestTicker(symbol)
        
        println("Best price for $symbol:")
        println("  Exchange: ${ticker.exchangeId}")
        println("  Bid: ${ticker.bid}")
        println("  Ask: ${ticker.ask}")
        println("  Last: ${ticker.last}")
        println("  Volume: ${ticker.volume}")
    }
    
    /**
     * Example 4: Smart Order Routing
     * Automatically route orders to best exchange
     */
    suspend fun smartOrderRouting(context: Context) {
        println("\n=== SMART ORDER ROUTING ===\n")
        
        val manager = AIConnectionManager(context)
        
        // Setup exchanges
        manager.addKnownExchange("binance_testnet", autoConnect = true)
        manager.addKnownExchange("kraken", autoConnect = true)
        
        delay(2000)
        
        // Create order request
        val orderRequest = OrderRequest(
            symbol = "BTC/USDT",
            side = com.miwealth.sovereignvantage.core.exchange.TradeSide.BUY,
            type = com.miwealth.sovereignvantage.core.exchange.OrderType.MARKET,
            quantity = 0.001
        )
        
        // Place order (automatically routed to best exchange)
        println("Placing order on best exchange...")
        val result = manager.placeOrderSmart(orderRequest)
        
        when (result) {
            is com.miwealth.sovereignvantage.core.trading.engine.OrderExecutionResult.Success -> {
                println("✓ Order placed successfully!")
                println("  Exchange: ${result.order.exchangeId}")
                println("  Order ID: ${result.order.id}")
                println("  Price: ${result.order.price}")
            }
            is com.miwealth.sovereignvantage.core.trading.engine.OrderExecutionResult.Error -> {
                println("✗ Order failed: ${result.error.message}")
            }
        }
    }
    
    /**
     * Example 5: Real-Time Price Streaming
     * Subscribe to WebSocket price feeds
     */
    suspend fun realTimePriceStreaming(context: Context) {
        println("\n=== REAL-TIME PRICE STREAMING ===\n")
        
        val manager = AIConnectionManager(context)
        
        manager.addKnownExchange("binance_testnet", autoConnect = true)
        delay(2000)
        
        // Subscribe to prices
        val symbols = listOf("BTC/USDT", "ETH/USDT", "SOL/USDT")
        println("Subscribing to: ${symbols.joinToString()}")
        println("\nPrice Stream (10 seconds):\n")
        
        withTimeout(10000) {
            manager.subscribeToAllPrices(symbols).collect { tick ->
                println("  ${tick.symbol}: ${tick.last} (${tick.timestamp})")
            }
        }
    }
    
    /**
     * Example 6: Integration with TradingCoordinator
     * Complete integration example
     */
    class TradingCoordinatorIntegration(
        private val context: Context
    ) {
        private val exchangeManager = AIConnectionManager(context)
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        /**
         * Initialize exchanges
         */
        suspend fun initialize() {
            println("=== TRADING COORDINATOR INTEGRATION ===\n")
            
            // Add primary exchanges
            println("Adding exchanges...")
            
            // Binance (main spot trading)
            exchangeManager.addKnownExchange(
                exchangeId = "binance",
                credentials = getCredentials("binance"),
                autoConnect = true
            )
            
            // Kraken (backup)
            exchangeManager.addKnownExchange(
                exchangeId = "kraken",
                credentials = getCredentials("kraken"),
                autoConnect = true
            )
            
            // Gate.io (additional liquidity)
            exchangeManager.addKnownExchange(
                exchangeId = "gateio",
                credentials = getCredentials("gateio"),
                autoConnect = true
            )
            
            delay(3000)
            
            // Discover assets
            println("\nDiscovering available assets...")
            val assets = exchangeManager.discoverAllAssets()
            println("Found ${assets.size} assets across ${exchangeManager.getExchangeIds().size} exchanges")
            
            // Start health monitoring
            exchangeManager.startHealthMonitoring(intervalSeconds = 30)
            
            println("\n✓ Trading Coordinator initialized!")
        }
        
        /**
         * Execute trade through coordinator
         */
        suspend fun executeTrade(
            symbol: String,
            side: com.miwealth.sovereignvantage.core.exchange.TradeSide,
            quantity: Double
        ) {
            println("\n--- EXECUTING TRADE ---")
            println("Symbol: $symbol")
            println("Side: $side")
            println("Quantity: $quantity")
            
            // 1. Get best price
            val ticker = exchangeManager.getBestTicker(symbol)
            println("\nBest price: ${ticker.last} on ${ticker.exchangeId}")
            
            // 2. Create order
            val orderRequest = OrderRequest(
                symbol = symbol,
                side = side,
                type = com.miwealth.sovereignvantage.core.exchange.OrderType.MARKET,
                quantity = quantity
            )
            
            // 3. Execute via smart routing
            val result = exchangeManager.placeOrderSmart(orderRequest)
            
            when (result) {
                is com.miwealth.sovereignvantage.core.trading.engine.OrderExecutionResult.Success -> {
                    println("\n✓ TRADE EXECUTED")
                    println("  Exchange: ${result.order.exchangeId}")
                    println("  Order ID: ${result.order.id}")
                    println("  Fill Price: ${result.order.price}")
                    println("  Status: ${result.order.status}")
                }
                is com.miwealth.sovereignvantage.core.trading.engine.OrderExecutionResult.Error -> {
                    println("\n✗ TRADE FAILED")
                    println("  Error: ${result.error.message}")
                }
            }
        }
        
        /**
         * Monitor positions across all exchanges
         */
        suspend fun monitorPositions() {
            println("\n--- MONITORING POSITIONS ---")
            
            // Get all balances
            val aggregated = exchangeManager.getAggregatedBalances()
            
            println("\nTotal Portfolio:")
            aggregated
                .filter { it.total > 0.0 }
                .sortedByDescending { it.total }
                .take(10)
                .forEach { balance ->
                    println("  ${balance.asset}: ${balance.total}")
                    balance.byExchange.forEach { (exchange, bal) ->
                        if (bal.free > 0.0 || bal.locked > 0.0) {
                            println("    - $exchange: ${bal.free} (locked: ${bal.locked})")
                        }
                    }
                }
            
            // Get all open orders
            val openOrders = exchangeManager.getAllOpenOrders()
            println("\nOpen Orders: ${openOrders.size}")
            openOrders.forEach { order ->
                println("  ${order.symbol} ${order.side} ${order.quantity} @ ${order.price}")
                println("    Exchange: ${order.exchangeId}, Status: ${order.status}")
            }
        }
        
        /**
         * Get credentials from secure storage
         */
        private fun getCredentials(exchangeId: String): ExchangeCredentials? {
            // In production: Load from encrypted storage
            // For testing: Return null for public-only access
            return null
        }
        
        /**
         * Shutdown
         */
        fun shutdown() {
            exchangeManager.shutdown()
            scope.cancel()
        }
    }
    
    /**
     * Example 7: Error Handling & Recovery
     */
    suspend fun errorHandlingExample(context: Context) {
        println("\n=== ERROR HANDLING & RECOVERY ===\n")
        
        val manager = AIConnectionManager(context)
        
        // Add exchange
        manager.addKnownExchange("binance_testnet", autoConnect = true)
        delay(2000)
        
        // Monitor health
        manager.startHealthMonitoring(intervalSeconds = 10)
        
        // Subscribe to health updates
        val healthJob = CoroutineScope(Dispatchers.IO).launch {
            manager.healthUpdates.collect { health ->
                println("Health Update: ${health.exchangeId}")
                println("  Status: ${health.status}")
                println("  Success Rate: ${health.successRate}%")
                println("  Latency: ${health.latencyMs}ms")
                
                if (health.status == com.miwealth.sovereignvantage.core.exchange.ConnectionStatus.FAILING) {
                    println("  ⚠️  Exchange degraded - triggering recovery...")
                    manager.reconnect(health.exchangeId)
                }
            }
        }
        
        // Run for 30 seconds
        delay(30000)
        healthJob.cancel()
        manager.stopHealthMonitoring()
    }
}

/**
 * Run integration examples
 */
suspend fun runIntegrationExamples(context: Context) {
    // Basic setup
    AIExchangeIntegrationGuide.basicSetup(context)
    
    delay(2000)
    
    // Price discovery
    AIExchangeIntegrationGuide.multiExchangePriceDiscovery(context)
    
    delay(2000)
    
    // Real-time streaming
    AIExchangeIntegrationGuide.realTimePriceStreaming(context)
    
    delay(2000)
    
    // Full coordinator integration
    val coordinator = AIExchangeIntegrationGuide.TradingCoordinatorIntegration(context)
    coordinator.initialize()
    coordinator.monitorPositions()
    coordinator.shutdown()
}
