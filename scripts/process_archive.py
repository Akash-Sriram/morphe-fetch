#!/usr/bin/env python3
"""
Morphe Archive Multi-Layer Processor & Aggregator
-------------------------------------------------
Combines multiple catalog sources (Rush Archive, Morphe-Patches, etc.)
with freshness arbitration and metadata enrichment.
"""

import json
import os
import sys
from archive_aggregator import CatalogAggregator, RushArchiveSource, MorphePatchesSource

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    # Handle workflow call signature: python3 process_archive.py "" processed_archive.json
    output_file = None
    for arg in reversed(sys.argv[1:]):
        if arg.strip():
            output_file = arg
            break
    repo_root = os.path.abspath(os.path.join(script_dir, ".."))
    if not output_file:
        output_file = os.path.join(repo_root, "data", "processed_archive.json")
    os.makedirs(os.path.dirname(output_file), exist_ok=True)
    min_file = output_file.replace(".json", ".min.json")
    bundled_asset = os.path.join(repo_root, "app", "src", "main", "assets", "morphe_archive_bundled.json")

    aggregator = CatalogAggregator()
    aggregator.add_source(RushArchiveSource())
    aggregator.add_source(MorphePatchesSource())

    print("Running Multi-Source Catalog Aggregator...")
    processed = aggregator.run()
    stats = aggregator.stats

    print("\n" + "=" * 65)
    print("         MORPHE MULTI-SOURCE AGGREGATOR METRICS")
    print("=" * 65)
    print(f"Sources Processed      : {stats['sources_loaded']}")
    print(f"Total Raw Repositories : {stats['total_raw_repos']}")
    print(f"Overlapping Repos      : {stats['overlap_repos']}")
    print(f"Freshness Wins         : {dict(stats['freshness_wins'])}")
    print(f"Pruned (No Releases)   : {stats.get('pruned_no_release_repos', 0)}")
    print(f"Unique Verified Repos  : {stats['unique_repos']}")
    print(f"Unique Apps Synthesized: {stats['total_apps']}")
    print(f"Total Unique Patches   : {stats['total_patches']}")
    print("=" * 65)

    print(f"Saving formatted result to {output_file}...")
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(processed, f, ensure_ascii=False, indent=2)

    print(f"Saving minified mobile result to {min_file}...")
    with open(min_file, "w", encoding="utf-8") as f:
        json.dump(processed, f, ensure_ascii=False, separators=(',', ':'))

    if os.path.exists(os.path.dirname(bundled_asset)):
        print(f"Syncing bundled Android asset at {bundled_asset}...")
        with open(bundled_asset, "w", encoding="utf-8") as f:
            json.dump(processed, f, ensure_ascii=False, separators=(',', ':'))

    print("✓ Completed multi-source catalog aggregation successfully.")

if __name__ == "__main__":
    main()
