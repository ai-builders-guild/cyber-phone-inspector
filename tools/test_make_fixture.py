"""Stdlib unittest for the fixture generator. Run: uv run python -m unittest discover -s tools -p 'test_*.py'"""

import json
import tempfile
import unittest
from pathlib import Path

import make_fixture as mf


class MakeFixtureTest(unittest.TestCase):
    def test_empty_scenario_yields_no_records_and_all_checks(self):
        evidence = mf.build({"name": "t", "device": {"manufacturer": "A", "model": "B", "androidRelease": "16", "sdkInt": 36}})
        self.assertEqual(evidence["apps"], [])
        self.assertEqual([c["check"] for c in evidence["checks"]], mf.CHECKS)
        self.assertTrue(all(c["status"] == "CHECKED" for c in evidence["checks"]))

    def test_generate_apps_produces_exact_count_with_system_share(self):
        evidence = mf.build({"name": "t", "device": {}, "generate_apps": 2000})
        self.assertEqual(len(evidence["apps"]), 2000)
        self.assertEqual(sum(a["isSystem"] for a in evidence["apps"]), 400)

    def test_beacon_flows_expand_at_fixed_interval(self):
        evidence = mf.build(
            {"name": "t", "device": {}, "flows": [{"uid": 10001, "host": "x.example", "repeat": 5, "intervalSeconds": 60}]}
        )
        times = [f["observedAt"] for f in evidence["flows"]]
        self.assertEqual([b - a for a, b in zip(times, times[1:])], [60000] * 4)

    def test_output_is_deterministic(self):
        scenario = {"name": "t", "device": {"manufacturer": "A"}, "apps": [{"packageName": "com.example.one"}]}
        self.assertEqual(json.dumps(mf.build(scenario), sort_keys=True), json.dumps(mf.build(scenario), sort_keys=True))

    def test_check_mode_detects_stale_fixture(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            self.assertEqual(mf.main(["--out", str(out)]), 0)
            self.assertEqual(mf.main(["--check", "--out", str(out)]), 0)
            next(out.glob("*.json")).write_text("{}", encoding="utf-8")
            self.assertEqual(mf.main(["--check", "--out", str(out)]), 1)


if __name__ == "__main__":
    unittest.main()
