# ROADMAP.md — Cyber Phone Inspector (Android)

Pinned (do not let these drift):
- Boundary: Claude Code builds the app, rules, fixtures, tests. The app collects, flags, renders, deterministically, on device, read only. Rick grants consents, runs scans, removes things, and makes the final call on "Needs a professional". iOS out of scope.
- Quality bar: every flag traces to an evidence record; severity from the rule table; zero false "compromised" on the clean fixtures; the app never writes to another app or setting.
- Recovery vocabulary: two strikes on a chunk → refactor (split, rescope, or restart upstream), never a third blind retry. Three consecutive failures → BLOCKED.md.

Each phase: 30 to 90 min, own feature branch `phase-NN-slug`, tests green before commit, UPDATES.md entry, DECISIONS.md entries for anything non obvious. Validation gates name concrete values, not "tests pass".

Dependency map: 1 → 2 → 3 → (4, 5, 6, 7 in parallel) → 8 → 9 → 10 → 11 → 12 → 13 → 14. Phases 4 to 7 share only the evidence model from Phase 3 and touch separate files, so they may run as parallel sessions.

---

## Phase 1 — Scaffold and guardrails
Goal: an empty app that builds, installs, and already enforces the read only and privacy rules mechanically.
Deliverables: `git init` on `main` with first commit; Gradle project (version catalog, Kotlin, Compose, minSdk 29, compileSdk/targetSdk 36, build tools 36.1.0, JDK 21 from Android Studio via `gradle.properties`); package `com.cyberrick.phoneinspector`; `app/src/test/.../GuardrailTest.kt`; `api_usage.jsonl` (empty), `UPDATES.md`, `BACKLOG.md`, `README.md` stub; `tools/` with `uv` project for helper scripts.
Acceptance criteria: `./gradlew assembleDebug test` green from a clean checkout following README. Manifest declares exactly the permission allowlist from PROJECT.md. GuardrailTest fails the build if any forbidden symbol (`PackageInstaller`, `DevicePolicyManager.set`, `Settings.Secure.putString`, `Settings.Global.putString`, `ACTION_DELETE`, `ACTION_UNINSTALL_PACKAGE`, `killBackgroundProcesses`) appears in `app/src/main`, if the manifest holds a permission outside the allowlist, or if any `http://` or non pinned `https://` literal appears. Dependency allowlist test: every artifact in the lockfile is on the list in INITIAL_INSTRUCTIONS.md.
Dependencies: none.
Validation gate: the three guardrail tests are proven to fail by temporarily adding one forbidden symbol, one extra permission, one stray URL (each reverted); `assembleDebug` installs on the API 36.1 emulator and shows a placeholder screen; `git log` shows the phase commit on `phase-01-scaffold` merged to main; `.gitignore` verified before the first `git add`.
Failure modes: AGP / Kotlin / Compose version mismatch with the installed Android Studio → pin to the versions Android Studio's new project wizard generates today, log them. Gradle cannot find Java → `org.gradle.java.home` to the JBR path in PROJECT.md.

## Phase 2 — API probe on real hardware
Goal: verify every data source in PROJECT.md actually returns usable data on Rick's phone and on the emulator before writing collectors.
Deliverables: `app/src/main/.../probe/ProbeActivity.kt` behind a debug only launcher entry; `PROBE_RESULTS.md` (sanitized: counts and field presence, never package lists from the real phone); DECISIONS.md entries per API; API 29 emulator image downloaded and an AVD created.
Acceptance criteria: for each row of the PROJECT.md data source table, PROBE_RESULTS.md records: API called, Android version tested, returned count or null, whether the needed field was populated, elapsed ms. Both the real phone and API 29 and API 36.1 emulators are covered. Usage access and VPN consent dialogs are exercised once each by Rick and the app detects grant state correctly.
Dependencies: Phase 1; Rick's phone connected, USB debugging on.
Validation gate: `getInstalledPackages` count on the phone > 100; `getInstallSourceInfo` returns a non null installer for at least one Play app; `getActiveAdmins` returns a list (empty is fine); `ENABLED_ACCESSIBILITY_SERVICES` readable; `queryEvents` returns > 0 events for the last 24 h after usage access is granted; `VpnService.prepare` returns an intent, and after consent a tun fd opens and closes cleanly with no lingering VPN icon. Any row that fails on the real phone is rewritten in PROJECT.md as a documented limitation, not silently skipped.
Failure modes: OEM hides packages → record OEM and count delta vs Settings app list. Usage access denied → ProbeActivity must show the exact Settings deep link, not crash.

