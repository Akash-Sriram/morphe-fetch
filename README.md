# Morphe Fetch

[![Latest Release](https://img.shields.io/github/v/release/Akash-Sriram/morphe-fetch?color=007AFF&style=flat-square)](https://github.com/Akash-Sriram/morphe-fetch/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/github/license/Akash-Sriram/morphe-fetch?style=flat-square)](LICENSE)

A companion downloader and patch repository browser for [Morphe Manager](https://github.com/MorpheApp/morphe-manager).

Forked from [`helper-for-morphe`](https://github.com/rushiranpise/helper-for-morphe) by [Rushi Ranpise](https://github.com/rushiranpise), maintained by [Akash Sriram](https://github.com/Akash-Sriram).

---

## Features

- **Download Sources**: Fetches base APKs from Google Play (via Aurora / GSF API), APKMirror, APKPure, and Uptodown.
- **Find New Apps**: Searchable catalog of 1,600+ patchable apps aggregated from both Rush Archive and Morphe-Patches.
- **Per-Source Compatibility**: Shows exact supported app versions for each repository directly above the "Add to Morphe" button.
- **Smart Architecture Selection**: Automatically prioritizes native `arm64-v8a` variants over Universal or 32-bit builds.
- **Fast Downloads**: Multi-connection parallel chunk downloading for faster transfers.
- **Cloudflare / Captcha Solver**: Automated headless challenge clearance for Uptodown and APKMirror with session caching.
- **Seamless Handoff**: Hands downloaded packages straight to Morphe Manager via FileProvider with safety grace periods.

---

## Acknowledgements

- **[`helper-for-morphe`](https://github.com/rushiranpise/helper-for-morphe)** by [Rushi Ranpise](https://github.com/rushiranpise): Original Compose rewrite, source parsers, and Morphe download helper architecture.
- **[`apkupdater`](https://github.com/rumboalla/apkupdater)** by [rumboalla](https://github.com/rumboalla):
  - APKMirror 1-hop REST API (`app_exists`) request models and architecture.
  - Wear OS (`wear_standalone`) and Android TV (`leanback_standalone`) capability filtering.
  - Standalone single-APK resolution using `forcebaseapk=true`.
  - APKPure 1-hop direct CDN resolution (`https://d.apkpure.com/b/...`).
- **[`morphe-download-helper`](https://github.com/GeneralZyonix/morphe-download-helper)** by [GeneralZyonix](https://github.com/GeneralZyonix): Foundational helper protocol and early integration concepts.

---

## License

GPL-3.0 License. See [LICENSE](LICENSE) for details.


