#!/usr/bin/env python3
"""
Morphe Multi-Source Catalog Aggregator & Freshness Arbiter
-----------------------------------------------------------
Extensible intermediate system that:
1. Ingests catalog data from N upstream sources (e.g., Rush Archive, Morphe-Patches, etc.).
2. Normalizes repository keys, app packages, and timestamps.
3. Performs Freshness Arbitration: when a repo exists in multiple feeds, keeps the latest
   snapshot based on normalized updatedAt timestamps.
4. Blends metadata: enriches with Play Store CDN icons, categories, stars, and hot rankings.
5. Deduplicates apps and patches, calculates exclusive patches, and clusters identical mirrors.
"""

import abc
import argparse
import json
import logging
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Set, Tuple

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("archive_aggregator")

EXPERIMENTAL_SUFFIX_RE = re.compile(
    r"\s*(?:-\s*[^(\n]+\s*)?\((?:Experimental|Dev|Test|Beta)\)|\s*-\s*(?:Experimental|Dev|Beta)\b",
    re.IGNORECASE
)

def clean_patch_name(raw_name: str) -> str:
    cleaned = EXPERIMENTAL_SUFFIX_RE.sub("", raw_name).strip()
    return cleaned if cleaned else raw_name.strip()

def normalize_host(raw_host: Optional[str], raw_repo: str) -> str:
    if raw_host:
        h = raw_host.lower().strip()
        if "gitlab" in h:
            return "gitlab.com"
        return "github.com"
    if "gitlab" in raw_repo.lower():
        return "gitlab.com"
    return "github.com"

def normalize_repo_name(raw_repo: str) -> str:
    """Strips schemas, hostnames, trailing .git, and normalizes to owner/repo."""
    r = raw_repo.strip()
    r = re.sub(r"^https?://(?:www\.)?(?:github\.com|gitlab\.com)/", "", r, flags=re.IGNORECASE)
    r = re.sub(r"\.git$", "", r, flags=re.IGNORECASE)
    return r.strip().strip("/")

def canonical_repo_key(host: str, repo: str) -> str:
    return f"{normalize_host(host, repo)}:{normalize_repo_name(repo).lower()}"

def parse_timestamp_ms(val: Any) -> int:
    """Converts diverse timestamp formats (int ms, int s, ISO 8601 string, null) to epoch ms."""
    if val is None:
        return 0
    if isinstance(val, (int, float)):
        v = int(val)
        # If timestamp is in seconds (< 100 billion, i.e. < year 5138), convert to ms
        if v < 100_000_000_000:
            return v * 1000
        return v
    if isinstance(val, str):
        val = val.strip()
        if not val:
            return 0
        if val.isdigit():
            return parse_timestamp_ms(int(val))
        # Try ISO 8601 formats
        for fmt in (
            "%Y-%m-%dT%H:%M:%S.%fZ",
            "%Y-%m-%dT%H:%M:%SZ",
            "%Y-%m-%dT%H:%M:%S%z",
            "%Y-%m-%d %H:%M UTC",
            "%Y-%m-%d %H:%M:%S",
            "%Y-%m-%d"
        ):
            try:
                dt = datetime.strptime(val, fmt)
                if dt.tzinfo is None:
                    dt = dt.replace(tzinfo=timezone.utc)
                return int(dt.timestamp() * 1000)
            except ValueError:
                continue
    return 0

def score_primary_repo(repo: str) -> int:
    """Prioritizes official / primary repositories when choosing the canonical parent."""
    r = repo.lower()
    if "morpheapp" in r:
        return 1000
    if "revanced" in r:
        return 900
    if "inotia00" in r:
        return 850
    if "anddea" in r:
        return 800
    if "akash-sriram" in r:
        return 750
    return -len(repo)

def get_github_token() -> Optional[str]:
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token:
        return token
    try:
        res = subprocess.run(["gh", "auth", "token"], capture_output=True, text=True, timeout=3)
        if res.returncode == 0 and res.stdout.strip():
            return res.stdout.strip()
    except Exception:
        pass
    return None

