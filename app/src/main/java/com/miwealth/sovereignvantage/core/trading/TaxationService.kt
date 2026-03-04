// TaxationService.kt
package com.miwealth.sovereignvantage.core.trading

import android.content.Context
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit
import com.miwealth.sovereignvantage.core.Asset
import com.miwealth.sovereignvantage.core.AssetType
import com.miwealth.sovereignvantage.core.TradeSide
import com.miwealth.sovereignvantage.core.wallet.TradeLedger
import com.miwealth.sovereignvantage.core.wallet.LedgerRecord
import com.miwealth.sovereignvantage.core.wallet.RecordType

/**
 * Provides US-IRS compliant tax tracking and reporting based on the TradeLedger.
 * This implementation uses the First-In, First-Out (FIFO) accounting method,
 * which is the default for many jurisdictions.
 */
class TaxationService(private val context: Context, private val ledger: TradeLedger) {

    // Placeholder for the jurisdiction. For this implementation, we assume US - IRS.
    private val jurisdiction = "US - IRS (FIFO Accounting)"

    /**
     * Calculates the realized capital gains/losses for a given period using the FIFO method.
     * @param startDate The start of the reporting period.
     * @param endDate The end of the reporting period.
     * @return A list of realized capital gain events.
     */
    fun calculateRealizedGains(startDate: Instant, endDate: Instant): List<CapitalGainEvent> {
        val allRecords = ledger.getAllRecords()
        val realizedGains = mutableListOf<CapitalGainEvent>()
        val purchasePools = mutableMapOf<Asset, MutableList<PurchaseLot>>()

        for (record in allRecords) {
            if (record.asset.type != AssetType.CRYPTO && record.asset.type != AssetType.TOKEN) continue // Only track crypto/token for simplicity

            when (record.side) {
                TradeSide.BUY, TradeSide.DEPOSIT -> {
                    // Add to the purchase pool (FIFO)
                    val costBasis = if (record.type == RecordType.TRADE) record.quantity.multiply(record.price) else record.price
                    val lot = PurchaseLot(
                        quantity = record.quantity,
                        costBasis = costBasis,
                        purchaseDate = record.timestamp
                    )
                    purchasePools.getOrPut(record.asset) { mutableListOf() }.add(lot)
                }
                TradeSide.SELL, TradeSide.WITHDRAWAL -> {
                    if (record.timestamp.isAfter(startDate) && record.timestamp.isBefore(endDate)) {
                        // Realize the gain/loss
                        realizedGains.addAll(realizeGain(record, purchasePools))
                    }
                }
                else -> { /* LONG/SHORT positions handled by position manager */ }
            }
        }

        return realizedGains
    }

