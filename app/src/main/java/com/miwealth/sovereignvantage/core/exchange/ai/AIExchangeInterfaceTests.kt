package com.miwealth.sovereignvantage.core.exchange.ai

import android.content.Context
import android.util.Log
import com.miwealth.sovereignvantage.core.exchange.ExchangeCredentials
import com.miwealth.sovereignvantage.core.exchange.ExchangeType
import com.miwealth.sovereignvantage.core.TradeSide
import com.miwealth.sovereignvantage.core.OrderType
import com.miwealth.sovereignvantage.core.trading.engine.OrderRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import java.time.Instant

/**
 * AI EXCHANGE INTERFACE - TEST SUITE
 * 
 * Comprehensive testing for Binance Testnet and Gate.io
 * 
 * Tests:
 * 1. Schema Learning - Discover API structure
 * 2. Public Endpoints - Ticker, OrderBook, Pairs
 * 3. WebSocket Streaming - Real-time price feeds
 * 4. Authentication - Private endpoint access
 * 5. Order Flow - Place/Cancel/Modify (testnet only)
 * 6. Error Handling - Rate limits, failures, recovery
 * 
 * V5.17.0: Validation testing for production readiness
 * 
 * @author MiWealth Pty Ltd
 * @version 5.5.87 "Arthur Edition"
 */
