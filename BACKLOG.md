# BACKLOG.md — out of scope for v1, not implemented

- Full packet capture with userspace TCP/UDP forwarding (tun2socks style) for non DNS raw IP flows.
- Claude narrative pass via a proxy backend (no API key may ship in the APK).
- Google Play listing and the QUERY_ALL_PACKAGES declaration form.
- iOS companion via sysdiagnose import plus Network Extension.
- Scheduled rescans and change diffs between reports.
- In app refresh of the indicator snapshot. Needs YAML index and STIX2 parsing on device; v1 ships a snapshot refreshed by tools/fetch_iocs.py at build time.
- v1.1: per app "I installed this myself" acknowledgement for IOC matches (DECISIONS.md 2026-10-21, known dual use tools). Local only, never suppresses, always listed in the report.
