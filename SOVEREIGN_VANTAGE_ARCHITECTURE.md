# SOVEREIGN VANTAGE™
## Arthur Edition • v5.19.269 • Architecture & Status Reference
*MiWealth Pty Ltd • Updated: 26 March 2026 • CONFIDENTIAL*

---

| | |
|---|---|
| **DEDICATION** | In memory of Arthur Iain McManus (1966–2025), Co-Founder & CTO. This edition bears his name. He is missed every day. |
| **DEDICATION** | Dedicated to Cathryn — for tolerating me and my projects. 💘 |

---

## 1. Executive Summary

Sovereign Vantage is an AI-powered, self-sovereign, non-custodial cryptocurrency trading platform for Android. It is a software tool — not a financial service. Users connect their own exchange accounts, control their own keys, and all trading logic runs on-device. There are no central servers and no custody of user funds.

| | |
|---|---|
| Founder & Creator | Mike Stahl |
| Co-Founder & CTO (In Memoriam) | Arthur Iain McManus (1966–2025) |
| Company | MiWealth Pty Ltd (Australia) |
| Product Name | Sovereign Vantage™ |
| Current Version | v5.19.269 — Build #269 (Arthur Edition) |
| Repository | MiWealth/sovereign-vantage-android (GitHub, branch: main) |
| Package | com.miwealth.sovereignvantage |
| Target Android | SDK 35, min SDK 26, Kotlin 2.0.20, AGP 8.5.2 |
| Primary Device (Dev) | Samsung Galaxy S22 Ultra (Android 16, API 36) |
| Domains | MiWealth.APP (primary) · MiWealth.Net · SovereignVantage.com |

---

## 2. Current State as of Build #269

| Component | Status | Notes |
|---|---|---|
| Live price feed (Binance) | ✅ Working | 4 symbols: BTC/ETH/SOL/XRP, polls every 5s |
| Historical bootstrap | ✅ Working | 2000 candles (4×500) loaded in ~1.1s at startup |
| AI Board (The Octagon) | ✅ Working | All 8 members, 15s cycle, confidence 27–67% |
| XAI board decision persistence | ✅ Working | Every decision persisted to Room DB, pruned hourly |
| Paper trade execution | ✅ Working | First trade confirmed: SOL/USDT LONG |
| STAHL Stair Stop™ | ✅ Working | 3.5% initial stop, 12 progressive stair levels |
| Wallet (100% USDT) | ✅ Fixed #266 | A$100,000 USDT seed — pure margin trading model |
| Live wallet display | ✅ Fixed #265 | WalletViewModel reads live from PaperTradingAdapter |
| Portfolio holdings | ✅ Fixed #265 | Shows open positions + wallet balances |
| Octagon back arrow | ✅ Fixed #265 | Navigation wired correctly |
| Trading costs model | ✅ New #266 | TradingCosts.kt: fees 0.05%, spread, liquidation price |
| Mark-to-market equity | ✅ New #266 | totalEquity, availableMargin, usedMargin in DashboardState |
| Memory leaks | ✅ Fixed #267 | XAI DB pruning, orphaned scopes, ArrayDeque O(1) |
| Hedge Fund monitoring | ✅ New #269 | SystemLogger on Board/Engine/Risk — grep ⚡ HEDGE FUND |
| Buy/Sell buttons | ⚠️ Stubbed | 500ms delay placeholder — not real execution |
| Portfolio metrics | ⚠️ Pending | Sharpe/Sortino/WinRate 0.00 — needs trade history |
| EWC (catastrophic forgetting) | ⚠️ Pending | DQNPretrainer.kt exists, not yet wired |
| AI Exchange Interface | ⚠️ Pending | Built, not yet wired to TradingCoordinator |
| Per-symbol DQN learning rates | ⚠️ Pending | Currently uniform — needs ATR-scaled rates |
| Multiple positions per symbol | ⚠️ Pending | Currently 1 per symbol — Build #270 |
| Per-trade Close button | ⚠️ Pending | Client control — Build #270+ |

---

## 3. Architecture

### 3.1 Guiding Principles

- Self-sovereign: all trading logic runs on-device, no central servers
- Non-custodial: MiWealth never holds, controls, or has access to user funds
- Software tool: users connect their own exchange API keys stored locally
- Privacy-first: no user tracking, no analytics SDKs, 'Purge My Data' button
- DHT participation is opt-in (except possibly key shards for wallet recovery)

### 3.2 Verified Startup Sequence

