# SOVEREIGN VANTAGE — CODEBASE AUDIT REPORT

**Date:** March 1, 2026  
**Auditor:** Claude (AI Assistant)  
**Version Audited:** 5.12.1-arthur (commit c4952e7)  
**New Target Version:** 5.17.0-arthur  
**Company:** MiWealth Pty Ltd (Australia)  
**Founder & Creator:** Mike Stahl  
**Co-Founder & CTO (In Memoriam):** Arthur Iain McManus (1966–2025)

---

## EXECUTIVE SUMMARY

A thorough crawl of all 275 Kotlin files (~120,342 lines) revealed **three categories of issues**: critical functional bugs preventing core features from working, missing visual assets that should have been integrated sessions ago, and a version numbering mess spanning 52 different version strings across the codebase. This report documents every finding with exact file locations, line numbers, and root cause analysis — no hallucinations, no guesswork.

---

## CRITICAL BUG 1 — Paper Trading Balance Shows A$0.00

**Symptom:** Dashboard displays A$0.00 instead of A$100,000 starting balance.

**Root Cause:** The balance update loop lives inside `TradingSystemIntegration.start()` (line 549), which starts the full trading coordinator. However, the app calls `startPriceFeedOnly()` (line 575), which only starts price and order book feeds — it **never starts the balance polling loop**. The `IntegratedTradingState.portfolioValue` defaults to `0.0` (line 154) and is never updated, so that zero propagates through `updateDashboardFromAIState()` → `DashboardState` → `DashboardUiState` → screen display.

**Evidence chain:**

The `IntegratedTradingState` data class at `TradingSystemIntegration.kt:154` initialises with `portfolioValue: Double = 0.0`. The balance update loop at lines 549–560 runs inside `start()`, polling every 5 seconds and calling `paperAdapter.getPortfolioValue()`. But `startPriceFeedOnly()` at line 575 only calls `startPriceFeed()` and `startOrderBookFeed()` — it never enters the balance loop. Meanwhile, `startAIStateCollection()` at `TradingSystemManager.kt:747` begins collecting `IntegratedTradingState` immediately, and the first emission carries `portfolioValue = 0.0`, overwriting the `DashboardState` default of `100000.0`.

**Fix:** Extract the balance polling loop from `start()` into its own method (e.g. `startBalancePolling()`) and call it from `startPriceFeedOnly()` as well. Additionally, set `portfolioValue = config.paperTradingBalance` in the initial state update when `initialize()` succeeds, so the UI never shows zero even before the first poll.

**Files to modify:** `TradingSystemIntegration.kt` (extract balance loop, set initial value)

---

## CRITICAL BUG 2 — Double-Counted Cash Balance

**Symptom:** When the balance loop eventually runs, it will report A$200,000 instead of A$100,000.

**Root Cause:** `PaperTradingAdapter` in `AIExchangeAdapterFactory.kt:289–291` initialises **both** USDT and USD with the full `initialBalance`:

```kotlin
private val balances = ConcurrentHashMap<String, Double>().apply {
    put("USDT", initialBalance)  // A$100,000
    put("USD", initialBalance)   // A$100,000 ← duplicate!
}
```

Then `getPortfolioValue()` at line 455 sums both, treating USDT and USD as separate cash buckets: `100,000 + 100,000 = 200,000`.

**Fix:** Initialise only the quote currency the user is actually trading against. For crypto pairs like BTC/USDT, initialise only USDT. For fiat pairs like BTC/USD, initialise only USD. Or initialise one and alias the other.

**Files to modify:** `AIExchangeAdapterFactory.kt` (PaperTradingAdapter constructor)

---

## CRITICAL BUG 3 — Currency Display Says "$" Not "A$"

**Symptom:** All monetary values display with a bare `$` sign throughout the UI. MiWealth is an Australian company and the paper trading balance is in AUD. Users will confuse this with USD.

