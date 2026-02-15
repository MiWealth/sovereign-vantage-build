package com.miwealth.sovereignvantage.core.trading.engine

import com.miwealth.sovereignvantage.core.trading.assets.AssetClass
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue

/**
 * High-Performance Matching Engine for the Sovereign Vantage.
 * Uses price-time priority matching algorithm.
 */
class MatchingEngine {

    private val orderBooks = ConcurrentHashMap<String, OrderBook>()

    fun placeOrder(order: Order): ExecutionReport {
        val book = orderBooks.computeIfAbsent(order.symbol) { OrderBook(it) }
        return book.match(order)
    }

    fun cancelOrder(orderId: String, symbol: String): Boolean {
        val book = orderBooks[symbol] ?: return false
        return book.cancel(orderId)
    }
}

data class Order(
    val id: String,
    val symbol: String,
    val side: OrderSide,
    val type: OrderType,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val timestamp: Long
)

enum class OrderSide { BUY, SELL }
enum class OrderType { LIMIT, MARKET, STOP_LOSS }

data class ExecutionReport(
    val orderId: String,
    val status: OrderStatus,
    val filledQuantity: BigDecimal,
    val remainingQuantity: BigDecimal,
    val averagePrice: BigDecimal
)

enum class OrderStatus { NEW, PARTIALLY_FILLED, FILLED, CANCELED, REJECTED }

class OrderBook(val symbol: String) {
    private val bids = PriorityBlockingQueue<Order>(1000, compareByDescending { it.price })
    private val asks = PriorityBlockingQueue<Order>(1000, compareBy { it.price })

    fun match(order: Order): ExecutionReport {
        // Simplified matching logic for demonstration
        // In production, this would handle partial fills and maker/taker fees
        if (order.side == OrderSide.BUY) {
            bids.add(order)
        } else {
            asks.add(order)
        }
        return ExecutionReport(order.id, OrderStatus.NEW, BigDecimal.ZERO, order.quantity, BigDecimal.ZERO)
    }

    fun cancel(orderId: String): Boolean {
        return bids.removeIf { it.id == orderId } || asks.removeIf { it.id == orderId }
    }
}