def check_github_repo_release(repo_name: str, token: Optional[str] = None) -> Tuple[str, Optional[Dict[str, Any]]]:
    """
    Checks if a repository has a valid release published on GitHub.
    Uses fast curl redirect inspection on /releases/latest first (no rate limit,
    proxy-compatible), followed by API fallback.
    Returns:
      ("VALID", latestChangesDict) -> Repo has a published release
      ("NO_RELEASE", None)         -> Repo exists (or 404), but has 0 releases
      ("UNKNOWN", None)            -> Network error, retain repo defensively
    """
    url = f"https://github.com/{repo_name}/releases/latest"
    for attempt in range(3):
        try:
            res = subprocess.run(
                ["curl", "-s", "-L", "-o", "/dev/null", "-w", "%{http_code} %{url_effective}", url],
                capture_output=True, text=True, timeout=6
            )
            parts = res.stdout.strip().split(" ", 1)
            if len(parts) == 2:
                code, eff_url = parts
                if code == "404":
                    return ("NO_RELEASE", None)
                if code == "429":
                    time.sleep(2.0 * (attempt + 1))
                    continue
                if "/releases/tag/" in eff_url:
                    tag = eff_url.split("/releases/tag/")[-1].strip()
                    return ("VALID", {"title": tag, "date": "", "items": []})
                if eff_url.rstrip("/").endswith("/releases"):
                    return ("NO_RELEASE", None)
                if code.startswith("2") or code.startswith("3"):
                    return ("VALID", {"title": "v1.0.0", "date": "", "items": []})
        except Exception:
            pass

    if token:
        api_url = f"https://api.github.com/repos/{repo_name}/releases/latest"
        api_req = urllib.request.Request(api_url, headers={
            "User-Agent": "MorpheArchiveAggregator/1.0",
            "Authorization": f"Bearer {token}"
        })
        try:
            with urllib.request.urlopen(api_req, timeout=5) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                tag = data.get("tag_name") or data.get("name") or "v1.0.0"
                date = (data.get("published_at") or data.get("created_at") or "")[:10]
                body = data.get("body", "")
                first_line = body.strip().split("\n")[0] if body else ""
                return ("VALID", {
                    "title": tag,
                    "date": date,
                    "items": [{"category": "Release", "text": first_line}] if first_line else []
                })
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return ("NO_RELEASE", None)
            return ("UNKNOWN", None)
        except Exception:
            return ("UNKNOWN", None)

    return ("UNKNOWN", None)

# -------------------------------------------------------------------------
# Base Source Interface
# -------------------------------------------------------------------------

class BaseCatalogSource(abc.ABC):
    """Abstract interface for pluggable catalog sources."""

    def __init__(self, name: str, priority: int = 0):
        self.name = name
        self.priority = priority

    @abc.abstractmethod
    def fetch_data(self) -> Dict[str, Any]:
        """Fetches raw data from remote or local storage."""
        pass

    @abc.abstractmethod
    def parse_repos(self, raw_data: Dict[str, Any]) -> List[Dict[str, Any]]:
        """Parses raw data into a normalized list of repository objects."""
        pass

    def parse_store_metadata(self, raw_data: Dict[str, Any]) -> Dict[str, Dict[str, Any]]:
        """Optional: returns app store metadata (icons, categories, descriptions) keyed by packageName."""
        return {}

    def parse_featured_apps(self, raw_data: Dict[str, Any]) -> Set[str]:
        """Optional: returns set of featured package names."""
        return set()

# -------------------------------------------------------------------------
# Source 1: Rush Archive Source (rushiforai/morphe-archive)
def fetch_json_url(url: str, timeout: int = 90) -> Dict[str, Any]:
    """Fetches and parses JSON from a URL using curl with urllib fallback."""
    try:
        res = subprocess.run(["curl", "-s", "-L", "--max-time", str(timeout), url], capture_output=True, timeout=timeout + 10)
        if res.returncode == 0 and res.stdout:
            return json.loads(res.stdout.decode("utf-8"))
        else:
            logger.warning(f"curl exited with code {res.returncode}")
    except Exception as e:
        logger.warning(f"curl failed: {e}")
    req = urllib.request.Request(url, headers={"User-Agent": "MorpheArchiveAggregator/1.0"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))

