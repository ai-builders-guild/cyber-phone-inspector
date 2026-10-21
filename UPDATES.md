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
