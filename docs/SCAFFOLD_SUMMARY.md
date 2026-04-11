# MiWealth v5.30.0 - Scaffold Summary

**Created:** April 11, 2026  
**Status:** Ready for development  
**Target:** Singapore Fintech (April 28-30, 2026)

---

## What Was Created

### **Documentation (Complete) ✅**

1. **README.md** - Project overview with verified performance metrics
2. **CHANGELOG.md** - Full version history (v1.x → v5.30.0)
3. **ARCHITECTURE.md** - Clean system design (~500 LOC coordinator)
4. **PROJECT_STRUCTURE.md** - Detailed file structure + porting guide
5. **.gitignore** - Standard Android exclusions

### **Directory Structure (Ready)**

```
miwealth-android/
├── README.md
├── CHANGELOG.md  
├── .gitignore
├── docs/
│   ├── ARCHITECTURE.md
│   ├── PROJECT_STRUCTURE.md
│   └── SCAFFOLD_SUMMARY.md (this file)
└── (gradle files and source folders to be added)
```

---

## Key Decisions Made

### **1. Versioning: v5.30.0-arthur**
- Major: 5 (5th iteration of MiWealth vision)
- Minor: 30 (implies significant development maturity)
- Patch: 0 (fresh rebuild)
- Edition: "Arthur" (in memory of co-founder)

### **2. Performance Metrics (VERIFIED)**
- **62% cumulative returns** (2024-2025, compounding)
- 2024: +23.70% (Sharpe 1.14, Max DD 9.20%)
- 2023: +31.38% (Sharpe 2.45)
- STAHL contribution: 103% of net profits
- ❌ **48.61% figure is FABRICATED** - never use it

### **3. Product Name: MiWealth**
- Simple, clear, regulatory-friendly
- Company: MiWealth Pty Ltd
- Corporate structure: MiFi → MiHoldings → MiWealth (later)
- Package: `com.miwealth.app`