# -------------------------------------------------------------------------
# Source 1: Rush Archive Source (rushiforai/morphe-archive)
# -------------------------------------------------------------------------

class RushArchiveSource(BaseCatalogSource):
    DEFAULT_URL = "https://raw.githubusercontent.com/rushiforai/morphe-archive/refs/heads/main/docs/data.json"

    def __init__(self, url: str = DEFAULT_URL, priority: int = 10):
        super().__init__("rush_archive", priority)
        self.url = url

    def fetch_data(self) -> Dict[str, Any]:
        logger.info(f"[{self.name}] Fetching upstream data from {self.url}...")
        return fetch_json_url(self.url, timeout=30)

    def parse_repos(self, raw_data: Dict[str, Any]) -> List[Dict[str, Any]]:
        # Index patches per repo across raw_data["apps"]
        repo_patches_map: Dict[str, Dict[str, Dict[str, Any]]] = defaultdict(dict)
        for app in raw_data.get("apps", []):
            pkg = app.get("packageName", "").strip()
            for s in app.get("sources", []):
                r_name = normalize_repo_name(s.get("repo", ""))
                if not r_name:
                    continue
                for p in s.get("patches", []):
                    p_name = p.get("name", "").strip()
                    if not p_name:
                        continue
                    clean = clean_patch_name(p_name).lower()
                    if clean not in repo_patches_map[r_name]:
                        repo_patches_map[r_name][clean] = {
                            "name": p_name,
                            "description": p.get("description"),
                            "compatiblePackages": [{"name": pkg, "versions": []}] if pkg else []
                        }
                    elif pkg:
                        comp_pkgs = repo_patches_map[r_name][clean]["compatiblePackages"]
                        existing_names = {cp.get("name") if isinstance(cp, dict) else cp for cp in comp_pkgs}
                        if pkg not in existing_names:
                            comp_pkgs.append({"name": pkg, "versions": []})

        raw_repos = raw_data.get("repos", [])
        normalized_repos = []
        for r in raw_repos:
            repo_name = normalize_repo_name(r.get("repo", ""))
            if not repo_name:
                continue
            host = normalize_host(r.get("host"), repo_name)
            updated_at = parse_timestamp_ms(r.get("updatedAt") or r.get("updatedDate"))

            apps_list = []
            for app in r.get("apps", []):
                pkg = app.get("packageName", "").strip()
                if pkg:
                    apps_list.append({
                        "packageName": pkg,
                        "name": app.get("name", pkg),
                        "patchCount": app.get("patchCount", 0),
                        "iconUrl": app.get("iconUrl"),
                        "iconColor": app.get("iconColor")
                    })

            # Prefer extracted patches from apps map, with fallback to r.get("patches")
            extracted = list(repo_patches_map.get(repo_name, {}).values())
            if not extracted:
                for p in r.get("patches", []):
                    p_name = p.get("name", "").strip()
                    if p_name:
                        extracted.append({
                            "name": p_name,
                            "description": p.get("description"),
                            "compatiblePackages": p.get("compatiblePackages", [])
                        })
            patches_list = extracted

            # Ensure apps_list includes any target apps discovered in extracted patches
            existing_pkg_names = {a["packageName"] for a in apps_list}
            for p in patches_list:
                for cp in p.get("compatiblePackages", []):
                    pkg_name = cp.get("name") if isinstance(cp, dict) else cp
                    if pkg_name and pkg_name not in existing_pkg_names:
                        apps_list.append({
                            "packageName": pkg_name,
                            "name": pkg_name,
                            "patchCount": 1
                        })
                        existing_pkg_names.add(pkg_name)

            normalized_repos.append({
                "sourceName": self.name,
                "host": host,
                "repo": repo_name,
                "name": r.get("name") or repo_name.split("/")[-1],
                "updatedAt": updated_at,
                "avatarUrl": r.get("avatarUrl"),
                "webUrl": r.get("webUrl") or f"https://{host}/{repo_name}",
                "addUrl": r.get("addUrl") or f"https://morphe.software/add-source?github={repo_name}",
                "source": r.get("source"),
                "changelogUrl": r.get("changelogUrl"),
                "latestChanges": r.get("latestChanges"),
                "patchCount": max(r.get("patchCount", 0), len(patches_list)),
                "appCount": max(r.get("appCount", 0), len(apps_list)),
                "apps": apps_list,
                "patches": patches_list,
                "stars": 0,
                "hotRank": 9999
            })
        return normalized_repos