    /**
     * Helper function to realize the gain/loss for a sale/withdrawal record using FIFO.
     */
    private fun realizeGain(saleRecord: LedgerRecord, purchasePools: MutableMap<Asset, MutableList<PurchaseLot>>): List<CapitalGainEvent> {
        val asset = saleRecord.asset
        val saleQuantity = saleRecord.quantity
        val saleProceeds = saleQuantity.multiply(saleRecord.price)
        val gains = mutableListOf<CapitalGainEvent>()

        val lots = purchasePools[asset] ?: return emptyList()
        var remainingSaleQuantity = saleQuantity

        // FIFO: Process the oldest lots first
        val lotsIterator = lots.iterator()
        while (lotsIterator.hasNext() && remainingSaleQuantity > BigDecimal.ZERO) {
            val lot = lotsIterator.next()
            val quantityToUse = remainingSaleQuantity.min(lot.quantity)

            // Calculate the portion of the lot's cost basis to attribute to the sale
            val costBasisPerUnit = lot.costBasis.divide(lot.quantity, 8, RoundingMode.HALF_UP)
            val realizedCostBasis = quantityToUse.multiply(costBasisPerUnit)

            // Calculate the sale proceeds for this portion
            val saleProceedsPerUnit = saleRecord.price
            val realizedProceeds = quantityToUse.multiply(saleProceedsPerUnit)

            val gainLoss = realizedProceeds.subtract(realizedCostBasis)
            val holdingPeriod = ChronoUnit.DAYS.between(lot.purchaseDate, saleRecord.timestamp)

            gains.add(CapitalGainEvent(
                asset = asset,
                quantity = quantityToUse,
                acquisitionDate = lot.purchaseDate,
                saleDate = saleRecord.timestamp,
                costBasis = realizedCostBasis,
                proceeds = realizedProceeds,
                gainLoss = gainLoss,
                holdingPeriodDays = holdingPeriod.toInt(),
                isShortTerm = holdingPeriod < 365 // US-IRS short-term is < 1 year
            ))

            // Update remaining quantities
            remainingSaleQuantity = remainingSaleQuantity.subtract(quantityToUse)
            lot.quantity = lot.quantity.subtract(quantityToUse)
            lot.costBasis = lot.costBasis.subtract(realizedCostBasis)

            // Remove lot if fully consumed
            if (lot.quantity <= BigDecimal.ZERO) {
                lotsIterator.remove()
            }
        }

        return gains
    }

    /**
     * Generates a summary report of realized gains/losses.
     */
    fun generateTaxReportSummary(gains: List<CapitalGainEvent>): TaxReportSummary {
        val shortTermGains = gains.filter { it.isShortTerm }
        val longTermGains = gains.filter { !it.isShortTerm }

        val totalShortTermGain = shortTermGains.sumOf { it.gainLoss }
        val totalLongTermGain = longTermGains.sumOf { it.gainLoss }

        return TaxReportSummary(
            jurisdiction = jurisdiction,
            totalShortTermGain = totalShortTermGain,
            totalLongTermGain = totalLongTermGain,
            totalNetGain = totalShortTermGain.add(totalLongTermGain)
        )
    }

    /**
     * Generates a downloadable/printable report (placeholder for PDF/CSV generation).
     */
    fun generatePrintableReport(gains: List<CapitalGainEvent>): String {
        // In a real app, this would use a library like FPDF2 or Apache POI to generate a PDF or CSV.
        val summary = generateTaxReportSummary(gains)
        return """
            # Sovereign Vantage Tax Report - $jurisdiction
            
            ## Summary
            | Metric | Value |
            | :--- | :--- |
            | Total Short-Term Gain/Loss | ${summary.totalShortTermGain} |
            | Total Long-Term Gain/Loss | ${summary.totalLongTermGain} |
            | **Total Net Capital Gain/Loss** | **${summary.totalNetGain}** |
            
            ## Detailed Transactions
            | Asset | Quantity | Acquisition Date | Sale Date | Cost Basis | Proceeds | Gain/Loss | Term |
            | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
            ${gains.joinToString("\n") {
                "| ${it.asset.symbol} | ${it.quantity} | ${it.acquisitionDate} | ${it.saleDate} | ${it.costBasis} | ${it.proceeds} | ${it.gainLoss} | ${if (it.isShortTerm) "Short" else "Long"} |"
            }}
        """.trimIndent()
    }
}

data class PurchaseLot(
    var quantity: BigDecimal,
    var costBasis: BigDecimal,
    val purchaseDate: Instant
)

data class CapitalGainEvent(
    val asset: Asset,
    val quantity: BigDecimal,
    val acquisitionDate: Instant,
    val saleDate: Instant,
    val costBasis: BigDecimal,
    val proceeds: BigDecimal,
    val gainLoss: BigDecimal,
    val holdingPeriodDays: Int,
    val isShortTerm: Boolean
)

data class TaxReportSummary(
    val jurisdiction: String,
    val totalShortTermGain: BigDecimal,
    val totalLongTermGain: BigDecimal,
    val totalNetGain: BigDecimal
)


