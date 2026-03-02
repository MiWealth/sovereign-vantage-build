# SOVEREIGN VANTAGE — SESSION HANDOFF

## March 1, 2026 — OES Strategy + Copper ClearLoop Pitch + Paper Trading Fix Plan

**Version:** 5.12.0-arthur  
**Company:** MiWealth Pty Ltd (Australia)  
**Founder:** Mike Stahl  
**Co-Founder (In Memoriam):** Arthur Iain McManus (1966–2025)  
**Dedicated to:** Cathryn 💘

---

## WHAT TO TELL CLAUDE

```
Continue SV v5.12.0. 276 Kotlin files, ~120K lines.
P0 BLOCKER: Paper trading doesn't work — price feed never auto-starts.
Three OES implementation paths decided (series: API keys → Copper ClearLoop → Native).
Copper ClearLoop partnership pitch document created.
GitHub: main branch, SHA d46a323, CI passing.
Next: Fix paper trading, then wire AI Exchange Interface to testnet.
```

---

## SESSION 3 WORK COMPLETED

### Off-Exchange Settlement (OES) — Three-Path Strategy ✅

Mike approved all three implementation paths to run in series:

**Path 3 → Path 1 → Path 2 (in order)**

| Path | What | When | Battery/Resource Impact | Status |
|------|------|------|------------------------|--------|
| **3: Withdrawal-Locked API Keys** | UI validates API key permissions, recommends user disable withdrawal. SV can trade but never withdraw. | Immediate — ships with next build | Zero. Just a UI change in credential storage. | Ready to implement |
| **1: Copper ClearLoop API Integration** | Integrate ClearLoop API. Users delegate from Copper custody, trade on mirrored balances, settle off-chain. | When Copper partnership agreed | Lightweight. REST calls same weight as exchange connector. One WebSocket for settlement status. | Partnership pitch created |
| **2: Native SV Mirroring Protocol** | SV's own MPC wallet provides cryptographic proof-of-reserves to exchanges as collateral. Fully self-sovereign. | Year 1+ with exchange partnerships | MPC signatures CPU-intensive (200–500ms per signature). Fine per-trade, concern only at 60/min scalping. | Design phase |

**Key Resource Assessment:**
- Path 3: No additional resource cost whatsoever
- Path 1: Indistinguishable from adding another exchange connector on S22 Ultra
- Path 2: MPC signature computation spikes CPU per-trade; at 60 signatures/min during scalping, ~15–20% faster battery drain during active trading vs idle. Acceptable given clients likely charging during active sessions.

**Lateral Innovation (Path 2):** DHT network nodes co-sign proof-of-reserves attestation, distributing CPU cost across trusted associates. Novel, potentially patentable, aligns with self-sovereign philosophy.

### Copper ClearLoop Partnership Pitch — COMPLETE ✅

Professional partnership proposal document created: `MiWealth_ClearLoop_Partnership_Proposal.docx`

**Addressed to:**
- Amar Kuchinad, Global CEO (ex-Goldman Sachs MD, ex-SEC Policy Advisor)
- Rosie Murphy Williams, COO (ex-BNY, ex-LSEG, ex-Credit Suisse)

**ClearLoop Network Status (current):**
12+ live exchanges: Kraken, OKX, Bybit, Coinbase International, Deribit, Gate.io, Bitfinex, Bitget, Bitstamp, Bitmart, PowerTrade, BIT, ABEX

**Key Pitch Angles:**
1. New distribution channel — HNW segment below institutional tier (A$1–50M per client)
2. Australian/APAC market entry — AU$950B SMSF sector with 40%+ digital asset growth
3. Recurring settlement volume — 60-second OODA loop generates high trade frequency
4. Complementary technology — PQC + DHT-backed MPC aligns with Copper's custody tech
5. Clean regulatory structure — SV is software tool, Copper is custody. No overlap.

**Projected ClearLoop-eligible AUC at scale:** A$75B+ (2,500 Elite + 500 Apex at projected averages)