# -------------------------------------------------------------------------
# Source 2: Morphe-Patches Source (morphe-patches.software)
# -------------------------------------------------------------------------

class MorphePatchesSource(BaseCatalogSource):
    DEFAULT_CATALOG_URL = "https://morphe-patches.software/data/catalog.json"

    def __init__(self, catalog_url: str = DEFAULT_CATALOG_URL, priority: int = 20):
        super().__init__("morphe_patches", priority)
        self.catalog_url = catalog_url

    def fetch_data(self) -> Dict[str, Any]:
        logger.info(f"[{self.name}] Fetching catalog from {self.catalog_url}...")
        return fetch_json_url(self.catalog_url, timeout=30)

    def parse_repos(self, raw_data: Dict[str, Any]) -> List[Dict[str, Any]]:
        raw_bundles = raw_data.get("bundles", [])
        compat_table = raw_data.get("compatibilities", [])
        normalized_repos = []

        for b in raw_bundles:
            repo_name = normalize_repo_name(b.get("repo", ""))
            if not repo_name:
                continue
            host = normalize_host(b.get("source"), repo_name)
            updated_at = parse_timestamp_ms(b.get("updatedAt") or b.get("firstSeen"))

            patches_list = []
            apps_targeted = set(b.get("targetApps", []))
            for p in b.get("patches", []):
                p_name = p.get("name", "").strip()
                if not p_name:
                    continue
                comp_key = p.get("compatiblePackagesKey")
                comp_pkgs = []
                if comp_key is not None and isinstance(comp_key, int) and comp_key < len(compat_table):
                    raw_comp = compat_table[comp_key]
                    if isinstance(raw_comp, list):
                        for c in raw_comp:
                            if isinstance(c, dict):
                                pkg = c.get("packageName")
                                if pkg:
                                    targets = [t.get("version") for t in c.get("targets", []) if isinstance(t, dict) and t.get("version")]
                                    comp_pkgs.append({"name": pkg, "versions": targets})
                                    apps_targeted.add(pkg)
                    elif isinstance(raw_comp, dict):
                        for pkg, targets in raw_comp.items():
                            comp_pkgs.append({"name": pkg, "versions": targets if isinstance(targets, list) else []})
                            apps_targeted.add(pkg)

                patches_list.append({
                    "name": p_name,
                    "description": p.get("description"),
                    "compatiblePackages": comp_pkgs
                })

            apps_list = [{"packageName": pkg, "name": pkg, "patchCount": 0} for pkg in sorted(apps_targeted)]

            normalized_repos.append({
                "sourceName": self.name,
                "host": host,
                "repo": repo_name,
                "name": b.get("name") or repo_name.split("/")[-1],
                "updatedAt": updated_at,
                "avatarUrl": b.get("avatarUrl"),
                "bundleIconUrl": b.get("bundleIconUrl"),
                "webUrl": f"https://{host}/{repo_name}",
                "addUrl": b.get("deepLink") or f"https://morphe.software/add-source?github={repo_name}",
                "source": None,
                "changelogUrl": b.get("changelogUrl"),
                "latestChanges": None,
                "patchCount": b.get("patchCount", len(patches_list)),
                "appCount": len(apps_list),
                "apps": apps_list,
                "patches": patches_list,
                "stars": b.get("stars", 0),
                "hotRank": b.get("hotRank", 9999)
            })
        return normalized_repos

    def parse_store_metadata(self, raw_data: Dict[str, Any]) -> Dict[str, Dict[str, Any]]:
        return raw_data.get("store", {})

    def parse_featured_apps(self, raw_data: Dict[str, Any]) -> Set[str]:
        return set(raw_data.get("featured", []))

