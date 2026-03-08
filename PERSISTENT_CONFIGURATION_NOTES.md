# SOVEREIGN VANTAGE - PERSISTENT CONFIGURATION NOTES
## CRITICAL INFORMATION FOR ALL FUTURE SESSIONS

**Date Created:** March 8, 2026  
**Status:** PERMANENT - Include in all handoff documents  
**Version:** 5.19.146+

---

## ⚠️ RISK MANAGER CONFIGURATION - INTENTIONALLY RELAXED FOR TESTING

### CURRENT STATE (DO NOT CHANGE WITHOUT MIKE'S APPROVAL)

**RiskManager.kt** is configured with **INTENTIONALLY OPEN** limits for debugging:

```kotlin
data class RiskConfig(
    val maxDrawdownPercent: Double = 20.0,      // INTENTIONAL - For testing
    val dailyLossLimitPercent: Double = 60.0,    // INTENTIONAL - For testing  
    val maxPositionPercent: Double = 25.0,       // INTENTIONAL - For testing
    val maxTotalExposurePercent: Double = 100.0,
    val maxCorrelatedExposure: Double = 40.0,    
    val maxLeverage: Double = 3.0,               // INTENTIONAL - For testing
    val minCashReservePercent: Double = 10.0,
    val cooldownMinutes: Int = 15,
    val requireManualResetAfterKillSwitch: Boolean = true
)
```

### WHY THESE SETTINGS?

Mike has **deliberately opened risk limits** to permit full testing without premature kill switch activation:

1. **60% Daily Loss Limit** - Allows system to run without stopping during debugging
2. **Open Position Sizes** - Enables testing of position sizing algorithms
3. **Open Drawdown** - Permits observation of full system behavior

### WHEN WILL THIS CHANGE?

**These limits will be tightened when:**
- ✅ Trading system is proven to work
- ✅ Orders are executing correctly
- ✅ Positions are being managed properly
- ✅ AI Board decisions are validated

**Target Production Limits:**
```kotlin
// For general trading:
val maxDrawdownPercent: Double = 5.0        // Conservative
val dailyLossLimitPercent: Double = 5.0     // Conservative
val maxPositionPercent: Double = 10.0       // Conservative

// For Arbitrage & Alpha Scanner strategies:
val maxDrawdownPercent: Double = 10.0       // Moderate
val dailyLossLimitPercent: Double = 15.0    // Moderate
// (These strategies REQUIRE >5% room to function)
```

---

## 🎯 DUAL RISK MANAGER SYSTEM

### Two Risk Managers Exist:

1. **RiskManager.kt** (Currently Active)
   - Location: `core/trading/engine/RiskManager.kt`
   - Purpose: General portfolio risk management
   - Used by: TradingCoordinator
   - Settings: Currently RELAXED for testing

2. **StrategyRiskManager.kt** (Available but Not Active)
   - Location: `core/trading/strategies/StrategyRiskManager.kt`
   - Purpose: Per-strategy kill switches with auto-liquidation
   - Features: 5% kill switch, auto-liquidate to USDT
   - Settings: Conservative (5% drawdown, 3% daily loss)
   - Status: Complete but not wired to TradingCoordinator

### Future Architecture:

When production-ready, implement **dual risk manager system**:

```kotlin
enum class RiskManagerType {
    CONSERVATIVE,    // Use StrategyRiskManager (5% limits)
    MODERATE,        // Use RiskManager with 10% limits
    AGGRESSIVE,      // Use RiskManager with 15% limits
    TESTING          // Current state (60% limits)
}
```

Allow user to select risk profile:
- **CONSERVATIVE** → Most users, StrategyRiskManager
- **MODERATE** → Experienced traders
- **AGGRESSIVE** → Arbitrage & Alpha Scanner strategies
- **TESTING** → Development only (current state)

---

## 🔍 CURRENT DEBUGGING FOCUS

### Primary Issue: Trading May Not Be Starting

**Observation from Build #125:**
- System showed -30% loss
- BUT: Trading may have **never actually started**
- Loss may be from:
  - Initialization artifacts
  - UI display issues
  - Price feed simulation errors
  - NOT from actual trade execution

### Investigation Required:

1. ✅ Verify BinancePublicPriceFeed is running
2. ✅ Confirm prices are updating
3. ⏳ **CRITICAL:** Verify orders are actually being placed
4. ⏳ Check if OrderExecutor is executing or stubbed
5. ⏳ Confirm PaperTradingAdapter is processing orders
6. ⏳ Validate position tracking is working

### Key Questions:

- Are trade signals being generated?
- Are orders being submitted to orderExecutor?
- Is orderExecutor calling PaperTradingAdapter?
- Is PaperTradingAdapter updating balances?
- Is PositionManager tracking positions?

---

## 📋 TESTING PROTOCOL

### Before Tightening Risk Limits:

**Phase 1: Verify Order Execution** ⏳ IN PROGRESS
- [ ] Confirm orders are placed (not just logged)
- [ ] Verify PaperTradingAdapter receives orders
- [ ] Check balances update after trades
- [ ] Validate position tracking
- [ ] Confirm P&L calculations

**Phase 2: Validate Trading Logic**
- [ ] AI Board generates signals
- [ ] Signals pass risk checks
- [ ] Orders execute correctly
- [ ] Positions open and close
- [ ] STAHL Stair Stop™ works

**Phase 3: Performance Testing**
- [ ] Run 24-hour paper trading
- [ ] Collect full trade logs
- [ ] Calculate Sharpe ratio
- [ ] Measure win rate
- [ ] Analyze drawdown

**Phase 4: Risk System Validation**
- [ ] Manually trigger kill switch
- [ ] Verify cooldown period works
- [ ] Test manual restart
- [ ] Validate liquidation logic

**Phase 5: Production Deployment**
- [ ] Switch to conservative limits
- [ ] Enable StrategyRiskManager
- [ ] Add risk profile selector UI
- [ ] Document all settings

---

## 🚫 DO NOT DO (Without Mike's Approval):

1. **DO NOT** change risk limits from current 60% daily loss
2. **DO NOT** switch to StrategyRiskManager yet
3. **DO NOT** add kill switches that could interfere with testing
4. **DO NOT** assume trading is working (verify first)
5. **DO NOT** remove risk logging (critical for debugging)

---

## ✅ DO (Always):

1. **DO** investigate if trading is actually executing
2. **DO** verify each component is working (not just compiling)
3. **DO** collect comprehensive logs
4. **DO** test order flow end-to-end
5. **DO** document all findings in handoff docs
6. **DO** ask Mike before making risk-related changes

---

## 📊 ARBITRAGE & ALPHA SCANNER REQUIREMENTS

### Why These Strategies Need >5% Room:

**Arbitrage Trading:**
- Simultaneous long/short positions across exchanges
- Price discrepancies typically 0.5-2%
- Need room for slippage, fees, timing delays
- May show temporary -5% on one leg before completing
- **Minimum requirement:** 10% daily loss tolerance
- **Recommended:** 15% daily loss tolerance

**Alpha Factor Scanner:**
- Scans for statistical arbitrage opportunities
- Multiple concurrent positions
- Market-neutral strategy (hedged)
- Individual positions may move -5% while portfolio stays flat
- **Minimum requirement:** 10% daily loss tolerance
- **Recommended:** 15% daily loss tolerance

**Why 5% Kill Switch Would Break These:**
- Arbitrage leg A goes long +$1000
- Arbitrage leg B goes short -$1500 (temporary)
- Net position: -$500 (-5% on $10K portfolio)
- 5% kill switch → **LIQUIDATES EVERYTHING** ❌
- Leg A completes 2 seconds later → Would have made +$500 ✅
- **Strategy destroyed by premature kill switch**

---

## 🎓 DEVELOPMENT PHILOSOPHY

### Mike's Approach:

1. **Open limits during development** - See what breaks, find edge cases
2. **Comprehensive logging** - Every decision, every trade, every error
3. **Test in paper mode first** - No real money until proven
4. **Verify, don't assume** - Code compiling ≠ code working
5. **Tighten only when ready** - Conservative limits block testing

### This Is Correct:

- Better to see -30% loss in testing (with open limits)
- Than to have 5% kill switch hide a critical bug
- Once system is proven, THEN add safety rails
- Arbitrage/Alpha strategies get custom risk profiles

---

## 📝 HANDOFF CHECKLIST

Every session handoff document must include:

- [ ] Current build number and version
- [ ] Risk manager configuration status
- [ ] Whether trading is actually executing (verified, not assumed)
- [ ] Any changes to risk limits (require Mike approval)
- [ ] Link to this persistent notes document
- [ ] GitHub PAT: `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`
- [ ] Repository: `MiWealth/sovereign-vantage-android`
- [ ] Current branch: `master`

---

## 🔗 RELATED DOCUMENTS

- `SESSION_HANDOFF_BUILD126.md` - Last major session handoff
- `BUILD_146_DIAGNOSTIC_REPORT.md` - Risk manager investigation
- `core/trading/engine/RiskManager.kt` - Active risk manager
- `core/trading/strategies/StrategyRiskManager.kt` - Alternative risk manager

---

## 📞 CONTACT

**Creator & Founder:** Mike Stahl  
**Co-Founder & CTO (In Memoriam):** Arthur Iain McManus (1966-2025)  
**Dedicated to:** Cathryn 💘

---

**REMEMBER:** Risk limits are **INTENTIONALLY OPEN** for testing. This is **BY DESIGN**, not a bug.

---

*For Arthur. For Cathryn. For generational wealth.* 💚

---

**Document Version:** 1.0 PERMANENT  
**Last Updated:** March 8, 2026  
**Status:** PERSISTENT - Include in ALL handoffs