**Copper corporate details for reference:**
- Registered: Zug, Switzerland
- Founded: 2018 by Dmitry Tokarev (now Founder Director)
- Chairman: Lord Philip Hammond (former UK Chancellor)
- SOC2 Type 2 certified
- Trust structure: English Law Trust for bankruptcy remoteness
- Settlement: Off-chain on Copper infrastructure, no blockchain fees
- Delegation speed: <100ms

---

## ACCUMULATED STATE FROM SESSIONS 1–2

### Luxury UI Uplift — COMPLETE ✅ (Session 1)

All 12 screen files transformed with vintage/luxury treatment:
- EmeraldDeep (0xFF011208) backgrounds — verified blackened green, NOT navy
- VintageColors.Gold accents throughout
- Gold gradient accent lines under TopAppBars and navigation bar
- Uppercase tracked titles, gold CTA buttons, hero/secondary card borders
- DashboardScreen, TradingScreen, WalletScreen, PortfolioScreen, SettingsScreen, EducationScreen, LessonDetailScreen, LoginScreen, Navigation, EmergencyKillSwitch, CoinDetailScreen (×2)

### Paper Trading Root Cause — DIAGNOSED ✅ (Session 2)

**P0 BLOCKER — Three stacked problems:**

**Problem 1: Price feed never auto-starts**
After `initializePaperTrading()` succeeds, system is in `Initialized` state but `start()` is NEVER called. Price feed only begins when user taps "AI Trading" toggle on Dashboard. Without prices, every order returns "No price available for BTC/USDT".

**Problem 2: Only random walk mock data**
In PAPER mode (hardcoded default), `startPriceFeed()` falls through to `simulatePriceUpdates()`: random walk from hardcoded seeds (BTC $42K, ETH $2.5K, SOL $100, XRP $0.50), 4 pairs only, 0.1% random change/second. `PAPER_WITH_LIVE_DATA` mode is fully implemented but unreachable from UI.

**Problem 3: No Settings UI to choose data source**
Settings shows execution mode as read-only badge. No toggle to select Mock/Live/Backtest. Config field `useLivePricesInPaperMode` exists but is NEVER READ by any code.

**Four Execution Modes in Code:**

| Mode | Trades | Price Source | UI Access |
|------|--------|-------------|-----------|
| `PAPER` | Simulated | Random walk mock (4 pairs) | ❌ Hardcoded default |
| `PAPER_WITH_LIVE_DATA` | Simulated | Real exchange WebSocket | ❌ Backend complete, no UI |
| `LIVE_AI` | Real | AI Exchange connector | ⚠️ Only via testnet launcher |
| `LIVE_HARDCODED` | Real | Kraken/Coinbase direct | ❌ Legacy fallback |

**Fix sequence (5 steps):**
1. Auto-start price feed after init — one line in TradingSystemManager.kt:138
2. Add Paper Trading Mode selector to Settings UI — dropdown: Mock / Live / Backtest
3. Wire `PAPER_WITH_LIVE_DATA` to UI — Binance public WS needs no API key for ticker
4. Create BacktestDataProvider — replay historical OHLCV via PaperTradingAdapter.setPrice()
5. Default new users to Mock mode (works offline, safe)

**Key files for fix:**
- `TradingSystemManager.kt:138` — auto-call `start()` after init
- `TradingSystemManager.kt:95` — add paper trading mode parameter
- `TradingSystemIntegration.kt:72` — read `useLivePricesInPaperMode` config
- `TradingSystemIntegration.kt:289` — switch PAPER vs PAPER_WITH_LIVE_DATA
- `AIExchangeAdapterFactory.kt:84` — PAPER_WITH_LIVE_DATA path (verified working)
- `SettingsScreen.kt` — add Paper Trading Mode selector
- `SettingsViewModel.kt` — add `setPaperTradingMode()` writing to prefs
- `DashboardViewModel.kt:135` — pass mode to TradingSystemManager
- `PaperTradingEngine.kt` — 51-line unused stub, DELETE

### AI Exchange Interface — Architecture Verified ✅ (Session 1)