## Phase 3 — Evidence model and fixture corpus
Goal: one typed evidence model every collector writes to and every rule reads from, plus the synthetic test corpus.
Deliverables: `core/model/` (`AppRecord`, `ControlRecord`, `PermissionRecord`, `PersistenceRecord`, `FlowRecord`, `Evidence` envelope with `source`, `api`, `collectedAt` UTC, `deviceInfo`); kotlinx.serialization JSON; `app/src/test/resources/fixtures/*.json` with at least 12 scenarios named in INITIAL_INSTRUCTIONS.md; `tools/make_fixture.py` that generates fixtures from a YAML scenario spec.
Acceptance criteria: round trip serialize/deserialize is lossless for every fixture; every timestamp is epoch millis UTC and the model rejects a naive local time string at the boundary; empty, single element, duplicate package, and 2,000 package fixtures exist; unknown JSON fields are ignored, not fatal.
Dependencies: Phase 2 (field availability informs the model).
Validation gate: 12 fixtures load; the 2,000 package fixture deserializes under 500 ms on JVM; a fixture with a duplicate package id loads with both records preserved and flagged `duplicate=true`; a malformed fixture yields an `EvidenceParseError` naming the file and field, not a stack trace.
Failure modes: model churn as collectors land → additive fields only after this phase, logged.

## Phase 4 — Apps collector
Goal: full installed app inventory with publisher, install source, install time, hidden launcher, and disguised system detection.
Deliverables: `collect/AppsCollector.kt`; bundled `assets/system_packages.json` (AOSP core packages plus Google, Samsung, Pixel known lists); unit tests over a fake `PackageManager`.
Acceptance criteria: every record has package id, label, version, first/last install UTC, installer package (or `unknown`), signing cert SHA 256, `isSystem`, `hasLauncherIcon`, `enabled`. An app with `MAIN/LAUNCHER` absent and not `isSystem` is marked `hidden=true`. A user installed app whose label matches a system style name (regex list: "system", "update", "sync", "service", "settings", "security", case insensitive) but whose package is not on the system list is marked `disguised=true`.
Dependencies: Phase 3.
Validation gate: on the "hidden stalkerware" fixture, exactly 1 app is hidden and it is the planted one; on the "clean phone" fixture, 0 hidden, 0 disguised; on the real phone (manual), the count equals Settings → Apps count within ±3 and the delta is explained in UPDATES.md; collector completes under 3 s for 400 packages on the phone.
Failure modes: `getInstallSourceInfo` throws for uninstalled-but-present packages → catch `NameNotFoundException` per package, record `unknown`, continue; never abort the inventory on one package.

## Phase 5 — Deep control collector
Goal: who holds device admin, owner, work profile, accessibility, and notification listener power.
Deliverables: `collect/ControlCollector.kt`; tests.
Acceptance criteria: `ControlRecord` per grant with `kind` (admin, deviceOwner, profileOwner, accessibility, notificationListener), package, and `sinceAtLatest` = that package's `firstInstallTime` (documented bound). Accessibility entries parsed from the colon separated `ENABLED_ACCESSIBILITY_SERVICES` string and cross checked against `AccessibilityManager`.
Dependencies: Phase 3.
Validation gate: "legit work profile" fixture → 1 profileOwner record, severity later resolves to low; "accessibility stalkerware" fixture → 1 accessibility record on a hidden app; a malformed settings string (`"::"`, trailing colon, unknown component) parses to an empty or partial list with a logged warning, never a crash.
Failure modes: `getActiveAdmins` returns null on some OEMs → treat null as empty and log.

## Phase 6 — Permissions collector and need heuristic
Goal: which apps hold location (foreground and background), mic, camera, and whether that looks justified.
Deliverables: `collect/PermissionsCollector.kt`; `assets/permission_expectations.json` (category → expected permissions); tests.
Acceptance criteria: per app, the four sensitive groups with `requested`, `granted`, `background` (for location), from `requestedPermissionsFlags` only; `unexpected=true` when granted and the app's `ApplicationInfo.category` (or package family) is not in the expectation list. Wording in the record says `granted`, never `used`.
Dependencies: Phase 3.
Validation gate: "maps app always on location" fixture → location background granted, `unexpected=false`; "calculator with mic" fixture → mic granted, `unexpected=true`; an app with zero requested permissions yields a record, not an absence; flags are never inferred from an app's own declared label text.
Failure modes: `requestedPermissionsFlags` null on API 29 for some packages → treat as not granted and mark `uncertain=true`.

