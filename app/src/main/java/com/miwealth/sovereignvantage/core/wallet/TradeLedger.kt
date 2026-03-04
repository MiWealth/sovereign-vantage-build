// TradeLedger.kt
package com.miwealth.sovereignvantage.core.wallet

import android.content.Context
import com.miwealth.sovereignvantage.core.Asset
import com.miwealth.sovereignvantage.core.AssetType
import com.miwealth.sovereignvantage.core.TradeSide
import java.math.BigDecimal
import java.time.Instant

/**
 * A local, PQC-encrypted ledger for tracking all trade executions and asset transfers.
 * This is the foundation for Cost Basis tracking and performance reporting.
 */
class TradeLedger(private val context: Context) {

    // Placeholder for the local, encrypted database (e.g., SQLite or Room)
    private val records = mutableListOf<LedgerRecord>()

    /**
     * Records a trade execution from the ExecutionService.
     */
    fun recordTrade(
        asset: Asset,
        side: TradeSide,
        quantity: BigDecimal,
        price: BigDecimal,
        timestamp: Instant,
        source: String,
        transactionId: String
    ) {
        val record = LedgerRecord(
            type = RecordType.TRADE,
            asset = asset,
            side = side,
            quantity = quantity,
            price = price,
            timestamp = timestamp,
            source = source,
            transactionId = transactionId
        )
        records.add(record)
        println("TradeLedger: Recorded TRADE for ${asset.symbol}. ID: $transactionId")
    }

    /**
     * Records an external asset transfer (e.g., deposit from an exchange).
     * This is where the user must manually input the cost basis.
     */
    fun recordTransfer(
        asset: Asset,
        side: TradeSide, // DEPOSIT or WITHDRAWAL
        quantity: BigDecimal,
        timestamp: Instant,
        source: String,
        transactionId: String,
        costBasis: BigDecimal? = null // Cost basis is optional for transfers
    ) {
        val record = LedgerRecord(
            type = RecordType.TRANSFER,
            asset = asset,
            side = side,
            quantity = quantity,
            price = costBasis ?: BigDecimal.ZERO, // Use costBasis as price for transfers
            timestamp = timestamp,
            source = source,
            transactionId = transactionId
        )
        records.add(record)
        println("TradeLedger: Recorded TRANSFER for ${asset.symbol}. ID: $transactionId")
    }

    /**
     * Retrieves all records for a specific asset.
     */
    fun getRecordsForAsset(asset: Asset): List<LedgerRecord> {
        return records.filter { it.asset == asset }.sortedBy { it.timestamp }
    }

    /**
     * Retrieves all records.
     */
    fun getAllRecords(): List<LedgerRecord> {
        return records.sortedBy { it.timestamp }
    }
}

data class LedgerRecord(
    val type: RecordType,
    val asset: Asset,
    val side: TradeSide,
    val quantity: BigDecimal,
    val price: BigDecimal, // Price per unit (for trades) or Cost Basis (for transfers)
    val timestamp: Instant,
    val source: String,
    val transactionId: String
)

enum class RecordType {
    TRADE, TRANSFER, SWAP
}


