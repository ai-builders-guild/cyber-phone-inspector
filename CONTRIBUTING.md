# Contributing

Thanks for testing or improving Cyber Phone Inspector. The project is GPL-3.0-or-later; by contributing you agree your contribution is under the same licence.

## Testing on your own phone

1. Install the latest release APK (see [docs/USING.md](docs/USING.md)) or build from source (README, "Build from a clean checkout").
2. Walk [SMOKE.md](SMOKE.md). It is the checklist the maintainer runs before every release.
3. Report what reads wrong or surprises you with the issue templates. For a false positive, the "False positive" template asks for the label, the rule names from the detail screen and whether you installed the app yourself. Share package names only from your own phone and only if you are comfortable doing so; never post a report that names someone else's apps.

## Building and checking

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # or your JDK 17+
./gradlew check            # unit tests, ktlint, Android Lint (fatal), dependency allowlist
./gradlew assembleDebug
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

`./gradlew check` must be green before a pull request. Lint is fatal on purpose: unused strings, plural candidates and API level mistakes fail the build.

## The guardrails

Some things are enforced by tests rather than review, and a pull request that trips one will not merge:

- The manifest may request only the eight permissions in `PROJECT.md`; the allowlist lives in `GuardrailTest`.
- No code may call anything that changes the phone (package installer, kill, settings writes, admin removal, component enabling). The forbidden symbol list is in `GuardrailTest`.
- Android's uninstall intent may appear in exactly one file, `actions/RemoveAction.kt`.
- The only outbound addresses are the MVT index and the feeds repository, both pinned in `GuardrailTest`.
- New dependencies must be on the Gradle allowlist with a reason in `DECISIONS.md`.

## Changing a rule

Rules are data in `app/src/main/assets/rules.json`. Every rule needs an `id`, `check`, `severity`, `bucket`, `summary`, `reason`, `remediation`, `evidence`, a `label` from `Labels.ALL`, a `cardText` sentence and a `when` condition in the six operator language (`eq`, `in`, `gt`, `not`, `all`, `any`). Behaviour rules say "unrecognised", "hidden" or "odd", never "malicious"; a test enforces it.

After a rule change, regenerate and read the goldens:

```bash
./gradlew :app:testDebugUnitTest -Pcpi.updateGolden=true
git diff app/src/test/resources/expected app/src/test/resources/golden
```

Add a scenario under `tools/scenarios/` for any new behaviour and run `uv run python tools/make_fixture.py`.

## Pull requests

- One topic per branch, branched from `main`. The maintainer merges with `--no-ff`.
- Describe what changed and how you verified it (which tests, which device). Screenshots for anything visual.
- Keep personal data out: no phone serials, no package names from a phone that is not yours, no reports.
- Non obvious choices go in `DECISIONS.md` with a dated entry; what happened goes in `UPDATES.md`.

## Code style

Kotlin, ktlint (`./gradlew :app:ktlintFormat`), Compose for screens, pure Kotlin for anything that decides something so it can be tested on the JVM. Collectors read through small adapter interfaces; the Android side is thin.