### **4. Architecture: Clean Rebuild**
- Target: <5,000 total LOC (vs 40,000+ in BUILD #441)
- Coordinator: ~500 LOC max (vs 4,151 LOC in #441)
- Single PortfolioTracker (single source of truth)
- No DQN, no bootstrap, no Phase 2 features (for Singapore)

---

## Files to Port from BUILD #441

### **✅ Port Directly (Already Working)**

| Source File | New Location | LOC |
|------------|--------------|-----|
| `AIBoardOrchestrator.kt` | `core/board/` | ~800 |
| `HedgeFundBoardOrchestrator.kt` | `core/board/` | ~600 |
| `BinancePaperExchangeAdapter.kt` | `core/execution/PaperExecutor.kt` | ~300 |
| `StahlStairStop.kt` | `core/risk/` | ~200 |
| `TradingCosts.kt` | `core/risk/` | ~150 |
| `BinancePublicPriceFeed.kt` | `core/market/` | ~250 |

**Total from BUILD #441:** ~2,300 LOC (proven components)

### **🔨 Write New (Simplified)**

| Component | Target LOC |
|-----------|------------|
| `TradingCoordinator.kt` | ~500 |
| `PortfolioTracker.kt` | ~200 |
| `TradingViewModel.kt` | ~150 |
| Dashboard UI (Compose) | ~800 |
| Data models | ~300 |
| Utilities | ~100 |

**New code:** ~2,050 LOC

### **Total: ~4,350 LOC** (within 5,000 budget)

---

## Next Steps (In Order)

### **Step 1: Initialize Git Repository**
```bash
cd /home/claude/miwealth-android
git init
git add .
git commit -m "feat: Initial scaffold for MiWealth v5.30.0 Arthur Edition

Created:
- Documentation (README, CHANGELOG, ARCHITECTURE, STRUCTURE)
- Project structure
- .gitignore

Version: 5.30.0-arthur (Build #1)
For Arthur. For Singapore. 💚"

git tag -a v5.30.0-arthur-build1 -m "Initial scaffold"
```

### **Step 2: Create Gradle Build Files**
- `settings.gradle.kts` (project settings)
- `build.gradle.kts` (root build config)
- `app/build.gradle.kts` (app module config)
- `miwealth-core/build.gradle.kts` (core module config)

### **Step 3: Port Proven Components** (Week 1 - Days 1-3)
1. Copy `AIBoardOrchestrator.kt` → `core/board/`
2. Copy `HedgeFundBoardOrchestrator.kt` → `core/board/`
3. Copy `BinancePaperExchangeAdapter.kt` → `core/execution/PaperExecutor.kt`
4. Copy `StahlStairStop.kt` → `core/risk/`
5. Copy `TradingCosts.kt` → `core/risk/`
6. Copy `BinancePublicPriceFeed.kt` → `core/market/`

**Verify each component compiles independently.**

### **Step 4: Write New Coordinator** (Week 1 - Days 4-5)
Create simplified `TradingCoordinator.kt`:
```kotlin
class TradingCoordinator {
    suspend fun analyzeAndTrade(symbol: String) {
        // 1. Get board decisions
        // 2. Determine action
        // 3. Execute if actionable
        // 4. Monitor STAHL exits
    }
}
```

**Target: <500 LOC, no complex event chains.**

### **Step 5: Build PortfolioTracker** (Week 1 - Day 6)
```kotlin
class PortfolioTracker {
    private val positions = mutableMapOf<String, Position>()
    
    fun addPosition(order: FilledOrder)
    fun getCurrentValue(): Double
    fun checkStahlExits()
}
```

**Single source of truth. No duplication.**

### **Step 6: End-to-End Test** (Week 1 - Day 7)
Wire everything together:
```kotlin
val coordinator = TradingCoordinator(
    mainBoard, hedgeFund, executor, portfolio
)

// Run one complete cycle
coordinator.analyzeAndTrade("BTC/USDT")

// Verify:
// - Boards made decision
// - Order executed (if actionable)
// - Portfolio updated
// - STAHL monitoring active
```

### **Step 7: Build UI** (Week 2)
Compose screens:
- DashboardScreen (portfolio value, P&L)
- PositionsScreen (active trades)
- BoardVotesScreen (AI decisions)

### **Step 8: Polish & Test** (Week 3)
- Bug fixes
- Demo rehearsal
- Edge case handling
- Backup device ready

---

## Success Criteria (Singapore Demo)

**Must work flawlessly:**
1. ✅ Launch app → Boards start analyzing
2. ✅ Decisions made every 15 seconds
3. ✅ Paper trades execute correctly
4. ✅ Portfolio value updates in real-time
5. ✅ STAHL exits trigger at right levels
6. ✅ UI shows all data clearly
7. ✅ Zero crashes in 30-minute demo

**That's the bar. Nothing more, nothing less.**

---

## What's NOT in v5.30.0

**Deliberately excluded (Phase 2):**
- ❌ DQN reinforcement learning
- ❌ Historical 500-candle bootstrap
- ❌ XAI board decision persistence
- ❌ Multiple positions per symbol
- ❌ Live exchange connection (paper only)
- ❌ Llama FLM integration

**Why?**
- Faster to working demo
- Prove core concept first
- Add features post-Singapore

---

## Build Timeline

### **Week 1 (Apr 12-18): Core Loop**
- Days 1-3: Port proven components
- Days 4-5: New coordinator
- Day 6: Portfolio tracker
- Day 7: End-to-end test

**Milestone:** Working trades (no UI)

### **Week 2 (Apr 19-25): UI**
- Days 1-3: Dashboard + Positions screens
- Days 4-5: Board votes screen
- Days 6-7: Polish & bug fixes

**Milestone:** Demo-ready UI

### **Week 3 (Apr 26-28): Singapore Prep**
- Days 1-2: Final testing
- Day 3: Demo rehearsal

**Milestone:** Flawless 30-minute demo

---

## Metrics to Track

| Metric | Target | Current |
|--------|--------|---------|
| Total LOC | <5,000 | 0 |
| Coordinator LOC | <500 | 0 |
| Build time (clean) | <2 min | - |
| Build time (incremental) | <10 sec | - |
| Test coverage | >80% | 0% |
| Crashes in demo | 0 | - |

---

## Mike's Next Action

**Option A: Initialize Git now**
```bash
cd /home/claude/miwealth-android
git init
git add .
git commit -m "feat: Initial scaffold v5.30.0-arthur"
git tag -a v5.30.0-arthur-build1 -m "Scaffold complete"
```

**Option B: Review scaffold first**
- Read ARCHITECTURE.md
- Read PROJECT_STRUCTURE.md
- Confirm approach before proceeding

**Option C: Start porting immediately**
- Copy files from BUILD #441
- Begin Week 1 Day 1 tasks

---

## Repository Location

**Current:** `/home/claude/miwealth-android/`

**To clone BUILD #441 reference:**
```bash
cd /home/claude
git clone https://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@github.com/MiWealth/sovereign-vantage-android.git --branch main --depth 1
```

**Both repos side-by-side:**
```
/home/claude/
├── sovereign-vantage-android/    # BUILD #441 (reference only)
└── miwealth-android/              # v5.30.0 (new clean build)
```

---

## Important Reminders

### **Performance Claims (Use ONLY These)**
- ✅ +62% cumulative returns (2024-2025)
- ✅ 2024: +23.70% (Sharpe 1.14, Max DD 9.20%)
- ✅ 2023: +31.38% (Sharpe 2.45)
- ✅ STAHL: 103% profit contribution
- ❌ **NEVER use 48.61%** (fabricated by previous AI)

### **Naming**
- ✅ Product: MiWealth
- ✅ Company: MiWealth Pty Ltd
- ✅ Package: com.miwealth.app
- ✅ Version: v5.30.0-arthur

### **Architecture Principles**
- ✅ Single source of truth (PortfolioTracker)
- ✅ Unidirectional data flow
- ✅ No complex event chains
- ✅ Each component has ONE job
- ✅ Proven components only
- ✅ <500 LOC per file

---

**For Arthur. For clean scaffold. For Singapore.** 💚

**Scaffold Status: COMPLETE ✅**  
**Next: Initialize Git + Port Components**