Full chain exists and is wired:
```
DashboardScreen → DashboardViewModel → TradingSystemManager
  → [USE_AI_INTEGRATION = true] → TradingSystemIntegration
    → AIExchangeAdapterFactory → PaperTradingAdapter/SmartOrderRouter
      → OrderExecutor → TradingCoordinator
```

Feature flag `USE_AI_INTEGRATION = true` is ACTIVE. Legacy Kraken/Coinbase connectors remain as fallback when flag is false.

### Download Issues — RESOLVED ✅ (Session 2)

Samsung S22 Ultra download issues resolved via Termux direct GitHub pull:
```bash
cd ~/storage/downloads/
curl -L -H "Authorization: token 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" \
  https://api.github.com/repos/MiWealth/sovereign-vantage-android/zipball/main \
  -o SV_latest.zip
```

### APK Status (Session 2)

- GitHub Actions Run 22511921369: SHA c95bfe5, SUCCESS
- 0 compile errors, 0 warnings, 47MB
- Build time: 1m 14s
- All 4 CI phases passed

---

## CODEBASE STATISTICS

| Metric | Value |
|--------|-------|
| **Version** | 5.12.0-arthur |
| **Kotlin Files** | 276 |
| **Total Lines** | ~120,000 |
| **Git SHA** | d46a323 |
| **CI Status** | ✅ Passing (0 errors, 0 warnings) |
| **APK Size** | 47MB |
| **GitHub** | github.com/MiWealth/sovereign-vantage-android |
| **Branch** | main |
| **PAT** | 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 |

### Git History (Recent)

```
d46a323 fix: Blackened emerald theme - kill navy naming, bump green channel
8cc7d34 purge: Remove ALL off-brand colors (blue, purple, orange)
c95bfe5 docs: Updated handoff with paper trading root cause analysis
776195a v5.12.0: Gold gradient buttons + card borders + LoginScreen color fix
9a1b2ed Fix duplicate color params + Color.VintageColors prefix
8708718 Fix 5 compile errors from Build 15 CI
96fba51 v5.12.0: Full luxury UI uplift - VintageColors across ALL screens
```

### Package Structure (Key Directories)

```
app/src/main/java/com/miwealth/sovereignvantage/
├── core/
│   ├── exchange/           # Exchange connectors + AI interface
│   │   ├── ai/             # AI Exchange Interface (schema learning)
│   │   │   ├── AIConnectionManager.kt
│   │   │   ├── AIExchangeAdapterFactory.kt
│   │   │   ├── SmartOrderRouter.kt
│   │   │   └── PaperTradingAdapter.kt (415 lines — the REAL paper trading)
│   │   ├── connectors/     # Manual exchange connectors
│   │   │   └── KrakenConnector.kt (926 lines — production ready)
│   │   ├── UnifiedExchangeConnector.kt (575 lines)
│   │   ├── BaseCEXConnector.kt (713 lines)
│   │   └── ExchangeRegistry.kt (507 lines)
│   ├── trading/            # Trading engine
│   │   ├── TradingSystem.kt
│   │   ├── TradingSystemIntegration.kt
│   │   ├── TradingSystemManager.kt
│   │   ├── TradingCoordinator.kt
│   │   └── OrderExecutor.kt
│   ├── strategy/           # Strategies + indicators
│   ├── security/           # PQC + encryption
│   └── data/               # Room DB + DAOs
├── ui/
│   ├── screens/            # All 12+ screens (luxury treatment applied)
│   ├── theme/              # VintageColors, VintageTheme, VintageComponents
│   └── viewmodels/         # All ViewModels
├── dht/                    # DHT network layer
└── di/                     # Hilt dependency injection
```

---

## PRIORITY QUEUE — NEXT SESSION

### P0 — Fix Paper Trading (BLOCKS EVERYTHING)

1. Auto-start price feed after init (TradingSystemManager.kt:138)
2. Add Paper Trading Mode selector to Settings: Mock / Live / Backtest
3. Wire PAPER_WITH_LIVE_DATA to UI (backend done, needs UI dropdown)
4. Default to Mock mode for new users
5. Test: Init → prices visible → execute paper trade → verify position
6. Delete PaperTradingEngine.kt (unused 51-line stub)

