# Handoff prompt for Claude Code (paste as the first message in a session opened at the project root)

```
Read CLAUDE.md, PROJECT.md, ROADMAP.md, and INITIAL_INSTRUCTIONS.md in full
before doing anything. These are your operating constraints and plan.

Then begin Phase 1 from ROADMAP.md. Follow the phases in order. After each
phase: run the validation gate, run the tests, append to UPDATES.md, append
any decisions to DECISIONS.md, and commit to a feature branch with a message
referencing the phase. Do not proceed to the next phase until the current
phase's validation gate passes.

Respect every cost cap in PROJECT.md (there are zero paid APIs in this
project; api_usage.jsonl must stay empty). If you hit a stopping condition,
write BLOCKED.md (or BUDGET_EXCEEDED.md) and stop rather than guessing. Do not
ask me questions mid-run; make reasonable decisions and log them.

Phase 2 needs my physical Android phone connected over USB with USB debugging
on. If it is not connected when you reach Phase 2, write BLOCKED.md saying so
and stop.

Start now with Phase 1.
```
