"""Stdlib unittest for the IOC fetch script. No network: fetcher is injected."""

import json
import unittest

import fetch_iocs as fi

INDEX = """indicators:
  -
    type: github
    name: Source One
    github:
      owner: ExampleOrg
      repo: investigations
      branch: master
      path: 2021/one.stix2
  -
    type: github
    name: Source Two
    github:
      owner: ExampleOrg
      repo: investigations
      branch: main
      path: two.stix2
"""

STIX = {
    "objects": [
        {"type": "indicator", "pattern": "[app:id = 'com.bad.app']"},
        {"type": "indicator", "pattern": "[domain-name:value = 'C2.Evil.Example.']"},
        {"type": "indicator", "pattern": "[app:cert.sha1 = 'ABCDEF']"},
        {"type": "indicator", "pattern": "[url:value = 'https://drop.evil.example/x/y']"},
        {"type": "indicator", "pattern": "[ipv4-addr:value = '10.9.8.7']"},
        {"type": "indicator", "pattern": "[app:id = 'com.example.ioc.match']"},
        {"type": "malware", "name": "ignored"},
    ]
}


class FetchIocsTest(unittest.TestCase):
    def test_parse_index_reads_github_sources(self):
        sources = fi.parse_index(INDEX)
        self.assertEqual([s["path"] for s in sources], ["2021/one.stix2", "two.stix2"])
        self.assertEqual(fi.source_url(sources[0]), "https://raw.githubusercontent.com/ExampleOrg/investigations/master/2021/one.stix2")

    def test_extract_normalises_each_indicator_kind(self):
        found = fi.extract(STIX)
        self.assertEqual(found["packages"], {"com.bad.app", "com.example.ioc.match"})
        self.assertEqual(found["domains"], {"c2.evil.example", "drop.evil.example"})
        self.assertEqual(found["certs"], {"sha1:abcdef"})
        self.assertEqual(found["ips"], {"10.9.8.7"})

    def test_snapshot_drops_the_test_only_package_and_survives_a_failed_source(self):
        def fetcher(url):
            if url.endswith("two.stix2"):
                raise OSError("boom")
            return json.dumps(STIX).encode()

        snapshot, report = fi.build_snapshot(INDEX, fetcher)
        self.assertNotIn(fi.TEST_ONLY_PACKAGE, snapshot["packages"])
        self.assertIn("com.bad.app", snapshot["packages"])
        self.assertEqual(len([r for r in report if "error" in r]), 1)
        self.assertEqual(snapshot["sources"], ["ExampleOrg/investigations@master:2021/one.stix2"])

    def test_refuses_any_other_index_url(self):
        self.assertEqual(fi.main(["--index", "https://example.com/other.yaml"]), 2)

    def test_refuses_fetching_off_the_raw_host(self):
        with self.assertRaises(fi.RefusedUrl):
            fi.fetch("https://example.com/evil.json")

    def test_refuses_path_traversal_in_source_fields(self):
        with self.assertRaises(fi.RefusedUrl):
            fi.source_url({"owner": "a", "repo": "b", "branch": "c", "path": "../../etc"})


if __name__ == "__main__":
    unittest.main()