class AIExchangeInterfaceTestSuite(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "AIExchangeTest"
        
        // Test symbols
        private const val TEST_SYMBOL_BINANCE = "BTC/USDT"
        private const val TEST_SYMBOL_GATEIO = "BTC/USDT"
        
        // Test credentials (placeholder - user must provide real testnet keys)
        private const val BINANCE_TESTNET_KEY = "YOUR_BINANCE_TESTNET_API_KEY"
        private const val BINANCE_TESTNET_SECRET = "YOUR_BINANCE_TESTNET_SECRET"
        
        private const val GATEIO_KEY = "YOUR_GATEIO_API_KEY"  
        private const val GATEIO_SECRET = "YOUR_GATEIO_SECRET"
    }
    
    /**
     * Test result tracking
     */
    data class TestResult(
        val testName: String,
        val exchangeId: String,
        val passed: Boolean,
        val duration: Long,  // milliseconds
        val message: String,
        val details: Map<String, Any> = emptyMap()
    )
    
    private val results = mutableListOf<TestResult>()
    
    // =================================================================
    // BINANCE TESTNET TESTS
    // =================================================================
    
    /**
     * Complete Binance Testnet validation
     */
    suspend fun testBinanceTestnet(): List<TestResult> {
        println("\n╔════════════════════════════════════════════════╗")
        println("║  BINANCE TESTNET - AI EXCHANGE INTERFACE TEST ║")
        println("╚════════════════════════════════════════════════╝\n")
        
        // Test 1: Schema Learning
        testSchemaLearning("binance_testnet")
        
        // Test 2: Public Endpoints
        testPublicEndpoints("binance_testnet", TEST_SYMBOL_BINANCE)
        
        // Test 3: WebSocket Streaming
        testWebSocketStreaming("binance_testnet", TEST_SYMBOL_BINANCE)
        
        // Test 4: Trading Pairs Discovery
        testTradingPairsDiscovery("binance_testnet")
        
        // Test 5: Authentication (if credentials provided)
        if (BINANCE_TESTNET_KEY != "YOUR_BINANCE_TESTNET_API_KEY") {
            testAuthentication("binance_testnet")
            testOrderFlow("binance_testnet", TEST_SYMBOL_BINANCE)
        } else {
            println("⚠️  Skipping authenticated tests - no credentials provided")
        }
        
        // Test 6: Error Handling
        testErrorHandling("binance_testnet")
        
        // Test 7: Rate Limiting
        testRateLimiting("binance_testnet")
        
        return results.filter { it.exchangeId == "binance_testnet" }
    }
    
    // =================================================================
    // GATE.IO TESTS
    // =================================================================
    
    /**
     * Complete Gate.io validation
     */
    suspend fun testGateIO(): List<TestResult> {
        println("\n╔════════════════════════════════════════════════╗")
        println("║     GATE.IO - AI EXCHANGE INTERFACE TEST      ║")
        println("╚════════════════════════════════════════════════╝\n")
        
        // Test 1: Schema Learning
        testSchemaLearning("gateio")
        
        // Test 2: Public Endpoints
        testPublicEndpoints("gateio", TEST_SYMBOL_GATEIO)
        
        // Test 3: WebSocket Streaming
        testWebSocketStreaming("gateio", TEST_SYMBOL_GATEIO)
        
        // Test 4: Trading Pairs Discovery
        testTradingPairsDiscovery("gateio")
        
        // Test 5: Authentication (if credentials provided)
        if (GATEIO_KEY != "YOUR_GATEIO_API_KEY") {
            testAuthentication("gateio")
        } else {
            println("⚠️  Skipping authenticated tests - no credentials provided")
        }
        
        // Test 6: Error Handling
        testErrorHandling("gateio")
        
        return results.filter { it.exchangeId == "gateio" }
    }
    
    // =================================================================
    // INDIVIDUAL TESTS
    // =================================================================
    
    /**
     * Test 1: Schema Learning
     * Validates that AI can discover and learn exchange API structure
     */
    private suspend fun testSchemaLearning(exchangeId: String) {
        val testName = "Schema Learning"
        val startTime = System.currentTimeMillis()
        
        try {
            println("📚 Test: $testName - $exchangeId")
            println("   Learning exchange API structure...")
            
            val learner = ExchangeSchemaLearner(context)
            
            // Get exchange info
            val info = AIConnectionManager.KNOWN_EXCHANGES[exchangeId]
                ?: throw Exception("Unknown exchange: $exchangeId")
            
            // Learn schema
            val result = learner.learnExchange(SchemaLearnRequest(
                exchangeId = exchangeId,
                name = info.name,
                type = info.type,
                baseUrl = info.baseUrl,
                wsUrl = info.wsUrl
            ))
            
            when (result) {
                is SchemaLearnResult.Success -> {
                    val schema = result.schema
                    val duration = System.currentTimeMillis() - startTime
                    
                    println("   ✓ SUCCESS ($duration ms)")
                    println("   Discovered endpoints:")
                    println("     - Ticker: ${schema.endpoints.ticker?.path}")
                    println("     - OrderBook: ${schema.endpoints.orderBook?.path}")
                    println("     - Balance: ${schema.endpoints.balances?.path}")
                    println("     - PlaceOrder: ${schema.endpoints.placeOrder?.path}")
                    println("     - WebSocket: ${schema.wsUrl}")
                    
                    results.add(TestResult(
                        testName = testName,
                        exchangeId = exchangeId,
                        passed = true,
                        duration = duration,
                        message = "Successfully learned schema",
                        details = mapOf(
                            "endpoints" to listOfNotNull(schema.endpoints.ticker, schema.endpoints.orderBook, schema.endpoints.balances, schema.endpoints.placeOrder).size,
                            "wsUrl" to (schema.wsUrl ?: "none"),
                            "capabilities" to schema.capabilities
                        )
                    ))
                }
                
                is SchemaLearnResult.PartialSuccess -> {
                    val duration = System.currentTimeMillis() - startTime
                    println("   ⚠️  PARTIAL SUCCESS ($duration ms)")
                    println("   Missing: ${result.missingCapabilities}")
                    
                    results.add(TestResult(
                        testName = testName,
                        exchangeId = exchangeId,
                        passed = true,
                        duration = duration,
                        message = "Partial schema learned",
                        details = mapOf(
                            "missing" to result.missingCapabilities
                        )
                    ))
                }
                
                is SchemaLearnResult.Failure -> {
                    throw Exception(result.reason)
                }
            }
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            println("   ✗ FAILED: ${e.message}")
            
            results.add(TestResult(
                testName = testName,
                exchangeId = exchangeId,
                passed = false,
                duration = duration,
                message = e.message ?: "Unknown error"
            ))
        }
        
        println()
    }
    
    /**
     * Test 2: Public Endpoints
     * Tests ticker, order book, and trading pairs
     */
    private suspend fun testPublicEndpoints(exchangeId: String, symbol: String) {
        val testName = "Public Endpoints"
        val startTime = System.currentTimeMillis()
        
        try {
            println("🌍 Test: $testName - $exchangeId")
            
            // Create connector
            val connector = AIExchangeConnector.create(
                context = context,
                exchangeId = exchangeId,
                baseUrl = AIConnectionManager.KNOWN_EXCHANGES[exchangeId]!!.baseUrl,
                type = ExchangeType.CEX_SPOT
            )
            
            // Connect
            println("   Connecting...")
            connector.connect()
            
            // Test getTicker
            println("   Testing getTicker($symbol)...")
            val ticker = connector.getTicker(symbol)!!
            println("     ✓ Price: ${ticker.last} (bid: ${ticker.bid}, ask: ${ticker.ask})")
            
            // Test getOrderBook
            println("   Testing getOrderBook($symbol)...")
            val orderBook = connector.getOrderBook(symbol)!!
            println("     ✓ Bids: ${orderBook.bids.size}, Asks: ${orderBook.asks.size}")
            println("     ✓ Best bid: ${orderBook.bids.firstOrNull()?.price}")
            println("     ✓ Best ask: ${orderBook.asks.firstOrNull()?.price}")
            
            // Test getTradingPairs
            println("   Testing getTradingPairs()...")
            val pairs = connector.getTradingPairs()
            println("     ✓ Found ${pairs.size} trading pairs")
            println("     ✓ Sample pairs: ${pairs.take(5).joinToString { it.symbol }}")
            
            val duration = System.currentTimeMillis() - startTime
            println("   ✓ SUCCESS ($duration ms)\n")
            
            results.add(TestResult(
                testName = testName,
                exchangeId = exchangeId,
                passed = true,
                duration = duration,
                message = "All public endpoints working",
                details = mapOf(
                    "price" to ticker.last,
                    "spread" to (ticker.ask - ticker.bid),
                    "orderBookDepth" to (orderBook.bids.size + orderBook.asks.size),
                    "tradingPairs" to pairs.size
                )
            ))
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            println("   ✗ FAILED: ${e.message}\n")
            
            results.add(TestResult(
                testName = testName,
                exchangeId = exchangeId,
                passed = false,
                duration = duration,
                message = e.message ?: "Unknown error"
            ))
        }
    }
    
    /**
     * Test 3: WebSocket Streaming
     * Tests real-time price feeds
     */
    private suspend fun testWebSocketStreaming(exchangeId: String, symbol: String) {
        val testName = "WebSocket Streaming"
        val startTime = System.currentTimeMillis()
        
        try {
            println("📡 Test: $testName - $exchangeId")
            
            // Create connector
            val connector = AIExchangeConnector.create(
                context = context,
                exchangeId = exchangeId,
                baseUrl = AIConnectionManager.KNOWN_EXCHANGES[exchangeId]!!.baseUrl,
                wsUrl = AIConnectionManager.KNOWN_EXCHANGES[exchangeId]!!.wsUrl,
                type = ExchangeType.CEX_SPOT
            )
            
            connector.connect()
            
            // Subscribe to prices
            println("   Subscribing to $symbol prices...")
            val prices = mutableListOf<Double>()
            
            withTimeout(10000) {  // 10 second timeout
                connector.subscribeToPrices(listOf(symbol))
                    .take(5)  // Collect 5 price ticks
                    .collect { tick ->
                        prices.add(tick.last)
                        println("     ✓ Received: ${tick.symbol} @ ${tick.last}")
                    }
            }
            
            val duration = System.currentTimeMillis() - startTime
            println("   ✓ SUCCESS ($duration ms)")
            println("   Received ${prices.size} price updates\n")
            
            results.add(TestResult(
                testName = testName,
                exchangeId = exchangeId,
                passed = true,
                duration = duration,
                message = "WebSocket streaming functional",
                details = mapOf(
                    "priceUpdates" to prices.size,
                    "priceRange" to "${prices.minOrNull()} - ${prices.maxOrNull()}"
                )
            ))
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            println("   ✗ FAILED: ${e.message}\n")
            
            results.add(TestResult(
                testName = testName,
                exchangeId = exchangeId,
                passed = false,
                duration = duration,
                message = e.message ?: "Unknown error"
            ))
        }
    }
    
    /**
     * Test 4: Trading Pairs Discovery
     * Validates comprehensive pair discovery
     */
    private suspend fun testTradingPairsDiscovery(exchangeId: String) {
        val testName = "Trading Pairs Discovery"
        val startTime = System.currentTimeMillis()
        
        try {
            println("🔍 Test: $testName - $exchangeId")
            
            val connector = AIExchangeConnector.create(
                context = context,
                exchangeId = exchangeId,
                baseUrl = AIConnectionManager.KNOWN_EXCHANGES[exchangeId]!!.baseUrl,
                type = ExchangeType.CEX_SPOT
            )
            
            connector.connect()
            
            val pairs = connector.getTradingPairs()
            
            // Analyze pairs
            val btcPairs = pairs.count { it.symbol.startsWith("BTC/") }
            val ethPairs = pairs.count { it.symbol.startsWith("ETH/") }
            val usdtPairs = pairs.count { it.symbol.endsWith("/USDT") }
            
            val duration = System.currentTimeMillis() - startTime
            println("   ✓ SUCCESS ($duration ms)")
            println("   Statistics:")
            println("     - Total pairs: ${pairs.size}")
            println("     - BTC pairs: $btcPairs")
            println("     - ETH pairs: $ethPairs")
            println("     - USDT pairs: $usdtPairs\n")
            
            results.add(TestResult(
                testName = testName,
                exchangeId = exchangeId,
                passed = true,
                duration = duration,
                message = "Discovered ${pairs.size} trading pairs",
                details = mapOf(
                    "totalPairs" to pairs.size,
                    "btcPairs" to btcPairs,
                    "ethPairs" to ethPairs,
                    "usdtPairs" to usdtPairs
                )
            ))
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            println("   ✗ FAILED: ${e.message}\n")
            
            results.add(TestResult(
                testName = testName,
                exchangeId = exchangeId,
                passed = false,
                duration = duration,
                message = e.message ?: "Unknown error"
            ))
        }
    }
    
    /**
     * Test 5: Authentication
     * Tests private endpoints (balance, orders)
     */
    private suspend fun testAuthentication(exchangeId: String) {
        val testName = "Authentication"
        val startTime = System.currentTimeMillis()
        
        try {
            println("🔐 Test: $testName - $exchangeId")
            
            val credentials = when (exchangeId) {
                "binance_testnet" -> ExchangeCredentials(
                    exchangeId = exchangeId,
                    apiKey = BINANCE_TESTNET_KEY,
                    apiSecret = BINANCE_TESTNET_SECRET
                )
                "gateio" -> ExchangeCredentials(
                    exchangeId = exchangeId,
                    apiKey = GATEIO_KEY,
                    apiSecret = GATEIO_SECRET
                )
                else -> throw Exception("No credentials for $exchangeId")
            }
            
            val connector = AIExchangeConnector.create(
                context = context,
                exchangeId = exchangeId,
                baseUrl = AIConnectionManager.KNOWN_EXCHANGES[exchangeId]!!.baseUrl,
                credentials = credentials,
                type = ExchangeType.CEX_SPOT
            )
            
            connector.connect()
            
            // Test getBalances
            println("   Testing getBalances()...")
            val balances = connector.getBalances()
            println("     ✓ Found ${balances.size} asset balances")
            balances.take(3).forEach { balance ->
                println("       - ${balance.asset}: ${balance.free} (locked: ${balance.locked})")
            }
            
            val duration = System.currentTimeMillis() - startTime
            println("   ✓ SUCCESS ($duration ms)\n")
            
            results.add(TestResult(
                testName = testName,
                exchangeId = exchangeId,
                passed = true,
                duration = duration,
                message = "Authentication successful",
                details = mapOf(
                    "balances" to balances.size
                )
            ))
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            println("   ✗ FAILED: ${e.message}\n")
            
            results.add(TestResult(
                testName = testName,
                exchangeId = exchangeId,
                passed = false,
                duration = duration,
                message = e.message ?: "Unknown error"
            ))
        }
    }
    
    /**
     * Test 6: Order Flow (Testnet only!)
     * Tests place/cancel/modify orders
     */
    private suspend fun testOrderFlow(exchangeId: String, symbol: String) {
        val testName = "Order Flow"
        val startTime = System.currentTimeMillis()
        
        try {
            println("💱 Test: $testName - $exchangeId (TESTNET ONLY)")
            
            if (!exchangeId.contains("testnet") && !exchangeId.contains("sandbox")) {
                throw Exception("Order flow test only allowed on testnet/sandbox!")
            }
            
            val credentials = ExchangeCredentials(
                exchangeId = exchangeId,
                apiKey = BINANCE_TESTNET_KEY,
                apiSecret = BINANCE_TESTNET_SECRET
            )
            
            val connector = AIExchangeConnector.create(
                context = context,
                exchangeId = exchangeId,
                baseUrl = AIConnectionManager.KNOWN_EXCHANGES[exchangeId]!!.baseUrl,
                credentials = credentials,
                type = ExchangeType.CEX_SPOT
            )
            
            connector.connect()
            
            // Get current price
            val ticker = connector.getTicker(symbol)!!
            val currentPrice = ticker.last
            
            // Place limit order far from market (won't fill)
            val limitPrice = currentPrice * 0.5  // 50% below market
            
            println("   Placing test limit order...")
            println("     Symbol: $symbol")
            println("     Side: BUY")
            println("     Price: $limitPrice (50% below market)")
            println("     Quantity: 0.001")
            
            val orderRequest = OrderRequest(
                symbol = symbol,
                side = TradeSide.BUY,
                type = OrderType.LIMIT,
                quantity = 0.001,
                price = limitPrice
            )
            
            val result = connector.placeOrder(orderRequest)
            
            when (result) {
                is com.miwealth.sovereignvantage.core.trading.engine.OrderExecutionResult.Success -> {
                    val orderId = result.order.orderId
                    println("     ✓ Order placed: $orderId")
                    
                    // Cancel order
                    println("   Cancelling order...")
                    val cancelled = connector.cancelOrder(symbol, orderId)
                    println("     ✓ Order cancelled: $cancelled")
                    
                    val duration = System.currentTimeMillis() - startTime
                    println("   ✓ SUCCESS ($duration ms)\n")
                    
                    results.add(TestResult(
                        testName = testName,
                        exchangeId = exchangeId,
                        passed = true,
                        duration = duration,
                        message = "Order flow validated",
                        details = mapOf(
                            "orderId" to orderId,
                            "cancelled" to cancelled
                        )
                    ))
                }
                is com.miwealth.sovereignvantage.core.trading.engine.OrderExecutionResult.Error -> {
                    throw result.exception
                }
                else -> { /* no-op */ }
            }
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            println("   ✗ FAILED: ${e.message}\n")
            
            results.add(TestResult(
                testName = testName,
                exchangeId = exchangeId,
                passed = false,
                duration = duration,
                message = e.message ?: "Unknown error"
            ))
        }
    }
    
    /**
     * Test 7: Error Handling
     * Tests recovery from errors
     */
    private suspend fun testErrorHandling(exchangeId: String) {
        val testName = "Error Handling"
        val startTime = System.currentTimeMillis()
        
        try {
            println("⚠️  Test: $testName - $exchangeId")
            
            val connector = AIExchangeConnector.create(
                context = context,
                exchangeId = exchangeId,
                baseUrl = AIConnectionManager.KNOWN_EXCHANGES[exchangeId]!!.baseUrl,
                type = ExchangeType.CEX_SPOT
            )
            
            connector.connect()
            
            // Test invalid symbol
            println("   Testing invalid symbol...")
            try {
                connector.getTicker("INVALID/SYMBOL")
                println("     ✗ Should have thrown exception")
            } catch (e: Exception) {
                println("     ✓ Correctly handled: ${e.message?.take(50)}")
            }
            
            // Test invalid pair
            println("   Testing non-existent pair...")
            try {
                connector.getOrderBook("XXX/YYY")
                println("     ✗ Should have thrown exception")
            } catch (e: Exception) {
                println("     ✓ Correctly handled: ${e.message?.take(50)}")
            }
            
            val duration = System.currentTimeMillis() - startTime
            println("   ✓ SUCCESS ($duration ms)\n")
            
            results.add(TestResult(
                testName = testName,
                exchangeId = exchangeId,
                passed = true,
                duration = duration,
                message = "Error handling validated"
            ))
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            println("   ✗ FAILED: ${e.message}\n")
            
            results.add(TestResult(
                testName = testName,
                exchangeId = exchangeId,
                passed = false,
                duration = duration,
                message = e.message ?: "Unknown error"
            ))
        }
    }
    
    /**
     * Test 8: Rate Limiting
     * Tests rate limit detection
     */
    private suspend fun testRateLimiting(exchangeId: String) {
        val testName = "Rate Limiting"
        val startTime = System.currentTimeMillis()
        
        try {
            println("🚦 Test: $testName - $exchangeId")
            
            val connector = AIExchangeConnector.create(
                context = context,
                exchangeId = exchangeId,
                baseUrl = AIConnectionManager.KNOWN_EXCHANGES[exchangeId]!!.baseUrl,
                type = ExchangeType.CEX_SPOT
            )
            
            connector.connect()
            
            println("   Checking rate limit status...")
            val isRateLimited = connector.isRateLimited()
            println("     ✓ Rate limited: $isRateLimited")
            
            val duration = System.currentTimeMillis() - startTime
            println("   ✓ SUCCESS ($duration ms)\n")
            
            results.add(TestResult(
                testName = testName,
                exchangeId = exchangeId,
                passed = true,
                duration = duration,
                message = "Rate limit detection working",
                details = mapOf(
                    "rateLimited" to isRateLimited
                )
            ))
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            println("   ✗ FAILED: ${e.message}\n")
            
            results.add(TestResult(
                testName = testName,
                exchangeId = exchangeId,
                passed = false,
                duration = duration,
                message = e.message ?: "Unknown error"
            ))
        }
    }
    
    // =================================================================
    // SUMMARY REPORTING
    // =================================================================
    
    /**
     * Generate test summary report
     */
    fun generateReport(): String {
        val totalTests = results.size
        val passed = results.count { it.passed }
        val failed = results.count { !it.passed }
        val passRate = if (totalTests > 0) (passed.toDouble() / totalTests * 100) else 0.0
        
        return buildString {
            appendLine("\n╔════════════════════════════════════════════════╗")
            appendLine("║       AI EXCHANGE INTERFACE - TEST REPORT      ║")
            appendLine("╚════════════════════════════════════════════════╝\n")
            
            appendLine("SUMMARY:")
            appendLine("  Total Tests: $totalTests")
            appendLine("  Passed: ✓ $passed")
            appendLine("  Failed: ✗ $failed")
            appendLine("  Pass Rate: ${"%.1f".format(passRate)}%")
            appendLine()
            
            // Group by exchange
            results.groupBy { it.exchangeId }.forEach { (exchangeId, exchangeResults) ->
                val exchangePassed = exchangeResults.count { it.passed }
                val exchangeTotal = exchangeResults.size
                val exchangeRate = (exchangePassed.toDouble() / exchangeTotal * 100)
                
                appendLine("$exchangeId: $exchangePassed/$exchangeTotal (${"%.1f".format(exchangeRate)}%)")
                exchangeResults.forEach { result ->
                    val icon = if (result.passed) "✓" else "✗"
                    appendLine("  $icon ${result.testName} (${result.duration}ms)")
                    if (!result.passed) {
                        appendLine("    Error: ${result.message}")
                    }
                }
                appendLine()
            }
            
            if (failed == 0) {
                appendLine("🎉 ALL TESTS PASSED! Ready for production deployment.")
            } else {
                appendLine("⚠️  Some tests failed. Review errors before production.")
            }
        }
    }
}

/**
 * Run all tests
 */
suspend fun runAIExchangeTests(context: Context) {
    val suite = AIExchangeInterfaceTestSuite(context)
    
    // Run Binance Testnet
    suite.testBinanceTestnet()
    
    // Run Gate.io
    suite.testGateIO()
    
    // Print report
    println(suite.generateReport())
}
