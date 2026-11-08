# Morphe Fetch 📦

[![Latest Release](https://img.shields.io/github/v/release/Akash-Sriram/morphe-fetch?include_prereleases&color=007AFF&style=flat-square)](https://github.com/Akash-Sriram/morphe-fetch/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/github/license/Akash-Sriram/morphe-fetch?style=flat-square)](LICENSE)

Fast, resilient, native Android companion downloader and patch catalog explorer for [Morphe Manager](https://github.com/MorpheApp/morphe-manager).

A hard fork of [`helper-for-morphe`](https://github.com/rushiranpise/helper-for-morphe) by [Rushi Ranpise](https://github.com/rushiranpise), heavily expanded, modernized, and maintained by [Akash Sriram](https://github.com/Akash-Sriram).

---

## 🔥 Key Highlights in v1.6.0

- 🌐 **Multi-Source Catalog Aggregator**: Automatically aggregates and freshness-arbitrates repositories from both **Rush Archive** and **Morphe Patches / Awesome Morphe**. Enriched with Google Play Store metadata (official high-res icons, categories, descriptions, install counts, and featured app badges).
- 🧩 **1,690+ Apps & 8,600+ Patches**: Live catalog indexed directly on `main` (`data/processed_archive.min.json`) with automated 6-hour GitHub Actions freshness updates.
- 🛡️ **Zero-Release & Dead Repo Pruning**: Actively checks GitHub releases (`/releases/latest`) and automatically prunes deleted (404) or release-lacking repositories so users never encounter broken patch bundles.
- 🗂️ **Independent Standalone Source Cards**: Every distinct repository providing patches for an app is presented as its own full, interactive source card (with its star count, patch count, author avatar, and 1-tap "Add to Morphe" bundle integration).
- 🚀 **Multi-Connection Segmented Downloads**: Accelerates large file downloads ($\ge 8\text{ MB}$) using concurrent parallel TCP range chunks with automatic single-stream fallback.
- 🎯 **Native ARM64 Architecture Priority**: Rigorous ABI ranking engine that strictly prioritizes native `arm64-v8a` builds over Universal or 32-bit variants.
- 🍪 **Centralized Cookie & Headless Turnstile Engine**: Turnstile solver with instant UI responsiveness, 30-minute direct CDN caching (`dw.uptodown.com`), automated cookie consent banner rejection, and HTTP/1.1 CDN protocol enforcement to eliminate download stalls.
- 🔍 **Archive Format Auto-Detector**: Inspects internal zip magic headers (`toc.pb` $\to$ `.apks`, `meta.sai_v2.json` $\to$ `.apkm`, `manifest.json` $\to$ `.xapk`, root `AndroidManifest.xml` $\to$ `.apk`) to fix mislabeled file extensions before handoff.
- 🤝 **Rock-Solid Morphe Manager Handoff**: Extended 10-minute cache grace period and direct `ACTION_SEND` FileProvider intent delivery with explicit read URI permissions.

---

## 📱 Supported Download Sources

| Source | Type | Highlights & Capabilities |
| :--- | :--- | :--- |
| **Google Play (via Aurora)** | Direct Store CDN | Anonymous check-in via native MicroG-RE or fallback dispensers, Android 15 Pixel baseline profile, split-APK delivery. |
| **APKMirror** | Verified Mirror | Native ARM64 ABI pool isolation, background session warmer, AutoPilot link navigation guarded against ads and widgets. |
| **APKPure** | Versioned Archive | Direct CDN retrieval with device-matched ABIs and verified hash checksums. |
| **Uptodown** | Global Directory | Headless Cloudflare clearance, cookie consent dismissal, HTTP/1.1 stall prevention, 30-minute direct URL caching. |

---

## 🔍 Find New Apps (Archive Browser)

The **Find New Apps** tab turns Morphe Fetch into a discovery hub for patchable applications:

- **Unified Multi-Source Catalog**: Synthesizes patch manifests from both Rush Archive and Morphe-Patches (`morphe-patches.software`), resolving hundreds of overlapping repositories by picking the newest update timestamp.
- **Smart Filtering & Search**:
  - Filter by **All**, **Featured**, **Installed**, **Patchable**, or **Patched**.
  - Search across App Names, Package Names, Categories (*Video Players*, *Social*, *Music*, etc.), and Patch Names.
- **Direct Repository Management**:
  - One-tap "Add to Morphe" button deep-links repository sources directly into Morphe Manager's source manager.
  - View individual patch descriptions, compatible target versions, and changelogs.
- **Offline & Bundled Fallback**: Includes a pre-bundled snapshot (`morphe_archive_bundled.json`) inside the APK, ensuring full offline functionality even without network access.

---

## ⚡ Download Engine & Resilience

1. **Segmented Parallel TCP Streams**: Probes servers with byte-range requests and downloads up to 4 concurrent chunks simultaneously into a preallocated file.
2. **Stream & Signature Verification**: Validates download sizes against HTTP headers, verifies ZIP/DEX structures, and inspects developer signing certificates.
3. **Crash Protection**: Background headless WebViews run with software rendering (`LAYER_TYPE_SOFTWARE`) and explicit `onRenderProcessGone` lifecycle handling, completely preventing Android 15 GPU driver crashes.
4. **Handoff Safety**: Temporary downloads are retained for 10 minutes in the app cache to prevent race conditions while Morphe Manager reads or patches the file.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 100%
- **UI Toolkit**: Jetpack Compose (Material 3) with dynamic theming and edge-to-edge support
- **HTTP Engine**: OkHttp 4.12 with custom connection pools, protocol enforcement, and centralized cookies
- **Asynchronous**: Kotlin Coroutines & Flow
- **HTML Parsing**: Jsoup 1.18
- **Target Platform**: Android 8.0+ (API level 26 to 35 / Android 15 & 16)
- **CI / CD**: Automated GitHub Actions pipeline for scheduled catalog indexing and signed tag releases

---

## 📜 License

Morphe Fetch is free and open-source software licensed under the [GNU General Public License v3.0](LICENSE).

