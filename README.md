# Morphe Fetch

Fast, native Android companion downloader for [Morphe Manager](https://github.com/MorpheApp) and standalone APK retriever.

A hard fork of [`helper-for-morphe`](https://github.com/rushiranpise/helper-for-morphe) by [Rushi Ranpise](https://github.com/rushiranpise), maintained and evolved by [Akash Sriram](https://github.com/Akash-Sriram).

---

### Features

- **Multi-Source Parallel Resolution** — Concurrently queries all sources and prioritizes direct APK links.
- **Headless Cloudflare Solver** — Background Turnstile solver and session warmer for zero-prompt downloads.
- **Strict Package Verification** — Enforces exact package IDs to eliminate unhosted fallbacks and broken links.
- **Universal Format Support** — Full support for `APK`, `APKM`, `APKS`, and `XAPK` with root manifest parsing.
- **Morphe Manager Integration** — Seamless automated package resolution and installation handoff via intents.
- **Fluid Search & Navigation** — Instant offline catalog search, architecture filters, and search-first back navigation.

---

### Supported Sources

- **Aurora Store** — Direct Google Play CDN downloads with anonymous check-in, device-tailored architectures, and split-APK delivery.
- **APKMirror** — Direct variants, release dates, and automated session keeper.
- **APKPure** — Version update API with device-matched architectures.
- **APKCombo** — Direct API and variant streams.
- **Uptodown** — CDN downloads with automated background Turnstile bypass.

---

### License

Licensed under the [GPL-3.0 License](LICENSE).