## Phase 7 — Persistence collector
Goal: what relaunches on boot and what runs constantly.
Deliverables: `collect/PersistenceCollector.kt`; tests with synthetic `UsageEvents`.
Acceptance criteria: per app: `bootReceiver=true` if a receiver resolves for `BOOT_COMPLETED` or the permission is requested; `foregroundServiceMinutes7d` and `lastForegroundServiceStart` computed from `queryEvents` FOREGROUND_SERVICE_START/STOP pairs over the last 7 days, UTC; `backgroundHeavy=true` when foreground service time > 6 h in 7 d and the app is not in the expected always on category list (music, VPN, fitness, messaging).
Dependencies: Phase 3.
Validation gate: a synthetic event stream spanning a DST transition (US fall back 2026-11-01) sums minutes correctly; unmatched STOP without START is ignored and counted in a `droppedEvents` counter; "beacon app" fixture → bootReceiver and backgroundHeavy both true; clean fixture → 0 backgroundHeavy.
Failure modes: usage access not granted → collector returns `status=NOT_CHECKED` with the Settings deep link; the rule engine must treat NOT_CHECKED as unknown, never as clean.

## Phase 8 — Rule engine, severity table, IOC matching
Goal: turn evidence into findings with a severity, a reason, and a pointer back to the evidence.
Deliverables: `rules/RuleEngine.kt`, `rules/rules.json` (id, condition, severity, plain language reason, remediation bucket); `rules/IocMatcher.kt`; `tools/fetch_iocs.py` that downloads the MVT STIX2 bundle and writes a pinned snapshot `assets/iocs/{packages,domains,certs}.json` plus `assets/iocs/SNAPSHOT.md` with date and source commit; tests.
Acceptance criteria: every finding carries `ruleId`, `severity` (low, medium, high), `evidenceRef` (record id), `bucket` (safe, worthRemoving, needsProfessional). Severity table in INITIAL_INSTRUCTIONS.md is implemented exactly. IOC package or cert hit → high, needsProfessional. Rules are data, the engine is generic.
Dependencies: Phases 4 to 7.
Validation gate: across the 12 fixtures, the expected findings file (`fixtures/expected/*.json`) matches exactly, per fixture, with 0 unexpected findings on the two clean fixtures; a fixture whose only oddity is a Play installed maps app with background location yields 0 medium or high; IOC snapshot loads offline with a date no older than the build date; the IOC fetch script runs under `uv run` and refuses any URL but the pinned one.
Failure modes: rule condition language gets too clever → keep conditions to a small expression set (equals, in list, gt, and, or); anything needing code is a named Kotlin predicate referenced by id.

## Phase 9 — Report renderer and export
Goal: REPORT.md in the exact contract from the brief, plus a JSON evidence export.
Deliverables: `report/ReportRenderer.kt`, `report/templates/report.md.kts` (or string template); export via `FileProvider` and the share sheet; tests with golden files.
Acceptance criteria: output order is fixed: header (device, Android version, scan time UTC and local, snapshot date), "What this scan touched", per check sections with each flag as: what it is, what it can reach, how long it has been there (`since at latest <date>`), where it sends data (hosts or "no network observed"), severity; then the verdict line (`VERDICT: clean` or `VERDICT: compromised`), the three lists, the single next step, and the fixed "What this scan cannot see" footer. Verdict is `compromised` only when at least one high finding exists; medium only findings yield `clean` with the "Worth removing" list populated.
Dependencies: Phase 8.
Validation gate: golden REPORT.md for all 12 fixtures byte identical to committed expected output; a NOT_CHECKED section renders the words `NOT CHECKED` and the reason; the report for the clean fixture contains the string `VERDICT: clean` and empty "Worth removing" and "Needs a professional" lists; exported file lands in the app's Downloads via share sheet on the emulator and is readable by another app.
Failure modes: template drift from the brief → the brief's END WITH block is embedded as a test string and asserted present in order.

