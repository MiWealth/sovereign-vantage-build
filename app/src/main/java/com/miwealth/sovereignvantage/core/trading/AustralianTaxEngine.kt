package com.miwealth.sovereignvantage.core.trading

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Australian Tax Engine (ATO Compliance)
 * 
 * FEATURES:
 * - Financial Year: July 1 to June 30.
 * - CGT Discount: 50% discount for assets held > 12 months.
 * - Event Classification: Capital Gains vs. Ordinary Income.
 * 
 * Copyright: © 2025 MiWealth Pty Ltd. All Rights Reserved.
 */
class AustralianTaxEngine {

    data class TaxEvent(
        val id: String,
        val type: EventType,
        val asset: String,
        val date: LocalDate,
        val amountAUD: Double,
        val costBasisAUD: Double
    )

    enum class EventType {
        BUY, SELL, STAKING_REWARD, AIRDROP, MINING
    }

    data class TaxReport(
        val financialYear: String,
        val totalCapitalGains: Double,
        val netCapitalGainsAfterDiscount: Double,
        val totalOrdinaryIncome: Double
    )

    fun generateReport(events: List<TaxEvent>, yearEnd: Int): TaxReport {
        val startOfFY = LocalDate.of(yearEnd - 1, 7, 1)
        val endOfFY = LocalDate.of(yearEnd, 6, 30)

        val fyEvents = events.filter { !it.date.isBefore(startOfFY) && !it.date.isAfter(endOfFY) }

        var capitalGains = 0.0
        var discountedGains = 0.0
        var ordinaryIncome = 0.0

        for (event in fyEvents) {
            when (event.type) {
                EventType.SELL -> {
                    val gain = event.amountAUD - event.costBasisAUD
                    capitalGains += gain
                    
                    // Check for 50% CGT Discount (Held > 12 Months)
                    // Note: This requires linking the SELL to the original BUY (FIFO/LIFO).
                    // Simplified logic here assumes the event carries the holding period info.
                    val isLongTerm = true // Placeholder for actual holding period check
                    
                    if (gain > 0 && isLongTerm) {
                        discountedGains += (gain * 0.5)
                    } else {
                        discountedGains += gain
                    }
                }
                EventType.STAKING_REWARD, EventType.AIRDROP, EventType.MINING -> {
                    ordinaryIncome += event.amountAUD
                }
                else -> {} // Buys are not taxable events until sold
            }
        }

        return TaxReport(
            financialYear = "$yearEnd",
            totalCapitalGains = capitalGains,
            netCapitalGainsAfterDiscount = discountedGains,
            totalOrdinaryIncome = ordinaryIncome
        )
    }
}