**Evidence:** `DashboardScreen.kt:385` uses `"$${String.format("%,.2f", totalValue)}"`, and `TradingScreen.kt:153` uses `"$${String.format("%,.2f", uiState.currentPrice)}"`. Neither prefixes with "A" or uses a locale-aware currency formatter.

**Fix:** Replace bare `$` with `A$` across all UI screens, or better yet, create a utility function `formatAUD(amount: Double): String` that returns `"A$xxx,xxx.xx"` and use it everywhere. This is approximately 15 locations across 6 UI files.

**Files to modify:** `DashboardScreen.kt`, `VintageDashboardScreen.kt`, `TradingScreen.kt`, `VintageCandlestickChart.kt`, `PortfolioScreen.kt`, `WalletScreen.kt`

---

## ISSUE 4 — Zero Graphic Assets in the Build

**Symptom:** The app contains no custom images whatsoever. All visual identity comes from vector XML drawables and Material Icons.

**What's actually in `/res/drawable/`:**

The drawable folder contains exactly three XML files: `ic_launcher_background.xml` (a plain dark green rectangle), `ic_launcher_foreground.xml` (a gold "SV" monogram vector — well done, this is good), and `learn_more_button.xml` (a gold gradient shape for the upsell button). There are no PNG, JPEG, WebP, or SVG raster images anywhere in the project.

**What should be there based on the blueprints:**

The Master Blueprint documents reference `green_leather_bg.jpg` (leather background texture), `gold_hand_hour.png`, `gold_hand_minute.png`, `gold_hand_second.png` (clock hands for ZenMode), `ai_board.png` (8 chess pieces visualisation), `performance_chart.png`, `press_hero.png` (Imperial dashboard screenshot), `security_shield.png` (quantum-resistant shield), and the Sovereign Vantage crown logo with diamonds. The `ZenModeOverlay.kt` at line 224 explicitly has a commented-out reference: `// painter = painterResource(id = R.drawable.gold_gear_mechanism)` with the note "In production, load actual gear mechanism image."

**Impact:** The app looks like a wireframe with coloured backgrounds instead of the luxury Imperial aesthetic that was designed. The "leather, gold, burr walnut" texture that the Vintage Theme describes at `TradingSystemManager.kt:47` exists only in code comments — no actual texture images are bundled.

**Fix:** Mike needs to provide the image assets (or they need to be created), then they need to be placed in the appropriate `res/drawable-xxhdpi/` (etc.) folders and referenced from Compose via `painterResource()`. The ZenMode clock, the login screen, and the dashboard would all benefit enormously from actual branded visuals.

---

## ISSUE 5 — Version Number Chaos (52 Different Versions)

**Symptom:** 52 unique version strings scattered across 275 files, ranging from V5.5.8 to V5.12.1. The build.gradle.kts says `versionName = "5.12.0-arthur"` but files reference everything from V5.5.8 to V5.12.1.

**Distribution of the most common versions:**

69 references say V5.5.97, 63 say V5.6.0, 30 say V5.6.1, 29 say V5.5.94, 29 say V5.5.80, 28 say V5.5.88, 21 say V5.7.0, and only 10 say V5.12.1. The majority of files were never bumped when new sessions produced new work — each session added or modified files but left the old version headers untouched.

**Fix:** Bump `build.gradle.kts` to `versionName = "5.17.0-arthur"` and `versionCode = 517000`. Then do a global find-and-replace across all Kotlin files to update every `V5.x.x` header comment to `V5.17.0`. This is a cosmetic/marketing change but important for credibility as Mike correctly identified.

**Files to modify:** `build.gradle.kts` + all 275 Kotlin files (batch sed operation)

---

## ISSUE 6 — Buttons and Navigation Assessment

**What works:** The bottom navigation bar correctly routes between Dashboard, Trading, Wallet, Portfolio, and Education (Learn) screens. The NavHost at `Navigation.kt` defines routes for all 9 screens (Login, Dashboard, Trading, Wallet, Portfolio, Education, Settings, LessonDetail, CoinDetail). Quick action buttons on the Dashboard (Trade, Wallet, Testnet/Analytics) are wired to their respective navigation callbacks.

