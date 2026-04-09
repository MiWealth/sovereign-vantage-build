package com.miwealth.sovereignvantage.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * BUILD #428: Database Migration 7 → 8
 * 
 * ADDS: board field to positions table for dual capital tracking
 * 
 * Purpose: Track which board (MAIN or HEDGE_FUND) created each position
 * so we can properly attribute P&L and margin usage to the correct capital pool.
 * 
 * New field:
 *   - board TEXT (nullable) - "MAIN" or "HEDGE_FUND"
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // BUILD #428: Add board field to positions table
        database.execSQL(
            "ALTER TABLE positions ADD COLUMN board TEXT DEFAULT NULL"
        )
        
        android.util.Log.i(
            "Migration_7_8",
            "✅ BUILD #428: Added 'board' column to positions table for dual capital tracking"
        )
    }
}
