#!/usr/bin/env python3
"""
Morphe Archive Multi-Layer Processor & Indexer
----------------------------------------------
Groups fork and mirror repositories under their canonical main repo.
Filters out 100% duplicate / subset repositories that have NO unique patches.
"""

import json
import os
import re
import sys
import urllib.request
from collections import Counter
from datetime import datetime, timezone

UPSTREAM_DATA_URL = "https://raw.githubusercontent.com/rushiforai/morphe-archive/refs/heads/main/docs/data.json"

EXPERIMENTAL_SUFFIX_RE = re.compile(
    r"\s*(?:-\s*[^(\n]+\s*)?\((?:Experimental|Dev|Test|Beta)\)|\s*-\s*(?:Experimental|Dev|Beta)\b",
    re.IGNORECASE
)

def clean_patch_name(raw_name: str) -> str:
    cleaned = EXPERIMENTAL_SUFFIX_RE.sub("", raw_name).strip()
    return cleaned if cleaned else raw_name.strip()

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

def process_archive(data: dict) -> dict:
    raw_apps = data.get("apps", [])
    raw_repos = data.get("repos", [])
    generated_at = data.get("generatedAt") or datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")

    processed_apps = []
    repo_patch_counts = Counter()
    repo_exclusive_counts = Counter()

    for app in raw_apps:
        pkg = app.get("packageName", "")
        name = app.get("name", "")
        sources = app.get("sources", [])
        if not sources:
            continue

        # Map each source to its cleaned patch set
        source_clean_patches = {}
        for s in sources:
            repo = s.get("repo", "")
            cleaned = set(clean_patch_name(p.get("name", "")).lower() for p in s.get("patches", []))
            source_clean_patches[repo] = cleaned

        # Identify which patches are unique across ALL sources
        all_patch_occurrences = Counter()
        for p_set in source_clean_patches.values():
            for p in p_set:
                all_patch_occurrences[p] += 1

        # 1. Cluster 100% exact duplicate repos
        clusters = []
        handled_repos = set()

        # Sort sources so known primary repos are evaluated first
        sorted_sources = sorted(sources, key=lambda s: score_primary_repo(s.get("repo", "")), reverse=True)

        for s in sorted_sources:
            repo = s.get("repo", "")
            if repo in handled_repos:
                continue

            p_set = source_clean_patches[repo]
            
            # Find identical repos that provide the exact same set of patches
            identical_mirrors = []
            for s2 in sources:
                r2 = s2.get("repo", "")
                if r2 == repo or r2 in handled_repos:
                    continue
                if p_set == source_clean_patches[r2]:
                    identical_mirrors.append(r2)
                    handled_repos.add(r2)

            handled_repos.add(repo)

            # Count exclusive patches
            exclusive_patches = [
                p.get("name", "")
                for p in s.get("patches", [])
                if all_patch_occurrences[clean_patch_name(p.get("name", "")).lower()] == (1 + len(identical_mirrors))
            ]

            # Check if this repo is a strict subset of a larger repo with 0 exclusive patches
            is_subset_duplicate = False
            superset_parent = None
            if len(exclusive_patches) == 0:
                for other_s in sources:
                    other_r = other_s.get("repo", "")
                    if other_r == repo or other_r in identical_mirrors:
                        continue
                    other_set = source_clean_patches[other_r]
                    if p_set.issubset(other_set) and len(p_set) < len(other_set):
                        is_subset_duplicate = True
                        superset_parent = other_r
                        break

            # If it's a 100% subset copy with no unique patches of its own (like vomw), omit it from top-level
            if is_subset_duplicate:
                continue

            s_copy = dict(s)
            s_copy["exclusivePatchCount"] = len(exclusive_patches)
            s_copy["exclusivePatches"] = exclusive_patches
            s_copy["mirrors"] = identical_mirrors
            clusters.append(s_copy)

            if len(exclusive_patches) > 0:
                repo_exclusive_counts[repo] += len(exclusive_patches)
            repo_patch_counts[repo] += len(s.get("patches", []))

        # Sort filtered sources: bundles with exclusive patches first, then patch count
        clusters.sort(key=lambda s: (-s.get("exclusivePatchCount", 0), -len(s.get("patches", []))))

        processed_apps.append({
            "packageName": pkg,
            "name": name,
            "patchCount": len(app.get("patches", [])),
            "sources": clusters,
            "sourceCount": len(clusters),
            "versions": app.get("versions", []),
            "iconUrl": app.get("iconUrl"),
            "iconColor": app.get("iconColor"),
        })

    # Sort apps by source count + patch count
    processed_apps.sort(key=lambda a: (-a["sourceCount"], -a["patchCount"], a["name"].lower()))

    # Process Repos tab, ranking repos with verified exclusive patches higher
    processed_repos = []
    for r in raw_repos:
        repo_name = r.get("repo", "")
        r_dict = dict(r)
        r_dict["exclusivePatchCount"] = repo_exclusive_counts[repo_name]
        processed_repos.append(r_dict)

    processed_repos.sort(key=lambda r: (-r.get("exclusivePatchCount", 0), -r.get("patchCount", 0)))

    return {
        "generatedAt": generated_at,
        "processedAt": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC"),
        "appCount": len(processed_apps),
        "repoCount": len(processed_repos),
        "apps": processed_apps,
        "repos": processed_repos,
        "universalSources": data.get("universalSources", [])
    }

def main():
    input_file = sys.argv[1] if len(sys.argv) > 1 and sys.argv[1] else None
    output_file = sys.argv[2] if len(sys.argv) > 2 else "processed_archive.json"

    if input_file and os.path.exists(input_file):
        print(f"Loading raw archive data from {input_file}...")
        with open(input_file, "r", encoding="utf-8") as f:
            raw_data = json.load(f)
    else:
        print(f"Downloading upstream archive data from {UPSTREAM_DATA_URL}...")
        req = urllib.request.Request(UPSTREAM_DATA_URL, headers={"User-Agent": "Morphe-Fetch-Processor/1.0"})
        with urllib.request.urlopen(req) as resp:
            raw_data = json.loads(resp.read().decode("utf-8"))

    print(f"Processing {len(raw_data.get('apps', []))} apps...")
    processed = process_archive(raw_data)

    print(f"Saving indexed result to {output_file}...")
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(processed, f, ensure_ascii=False, indent=2)

    min_file = output_file.replace(".json", ".min.json")
    print(f"Saving minified mobile result to {min_file}...")
    with open(min_file, "w", encoding="utf-8") as f:
        json.dump(processed, f, ensure_ascii=False, separators=(',', ':'))

    print(f"✓ Completed. Output saved to {output_file} and {min_file}.")

if __name__ == "__main__":
    main()
