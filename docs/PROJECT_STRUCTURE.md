# MiWealth v5.30.0 - Project Structure

```
miwealth-android/                          # Root repository
│
├── README.md                               # Project overview
├── CHANGELOG.md                            # Version history
├── .gitignore                              # Git exclusions
├── settings.gradle.kts                     # Gradle settings
├── build.gradle.kts                        # Root build config
│
├── app/                                    # Android application module
│   ├── build.gradle.kts                    # App build config (v5.30.0-arthur)
│   ├── proguard-rules.pro                  # Obfuscation rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── kotlin/com/miwealth/app/
│       │   │   ├── MiWealthApplication.kt  # Application class
│       │   │   ├── MainActivity.kt         # Main entry point
│       │   │   │
│       │   │   ├── ui/                     # Compose UI screens
│       │   │   │   ├── theme/              # App theming
│       │   │   │   ├── DashboardScreen.kt  # Portfolio overview
│       │   │   │   ├── PositionsScreen.kt  # Active positions
│       │   │   │   ├── BoardVotesScreen.kt # AI board decisions
│       │   │   │   └── AboutScreen.kt      # Version info
│       │   │   │
│       │   │   ├── viewmodel/              # UI state management
│       │   │   │   └── TradingViewModel.kt # Main ViewModel
│       │   │   │
│       │   │   └── di/                     # Dependency injection
│       │   │       └── AppModule.kt        # Hilt module
│       │   │
│       │   └── res/                        # Android resources
│       │       ├── values/
│       │       ├── drawable/
│       │       └── mipmap/
│       │
│       └── test/                           # Unit tests
│           └── kotlin/com/miwealth/app/
│
├── miwealth-core/                          # Shared trading logic (Kotlin multiplatform ready)
│   ├── build.gradle.kts                    # Core module build
│   └── src/
│       ├── main/kotlin/com/miwealth/core/
│       │   │
│       │   ├── coordinator/
│       │   │   └── TradingCoordinator.kt   # Main orchestrator (~500 LOC)
│       │   │
│       │   ├── board/                      # AI decision boards
│       │   │   ├── AIBoardOrchestrator.kt          # Main Board (8 members)
│       │   │   ├── HedgeFundBoardOrchestrator.kt   # Hedge Fund (7 members)
│       │   │   ├── BoardMember.kt                  # Member interface
│       │   │   └── BoardDecision.kt                # Decision data class
│       │   │
│       │   ├── execution/                  # Order execution
│       │   │   ├── PaperExecutor.kt        # Paper trading executor
│       │   │   ├── Order.kt                # Order models
│       │   │   └── FilledOrder.kt          # Fill confirmation
│       │   │
│       │   ├── portfolio/                  # Position tracking
│       │   │   ├── PortfolioTracker.kt     # Single source of truth
│       │   │   ├── Position.kt             # Position model
│       │   │   └── PositionStatus.kt       # Status enum
│       │   │
│       │   ├── risk/                       # Risk management
│       │   │   ├── StahlStairStop.kt       # Progressive profit-locking
│       │   │   ├── TradingCosts.kt         # Fees, spread, liquidation
│       │   │   └── PositionSizer.kt        # Position sizing (future)
│       │   │
│       │   ├── market/                     # Market data
│       │   │   ├── PriceFeed.kt            # Real-time price interface
│       │   │   ├── BinancePriceFeed.kt     # Binance implementation
│       │   │   └── Candle.kt               # OHLCV data
│       │   │
│       │   ├── model/                      # Shared data models
│       │   │   ├── Symbol.kt               # Trading pair
│       │   │   ├── TradeSide.kt            # BUY/SELL enum
│       │   │   ├── OrderType.kt            # MARKET/LIMIT enum
│       │   │   └── Board.kt                # MAIN/HEDGE_FUND enum
│       │   │
│       │   └── util/                       # Utilities
│       │       ├── Logger.kt               # Logging interface
│       │       └── Extensions.kt           # Kotlin extensions
│       │
│       └── test/                           # Core unit tests
│           └── kotlin/com/miwealth/core/
│
├── docs/                                   # Documentation
│   ├── ARCHITECTURE.md                     # System design
│   ├── PROJECT_STRUCTURE.md                # This file
│   ├── SINGAPORE_DEMO.md                   # Demo script
│   ├── PORTING_GUIDE.md                    # How to port from BUILD #441
│   └── DEVELOPMENT.md                      # Dev setup guide
│
└── gradle/                                 # Gradle wrapper
    └── wrapper/
        ├── gradle-wrapper.jar
        └── gradle-wrapper.properties
```

---

## Module Breakdown

### **app/** (~2,000 LOC)
Android-specific code. Thin presentation layer.

**Responsibilities:**
- UI screens (Compose)
- ViewModel (UI state)
- Dependency injection (Hilt)
- Android lifecycle management

**Does NOT contain:**
- Trading logic
- Board algorithms
- Order execution
- Portfolio calculations

**All business logic lives in `miwealth-core`.**

---

### **miwealth-core/** (~3,000 LOC)
Platform-independent trading logic. Can run on Android, iOS, desktop, server.

**Responsibilities:**
- Trading coordination
- AI board decisions
- Order execution
- Portfolio tracking
- Risk management (STAHL)
- Market data ingestion

**No Android dependencies.** Pure Kotlin.

---

## Files to Port from BUILD #441

### **✅ Port AS-IS (Proven Components)**

