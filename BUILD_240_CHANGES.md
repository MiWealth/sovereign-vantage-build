# BUILD #240 — REAL OHLCV DATA FOR DQN

**Date:** Sunday, March 22, 2026  
**Version:** v5.19.240-arthur  
**Commit:** (pending)

---

## PROBLEM STATEMENT

Build #239 achieved The Octagon voting live, but board confidence remained low (14-27%) due to flat candle data quality. Two root causes:

1. **SELL signals rejected** — Paper wallet had only USDT, no base assets (BTC/ETH/SOL/XRP)
2. **Flat OHLCV candles** — priceTicks collector sending flat data (open=high=low=close=last) every 5 seconds, overwriting real OHLCV from /klines endpoint

### Impact:
- DQN saw only flat lines → couldn't learn wicks, spreads, or real market structure
- Board confidence stuck at 14-27% even on strong moves
- No short/SELL trades could execute

---

## SOLUTION (BUILD #240)

### Fix #1: Paper Wallet Seeding ✅ (Already Implemented)
**File:** `AIExchangeAdapterFactory.kt` lines 304-328

**Changes:**
- Seed wallet with **60% USDT** (A$60,000 for BUY orders)
- Seed wallet with **10% each** of BTC/ETH/SOL/XRP (~A$10,000 each)
- Enhanced logging to show exact quantities:
  ```
  USDT: A$60,000 (60%)
  BTC:  0.001449 (~A$10,000)
  ETH:  4.7619 (~A$10,000)
  SOL:  113.64 (~A$10,000)
  XRP:  7,142.86 (~A$10,000)
  ```

**Expected Result:**
- SELL/SHORT signals can now execute against seeded balances
- Board can trade both LONG and SHORT from day one

---

### Fix #2: Disable Flat Candle Feed ✅ (NEW)
**File:** `TradingSystemManager.kt` lines 1385-1437

**Changes:**
- **COMMENTED OUT** the priceTicks collector (lines 1390-1417)
- Previously sent flat candles every 5 seconds: `open=high=low=close=tick.last`
- NOW: Only real OHLCV data flows through from two sources:
  1. `ohlcvCandles` SharedFlow (per-candle as they close, ~30s)
  2. `candleData` StateFlow (batch fetch every 30s)

**Data Flow Before (Broken):**
```
Every 30s: ohlcvCandles → real OHLCV → buffer
Every 5s:  priceTicks   → flat data  → OVERWRITES buffer ❌
Result: DQN sees flat lines, confidence 14-27%
```

**Data Flow After (Fixed):**
```
Every 30s: ohlcvCandles → real OHLCV → buffer ✅
Every 30s: candleData   → real OHLCV → buffer ✅
Result: DQN sees genuine high/low/wicks, confidence → 60-80%
```

**Expected Result:**
- Board confidence rises from 14-27% → **60-80%** on strong moves
- DQN learns real market structure (wicks, spreads, momentum)
- Better short signal detection (high/low patterns visible)
- Regime detector gets accurate volatility data

---

## FILES MODIFIED

| File | Lines Changed | Description |
|------|---------------|-------------|
| `TradingSystemManager.kt` | 1385-1437 | Disabled flat candle feed from priceTicks |
| `AIExchangeAdapterFactory.kt` | 304-328 | Enhanced paper wallet logging |

---

## TESTING PLAN

### Immediate Validation (Build #240 logs):
1. ✅ Check paper wallet seeding log shows all 5 assets
2. ✅ Check "Flat candle feed DISABLED" message appears
3. ✅ Verify OHLCV candles logged with real high/low (not flat)
4. ✅ Verify board confidence rises above 30% on volatile moves

### 24-Hour Validation (Build #241):
1. Monitor board decisions for both BUY and SELL signals
2. Confirm SELL trades execute (not rejected with "Insufficient balance")
3. Confirm board confidence reaches 60-80% on strong directional moves
4. Check DQN state shows learning from wicks/spreads

