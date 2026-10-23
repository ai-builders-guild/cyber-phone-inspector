"""Build the local threat lists the app ships and refreshes: spyware (MVT), trackers (Exodus), malware hashes and
malicious hosts (abuse.ch, only when a key is present).

Usage (project root):
    uv run python tools/fetch_feeds.py [--out feeds]        # writes feeds/*.json and FEEDS.md
    uv run python tools/fetch_feeds.py --check [--out feeds] # exit 1 if any list is older than 90 days

Only PINNED addresses are contacted; anything else is refused (exit 2). Stdlib only. The abuse.ch key is read from
CPI_ABUSECH_AUTH_KEY (local .env) or ABUSECH_AUTH_KEY (GitHub Actions secret) and never written to any output.
abuse.ch datasets are CC0; Exodus Privacy data is CC BY-SA 4.0 and credited in FEEDS.md and in the app.
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

# `python -I` (isolated, as the Action runs it) does not put the script's own folder on sys.path.
sys.path.insert(0, str(Path(__file__).resolve().parent))
import fetch_iocs  # noqa: E402  the MVT spyware index logic lives there and is reused unchanged

PINNED = {
    "mvt_index": fetch_iocs.PINNED_INDEX,
    "exodus": "https://reports.exodus-privacy.eu.org/api/trackers",
    "bazaar": "https://mb-api.abuse.ch/api/v1/",
    "urlhaus": "https://urlhaus-api.abuse.ch/v1/urls/recent/limit/1000/",
    "threatfox": "https://threatfox-api.abuse.ch/api/v1/",
}
MAX_AGE_DAYS = 90
TEST_ONLY_HASH = "0000000000000000000000000000000000000000000000000000000000000001"
# MalwareBazaar's API returns at most 1,000 samples per query; these queries are unioned to cover Android families.
BAZAAR_QUERIES = [
    {"query": "get_file_type", "file_type": "apk", "limit": "1000"},
    {"query": "get_file_type", "file_type": "dex", "limit": "1000"},
] + [{"query": "get_taginfo", "tag": t, "limit": "1000"} for t in (
    "android", "Hydra", "Cerberus", "Anubis", "FluBot", "Joker", "SpyNote", "Alien", "Octo", "Ermac", "SOVA", "Hook",
    "TeaBot", "SharkBot", "Xenomorph", "BRATA", "Vultur", "Medusa", "Coper", "Triada", "Agent.Tesla", "AhMyth", "Rafel",
    "adware", "scareware", "FakeAV", "Hiddad", "Necro", "Mamont", "GoldDigger", "Antidot", "Tria",
)]


class RefusedUrl(Exception):
    pass


def today() -> str:
    return dt.date.today().isoformat()


def key() -> str | None:
    return os.environ.get("CPI_ABUSECH_AUTH_KEY") or os.environ.get("ABUSECH_AUTH_KEY") or None


def fetch(url: str, data: dict | str | None = None, auth: str | None = None) -> bytes:
    """GET when data is None; POST a form when data is a dict; POST JSON when data is a str (ThreatFox wants JSON)."""
    if url not in PINNED.values() and not url.startswith(fetch_iocs.RAW_HOST):
        raise RefusedUrl(f"refusing {url}: not a pinned address")
    body = None
    headers = {"User-Agent": "cyber-phone-inspector-feeds"}
    if isinstance(data, str):
        body = data.encode()
        headers["Content-Type"] = "application/json"
    elif data is not None:
        body = "&".join(f"{k}={v}" for k, v in data.items()).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    if auth:
        headers["Auth-Key"] = auth
    req = urllib.request.Request(url, data=body, headers=headers)
    with urllib.request.urlopen(req, timeout=60) as resp:  # noqa: S310 (pinned above)
        return resp.read()


# ---------------- Exodus trackers ----------------

def build_trackers(raw: bytes) -> dict:
    data = json.loads(raw)["trackers"]
    trackers = []
    for t in data.values():
        sigs = [s.strip() for s in (t.get("code_signature") or "").split("|") if s.strip()]
        if not sigs:
            continue
        trackers.append({"name": t["name"], "categories": sorted(t.get("categories") or []), "signatures": sigs})
    trackers.sort(key=lambda t: t["name"].lower())
    return {"generatedAt": today(), "source": "Exodus Privacy, CC BY-SA 4.0, " + PINNED["exodus"], "trackers": trackers}


# ---------------- abuse.ch ----------------

def parse_bazaar(raw: bytes) -> dict[str, dict]:
    doc = json.loads(raw)
    if doc.get("query_status") != "ok":
        return {}
    out = {}
    for s in doc.get("data", []):
        if (s.get("file_type") or "").lower() not in ("apk", "dex"):
            continue
        h = (s.get("sha256_hash") or "").lower()
        if re.fullmatch(r"[0-9a-f]{64}", h):
            out[h] = {"family": s.get("signature") or "unknown", "tags": sorted(t for t in (s.get("tags") or []) if t)}
    return out


def parse_urlhaus(raw: bytes) -> set[str]:
    doc = json.loads(raw)
    hosts = set()
    for u in doc.get("urls", []):
        host = re.sub(r"^[a-z]+://", "", u.get("url", "")).split("/")[0].split(":")[0].lower()
        if "." in host and not re.fullmatch(r"[0-9.]+", host):
            hosts.add(host)
    return hosts


def parse_threatfox(raw: bytes) -> tuple[set[str], set[str]]:
    doc = json.loads(raw)
    domains, ips = set(), set()
    if doc.get("query_status") != "ok":
        print(f"threatfox answered {doc.get('query_status')!r}: {str(doc.get('data', ''))[:120]}", file=sys.stderr)
    for i in doc.get("data", []) if doc.get("query_status") == "ok" else []:
        t = i.get("ioc_type", "")
        v = (i.get("ioc") or "").lower()
        if t == "domain" and v:
            domains.add(v)
        elif t in ("ip:port", "ip") and v:
            ips.add(v.split(":")[0])
    return domains, ips


def build_abusech(auth: str | None, fetcher=fetch) -> tuple[dict, dict]:
    if not auth:
        skipped = {"generatedAt": today(), "skipped": "no abuse.ch key; set ABUSECH_AUTH_KEY to include this feed"}
        return ({**skipped, "source": "MalwareBazaar, abuse.ch, CC0", "hashes": {}},
                {**skipped, "source": "URLhaus and ThreatFox, abuse.ch, CC0", "domains": [], "ips": []})
    hashes: dict[str, dict] = {}
    for q in BAZAAR_QUERIES:
        try:
            hashes.update(parse_bazaar(fetcher(PINNED["bazaar"], q, auth)))
        except (OSError, ValueError) as e:
            print(f"bazaar query {q} failed: {type(e).__name__}: {e}", file=sys.stderr)
    hashes.pop(TEST_ONLY_HASH, None)
    domains, ips = set(), set()
    try:
        domains |= parse_urlhaus(fetcher(PINNED["urlhaus"], None, auth))  # the recent list is a GET, limit in the path
    except (OSError, ValueError) as e:
        print(f"urlhaus failed: {type(e).__name__}: {e}", file=sys.stderr)
    try:
        d, i = parse_threatfox(fetcher(PINNED["threatfox"], json.dumps({"query": "get_iocs", "days": 7}), auth))
        domains |= d
        ips |= i
    except (OSError, ValueError) as e:
        print(f"threatfox failed: {type(e).__name__}: {e}", file=sys.stderr)
    return ({"generatedAt": today(), "source": "MalwareBazaar, abuse.ch, CC0", "hashes": dict(sorted(hashes.items()))},
            {"generatedAt": today(), "source": "URLhaus and ThreatFox, abuse.ch, CC0", "domains": sorted(domains), "ips": sorted(ips)})


# ---------------- write ----------------

def write(out: Path, spyware: dict, trackers: dict, malware: dict, hosts: dict) -> None:
    out.mkdir(parents=True, exist_ok=True)
    for name, doc in (("spyware.json", spyware), ("trackers.json", trackers), ("malware_hashes.json", malware), ("malicious_hosts.json", hosts)):
        (out / name).write_text(json.dumps(doc, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    lines = [
        "# Threat lists",
        "",
        f"Generated {today()} by fetch_feeds.py. Checked on the phone only; the phone sends nothing.",
        "",
        "| List | Source | Entries |",
        "|---|---|---|",
        f"| spyware.json | MVT indicator index (Amnesty Security Lab and the lists it names) | {len(spyware['packages'])} packages, {len(spyware['domains'])} domains, {len(spyware['certs'])} certs, {len(spyware.get('ips', []))} IPs |",
        f"| trackers.json | Exodus Privacy (CC BY-SA 4.0) | {len(trackers['trackers'])} trackers |",
        f"| malware_hashes.json | MalwareBazaar, abuse.ch (CC0) | {len(malware['hashes'])} Android hashes{' (skipped: ' + malware['skipped'] + ')' if malware.get('skipped') else ''} |",
        f"| malicious_hosts.json | URLhaus and ThreatFox, abuse.ch (CC0) | {len(hosts['domains'])} domains, {len(hosts['ips'])} IPs{' (skipped)' if hosts.get('skipped') else ''} |",
        "",
        "Attribution: Exodus Privacy tracker data is licensed CC BY-SA 4.0; abuse.ch data is CC0; MVT indicators carry their own sources' terms as listed in the index.",
    ]
    (out / "FEEDS.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def check(out: Path) -> int:
    worst = 0
    for name in ("spyware.json", "trackers.json", "malware_hashes.json", "malicious_hosts.json"):
        doc = json.loads((out / name).read_text(encoding="utf-8"))
        age = (dt.date.today() - dt.date.fromisoformat(doc["generatedAt"])).days
        print(f"{name}: {age} days old{' (skipped)' if doc.get('skipped') else ''}")
        worst = max(worst, age)
    return 1 if worst > MAX_AGE_DAYS else 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", type=Path, default=Path("feeds"))
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args(argv)
    if args.check:
        return check(args.out)
    spyware, _report = fetch_iocs.build_snapshot(fetch_iocs.fetch(fetch_iocs.PINNED_INDEX).decode("utf-8"))
    trackers = build_trackers(fetch(PINNED["exodus"]))
    malware, hosts = build_abusech(key())
    write(args.out, spyware, trackers, malware, hosts)
    print(f"wrote {args.out}: {len(spyware['packages'])} spyware packages, {len(trackers['trackers'])} trackers, {len(malware['hashes'])} malware hashes, {len(hosts['domains'])} malicious domains")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
