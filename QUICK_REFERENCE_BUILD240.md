# QUICK REFERENCE — Build #240
## Sovereign Vantage v5.19.240-arthur | 22 March 2026

### Repo
- URL: https://github.com/MiWealth/sovereign-vantage-android
- PAT: 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
- Branch: main | Commit: 1ec27e9

### Status
- ✅ Flat candle feed DISABLED — only real OHLCV data flows through
- ✅ Paper wallet seeded with BTC/ETH/SOL/XRP (60% USDT, 10% each asset)
- ⏳ Expected: Board confidence 60-80%, SELL trades executing

### The Fix
**Disabled:** priceTicks collector (flat candles every 5s)
**Active:** ohlcvCandles + candleData (real OHLCV every 30s)

### Expected Improvements
- Board confidence: 14-27% → **60-80%**
- SELL signals: Rejected → **Executing**
- DQN learning: Impossible → **Enabled**
- Data quality: Flat → **Real wicks/spreads**

### Files Changed
- `TradingSystemManager.kt` (lines 1385-1437) — disabled flat feed
- `AIExchangeAdapterFactory.kt` (lines 304-328) — enhanced logging

### Next Session
> "Continue Sovereign Vantage v5.19.240-arthur.
> Monitor CI results, verify board confidence rising."

### Key Metrics to Watch (Build #241 logs)
```
🕯️ BUILD #240: OHLCV candle: BTC/USDT O=68500 H=68700 L=68450 C=68650
   ↑ Real high/low spread (not flat) = SUCCESS
   
🎯 BOARD: BTC/USDT → BUY | conf=67% | agree=7/8
   ↑ Confidence >60% = SUCCESS
   
🚀 BUILD #240 TRADE EXECUTED [PAPER]: SELL 0.05 SOL/USDT @ $88.50
   ↑ SELL not rejected = SUCCESS
```

*For Arthur. 💚*