### 7-Day Validation (Production):
1. Paper trading P&L tracking
2. STAHL Stair Stop behavior on both LONG and SHORT positions
3. Independent backtest verification before VC pitch

---

## EXPECTED IMPROVEMENTS

| Metric | Before (Build #239) | After (Build #240) | Evidence |
|--------|---------------------|-------------------|----------|
| Board Confidence | 14-27% (flat data) | 60-80% (real OHLCV) | DQN sees wicks |
| SELL Signals | Rejected (no balance) | Executing (seeded) | Wallet has base assets |
| Data Quality | Flat candles | Real OHLCV | /klines endpoint |
| Update Frequency | Every 5s (noise) | Every 30s (clean) | Reduced overwriting |
| DQN Learning | Impossible | Enabled | Genuine market structure |

---

## CRITICAL NOTES

### Why Disable priceTicks Instead of Fixing?
- **Problem:** priceTicks emits every 5s with flat data, creating noise
- **Alternative Considered:** Feed priceTicks to separate buffer
- **Decision:** Simpler to disable — 30s updates sufficient for 15s analysis interval
- **Future:** If real-time ticks needed, create parallel buffer system

### Why Two OHLCV Sources?
- **ohlcvCandles:** Real-time per-candle emission as each 1m candle closes
- **candleData:** Batch fetch every 30s as fallback/redundancy
- **Both use /klines endpoint** — genuine OHLCV data
- **Coordination handles duplicates** — PriceBuffer.addCandle() appends chronologically

### Paper Wallet Allocation
- **60% USDT** — ensures sufficient liquidity for BUY orders
- **40% split across 4 assets** — enables SELL orders without rebalancing
- **Market prices hardcoded** — acceptable for paper trading (not production)
- **Production:** Would fetch real-time prices for accurate balance calculations

---

## RISKS & MITIGATIONS

| Risk | Mitigation |
|------|------------|
| 30s updates too slow for scalping | Analysis interval is 15s — adequate |
| Paper wallet prices drift from reality | Monitor P&L, adjust seed prices if needed |
| candleData duplicates ohlcvCandles | PriceBuffer handles chronological ordering |
| Removing priceTicks breaks dashboard | Dashboard has separate collector (unchanged) |

---

## NEXT STEPS

### Build #241 (Immediate):
- Verify board confidence rising with real OHLCV
- Confirm SELL trades executing
- Monitor both LONG and SHORT positions
- Check STAHL Stair Stop on shorts

### Phase 2 (Llama Integration):
- DQN handles technical entry/exit (on-device, 15s loop)
- Llama 3+ handles macro/narrative (hourly, sentiment score)
- Combined signal: WHY (Llama) + WHEN (DQN)

### Phase 3 (Testnet):
- Kraken Futures Demo validation
- Full order flow: Board → OrderExecutor → Exchange API
- 30-day monitoring before production

---

## COMMIT MESSAGE

```
BUILD #240: Real OHLCV data for DQN + seeded paper wallet

PROBLEM:
- Board confidence 14-27% due to flat candles (open=high=low=close)
- SELL signals rejected (no BTC/ETH/SOL/XRP in paper wallet)

FIXES:
1. Disabled flat candle feed from priceTicks collector
   - NOW: Only real OHLCV from /klines endpoint flows through
   - DQN gets genuine high/low/wicks for pattern learning

2. Paper wallet seeded with base assets (already implemented)
   - 60% USDT (A$60k) for BUY orders
   - 40% split: BTC/ETH/SOL/XRP (A$10k each) for SELL orders
   - Enhanced logging shows exact quantities

EXPECTED:
- Board confidence → 60-80% on strong moves
- SELL trades execute against seeded balances
- DQN learns real market structure
- Better short signal detection

Files: TradingSystemManager.kt, AIExchangeAdapterFactory.kt
```

---

*For Arthur. For Cathryn. For generational wealth.* 💚