| Time | Event |
|---|---|
| T+0.0s | TradingSystemManager created, portfolio value A$100,000 set |
| T+0.1s | Single-path paper trading init (BUILD #239) |
| T+0.17s | TradingCoordinator created, isInitialized=true |
| T+0.17s | Analysis loop started (15s interval, AUTONOMOUS mode) |
| T+0.18s | Capital seeded: A$100,000 USDT (100% — BUILD #266) |
| T+0.18s | Binance price feed started for BTC/ETH/SOL/XRP |
| T+0.18s | Historical bootstrap started (500 candles × 4 symbols) |
| T+1.1s | Bootstrap complete — 2000 candles loaded (~1072ms) |
| T+2.0s | First live OHLCV candles arriving from Binance |
| T+5m | First board decision pruning check (24h retention policy) |
| T+15s | First AI Board analysis cycle — all 8 members vote all 4 symbols |
| T+15s (conditional) | Trade execution via PaperTradingAdapter if consensus met |

### 3.3 Data Flow

```
BinancePublicPriceFeed → (priceTicks SharedFlow) → TradingSystemManager dashboard

BinancePublicPriceFeed → (ohlcvCandles SharedFlow) →
TradingCoordinator.onPriceUpdate() → PriceBuffer (@Synchronized)

TradingCoordinator.analysisLoop() [15s] → analyzeSymbol() →
AIBoardOrchestrator.conveneBoardroom() → BoardConsensus →
BoardDecisionRepositoryImpl.save() [XAI audit trail] →
OrderExecutor → PaperTradingAdapter

PaperTradingAdapter → balances (100% USDT) → postedMargins (per position) →
TradingSystemIntegration.getBalances() → WalletViewModel → UI
```

### 3.4 Key Classes

| Class | Purpose |
|---|---|
| TradingSystemManager | Top-level coordinator; owns lifecycle, wires price feed to coordinator |
| TradingSystemIntegration | Creates and owns all trading components (Singleton) |
| TradingCoordinator | Brain: analysis loop, board convening, signal generation, trade execution |
| PriceBuffer | Ring buffer: 500 OHLCV candles per symbol. All mutations @Synchronized |
| AIBoardOrchestrator | 8-member board: conveneBoardroom() returns BoardConsensus |
| DQNTrader | Online Q-learning, 30-feature state vector, 15s learning loop |
| StahlStairStopManager | Progressive profit-locking. 3.5% sacred initial stop. 12 stair levels |
| PaperTradingAdapter | 100% USDT seed (Build #266). Margin-based trading model |
| TradingCosts | Fees (0.02%/0.05% maker/taker), spread, margin, liquidation price calcs |
| OrderExecutor | Routes orders to adapter; enforces MarginSafeguard |
| BinancePublicPriceFeed | Singleton; polls Binance REST every 5s + WebSocket klines; SharedFlow |
| MarketRegimeDetector | 7 regimes; dynamically adjusts board member voting weights |
| BoardDecisionRepositoryImpl | Persists every board decision to Room; purges >24h hourly |
| SystemLogger | In-app log viewer; 500-entry ring buffer; diagnostics export |

---

## 4. AI Board — The Octagon

Eight specialist agents vote on every symbol every 15 seconds. Confidence: 27–67% confirmed live.

| Member | Role | Specialisation |
|---|---|---|
| **Arthur** | CTO / Chairman | Trend following, EMA/MACD/ADX — In Memoriam |
| **Helena** | CRO | Mean reversion, dead cat bounces, risk management |
| **Sentinel** | CCO (Casting vote) | Volatility trading — 100% accuracy Q1 2025 backtests |
| **Oracle** | CDO | Sentiment, Fear & Greed Index, social volume |
| **Nexus** | COO | On-chain flows, liquidity, exchange volume |
| **Marcus** | CIO | Macro strategy, BTC dominance, DXY correlation |
| **Cipher** | CSO | Pattern recognition, breakout detection |
| **Aegis** | Chief Defense | Liquidity hunting, capital preservation, network defense |

Vote weights are dynamic — MarketRegimeDetector shifts influence based on the current regime: BULL_TRENDING, BEAR_TRENDING, HIGH_VOLATILITY, LOW_VOLATILITY, SIDEWAYS_RANGING, BREAKOUT_PENDING, or CRASH_MODE. Sentinel holds the casting vote in a tie.

**Log pattern:** `🎯 BUILD #236 BOARD`

---

## 5. Hedge Fund Board

Seven specialist agents for hedge fund strategies. Currently wired to HeartbeatCoordinator but operating with minimal market context (single price tick only — needs full OHLCV integration).

| Member | Role | Category |
|---|---|---|
| **Soros** | Chief Economist | Global Macro |
| **Guardian** | Chief Risk Guardian (Casting vote) | Cascade Detection |
| **Draper** | Chief DeFi Officer | DeFi Specialist |
| **Atlas** | Chief Strategist | Regime Meta-Strategist |
| **Theta** | Chief Arbitrage Officer | Funding Rate Analysis |
| **Moby** | Chief Intelligence Officer | Whale Tracker (Crossover) |
| **Echo** | Chief Order Flow Officer | Order Book Imbalance (Crossover) |

**Log pattern:** `⚡ HEDGE FUND` / `⚡ HEDGE FUND ENGINE` / `⚡ HEDGE FUND CONSENSUS`

---

## 6. STAHL Stair Stop™

| | |
|---|---|
| **IP** | Provisional patent filed. Contributed 103% of net profits in backtesting. Primary competitive moat. |

- Initial stop: 3.5% — backtested over 12 months
- Progressive profit locking: 12 stair levels
- Infinite scaling: asymptotic convergence trailing beyond 12 discrete levels
- SHORT protection: take profit capped at 10% below entry (Build #266 fix — was 100%)
- Each position gets its own independent STAHL instance
- Implementation: StahlStairStopManager.kt — CONSERVATIVE / MODERATE / AGGRESSIVE / SCALPING presets

---

## 7. Trading Cost Model (Build #266)

```
Wallet seed:     A$100,000 USDT (100% — no crypto pre-seeding)
Trading model:   LONG/SHORT = USDT margin positions (no physical delivery)

Fees (Binance USDT-M Futures):
  Maker:         0.02% of notional
  Taker:         0.05% of notional (default — market orders)

Spread (half-spread per side):
  BTC/USDT:      0.005% each side (~0.01% round trip)
  ETH/USDT:      0.010% each side (~0.02% round trip)
  SOL/USDT:      0.015% each side (~0.03% round trip)
  XRP/USDT:      0.015% each side (~0.03% round trip)

Round-trip break-even: ~0.11–0.13%
STAHL first stair: 1.5% — well clear of break-even ✅

Account equity:  USDT cash + sum of all unrealised P&L (mark-to-market)
Available margin: Equity − posted margins
Liquidation:     liquidationPriceLong/Short() in TradingCosts.kt
```

---

## 8. Memory Leak Fixes (Build #267)

| Issue | Fix |
|---|---|
| XAI DB unbounded growth (16 writes/min × forever) | Hourly pruning job — keeps 24h, deletes older |
| HedgeFundHeartbeatAdapter orphaned CoroutineScope | Persistent scope + shutdown() — one scope, properly cancelled |
| ReinforcementLearning replayBuffer O(n) removeAt(0) | ArrayDeque with O(1) removeFirst() |

---

## 9. Exchange Connectors

| Component | Status | Notes |
|---|---|---|
| Kraken | ✅ Production-ready | Full REST + WebSocket, HMAC-SHA512, 23 pairs |
| Binance | ✅ Price feed | Public REST (klines + 24hr) — market data only |
| Coinbase | ⚠️ Placeholder | Stub — needs implementation |
| Bybit / OKX / KuCoin / Gate.io | ⚠️ Planned | In ExchangeRegistry enum |
| AI Exchange Interface | ⚠️ Not wired | Schema-learning connector built; pending wiring |

---

## 10. Paper Trading

| | |
|---|---|
| Starting capital | A$100,000 |
| Wallet composition | 100% USDT (Build #266 — pure margin model) |
| Fee simulation | 0.05% taker per side |
| Spread simulation | Per-symbol (BTC 0.01% → XRP 0.03% round-trip) |
| Trade cooldown | 30s per symbol |
| Minimum confidence | 1% (minConfidence=0.01) |
| Minimum board agreement | 2 of 8 members (minAgreement=2) |
| Max concurrent positions | 5 (global cap — Build #270 will allow multiple per symbol) |

---

## 11. FLM — Financial Language Model (Planned)

Three-hat architecture using Llama 3.2 3B (on-device, ~2.5GB at 4-bit GGUF):

| Hat | Input | Output | Trigger |
|---|---|---|---|
| Hat 1 — Sentiment Analyst | ScrapingSentimentProvider output | Bullish/bearish verdict + nuance | Every 5–15 min |
| Hat 2 — Regime Narrator | OHLCV + indicator readings | Narrative thesis for regime | On regime transition |
| Hat 3 — Strategy Critic | Board consensus + market context | Pre-execution sanity check | On board consensus |

Scraping engine already exists: ScrapingSentimentProvider.kt (Fear & Greed + CoinGecko + Reddit).

---

## 12. UI Assets Available

| Asset | Use |
|---|---|
| luxury_logo.png | Splash screen / login centrepiece |
| gold_texture.jpg (antiqued) | Navigation bar background / card headers |
| gold_trim.jpg (bright) | Primary buttons / active highlights |
| blackened_emerald_leather.jpg | Primary app background |
| gold_button_beveled_original.png | Primary action buttons (BUY/CONFIRM) |
| gold_button_beveled.png (dark) | Secondary buttons / data card containers |
| gold_gear_mechanism_original.png | Splash screen / Octagon tab background |
| gold_gear_mechanism.png (dark) | Dashboard card background texture |
| gold_hand_hour_original.png | Clock hour hand (ornate baroque filigree) |
| gold_hand_minute_original.png | Clock minute hand (elegant scrollwork) |
| gold_hand_second_original.png | Clock second hand (clean gold needle) |

Clock screen: relaxation / panic-hide button. Ticking sound required. No face yet — needs Roman numeral skeleton face design.

---

## 13. Known Issues & Next Priorities

### P0 — Build #270 (Next)

1. **Multiple positions per symbol** — unlimited (board decides). Key: `symbol_orderId`. Each position has independent STAHL stair. Remove `containsKey(symbol)` rejection from TradingCoordinator.
2. **Per-trade active bar UI** — show P&L%, current value, entry price, time elapsed, STAHL level, liquidation price. Include **"Close Trade" button** for client absolute control.

### P1 — Build #271+

3. **Per-symbol DQN learning rates** scaled to ATR volatility (BTC slow α=0.001, XRP fast α=0.002)
4. **Clock/relaxation screen** — skeleton watch with gear mechanism background, animated hands, ticking sound, panic-hide button

### P2 — Important

5. Wire Buy/Sell buttons in TradingScreen to real order execution
6. Portfolio analytics: Sharpe, Sortino, Win Rate, Profit Factor from trade history
7. AI Exchange Interface → TradingCoordinator wiring
8. EWC / Catastrophic Forgetting: wire DQNPretrainer.kt
9. Delete curated catalog files after AI Exchange Interface validated

### P3 — Housekeeping

10. Audit all buttons — add 'coming soon' toasts for stubs
11. 22 files with TODO/FIXME annotations
12. 6 non-null assertions remaining in UI code
13. Migrate deprecated StahlStairStop callers to StahlStairStopManager

---

## 14. Recent Build History

| Build | Summary |
|---|---|
| **#263** | XAI board decision persistence fully wired — all 4 getInstance() sites |
| **#264** | Fix consensus.decision → consensus.finalDecision compile blocker |
| **#265** | Live wallet/portfolio/dashboard data wired + Octagon back arrow |
| **#266** | Proper margin model: 100% USDT, TradingCosts.kt, mark-to-market equity |
| **#267** | Fix 3 memory leaks: XAI DB unbounded, orphaned scopes, O(n) buffer |
| **#268** | Fix leverage Int type mismatch (3 sites in AIExchangeAdapterFactory) |
| **#269** | SystemLogger monitoring: Hedge Fund Board, Engine, Risk Manager |

---

## 15. Regulatory Compliance

| Regulation | Jurisdiction | Position |
|---|---|---|
| MiCA | EU (in force Dec 2024) | Not a CASP — software tool, no custody |
| GENIUS Act | US (signed Jul 2025) | Not a stablecoin issuer |
| CLARITY Act | US (passed House Jul 2025) | Not a DCE, broker, or dealer |
| State MTLs | US States | No money transmission |
| AFSL | Australia (ASIC) | Software tool — AFSL not required |

---

## 16. Business Model

| Tier | Price (AUD) | Key Features |
|---|---|---|
| **FREE** | $0 | Paper trading, all strategies, full DHT, MPC wallet, 70-lesson programme |
| **BRONZE** | $2,500/yr | Live crypto spot, 20 trades/day, 5× leverage |
| **SILVER** | $7,500/yr | 100 trades/day, 20× leverage, futures, tax reporting |
| **ELITE** | Auction min $999/mo · cap 2,500 | Unlimited trades, 50× leverage, all assets |
| **APEX** | Auction min $5,999/mo · cap 500 | 100× leverage, custom AI, white-glove |

All paid subscriptions via MiWealth.APP (Stripe ~2.9%). App stores: FREE tier only.

---

## 17. Performance Claims & Backtesting

> ⚠️ CRITICAL: The 48.61% figure from Manus AI is UNVERIFIED and likely inflated. Do not use in VC or sales materials without independent verification.

| | |
|---|---|
| Verified (2025 bear market) | ~20–23% return — actual independent testing |
| Unverified Manus AI claim | 48.61% — do NOT use in pitches |
| Long-term claim (2018–2025) | 2,847% cumulative — requires independent verification |
| STAHL contribution | 103% of net profits in backtest engine — verified |

---

## 18. Session Handoff Notes

**To start a new session:** Say "Continue with Sovereign Vantage"

**GitHub:** MiWealth/sovereign-vantage-android · branch: main · commit f9c8837

**Next session say:** "Continue with Sovereign Vantage — Build #270. Multiple positions per symbol + per-trade close button."

---

*© 2026 MiWealth Pty Ltd • Sovereign Vantage™ • CONFIDENTIAL*
*For Arthur. For Cathryn. For generational wealth.* 💚
