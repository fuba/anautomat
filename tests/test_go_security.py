"""Fail-closed Go module mirror and toolchain pin regression tests."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).parents[1]
spec = importlib.util.spec_from_file_location("go_security", ROOT / "scripts/go-security.py")
go_security = importlib.util.module_from_spec(spec)
spec.loader.exec_module(go_security)
security = go_security.helpers()


class GoSecurityTests(unittest.TestCase):
    def test_reviewed_manifest_and_sums_are_consistent(self):
        self.assertGreater(len(go_security.load()["modules"]), 0)

    def fixture(self, folder, mutation=None):
        data = json.loads((ROOT / "security/go.lock.json").read_text())
        if mutation:
            mutation(data)
        Path(folder, "security").mkdir()
        Path(folder, "sandbox").mkdir()
        Path(folder, "security/go.lock.json").write_text(json.dumps(data))
        for name in ("go.mod", "go.sum"):
            Path(folder, "sandbox", name).write_text((ROOT / "sandbox" / name).read_text())

    def test_path_traversal_and_unpinned_changes_are_rejected(self):
        for mutation in (
            lambda d: d.update(cooldown_days=6),
            lambda d: d["modules"][0].update(module="github.com/../escape"),
            lambda d: d["modules"][0].update(sha256="missing"),
            lambda d: d["modules"][0].update(sum="unverified"),
            lambda d: d["modules"].append(d["modules"][0].copy()),
        ):
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as folder:
                self.fixture(folder, mutation)
                with patch.object(go_security, "ROOT", Path(folder)), self.assertRaises(ValueError):
                    go_security.load()

    def test_modified_go_sum_and_module_replacements_are_rejected(self):
        for name, addition in (("go.sum", "unexpected\n"), ("go.mod", "\nreplace go.starlark.net => ../outside\n"), ("go.mod", "\nrequire unreviewed.example/module v1.0.0\n")):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as folder:
                self.fixture(folder)
                path = Path(folder, "sandbox", name)
                path.write_text(path.read_text() + addition)
                with patch.object(go_security, "ROOT", Path(folder)), self.assertRaises(ValueError):
                    go_security.load()

    def test_cooldown_is_checked_before_any_module_request(self):
        data = {"cooldown_days": 7, "modules": [{"published_at": "2099-01-01T00:00:00Z"}]}
        with patch.object(go_security, "load", return_value=data), patch.object(go_security, "helpers", return_value=security), patch.object(security, "request") as request:
            with self.assertRaisesRegex(ValueError, "cooldown"):
                go_security.run("download")
            request.assert_not_called()

    def test_changed_checksum_db_record_prevents_download(self):
        item = go_security.load()["modules"][0]
        info = {"Version": item["version"], "Time": item["published_at"]}
        with patch.object(go_security, "load", return_value={"cooldown_days": 7, "modules": [item]}), patch.object(go_security, "helpers", return_value=security), patch.object(security, "request", side_effect=[io.BytesIO(json.dumps(info).encode()), io.BytesIO(b"unreviewed checksum")]) as request:
            with self.assertRaisesRegex(ValueError, "checksum database"):
                go_security.run("download")
            self.assertEqual(request.call_count, 2)

    def test_sdk_url_and_upstream_hash_are_checked(self):
        item = next(i for i in security.load()["downloads"] if i.get("kind") == "go_sdk").copy()
        item["published_at"] = "2020-01-01T00:00:00Z"
        bad = item.copy()
        bad["url"] = "https://example.com/tool.zip"
        with self.assertRaisesRegex(ValueError, "Unexpected Go SDK"):
            security.check_download(bad, 7)
        release = [{"version": item["version"], "stable": True, "files": [{"filename": item["asset"], "sha256": "0" * 64}]}]
        with patch.object(security, "request", return_value=io.BytesIO(json.dumps(release).encode())):
            with self.assertRaisesRegex(ValueError, "upstream checksum"):
                security.check_download(item, 7)


if __name__ == "__main__":
    unittest.main()
