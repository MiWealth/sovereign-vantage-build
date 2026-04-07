package com.miwealth.sovereignvantage.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * BUILD #412: Database Migration 6 → 7
 * 
 * FIXES: Position key duplicate symbol prefix bug from BUILD #411
 * 
 * Problem: Positions created before BUILD #411 have keys like:
 *   "BTC/USDT_BTC/USDT-BUY-1775516422180" (symbol duplicated)
 * 
 * Should be:
 *   "BTC/USDT-BUY-1775516422180" (correct format)
 * 
 * This migration strips the duplicate symbol prefix from all position IDs.
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // BUILD #412: Fix duplicate symbol prefix in position IDs
        
        // Step 1: Get all positions with duplicate symbol prefix
        val cursor = database.query(
            "SELECT id, symbol FROM positions WHERE id LIKE symbol || '_%'"
        )
        
        val positionsToFix = mutableListOf<Pair<String, String>>()
        
        while (cursor.moveToNext()) {
            val oldId = cursor.getString(0)
            val symbol = cursor.getString(1)
            
            // Check if ID starts with "SYMBOL_SYMBOL-"
            if (oldId.startsWith("${symbol}_${symbol}-")) {
                // Strip the first symbol prefix
                val newId = oldId.removePrefix("${symbol}_")
                positionsToFix.add(oldId to newId)
            }
        }
        cursor.close()
        
        // Step 2: Update each position with fixed ID
        var fixedCount = 0
        for ((oldId, newId) in positionsToFix) {
            try {
                // Update position ID
                database.execSQL(
                    "UPDATE positions SET id = ? WHERE id = ?",
                    arrayOf(newId, oldId)
                )
                fixedCount++
            } catch (e: Exception) {
                android.util.Log.e(
                    "Migration_6_7",
                    "Failed to fix position $oldId → $newId: ${e.message}"
                )
            }
        }
        
        android.util.Log.i(
            "Migration_6_7",
            "✅ BUILD #412: Fixed $fixedCount of ${positionsToFix.size} position keys with duplicate symbol prefix"
        )
    }
}
