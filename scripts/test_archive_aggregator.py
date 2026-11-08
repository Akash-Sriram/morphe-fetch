#!/usr/bin/env python3
"""
Unit tests for the Morphe Multi-Source Catalog Aggregator.
"""

import unittest
from unittest import mock
from archive_aggregator import (
    canonical_repo_key,
    clean_patch_name,
    normalize_host,
    normalize_repo_name,
    parse_timestamp_ms,
    BaseCatalogSource,
    CatalogAggregator,
    RushArchiveSource
)

class TestCatalogAggregator(unittest.TestCase):

    def test_normalize_repo_name(self):
        self.assertEqual(normalize_repo_name("https://github.com/owner/repo.git"), "owner/repo")
        self.assertEqual(normalize_repo_name("http://gitlab.com/owner/sub/repo/"), "owner/sub/repo")
        self.assertEqual(normalize_repo_name("  Owner/Repo.GIT  "), "Owner/Repo")
        self.assertEqual(normalize_repo_name("morpheapp/morphe-patches"), "morpheapp/morphe-patches")

    def test_canonical_repo_key(self):
        self.assertEqual(canonical_repo_key("github", "https://github.com/Owner/Repo.git"), "github.com:owner/repo")
        self.assertEqual(canonical_repo_key("gitlab.com", "GitLab-User/Repo"), "gitlab.com:gitlab-user/repo")

    def test_parse_timestamp_ms(self):
        # Epoch ms
        self.assertEqual(parse_timestamp_ms(1793943516813), 1793943516813)
        # Epoch seconds (< 100 billion)
        self.assertEqual(parse_timestamp_ms(1700000000), 1700000000000)
        # ISO string
        self.assertEqual(parse_timestamp_ms("2026-11-06T12:00:00Z"), 1793966400000)
        # Date string
        self.assertEqual(parse_timestamp_ms("2026-11-06"), 1793923200000)
        # Null / empty
        self.assertEqual(parse_timestamp_ms(None), 0)
        self.assertEqual(parse_timestamp_ms(""), 0)

    def test_clean_patch_name(self):
        self.assertEqual(clean_patch_name("Hide Ads (Experimental)"), "Hide Ads")
        self.assertEqual(clean_patch_name("Custom Theme - Dev"), "Custom Theme")
        self.assertEqual(clean_patch_name("Normal Patch"), "Normal Patch")

    def test_freshness_arbitration_newer_wins(self):
        class MockSourceA(BaseCatalogSource):
            def __init__(self):
                super().__init__("source_a", priority=10)
            def fetch_data(self):
                return {}
            def parse_repos(self, _):
                return [{
                    "sourceName": self.name,
                    "host": "github.com",
                    "repo": "test/repo",
                    "name": "Test Repo",
                    "updatedAt": 1000,
                    "avatarUrl": "https://example.com/avatar_a.png",
                    "stars": 5,
                    "patches": [{"name": "Patch A", "description": "Desc A"}]
                }]

        class MockSourceB(BaseCatalogSource):
            def __init__(self):
                super().__init__("source_b", priority=20)
            def fetch_data(self):
                return {}
            def parse_repos(self, _):
                return [{
                    "sourceName": self.name,
                    "host": "github.com",
                    "repo": "test/repo",
                    "name": "Test Repo",
                    "updatedAt": 2000, # NEWER
                    "bundleIconUrl": "https://example.com/bundle_icon.png",
                    "stars": 15,
                    "patches": [{"name": "Patch B (New)", "description": "Desc B"}]
                }]

        aggregator = CatalogAggregator([MockSourceA(), MockSourceB()], verify_releases=False)
        result = aggregator.run()
        repos = result["repos"]
        self.assertEqual(len(repos), 1)
        repo = repos[0]
        # Source B had updatedAt 2000 > 1000, so Source B's patch list wins
        self.assertEqual(repo["sourceName"], "source_b")
        self.assertEqual(repo["updatedAt"], 2000)
        self.assertEqual(repo["patches"][0]["name"], "Patch B (New)")
        # Blended metadata from Source A (avatarUrl was missing in B)
        self.assertEqual(repo["avatarUrl"], "https://example.com/avatar_a.png")
        self.assertEqual(repo["bundleIconUrl"], "https://example.com/bundle_icon.png")
        self.assertEqual(repo["stars"], 15)
        self.assertEqual(aggregator.stats["freshness_wins"]["source_b"], 1)

    def test_fault_tolerance_skips_failing_source(self):
        class FailingSource(BaseCatalogSource):
            def __init__(self):
                super().__init__("broken_source")
            def fetch_data(self):
                raise ConnectionError("Network down")
            def parse_repos(self, _):
                return []

        class WorkingSource(BaseCatalogSource):
            def __init__(self):
                super().__init__("healthy_source")
            def fetch_data(self):
                return {}
            def parse_repos(self, _):
                return [{
                    "sourceName": self.name,
                    "host": "github.com",
                    "repo": "healthy/repo",
                    "name": "Healthy",
                    "updatedAt": 5000,
                    "patches": []
                }]

        aggregator = CatalogAggregator([FailingSource(), WorkingSource()], verify_releases=False)
        result = aggregator.run()
        self.assertEqual(len(result["repos"]), 1)
        self.assertEqual(result["repos"][0]["repo"], "healthy/repo")
        self.assertEqual(aggregator.stats["sources_loaded"], 1)

    @mock.patch("archive_aggregator.check_github_repo_release", return_value=("NO_RELEASE", None))
    def test_release_pruning_removes_dead_repos(self, _mock_check):
        class MockDeadSource(BaseCatalogSource):
            def __init__(self):
                super().__init__("dead_source")
            def fetch_data(self):
                return {}
            def parse_repos(self, _):
                return [{
                    "sourceName": self.name,
                    "host": "github.com",
                    "repo": "nonexistent-user-12345/nonexistent-repo-99999",
                    "name": "Dead",
                    "updatedAt": 1000,
                    "patches": []
                }]

        aggregator = CatalogAggregator([MockDeadSource()], verify_releases=True)
        result = aggregator.run()
        self.assertEqual(len(result["repos"]), 0)
        self.assertEqual(aggregator.stats["pruned_no_release_repos"], 1)

    def test_rush_patch_extraction_from_apps(self):
        raw_data = {
            "repos": [
                {
                    "repo": "rushuser/my-patches",
                    "host": "github.com",
                    "updatedAt": 1000,
                    "patches": [], # Rush repos list has empty patches
                    "apps": [{"packageName": "com.test.app", "name": "Test App", "patchCount": 1}]
                }
            ],
            "apps": [
                {
                    "packageName": "com.test.app",
                    "sources": [
                        {
                            "repo": "rushuser/my-patches",
                            "patches": [
                                {"name": "Custom Theme", "description": "Enables AMOLED theme"}
                            ]
                        }
                    ]
                }
            ]
        }
        source = RushArchiveSource()
        repos = source.parse_repos(raw_data)
        self.assertEqual(len(repos), 1)
        r = repos[0]
        self.assertEqual(len(r["patches"]), 1)
        self.assertEqual(r["patches"][0]["name"], "Custom Theme")
        self.assertEqual(r["patches"][0]["description"], "Enables AMOLED theme")
        self.assertEqual(r["patches"][0]["compatiblePackages"], [{"name": "com.test.app", "versions": []}])

    def test_zero_patch_source_excluded_from_app(self):
        class MockSource(BaseCatalogSource):
            def __init__(self):
                super().__init__("mock_src")
            def fetch_data(self):
                return {}
            def parse_repos(self, _):
                return [
                    {
                        "sourceName": self.name,
                        "host": "github.com",
                        "repo": "working/repo",
                        "name": "Working",
                        "updatedAt": 2000,
                        "apps": [{"packageName": "com.target.app", "name": "Target App"}],
                        "patches": [
                            {"name": "Good Patch", "compatiblePackages": [{"name": "com.target.app"}]}
                        ]
                    },
                    {
                        "sourceName": self.name,
                        "host": "github.com",
                        "repo": "empty/repo",
                        "name": "Empty",
                        "updatedAt": 1000,
                        "apps": [{"packageName": "com.target.app", "name": "Target App"}],
                        "patches": [] # 0 patches for target app
                    }
                ]

        aggregator = CatalogAggregator([MockSource()], verify_releases=False)
        result = aggregator.run()
        apps = result["apps"]
        self.assertEqual(len(apps), 1)
        target_app = apps[0]
        self.assertEqual(target_app["packageName"], "com.target.app")
        # Ensure only working/repo is linked to com.target.app, empty/repo is excluded!
        attached_repos = [s["repo"] for s in target_app["sources"]]
        self.assertIn("working/repo", attached_repos)
        self.assertNotIn("empty/repo", attached_repos)

    def test_independent_repos_not_collapsed_by_matching_patch_names(self):
        class MockSource(BaseCatalogSource):
            def __init__(self):
                super().__init__("mock_src")
            def fetch_data(self):
                return {}
            def parse_repos(self, _):
                return [
                    {
                        "sourceName": self.name,
                        "host": "github.com",
                        "repo": "repo/one",
                        "name": "One",
                        "updatedAt": 2000,
                        "apps": [{"packageName": "com.app.x"}],
                        "patches": [{"name": "P1", "compatiblePackages": [{"name": "com.app.x"}]}]
                    },
                    {
                        "sourceName": self.name,
                        "host": "github.com",
                        "repo": "repo/two",
                        "name": "Two",
                        "updatedAt": 1000,
                        "apps": [{"packageName": "com.app.x"}],
                        "patches": [{"name": "P1", "compatiblePackages": [{"name": "com.app.x"}]}]
                    }
                ]

        aggregator = CatalogAggregator([MockSource()], verify_releases=False)
        result = aggregator.run()
        app_x = result["apps"][0]
        # Both independent repositories must be displayed as their own standalone sources
        self.assertEqual(len(app_x["sources"]), 2)
        source_repos = [s["repo"] for s in app_x["sources"]]
        self.assertIn("repo/one", source_repos)
        self.assertIn("repo/two", source_repos)


if __name__ == "__main__":
    unittest.main()