## Phase 10 — Scan UI
Goal: a plain, accessible Compose UI: one Start Scan button, progress per check, report view, export.
Deliverables: `ui/` screens (Home, Consent explainer, Scanning, Report); foreground service for the scan with a progress notification; TalkBack labels on every control.
Acceptance criteria: a first run explains the two consents in plain language before asking; scan runs the collectors in parallel coroutines with structured cancellation; the report screen shows the verdict line first; export button shares REPORT.md and evidence JSON; no network UI yet.
Dependencies: Phase 9.
Validation gate: full scan on the API 36.1 emulator from tap to report under 20 s; rotating the device mid scan does not restart the scan; denying usage access shows the explainer with the Settings deep link and the report says NOT CHECKED for persistence; accessibility scanner reports 0 missing content descriptions.
Failure modes: foreground service type crash on API 34+ → `FOREGROUND_SERVICE_SPECIAL_USE` with the `<property>` subtype declared, verified on the API 36.1 emulator.

## Phase 11 — Network monitor (DNS tunnel plus per app traffic counters)
Goal: see where apps send data, with app attribution, without root and without a full userspace TCP stack.
Deliverables: `net/DnsTunnelService.kt` (VpnService with a single `/32` route to an internal resolver address, `addDnsServer` pointing at it, UDP relay to the real upstream via a protected socket, owner uid via `getConnectionOwnerUid`); `net/NetStatsCollector.kt` (`NetworkStatsManager` per uid bytes for the monitor window); `FlowRecord`s with app, hostname queried, answer IPs, timestamp; tests with recorded DNS packets.
Acceptance criteria: while the monitor runs, the phone keeps normal connectivity (only DNS transits the tunnel); every DNS query is attributed to a uid and then a package; Private DNS strict mode is detected from `LinkProperties` and the monitor refuses to start with a plain language explanation instead of breaking DNS; tunnel is torn down on stop, on app exit, and on process death (verified by the VPN key icon disappearing); window default 10 min, max 60.
Dependencies: Phase 10.
Validation gate: on the emulator, opening a browser to three sites produces three FlowRecords attributed to the browser uid; a DNS answer is returned to the client within 200 ms median over the tunnel; after stop, `ConnectivityManager` reports no VPN transport; a malformed DNS packet (truncated header, label loop) is dropped with a counter, not a crash; monitor survives a 10 min run on the real phone with no ANR.
Failure modes: Private DNS automatic mode probes DoT on the fake resolver → confirm fallback to plain DNS happens on the emulator and the phone; if it does not, log it and show the user how to set Private DNS off for the scan duration (they do it, the app never does). Two strikes on the relay → rescope to DNS capture via `addDnsServer` only (observe, no answer rewrite) before anything bigger.

## Phase 12 — Network rules and report integration
Goal: classify observed hosts and traffic into known, unrecognized, raw IP only, odd port, and fixed schedule beacon.
Deliverables: `assets/known_services.json` (Google, Apple, Meta, Microsoft, Amazon, Cloudflare, Akamai, Fastly, major CDNs, carrier); `rules/network_rules.json`; beacon detector (inter arrival coefficient of variation < 0.15 over ≥ 5 queries to the same host); "bytes sent but no DNS seen" detector from NetStats vs FlowRecords; report Network section.
Acceptance criteria: each app in the report's Network section lists hosts grouped as known / unrecognized, raw IP indicators, beacon intervals; an unrecognized host is medium only when combined with another finding on the same app, otherwise low; an app with > 1 MB sent in the window and zero DNS queries is flagged `rawIpSuspect` medium.
Dependencies: Phase 11.
Validation gate: synthetic flow fixture with a 60 s beacon to a non allowlisted host → one beacon finding with interval 60 ± 2 s; a jittered stream (CV 0.4) → no beacon finding; the clean fixture's hosts resolve 100 percent to known; the "no DNS, bytes sent" fixture → exactly one rawIpSuspect.
Failure modes: allowlist too small → unknown is labelled "unrecognized" in the report, never "malicious"; wording asserted in a test.

