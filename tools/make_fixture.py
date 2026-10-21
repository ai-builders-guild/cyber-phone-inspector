"""Generate evidence fixtures for the JVM tests from TOML scenario specs.

Usage (from the project root):
    uv run python tools/make_fixture.py                 # all scenarios -> app/src/test/resources/fixtures/
    uv run python tools/make_fixture.py --check         # regenerate to a temp dir and diff against committed

Stdlib only (tomllib, json). Output is deterministic: fixed timestamps, sorted keys, stable order.
Field names mirror app/src/main/java/com/cyberrick/phoneinspector/core/model/Records.kt.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import tomllib
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
SCENARIO_DIR = PROJECT_ROOT / "tools" / "scenarios"
FIXTURE_DIR = PROJECT_ROOT / "app" / "src" / "test" / "resources" / "fixtures"

# 2026-10-21T12:00:00Z and 2026-09-01T00:00:00Z, epoch milliseconds. Fixed so output never drifts.
COLLECTED_AT = 1792584000000
INSTALLED_AT = 1788220800000
DAY_MS = 86_400_000

API = {
    "apps": "PackageManager.getInstalledPackages",
    "controls": "DevicePolicyManager+Settings.Secure",
    "permissions": "PackageInfo.requestedPermissionsFlags",
    "persistence": "UsageStatsManager.queryEvents",
    "flows": "DnsTunnelService",
}
CHECKS = ["APPS", "CONTROL", "PERMISSIONS", "PERSISTENCE", "NETWORK"]


def app_record(spec: dict, index: int) -> dict:
    """Fill an AppRecord from a sparse scenario entry. Explicit keys win over defaults."""
    pkg = spec["packageName"]
    is_system = bool(spec.get("isSystem", False))
    record = {
        "packageName": pkg,
        "label": spec.get("label", pkg.rsplit(".", 1)[-1].capitalize()),
        "versionName": spec.get("versionName", "1.0"),
        "versionCode": int(spec.get("versionCode", 1)),
        "firstInstallAt": int(spec.get("firstInstallAt", INSTALLED_AT + index * DAY_MS)),
        "lastUpdateAt": int(spec.get("lastUpdateAt", INSTALLED_AT + index * DAY_MS)),
        "installerPackage": spec.get("installerPackage", None if is_system else "com.android.vending"),
        "installSource": spec.get("installSource", "SYSTEM" if is_system else "PLAY_STORE"),
        "signingCertSha256": list(spec.get("signingCertSha256", [f"sha256:{hashlib.sha256(pkg.encode()).hexdigest()[:16]}"])),
        "isSystem": is_system,
        "hasLauncherIcon": bool(spec.get("hasLauncherIcon", not is_system)),
        "enabled": bool(spec.get("enabled", True)),
        "hidden": bool(spec.get("hidden", False)),
        "disguised": bool(spec.get("disguised", False)),
        "category": spec.get("category"),
        "duplicate": bool(spec.get("duplicate", False)),
        "api": API["apps"],
        "collectedAt": COLLECTED_AT,
    }
    return record


def provenanced(kind: str, spec: dict, defaults: dict) -> dict:
    record = dict(defaults)
    record.update(spec)
    record.setdefault("api", API[kind])
    record.setdefault("collectedAt", COLLECTED_AT)
    return record


def generated_apps(count: int) -> list[dict]:
    """Bulk synthetic apps: 20 percent system, the rest Play installed. Deterministic names."""
    apps = []
    for i in range(count):
        is_system = i % 5 == 0
        apps.append(
            app_record(
                {
                    "packageName": f"com.synthetic.app{i:04d}",
                    "label": f"App {i:04d}",
                    "isSystem": is_system,
                },
                i,
            )
        )
    return apps


def build(scenario: dict) -> dict:
    apps = [app_record(a, i) for i, a in enumerate(scenario.get("apps", []))]
    apps += generated_apps(int(scenario.get("generate_apps", 0)))
    # Explicit duplicate flag lives in the loader; fixtures carry what a collector would emit.
    checks = {c: "CHECKED" for c in CHECKS}
    checks.update(scenario.get("checks", {}))
    reasons = scenario.get("check_reasons", {})
    return {
        "schemaVersion": 1,
        "scenario": scenario["name"],
        "device": scenario["device"],
        "collectedAt": COLLECTED_AT,
        "checks": [{"check": c, "status": checks[c], "reason": reasons.get(c)} for c in CHECKS],
        "apps": apps,
        "controls": [
            provenanced("controls", c, {"component": None, "sinceAtLatest": INSTALLED_AT})
            for c in scenario.get("controls", [])
        ],
        "permissions": [
            provenanced("permissions", p, {"requested": True, "granted": True, "uncertain": False, "unexpected": False})
            for p in scenario.get("permissions", [])
        ],
        "persistence": [
            provenanced(
                "persistence",
                p,
                {
                    "bootReceiver": False,
                    "foregroundServiceMinutes7d": 0,
                    "lastForegroundServiceStartAt": None,
                    "backgroundHeavy": False,
                    "droppedEvents": 0,
                },
            )
            for p in scenario.get("persistence", [])
        ],
        "flows": expand_flows(scenario.get("flows", [])),
        "iocSnapshotDate": scenario.get("iocSnapshotDate", "2026-10-21"),
    }


def expand_flows(specs: list[dict]) -> list[dict]:
    """A flow spec with `repeat` and `intervalSeconds` expands to a beacon series; `jitterSeconds` adds a fixed pattern."""
    flows = []
    for spec in specs:
        repeat = int(spec.pop("repeat", 1))
        interval = int(spec.pop("intervalSeconds", 0)) * 1000
        jitter = int(spec.pop("jitterSeconds", 0)) * 1000
        start = int(spec.pop("startAt", COLLECTED_AT - 10 * 60 * 1000))
        for n in range(repeat):
            # Deterministic jitter pattern: alternating plus and minus, growing, so the CV is well above a beacon's.
            offset = interval * n + (jitter * ((n % 3) - 1))
            flows.append(
                provenanced(
                    "flows",
                    {**spec, "observedAt": start + offset},
                    {"packageName": None, "host": None, "answerIps": [], "remotePort": 53, "protocol": "udp", "bytesSent": None},
                )
            )
    return flows


def render(scenario_path: Path) -> tuple[str, str]:
    with scenario_path.open("rb") as f:
        scenario = tomllib.load(f)
    evidence = build(scenario)
    return scenario["name"], json.dumps(evidence, indent=2, sort_keys=True, ensure_ascii=False) + "\n"


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="verify committed fixtures match the scenarios")
    parser.add_argument("--out", type=Path, default=FIXTURE_DIR)
    args = parser.parse_args(argv)

    scenario_paths = sorted(SCENARIO_DIR.glob("*.toml"))
    if not scenario_paths:
        print(f"no scenarios in {SCENARIO_DIR}", file=sys.stderr)
        return 2
    args.out.mkdir(parents=True, exist_ok=True)
    stale = []
    for path in scenario_paths:
        name, text = render(path)
        target = args.out / f"{name}.json"
        if args.check:
            if not target.exists() or target.read_text(encoding="utf-8") != text:
                stale.append(target.name)
        else:
            target.write_text(text, encoding="utf-8")
            shown = target.relative_to(PROJECT_ROOT) if target.is_relative_to(PROJECT_ROOT) else target
            print(f"wrote {shown}")
    if args.check:
        if stale:
            print("stale fixtures: " + ", ".join(stale), file=sys.stderr)
            return 1
        print(f"{len(scenario_paths)} fixtures up to date")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
