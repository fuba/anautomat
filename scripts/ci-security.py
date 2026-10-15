"""Fail-closed release age, integrity, advisory and repository policy checks.

Uses only Python's standard library. No bootstrap package installation is needed.
"""
import argparse
import datetime as dt
import email.utils
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
LOCK = ROOT / "security/dependencies.lock.json"
NOW = lambda: dt.datetime.now(dt.timezone.utc)


def request(url, method="GET"):
    headers = {"User-Agent": "anautomat-ci", "Accept": "application/vnd.github+json"}
    if url.startswith("https://api.github.com/"):
        token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
        if token:
            headers["Authorization"] = "Bearer " + token
    for attempt in range(4):
        try:
            return urllib.request.urlopen(urllib.request.Request(url, headers=headers, method=method), timeout=120)
        except urllib.error.HTTPError as error:
            detail = error.read(8192).decode("utf-8", errors="replace").lower()
            limited = error.code == 429 or (error.code == 403 and ("rate limit" in detail or error.headers.get("X-RateLimit-Remaining") == "0"))
            if attempt == 3 or not (limited or error.code in (502, 503, 504)):
                raise ValueError(f"Dependency metadata request failed (HTTP {error.code}): {url}") from error
            retry_after = error.headers.get("Retry-After", "")
            delay = min(60, max(5, int(retry_after))) if retry_after.isdigit() else 5 * (2 ** attempt)
            print(f"Temporary metadata service failure; retrying after {delay}s", flush=True)
            time.sleep(delay)


def api(path):
    with request("https://api.github.com/" + path) as response:
        return json.load(response)


def timestamp(value):
    try:
        result = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        result = email.utils.parsedate_to_datetime(value)
    if result.tzinfo is None:
        raise ValueError("Publication timestamp has no timezone")
    return result


def aged(value, days):
    if NOW() - timestamp(value) < dt.timedelta(days=days):
        raise ValueError(f"Dependency is inside the {days}-day cooldown: {value}")


def load():
    data = json.loads(LOCK.read_text(encoding="utf-8-sig"))
    if data["cooldown_days"] < 7:
        raise ValueError("The dependency cooldown must be at least seven days")
    return data


def digest(path):
    checksum = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(1024 * 1024):
            checksum.update(chunk)
    return checksum.hexdigest()


def check_action(item, days):
    repo, version, sha = item["repository"], item["version"], item["sha"]
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Actions must be pinned to full commit SHAs")
    release = api(f"repos/{repo}/releases/tags/{version}")
    if release["draft"] or release["prerelease"]:
        raise ValueError("Only stable published action releases are allowed")
    aged(release["published_at"], days)
    ref = api(f"repos/{repo}/git/ref/tags/{version}")["object"]
    while ref["type"] == "tag":
        ref = api(f"repos/{repo}/git/tags/{ref['sha']}")["object"]
    if ref["type"] != "commit" or ref["sha"] != sha:
        raise ValueError(f"Action tag moved or does not match its lock: {repo}")
    aged(api(f"repos/{repo}/commits/{sha}")["commit"]["committer"]["date"], days)


def check_download(item, days):
    aged(item["published_at"], days)
    if not re.fullmatch(r"[0-9a-f]{64}", item["sha256"]):
        raise ValueError("Downloads require a SHA-256 checksum")
    if "repository" in item:
        release = api(f"repos/{item['repository']}/releases/tags/{item['version']}")
        if release["draft"] or release["prerelease"]:
            raise ValueError("Only stable tool releases are allowed")
        aged(release["published_at"], days)
        asset = next(a for a in release["assets"] if a["name"] == item["asset"])
        aged(asset["created_at"], days)
        aged(asset["updated_at"], days)
        if asset["browser_download_url"] != item["url"] or asset.get("digest") != "sha256:" + item["sha256"]:
            raise ValueError(f"Upstream asset changed: {item['name']}")
    elif item.get("kind") == "go_sdk":
        expected = "https://go.dev/dl/" + item["version"] + ".windows-amd64.zip"
        if item["url"] != expected or item["asset"] != expected.rsplit("/", 1)[1]:
            raise ValueError("Unexpected Go SDK URL")
        with request("https://go.dev/dl/?mode=json&include=all") as response:
            releases = json.load(response)
        release = next(r for r in releases if r["version"] == item["version"])
        asset = next(f for f in release["files"] if f["filename"] == item["asset"])
        if not release["stable"] or asset["sha256"] != item["sha256"]:
            raise ValueError("Go SDK upstream checksum changed")
        with request(item["url"], "HEAD") as response:
            modified = response.headers.get("Last-Modified")
            if not modified or timestamp(modified) != timestamp(item["published_at"]):
                raise ValueError("Go SDK publication metadata changed")
            aged(modified, days)
    else:
        if not item["url"].startswith(("https://repo.maven.apache.org/maven2/", "https://dl.google.com/dl/android/maven2/")):
            raise ValueError("Unexpected Maven download host")
        with request(item["url"], "HEAD") as response:
            modified = response.headers.get("Last-Modified")
            if not modified or timestamp(modified) != timestamp(item["published_at"]):
                raise ValueError("Maven publication metadata changed or is missing")
            aged(modified, days)


