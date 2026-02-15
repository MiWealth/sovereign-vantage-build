// AnalyticsDashboardActivity.kt

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.math.RoundingMode

class AnalyticsDashboardActivity : AppCompatActivity() {

    private lateinit var textViewCurrentCapital: TextView
    private lateinit var textViewTotalReturn: TextView
    private lateinit var textViewActiveTrades: TextView
    private lateinit var textViewSharpeRatio: TextView
    private lateinit var textViewSortinoRatio: TextView
    private lateinit var textViewMaxDrawdown: TextView
    private lateinit var textViewWinRate: TextView
    private lateinit var textViewActiveStrategy: TextView

    private lateinit var analyticsService: AnalyticsService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics_dashboard)

        analyticsService = AnalyticsService(this)

        // Initialize Views
        textViewCurrentCapital = findViewById(R.id.text_view_current_capital)
        textViewTotalReturn = findViewById(R.id.text_view_total_return)
        textViewActiveTrades = findViewById(R.id.text_view_active_trades)
        textViewSharpeRatio = findViewById(R.id.text_view_sharpe_ratio)
        textViewSortinoRatio = findViewById(R.id.text_view_sortino_ratio)
        textViewMaxDrawdown = findViewById(R.id.text_view_max_drawdown)
        textViewWinRate = findViewById(R.id.text_view_win_rate)
        textViewActiveStrategy = findViewById(R.id.text_view_active_strategy)

        loadDashboardData()
    }

    private fun loadDashboardData() {
        lifecycleScope.launch {
            // Load summary data
            val summary = analyticsService.getDashboardSummary()
            
            // Load history data (used to populate the detailed metrics)
            val history = analyticsService.getPerformanceHistory("30d")
            
            // Display Summary
            textViewCurrentCapital.text = "NAV: $${summary.currentCapital.setScale(2, RoundingMode.HALF_UP)}"
            
            val returnText = summary.totalReturnPercent.setScale(2, RoundingMode.HALF_UP).toString() + "%"
            textViewTotalReturn.text = "Total Return: $returnText"
            
            val color = if (summary.totalReturnPercent > java.math.BigDecimal.ZERO) getColor(R.color.scb_accent_green) else getColor(R.color.scb_accent_red)
            textViewTotalReturn.setTextColor(color)
            
            textViewActiveTrades.text = "Active Trades: ${summary.activeTrades} | Active Exchanges: ${summary.activeExchanges}"

            // Display Key Metrics (using the last snapshot from history for the most current metrics)
            val latestSnapshot = history.lastOrNull()
            if (latestSnapshot != null) {
                textViewSharpeRatio.text = latestSnapshot.sharpeRatio.setScale(2, RoundingMode.HALF_UP).toString()
                textViewSortinoRatio.text = latestSnapshot.sortinoRatio.setScale(2, RoundingMode.HALF_UP).toString()
                textViewMaxDrawdown.text = latestSnapshot.maxDrawdownPercent.setScale(2, RoundingMode.HALF_UP).toString() + "%"
                textViewWinRate.text = summary.lifetimeWinRate.setScale(2, RoundingMode.HALF_UP).toString() + "%"
                textViewActiveStrategy.text = latestSnapshot.activeStrategy
            }
        }
    }
}
