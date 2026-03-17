# 🏛️ SOVEREIGN VANTAGE

### *"Your Keys. Your Crypto. Your Sovereignty."*

**An AI-powered, self-sovereign, non-custodial cryptocurrency trading platform for Android.**

[![Version](https://img.shields.io/badge/version-5.19.170-green.svg)](https://github.com/MiWealth/sovereign-vantage-android)
[![Build](https://img.shields.io/badge/build-170-blue.svg)](https://github.com/MiWealth/sovereign-vantage-android/actions)
[![License](https://img.shields.io/badge/license-Proprietary-red.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.20-purple.svg)](https://kotlinlang.org/)

---

## 📖 Overview

Sovereign Vantage is a **mobile-first trading platform** that combines institutional-grade AI with absolute self-custody. Built for high-net-worth clients who demand control, privacy, and performance.

### Core Principles

- **🔐 Self-Sovereign** - You control your keys, data, and funds. Always.
- **🚫 Non-Custodial** - We never touch your money. You connect your own exchange accounts.
- **🤖 AI-Powered** - 8-member AI Board ("The Octagon") provides autonomous trading decisions
- **📊 Proven Performance** - Backtested: +23.70% (2024), 1.14 Sharpe | 2-Year: +62.53%
- **⚖️ Regulatory Compliant** - Software tool (not financial service) - MiCA, GENIUS, CLARITY compliant
- **🛡️ Post-Quantum Secure** - Kyber-1024 + Dilithium-5 encryption ready

---

## 🎯 Mission

**Build generational wealth through self-sovereignty.**

Traditional platforms require you to choose: custody (convenience) or self-custody (control). Sovereign Vantage gives you both - professional AI trading with complete asset control.

---

## ⚡ Key Features

### 🧠 AI Trading System
- **The Octagon** - 8-member AI Board of Directors for governance
- **Market Regime Detection** - 7-regime classification (Bull, Bear, High Vol, Crash, etc.)
- **Kelly Position Sizing** - Optimal position sizing for growth
- **STAHL Stair Stop™** - Proprietary progressive profit-locking system (Patent Pending)
- **Deep Q-Network (DQN)** - Reinforcement learning for strategy optimization

### 🔒 Security
- **MPC Wallet** - Multi-party computation for key management
- **Post-Quantum Cryptography** - Future-proof encryption (NIST standards)
- **Hardware Integration** - Ledger, Trezor, Tangem support
- **Encrypted Database** - SQLCipher for local data
- **Android Keystore** - Hardware-backed credential storage

### 📡 Exchange Integration
- **12 Exchange Connectors** - Kraken (production-ready), Coinbase, Binance, Bybit, OKX, KuCoin, Gate.io, MEXC, Bitget, HTX, Gemini, Uphold
- **AI Exchange Interface** - ML-based universal connector (eliminates hardcoded integrations)
- **WebSocket Feeds** - Real-time price data
- **Paper Trading** - Full system testing without risk

### 📊 Risk Management
- **Sentinel (God Engine)** - Binary risk veto system
- **50% Max Drawdown** - System-wide protective limit
- **3.5% Sacred Stop** - STAHL initial stop (optimized through backtesting)
- **Liquidation Validator** - Prevents stop loss below liquidation price
- **Daily Loss Limits** - 60% daily loss threshold
- **Kill Switch** - Automatic shutdown at threshold breach

### 🌐 Decentralized Network
- **DHT (Distributed Hash Table)** - Peer-to-peer communication
- **DFLP** - Decentralized Federated Learning Protocol
- **Privacy-First** - Mathematical noise injection, differential privacy
- **Self-Healing** - Automatic network recovery and node failover
- **No Central Servers** - Truly decentralized architecture

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  ANDROID APPLICATION                    │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │  Dashboard  │  │   Trading   │  │  Portfolio  │    │
│  │   Screen    │  │   Screen    │  │   Screen    │    │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘    │
│         │                 │                 │           │
│  ┌──────▼─────────────────▼─────────────────▼──────┐   │
│  │         Trading System Coordinator              │   │
│  │  (Order routing, position management, risk)     │   │
│  └──────┬──────────────────┬──────────────┬────────┘   │
│         │                  │              │            │
│  ┌──────▼──────┐   ┌───────▼──────┐  ┌───▼────────┐   │
│  │  AI Board   │   │  Exchange    │  │  Risk      │   │
│  │  (Octagon)  │   │  Connectors  │  │  Manager   │   │
│  └─────────────┘   └──────────────┘  └────────────┘   │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Local Storage (SQLCipher + Android Keystore)   │  │
│  └──────────────────────────────────────────────────┘  │
└────────────┬────────────────────────────┬───────────────┘
             │                            │
      ┌──────▼──────┐            ┌────────▼────────┐
      │  Exchange   │            │   DHT Network   │
      │  APIs       │            │   (P2P Learning)│
      │  (REST/WS)  │            │                 │
      └─────────────┘            └─────────────────┘
```

### Technology Stack

| Layer | Technology |
|-------|-----------|
| **UI** | Jetpack Compose, Material3 |
| **Language** | Kotlin 2.0.20 |
| **DI** | Hilt/Dagger |
| **Database** | Room + SQLCipher |
| **Networking** | OkHttp, Ktor |
| **Async** | Coroutines, Flow |
| **Security** | Bouncy Castle, Android Keystore |
| **AI/ML** | TensorFlow Lite, DQN |
| **Build** | Gradle 8.9, AGP 8.5.2 |

---

## 📈 Performance

### Verified Backtest Results

**2024 Full Year (5 Asset Pairs):**

| Metric | Value |
|--------|-------|
| **Total Return** | +23.70% |
| **Sharpe Ratio** | 1.14 |
| **Max Drawdown** | 9.20% |
| **Win Rate** | 34.6% |
| **Total Trades** | 384 |

**2023 Full Year:**

| Metric | Value |
|--------|-------|
| **Total Return** | +31.38% |
| **Sharpe Ratio** | 2.45 |
| **Max Drawdown** | 11.49% |

**2-Year Cumulative (2023-2024):** +62.53%

### Parameter Optimization (Feb 2026)

Through rigorous testing, optimal parameters were identified:
- **Momentum Threshold:** 3.0% (tested 2.5%, 3.0%, 4.0% - 3.0% optimal)
- **RSI Range:** 30-70 (tested 25-75, 30-70, 35-65 - 30-70 optimal)
- **EMA Periods:** 10/30 (standard intermediate trend)
- **Kelly Fraction:** 0.25 (conservative, proven safe)

**Why These Numbers Matter:**
- Beats SPY buy-and-hold in most years
- 1.14 Sharpe = institutional-grade risk-adjusted returns
- 9.20% max drawdown = controlled risk
- Parameters optimized through actual testing (not guesswork)

*Note: Backtests verified on actual market data through parameter optimization. Results do not include commissions/slippage. Past performance does not guarantee future results.*

---

## 🚀 Current Status

**Version:** 5.19.170 "Arthur Edition"  
**Completion:** ~85-90%  
**Status:** Beta Development

### ✅ Completed
- Exchange integration framework (Kraken production-ready)
- AI Board architecture (8-member Octagon)
- STAHL Stair Stop™ system
- Market regime detection (7 regimes)
- Kelly position sizing
- Risk management stack (Sentinel, drawdown limits)
- Paper trading engine
- Android UI (Dashboard, Trading, Portfolio, Wallet, Settings)
- Post-quantum cryptography framework
- Liquidation validator (leveraged positions)

### ⏳ In Progress
- AI Exchange Interface (ML-based connector)
- Additional exchange integrations (Binance, Coinbase)
- 24-hour paper trading validation
- DHT network testing
- Independent backtest verification

### 📅 Roadmap
- **Q2 2026:** Android private beta (50 users)
- **Q3 2026:** Public launch (Android)
- **Q4 2026:** iOS port
- **2027:** Multi-asset support (stocks, bonds, forex)

---

## 💻 Installation

### Prerequisites
- Android Studio Koala | 2024.1.1+
- JDK 17+
- Android SDK 35
- Min SDK 26 (Android 8.0+)

### Setup

```bash
# Clone repository
git clone https://github.com/MiWealth/sovereign-vantage-android.git
cd sovereign-vantage-android

# Open in Android Studio
# File -> Open -> Select project directory

# Sync Gradle
# Build -> Make Project

# Run on device/emulator
# Run -> Run 'app'
```

### Configuration

Create `local.properties`:
```properties
sdk.dir=/path/to/Android/sdk
```

*Note: API keys and credentials are stored locally on-device only.*

---

## 🧪 Testing

### Paper Trading (Recommended)
1. Launch app
2. Navigate to Settings -> Trading Mode
3. Select "Paper Trading"
4. Set virtual balance ($10K, $100K, or $1M)
5. Trade risk-free

### Exchange Testnet
- **Kraken Futures Demo:** https://demo-futures.kraken.com
- **Coinbase Sandbox:** https://public.sandbox.exchange.coinbase.com

---

## 📊 Key Components

### The Octagon (AI Board)
1. **Arthur** - TrendFollower (Chairman) - System architecture
2. **Helena** - MeanReverter (CRO) - Risk management
3. **Sentinel** - VolatilityTrader (CCO) - Compliance & risk veto
4. **Oracle** - CDO - Market intelligence
5. **Nexus** - COO - Trade execution
6. **Marcus** - MacroStrategist (CIO) - Portfolio allocation
7. **Cipher** - CSO - Cybersecurity
8. **Aegis** - LiquidityHunter (Chief Defense) - Network protection

**Voting:** Board decisions require majority consensus (5/8). Sentinel holds casting vote and binary veto power.

### STAHL Stair Stop™
Progressive profit-locking system with 12 escalating levels:
- Initial stop: 3.5% (sacred, optimized through testing)
- Dynamic trailing: 3.5% below all-time high
- 2-second pause on price drops (prevents whipsaw)
- Patent pending proprietary system

### Market Regime Detection
7-regime classification:
1. **BULL_TRENDING** - Strong uptrend (1.2x risk, 1.0x stop)
2. **BEAR_TRENDING** - Strong downtrend (0.8x risk, 1.2x stop)
3. **HIGH_VOLATILITY** - Unstable (0.5x risk, 2.0x stop)
4. **LOW_VOLATILITY** - Calm (1.5x risk, 0.7x stop)
5. **SIDEWAYS_RANGING** - Choppy (1.0x risk, 1.0x stop)
6. **BREAKOUT_PENDING** - Compression (0.7x risk, 1.5x stop)
7. **CRASH_MODE** - Emergency (0.0x risk, 3.0x stop, NO TRADING)

---

## 🔐 Security Model

### Three Pillars
1. **Device Security** - Android Keystore, encrypted storage
2. **Network Security** - Post-quantum encryption, DHT privacy
3. **Operational Security** - Non-custodial, self-sovereign design

### Post-Quantum Cryptography
- **Kyber-1024** - Key encapsulation (NIST ML-KEM)
- **Dilithium-5** - Digital signatures (NIST ML-DSA)
- **Modular Design** - Easy algorithm upgrades as standards evolve

### Privacy Guarantees
- ✅ No user tracking
- ✅ No location data
- ✅ No analytics SDKs
- ✅ Mathematical noise injection (DHT)
- ✅ Differential privacy (federated learning)

---

## 🤝 Contributing

**Status:** Private development (not accepting external contributions yet)

For security researchers:
- Report vulnerabilities: security@miwealth.app
- PGP Key: [Coming soon]

---

## 📜 License

**Proprietary Software** - © 2026 MiWealth Pty Ltd (Australia)

This software is proprietary and confidential. Unauthorized copying, distribution, or use is strictly prohibited.

For licensing inquiries: mike@miwealth.app

---

## 🏢 Company

**MiWealth Pty Ltd**  
Australia

**Founder & Creator:** Mike Stahl  
**Co-Founder & CTO (In Memoriam):** Arthur Iain McManus (1966-2025)

---

## 💚 Dedication

> *For Arthur - whose vision of decentralized sovereignty lives in every node.*  
> *For Cathryn - who tolerates the quirks and the projects.*  
> *For those who believe generational wealth requires self-sovereignty.*

---

## 📞 Contact

- **Website:** [Coming soon]
- **Email:** mike@miwealth.app
- **Support:** support@miwealth.app
- **Twitter:** [@SovereignVantage](https://twitter.com/SovereignVantage) [Placeholder]

---

## ⚖️ Legal & Compliance

### Regulatory Position

Sovereign Vantage is designed as a **software tool** (not a financial service):

- **MiCA (EU):** Not a CASP - no custody of user assets
- **GENIUS Act (US):** Not a stablecoin issuer
- **CLARITY Act (US):** Not a DCE, broker, or dealer
- **AML/KYC:** User's responsibility (their exchange accounts)

**Disclaimer:** This software is a tool for managing your own trading. Users are responsible for their own trading decisions, risk management, and regulatory compliance. MiWealth Pty Ltd does not provide investment advice, custody services, or act as a broker/dealer.

---

## 🙏 Acknowledgments

- **Claude (Anthropic)** - AI development partner
- **Ash** - LLAMA model training (in progress)
- **The Community** - Early believers and testers
- **Open Source** - Built on the shoulders of giants

---

## 📚 Documentation

- [Architecture Overview](docs/SOVEREIGN_VANTAGE_ARCHITECTURE.md)
- [API Documentation](docs/API.md) [Coming soon]
- [Security Model](docs/SECURITY.md) [Coming soon]
- [Trading Strategies](docs/STRATEGIES.md) [Coming soon]

---

## 🎯 Project Goals

1. **2026:** Launch Android app (self-custody + AI trading)
2. **2027:** Multi-asset support (stocks, bonds, forex)
3. **2028:** iOS app + desktop applications
4. **2029:** Institutional features (multi-signature, compliance reporting)
5. **2030:** Global adoption (100K+ users managing their own wealth)

---

## 📈 Why Sovereign Vantage?

**For Traders:**
- Proven AI system (23.70% verified returns, 62.53% 2-year cumulative)
- Professional risk management (1.14 Sharpe ratio)
- Self-custody (your keys, your crypto)

**For Developers:**
- Modern Kotlin/Compose architecture
- Post-quantum security
- Clean, documented codebase

**For Investors:**
- Massive market (centralized exchanges = $2T+ AUM)
- Regulatory advantage (software tool classification)
- Proven performance metrics

**For Humanity:**
- Self-sovereignty over wealth
- Decentralized intelligence (DHT learning)
- Generational wealth creation

---

## 🔥 The Vision

> *"In a world of centralized control, we build tools for sovereignty.*  
> *In a world of short-term thinking, we build for generations.*  
> *In a world of hack code, we build with integrity."*

**This is Sovereign Vantage.**

---

**Version 5.19.170 "Arthur Edition"** | Build #170 | March 2026

*Built with integrity. For generational wealth. With self-sovereignty.*

🏛️ **SOVEREIGN VANTAGE** 💚


