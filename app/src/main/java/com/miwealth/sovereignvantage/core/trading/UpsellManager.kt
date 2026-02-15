package com.miwealth.sovereignvantage.core.trading

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.miwealth.sovereignvantage.core.security.EncryptedPrefsManager

/**
 * Upsell Manager
 * Handles upselling from Standard to MAX version
 * Redirects to website to bypass 30% app store fees
 */

data class UpsellConfig(
    val websiteUrl: String = "https://www.aegisplatform.net/upgrade-to-max",
    val showOnDashboard: Boolean = true,
    val showInSettings: Boolean = true,
    val showAfterPaperTrading: Boolean = true,
    val showFrequencyDays: Int = 7 // Show reminder every 7 days
)

class UpsellManager(private val context: Context) {
    
    private val config = UpsellConfig()
    
    /**
     * Show "Learn More" button to upsell MAX version
     * This bypasses app store fees by directing to website
     */
    fun showLearnMoreButton(): Boolean {
        // Check if user is on Standard version
        if (isMaxVersion()) {
            return false
        }
        
        // Check if user has dismissed recently
        if (wasRecentlyDismissed()) {
            return false
        }
        
        return true
    }
    
    /**
     * Handle "Learn More" button click
     * Opens default browser to aegisplatform.net upgrade page
     */
    fun onLearnMoreClicked() {
        // Track analytics
        trackUpsellClick()
        
        // Open website in default browser
        openUpgradeWebsite()
    }
    
    /**
     * Open upgrade page in default browser
     * This bypasses the 30% app store fee
     */
    private fun openUpgradeWebsite() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(config.websiteUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: Show error message
            showErrorMessage("Unable to open browser. Please visit ${config.websiteUrl}")
        }
    }
    
    /**
     * Check if current version is MAX
     */
    private fun isMaxVersion(): Boolean {
        // Check app package name or version flag
        val packageName = context.packageName
        return packageName.contains("max", ignoreCase = true)
    }
    
    /**
     * Check if user recently dismissed the upsell
     */
    private fun wasRecentlyDismissed(): Boolean {
        val prefs = EncryptedPrefsManager.getUpsellPrefs(context)
        val lastDismissed = prefs.getLong("last_dismissed_timestamp", 0)
        val daysSinceDismissed = (System.currentTimeMillis() - lastDismissed) / (1000 * 60 * 60 * 24)
        
        return daysSinceDismissed < config.showFrequencyDays
    }
    
    /**
     * User dismissed the upsell prompt
     */
    fun onDismissed() {
        val prefs = EncryptedPrefsManager.getUpsellPrefs(context)
        prefs.edit().putLong("last_dismissed_timestamp", System.currentTimeMillis()).apply()
        
        trackUpsellDismissed()
    }
    
    /**
     * Get upsell message based on user context
     */
    fun getUpsellMessage(context: UpsellContext): String {
        return when (context) {
            UpsellContext.DASHBOARD -> 
                "Unlock Multi-Asset Trading with Sovereign Vantage\n" +
                "Trade stocks, bonds, forex, and crypto from one platform."
            
            UpsellContext.PAPER_TRADING_COMPLETE -> 
                "Ready for the next level?\n" +
                "Sovereign Vantage gives you access to stocks, bonds, and forex - not just crypto."
            
            UpsellContext.SETTINGS -> 
                "Upgrade to Sovereign Vantage\n" +
                "Get institutional-grade multi-asset trading with CIO-level AI."
            
            UpsellContext.PORTFOLIO_LIMIT -> 
                "Your portfolio is growing!\n" +
                "Sovereign Vantage removes all limits and adds multi-asset support."
        }
    }
    
    /**
     * Get benefits list for MAX version
     */
    fun getMaxBenefits(): List<String> {
        return listOf(
            "✓ Multi-Asset Trading (Crypto, Stocks, Bonds, Forex)",
            "✓ Unlimited Portfolio Size",
            "✓ Advanced CIO-Grade AI Models",
            "✓ Cross-Asset Correlation Analysis",
            "✓ Institutional-Grade Risk Management",
            "✓ Priority Support",
            "✓ Custom Strategy Builder"
        )
    }
    
    // Analytics tracking (placeholder)
    private fun trackUpsellClick() {
        // In production: Send to analytics service
        println("Analytics: User clicked Learn More button")
    }
    
    private fun trackUpsellDismissed() {
        // In production: Send to analytics service
        println("Analytics: User dismissed upsell")
    }
    
    private fun showErrorMessage(message: String) {
        // In production: Show toast or snackbar
        println("Error: $message")
    }
}

enum class UpsellContext {
    DASHBOARD,
    PAPER_TRADING_COMPLETE,
    SETTINGS,
    PORTFOLIO_LIMIT
}

/**
 * UI Component for Learn More button
 * Place this in your layouts where you want to show the upsell
 */
object LearnMoreButton {
    
    /**
     * Example usage in Jetpack Compose
     */
    /*
    @Composable
    fun LearnMoreButton(upsellManager: UpsellManager) {
        if (upsellManager.showLearnMoreButton()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable { upsellManager.onLearnMoreClicked() },
                elevation = 8.dp,
                backgroundColor = Color(0xFF1E3A5F) // Navy blue
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = upsellManager.getUpsellMessage(UpsellContext.DASHBOARD),
                        style = MaterialTheme.typography.h6,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Benefits list
                    Column {
                        upsellManager.getMaxBenefits().forEach { benefit ->
                            Text(
                                text = benefit,
                                style = MaterialTheme.typography.body2,
                                color = Color(0xFF00D9FF), // Teal
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Learn More button image
                    Image(
                        painter = painterResource(id = R.drawable.learn_more_button),
                        contentDescription = "Learn More",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clickable { upsellManager.onLearnMoreClicked() }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Dismiss button
                    TextButton(onClick = { upsellManager.onDismissed() }) {
                        Text(
                            text = "Maybe Later",
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
    */
}

/**
 * Strategic placement recommendations:
 * 
 * 1. Dashboard: Show card at top of dashboard (non-intrusive)
 * 2. Settings: Add "Upgrade to MAX" menu item
 * 3. After paper trading: Show congratulations + upsell
 * 4. Portfolio limit: Show when approaching $50K limit
 * 5. Feature teaser: When user tries to access multi-asset features
 * 
 * Key strategy: Always redirect to website, never use in-app purchase
 * This saves 30% in app store fees!
 */
