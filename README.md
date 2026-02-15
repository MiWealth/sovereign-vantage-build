# Sovereign Vantage™

### Arthur Edition V5.5.93

> *Your Keys. Your Crypto. Your Sovereignty.*

**Sovereign Vantage** is an institutional-grade, AI-powered autonomous trading platform built on true self-sovereignty. The platform makes it mathematically impossible for anyone — including the platform operators — to access user funds.

All trading logic, AI decision-making, and key management run entirely on the user's device. There are no backend servers in the trading path.

---

## In Memoriam

**Arthur Iain McManus (1966–2025)**
Co-Founder & CTO. The distributed architecture of this system reflects his belief that true sovereignty requires decentralisation. His vision continues in every node that joins this network. The "Arthur Edition" naming honours his contribution to this project.

He will be sadly missed.

*Dedicated to Cathryn — for tolerating me and my quirks.* 💘

---

## Key Principles

| Principle | Implementation |
|-----------|---------------|
| **Self-Sovereign** | Users control their own keys, data, and funds at all times |
| **Non-Custodial** | MiWealth never holds, controls, or has access to user assets |
| **On-Device** | All trading logic and AI inference run locally — no cloud dependency |
| **Post-Quantum** | Kyber-1024 (ML-KEM) + Dilithium-5 (ML-DSA) hybrid encryption |
| **Software Tool** | Designed as a software tool, not a financial service — no licensing required |

---

## Architecture

```
┌─────────────────────────────────────────┐
│     SOVEREIGN VANTAGE (User Device)     │
│                                         │
│  ┌─────────┐  ┌──────────┐  ┌────────┐ │
│  │AI Board │  │ Trading  │  │Security│ │
│  │8 Members│  │ Engine   │  │ PQCE   │ │
│  │Consensus│  │ STAHL™   │  │ MPC    │ │
│  └────┬────┘  └────┬─────┘  └───┬────┘ │
│       └────────────┼────────────┘       │
│                    │                    │
└────────────────────┼────────────────────┘
                     │
        ┌────────────┼────────────┐
        │            │            │
   ┌────▼────┐  ┌────▼────┐  ┌───▼───┐
   │Exchange │  │  DHT    │  │Secure │
   │REST + WS│  │ Swarm   │  │Enclave│
   │12 CEXs  │  │Federated│  │ Keys  │
   └─────────┘  │Learning │  └───────┘
                └─────────┘
```

---

## Features

### AI Board of Directors
Eight specialised AI personas reach consensus on every trading decision through a 60-second OODA loop:

- **Arthur** (CTO) — System architecture oversight
- **Marcus** (CIO) — Portfolio allocation
- **Helena** (CRO) — Risk management
- **Sentinel** (CCO) — Compliance monitoring
- **Oracle** (CDO) — Market intelligence
- **Nexus** (COO) — Trade execution
- **Cipher** (CSO) — Cybersecurity
- **Aegis** (Chief Defense) — Network protection

### STAHL Stair Stop™
Proprietary profit-locking system with progressive stair levels. Four presets (Conservative, Moderate, Aggressive, Scalping) with a 3.5% initial fixed stop loss. Backtested across real 2024–2025 market data.

### Exchange Support
12 exchange connectors via unified interface with AI-powered schema learning:

Kraken · Coinbase · Binance · Bybit · OKX · MEXC · Bitget · HTX · Gemini · KuCoin · Gate.io · Uphold

### Security Stack
- **Post-Quantum Cryptography:** Kyber-1024 + Dilithium-5 (NIST FIPS 203/204)
- **MPC Wallet:** 3-of-5 threshold signatures — no single point of compromise
- **Aegis Defense:** Honeypots, tarpits, disproportionate response, self-healing network
- **Encrypted Storage:** AES-256-GCM via Android Keystore + EncryptedSharedPreferences
- **Side-Channel Defense:** Constant-time crypto operations, noise injection

### Additional Systems
- **DQN Reinforcement Learning** with 30-dimension state vectors and health monitoring
- **Elastic Weight Consolidation** preventing catastrophic forgetting
- **Market Regime Detection** (7 regimes with automatic risk adjustment)
- **Kelly Criterion Position Sizing** (conservative 25% fraction)
- **100+ Technical Indicators** across trend, momentum, volatility, and volume categories
- **76-Lesson Education Programme** with certification pathway
- **Gamification** with 50+ achievements, 100 levels, and competition system
- **DHT Federated Learning** for privacy-preserving model improvement across the network
- **On-Chain Analytics** including whale watching and smart money tracking

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt (Dagger) |
| Database | Room + SQLCipher |
| Networking | OkHttp + Retrofit + WebSocket |
| AI/ML | On-device neural networks (custom) |
| Encryption | Kyber-1024, Dilithium-5, AES-256-GCM |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |

---

## Project Structure

