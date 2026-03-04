package com.miwealth.sovereignvantage.core.portfolio

import com.miwealth.sovereignvantage.data.local.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

/**
 * COST BASIS TRACKER
 * 
 * Manages tax lot tracking for capital gains calculation:
 * - FIFO (First In, First Out)
 * - LIFO (Last In, First Out)
 * - Specific Identification
 * 
 * Tracks:
 * - Acquisition cost per lot
 * - Holding period for long/short term determination
 * - Partial disposals
 * - Wash sale adjustments (future)
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */

// ============================================================================
// DATA CLASSES
// ============================================================================

data class DisposalRecord(
    val disposalDate: Long,
    val disposalTradeId: String,
    val quantity: Double,
    val proceeds: Double,
    val costBasisUsed: Double,
    val gainLoss: Double,
    val isLongTerm: Boolean
)

data class LotSelectionResult(
    val selectedLots: List<SelectedLot>,
    val totalQuantity: Double,
    val totalCostBasis: Double,
    val totalProceeds: Double,
    val totalGainLoss: Double,
    val shortTermGainLoss: Double,
    val longTermGainLoss: Double
)

data class SelectedLot(
    val lotId: String,
    val symbol: String,
    val quantityFromLot: Double,
    val costBasisFromLot: Double,
    val acquisitionDate: Long,
    val isLongTerm: Boolean,
    val proceedsAllocated: Double,
    val gainLoss: Double
)

data class OpenLotSummary(
    val symbol: String,
    val totalQuantity: Double,
    val totalCostBasis: Double,
    val averageCost: Double,
    val lots: List<TaxLotSummary>
)

data class TaxLotSummary(
    val lotId: String,
    val acquisitionDate: Long,
    val quantity: Double,
    val costBasis: Double,
    val costPerUnit: Double,
    val currentValue: Double?,
    val unrealizedGainLoss: Double?,
    val holdingDays: Int,
    val isLongTerm: Boolean
)

// ============================================================================
// COST BASIS TRACKER
// ============================================================================

