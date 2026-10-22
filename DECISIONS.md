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

## 2026-10-21 / Rules are JSON with a six operator condition language over per package facts
`rules.json` holds id, severity, bucket, plain language reason and remediation, the evidence kinds that back the finding, and a `when` tree of eq, in, gt, not, all, any. `Facts.kt` flattens the evidence into one map per package and is the only place that knows the record types; adding a fact is a code change, adding or tuning a rule is a data change. Conditions are parsed at load so a typo fails at startup, not mid scan.

## 2026-10-21 / Expected findings per fixture are committed golden files
`GoldenFindingsTest` compares the engine output for all 16 fixtures to `src/test/resources/expected/*.json` byte for byte. `-Pcpi.updateGolden=true` regenerates them; the diff is reviewed by eye and the review is the acceptance step. First review 2026-10-21: every file matched its scenario's intent.

## 2026-10-21 / IOC snapshot: MVT public index, 19 STIX2 sources, stdlib fetch, test only id never ships
`tools/fetch_iocs.py` reads only the pinned index and only raw.githubusercontent.com sources named by it, extracts app ids, domains (including url hosts), SHA-1 and SHA-256 signing certs and IPv4 addresses, drops `com.example.ioc.match`, and writes `assets/iocs/snapshot.json` plus `SNAPSHOT.md`. First snapshot 2026-10-21: 680 packages, 6,548 domains, 497 certs, 73 IPs. Certs in the feed are mostly SHA-1, so the Apps adapter now hashes signing certs both ways. In app refresh is BACKLOG; the guardrail's single pinned URL stays reserved for it.

## 2026-10-21 / Two rules beyond the original table
`control_on_unknown_package` (a grant on a package the inventory does not list: medium, needs a professional, because OEMs hide packages and so does malware) and `ioc_domain_contact` (high). Both are in INITIAL_INSTRUCTIONS.md now.

## 2026-10-21 / Verdict: compromised iff any high; NOT_CHECKED checks are named under the verdict line
The verdict word is strictly `clean` or `compromised` (the brief asks for one line). A check that did not run is listed right under it as "This verdict does not cover: ...", so a clean verdict never silently covers a check that was skipped.

## 2026-10-21 / Rules carry a priority; the top finding supplies the single next step
Equal severity findings sorted alphabetically put "turn off the accessibility service" ahead of "preserve the phone as evidence" for the stalkerware fixture. IOC and hidden stalkerware rules now have priorities 10 to 30; everything else 100. The first finding after sorting by severity then priority supplies the report's single most important next step.

## 2026-10-21 / IOC matches name the list that published them
The MVT index merges sources with very different meanings: Amnesty's Pegasus and Predator domains, and the community "Stalkerware Indicators of Compromise" list, which also names dual use tools such as remote access and family monitoring apps. The snapshot now records which list names each package and the ioc_match reason says "listed by: ...". 648 of the 680 package ids come from the stalkerware list. The reader, not the tool, judges a dual use match; the remediation still says preserve evidence and ask a professional.

## 2026-10-21 / Fixture clock constant corrected
The generator's COLLECTED_AT was 2026-10-19T12:00Z, two days off the intended 2026-10-21T12:00Z. Corrected; all fixtures, expected findings and golden reports regenerated. No test depended on the wrong value.

## 2026-10-21 / Export: MediaStore Downloads plus a share sheet intent, never a network send
Report and evidence JSON are written to Download/CyberPhoneInspector via MediaStore (no storage permission on API 29+) and offered through the system share sheet from the app's cache via FileProvider. The app itself never transmits either file.

## 2026-10-21 / First real report on Rick's phone (counts only recorded here)
45 findings: 2 high (both `ioc_match`, both Play Store installs with launcher icons, both named by the stalkerware community list, one also by signing cert), 4 medium (boot and background heavy 1, device admin without profile 1, sideloaded with sensitive 1, unexpected grant off store 1), 39 low (37 unexpected grants on store installs, 2 sideloaded without sensitive grants). Verdict: compromised. The report is in the phone's Downloads for Rick to read; no package names were written to the repo, Open Brain, Obsidian, or this log. The 37 lows confirm the Phase 6 decision to keep store install oddities low.