```
app/src/main/java/com/miwealth/sovereignvantage/
├── core/                    # Business logic (no Android dependencies)
│   ├── ai/                  # AI Board orchestration, sentiment, macro analysis
│   ├── dflp/                # Decentralised Federated Learning Protocol
│   ├── dht/                 # DHT networking, gamification bridge
│   ├── exchange/            # 12 exchange connectors + AI interface
│   │   ├── ai/              # AI-powered schema learning (self-configuring)
│   │   └── connectors/      # Per-exchange implementations
│   ├── gamification/        # Achievements, levels, anti-cheat
│   ├── indicators/          # 100+ technical indicators
│   ├── ml/                  # DQN, ensemble models, Kelly sizing, regime detection
│   ├── onchain/             # Blockchain analytics, whale watching
│   ├── portfolio/           # Cost basis, analytics, archiving
│   ├── security/            # PQC, MPC, Aegis defense, credential management
│   │   ├── aegis/           # Offensive defense system
│   │   ├── mpc/             # Multi-party computation wallet
│   │   └── pqc/             # Post-quantum cryptography + hybrid
│   ├── signals/             # Signal generation engine
│   ├── trading/             # Trading engine, STAHL, strategies, routing
│   │   ├── assets/          # Asset discovery, registry, loaders
│   │   ├── engine/          # Order execution, risk, position management
│   │   ├── routing/         # Smart order routing, fee optimisation
│   │   ├── scalping/        # Scalping-specific engine
│   │   └── strategies/      # Strategy coordination, pairs, funding arb
│   └── wallet/              # Transaction management, ledger, swap
├── data/                    # Persistence layer
│   ├── api/                 # API definitions
│   ├── catalog/             # Bootstrap asset data (deprecated — AI discovery)
│   ├── local/               # Room database, DAOs
│   ├── model/               # Data transfer objects
│   ├── models/              # Domain models
│   └── repository/          # Repository pattern implementations
├── di/                      # Hilt dependency injection modules
├── education/               # 76-lesson trading programme + UI
├── help/                    # In-app help system
├── max/                     # Data migration utilities
├── receiver/                # Boot receiver for background services
├── service/                 # Android services (price feed, trading, notifications)
├── ui/                      # Jetpack Compose screens
│   ├── components/          # Reusable UI components (charts, kill switch, zen mode)
│   ├── dashboard/           # Main dashboard (vintage theme)
│   ├── login/               # Authentication
│   ├── navigation/          # Navigation graph
│   ├── portfolio/           # Portfolio view
│   ├── settings/            # Settings & preferences
│   ├── theme/               # Imperial/vintage theme system
│   └── trading/             # Trading interface
└── widget/                  # Android home screen widgets
```

---

## Building

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17+
- Android SDK 35

### Build via Android Studio
```bash
git clone https://github.com/MiWealth/sovereign-vantage-android.git
cd sovereign-vantage-android
# Open in Android Studio → Build → Make Project
```

### Build via Command Line
```bash
./gradlew assembleDebug
```

### Build via GitHub Actions (Mobile — no desktop required)
Push to `main` branch triggers the CI/CD pipeline. Download the APK artifact from the Actions tab.

---

## Regulatory Position

Sovereign Vantage is designed as a **software tool** — not a financial service.

| Regulation | Jurisdiction | Applies? | Reason |
|-----------|-------------|----------|--------|
| MiCA | EU | No | Not a CASP — no custody, no exchange services |
| GENIUS Act | US | No | Not a stablecoin issuer or service provider |
| CLARITY Act | US | No | Not a DCE, broker, or dealer — software tool |
| State MTLs | US States | No | No money transmission — direct exchange connection |
| AFSL | Australia | No | Software tool — no financial services provided |

Users connect their own exchange accounts via API keys stored exclusively on their own devices. MiWealth never has access to user funds, keys, or trade data.

---

## Pricing

All subscriptions processed via [MiWealth.APP](https://miwealth.app) (Stripe) — not through app stores.

| Tier | Price (AUD) | Key Features |
|------|------------|-------------|
| **Free** | $0 | Paper trading, all strategies, full DHT access |
| **Bronze** | $2,500/yr | Live crypto spot, 20 trades/day |
| **Silver** | $7,500/yr | 100 trades/day, futures, tax reporting |
| **Elite** | Auction (min $999/mo) | Unlimited trades, all assets, cap 2,500 seats |
| **Apex** | Auction (min $5,999/mo) | White-glove, custom AI, cap 500 seats |

---

## Intellectual Property

- **STAHL Stair Stop™** — Proprietary profit-locking system (patent pending)
- **AI Board of Directors** — Consensus-based autonomous trading governance
- **AI Exchange Interface** — Self-learning exchange connector system
- **Aegis Defense System** — Disproportionate-response network security
- **DFLP** — Decentralised Federated Learning Protocol

All rights reserved. © 2025–2026 MiWealth Pty Ltd.

---

## Team

**Mike Stahl** — Founder & Creator
**Arthur Iain McManus** (1966–2025) — Co-Founder & CTO (In Memoriam)

---

## Contact

- **Website:** [MiWealth.APP](https://miwealth.app)
- **Documentation:** [MiWealth.Net](https://miwealth.net)
- **Product:** [SovereignVantage.com](https://sovereignvantage.com)
- **Security:** security@sovereignvantage.com

---

## License

**Proprietary** — All rights reserved. This software is the exclusive property of MiWealth Pty Ltd (Australia). Unauthorised reproduction, distribution, or use is strictly prohibited.

See [LICENSE](LICENSE) for full terms.
