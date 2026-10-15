"""Reviewable Go pins, publication cooldown, checksum-db records and offline mirror.

Only pinned module/version URLs are fetched. Native builds use this local file
proxy with GOTOOLCHAIN=local; neither Go nor cgo can resolve new network packages.
"""
import datetime as dt
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
import urllib.parse

ROOT = Path(__file__).resolve().parent.parent


def helpers():
    spec = importlib.util.spec_from_file_location("ci_security_helpers", ROOT / "scripts/ci-security.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def load():
    data = json.loads((ROOT / "security/go.lock.json").read_text(encoding="utf-8"))
    if data["cooldown_days"] < 7 or not data["modules"]:
        raise ValueError("Go dependencies require a seven-day cooldown")
    seen = set()
    for item in data["modules"]:
        path, version = item["module"], item["version"]
        if not re.fullmatch(r"[a-z0-9.-]+(?:/[a-z0-9_.-]+)+|go\.starlark\.net", path) or ".." in path:
            raise ValueError("Unexpected Go module path")
        if not re.fullmatch(r"v\d+\.\d+\.\d+(?:-[a-zA-Z0-9.-]+)?", version):
            raise ValueError("Unexpected Go module version")
        if path in seen:
            raise ValueError("Duplicate Go module pin")
        seen.add(path)
        for key in ("sha256", "go_mod_sha256"):
            if not re.fullmatch(r"[0-9a-f]{64}", item[key]):
                raise ValueError("Go artifacts require SHA-256 pins")
        for key in ("sum", "go_mod_sum"):
            if not re.fullmatch(r"h1:[A-Za-z0-9+/]{43}=", item[key]):
                raise ValueError("Go artifacts require checksum-db records")
    expected = "".join(f"{i['module']} {i['version']} {i['sum']}\n{i['module']} {i['version']}/go.mod {i['go_mod_sum']}\n" for i in data["modules"])
    if (ROOT / "sandbox/go.sum").read_text(encoding="utf-8") != expected:
        raise ValueError("go.sum does not match the reviewed module lock")
    manifest = (ROOT / "sandbox/go.mod").read_text(encoding="utf-8")
    if re.search(r"^\s*(replace|exclude|toolchain)\b", manifest, re.M):
        raise ValueError("Unreviewed Go replacement or toolchain directive")
    for path, version in re.findall(r"^\s*(?:require\s+)?([a-z][a-z0-9./_-]+)\s+(v[^\s)]+)", manifest, re.M):
        if not any(i["module"] == path and i["version"] == version for i in data["modules"]):
            raise ValueError("Unpinned dependency in go.mod")
    return data


def run(command):
    data, security = load(), helpers()
    for item in data["modules"]:
        security.aged(item["published_at"], data["cooldown_days"])
        path, version = item["module"], item["version"]
        base = "https://proxy.golang.org/" + path + "/@v/" + version
        if command == "audit":
            query = urllib.parse.urlencode({"ecosystem": "go", "affects": path + "@" + version, "per_page": 100})
            advisories = [a for a in security.api("advisories?" + query) if not a.get("withdrawn_at")]
            if advisories:
                raise ValueError("Vulnerable Go dependency: " + path + " " + ", ".join(a["ghsa_id"] for a in advisories))
            continue
        with security.request(base + ".info") as response:
            info = json.load(response)
        if info["Version"] != version or security.timestamp(info["Time"]) != security.timestamp(item["published_at"]):
            raise ValueError("Go publication metadata changed")
        if item.get("commit") and info.get("Origin", {}).get("Hash") != item["commit"]:
            raise ValueError("Go module commit changed")
        with security.request("https://sum.golang.org/lookup/" + path + "@" + version) as response:
            record = response.read().decode("utf-8")
        for suffix, key in (("", "sum"), ("/go.mod", "go_mod_sum")):
            if f"{path} {version}{suffix} {item[key]}" not in record.splitlines():
                raise ValueError("Go checksum database record changed")
        if command != "download":
            continue
        folder = ROOT / "build/go-proxy" / path / "@v"
        folder.mkdir(parents=True, exist_ok=True)
        for suffix, key in ((".mod", "go_mod_sha256"), (".zip", "sha256")):
            destination = folder / (version + suffix)
            if destination.exists() and security.digest(destination) == item[key]:
                continue
            with security.request(base + suffix) as response:
                content = response.read(32 * 1024 * 1024 + 1)
            if len(content) > 32 * 1024 * 1024 or hashlib.sha256(content).hexdigest() != item[key]:
                raise ValueError("Go artifact checksum mismatch")
            destination.write_bytes(content)
        (folder / (version + ".info")).write_text(json.dumps(info), encoding="utf-8")
        (folder / "list").write_text(version + "\n", encoding="ascii")
    if command == "audit":
        sdk = next(i for i in security.load()["downloads"] if i.get("kind") == "go_sdk")
        for package in ("stdlib", "toolchain"):
            query = urllib.parse.urlencode({"ecosystem": "go", "affects": package + "@" + sdk["version"][2:], "per_page": 100})
            if any(not a.get("withdrawn_at") for a in security.api("advisories?" + query)):
                raise ValueError("Vulnerable Go " + package)
    print("PASS: Go " + command + " (pinned modules, cooldown and checksum database)")


if __name__ == "__main__":
    try:
        if len(sys.argv) != 2 or sys.argv[1] not in ("verify", "download", "audit"):
            raise ValueError("Expected verify, download or audit")
        run(sys.argv[1])
    except Exception as error:
        print("GO SECURITY CHECK FAILED:", error, file=sys.stderr)
        sys.exit(1)