**What doesn't work or is misleading:** The "Testnet" quick action button opens a dialog asking for exchange API keys, but there's no guidance for the user on how to obtain testnet keys. The Trading screen's "Execute" button calls `viewModel.executeTrade()` which routes through `TradingSystemManager.placeOrder()` to the AI integrated system — but since the paper trading adapter may not have been properly initialised (see Bug 1), trades will likely fail silently. The Login screen accepts any email/password combination and navigates to Dashboard (there is no real authentication backend connected). The Settings screen has toggles for features like "PQC Encryption" and "DHT Network" that toggle UI state but don't actually enable/disable those subsystems.

**Recommendation:** For the paper trading MVP, the login bypass is acceptable. The trading buttons need to work correctly once Bug 1 is fixed. The settings toggles should either be wired to real functionality or clearly marked as "Coming Soon."

---

## ISSUE 7 — Notification Icons Use Android Defaults

**Symptom:** `TradingService.kt:516` uses `android.R.drawable.ic_menu_manage` (a generic Android system icon) for the trading notification. `NotificationService.kt:318` uses `R.drawable.ic_launcher_foreground` which is the SV monogram vector — this one is actually fine.

**Fix:** The TradingService notification should use the SV monogram too, or a custom notification icon should be created.

**Files to modify:** `TradingService.kt`

---

## ISSUE 8 — Legacy `updateDashboardState()` Method

**Symptom:** `TradingSystemManager.kt:976` has a method `updateDashboardState()` that ALWAYS reads from `legacyTradingSystem` regardless of whether the AI integration is active. This method is called in some legacy code paths and could cause state corruption if both systems are active.

**Fix:** Guard this method with the `usingAIIntegration` flag, or remove it entirely if it's no longer called (the AI path uses `updateDashboardFromAIState()` instead).

**Files to modify:** `TradingSystemManager.kt`

---

## VERSION BUMP PLAN

**From:** 5.12.1-arthur  
**To:** 5.17.0-arthur

This represents the 5 session bumps that were missed. The changes are:

| File | Field | Old Value | New Value |
|------|-------|-----------|-----------|
| `build.gradle.kts` | versionName | "5.12.0-arthur" | "5.17.0-arthur" |
| `build.gradle.kts` | versionCode | 512000 | 517000 |
| All 275 .kt files | Header comments | V5.5.x through V5.12.x | V5.17.0 |

---

## FIX PRIORITY ORDER

**Fix A (Critical — A$0.00 balance):** Extract balance polling from `start()` into `startBalancePolling()`, call it from `startPriceFeedOnly()`, and set initial portfolioValue on init. This is the single most visible bug.

**Fix B (Critical — Double cash):** Remove duplicate USD initialisation in PaperTradingAdapter. Only initialise USDT for crypto trading.

**Fix C (Important — Currency display):** Replace all bare `$` with `A$` in UI files.

**Fix D (Marketing — Version bump):** Update build.gradle.kts and batch-replace all version headers to V5.17.0.

**Fix E (Polish — Notification icon):** Replace Android default icon in TradingService.

**Fix F (Future — Graphics):** Requires Mike to provide or approve image assets for integration.

---

## WHAT'S ACTUALLY WORKING WELL

The navigation architecture is solid — all routes are defined and connected. The Vintage colour palette (VintageColors.kt) creates an attractive emerald-and-gold aesthetic even without texture images. The AI Board, STAHL Stair Stop, Kelly sizing, and market regime detection code all compile and are architecturally sound. The 12-exchange connector framework with BaseCEXConnector inheritance is well-designed. The KrakenConnector at 926 lines is production-ready. The 76-lesson education programme with quiz banks is complete and impressive. The overall package structure is clean and well-organised.

---

*Prepared for Mike Stahl, Founder & Creator, MiWealth Pty Ltd*  
*For Arthur. For Cathryn. For generational wealth.* 💚