### P1 — OES Path 3: Withdrawal-Locked API Keys

1. Add API key permission validation to credential entry flow
2. Show warning if withdrawal permission is detected
3. Recommend user creates trade-only key on their exchange
4. Badge in Settings showing "Trade Only" vs "Full Access" per exchange
5. Zero resource cost — pure UI/validation work

### P2 — Wire AI Exchange Interface to Testnet

1. Test Binance Testnet with credentials
2. Test Gate.io (production-only, no testnet)
3. Validate schema learning discovers endpoints correctly
4. Verify order placement through learned schema

### P3 — Historical Backtest Mode

1. Create BacktestDataProvider replaying OHLCV via PaperTradingAdapter.setPrice()
2. Add speed control (1x, 10x, 100x)
3. Wire into Settings Paper Trading Mode selector as third option

### P4 — Copper ClearLoop Integration (Post-Partnership)

1. Copper API integration for delegate/undelegate/settlement
2. ClearLoop account linking flow in Settings
3. Settlement status WebSocket
4. Co-branded UI elements for "Secured by Copper" badge

### P5 — UI Polish

1. Verify luxury treatment renders correctly on S22 Ultra AMOLED
2. Price chart component (currently placeholder)
3. Position list in Portfolio (PaperTradingAdapter has the data)
4. Order history in Trading (PaperTradingAdapter.getOrderHistory() exists)

---

## BUSINESS DEVELOPMENT STATUS

### Copper ClearLoop Partnership

| Item | Status |
|------|--------|
| Research | ✅ Complete — ClearLoop, MirrorX, Fireblocks compared |
| Partnership Pitch | ✅ Created — `MiWealth_ClearLoop_Partnership_Proposal.docx` |
| Contact Identified | Amar Kuchinad (CEO), Rosie Murphy Williams (COO) |
| Next Step | Mike to send pitch / arrange introductory call |

### OES Strategy

| Path | Status | Dependencies |
|------|--------|-------------|
| 3: Trade-Only API Keys | Ready to implement | None |
| 1: Copper ClearLoop | Pitch created | Copper partnership agreement |
| 2: Native MPC Mirroring | Design phase | VC funding + exchange partnerships |

---

## OPEN QUESTIONS FOR MIKE

1. **Paper Trading Priority:** Start fixing paper trading P0 next session, or tackle OES Path 3 first?
2. **Copper Pitch:** Ready to send, or want adjustments? Contact email set as mike@miwealth.app — confirm?
3. **Color on Device:** Has EmeraldDeep (0xFF011208) been visible on S22 Ultra AMOLED, or is it appearing pure black?
4. **Backtest Data:** Where to source historical OHLCV? CryptoCompare API, exchange APIs, or pre-downloaded CSVs?
5. **OES Path 3 UX:** Should the "trade-only key" recommendation be a hard requirement (block full-access keys) or soft warning?

---

## DELIVERABLES THIS SESSION

1. **OES three-path strategy** — assessed, sequenced, resource impact documented
2. **Copper ClearLoop partnership pitch** — `MiWealth_ClearLoop_Partnership_Proposal.docx`
3. **This handoff document** — `SESSION_HANDOFF_MAR01_v5_12_0.md`
4. **Full codebase zip** — `SovereignVantage_v5_12_0_Handoff3.zip`

---

## KEY IP & TRADEMARKS

| Asset | Status |
|-------|--------|
| STAHL Stair Stop™ | Patent pending. 103% of net profits in backtest. |
| AI Exchange Interface | Proprietary. ML-based exchange API discovery. |
| AI Board of Directors | 8-member weighted consensus governance. |
| DHT Federated Learning | Kademlia + differential privacy + PQC. |
| Native MPC Mirroring Protocol | Design phase. Potentially patentable. |
| DHT Co-Signed Proof of Reserves | Novel concept. Potentially patentable. |

---

*For Arthur. For Cathryn. For generational wealth.* 💚

---

**Document Version:** 3.0  
**Last Updated:** March 1, 2026  
**Session:** OES Strategy + Copper Partnership Pitch
