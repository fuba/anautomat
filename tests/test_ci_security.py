"""Regression tests for fail-closed dependency and release gates."""
import datetime as dt
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("security", Path(__file__).parents[1] / "scripts/ci-security.py")
security = importlib.util.module_from_spec(spec)
spec.loader.exec_module(security)


class SecurityGateTests(unittest.TestCase):
    def test_cooldown_boundary(self):
        now = dt.datetime(2026, 10, 15, tzinfo=dt.timezone.utc)
        with patch.object(security, "NOW", lambda: now):
            security.aged("2026-10-08T00:00:00Z", 7)
            for timestamp in ("2026-10-08T00:00:01Z", "2026-10-16T00:00:00Z"):
                with self.assertRaises(ValueError):
                    security.aged(timestamp, 7)

    def test_moved_action_tag_is_rejected(self):
        release = {"draft": False, "prerelease": False, "published_at": "2020-01-01T00:00:00Z"}
        with patch.object(security, "api", side_effect=[release, {"object": {"type": "commit", "sha": "b" * 40}}]):
            with self.assertRaisesRegex(ValueError, "tag moved"):
                security.check_action({"repository": "actions/example", "version": "v1", "sha": "a" * 40}, 7)

    def test_high_severity_codeql_finding_blocks_release(self):
        with tempfile.TemporaryDirectory() as folder:
            Path(folder, "java.sarif").write_text('{"runs":[{"tool":{"driver":{"rules":[{"id":"test","properties":{"security-severity":"7.0"}}]}},"results":[{"ruleId":"test","level":"warning"}]}]}')
            with self.assertRaisesRegex(ValueError, "High-severity"):
                security.sarif(folder)

    def test_recently_modified_release_asset_is_rejected(self):
        item = {"name": "tool", "repository": "org/tool", "version": "v1", "asset": "tool.zip", "url": "https://github.com/org/tool/releases/download/v1/tool.zip", "sha256": "a" * 64, "published_at": "2020-01-01T00:00:00Z"}
        release = {"draft": False, "prerelease": False, "published_at": item["published_at"], "assets": [{"name": "tool.zip", "created_at": item["published_at"], "updated_at": "2099-01-01T00:00:00Z", "digest": "sha256:" + "a" * 64, "browser_download_url": item["url"]}]}
        with patch.object(security, "api", return_value=release):
            with self.assertRaisesRegex(ValueError, "cooldown"):
                security.check_download(item, 7)


if __name__ == "__main__":
    unittest.main()
