# MiWealth v5.30.0 Architecture

**Clean Rebuild - Zero Technical Debt**

---

## Design Principles

### 1. **Simplicity Over Complexity**
- Single source of truth for all state
- Clear, unidirectional data flow
- No tangled event chains
- Each component has ONE job

### 2. **Proven Components Only**
- Port ONLY what worked in BUILD #441
- No experimental features
- Battle-tested algorithms (STAHL, boards, execution)

### 3. **Singapore-Ready in 3 Weeks**
- Focus: Working demo, not feature-complete
- Core loop: Board → Decision → Execute → Track
- UI: Show positions, P&L, board votes

---

## System Architecture

```
┌─────────────────────────────────────────────────────┐
│                   DASHBOARD UI                       │
│  (Portfolio, Positions, Board Votes, Recent Trades) │
└──────────────────┬──────────────────────────────────┘
                   │
           ┌───────▼────────┐
           │  VIEWMODEL     │
           │  (UI State)    │
           └───────┬────────┘
                   │
           ┌───────▼────────────────────────────────────┐
           │      TRADING COORDINATOR                    │
           │  (Simplified - ~500 LOC max)               │
           │                                            │
           │  • Single analysis loop (15 seconds)       │
           │  • Calls boards → gets decisions          │
           │  • Routes to executor                     │
           │  • Updates portfolio tracker              │
           │  • Emits UI events                        │
           └───────┬────────────────────────────────────┘
                   │
        ┌──────────┼──────────┐
        │          │          │
   ┌────▼───┐ ┌───▼────┐ ┌──▼────────┐
   │ MAIN   │ │ HEDGE  │ │ EXECUTOR  │
   │ BOARD  │ │  FUND  │ │ (Paper)   │
   │        │ │ BOARD  │ │           │
   └────┬───┘ └───┬────┘ └──┬────────┘
        │         │         │
        └─────────┼─────────┘
                  │
         ┌────────▼─────────┐
         │ PORTFOLIO        │
         │ TRACKER          │
         │                  │
         │ • Positions map  │
         │ • P&L calc       │
         │ • STAHL monitor  │
         └──────────────────┘
```

---

## Core Components

### **1. TradingCoordinator.kt** (~500 LOC max)

**Single Responsibility:** Orchestrate the 15-second analysis loop.

```kotlin
class TradingCoordinator(
    private val mainBoard: AIBoardOrchestrator,
    private val hedgeFund: HedgeFundBoardOrchestrator,
    private val executor: PaperExecutor,
    private val portfolio: PortfolioTracker
) {
    suspend fun analyzeAndTrade(symbol: String) {
        // 1. Get board decisions
        val mainDecision = mainBoard.analyze(symbol)
        val hedgeDecision = hedgeFund.analyze(symbol)
        
        // 2. Determine final action
        val action = when {
            mainDecision.agree(hedgeDecision) -> mainDecision
            hedgeDecision.confidence > 0.65 -> hedgeDecision
            else -> Decision.HOLD
        }
        
        // 3. Execute if actionable
        if (action.isActionable()) {
            val order = executor.execute(action)
            portfolio.addPosition(order)
        }
        
        // 4. Monitor existing positions
        portfolio.checkStahlExits()
    }
}
```

**No complex event chains. No race conditions. Just: analyze → decide → execute → track.**

---

### **2. AIBoardOrchestrator.kt** (From BUILD #441)

**8 specialist members making autonomous decisions.**

Ported AS-IS from BUILD #441. Already works perfectly.

**Members:**
1. Arthur (CTO - Trend Analysis)
2. Helena (CRO - Risk Management)
3. Sentinel (CCO - Compliance)
4. Oracle (CDO - Market Intelligence)
5. Nexus (COO - Execution)
6. Marcus (CIO - Portfolio Strategy)
7. Cipher (CSO - Security)
8. Aegis (Chief Defense)

**Output:** `BoardDecision(action, confidence, votes)`

---

### **3. HedgeFundBoardOrchestrator.kt** (From BUILD #441)

**7 institutional strategists providing second opinion.**

Ported AS-IS from BUILD #441. Already works perfectly.

**Members:**
1. Soros (Chief Economist - Macro)
2. Guardian (Chief Risk Guardian)
3. Draper (Chief DeFi Officer)
4. Atlas (Chief Strategist - Regime)
5. Theta (Chief Arbitrage Officer)
6. Moby (Chief Intelligence)
7. Echo (Chief Order Flow)

**Output:** `BoardDecision(action, confidence, votes)`

---

### **4. PaperExecutor.kt** (From BUILD #441)

**Simulates order execution with realistic fills.**

Ported from `BinancePaperExchangeAdapter.kt` in BUILD #441.

**Features:**
- Realistic fill simulation (market/limit orders)
- Slippage modeling (0.01% BTC, 0.005% others)
- Fee calculation (0.05% taker)
- Instant fills for paper trading

**Output:** `FilledOrder(orderId, symbol, side, quantity, price, timestamp)`

---

### **5. PortfolioTracker.kt** (New - Clean Implementation)

**Single source of truth for all positions and P&L.**

