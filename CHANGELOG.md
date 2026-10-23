# Changelog

All notable changes. Dates are release dates. The project follows semantic versioning from 0.2.0.

## 0.2.1 — 2026-10-23
- Keeping an already kept app again adds its new findings instead of only offering to stop keeping it.
- The bundled threat lists are the first fully keyed set: 1,941 malware hashes, 3,204 malicious domains, 1,086 IPs.
- Licensed GPL-3.0-or-later; NOTICE and in app credits.
- Published to the AI Builders Guild organisation.

## 0.2.0 — 2026-10-22
- New screens: safety score ring, headline by app count, one card per flagged app with a plain label, detail with four facts, Remove through Android's own uninstall dialog (two step path for admin, accessibility and notification holders), After screen with a protect yourself checklist, Kept apps, Lists.
- Local threat intelligence: SHA-256 of every non system APK against MalwareBazaar, a streaming scan of each app's code against the Exodus tracker signatures, URLhaus and ThreatFox hosts, alongside the MVT spyware list. All on the phone, cached per app version.
- Safety score (apps, not findings; capped at 90 when a check could not run).
- Keep it, I know this app: a dated note that returns on a stronger finding.
- In app list refresh from the public feeds repository, with parse and size checks and an optional weekly schedule.
- Rules gained labels and card text; high remediations say data theft first.

## 0.1.0 — 2026-10-22
- First release: five read only checks (apps, deep control, permissions, persistence, network), the data driven rule engine, the MVT indicator snapshot, the DNS only network monitor, the plain language report with a verdict, three lists and one next step.
