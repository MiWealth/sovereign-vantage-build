// BacktestingActivity.kt

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

class BacktestingActivity : AppCompatActivity() {

    private lateinit var editTextSymbol: EditText
    private lateinit var editTextCapital: EditText
    private lateinit var buttonRunBacktest: Button
    private lateinit var textViewStatus: TextView
    private lateinit var textViewReturn: TextView
    private lateinit var textViewDrawdown: TextView
    private lateinit var textViewSharpe: TextView
    private lateinit var textViewWinrate: TextView

    private lateinit var backtestingEngine: BacktestingEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backtesting)

        backtestingEngine = BacktestingEngine(this)

        // Initialize Views
        editTextSymbol = findViewById(R.id.edit_text_symbol)
        editTextCapital = findViewById(R.id.edit_text_capital)
        buttonRunBacktest = findViewById(R.id.button_run_backtest)
        textViewStatus = findViewById(R.id.text_view_status)
        textViewReturn = findViewById(R.id.text_view_return)
        textViewDrawdown = findViewById(R.id.text_view_drawdown)
        textViewSharpe = findViewById(R.id.text_view_sharpe)
        textViewWinrate = findViewById(R.id.text_view_winrate)

        buttonRunBacktest.setOnClickListener {
            runBacktestSimulation()
        }
        
        // Initial state
        clearResults()
    }

    private fun clearResults() {
        textViewReturn.text = "Total Return: --"
        textViewDrawdown.text = "Max Drawdown: --"
        textViewSharpe.text = "Sharpe Ratio: --"
        textViewWinrate.text = "Win Rate: --"
    }

    private fun runBacktestSimulation() {
        val symbol = editTextSymbol.text.toString()
        val capitalString = editTextCapital.text.toString()

        if (symbol.isBlank() || capitalString.isBlank()) {
            textViewStatus.text = "Status: Error - Please enter symbol and capital."
            return
        }

        val startingCapital = try {
            BigDecimal(capitalString)
        } catch (e: NumberFormatException) {
            textViewStatus.text = "Status: Error - Invalid capital format."
            return
        }

        textViewStatus.text = "Status: Running simulation... Please wait."
        buttonRunBacktest.isEnabled = false
        clearResults()

        lifecycleScope.launch {
            try {
                // Assuming a fixed timeframe for simplicity in this example
                val report = backtestingEngine.runBacktest(symbol, "1h", startingCapital)
                displayReport(report)
                textViewStatus.text = "Status: Simulation complete. Data discarded."
            } catch (e: Exception) {
                textViewStatus.text = "Status: Error during backtest: ${e.message}"
                e.printStackTrace()
            } finally {
                buttonRunBacktest.isEnabled = true
            }
        }
    }

    private fun displayReport(report: BacktestReport) {
        val returnText = report.totalReturnPercent.setScale(2, RoundingMode.HALF_UP).toString() + "%"
        val drawdownText = report.maxDrawdownPercent.setScale(2, RoundingMode.HALF_UP).toString() + "%"
        val sharpeText = report.sharpeRatio.setScale(2, RoundingMode.HALF_UP).toString()
        val winrateText = report.winRate.setScale(2, RoundingMode.HALF_UP).toString() + "%"

        textViewReturn.text = "Total Return: $returnText"
        textViewDrawdown.text = "Max Drawdown: $drawdownText"
        textViewSharpe.text = "Sharpe Ratio: $sharpeText"
        textViewWinrate.text = "Win Rate: $winrateText"
        
        // Set color based on return
        val color = if (report.totalReturnPercent > BigDecimal.ZERO) getColor(R.color.scb_accent_green) else getColor(R.color.scb_accent_red)
        textViewReturn.setTextColor(color)
    }
}