# -------------------------------------------------------------------------
# Core Aggregator & Freshness Arbiter Engine
# -------------------------------------------------------------------------

class CatalogAggregator:
    def __init__(self, sources: Optional[List[BaseCatalogSource]] = None, verify_releases: bool = True):
        self.sources = sources or []
        self.verify_releases = verify_releases
        self.stats = {
            "sources_loaded": 0,
            "total_raw_repos": 0,
            "unique_repos": 0,
            "overlap_repos": 0,
            "freshness_wins": Counter(),
            "pruned_no_release_repos": 0,
            "total_apps": 0,
            "total_patches": 0
        }

    def add_source(self, source: BaseCatalogSource):
        self.sources.append(source)

    def run(self) -> Dict[str, Any]:
        all_repos_by_source: Dict[str, List[Dict[str, Any]]] = {}
        store_metadata: Dict[str, Dict[str, Any]] = {}
        featured_apps: Set[str] = set()

        for src in self.sources:
            try:
                raw_data = src.fetch_data()
                repos = src.parse_repos(raw_data)
                all_repos_by_source[src.name] = repos
                self.stats["sources_loaded"] += 1
                self.stats["total_raw_repos"] += len(repos)
                logger.info(f"[{src.name}] Loaded {len(repos)} repositories.")

                # Blend store metadata & featured apps if provided
                src_store = src.parse_store_metadata(raw_data)
                if src_store:
                    store_metadata.update(src_store)
                    logger.info(f"[{src.name}] Ingested store metadata for {len(src_store)} apps.")

                src_featured = src.parse_featured_apps(raw_data)
                if src_featured:
                    featured_apps.update(src_featured)
                    logger.info(f"[{src.name}] Ingested {len(src_featured)} featured apps.")

            except Exception as e:
                logger.error(f"[{src.name}] Failed to fetch/parse: {e}. Skipping source.", exc_info=True)

        # -----------------------------------------------------------------
        # 1. Freshness Arbitration across Repositories
        # -----------------------------------------------------------------
        resolved_repos: Dict[str, Dict[str, Any]] = {}
        repo_occurrences: Dict[str, List[Dict[str, Any]]] = defaultdict(list)

        for src_name, repos in all_repos_by_source.items():
            for r in repos:
                key = canonical_repo_key(r["host"], r["repo"])
                repo_occurrences[key].append(r)

        for key, candidates in repo_occurrences.items():
            if len(candidates) == 1:
                resolved_repos[key] = candidates[0]
                continue

            self.stats["overlap_repos"] += 1
            # Sort candidates by updatedAt descending, then patchCount descending
            candidates.sort(key=lambda c: (c["updatedAt"], len(c["patches"])), reverse=True)
            winner = candidates[0]
            self.stats["freshness_wins"][winner["sourceName"]] += 1

            # Blend non-conflicting metadata from other candidates into winner
            for donor in candidates[1:]:
                if not winner.get("avatarUrl") and donor.get("avatarUrl"):
                    winner["avatarUrl"] = donor["avatarUrl"]
                if not winner.get("bundleIconUrl") and donor.get("bundleIconUrl"):
                    winner["bundleIconUrl"] = donor["bundleIconUrl"]
                if not winner.get("source") and donor.get("source"):
                    winner["source"] = donor["source"]
                if not winner.get("latestChanges") and donor.get("latestChanges"):
                    winner["latestChanges"] = donor["latestChanges"]
                if donor.get("stars", 0) > winner.get("stars", 0):
                    winner["stars"] = donor["stars"]
                if donor.get("hotRank", 9999) < winner.get("hotRank", 9999):
                    winner["hotRank"] = donor["hotRank"]

                # If winner has patches without descriptions, fill in from donor if match exists
                donor_patch_map = {clean_patch_name(p["name"]).lower(): p for p in donor.get("patches", [])}
                for wp in winner.get("patches", []):
                    if not wp.get("description"):
                        dp = donor_patch_map.get(clean_patch_name(wp["name"]).lower())
                        if dp and dp.get("description"):
                            wp["description"] = dp["description"]

                # Merge any missing target apps from donor
                existing_apps = {a["packageName"] for a in winner.get("apps", [])}
                for da in donor.get("apps", []):
                    if da["packageName"] not in existing_apps:
                        winner["apps"].append(da)
                        existing_apps.add(da["packageName"])

            resolved_repos[key] = winner

        self.stats["unique_repos_raw"] = len(resolved_repos)
        logger.info(f"Arbitrated {self.stats['overlap_repos']} overlapping repos. Raw canonical repos: {len(resolved_repos)}.")

        # -----------------------------------------------------------------
        # 1b. Validate Releases & Prune 404 / 0-Release Repositories
        # Morphe Manager requires a published release to download bundles.
        # Repositories with no releases or 404 are strictly filtered out.
        # -----------------------------------------------------------------
        if self.verify_releases:
            valid_repos = {}
            pruned_no_release = 0
            github_token = get_github_token()
            repos_to_check = []

            for key, repo in resolved_repos.items():
                host = repo.get("host", "github.com")
                repo_name = repo.get("repo", "")
                if host == "github.com" and repo_name:
                    if repo.get("bundleIconUrl") or repo.get("stars", 0) > 0:
                        valid_repos[key] = repo
                    else:
                        repos_to_check.append((key, repo_name, repo))
                else:
                    if repo.get("latestChanges"):
                        valid_repos[key] = repo
                    else:
                        pruned_no_release += 1

            def _check_worker(item):
                k, r_name, r_obj = item
                st, rel = check_github_repo_release(r_name, github_token)
                return k, r_name, r_obj, st, rel

            with ThreadPoolExecutor(max_workers=3) as executor:
                for k, r_name, r_obj, st, rel in executor.map(_check_worker, repos_to_check):
                    if st == "VALID":
                        if rel and not r_obj.get("latestChanges"):
                            r_obj["latestChanges"] = rel
                        elif rel and r_obj.get("latestChanges") and not r_obj["latestChanges"].get("title"):
                            r_obj["latestChanges"]["title"] = rel.get("title")
                        valid_repos[k] = r_obj
                    elif st == "NO_RELEASE":
                        logger.warning(f"Pruning repo with no releases or 404: {r_name}")
                        pruned_no_release += 1
                    else:
                        # UNKNOWN (network timeout / offline) -> retain defensively
                        valid_repos[k] = r_obj

            resolved_repos = valid_repos
            self.stats["pruned_no_release_repos"] = pruned_no_release
            self.stats["unique_repos"] = len(resolved_repos)
            logger.info(f"Pruned {pruned_no_release} repos lacking releases. Active working repos: {len(resolved_repos)}.")
        else:
            self.stats["unique_repos"] = len(resolved_repos)

        # -----------------------------------------------------------------
        # 2. Synthesize Apps & Link Repositories
        # -----------------------------------------------------------------
        apps_map: Dict[str, Dict[str, Any]] = defaultdict(lambda: {
            "packageName": "",
            "name": "",
            "category": None,
            "description": None,
            "installs": None,
            "iconUrl": None,
            "isFeatured": False,
            "sources": [],
            "patches": set(),
            "patchDetails": {},
            "versions": set()
        })

        universal_sources = []

        for repo in resolved_repos.values():
            repo_apps = repo.get("apps", [])
            repo_patches = repo.get("patches", [])
            is_universal = len(repo_apps) == 0

            if is_universal:
                universal_sources.append({
                    "repo": repo["repo"],
                    "host": repo["host"],
                    "name": repo["name"],
                    "avatarUrl": repo.get("avatarUrl"),
                    "bundleIconUrl": repo.get("bundleIconUrl"),
                    "stars": repo.get("stars", 0),
                    "hotRank": repo.get("hotRank", 9999),
                    "webUrl": repo.get("webUrl"),
                    "addUrl": repo.get("addUrl"),
                    "source": repo.get("source"),
                    "changelogUrl": repo.get("changelogUrl"),
                    "latestChanges": repo.get("latestChanges"),
                    "patchCount": len(repo_patches),
                    "patches": [{"name": p["name"], "description": p.get("description")} for p in repo_patches]
                })
                continue

            for app_stub in repo_apps:
                pkg = app_stub["packageName"]

                # Find patches in this repo compatible with this app
                app_specific_patches = []
                for p in repo_patches:
                    comp = p.get("compatiblePackages", [])
                    if not comp:
                        if len(repo_apps) <= 1:
                            app_specific_patches.append(p)
                    else:
                        for c in comp:
                            c_pkg = c.get("name") if isinstance(c, dict) else c
                            if c_pkg == pkg:
                                app_specific_patches.append(p)
                                break

                # Strict guarantee: NEVER attach a repo to an app if it provides 0 patches for that app
                if not app_specific_patches:
                    continue

                app_src_desc = {
                    "repo": repo["repo"],
                    "host": repo["host"],
                    "name": repo["name"],
                    "avatarUrl": repo.get("avatarUrl"),
                    "bundleIconUrl": repo.get("bundleIconUrl"),
                    "stars": repo.get("stars", 0),
                    "hotRank": repo.get("hotRank", 9999),
                    "webUrl": repo.get("webUrl"),
                    "addUrl": repo.get("addUrl"),
                    "source": repo.get("source"),
                    "changelogUrl": repo.get("changelogUrl"),
                    "latestChanges": repo.get("latestChanges"),
                    "patchCount": len(app_specific_patches),
                    "patches": [{"name": p["name"], "description": p.get("description")} for p in app_specific_patches]
                }

                app_entry = apps_map[pkg]
                app_entry["packageName"] = pkg
                if not app_entry["name"] or app_entry["name"] == pkg:
                    app_entry["name"] = app_stub.get("name") or pkg

                # Enrich from Play Store metadata if present
                if pkg in store_metadata:
                    meta = store_metadata[pkg]
                    if meta.get("name"):
                        app_entry["name"] = meta["name"]
                    if meta.get("category"):
                        app_entry["category"] = meta["category"]
                    if meta.get("iconUrl"):
                        app_entry["iconUrl"] = meta["iconUrl"]
                    if meta.get("description"):
                        app_entry["description"] = meta["description"]
                    if meta.get("installs"):
                        app_entry["installs"] = meta["installs"]

                if pkg in featured_apps:
                    app_entry["isFeatured"] = True

                # Attach this repo to the app's source list
                app_entry["sources"].append(app_src_desc)

                # Record patches compatible with this app
                for p in app_specific_patches:
                    clean_name = p["name"]
                    app_entry["patches"].add(clean_name)
                    if clean_name not in app_entry["patchDetails"] and p.get("description"):
                        app_entry["patchDetails"][clean_name] = p["description"]
                    for comp in p.get("compatiblePackages", []):
                        if isinstance(comp, dict) and comp.get("name") == pkg:
                            for v in comp.get("versions", []):
                                if v:
                                    app_entry["versions"].add(v)

        # -----------------------------------------------------------------
        # 3. Clustering & Exclusive Patch Computation
        # -----------------------------------------------------------------
        final_apps = []
        for pkg, app_data in apps_map.items():
            sources = app_data["sources"]
            if not sources:
                continue

            # Sort sources by primary score and stars
            sources.sort(key=lambda s: (score_primary_repo(s["repo"]), s.get("stars", 0)), reverse=True)

            # Calculate patch occurrences across sources for this app
            all_occurrences = Counter()
            for s in sources:
                for p in s.get("patches", []):
                    all_occurrences[clean_patch_name(p["name"]).lower()] += 1

            app_sources = []
            for s in sources:
                exclusive = [p["name"] for p in s.get("patches", []) if all_occurrences[clean_patch_name(p["name"]).lower()] == 1]
                s_copy = dict(s)
                s_copy["exclusivePatchCount"] = len(exclusive)
                s_copy["exclusivePatches"] = exclusive
                s_copy["mirrors"] = []
                app_sources.append(s_copy)


            patch_details_list = [
                {"name": name, "description": desc}
                for name, desc in sorted(app_data["patchDetails"].items(), key=lambda x: x[0])
            ]

            final_apps.append({
                "packageName": pkg,
                "name": app_data["name"],
                "category": app_data["category"],
                "iconUrl": app_data["iconUrl"],
                "description": app_data["description"],
                "installs": app_data["installs"],
                "isFeatured": app_data["isFeatured"],
                "patchCount": len(app_data["patches"]),
                "patches": sorted(app_data["patches"]),
                "patchDetails": patch_details_list,
                "versions": sorted(app_data["versions"]),
                "sources": app_sources
            })

        # Sort apps: featured first, then by patchCount descending, then name
        final_apps.sort(key=lambda a: (1 if a.get("isFeatured") else 0, a["patchCount"], a["name"]), reverse=True)

        self.stats["total_apps"] = len(final_apps)
        self.stats["total_patches"] = sum(a["patchCount"] for a in final_apps)

        output = {
            "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC"),
            "appCount": len(final_apps),
            "repoCount": len(resolved_repos),
            "patchCount": self.stats["total_patches"],
            "universalSources": universal_sources,
            "apps": final_apps,
            "repos": list(resolved_repos.values())
        }
        return output

