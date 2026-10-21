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