class CostBasisTracker(
    private val taxLotDao: TaxLotDao,
    private val enhancedTradeDao: EnhancedTradeDao,
    private var method: TaxLotMethod = TaxLotMethod.FIFO,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    
    private val gson = Gson()
    
    companion object {
        const val LONG_TERM_DAYS = 365  // > 1 year for long-term treatment
        const val MILLIS_PER_DAY = 86_400_000L
    }
    
    // ========================================================================
    // CONFIGURATION
    // ========================================================================
    
    fun setMethod(newMethod: TaxLotMethod) {
        method = newMethod
    }
    
    fun getMethod(): TaxLotMethod = method
    
    // ========================================================================
    // LOT CREATION (ACQUISITIONS)
    // ========================================================================
    
    /**
     * Create a new tax lot from an acquisition (buy/long)
     */
    suspend fun createLot(
        tradeId: String,
        symbol: String,
        baseAsset: String,
        quantity: Double,
        price: Double,
        fees: Double,
        exchange: String
    ): TaxLotEntity {
        val lotId = "lot_${symbol}_${System.currentTimeMillis()}_${tradeId.takeLast(8)}"
        val costBasis = (quantity * price) + fees
        val costPerUnit = costBasis / quantity
        
        val lot = TaxLotEntity(
            id = lotId,
            symbol = symbol,
            baseAsset = baseAsset,
            acquisitionDate = System.currentTimeMillis(),
            acquisitionPrice = price,
            acquisitionQuantity = quantity,
            acquisitionCostBasis = costBasis,
            acquisitionTradeId = tradeId,
            remainingQuantity = quantity,
            remainingCostBasis = costBasis,
            costPerUnit = costPerUnit,
            disposalRecordsJson = null,
            totalDisposedQuantity = 0.0,
            totalProceeds = 0.0,
            totalGainLoss = 0.0,
            isFullyDisposed = false,
            isLongTerm = false,  // Will become true after 365 days
            exchange = exchange,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        taxLotDao.insertLot(lot)
        return lot
    }
    
    // ========================================================================
    // LOT DISPOSAL (SALES)
    // ========================================================================
    
    /**
     * Process a disposal (sell/short close) using configured method
     */
    suspend fun processDisposal(
        tradeId: String,
        symbol: String,
        quantity: Double,
        proceeds: Double,
        specificLotIds: List<String>? = null
    ): LotSelectionResult {
        return when (method) {
            TaxLotMethod.FIFO -> processFIFO(tradeId, symbol, quantity, proceeds)
            TaxLotMethod.LIFO -> processLIFO(tradeId, symbol, quantity, proceeds)
            TaxLotMethod.SPECIFIC_ID -> {
                if (specificLotIds.isNullOrEmpty()) {
                    throw IllegalArgumentException("Specific lot IDs required for SPECIFIC_ID method")
                }
                processSpecificId(tradeId, symbol, quantity, proceeds, specificLotIds)
            }
        }
    }
    
    /**
     * FIFO - First In, First Out
     * Oldest lots are sold first
     */
    private suspend fun processFIFO(
        tradeId: String,
        symbol: String,
        quantityToSell: Double,
        totalProceeds: Double
    ): LotSelectionResult {
        val openLots = taxLotDao.getOpenLotsForSymbolFIFO(symbol)
        return processLotsInOrder(tradeId, openLots, quantityToSell, totalProceeds)
    }
    
    /**
     * LIFO - Last In, First Out
     * Newest lots are sold first
     */
    private suspend fun processLIFO(
        tradeId: String,
        symbol: String,
        quantityToSell: Double,
        totalProceeds: Double
    ): LotSelectionResult {
        val openLots = taxLotDao.getOpenLotsForSymbolLIFO(symbol)
        return processLotsInOrder(tradeId, openLots, quantityToSell, totalProceeds)
    }
    
    /**
     * Specific Identification
     * User selects which lots to sell
     */
    private suspend fun processSpecificId(
        tradeId: String,
        symbol: String,
        quantityToSell: Double,
        totalProceeds: Double,
        specificLotIds: List<String>
    ): LotSelectionResult {
        val lots = specificLotIds.mapNotNull { taxLotDao.getLotById(it) }
            .filter { it.symbol == symbol && !it.isFullyDisposed }
        
        if (lots.isEmpty()) {
            throw IllegalArgumentException("No valid open lots found for specified IDs")
        }
        
        return processLotsInOrder(tradeId, lots, quantityToSell, totalProceeds)
    }
    
    /**
     * Process lots in given order until quantity is fulfilled
     */
    private suspend fun processLotsInOrder(
        tradeId: String,
        lots: List<TaxLotEntity>,
        quantityToSell: Double,
        totalProceeds: Double
    ): LotSelectionResult {
        var remainingQuantity = quantityToSell
        var remainingProceeds = totalProceeds
        val selectedLots = mutableListOf<SelectedLot>()
        val now = System.currentTimeMillis()
        val pricePerUnit = totalProceeds / quantityToSell
        
        for (lot in lots) {
            if (remainingQuantity <= 0) break
            if (lot.remainingQuantity <= 0) continue
            
            // Determine how much to take from this lot
            val quantityFromLot = minOf(lot.remainingQuantity, remainingQuantity)
            val proportionFromLot = quantityFromLot / quantityToSell
            val proceedsFromLot = totalProceeds * proportionFromLot
            
            // Cost basis for this portion
            val costBasisFromLot = lot.costPerUnit * quantityFromLot
            
            // Calculate gain/loss
            val gainLoss = proceedsFromLot - costBasisFromLot
            
            // Determine holding period
            val holdingDays = ((now - lot.acquisitionDate) / MILLIS_PER_DAY).toInt()
            val isLongTerm = holdingDays > LONG_TERM_DAYS
            
            // Create disposal record
            val disposalRecord = DisposalRecord(
                disposalDate = now,
                disposalTradeId = tradeId,
                quantity = quantityFromLot,
                proceeds = proceedsFromLot,
                costBasisUsed = costBasisFromLot,
                gainLoss = gainLoss,
                isLongTerm = isLongTerm
            )
            
            // Update lot
            val existingDisposals = lot.disposalRecordsJson?.let {
                gson.fromJson(it, Array<DisposalRecord>::class.java).toMutableList()
            } ?: mutableListOf()
            existingDisposals.add(disposalRecord)
            
            val newRemainingQuantity = lot.remainingQuantity - quantityFromLot
            val newRemainingCostBasis = lot.costPerUnit * newRemainingQuantity
            
            val updatedLot = lot.copy(
                remainingQuantity = newRemainingQuantity,
                remainingCostBasis = newRemainingCostBasis,
                disposalRecordsJson = gson.toJson(existingDisposals),
                totalDisposedQuantity = lot.totalDisposedQuantity + quantityFromLot,
                totalProceeds = lot.totalProceeds + proceedsFromLot,
                totalGainLoss = lot.totalGainLoss + gainLoss,
                isFullyDisposed = newRemainingQuantity <= 0.0001,  // Small tolerance
                updatedAt = now
            )
            
            taxLotDao.updateLot(updatedLot)
            
            // Track selection
            selectedLots.add(SelectedLot(
                lotId = lot.id,
                symbol = lot.symbol,
                quantityFromLot = quantityFromLot,
                costBasisFromLot = costBasisFromLot,
                acquisitionDate = lot.acquisitionDate,
                isLongTerm = isLongTerm,
                proceedsAllocated = proceedsFromLot,
                gainLoss = gainLoss
            ))
            
            remainingQuantity -= quantityFromLot
            remainingProceeds -= proceedsFromLot
        }
        
        // Verify we fulfilled the order
        if (remainingQuantity > 0.0001) {
            throw IllegalStateException(
                "Insufficient lots to cover disposal. " +
                "Needed: $quantityToSell, Available: ${quantityToSell - remainingQuantity}"
            )
        }
        
        // Calculate totals
        val totalCostBasis = selectedLots.sumOf { it.costBasisFromLot }
        val totalGainLoss = selectedLots.sumOf { it.gainLoss }
        val shortTermGainLoss = selectedLots.filter { !it.isLongTerm }.sumOf { it.gainLoss }
        val longTermGainLoss = selectedLots.filter { it.isLongTerm }.sumOf { it.gainLoss }
        
        return LotSelectionResult(
            selectedLots = selectedLots,
            totalQuantity = quantityToSell,
            totalCostBasis = totalCostBasis,
            totalProceeds = totalProceeds,
            totalGainLoss = totalGainLoss,
            shortTermGainLoss = shortTermGainLoss,
            longTermGainLoss = longTermGainLoss
        )
    }
    
    // ========================================================================
    // QUERIES
    // ========================================================================
    
    /**
     * Get open lots summary for a symbol
     */
    suspend fun getOpenLotsSummary(
        symbol: String,
        currentPrice: Double? = null
    ): OpenLotSummary? {
        val lots = when (method) {
            TaxLotMethod.FIFO -> taxLotDao.getOpenLotsForSymbolFIFO(symbol)
            TaxLotMethod.LIFO -> taxLotDao.getOpenLotsForSymbolLIFO(symbol)
            TaxLotMethod.SPECIFIC_ID -> taxLotDao.getOpenLotsForSymbolFIFO(symbol)
        }
        
        if (lots.isEmpty()) return null
        
        val now = System.currentTimeMillis()
        val totalQuantity = lots.sumOf { it.remainingQuantity }
        val totalCostBasis = lots.sumOf { it.remainingCostBasis }
        
        val lotSummaries = lots.map { lot ->
            val holdingDays = ((now - lot.acquisitionDate) / MILLIS_PER_DAY).toInt()
            val currentValue = currentPrice?.let { it * lot.remainingQuantity }
            val unrealizedGainLoss = currentValue?.let { it - lot.remainingCostBasis }
            
            TaxLotSummary(
                lotId = lot.id,
                acquisitionDate = lot.acquisitionDate,
                quantity = lot.remainingQuantity,
                costBasis = lot.remainingCostBasis,
                costPerUnit = lot.costPerUnit,
                currentValue = currentValue,
                unrealizedGainLoss = unrealizedGainLoss,
                holdingDays = holdingDays,
                isLongTerm = holdingDays > LONG_TERM_DAYS
            )
        }
        
        return OpenLotSummary(
            symbol = symbol,
            totalQuantity = totalQuantity,
            totalCostBasis = totalCostBasis,
            averageCost = if (totalQuantity > 0) totalCostBasis / totalQuantity else 0.0,
            lots = lotSummaries
        )
    }
    
    /**
     * Get all open lots across all symbols
     */
    suspend fun getAllOpenLots(
        currentPrices: Map<String, Double> = emptyMap()
    ): List<OpenLotSummary> {
        val allLots = taxLotDao.getOpenLots().first()
        val bySymbol = allLots.groupBy { it.symbol }
        
        return bySymbol.mapNotNull { (symbol, _) ->
            getOpenLotsSummary(symbol, currentPrices[symbol])
        }
    }
    
    /**
     * Preview disposal without executing
     */
    suspend fun previewDisposal(
        symbol: String,
        quantity: Double,
        estimatedProceeds: Double,
        specificLotIds: List<String>? = null
    ): LotSelectionResult {
        val lots = when {
            specificLotIds != null -> {
                specificLotIds.mapNotNull { taxLotDao.getLotById(it) }
                    .filter { it.symbol == symbol && !it.isFullyDisposed }
            }
            method == TaxLotMethod.FIFO -> taxLotDao.getOpenLotsForSymbolFIFO(symbol)
            method == TaxLotMethod.LIFO -> taxLotDao.getOpenLotsForSymbolLIFO(symbol)
            else -> taxLotDao.getOpenLotsForSymbolFIFO(symbol)
        }
        
        return simulateDisposal(lots, quantity, estimatedProceeds)
    }
    
    private fun simulateDisposal(
        lots: List<TaxLotEntity>,
        quantityToSell: Double,
        totalProceeds: Double
    ): LotSelectionResult {
        var remainingQuantity = quantityToSell
        val selectedLots = mutableListOf<SelectedLot>()
        val now = System.currentTimeMillis()
        
        for (lot in lots) {
            if (remainingQuantity <= 0) break
            if (lot.remainingQuantity <= 0) continue
            
            val quantityFromLot = minOf(lot.remainingQuantity, remainingQuantity)
            val proportionFromLot = quantityFromLot / quantityToSell
            val proceedsFromLot = totalProceeds * proportionFromLot
            val costBasisFromLot = lot.costPerUnit * quantityFromLot
            val gainLoss = proceedsFromLot - costBasisFromLot
            val holdingDays = ((now - lot.acquisitionDate) / MILLIS_PER_DAY).toInt()
            val isLongTerm = holdingDays > LONG_TERM_DAYS
            
            selectedLots.add(SelectedLot(
                lotId = lot.id,
                symbol = lot.symbol,
                quantityFromLot = quantityFromLot,
                costBasisFromLot = costBasisFromLot,
                acquisitionDate = lot.acquisitionDate,
                isLongTerm = isLongTerm,
                proceedsAllocated = proceedsFromLot,
                gainLoss = gainLoss
            ))
            
            remainingQuantity -= quantityFromLot
        }
        
        val totalCostBasis = selectedLots.sumOf { it.costBasisFromLot }
        val totalGainLoss = selectedLots.sumOf { it.gainLoss }
        val shortTermGainLoss = selectedLots.filter { !it.isLongTerm }.sumOf { it.gainLoss }
        val longTermGainLoss = selectedLots.filter { it.isLongTerm }.sumOf { it.gainLoss }
        
        return LotSelectionResult(
            selectedLots = selectedLots,
            totalQuantity = quantityToSell - remainingQuantity,
            totalCostBasis = totalCostBasis,
            totalProceeds = totalProceeds,
            totalGainLoss = totalGainLoss,
            shortTermGainLoss = shortTermGainLoss,
            longTermGainLoss = longTermGainLoss
        )
    }
    
    // ========================================================================
    // TAX OPTIMIZATION
    // ========================================================================
    
    /**
     * Suggest optimal lot selection to minimize taxes
     */
    suspend fun suggestTaxOptimalLots(
        symbol: String,
        quantity: Double,
        estimatedProceeds: Double,
        preferLongTerm: Boolean = true
    ): List<TaxLotSummary> {
        val lots = taxLotDao.getOpenLotsForSymbolFIFO(symbol)
        val now = System.currentTimeMillis()
        val pricePerUnit = estimatedProceeds / quantity
        
        return lots.map { lot ->
            val holdingDays = ((now - lot.acquisitionDate) / MILLIS_PER_DAY).toInt()
            val isLongTerm = holdingDays > LONG_TERM_DAYS
            val estimatedGainPerUnit = pricePerUnit - lot.costPerUnit
            
            TaxLotSummary(
                lotId = lot.id,
                acquisitionDate = lot.acquisitionDate,
                quantity = lot.remainingQuantity,
                costBasis = lot.remainingCostBasis,
                costPerUnit = lot.costPerUnit,
                currentValue = lot.remainingQuantity * pricePerUnit,
                unrealizedGainLoss = lot.remainingQuantity * estimatedGainPerUnit,
                holdingDays = holdingDays,
                isLongTerm = isLongTerm
            )
        }.sortedWith(compareBy(
            { if (preferLongTerm) !it.isLongTerm else it.isLongTerm },
            { -(it.costPerUnit) },
            { it.unrealizedGainLoss ?: 0.0 }
        ))
    }
    
    /**
     * Find lots approaching long-term status
     */
    suspend fun getLotsApproachingLongTerm(
        symbol: String? = null,
        daysThreshold: Int = 30
    ): List<TaxLotSummary> {
        val allLots = taxLotDao.getOpenLots().first()
        val filteredLots = symbol?.let { s -> allLots.filter { it.symbol == s } } ?: allLots
        val now = System.currentTimeMillis()
        
        return filteredLots.mapNotNull { lot ->
            val holdingDays = ((now - lot.acquisitionDate) / MILLIS_PER_DAY).toInt()
            val daysUntilLongTerm = LONG_TERM_DAYS - holdingDays
            
            if (daysUntilLongTerm in 1..daysThreshold) {
                TaxLotSummary(
                    lotId = lot.id,
                    acquisitionDate = lot.acquisitionDate,
                    quantity = lot.remainingQuantity,
                    costBasis = lot.remainingCostBasis,
                    costPerUnit = lot.costPerUnit,
                    currentValue = null,
                    unrealizedGainLoss = null,
                    holdingDays = holdingDays,
                    isLongTerm = false
                )
            } else null
        }.sortedBy { LONG_TERM_DAYS - it.holdingDays }
    }
    
    /**
     * Calculate unrealized gains/losses by term
     */
    suspend fun getUnrealizedGainsByTerm(
        currentPrices: Map<String, Double>
    ): Pair<Double, Double> {
        val allLots = taxLotDao.getOpenLots().first()
        val now = System.currentTimeMillis()
        
        var shortTermUnrealized = 0.0
        var longTermUnrealized = 0.0
        
        for (lot in allLots) {
            val currentPrice = currentPrices[lot.symbol] ?: continue
            val currentValue = lot.remainingQuantity * currentPrice
            val unrealizedGain = currentValue - lot.remainingCostBasis
            val holdingDays = ((now - lot.acquisitionDate) / MILLIS_PER_DAY).toInt()
            
            if (holdingDays > LONG_TERM_DAYS) {
                longTermUnrealized += unrealizedGain
            } else {
                shortTermUnrealized += unrealizedGain
            }
        }
        
        return Pair(shortTermUnrealized, longTermUnrealized)
    }
}