## Phase 13 — Instrumented tests, real phone smoke, release build
Goal: prove it on real hardware and produce an installable signed APK.
Deliverables: `connectedCheck` suite on API 29 and API 36.1 AVDs; `SMOKE.md` manual checklist (Rick runs it); release signing from a local keystore whose path and passwords come from `.env` (never committed); `app-release.apk` in `dist/` (gitignored).
Acceptance criteria: instrumented tests cover: consent flows, a full scan, export, monitor start/stop; smoke run on Rick's phone completes the full scan plus a 10 min monitor and produces a report Rick agrees is plausible; release APK installs over the debug build cleanly.
Dependencies: Phase 12.
Validation gate: `connectedCheck` green on both AVDs; SMOKE.md every box ticked with date and phone model; release APK size under 15 MB; `apkanalyzer manifest permissions` prints exactly the allowlist; no `INTERNET` traffic observed from the app in a 10 min monitor of itself unless Refresh indicators is toggled.
Failure modes: flaky emulator tests → quarantine list with a reason and a BACKLOG entry, never a retry loop in CI.

## Phase 14 — Docs, memory, handoff
Goal: a stranger can build, install, and run it from README alone, and the project is recallable later.
Deliverables: README (setup from clean checkout, consent explainer, limitations), UPDATES.md final entry, DECISIONS.md complete, `/memory-save` run (Obsidian folder filled, Open Brain thoughts captured).
Acceptance criteria: README steps followed verbatim on a clean clone produce a working debug build; limitations section lists everything from PROJECT.md "cannot see"; no `{{placeholders}}` left in Obsidian.
Dependencies: Phase 13.
Validation gate: a second clean clone in a temp dir builds with only README; Obsidian folder passes the style rules in the framework (emoji filenames, wikilinks, tags, tables); Open Brain holds 3 to 5 project thoughts.
Failure modes: none expected; if README diverges from reality, fix README, not the reader.

---

## BACKLOG seeds (not in v1)
- Full packet capture with userspace TCP/UDP forwarding (tun2socks style) for non DNS raw IP flows.
- Claude narrative pass via a proxy backend.
- Google Play listing and the `QUERY_ALL_PACKAGES` declaration.
- iOS companion via sysdiagnose import.
- Scheduled rescans and change diffs between reports.

---

# v1.1 — Consumer UI, removal, local threat intelligence

Design: `docs/superpowers/specs/2026-10-22-v1.1-consumer-ui-design.md` (approved by Rick 2026-10-22). Same rules as v1: 30 to 90 minutes per phase, feature branch `phase-NN-slug`, tests written first, `./gradlew check` green before commit (gated), UPDATES.md entry, DECISIONS.md for anything non obvious, validation gates name values.

Pinned additions to the boundary: the app may launch Android's uninstall confirmation and open Settings pages; it never removes or changes anything itself. All threat lists are checked on the phone; the phone sends nothing. The abuse.ch key lives only in `.env` on the Mac; the build and the app work without it.

Dependency map: 15 → 16 → 17 → 18 → 19 → 20 → 21 → 22. Phase 16 and Phase 19 share nothing and may run in parallel sessions; everything else is sequential because each phase changes the model or the rules the next one renders.