# -------------------------------------------------------------------------
# CLI Runner & Demonstration
# -------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="Morphe Multi-Source Catalog Aggregator")
    parser.add_argument("--dry-run", action="store_true", help="Run aggregation and print metrics without saving")
    parser.add_argument("--output", default="processed_archive.min.json", help="Output JSON path")
    args = parser.parse_args()

    aggregator = CatalogAggregator()
    aggregator.add_source(RushArchiveSource())
    aggregator.add_source(MorphePatchesSource())

    logger.info("Executing Multi-Source Catalog Aggregator...")
    result = aggregator.run()
    stats = aggregator.stats

    print("\n" + "=" * 65)
    print("         MORPHE MULTI-SOURCE AGGREGATOR METRICS")
    print("=" * 65)
    print(f"Sources Processed      : {stats['sources_loaded']}")
    print(f"Total Raw Repositories : {stats['total_raw_repos']}")
    print(f"Overlapping Repos      : {stats['overlap_repos']}")
    print(f"Freshness Wins         : {dict(stats['freshness_wins'])}")
    print(f"Unique Canonical Repos : {stats['unique_repos']}")
    print(f"Unique Apps Synthesized: {stats['total_apps']}")
    print(f"Total Unique Patches   : {stats['total_patches']}")
    print("=" * 65)

    # Sample App Inspect
    sample_apps = [a for a in result["apps"] if a["packageName"] in ("com.google.android.youtube", "com.facebook.katana", "com.twitter.android")]
    print("\nSample Synthesized App Inspection:")
    for a in sample_apps:
        print(f" -> {a['name']} ({a['packageName']})")
        print(f"    Category: {a.get('category')} | Featured: {a.get('isFeatured')}")
        print(f"    Icon URL: {a.get('iconUrl')[:60] if a.get('iconUrl') else 'None'}...")
        print(f"    Patches : {a['patchCount']} across {len(a['sources'])} sources")

    if not args.dry_run:
        out_path = args.output
        logger.info(f"Saving minified output to {out_path}...")
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(result, f, separators=(",", ":"))
        logger.info(f"Saved {os.path.getsize(out_path) / (1024 * 1024):.2f} MB to {out_path}")

if __name__ == "__main__":
    main()
