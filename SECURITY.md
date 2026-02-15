# Security Policy

## Sovereign Vantage™ — Security

Sovereign Vantage is built security-first. The entire architecture is designed around the principle that **no one — including MiWealth — should be able to access user funds**.

### Supported Versions

| Version | Supported |
|---------|-----------|
| 5.5.x (Arthur Edition) | ✅ Active development |
| < 5.5 | ❌ Unsupported |

### Architecture Overview

- **Non-custodial:** User keys never leave their device
- **Post-quantum cryptography:** Kyber-1024 (ML-KEM) + Dilithium-5 (ML-DSA)
- **MPC wallet:** 3-of-5 threshold signatures — no single point of compromise
- **Encrypted storage:** AES-256-GCM via Android Keystore + SQLCipher
- **Zero analytics:** No tracking SDKs, no telemetry, no data collection

### Reporting a Vulnerability

If you discover a security vulnerability, **please do not open a public issue.**

Instead, report it responsibly:

**Email:** security@sovereignvantage.com

Please include:
- Description of the vulnerability
- Steps to reproduce
- Potential impact assessment
- Your suggested fix (if any)

### Response Timeline

| Stage | Timeframe |
|-------|-----------|
| Acknowledgement | Within 48 hours |
| Initial assessment | Within 7 days |
| Fix development | Based on severity |
| Disclosure | Coordinated with reporter |

### Scope

The following are **in scope** for security reports:

- Cryptographic weaknesses in PQC or MPC implementations
- Key extraction or leakage vulnerabilities
- Authentication or authorisation bypass
- Data exposure from encrypted storage
- Network-level attacks against DHT or exchange connections
- Side-channel attacks against on-device operations

The following are **out of scope:**

- Exchange-side vulnerabilities (report to the exchange directly)
- Social engineering attacks
- Denial of service against user devices
- Issues requiring physical access to an unlocked device

### Bug Bounty

We do not currently operate a formal bug bounty programme. Critical vulnerability reporters will be acknowledged (with permission) and may receive recognition at our discretion.

---

© 2025–2026 MiWealth Pty Ltd. All Rights Reserved.