## 2026-10-21 / Known dual use tools (Rick's call after reading the first real report)
Rick confirmed both IOC matches on his phone are apps he installed himself and knows. Handling:
1. An IOC match stays high on first sight. The tool cannot know who installed an app, and a dual use remote access or monitoring tool installed by someone else is exactly the stalkerware case. Downgrading on install source would blind the check.
2. The report names the list ("listed by: Stalkerware Indicators of Compromise") and says in the same sentence that this list also carries dual use tools, so the reader can judge. Done in Phase 9.
3. v1.1 (BACKLOG): an "I installed this myself" acknowledgement per app, stored only in the app's own data, that turns the finding into a medium "known dual use tool, review its access" note on later scans and lists it under Worth removing with that wording. Acknowledgements never suppress a finding entirely and are listed in the report's "What this scan touched" section so a reviewer sees them.
4. Never a global allowlist of "safe" dual use apps in the repo: that is the attacker's shopping list.

## 2026-10-21 / Scan UI: a state machine in a ViewModel, screens as pure functions of state
`ScanViewModel` owns the scan job (idempotent start, structured cancel, Failed on exception) so rotation never restarts a scan; the scanner is an injected suspend lambda so the state machine is JVM tested. Four screens (Consent on first run, Home, Scanning, Report) are composables of state only. No navigation library is needed for a four state flow; the dependency stays out.

## 2026-10-21 / Foreground service during the scan, special use type
`ScanService` keeps the process foreground while the view model runs the pipeline. Declared with `foregroundServiceType="specialUse"` and the required subtype property; `startForeground` passes the type on API 34+ and omits it below. Lint's ForegroundServiceType detector does not recognise special use and is suppressed at the call site with the reason. Notification permission is requested before the first scan; denial does not block the scan, the notification is simply hidden.

## 2026-10-21 / Consent explainer once, stored in the app's own preferences
The first run explains usage access and the future local VPN before anything is asked. The flag lives in app private SharedPreferences, the only state the app persists apart from exports the user requests.

## 2026-10-21 / The tool reports itself
The emulator report lists com.cyberrick.phoneinspector as a sideloaded app (installed by adb). Left in on purpose: the tool must not exempt itself from its own rules, and a reader should see it.

## 2026-10-21 / Network monitor: DNS only tunnel with transaction id rewriting
The VpnService routes one /32 (its own resolver address) and sets itself as the DNS server, so the phone stays on its normal path for everything except DNS. Each query gets a fresh upstream id so two apps sharing an id cannot collide; the response is restored to the client's id and written back. Attribution uses `getConnectionOwnerUid` on the client's source tuple, which works because the app is the active VPN. Pure engine (`DnsRelay`) with 21 JVM tests over hand assembled packets; the service is a read loop around it.

## 2026-10-21 / DNS over TLS probes are answered with a TCP reset
In automatic private DNS mode the resolver probes port 853 on the tunnel resolver. A SYN gets an immediate RST+ACK so the probe fails in milliseconds and the resolver falls back to plain DNS instead of waiting for a timeout. Emulator: 2 resets per run, median query latency 12 ms, phone stayed online.

## 2026-10-21 / Strict private DNS refuses the monitor on a configured server name, not on the active flag
Strict mode whose TLS validation has not succeeded yet is still strict: Android hard fails DNS rather than fall back, so the monitor would take the phone's DNS down. The policy keys on `privateDnsServerName` alone. First emulator test used the active flag and let the monitor start under strict mode; corrected test first.

## 2026-10-21 / A refused start still calls startForeground
`startForegroundService` obliges the service to call `startForeground` before stopping, or Android kills the app with ForegroundServiceDidNotStartInTimeException about ten seconds later. The refusal path now goes foreground, publishes the Refused state, and stops cleanly. Caught on the emulator by the crash buffer.

## 2026-10-21 / Apps with their own encrypted DNS bypass the monitor's name view
Chrome on the emulator looked up Google service names through the system resolver but resolved the three sites I opened through its built in secure DNS, so those never crossed the tunnel. Documented in the report's "cannot see" footer. Their bytes still count in the per app totals from NetworkStatsManager.

## 2026-10-21 / Monitor results are handed to the next scan through an app private file
`FlowStore` writes `monitor_latest.json` atomically; a scan within 24 hours includes its flows and reports the Network check as CHECKED with the window and lookup count, otherwise NOT CHECKED. The monitor also stops when the app's task is removed or the VPN is revoked.