def verify(data):
    go_security("verify")
    for item in data["actions"]:
        check_action(item, data["cooldown_days"])
    # Check the bundled composite action's nested action before allowing execution.
    wrapper = next(i for i in data["actions"] if i["repository"] == "actions/attest-build-provenance")
    nested = next(i for i in data["actions"] if i["repository"] == "actions/attest")
    with request(f"https://raw.githubusercontent.com/{wrapper['repository']}/{wrapper['sha']}/action.yml") as response:
        uses = re.findall(r"uses:\s*([^\s#]+)", response.read().decode())
    if uses != [nested["repository"] + "@" + nested["sha"]]:
        raise ValueError("Unreviewed nested attestation action")
    for item in data["downloads"]:
        check_download(item, data["cooldown_days"])
    print("PASS: dependency publication age, immutable pins and upstream metadata")


def download(data, names):
    folder = ROOT / "build/tools"
    folder.mkdir(parents=True, exist_ok=True)
    for name in names:
        item = next(i for i in data["downloads"] if i["name"] == name)
        check_download(item, data["cooldown_days"])
        path = folder / item["asset"]
        if not path.exists() or digest(path) != item["sha256"]:
            temporary = path.with_suffix(path.suffix + ".part")
            try:
                with request(item["url"]) as source, temporary.open("wb") as out:
                    while chunk := source.read(1024 * 1024):
                        out.write(chunk)
                if digest(temporary) != item["sha256"]:
                    raise ValueError("Downloaded artifact checksum does not match the reviewed lock")
                temporary.replace(path)
            finally:
                temporary.unlink(missing_ok=True)
        if path.suffix == ".zip":
            # Each SDK archive gets a fresh namespace: removed compiler/stdlib
            # files from an older SDK must never survive a toolchain update.
            destination = folder / (name + "-" + item["version"] + "-" + item["sha256"][:16] if item.get("kind") == "go_sdk" else name)
            destination.mkdir(exist_ok=True)
            with zipfile.ZipFile(path) as archive:
                for member in archive.infolist():
                    target = (destination / member.filename).resolve()
                    if not target.is_relative_to(destination.resolve()):
                        raise ValueError("Unsafe archive path")
                archive.extractall(destination)
        print("Verified download:", name)


def audit(data):
    go_security("audit")
    for item in data["downloads"]:
        if "ecosystem" not in item:
            continue
        query = urllib.parse.urlencode({"ecosystem": item["ecosystem"], "affects": item["package"] + "@" + item["version"], "per_page": 100})
        advisories = [a for a in api("advisories?" + query) if not a.get("withdrawn_at")]
        if advisories:
            raise ValueError("Vulnerable dependency: " + ", ".join(a["ghsa_id"] for a in advisories))
    print("PASS: GitHub advisory database audit")


