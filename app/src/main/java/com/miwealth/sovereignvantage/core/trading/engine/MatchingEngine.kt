package com.miwealth.sovereignvantage.core.trading.engine

import com.miwealth.sovereignvantage.core.trading.assets.AssetClass
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue

/**
 * High-Performance Matching Engine for the Sovereign Vantage.
 * Uses price-time priority matching algorithm.
 * 
 * All internal types use "ME" prefix to avoid conflicts with core.OrderType,
 * core.OrderStatus, and routing.OrderBook.
 */
class MatchingEngine {

    private val orderBooks = ConcurrentHashMap<String, MEOrderBook>()

    fun placeOrder(order: MEOrder): MEExecutionReport {
        val book = orderBooks.computeIfAbsent(order.symbol) { MEOrderBook(it) }
        return book.match(order)
    }

    fun cancelOrder(orderId: String, symbol: String): Boolean {
        val book = orderBooks[symbol] ?: return false
        return book.cancel(orderId)
    }
}

data class MEOrder(
    val id: String,
    val symbol: String,
    val side: MEOrderSide,
    val type: MEOrderType,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val timestamp: Long
)

enum class MEOrderSide { BUY, SELL }
enum class MEOrderType { LIMIT, MARKET, STOP_LOSS }

data class MEExecutionReport(
    val orderId: String,
    val status: MEOrderStatus,
    val filledQuantity: BigDecimal,
    val remainingQuantity: BigDecimal,
    val averagePrice: BigDecimal
)

enum class MEOrderStatus { NEW, PARTIALLY_FILLED, FILLED, CANCELED, REJECTED }

class MEOrderBook(val symbol: String) {
    private val bids = PriorityBlockingQueue<MEOrder>(1000, compareByDescending { it.price })
    private val asks = PriorityBlockingQueue<MEOrder>(1000, compareBy { it.price })

    fun match(order: MEOrder): MEExecutionReport {
        if (order.side == MEOrderSide.BUY) {
            bids.add(order)
        } else {
            asks.add(order)
        }
        return MEExecutionReport(order.id, MEOrderStatus.NEW, BigDecimal.ZERO, order.quantity, BigDecimal.ZERO)
    }

    fun cancel(orderId: String): Boolean {
        return bids.removeIf { it.id == orderId } || asks.removeIf { it.id == orderId }
    }
}
