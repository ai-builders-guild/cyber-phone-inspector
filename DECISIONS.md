# DECISIONS.md — Cyber Phone Inspector

Format: date / decision / rationale / alternatives considered. Claude Code appends; never rewrites earlier entries.

## 2026-10-21 / On phone Android app, not a Mac side USB tool
Rationale: Rick wants an app the owner installs and runs. Android exposes enough through PackageManager, DevicePolicyManager, Settings.Secure, UsageStatsManager and VpnService to cover all five checks from an unprivileged app.
Alternatives: Mac CLI over USB with libimobiledevice, adb and MVT (first draft, dropped); cross platform Flutter (every check is native code anyway, rejected).

## 2026-10-21 / iOS out of scope for this project
Rationale: an iOS app cannot enumerate other apps, their permissions, MDM profiles or background activity. The only legal routes are a Network Extension (traffic without app attribution) and sysdiagnose import. Partial coverage would undermine the verdict line.
Alternatives: iOS companion with Network Extension plus sysdiagnose parsing (BACKLOG).

## 2026-10-21 / No LLM in the app, zero paid APIs in v1
Rationale: an API key cannot ship inside an APK; a proxy backend is a separate project. A deterministic template keeps every sentence traceable to a finding, which the quality bar requires anyway.
Alternatives: Claude narrative pass through a proxy (BACKLOG).

## 2026-10-21 / Network monitoring via a DNS only VpnService tunnel plus NetworkStatsManager, not full packet forwarding
Rationale: a full capture VPN needs a userspace TCP/UDP forwarding stack (NetGuard style native code), which is weeks of work and the top source of connectivity bugs. Routing only the tunnel's own resolver address through the VPN and relaying UDP gives every hostname each app resolves with uid attribution, keeps the phone online, and is a few hundred lines. Per uid byte counters from NetworkStatsManager catch apps that send without resolving names.
Alternatives: tun2socks style forwarder (BACKLOG); `/proc/net` (unreadable since Android 10); root (out of scope).

## 2026-10-21 / minSdk 29
Rationale: `ConnectivityManager.getConnectionOwnerUid` and usable `queryEvents` foreground service events exist from API 29. Android 10 was released 2019; older devices are out of scope.
Alternatives: minSdk 26 with Network section NOT_CHECKED below 29 (rejected, two code paths for little gain).

## 2026-10-21 / Mechanical guardrails over policy
Rationale: the read only promise is enforced by a unit test that greps the source for installer, uninstall, settings write and process kill symbols, a manifest permission allowlist test, a dependency allowlist test, and a single pinned URL test. Policy text alone drifts.
Alternatives: code review only (rejected).

## 2026-10-21 / Severity is a data table, not code
Rationale: `rules.json` plus a tiny predicate set keeps the rules reviewable by Rick without reading Kotlin and makes the fixture expectations the spec.
Alternatives: hand written if/else per rule (rejected).

## 2026-10-21 / Universal CLAUDE.md copied from Nova C with three lines adapted for Kotlin/Gradle
Rationale: Rick's canonical guardrails; only the title and the Python venv lines changed. Source file untouched.

## 2026-10-21 / Build environment facts
Android Studio installed with bundled JBR 21.0.9; SDK platforms 34, 35, 36, 36.1; build tools 35.0.0 and 36.1.0; emulator image 36.1 only (API 29 image to be added in Phase 2); no system Java; 65 GB free. adb lives at `~/Library/Android/sdk/platform-tools/adb`, not on PATH.

## 2026-10-21 / Phase 1 toolchain pins
Gradle 8.14, AGP 8.13.1, Kotlin 2.2.20, Compose BOM 2025.10.00. Rationale: AGP and Kotlin were already in the Gradle cache and Android Studio 2025.3.2 supports them; Gradle 8.14 was the cached distribution, so the wrapper was generated offline. Alternatives: AGP 9.x (not cached, no need yet).

## 2026-10-21 / JVM target 17 on the bundled JDK 21, no toolchain provisioning
Rationale: the only JDK on the Mac is Android Studio's JBR 21. `jvmToolchain(17)` demands a real JDK 17 and toolchain auto download is off. Compile options and Kotlin `jvmTarget` 17 on JDK 21 give the same bytecode. The wrapper launcher needs `JAVA_HOME` exported (README).

## 2026-10-21 / Lint checks AndroidGradlePluginVersion and GradleDependency disabled
Rationale: with `warningsAsErrors` they turn every upstream release into a build failure. Versions are pinned deliberately (CLAUDE.md); upgrades are a decision, not a lint fix. All other lint checks stay fatal.

## 2026-10-21 / Backup and device transfer disabled for all app data
Rationale: PROJECT.md says everything stays on the phone. `dataExtractionRules` and `fullBackupContent` exclude every domain, so no scan evidence ever reaches a cloud backup or a new device.

