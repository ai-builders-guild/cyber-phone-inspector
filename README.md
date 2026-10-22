# Cyber Phone Inspector

An Android app you install on your own phone to answer one question in plain language: is anything on this phone watching me (location, messages, microphone, camera) or reporting to a third party?

It runs five read only checks and writes a report with a single verdict line, three lists (Safe, Worth removing, Needs a professional) and the single most important thing to do next. It never installs, removes or changes anything on the phone.

Status: v1 complete (0.1.0). All fourteen roadmap phases built, tested on API 29 and API 36 emulators and on a Galaxy S24 Ultra running Android 16.

## What it checks

| Check | How | Needs |
|---|---|---|
| Apps | Every installed package with publisher, install source, hidden launcher and disguised system name detection | nothing |
| Deep control | Device admins, device and profile owners, accessibility services, notification listeners | nothing |
| Permissions | Microphone, camera, location and background location grants, with a category based "looks odd" heuristic | nothing |
| Persistence | Boot receivers and foreground service time over the last 7 days | usage access, granted by you in Settings |
| Network | A local DNS only VPN that attributes every name lookup to the app that made it, plus per app byte totals, for a bounded window | the system VPN consent, tapped by you |

Findings are matched against the public MVT indicator snapshot (680 package ids, 6,548 domains, 497 signing certs) bundled at build time. Rules live in `app/src/main/assets/rules.json` as data.


v1.1 adds three checks that run against lists stored on the phone, never by sending anything out: every non system app's installed file is hashed and compared with the MalwareBazaar malware list; every non system app's code is searched for the Exodus Privacy tracker signatures (adware heavy outside a store is a medium, inside a store a note); and hosts an app contacted are compared with the URLhaus and ThreatFox lists. Results are cached per app version so a rescan only reads apps that changed. A safety score (100, minus 30 per app with a high, 10 per app with a medium) sits beside the verdict, and an app you mark as yours is kept as a dated note that returns if a stronger finding appears.

## What it cannot see

Rooted or system privileged spyware, packages an OEM hides from the inventory, anything on the network side (router, carrier, SIM), your accounts and cloud backups, physical trackers, the work side of a work profile, traffic from apps that only sent data while the monitor was off, and name lookups by apps that bring their own encrypted DNS (notably Chrome). Every report ends with this list.

## Build from a clean checkout (macOS)

Requirements: Android Studio 2025.3 or newer (its bundled JDK is used; no system Java needed) with SDK platform 36 and build tools 36.1.0 installed, and `uv` for the helper scripts.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug test
```

`JAVA_HOME` is needed because the Gradle wrapper launcher runs before `gradle.properties` is read. `ANDROID_HOME` tells Gradle where the SDK is; `local.properties` with `sdk.dir=...` works too. `./gradlew check` runs unit tests, lint, ktlint and the dependency allowlist.

Install the debug build on a connected phone or emulator with USB debugging on:

```bash
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build carries a second launcher entry, CPI Probe, used for the per phase hardware gates. It is absent from release builds.

## Release build

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleRelease && mkdir -p dist && cp app/build/outputs/apk/release/app-release.apk dist/
```

Signing reads `CPI_RELEASE_KEYSTORE_PATH`, `CPI_RELEASE_KEYSTORE_PASSWORD`, `CPI_RELEASE_KEY_ALIAS` and `CPI_RELEASE_KEY_PASSWORD` from `.env` (or the environment). Without them the release APK is unsigned and will not install. The keystore lives outside the repo. R8 shrinks the release to about 2 MB.

A release APK cannot be installed over a debug build (different signing key): uninstall the debug build first, which also clears the app's consents. Release over release keeps data.

## Instrumented tests

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

Six tests: consent flow, full scan to verdict, denied usage access, pipeline, export, monitor. They grant the emulator's own consents through the instrumentation shell. The monitor test skips where VPN consent was never given.

## Manual smoke test

`SMOKE.md` is the checklist to run on a real phone before any release.

## Using the app

1. First run explains the two consents. Continue.
2. Grant usage access from the Home button if you want the Persistence check.
3. Start scan. Answer the notification prompt either way. The verdict appears first; Share and Save to Downloads export `CPI_REPORT_<stamp>.md` and the evidence JSON to Download/CyberPhoneInspector.
4. Start network monitor, accept Android's Connection request, use the phone normally for 10 minutes. It stops itself. The next scan within 24 hours includes the Network section.
5. Private DNS set to a named provider blocks the monitor with an explanation; set it to Automatic for the monitor's duration.

## Privacy

Everything stays on the phone. No analytics, no crash reporting, no network calls of the app's own (1 byte attributed to the app in a 10 minute monitor window). The monitor relays DNS only and stores names, never packets. Exports go where you send them and nowhere else. Backup and device transfer of the app's data are disabled.

## Environment variables

See `.env.example`. Nothing is required for a debug build.

## Guardrails

`app/src/test/.../guardrails/GuardrailTest.kt` fails the build if any manifest declares a permission outside the allowlist, if any source symbol could write to the phone, or if any URL literal other than the pinned indicator source appears. `./gradlew :app:verifyDependencyAllowlist` fails on any dependency group outside the allowlist.

## Helper scripts

`tools/` is a uv managed Python project (stdlib only). It runs on the Mac, never on the phone.

```bash
uv run --project tools python tools/make_fixture.py          # regenerate test fixtures from tools/scenarios/*.toml
uv run --project tools python tools/make_fixture.py --check  # verify committed fixtures match the scenarios
uv run --project tools python -m unittest discover -s tools -p 'test_*.py'
uv run --project tools python tools/fetch_iocs.py          # refresh app/src/main/assets/iocs/snapshot.json from the MVT indicator index
uv run --project tools python tools/fetch_iocs.py --check  # fail if the committed snapshot is older than 90 days
./gradlew :app:testDebugUnitTest -Pcpi.updateGolden=true  # regenerate expected findings and golden reports after a deliberate rule change, then review the diff
```

## Repository

Private: https://github.com/CyberRick-AI/cyber-phone-inspector

## Project documents

CLAUDE.md (guardrails), PROJECT.md (limits and data sources), ROADMAP.md (phases and gates), INITIAL_INSTRUCTIONS.md (stack, severity table, assumptions), DECISIONS.md (why), UPDATES.md (what happened, per phase), PROBE_RESULTS.md (what each Android API returned on real hardware), BACKLOG.md (not in v1), SMOKE.md (manual checklist).
