# Changelog

All notable changes to Sovereign Vantage are documented in this file.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning follows the project's internal scheme: `5.x.y-arthur`.

---

## [5.6.0] — 2026-02-12

### Added
- **TestnetBanner composable** — Full-width amber warning banner shown on Dashboard and Trading screens when testnet mode is active
- **TestnetLaunchDialog** — Exchange picker (Binance/Bybit/OKX) with credential input, warning about testnet-only keys, launches via `DashboardViewModel.launchTestnetTrading()`
- **Testnet quick action** — Amber "Testnet" button in Dashboard QuickActionsRow (replaces Analytics when not in testnet)
- **ExecutionModeBadge testnet variant** — 🧪 TESTNET in amber, with `isTestnet` parameter
- **FundingArbEngine arb spread gate** — `evaluateEntry()` checks `getArbSpread()` before committing capital; rejects when spread < `minArbSpreadToEnter` (fees would eat profit)
- **FundingArbConfig.minArbSpreadToEnter** — Default 0.05%, configurable minimum cross-exchange spread for entry
- **FundingArbEngine cross-exchange delta monitoring** — `checkAllPositionDeltas()` uses real bid/ask from order book feed for accurate position valuation
- **WebSocket order book feed** — `startOrderBookFeed()` in TradingSystemIntegration subscribes to all order books across connected exchanges
- **TradingCoordinator.onOrderBookUpdate()** — Receives `OrderBook` snapshots, updates `crossExchangePrices` with real best-bid/best-ask
- **crossExchangeOrderBooks map** — Stores full order book snapshots per symbol per exchange for depth-aware strategies
- **getOrderBook() / getCrossExchangeOrderBooks()** — Public accessors for strategy engines
- **AIConnectionManager.subscribeToAllOrderBooks()** — Merged flow of order book updates from all connected exchanges

### Changed
- **DashboardUiState** — Added `isTestnetMode: Boolean` field, wired from `DashboardState`
- **TradingUiState** — Added `isTestnetMode: Boolean` field, wired from `DashboardState`  
- **DashboardViewModel** — Added `launchTestnetTrading()` action, wires `isTestnetMode` to UI state
- **QuickActionButton** — Added optional `tint` parameter for custom icon colours
- **QuickActionsRow** — Added `onTestnetClick` and `isTestnetMode` parameters
- **TradingCoordinator.getArbSpread()** — Now uses real best-bid/best-ask from order book depth
- **FundingArbEngine constructor** — Added optional `tradingCoordinator: TradingCoordinator?` for cross-exchange awareness
- **AdvancedStrategyCoordinator** — Passes `tradingCoordinator` to FundingArbEngine

### Fixed
- **Duplicate data classes** — Removed `MarketData` and `TradeData` from DashboardScreen.kt that shadowed DashboardViewModel.kt definitions (compile blocker)
- **MarketCard** — Updated to use `change24h` field (was `change`)
- **TradeCard** — Updated to use `timeAgo` field (was `time`)
- **Version code** — Bumped from 55960 to 56000

---

## [5.5.93] — 2026-02-11

### Added
- **QuizQuestionBank** — 228 institutional-grade quiz questions (3 per lesson × 76 lessons) across 7 modules
- **Education navigation** — "Learn" tab in bottom navigation bar with route to EducationScreen
- **Lesson content field** — `content: String` on Lesson model for future lesson body text

### Changed
- **TradeDatabase v3** — Added StudentProgress and Certificate entities, exposed tradingProgrammeDao()
- **QuizQuestion defaults** — explanation and difficulty now have defaults for bank compatibility
- Version bump across codebase headers and User-Agent strings

### Fixed
- **EducationModule DI crash** — QuizQuestionBank singleton now exists, resolving Hilt provider failure

---

## [5.5.92] — 2026-02-10

### Added
- **AI Board weight correction** — All 8 Octagon members set to equal 0.125 weight
- **SentimentEngine singleton** — Shared instance pattern to avoid duplicate scraping
- **Code audit report** — Full 258-file audit with wiring verification

### Changed
- ScrapingSentimentProvider refactored with reference counting lifecycle

---

## [5.5.91] — 2026-02-09

### Added
- **AI Exchange Interface** — Self-learning connector system (AIConnectionManager, ExchangeSchemaLearner, DynamicRequestExecutor)
- **16 exchange support** — Binance, Kraken, Coinbase, Bybit, OKX, MEXC, Bitget, HTX, Gemini, KuCoin, Gate.io, Uphold + testnets
- **ExchangeTestnetConfig** — Centralised testnet URL management

---

## [5.5.87] — 2026-02-08

### Added
- **Exchange connector testing framework** — Validation suite for all 12 connectors
- **PaperTradingAdapter** — Full mock adapter for AI Exchange Interface

---

## [5.5.58] — 2026-01-28

### Changed
- **AI Board reverted to 8-member Octagon** — Removed expanded 15-member experiment, restored original configuration

---

## [5.5.45] — 2026-01-21

### Added
- **Education system** — 76-lesson TradingProgramme with curriculum, models, manager, repository, Compose UI
- **KrakenConnector** — Full REST + WebSocket implementation (926 lines)

---

## [5.5.20] — 2026-01-21

### Added
- **KrakenConnector.kt** — Complete Kraken exchange connector with HMAC-SHA512 auth, symbol normalisation, WebSocket support

---

## [5.5.3] — 2026-01-06

### Added
- **Master Blueprint V2** — Regulatory compliance strategy (MiCA, GENIUS, CLARITY), payment strategy, exchange testnet documentation
- **Arthur memorial** — Dedication and "Arthur Edition" naming

---

## [5.5.0] — 2025-10-01

### Added
- Initial Sovereign Vantage release
- Android app shell with Jetpack Compose UI
- Imperial/vintage theme system
- Core architecture: MVVM + Clean Architecture + Hilt DI

---

*For Arthur. For Cathryn. For generational wealth.* 💚