## 2026-10-21 / ktlint official style with Composable naming exemption
Rationale: `.editorconfig` sets `ktlint_official` and exempts functions annotated `@Composable` from the lowercase rule, the Compose convention. Formatter runs via `./gradlew ktlintFormat`; check is part of `./gradlew check`.

## 2026-10-21 / Dependency allowlist is a Gradle task over the resolved release graph
Rationale: a unit test cannot see the resolved classpath. The task walks `releaseRuntimeClasspath` and fails on any external group outside `androidx.*`, `org.jetbrains*`, and the four transitive annotation/guava groups AndroidX pulls in. Baseline: 101 components, all on the list.

## 2026-10-21 / Phase 2 probe results drive the collectors (see PROBE_RESULTS.md)
Verified on API 29 and API 36 emulators and on Rick's test phone: Samsung Galaxy S24 Ultra SM-S928U, Android 16, One UI 8.5, patch 2026-09-05, verified boot green, not rooted. `getConnectionOwnerUid` resolves only other apps' flows and only while this app is the active VPN; the VPN app's own sockets are invisible to it. `getActiveAdmins` and `ENABLED_ACCESSIBILITY_SERVICES` return null for "none". `getInstallSourceInfo` needs API 30; on API 29 "no installer" means unknown, not sideloaded. Usage and network stats work with usage access on both levels. Socket work must run off the main thread.

## 2026-10-21 / ACCESS_NETWORK_STATE is required for the network check; permission allowlist change PENDING Rick
Every ConnectivityManager read throws SecurityException without it. It is a normal (install time, no dialog) permission that reveals only whether the device is on Wi Fi or mobile and whether a VPN or private DNS is active. Phase 11 needs it to detect strict private DNS before starting the tunnel and to confirm teardown. Not added: PROJECT.md says any permission outside the allowlist stops the run. Rick decides; if yes, PROJECT.md, INITIAL_INSTRUCTIONS.md and GuardrailTest change together in one commit.

## 2026-10-21 / Probe activity lives in the debug source set
Rationale: release builds never carry it; the debug manifest adds no permissions and GuardrailTest now checks every source set's manifest, not only main.

## 2026-10-21 / Traffic counters query both Wi Fi and mobile
Rationale: the phone was on mobile data during the probe and Wi Fi buckets were empty while mobile had 124. Phase 12's "bytes sent with no DNS" rule sums both per uid.

## 2026-10-21 / Attribution probe peer must be a real app, not the adb shell
Rationale: on the S24 Ultra the shell uid's loopback connection resolved to INVALID_UID while the browser's resolved correctly; emulators resolved both. The Phase 11 gate uses a real app flow through the tunnel.

## 2026-10-21 / The adb server must run outside the sandboxed shell
Rationale: an adb server started from the sandbox enumerates emulators (TCP) but not USB devices. Smoke scripts note it. Also: Samsung phones enumerate as a mass storage CD image until USB mode is set to Transferring files; no adb interface exists in that mode.

## 2026-10-21 / ACCESS_NETWORK_STATE added to the permission allowlist (Rick's decision)
Rationale: Phase 2 showed every ConnectivityManager read throws without it on API 29 and API 36. It is an install time permission with no dialog that reveals only network type and whether a VPN or private DNS is active. Enables strict private DNS detection before the tunnel starts and programmatic teardown confirmation. Changed together: PROJECT.md, INITIAL_INSTRUCTIONS.md, AndroidManifest.xml, GuardrailTest. Allowlist is now seven entries.

## 2026-10-21 / Scenario specs are TOML, not YAML
Rationale: Python 3.11+ reads TOML from the stdlib (`tomllib`); YAML would add PyYAML. CLAUDE.md says prefer stdlib. `tools/scenarios/*.toml` → `tools/make_fixture.py` → `app/src/test/resources/fixtures/*.json`. `--check` mode fails when committed fixtures drift from their scenarios.

## 2026-10-21 / Evidence model shape
One `Evidence` envelope with five record lists (apps, controls, permissions, persistence, flows) plus a `CheckResult` per check (CHECKED, PARTIAL, NOT_CHECKED with reason). Every record implements `Provenanced` (api, collectedAt epoch millis UTC). Timestamps are `Long` only; a quoted string fails at the boundary with `EvidenceParseError` naming the file and field. Unknown JSON fields are ignored so older fixtures survive additive changes. Duplicate package ids are flagged by the loader, not by collectors, so a collector cannot forget. Fields from this phase on are additive only.

## 2026-10-21 / Fixture timestamps are fixed constants
Rationale: `collectedAt` is 2026-10-21T12:00:00Z and installs start 2026-09-01T00:00:00Z, one day apart per app, so fixtures and golden reports never change between runs. The DST test in Phase 7 builds its own event stream around 2026-11-01.

