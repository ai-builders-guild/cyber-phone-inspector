# INITIAL_INSTRUCTIONS.md — Cyber Phone Inspector

## Boundary (pinned, from PROJECT.md Q2)
Claude Code builds the Kotlin app, the rule engine, the report renderer, the fixtures and the tests. The app collects, flags and renders on device, deterministically, read only, with no LLM. Rick installs it, grants the two consents (usage access, local VPN), runs scans, removes things himself, and makes the final call on anything in "Needs a professional". iOS is out of scope for this project.

## Overview and constraints
An Android app the phone's owner runs to find out whether anything on the phone watches them or reports to a third party. Five checks (Apps, Deep control, Permissions, Persistence, Network), one report in the brief's exact contract. Constraints: zero paid APIs, zero telemetry, permission allowlist only (eight entries in PROJECT.md; `ACCESS_NETWORK_STATE` added after Phase 2, `REQUEST_DELETE_PACKAGES` added for v1.1 removal), no writes to other apps or settings, everything stays on the phone. Read CLAUDE.md and PROJECT.md for the rules; this file is stack and layout.

## The brief's output contract (embed as a test string in Phase 9)
For each flag: what it is, what it can reach, how long it has been there, where it sends data, severity low/medium/high. END WITH: one verdict line (clean or compromised), then three lists Safe / Worth removing / Needs a professional, then the single most important thing to do next. Read only.

## Tech stack (decided)
| Choice | Decision | Rationale |
|---|---|---|
| Language | Kotlin 2.x | Android default, coroutines for parallel collectors |
| UI | Jetpack Compose + Material 3 | Simple screens, accessibility semantics built in |
| Build | Gradle Kotlin DSL, version catalog `gradle/libs.versions.toml`, AGP matching the installed Android Studio | Pinned, reproducible |
| JDK | Android Studio bundled JBR 21 via `org.gradle.java.home` in `gradle.properties` | No system Java on the Mac |
| SDK | minSdk 29, compileSdk 36, targetSdk 36, build tools 36.1.0 | Flow attribution needs API 29; 36 is what is installed |
| JSON | kotlinx.serialization | No reflection, small, stdlib adjacent |
| Async | kotlinx.coroutines | Structured cancellation for scan and monitor |
| Networking | `HttpsURLConnection` for the single IOC refresh | No third party HTTP client, no transitive SDKs |
| Tests | JUnit 4, Robolectric (JVM), AndroidX Test + Espresso (instrumented), Truth assertions | Standard, runs without a phone |
| Lint | Android Lint + ktlint, run in the Phase 1 gate | Formatter, not hand style |
| Helper scripts | Python 3.13 via uv in `tools/` (`stix2` parsing is plain JSON, stdlib only) | Fixture and IOC generation off device |

Dependency allowlist (anything else fails the Phase 1 test): `androidx.core`, `androidx.activity:activity-compose`, `androidx.compose.*` (BOM), `androidx.lifecycle:*`, `androidx.navigation:navigation-compose`, `org.jetbrains.kotlinx:kotlinx-coroutines-*`, `org.jetbrains.kotlinx:kotlinx-serialization-json`, test only: `junit`, `org.robolectric:robolectric`, `androidx.test.*`, `com.google.truth:truth`. No Firebase, no Play Services, no analytics, no ads, no OkHttp, no Retrofit.

## Directory structure
```
Cyber Phone Inspector/
  CLAUDE.md  PROJECT.md  ROADMAP.md  INITIAL_INSTRUCTIONS.md  DECISIONS.md
  UPDATES.md  BACKLOG.md  README.md  SMOKE.md  api_usage.jsonl  .env.example  .gitignore
  settings.gradle.kts  build.gradle.kts  gradle.properties  gradle/libs.versions.toml
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/java/com/cyberrick/phoneinspector/
      core/model/        evidence data classes, Evidence envelope, ids, UTC helpers
      collect/           AppsCollector, ControlCollector, PermissionsCollector, PersistenceCollector
      net/               DnsTunnelService, NetStatsCollector, dns/ (packet parser)
      rules/             RuleEngine, IocMatcher, predicates/
      report/            ReportRenderer, Exporter
      ui/                Home, Consent, Scanning, Report screens, theme
      service/           ScanService (foreground), notifications
      probe/             ProbeActivity (debug build only)
    src/main/assets/
      iocs/              packages.json domains.json certs.json SNAPSHOT.md
      system_packages.json  permission_expectations.json  known_services.json
    src/main/res/
    src/test/            JVM tests, resources/fixtures/*.json, resources/expected/*.json, golden/*.md
    src/androidTest/     instrumented tests
  tools/                 uv project: make_fixture.py, fetch_iocs.py, pyproject.toml
  fixtures/live/         gitignored, Rick's real captures only
  dist/                  gitignored, release APKs
```