def go_security(command):
    # Reuse the same fail-closed network and age checks for the Go module mirror.
    import importlib.util
    spec = importlib.util.spec_from_file_location("go_security", ROOT / "scripts/go-security.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.run(command)


def sandbox_policy(app, namespace):
    services = [node for node in app.findall("service") if node.get(namespace + "name") == ".SandboxService"]
    if len(services) != 1 or any(services[0].get(namespace + key) != value for key, value in {"exported": "false", "isolatedProcess": "true", "process": ":sandbox"}.items()):
        raise ValueError("Code execution must use one private isolated sandbox service")


def policy(data):
    pins = {i["repository"]: i["sha"] for i in data["actions"]}
    for path in (ROOT / ".github/workflows").glob("*.yml"):
        text = path.read_text()
        if "pull_request_target" in text:
            raise ValueError("Privileged pull_request_target workflows are forbidden")
        for action in re.findall(r"uses:\s*([^\s#]+)", text):
            if action.startswith("./"):
                target = (ROOT / action).resolve()
                if not target.is_relative_to((ROOT / ".github/workflows").resolve()) or not target.is_file():
                    raise ValueError("Unreviewed reusable workflow path")
                continue
            repo_path, sha = action.rsplit("@", 1)
            repo = "/".join(repo_path.split("/")[:2])
            if pins.get(repo) != sha:
                raise ValueError(f"Unpinned or unreviewed action in {path.name}: {action}")
    namespace = "{http://schemas.android.com/apk/res/android}"
    manifest = ET.parse(ROOT / "app/AndroidManifest.xml").getroot()
    app = manifest.find("application")
    for attribute in ("allowBackup", "usesCleartextTraffic"):
        if app.get(namespace + attribute) != "false":
            raise ValueError("Sensitive app data must not be backed up or sent over cleartext")
    if app.get(namespace + "debuggable", "false") != "false":
        raise ValueError("Release manifest must not be debuggable")
    allowed = {"android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_DATA_SYNC", "android.permission.POST_NOTIFICATIONS", "android.permission.REQUEST_INSTALL_PACKAGES", "android.permission.RECEIVE_BOOT_COMPLETED"}
    if {i.get(namespace + "name") for i in manifest.findall("uses-permission")} != allowed:
        raise ValueError("Review new Android permissions before changing the policy")
    for node in app:
        if node.get(namespace + "exported") == "true":
            if node.get(namespace + "name") == ".MainActivity":
                continue
            if node.get(namespace + "name") == ".AgentService" and node.get(namespace + "permission") == "android.permission.BIND_ACCESSIBILITY_SERVICE":
                continue
            if node.get(namespace + "name") == ".UpdateJob" and node.get(namespace + "permission") == "android.permission.BIND_JOB_SERVICE":
                continue
            raise ValueError("Unexpected exported Android component")
    sandbox_policy(app, namespace)
    english = {i.get("name"): i.text or "" for i in ET.parse(ROOT / "app/res/values/strings.xml").getroot()}
    japanese = {i.get("name"): i.text or "" for i in ET.parse(ROOT / "app/res/values-ja/strings.xml").getroot()}
    if english.keys() != japanese.keys():
        raise ValueError("Missing locale strings")
    for key in english:
        if sorted(re.findall(r"%\d+\$[ds]", english[key])) != sorted(re.findall(r"%\d+\$[ds]", japanese[key])):
            raise ValueError("Locale placeholder mismatch: " + key)
    for path in ROOT.rglob("*.java"):
        if "build" in path.parts:
            continue
        for key in re.findall(r'I18n\.text\([^,]+,"([a-z_]+)"', path.read_text(encoding="utf-8")):
            if key not in english:
                raise ValueError("Missing resource referenced by Java: " + key)
    print("PASS: workflow pins, Android manifest and locale parity")


def sarif(path):
    files = list(Path(path).rglob("*.sarif"))
    if not files:
        raise ValueError("CodeQL did not produce SARIF results")
    for file in files:
        data = json.loads(file.read_text(encoding="utf-8-sig"))
        for run in data.get("runs", []):
            tool = run.get("tool", {})
            components = [tool.get("driver", {})] + tool.get("extensions", [])
            rules = {r["id"]: r for component in components for r in component.get("rules", [])}
            for result in run.get("results", []):
                rule_id = result.get("ruleId") or result.get("rule", {}).get("id")
                if rule_id not in rules:
                    raise ValueError("CodeQL finding has no rule metadata: " + str(rule_id))
                score = float(rules[rule_id].get("properties", {}).get("security-severity", 0))
                if score >= 7 or result.get("level") == "error":
                    raise ValueError("High-severity CodeQL finding: " + rule_id)
    print("PASS: no high-severity CodeQL findings")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["verify", "download", "audit", "policy", "sarif"])
    parser.add_argument("--name", action="append", default=[])
    parser.add_argument("--path", default="build/codeql-results")
    args = parser.parse_args()
    try:
        data = load()
        if args.command == "download":
            download(data, args.name)
        elif args.command == "sarif":
            sarif(args.path)
        else:
            globals()[args.command](data)
    except Exception as error:
        print("SECURITY CHECK FAILED:", error, file=sys.stderr)
        sys.exit(1)
