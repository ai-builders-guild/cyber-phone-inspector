# CLAUDE.md — Cyber Phone Inspector (universal guardrails)

These guardrails are always in force for Claude Code on this project. Project-specific limits live in
PROJECT.md; the plan lives in ROADMAP.md; assumptions and tech stack live in INITIAL_INSTRUCTIONS.md.

## Scope & execution
- Work only in the project dir. Follow ROADMAP phases in order.
- Out-of-scope ideas go to BACKLOG.md, not implemented.
- Don't ask clarifying questions mid-run — decide and log to DECISIONS.md.

## Environment
- Target build machine: macOS (Mac Mini). Shell zsh.
- App is Kotlin/Gradle built with Android Studio (bundled JDK 21, SDK at `~/Library/Android/sdk`). Helper scripts only: Python via **uv** (`uv run`, `uv add`); venv at `.venv/bin/python`.
- Forward slashes in code; paths via `pathlib`, never string concatenation.

## Git safety
- Never force-push; never rewrite pushed history; never commit to main directly.
- Feature branch per phase. Stage explicitly (no `git add .` until `.gitignore` is verified).
- Commit after each phase with a message referencing it. Push to the GitHub remote to keep history.

## Secrets
- Never hardcode. Env vars only. Never commit `.env*`.
- New secret → add name to `.env.example` (no value) + document in README.

## Filesystem safety
- Read before overwrite. No recursive deletes without an explicit allowlist. No ops outside the project dir.

## Dependencies
- Prefer stdlib / AndroidX. Justify each new dep in DECISIONS.md. Pin versions in the Gradle version catalog. Python deps into the project venv only.

## Testing
- Run tests after each phase. Failing tests block the commit. New code needs tests before the phase is done.
- "No tests exist" is not "tests pass."

## Documentation
- Dated entry in UPDATES.md after each phase. Keep README current. Log non-obvious choices in DECISIONS.md.

## Stopping conditions
- 3 consecutive failed attempts at the same problem → stop, write BLOCKED.md.
- Unresolvable ambiguity → stop, write BLOCKED.md. Uncertain an op is safe → stop.
- No background/non-terminating processes without explicit instruction.

## API & cost
- Log every paid call to `api_usage.jsonl` before making it. Never retry a failed call more than twice.
- Never call batch endpoints without per-item limits. Prefer cached/local first. Caps live in PROJECT.md.