## Phase 15 — Removal action and the eighth permission
Goal: the app can ask Android to uninstall a package and open the right Settings list, with the guardrails updated deliberately.
Deliverables: `REQUEST_DELETE_PACKAGES` in the manifest, PROJECT.md allowlist, INITIAL_INSTRUCTIONS.md and `GuardrailTest` (one commit); `actions/RemoveAction.kt` (the only file allowed to reference `ACTION_DELETE` / `ACTION_UNINSTALL_PACKAGE`, enforced by GuardrailTest); `actions/SettingsLinks.kt` (device admin list, accessibility list, notification access, app info page); `RemovalState` machine (requested, confirmed gone, still installed with reason); tests.
Acceptance criteria: GuardrailTest fails on the uninstall intent anywhere but `RemoveAction.kt`; `RemoveAction` launches the system dialog and, on return, reports gone or still installed by rechecking the package; `SettingsLinks` resolves each target to an intent that exists on the device or falls back to the app info page.
Dependencies: none.
Validation gate: planted violation (uninstall intent in another file) fails the build and is reverted; on the API 36 emulator a tiny test APK installed by adb is removed through the dialog and `RemovalState` reads gone; a device admin holder (the emulator's own test admin app, or the planted APK registered as admin) reads still installed with reason "device admin active" and the two step link opens the Device admin apps list; nothing is removed without the dialog (asserted by attempting removal with the dialog dismissed).
Failure modes: OEM installer ignores the intent → fall back to the app info page and say so in the state; this is the reason for the fallback, not an afterthought.

## Phase 16 — Threat feeds: build script and snapshot v2
Goal: one script fetches every list, with or without the abuse.ch key, into a dated, verifiable local snapshot.
Deliverables: `tools/fetch_feeds.py` replacing `fetch_iocs.py` (MVT index as before; Exodus trackers; MalwareBazaar Android hashes with family and tags, URLhaus hosts and ThreatFox domains and IPs when `CPI_ABUSECH_AUTH_KEY` is set, otherwise skipped with a line in SNAPSHOT.md); `assets/feeds/` (`spyware.json`, `trackers.json`, `malware_hashes.json`, `malicious_hosts.json`, `FEEDS.md` with dates and counts); `.env.example` gains the key name; GuardrailTest's pinned URL becomes a pinned list of the open feed addresses; `--check` fails past 90 days per feed; unittest coverage with injected fetchers.
Acceptance criteria: every feed file carries `generatedAt`, `source` and counts; the test only package and hash never ship; the script refuses any host outside the pinned set; without the key the three abuse.ch files are written empty with `skipped: "no key"` and the build still passes.
Dependencies: none (parallel with 15).
Validation gate: run with and without the key; Exodus file parses to 400 or more trackers with code signatures; with the key, the malware hash file holds Android hashes only (file type apk or dex) and at least 1,000 entries, and the hosts file at least 1,000; `./gradlew check` green with the new pinned URL list; a planted extra URL fails the guardrail test and is reverted.
Failure modes: abuse.ch export format changes → the script fails loudly with the first unparsed line, never writes a partial file; the previous snapshot stays.

## Phase 17 — Hash and tracker collectors
Goal: two new read only collectors feeding the evidence model.
Deliverables: `collect/ApkHashCollector.kt` (SHA-256 of the base APK for non system apps, via the package's source path), `collect/TrackerScanCollector.kt` (DEX string pool search for Exodus code signature prefixes, per app time cap, PARTIAL with a count when the budget runs out), additive fields on `AppRecord` (`apkSha256`, `trackers: List<String>`, `scanBudgetHit`), fixtures and scenarios (`17_known_malware_hash`, `18_adware_heavy_trackers`, `19_tracker_budget_partial`), probe hook, phone run.
Acceptance criteria: hashing 300 non system apps under 10 s on the S24 Ultra; tracker scan under 20 s with the budget; both run in parallel with the existing collectors; a split APK hashes its base and says so; an unreadable APK yields `null` and continues.
Dependencies: 16 (signatures and hash set).
Validation gate: JVM tests on synthetic DEX bytes find a planted signature and miss a near miss; a 50 MB synthetic DEX trips the per app cap and the record says `scanBudgetHit`; phone probe reports counts (apps hashed, apps scanned, trackers found, budget hits) and total time; fixtures regenerate and the model round trips.
Failure modes: reading other apps' APKs blocked on some OEMs → the collector reports PARTIAL with the count of unreadable packages; the report says the check was partial.

## Phase 18 — Rules v1.1, score, keep
Goal: the engine speaks the consumer vocabulary and the score and keep logic exist as pure, tested functions.
Deliverables: new rules in `rules.json` (`known_malware_hash` high, `malicious_host_contact` high, `adware_heavy` medium, `trackers_note` low) with `label` and `cardText` on every rule; high remediations reworded per the design; `score/SafetyScore.kt` (per app, 30/10/0, cap 90 when usage access declined, no cap for the watch); `keep/KeepStore.kt` and the pre render transform; the "kept" line under What this scan touched and in Safe; updated golden findings and reports, reviewed.
Acceptance criteria: every high and medium finding across all fixtures maps to one of the labels and has a non empty `cardText` (test); score tests: clean 100, one app one high 70, one app two highs 70, mixed, kept excluded, usage declined cap 90, watch not run uncapped; keep returns on a stronger finding; verdict is computed after keep.
Dependencies: 17.
Validation gate: fixture 03 scores 60 (one app with highs, plus one medium app? state the exact expected per fixture in `expected/scores.json`); the known malware fixture is high with the family in the reason; goldens reviewed by eye and committed; wording tests pass (behavioural rules never "malicious").
Failure modes: label mapping gaps → the test lists the rule ids missing a label; no rule ships without one.

## Phase 19 — Design system and the first three screens
Goal: the dark look with the ring: Launch, Scanning, Result.
Deliverables: `ui/theme` (colours, type scale, 56 dp primary buttons), `ui/components` (ScoreRing with animated fill and grey and dotted segments, ThreatCard, Label pill), Launch screen with the consent sheet on first Scan, Scanning screen with the five checks ticking, Result screen with score, headline, cards worst first, Remove all highs, the watch card, "N apps are fine, see what was checked"; TalkBack labels everywhere.
Acceptance criteria: Result renders from a `ScanResult` plus score with no Android calls (previewable); cards show label, name, `cardText`; headline logic per the design; the watch card leads to the VPN consent; no network UI prerequisite.
Dependencies: 18 (labels, score).
Validation gate: Compose previews for the five fixture states (clean, mixed, usage declined, watch not run, many threats); emulator: fresh install to result under 15 s; uiautomator check of zero unlabelled clickables; rotation keeps state; screenshot set saved under `docs/screenshots/` for Rick's review.
Failure modes: the ring animation stutters on API 29 → fall back to a static ring below API 31.

## Phase 20 — Detail, removal flow, after removal, kept apps
Goal: the second half of the journey.
Deliverables: Detail screen (label, name, why, four facts, Remove, Keep), two step path for admin and accessibility holders (open list, re check on resume, then uninstall), silent rescan after the dialog returns, After screen with the protect yourself checklist (local state), Kept apps screen with un keep, Full report entry with Share and Save, Remove all highs as one dialog at a time.
Acceptance criteria: removing an app updates the list and the score without a manual rescan; still installed shows Android's reason and the next step; Keep removes the card, lowers nothing else, and the report shows the kept line; the checklist persists per phone.
Dependencies: 15, 19.
Validation gate: emulator: planted app removed through the dialog, card gone, score up; admin holder walks the two step path; Keep on a visible store IOC match clears it from cards and the golden report for that evidence shows the kept line; TalkBack order on Detail is label, name, why, facts, actions; SMOKE.md gains the removal and keep steps.
Failure modes: the uninstall dialog returns before the package is gone → poll the package up to 3 s before deciding still installed.

## Phase 21 — In app refresh of the open feeds
Goal: the open lists stay fresh without a release.
Deliverables: `feeds/FeedRefresher.kt` (pinned addresses only, HTTPS, verifies parse and size not smaller than the bundled copy, atomic replace into app files, dates), a Refresh entry in settings with the last refresh date and the oldest list date, an optional schedule the user picks (off by default), the result screen's oldest date line; tests with injected responses.
Acceptance criteria: a refused host never connects (asserted by the guardrail pinned list and a unit test); a bad download leaves the previous file intact; the scan uses the refreshed files on the next run.
Dependencies: 16, 19.
Validation gate: emulator refresh against the live open feeds updates the dates; a simulated truncated download is rejected; the app makes no connection when refresh is off (monitor's own counter for the app stays at bytes near zero across a 10 minute window).
Failure modes: feed too large for the phone → size cap with a clear message, previous file kept.

## Phase 22 — Instrumented tests, smoke, release 0.2.0, docs and memory
Goal: prove it on hardware and ship.
Deliverables: instrumented tests for launch to result, detail to keep, detail to removal dialog, refresh; `SMOKE.md` v1.1; `dist/app-release.apk` 0.2.0; README, UPDATES, DECISIONS, PROBE_RESULTS updated; Obsidian notes and Open Brain refreshed; SOP v2.
Acceptance criteria: `connectedCheck` green on API 29 and 36.1; smoke ticked on the S24 Ultra; release under 15 MB with the eight permission allowlist exactly; no traffic of the app's own with refresh off.
Dependencies: 20, 21.
Validation gate: every acceptance line in the design's final section has a named test or a ticked smoke box; a fresh clone builds from README; Rick's phone scores and reads as the design predicts (6 to review, nothing is stealing, or whatever the new lists add, reviewed by Rick).
Failure modes: none expected beyond flaky emulator tests, quarantined with a reason.