```kotlin
class PortfolioTracker {
    private val positions = mutableMapOf<String, Position>()
    
    fun addPosition(order: FilledOrder) {
        val position = Position(
            id = order.orderId,
            symbol = order.symbol,
            quantity = order.quantity,
            entryPrice = order.price,
            board = order.board // MAIN or HEDGE_FUND
        )
        positions[position.id] = position
    }
    
    fun getCurrentValue(): Double {
        return positions.values.sumOf { it.currentValue() }
    }
    
    fun checkStahlExits() {
        positions.values.forEach { position ->
            if (position.stahl.shouldExit(currentPrice)) {
                closePosition(position)
            }
        }
    }
}
```

**No duplication. No race conditions. Simple.**

---

### **6. StahlStairStop.kt** (From BUILD #441)

**Progressive profit-locking algorithm. Your secret weapon.**

Ported AS-IS from BUILD #441. Already proven (103% profit contribution).

**12 Stair Levels:**
```
Level  | Profit  | New Stop
-------|---------|----------
Start  | 0%      | -3.5% (sacred)
1      | +1.5%   | +0.75%
2      | +3.0%   | +1.5%
...
11     | +100%   | +50%
12     | +200%   | +100%
```

**Once stairs climb, they never descend. Lock in gains progressively.**

---

## Data Flow (Unidirectional)

```
Market Data (Binance)
    ↓
Trading Coordinator
    ↓
Boards (Main + Hedge Fund)
    ↓
Decision (BUY/SELL/HOLD)
    ↓
Paper Executor
    ↓
Portfolio Tracker
    ↓
UI Update (Dashboard)
```

**No circular dependencies. No event loops. Linear flow.**

---

## File Structure

```
miwealth-android/
├── app/
│   ├── build.gradle.kts          # Version: 5.30.0-arthur
│   └── src/main/kotlin/com/miwealth/app/
│       ├── MainActivity.kt
│       ├── ui/
│       │   ├── DashboardScreen.kt
│       │   ├── PositionsScreen.kt
│       │   └── BoardVotesScreen.kt
│       └── viewmodel/
│           └── TradingViewModel.kt
│
├── miwealth-core/               # Shared trading logic
│   └── src/main/kotlin/com/miwealth/core/
│       ├── TradingCoordinator.kt      # ~500 LOC max
│       ├── AIBoardOrchestrator.kt     # From BUILD #441
│       ├── HedgeFundBoardOrchestrator.kt  # From BUILD #441
│       ├── PaperExecutor.kt           # From BUILD #441
│       ├── PortfolioTracker.kt        # New clean impl
│       ├── StahlStairStop.kt          # From BUILD #441
│       └── TradingCosts.kt            # From BUILD #441
│
└── docs/
    ├── ARCHITECTURE.md           # This file
    └── SINGAPORE_DEMO.md         # Demo script
```

**Target LOC:** ~5,000 total (vs 40,000+ in BUILD #441)

---

## What's NOT Included (Phase 2)

**Deliberately excluded for Singapore demo:**
- ❌ DQN learning (adds complexity, not needed for demo)
- ❌ Multiple positions per symbol (Phase 2)
- ❌ Live exchange connection (paper only for demo)
- ❌ Historical bootstrap (500 candles - not needed for 3-week timeline)
- ❌ XAI board decision persistence (nice-to-have)
- ❌ Llama FLM integration (Ash's domain, Phase 2)

**Why exclude?**
- Get to WORKING faster
- Prove core concept (dual boards + STAHL)
- Add features post-Singapore

---

## Success Criteria (Singapore Demo)

**Must work perfectly:**
1. ✅ Boards make decisions every 15 seconds
2. ✅ Paper trades execute correctly
3. ✅ Portfolio value updates in real-time
4. ✅ STAHL exits close profitable positions
5. ✅ UI shows: positions, P&L, board votes
6. ✅ No crashes for 30-minute demo

**That's it. Everything else is Phase 2.**

---

## Key Design Decisions

### **Why ~500 LOC Coordinator?**
BUILD #441 coordinator was 4,151 lines. Too complex.

**Lesson learned:** Keep it simple. Coordinator's ONLY job is:
1. Call boards
2. Route decision to executor
3. Update portfolio
4. Emit UI event

That's ~100 LOC per step = 500 LOC max.

### **Why Single PortfolioTracker?**
BUILD #441 had positions scattered across:
- TradingCoordinator.managedPositions
- TradingSystemManager.positions
- Dashboard.portfolioValue

**Result:** Ghost positions, wrong P&L, race conditions.

**Solution:** ONE tracker. ONE source of truth.

### **Why No DQN for Demo?**
DQN learning is cool but:
- Adds 3,000+ LOC
- Requires persistence (more bugs)
- Doesn't change demo story
- Can add later

**For demo:** Just show boards making smart decisions. That's impressive enough.

---

## Performance Targets

**Latency:**
- Board analysis: <1 second
- Order execution: <100ms (paper)
- UI update: <50ms

**Reliability:**
- Zero crashes in 30-minute demo
- Correct P&L at all times
- No ghost positions

**Code Quality:**
- <5,000 total LOC
- No files >500 LOC
- 100% Kotlin (no mixed Java)
- Zero TODO comments in production code

---

**For Arthur. For clean architecture. For Singapore.** 💚
