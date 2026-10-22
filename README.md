# Cyber Phone Inspector

An Android app you install on your own phone to find out, in plain language, whether anything on it is watching you (location, messages, mic, camera) or reporting to a third party. Read only: it never modifies, installs, or removes anything.

Status: v1 feature complete through Phase 12 (inventory, deep control, permissions, persistence, DNS monitor, rules, report). Phase 13 adds instrumented tests and a signed release build.

## Build from a clean checkout (macOS)

Requirements: Android Studio 2025.3 or newer (its bundled JDK is used; no system Java needed), Android SDK with platform 36 and build tools 36.1.0.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug test
```

`ANDROID_HOME` tells Gradle where the SDK is. Alternatively create `local.properties` (gitignored) containing `sdk.dir=/Users/<you>/Library/Android/sdk`.

The wrapper launcher needs `JAVA_HOME` because this Mac has no system Java; `gradle.properties` then points the build itself at the same bundled JDK via `org.gradle.java.home`. If your Android Studio lives elsewhere, change both.

## Release build

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleRelease && mkdir -p dist && cp app/build/outputs/apk/release/app-release.apk dist/
```

Signing reads `CPI_RELEASE_KEYSTORE_PATH`, `CPI_RELEASE_KEYSTORE_PASSWORD`, `CPI_RELEASE_KEY_ALIAS` and `CPI_RELEASE_KEY_PASSWORD` from `.env` (or the environment). Without them the release APK is unsigned and will not install. The keystore lives outside the repo. R8 shrinks the release to about 2 MB; the debug build is about 30 MB because of the probe and test tooling.

A release APK cannot be installed over a debug build (different signing key): uninstall the debug build first, which also clears the app's consents.

## Manual smoke test

`SMOKE.md` is the checklist Rick runs on the real phone before any release.

## Environment variables

See `.env.example`. Nothing is required for a debug build. Release signing reads the keystore variables from `.env` (never committed).

## Guardrails

`app/src/test/.../guardrails/GuardrailTest.kt` fails the build if the manifest declares a permission outside the allowlist, if any source symbol could write to the phone, or if any URL literal other than the pinned indicator source appears. `./gradlew :app:verifyDependencyAllowlist` fails on any dependency group outside the allowlist.

## Helper scripts

`tools/` is a uv managed Python project for fixture and indicator generation. It runs on the Mac, never on the phone.

```bash
uv run --project tools python tools/make_fixture.py          # regenerate test fixtures from tools/scenarios/*.toml
uv run --project tools python tools/make_fixture.py --check  # verify committed fixtures match the scenarios
uv run --project tools python -m unittest discover -s tools -p 'test_*.py'
uv run --project tools python tools/fetch_iocs.py          # refresh app/src/main/assets/iocs/snapshot.json from the MVT indicator index
uv run --project tools python tools/fetch_iocs.py --check  # fail if the committed snapshot is older than 90 days
./gradlew :app:testDebugUnitTest -Pcpi.updateGolden=true  # regenerate expected findings after a deliberate rule change, then review the diff
```
