# SOVEREIGN VANTAGE — QUICK REFERENCE CARD
## v5.19.240-arthur | 22 March 2026

---

## REPO
- **URL:** https://github.com/MiWealth/sovereign-vantage-android
- **Branch:** main | **Commit:** 081a91d
- **PAT:** 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
- **Package:** com.miwealth.sovereignvantage
- **Files:** 290 Kotlin | ~126,337 lines

## BUILD CI
https://github.com/MiWealth/sovereign-vantage-android/actions
Download APK from Artifacts after ~7 min build time

## BOARD STATUS (The Octagon)
✅ CONFIRMED VOTING LIVE as of Build #239
Members: Arthur (Chairman), Helena (CRO), Sentinel (CCO),
         Oracle (CDO), Nexus (COO), Marcus (CIO),
         Cipher (CSO), Aegis (Chief Defense)

## WHAT'S WORKING
✅ Foreground service (network alive in background)
✅ 4 symbols: BTC/USDT ETH/USDT SOL/USDT XRP/USDT
✅ collectors: 2 (Dashboard + Coordinator)
✅ Buffer filling correctly (onPriceUpdate → addCandle)
✅ Single coordinator instance (dual bug fixed)
✅ Analysis loop: 15s interval, AUTONOMOUS mode
✅ Real OHLCV klines (1-min candles, genuine wicks)
✅ Paper wallet: USDT + BTC/ETH/SOL/XRP seeded
✅ SELL signals can execute (base assets available)

## WHAT'S NEXT
#241 — Verify klines flowing; confirm buffer has real high≠low
#242 — Wire ohlcvCandles to coordinator (confirm real OHLCV used)
#243 — Short strategy: BEAR_TRENDING + SELL execution verified

## KEY NUMBERS
analysisIntervalMs = 15,000 ms
minConfidenceToTrade = 0.01
minBoardAgreement = 2
hasEnoughData = 20 candles (fills in ~20 min with klines)
cooldownAfterTrade = 30,000 ms
Paper wallet = A$100,000 total (70k USDT + 4×7.5k assets)

## BLOCKING ISSUES
None currently — Build #240 in CI now

## STAHL STAIR STOP™
- Sacred 3% initial stop loss (NEVER changes)
- 12 progressive levels
- Works for BOTH longs and shorts
- Provisional patent filed
- Contributed 103% of net profits in backtesting

## MIKE'S SETUP
- Device: Samsung Galaxy S22 Ultra (12GB RAM, 512GB)
- Android 16 (API 36)
- Termux for development tasks
- Files: /downloads/000 Claude/Files
- Recovering from spinal fusion surgery — take it easy!

---
*For Arthur. For Cathryn.* 💚
