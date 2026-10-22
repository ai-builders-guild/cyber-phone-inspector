"""Stdlib unittest for fetch_feeds. No network: fetchers are injected."""

import json
import unittest

import fetch_feeds as ff

EXODUS = {"trackers": {"1": {"name": "Zeta", "categories": ["Analytics"], "code_signature": "com.zeta.|com.zeta2."}, "2": {"name": "NoSig", "categories": [], "code_signature": ""}, "3": {"name": "Alpha", "categories": ["Advertisement"], "code_signature": "com.alpha.ads"}}}
BAZAAR = {"query_status": "ok", "data": [
    {"sha256_hash": "A" * 64, "file_type": "apk", "signature": "Cerberus", "tags": ["banker", "android"]},
    {"sha256_hash": "b" * 64, "file_type": "exe", "signature": "Emotet", "tags": []},
    {"sha256_hash": ff.TEST_ONLY_HASH, "file_type": "apk", "signature": "test", "tags": []},
]}
URLHAUS = {"urls": [{"url": "http://evil.example/x.apk"}, {"url": "https://1.2.3.4/payload"}]}
THREATFOX = {"query_status": "ok", "data": [{"ioc_type": "domain", "ioc": "C2.Example"}, {"ioc_type": "ip:port", "ioc": "5.6.7.8:443"}]}


class FetchFeedsTest(unittest.TestCase):
    def test_trackers_keep_only_entries_with_signatures_sorted_by_name(self):
        doc = ff.build_trackers(json.dumps(EXODUS).encode())
        self.assertEqual([t["name"] for t in doc["trackers"]], ["Alpha", "Zeta"])
        self.assertEqual(doc["trackers"][1]["signatures"], ["com.zeta.", "com.zeta2."])
        self.assertIn("Exodus", doc["source"])

    def test_bazaar_keeps_android_file_types_lowercases_and_drops_the_test_hash(self):
        hashes = ff.parse_bazaar(json.dumps(BAZAAR).encode())
        self.assertEqual(set(hashes), {"a" * 64, ff.TEST_ONLY_HASH})
        self.assertEqual(hashes["a" * 64], {"family": "Cerberus", "tags": ["android", "banker"]})
        malware, _ = ff.build_abusech("k", fetcher=lambda url, data=None, auth=None: json.dumps(BAZAAR if "mb-api" in url else (URLHAUS if "urlhaus" in url else THREATFOX)).encode())
        self.assertNotIn(ff.TEST_ONLY_HASH, malware["hashes"])

    def test_hosts_merge_urlhaus_hostnames_and_threatfox_domains_and_ips(self):
        _, hosts = ff.build_abusech("k", fetcher=lambda url, data=None, auth=None: json.dumps(BAZAAR if "mb-api" in url else (URLHAUS if "urlhaus" in url else THREATFOX)).encode())
        self.assertEqual(hosts["domains"], ["c2.example", "evil.example"])
        self.assertEqual(hosts["ips"], ["5.6.7.8"])

    def test_without_a_key_the_abusech_lists_are_written_as_skipped(self):
        malware, hosts = ff.build_abusech(None)
        self.assertIn("skipped", malware)
        self.assertEqual(malware["hashes"], {})
        self.assertEqual(hosts["domains"], [])

    def test_refuses_an_unpinned_address(self):
        with self.assertRaises(ff.RefusedUrl):
            ff.fetch("https://example.com/feed.json")

    def test_a_failed_bazaar_query_does_not_abort_the_others(self):
        calls = {"n": 0}

        def fetcher(url, data=None, auth=None):
            calls["n"] += 1
            if "mb-api" in url and calls["n"] == 1:
                raise OSError("boom")
            return json.dumps(BAZAAR if "mb-api" in url else (URLHAUS if "urlhaus" in url else THREATFOX)).encode()

        malware, _ = ff.build_abusech("k", fetcher=fetcher)
        self.assertIn("a" * 64, malware["hashes"])


if __name__ == "__main__":
    unittest.main()
