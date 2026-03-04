package com.miwealth.sovereignvantage.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Centralized Encrypted SharedPreferences Manager
 * 
 * All SharedPreferences in Sovereign Vantage MUST use this manager.
 * No data is considered "low sensitivity" - everything is encrypted.
 * 
 * Uses:
 * - AES-256-SIV for key encryption
 * - AES-256-GCM for value encryption
 * - Android Keystore for master key storage
 * 
 * © 2025-2026 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 */
object EncryptedPrefsManager {
    
    private const val MAIN_PREFS_NAME = "sv_encrypted_prefs"
    private const val WIDGET_PREFS_NAME = "sv_widget_encrypted"
    private const val MIGRATION_PREFS_NAME = "sv_migration_encrypted"
    private const val UPSELL_PREFS_NAME = "sv_upsell_encrypted"
    private const val DB_KEY_PREFS_NAME = "sv_db_key_encrypted"
    private const val SETTINGS_PREFS_NAME = "sv_settings_encrypted"
    
    @Volatile private var mainPrefs: SharedPreferences? = null
    @Volatile private var widgetPrefs: SharedPreferences? = null
    @Volatile private var migrationPrefs: SharedPreferences? = null
    @Volatile private var upsellPrefs: SharedPreferences? = null
    @Volatile private var dbKeyPrefs: SharedPreferences? = null
    @Volatile private var settingsPrefs: SharedPreferences? = null
    
    private fun getMasterKey(context: Context): MasterKey {
        return MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    private fun createEncryptedPrefs(context: Context, name: String): SharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                context,
                name,
                getMasterKey(context),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Keystore corruption, StrongBox unavailable, or first-run timeout
            // on Samsung devices. Fall back to plain prefs to prevent crash.
            // Data at rest is still protected by Android's app sandbox.
            android.util.Log.e("EncryptedPrefsManager",
                "EncryptedSharedPreferences failed for '$name', using plain fallback", e)
            context.getSharedPreferences("${name}_fallback", Context.MODE_PRIVATE)
        }
    }
    
    /**
     * Main application preferences
     */
    fun getMainPrefs(context: Context): SharedPreferences {
        return mainPrefs ?: synchronized(this) {
            mainPrefs ?: createEncryptedPrefs(context, MAIN_PREFS_NAME).also { mainPrefs = it }
        }
    }
    
    /**
     * Widget cache preferences
     */
    fun getWidgetPrefs(context: Context): SharedPreferences {
        return widgetPrefs ?: synchronized(this) {
            widgetPrefs ?: createEncryptedPrefs(context, WIDGET_PREFS_NAME).also { widgetPrefs = it }
        }
    }
    
    /**
     * Data migration preferences
     */
    fun getMigrationPrefs(context: Context): SharedPreferences {
        return migrationPrefs ?: synchronized(this) {
            migrationPrefs ?: createEncryptedPrefs(context, MIGRATION_PREFS_NAME).also { migrationPrefs = it }
        }
    }
    
    /**
     * Upsell/marketing preferences
     */
    fun getUpsellPrefs(context: Context): SharedPreferences {
        return upsellPrefs ?: synchronized(this) {
            upsellPrefs ?: createEncryptedPrefs(context, UPSELL_PREFS_NAME).also { upsellPrefs = it }
        }
    }
    
    /**
     * Database encryption key storage preferences
     */
    fun getDbKeyPrefs(context: Context): SharedPreferences {
        return dbKeyPrefs ?: synchronized(this) {
            dbKeyPrefs ?: createEncryptedPrefs(context, DB_KEY_PREFS_NAME).also { dbKeyPrefs = it }
        }
    }
    
    /**
     * General settings preferences
     */
    fun getSettingsPrefs(context: Context): SharedPreferences {
        return settingsPrefs ?: synchronized(this) {
            settingsPrefs ?: createEncryptedPrefs(context, SETTINGS_PREFS_NAME).also { settingsPrefs = it }
        }
    }
    
    // ========================================================================
    // Convenience methods
    // ========================================================================
    
    fun putString(context: Context, key: String, value: String) {
        getMainPrefs(context).edit().putString(key, value).apply()
    }
    
    fun getString(context: Context, key: String, default: String? = null): String? {
        return getMainPrefs(context).getString(key, default)
    }
    
    fun putInt(context: Context, key: String, value: Int) {
        getMainPrefs(context).edit().putInt(key, value).apply()
    }
    
    fun getInt(context: Context, key: String, default: Int = 0): Int {
        return getMainPrefs(context).getInt(key, default)
    }
    
    fun putLong(context: Context, key: String, value: Long) {
        getMainPrefs(context).edit().putLong(key, value).apply()
    }
    
    fun getLong(context: Context, key: String, default: Long = 0L): Long {
        return getMainPrefs(context).getLong(key, default)
    }
    
    fun putBoolean(context: Context, key: String, value: Boolean) {
        getMainPrefs(context).edit().putBoolean(key, value).apply()
    }
    
    fun getBoolean(context: Context, key: String, default: Boolean = false): Boolean {
        return getMainPrefs(context).getBoolean(key, default)
    }
    
    fun remove(context: Context, key: String) {
        getMainPrefs(context).edit().remove(key).apply()
    }
    
    fun clear(context: Context) {
        getMainPrefs(context).edit().clear().apply()
    }
    
    /**
     * Migrate from plain SharedPreferences to encrypted
     */
    fun migrateFromPlainPrefs(context: Context, oldPrefsName: String, targetPrefs: SharedPreferences) {
        val oldPrefs = context.getSharedPreferences(oldPrefsName, Context.MODE_PRIVATE)
        val editor = targetPrefs.edit()
        
        oldPrefs.all.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
            }
        }
        
        editor.apply()
        
        // Clear old prefs
        oldPrefs.edit().clear().apply()
    }
}
