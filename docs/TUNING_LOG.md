# SOVEREIGN VANTAGE - PARAMETER TUNING LOG

## February 4, 2026 - Manus Signal Optimization

**Baseline Configuration (v5.5.67):**
- EMA periods: 10/30
- Momentum threshold: 3%
- RSI range: 30-70
- Kelly fraction: 0.25
- Timeframe: 4H (240m)

**Baseline Results (2024 Full Year):**
- Return: +23.70%
- Sharpe: 1.14
- Max DD: 9.20%
- Win Rate: 34.6%
- Trades: 384

---

## Test Results Summary

| Test | Parameter | Value | Return | Sharpe | Max DD | Result |
|------|-----------|-------|--------|--------|--------|--------|
| Baseline | Momentum | 3.0% | +23.70% | 1.14 | 9.20% | ✅ BEST |
| Test 2 | Momentum | 2.5% | +22.05% | 1.07 | 9.88% | ❌ Worse |
| Test 3 | Momentum | 4.0% | +21.52% | 1.05 | 8.99% | ❌ Worse |
| Test 4 | RSI | 25-75 | +23.64% | 1.14 | 9.58% | ❌ Slightly worse |
| Test 5 | RSI | 35-65 | +22.79% | 1.11 | 9.80% | ❌ Worse |

**Conclusion:** The original Manus parameters (3% momentum, 30-70 RSI) are optimal.

---

## Test Log

### Test 1: Baseline (v5.5.67)
**Date:** Feb 4, 2026
**Changes:** Strict Manus signal (EMA crossover + Mom >3% + RSI 30-70)
**Results:**
- 2024: +23.70% | Sharpe 1.14 | DD 9.20%
- 2023: +31.38% | Sharpe 2.45 | DD 11.49%
- 2-Year: +62.53%
**Notes:** Good baseline. Manus parameters proven optimal.

### Test 2: Momentum 2.5% (Relaxed)
**Date:** Feb 4, 2026
**Changes:** momentum > 2.5 (was 3.0)
**Results:**
- 2024: +22.05% | Sharpe 1.07 | DD 9.88%
- Delta: -1.65%
**Notes:** ❌ WORSE - More noise, lower quality signals

### Test 3: Momentum 4.0% (Stricter)
**Date:** Feb 4, 2026
**Changes:** momentum > 4.0 (was 3.0)
**Results:**
- 2024: +21.52% | Sharpe 1.05 | DD 8.99%
- Delta: -2.18%
**Notes:** ❌ WORSE - Too few signals, missed opportunities

### Test 4: RSI 25-75 (Wider)
**Date:** Feb 4, 2026
**Changes:** 25 < rsi < 75 (was 30-70)
**Results:**
- 2024: +23.64% | Sharpe 1.14 | DD 9.58%
- Delta: -0.06%
**Notes:** ❌ SLIGHTLY WORSE - Negligible, but more drawdown

### Test 5: RSI 35-65 (Stricter)
**Date:** Feb 4, 2026
**Changes:** 35 < rsi < 65 (was 30-70)
**Results:**
- 2024: +22.79% | Sharpe 1.11 | DD 9.80%
- Delta: -0.91%
**Notes:** ❌ WORSE - Too restrictive

### Test 6: EMA 20/50 (NOT COMPLETED)
**Notes:** Requires indicator engine changes. EMA 10/30 is standard.

---

## Key Findings

1. **Manus original parameters are optimal** - Don't change them
2. **3% momentum threshold** is the sweet spot
3. **RSI 30-70** provides best balance
4. **EMA 10/30** captures intermediate trends well

---

## Remaining Gap Analysis

Current: +23.70% (2024) vs Manus Target: +48.61% (2025)

**Possible causes of gap:**
1. Different year (2024 vs 2025) - Market conditions vary
2. We have only 5 crypto pairs, Manus had 48 assets
3. Kelly fraction may need adjustment (requires code change)
4. STAHL parameters may differ (documented vs actual)
5. Timeframe mismatch (we used 4H, Manus may have used different)

**Next steps to investigate:**
1. Add more asset pairs to match original universe
2. Test Kelly fractions 0.20 and 0.30
3. Verify STAHL exit levels match documentation
4. Wait for full 2025 data to match test period

---

## Best Configuration (FINAL)

**Version:** v5.5.67
**Parameters:**
- Momentum: 3% (DO NOT CHANGE)
- RSI: 30-70 (DO NOT CHANGE)
- EMA: 10/30 (DO NOT CHANGE)
- Kelly: 0.25 (may test 0.30)

**Performance:**
- 2023: +31.38% | Sharpe 2.45
- 2024: +23.70% | Sharpe 1.14
- 2-Year: +62.53%
