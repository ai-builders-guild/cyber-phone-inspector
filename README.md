# Cyber Phone Inspector

An Android app you install on your own phone to find out, in plain language, whether anything on it is watching you (location, messages, mic, camera) or reporting to a third party. Read only: it never modifies, installs, or removes anything.

Status: Phase 1 scaffold. Not yet usable.

## Build from a clean checkout (macOS)

Requirements: Android Studio 2025.3 or newer (its bundled JDK is used; no system Java needed), Android SDK with platform 36 and build tools 36.1.0.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug test
```

`ANDROID_HOME` tells Gradle where the SDK is. Alternatively create `local.properties` (gitignored) containing `sdk.dir=/Users/<you>/Library/Android/sdk`.

The wrapper launcher needs `JAVA_HOME` because this Mac has no system Java; `gradle.properties` then points the build itself at the same bundled JDK via `org.gradle.java.home`. If your Android Studio lives elsewhere, change both.

## Environment variables

See `.env.example`. Nothing is required for a debug build. Release signing reads the keystore variables from `.env` (never committed).

## Guardrails

`app/src/test/.../guardrails/GuardrailTest.kt` fails the build if the manifest declares a permission outside the allowlist, if any source symbol could write to the phone, or if any URL literal other than the pinned indicator source appears. `./gradlew :app:verifyDependencyAllowlist` fails on any dependency group outside the allowlist.

## Helper scripts

`tools/` is a uv managed Python project for fixture and indicator generation. It runs on the Mac, never on the phone.