## 2026-10-21 / IOC fixture uses a test only package id
`04_ioc_package_match` uses `com.example.ioc.match`. Phase 8 adds that id to a test only indicator file, never to the shipped snapshot, so the match path is tested without naming a real stalkerware package in the repo.

## 2026-10-21 / Hidden launcher is common on legitimate installs; the high rule needs install source
Phase 4 probe on Rick's Galaxy S24 Ultra: 13 non system packages have no launcher icon, 3 from Play, 5 from the Samsung store, 5 from carrier or OEM installers. Google speech, AR and device health services are Play installed, icon less, and hold mic or location. The severity table now requires a non store install source for the high "hidden" rule; store installed hidden apps with a control grant are medium. Counts only were recorded; no package names left the phone.

## 2026-10-21 / Install source: WEB_APP added, carrier and OEM module installers are VENDOR_STORE
Phase 4 probe showed 44 "sideloaded" packages on the S24 Ultra: 20 WebAPKs (installed web apps, Chrome is the installer), 20 carrier preloads via LogiaDeck, 3 Samsung Good Lock modules. None are sideloads in the brief's sense. `org.chromium.webapk.*` maps to WEB_APP; LogiaDeck, Good Lock, the Samsung preload installer and the OMC agent map to VENDOR_STORE. An APK installed through Chrome's downloads keeps SIDELOADED. Installer package ids were read from logcat and never written to a file.

## 2026-10-21 / Disguised check exempts Play Store installs
Play policy forbids impersonating system apps and names like "Sync for Reddit" are common there; the rule is for sideloaded and unknown source apps labelled System Update, Sync Service and the like.

## 2026-10-21 / Apps collector adapter does per package work in parallel
Label loading and signing cert hashing for 800 packages took 5 to 6 s cold on the S24 Ultra; a parallel stream in the adapter brings a cold run to 2 to 3 s. The collector itself stays pure and sequential.

## 2026-10-21 / Control records: one per grant kind, deduplicated by kind and component
A package holding admin, device owner and profile owner yields three records with the same component, so the rule engine can reason per kind. Accessibility is the union of the raw setting and AccessibilityManager, deduplicated. Blank entries in the colon separated settings are skipped silently; an entry without a slash is counted and surfaces as PARTIAL with the count, never a crash.

## 2026-10-21 / Component parsing mirrors ComponentName.unflattenFromString in pure Kotlin
Rationale: keeps the collector testable on the JVM without Robolectric. "pkg/.Cls" expands to "pkg/pkg.Cls".

## 2026-10-21 / Permissions: four records per app, always
Every app yields one record per sensitive group (location, background location, microphone, camera) even when nothing is requested, so "no record" can never be mistaken for "nothing granted". Granted means the REQUESTED_PERMISSION_GRANTED flag; null flags mean not granted and uncertain. The app label is never an input.

## 2026-10-21 / Need heuristic is category plus a first party family list, system apps exempt
`assets/permission_expectations.json` maps each group to the ApplicationInfo categories that plausibly need it and lists package prefixes of well known messaging, maps, camera and wearable apps that are expected regardless of category (most do not set a category). System partition apps are never "unexpected". Outside the list means the report says "looks odd for this kind of app", never "malicious". Expect false positives on store apps with no category; the rule engine treats unexpected alone as medium, and Rick reviews the first real report for tuning.

## 2026-10-21 / "Unexpected" permission alone on a store install is low, not medium
Phase 6 probe on Rick's clean S24 Ultra: 27 location, 2 background location, 7 microphone and 15 camera grants fall outside the expected categories, nearly all store apps with no category set. Twenty four mediums on a clean phone would bury the real findings. Severity table amended: medium only when the app is not from Play, a vendor store or a web app, or when combined with another finding on the same app.

## 2026-10-21 / Persistence time is summed in epoch milliseconds with a running service counter per package
START increments and STOP decrements a per package counter; time counts while the counter is above zero, so two services of one app overlapping count once. Events are fetched with a 7 day lookback before the 7 day window and clipped to the window start; a run still open at collection time runs until now; a STOP with nothing running is dropped and counted in `droppedEvents`. On Rick's phone naive per package pairing produced 71 orphans; the counter model produces 5. Because all arithmetic is epoch based, the 2026-11-01 US fall back (tested) and any other local clock change cannot distort the sum.

## 2026-10-21 / Background heavy means over 6 hours in 7 days, non system, outside always on categories
Always on categories: AUDIO, SOCIAL, ACCESSIBILITY, MAPS (music, messaging, accessibility, navigation). The threshold and list live as constants in the collector; the rule engine combines `backgroundHeavy` with `bootReceiver` for a medium, never alone.

## 2026-10-21 / Usage access missing makes the whole Persistence check NOT_CHECKED
Boot receiver facts are still available without usage access, but reporting them as a complete check would let the verdict call persistence clean on half the evidence. The reason string carries the Settings action so the UI can deep link.
