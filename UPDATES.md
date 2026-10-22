# UPDATES.md — Cyber Phone Inspector

State log. One dated entry per phase: what ran, what was verified, what failed and how it was refactored, what is queued next, surprises.

## 2026-10-21 — Phase 1: Scaffold and guardrails (branch phase-01-scaffold)

What ran
- `git init` on main, planning docs committed first, `.gitignore` verified with `git check-ignore` before the first `git add`.
- Gradle project: Gradle 8.14 (wrapper generated offline from the cached distribution), AGP 8.13.1, Kotlin 2.2.20, Compose BOM 2025.10.00, minSdk 29, compileSdk/targetSdk 36, build tools 36.1.0, bundled JBR 21 with JVM target 17.
- Placeholder Compose activity, manifest with exactly the six allowlisted permissions, data extraction and backup rules that exclude all app data.
- `GuardrailTest` (3 tests): manifest permission allowlist, forbidden write symbols, single pinned URL. `verifyDependencyAllowlist` Gradle task wired into `check`.
- ktlint with `.editorconfig`, Android Lint with warnings as errors; two upgrade notice checks disabled (see DECISIONS.md).
- `tools/` uv project (Python 3.13, stdlib only), `api_usage.jsonl` (empty), README, BACKLOG.md.

What was verified (validation gate)
- RED: all three guardrail tests failed for the intended reason with a planted `PackageInstaller` symbol, a planted stray URL, and an empty manifest. GREEN after removing the plant and declaring the allowlist. Extra permission plant (`RECORD_AUDIO`) failed the test naming the unexpected entry; reverted.
- `./gradlew check assembleDebug` green: 3 unit tests, lint clean, ktlint clean, dependency allowlist OK over 101 resolved components.
- Debug APK installed on the API 36.1 emulator, launched, placeholder screen captured, `dumpsys package` shows exactly six declared permissions.

What failed and how it was fixed
- Wrapper launcher could not find Java (no system JDK) → README now exports `JAVA_HOME` to the bundled JBR.
- `jvmToolchain(17)` needed a JDK 17 that does not exist → replaced with an explicit JVM 17 target on JDK 21.
- Allowlist task was not configuration cache safe → takes the resolution root as a Provider and walks the graph.
- Lint promoted "newer version available" to errors → those two checks disabled, versions stay pinned by policy.
- Lint required explicit backup rules → added, excluding everything.

Queued next
- Phase 2: API probe on Rick's real phone (USB debugging on) plus an API 29 emulator image.

Surprises
- Debug APK is 29 MB; the release size gate (under 15 MB) is a Phase 13 concern with minify on.

## 2026-10-21 — Phase 2: API probe on real hardware (branch phase-02-probe)

What ran
- Debug only `ProbeActivity` + `ProbeVpnService` (never in release). `ProbeReport` renders a sanitized table; `ProbeResult` rejects notes that look like package ids at construction (5 JVM tests, written first).
- API 29 system image downloaded, `CPI_API29` AVD created. Probes run on API 29 and API 36.1 emulators and on Rick's Galaxy S24 Ultra (Android 16).
- Usage access and VPN consent on the phone were tapped by Rick. The tool opened the usage access Settings screen and showed the system VPN dialog; it sent no taps.

What was verified (validation gate)
- Phone: 800 packages (> 100), 392 with a known installer, admins list returned (2), accessibility setting readable, 2,436 usage events in 24 h after consent, VpnService.prepare returned an intent, tun opened in 20 to 30 ms and closed, no tun interface left (`ip link` over adb).
- Emulators: all rows populated on both API levels; VPN consent exercised on both; attribution resolved for a foreign uid on both.
- GuardrailTest extended to every source set manifest; `./gradlew check` green with the probe code (9 unit tests).

What failed and how it was fixed
- Phone invisible to the Mac: charge only cable, then Samsung mass storage USB mode, then a sandboxed adb server. Fixed in turn; documented in DECISIONS.md.
- `getConnectionOwnerUid` returned INVALID_UID for own sockets (by design) and for the shell uid on Samsung; a real app peer resolved. Design for Phase 11 unchanged.
- `NetworkOnMainThreadException` on the socket probe; probes moved to Dispatchers.IO.
- Wi Fi traffic buckets empty on mobile data; mobile probe added.

Queued next
- Rick's decision on `ACCESS_NETWORK_STATE` (DECISIONS.md). Then Phase 3: evidence model and fixture corpus.