## 2026-10-21 / Rule tuning from the first full report (supersedes part of the known dual use entry)
Rick's first full report (phone, with the monitor) carried two highs, both parental control apps he installed and knows, plus 39 lows that were foreground location or camera on ordinary store apps and Samsung's own apps. Changes, all test first, goldens regenerated and reviewed:
1. `ioc_match_visible_store` (medium, Worth removing): an indicator match on an app that is store installed, has a launcher icon, and is not disguised. Covertness is what separates stalkerware from an openly installed monitoring app; the upstream list types every entry "stalkerware" and offers no parental control category, so the evidence has to carry the distinction. The reason still names the list and the remediation says: if you did not install it, treat as high. `ioc_match` (high) now fires only when the app is hidden, disguised, or not from a store. This replaces point 1 of the known dual use entry; point 3 (per app acknowledgement) stays in BACKLOG as a refinement, no longer a necessity.
2. `unexpected_grant_store` counts only microphone and background location. Foreground location and camera on a store app are the norm (store locators, barcode scanning, ride apps); flagging them produced most of the 39 lows.
3. `com.samsung.`, `com.sec.android.` and `com.google.android.` are first party prefixes in the permission expectations: the phone's own recorder, camera, calendar and health apps are expected to hold what they hold.
4. Every rule has a `summary`; the three lists use it. Low findings render as one line with the five facts inline; medium and high keep the full block. The report for the stalkerware fixture went from four repeated paragraphs per list entry to one line each.
No package names from the report are recorded here; the categories above are enough to reproduce the decision.

## 2026-10-22 / Network rules: known is a suffix list, beacons are regularity, raw IP is bytes without lookups
`assets/known_services.json` holds 146 domain suffixes (platform vendors, big apps, CDNs, analytics, carriers). Known means recognised, never endorsed; unrecognized means not on the list, and the rule text says so and never says "malicious" (asserted by a test). A beacon is five or more lookups of one unrecognized host whose gaps vary by under 15 percent around their mean; regular polling of a known host is ignored. Raw IP suspect is over 1 MB sent with zero lookups in the window. An unrecognized host alone is low; with another oddity on the same app it is medium, using an `anyOddity` fact rather than cross rule lookups.

## 2026-10-22 / The Network section lists every app's hosts, flagged or not
"Where it sends data" is answered for every app seen by the monitor, grouped as known and unrecognized, with any beacon interval and byte total. This is the part of the brief a reader most wants to eyeball, so it does not depend on a rule firing.

## 2026-10-22 / Clean fixture carries a jittered series
Six WhatsApp lookups with a deterministic jitter pattern (gap variation about 0.4) sit in the clean fixture so the non beacon case is in a golden report, next to the ten lookup, 60 second series in the beacon fixture.

## 2026-10-22 / Release signing: keystore outside the repo, secrets only in .env
`app/build.gradle.kts` reads the four `CPI_RELEASE_*` names from the environment or the gitignored `.env`; missing values leave the release unsigned rather than failing the build. The keystore was generated with the bundled JDK's keytool into `~/.cpi-release/` with a random password that exists only in `.env`. Never printed, never committed.

## 2026-10-22 / R8 on release; serialization keep rules
Release is minified and resource shrunk: 29 MB debug became 1.8 MB release, well under the 15 MB gate. `proguard-rules.pro` keeps the generated serializers and companions for the app's @Serializable classes; the instrumented run on the release APK (scan through the UI) is the proof that nothing needed at runtime was stripped.

