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
