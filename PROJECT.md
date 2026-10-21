# PROJECT.md — Cyber Phone Inspector (Android app)

Project root: `/Users/cyberrick/Projects/Cyber Phone Inspector`
Build machine: macOS (Mac Mini, zsh). Android Studio + Android SDK, Kotlin, Gradle. No Python runtime in the app; helper scripts (fixture generation, IOC snapshot) use uv managed Python at `.venv/bin/python`.
Status: PLANNING. Caps below are deliberately tight. Rick loosens if needed.
Supersedes: an earlier draft of this file that planned a Mac side USB tool. That approach is dropped. iOS is out of scope for this project (see Boundary).

## What this is (Q1)

A native Android app, Cyber Phone Inspector, that the owner installs on their own phone and runs on demand. It scans in read only mode and answers one question in plain language: is anything on this phone watching me (location, messages, mic, camera) or reporting to a third party. It performs the five checks from the brief (Apps, Deep control, Permissions, Persistence, Network), then shows and exports REPORT.md in the exact output contract: one verdict line (clean or compromised), three lists (Safe / Worth removing / Needs a professional), one single most important next step. Quality bar: every flag traces to a named evidence record (package id, API that produced it, timestamp), severity comes from a rule table not guesswork, the app never modifies, uninstalls, or disables anything, and a phone with nothing wrong gets a clean verdict with zero false "compromised" calls across the test corpus.

## AI vs human boundary (Q2, pinned)

Claude Code builds the Kotlin app, the rule engine, the report renderer, the fixtures, and the tests. The app itself does collection, normalization, rule based flagging and report rendering on device, deterministically, with no LLM. Rick does: install the app, grant the two consent dialogs (usage access, VPN), run the scan, decide what to remove, and make the final "compromised" judgment when the report says "Needs a professional". iOS is explicitly out of scope for this project; if wanted later it is a separate project using the sysdiagnose import approach.

## Paid APIs this project touches

None in v1. The app ships with no API keys and makes no outbound calls by default.

| Service | Purpose | Status |
|---|---|---|
| Anthropic Claude | Plain language narrative polish | NOT in v1. An API key cannot be shipped inside an app. If wanted later it needs a proxy backend, which is its own project. Logged to BACKLOG.md. |
| MVT public STIX2 IOC feed (GitHub raw, free) | Known stalkerware / spyware package names and domains | Bundled as a pinned snapshot in `app/src/main/assets/iocs/`. Optional in app refresh is OFF by default and only fetches from the pinned GitHub URL. |
| Google Play Console ($25 one time) | Store listing | NOT in v1. v1 is a sideloaded APK installed by Rick. |

## Cost caps

| Cap | Value | Action when tripped |
|---|---|---|
| Paid API calls, whole project | 0 | Any attempt to add one writes BLOCKED.md with the justification and stops |
| Hard dollar stop, whole project | $0.00 | Same. BUDGET_EXCEEDED.md if any paid service is used |
| Image generation | 0 | Not applicable. App icon is produced by hand or a free tool; no paid gen |
| Outbound network from the app | IOC refresh only, user toggled, pinned URL | Anything else is a bug and a test failure |

`api_usage.jsonl` still exists in the repo (universal CLAUDE.md rule) and must stay empty through v1.

## Privacy and data minimization rules (non negotiable)

- Everything stays on the phone. No analytics, no crash reporting SDKs, no telemetry, no ads SDK, no third party libraries that make network calls. Dependency allowlist is enforced in Phase 1 and every new dependency is justified in DECISIONS.md.
- The app never reads message contents, contacts, photos, call logs, or any other app's private data. It requests no location, mic, camera, SMS, contacts, or storage permissions. If a check would need one of those, the check is dropped, not the rule.
- Network monitor captures connection metadata only (owner app uid, destination host or IP, port, protocol, timestamp, byte counts). Never payload. DNS names come from the app's own DNS observation inside the VPN tunnel. Nothing is stored beyond the current scan unless the user exports a report.
- Exported reports (`REPORT.md` and a JSON evidence file) go to the app's own Downloads folder via the system share sheet. The app never emails or uploads them.
- Test fixtures in the repo are synthetic. No real device dump is ever committed. `fixtures/live/` is gitignored for any real captures Rick makes during smoke testing.

## Read only guarantee (operational rules)

The app has no code path that calls `PackageInstaller`, `DevicePolicyManager` setters, `Settings.*.putString`, `ActivityManager.killBackgroundProcesses`, or any uninstall intent. A lint rule and a unit test grep the source for those symbols and fail the build if they appear. The two system level things the app does touch, with user consent each time, and which the report lists under "What this scan touched":

| Touch | Why | Reversible |
|---|---|---|
| Usage access (PACKAGE_USAGE_STATS) consent screen | Needed to see which apps ran recently and in background | Yes, user revokes in Settings |
| Local VPN (VpnService) consent dialog | Needed to see outbound connections with app attribution, no root | Yes, VPN stops when the scan ends or the user stops it; app always tears it down on exit |

