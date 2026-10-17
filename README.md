# Morphe Fetch 📦

[![Latest Release](https://img.shields.io/github/v/release/Akash-Sriram/morphe-fetch?include_prereleases&color=007AFF&style=flat-square)](https://github.com/Akash-Sriram/morphe-fetch/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/github/license/Akash-Sriram/morphe-fetch?style=flat-square)](LICENSE)

Fast, native Android companion downloader for [Morphe Manager](https://github.com/MorpheApp/morphe-manager) and standalone APK retriever.

A hard fork of [`helper-for-morphe`](https://github.com/rushiranpise/helper-for-morphe) by [Rushi Ranpise](https://github.com/rushiranpise), maintained and evolved by [Akash Sriram](https://github.com/Akash-Sriram).

---

## Key Features

- **Multi-Source Parallel Resolution** — Concurrently queries all enabled sources (Google Play via Aurora, APKMirror, APKPure, and Uptodown) to fetch recommended architectures and compatible versions with zero manual link hunting.
- **Find New Apps (Morphe Archive Explorer)** — Built-in catalog of 100+ patchable applications and community repositories. Features live GitHub catalog synchronization, offline fallback, favourites bookmarking, and smart installed/patched app status detection.
- **Download Stream & Archive Integrity** — Validates network download streams against server content lengths, verifies ZIP/DEX file integrity before handoff, and extracts signing certificates to fail closed against corrupt downloads or malicious files.
- **Headless Cloudflare Solver** — Background Turnstile solver, ad-blocking engine, and session keeper for seamless downloads on protected mirrors.
- **Aurora Store & MicroG-RE Integration** — One-tap anonymous Google Play token acquisition via installed MicroG-RE or verified fallback dispensers for direct Play Store CDN delivery.
- **Universal Format Support** — Fully parses, verifies, and packages standalone `APK` files as well as split bundles (`APKM`, `APKS`, and `XAPK`).
- **Morphe Manager Integration** — Seamless automated handoff via Android intents (`ACTION_DOWNLOAD_ORIGINAL_APK`), automatically routing clean stock binaries directly to Morphe Manager's patching pipeline.
- **Modern Jetpack Compose UI** — Material 3 dynamic color, edge-to-edge system bar support, pull-to-refresh, tablet two-pane layout, and responsive search navigation.

---

## Supported Sources

| Source | Description | Features |
|---|---|---|
| **Aurora Store** | Direct Google Play CDN | Anonymous check-in via MicroG-RE, architecture filtering, split-APK delivery |
| **APKMirror** | Verified community mirror | Architecture variants, release dates, automated session warmer |
| **APKPure** | Versioned archive API | Fast direct CDN downloads with device-matched ABIs |
| **Uptodown** | Global software directory | Background Turnstile bypass and direct variant downloads |

---

## License

Licensed under the [GNU General Public License v3.0](LICENSE).