## Architecture in one paragraph
Collectors are pure functions of Android system services that emit immutable evidence records into one `Evidence` envelope. The rule engine is generic: it reads `rules.json`, evaluates each rule's condition against the evidence, and emits immutable `Finding`s with `ruleId`, `severity`, `bucket`, `evidenceRef`. The renderer is a pure function of findings plus evidence to a string. Nothing downstream of collectors touches Android APIs, so JVM tests cover everything but the collectors' adapters. All state is created new, never mutated.

## Severity table (implemented as data in app/src/main/assets/rules.json; this table and that file must agree)
| Condition | Severity | Bucket |
|---|---|---|
| IOC match on package name or signing cert | high | needsProfessional |
| Hidden launcher AND install source not Play, vendor store, or web app AND (accessibility OR device admin OR mic/camera/location granted) | high | needsProfessional |
| Hidden launcher on a Play, vendor store, or web app install AND (accessibility OR device admin) | medium | worthRemoving |
| Accessibility service enabled on a non system, non accessibility category app | high | needsProfessional |
| Device admin held by a non system app that is not a profile owner (no work profile) | medium | worthRemoving |
| Disguised system name on a user installed app | medium | worthRemoving |
| Sideloaded (installer unknown or not a known store) AND any sensitive permission granted | medium | worthRemoving |
| Notification listener on an app outside expected categories | medium | worthRemoving |
| Background location, mic, or camera granted outside expected category AND install source not Play, vendor store, or web app | medium | worthRemoving |
| Background location, mic, or camera granted outside expected category on a store install, alone | low | safe (listed with the note) |
| Boot receiver AND backgroundHeavy outside expected category | medium | worthRemoving |
| Fixed interval beacon to an unrecognized host (5 or more lookups, gap variation under 15 percent) | medium | worthRemoving |
| More than 1 MB sent with no DNS lookup at all (rawIpSuspect) | medium | worthRemoving |
| Unrecognized host AND another oddity on the same app (non store install, control grant, odd permission, hidden, disguised, heavy background) | medium | worthRemoving |
| Unrecognized host alone | low | safe (listed with the note) |
| Any single oddity alone (sideloaded with no sensitive perms, unrecognized host alone, boot receiver alone) | low | safe (listed with the note) |
| Control grant (admin, accessibility, listener) on a package missing from the inventory | medium | needsProfessional |
| Profile owner present (work profile) | low | safe (listed with the note) |
| Duplicate package entry | low | safe (listed with the note) |
| App contacted a domain on the IOC list | high | needsProfessional |
| Check NOT_CHECKED | n/a | reported as unknown, verdict cannot say clean for that check |

Verdict: `compromised` if any high. Otherwise `clean`, with mediums in "Worth removing". Next step rule: the highest severity finding's remediation text; if none, "Nothing to do. Rescan after installing anything new."

## Fixture corpus (Phase 3, minimum 12)
01 clean_pixel, 02 clean_samsung_with_work_profile, 03 hidden_stalkerware_accessibility, 04 ioc_package_match, 05 disguised_system_update_app, 06 maps_background_location_legit, 07 calculator_with_mic, 08 sideloaded_no_perms, 09 device_admin_antitheft_legit_play, 10 beacon_60s_unknown_host, 11 no_dns_bytes_sent, 12 usage_access_denied_not_checked, plus edge: 13 empty_phone, 14 two_thousand_packages, 15 duplicate_package_ids, 16 malformed_settings_string.

## Assumptions (labelled, each one is a DECISIONS.md entry Claude Code may overturn with evidence from Phase 2)
- ASSUMPTION: Rick's test phone runs Android 12 or newer and is not rooted.
- ASSUMPTION: `getConnectionOwnerUid` works for UDP flows through a VpnService tunnel on API 29+ as documented.
- ASSUMPTION: Private DNS in automatic mode falls back to plain DNS when the tunnel resolver does not answer DoT.
- ASSUMPTION: sideloaded debug and release builds are sufficient for v1; no Play listing.
- ASSUMPTION: MVT's public STIX2 bundle remains at its current GitHub raw URL; snapshot bundled regardless.
- ASSUMPTION: `requestedPermissionsFlags` is sufficient for "granted" on all target devices; AppOps for other packages is not available.
- ASSUMPTION: the user reads English; no localization in v1.