The VPN never routes traffic anywhere but straight out the device's normal interface. It is a local observer, not a proxy to any server.

## Permissions the app declares (allowlist, anything else is a build failure)

`QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS` (special access), `BIND_VPN_SERVICE` (service), `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` (monitor runs while the user watches), `INTERNET` (IOC refresh only, off by default), `POST_NOTIFICATIONS` (scan progress), `ACCESS_NETWORK_STATE` (install time, no dialog; approved by Rick 2026-10-21 after Phase 2 showed every ConnectivityManager read needs it). Nothing else.

## Data sources, verification status, fallbacks

Verification: nothing is verified yet. Phase 1 builds a throwaway probe activity that calls each API below on Rick's real phone and on an emulator, records what each returns on the actual Android version, and logs results to DECISIONS.md before any collector is written.

| Check | Primary Android API | What it gives | Fallback | SPOF / limit |
|---|---|---|---|---|
| Apps: id, label, publisher, install source, install time | `PackageManager.getInstalledPackages`, `getInstallSourceInfo` (API 30+), `PackageInfo.firstInstallTime`, signing cert via `GET_SIGNING_CERTIFICATES` | Full app inventory, which store installed it, cert hash to match a known publisher | `getInstallerPackageName` on API < 30 | None. Needs `QUERY_ALL_PACKAGES`. |
| Apps: no icon / hidden launcher | `queryIntentActivities(MAIN+LAUNCHER)` vs installed list; `ApplicationInfo.enabled`; label mimicking system names | Classic stalkerware hiding pattern | none | Low |
| Apps: disguised as system service | Compare package name and label against a bundled list of real system packages (per OEM where known), check if it is in `/system` partition (`FLAG_SYSTEM`) | Flags user installed apps that claim to be "System Update", "Sync Service" etc | none | OEM list is incomplete; rule is "flag and explain", never auto clean |
| Deep control: device admins | `DevicePolicyManager.getActiveAdmins`, `isDeviceOwnerApp`, `isProfileOwnerApp` | Who has admin, owner, or work profile control | none | None |
| Deep control: accessibility services | `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`, `AccessibilityManager.getEnabledAccessibilityServiceList` | Apps that can read the screen and inject input | none | None |
| Deep control: notification listeners | `Settings.Secure.ENABLED_NOTIFICATION_LISTENERS` | Apps that read every notification (messages included) | none | None |
| Deep control: when granted, by whom | Not exposed by Android for admin/accessibility grant time | Use the app's `firstInstallTime` and `lastUpdateTime` as the bound; report says "at the latest since <install date>" | none | Documented limitation |
| Permissions: location, mic, camera per app | `PackageInfo.requestedPermissions` + `requestedPermissionsFlags` (granted), `AppOpsManager.unsafeCheckOpNoThrow` for background location / mic / camera ops where allowed | Which apps hold always on location, mic, camera | `PackageManager.checkPermission` | AppOps for other packages is restricted on newer Android; fall back to requested+granted flags and say "granted" not "used" |
| Permissions: does it need it | Category heuristic: app category (`ApplicationInfo.category`), store listing not available offline. Rule: flag always on location / mic / camera on any app that is not in a bundled allowlist of categories (maps, camera, calling, messaging) | Plain language "why this looks odd" | none | Heuristic by design; report wording hedges |
| Persistence: boot receivers | `queryBroadcastReceivers(BOOT_COMPLETED)`, `RECEIVE_BOOT_COMPLETED` in requested permissions | Apps that relaunch on boot | none | None |
| Persistence: runs constantly | `UsageStatsManager.queryEvents` (foreground service start/stop events, API 29+), `queryUsageStats` last 7 days; `ActivityManager.getRunningAppProcesses` only sees self | Background runtime and foreground service time per app | none | Needs usage access consent. Cannot see system privileged daemons. |
| Network: outbound connections per app | `VpnService` local tunnel + `ConnectivityManager.getConnectionOwnerUid` (API 29+) to map each flow to a uid, then uid to package | Every outbound flow while monitor runs, with app, host, port, timing | On API < 29 or if VPN consent refused: `/proc/net` is no longer readable; Network section reads NOT CHECKED | Only while the monitor runs. Default monitor window 10 minutes, user extendable. |
| Network: "known service?" classification | Bundled allowlist of well known domains (Google, Apple, Meta, Microsoft, Amazon, Cloudflare, major CDNs), reverse DNS from observed DNS answers, raw IP with no DNS flagged, non standard ports flagged, fixed interval beacons detected by inter arrival regularity | Raw IPs, odd ports, scheduled reporting | none | Allowlist is finite; unknown is "unrecognized", not "malicious" |
| Known spyware | MVT STIX2 IOCs (package names, domains, cert hashes) pinned snapshot | High confidence stalkerware hits | In app refresh, user toggled | Snapshot date shown in the report |

