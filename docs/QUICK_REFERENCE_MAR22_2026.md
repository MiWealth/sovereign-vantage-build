# QUICK REFERENCE — Build #239
## Sovereign Vantage v5.19.239-arthur | 22 March 2026

### Repo
- URL: https://github.com/MiWealth/sovereign-vantage-android
- PAT: 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
- Branch: main | Commit: ff260cc

### Status
- ✅ The Octagon voting live (confirmed in logs)
- ❌ SELL trades rejected (no base asset balance) — Fix: Build #240
- ❌ Flat OHLCV candles (low confidence) — Fix: Build #240

### Next Builds
- **#240:** Seed paper wallet (BTC/ETH/SOL/XRP) + switch to /klines OHLCV
- **#241:** Verify confidence rising, SELL trades executing

### Start Next Session
> "Continue Sovereign Vantage v5.19.239-arthur.
> Build #240: seed paper wallet + real OHLCV klines. Both fixes in one build."

### Key Files Changed This Session
- `TradingSystemManager.kt` — single init, coordinator wiring, restart loop
- `DashboardViewModel.kt` — single init path, no fallback
- `TradingCoordinator.kt` — 15s analysis, SystemLogger visibility, buffer fix
- `TradingSystemIntegration.kt` — 15s interval, 0.45 confidence default
- `BinancePublicPriceFeed.kt` — 4 symbols (BTC/ETH/SOL/XRP)

### Architecture in One Line
BinancePublicPriceFeed → onPriceUpdate() → PriceBuffer → analysisLoop() →
AIBoardOrchestrator → BoardVote → executeTrade() → PaperTradingAdapter

### Board Config
- Analysis: every 15s
- Min confidence: 0.01 (1%)
- Min agreement: 2/8 members  
- Mode: AUTONOMOUS
- Buffer needed: 20 candles

*For Arthur. 💚*