Surprises
- Samsung exposes a Smart Switch CD image over USB by default; adb only appears after choosing Transferring files.
- Private DNS automatic mode on the phone makes the Phase 11 fallback check mandatory on this exact device.

## 2026-10-21 — Phase 3: Evidence model and fixture corpus (branch phase-03-evidence-model)

What ran
- `core/model/Records.kt` and `Evidence.kt`: five record types with provenance, `CheckResult`, `DeviceInfo`, the `Evidence` envelope, `EvidenceParseError`, and a loader that flags duplicate package ids.
- `tools/make_fixture.py` (stdlib only, TOML scenarios) with 5 unittest cases; 16 scenarios under `tools/scenarios/`; 16 generated fixtures under `app/src/test/resources/fixtures/`.
- `EvidenceModelTest` (10 tests, written first and seen failing on unresolved references).
- Housekeeping: `ACCESS_NETWORK_STATE` landed in its own commit before this phase (Rick's decision); three compiler warnings from Phase 2 code cleared.

What was verified (validation gate)
- 16 fixtures load and round trip losslessly; serialization deterministic.
- 2,000 package fixture parses well under 500 ms (steady state) on the JVM.
- Duplicate package fixture: both records preserved, both `duplicate=true`, all others false.
- String timestamp → `EvidenceParseError` naming `bad_timestamp.json` and `firstInstallAt`; malformed JSON names the file.
- Unknown fields at top level and inside a record are ignored.
- `make_fixture.py --check` passes against the committed fixtures; `./gradlew check` green (19 unit tests, lint, ktlint, dependency allowlist).

What failed and how it was fixed
- Generator used Python `hash()` for synthetic cert ids (randomized per process) → sha256.
- Shell quoting of scenario text broke → scenarios written by a Python script instead.
- Generator printed a path relative to the project root even for a temp dir → guarded.

Queued next
- Phases 4 to 7 (collectors) may run as parallel sessions; they share only this model and touch separate files.

Surprises
- None in the model. The fixture corpus took more design than the model: the scenarios are the spec the rule engine will be judged against.

## 2026-10-21 — Phase 4: Apps collector (branch phase-04-apps-collector)

What ran
- `collect/AppsCollector.kt` (pure, over `PackageSource`), `AndroidPackageSource.kt` (PackageManager adapter, parallel per package work), `SystemPackages.kt` + `assets/system_packages.json` (111 packages that legitimately carry system style names), `CollectorResult`.
- `AppsCollectorTest` (14 tests, written first) with `FakePackageSource`; `SystemPackagesTest`.
- Debug probe hook `--es collector apps` runs the real collector on a device and records counts and timing only.

What was verified (validation gate)
- Hidden stalkerware test: exactly one hidden record, the planted one. Clean test: 0 hidden, 0 disguised.
- Real phone: collector count 800 equals `pm list packages` 800 exactly; system 497 equals `pm list packages -s` 497. Delta zero.
- Cold run 2.0 to 3.0 s for 800 packages on the S24 Ultra (gate: under 3 s for 400).
- Per package installer failure keeps the record as UNKNOWN; an inventory failure yields NOT_CHECKED with the reason.
- 2,000 fake packages collect under 500 ms on the JVM.

What failed and how it was fixed
- First phone run 6.4 s → adapter parallelized (2.0 to 3.0 s).
- 44 packages read as SIDELOADED were WebAPKs, carrier preloads and Good Lock modules → WEB_APP source added, installers added to the vendor list.
- 13 icon less non system packages on a clean phone → severity table amended so hidden alone, or hidden on a store install, is never high.
- A formatter rewrap made a patch miss its anchor; caught by the compile error.
- Process slip: the Phase 4 merge ran although ktlint had failed on a file name rule, because the commit chain was not gated on the check result. Fixed in a follow up merge within minutes; commit chains are now gated on BUILD SUCCESSFUL.

Queued next
- Phase 5: Deep control collector.

Surprises
- Chrome is the installer of record for installed web apps.
- Carrier preload agents (LogiaDeck) install dozens of packages that look sideloaded to a naive check.

## 2026-10-21 — Phase 5: Deep control collector (branch phase-05-control-collector)

What ran
- `collect/ControlCollector.kt` (pure, over `ControlSource`), `AndroidControlSource.kt`, probe hook `--es collector control`.
- `ControlCollectorTest` (10 tests, written first) with `FakeControlSource`.

What was verified (validation gate)
- Work profile case: one PROFILE_OWNER record plus the agent's DEVICE_ADMIN record, correct component expansion.
- Accessibility stalkerware case: one ACCESSIBILITY record on the planted package with install time as the bound.
- Malformed settings ("::garbage-no-slash:com.a/.X::", ":"): one parsed record, PARTIAL with "1 unparseable", no throw. Null admins and empty settings: no records, CHECKED.
- Real phone: 2 DEVICE_ADMIN, 3 NOTIFICATION_LISTENER, 0 ACCESSIBILITY, 0 owners, all 5 with an install time bound, 32 ms. Matches the Phase 2 probe counts.
- Full `./gradlew check` green before commit (gated).

What failed and how it was fixed
- Nothing failed in this phase.

Queued next
- Phase 6: Permissions collector and need heuristic.

## 2026-10-21 — Phase 6: Permissions collector and need heuristic (branch phase-06-permissions-collector)

What ran
- `collect/PermissionsCollector.kt` (pure, over `PermissionSource`), `PermissionExpectations.kt` + `assets/permission_expectations.json`, `AndroidPermissionSource.kt`, probe hook `--es collector permissions`.
- `PermissionsCollectorTest` (12 tests, written first).

What was verified (validation gate)
- Maps app with background location: granted, expected. Calculator with mic: granted, unexpected. Zero requested permissions: four records, none granted. Requested but denied: never unexpected. Null flags: not granted, uncertain. System apps never unexpected. Label never consulted (an app named like a maps app with no category is unexpected).
- Real phone: 800 apps, 3,200 records, 0 uncertain, 465 ms. Granted counts: location 109, background 28, mic 64, camera 85. Unexpected: 27, 2, 7, 15.
- Full `./gradlew check` green before commit (gated).

What failed and how it was fixed
- Nothing failed. The unexpected counts on a clean phone drove a severity table amendment (DECISIONS.md).

Queued next
- Phase 7: Persistence collector.

## 2026-10-21 — Phase 7: Persistence collector (branch phase-07-persistence-collector)

What ran
- `collect/PersistenceCollector.kt` (pure, over `PersistenceSource`), `AndroidPersistenceSource.kt` (boot receivers, RECEIVE_BOOT_COMPLETED, usage events), probe hook `--es collector persistence`.
- `PersistenceCollectorTest` (14 tests, written first) including the 2026-11-01 US fall back interval and overlapping services.

What was verified (validation gate)
- DST: a 3 hour run across the fall back transition sums to 180 minutes. Unmatched STOP dropped and counted. START before the window clipped. START still running counts until now. Beacon app: boot receiver and backgroundHeavy both true; music app with 19 h is not heavy; clean case has none. Usage access denied → NOT_CHECKED with the Settings action in the reason.
- Real phone: 800 records, 441 boot receivers, 12 apps with foreground service time, 1 over 6 h (about 67 h in 7 d, non system, outside always on categories), 5 dropped events, 441 ms.
- Full `./gradlew check` green before commit (gated).

What failed and how it was fixed
- First phone run reported 50 dropped events; a 7 day lookback raised it to 71, proving they were not window edges. Root cause: per package START/STOP pairing breaks when one app runs two services at once. Replaced with a running counter (test first: 30 vs expected 50 minutes, then green). Dropped events on the phone: 5.
- A test fake edit missed its anchor after formatting; replaced with a regex anchored edit.

Queued next
- Phase 8: rule engine, severity table, IOC matching. Inputs from the collectors: hidden store installs are common (13), unexpected grants on a clean phone are common (24), one genuinely heavy background app exists on Rick's phone.

## 2026-10-21 — Phase 8: Rule engine, severity table, IOC matching (branch phase-08-rule-engine)

What ran
- `rules/Condition.kt` (eq, in, gt, not, all, any), `Finding.kt`, `RuleSet.kt`, `Facts.kt`, `RuleEngine.kt`, `IocMatcher.kt`; `assets/rules.json` (18 rules); `assets/iocs/snapshot.json` + `SNAPSHOT.md`.
- `tools/fetch_iocs.py` (stdlib, pinned index only) with 6 unittest cases; `ConditionTest` (6), `RuleEngineTest` (19), `IocMatcherTest` (4), `GoldenFindingsTest` (5), all written first.
- Apps adapter now emits SHA-1 as well as SHA-256 cert hashes to match the feed.

What was verified (validation gate)
- The MVT feed was fetched and tallied before the script was written: 19 sources, 11,632 indicators.
- Golden findings for all 16 fixtures reviewed by eye and committed; 0 findings above low on the two clean fixtures and on the maps fixture; highs on the stalkerware and IOC fixtures.
- Snapshot loads offline with a date; the shipped snapshot never contains the test only id; the fetch script refuses any index but the pinned one (exit 2) and any host but raw.githubusercontent.com.
- 2,000 package fixture evaluates under 1 s.
- Full `./gradlew check` green before commit (gated).

What failed and how it was fixed
- `Condition.Any` shadowed `kotlin.Any` across the package → renamed to AnyOf and AllOf.
- A shell glob with no match aborted a multi step command before the golden test was written; the next run showed "no tests found". Re-applied.

Queued next
- Phase 9: report renderer in the brief's exact contract, including NOT_CHECKED handling and the verdict rule.

## 2026-10-21 — Phase 9: Report renderer and export (branch phase-09-report)

What ran
- `report/ReportRenderer.kt` (pure, fixed order: header, what the scan touched, five check sections with the five fields per flag, verdict, three lists, single next step, cannot see footer), `Verdict.kt`, `ReportInput.kt`, `ReportExporter.kt` (MediaStore Downloads + share intent via FileProvider), `scan/ScanPipeline.kt` (collectors → engine → renderer on a device).
- Rules gained `check` and `priority`; `Finding` carries both. IOC snapshot gained per package source attribution; `ioc_match` reason names the list.
- `ReportRendererTest` (13), `VerdictTest` (3), all written first; golden REPORT.md for all 16 fixtures reviewed and committed; probe hook `--es collector scan` runs the whole pipeline and exports.

What was verified (validation gate)
- Golden reports byte identical for 16 fixtures. The brief's END WITH block appears in order; each flag has the five fields in order.
- Clean fixture: `VERDICT: clean`, "- none" in both removal lists, "Nothing to do" next step. Medium only fixture: clean with Worth removing populated. Stalkerware fixture: compromised, next step "preserve the phone as evidence".
- NOT_CHECKED fixture: section reads "NOT CHECKED: Usage access not granted" and the verdict says it does not cover Persistence.
- Emulator: export landed in Download/CyberPhoneInspector (3.5 KB report, 690 KB evidence) and was read back by the shell user, a different app.
- Real phone: full pipeline 1.5 to 6.6 s, 30 KB report exported to Downloads; counts in DECISIONS.md.
- Full `./gradlew check` green before commit (gated).

What failed and how it was fixed
- Fixture clock two days off → generator constant corrected, everything regenerated.
- Next step chose the alphabetically first high rule → rule priority added.
- IOC matches on the real phone came from a list that includes dual use tools → snapshot and reason now name the list, so the reader can judge.
- Three patches missed their anchors after ktlint formatting → re-applied by regex or by file tail; noted as a working pattern: format first, then patch.

Queued next
- Phase 10: scan UI. Rick to read the first real report on the phone and tune rules if the two IOC matches are dual use tools he installed himself.

## 2026-10-21 — Phase 10: Scan UI (branch phase-10-scan-ui)

What ran
- `ScanPipeline` made suspend with the four collectors in parallel coroutines and per check progress; `ScanResult`; `ScanViewModel` + `ScanState`; `ScanService` (special use foreground type); `MainActivity` with Consent, Home, Scanning, Report and Failed screens; strings; FileProvider and service in the manifest.
- `ScanViewModelTest` (6 tests, written first) with a fake scanner on a test dispatcher.
- Recorded Rick's decision on known dual use tools (DECISIONS.md, BACKLOG.md).

What was verified (validation gate, API 36.1 emulator, driven by uiautomator)
- First run shows the consent explainer before anything is asked; Continue leads to Home.
- Full scan from tap to the verdict heading: about 2 s (gate: under 20 s), one `ScanService start` in logcat.
- Rotation 0.4 s into a scan: the report still appeared and logcat shows exactly one service start, so the scan did not restart.
- Usage access denied: Home says "not granted" with an "Open usage access settings" button; the report section reads NOT CHECKED and the verdict says it does not cover Persistence.
- Notification permission: both Allow and Don't allow lead to exactly one scan.
- Every clickable has a label on itself or a descendant (0 missing) on Consent, Home and Report screens.
- No ScanService entry remains after the scan.
- Full `./gradlew check` green before commit (gated); 129 unit tests.

What failed and how it was fixed
- The uiautomator helper matched "Allow" inside the dialog's message text and tapped the message; exact match first fixed the driver, not the app.
- A zsh variable holding "python3 path" did not word split; replaced with a shell function.
- Lint: special use type constant needs an API 34 guard; counts string rephrased to avoid a plural candidate; preferences edit via KTX; ForegroundServiceType false positive suppressed with reason.

Queued next
- Phase 11: DNS tunnel network monitor. Rick to try the UI build on his phone.

## 2026-10-21 — Phase 11: DNS tunnel network monitor (branch phase-11-dns-tunnel)

What ran
- Pure: `DnsMessage` (compression, bounded pointers, A/AAAA/CNAME), `IpPacket` (IPv4/UDP/TCP parse and build with checksums, TCP reset), `DnsRelay` (id rewriting, pending expiry, counters, FlowRecords), `NetStatsCollector`, `FlowStore`, `PrivateDnsPolicy`, `MonitorWindow`. 27 JVM tests written first.
- Android: `DnsTunnelService` (VpnService, special use foreground, bounded window, stops on task removal and revoke), `AndroidUidResolver`, `AndroidNetStatsSource`, `MonitorState`, Home screen monitor section with the system VPN consent, `ScanPipeline` reads the latest monitor result, probe hooks for headless control.

What was verified (validation gate, API 36.1 emulator)
- Monitor started from the UI through the system consent dialog; tun present while running; 17 lookups relayed in a 36 s window; all 17 attributed to a uid and a package across 3 apps; 9 distinct hosts; median query latency 12 ms (gate: 200 ms).
- Two DNS over TLS probes answered with resets; name resolution kept working under automatic private DNS.
- Stop from the UI: no tun interface remains; "Last run" shown; result saved and read by the next scan.
- Strict private DNS (hostname mode): refused with the Private DNS message in the UI, no tun, app alive; monitor runs again after switching back to automatic.
- Malformed and foreign packets dropped with counters (8 on the emulator, IPv6 and ICMP noise), never a throw.
- Full `./gradlew check` green before commit (gated).

What failed and how it was fixed
- Refusal path crashed the app ten seconds later (ForegroundServiceDidNotStartInTimeException) → refusal now goes foreground first, then stops.
- Strict mode slipped through when validation had not happened → policy keys on the configured server name.
- Chrome resolved the test sites with its own secure DNS, so the "3 sites in the browser" case could not be observed through the tunnel; other apps' lookups were. Documented as a limitation in the report footer.
- Two patches missed anchors after formatting; re-applied by position.

Real phone (Galaxy S24 Ultra, private DNS automatic), started by Rick from the app
- Full 600 s window, 65 lookups, all 65 attributed to a uid and package, 6 apps, 48 distinct hosts, median 51 ms, 2 DoT probes reset, 11 per uid byte counters, 12 malformed or foreign packets dropped, no crash, no ANR. The next scan reported the Network check as CHECKED and showed a host for a device admin app.

Queued next
- Rule tuning from the first full report (see DECISIONS.md), then Phase 12 network rules.

## 2026-10-21 — Rule tuning from the first full report (branch rules-tuning-first-report)

What ran
- rules.json: summaries on all 19 rules, `ioc_match_visible_store` (medium) split from `ioc_match` (high), store grant rule limited to microphone and background location; permission expectations gained Samsung and Google first party prefixes; `Facts` gained `visibleStoreInstall`; `Finding` gained `summary`; renderer compacts low findings to one line and uses summaries in the three lists.
- 8 new or changed tests, written first; goldens regenerated and reviewed.

What was verified
- Visible store IOC match is medium; hidden or non store stays high; cert match follows the same rule.
- Foreground location or camera on a store app yields nothing; microphone or background location still yields low.
- Samsung first party packages are expected by the bundled asset.
- Low findings carry their five facts on one line; medium and high keep the full block; lists use summaries.
- Full `./gradlew check` green (gated); rescan of Rick's phone recorded below as counts.
- Rescan of Rick's phone after tuning: 13 findings, verdict clean. 0 high; 6 medium (2 visible store IOC matches, 1 device admin without profile, 1 boot and background heavy, 1 sideloaded with a sensitive grant, 1 unexpected grant off store); 7 low (5 odd microphone or background location on store apps, 2 sideloaded without grants). Before tuning: 45 findings, 2 high, verdict compromised.

Queued next
- Phase 12: network rules (known services, raw IP, beacon detection) over the monitor's flows.

## 2026-10-22 — Phase 12: Network rules and report integration (branch phase-12-network-rules)

What ran
- `rules/KnownServices.kt` + `assets/known_services.json` (146 suffixes), `rules/NetworkFacts.kt` (host split, beacon detection, raw IP), network facts in `Facts`, four network rules in rules.json with host, interval and byte placeholders, per app host listing in the report's Network section, known services wired through the pipeline.
- `NetworkFactsTest` (6), 5 new `RuleEngineTest` cases, 2 new `ReportRendererTest` cases, written first; goldens regenerated and reviewed.

What was verified (validation gate)
- Beacon fixture: one beacon finding at 60 s; jittered series (variation 0.4) in the clean fixture: none; fewer than five lookups: none; regular lookups to a known host: none.
- Clean fixture hosts resolve 100 percent known; "no DNS, bytes sent" fixture: exactly one raw IP finding.
- Unrecognized alone is low; with another oddity medium; rule wording never contains "malicious".
- Full `./gradlew check` green before commit (gated); 177 unit tests. Phone rescan counts below.

What failed and how it was fixed
- The two golden test engines were built without the known services list, so every host read as unrecognized and clean fixtures grew low findings; caught by the goldens, fixed by wiring the list.
- My own rule text said "not malicious"; the wording test rejected it; reworded to "not a judgment".
- Three signature patches missed formatter anchors; patched by regex.
- Phone rescan: the phone was disconnected when the build finished; Rick runs the next scan from the app itself (the build is on the phone from the Phase 11 install plus the tuning install; the Network section appears after any scan that follows a monitor run within 24 h).

Queued next
- Phase 13: instrumented tests on API 29 and 36.1, smoke checklist, signed release APK.

## 2026-10-22 — Phase 13: Instrumented tests, smoke checklist, signed release (branch phase-13-release)

What ran
- `app/src/androidTest`: `ConsentAndScanTest` (consent then Home, full scan to verdict under 20 s, denied usage access reported NOT CHECKED), `PipelineAndExportTest` (five check statuses and a verdict, export readable through the content resolver, share intent with two streams), `MonitorTest` (start, run, stop, saved result; skips where VPN consent is absent), `TestShell` (shell grants on the test device only).
- Release signing from `.env` (keystore generated outside the repo with the bundled keytool), R8 minify and resource shrink with serialization keep rules, `SMOKE.md`, README release and smoke sections, `dist/app-release.apk` (ignored).

What was verified (validation gate)
- `connectedDebugAndroidTest`: 6 of 6 on the API 36.1 emulator and 6 of 6 on the API 29 emulator, including the monitor test on both (consent existed from earlier runs).
- Release APK: 1.8 MB (gate 15 MB); `apksigner` verifies the project certificate; `apkanalyzer manifest permissions` prints the seven allowlisted permissions plus AndroidX's self declared receiver marker; no ProbeActivity; all assets bundled; min 29, target 36.
- Release APK installed on the API 36 emulator after removing the debug build: consent, scan to verdict in 2.3 s, no crash, so R8 kept everything the runtime needs.
- Self traffic: the phone's 10 minute monitor attributed 1 byte to the app itself; the app has no outbound call of its own.
- Phone rescan with the Phase 12 build: 16 findings, verdict clean, 0 high; network rules added 2 mediums (unrecognized hosts on apps with another oddity) and 1 low.
- `./gradlew check` green before commit (gated).

What failed and how it was fixed
- Instrumented tests did not compile: Truth was JVM test only → added to the androidTest classpath.
- A release APK cannot install over a debug build (different signing key); documented in README and SMOKE.md: uninstall the debug build first, which clears consents.

Queued next
- Rick runs SMOKE.md on the phone with `dist/app-release.apk` (uninstall the debug build first). Then Phase 14: docs, memory save.

## 2026-10-22 — Smoke run on the Galaxy S24 Ultra (release 0.1.0) and one rule fix

- Rick uninstalled the debug build and installed `dist/app-release.apk`. One launcher entry. First run, usage access, scans, save, share and the 10 minute monitor passed. Teardown read from adb: no tun interface, private DNS automatic, usage access granted.
- The smoke scan showed one high: a visible store installed family app contacting its own server, whose domain is on the same published list as its package. The domain rule now follows the package visibility rule (DECISIONS.md 2026-10-22). Rescan: clean, mediums and lows only.
- Skipped on the phone, covered by the emulator and instrumented tests: rotation mid scan, strict private DNS refusal, revoke usage access rescan.

## 2026-10-22 — Phase 14: Docs and memory (branch phase-14-docs-memory)

What ran
- README rewritten for a reader starting from a clean checkout: what it checks, what it cannot see, build, release, instrumented tests, smoke, usage, privacy, guardrails, helper scripts, document map.
- UPDATES and DECISIONS complete through the smoke run; BACKLOG holds everything deferred.
- Obsidian folder `Brain/Cyber Phone Inspector/` filled from the web-project template; Open Brain thoughts captured; SOP .docx generated.

What was verified (validation gate)
- A fresh clone in a temp directory builds and tests with only the README's commands (recorded below).
- No `{{placeholders}}` left in the Obsidian notes; every wikilink resolves; emoji filenames, tags and tables per the style rules.
- Open Brain holds the project summary, decisions, locations, lessons and next steps.

Final state
- 14 of 14 phases merged to main. 178 unit tests, 6 instrumented tests on API 29 and API 36, release 0.1.0 signed and installed on Rick's phone, verdict clean.
- Clean clone gate: a fresh clone in a temp directory built `assembleDebug` and ran 178 unit tests green with only the README's three commands.
- Obsidian: 9 notes filled, 0 placeholders, every wikilink resolves. Open Brain: 4 thoughts captured (summary and locations, decisions, hardware lessons, working method and next steps). SOP: `/Users/cyberrick/Documents/AI/Cyber Phone Inspector/Cyber Phone Inspector-SOP-v1.docx`.

## 2026-10-22 — GitHub remote
- Private repository created at https://github.com/CyberRick-AI/cyber-phone-inspector and `main` pushed (43 commits). Tracked files were checked for secret files, password values and the phone serial before the push: none present.

## 2026-10-22 — v1.1 Phase A: removal, eighth permission, feeds (branch phase-a-removal-feeds)

What ran
- `actions/RemovalFlow.kt` (pure state machine, blockers, step text), `SettingsLinks.kt` (targets with app info fallback), `RemoveAction.kt` (the single file allowed to name the uninstall intent). `REQUEST_DELETE_PACKAGES` added to manifest, PROJECT.md, INITIAL_INSTRUCTIONS.md and GuardrailTest in one change; GuardrailTest gained "the uninstall intent appears only in RemoveAction".
- `tools/fetch_feeds.py` + tests; `assets/feeds/` (spyware.json, trackers.json, malware_hashes.json, malicious_hosts.json, FEEDS.md); public repo `cyber-phone-inspector-feeds` with the weekly Action; the guardrail's allowed URL list now includes the feeds repo and Exodus.
- Debug probe hook `--es remove <package>` to drive the flow on a device.

What was verified (validation gate)
- Planted `ACTION_UNINSTALL_PACKAGE` in another file fails GuardrailTest naming it; reverted; green again.
- API 36 emulator with a planted package: Cancel reads StillInstalled with the reason; Uninstall reads Gone and the package is gone; a system package reads NotRemovable and no dialog is shown.
- Device admin two step path: covered by the RemovalFlow unit tests (NeedsSettings, afterSettings) and the resolver fallback test; no admin holder was available on the emulator, so the live walk is left to Phase C's instrumented run with the app's own test admin receiver.
- Feeds: live run without a key gives 680 spyware packages and 428 trackers with the abuse.ch lists marked skipped; the Action runs green on the public repo; the raw address serves the lists without credentials; `./gradlew check` green (186 unit tests).

What failed and how it was fixed
- The Action's first run: `python -I` omits the script folder from sys.path → explicit sys.path insert.
- The emulator dialog's button is "Uninstall", not "OK"; the driver was corrected, not the app.
- Lint: KTX `toUri`, API 30 guard on the notification listener extra.

Queued next
- Phase B: hash and tracker collectors, rules v1.1, score, keep. Rick: add `ABUSECH_AUTH_KEY` as an Actions secret on the feeds repo when you have it; nothing waits on it.