### Phase 2 verification notes (2026-10-21, see PROBE_RESULTS.md)

- Every data source above returned usable data on Rick's Galaxy S24 Ultra (Android 16) and on API 29 and API 36 emulators, with these adjustments:
  - `getConnectionOwnerUid` works only inside the VPN service, only for other apps' flows. Never from the activity.
  - `NetworkStatsManager` must be queried for both Wi Fi and mobile and summed per uid.
  - `getActiveAdmins` and `ENABLED_ACCESSIBILITY_SERVICES` return null for "none"; treat as empty.
  - `getInstallSourceInfo` is API 30+; on API 29 "no installer" is `unknown`, never `sideloaded`.
  - Private DNS was in automatic (opportunistic) mode on Rick's phone; Phase 11 verifies the plain DNS fallback there.
- RESOLVED 2026-10-21: `ACCESS_NETWORK_STATE` added to the allowlist by Rick so private DNS detection and tunnel teardown confirmation work.

### Known dead, blocked, or fragile sources (do not rediscover)

- `ActivityManager.getRunningServices` / `getRunningAppProcesses` return only the caller's own processes since Android 8. Do not plan anything on them.
- `/proc/net/tcp` and `/proc/<pid>` are unreadable for other uids since Android 10. The VPN approach is the only non root way to attribute traffic.
- `AppOpsManager` queries for other packages require `GET_APP_OPS_STATS`, a signature permission. Not available. Use requested+granted flags.
- Google Play policy: `QUERY_ALL_PACKAGES` is allowed for "device security / antivirus" apps with a declaration form. Irrelevant for the v1 sideload build; noted for a later store release.
- Android 14+ requires `FOREGROUND_SERVICE_SPECIAL_USE` with a declared subtype for the VPN monitor; forgetting it crashes at start on API 34.
- Some OEM builds (Xiaomi, Huawei) hide packages from `getInstalledPackages` unless the app is allowed "autostart". Report notes the OEM and says coverage may be partial.
- Rooted or system privileged spyware that hides from PackageManager is undetectable from an unprivileged app. The report says so in a fixed "What this scan cannot see" footer. This is the single biggest honesty requirement of the product.

## Filesystem rules

- All code under the project root. Gradle caches and build output in the standard `build/` dirs, gitignored.
- The app writes only to its own internal storage and, on export, to Downloads via the share sheet.
- Helper Python scripts write only under `tools/` and `app/src/main/assets/`.

## Test devices and test policy

- Rick supplies one physical Android phone (model and Android version recorded in DECISIONS.md in Phase 1). Phase 1 cannot complete without it connected over USB with USB debugging on; Rick toggles that himself.
- Unit tests (JVM, Robolectric) run against synthetic fixtures: fake PackageManager data, fake usage events, fake flow logs. These carry the test corpus of at least 12 scenarios (clean phone, hidden stalkerware, legit device admin from a work profile, maps app with always on location, raw IP beacon every 60 s, and so on). No test needs a phone.
- Instrumented tests run on an emulator (API 29 and API 35 at minimum) in CI style `./gradlew connectedCheck`.
- Live phone smoke test is a manual checklist in `SMOKE.md`, run by Rick, gated behind nothing because it is manual.
- Minimum supported Android: API 29 (Android 10), because flow attribution needs it. Target the latest stable SDK the installed Android Studio supports.

## Network rules for the tool itself

The app makes no outbound request unless the user turns on "Refresh indicators", and then only to the pinned MVT IOC URL over HTTPS. A unit test asserts the only `URL` literal in the codebase is that one.

## Stopping conditions (adds to universal CLAUDE.md)

- If a planned check requires any permission outside the allowlist above, stop, write BLOCKED.md, do not add the permission.
- If any dependency pulls in a network, analytics, or ads SDK transitively, stop and swap it. Logged to DECISIONS.md.
- If the Phase 1 probe shows an API returns nothing useful on Rick's actual phone, the check moves to "documented limitation" rather than being faked.

## Build environment (verified 2026-10-21 on the Mac Mini)

| Item | Found | Note |
|---|---|---|
| Android Studio | `/Applications/Android Studio.app` | Bundled JBR OpenJDK 21.0.9. No system Java; Gradle uses the JBR via `org.gradle.java.home` or `JAVA_HOME` in the project `gradle.properties`. |
| SDK root | `~/Library/Android/sdk` | platform-tools (adb) present but not on PATH; scripts call it by full path |
| Platforms | 34, 35, 36, 36.1 | compileSdk / targetSdk = 36, minSdk = 29 |
| Build tools | 35.0.0, 36.1.0 | pin 36.1.0 |
| Emulator images | android-36.1 only | Phase 1 adds an API 29 image for the min SDK instrumented tests (download, ~1 GB) |
| Free disk | 65 GB | Fine |