## 2026-10-22 / The release permission list carries one self declared marker
`apkanalyzer manifest permissions` prints the seven allowlisted platform permissions plus `com.cyberrick.phoneinspector.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, an app private permission AndroidX core declares for its own unexported receivers. It grants nothing and is not a platform permission; GuardrailTest keeps checking the source manifest's uses-permission list.

## 2026-10-22 / Instrumented tests take the test device's own consents through the instrumentation shell
On an emulator the instrumentation grants usage access and notifications with `appops` and `pm grant` as the shell user. The VPN consent cannot be granted that way, so the monitor test runs only where consent already exists and skips otherwise (`Assume`). None of this applies to a user's phone.

## 2026-10-22 / Listed domain contact follows the same visibility rule as a listed package
Rick's smoke scan showed one high: a visible, store installed family app contacting its own server, whose domain sits on the same published list as its package id. The package rule had already learned that an openly installed listed app is medium; the domain rule had not, so the same app was medium in Apps and high in Network. `ioc_domain_contact` now fires high only when the contacting app is hidden, disguised or not from a store; `ioc_domain_contact_visible_store` is medium with the same remediation as the package case. The report still names the domain under "Where it sends data".

## 2026-10-22 / v1.1 planning: four phases, no review pauses except where Rick's hands are needed
Rick found eight 30 to 90 minute phases excessive and asked for the work to be finished without checkpoints. Compressed to four (A removal and feeds, B collectors and rules, C screens, D refresh and release). The pauses that remain are physical: the abuse.ch secret, a look at the screenshots, the smoke run.

## 2026-10-22 / Ship the data, not the key: a public feeds repo with the abuse.ch secret in its Action
Guild members will clone, build and test the app, and a per person abuse.ch key is the wrong shape. The lists (CC0 from abuse.ch, attributed Exodus and MVT data) live in a public data only repository, `cyber-phone-inspector-feeds`, refreshed weekly by a GitHub Action that holds the only key as a repository secret. The app and every tester refresh from that repo's raw address without credentials; the code repo can stay private and later be pushed to the guild organisation as a new public repo (GitHub cannot make a fork of a private repo public).

## 2026-10-22 / Removal is one file, one intent, and Android's own dialog
`actions/RemoveAction.kt` is the only file that may name the uninstall intent (`ACTION_DELETE` with a `package:` URI and `EXTRA_RETURN_RESULT`); GuardrailTest fails the build on any other occurrence. `RemovalFlow` is a pure state machine: a system app is NotRemovable; an active device admin, accessibility service or notification listener routes to NeedsSettings with the list page Android exposes and a step sentence naming the app; a cancelled dialog is StillInstalled with the reason; a package that is gone after the dialog is Gone. Verified on the API 36 emulator with a planted package (cancel, confirm, system app). `REQUEST_DELETE_PACKAGES` is the eighth allowlisted permission.

## 2026-10-22 / Feeds live in a public data repo and in the app's assets
`tools/fetch_feeds.py` (stdlib, pinned addresses only) builds four lists: spyware (MVT index, unchanged logic), trackers (Exodus, 428 with code signatures), malware hashes and malicious hosts (abuse.ch, written as skipped until the Action secret `ABUSECH_AUTH_KEY` exists). The public repo `CyberRick-AI/cyber-phone-inspector-feeds` holds the lists and a weekly Action; the app bundles the same files under `assets/feeds/` and (Phase D) refreshes from the repo's raw address. The first Action run failed because `python -I` does not put the script folder on `sys.path`; fixed in the script, second run green.

## 2026-10-22 / The DEX string pool is read in one forward pass, never the whole DEX
The first phone run of the tracker scan died with OutOfMemoryError: reading each classes.dex into a byte array (10 to 16 MB each, dozens per big app) filled the 256 MB heap faster than the collector freed it. `DexStrings` now reads a stream: header, string id table, then each string's data in ascending offset order, which is how d8 and dx lay the pool out. Peak memory is the id table (a few hundred KB). A pool whose offsets go backwards is reported as unreadable rather than guessed at. The planned 50 MB size cap is gone because nothing is held; the per app time budget stays.

## 2026-10-22 / Adware heavy is a threat only outside a store; inside a store it is a note
The spec said five or more advertising trackers is medium. On Rick's phone that fired on 33 store installed apps, mostly games with 16 to 18 ad mediation kits, which put 44 mediums on a clean phone and the safety score at 0. That is not what a person needs to hear. `adware_heavy` (medium, label Adware heavy) now needs heavy ads or a profiling tracker *and* an install from outside any store; `adware_heavy_store` (low note, "Adware heavy: N advertising trackers inside") covers store apps at ten or more; everything else with trackers is `trackers_note`. After the change the same phone reads 0 high, the same mediums as v1, and the ad heavy games sit in the lows where they belong. Counts only; no package names recorded.

## 2026-10-22 / Hash and tracker results are cached per package version
A cold scan of 303 non system apps hashed and scanned every APK (several GB of games) in about two minutes. `AppScanCache` keeps the SHA-256 and the tracker list keyed by package, version code and last update time, in private storage; the hash is reused while the package is unchanged, the tracker list only while the tracker catalog date is the same, and an app that ran out of budget is never cached so it gets another try. The uncached packages are split across four workers because the scan is CPU bound (inflate and parse). A rescan on the same phone reads the inventory, the few changed apps and the budget apps only.

## 2026-10-22 / Keep stores the severity it accepted; a high is never kept away by default
`KeepStore.keep(package, ruleIds, worst)` records the worst severity the person accepted (default medium). `KeepTransform` collapses only findings whose rule is in the kept list and whose severity is not above that, so a kept medium that becomes high comes back in full, and a new rule on a kept app shows in full. The kept note is a low in the Safe list with the date, and the report's "what this scan touched" names every kept app.

## 2026-10-22 / High remediations say data theft first, help second
Every high rule shares one remediation: remove it, then protect your accounts (passwords from another device, two step sign in, statements), with the stalkerware line kept as the last sentence for the person who believes someone they know installed it. The golden reports regenerated once for this; the renderer test that looked for "Preserve the phone as evidence" now looks for "protect your accounts".

## 2026-10-22 / The screens are a state machine in the view model, not a navigation graph
Phase C keeps one `ScanViewModel` holding the scan state, the screen on top (Home, Consent, Result, Detail, After, Kept, Report), the removal state per package and the kept count. The activity only launches what Android must show (the uninstall confirmation, a Settings page, the VPN consent) and reports back. Rotation keeps the place because the view model survives it (checked on the API 36 emulator). Every screen is a plain composable with callbacks, so the flow is tested on the JVM with a fake scanner: open and back, remove through the dialog, cancel, remove all highs one dialog after another, the two step admin path, keep and un keep.

## 2026-10-22 / Keep and un keep re render instantly; removal rescans
`ScanResult` now carries the raw findings and the base "touched" lines, so a keep re applies the kept list, re renders the report and recomputes the score without a rescan (`ScanPipeline.rekeep`). A removal does rescan, because the package list is the truth, and the After screen shows when that rescan finishes with no high left. The "X is gone" line survives its own rescan and a scan the person starts clears it.

## 2026-10-22 / The device admin step opens the AOSP Device admin apps list, with the Security page as fallback
Android has no public action for the Device admin apps list, and on API 36 `ACTION_ADD_DEVICE_ADMIN` for an already active admin returns at once. The explicit AOSP Settings component (`Settings$DeviceAdminSettingsActivity`) is tried first and resolved before use; where it does not exist the public Security page opens and the step text names the app. Verified on the API 36 emulator with a planted hidden admin app: deactivate on its page, back, the Uninstall step unlocks, Android's dialog, gone, After screen.

## 2026-10-22 / Four of the v1 strings became plurals; the unused v1 strings are gone
Lint (fatal) flagged "%d threats", "%d apps", "%d lookups" and "%d check" as plural candidates and 28 v1 strings as unused once the screens were replaced. Plurals it is, and the old strings were deleted rather than suppressed.

## 2026-10-22 / The in app refresh is one host, one path, parse and size checked, atomic
`feeds/FeedRefresher` downloads the four lists from the feeds repository's raw address and nowhere else; `HttpsFetcher` refuses any address that is not HTTPS on that host before a socket opens (GuardrailTest already pins the string). A download must parse as its own list (the spyware and tracker lists must be non empty) and must not be less than half the size of the copy it replaces, so a truncated or empty file is refused and the previous file kept; the replace is a temp file plus rename. Verified on the JVM with a fake repository and on the S24 Ultra against the live repository ("Lists are up to date").

## 2026-10-22 / The weekly refresh is a JobScheduler job, off by default, not persisted
WorkManager would be a new dependency and `setPersisted` needs the boot permission, which is not on the eight permission allowlist. The job (unmetered network, every seven days) is scheduled while the switch is on and scheduled again on every launch, so a reboot delays it until the app is next opened. Good enough for lists that change weekly.

## 2026-10-22 / The spyware list comes from the feeds folder too
`feeds/spyware.json` has the same shape as the v1 `iocs/snapshot.json`, so the pipeline reads it through `FeedFiles` (refreshed copy first, then the bundled feed, then the v1 snapshot). The golden tests keep using the v1 snapshot so a feed refresh never moves a golden.

## 2026-10-22 / A time based unit test became a counted one
The tracker budget test used a real one millisecond budget; with two emulators running it tripped on the tiny app too. It now injects a clock that advances one millisecond per look, so the budget trips by count. Any test that must trip a timeout should count, not wait.
