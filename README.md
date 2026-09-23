# Morphe Fetch

The native Android companion for [Morphe Manager](https://github.com/MorpheApp) to resolve and download APKs directly on device with zero friction.

---

### Core Architecture & Capabilities

- **Native Browser Emulation & War Mode** — Device-authentic Chrome User-Agent headers, Chromium TLS cipher suite ordering, full Client Hints (`sec-ch-ua`, `sec-ch-ua-mobile`, `sec-ch-ua-platform`), and structured fetch metadata (`Sec-Fetch-*`).
- **Headless Cloudflare Session Warming** — Background WebView pre-warms sessions and automatically intercepts HTTP 403 / verification challenges, passing verified cookies and sessions to native download workers with zero user prompts.
- **Dynamic Source Resolvers** — High-speed direct resolution across APKMirror, Uptodown, APKPure, APKCombo, Aptoide, and Google Play fallback.
- **Zero-Lag Search** — Debounced asynchronous querying and virtualized lazy list rendering on the home screen.
- **Comprehensive Version History** — Formatted release dates and release suffix badges (Secondary, Beta, Alpha, Wear OS, Android TV) distinguishing primary vs. secondary variants.
- **Smart ABI Architecture Selection** — Automatically prioritizes native CPU ABI matches (e.g. `arm64-v8a`) to eliminate incompatible installs.
- **Morphe Manager Intent Integration** — Seamlessly satisfies Morphe Manager download contracts with APK verification and handoff.
- **Real-Time Download Indicators** — Live downloaded / total size indicators with instant visual feedback.

---

### Supported Sources

APKMirror · Uptodown · APKPure · APKCombo · Aptoide · Google Play *(fallback)*

- Per-source toggles and configurable default provider
- Integrated WebView challenge solver and automated background session keeper
- Standalone search or Morphe Manager zero-click auto-fetch mode