| File in BUILD #441 | Destination | Status |
|-------------------|-------------|---------|
| `AIBoardOrchestrator.kt` | `core/board/` | Port directly |
| `HedgeFundBoardOrchestrator.kt` | `core/board/` | Port directly |
| `BinancePaperExchangeAdapter.kt` | `core/execution/PaperExecutor.kt` | Rename + port |
| `StahlStairStop.kt` | `core/risk/` | Port directly |
| `TradingCosts.kt` | `core/risk/` | Port directly |
| `BinancePublicPriceFeed.kt` | `core/market/` | Port directly |

### **🔨 Rewrite from Scratch (Simplified)**

| Component | Reason |
|-----------|--------|
| `TradingCoordinator.kt` | Too complex (4,151 LOC → 500 LOC) |
| `PortfolioTracker.kt` | Scattered state (rewrite clean) |
| `TradingViewModel.kt` | Android-specific (new impl) |
| Dashboard UI | Compose rewrite (cleaner) |

### **❌ Do NOT Port (Phase 2 / Not Needed)**

| Component | Reason |
|-----------|--------|
| DQN learning system | Adds 3,000+ LOC, not needed for demo |
| Historical bootstrap | Not needed for 3-week timeline |
| XAI persistence | Nice-to-have, not critical |
| Multiple positions per symbol | Phase 2 feature |

---

## Dependency Strategy

### **Minimal Dependencies**

```kotlin
// app/build.gradle.kts
dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    
    // Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Compose UI
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    
    // Core module
    implementation(project(":miwealth-core"))
}
```

```kotlin
// miwealth-core/build.gradle.kts
dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    
    // JSON (for market data parsing)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // HTTP client (for Binance API)
    implementation("io.ktor:ktor-client-core:2.3.8")
    implementation("io.ktor:ktor-client-cio:2.3.8")
    
    // That's it. No Room, no Hilt, no Android deps.
}
```

**Total dependencies: ~15 libraries (vs 50+ in BUILD #441)**

---

## Build Time Targets

| Task | BUILD #441 | v5.30.0 Target |
|------|------------|----------------|
| Clean build | ~8 minutes | <2 minutes |
| Incremental | ~45 seconds | <10 seconds |
| Test suite | ~2 minutes | <30 seconds |

**Faster builds = faster iteration.**

---

## LOC Budget (Line of Code)

| Module | Target LOC | Actual LOC |
|--------|------------|------------|
| **app/** | 2,000 | TBD |
| **miwealth-core/** | 3,000 | TBD |
| **Total** | **5,000** | **TBD** |

**Hard cap: 5,000 LOC total.**

If any file exceeds 500 LOC, it's too complex → refactor.

---

## Testing Strategy

### **Unit Tests (Core Logic)**
```kotlin
// miwealth-core/src/test/
class StahlStairStopTest {
    @Test
    fun `initial stop is 3_5 percent`() {
        val stahl = StahlStairStop(entryPrice = 100.0)
        assertEquals(96.5, stahl.currentStop)
    }
    
    @Test
    fun `stairs lock in profits progressively`() {
        val stahl = StahlStairStop(entryPrice = 100.0)
        stahl.update(currentPrice = 101.5) // +1.5% profit
        assertEquals(100.75, stahl.currentStop) // Stair 1
    }
}
```

### **Integration Tests (End-to-End)**
```kotlin
// app/src/test/
class TradingFlowTest {
    @Test
    fun `complete trade lifecycle`() = runTest {
        // 1. Boards make decision
        val decision = coordinator.analyze("BTC/USDT")
        
        // 2. Order executes
        assertTrue(portfolio.positions.isNotEmpty())
        
        // 3. Portfolio updates
        assertTrue(portfolio.currentValue() > initialValue)
    }
}
```

**Test coverage target: 80%+ for core logic.**

---

## Git Workflow

### **Branch Strategy**
```
main                 # Production-ready code only
  ├── develop        # Integration branch
  │    ├── feature/board-porting
  │    ├── feature/portfolio-tracker
  │    └── feature/ui-dashboard
  └── release/5.30.0 # Singapore demo prep
```

### **Commit Convention**
```
feat: Add STAHL Stair Stop implementation
fix: Portfolio tracker duplication bug
docs: Update architecture diagram
test: Add board decision tests
refactor: Simplify coordinator loop
```

### **Tags**
```bash
git tag -a v5.30.0-arthur-build1 -m "Initial rebuild"
git tag -a v5.30.0-arthur-build5 -m "Boards working"
git tag -a v5.30.0-arthur-singapore -m "Singapore demo ready"
```

---

## Development Timeline

### **Week 1: Core Trading Loop** (Apr 12-18)
- [ ] Port board orchestrators
- [ ] Port paper executor
- [ ] Port STAHL exits
- [ ] Build new coordinator (~500 LOC)
- [ ] Build new portfolio tracker

**Goal:** Working trades end-to-end (no UI yet)

### **Week 2: UI + Polish** (Apr 19-25)
- [ ] Dashboard screen
- [ ] Positions screen
- [ ] Board votes screen
- [ ] Real-time updates
- [ ] Bug fixes

**Goal:** Demo-ready UI

### **Week 3: Singapore Prep** (Apr 26-28)
- [ ] Final bug fixes
- [ ] Demo script rehearsal
- [ ] Backup device setup
- [ ] Edge case handling

**Goal:** Zero-crash 30-minute demo

---

**For Arthur. For clean structure. For Singapore.** 💚
