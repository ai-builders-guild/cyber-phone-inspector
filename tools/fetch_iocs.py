"""Download the MVT public indicators and write the bundled snapshot the app ships with.

Usage (from the project root, network required):
    uv run python tools/fetch_iocs.py
    uv run python tools/fetch_iocs.py --check   # exit 1 if the committed snapshot is older than 90 days

The only index this script will read is PINNED_INDEX. Sources it follows must be GitHub repositories named by
that index and are fetched from raw.githubusercontent.com only. Any other URL is refused (exit 2).
Stdlib only. Output: app/src/main/assets/iocs/snapshot.json and SNAPSHOT.md.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import sys
import urllib.request
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
ASSET_DIR = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "iocs"
PINNED_INDEX = "https://raw.githubusercontent.com/mvt-project/mvt-indicators/main/indicators.yaml"
RAW_HOST = "https://raw.githubusercontent.com/"
TEST_ONLY_PACKAGE = "com.example.ioc.match"
MAX_AGE_DAYS = 90

PATTERN = re.compile(r"([\w\-]+:[\w\.\-]+)\s*=\s*'([^']*)'")


class RefusedUrl(Exception):
    pass


def parse_index(text: str) -> list[dict[str, str]]:
    """The index is a YAML list of sources; only `github:` sources with owner, repo, branch and path are used."""
    sources = []
    for block in re.split(r"\n\s+-\s*\n", text):
        fields = {k: v.strip() for k, v in re.findall(r"^\s+(owner|repo|branch|path|name):\s*(.+)$", block, re.M)}
        if {"owner", "repo", "branch", "path"} <= fields.keys():
            sources.append(fields)
    return sources


def source_url(source: dict[str, str]) -> str:
    for key in ("owner", "repo", "branch", "path"):
        if not re.fullmatch(r"[\w\-\./]+", source[key]) or ".." in source[key]:
            raise RefusedUrl(f"unsafe source field {key}={source[key]!r}")
    return f"{RAW_HOST}{source['owner']}/{source['repo']}/{source['branch']}/{source['path']}"


def fetch(url: str) -> bytes:
    if url != PINNED_INDEX and not url.startswith(RAW_HOST):
        raise RefusedUrl(f"refusing to fetch {url}: only the pinned index and raw.githubusercontent.com are allowed")
    with urllib.request.urlopen(url, timeout=30) as resp:  # noqa: S310 (host checked above)
        return resp.read()


def extract(stix: dict) -> dict[str, set[str]]:
    out: dict[str, set[str]] = {"packages": set(), "domains": set(), "certs": set(), "ips": set()}
    for obj in stix.get("objects", []):
        if obj.get("type") != "indicator":
            continue
        for key, value in PATTERN.findall(obj.get("pattern", "")):
            value = value.strip()
            if not value:
                continue
            if key in ("app:id", "android-app:id"):
                out["packages"].add(value)
            elif key in ("domain-name:value",):
                out["domains"].add(value.lower().rstrip("."))
            elif key == "url:value":
                host = re.sub(r"^[a-z]+://", "", value).split("/")[0].split(":")[0].lower()
                if "." in host:
                    out["domains"].add(host)
            elif key == "app:cert.sha1":
                out["certs"].add("sha1:" + value.lower())
            elif key == "app:cert.sha256":
                out["certs"].add("sha256:" + value.lower())
            elif key == "ipv4-addr:value":
                out["ips"].add(value)
    return out


def build_snapshot(index_text: str, fetcher=fetch) -> tuple[dict, list[dict]]:
    sources = parse_index(index_text)
    merged: dict[str, set[str]] = {"packages": set(), "domains": set(), "certs": set(), "ips": set()}
    report = []
    for src in sources:
        url = source_url(src)
        try:
            found = extract(json.loads(fetcher(url)))
        except (OSError, ValueError) as e:
            report.append({"name": src.get("name", url), "error": f"{type(e).__name__}: {e}"})
            continue
        for k in merged:
            merged[k] |= found[k]
        report.append({"name": src.get("name", url), "ref": f"{src['owner']}/{src['repo']}@{src['branch']}:{src['path']}", **{k: len(v) for k, v in found.items()}})
    merged["packages"].discard(TEST_ONLY_PACKAGE)
    snapshot = {
        "generatedAt": dt.date.today().isoformat(),
        "sources": [r["ref"] for r in report if "ref" in r],
        "packages": sorted(merged["packages"]),
        "domains": sorted(merged["domains"]),
        "certs": sorted(merged["certs"]),
        "ips": sorted(merged["ips"]),
    }
    return snapshot, report


def write(snapshot: dict, report: list[dict], asset_dir: Path) -> None:
    asset_dir.mkdir(parents=True, exist_ok=True)
    (asset_dir / "snapshot.json").write_text(json.dumps(snapshot, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    lines = [
        "# IOC snapshot",
        "",
        f"Generated {snapshot['generatedAt']} by tools/fetch_iocs.py from the MVT public indicator index.",
        f"Index: {PINNED_INDEX}",
        "",
        f"Totals: {len(snapshot['packages'])} package ids, {len(snapshot['domains'])} domains, {len(snapshot['certs'])} signing certs, {len(snapshot['ips'])} IPs.",
        "",
        "| Source | Packages | Domains | Certs | IPs |",
        "|---|---|---|---|---|",
    ]
    for r in report:
        if "error" in r:
            lines.append(f"| {r['name']} | error: {r['error']} | | | |")
        else:
            lines.append(f"| {r['name']} | {r['packages']} | {r['domains']} | {r['certs']} | {r['ips']} |")
    (asset_dir / "SNAPSHOT.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def check_age(asset_dir: Path) -> int:
    snapshot = json.loads((asset_dir / "snapshot.json").read_text(encoding="utf-8"))
    age = (dt.date.today() - dt.date.fromisoformat(snapshot["generatedAt"])).days
    print(f"snapshot is {age} days old ({len(snapshot['packages'])} packages, {len(snapshot['domains'])} domains)")
    return 1 if age > MAX_AGE_DAYS else 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--index", default=PINNED_INDEX, help="must equal the pinned index; anything else is refused")
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--out", type=Path, default=ASSET_DIR)
    args = parser.parse_args(argv)
    env_url = os.environ.get("CPI_IOC_SOURCE_URL")
    if args.index != PINNED_INDEX or (env_url and env_url != PINNED_INDEX):
        print(f"refused: only {PINNED_INDEX} may be used as the index", file=sys.stderr)
        return 2
    if args.check:
        return check_age(args.out)
    snapshot, report = build_snapshot(fetch(PINNED_INDEX).decode("utf-8"))
    write(snapshot, report, args.out)
    print(f"wrote {args.out / 'snapshot.json'}: {len(snapshot['packages'])} packages, {len(snapshot['domains'])} domains, {len(snapshot['certs'])} certs")
    errors = [r for r in report if "error" in r]
    if errors:
        print(f"{len(errors)} sources failed; see SNAPSHOT.md", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
